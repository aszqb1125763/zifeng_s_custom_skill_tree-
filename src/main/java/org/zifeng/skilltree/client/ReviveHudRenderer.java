package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 凤凰涅槃冷却 HUD 提示（2026-09-08 改版：并入技能点 HUD 模块正下方显示）：
 * <ul>
 *   <li>位置：技能点 HUD（左下角模块）正下方，随模块显隐/偏移，不再悬浮经验条上方</li>
 *   <li>图标：不死图腾（凤凰涅槃图标同款）</li>
 *   <li>冷却中：图标暗色遮罩 + 剩余秒数；冷却就绪：亮色闪烁 + 「就绪」文字</li>
 *   <li>未学/关闭/技能重置：图标隐藏（服务端在学→未学时补发 false，2026-09-08 修复）</li>
 * </ul>
 */
public class ReviveHudRenderer {

    /** 是否已学且启用（服务端每秒同步） */
    private static boolean learned = false;

    /** 凤凰涅槃冷却剩余 tick（服务端每秒同步，0 = 冷却就绪） */
    private static int cooldownRemainingTicks = 0;

    public static void setCooldown(boolean learnedSkill, int remainingTicks) {
        learned = learnedSkill;
        cooldownRemainingTicks = Math.max(0, remainingTicks);
    }

    /** 客户端当前凤凰涅槃冷却剩余 tick（供 HUD / 其他系统查询） */
    public static int getCooldownTicks() {
        return learned ? cooldownRemainingTicks : 0;
    }

    /** 客户端每 tick 本地递减倒计时（服务端每秒校准一次 → 秒数平滑准确，2026-09-08 修复计时不准） */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (learned && cooldownRemainingTicks > 0) {
            cooldownRemainingTicks--;
        }
    }

    /**
     * 不死图腾图标（缓存共享实例，避免每帧 {@code new ItemStack} 分配）。
     * <p>只用于 {@code GuiGraphicsExtractor#item} 只读渲染（26.1.2 由 {@code renderItem} 改名而来），
     * 不改 stack 内容，故共享安全。
     *
     * <p>⚠️ ★ 26.1.2 崩溃修复（2026-10-16，启动即在主菜单崩溃）：
     * <b>不能在静态初始化器里创建 ItemStack</b>。
     * <p>新版 {@code ItemStack} 构造会<b>立即读取物品的默认组件</b>
     * （{@code ItemStack.java:253} → {@code Holder.Reference#components()}），
     * 而组件要到 {@code DataComponentInitializers.PendingComponents#apply()} 才绑定 ——
     * 客户端侧由 {@code RegistryDataCollector#updateComponents()} 在「加入世界 / 收到注册表数据」
     * 时触发（服务端侧在数据包重载时触发）。
     * 也就是说客户端停在<b>主菜单</b>时组件还没绑定，此刻 {@code new ItemStack(...)} 会抛
     * {@code NullPointerException: Components not bound yet}；又因为它发生在
     * {@code <clinit>} 里，会被包装成 {@code ExceptionInInitializerError} → <b>启动即崩</b>。
     * <p>本类会被 {@code ClientTickEvent.Post} 在主菜单就首次触碰，静态字段必然早于组件绑定执行，
     * 因此改为<b>首次绘制时惰性创建</b>（HUD 只在世界内绘制，那时组件已绑定）。
     * <p>判断依据用官方 API {@link Holder#areComponentsBound()}，而不是猜「世界是否已加载」。
     * <p>另加异常兜底：HUD 图标属装饰性内容，绝不该因此崩掉游戏。
     */
    private static ItemStack totemIcon;

    /** 图腾图标是否已确认不可用（避免每次绘制都重复抛异常）。 */
    private static boolean totemIconUnavailable;

    /** 惰性获取图腾图标；组件尚未绑定或已确认不可用时返回 {@code null}。 */
    private static ItemStack totemIcon() {
        if (totemIcon == null && !totemIconUnavailable) {
            net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> holder =
                    Items.TOTEM_OF_UNDYING.builtInRegistryHolder();
            if (!holder.areComponentsBound()) {
                return null; // 主菜单/未加入世界：物品组件尚未绑定，此刻构造 ItemStack 会崩
            }
            try {
                totemIcon = new ItemStack(holder);
            } catch (Throwable t) {
                totemIconUnavailable = true; // 兜底：不显示图标，绝不崩游戏
                return null;
            }
        }
        return totemIcon;
    }

    /**
     * 由技能点 HUD 模块调用：在 (x, y) 处绘制不死图腾图标 + 冷却倒计时 / 就绪提示。
     * 模块已统一处理 hudVisible / 旁观 / 开界面隐藏，此处只画内容。
     */
    public static void renderInto(GuiGraphicsExtractor gui, int x, int y) {
        if (!learned) {
            return; // 未学/已重置/已关闭：隐藏
        }
        Minecraft mc = Minecraft.getInstance();
        // 不死图腾图标 16×16（惰性缓存的共享实例，避免每帧分配）
        ItemStack icon = totemIcon();
        if (icon != null) {
            gui.item(icon, x, y);
        }
        if (cooldownRemainingTicks > 0) {
            // 冷却中：暗色遮罩 + 剩余秒数
            gui.fill(x, y, x + 16, y + 16, 0x88000000);
            int seconds = (cooldownRemainingTicks + 19) / 20;
            gui.text(mc.font, seconds + "s", x + 18, y + 4, 0xFFFFAA55, false);
        } else {
            // 冷却就绪：亮色描边闪烁 + 「就绪」文字
            int flash = (int) ((System.currentTimeMillis() / 500) % 2);
            int c = flash == 0 ? 0xFFFFFF55 : 0xFFFFAA00;
            gui.fill(x - 1, y - 1, x + 17, y + 17, c);
            gui.fill(x, y, x + 16, y + 16, 0x22000000);
            gui.text(mc.font, net.minecraft.network.chat.Component.translatable(
                    "ui.zifeng_s_custom_skill_tree.revive_ready").getString(), x + 18, y + 4, c, false);
        }
    }
}
