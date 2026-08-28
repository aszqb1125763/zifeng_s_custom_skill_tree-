package org.zifeng.skilltree.event;
import net.minecraft.network.chat.Component;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

/**
 * 光环锁定（AURA_LOCK，一次性解锁 1000 点）：免疫 TP 与击退。
 * 采用「事件驱动」，**零 tick 开销**（无任何轮询）：
 * <ul>
 *   <li>EntityTeleportEvent → 取消（免疫 /tp、传送门、末影珍珠、紫颂果等一切传送）</li>
 *   <li>LivingKnockBackEvent → 取消（免疫击退）</li>
 * </ul>
 */
public class LockEvents {

    /** 光环锁定是否已学且启用 */
    private static boolean isLockEnabled(ServerPlayer player) {
        PlayerSkillRecord record = PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
        return record.getLearnedPoints(Skills.AURA_LOCK) > 0 && record.isEnabled(Skills.AURA_LOCK);
    }

    // ============ ① 免疫传送（/tp、传送门、末影珍珠、紫颂果等） ============
    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player && isLockEnabled(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.lock_tp_immune"));
        }
    }

    // ============ ② 免疫击退 ============
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player && isLockEnabled(player)) {
            event.setCanceled(true);
        }
    }
}
