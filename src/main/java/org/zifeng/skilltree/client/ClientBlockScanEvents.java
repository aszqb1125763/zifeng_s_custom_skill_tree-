package org.zifeng.skilltree.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 方块扫描（BLOCK_SCAN，★ 2026-10-11，1.20.1）：把【已标记方块 id】在 64 格半径内的同类方块高亮。
 *
 * <h2>与寻宝大师（{@link ClientTreasureEvents}）的区别</h2>
 * <ul>
 *   <li>寻宝大师扫<b>方块实体</b>；本类扫<b>方块状态</b>（要按 id 匹配普通方块）。</li>
 *   <li>寻宝大师每 40 tick <b>周期扫描</b>；本类<b>只在玩家按触发键时主动扫描一次</b>
 *       —— 需求明确「不能一直扫描」。</li>
 * </ul>
 *
 * <h2>主动扫描（需求核心）</h2>
 * 条件不满足时<b>连扫描都不做</b>（{@link #scanNow()} 直接清空返回）：
 * <ol>
 *   <li>技能未开启（{@link ModKeyBindingEvents#isSkillEnabledClient}）；</li>
 *   <li>没有任何标记（{@link #MARKED} 为空）。</li>
 * </ol>
 *
 * <h2>扫描方式（套用区块拆解的扫描模块思路）</h2>
 * 按<b>区块 → 区段</b>推进，先用 {@link LevelChunkSection#maybeHas} 借调色板<b>整段跳过</b>
 * 不含目标方块的分段，再逐格核对 —— 避免在 129×129 水平范围里逐格 {@code getBlockState}
 * 造成一次性卡顿（区块拆解遍历也是同一套「先粗筛再精扫」思路）。
 *
 * <h2>高亮渲染：可见面 + 轮廓边界 + 世界直线去重（单层细线）</h2>
 * 命中方块如果很多，<b>不能逐格画整个框</b>（成百上千个线框 = 渲染压力）。做法分三步：
 * <ol>
 *   <li><b>可见面</b>：每个命中方块只保留 6 个面里朝向<b>不属于同一整体</b>的那些
 *       （相邻方块之间那个面两边都不画）。
 *       ⚠️ 「同一整体」<b>不区分方块 id</b>（★ 2026-10-11 用户要求「包括不同标记方块的
 *       都要视为一个整体」），并且<b>被整块围住的内部空间</b>也算整体（见 {@link #prepareFill}）
 *       —— 但只限体积小于 {@link #MAX_CAVITY_CELLS} 格的小空腔，大洞穴照常画内壁；</li>
 *   <li><b>平面轮廓边界</b>：同一方向、同一平面上把这些可见面看成一整片 2D 区域，
 *       只画它的<b>边界线段</b>（隔壁没格子的那一侧），并把同一直线上连续的段合并成长线段
 *       → <b>任何形状（L 形/阶梯/不规则矿脉）都零内部接缝</b>。
 *       ⚠️ 原实现是"贪心合并成矩形"，但矩形相邻仍各画各的边 → 不规则区域留下大量接缝
 *       （用户反馈"很多方块不会合并"），故改为轮廓边界法；</li>
 *   <li><b>世界直线去重</b>（★ 2026-10-11 用户要求：参照 FTB Ultimine 那种<b>单层</b>细线）：
 *       一条 3D 棱会被<b>两个相邻方向</b>的可见面同时当作边界（沿 Y 的棱既是 +X 面的边界、
 *       也是 +Z 面的边界），两个面各画一遍 → 棱处两次叠加、与面内亮度不一致，整圈看起来发脏
 *       （用户实测反馈：「一个方块的 6 个面交界处」）。改为先按「方向轴 + 两个固定坐标」
 *       把线段分组、区间<b>求并集</b>，保证每条棱只发一条线，见 {@link #addRun} / {@link #flushLines}。</li>
 * </ol>
 * ⚠️ <b>绝不能</b>改用「整组包围盒」：煤矿等散点分布会被包成一个覆盖整片区域的
 * <b>超巨型框</b>（用户实测反馈）。可见面 + 轮廓边界逐格贴合，永远不会出现巨框。
 * <p>⚠️ 线段端点<b>不做任何偏移</b>，严格落在区域边界的整数格上 —— 这样所有角点与三岔口
 * 都天然精确相接。早先为「防 z-fighting」加过 0.02 格沿面法线的外推，但本 RenderType 是
 * <b>关闭深度测试</b>的穿墙覆盖层，根本不会 z-fighting，那个偏移只会把角点沿对角方向拆开。
 *
 * <p>深度与寻宝大师对齐：水平 64 格 + 垂直 128 格（椭球判定）。
 * <p>渲染管线与寻宝大师<b>同款</b>（自定义 TREASURE_LINES 式 RenderType：NO_DEPTH_TEST 穿墙 +
 * 静态复用 BufferSource + 标准 endBatch），Oculus/Embeddium 兼容已验证。
 */
public class ClientBlockScanEvents {

    private static final int SCAN_RADIUS_SQ = 64 * 64;        // 水平半径 64 格
    private static final int SCAN_Y_RADIUS_SQ = 128 * 128;    // 垂直半径 128 格
    /**
     * 高亮配色：<b>亮绿</b>单层线框（★ 2026-10-11 用户要求：单层 + 换更显眼的颜色）。
     * <p>选绿色的理由：人眼对绿光最敏感，而矿洞里的背景主要是石头 / 深板岩 / 泥土（灰褐系），
     * 亮绿在它们上面的对比度是所有色相里最高的。
     * <p>之前「不够清晰」的主因其实是<b>同一条棱被相邻两个面各画了一遍</b>（见 {@link #addRun}）——
     * 棱处两次叠加、亮度与面内不一致，整圈看起来发脏；去重之后单层就很清楚。
     */
    private static final float LINE_R = 0.20F, LINE_G = 1.00F, LINE_B = 0.30F;
    private static final float OUTLINE_ALPHA = 1.0F;
    /** 每条线段的 double 数（x1,y1,z1,x2,y2,z2） */
    private static final int SEG_STRIDE = 6;
    /**
     * 上次扫描算好的边界线段，<b>扁平存在一个 double 数组里</b>（渲染帧只遍历它）。
     * <p>一个线段占 {@link #SEG_STRIDE} 个 double，共 {@link #faceCount} 条。
     * <p>⚠️ 为什么不用 {@code List<double[]>}：一次 64 格扫描轻松产出上千条线段，用对象列表就成了
     * 上千个独立小数组 —— 每个都有自己的对象头、且地址分散，渲染帧逐条解引用时代价明显。
     * 扁平数组只剩一次连续内存扫描，对缓存友好。
     * <p>★ 2026-10-11：端点<b>不做任何偏移</b>，严格落在区域边界的整数格上，所以所有角点与三岔口
     * 天然精确相接。原先为了「防 z-fighting」把每条棱沿所属面的法线外推 0.02 格，但本 RenderType
     * 已是 {@code NO_DEPTH_TEST} + 全局 {@code disableDepthTest()} 的<b>穿墙覆盖层</b>，深度测试
     * 根本不参与、压根不会 z-fighting —— 那个偏移纯属多余，却会把每个角点<b>沿对角方向拆开</b>
     * （三条棱各自偏离真角点 √2×0.02 ≈ 0.028 格，用户实测反馈「渲染框的对角线位置不是连接的」）。
     */
    private static double[] faceBuf = new double[0];
    /** {@link #faceBuf} 里已有的线段条数 */
    private static int faceCount = 0;
    /**
     * 与 {@link #faceBuf} 一一对应的<b>预生成包围盒</b>，仅供视锥剔除使用。
     * <p>⚠️ 为什么预生成而不是每帧现算：`Frustum` 只公开 {@code isVisible(AABB)}
     * （{@code intersection} / {@code cubeInFrustum} 在 1.20.1 都是 private），
     * 而每帧给上千条线段 new 一个 AABB 会制造大量 GC 压力 —— 那就把「优化」做成了负优化。
     * 这些 AABB 只在 {@link #rebuildFaces()} 时创建一次，渲染帧重复使用、<b>零分配</b>。
     */
    private static net.minecraft.world.phys.AABB[] faceBoxes = new net.minecraft.world.phys.AABB[0];
    /**
     * 视锥剔除时给线段包围盒留的余量（格）。
     * <p>两个作用：① 线段包围盒有一条轴是<b>零厚度</b>的（面所在的平面），加余量后成为合法立体，
     * 避免退化盒在视锥边界被误判；② 屏幕边缘不会因为浮点误差把刚好压线的线段剔掉而闪烁。
     */
    private static final double CULL_PAD = 1.0D;

    // ══════════ 空腔填充（★ 2026-10-11：整个连通体视为一个整体，只画最外壳）══════════
    /**
     * 判定网格允许处理的最大格数（超过就跳过判定）。
     * <p>为什么有上限：★ 2026-10-11 起要<b>逐格读世界的方块状态</b>（判断哪里是空气、哪里是石头），
     * 开销与盒体积成正比 —— 这是本功能现在的主要代价，必须封顶。
     * <p>100 万格 ≈ 100³，约几十毫秒。而**散点分布的矿脉盒会接近整个扫描范围**
     * （130×258×130 ≈ 436 万），却不可能围出空腔 —— 属于「花最大代价换零收益」。
     * <p>这个权衡是<b>自洽</b>的：被围住的区域只可能出现在<b>紧凑的整块</b>里，而紧凑整块的盒必然小。
     */
    private static final int MAX_FILL_CELLS = 1_000_000;
    /**
     * 稀疏度闸门：包围盒体积 &gt; 标记数 × 该系数时直接跳过判定。
     * <p>理由（代价很实在）：判定要逐格读世界方块，而<b>围出空腔必须有足够密的方块壳</b>
     * —— 一层 1 格厚的壳约占其包围盒体积的 <b>1/6 以上</b>，而散点矿脉（最常见的形态）
     * 低到<b>千分之一</b>。散点本来也围不出空腔，跳过它只是省掉白花的代价，<b>不影响正确性</b>
     * （跳过 = 退回原来的纯标记方块判定）。
     * <p>实测数据（1 格厚空心壳）：3³ 占 21%、5³ 占 29%、10³ 占 41%、40³ 占 12%
     * —— 全部远高于本闸门的 1/16。
     */
    private static final int FILL_SPARSITY_LIMIT = 16;
    /**
     * 内部空间多小才算「被包裹」而省略渲染。★ 2026-10-11 用户要求：
     * 「标定方块内部空间，空间体积小于 32 任意方块，可以视为被包裹，可以省略渲染」。
     * <ul>
     *   <li><b>小于</b> 32 格 → 视为整体的一部分 → <b>不画</b>它的内壁
     *       （矿脉里夹杂的空气/石头、小缝隙不再冒出零碎小框）；</li>
     *   <li><b>大于等于</b> 32 格 → 算真实开放空间 → 内壁<b>照常渲染</b>
     *       （天然洞穴、大空腔的轮廓要看得见）。</li>
     * </ul>
     * <p>★ 用户补充「任何方块都要算进被包裹的公式内」→ 体积按<b>空气 + 里面的任何方块</b>一起数
     * （见 {@link #floodChamber}），不是只数空气。
     * <p>按<b>逐个连通分量</b>独立算体积（不是所有区域加起来），这样大洞穴旁边的小缝隙
     * 不会被大洞穴「带累」而一起显示出来。
     */
    private static final int MAX_CAVITY_CELLS = 32;
    /**
     * 判定用的世界网格（格值见 {@code G_*} 常量）。
     * <p>「属于同一整体」= {@link #G_MARK}（标记方块）或 {@link #G_BODY}（被包裹的区域）。
     */
    private static byte[] fillGrid = new byte[0];
    /** 泛洪用的可增长栈（只在需要时按需长，正常情形很小） */
    private static int[] fillStack = new int[0];
    /** 收集单个连通区域时记下的网格下标（配合 {@link #floodChamber}） */
    private static int[] fillCavity = new int[0];
    /** 6 邻域的临时下标缓冲（静态复用 → 泛洪时零分配） */
    private static final int[] FLOOD_NBRS = new int[6];
    /** 读世界方块状态时复用的可变坐标（避免逐格 new 一个 BlockPos） */
    private static final BlockPos.MutableBlockPos FILL_POS = new BlockPos.MutableBlockPos();

    // ── 填充网格的格值 ──
    /** 空气（尚未归类） */
    private static final byte G_AIR = 0;
    /** 标记方块 —— 要渲染的那些 */
    private static final byte G_MARK = 1;
    /** 其它<b>任何非空气方块</b>（石头、泥土、水…）：既能封住空间，也要计入被包裹区域的体积 */
    private static final byte G_SOLID = 2;
    /** 通到包围盒外面的空气 */
    private static final byte G_OUT_AIR = 3;
    /** 连回包围盒外面的方块（地下的矿石周围那一圈石头） */
    private static final byte G_OUT_SOLID = 4;
    /** 被标记方块整块围住、体积又够小 → 视为「同一整体」的一部分（不画内壁） */
    private static final byte G_BODY = 5;

    /** 本次重算是否启用了填充（false → 退回 {@code hits.contains} 判定） */
    private static boolean fillActive = false;
    /** 填充网格的原点与世界尺寸 */
    private static int fillOx, fillOy, fillOz, fillNx, fillNy, fillNz;

    /**
     * 准备「判定网格」：把<b>被标记方块整块围住的区域</b>也当成同一整体的一部分。
     *
     * <h2>为什么要做</h2>
     * ⚠️ 与「六个面都贴着标记方块」是<b>两回事</b>：那种情况下邻居本身就是标记方块，现有代码
     * 已经会消隐。但<b>空心</b>的时候内壁对着的不是标记方块 → 内壁照样被画出来，
     * 于是空心方块会多出一整套内层线框。
     * <p>又不能把所有空腔都填掉：那是<b>大洞穴</b>那样真实存在的开放空间，轮廓应当看得见。
     * 所以按体积分流（见 {@link #MAX_CAVITY_CELLS}）。
     *
     * <h2>怎么判（★ 2026-10-11 起，真实方块也参与）</h2>
     * 用户要求「任何方块都要算进被包裹的公式内」「真实方块能封住空间，并且这些真实方块也计入体积」。
     * 于是在「标记方块包围盒<b>外扩 1 格</b>」的网格上分四步：
     * <ol>
     *   <li><b>读世界分类</b>：空气 → {@link #G_AIR}；<b>其它任何非空气方块</b> → {@link #G_SOLID}。
     *       于是石头/泥土/水既能<b>封住</b>空间，也会被<b>计入</b>体积；</li>
     *   <li>标记方块盖成 {@link #G_MARK}；</li>
     *   <li><b>分清「外面」</b>：空气走空气、方块走方块，见 {@link #floodFromShell}
     *       → 没连回外扩层的才是「被围住」；</li>
     *   <li><b>按连通分量量体积</b>（空气与里面的方块一起数，见 {@link #floodChamber}）：
     *       {@code < }{@link #MAX_CAVITY_CELLS} 就标成 {@link #G_BODY} → 不画内壁；
     *       否则标回 {@link #G_OUT_AIR} → 内壁照常渲染。</li>
     * </ol>
     *
     * <p>结果只会让<b>更少</b>的线段被画出来（被围住区域的边界本来夹在实体之间），绝不会凭空多画。
     *
     * @param level 当前世界；为 null（例如刚退出存档）时直接放弃判定、退回纯标记方块逻辑
     */
    private static void prepareFill(Set<BlockPos> hits, Level level) {
        fillActive = false;
        if (hits.isEmpty() || level == null) {
            return;
        }
        int hx0 = Integer.MAX_VALUE, hy0 = Integer.MAX_VALUE, hz0 = Integer.MAX_VALUE;
        int hx1 = Integer.MIN_VALUE, hy1 = Integer.MIN_VALUE, hz1 = Integer.MIN_VALUE;
        for (BlockPos p : hits) {
            final int x = p.getX(), y = p.getY(), z = p.getZ();
            if (x < hx0) hx0 = x;
            if (x > hx1) hx1 = x;
            if (y < hy0) hy0 = y;
            if (y > hy1) hy1 = y;
            if (z < hz0) hz0 = z;
            if (z > hz1) hz1 = z;
        }
        fillOx = hx0 - 1;
        fillOy = hy0 - 1;
        fillOz = hz0 - 1;
        fillNx = hx1 - hx0 + 3;
        fillNy = hy1 - hy0 + 3;
        fillNz = hz1 - hz0 + 3;
        final long vol = (long) fillNx * fillNy * fillNz;
        if (vol > MAX_FILL_CELLS || vol > (long) hits.size() * FILL_SPARSITY_LIMIT) {
            return; // 太大 / 太散：不可能围出区域，读世界只是白花代价 → 退回纯标记判定
        }
        final int total = (int) vol;
        if (fillGrid.length < total) {
            fillGrid = new byte[total];
        } else {
            java.util.Arrays.fill(fillGrid, 0, total, G_AIR);
        }
        // ① 读世界分类：任何非空气方块都算 G_SOLID。
        //    未加载的区块按空气处理 —— 偏向「不抑制」，宁可多画几条线也不误藏。
        final BlockPos.MutableBlockPos mp = FILL_POS;
        for (int i = 0; i < total; i++) {
            final int gz = i % fillNz;
            final int t = i / fillNz;
            final int gy = t % fillNy;
            final int gx = t / fillNy;
            mp.set(fillOx + gx, fillOy + gy, fillOz + gz);
            if (level.hasChunkAt(mp) && !level.getBlockState(mp).isAir()) {
                fillGrid[i] = G_SOLID;
            }
        }
        // ② 标记方块盖在最上层（它们也是「同一整体」的一部分）
        for (BlockPos p : hits) {
            fillGrid[fillIndex(p.getX(), p.getY(), p.getZ())] = G_MARK;
        }
        // ③ 分清「外面」：空气走空气、方块走方块 —— 于是「缺口被石头堵住」也算密封
        floodFromShell(G_AIR, G_OUT_AIR);
        floodFromShell(G_SOLID, G_OUT_SOLID);
        // ④ 剩下还是 G_AIR / G_SOLID 的 = 被整块围住的区域。
        //    按连通分量量体积（空气 + 里面的任何方块都算），小于阈值就视为整体的一部分。
        boolean enclosed = false;
        for (int i = 0; i < total; i++) {
            final byte v = fillGrid[i];
            if (v != G_AIR && v != G_SOLID) {
                continue;
            }
            final int chamber = floodChamber(i); // 先临时标成 G_BODY 并记下全部下标
            final byte mark = (chamber < MAX_CAVITY_CELLS) ? G_BODY : G_OUT_AIR;
            if (mark == G_BODY) {
                enclosed = true; // 小区域 → 视为被包裹 → isInside() 返回 true → 不画内壁
            }
            for (int k = 0; k < chamber; k++) {
                fillGrid[fillCavity[k]] = mark;
            }
        }
        if (enclosed) {
            fillActive = true;
        }
        // 没有「小到算被包裹」的区域 → 不必用网格（省掉后续逐次判定的开销），退回 HashSet 判定
    }

    /**
     * 从包围盒<b>外扩层</b>出发，把「连到外面」的格子标出来。
     *
     * <p>规则：<b>空气只走空气、方块只走方块</b>，两者互相阻挡。
     * <ul>
     *   <li>{@code floodFromShell(G_AIR, G_OUT_AIR)} → 泛洪到的空气就是<b>通到外面的空气</b>；</li>
     *   <li>{@code floodFromShell(G_SOLID, G_OUT_SOLID)} → 泛洪到的方块就是<b>长在整块外面的石头</b>
     *       （地下的矿石周围那一圈石头全属于这一层，所以它们<b>不会</b>被算进腔的体积）。</li>
     * </ul>
     * 「石头能挡住空气」正是「缺口填了石头也算密封」的实现（★ 2026-10-11 用户要求）；
     * 而<b>没</b>连回外扩层的方块 = 悬浮在密闭腔里的方块，会通过 {@link #floodChamber} 计入腔的格数。
     *
     * <p>外扩层一定在标记方块之外，所以它就是「世界的外侧」，从它起步即可覆盖所有外部区域。
     */
    private static void floodFromShell(byte from, byte to) {
        int sp = 0;
        if (fillStack.length < 256) {
            fillStack = new int[256];
        }
        // 种子 = 外扩层（六个面上的那层壳）里值为 from 的格子
        for (int gx = 0; gx < fillNx; gx++) {
            final boolean edgeX = (gx == 0 || gx == fillNx - 1);
            for (int gy = 0; gy < fillNy; gy++) {
                final boolean edgeY = (gy == 0 || gy == fillNy - 1);
                for (int gz = 0; gz < fillNz; gz++) {
                    if (!edgeX && !edgeY && gz != 0 && gz != fillNz - 1) {
                        continue; // 内层：不是外扩层，跳过
                    }
                    final int i = ((gx * fillNy) + gy) * fillNz + gz;
                    if (fillGrid[i] == from) {
                        fillGrid[i] = to;
                        if (sp == fillStack.length) {
                            fillStack = java.util.Arrays.copyOf(fillStack, fillStack.length * 2);
                        }
                        fillStack[sp++] = i;
                    }
                }
            }
        }
        final int strideX = fillNy * fillNz;
        final int strideY = fillNz;
        while (sp > 0) {
            final int ci = fillStack[--sp];
            final int gz = ci % fillNz;
            final int t = ci / fillNz;
            final int gy = t % fillNy;
            final int gx = t / fillNy;
            // ⚠️ 索引边界必须显式判断：±1 在最后一维会串到相邻行，±stride 在前两维会越界。
            int cnt = 0;
            if (gx > 0) {
                FLOOD_NBRS[cnt++] = ci - strideX;
            }
            if (gx + 1 < fillNx) {
                FLOOD_NBRS[cnt++] = ci + strideX;
            }
            if (gy > 0) {
                FLOOD_NBRS[cnt++] = ci - strideY;
            }
            if (gy + 1 < fillNy) {
                FLOOD_NBRS[cnt++] = ci + strideY;
            }
            if (gz > 0) {
                FLOOD_NBRS[cnt++] = ci - 1;
            }
            if (gz + 1 < fillNz) {
                FLOOD_NBRS[cnt++] = ci + 1;
            }
            for (int k = 0; k < cnt; k++) {
                final int j = FLOOD_NBRS[k];
                if (fillGrid[j] == from) {
                    fillGrid[j] = to;
                    if (sp == fillStack.length) {
                        fillStack = java.util.Arrays.copyOf(fillStack, fillStack.length * 2);
                    }
                    fillStack[sp++] = j;
                }
            }
        }
    }

    /**
     * 收集一个<b>连通区域</b>：从 {@code start} 出发走 6 邻域，把还没归类成「外面」的格子都算进来
     * —— {@link #G_AIR} 与 {@link #G_SOLID} 属于同一块区域（★ 用户要求「任何方块都要算进
     * 被包裹的公式内」，所以腔里的石头和空气一起数）。
     *
     * <p>过程中先临时标成 {@link #G_BODY}，并把下标记进 {@link #fillCavity}，
     * 让调用方按体积决定最终标成 {@link #G_BODY}（被包裹）还是 {@link #G_OUT_AIR}（内壁照常渲染）。
     *
     * <p>⚠️ 索引边界必须显式判断：{@code ±1} 在最后一维的边界会串到相邻行，
     * {@code ±stride} 在前两维会越界。
     *
     * @return 该区域的格数（= 体积，空气与里面的方块都算）
     */
    private static int floodChamber(int start) {
        final int strideX = fillNy * fillNz;
        final int strideY = fillNz;
        if (fillStack.length < 256) {
            fillStack = new int[256];
        }
        if (fillCavity.length < 256) {
            fillCavity = new int[256];
        }
        int sp = 0;
        int n = 0;
        fillGrid[start] = G_BODY;
        fillStack[sp++] = start;
        fillCavity[n++] = start;
        while (sp > 0) {
            final int ci = fillStack[--sp];
            final int gz = ci % fillNz;
            final int t = ci / fillNz;
            final int gy = t % fillNy;
            final int gx = t / fillNy;
            int cnt = 0;
            if (gx > 0) {
                FLOOD_NBRS[cnt++] = ci - strideX;
            }
            if (gx + 1 < fillNx) {
                FLOOD_NBRS[cnt++] = ci + strideX;
            }
            if (gy > 0) {
                FLOOD_NBRS[cnt++] = ci - strideY;
            }
            if (gy + 1 < fillNy) {
                FLOOD_NBRS[cnt++] = ci + strideY;
            }
            if (gz > 0) {
                FLOOD_NBRS[cnt++] = ci - 1;
            }
            if (gz + 1 < fillNz) {
                FLOOD_NBRS[cnt++] = ci + 1;
            }
            for (int k = 0; k < cnt; k++) {
                final int j = FLOOD_NBRS[k];
                final byte v = fillGrid[j];
                if (v == G_AIR || v == G_SOLID) {
                    fillGrid[j] = G_BODY;
                    if (sp == fillStack.length) {
                        fillStack = java.util.Arrays.copyOf(fillStack, fillStack.length * 2);
                    }
                    fillStack[sp++] = j;
                    if (n == fillCavity.length) {
                        fillCavity = java.util.Arrays.copyOf(fillCavity, fillCavity.length * 2);
                    }
                    fillCavity[n++] = j;
                }
            }
        }
        return n;
    }

    /** 世界坐标 → 填充网格下标 */
    private static int fillIndex(int x, int y, int z) {
        return (((x - fillOx) * fillNy) + (y - fillOy)) * fillNz + (z - fillOz);
    }

    /**
     * 该坐标是否属于「同一整体」（标记方块 <b>或</b>被包裹的小区域）。
     * <p>启用判定时走网格（<b>零分配</b>、比 HashSet 快）；否则退回原来的 {@code HashSet} 判定。
     */
    private static boolean isInside(Set<BlockPos> hits, int x, int y, int z) {
        if (fillActive) {
            final int gx = x - fillOx, gy = y - fillOy, gz = z - fillOz;
            if (gx < 0 || gy < 0 || gz < 0 || gx >= fillNx || gy >= fillNy || gz >= fillNz) {
                return false; // 盒外：整体与判定网格都在盒内
            }
            final byte v = fillGrid[fillIndex(x, y, z)];
            return v == G_MARK || v == G_BODY;
        }
        return hits.contains(new BlockPos(x, y, z));
    }

    /** 六个面方向（dx,dy,dz）：0=+X 1=-X 2=+Y 3=-Y 4=+Z 5=-Z */
    private static final int[][] FACE_DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** 已标记方块（服务端 S2C 校准；per-player） */
    private static final Set<Block> MARKED = new HashSet<>();
    /** 实时校验计数（每 {@link #VALIDATE_INTERVAL} tick 查一次，避免每帧遍历） */
    private static int validateTimer = 0;
    /** 实时校验间隔（tick）：0.5 秒查一次 —— 破坏方块是低频操作，够快且几乎零开销 */
    private static final int VALIDATE_INTERVAL = 10;

    /**
     * 上次扫描的<b>逐格命中</b>（按方块 id 分组）—— 用于<b>实时校验</b>：
     * 方块被挖掉/被替换后，对应高亮要立刻消失（★ 2026-10-11 用户要求）。
     */
    private static final Map<Block, Set<BlockPos>> LAST_HITS = new LinkedHashMap<>();
    /** 上次扫描命中方块总数（提示文案用） */
    private static int lastHitCount = 0;
    /** 上次扫描算出的边界线段数（提示文案用） */
    private static int lastFaceCount = 0;

    /** 1.20.1：RenderStateShard 的 shard 常量是 protected，嵌套子类继承以访问（与寻宝大师同款） */
    private static final class ShardAccess extends net.minecraft.client.renderer.RenderStateShard {
        private ShardAccess() {
            super("zifeng_blockscan_shard", () -> {
            }, () -> {
            });
        }

        static final DepthTestStateShard NO_DEPTH = NO_DEPTH_TEST;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final ShaderStateShard LINES_SHADER = RENDERTYPE_LINES_SHADER;
        static final LayeringStateShard VIEW_OFFSET = VIEW_OFFSET_Z_LAYERING;
        static final OutputStateShard ITEM_TARGET = ITEM_ENTITY_TARGET;
        static final WriteMaskStateShard COLOR_DEPTH_WRITE_S = COLOR_DEPTH_WRITE;
        static final CullStateShard NO_CULL_S = NO_CULL;
        // LineStateShard 构造器也是 protected：在子类内实例化（lines() 同款默认线宽）
        static final LineStateShard LINE_WIDTH = new LineStateShard(java.util.OptionalDouble.empty());
    }

    private static net.minecraft.client.renderer.MultiBufferSource.BufferSource SCAN_BUFFERS;

    private static net.minecraft.client.renderer.MultiBufferSource.BufferSource scanBuffers() {
        if (SCAN_BUFFERS == null) {
            SCAN_BUFFERS = net.minecraft.client.renderer.MultiBufferSource.immediateWithBuffers(
                    java.util.Map.of(SCAN_LINES, new com.mojang.blaze3d.vertex.BufferBuilder(256)),
                    new com.mojang.blaze3d.vertex.BufferBuilder(256));
        }
        return SCAN_BUFFERS;
    }

    /** 透视线框 RenderType（lines() 完整克隆，仅 depthTest=NO_DEPTH_TEST；与寻宝大师同款） */
    private static final net.minecraft.client.renderer.RenderType SCAN_LINES = net.minecraft.client.renderer.RenderType.create(
            "zifeng_blockscan_lines",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES,
            1536, false, false,
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.LINES_SHADER)
                    .setLineState(ShardAccess.LINE_WIDTH)
                    .setLayeringState(ShardAccess.VIEW_OFFSET)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setOutputState(ShardAccess.ITEM_TARGET)
                    .setWriteMaskState(ShardAccess.COLOR_DEPTH_WRITE_S)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.NO_DEPTH)
                    .createCompositeState(false));

    /** 会话重置（{@link ClientSession#resetSession()} 登记调用）：扫描结果是临时的 → 清空 */
    public static void resetSession() {
        clearScan();
    }

    /** 清空扫描结果与计数 */
    private static void clearScan() {
        faceCount = 0;
        fillActive = false;
        LAST_HITS.clear();
        lastHitCount = 0;
        lastFaceCount = 0;
    }

    /** 服务端回发校准已标记方块（非法/未知 id 直接忽略，不抛异常） */
    public static void setMarkedBlocksClient(List<String> ids) {
        // ★ 2026-10-11 用户要求：标记 / 取消标记时【不能关掉已有高亮】。
        //   所以这里不再无条件清空，而是：若当前已有扫描结果 → 立刻按新标记重扫一次，
        //   高亮持续存在且实时更新（没扫过就维持不扫，尊重「不常驻扫描」）。
        boolean hadScan = faceCount > 0;
        MARKED.clear();
        if (ids != null) {
            for (String id : ids) {
                ResourceLocation loc = ResourceLocation.tryParse(id);
                if (loc == null) {
                    continue;
                }
                Block block = BuiltInRegistries.BLOCK.get(loc);
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                    MARKED.add(block);
                }
            }
        }
        if (hadScan) {
            scanNow(); // 重扫（技能关闭 / 已无标记时会自行清空）
        }
    }

    /** 已标记方块种类数（信息栏/提示用） */
    public static int markedCount() {
        return MARKED.size();
    }

    /** 上次扫描命中方块总数 */
    public static int lastHitCount() {
        return lastHitCount;
    }

    /** 上次扫描算出的可见面数（< 6×命中数 即表示发生了贴合合并） */
    public static int lastFaceCount() {
        return lastFaceCount;
    }

    /**
     * <b>主动扫描一次</b>（由「方块扫描」功能触发键调用）。
     *
     * @return 本次扫描命中的方块数；未扫描（技能未开 / 无标记）返回 -1
     */
    public static int scanNow() {
        clearScan();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return -1;
        }
        // ⚠️ 需求：没有开启技能 → 关闭扫描（连遍历都不做）
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.BLOCK_SCAN)) {
            return -1;
        }
        // ⚠️ 需求：没有标记 → 关闭扫描
        if (MARKED.isEmpty()) {
            return -1;
        }
        BlockPos center = mc.player.blockPosition();
        int minSection = mc.level.getMinSection();
        // 扫描先收集全部命中坐标（按 id 分桶只为「只在同 id 之间消接缝」——不同方块不互相消面）
        Map<Block, Set<BlockPos>> hitsByBlock = new LinkedHashMap<>();
        int total = 0;
        int minSX = (center.getX() - 64) >> 4, maxSX = (center.getX() + 64) >> 4;
        int minSZ = (center.getZ() - 64) >> 4, maxSZ = (center.getZ() + 64) >> 4;
        for (int cx = minSX; cx <= maxSX; cx++) {
            for (int cz = minSZ; cz <= maxSZ; cz++) {
                // ⚠️ 同寻宝大师：getChunk(x,z,status,false) 未加载时返回 null，
                //    避免单参 getChunk 强制加载区块（客户端站在加载边缘会周期性卡顿）。
                net.minecraft.world.level.chunk.ChunkAccess chunk = mc.level.getChunk(cx, cz,
                        net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
                if (!(chunk instanceof LevelChunk lc)) {
                    continue; // 未加载 → 跳过
                }
                LevelChunkSection[] sections = lc.getSections();
                for (int si = 0; si < sections.length; si++) {
                    LevelChunkSection section = sections[si];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    int baseY = (minSection + si) << 4;
                    // 垂直范围（区段级粗筛：整段都在范围外就跳过）
                    if (!withinYSq(center, baseY + 8, 12)) {
                        continue;
                    }
                    // ★ 调色板粗筛：整段不含任何已标记方块 → 完全跳过（不逐格查）
                    if (!section.maybeHas(bs -> MARKED.contains(bs.getBlock()))) {
                        continue;
                    }
                    int baseX = cx << 4, baseZ = cz << 4;
                    for (int ly = 0; ly < 16; ly++) {
                        int y = baseY + ly;
                        if (!withinYSq(center, y, 0)) {
                            continue;
                        }
                        for (int lx = 0; lx < 16; lx++) {
                            int dx = baseX + lx - center.getX();
                            if (dx * dx > SCAN_RADIUS_SQ) {
                                continue;
                            }
                            for (int lz = 0; lz < 16; lz++) {
                                int dz = baseZ + lz - center.getZ();
                                if (dx * dx + dz * dz > SCAN_RADIUS_SQ) {
                                    continue;
                                }
                                BlockState state = section.getBlockState(lx, ly, lz);
                                Block block = state.getBlock();
                                if (MARKED.contains(block)) {
                                    hitsByBlock.computeIfAbsent(block, k -> new HashSet<>())
                                            .add(new BlockPos(baseX + lx, y, baseZ + lz));
                                    total++;
                                }
                            }
                        }
                    }
                }
            }
        }
        // ===== 只保留「可见面」（扫描时算一次，渲染帧零开销）=====
        LAST_HITS.putAll(hitsByBlock);
        rebuildFaces();
        return total;
    }

    /**
     * 按 {@link #LAST_HITS} 重算高亮线段（扫描后 / 实时校验剔除后调用）。
     *
     * <p>★ 2026-10-11 用户要求：<b>不同 id 的标记方块也要视为一个整体</b>。
     * 所以这里先把<b>所有</b> id 的命中位置合并成一个集合，再整体做一次消隐 + 轮廓 + 去重。
     * <ul>
     *   <li>原先按 id 分组各算各的 → 两个<b>不同</b> id 相邻时，它们之间那个接触面两边都不消隐，
     *       于是内部凭空多一道线；而且两个 id 还可能在<b>同一位置各画一条</b>（重复绘制、棱处叠加）。</li>
     *   <li>合并后这些接触面被正常消隐，同位置的重复线段也在 {@link #flushLines} 里并成一条。</li>
     * </ul>
     *
     * <p>去重的开销只落在<b>这里</b>（扫描 / 方块变动时才跑），渲染帧依旧只是遍历一张现成的表。
     */
    private static void rebuildFaces() {
        faceCount = 0;
        // 合并所有 id 的命中位置（BlockPos 的 equals/hashCode 是按坐标比较的，Set 合并安全）
        Set<BlockPos> all = new HashSet<>();
        for (Set<BlockPos> hits : LAST_HITS.values()) {
            all.addAll(hits);
        }
        if (all.isEmpty()) { // 全部被挖掉 / 没扫描过
            fillActive = false;
            lastFaceCount = 0;
            return;
        }
        prepareFill(all, Minecraft.getInstance().level); // ★ 把被整块围住的小区域也算成整体
        Map<Long, List<long[]>> lines = new HashMap<>();
        buildVisibleFaces(all, lines);
        flushLines(lines);
        fillActive = false; // 用完关掉，避免影响下次判定
        lastFaceCount = faceCount;
    }

    /**
     * <b>实时校验</b>（★ 2026-10-11 用户要求）：方块被挖掉 / 被替换后，对应高亮立刻消失。
     *
     * <p>每 {@link #VALIDATE_INTERVAL} tick 查一次上次扫描的命中位置，
     * 把"那里已经不是被标记的那类方块"的条目剔除；有任何剔除才重算线段
     * （避免每帧遍历 —— 校验是低频的，重算只在真的变化时发生）。
     *
     * <p>⚠️ 1.20.1 的 Forge 客户端 tick 事件每 tick 触发 START + END 两次（同
     * {@code ClientTreasureEvents} 的既有教训）→ 必须判 phase，否则校验频率翻倍。
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return; // 每 tick 只处理一次
        }
        if (LAST_HITS.isEmpty()) {
            return; // 没扫描过 / 已清空 → 零开销
        }
        if (++validateTimer < VALIDATE_INTERVAL) {
            return;
        }
        validateTimer = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        boolean changed = false;
        var it = LAST_HITS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Block, Set<BlockPos>> e = it.next();
            Block block = e.getKey();
            // 逐个命中位置核对：还是那个方块 id 就留着，否则剔除
            if (e.getValue().removeIf(pos -> mc.level.getBlockState(pos).getBlock() != block)) {
                changed = true;
            }
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
        if (changed) {
            rebuildFaces(); // 只在真的有变化时重算（并把计数同步给提示文案）
            lastHitCount = LAST_HITS.values().stream().mapToInt(Set::size).sum();
        }
    }

    /**
     * 算出 {@code hits} 这整个整体的<b>轮廓边界线段</b>，按「世界直线」收集进 {@code lines}。
     * <p>① 可见面：某方块的面若朝外方向的相邻格<b>属于同一整体</b>（命中方块，
     *    <b>或</b>被包裹的小内部空间 —— 见 {@link #prepareFill}）→ 该面被盖住，不参与渲染。
     * <p>② 平面轮廓边界：同方向 + 同平面上的可见面看成一整片 2D 区域，只画「隔壁没格子」的那一侧，
     * 并把同一直线上连续的段合并成长线段 → 相邻方块之间只剩<b>唯一一条外轮廓</b>，接缝完全无缝。
     *
     * <p>⚠️ 绝不能改用「整组包围盒」：煤矿等散点分布会被包成超巨型框（用户实测反馈）。
     *
     * @param hits  <b>所有</b>标记方块位置的并集（不区分 id）—— 见 {@link #rebuildFaces}
     * @param lines 收集器：key = 打包后的「世界直线」，value = 该线上的区间列表（见 {@link #addRun}）
     */
    private static void buildVisibleFaces(Set<BlockPos> hits, Map<Long, List<long[]>> lines) {
        for (int d = 0; d < FACE_DIRS.length; d++) {
            int[] dir = FACE_DIRS[d];
            // 按【平面坐标】分桶，桶内是 2D 单元 (u, v)
            Map<Integer, Set<Long>> planes = new HashMap<>();
            for (BlockPos p : hits) {
                // ★ 邻居「属于整块」就消隐：标记方块 或 被包裹的小内部空间
                if (isInside(hits, p.getX() + dir[0], p.getY() + dir[1], p.getZ() + dir[2])) {
                    continue;
                }
                final int plane, u, v;
                switch (d) {
                    case 0 -> { plane = p.getX() + 1; u = p.getZ(); v = p.getY(); }
                    case 1 -> { plane = p.getX(); u = p.getZ(); v = p.getY(); }
                    case 2 -> { plane = p.getY() + 1; u = p.getX(); v = p.getZ(); }
                    case 3 -> { plane = p.getY(); u = p.getX(); v = p.getZ(); }
                    case 4 -> { plane = p.getZ() + 1; u = p.getX(); v = p.getY(); }
                    default -> { plane = p.getZ(); u = p.getX(); v = p.getY(); }
                }
                planes.computeIfAbsent(plane, k -> new HashSet<>()).add(cellKey(u, v));
            }
            // 每个平面内算【区域轮廓边界】（不是合并成矩形 —— 矩形相邻仍会各画边、留接缝）
            for (Map.Entry<Integer, Set<Long>> e : planes.entrySet()) {
                outlinePlane(d, e.getKey(), e.getValue(), lines);
            }
        }
    }

    /** 2D 单元键（u,v 打包成 long；u 为高 32 位、v 为低 32 位） */
    private static long cellKey(int u, int v) {
        return ((long) u << 32) | (v & 0xFFFFFFFFL);
    }

    /**
     * 单个平面内的<b>区域轮廓边界</b>（★ 2026-10-11 修正）。
     *
     * <p>为什么要改：原实现把可见面<b>贪心合并成矩形</b>，但矩形之间<b>相邻仍会各自画边</b>
     * → L 形/阶梯/不规则矿脉留下大量内部接缝（用户反馈"很多方块不会合并"）。
     * 现在改为直接求<b>2D 区域的边界线段</b>：只有"隔壁没格子"的那一侧才算边界，
     * 因此<b>任何形状都零内部接缝</b>；再把同一直线上连续的边界段<b>合并成长线段</b>降 draw call。
     * <p>算出的线段按「世界直线」登记到 {@code lines} 里，共享棱的重复由 {@link #flushLines} 合并。
     */
    private static void outlinePlane(int d, int plane, Set<Long> cellKeys, Map<Long, List<long[]>> lines) {
        // 竖边（沿 v，位于 u=const）：u → {v...}
        Map<Integer, java.util.TreeSet<Integer>> vAtU = new HashMap<>();
        // 横边（沿 u，位于 v=const）：v → {u...}
        Map<Integer, java.util.TreeSet<Integer>> uAtV = new HashMap<>();
        for (long c : cellKeys) {
            int u = (int) (c >> 32);
            int v = (int) (c & 0xFFFFFFFFL);
            // 左/右边界、下/上边界（隔壁格不存在才算边界）
            if (!cellKeys.contains(cellKey(u - 1, v))) {
                vAtU.computeIfAbsent(u, k -> new java.util.TreeSet<>()).add(v);
            }
            if (!cellKeys.contains(cellKey(u + 1, v))) {
                vAtU.computeIfAbsent(u + 1, k -> new java.util.TreeSet<>()).add(v);
            }
            if (!cellKeys.contains(cellKey(u, v - 1))) {
                uAtV.computeIfAbsent(v, k -> new java.util.TreeSet<>()).add(u);
            }
            if (!cellKeys.contains(cellKey(u, v + 1))) {
                uAtV.computeIfAbsent(v + 1, k -> new java.util.TreeSet<>()).add(u);
            }
        }
        // 同一直线上连续的边界段先合并成长线段，再登记到对应的【世界直线】上
        emitVerticalRuns(vAtU, d, plane, lines);
        emitHorizontalRuns(uAtV, d, plane, lines);
    }

    /** 竖边合并：同一 u 上连续的 v 合成一条长线段 → 登记到世界直线 */
    private static void emitVerticalRuns(Map<Integer, java.util.TreeSet<Integer>> runs,
                                         int d, int plane, Map<Long, List<long[]>> lines) {
        for (Map.Entry<Integer, java.util.TreeSet<Integer>> e : runs.entrySet()) {
            final int u = e.getKey();
            int start = Integer.MIN_VALUE;
            int prev = Integer.MIN_VALUE;
            for (int v : e.getValue()) {
                if (start == Integer.MIN_VALUE) {
                    start = v;
                } else if (v != prev + 1) {
                    addVerticalRun(lines, d, plane, u, start, prev + 1);
                    start = v;
                }
                prev = v;
            }
            if (start != Integer.MIN_VALUE) {
                addVerticalRun(lines, d, plane, u, start, prev + 1);
            }
        }
    }

    /**
     * 把一条竖边（面内 u=const、v∈[v0,v1)）翻译成<b>世界直线</b>上的区间并登记。
     *
     * <p>竖边沿面内的 v 轴走，映射到哪根世界轴取决于面方向：
     * <ul>
     *   <li>{@code d=0/1}（±X 面：u=z、v=y）→ 沿 <b>Y</b>，固定 x=plane、z=u；</li>
     *   <li>{@code d=2/3}（±Y 面：u=x、v=z）→ 沿 <b>Z</b>，固定 x=u、y=plane；</li>
     *   <li>{@code d=4/5}（±Z 面：u=x、v=y）→ 沿 <b>Y</b>，固定 x=u、z=plane。</li>
     * </ul>
     * <p>固定坐标一律按<b>轴号升序</b>传入（X&lt;Y&lt;Z），与 {@link #packLineKey} 的约定一致。
     */
    private static void addVerticalRun(Map<Long, List<long[]>> lines, int d, int plane, int u,
                                       int v0, int v1) {
        switch (d) {
            case 0, 1 -> addRun(lines, 1, plane, u, v0, v1);
            case 2, 3 -> addRun(lines, 2, u, plane, v0, v1);
            default -> addRun(lines, 1, u, plane, v0, v1);
        }
    }

    /** 横边合并：同一 v 上连续的 u 合成一条长线段 → 登记到世界直线 */
    private static void emitHorizontalRuns(Map<Integer, java.util.TreeSet<Integer>> runs,
                                           int d, int plane, Map<Long, List<long[]>> lines) {
        for (Map.Entry<Integer, java.util.TreeSet<Integer>> e : runs.entrySet()) {
            final int v = e.getKey();
            int start = Integer.MIN_VALUE;
            int prev = Integer.MIN_VALUE;
            for (int u : e.getValue()) {
                if (start == Integer.MIN_VALUE) {
                    start = u;
                } else if (u != prev + 1) {
                    addHorizontalRun(lines, d, plane, v, start, prev + 1);
                    start = u;
                }
                prev = u;
            }
            if (start != Integer.MIN_VALUE) {
                addHorizontalRun(lines, d, plane, v, start, prev + 1);
            }
        }
    }

    /**
     * 把一条横边（面内 v=const、u∈[u0,u1)）翻译成<b>世界直线</b>上的区间并登记。
     * <ul>
     *   <li>{@code d=0/1}（±X 面）→ 沿 <b>Z</b>，固定 x=plane、y=v；</li>
     *   <li>{@code d=2/3}（±Y 面）→ 沿 <b>X</b>，固定 y=plane、z=v；</li>
     *   <li>{@code d=4/5}（±Z 面）→ 沿 <b>X</b>，固定 y=v、z=plane。</li>
     * </ul>
     */
    private static void addHorizontalRun(Map<Long, List<long[]>> lines, int d, int plane, int v,
                                         int u0, int u1) {
        switch (d) {
            case 0, 1 -> addRun(lines, 2, plane, v, u0, u1);
            case 2, 3 -> addRun(lines, 0, plane, v, u0, u1);
            default -> addRun(lines, 0, v, plane, u0, u1);
        }
    }

    /**
     * 登记一条<b>世界直线</b>上的一段区间。
     *
     * <p>为什么要按「世界直线」而不是「面轮廓」直接输出：一条 3D 棱会被<b>两个相邻方向</b>的
     * 可见面同时当成边界（沿 Y 的棱既是 +X 面的边界、也是 +Z 面的边界），两个面各登记一次 →
     * 同一条线被画两遍，棱处叠加、亮度与面内不一致，整圈看起来发脏
     * （用户实测反馈：「一个方块的 6 个面交界处」）。按直线分组后把区间求并集，
     * 就保证每条棱正好发一条线。
     * <p>（FTB Ultimine 是用合并后的 {@code VoxelShape#forAllEdges} + BitSet 去重达到同样效果。）
     *
     * @param dir 线段变化轴（0=X 1=Y 2=Z）
     * @param a   固定轴坐标（轴号较小的那个）
     * @param b   固定轴坐标（轴号较大的那个）
     * @param lo  沿 {@code dir} 的区间起点（含）
     * @param hi  沿 {@code dir} 的区间终点（不含）
     */
    private static void addRun(Map<Long, List<long[]>> lines, int dir, int a, int b,
                               long lo, long hi) {
        lines.computeIfAbsent(packLineKey(dir, a, b), k -> new ArrayList<>(4))
                .add(new long[]{lo, hi});
    }

    /** 把「方向轴 + 两个固定坐标」打包成 key（各占 26 位，足以覆盖 ±33M 的世界边界） */
    private static long packLineKey(int dir, int a, int b) {
        return ((long) dir << 52) | ((a & 0x3FFFFFFL) << 26) | (b & 0x3FFFFFFL);
    }

    /** 26 位有符号数还原（{@link #packLineKey} 的逆运算） */
    private static int unpack26(long v) {
        return (int) ((v << 38) >> 38);
    }

    /**
     * 把分组收集到的区间<b>求并集</b>后输出（重叠 / 相接的段合并成一段）。
     *
     * <p>⚠️ 必须求并集，而不是「遇到重复就丢掉」：同一条棱由两个面登记时，两个面各自合并出的
     * 区间<b>不一定相同</b>（各自的合并依据是本面 2D 集合的连续性），简单去重会丢掉一部分棱线。
     * 求并集是唯一正确且不重复的做法。
     */
    private static void flushLines(Map<Long, List<long[]>> lines) {
        for (Map.Entry<Long, List<long[]>> e : lines.entrySet()) {
            long key = e.getKey();
            int dir = (int) (key >>> 52);
            int a = unpack26((key >>> 26) & 0x3FFFFFFL);
            int b = unpack26(key & 0x3FFFFFFL);
            List<long[]> runs = e.getValue();
            runs.sort((x, y) -> Long.compare(x[0], y[0]));
            long lo = runs.get(0)[0];
            long hi = runs.get(0)[1];
            for (int i = 1; i < runs.size(); i++) {
                long[] r = runs.get(i);
                if (r[0] > hi) {
                    emitRun(dir, a, b, lo, hi); // 与当前段不相接 → 先输出，再另起一段
                    lo = r[0];
                    hi = r[1];
                } else if (r[1] > hi) {
                    hi = r[1]; // 重叠 / 相接 → 并入
                }
            }
            emitRun(dir, a, b, lo, hi);
        }
    }

    /** 把「方向轴 + 两个固定坐标 + 区间」还原成 3D 线段，追加进 {@link #faceBuf} */
    private static void emitRun(int dir, int a, int b, long lo, long hi) {
        final int o = ensureFaceCapacity();
        switch (dir) {
            // 沿 X：y=a、z=b
            case 0 -> {
                faceBuf[o] = lo;
                faceBuf[o + 1] = a;
                faceBuf[o + 2] = b;
                faceBuf[o + 3] = hi;
                faceBuf[o + 4] = a;
                faceBuf[o + 5] = b;
            }
            // 沿 Y：x=a、z=b
            case 1 -> {
                faceBuf[o] = a;
                faceBuf[o + 1] = lo;
                faceBuf[o + 2] = b;
                faceBuf[o + 3] = a;
                faceBuf[o + 4] = hi;
                faceBuf[o + 5] = b;
            }
            // 沿 Z：x=a、y=b
            default -> {
                faceBuf[o] = a;
                faceBuf[o + 1] = b;
                faceBuf[o + 2] = lo;
                faceBuf[o + 3] = a;
                faceBuf[o + 4] = b;
                faceBuf[o + 5] = hi;
            }
        }
        // 顺手把这根线的包围盒一并生成（世界坐标，含 CULL_PAD 余量），渲染帧直接用
        final double x1 = faceBuf[o], y1 = faceBuf[o + 1], z1 = faceBuf[o + 2];
        final double x2 = faceBuf[o + 3], y2 = faceBuf[o + 4], z2 = faceBuf[o + 5];
        faceBoxes[faceCount] = new net.minecraft.world.phys.AABB(
                Math.min(x1, x2) - CULL_PAD, Math.min(y1, y2) - CULL_PAD, Math.min(z1, z2) - CULL_PAD,
                Math.max(x1, x2) + CULL_PAD, Math.max(y1, y2) + CULL_PAD, Math.max(z1, z2) + CULL_PAD);
        faceCount++;
    }

    /** 为下一条线段腾位置（不够就倍增），返回它的起始下标 */
    private static int ensureFaceCapacity() {
        final int off = faceCount * SEG_STRIDE;
        if (off + SEG_STRIDE > faceBuf.length) {
            // ⚠️ 每次只多要 6 个 double，倍增后必然够（初值 64 ≥ 6）
            final int cap = Math.max(64, faceBuf.length * 2);
            faceBuf = java.util.Arrays.copyOf(faceBuf, cap);
            faceBoxes = java.util.Arrays.copyOf(faceBoxes, cap);
        }
        return off;
    }

    /** 垂直椭球判定：{@code dy² <= SCAN_Y_RADIUS_SQ - slackSq}（slackSq 传区段半高的平方做粗筛） */
    private static boolean withinYSq(BlockPos center, int y, int slack) {
        double dy = y - center.getY();
        long limit = (long) SCAN_Y_RADIUS_SQ - (long) slack * slack;
        return dy * dy <= limit;
    }

    /**
     * 画上次扫描算好的边界线段。
     * <p>★ 2026-10-11 用户要求：<b>一条线段只画 1 根线</b>（顶点直接画，不用 renderLineBox
     * —— 后者会把一条线画成 4 条棱的细长盒子，数量级放大 4 倍）。
     * <p>★ 2026-10-11 降渲染压力：加<b>视锥剔除</b>。64 格半径扫出来的线段原本每帧全提交，
     * 但其中相当一部分在<b>视野外甚至身后</b>（背对时约一半），属于纯白干。现在先用线段包围盒
     * 过一遍视锥测试，只提交看得见的。
     * <p>阶段与穿墙方案与 {@link ClientTreasureEvents} 完全一致（1.20.1 已验证的管线）。
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        if (faceCount == 0) {
            return; // 还没扫描过 → 什么都不画（零开销）
        }
        if (isIrisShadowPass()) {
            return; // 光影阴影 pass 跳过（与寻宝大师一致）
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        var cam = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.getPosition().x, -cam.getPosition().y, -cam.getPosition().z);

        net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = scanBuffers();
        VertexConsumer vertex = bufferSource.getBuffer(SCAN_LINES);
        final org.joml.Matrix4f mat = poseStack.last().pose();
        // ★ 视锥剔除（★ 2026-10-11 降渲染压力）：64 格半径扫出来的线段原本每帧全提交，但其中
        //   相当一部分在视野外甚至<b>身后</b>（背对时约一半），属于纯白干。包围盒在重建时就已生成，
        //   这里只是零分配地过一遍平面测试。
        final net.minecraft.client.renderer.culling.Frustum frustum = event.getFrustum();
        // 每条线段只提交 2 个顶点 → 1 根线（顶点数 = 线段数 × 2，不再是 × 8）
        // 单层亮绿细线（参照 FTB Ultimine 的观感；共享棱已在 flushLines 里去过重）
        for (int k = 0; k < faceCount; k++) {
            if (!frustum.isVisible(faceBoxes[k])) {
                continue; // 视野外 → 不提交
            }
            final int i = k * SEG_STRIDE;
            addLine(vertex, mat, faceBuf[i], faceBuf[i + 1], faceBuf[i + 2],
                    faceBuf[i + 3], faceBuf[i + 4], faceBuf[i + 5]);
        }
        // 穿墙双保险（同寻宝大师）：NO_DEPTH_TEST shard + 全局 disableDepthTest
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        bufferSource.endBatch(SCAN_LINES);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();

        poseStack.popPose();
    }

    /**
     * 提交一条线段（2 个顶点 = 1 根线）。
     * <p>★ 不施加任何偏移：端点严格落在区域边界的整数格上，角点与三岔口天然精确相接。
     * <p>⚠️ 1.20.1 的 lines() 格式是 POSITION_COLOR_NORMAL（含法线）→ 用正常 (0,0,1) 占位。
     */
    private static void addLine(VertexConsumer vertex, org.joml.Matrix4f mat,
                                double x1, double y1, double z1, double x2, double y2, double z2) {
        vertex.vertex(mat, (float) x1, (float) y1, (float) z1)
                .color(LINE_R, LINE_G, LINE_B, OUTLINE_ALPHA)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
        vertex.vertex(mat, (float) x2, (float) y2, (float) z2)
                .color(LINE_R, LINE_G, LINE_B, OUTLINE_ALPHA)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    /** Iris 光影软检测（无 Iris 时返回 false）：阴影 pass → 跳过渲染。 */
    private static boolean isIrisShadowPass() {
        return IrisCompat.isShadowPass();
    }
}
