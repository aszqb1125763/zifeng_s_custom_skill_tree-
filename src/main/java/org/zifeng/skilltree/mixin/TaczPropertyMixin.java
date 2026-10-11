package org.zifeng.skilltree.mixin;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.resource.pojo.data.gun.MoveSpeed;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zifeng.skilltree.compat.TaczSkills;

import java.util.HashMap;
import java.util.Map;

/**
 * 枪械技能 · 属性强化（★ 2026-10-11 第二版，TaCZ 兼容）。
 *
 * <h2>为什么集中注入这一个方法</h2>
 * TaCZ 把每把枪的运行时属性算好放在<b>按玩家</b>的
 * {@link AttachmentCacheProperty} 里（{@code IGunOperator#getCacheProperty()}），
 * 而 {@link AttachmentPropertyManager#postChangeEvent(LivingEntity, ItemStack)} 是这张缓存的
 * <b>唯一重算入口</b>（TaCZ 自己在「举枪 / 切换开火模式 / 改装配件」时调用它），
 * 且它<b>带玩家上下文</b>（形参就是 shooter）。
 *
 * <p>于是注入这一个 TAIL 就能一次覆盖 4 项技能 —— 比逐个去改 TaCZ 的读取点少 4 个 mixin，
 * 且天然双端一致（TaCZ 在客户端 {@code LocalPlayerDraw} / 服务端 {@code LivingEntityDrawGun}
 * 都会调用本方法）：
 * <table border="1">
 *   <caption>本类覆盖的属性</caption>
 *   <tr><th>缓存项</th><th>技能</th><th>算法</th></tr>
 *   <tr><td>{@code GunProperties#PIERCE}</td><td>弹道穿透强化</td><td>{@code +每级1}</td></tr>
 *   <tr><td>{@code GunProperties#INACCURACY}</td><td>精准射击</td><td>各项 {@code ×(1-r)}</td></tr>
 *   <tr><td>{@code GunProperties#ADS_TIME}</td><td>快速开镜</td><td>{@code ×(1-r)}</td></tr>
 *   <tr><td>{@code GunProperties#MOVE_SPEED}</td><td>持枪疾行</td><td>三项 {@code ×(1+bonus)}</td></tr>
 * </table>
 *
 * <h2>为什么不会「越乘越多」</h2>
 * {@code postChangeEvent} 内部会先发 {@code AttachmentPropertyEvent}，
 * 其处理器 {@code ChangeGunPropertyEvent} 调用 {@code AttachmentCacheProperty#eval(...)}
 * 把缓存<b>从基础值 + 配件重新算一遍</b>，之后才轮到本注入。
 * 所以每次调用都是「全新基础值 × 我们的倍率」，<b>不会叠加</b>（已用字节码核实）。
 *
 * <p>⚠️ {@code getCache} 对未配置该项的枪会返回 null → 每一项都必须判空。
 * <p>⚠️ 未学技能时 {@link TaczSkills} 返回中性值（×1.0 / +0），故可无条件应用。
 * <p>⚠️ TaCZ 是模组类 → {@code remap = false}。
 */
@Mixin(value = AttachmentPropertyManager.class, remap = false)
public abstract class TaczPropertyMixin {

    @Inject(remap = false, method = "postChangeEvent", at = @At("TAIL"))
    private static void zifeng$applyGunSkills(LivingEntity shooter, ItemStack gunItem, CallbackInfo ci) {
        if (shooter == null || gunItem == null || shooter.level() == null) {
            return; // 未入世界的实体没有缓存可改
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        if (operator == null) {
            return;
        }
        AttachmentCacheProperty cache = operator.getCacheProperty();
        if (cache == null) {
            return;
        }

        boolean changed = false;
        changed |= applyPierce(cache, shooter);
        changed |= applyInaccuracy(cache, shooter);
        changed |= applyAdsTime(cache, shooter);
        changed |= applyMoveSpeed(cache, shooter);

        if (changed) {
            // 自证：任一属性真的被改动过才打印（避免未学技能时刷日志）
            TaczSkills.logActiveOnce("属性缓存 postChangeEvent");
        }
    }

    /** 弹道穿透强化：缓存里的穿透数是 Integer */
    private static boolean applyPierce(AttachmentCacheProperty cache, LivingEntity shooter) {
        int bonus = TaczSkills.pierceBonus(shooter);
        if (bonus <= 0) {
            return false;
        }
        Integer current = cache.getCache(GunProperties.PIERCE);
        if (current == null) {
            return false; // 该枪没有穿透配置
        }
        cache.setCache(GunProperties.PIERCE, current + bonus);
        return true;
    }

    /** 精准射击：扩散是一个「按姿态分类」的表（站立/移动/潜行/趴下/瞄准）→ 逐项缩放 */
    private static boolean applyInaccuracy(AttachmentCacheProperty cache, LivingEntity shooter) {
        float mult = TaczSkills.inaccuracyMultiplier(shooter);
        if (mult >= 1.0F) {
            return false;
        }
        Map<InaccuracyType, Float> current = cache.getCache(GunProperties.INACCURACY);
        if (current == null || current.isEmpty()) {
            return false;
        }
        Map<InaccuracyType, Float> scaled = new HashMap<>(current.size());
        for (Map.Entry<InaccuracyType, Float> entry : current.entrySet()) {
            // ⚠️ 原表可能是不可变视图 → 必须新建 map 再 setCache
            scaled.put(entry.getKey(),
                    entry.getValue() == null ? null : entry.getValue() * mult);
        }
        cache.setCache(GunProperties.INACCURACY, scaled);
        return true;
    }

    /** 快速开镜：ADS_TIME 是 Float（秒） */
    private static boolean applyAdsTime(AttachmentCacheProperty cache, LivingEntity shooter) {
        double mult = TaczSkills.adsTimeMultiplier(shooter);
        if (mult >= 1.0) {
            return false;
        }
        Float current = cache.getCache(GunProperties.ADS_TIME);
        if (current == null) {
            return false;
        }
        cache.setCache(GunProperties.ADS_TIME, (float) (current * mult));
        return true;
    }

    /** 持枪疾行：MoveSpeed 有「普通/瞄准/换弹」三个倍率，一并放大 */
    private static boolean applyMoveSpeed(AttachmentCacheProperty cache, LivingEntity shooter) {
        float mult = TaczSkills.moveSpeedMultiplier(shooter);
        if (mult <= 1.0F) {
            return false;
        }
        MoveSpeed current = cache.getCache(GunProperties.MOVE_SPEED);
        if (current == null) {
            return false;
        }
        cache.setCache(GunProperties.MOVE_SPEED, new MoveSpeed(
                current.getBaseMultiplier() * mult,
                current.getAimMultiplier() * mult,
                current.getReloadMultiplier() * mult));
        return true;
    }
}
