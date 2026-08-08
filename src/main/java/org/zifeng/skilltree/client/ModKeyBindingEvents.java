package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.client.screen.SkillTreeScreen;
import org.zifeng.skilltree.network.AuraTargetC2SPacket;
import org.zifeng.skilltree.network.OpenSkillTreeC2SPacket;
import org.zifeng.skilltree.network.SetSkillToggleC2SPacket;
import org.zifeng.skilltree.network.ToggleAuraC2SPacket;
import org.zifeng.skilltree.network.ToggleMagnetC2SPacket;
import org.zifeng.skilltree.skill.Skills;

import java.util.HashMap;
import java.util.Map;

/**
 * 快捷键检测（GAME 总线，由 ClientRegistrar 手动注册）：
 * N = 打开技能树；K = 切换杀戮光环总开关；L = 循环光环目标模式。
 * 光环技能（伤害/武器/速度/治愈）独立快捷键默认空键，需玩家自行在 设置→控制 绑定。
 */
public class ModKeyBindingEvents {

    /** 光环技能开关缓存（服务端回发校准，供快捷键取反发送） */
    private static final Map<String, Boolean> auraToggles = new HashMap<>();

    /** 光环总开关客户端缓存（服务端回发校准，供圆环渲染器判断是否显示） */
    private static boolean auraEnabledClient = true;

    /** 磁力光环是否已学习（客户端缓存，服务端回发校准，圆环渲染判断用） */
    private static boolean magnetLearnedClient = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        while (ModKeyBindings.OPEN_SKILL_TREE.consumeClick()) {
            // 客户端乐观打开技能树界面（空数据），服务端回发数据包后 updateData 填充
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof SkillTreeScreen)) {
                mc.setScreen(new SkillTreeScreen(0, Map.of(), Map.of(), Map.of(), true, 0));
            }
            PacketDistributor.sendToServer(new OpenSkillTreeC2SPacket());
        }
        while (ModKeyBindings.TOGGLE_AURA.consumeClick()) {
            PacketDistributor.sendToServer(new ToggleAuraC2SPacket());
        }
        while (ModKeyBindings.CYCLE_AURA_TARGET.consumeClick()) {
            // 客户端乐观循环：0→1→2→0，服务端接收后回发校准
            int next = (lastMode + 1) % 3;
            lastMode = next;
            PacketDistributor.sendToServer(new AuraTargetC2SPacket(next));
        }
        while (ModKeyBindings.TOGGLE_MAGNET.consumeClick()) {
            PacketDistributor.sendToServer(new ToggleMagnetC2SPacket());
        }
        // 光环技能独立开关快捷键（默认空键，未绑定不触发）
        for (Map.Entry<String, net.minecraft.client.KeyMapping> entry : ModKeyBindings.auraKeyMappings().entrySet()) {
            while (entry.getValue().consumeClick()) {
                boolean now = !auraToggles.getOrDefault(entry.getKey(), Boolean.TRUE);
                auraToggles.put(entry.getKey(), now);
                PacketDistributor.sendToServer(new SetSkillToggleC2SPacket(entry.getKey(), now));
            }
        }
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

    /** 光环是否有攻击能力（伤害或速度技能任一开启，渲染圆环用） */
    public static boolean isAuraAttackEnabled() {
        return auraToggles.getOrDefault(Skills.AURA_DAMAGE, Boolean.TRUE)
                || auraToggles.getOrDefault(Skills.AURA_SPEED, Boolean.TRUE);
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
    }

    /** 是否有光环快捷键未绑定（供界面提示玩家设置） */
    public static boolean hasUnboundAuraKeys() {
        for (net.minecraft.client.KeyMapping mapping : ModKeyBindings.auraKeyMappings().values()) {
            if (mapping.isUnbound()) {
                return true;
            }
        }
        return false;
    }
}
