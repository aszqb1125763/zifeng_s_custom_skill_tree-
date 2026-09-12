package org.zifeng.skilltree.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.zifeng.skilltree.data.OperZone;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.List;

/**
 * 机械共鸣·区块技能执行器（2026-09-08，1.20.1）：
 * <ul>
 *   <li>选区放置（triggerPlace）：手持物品=标签（决定放什么方块），按触发键执行一次。</li>
 *   <li>选区挖掘（triggerExcavate）：手持工具=标签（只供附魔，不耗耐久），按触发键瞬间挖空全区。</li>
 *   <li>选区攻击：无触发键，开启后 tick 自动攻击区内生物（频率/伤害同杀戮光环）。</li>
 *   <li>防护选区（多块，上限 10）：区内生物对【全服任何人的杀戮光环/选区攻击】不可见（目标扫描剔除）——
 *       只屏蔽杀戮光环伤害（含其范围判定与虚空直杀），不拦怪物/环境/普通近战等其它来源。</li>
 * </ul>
 * 单人生效：操作区存各玩家 PlayerSkillRecord。
 */
public final class ZoneSkillEvents {
    private ZoneSkillEvents() {
    }

    // ============ 分批处理（2026-09-12 1.4.1）：大选区跳 tick 平滑处理 ============
    //
    // 背景：原实现「单次触发最多处理 32768 格」有两个问题：
    //   ① 框大区只能处理一部分（需多次按键，体验差）
    //   ② 32768 格在【同一帧】内跑完本身就是明显卡顿源（挖掘还要算掉落/熔炼/塞容器）
    // 现改为【每 tick 时间预算推进】的作业队列：
    //   · 触发一次 = 建作业（选区快照）+ 本 tick 立即推进一片 → 小选区仍"一键即完"（手感不变）
    //   · 大选区跳多 tick 继续跑，每 tick 最多花 TICK_BUDGET_NANOS → 服务器不卡
    //   · 再按一次触发键 = 取消当前作业（可中断，避免大区"没完没了"）
    //   · 技能关掉 / 换维度 / 选区被改 / 登出 → 自动取消
    // 进度用动作栏提示（每 PROGRESS_REPORT_TICKS tick 一次）。

    /**
     * 本 tick 作业的**绝对截止时刻**（nanoTime）—— 自适应预算：
     * <b>目标 = 本 tick 总耗时不超过配置的 {@code zoneMspLimitMs}</b>。
     * <p>原理：本模块在 {@code ServerTickEvent} <b>末尾</b>运行，此时实体/方块/网络等主要工作已完成
     * → {@code tickStart + limit} 天然把"本 tick 已耗时"扣除，只用真剩下的时间推进作业：
     * <ul>
     *   <li>空闲服（基础开销小）→ 拿到接近 full limit 的预算 → 跑得快</li>
     *   <li>忙碌服（基础开销已接近/超过 limit）→ 预算接近 0 → <b>自动让路，不雪上加霜</b></li>
     * </ul>
     * ⚠️ 2026-09-12（1.4.1）：初版固定 5ms 太慢；第二版固定 25ms 不感知服务器负载；
     * 现为用户实测反馈后改为本自适应方案（配置项 {@code zoneMspLimitMs}）。
     *
     * @return 截止时刻；若 {@code TickClock} 不可用（返回 0）则回退为"从现在起 limit"
     */
    private static long jobDeadlineNanos() {
        long limit = Math.max(1, org.zifeng.skilltree.Config.ZONE_MSP_LIMIT_MS.get()) * 1_000_000L;
        long tickStart = org.zifeng.skilltree.system.TickClock.tickStartNanos();
        if (tickStart == 0L) {
            return System.nanoTime() + limit; // 时钟不可用 → 回退固定预算
        }
        return tickStart + limit;
    }

