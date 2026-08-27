package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameRules;

/**
 * 服务器全局状态客户端缓存（2026-08-27）：全局技能（寰宇法则）的当前服务器状态提示数据。
 * <ul>
 *   <li>AE 频道模式码（服务端 GlobalStateS2CPacket 同步；0=默认 1=X2 2=X3 3=X4 4=无限）</li>
 *   <li>时之环/晴空环状态：客户端直接读本地 gamerule 副本（零网络开销，性能最优）</li>
 * </ul>
 */
public final class ClientGlobalState {
    private ClientGlobalState() {
    }

    /** AE2 频道模式码（-1 = 未知/未装 AE2；0=默认 1=X2 2=X3 3=X4 4=无限） */
    private static volatile int aeChannelMode = -1;

    public static void setAeChannelMode(int mode) {
        aeChannelMode = mode;
    }

    /** AE 频道模式文字（tooltip 显示用） */
    public static String getAeChannelModeText() {
        return switch (aeChannelMode) {
            case 0 -> "默认（原版 8 频道）";
            case 1 -> "2 倍频道（X2）";
            case 2 -> "3 倍频道（X3）";
            case 3 -> "4 倍频道（X4）";
            case 4 -> "无限频道（INFINITE）";
            default -> "未知（未装 AE2 或未同步）";
        };
    }

    /** 时之环：服务器时间是否已锁定（doDaylightCycle=false = 锁定中；客户端本地读取零开销） */
    public static boolean isTimeLocked() {
        var level = Minecraft.getInstance().level;
        return level != null && !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
    }

    /** 晴空环：服务器天气是否已锁定（doWeatherCycle=false = 锁定中） */
    public static boolean isWeatherLocked() {
        var level = Minecraft.getInstance().level;
        return level != null && !level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
    }
}
