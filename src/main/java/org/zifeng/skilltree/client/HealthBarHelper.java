package org.zifeng.skilltree.client;

import net.minecraft.world.entity.player.Player;

/**
 * 血条压缩工具（2026-09-11，1.4.0 血条卡顿修复）。
 *
 * <h3>背景</h3>
 * 本模组的「血魄淬炼」（1000 级 × +2 生命 = +2000）+「血魄真解」（500 级 × +10% = ×51）
 * 可把最大生命推到 10 万量级。而原版血条是 <b>每 2 点生命画 1 颗心</b>
 * （{@code Mth.ceil(maxHealth / 2.0)}）→ 心数可达 5 万颗 → 每帧 10 万次 blit → 掉到 2 帧。
 * <p>（另注：创造/旁观模式 {@code gameMode.canHurtPlayer() == false}，原版压根不画血条，
 * 所以"生存卡、创造不卡"。）
 *
 * <h3>方案：只压缩"喂给原版渲染器的数值"，不改原版渲染代码</h3>
 * 原版 {@code Gui.renderPlayerHealth} → {@code renderHearts} 的入参被等比压缩到
 * {@code [0, COMPRESSED_MAX]}，因此：
 * <ul>
 *   <li>血条永远最多 {@code COMPRESSED_MAX / 2 = 10} 颗心（1 行）→ 性能回到原版水平</li>
 *   <li><b>原版渲染代码一行未改</b> → 其他血条 mod（ColorfulHearts / OverloadedArmorBar 等）
 *       接管渲染后照常生效，它们显示自己的方案，我们只在旁边加真实数字</li>
 *   <li>真实生命值<b>完全保留</b>（属性/伤害/存档/其他 mod 读取均不受影响），
 *       仅显示层压缩——这正是"数字显示真实血量"的意义所在</li>
 * </ul>
 *
 * <h3>安全阈值</h3>
 * 真实最大生命 ≤ {@link #COMPRESS_THRESHOLD}（= 原版 20 点 / 10 颗心）时<b>完全不干预</b>，
 * 原版行为 100% 保留（绝大多数普通玩家/其他模组玩家不受任何影响）。
 */
public final class HealthBarHelper {
    private HealthBarHelper() {
    }

    /** 压缩阈值：真实最大生命超过此值才启用压缩（20.0 = 原版 10 颗心，普通玩家永远不触发） */
    public static final float COMPRESS_THRESHOLD = 20.0f;

    /** 压缩后的最大生命（20.0 → 原版最多 10 颗心 = 1 行） */
    public static final float COMPRESSED_MAX = 20.0f;

    /** 是否需要压缩（真实最大生命超过阈值） */
    public static boolean shouldCompress(float realMaxHealth) {
        return realMaxHealth > COMPRESS_THRESHOLD;
    }

    /** 压缩后的"最大生命"（喂给原版渲染器用）；未超阈值时原样返回 */
    public static float compressMax(float realMaxHealth) {
        return shouldCompress(realMaxHealth) ? COMPRESSED_MAX : realMaxHealth;
    }

    /**
     * 等比压缩一个"生命量"（当前生命/吸收量）。
     * @param value       真实值（当前生命 或 吸收量）
     * @param realMaxHealth 真实最大生命（作为压缩基准）
     */
    public static float compressValue(float value, float realMaxHealth) {
        if (!shouldCompress(realMaxHealth)) {
            return value;
        }
        return value * (COMPRESSED_MAX / realMaxHealth);
    }

    /**
     * 压缩伤害吸收量。
     * <p>⚠️ 基准取 {@code max(最大生命, 吸收量)}：否则当"最大生命不超阈值但吸收量极大"时
     * （某些模组给海量吸收盾），吸收心仍会爆炸成上千颗。
     */
    public static float compressAbsorption(float realAbsorption, float realMaxHealth) {
        float basis = Math.max(realMaxHealth, realAbsorption);
        if (!shouldCompress(basis)) {
            return realAbsorption;
        }
        return realAbsorption * (COMPRESSED_MAX / basis);
    }

    // ============ 真实值读取（自动兼容其他模组加的生命/吸收） ============

    /** 真实当前生命（原版接口，含所有模组加成后的实际值） */
    public static float realHealth(Player player) {
        return player != null ? player.getHealth() : 0.0f;
    }

    /** 真实最大生命（读属性系统，自动含装备/药水/其他模组的修饰符） */
    public static float realMaxHealth(Player player) {
        return player != null ? player.getMaxHealth() : 20.0f;
    }

    /** 真实伤害吸收量 */
    public static float realAbsorption(Player player) {
        return player != null ? player.getAbsorptionAmount() : 0.0f;
    }

    /** 真实护甲值（读原版接口，含装备与所有模组加成） */
    public static int realArmor(Player player) {
        return player != null ? player.getArmorValue() : 0;
    }

    /** 护甲数字显示阈值：护甲超过此值才显示数字（20 = 原版满护甲 10 个图标） */
    public static final int ARMOR_NUMBER_THRESHOLD = 20;

    /** 是否需要显示护甲数字 */
    public static boolean shouldShowArmorNumber(int armor) {
        return armor > ARMOR_NUMBER_THRESHOLD;
    }

    // ============ 原版护甲行坐标捕获（2026-09-11） ============

    /**
     * 原版渲染护甲时的 {@code leftHeight}（由 Mixin 直接读取原版字段，故为<b>实测值</b>）。
     * <p>各版本护甲公式不同（1.20.1 {@code height-leftHeight}；1.21.1 {@code guiHeight-leftHeight}），
     * 但都可统一表达为 {@code guiHeight - leftHeight}（1.20.1 的 {@code height} 即 {@code guiHeight}）。
     * <p>因此统一存 leftHeight，渲染时用 {@link #armorRowY(int)} 算，保证数字与图标严格对齐。
     */
    private static volatile int armorLeftHeight = Integer.MIN_VALUE;

    public static void setArmorLeftHeight(int leftHeight) {
        armorLeftHeight = leftHeight;
    }

    /**
     * 护甲行 y 坐标。
     * @param guiHeight 当前 GUI 高度（{@code GuiGraphics.guiHeight()}）
     * @return 护甲行 y；未捕获到时返回 {@link Integer#MIN_VALUE}（调用方应跳过绘制）
     */
    public static int armorRowY(int guiHeight) {
        int lh = armorLeftHeight;
        return lh == Integer.MIN_VALUE ? Integer.MIN_VALUE : guiHeight - lh;
    }

    /** 是否已捕获到护甲行坐标 */
    public static boolean hasArmorRowY() {
        return armorLeftHeight != Integer.MIN_VALUE;
    }
}
