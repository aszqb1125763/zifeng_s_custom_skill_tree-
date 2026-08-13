package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.client.screen.SkillTreeScreen;
import org.zifeng.skilltree.network.OpenSkillTreeC2SPacket;
import org.zifeng.skilltree.network.SetSkillToggleC2SPacket;
import org.zifeng.skilltree.skill.Skills;

import java.util.HashMap;
import java.util.Map;

/**
 * 快捷键检测（GAME 总线，由 ClientRegistrar 手动注册）：
 * N = 打开技能树；每个技能的开关快捷键由 技能树界面内 SkillKeyBinds 独立绑定（本地持久化）。
 */
public class ModKeyBindingEvents {

    /** 光环技能开关缓存（服务端回发校准，供快捷键取反发送） */
    private static final Map<String, Boolean> auraToggles = new HashMap<>();

    /** 光环总开关客户端缓存（服务端回发校准，供圆环渲染器判断是否显示） */
    private static boolean auraEnabledClient = true;

    /** 磁力光环是否已学习（客户端缓存，服务端回发校准，圆环渲染判断用） */
    private static boolean magnetLearnedClient = false;

    /** 杀戮光环·伤害/速度是否已学（客户端缓存，服务端回发校准；配合 toggles 判断光环真实状态） */
    private static boolean auraDamageLearned = false;
    private static boolean auraSpeedLearned = false;

    /** 所有光环技能已学缓存（独立快捷键未学不触发用） */
    private static final Map<String, Boolean> auraLearnedCache = new HashMap<>();

    /** 所有技能已学缓存（2026-08-13：任意技能可绑定独立开关快捷键，用全部技能已学状态判断） */
    private static final Map<String, Boolean> allSkillsLearnedCache = new HashMap<>();

    /** 所有技能开关缓存（2026-08-13：全技能开关状态，供快捷键与渲染辅助） */
    private static final Map<String, Boolean> allTogglesCache = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        org.zifeng.skilltree.client.SkillKeyBinds.load(); // 确保技能绑定已加载
        // 技能独立开关快捷键（2026-08-13：每个技能可单独绑定，SkillKeyBinds 本地持久化）
        // 按下即切换该技能开关；未学的技能不处理（等服务端校准）
        if (Minecraft.getInstance().screen == null) { // 界面打开时不触发（避免与设置窗口/技能树交互冲突）
            for (String skillId : org.zifeng.skilltree.client.SkillKeyBinds.allBinds().keySet()) {
                if (org.zifeng.skilltree.client.SkillKeyBinds.consumeClick(skillId)) {
                    // 已学才触发（光环技能走 auraLearnedCache，其余技能走 allSkillsLearnedCache）
                    boolean learned = auraLearnedCache.getOrDefault(skillId, Boolean.FALSE)
                            || allSkillsLearnedCache.getOrDefault(skillId, Boolean.FALSE);
                    if (learned) {
                        boolean now = !auraToggles.getOrDefault(skillId, Boolean.TRUE);
                        auraToggles.put(skillId, now);
                        PacketDistributor.sendToServer(new SetSkillToggleC2SPacket(skillId, now));
                    }
                }
            }
        }
        while (ModKeyBindings.OPEN_SKILL_TREE.consumeClick()) {
            // 客户端乐观打开技能树界面（空数据），服务端回发数据包后 updateData 填充
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof SkillTreeScreen)) {
                mc.setScreen(new SkillTreeScreen(0, Map.of(), Map.of(), Map.of(), true, 0));
            }
            PacketDistributor.sendToServer(new OpenSkillTreeC2SPacket());
        }
        // 2026-08-13：原版 设置→控制 只保留打开技能树；
        // 光环总开关/目标模式/磁铁/光环锁定/光环独立开关的原版快捷键全部移除，
        // 统一改由技能树界面内为每个技能绑定独立开关快捷键（SkillKeyBinds，见上方循环）。
    }

    private static int lastMode = 0;

    /** 由服务端回发的技能数据校准本地目标模式（防 L 键循环从错误状态开始） */
    public static void setLastMode(int mode) {
        lastMode = Math.max(0, Math.min(2, mode));
    }
    /** 由服务端回发的技能数据校准光环总开关（供圆环渲染器使用） */
    public static void setAuraEnabledClient(boolean auraEnabled) {
        auraEnabledClient = auraEnabled;
    }

    /** 光环总开关是否开启（客户端缓存） */
    public static boolean isAuraEnabledClient() {
        return auraEnabledClient;
    }

    /** 光环是否有攻击能力（伤害或速度技能【已学且开启】，渲染圆环用；isEnabled 默认 true，必须配合已学判断） */
    public static boolean isAuraAttackEnabled() {
        return (auraDamageLearned && auraToggles.getOrDefault(Skills.AURA_DAMAGE, Boolean.TRUE))
                || (auraSpeedLearned && auraToggles.getOrDefault(Skills.AURA_SPEED, Boolean.TRUE));
    }

    /** 磁力光环是否开启（已学习且开关开启，渲染蓝色圆环用） */
    public static boolean isMagnetEnabledClient() {
        return magnetLearnedClient && auraToggles.getOrDefault(Skills.AURA_MAGNET, Boolean.FALSE);
    }

    /** 由服务端回发的技能数据校准磁力光环已学状态 */
    public static void setMagnetLearnedClient(boolean learned) {
        magnetLearnedClient = learned;
    }
    /** 由服务端回发的技能数据校准光环技能开关缓存 */
    public static void updateAuraToggles(Map<String, Boolean> toggles) {
        if (toggles == null) {
            return;
        }
        for (String skillId : Skills.AURA_SKILLS) {
            auraToggles.put(skillId, toggles.getOrDefault(skillId, Boolean.TRUE));
        }
        // 技能开关缓存：由 updateAuraLearned 用 allTogglesCache 计算（已学 && 开启），这里只存全量开关
        // 全技能开关缓存（渲染光束隐藏等用）
        allTogglesCache.clear();
        allTogglesCache.putAll(toggles);
    }

    /** 由服务端回发的技能数据校准光环技能已学状态（圆环渲染防重置残留 + 未学快捷键不触发） */
    public static void updateAuraLearned(Map<String, Integer> learnedSkills) {
        if (learnedSkills == null) {
            return;
        }
        auraDamageLearned = learnedSkills.getOrDefault(Skills.AURA_DAMAGE, 0) > 0;
        auraSpeedLearned = learnedSkills.getOrDefault(Skills.AURA_SPEED, 0) > 0;
        for (String skillId : Skills.AURA_SKILLS) {
            auraLearnedCache.put(skillId, learnedSkills.getOrDefault(skillId, 0) > 0);
        }
        // 全技能已学缓存（2026-08-13：任意技能可绑定独立开关快捷键）
        allSkillsLearnedCache.clear();
        for (String skillId : Skills.ALL_SKILLS) {
            allSkillsLearnedCache.put(skillId, learnedSkills.getOrDefault(skillId, 0) > 0);
        }
    }

    /** 是否有光环快捷键未绑定（2026-08-13 起原版快捷键全部移除，技能开关统一在技能树界面绑定 → 恒 false 不再提示） */
    public static boolean hasUnboundAuraKeys() {
        return false;
    }

    /** 技能客户端状态：是否已学且开启（供客户端渲染/行为判断，服务端 S2CPacket 校准；2026-08-13 万物挖掘用） */
    public static boolean isSkillEnabledClient(String skillId) {
        return allSkillsLearnedCache.getOrDefault(skillId, Boolean.FALSE)
                && allTogglesCache.getOrDefault(skillId, Boolean.TRUE);
    }
}