    /**
     * 时间检查间隔（格）：每处理 N 格才调一次 {@code System.nanoTime()}。
     * <p>单格处理常在 1μs 量级，而 nanoTime() 自身约 20~30ns；逐格检查会白白吃掉几个百分点。
     * 64 格的粒度足以把超时控制在预算 + 几十微秒内。
     */
    private static final int TIME_CHECK_INTERVAL = 64;

    /** 进度上报间隔（tick；20 tick = 1 秒） */
    private static final int PROGRESS_REPORT_TICKS = 20;

    /** 进行中的选区作业（玩家 UUID → 作业）。单人生效，每人最多一个。 */
    private static final java.util.Map<java.util.UUID, Job> JOBS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 一次选区作业（放置或挖掘）：选区快照 + 遍历状态 + 本次改动计数。
     *
     * <p><b>遍历顺序：X 最快 → Z → Y 最慢，且 Y 从高到低（自上而下逐层）。</b>
     * <p>高→低的好处：上层重力方块（沙/砾石）在被处理时其下方支撑还在，会被**直接挖掉**，
     * 不会因为支撑先被抽走而转化为掉落物（避免成千上万个掉落实体）。
     * <p>⚠️ 2026-09-12 曾试过层内按区块分组（C 方案）以提升缓存局部性，用户确认不需要。
     */
    private static final class Job {
        final String skillId;      // MACHINE_ZONE_PLACE / MACHINE_ZONE_EXCAVATE
        final String dim;
        final OperZone zone;       // 快照：作业期间不受"重新框选"影响
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final int total;           // 总格数（进度分母）
        final BlockState placeState;              // 仅放置：要放的方块状态
        final BlockItem tagItem;                  // 仅放置：标签物品（取材料比对用）

        // ---- 遍历状态（curX 用 minX-1 哨兵，使首次 advanceCursor() 落到 minX） ----
        int curX, curY, curZ;

        int scanned;               // 已扫描格数（含空气；进度分子）
        int changed;               // 实际改动方块数
        long lastReportTick;       // 上次进度上报的 tick
        /** 作业开始时刻（nanoTime）：完成后输出精确耗时到日志（2026-09-12，供调参参考） */
        final long startNanos = System.nanoTime();

        // ---- 掉落物攒批（2026-09-12 1.4.1 性能优化） ----
        /** 本 tick 是否已绑定容器/AE（决定掉落是"攒起来批量入"还是"直接丢弃"） */
        boolean vacuumBound;
        /** 本 tick 攒下的掉落物（flushDrops 时一次入容器，不再逐物品解析目标） */
        final java.util.List<ItemStack> pendingDrops = new java.util.ArrayList<>();

        // ---- 分项计时（2026-09-12：定位真实瓶颈，完成时打进日志） ----
        long tDrops;   // Block.getDrops（战利品表）
        long tSmelt;   // 自动熔炼 / 点石成金
        long tRemove;  // 移除方块
        long tInsert;  // 掉落入容器

        Job(String skillId, String dim, OperZone zone, BlockState placeState, BlockItem tagItem) {
            this.skillId = skillId;
            this.dim = dim;
            this.zone = zone;
            this.placeState = placeState;
            this.tagItem = tagItem;
            this.minX = zone.minX();
            this.minY = zone.minY();
            this.minZ = zone.minZ();
            this.maxX = zone.maxX();
            this.maxY = zone.maxY();
            this.maxZ = zone.maxZ();
            this.total = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            this.curX = minX - 1; // 哨兵（见字段注释）
            this.curY = maxY;     // ★ 从最高层开始（Y 高→低）
            this.curZ = minZ;
        }

        /** 推进到下一个坐标；无更多坐标返回 false。顺序：X → Z → Y，且 Y 从高到低 */
        boolean advanceCursor() {
            if (++curX <= maxX) {
                return true;
            }
            curX = minX;
            if (++curZ <= maxZ) {
                return true;
            }
            curZ = minZ;
            return --curY >= minY;
        }

        int percent() {
            return total <= 0 ? 100 : (int) Math.min(100L, (long) scanned * 100L / total);
        }
    }

