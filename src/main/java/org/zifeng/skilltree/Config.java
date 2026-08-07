package org.zifeng.skilltree;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 通用配置（COMMON，可在游戏内热重载）。
 */
public class Config {
    public static final ModConfigSpec SPEC;

    /** 每转换 1 点技能点需要消耗的能量（FE），默认 1 亿 */
    public static final ModConfigSpec.LongValue ENERGY_PER_SKILL_POINT;

    /** 技能树界面背景色（淡灰，ARGB） */
    public static final ModConfigSpec.IntValue SKILL_TREE_BACKGROUND_COLOR;

    /** 技能树界面边框色（淡蓝，ARGB） */
    public static final ModConfigSpec.IntValue SKILL_TREE_BORDER_COLOR;

    /** 机器界面进度条颜色（星辉蓝，ARGB） */
    public static final ModConfigSpec.IntValue MACHINE_PROGRESS_COLOR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("星能转换机：每消耗多少 FE 能量转换 1 点技能点（1 亿 = 100000000）")
                .push("machine");
        ENERGY_PER_SKILL_POINT = builder
                .comment("每 1 点技能点所需能量（FE）。进度中断（停止输入能量）会清空重算")
                .defineInRange("energyPerSkillPoint", 100_000_000L, 1L, Long.MAX_VALUE);
        MACHINE_PROGRESS_COLOR = builder
                .comment("机器界面进度条颜色（ARGB，默认星辉蓝 0xFF4FC3F7）")
                .defineInRange("machineProgressColor", 0xFF4FC3F7, Integer.MIN_VALUE, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("技能树界面样式").push("skillTree");
        SKILL_TREE_BACKGROUND_COLOR = builder
                .comment("技能树界面背景色（ARGB，淡灰色 0xFFBEBEBE，必须完全不透明否则文字被底层叠加变色）")
                .defineInRange("skillTreeBackgroundColor", 0xFFBEBEBE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        SKILL_TREE_BORDER_COLOR = builder
                .comment("技能树界面边框色（ARGB，淡蓝色 0xFF87CEEB）")
                .defineInRange("skillTreeBorderColor", 0xFF87CEEB, Integer.MIN_VALUE, Integer.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }
}
