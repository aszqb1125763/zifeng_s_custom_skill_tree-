package org.zifeng.skilltree.client;

import org.zifeng.skilltree.skill.Skills;

/**
 * 木棍工具 6 模式统一 UI 映射（2026-09-08，1.20.1）：
 * 工具卡/信息栏/选区提示全部读这里，禁止散落 BIND/RANGE 二元特判。
 * lang 片段 = ui.zifeng_s_custom_skill_tree.<mode|hint|line|mod>_xxx。
 */
public final class StickToolModes {
    private StickToolModes() {
    }

    /** 模式 → 名称 lang 片段（stick_tool_mode_*） */
    public static String modeLang(int mode) {
        return "stick_tool_mode_" + suffix(mode);
    }

    /** 模式 → 操作提示 lang 片段（stick_tool_hint_*） */
    public static String hintLang(int mode) {
        return "stick_tool_hint_" + suffix(mode);
    }

    /** 模式 → 模块说明 lang 片段（stick_tool_mod_*） */
    public static String modLang(int mode) {
        return "stick_tool_mod_" + suffix(mode);
    }

    /** 模式 → 工具卡第 3 行说明 lang 片段（stick_tool_line_*） */
    public static String lineLang(int mode) {
        return "stick_tool_line_" + suffix(mode);
    }

    /** 模式 → 显示色：BIND=紫 / RANGE=蓝 / 放置=金黄 / 挖掘=青 / 攻击=红 / 防护=绿 */
    public static int colorOfMode(int mode) {
        return switch (mode) {
            case Skills.STICK_MODE_ZONE_PLACE -> 0xFFFFD24A;    // 放置 金黄
            case Skills.STICK_MODE_ZONE_EXCAVATE -> 0xFF55FFFF; // 挖掘 青
            case Skills.STICK_MODE_ZONE_ATTACK -> 0xFFFF5555;   // 攻击 红
            case Skills.STICK_MODE_ZONE_PROTECT -> 0xFF55FF55;  // 防护 绿
            case Skills.STICK_MODE_RANGE -> 0xFF66CCFF;         // RANGE 蓝
            default -> 0xFFBB77FF;                              // BIND 紫
        };
    }

    /** 模式 → lang 后缀 */
    private static String suffix(int mode) {
        return switch (mode) {
            case Skills.STICK_MODE_ZONE_PLACE -> "place";
            case Skills.STICK_MODE_ZONE_EXCAVATE -> "excavate";
            case Skills.STICK_MODE_ZONE_ATTACK -> "attack";
            case Skills.STICK_MODE_ZONE_PROTECT -> "protect";
            case Skills.STICK_MODE_RANGE -> "range";
            default -> "bind";
        };
    }
}
