package com.msop.player.feature.player

/**
 * 播放模式。
 *
 * 与 ExoPlayer 的 repeat/shuffle 两个开关的映射：
 * - [SEQUENTIAL] `repeat=OFF, shuffle=false` —— 顺着队列播完就停
 * - [REPEAT_ONE] `repeat=ONE, shuffle=false` —— 单曲循环
 * - [SHUFFLE]    `repeat=ALL, shuffle=true`  —— 随机但不重复，播完一轮继续
 *
 * 「列表循环」没做成单独一档：自用场景下随机播放基本都要循环，
 * 真要顺序循环时用 [SEQUENTIAL] 手动重播即可。
 */
enum class PlayMode {
    SEQUENTIAL,
    REPEAT_ONE,
    SHUFFLE,
}
