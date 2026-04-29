package com.example.unzipfile.membership

/**
 * 会员等级枚举
 * 定义不同会员等级及其权限配置
 */
enum class MembershipTier(
    val displayName: String,
    val maxFileSizeMB: Long,  // 单个文件最大大小（MB），-1表示无限制
    val allowBatchOperation: Boolean,  // 是否允许批量操作
    val maxBatchCount: Int,  // 批量操作最大文件数，-1表示无限制
    val supportedFormats: List<String>, // 支持的格式
    val allowEncryption: Boolean, // 是否支持加密/解密
    val hasAds: Boolean, // 是否显示广告
    val allowAdvancedCompression: Boolean, // 是否支持高压缩率、分卷、密码保护
    val commissionRateBonus: Double // 推广佣金加成 (0.1 = 10%)
) {
    /**
     * 免费用户
     * - 单文件处理 ≤ 50MB
     * - 不支持批量操作
     * - 支持常见格式 (ZIP, RAR)
     * - 有广告
     */
    FREE(
        displayName = "免费版",
        maxFileSizeMB = 50,
        allowBatchOperation = false,
        maxBatchCount = 1,
        supportedFormats = listOf(".zip", ".rar"),
        allowEncryption = false,
        hasAds = true,
        allowAdvancedCompression = false,
        commissionRateBonus = 0.0
    ),
    
    /**
     * 会员用户
     * - 无文件大小限制
     * - 支持批量操作
     * - 支持所有格式
     * - 无广告
     * - 支持加密、分卷、高压缩率
     */
    PREMIUM(
        displayName = "会员版",
        maxFileSizeMB = -1,
        allowBatchOperation = true,
        maxBatchCount = -1,
        supportedFormats = listOf(".zip", ".rar", ".rar5", ".7z", ".iso", ".tar", ".gz", ".bz2", ".xz"),
        allowEncryption = true,
        hasAds = false,
        allowAdvancedCompression = true,
        commissionRateBonus = 0.1
    ),

    /**
     * 管理员权限 (专属)
     * - 拥有最高权限，无任何限制
     */
    ADMIN(
        displayName = "管理员控制台",
        maxFileSizeMB = -1,
        allowBatchOperation = true,
        maxBatchCount = -1,
        supportedFormats = listOf(".zip", ".rar", ".rar5", ".7z", ".iso", ".tar", ".gz", ".bz2", ".xz"),
        allowEncryption = true,
        hasAds = false,
        allowAdvancedCompression = true,
        commissionRateBonus = 0.25
    );
    
    /**
     * 检查文件大小是否在权限范围内
     */
    fun isFileSizeAllowed(fileSizeBytes: Long): Boolean {
        if (maxFileSizeMB == -1L) return true
        val fileSizeMB = fileSizeBytes / (1024 * 1024)
        return fileSizeMB <= maxFileSizeMB
    }
    
    /**
     * 检查批量操作文件数是否在权限范围内
     */
    fun isBatchCountAllowed(fileCount: Int): Boolean {
        if (!allowBatchOperation && fileCount > 1) return false
        if (maxBatchCount == -1) return true
        return fileCount <= maxBatchCount
    }

    /**
     * 检查文件格式是否受支持
     */
    fun isFormatSupported(fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        return supportedFormats.any { lowerName.endsWith(it) }
    }
    
    /**
     * 获取权限描述文本
     */
    fun getPermissionDescription(): String {
        return when (this) {
            FREE -> "常见格式解压，单文件 ≤ 50MB\n不支持批量、加密、高级压缩"
            PREMIUM -> "支持全格式、批量解压、加密分卷\n纯净无广告，极速处理"
            ADMIN -> "最高控制权限：无视所有规则限制\n系统级解压核心已开启"
        }
    }
}
