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

    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SKILL_TREE);
        event.register(TOGGLE_AURA);
        event.register(CYCLE_AURA_TARGET);
    }
}
