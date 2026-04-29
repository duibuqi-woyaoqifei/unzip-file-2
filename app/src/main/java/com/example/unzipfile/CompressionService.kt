package com.example.unzipfile

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.example.unzipfile.membership.MembershipManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * 压缩服务
 * 支持单文件和批量文件压缩为ZIP格式
 */
class CompressionService : Service() {

    private val CHANNEL_ID = "compression_channel"
    private val NOTIFICATION_ID = 2

    private lateinit var membershipManager: MembershipManager
    private var isCancelled = false
    
    private val serviceJob = kotlinx.coroutines.Job()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + serviceJob)

    companion object {
        const val ACTION_STOP = "com.example.unzipfile.ACTION_STOP_COMPRESSION"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        membershipManager = MembershipManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isCancelled = true
            updateNotification(-2, "停止中...")
            return START_NOT_STICKY
        }

        val sourceUriStrings = intent?.getStringArrayListExtra("source_uris")
        val destUriStr = intent?.getStringExtra("dest_uri")
        val password = intent?.getStringExtra("password")
        val compressionLevel = intent?.getIntExtra("compression_level", 5) ?: 5
        val isSplit = intent?.getBooleanExtra("is_split", false) ?: false
        val zipFileName = intent?.getStringExtra("zip_file_name") ?: "archive.zip"

        if (!sourceUriStrings.isNullOrEmpty() && destUriStr != null) {
            val sourceUris = sourceUriStrings.map { Uri.parse(it) }
            val destUri = Uri.parse(destUriStr)

            startForeground(NOTIFICATION_ID, createNotification(0, getString(R.string.preparing)))

            serviceScope.launch {
                performCompression(sourceUris, destUri, zipFileName, password, compressionLevel, isSplit)
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotification(progress: Int, message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.compressing_files))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.compression_service_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun performCompression(
        sourceUris: List<Uri>, 
        destUri: Uri, 
        zipFileName: String,
        password: String?,
        compressionLevel: Int,
        isSplit: Boolean
    ) {
        var success = false
        try {
            // 确认权限
            val fileSizes = sourceUris.mapNotNull { uri ->
                getFileSize(uri)
            }

            // 1. 基础权限检查
            val (allowed, errorMessage) = membershipManager.checkMultipleFilesPermission(fileSizes)
            if (!allowed) {
                updateNotification(-1, errorMessage ?: getString(R.string.permission_denied))
                return
            }

            // 2. 加密功能权限检查
            if (!password.isNullOrBlank()) {
                val (encAllowed, encMessage) = membershipManager.checkEncryptionPermission()
                if (!encAllowed) {
                    updateNotification(-1, encMessage ?: "加密功能仅限会员")
                    return
                }
            }

            // 3. 高级压缩/分卷权限检查
            if (isSplit || compressionLevel > 6) { // 假设 > 6 为高压缩率
                val (advAllowed, advMessage) = membershipManager.checkAdvancedCompressionPermission()
                if (!advAllowed) {
                    updateNotification(-1, advMessage ?: "分卷或高压缩率仅限会员")
                    return
                }
            }

            // 创建临时目录用于处理
            val tempDir = File(cacheDir, "temp_compression_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            val tempZipFile = File(tempDir, zipFileName)
            
            // 配置 Zip4j 参数
            val zipFile = if (isSplit) {
                // 如果是分卷，Zip4j 需要特殊初始化（此处为简单演示，通常分卷需指定分卷大小）
                ZipFile(tempZipFile, password?.toCharArray())
            } else {
                ZipFile(tempZipFile, password?.toCharArray())
            }

            val parameters = ZipParameters().apply {
                if (!password.isNullOrBlank()) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
                
                // 设置压缩率 (Zip4j 级别与系统自带略有不同，映射一下)
                this.compressionLevel = when(compressionLevel) {
                    in 0..2 -> net.lingala.zip4j.model.enums.CompressionLevel.FASTEST
                    in 3..4 -> net.lingala.zip4j.model.enums.CompressionLevel.FAST
                    in 5..7 -> net.lingala.zip4j.model.enums.CompressionLevel.NORMAL
                    else -> net.lingala.zip4j.model.enums.CompressionLevel.ULTRA
                }
            }

            // 执行压缩
            val totalSize = fileSizes.sum()
            var currentProcessedSize = 0L

            sourceUris.forEachIndexed { index, uri ->
                if (isCancelled) return@forEachIndexed
                
                val fileName = getFileName(uri) ?: "file_$index"
                
                // 将输入流转为临时文件，因为 Zip4j 对 Stream 的支持较弱，直接 Add File/Folder 更好
                val entryFile = prepareEntryFile(uri, fileName, tempDir)
                
                if (entryFile.isDirectory) {
                    zipFile.addFolder(entryFile, parameters)
                } else {
                    zipFile.addFile(entryFile, parameters)
                }
                
                currentProcessedSize += getFileSize(uri) ?: 0L
                val progress = if (totalSize > 0) (currentProcessedSize * 100 / totalSize).toInt() else (index + 1) * 100 / sourceUris.size
                updateNotificationThrottled(progress.coerceAtMost(99), "正在压缩: $fileName")
                
                // 压缩完删除 entryFile 临时文件 (如果是从 Uri 拷贝过来的话)
                if (uri.scheme != "file") entryFile.deleteRecursively()
            }
            if (isCancelled) {
                tempZipFile.delete()
                updateNotification(-2, "已停止压缩")
                return
            }

            // 将ZIP文件复制到目标位置
            if (destUri.scheme == "file") {
                val destFile = File(destUri.path ?: "")
                val targetFile = File(destFile, zipFileName)
                tempZipFile.copyTo(targetFile, overwrite = true)
                success = true
            } else {
                val destDocument = DocumentFile.fromTreeUri(this, destUri)
                if (destDocument != null && destDocument.canWrite()) {
                    val zipDoc = destDocument.createFile("application/zip", zipFileName)
                    if (zipDoc != null) {
                        contentResolver.openOutputStream(zipDoc.uri)?.use { output ->
                            tempZipFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        success = true
                    }
                }
            }

            tempDir.deleteRecursively()

        } catch (e: Exception) {
            android.util.Log.e("CompressionService", "Compression error", e)
            updateNotification(-1, e.message ?: getString(R.string.compression_failed))
        } finally {
            if (success) {
                updateNotification(100, getString(R.string.compression_successful), true)
            } else {
                // 如果失败也发送一个完成信号，虽然 progress 为 -1
                updateNotification(-1, "任务失败", true)
            }
            stopForeground(true)
            stopSelf()
        }
    }

    private fun prepareEntryFile(uri: Uri, name: String, tempParent: File): File {
        if (uri.scheme == "file") return File(uri.path ?: "")
        
        val dest = File(tempParent, name)
        if (dest.exists()) dest.deleteRecursively()

        val doc = DocumentFile.fromSingleUri(this, uri)
        if (doc?.isDirectory == true) {
            dest.mkdirs()
            // 简单演示：如果是SAF目录，暂时只支持一级，实际应递归拷贝
            doc.listFiles().forEach { child ->
                contentResolver.openInputStream(child.uri)?.use { input ->
                    File(dest, child.name ?: UUID.randomUUID().toString()).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } else {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return dest
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

    private var lastUpdateTime = 0L
    private fun updateNotificationThrottled(progress: Int, message: String) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > 300) { // Every 300ms
            updateNotification(progress, message)
            lastUpdateTime = now
        }
    }

    private fun updateNotification(progress: Int, message: String, complete: Boolean = false) {
        val cappedProgress = progress.coerceIn(-2, 100)
        val notification = createNotification(cappedProgress, message)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)

        // 发送广播到UI
        val intent = Intent("COMPRESSION_PROGRESS").apply {
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
