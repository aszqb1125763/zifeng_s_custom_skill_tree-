package org.zifeng.skilltree.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 木棍手势载体判定（★ 2026-10-09，双版本同构）。
 *
 * <h2>为什么要有这个类</h2>
 * 木棍手势原先只有「主手/副手是原版木棍」一个条件，该判定<b>散落在 9 处</b>
 * （4 个客户端输入/渲染 + 2 个服务端事件 + HUD/nav 等）。新增「链接到手持物」后，
 * 条件变成「木棍本体 <b>或</b> 物品类型 == 玩家已链接的类型」，如果再散着改，
 * 迟早有漏改的入口导致「框选能用但绑定容器不认」这类行为不一致。
 * 故收敛到本类，所有入口统一调用。
 *
 * <h2>链接语义（用户 2026-10-09 明确）</h2>
 * <ul>
 *   <li>按<b>物品类型</b>而非具体实例 —— 附魔/自定义名不影响（「石头就是石头，剑就是剑」）</li>
 *   <li><b>单值</b>：新链接顶替旧的（「下一个顶替上一个」）</li>
 *   <li>已链接物品<b>不必是木棍</b>；木棍本体的能力<b>永远保留</b>，不因链接而失效</li>
 * </ul>
 *
 * <h2>⚠️ 多人安全</h2>
 * 本类<b>无任何静态可变状态</b>，全部参数由调用方传入 —— 链接数据存在各自的
 * {@code PlayerSkillRecord} 里（per-player NBT），绝不共享。
 */
public final class StickGesture {
    private StickGesture() {
    }

    /**
     * 取物品的<b>类型</b> id（如 {@code minecraft:stone}）。
     * <p>空栈 / 未注册物品 → 空串（调用方须把空串视为「永不匹配」）。
     */
    public static String itemIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Identifier rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return rl == null ? "" : rl.toString();
    }

    /** 把类型 id 解析回 {@link Item}（无效 → null）。客户端可缓存结果以省去字符串比较。 */
    public static Item resolve(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        Identifier rl = Identifier.tryParse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return null;
        }
        // ⚠️ 26.1.2：Registry#get(Identifier) 返回 Optional<Holder.Reference<T>>（不再是裸类型），
        // 取裸对象用 getValue(Identifier)（查不到返回 null，调用方本就按 null 处理）
        return BuiltInRegistries.ITEM.getValue(rl);
    }

    /**
     * 该物品是否可承载木棍手势。
     *
     * <p>★ 2026-10-09 <b>完全替换语义</b>（用户明确：「木棍工具替换的时候要顶替掉原木棍」）：
     * <ul>
     *   <li><b>未链接</b>：只有原版木棍可用（默认行为）</li>
     *   <li><b>已链接</b>：<b>只有</b>该物品类型可用，<b>木棍失效</b>（被顶替）</li>
     * </ul>
     * 想回到木棍：手持木棍再按一次触发键（= 链接木棍本体）即可恢复默认。
     *
     * <p>⚠️ 曾犯的错：初版写成「木棍本体永远可用，链接只是额外放开别的物品」（两者共存），
     * 用户要的是<b>替换</b>而非<b>叠加</b>，已改。
     *
     * @param stack        待判定的物品（主手或副手）
     * @param linkedItemId 该玩家已链接的物品类型 id（空串 = 未链接）
     * @return true = 该物品可承载木棍手势
     */
    public static boolean matches(ItemStack stack, String linkedItemId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (linkedItemId == null || linkedItemId.isEmpty()) {
            return stack.is(Items.STICK); // 未链接 → 木棍本体（默认状态）
        }
        // 已链接 → 只有该类型可用；此处【不再有】无条件的木棍分支（木棍已被顶替）
        return linkedItemId.equals(itemIdOf(stack));
    }

    /**
     * 主手<b>或</b>副手任一可承载木棍手势。
     * <p>用于渲染/HUD 这类「只要手上有就行」的判定；输入类入口请用 {@link #matches} 单独判主手。
     */
    public static boolean matchesEitherHand(Player player, String linkedItemId) {
        if (player == null) {
            return false;
        }
        return matches(player.getMainHandItem(), linkedItemId)
                || matches(player.getOffhandItem(), linkedItemId);
    }
}
