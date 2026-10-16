package org.zifeng.skilltree.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
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
    /** 旧版字符串键名（1.21.1 及以前）——26.1.2 的 KeyMapping.Category 用 Identifier，翻译键自动生成为
     *  {@code key.category.<命名空间>.<路径>}，故旧的 {@code key.categories.*} 已废弃。 */
    @Deprecated
    public static final String CATEGORY = "key.categories." + SkillTreeMod.MOD_ID;

    /** 快捷键分类（26.1.2：Category 是 record(Identifier)，由事件注册） */
    public static final KeyMapping.Category SKILL_TREE_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "main"));

    public static final KeyMapping OPEN_SKILL_TREE = new KeyMapping(
            "key." + SkillTreeMod.MOD_ID + ".open_skill_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            SKILL_TREE_CATEGORY);

    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        // ⚠️ 26.1.2：分类必须通过事件注册（KeyMapping.Category.register 已废弃，且会被排序表拒重复）
        event.registerCategory(SKILL_TREE_CATEGORY);
        event.register(OPEN_SKILL_TREE);
    }
}
