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

    /**
     * ★ 2026-09-15 掉落模拟标志（<b>1.20.1 专用</b>；NeoForge 无需，见下）。
     *
     * <h2>为什么需要</h2>
     * 1.20.1 的技能掉落管线挂在 <b>GLM（全局掉落修饰器）</b>上，而 GLM 是在
     * {@code Block.getDrops} <b>内部</b>执行的；本模块的 {@link #simulateDrops} 恰好在
     * 「清空方块之前」调 {@code Block.getDrops} 去模拟一次掉落 → GLM 在模拟里
     * 【把掉落当场挪进容器并 clear 列表】→ 模板恒为空 → 结算（×格数）全部跳过，
     * 玩家只收到「模拟那一次」的量（实测：8763 格铁矿只给 1 个粗铁）。
     *
     * <h2>怎么办</h2>
     * 模拟期间置位本标志；{@code SkillTreeLootModifier} 见标志为真则<b>原样返回</b>
     * （不熔炼/不倍率/不挪移）——这三项由 {@link #simulateDrops} 自己按模板应用、
     * 由 {@link #flushDrops} 在 tick 末按「结算后的完整数量」入容器，因此不丢效果。
     *
     * <p>用 {@link ThreadLocal} 而非 static 字段：模拟只发生在服务器主线程，且同一线程
     * 内其它来源（区块生成掉落等）不会被误判。
     */
    private static final ThreadLocal<Boolean> DROP_SIMULATION =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 供 {@code SkillTreeLootModifier} 查询：当前是否处于选区挖掘的掉落模拟阶段 */
    public static boolean isSimulatingDrops() {
        return Boolean.TRUE.equals(DROP_SIMULATION.get());
    }

    /** 进行中的选区作业（玩家 UUID → 作业）。单人生效，每人最多一个。 */
    private static final java.util.Map<java.util.UUID, Job> JOBS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 一次选区作业（放置或挖掘）：选区快照 + 遍历状态 + 本次改动计数。
     *
     * <p><b>遍历顺序（2026-09-15 纵向切片起）：Y 最内（高→低）→ X 中间 → Z 最外。</b>
     * 即“柱 (minX,minZ) 自上而下走完 → 柱 (minX+1,minZ) → … → 柱 (maxX,maxZ)”。
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

        // ---- 遍历状态（只有 Y 需要哨兵：Y 最内层，首次 --curY 落到 maxY） ----
        int curX, curY, curZ;

        // ★ 2026-09-19 分批（batch）：把选区在 X-Z 平面上切成 batchSide×batchSide 根柱的块，
        //    逐批处理（批内仍是「整柱自上而下」）。这样单批规模与旧的 256³ 同量级，
        //    内存与单 tick 负载不随【总选区面积】增长 → 单边上限才敢从 256 提到 512。
        //    ⚠️ Y 方向【不分批】（始终 minY..maxY 全高）：分批只切 X-Z 平面，
        //       否则一根柱会被切断，丧失「整柱原子化」与光照一次到位的性能优势。
        /** 批次边长（格）：构造时从配置快照，作业期间不变 */
        final int batchSide;
        /** X 方向批数 / Z 方向批数（向上取整） */
        final int batchColsX, batchColsZ;
        /** 当前批次索引（0 起，先 X 后 Z 推进）；达到 batchColsX*batchColsZ 即全部完成 */
        int batchIndex;
        /** 当前批次的 X / Z 范围（Y 不分批，始终 minY..maxY） */
        int batchMinX, batchMaxX, batchMinZ, batchMaxZ;

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
        long tDrops;   // 掉落模拟（每类型一次）
        long tSmelt;   // 结算建栈（类型 → 物品×数量）
        long tRemove;  // 移除方块
        long tInsert;  // 掉落入容器

        // ---- ★ 2026-09-15 批量结算（每层：扫描建表 → 统一清空 → 按类型结算）----
        // 设计：逐格“读方块 + 计数 + 清空”，每遇到新类型时【立即模拟一次掉落】
        // （此刻方块还在，带真工具→粗矿/模组掉落/精准采集/时运全部由原版自己算）；
        // 层末用“模板 × 格数”一次性结算 → 掉落管线从 O(格数) 降到 O(类型数)。
        /** 当前柱的类型计数（类型 → 格数）；每柱结算后清空 */
        final java.util.Map<BlockState, Integer> layerCounts = new java.util.LinkedHashMap<>();
        /**
         * ★ 掉落模板缓存（<b>作业级</b>，不随柱结算清空！）。
         *
         * <p>⚠️ 2026-09-15 回归修复：原先本字段随每次结算清空。当结算粒度从“层”改成“柱”后，
         * 变成了每 256 格就清一次 → {@code simulateDrops} 的调用量暴涨约 256 倍
         * （实测「取掉落」分项从 194ms 涨到 2076~3207ms）。
         * 现改为<b>作业级缓存</b>，仅在<b>工具变化</b>时失效重建（工具变了掉落才可能变）。
         */
        final java.util.Map<BlockState, java.util.List<ItemStack>> dropTemplates = new java.util.LinkedHashMap<>();
        /** dropTemplates 对应的工具快照（工具变化 → 模板失效） */
        ItemStack dropTemplatesTool = ItemStack.EMPTY;
        // ★ 当前正在处理的柱子（用于判定“换柱”）；Integer.MIN_VALUE = 尚未开始
        int layerX = Integer.MIN_VALUE;
        int layerZ = Integer.MIN_VALUE;
        /** 当前柱使用的工具（每柱开始时读一次，不逐格读） */
        ItemStack layerTool = ItemStack.EMPTY;

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
            // ★ 2026-09-19 分批：先把选区在 X-Z 平面上切块，算出批数与【第一批】范围
            this.batchSide = Math.max(1, org.zifeng.skilltree.Config.ZONE_BATCH_SIDE.get());
            this.batchColsX = (maxX - minX) / batchSide + 1;
            this.batchColsZ = (maxZ - minZ) / batchSide + 1;
            this.batchIndex = 0;
            this.applyBatchBounds();
            // ★ 2026-09-15 纵向切片：最小单位 = 一根 (x,z) 上的整根柱（Y 高→低）
            //    ⚠️ 只有 Y 需要哨兵（最内层，第一次先递减落到 maxY）；X/Z 直接取起始值。
            //       曾犯的错：curX 也写成 minX-1，而首次调用只推进 Y、X 不会被补上
            //       → 第一根柱实际跑在 (minX-1, *, minZ)，**多挖了选区外一整列**（已修）
            this.curY = maxY + 1; // Y 最内层哨兵：首次 --curY 落到 maxY
            this.curX = batchMinX; // ★ X 从【当前批次】起点开始（非整个选区）
            this.curZ = batchMinZ; // ★ Z 从【当前批次】起点开始（非整个选区）
        }

        /**
         * ★ 2026-09-15 纵列优先（纵向切片）推进器。
         *
         * <p><b>顺序</b>：Y 最内（高→低）→ X 中间（低→高）→ Z 最外（低→高）。
         * 即“柱 (minX,minZ) 从上到下走完 → 柱 (minX+1,minZ) → … → 柱 (maxX,maxZ)”。
         *
         * <p><b>为什么换成这个顺序</b>（原来的顺序是 X 内→Z 中→Y 外，即“一层一层”）：
         * 原顺序下，同一根柱的相邻两格分属相邻两层，而一层有 65536 格、要跨好几 tick →
         * 同一列的 256 格被拉得非常开，中间穿插了几百次 tick 末的 {@code runLightUpdates}，
         * 导致该列的天空光被反复重算。
         *
         * <p>纵列优先后，256 格（远小于每 tick 产能）能在极短时间内连续处理完 →
         * 该列的天空光状态一次性走到最终值，列更新有望从 256 次降到 1 次。
         *
         * <p>⚠️ 重力方块：柱内 Y 高→低，上层沙/砾石先被主动删除（此时下方支撑还在），
         * 因此不会因失去支撑而变成掉落物（与旧顺序同理）。
         *
         * <p>★ 2026-09-19 分批：X/Z 的推进边界不是整个选区，而是【当前批次】的
         * {@code batchMinX/MaxX/MinZ/MaxZ}；每批走完调 {@link #nextBatch()} 切下一批。
         * 因此「柱内自上而下」这个原子单位完全没变，只是把大选区拆成多次小作业。
         */
        boolean advanceCursor() {
            if (--curY >= minY) {
                return true;        // ① Y 最内：柱内自上而下（高→低）
            }
            curY = maxY;
            if (++curX <= batchMaxX) {
                return true;        // ② X 中间：换下一根柱（★ 至【批次】右边界为止）
            }
            curX = batchMinX;
            if (++curZ <= batchMaxZ) {
                return true;        // ③ Z 最外：换下一排（★ 至【批次】后边界为止）
            }
            return nextBatch();     // ④ ★ 本批走完 → 切下一批；所有批完成才返回 false
        }

        /**
         * ★ 2026-09-19 按 batchIndex 重算当前批次的 X / Z 范围（Y 始终全高 minY..maxY）。
         * <p>批次网格按「先 X 后 Z」排列：{@code bx = idx % colsX}、{@code bz = idx / colsX}。
         * <p>边界钳制到选区真实范围（最后一行/列可能不满一个 batchSide）。
         */
        private void applyBatchBounds() {
            int bx = batchIndex % batchColsX;
            int bz = batchIndex / batchColsX;
            batchMinX = minX + bx * batchSide;
            batchMinZ = minZ + bz * batchSide;
            batchMaxX = Math.min(batchMinX + batchSide - 1, maxX);
            batchMaxZ = Math.min(batchMinZ + batchSide - 1, maxZ);
        }

        /**
         * ★ 2026-09-19 切到下一批。返回 false = 全部批次已完成（作业结束）。
         * <p>把游标搬到新批起点；Y 无需处理——{@code advanceCursor()} 每轮都会重新从 maxY 开始。
         *
         * <p>⚠️ 切批会让 curX/curZ 跳变，下一轮循环的「换柱判定」会因此触发一次
         * {@code settleColumn} —— 这正是期望行为（旧批最后一列必须结算），
         * 且 layerCounts 为空时 settleColumn 会提前返回，无额外开销。
         */
        private boolean nextBatch() {
            if (++batchIndex >= batchColsX * batchColsZ) {
                return false;       // 所有批处理完毕
            }
            applyBatchBounds();
            curX = batchMinX;
            curZ = batchMinZ;
            return true;
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

    /**
     * 不可被选区挖掘破坏的方块（末地门框架/末地折跃门/屏障/命令方块/结构方块等，参考方块破坏保护）。
     * <p>⚠️ 2026-09-15 用户要求：<b>基岩不在本表</b> —— 它走 {@code advanceExcavate} 里的
     * <b>直接清除分支</b>（与流体同款）：不产掉落、不进掉落管线、不计入 layerCounts。
     * <p>保留的 9 种仍不可破：末地门框架 / 末地传送门 / 末地折跃门 / 屏障 / 命令方块×3 / 结构方块 / 拼图方块。
     */
    private static boolean isUnyielding(BlockState state) {
        Block b = state.getBlock();
        return b == net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME
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
        // ★ 2026-09-15：挖掘走「逐层扫描建表 → 统一清空 → 按类型结算」批量路径；放置保持原逻辑
        if (!Skills.MACHINE_ZONE_PLACE.equals(job.skillId)) {
            return advanceExcavate(player, record, level, job, deadlineNanos);
        }
        job.vacuumBound = false;
        int sinceCheck = 0; // 距离上次时间检查已处理格数（降频用，见 TIME_CHECK_INTERVAL）
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        while (job.advanceCursor()) {
            pos.set(job.curX, job.curY, job.curZ);
            job.scanned++;
            BlockState cur = level.getBlockState(pos);
            if (cur.isAir() || cur.canBeReplaced()) {
                ItemStack material = takeMaterial(player, record, job.tagItem, 1);
                if (material.isEmpty()) {
                    // 材料耗尽：中断作业（与旧行为一致：缺则停）
                    finishJob(player, job, false);
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "chat.zifeng_s_custom_skill_tree.zone_job_nomaterial"), true);
                    return false;
                }
                level.setBlock(pos, job.placeState, 3);
                job.changed++;
            }
            // 时间检查降频：每 TIME_CHECK_INTERVAL 格才调一次 nanoTime（见常量注释）
            if (++sinceCheck >= TIME_CHECK_INTERVAL) {
                sinceCheck = 0;
                if (System.nanoTime() >= deadlineNanos) {
                    reportProgress(player, job);
                    return true; // 预算用完：保持作业，下 tick 继续（由 ZoneWorkModule 驱动）
                }
            }
        }
        finishJob(player, job, true);  // 遍历结束 = 完成
        return false;
    }

    /**
     * ★ 2026-09-15 挖掘批量路径：<b>逐层「扫描建表 → 统一清空 → 按类型结算」</b>。
     *
     * <h2>为什么快</h2>
     * 原实现每格都跑一遍掉落管线（战利品表 + 熔炼 + 倍率），在"同质方块占绝大多数"的场景下
     * 是纯粹的重复劳动。现改为：
     * <ol>
     *   <li><b>逐格</b>：读方块 → 类型计数 +1 → 清空（<b>不算掉落</b>）</li>
     *   <li><b>遇新类型时</b>：立即用真工具模拟一次掉落（此刻方块还在）→ 存为模板</li>
     *   <li><b>层末</b>：模板 × 格数 → 一次性结算入容器</li>
     * </ol>
     * 掉落计算从 {@code O(格数)} 降到 {@code O(类型数)}。
     *
     * <h2>为什么粗矿/模组矿石不用写死映射</h2>
     * "模拟"就是真的调 {@code Block.getDrops}，所以粗矿、模组新增矿石、模组改写掉落、
     * 精准采集、时运全部由原版自己算，我们不需要知道任何规则。
     *
     * <p>⚠️ 未绑定容器时（掉落本来就全丢弃）连计数与模拟都跳过 → 最快路径。
     * <p>⚠️ 工具<b>每柱读一次</b>（不逐格读）；柱内换工具不影响本柱（玩家感知不到）。
     * <p>⚠️ 掉落模板缓存是<b>作业级</b>的（{@link Job#dropTemplates}），仅在工具变化时失效。
     *
     * <h2>★ 2026-09-15 不产生更新（用户要求）</h2>
     * 清空方块用 {@code setBlock(pos, AIR, 18)}（= {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE}），
     * 而不是 {@code removeBlock}（标志 3），因此：
     * <ul>
     *   <li>{@code UPDATE_CLIENTS(2)} 保留 → 客户端照常看到方块消失</li>
     *   <li>无 {@code UPDATE_NEIGHBORS(1)} → 邻居不收到 {@code neighborChanged}
     *       → <b>流体不会因为邻居变化而被调度（这就是“边界处水很卡”的根源）</b></li>
     *   <li>有 {@code UPDATE_KNOWN_SHAPE(16)} → {@code Level.markAndNotifyBlock} 里
     *       {@code (flags & 16) == 0} 为假 → 整块跳过形状传播（栅栏/墙/管道/水流那套）</li>
     * </ul>
     * 保留的（与 flags 无关，在 {@code LevelChunk.setBlockState} 内）：高度图x4 / 光照 / 区块标记。
     * <p>代价：挖空区<b>边缘</b>的沙/砾石/水不会即时反应（悬停不动）—— 用户明确选择此行为。
     */
    private static boolean advanceExcavate(ServerPlayer player, PlayerSkillRecord record, ServerLevel level,
                                           Job job, long deadlineNanos) {
        // 未绑容器 → 掉落全部丢弃，连计数/模拟都不需要（最快路径）
        job.vacuumBound = record.hasLootVacuumBind();
        final boolean needDrops = job.vacuumBound;
        final boolean smeltOn = needDrops && record.getLearnedPoints(Skills.AUTO_SMELT) > 0
                && record.isEnabled(Skills.AUTO_SMELT);
        final double blockMult = needDrops
                ? org.zifeng.skilltree.skill.SkillEffects.getBlockDropMultiplier(record) : 1.0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // ★ 2026-09-15 纵列原子化（用户实测反馈）：预算检查只在【柱末】做。
        //    原实现每 TIME_CHECK_INTERVAL(64) 格查一次截止时刻，与柱高（如 256）不对齐
        //    → 柱子在中间被 tick 边界切断 → 玩家看到“纵列走到一半就分段消失”。
        //    现改为整根柱处理完才检查 → 每根柱要么整根消失、要么完全不发生。
        //    超支上限 = 一根柱的耗时（256 格 × 2.83μs ≈ 0.72ms），对 45ms 预算 ≈ 1.6%，可忽略。

        while (job.advanceCursor()) {
            // ★ 换柱（X/Z 变化）：先结算上一根柱，再刷新本柱工具
            if (job.curX != job.layerX || job.curZ != job.layerZ) {
                settleColumn(player, record, job);
                job.layerX = job.curX;
                job.layerZ = job.curZ;
                job.layerTool = player.getMainHandItem().copy(); // 每柱读一次工具
                // ★ 工具变化 → 作业级模板缓存失效（掉落只取决于「方块类型 + 工具」）
                if (!ItemStack.matches(job.layerTool, job.dropTemplatesTool)) {
                    job.dropTemplates.clear();
                    job.dropTemplatesTool = job.layerTool.copy();
                }
            }
            pos.set(job.curX, job.curY, job.curZ);
            job.scanned++;

            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                // ★ 2026-09-15：基岩与流体同款处理 —— 直接清除，**完全不进掉落管线**。
                //    用户要求：区块拆解可破基岩、不联动「万物挖掘」、不给掉落。
                //    为何走这条分支而不只是从 isUnyielding 移除：
                //      · 不调 simulateDrops → 不会触发【模拟期副作用】挂载点
                //        （1.20.1 = GLM + BlockDropsMixin；1.21.1 = BlockDropsEvent）
                //      · 不计入 layerCounts → 结算阶段不会为基岩做任何展开
                //      · 无掉落表也不查，省一次 Block.getDrops
                final boolean directClear = !state.getFluidState().isEmpty()
                        || state.getBlock() == net.minecraft.world.level.block.Blocks.BEDROCK;
                if (directClear) {
                    // 流体（水/岩浆，含流动态）/ 基岩：直接清空为空气——不产掉落、不计数
                    // ★ 标志 18：不通知邻居/不传播形状 → 避免流体被调度（水的级联扩散）
                    level.setBlock(pos, EXCAVATE_AIR, EXCAVATE_FLAGS);
                    job.changed++;
                } else if (!isUnyielding(state)) {
                    if (needDrops) {
                        // ★ 新类型 → 立即模拟一次掉落（此刻方块还没被清掉，能拿到方块实体）
                        if (!job.dropTemplates.containsKey(state)) {
                            long t0 = System.nanoTime();
                            job.dropTemplates.put(state,
                                    simulateDrops(player, record, level, pos, state, job.layerTool, smeltOn, blockMult));
                            job.tDrops += System.nanoTime() - t0;
                        }
                        job.layerCounts.merge(state, 1, Integer::sum);
                    }
                    // ★ 清空方块（掉落已在上面模拟成模板，这里不再逐格计算）
                    // ★ 标志 18：客户端同步保留（玩家能看到消失），但不通知邻居、不传播形状
                    long tr0 = System.nanoTime();
                    level.setBlock(pos, EXCAVATE_AIR, EXCAVATE_FLAGS);
                    job.tRemove += System.nanoTime() - tr0;
                    job.changed++;
                }
            }

            // ★ 预算检查点 = 柱末（Y 已递减到底，本格是本柱最后一格）。
            //    为何放柱末而不是柱首：advanceCursor() 已把游标推到位，若在柱首中断返回，
            //    下个 tick 会再推进一格 → 跳过新柱的第一格（少挖一格）。放柱末则游标停在
            //    本柱最后一格，下个 tick 正好推进到下一根柱首，一格不漏。✓
            if (job.curY == job.minY && System.nanoTime() >= deadlineNanos) {
                // ★ 只在 tick 末尾真正入容器（解析绑定容器有开销，不能每柱解析）
                flushDrops(player, record, job);
                reportProgress(player, job);
                return true; // 预算用完：本柱已处理完并结算，下 tick 从下一根柱继续
            }
        }
        settleColumn(player, record, job); // 最后一根柱
        flushDrops(player, record, job);  // 最后一次入容器
        finishJob(player, job, true);     // 遍历结束 = 完成
        return false;
    }

    /**
     * ★ 2026-09-15：区块拆解清空方块用的固定参数。
     * <pre>
     * EXCAVATE_FLAGS = Block.UPDATE_CLIENTS(2) | Block.UPDATE_KNOWN_SHAPE(16) = 18
     * </pre>
     * <b>为什么不通知邻居（用户明确要求“区域内方块消失不产生更新”）</b>：
     * <ul>
     *   <li><b>性能</b>：去掉 {@code UPDATE_NEIGHBORS(1)} → 省掉每格 6 次邻居回调 + 级联；
     *       去掉形状传播 → 跳过 {@code Level.markAndNotifyBlock} 里的 {@code updateNeighbourShapes}</li>
     *   <li><b>★ 避开流体卡顿</b>：{@code LiquidBlock.neighborChanged} 会调 {@code level.scheduleTick(...)}
     *       调度流体 tick —— 之前“边界处的水运算很卡”正是这里来的。不通知邻居 → 不会被调度。</li>
     *   <li><b>正确性</b>：遍历是「自上而下、整层扫完再下一层」，区域内每格都会被我们主动清掉，
     *       所以它们不需要“因邻居变化而反应”；重力方块也不会因为支撑被抽走而变成掉落物。</li>
     * </ul>
     * <b>保留</b>：{@code UPDATE_CLIENTS} → 客户端照常收到方块消失（玩家能看到）。
     * <p><b>已知代价</b>：挖空区<b>边缘</b>（选区外那一圈）的沙/砾石/水不会即时反应（悬停不动），
     * 需等其它原因触发更新。用户已确认接受此行为。
     */
    private static final int EXCAVATE_FLAGS = 2 | 16; // UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE

    /** 区块拆解清空后的方块状态（空气，复用同一实例） */
    private static final BlockState EXCAVATE_AIR =
            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

    /**
     * 结算一根柱：把「类型 → 格数」按<b>作业级</b>掉落模板展开成物品堆，<b>只写入 pendingDrops（纯内存）</b>。
     * <p>模板已在扫描时模拟并应用过熔炼/倍率，这里只做「×格数」的批量展开。
     * <p>⚠️ 建栈时按单堆上限切分（5000 个物品 → 79 个 64 堆，而不是 5000 个 1 堆）。
     * <p>⚠️ <b>不在此处入容器</b>：解析绑定容器有开销（{@code ResourceLocation.parse} + {@code getChunk}），
     * 而本方法每根柱（256 格）就调一次，共 65536 次 → 必须在 tick 末尾统一 {@link #flushDrops}。
     * <p>⚠️ <b>也不清 {@link Job#dropTemplates}</b>：它是作业级缓存，否则每 256 格就要重新模拟一次掉落。
     */
    private static void settleColumn(ServerPlayer player, PlayerSkillRecord record, Job job) {
        if (job.layerCounts.isEmpty()) {
            return;
        }
        long t0 = System.nanoTime();
        for (java.util.Map.Entry<BlockState, Integer> e : job.layerCounts.entrySet()) {
            java.util.List<ItemStack> tpl = job.dropTemplates.get(e.getKey());
            if (tpl == null || tpl.isEmpty()) {
                continue;
            }
            int blocks = e.getValue();
            for (ItemStack one : tpl) {
                if (one == null || one.isEmpty()) {
                    continue;
                }
                long remaining = (long) one.getCount() * blocks;
                int maxStack = Math.max(1, one.getMaxStackSize());
                while (remaining > 0) {
                    int take = (int) Math.min(maxStack, remaining);
                    ItemStack s = one.copy();
                    s.setCount(take);
                    job.pendingDrops.add(s);
                    remaining -= take;
                }
            }
        }
        job.tSmelt += System.nanoTime() - t0; // 结算建栈耗时（复用 tSmelt 槽位）
        job.layerCounts.clear();
        // ⚠️ 不清 dropTemplates：它是作业级缓存（仅在工具变化时失效）
    }

    /**
     * 掉落模拟（每个方块类型只调一次）：走原版 {@link Block#getDrops}，再按技能规则应用
     * 自动熔炼与点石成金倍率，得到「该类型的基准掉落」作为模板。
     * <p>因为用的是玩家主手真工具，所以精准采集 / 时运 / 粗矿 / 模组自定义掉落全部自动正确。
     * <p>★ 每种方块类型独立一项（铁矿石/深板岩铁矿石/末地铁矿石/模组铁矿各算一次），
     * 结算时按物品自动合并（多种铁矿 → 同一个粗铁堆）。
     */
    private static java.util.List<ItemStack> simulateDrops(ServerPlayer player, PlayerSkillRecord record,
                                                           ServerLevel level, BlockPos pos, BlockState state,
                                                           ItemStack tool, boolean smeltOn, double blockMult) {
        // ⚠️ 先用 state.hasBlockEntity() 判断（纯数据标志，零查询开销），只有真有 BE 才查区块
        net.minecraft.world.level.block.entity.BlockEntity be =
                state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        // ★ 2026-09-15 关键修复（见 DROP_SIMULATION 注释）：模拟期间屏蔽 GLM 副作用。
        //    1.20.1 的 GLM 在 Block.getDrops 内部执行，若不禁用会【当场把掉落挪走并 clear】
        //    → 本方法拿到空模板 → 结算（×格数）全部跳过 → 玩家只收到“模拟那一次”的量。
        //    禁用后拿到的是【原始掉落】；熔炼/倍率由下面两段自己应用
        //    （与 1.21.1 的 BlockDropsEvent 语义一致）。
        //    另：包一层 ArrayList —— 原版在无掉落表时返回 Collections.emptyList()（不可变），
        //    而下面的 applyAutoSmelt 会 set(i, ...)，防御性转可变避免潜在 UnsupportedOperationException。
        java.util.List<ItemStack> drops;
        DROP_SIMULATION.set(Boolean.TRUE);
        try {
            drops = new java.util.ArrayList<>(Block.getDrops(state, level, pos, be, player, tool));
        } finally {
            DROP_SIMULATION.set(Boolean.FALSE);
        }
        // 自动熔炼：统一走 UltimateEvents.applyAutoSmelt（唯一入口，含黑名单/配方缓存）
        if (smeltOn && !drops.isEmpty()) {
            org.zifeng.skilltree.event.UltimateEvents.applyAutoSmelt(player, drops, record);
        }
        // 点石成金：仅吃时运的方块（矿石类）掉落×倍率（与正常挖掘语义一致，防泥土/石头刷量）
        if (blockMult > 1.0 && org.zifeng.skilltree.event.UltimateEvents.isOreBlock(state, level)) {
            org.zifeng.skilltree.event.UltimateEvents.applyDropMultiplierStacks(drops, player, blockMult);
        }
        return drops;
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
