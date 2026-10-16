package org.zifeng.skilltree.client;

import org.zifeng.skilltree.skill.Skills;

/**
 * 木棍工具 6 模式统一 UI 映射（2026-09-08）：
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

    /** 模式 → 显示色：BIND=紫 / RANGE=蓝 / 放置=金黄 / 挖掘=青 / 攻击=红 / 防护=绿 / 标记=紫罗兰 / 挖掘防护=钢青 */
    public static int colorOfMode(int mode) {
        return switch (mode) {
            case Skills.STICK_MODE_ZONE_PLACE -> 0xFFFFD24A;    // 放置 金黄
            case Skills.STICK_MODE_ZONE_EXCAVATE -> 0xFF55FFFF; // 挖掘 青
            case Skills.STICK_MODE_ZONE_ATTACK -> 0xFFFF5555;   // 攻击 红
            case Skills.STICK_MODE_ZONE_PROTECT -> 0xFF55FF55;  // 防护 绿
            case Skills.STICK_MODE_RANGE -> 0xFF66CCFF;         // RANGE 蓝
            case Skills.STICK_MODE_MARK -> 0xFFCC88FF;          // 标记 紫罗兰（★ 2026-10-11）
            case Skills.STICK_MODE_MINE_PROTECT -> 0xFF5FD7D7;  // 挖掘防护 钢青（★ 2026-10-11）
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
            case Skills.STICK_MODE_MARK -> "mark"; // ★ 2026-10-11 方块标记
            case Skills.STICK_MODE_MINE_PROTECT -> "mineprotect"; // ★ 2026-10-11 方块保护
            default -> "bind";
        };
    }

    /**
     * 模式 → 图标物品（★ 2026-10-11：HUD 在模式名前显示对应图标，一眼认出当前功能）。
     * <p>用具名物品图标而不是新增贴图 —— 免美术资源、任意资源包下都能显示。
     */
    public static net.minecraft.world.item.Item iconOfMode(int mode) {
        return switch (mode) {
            case Skills.STICK_MODE_RANGE -> net.minecraft.world.item.Items.LODESTONE;            // 磁铁屏蔽区
            case Skills.STICK_MODE_ZONE_PLACE -> net.minecraft.world.item.Items.BRICK;           // 选区放置
            case Skills.STICK_MODE_ZONE_EXCAVATE -> net.minecraft.world.item.Items.IRON_PICKAXE; // 选区挖掘
            case Skills.STICK_MODE_ZONE_ATTACK -> net.minecraft.world.item.Items.IRON_SWORD;     // 选区攻击
            case Skills.STICK_MODE_ZONE_PROTECT -> net.minecraft.world.item.Items.SHIELD;        // 防护选区
            case Skills.STICK_MODE_MARK -> net.minecraft.world.item.Items.SPYGLASS;              // 方块标记
            case Skills.STICK_MODE_MINE_PROTECT -> net.minecraft.world.item.Items.BEDROCK;       // 方块保护
            default -> net.minecraft.world.item.Items.CHEST;                                     // 容器绑定
        };
    }
}

