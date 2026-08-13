package org.zifeng.skilltree.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 快捷键注册（MOD 总线）：唯一注册【打开技能树】。
 * ⚠️ 2026-08-13 需求：原版 设置→控制 只保留打开技能树一个快捷键；
 * 其余所有快捷键（光环总开关/目标模式/磁铁/光环独立开关等）一律移除，
 * 每个技能的开关快捷键改由 技能树界面内 独立绑定（SkillKeyBinds，本地持久化）。
 */
public class ModKeyBindings {
    public static final String CATEGORY = "key.categories." + SkillTreeMod.MOD_ID;

    public static final KeyMapping OPEN_SKILL_TREE = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".open_skill_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SKILL_TREE);
    }
}
