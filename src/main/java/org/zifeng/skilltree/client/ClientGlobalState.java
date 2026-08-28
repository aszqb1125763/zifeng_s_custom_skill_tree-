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
    /** 晴空环全局天气模式码（-1 = 未知；0=晴 1=雨 2=雷暴；2026-08-28 随 GlobalStateS2CPacket 同步） */
    private static volatile int weatherMode = -1;
    /** 时间锁定状态（-1 = 未知；0=未锁 1=锁定；2026-08-28 随 GlobalStateS2CPacket 同步） */
    private static volatile int timeLocked = -1;

    public static void setAeChannelMode(int mode) {
        aeChannelMode = mode;
    }

    public static void setWeatherMode(int mode) {
        weatherMode = mode;
    }

    public static void setTimeLocked(boolean locked) {
        timeLocked = locked ? 1 : 0;
    }

    /** 断开连接重置全部缓存（2026-08-28：防跨服残留——上次服务器的 AE/天气/时间状态不能带到下个服务器） */
    public static void reset() {
        aeChannelMode = -1;
        weatherMode = -1;
        timeLocked = -1;
    }

    /** 服务器当前晴空环天气模式码（-1 未知时回退本地缓存） */
    public static int getWeatherMode() {
        return weatherMode;
    }

    /** 服务器当前天气模式文字 */
    public static String getWeatherModeText() {
        return switch (weatherMode) {
            case 1 -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.weather_rain").getString();
            case 2 -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.weather_thunder").getString();
            case 0 -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.weather_sunny").getString();
            default -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.weather_unknown").getString();
        };
    }

    /** AE 频道模式文字（tooltip 显示用） */
    public static String getAeChannelModeText() {
        return switch (aeChannelMode) {
            case 0 -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.ae_default").getString();
            case 1 -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.ae_x2").getString();
            case 2 -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.ae_x3").getString();
            case 3 -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.ae_x4").getString();
            case 4 -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.ae_infinite").getString();
            default -> net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.ae_unknown").getString();
        };
    }

    /** 时之环：服务器时间是否已锁定（优先推送值；未同步时本地读 gamerule 零开销） */
    public static boolean isTimeLocked() {
        if (timeLocked >= 0) {
            return timeLocked == 1;
        }
        var level = Minecraft.getInstance().level;
        return level != null && !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
    }

    /** 晴空环：服务器天气是否已锁定（doWeatherCycle=false = 锁定中） */
    public static boolean isWeatherLocked() {
        var level = Minecraft.getInstance().level;
        return level != null && !level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
    }
}
