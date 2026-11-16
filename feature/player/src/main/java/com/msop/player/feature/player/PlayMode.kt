package com.msop.player.feature.player

/**
 * 播放模式。
 *
 * 与 ExoPlayer 的 repeat/shuffle 两个开关的映射：
 * - [SEQUENTIAL] `repeat=OFF, shuffle=false` —— 顺序播完就停（默认）
 * - [REPEAT_ALL] `repeat=ALL, shuffle=false` —— 列表循环
 * - [REPEAT_ONE] `repeat=ONE, shuffle=false` —— 单曲循环
 * - [SHUFFLE]    `repeat=ALL, shuffle=true`  —— 随机播放
 *
 * 2026-11-15（用户指令）：播放页左下角改成「点开扇形菜单直接选」，
 * 四档都要能直接点到，所以补上了此前没有的「列表循环」。
 */
enum class PlayMode {
    SEQUENTIAL,
    REPEAT_ALL,
    REPEAT_ONE,
    SHUFFLE,
}
