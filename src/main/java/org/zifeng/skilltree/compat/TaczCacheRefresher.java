package org.zifeng.skilltree.compat;

import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 枪械技能 · 属性缓存强制刷新（★ 2026-10-11 第二版）。
 *
 * <h2>为什么需要它</h2>
 * {@code TaczPropertyMixin} 把技能加成写进玩家的 {@code AttachmentCacheProperty}，
 * 而这张缓存只在 TaCZ 自己认为"属性可能变了"时才重算（举枪 / 切开火模式 / 改装）。
 * 玩家在<b>持枪状态下</b>加点或开关枪械技能时缓存不会更新 → 表现为"点了没反应"。
 * 本类定时（见 {@code TaczGunSkillEvents}）主动触发一次重算即可。
 *
 * <h2>⚠️ 为什么不直接写在事件类里</h2>
 * 本类直接引用 TaCZ 类型（{@link AttachmentPropertyManager}），
 * <b>未装 TaCZ 时绝不能被加载</b> —— 故所有调用方都必须先用
 * {@link TaczSkills#isTaczLoaded()} 把关（与 {@code TaczInfiniteAmmo} 当年的做法一致）。
 */
public final class TaczCacheRefresher {

    private TaczCacheRefresher() {
    }

    /**
     * 让 TaCZ 重新计算该实体手持枪械的全部属性，从而把枪械技能加成重新写回缓存。
     *
     * <p>幂等：TaCZ 内部会先把缓存从基础值 + 配件重算一遍，再轮到我们的 mixin 乘倍率，
     * 所以重复调用不会「越乘越多」。
     */
    public static void refresh(LivingEntity shooter, ItemStack gunItem) {
        if (shooter == null || gunItem == null || shooter.level() == null) {
            return;
        }
        AttachmentPropertyManager.postChangeEvent(shooter, gunItem);
    }
}
