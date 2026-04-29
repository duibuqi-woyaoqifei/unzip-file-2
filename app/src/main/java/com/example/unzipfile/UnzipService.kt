package com.example.unzipfile

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import androidx.documentfile.provider.DocumentFile
import com.example.unzipfile.membership.MembershipManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.io.FilterInputStream
import net.lingala.zip4j.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.utils.IOUtils

class UnzipService : Service() {

    private val CHANNEL_ID = "unzip_channel"
    private val NOTIFICATION_ID = 1

    private lateinit var membershipManager: MembershipManager
    private var isCancelled = false
    
    private val serviceJob = kotlinx.coroutines.Job()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + serviceJob)

    companion object {
        const val ACTION_STOP = "com.example.unzipfile.ACTION_STOP_UNZIP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        membershipManager = MembershipManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 支持单文件和批量文件
        if (intent?.action == ACTION_STOP) {
            isCancelled = true
            updateNotification(-2, "停止中...")
            return START_NOT_STICKY
        }

        val sourceUriStrings = intent?.getStringArrayListExtra("source_uris")
        val destUriStr = intent?.getStringExtra("dest_uri")
        val password = intent?.getStringExtra("password")

        if (!sourceUriStrings.isNullOrEmpty() && destUriStr != null) {
            val sourceUris = sourceUriStrings.map { Uri.parse(it) }
            val destUri = Uri.parse(destUriStr)
            
            startForeground(NOTIFICATION_ID, createNotification(0, getString(R.string.preparing)))
            
            serviceScope.launch {
                performBatchUnzip(sourceUris, destUri, password)
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotification(progress: Int, message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.extracting_archive))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.unzip_service_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun performBatchUnzip(sourceUris: List<Uri>, destUri: Uri, password: String?) {
        val totalFiles = sourceUris.size
        var successCount = 0
        var failCount = 0

        try {
            // 1. 基础权限检查
            val fileSizes = sourceUris.mapNotNull { uri ->
                getFileSize(uri)
            }

            val (allowed, errorMessage) = membershipManager.checkMultipleFilesPermission(fileSizes)
            if (!allowed) {
                updateNotification(-1, errorMessage ?: getString(R.string.permission_denied))
                return
            }

            // 2. 加密功能检查
            if (!password.isNullOrBlank()) {
                val (encAllowed, encMessage) = membershipManager.checkEncryptionPermission()
                if (!encAllowed) {
                    updateNotification(-1, encMessage ?: "加密解密功能仅限会员")
                    return
                }
            }

            // 逐个处理文件
            val totalSize = fileSizes.sum()
            var currentProcessedSize = 0L

            sourceUris.forEachIndexed { index, sourceUri ->
                if (isCancelled) return@forEachIndexed
                try {
                    val fileName = getFileName(sourceUri) ?: "archive_$index"
                    
                    // 检查格式权限
                    val (formatAllowed, formatMessage) = membershipManager.checkUnzipFormatPermission(fileName)
                    if (!formatAllowed) {
                        failCount++
                        updateNotification(-1, formatMessage ?: "该格式仅限会员使用")
                        return@forEachIndexed 
                    }

                    val sourceLength = getFileSize(sourceUri) ?: 0L
                    
                    performSingleUnzip(sourceUri, destUri, password) { bytesRead ->
                        currentProcessedSize += bytesRead
                        // Progress calculation based on bytes read from compressed source
                        val progress = if (totalSize > 0) (currentProcessedSize * 100 / totalSize).toInt() else index * 100 / totalFiles
                        
                        // Throttle notification updates (at most once per 200ms or on significant progress change)
                        updateNotificationThrottled(progress.coerceAtMost(99), "正在解析: $fileName")
                    }
                    successCount++
                } catch (e: Exception) {
                    android.util.Log.e("UnzipService", "Failed to extract file $index", e)
                    failCount++
                }
            }
            
            if (isCancelled) {
                updateNotification(-2, "已停止解压")
                return
            }

        } catch (e: Exception) {
            android.util.Log.e("UnzipService", "Batch extraction error", e)
            updateNotification(-1, e.message ?: getString(R.string.extraction_failed), true)
        } finally {
            val resultMessage = if (failCount == 0) {
                getString(R.string.extraction_successful)
            } else {
                "完成: $successCount 成功, $failCount 失败"
            }
            updateNotification(100, resultMessage, true)
            stopForeground(true)
            stopSelf()
        }
    }

    private fun performSingleUnzip(sourceUri: Uri, destUri: Uri, password: String?, onProgress: (Long) -> Unit) {
        val tempExtractDir = File(cacheDir, "temp_extraction_${System.currentTimeMillis()}")
        tempExtractDir.mkdirs()

        try {
            val fileName = getFileName(sourceUri) ?: "archive"
            
            val extension = fileName.lowercase().substringAfterLast(".", "")
            
            when (extension) {
                "zip" -> extractZipWithPassword(sourceUri, tempExtractDir, password, onProgress)
                "rar" -> extractRar(sourceUri, tempExtractDir, password, onProgress)
                "7z" -> extract7z(sourceUri, tempExtractDir, password, onProgress)
                else -> {
                    // Try Apache Commons Compress for generic formats (Zip, Tar, etc.)
                    val rawInputStream = contentResolver.openInputStream(sourceUri) ?: throw Exception("Cannot open input stream")
                    val countingStream = CountingInputStream(rawInputStream, onProgress)
                    extractGeneric(countingStream, tempExtractDir)
                }
            }

            // Verify if files exist after extraction
            val extractedFiles = tempExtractDir.listFiles()
            if (extractedFiles != null && extractedFiles.isNotEmpty()) {
                if (destUri.scheme == "file") {
                    val destFile = File(destUri.path ?: "")
                    val baseName = fileName.substringBeforeLast(".")
                    val wrapperFolder = File(destFile, baseName)
                    wrapperFolder.mkdirs()
                    tempExtractDir.copyRecursively(wrapperFolder, overwrite = true)
                } else {
                    val destDocument = DocumentFile.fromTreeUri(this, destUri)
                    if (destDocument != null && destDocument.canWrite()) {
                        // Create a wrapper folder named after the archive (minus extension)
                        val baseName = fileName.substringBeforeLast(".")
                        val wrapperFolder = destDocument.createDirectory(baseName) ?: destDocument
                        
                        copyDirectoryToDocument(tempExtractDir, wrapperFolder)
                    }
                }
            }

        } finally {
            tempExtractDir.deleteRecursively()
        }
    }

    private fun extractGeneric(inputStream: InputStream, targetDir: File) {
        // Use Apache Commons Compress for better format support
        BufferedInputStream(inputStream).use { bis ->
            try {
                bis.mark(1024 * 1024) // Set 1MB mark to allow reset if detection fails
                ArchiveStreamFactory().createArchiveInputStream(bis).use { ais ->
                    var entry = ais.nextEntry
                    while (entry != null) {
                        if (isCancelled) break
                        val file = File(targetDir, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { fos ->
                                IOUtils.copy(ais, fos)
                            }
                        }
                        entry = ais.nextEntry
                    }
                }
            } catch (e: Exception) {
                // Fallback to basic Zip if detection fails
                Log.e("UnzipService", "Generic extract failed, attempting basic zip fallback", e)
                try {
                    bis.reset()
                    extractZip(bis, targetDir)
                } catch (resetError: Exception) {
                    Log.e("UnzipService", "Failed to reset stream", resetError)
                }
            }
        }
    }

    private fun extractZipWithPassword(sourceUri: Uri, targetDir: File, password: String?, onProgress: (Long) -> Unit) {
        val tempFile = File(cacheDir, "temp_zip_${System.currentTimeMillis()}.zip")
        try {
            contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val zipFile = ZipFile(tempFile, password?.toCharArray())
            if (zipFile.isEncrypted && password.isNullOrBlank()) {
                throw Exception("ZIP 已加密，请输入密码")
            }
            zipFile.extractAll(targetDir.absolutePath)
            onProgress(tempFile.length())
        } finally {
            tempFile.delete()
        }
    }

    private fun extractRar(sourceUri: Uri, targetDir: File, password: String?, onProgress: (Long) -> Unit) {
        // Rar progress is harder via Junrar, let's just use the counting stream for the source file copy, 
        // then for the extraction itself we might have to settle for header progress or just counting bytes from source if possible.
        // Actually Junrar reads from File, so we can't easily wrap the input.
        // Let's use the header size which is uncompressed but it's better than nothing if we can't track source.
        val tempFile = File(cacheDir, "temp_rar_bundle.rar")
        val archiveSize = getFileSize(sourceUri) ?: 0L
        
        contentResolver.openInputStream(sourceUri)?.use { input ->
            tempFile.outputStream().use { output ->
                // Copy with progress
                val buffer = ByteArray(32768)
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    output.write(buffer, 0, bytes)
                    onProgress(bytes.toLong() / 2) // We count copy as 50% of work
                    bytes = input.read(buffer)
                }
            }
        }

        Archive(tempFile, password).use { archive ->
            var header: FileHeader? = archive.nextFileHeader()
            while (header != null) {
                val currentHeader = header!!
                if (isCancelled) break
                val out = File(targetDir, currentHeader.fileNameString.trim())
                if (currentHeader.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos ->
                        archive.extractFile(currentHeader, fos)
                        // Assume extraction is the other 50%
                        // This is tricky. Let's just update based on uncompressed size but adjust ratio
                        onProgress((currentHeader.fullUnpackSize / 2)) 
                    }
                }
                header = archive.nextFileHeader()
            }
        }
        tempFile.delete()
    }

    private var lastUpdateTime = 0L
    private fun updateNotificationThrottled(progress: Int, message: String) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > 300) { // Every 300ms
            updateNotification(progress, message)
            lastUpdateTime = now
        }
    }

    private class CountingInputStream(
        inputStream: InputStream,
        private val onProgress: (Long) -> Unit
    ) : FilterInputStream(inputStream) {
        override fun read(): Int {
            val byte = super.read()
            if (byte != -1) onProgress(1)
            return byte
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytes = super.read(b, off, len)
            if (bytes != -1) onProgress(bytes.toLong())
            return bytes
        }
    }

    private fun extract7z(sourceUri: Uri, targetDir: File, password: String?, onProgress: (Long) -> Unit) {
        val tempFile = File(cacheDir, "temp_7z_bundle.7z")
        contentResolver.openInputStream(sourceUri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        SevenZFile(tempFile, password?.toCharArray()).use { sevenZFile ->
            var entry = sevenZFile.nextEntry
            while (entry != null) {
                if (isCancelled) break
                val out = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos ->
                        val content = ByteArray(8192)
                        var size: Int
                        while (sevenZFile.read(content).also { size = it } != -1) {
                            fos.write(content, 0, size)
                        }
                        onProgress(entry.size)
                    }
                }
                entry = sevenZFile.nextEntry
            }
        }
        tempFile.delete()
    }

    private fun extractZip(inputStream: InputStream, targetDir: File) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (isCancelled) break
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        val buffer = ByteArray(32768)
                        var bytes = zis.read(buffer)
                        while (bytes >= 0) {
                            if (isCancelled) break
                            fos.write(buffer, 0, bytes)
                            bytes = zis.read(buffer)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
        }
    }
}

    private fun getFileSize(uri: Uri): Long? {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").length()
        }
        var size: Long? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = android.provider.OpenableColumns.SIZE
                val idx = cursor.getColumnIndex(sizeIndex)
                if (idx != -1) {
                    size = cursor.getLong(idx)
                }
            }
        }
        return size
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) name = cursor.getString(nameIndex)
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }


    private fun copyDirectoryToDocument(sourceDir: File, destDoc: DocumentFile) {
        if (isCancelled) return
        sourceDir.listFiles()?.forEach { file ->
            if (isCancelled) return@forEach
            if (file.isDirectory) {
                val newDir = destDoc.createDirectory(file.name)
                if (newDir != null) {
                    copyDirectoryToDocument(file, newDir)
                }
            } else {
                val newFile = destDoc.createFile("*/*", file.name)
                if (newFile != null) {
                    contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }


    private fun updateNotification(progress: Int, message: String, complete: Boolean = false) {
        val cappedProgress = progress.coerceIn(-2, 100)
        val notification = createNotification(cappedProgress, message)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
        
        // Send broadcast to UI
        val intent = Intent("UNZIP_PROGRESS").apply {
            putExtra("progress", cappedProgress)
            putExtra("message", message)
            putExtra("complete", complete)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}




