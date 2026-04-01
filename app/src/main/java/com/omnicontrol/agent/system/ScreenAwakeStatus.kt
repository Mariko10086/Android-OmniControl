package com.omnicontrol.agent.system

/**
 * 屏幕常亮 / 锁屏状态快照，供 Dashboard UI 展示。
 */
data class ScreenAwakeStatus(
    /** Settings.System.SCREEN_OFF_TIMEOUT 当前值（毫秒），-1 表示读取失败 */
    val screenTimeoutMs: Long,
    /** 是否已通过系统设置禁用锁屏 */
    val lockScreenDisabled: Boolean
)
