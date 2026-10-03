package org.zifeng.skilltree.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 垂钓技能（★ 2026-10-03 新增，第 11 列「垂钓」）。
 *
 * <p><b>核心设计</b>：产物由本类【自己抽原版钓鱼战利品表】产生 ——
 * <ul>
 *   <li>手动收杆：{@link ItemFishedEvent} 里【重抽】一遍并替换原版结果（这样我们的幸运才生效）；</li>
 *   <li>全自动垂钓：{@link #tick} 自带倒计时，<b>钩子不收不换、原封不动留在水里</b>（用户明确要求）。</li>
 * </ul>
 * <p>⚠️ 1.20.1 差异：事件包名（net.minecraftforge）、战利品表取法（getLootData()）、
 * 鱼竿能力接口（ToolActions.FISHING_ROD_CAST）、击杀者参数（KILLER_ENTITY）。
 * 因为产物是我们产的，幸运/数量/水域/熔炼/传送/爆发全部由我们控制 ——
 * <b>无需 mixin、无需改动原版钩子状态机、无需反射读咬钩状态</b>。
 *
 * <p><b>为什么不复用原版 {@code retrieve()}</b>：它会把钩子 {@code discard()} 掉，
 * 而「自动垂钓」要求鱼竿一直在原地（不收杆），两者矛盾；且它的幸运值在抽表时就固化了，
 * 无法体现「海神眷顾」。自己抽表反而更简单、更可控。
 */
public final class FishingEvents {

    private FishingEvents() {
    }

    // ══════════════ 数值常量（与 Skills 的上限/消耗曲线注释一一对应） ══════════════

    /** 急流垂钓：每级缩短的等待时间比例（−4%/级，上限 20 级 = −80%） */
    public static final double HASTE_PER_LEVEL = 0.04;
    /** 海神眷顾：每级追加的钓鱼幸运（原版权重：垃圾 quality −2 / 宝藏 quality +2） */
    public static final int FORTUNE_PER_LEVEL = 1;
    /**
     * 渔获满仓：每级【追加的独立抽取次数】（★ 2026-10-03 改为种类增长）。
     *
     * <p>机制：基础固定抽 1 份，每级再抽 1 份 → 满 50 级 = 51 份。
     * 每份都是【独立抽原版钓鱼战利品表】，所以产物种类自然随等级变多（用户要求）。
     * <p>★ 顺序（用户定稿）：本追加【在渔获爆发之前】执行 → 51 份全部参与爆发倍率。
     * 产量很大是有意为之：高倍率下玩家需要绑定容器（定点渔获）来接产物。
     */
    public static final int BOUNTY_ROLLS_PER_LEVEL = 1;
    /** 渔获经验：每级每次钓获追加的经验（满 25 级 = +50/次） */
    public static final int XP_PER_LEVEL = 2;

    /**
     * 钓鱼幸运硬上限（★ 2026-10-03 防御）。
     *
     * <p>权重公式 {@code weight + quality x luck}（鱼 quality −1 / 垃圾 −2 / 宝藏 +2）：
     * luck 接近 85 时鱼的权重变负数 → 鱼彻底消失。故必须封顶。
     * 取值 25：宝藏 55 / 鱼 60 / 垃圾 0 —— 宝藏很多但鱼仍占多数，不会"看不到鱼"。
     */
    private static final float MAX_FISHING_LUCK = 25.0F;

    /** 倒计时基准（照原版 catchingFish 的 timeUntilLured = 100~600 tick） */
    private static final int BASE_MIN_TICKS = 100;
    private static final int BASE_MAX_TICKS = 600;
    /** 倒计时下限：急流垂钓满级后约 3.5 秒/次，但绝不快于 1 秒/次（防数值失控） */
    private static final int MIN_TICKS = 20;

    /** 自动垂钓：玩家 → [钩子实体 id, 剩余 tick] */
    private static final Map<UUID, int[]> AUTO_TIMER = new HashMap<>();

    /** 反射字段缓存（1.20.1 / 1.21.1 字段名与可见性完全一致，已用 javap 核对） */
    private static java.lang.reflect.Field fLuck;
    private static java.lang.reflect.Field fOpenWater;
    private static boolean reflectTried;

    // ══════════════ 基础工具 ══════════════

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        try {
            return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 该技能当前的生效等级（未开启 = 0） */
    private static int lvl(PlayerSkillRecord record, String skillId) {
        return record.isEnabled(skillId) ? record.getActiveLevel(skillId) : 0;
    }

    /** 是否学过任何垂钓技能（没学过 → 整个垂钓系统零开销，连抽表都不做） */
    private static boolean hasAnyFishingSkill(PlayerSkillRecord record) {
        for (String id : Skills.FISHING_SKILLS) {
            if (record.getLearnedPoints(id) > 0) {
                return true;
            }
        }
        return false;
    }

    /** 惰性解析反射字段（失败只试一次，不刷屏） */
    private static void ensureReflect() {
        if (reflectTried) {
            return;
        }
        reflectTried = true;
        try {
            fLuck = FishingHook.class.getDeclaredField("luck");
            fLuck.setAccessible(true);
            fOpenWater = FishingHook.class.getDeclaredField("openWater");
            fOpenWater.setAccessible(true);
        } catch (Throwable t) {
            org.zifeng.skilltree.SkillTreeMod.LOGGER.warn(
                    "[垂钓] 反射 FishingHook 字段失败，幸运/无界垂钓将退化为原版行为: {}", t.toString());
        }
    }

    // ══════════════ 抽表（手动 / 自动共用） ══════════════

    /**
     * 抽取一次钓鱼产物（原版战利品表）。
     *
     * <p>幸运 = 钩子自带幸运（含海之眷顾附魔 + 玩家幸运）+ 海神眷顾等级 ×1。
     * 「无界垂钓」开启时临时把钩子的 {@code openWater} 置 true，
     * 让宝藏池的 {@code FishingHookPredicate.inOpenWater} 检查通过（抽完立刻还原）。
     */
    private static List<ItemStack> rollLoot(ServerPlayer player, FishingHook hook, ItemStack rod,
                                            PlayerSkillRecord record) {
        if (hook == null) {
            return new ArrayList<>();
        }
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return new ArrayList<>();
        }
        ensureReflect();
        int fortune = lvl(record, Skills.FISH_FORTUNE) * FORTUNE_PER_LEVEL;
        int hookLuck = 0;
        try {
            if (fLuck != null) {
                hookLuck = fLuck.getInt(hook);
            }
        } catch (Throwable ignored) {
        }
        // ★ 2026-10-03 修复：【不再计入 player.getLuck()（原版幸运属性）】。
        //    原因：本模组的「鸿运当头」(每点 +0.1，上限 100 级 = +10) 与「鸿运真解」(每点 +10%，
        //    上限 50 级 = +500%) 会把幸运属性顶到 60 以上；而钓鱼权重公式是 weight + quality×luck
        //    （鱼 quality −1 / 宝藏 quality +2）→ luck=60 时【宝藏 83%、鱼 17%】，
        //    实测表现就是「看不到鱼、只出命名牌那几种宝藏」。
        //    钓鱼幸运改为【独立轴】：只由 海之眷顾附魔（钩子自带 luck）+ 海神眷顾 组成，
        //    职责单一、可预测，且不会被其他技能意外放大。
        float luck = hookLuck + (float) fortune;
        if (luck > MAX_FISHING_LUCK) {
            luck = MAX_FISHING_LUCK; // 兜底：绝不让幸运把鱼的权重压成负数（否则 100% 出宝藏）
        }

        boolean forceOpenWater = lvl(record, Skills.FISH_OPEN_WATER) > 0;
        boolean savedOpen = false;
        boolean restored = false;
        if (forceOpenWater && fOpenWater != null) {
            try {
                savedOpen = fOpenWater.getBoolean(hook);
                fOpenWater.setBoolean(hook, true);
                restored = true;
            } catch (Throwable ignored) {
            }
        }
        try {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, hook.position())
                    .withParameter(LootContextParams.TOOL, rod)
                    .withParameter(LootContextParams.THIS_ENTITY, hook)
                    .withParameter(LootContextParams.KILLER_ENTITY, player)
                    .withLuck(luck)
                    .create(LootContextParamSets.FISHING);
            LootTable table = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
            List<ItemStack> list = table.getRandomItems(params);
            return list == null ? new ArrayList<>() : list;
        } catch (Throwable t) {
            org.zifeng.skilltree.SkillTreeMod.LOGGER.warn("[垂钓] 抽取战利品表失败: {}", t.toString());
            return new ArrayList<>();
        } finally {
            if (restored) {
                try {
                    fOpenWater.setBoolean(hook, savedOpen);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * 对产物套用全部垂钓技能（不含交付）。
     *
     * <p>顺序（有讲究）：渔获爆发过滤 → 渔获满仓放大 → 现钓现炼熔炼。
     * 熔炼放最后，避免「熔炼后的物品」再被数量放大时与原版倍率叠加出错。
     */
    private static void applySkills(ServerPlayer player, PlayerSkillRecord record,
                                    FishingHook hook, ItemStack rod, List<ItemStack> list) {
        if (list.isEmpty()) {
            return;
        }
        // ── 渔获满仓：追加 N 次【独立抽取】（默认 1 份 → 1 + 等级 份），产物种类随等级变多 ──
        //    ★ 2026-10-03 改造：原为「份数 ×(1+0.2×等级)」（同种物品变多），改为「多抽几次」（种类变多）。
        int bountyLevel = lvl(record, Skills.FISH_BOUNTY);
        if (bountyLevel > 0) {
            for (int i = 0; i < bountyLevel * BOUNTY_ROLLS_PER_LEVEL; i++) {
                List<ItemStack> extra = rollLoot(player, hook, rod, record);
                if (extra != null && !extra.isEmpty()) {
                    list.addAll(extra);
                }
            }
        }
        // ── 渔获爆发（需【已学并开启「渔获爆发」】，再取战利品大爆发的倍率）──
        //    ⚠️ 2026-10-03 修复：原实现直接取 LOOT_BOMB 等级，没先查渔获爆发开关→
        //    只要学了战利品大爆发，【没买渔获爆发的玩家钓鱼也会跟着爆发】（用户实测反馈）。
        int bombLevel = lvl(record, Skills.FISH_BOMB) > 0 ? lvl(record, Skills.LOOT_BOMB) : 0;
        if (bombLevel > 0) {
            int maxMult = org.zifeng.skilltree.Config.LOOT_BOMB_MAX_MULTIPLIER.get();
            int mult = Math.min(maxMult, 1 + bombLevel);
            int mode = record.getAuraTargetMode(Skills.LOOT_BOMB);
            // 黑名单模式：命中的【完全不产出】
            if (mode == Skills.LOOT_MODE_BLACKLIST) {
                list.removeIf(s -> s != null && !s.isEmpty() && record.isLootBlacklisted(s));
            }
            if (mult > 1) {
                list.removeIf(s -> s == null || s.isEmpty());
                // 模式过滤（仅堆叠 / 仅不堆叠：只放大符合条件的，其余保持原样）
                List<ItemStack> targets = new ArrayList<>();
                for (ItemStack s : list) {
                    if (lootModeAllows(mode, s)) {
                        targets.add(s);
                    }
                }
                if (!targets.isEmpty()) {
                    int before = targets.size();
                    // ⚠️ applyDropMultiplierStacks 需要玩家取随机源；它把额外产物 append 到传入列表
                    UltimateEvents.applyDropMultiplierStacks(targets, player, mult);
                    for (int i = before; i < targets.size(); i++) {
                        list.add(targets.get(i));
                    }
                }
            }
        }
        // ── 渔获满仓：追加 N 次【独立抽取】已上移到【渔获爆发之前】（额外份数也吃爆发倍率）──
        // ── 现钓现炼：熔炼放最后（避免把熔炼产物再卷进倍率计算）──
        if (lvl(record, Skills.FISH_SMELT) > 0) {
            UltimateEvents.applyAutoSmelt(player, list, record);
        }
        list.removeIf(s -> s == null || s.isEmpty());
    }

    /** 战利品大爆发的模式过滤（与 UltimateEvents 同义；黑名单模式已在上面直接移除） */
    private static boolean lootModeAllows(int mode, ItemStack stack) {
        if (mode == Skills.LOOT_MODE_STACKABLE) {
            return stack.getMaxStackSize() > 1;
        }
        if (mode == Skills.LOOT_MODE_UNSTACKABLE) {
            return stack.getMaxStackSize() == 1;
        }
        return true;
    }

    // ══════════════ 交付（定点渔获 → 背包 → 脚下） ══════════════

    /**
     * 交付产物（用户定稿：学了「定点渔获」且已绑定容器 → 直传容器；
     * 否则进背包；背包满 → 掉在浮标处）。
     *
     * @param spawnPos 掉落物备用落点（浮标位置）；null 表示用玩家位置
     */
    private static void deliver(ServerPlayer player, PlayerSkillRecord record, List<ItemStack> list,
                                BlockPos spawnPos) {
        if (list.isEmpty()) {
            return;
        }
        boolean vacuum = lvl(record, Skills.FISH_VACUUM) > 0 && record.hasLootVacuumBind();
        LootVacuumEvents.BoundTarget target = vacuum ? LootVacuumEvents.resolveBoundTarget(player, record) : null;
        for (ItemStack stack : list) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack rest = stack;
            if (target != null) {
                rest = LootVacuumEvents.insertIntoTarget(target, rest); // 未塞下的剩余继续走背包
            }
            if (rest == null || rest.isEmpty()) {
                continue;
            }
            if (player.getInventory().add(rest)) {
                continue; // 进背包成功
            }
            dropAt(player, spawnPos, rest); // 背包满 → 掉在浮标处
        }
    }

    /** 在指定位置（或玩家位置）生成掉落物实体 */
    private static void dropAt(ServerPlayer player, BlockPos pos, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        double x = pos != null ? pos.getX() + 0.5 : player.getX();
        double y = pos != null ? pos.getY() + 0.5 : player.getY() + 0.5;
        double z = pos != null ? pos.getZ() + 0.5 : player.getZ();
        ItemEntity drop = new ItemEntity(level, x, y, z, stack);
        drop.setPickUpDelay(10);
        level.addFreshEntity(drop);
    }

    /**
     * 发放经验。
     *
     * @param vanillaEquivalent true = 补发原版「每件 1~6 点」的经验
     *                          （仅在【不走原版循环】的路径需要：定点渔获取消事件 / 全自动垂钓），
     *                          否则会与「定点渔获」一起把经验静默吞掉
     */
    private static void grantXp(ServerPlayer player, PlayerSkillRecord record, List<ItemStack> list,
                                boolean vanillaEquivalent) {
        int total = 0;
        if (vanillaEquivalent) {
            for (ItemStack s : list) {
                if (s != null && !s.isEmpty()) {
                    for (int i = 0; i < s.getCount(); i++) {
                        total += player.getRandom().nextInt(6) + 1; // 原版：每件一个 1~6 经验球
                    }
                }
            }
        }
        // 渔获经验：每次钓获（不随份数放大，避免与渔获满仓乘算）
        int xpLevel = lvl(record, Skills.FISH_XP);
        if (xpLevel > 0) {
            total += XP_PER_LEVEL * xpLevel;
        }
        if (total > 0) {
            player.giveExperiencePoints(total);
        }
    }

    /** 产鱼反馈：浮标处水花 + 原版咬钩音效（用户定稿） */
    private static void playCatchFeedback(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (level == null || pos == null) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 8, 0.2, 0.0, 0.2, 0.05);
        level.playSound(null, x, y, z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL,
                0.35F, 1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F);
    }

    // ══════════════ ① 手动收杆（ItemFishedEvent） ══════════════

    /**
     * 手动收杆时重抽产物并套用垂钓技能。
     *
     * <p>为什么要「重抽」而不是直接改原版结果：原版的幸运值在抽表那一刻就固化了，
     * 事后再改列表无法体现「海神眷顾」（原版靠 {@code setQuality} 的权重公式）。
     * 重抽时把我们的幸运一起算进去，规则才真正生效。
     */
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        if (record == null || !hasAnyFishingSkill(record)) {
            return; // 没学任何垂钓技能 → 完全不干预（零开销、原版行为）
        }
        FishingHook hook = player.fishing;
        ItemStack rod = player.getMainHandItem();
        List<ItemStack> rolled = rollLoot(player, hook, rod, record);
        if (rolled.isEmpty()) {
            // ⚠️ 防御：原版钓鱼表 rolls=1 必定有产物 → 空 = 抽表失败。
            //    此时【绝不】清空原版结果（否则玩家白钓一次、静默丢物），直接放弃干预。
            return;
        }
        List<ItemStack> list = new ArrayList<>(rolled);
        applySkills(player, record, hook, rod, list);
        if (list.isEmpty()) {
            return; // 全被黑名单吃掉等情形 → 保持原版结果（不干预）
        }

        // 定点渔获：直传容器；全部送完则取消事件（不生成掉落物实体）
        boolean allMoved = false;
        if (lvl(record, Skills.FISH_VACUUM) > 0 && record.hasLootVacuumBind()) {
            LootVacuumEvents.BoundTarget target = LootVacuumEvents.resolveBoundTarget(player, record);
            if (target != null) {
                allMoved = true;
                List<ItemStack> keep = new ArrayList<>();
                for (ItemStack s : list) {
                    if (s == null || s.isEmpty()) {
                        continue;
                    }
                    ItemStack rest = LootVacuumEvents.insertIntoTarget(target, s);
                    if (rest != null && !rest.isEmpty()) {
                        keep.add(rest); // 容器满 → 保留，走原版掉落
                        allMoved = false;
                    }
                }
                list = keep;
            }
        }

        event.getDrops().clear();
        event.getDrops().addAll(list);
        if (allMoved && list.isEmpty()) {
            // 全部进容器 → 取消事件（原版不会生成掉落物实体，但会按 getRodDamage 扣耐久）
            grantXp(player, record, new ArrayList<>(), true);
            event.setCanceled(true);
        } else {
            // 未取消：原版会按列表逐件生成掉落物 + 经验球 → 只需补「渔获经验」
            int xpLevel = lvl(record, Skills.FISH_XP);
            if (xpLevel > 0) {
                player.giveExperiencePoints(XP_PER_LEVEL * xpLevel);
            }
        }
        if (hook != null && !list.isEmpty()) {
            playCatchFeedback(player, hook.blockPosition());
        }
    }

    // ══════════════ ② 全自动垂钓（Z-Link 模块驱动） ══════════════

    /** 模块活跃条件（每 tick 轻量评估）：学了「全自动垂钓」且钩子还在 */
    public static boolean shouldTick(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        PlayerSkillRecord record = getRecord(player);
        if (record == null
                || record.getLearnedPoints(Skills.FISH_AUTO) <= 0
                || !record.isEnabled(Skills.FISH_AUTO)) {
            return false;
        }
        FishingHook hook = player.fishing;
        return hook != null && hook.isAlive();
    }

    /**
     * 全自动垂钓 tick：钩子在水里就倒计时，到点产鱼。
     *
     * <p><b>关键</b>：全程不动钩子 —— 不收杆、不重抛、不新建，
     * 所以鱼竿一直"在那里"（用户明确要求「删除反复抛竿的过程」）。
     * 原版钩子自己仍在水里循环（浮标照常沉浮），保证观感正常。
     */
    public static void tick(ServerPlayer player) {
        FishingHook hook = player.fishing;
        UUID id = player.getUUID();
        if (hook == null || !hook.isAlive() || !hookInWater(hook)) {
            AUTO_TIMER.remove(id); // 没钩子 / 钩子不在水里 → 停表（收杆后重新抛会重新计时）
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        if (record == null) {
            return;
        }
        int hookId = hook.getId();
        int[] state = AUTO_TIMER.get(id);
        if (state == null || state[0] != hookId) {
            // 首次（或换了新钩子）→ 掷基准倒计时
            AUTO_TIMER.put(id, new int[]{hookId, rollBaseTicks(record)});
            return;
        }
        int left = state[1] - 1;
        if (left > 0) {
            state[1] = left;
            return;
        }
        // 到点 → 产鱼
        ItemStack rod = player.getMainHandItem();
        List<ItemStack> list = new ArrayList<>(rollLoot(player, hook, rod, record));
        applySkills(player, record, hook, rod, list);
        BlockPos pos = hook.blockPosition();
        if (!list.isEmpty()) {
            playCatchFeedback(player, pos);
            // 耐久：每次钓获扣 1（与原版收杆一致；「万载不磨」可免除）。
            //   ⚠️ 2026-10-03 由「每件扣 1」改回「每次扣 1」：
            //   满仓满级一次产 51 件（再乘爆发倍率可达数千件）→ 按件扣会让鱼竿瞬间报废。
            if (!damageRod(player, 1)) {
                AUTO_TIMER.remove(id); // 竿没了 → 停表，不再产出
                return;
            }
            deliver(player, record, list, pos);
        }
        grantXp(player, record, list, true); // 全自动路径不走原版循环 → 补发原版经验
        AUTO_TIMER.put(id, new int[]{hookId, rollBaseTicks(record)});
    }

    /** 掷一次倒计时：原版随机 100~600 tick，按急流垂钓等级缩短 */
    private static int rollBaseTicks(PlayerSkillRecord record) {
        int base = BASE_MIN_TICKS
                + (int) (Math.random() * (BASE_MAX_TICKS - BASE_MIN_TICKS + 1));
        int hasteLevel = lvl(record, Skills.FISH_HASTE);
        double reduce = Math.min(1.0, hasteLevel * HASTE_PER_LEVEL);
        int ticks = (int) Math.round(base * (1.0 - reduce));
        return Math.max(MIN_TICKS, ticks);
    }

    /** 产物总件数（用于扣耐久） */
    private static int countItems(List<ItemStack> list) {
        int n = 0;
        for (ItemStack s : list) {
            if (s != null && !s.isEmpty()) {
                n += s.getCount();
            }
        }
        return n;
    }

    /**
     * 扣鱼竿耐久（每件 1 点）。
     *
     * @return true = 竿还在（可以继续自动垂钓）；false = 已经损坏
     */
    private static boolean damageRod(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return true;
        }
        var main = player.getMainHandItem();
        if (isFishingRod(main)) {
            // ⚠️ 1.20.1 的 hurtAndBreak 第三参是 Consumer（无 getSlotForHand）→ 照抄原版 FishingRodItem 写法
            main.hurtAndBreak(amount, player,
                    p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
            return !main.isEmpty();
        }
        var off = player.getOffhandItem();
        if (isFishingRod(off)) {
            off.hurtAndBreak(amount, player,
                    p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.OFFHAND));
            return !off.isEmpty();
        }
        return false; // 手上没有鱼竿 → 停
    }

    /** 是否鱼竿（用加载器的能力接口判断 → 模组鱼竿同样识别） */
    private static boolean isFishingRod(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.canPerformAction(net.minecraftforge.common.ToolActions.FISHING_ROD_CAST);
    }

    /** 钩子是否在水里（浮标所在格或其下方一格是水） */
    private static boolean hookInWater(FishingHook hook) {
        var level = hook.level();
        BlockPos p = hook.blockPosition();
        if (level.getFluidState(p).is(FluidTags.WATER)) {
            return true;
        }
        return level.getFluidState(p.below()).is(FluidTags.WATER);
    }

    // ══════════════ 生命周期清理 ══════════════

    /** 玩家登出：清该玩家的倒计时（防跨会话残留） */
    public static void clearPlayer(ServerPlayer player) {
        if (player != null) {
            AUTO_TIMER.remove(player.getUUID());
        }
    }

    /** 服务器停止：清空全部倒计时 */
    public static void clearAll() {
        AUTO_TIMER.clear();
    }
}
