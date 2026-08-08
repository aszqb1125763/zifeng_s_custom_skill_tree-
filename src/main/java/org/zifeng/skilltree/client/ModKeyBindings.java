package org.zifeng.skilltree.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 快捷键注册（MOD 总线）：N 打开技能树；K 切换杀戮光环；L 循环光环目标模式。
 * 全部支持在游戏设置-控制中自定义。
 */
public class ModKeyBindings {
    public static final String CATEGORY = "key.categories." + SkillTreeMod.MOD_ID;

    public static final KeyMapping OPEN_SKILL_TREE = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".open_skill_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    /** 切换杀戮光环总开关 */
    public static final KeyMapping TOGGLE_AURA = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".toggle_aura",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    /** 循环光环目标模式（敌对/友好/所有） */
    public static final KeyMapping CYCLE_AURA_TARGET = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".cycle_aura_target",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            CATEGORY);

    /** 磁铁开关（默认 H，开启消耗技能点） */
    public static final KeyMapping TOGGLE_MAGNET = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".toggle_magnet",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY);

    // ============ 光环技能独立开关快捷键（仅治愈/时环/晴空保留独立键，伤害/速度用总开关 K 控制） ============

    /** 治愈光环 开关 */
    public static final KeyMapping TOGGLE_AURA_HEAL = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".toggle_aura_heal",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    /** 时之环·永恒正午 开关（默认空键，需玩家自行绑定） */
    public static final KeyMapping TOGGLE_AURA_TIME = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".toggle_aura_time",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);

    /** 晴空环·永恒晴天 开关（默认空键，需玩家自行绑定） */
    public static final KeyMapping TOGGLE_AURA_WEATHER = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".toggle_aura_weather",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);

    /** 光环锁定 开关（默认空键，需玩家自行绑定） */
    public static final KeyMapping TOGGLE_AURA_LOCK = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".toggle_aura_lock",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);

    /** 光环技能快捷键：技能ID → 对应 KeyMapping（供事件/提示统一遍历；伤害/速度/锁定用独立键，锁定制处理） */
    public static java.util.Map<String, KeyMapping> auraKeyMappings() {
        return java.util.Map.of(
                org.zifeng.skilltree.skill.Skills.AURA_HEAL, TOGGLE_AURA_HEAL,
                org.zifeng.skilltree.skill.Skills.AURA_TIME, TOGGLE_AURA_TIME,
                org.zifeng.skilltree.skill.Skills.AURA_WEATHER, TOGGLE_AURA_WEATHER);
    }

    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SKILL_TREE);
        event.register(TOGGLE_AURA);
        event.register(CYCLE_AURA_TARGET);
        event.register(TOGGLE_MAGNET);
        event.register(TOGGLE_AURA_HEAL);
        event.register(TOGGLE_AURA_TIME);
        event.register(TOGGLE_AURA_WEATHER);
        event.register(TOGGLE_AURA_LOCK);
    }
}
