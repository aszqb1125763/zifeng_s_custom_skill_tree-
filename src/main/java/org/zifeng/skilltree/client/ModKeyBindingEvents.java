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
import org.zifeng.skilltree.network.ToggleAuraC2SPacket;

import java.util.Map;

/**
 * 快捷键检测（GAME 总线，由 ClientRegistrar 手动注册）：
 * N = 打开技能树；K = 切换杀戮光环总开关；L = 循环光环目标模式。
 */
public class ModKeyBindingEvents {

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
    }

    private static int lastMode = 0;
}
