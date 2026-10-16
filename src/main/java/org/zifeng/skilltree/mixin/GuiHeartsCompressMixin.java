package org.zifeng.skilltree.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zifeng.skilltree.client.HealthBarHelper;

/**
 * 血条压缩（2026-09-11，1.4.0 血条卡顿修复）。
 *
 * <h3>问题</h3>
 * 本模组可把最大生命推到 10 万量级，而原版血条「每 2 点生命 1 颗心」→ 5 万颗心
 * → 每帧 10 万次 blit（每颗心还伴随 enableBlend/disableBlend）→ 帧数掉到 2。
 *
 * <h3>核心原则（用户明确要求，兼容性优先）</h3>
 * <b>原版血条渲染代码一个字都不改</b>——只把「喂给原版渲染器的数值」等比压缩，
 * 让原版自己照常画（永远最多 10 颗心 = 1 行）。
 * <p>因此其他血条 mod（ColorfulHearts 等）接管渲染后<b>照常生效</b>，
 * 它们显示自己的方案，我们在旁边加真实数字即可。
 *
 * <h3>为什么只改 extractHealthLevel 的 3 个取值点就够</h3>
 * 该方法的局部变量全部由这 3 个调用派生：
 * <pre>
 *   int   currentHealth   = ceil(player.getHealth())                       // ← ① 当前生命
 *   float maxHealth       = max(getAttributeValue(MAX_HEALTH), max(old,current)) // ← ② 最大生命
 *                                                                          //    （心数 = f/2、行数 = (f+吸收)/2/10）
 *   int   totalAbsorption = ceil(player.getAbsorptionAmount())             // ← ③ 伤害吸收
 *   → numHealthRows（行数）/ healthRowHeight（行距）/ leftHeight（HUD 高度累加）
 *     / extractHearts(...) 的 4 个入参 / heartOffsetIndex（回血抖动种子）
 * </pre>
 * 改掉这 3 处后，血条颗数、行数、以及 <b>leftHeight</b>（否则饥饿条会被推到屏幕外 1.5 万像素）
 * 全部自动一致。
 *
 * <h3>★ 26.1.2 移植说明</h3>
 * 目标方法被原版重命名过（GUI 整体改为「图层 + extract 渲染状态」模型），注入目标已同步更新：
 * <pre>
 *   1.21.1 {@code Gui#renderHealthLevel} → 26.1.2 {@code Gui#extractHealthLevel}
 *   1.21.1 {@code Gui#renderArmorLevel}  → 26.1.2 {@code Gui#extractArmorLevel}
 * </pre>
 * 三个取值表达式本身<b>一字未变</b>（仍是 {@code Player.getHealth} /
 * {@code Player.getAttributeValue(Holder)} / {@code Player.getAbsorptionAmount}），
 * 所以压缩逻辑无需改动。另：{@code leftHeight} 在 26.1.2 已是 NeoForge 主动保留的
 * {@code public} 字段（源码注释 {@code Neo: This variable controls the height of overlays…}），
 * 每帧在 {@code Gui#extractRenderState} 里重置为 39，随后按图层顺序先跑生命、再跑护甲 ——
 * 与原版旧流程一致。
 *
 * <h3>安全措施</h3>
 * <ul>
 *   <li>真实最大生命 ≤ 20（原版 10 颗心）时 {@link HealthBarHelper} 原样返回 → <b>零干预</b></li>
 *   <li>全部 {@code require = 0}：注入失败（与其他 mod 冲突）只静默跳过，绝不崩游戏</li>
 * </ul>
 */
@Mixin(Gui.class)
public abstract class GuiHeartsCompressMixin {

    /** ① 当前生命：等比压缩（喂给原版画心用） */
    @Redirect(
            method = "extractHealthLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getHealth()F"),
            require = 0
    )
    private float zifeng$compressHealth(Player player) {
        float real = player.getHealth();
        float realMax = player.getMaxHealth();
        if (!HealthBarHelper.shouldCompress(realMax)) {
            return real; // 未超阈值：原版行为，零干预
        }
        return HealthBarHelper.compressValue(real, realMax);
    }

    /** ② 最大生命：压缩到阈内（决定心数与行数，并修正 leftHeight） */
    @Redirect(
            method = "extractHealthLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"),
            require = 0
    )
    private double zifeng$compressMaxHealth(Player player, Holder<Attribute> attribute) {
        double real = player.getAttributeValue(attribute);
        if (!HealthBarHelper.shouldCompress(player.getMaxHealth())) {
            return real; // 未超阈值：原版行为，零干预
        }
        return HealthBarHelper.compressMax((float) real);
    }

    /** ③ 伤害吸收：同样压缩（基准取 max(最大生命, 吸收量)，防海量吸收盾爆炸） */
    @Redirect(
            method = "extractHealthLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"),
            require = 0
    )
    private float zifeng$compressAbsorption(Player player) {
        float real = player.getAbsorptionAmount();
        float realMax = player.getMaxHealth();
        if (!HealthBarHelper.shouldCompress(Math.max(realMax, real))) {
            return real; // 未超阈值：原版行为，零干预
        }
        return HealthBarHelper.compressAbsorption(real, realMax);
    }

    /**
     * 捕获原版护甲图标的 y 坐标（供护甲数字对齐使用）。
     * <p>26.1.2 中 {@code extractArmorLevel} 调用
     * {@code extractArmor(graphics, player, guiHeight() - leftHeight + 10, 1, 0, l)}，
     * 而 {@code extractArmor} 内部再 {@code -10}，故护甲行 y = {@code guiHeight - leftHeight}
     * （与 1.21.1 的 {@code renderArmorLevel} 完全一致）。
     * <p>{@code require = 0}：拿不到就不显示，绝不影响游戏。
     */
    @Inject(method = "extractArmorLevel", at = @At("HEAD"), require = 0)
    private void zifeng$captureArmorRowY(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        int leftHeight = ((Gui) (Object) this).leftHeight;
        HealthBarHelper.setArmorLeftHeight(leftHeight);
    }
}
