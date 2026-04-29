package com.example.unzipfile.membership

/**
 * 项目调试全局配置
 * [MANDATORY] 在项目完成时，请将 IS_DEBUG_MODE 设置为 false，并移除所有测试逻辑。
 */
object DebugConfig {
    /**
     * 是否开启开发测试模式
     * 开启后：
     * 1. 自动解锁所有会员功能 (不限大小、不限格式、批量操作、加密压缩)
     * 2. UI 界面会显示 【测试环境】 标记
     * 3. 模拟支付逻辑会跳过实际扣费
     */
    const val IS_DEBUG_MODE = true

    /**
     * 获取带标记的文本
     */
    fun getMarkedText(original: String): String {
        return if (IS_DEBUG_MODE) "【测试版】$original" else original
    }
}
