package org.zifeng.skilltree.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.SkillTreeMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 硬杀补掉落（2026-10-21）。
 *
 * <h2>为什么需要它</h2>
 * 有些 Boss 用<b>延迟死亡</b>机制结算掉落（典型：植物魔法盖亚守护者 {@code botania:doppleganger}）。
 * 正常打死时掉落是在后续 tick 的死亡流程里发的；而我们的虚空诛灭 / 杀戮弹幕走
 * 「1 亿混沌伤害 ×3 → 兜底 {@code setHealth(0)+die()}」的硬杀，实体当场消失，
 * 那段延迟流程没跑 → <b>掉落表从未被抽取，掉落为空</b>。
 *
 * <h2>怎么做</h2>
 * 配置 {@code hardKillLoot.manualLootGrant} 是「实体ID;战利品表ID」列表。硬杀结束时若目标命中配置，
 * 就自己按战利品表抽一次并交给击杀者（参考 Spear of Void 的 {@code LootGrantUtil} 思路，
 * 代码为本项目自行实现）。
 *
 * <p>⚠️ 不能靠 {@code LivingDropsEvent} 拦：这类 Boss 根本不会触发正常掉落事件，只能主动抽表。
 * <p>⚠️ 只在<b>硬杀通道</b>调用（{@code AuraEvents#voidSpearKill}），普通 {@code hurt()} 打死不调用，
 * 避免和原版掉落重复。同一实体用弱引用集合去重，防止多通道重复补发。
 */
public final class HardKillLootGrant {

    /** 已补发过的实体（弱引用，实体卸载后自动回收） */
    private static final Set<LivingEntity> GRANTED =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    private HardKillLootGrant() {
    }

    /**
     * 若目标命中配置表，则按其战利品表补发一次掉落给击杀者。
     *
     * @param killer 击杀者（可为 null，null 时只按世界坐标掉落）
     * @param dead   刚被硬杀死的实体
     */
    public static void grantIfConfigured(ServerPlayer killer, LivingEntity dead) {
        if (dead == null || dead.level().isClientSide() || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        // ★ 2026-10-21：这是【杀戮光环】的支线效果 —— 没学杀戮光环（或没有击杀者）就不补
        if (killer == null) {
            return;
        }
        if (org.zifeng.skilltree.data.PlayerSkillSavedData.get(level)
                .getOrCreatePlayer(killer.getUUID())
                .getLearnedPoints(org.zifeng.skilltree.skill.Skills.AURA_DAMAGE) <= 0) {
            return;
        }
        final List<String> entries;
        try {
            entries = new ArrayList<>(Config.HARD_KILL_LOOT_GRANT.get());
        } catch (Exception e) {
            return; // 配置未加载（早期初始化）→ 静默跳过
        }
        if (entries.isEmpty()) {
            return;
        }
        final ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        if (entityId == null) {
            return;
        }
        final String entityKey = entityId.toString();
        final List<String> tables = new ArrayList<>(2);
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            final int sep = entry.indexOf(';');
            if (sep <= 0 || sep >= entry.length() - 1) {
                continue;
            }
            if (!entry.substring(0, sep).trim().equals(entityKey)) {
                continue;
            }
            tables.add(entry.substring(sep + 1).trim());
        }
        if (tables.isEmpty()) {
            return;
        }
        // ★ 2026-10-21：只在「原版本来就不会掉」时补 —— 掉落表非空说明原版会正常掉，
        //   再补就是双掉（调用方可能是死亡事件兜底，也可能是硬杀分支，两边都会进来）
        // 用字符串比较而不是 BuiltInLootTables.EMPTY：26.1.2 已没有该常量，
        // 而这个常量的值在所有版本都是 minecraft:empty（版本无关写法）
        final boolean vanillaDrops = !"minecraft:empty".equals(dead.getLootTable().toString());
        if (vanillaDrops) {
            SkillTreeMod.LOGGER.info("[硬杀补掉落] {} 命中配置但原版掉落表非空，跳过", entityKey);
            return;
        }
        if (!GRANTED.add(dead)) {
            return; // 已经补过（多通道/多次调用）
        }
        final String tableId = pickTable(tables, isHardMode(dead));
        final boolean hard = isHardMode(dead);
        SkillTreeMod.LOGGER.info("[硬杀补掉落] {} 命中配置，按 {} 补发（hardMode={}）", entityKey, tableId, hard);
        final ResourceLocation tableLoc = ResourceLocation.tryParse(tableId);
        if (tableLoc == null) {
            return;
        }
        try {
            final LootTable table = level.getServer().reloadableRegistries()
                    .getLootTable(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.LOOT_TABLE, tableLoc));
            if (table == null || table == LootTable.EMPTY) {
                SkillTreeMod.LOGGER.warn("[硬杀补掉落] 找不到战利品表 {}", tableId);
                return;
            }
            // ★ 2026-10-21（多人兼容）：Botania 的 dropFromLootTable 是【每个参战玩家各抽一次表】，
            //   并且把 Boss 临时挪到该玩家脚下再抽 → 每人的那份掉在自己脚下。
            //   这里照抄同样的语义：参战名单反射读 playersWhoAttacked，读不到就退回击杀者。
            final List<ServerPlayer> participants = resolveParticipants(level, dead, killer);
            // true_guardian_killer 条件读 Boss 的 trueKiller 字段：抽表期间临时填成真正的击杀者，
            // 这样击杀者那份拿 8 个生命精华、其他人 6 个（与原版一致）
            final Object oldTrueKiller = readField(dead, "trueKiller");
            writeField(dead, "trueKiller", killer);
            try {
                for (ServerPlayer p : participants) {
                    final var source = level.damageSources().playerAttack(p);
                    final LootParams.Builder builder = new LootParams.Builder(level)
                            .withParameter(LootContextParams.THIS_ENTITY, dead)
                            .withParameter(LootContextParams.ORIGIN, p.position())
                            .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, p)
                            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, p)
                            // ★ Botania 的 killed_by_player 条件读的是 LAST_DAMAGE_PLAYER，必须传
                            .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, p)
                            .withOptionalParameter(LootContextParams.TOOL, p.getMainHandItem());
                    final List<ItemStack> rolled = table.getRandomItems(builder.create(LootContextParamSets.ENTITY));
                    if (rolled.isEmpty()) {
                        continue;
                    }
                    // 像真实生物掉落：生成 ItemEntity（不直接塞背包），带随机散射与拾取延迟
                    final List<net.minecraft.world.entity.item.ItemEntity> batch = new ArrayList<>();
                    for (ItemStack stack : rolled) {
                        if (stack == null || stack.isEmpty()) {
                            continue;
                        }
                        batch.add(newDrop(level, p, stack));
                    }
                    // 战利品大爆发：用该玩家自己的等级/模式放大（与 LivingDropsEvent 同一套规则）
                    org.zifeng.skilltree.event.UltimateEvents.applyLootBombToDrops(p, batch);
                    // ★ 2026-10-21：兼容【挪移术】（凋落物挪移）—— 先尝试直传该玩家绑定的容器，
                    //   能进容器的会从 batch 里移除（和真实击杀时的执行顺序一致：放大 → 挪移 → 剩下才落地）
                    org.zifeng.skilltree.event.LootVacuumEvents.tryVacuumDrops(
                            p, org.zifeng.skilltree.event.UltimateEvents.getRecordFor(p), batch);
                    for (net.minecraft.world.entity.item.ItemEntity ie : batch) {
                        level.addFreshEntity(ie);
                    }
                    SkillTreeMod.LOGGER.info("[硬杀补掉落] {} -> {} 补发 {} 件", tableId,
                            p.getName().getString(), batch.size());
                }
            } finally {
                writeField(dead, "trueKiller", oldTrueKiller);
            }
        } catch (Exception e) {
            SkillTreeMod.LOGGER.warn("[硬杀补掉落] {} 按 {} 抽表失败（不影响游戏）：{}", entityKey, tableId, e.toString());
        }
    }

    /** 生成一个「像原版掉落」的 ItemEntity：落在玩家脚下、带随机散射、默认拾取延迟 */
    private static net.minecraft.world.entity.item.ItemEntity newDrop(ServerLevel level, ServerPlayer p, ItemStack stack) {
        final var rand = level.getRandom();
        final double dx = p.getX() + (rand.nextDouble() - 0.5) * 0.5;
        final double dy = p.getY() + 0.2 + rand.nextDouble() * 0.3;
        final double dz = p.getZ() + (rand.nextDouble() - 0.5) * 0.5;
        final var ie = new net.minecraft.world.entity.item.ItemEntity(level, dx, dy, dz, stack);
        ie.setDeltaMovement((rand.nextDouble() - 0.5) * 0.1, 0.15, (rand.nextDouble() - 0.5) * 0.1);
        ie.setDefaultPickUpDelay();
        return ie;
    }

    /** 参战名单：反射读 Botania 的 playersWhoAttacked；读不到就用击杀者一人 */
    private static List<ServerPlayer> resolveParticipants(ServerLevel level, LivingEntity dead, ServerPlayer killer) {
        final List<ServerPlayer> out = new ArrayList<>(2);
        try {
            final java.lang.reflect.Field f = dead.getClass().getDeclaredField("playersWhoAttacked");
            f.setAccessible(true);
            final Object v = f.get(dead);
            if (v instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof java.util.UUID id)) {
                        continue;
                    }
                    final ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                    if (p != null && !out.contains(p)) {
                        out.add(p);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 没有这个字段（非植物魔法 Boss / 版本差异）→ 退回击杀者
        }
        if (killer != null && !out.contains(killer)) {
            out.add(killer);
        }
        return out;
    }

    private static Object readField(LivingEntity entity, String name) {
        try {
            final var f = entity.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(entity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeField(LivingEntity entity, String name, Object value) {
        try {
            final var f = entity.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(entity, value);
        } catch (Throwable ignored) {
            // 忽略：只是拿不到 true killer 加成
        }
    }


    /** 该实体是否命中配置表（只看配置，不看技能/掉落表状态） */
    public static boolean matchesConfig(LivingEntity dead) {
        return !matchingTables(dead).isEmpty();
    }

    /** 该实体命中的战利品表 id（按配置顺序；同实体多条时第一条为普通、`_2` 结尾为困难） */
    public static List<String> matchingTables(LivingEntity dead) {
        if (dead == null) {
            return List.of();
        }
        final List<String> entries;
        try {
            entries = new ArrayList<>(Config.HARD_KILL_LOOT_GRANT.get());
        } catch (Exception e) {
            return List.of();
        }
        final net.minecraft.resources.ResourceLocation id;
        if (dead.getType() == null) {
            return List.of();
        }
        id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        if (id == null) {
            return List.of();
        }
        final String key = id.toString();
        final List<String> tables = new ArrayList<>(2);
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            final int sep = entry.indexOf(';');
            if (sep <= 0 || sep >= entry.length() - 1) {
                continue;
            }
            if (entry.substring(0, sep).trim().equals(key)) {
                tables.add(entry.substring(sep + 1).trim());
            }
        }
        return tables;
    }

    /** 击杀者是否已学【杀戮光环】（这条支线的生效条件） */
    public static boolean hasBranchSkill(ServerPlayer killer, ServerLevel level) {
        if (killer == null || level == null) {
            return false;
        }
        return org.zifeng.skilltree.data.PlayerSkillSavedData.get(level)
                .getOrCreatePlayer(killer.getUUID())
                .getLearnedPoints(org.zifeng.skilltree.skill.Skills.AURA_DAMAGE) > 0;
    }

    /**
     * 打开「召唤阶段的掉落锁」：Botania 的 {@code getLootTable()} 在 {@code mobSpawnTicks > 0} 时
     * 返回 {@code BuiltInLootTables.EMPTY}。把该字段归零后，实体自己的死亡掉落流程就能正常抽表
     * （多人分摊、硬编码掉落、战利品大爆发、挪移术全都由原版链路自然处理）。
     *
     * @return true = 已解锁且掉落表不再为空 → 交给原版，调用方不要再手动补
     */
    public static boolean tryReleaseSpawnLock(LivingEntity dead) {
        final Object ticks = readField(dead, "mobSpawnTicks");
        if (!(ticks instanceof Integer t) || t <= 0) {
            return false; // 不是「召唤阶段被锁表」这种情况
        }
        // Botania 的 dropFromLootTable 只按 playersWhoAttacked 逐人抽表：列表为空时原生链路会掉空，
        // 这种情况交给手动兜底（会把击杀者算作一份）
        final Object participants = readField(dead, "playersWhoAttacked");
        if (participants instanceof List<?> list && list.isEmpty()) {
            return false;
        }
        writeField(dead, "mobSpawnTicks", 0);
        final boolean released = !"minecraft:empty".equals(dead.getLootTable().toString());
        if (released) {
            SkillTreeMod.LOGGER.info("[硬杀补掉落] {} 召唤阶段掉落锁已解除，交由原版掉落", dead.getType());
        }
        return released;
    }

    /**
     * 同一实体多条配置时的选表规则：困难模式（{@code isHardMode()==true}）优先 {@code _2} 结尾的表，
     * 普通模式优先其余；都不匹配时退回第一条。
     */
    private static String pickTable(List<String> tables, boolean hardMode) {
        for (String t : tables) {
            final boolean isHardTable = t.endsWith("_2");
            if (hardMode == isHardTable) {
                return t;
            }
        }
        return tables.get(0);
    }

    /**
     * 软探测困难模式（盖亚 I/II 用来选表）。
     *
     * <p>Botania 1.20.1 的 {@code GaiaGuardianEntity} <b>没有</b> isHardMode() 方法，
     * 只有一个私有字段 {@code hardMode}（NBT 里也叫 hardMode）→ 先试方法，再退回读字段；
     * 两者都拿不到就按普通模式处理（只影响多给的那部分战利品，不会崩）。
     */
    private static boolean isHardMode(LivingEntity entity) {
        try {
            final var m = entity.getClass().getMethod("isHardMode");
            final Object r = m.invoke(entity);
            if (r instanceof Boolean b) {
                return b;
            }
        } catch (Throwable ignored) {
            // 没有这个方法：继续走字段
        }
        try {
            final var f = entity.getClass().getDeclaredField("hardMode");
            f.setAccessible(true);
            final Object r = f.get(entity);
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