    // ============ 选区放置：填满整个选区 ============

    /**
     * 触发一次选区放置：遍历操作区每个可放格，用绑定容器优先的方块填满。
     * 需求（2026-09-08）：手持物品当"标签"决定放什么方块；容器优先→手头补→再缺跳过；
     * 单人生效、无粒子。
     */
    public static void triggerPlace(ServerPlayer player, PlayerSkillRecord record) {
        if (!isActive(player, record, Skills.MACHINE_ZONE_PLACE)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        // 再按一次 = 取消当前作业（可中断：大选区不必等完）
        if (JOBS.containsKey(player.getUUID())) {
            cancelJob(player, true);
            return;
        }
        OperZone zone = record.getOperZone(Skills.MACHINE_ZONE_PLACE);
        if (zone == null) {
            return; // 未框选
        }
        if (!zone.dim().equals(level.dimension().location().toString())) {
            return; // 选区不在当前维度（避免跨维乱改）
        }
        // 标签方块 = 主手 BlockItem
        ItemStack tag = player.getMainHandItem();
        if (!(tag.getItem() instanceof BlockItem blockItem)) {
            return; // 必须手持方块类物品当标签
        }
        Job job = new Job(Skills.MACHINE_ZONE_PLACE,
                level.dimension().location().toString(), zone,
                blockItem.getBlock().defaultBlockState(), blockItem);
        JOBS.put(player.getUUID(), job);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "chat.zifeng_s_custom_skill_tree.zone_job_start",
                Skills.getDisplayNameComponent(Skills.MACHINE_ZONE_PLACE),
                String.valueOf(job.total)), true);
        advance(player, record, level, job, jobDeadlineNanos());
    }

    /**
     * 取 1 个同标签方块：绑定容器优先（不要求挪移开启），再玩家背包/手上补，最后缺 → 空。
     */
    private static ItemStack takeMaterial(ServerPlayer player, PlayerSkillRecord record, BlockItem tag, int count) {
        // ① 绑定容器优先
        if (record.hasLootVacuumBind()) {
            ItemStack fromBound = org.zifeng.skilltree.event.LootVacuumEvents.extractFromBoundRaw(player, record,
                    stack -> stack.getItem() == tag, count);
            if (!fromBound.isEmpty()) {
                return fromBound;
            }
        }
        // ② 手头补（主手/背包逐个找同物品）
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == tag) {
                ItemStack take = stack.split(count);
                return take.isEmpty() ? ItemStack.EMPTY : take;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.getItem() == tag) {
            return offhand.split(count);
        }
        return ItemStack.EMPTY;
    }

    // ============ 选区挖掘：瞬间挖空整个选区 ============

    /**
     * 触发一次选区挖掘：操作区内所有非空气方块瞬间移除，不需要进度条、无粒子。
     * 掉落尝试塞绑定容器、多余掉落清除、不生成经验；主手工具只提供附魔（精准采集/时运等），不耗耐久。
     * <p>联动增幅（2026-09-08，全部对齐正常挖掘管线语义，尊重各技能开关/生效等级）：
     * <ul>
     *   <li>自动熔炼（AUTO_SMELT 已学+开启）→ 挖区掉落物先按熔炉配方熔炼再入容器（尊重熔炼黑名单）</li>
     *   <li>点石成金（BLOCK_DROP 开启）→ 仅对吃时运的方块（矿石类，isOreBlock）掉落×倍率</li>
     * </ul>
     * ⚠️ 2026-09-08：不兼容万物挖掘（太 OP）——基岩等无法破坏方块在挖区内被直接忽视、不拆除。
     */
    public static void triggerExcavate(ServerPlayer player, PlayerSkillRecord record) {
        if (!isActive(player, record, Skills.MACHINE_ZONE_EXCAVATE)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        // 再按一次 = 取消当前作业（可中断：大选区不必等完）
        if (JOBS.containsKey(player.getUUID())) {
            cancelJob(player, true);
            return;
        }
        OperZone zone = record.getOperZone(Skills.MACHINE_ZONE_EXCAVATE);
        if (zone == null) {
            return;
        }
        if (!zone.dim().equals(level.dimension().location().toString())) {
            return; // 选区不在当前维度（避免跨维乱改）
        }
        // 挖掘作业不需 placeState/tagItem（仅放置用）→ 传 null
        Job job = new Job(Skills.MACHINE_ZONE_EXCAVATE,
                level.dimension().location().toString(), zone, null, null);
        JOBS.put(player.getUUID(), job);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "chat.zifeng_s_custom_skill_tree.zone_job_start",
                Skills.getDisplayNameComponent(Skills.MACHINE_ZONE_EXCAVATE),
                String.valueOf(job.total)), true);
        advance(player, record, level, job, jobDeadlineNanos());
    }

    /** 不可被选区挖掘破坏的方块（基岩/末地门框架/末地折跃门/屏障等，参考方块破坏保护） */
    private static boolean isUnyielding(BlockState state) {
        Block b = state.getBlock();
        return b == net.minecraft.world.level.block.Blocks.BEDROCK
                || b == net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME
                || b == net.minecraft.world.level.block.Blocks.END_PORTAL
                || b == net.minecraft.world.level.block.Blocks.END_GATEWAY
                || b == net.minecraft.world.level.block.Blocks.BARRIER
                || b == net.minecraft.world.level.block.Blocks.COMMAND_BLOCK
                || b == net.minecraft.world.level.block.Blocks.CHAIN_COMMAND_BLOCK
                || b == net.minecraft.world.level.block.Blocks.REPEATING_COMMAND_BLOCK
                || b == net.minecraft.world.level.block.Blocks.JIGSAW
                || b == net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK;
    }

    // ============ 选区攻击：开启后自动攻击区内生物（tick 挂载） ============

    /**
     * Z-Link 门面迁移（2026-09-09）：原 onPlayerTick 中"选区攻击"部分抽为 tickZoneAttack，
     * 由 system/ZoneAttackModule 调度调用（学了攻击区技能且有区才唤醒）。内容一字未改。
     */
    public static void tickZoneAttack(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return;
        }
        PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
        PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
        // 攻击区：需技能开启 + 有区
        if (record.getLearnedPoints(Skills.MACHINE_ZONE_ATTACK) > 0
                && record.isEnabled(Skills.MACHINE_ZONE_ATTACK)) {
            OperZone zone = record.getOperZone(Skills.MACHINE_ZONE_ATTACK);
            if (zone != null) {
                zoneAttack(player, record, zone);
            }
        }
        // 防护区：无需每 tick 动作——目标扫描时已按全服防护区剔除（只屏蔽杀戮光环伤害）
    }

    /**
     * 攻击区自动攻击：频率/伤害与杀戮光环完全同款（auraAttackInterval + auraAttackParams），
     * 只把 AOE 范围换成玩家框选的选区；无粒子（dealAuraDamageToOne 内已删粒子）。
     * ⚠️ 1.20.1：dealAuraDamageToOne 附魔用循环外解析的 weaponEnch 传入（与 auraAttack 一致）。
     */
    private static void zoneAttack(ServerPlayer player, PlayerSkillRecord record, OperZone zone) {
        int interval = AuraEvents.auraAttackInterval(player, record);
        if (player.level().getGameTime() % interval != 0) {
            return;
        }
        float[] p = AuraEvents.auraAttackParams(player, record);
        float damage = p[0];
        boolean voidSpear = p[1] > 0;
        boolean empower = p[2] > 0;
        boolean ignoreIFrames = p[3] > 0;
        ItemStack weapon = player.getMainHandItem();
        java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> weaponEnch =
                weapon.isEmpty() ? java.util.Collections.emptyMap()
                        : net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(weapon);
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        // 扫区内目标（目标过滤同杀戮光环：敌我/所有由目标模式决定；跳过【全服任意布防者】防护区内生物——区域内不判定）
        java.util.List<PlayerSkillRecord> protectors = activeProtectors(level);
        String dim = level.dimension().location().toString();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                zone.aabb(),
                target -> AuraEvents.isTargetValid(player, target, record.getAuraTargetMode(Skills.AURA_DAMAGE))
                        && !inAnyProtectOf(protectors, dim, target.blockPosition()));
        if (targets.isEmpty()) {
            return;
        }
        if (targets.size() > 50) {
            targets = new ArrayList<>(targets.subList(0, 50)); // 每轮上限，防刷怪塔卡死
        }
        for (LivingEntity target : targets) {
            AuraEvents.dealAuraDamageToOne(player, level, target, damage, voidSpear, empower, ignoreIFrames,
                    weapon, weaponEnch);
        }
    }

    // ============ 防护区：区内生物对玩家光环/攻击完全不可见（多块，上限 10，全服向） ============

    /**
     * 收集已开启防护区且至少有一块的玩家 record 列表（光环触发 tick 只算一次，供过滤复用）。
     * <p><b>⚠️ 2026-09-10 修复（链接玩家，不分维度）</b>：改用全服在线玩家
     * （{@code level.getServer().getPlayerList().getPlayers()}）—— 旧实现用 {@code level.players()}
     * 只拿到【与目标同维度】的布防者，导致其他维度玩家布的防护区失效。
     * 区域本身仍按 dim 匹配（在维度 X 布的防护区只保护维度 X 内的目标）。
     */
    public static java.util.List<PlayerSkillRecord> activeProtectors(ServerLevel level) {
        java.util.List<PlayerSkillRecord> out = new java.util.ArrayList<>();
        if (level == null) {
            return out;
        }
        PlayerSkillSavedData data = PlayerSkillSavedData.get(level);
        net.minecraft.server.MinecraftServer server = level.getServer();
        // ⚠️ 链接玩家：全服在线玩家（不分维度）；server 为 null 时降级为当前维度玩家
        java.util.List<ServerPlayer> online = server != null
                ? server.getPlayerList().getPlayers() : level.players();
        for (ServerPlayer p : online) {
            // ⚠️ 2026-09-11 性能修复：改用只读 getPlayer（不创建）——
            // 原 getOrCreatePlayer 会为【从没学过技能的玩家】创建空记录，
            // 而本方法每次光环攻击触发都跑一遍全服玩家（高频），会白建记录 + 白写存档。
            PlayerSkillRecord rec = data.getPlayer(p.getUUID());
            if (rec == null) {
                continue; // 无技能数据 → 不可能有防护区，跳过
            }
            if (rec.getLearnedPoints(Skills.MACHINE_ZONE_PROTECT) > 0
                    && rec.isEnabled(Skills.MACHINE_ZONE_PROTECT)
                    && !rec.getProtectZones().isEmpty()) {
                out.add(rec);
            }
        }
        return out;
    }

    /** 目标是否位于给定防护者列表（任一块，同维度）内 */
    public static boolean inAnyProtectOf(java.util.List<PlayerSkillRecord> protectors,
                                         String dim, net.minecraft.core.BlockPos pos) {
        for (PlayerSkillRecord rec : protectors) {
            if (rec.isInAnyProtectZone(dim, pos)) {
                return true;
            }
        }
        return false;
    }

    // ⚠️ 2026-09-08 用户定稿：防护区【只屏蔽杀戮光环伤害】——杀戮光环全部伤害通道
    //   （普通/混沌连击/虚空秒杀直杀等）都从 dealAuraDamageToOne 一个口子走，而该口子上游
    //   的目标扫描已按全服防护区过滤（auraAttack / zoneAttack 的 inAnyProtectOf 剔除），
    //   源头屏蔽即已完整覆盖。不再设受击事件兜底：否则会误拦怪物/环境/普通玩家近战等
    //   非杀戮光环来源（与"只屏蔽杀戮光环"相悖）。

    // ============ 作业推进（分批跳 tick 核心） ============

    /**
     * 推进作业一片（时间预算内）。返回 true = 作业仍在进行。
     * <p>每 tick 由 {@code ZoneWorkModule} 驱动；刚建作业时也会立即调一次（小选区仍一键即完）。
     * <p>⚠️ 预算用【纳秒】而非格数：挖掘单格成本差异很大（有无自动熔炼/矿石倍率），
     * 固定格数在不同场景下卡顿程度天差地别；时间预算能自动适应。
     */
    private static boolean advance(ServerPlayer player, PlayerSkillRecord record, ServerLevel level,
                                   Job job, long deadlineNanos) {
        // 本 tick 已无剩余预算（服务器忙 / 本 tick 已超目标）→ 不占用，作业保留到下 tick
        if (System.nanoTime() >= deadlineNanos) {
            return true;
        }
        boolean place = Skills.MACHINE_ZONE_PLACE.equals(job.skillId);
        ItemStack tool = player.getMainHandItem();
        // 挖掘用：自动熔炼/点石成金（getBlockDropMultiplier 内部已尊重开关+生效等级，未学=1.0）
        boolean smeltOn = !place && record.getLearnedPoints(Skills.AUTO_SMELT) > 0
                && record.isEnabled(Skills.AUTO_SMELT);
        double blockMult = place ? 1.0
                : org.zifeng.skilltree.skill.SkillEffects.getBlockDropMultiplier(record);
        job.vacuumBound = !place && record.hasLootVacuumBind(); // 掉落是"攒起来批量入"还是"直接丢弃"
        int sinceCheck = 0; // 距离上次时间检查已处理格数（降频用，见 TIME_CHECK_INTERVAL）
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        while (job.advanceCursor()) {
            pos.set(job.curX, job.curY, job.curZ);
            job.scanned++;
            if (place) {
                BlockState cur = level.getBlockState(pos);
                if (cur.isAir() || cur.canBeReplaced()) {
                    ItemStack material = takeMaterial(player, record, job.tagItem, 1);
                    if (material.isEmpty()) {
                        // 材料耗尽：中断作业（与旧行为一致：缺则停）
                        flushDrops(player, record, job);
                        finishJob(player, job, false);
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "chat.zifeng_s_custom_skill_tree.zone_job_nomaterial"), true);
                        return false;
                    }
                    level.setBlock(pos, job.placeState, 3);
                    job.changed++;
                }
            } else if (excavateOne(player, record, level, job, pos, tool, smeltOn, blockMult)) {
                job.changed++;
            }
            // 时间检查降频：每 TIME_CHECK_INTERVAL 格才调一次 nanoTime（见常量注释）
            if (++sinceCheck >= TIME_CHECK_INTERVAL) {
                sinceCheck = 0;
                if (System.nanoTime() >= deadlineNanos) {
                    flushDrops(player, record, job); // 本 tick 攒的掉落先入容器
                    reportProgress(player, job);
                    return true; // 预算用完：保持作业，下 tick 继续（由 ZoneWorkModule 驱动）
                }
            }
        }
        flushDrops(player, record, job);
        finishJob(player, job, true);  // 遍历结束 = 完成
        return false;
    }

    /**
     * 把本 tick 攒下的掉落物批量入绑定容器（2026-09-12 1.4.1 性能优化）。
     * <p>目标只解析一次 + 相同物品先合并 → 插入次数从「每格一次」降到「每 tick 个位数」。
     * <p>未绑定容器 → 掉落直接丢弃（与旧行为一致：不多余生成掉落实体）。
     */
    private static void flushDrops(ServerPlayer player, PlayerSkillRecord record, Job job) {
        if (job.pendingDrops.isEmpty()) {
            return;
        }
        long t0 = System.nanoTime();
        org.zifeng.skilltree.event.LootVacuumEvents.BoundTarget target =
                org.zifeng.skilltree.event.LootVacuumEvents.resolveBoundTarget(player, record);
        if (target != null) {
            org.zifeng.skilltree.event.LootVacuumEvents.insertBatch(target, job.pendingDrops);
        }
        job.pendingDrops.clear();
        job.tInsert += System.nanoTime() - t0;
    }

    /**
     * 挖掘单格。
     * @return true = 实际改动了方块（非空气、非不可破坏方块）
     */
    private static boolean excavateOne(ServerPlayer player, PlayerSkillRecord record, ServerLevel level,
                                       Job job, BlockPos pos, ItemStack tool, boolean smeltOn, double blockMult) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        // 流体（水/岩浆，含流动态）：直接清空为空气——不产掉落、不走熔炼/倍率（2026-09-08）
        if (!state.getFluidState().isEmpty()) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            return true;
        }
        if (isUnyielding(state)) {
            return false; // 基岩等无法破坏方块：直接忽视、不拆除（不兼容万物挖掘，太 OP）
        }
        long t0 = System.nanoTime();
        // 取掉落（附魔生效：精准采集/时运由工具提供）
        // ⚠️ 2026-09-12 性能优化：先用 state.hasBlockEntity() 判断（纯数据标志，来自 Block，
        //    零查询开销），只有真有 BE 的方块才调 level.getBlockEntity(pos)（会查区块）。
        net.minecraft.world.level.block.entity.BlockEntity be =
                state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        List<ItemStack> drops = Block.getDrops(state, level, pos, be, player, tool);
        long t1 = System.nanoTime();
        // 自动熔炼：统一走 UltimateEvents.applyAutoSmelt（唯一入口，含黑名单/配方缓存）
        if (smeltOn && !drops.isEmpty()) {
            org.zifeng.skilltree.event.UltimateEvents.applyAutoSmelt(player, drops, record);
        }
        // 点石成金：仅吃时运的方块（矿石类）掉落×倍率（与正常挖掘语义一致，防泥土/石头刷量）
        if (blockMult > 1.0 && org.zifeng.skilltree.event.UltimateEvents.isOreBlock(state, level)) {
            org.zifeng.skilltree.event.UltimateEvents.applyDropMultiplierStacks(drops, player, blockMult);
        }
        long t2 = System.nanoTime();
        // ⚠️ 2026-09-12：恢复 flag 3（removeBlock 默认行为）——实时通知邻居，
        //    沙/砾石/流体/红石/藤蔓等会立即反应。配合「Y 高→低」遍历，
        //    上层重力方块在处理时下方支撑还在 → 被直接挖掉，不会变成掉落物。
        //    （曾试过 flag 2 换速度 + 收尾统一结算 refreshOuterLayer，用户选择行为更自然的 flag 3）
        level.removeBlock(pos, false);
        long t3 = System.nanoTime();
        // 掉落先攒起来（本 tick 结束时一次入容器，见 flushDrops）；未绑定容器则不攒（直接丢弃）
        if (job.vacuumBound) {
            for (int i = 0, n = drops.size(); i < n; i++) {
                ItemStack d = drops.get(i);
                if (d != null && !d.isEmpty()) {
                    job.pendingDrops.add(d);
                }
            }
        }
        long t4 = System.nanoTime();
        job.tDrops += t1 - t0;
        job.tSmelt += t2 - t1;
        job.tRemove += t3 - t2;
        job.tInsert += t4 - t3;
        return true;
    }

    /** 进度上报（动作栏；节流到每 PROGRESS_REPORT_TICKS tick 一次） */
    private static void reportProgress(ServerPlayer player, Job job) {
        long now = player.level().getGameTime();
        if (now - job.lastReportTick < PROGRESS_REPORT_TICKS) {
            return;
        }
        job.lastReportTick = now;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "chat.zifeng_s_custom_skill_tree.zone_job_progress",
                Skills.getDisplayNameComponent(job.skillId),
                job.percent() + "%"), true);
    }

    /** 结束作业：移出队列；completed=true 时提示已完成 */
    private static void finishJob(ServerPlayer player, Job job, boolean completed) {
        JOBS.remove(player.getUUID());
        if (completed) {
            // 精确耗时写日志（调 zoneMspLimitMs 的参考数据；每作业仅一行，不刷屏）
            long elapsedMs = Math.max(1L, (System.nanoTime() - job.startNanos) / 1_000_000L);
            org.zifeng.skilltree.SkillTreeMod.LOGGER.info(
                    "[选区作业] {} 完成：共 {} 格（改动 {} 格），耗时 {} ms（{} 格/秒）"
                            + "｜分项：取掉落 {}ms / 熔炼倍率 {}ms / 移除方块 {}ms / 入容器 {}ms",
                    job.skillId, job.total, job.changed, elapsedMs,
                    (long) job.total * 1000L / elapsedMs,
                    job.tDrops / 1_000_000L, job.tSmelt / 1_000_000L,
                    job.tRemove / 1_000_000L, job.tInsert / 1_000_000L);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.zone_job_done",
                    Skills.getDisplayNameComponent(job.skillId)), true);
        }
    }

    /** 取消作业（notify=true 时提示；二次按键/失效条件都走这里） */
    public static void cancelJob(ServerPlayer player, boolean notify) {
        if (player == null) {
            return;
        }
        Job job = JOBS.remove(player.getUUID());
        if (job != null && notify) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "chat.zifeng_s_custom_skill_tree.zone_job_cancel",
                    Skills.getDisplayNameComponent(job.skillId)), true);
        }
    }

    /**
     * 每 tick 推进该玩家的作业（由 {@code ZoneWorkModule} 调度；无作业时模块冬眠不调本方法）。
     * <p>先校验失效条件（换维度/技能关/选区被改），都通过才推进。
     */
    public static void tickJobs(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return;
        }
        Job job = JOBS.get(player.getUUID());
        if (job == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!job.dim.equals(level.dimension().location().toString())) {
            cancelJob(player, true); // 换维度
            return;
        }
        PlayerSkillRecord record = PlayerSkillSavedData.get(level).getOrCreatePlayer(player.getUUID());
        if (!isActive(player, record, job.skillId)) {
            cancelJob(player, true); // 技能被关掉/重置
            return;
        }
        OperZone current = record.getOperZone(job.skillId);
        if (current == null || !sameZone(current, job.zone)) {
            cancelJob(player, true); // 选区被改/清除 → 旧作业作废
            return;
        }
        advance(player, record, level, job, jobDeadlineNanos());
    }

    /** 选区是否完全相同（维度 + 六坐标） */
    private static boolean sameZone(OperZone a, OperZone b) {
        return a.dim().equals(b.dim())
                && a.minX() == b.minX() && a.minY() == b.minY() && a.minZ() == b.minZ()
                && a.maxX() == b.maxX() && a.maxY() == b.maxY() && a.maxZ() == b.maxZ();
    }

    /** 该玩家是否有进行中的作业（供 ZoneWorkModule.activeCondition 快速判断） */
    public static boolean hasJob(java.util.UUID id) {
        return id != null && JOBS.containsKey(id);
    }

    /** 玩家登出：丢弃其作业（遗留作业不会跨会话继续） */
    public static void onPlayerLogout(ServerPlayer player) {
        if (player != null) {
            JOBS.remove(player.getUUID());
        }
    }

    /** 服务器停止：清空全部作业 */
    public static void onServerStop() {
        JOBS.clear();
    }

    // ============ 共用 ============

    private static boolean isActive(ServerPlayer player, PlayerSkillRecord record, String skillId) {
        if (player == null || player.serverLevel() == null || record == null) {
            return false;
        }
        return record.getLearnedPoints(skillId) > 0 && record.isEnabled(skillId);
    }
}
