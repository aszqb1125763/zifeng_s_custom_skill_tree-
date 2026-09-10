package org.zifeng.skilltree.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import org.zifeng.skilltree.skill.Skills;

/**
 * 御风止步（FLY_NO_INERTIA，2026-08-27）：飞行无惯性（全方向）。
 * <p>客户端处理：飞行运动由客户端驱动（服务端接受预测位置），且只有客户端有完整输入状态
 * （LocalPlayer.input：jump/shift/方向键/运动意图）。松开按键立即停止对应方向：
 * <ul>
 *   <li>垂直：未按空格也未按潜行 → 垂直速度归零（松空格立即停止升降）</li>
 *   <li>水平：无任何水平移动意图（WASD 均未按）→ 水平速度归零（松方向键立即悬停，无滑行）</li>
 * </ul>
 * 技能状态用客户端本地缓存（服务端 S2CPacket 校准），多人下各玩家独立判断。
 */
public class ClientFlightEvents {

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        // ⚠️ 2026-09-11 修复：Forge 的 PlayerTickEvent 每 tick 触发 START+END 两次
        //  （NeoForge 1.21.1 拆成 Pre/Post 只触发一次）。本方法虽幂等，
        //  但每 tick 白跑一遍（含属性/状态读取），加上 phase 守卫与 1.21.1 对齐。
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) {
            return;
        }
        if (!(event.player instanceof LocalPlayer player)) {
            return;
        }
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.FLY_NO_INERTIA)) {
            return; // 技能未学/未开启
        }
        if (!player.getAbilities().flying && !player.isFallFlying()) {
            return; // 仅飞行中生效（创造飞行/鞘翅）
        }
        var motion = player.getDeltaMovement();
        // 垂直：松空格且未按潜行 → y 归零（无垂直惯性，立即停止升降）
        if (!player.input.jumping && !player.input.shiftKeyDown) {
            if (motion.y != 0) {
                motion = new net.minecraft.world.phys.Vec3(motion.x, 0, motion.z);
            }
        }
        // 水平：无任何水平移动意图（WASD 均未按/互相抵消）→ x/z 归零（无水平滑行惯性）
        if (player.input.leftImpulse == 0.0F && player.input.forwardImpulse == 0.0F) {
            if (motion.x != 0 || motion.z != 0) {
                motion = new net.minecraft.world.phys.Vec3(0, motion.y, 0);
            }
        }
        if (motion != player.getDeltaMovement()) {
            player.setDeltaMovement(motion);
        }
    }
}
