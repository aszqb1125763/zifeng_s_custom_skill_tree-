package org.zifeng.skilltree.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.skilltree.client.ModKeyBindingEvents;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

/**
 * 烈焰不侵（FIRE_PROTECT，2026-08-27）：覆写 Entity.fireImmune()。
 * <p>原版 fireImmune() 返回 true 时自动获得完整火焰免疫，无任何闪烁：
 * <ul>
 *   <li>baseTick：remainingFireTicks &gt; 0 时 fireImmune() → clearFire() 立即灭火，不扣血</li>
 *   <li>lavaIgnite()：岩浆不点燃</li>
 *   <li>lavaHurt()：岩浆不扣血</li>
 *   <li>isInvulnerableTo()：IS_FIRE 标签伤害全部免疫（火焰/岩浆/着火）</li>
 *   <li>isOnFire()：!fireImmune() → 客户端无火焰视觉 overlay</li>
 * </ul>
 * 非玩家实体零开销（一个 instanceof 判断即返回原逻辑）。
 */
@Mixin(Entity.class)
public abstract class EntityFireImmuneMixin {

    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void zifeng$fireProtect(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player player)) {
            return; // 非玩家：走原逻辑，零开销
        }
        boolean enabled;
        if (player instanceof ServerPlayer sp) {
            PlayerSkillRecord record = PlayerSkillSavedData.get(sp.serverLevel()).getOrCreatePlayer(sp.getUUID());
            enabled = record.getLearnedPoints(Skills.FIRE_PROTECT) > 0 && record.isEnabled(Skills.FIRE_PROTECT);
        } else {
            // 客户端（LocalPlayer）：本地缓存判断（服务端 S2CPacket 校准），保证 isOnFire 视觉同步
            enabled = ModKeyBindingEvents.isSkillEnabledClient(Skills.FIRE_PROTECT);
        }
        if (enabled) {
            cir.setReturnValue(true);
        }
    }
}
