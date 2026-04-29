package com.example.unzipfile

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.os.Environment
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.unzipfile.databinding.ActivityMainBinding
import com.example.unzipfile.membership.MembershipManager
import com.example.unzipfile.membership.MembershipTier
import com.example.unzipfile.membership.*
import com.example.unzipfile.PromotionActivity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Handler
import android.os.Looper
import com.example.unzipfile.picker.FilePickerActivity
import java.io.File
import androidx.recyclerview.widget.LinearLayoutManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedSourceUris = mutableListOf<Uri>()
    private var destUri: Uri? = null
    private lateinit var selectedFilesAdapter: SelectedFilesAdapter
    private lateinit var membershipManager: MembershipManager
    private val affiliateManager by lazy { AffiliateManager.getInstance(this) }
    
    // Operation mode: true = extract, false = compress
    private var isExtractMode = true

    private val customFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val paths = result.data?.getStringArrayListExtra(FilePickerActivity.EXTRA_SELECTED_PATHS)
            if (paths != null) {
                paths.forEach { path ->
                    val uri = Uri.fromFile(File(path))
                    if (!selectedSourceUris.contains(uri)) {
                        selectedSourceUris.add(uri)
                    }
                }
                updateFileSelectionDisplay()
            }
        }
    }

    private val customDestPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val paths = result.data?.getStringArrayListExtra(FilePickerActivity.EXTRA_SELECTED_PATHS)
            if (!paths.isNullOrEmpty()) {
                val path = paths[0]
                destUri = Uri.fromFile(File(path))
                binding.tvDestPath.text = destUri?.path
                saveDestDirectory(path) // 保存常用目录
            }
        }
    }

    private val selectDestLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            destUri = it
            binding.tvDestPath.text = it.lastPathSegment ?: it.path
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private var progressDialog: androidx.appcompat.app.AlertDialog? = null
    private var tvDialogTitle: android.widget.TextView? = null
    private var tvDialogStatus: android.widget.TextView? = null
    private var tvDialogPercent: android.widget.TextView? = null
    private var tvDialogCurrentFile: android.widget.TextView? = null
    private var dialogProgressIndicator: com.google.android.material.progressindicator.LinearProgressIndicator? = null
    private var btnDialogCancel: android.widget.Button? = null

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra("progress", 0) ?: 0
            val message = intent?.getStringExtra("message") ?: ""
            val isComplete = intent?.getBooleanExtra("complete", false) ?: false
            
            if (progressDialog?.isShowing == true) {
                dialogProgressIndicator?.progress = progress
                tvDialogPercent?.text = "$progress%"
                tvDialogCurrentFile?.text = message
                
                if (isComplete) {
                    binding.btnStart.isEnabled = true
                    progressDialog?.dismiss()
                    
                    val finalMessage = if (progress == -1) "任务执行失败" else message
                    showCompletionDialog(finalMessage)
                    
                    updateMembershipDisplay()
                } else {
                    tvDialogStatus?.text = "处理中..."
                    btnDialogCancel?.text = "取消任务"
                    btnDialogCancel?.setOnClickListener {
                        val serviceClass = if (isExtractMode) UnzipService::class.java else CompressionService::class.java
                        val stopAction = if (isExtractMode) UnzipService.ACTION_STOP else CompressionService.ACTION_STOP
                        val intent = Intent(this@MainActivity, serviceClass).apply { action = stopAction }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                        progressDialog?.dismiss()
                    }
                }
            }
        }
    }


    private fun showCompletionDialog(message: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("任务完成")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("打开目录") { _, _ ->
                openOutputDirectory()
            }
            .setNegativeButton("关闭", null)
            .create()
        dialog.show()
    }

    private fun openOutputDirectory() {
        destUri?.let { uri ->
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    val authority = "${packageName}.fileprovider"
                    val contentUri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
                    intent.setDataAndType(contentUri, "resource/folder")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    // SAF Tree URI
                    intent.setDataAndType(uri, "resource/folder")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                // 尝试多种 MIME 类型以提高兼容性
                val mimeTypes = arrayOf("resource/folder", "vnd.android.document/directory")
                var success = false
                for (mime in mimeTypes) {
                    try {
                        intent.type = mime
                        startActivity(intent)
                        success = true
                        break
                    } catch (e: Exception) {}
                }
                
                // 兜底方案：通用查看
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
                fallbackIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(fallbackIntent)
                
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开目录: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProgressDialog() {
        if (progressDialog?.isShowing == true) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
        tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle)
        tvDialogStatus = dialogView.findViewById(R.id.tvDialogStatus)
        tvDialogPercent = dialogView.findViewById(R.id.tvDialogPercent)
        tvDialogCurrentFile = dialogView.findViewById(R.id.tvDialogCurrentFile)
        dialogProgressIndicator = dialogView.findViewById(R.id.dialogProgressIndicator)
        btnDialogCancel = dialogView.findViewById(R.id.btnDialogCancel)
        btnDialogCancel?.setOnClickListener {
            val serviceClass = if (isExtractMode) UnzipService::class.java else CompressionService::class.java
            val stopAction = if (isExtractMode) UnzipService.ACTION_STOP else CompressionService.ACTION_STOP
            val intent = Intent(this, serviceClass).apply { action = stopAction }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            progressDialog?.dismiss()
        }
        progressDialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
        progressDialog?.show()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        com.example.unzipfile.utils.UmengHelper.preInit(this)
        
        if (com.example.unzipfile.utils.ComplianceManager.hasAgreedPrivacy(this)) {
            initAppLogic()
        } else {
            showPrivacyDialog()
        }
    }

    private fun showPrivacyDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_privacy, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<android.view.View>(R.id.btnAgree).setOnClickListener {
            com.example.unzipfile.utils.ComplianceManager.setAgreedPrivacy(this)
            dialog.dismiss()
            initAppLogic()
        }

        dialogView.findViewById<android.view.View>(R.id.btnDisagree).setOnClickListener {
            finish()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun initAppLogic() {
        com.example.unzipfile.utils.UmengHelper.init(this)
        membershipManager = MembershipManager.getInstance(this)
        
        // 加载上次保存的解压/压缩常用目录
        val savedDest = getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("last_dest_path", "")
        if (!savedDest.isNullOrEmpty()) {
            destUri = Uri.fromFile(File(savedDest))
            binding.tvDestPath.text = destUri?.path
        }
        
        com.example.unzipfile.utils.UmengHelper.handleDeepLink(this, intent) { inviteCode ->
            lifecycleScope.launch {
                val deviceId = com.example.unzipfile.utils.DeviceUtils.getDeviceFingerprint(this@MainActivity)
                val success = CommissionManager.getInstance(this@MainActivity)
                    .processInvitationSuccess(inviteCode, "me", deviceId)
                
                if (success) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "检测到好友推荐，已为您发放奖励！", Toast.LENGTH_LONG).show()
                        updateMembershipDisplay()
                    }
                }
            }
        }

        val unzipFilter = IntentFilter("UNZIP_PROGRESS")
        val compressionFilter = IntentFilter("COMPRESSION_PROGRESS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(progressReceiver, unzipFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(progressReceiver, compressionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(progressReceiver, unzipFilter)
            registerReceiver(progressReceiver, compressionFilter)
        }

        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isExtractMode = checkedId == R.id.btnModeExtract
                updateModeUI()
            }
        }

        binding.btnNavRewards.setOnClickListener { showPromotionHub() }
        binding.btnUpgrade.setOnClickListener { showPromotionHub() }

        // 隐藏入口：连续点击 5 次 "MEMBERSHIP" 文案激活管理员认证
        var adminClickCount = 0
        findViewById<View>(R.id.cardMembership)?.findViewById<View>(android.R.id.text1)?.setOnClickListener {
            // 注意：因为布局里没有直接给这个TextView赋ID，我稍后会在XML里给它加个ID
        }
        // 为了方便，我们直接给那个文案加个点击监听
        binding.root.findViewById<android.widget.TextView>(R.id.tvMemberLabel)?.setOnClickListener {
            adminClickCount++
            if (adminClickCount >= 5) {
                adminClickCount = 0
                showAdminActivationDialog()
            }
        }

        binding.btnSelectFile.setOnClickListener {
            if (checkStoragePermission()) {
                val intent = Intent(this, FilePickerActivity::class.java).apply {
                    putExtra(FilePickerActivity.EXTRA_MULTI_SELECT, true)
                    if (isExtractMode) {
                        putExtra(FilePickerActivity.EXTRA_SELECT_TYPE, FilePickerActivity.SELECT_TYPE_FILE)
                        putStringArrayListExtra(FilePickerActivity.EXTRA_ALLOWED_EXTENSIONS, 
                            arrayListOf(".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz", ".tgz"))
                    } else {
                        putExtra(FilePickerActivity.EXTRA_SELECT_TYPE, FilePickerActivity.SELECT_TYPE_BOTH)
                    }
                }
                customFilePickerLauncher.launch(intent)
            }
        }

        binding.btnSelectDest.setOnClickListener {
            if (checkStoragePermission()) {
                // 打开文件夹选择器，起始位置为上次保存的目录
                val startPath = destUri?.path ?: Environment.getExternalStorageDirectory().absolutePath
                val intent = Intent(this, FilePickerActivity::class.java).apply {
                    putExtra(FilePickerActivity.EXTRA_MULTI_SELECT, false)
                    putExtra(FilePickerActivity.EXTRA_SELECT_TYPE, FilePickerActivity.SELECT_TYPE_FOLDER)
                    putExtra(FilePickerActivity.EXTRA_START_PATH, startPath)
                }
                customDestPickerLauncher.launch(intent)
            }
        }

        binding.btnStart.setOnClickListener {
            if (selectedSourceUris.isEmpty() || destUri == null) {
                Toast.makeText(this, getString(R.string.select_hint), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.btnStart.isEnabled = false
            if (isExtractMode) startExtraction() else startCompression()
        }

        updateModeUI()
        updateMembershipDisplay()
        setupSelectedFilesList()
        checkVersionUpdate()
    }

    private fun saveDestDirectory(path: String) {
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("last_dest_path", path)
            .apply()
    }

    private fun checkVersionUpdate() {
        lifecycleScope.launch {
            val config = affiliateManager.getAppConfig("unzip-pro") ?: return@launch
            val updateConfig = config.optJSONObject("update_config") ?: return@launch
            
            val latestVersion = updateConfig.optString("latest_version")
            val currentVersion = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }

            if (latestVersion > currentVersion) {
                showUpdateDialog(latestVersion, updateConfig.optString("update_msg"), config.optString("link"), updateConfig.optBoolean("force_update", false))
            }
        }
    }

    private fun showUpdateDialog(version: String, msg: String, url: String, isForce: Boolean) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("发现新版本 v$version")
            .setMessage(msg)
            .setCancelable(!isForce)
            .setPositiveButton("立即更新") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {}
                if (isForce) finish() 
            }
            .apply { if (!isForce) setNegativeButton("以后再说", null) }
            .show()
    }

    private fun updateMembershipDisplay() {
        if (!::membershipManager.isInitialized) return
        val isVip = membershipManager.isMembershipActive()
        val tier = membershipManager.getCurrentTier()
        
        binding.tvMembershipStatus.text = when {
            tier == MembershipTier.ADMIN -> "🛡️ 首席管理员"
            isVip -> "✨ 尊享会员版 (${membershipManager.getFormattedRemainingTime()})"
            else -> "🔓 免费标准版"
        }
        
        binding.tvPermissionDesc.text = tier.getPermissionDescription()
        
        // 动态切换尊贵皮肤
        if (isVip || tier == MembershipTier.ADMIN) {
            binding.rlMemberCardContent.setBackgroundResource(R.drawable.bg_vip_noble)
            binding.ivMemberIcon.setImageResource(R.drawable.ic_crown)
            binding.ivMemberIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D4AF37"))
            binding.btnUpgrade.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D4AF37"))
            binding.btnUpgrade.text = "续费专属权益"
            binding.tvMemberLabel.text = "SUPREME PRIVILEGE"
            binding.tvMemberLabel.setTextColor(android.graphics.Color.parseColor("#B3C5A059"))
        } else {
            binding.rlMemberCardContent.setBackgroundResource(R.drawable.gradient_premium)
            binding.ivMemberIcon.setImageResource(android.R.drawable.ic_menu_info_details)
            binding.ivMemberIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            binding.btnUpgrade.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            binding.btnUpgrade.text = "领取免费会员"
            binding.tvMemberLabel.text = "FREE MEMBERSHIP"
            binding.tvMemberLabel.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"))
        }
    }

    private fun updateModeUI() {
        binding.tvConfigTitle.text = if (isExtractMode) "选择要解压的文件" else "选择要压缩的文件/文件夹"
        binding.btnStart.text = if (isExtractMode) "开始解压" else "开始压缩"
        
        // 解压模式隐藏整个高级选项卡片
        binding.cardAdvancedOptions.visibility = if (isExtractMode) View.GONE else View.VISIBLE
        binding.llCompressionSettings.visibility = if (isExtractMode) View.GONE else View.VISIBLE
        
        val tier = membershipManager.getCurrentTier()
        val isMemberOrAdmin = tier == MembershipTier.PREMIUM || tier == MembershipTier.ADMIN
        binding.tilPassword.isEnabled = isMemberOrAdmin
        binding.sliderCompressionLevel.isEnabled = isMemberOrAdmin
        
        if (!isMemberOrAdmin) {
            binding.etPassword.setText("")
            binding.etPassword.hint = "密码保护 (仅限会员)"
        } else {
            binding.etPassword.hint = "设置保护密码 (选填)"
        }
        
        selectedSourceUris.clear()
        updateFileSelectionDisplay()
    }

    private fun updateFileSelectionDisplay() {
        if (selectedSourceUris.isNotEmpty()) {
            binding.rvSelectedFiles.visibility = View.VISIBLE
            binding.tvFilePath.visibility = View.GONE
            binding.rvSelectedFiles.adapter = SelectedFilesAdapter(selectedSourceUris.map { it.path ?: "" }.toMutableList()) { position ->
                selectedSourceUris.removeAt(position)
                updateFileSelectionDisplay()
            }
        } else {
            binding.rvSelectedFiles.visibility = View.GONE
            binding.tvFilePath.visibility = View.VISIBLE
            binding.tvFilePath.text = "尚未选择任何源文件"
        }
    }

    private fun showAdminActivationDialog() {
        val input = android.widget.EditText(this)
        input.hint = "输入授权密钥"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("管理员认证")
            .setMessage("请输入开发者专属密钥以解锁最高权限：")
            .setView(input)
            .setPositiveButton("认证") { _, _ ->
                val key = input.text.toString()
                if (membershipManager.activateAdminMode(key)) {
                    updateMembershipDisplay()
                    Toast.makeText(this, "认证成功！欢迎回来，首席管理员", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "密钥无效", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPromotionHub() {
        startActivity(Intent(this, PromotionActivity::class.java))
    }

    private fun setupSelectedFilesList() {
        binding.rvSelectedFiles.layoutManager = LinearLayoutManager(this)
    }

    private fun startExtraction() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 1. 会员权限预检
            val fileSizes = selectedSourceUris.map { uri ->
                val file = File(uri.path ?: "")
                if (file.exists()) file.length() else 0L
            }
            
            val (allowed, message) = membershipManager.checkMultipleFilesPermission(fileSizes)
            if (!allowed) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, message ?: "会员权限不足", Toast.LENGTH_LONG).show()
                    binding.btnStart.isEnabled = true
                }
                return@launch
            }

            // 2. 格式权限检查
            for (uri in selectedSourceUris) {
                val fileName = uri.path ?: ""
                val (formatAllowed, formatMessage) = membershipManager.checkUnzipFormatPermission(fileName)
                if (!formatAllowed) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, formatMessage ?: "该格式仅限会员解压", Toast.LENGTH_LONG).show()
                        binding.btnStart.isEnabled = true
                    }
                    return@launch
                }
            }

            // 3. 检查是否加密
            var needsPassword = false
            for (uri in selectedSourceUris) { if (isArchiveEncrypted(uri)) { needsPassword = true; break } }
            
            withContext(Dispatchers.Main) {
                if (needsPassword) {
                    val (encAllowed, encMsg) = membershipManager.checkEncryptionPermission()
                    if (!encAllowed) {
                        Toast.makeText(this@MainActivity, encMsg, Toast.LENGTH_LONG).show()
                        binding.btnStart.isEnabled = true
                    } else {
                        showPasswordPromptDialog { startUnzipService(it) }
                    }
                } else {
                    startUnzipService(null)
                }
            }
        }
    }

    private fun startUnzipService(password: String?) {
        showProgressDialog()
        val intent = Intent(this, UnzipService::class.java).apply {
            putStringArrayListExtra("source_uris", ArrayList(selectedSourceUris.map { it.toString() }))
            putExtra("dest_uri", destUri.toString()); putExtra("password", password)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun startCompression() {
        // 1. 会员权限预检
        val fileSizes = selectedSourceUris.map { uri ->
            val file = File(uri.path ?: "")
            if (file.exists()) file.length() else 0L
        }
        
        val (allowed, message) = membershipManager.checkMultipleFilesPermission(fileSizes)
        if (!allowed) {
            Toast.makeText(this, message ?: "会员权限不足", Toast.LENGTH_LONG).show()
            binding.btnStart.isEnabled = true
            return
        }

        // 2. 高级功能预检 (密码保护、分卷压缩等)
        val password = binding.etPassword.text.toString()
        val compressionLevel = binding.sliderCompressionLevel.value.toInt()
        
        if (password.isNotEmpty() || compressionLevel > 1) {
            val (advAllowed, advMsg) = membershipManager.checkAdvancedCompressionPermission()
            if (!advAllowed) {
                Toast.makeText(this, advMsg, Toast.LENGTH_LONG).show()
                binding.btnStart.isEnabled = true
                return
            }
        }

        showProgressDialog()
        val intent = Intent(this, CompressionService::class.java).apply {
            putStringArrayListExtra("source_uris", ArrayList(selectedSourceUris.map { it.toString() }))
            putExtra("dest_uri", destUri.toString())
            putExtra("zip_file_name", "archive_${System.currentTimeMillis()}.zip")
            putExtra("password", password)
            putExtra("compression_level", compressionLevel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun isArchiveEncrypted(uri: Uri): Boolean {
        val path = uri.path ?: return false
        val file = File(path)
        return try { net.lingala.zip4j.ZipFile(file).isEncrypted } catch (e: Exception) { false }
    }

    private fun showPasswordPromptDialog(onPasswordEntered: (String) -> Unit) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("加密档案").setMessage("请输入解压密码").setView(input)
            .setPositiveButton("确定") { _, _ -> onPasswordEntered(input.text.toString()) }.show()
    }

    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            return false
        }
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let {
            com.example.unzipfile.utils.UmengHelper.handleDeepLink(this, it) { inviteCode ->
                // 重复唤起时的领奖逻辑
                lifecycleScope.launch {
                    val deviceId = com.example.unzipfile.utils.DeviceUtils.getDeviceFingerprint(this@MainActivity)
                    CommissionManager.getInstance(this@MainActivity).processInvitationSuccess(inviteCode, "me", deviceId)
                    updateMembershipDisplay()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::membershipManager.isInitialized) updateMembershipDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(progressReceiver)
        } catch (e: Exception) {}
        progressDialog?.dismiss()
        progressDialog = null
    }
}
