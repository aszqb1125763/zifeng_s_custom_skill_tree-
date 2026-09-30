package org.zifeng.skilltree.client.screen;


import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.network.AuraTargetC2SPacket;
import org.zifeng.skilltree.network.LearnSkillC2SPacket;
import org.zifeng.skilltree.network.OpenSkillTreeC2SPacket;
import org.zifeng.skilltree.network.ResetSkillC2SPacket;
import org.zifeng.skilltree.network.SetSkillLevelC2SPacket;
import org.zifeng.skilltree.network.SetSkillToggleC2SPacket;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 曜芒座技能树界面：
 * <ul>
 *   <li>四纵列：基础属性 / 特殊增幅 / 终极节点 / 杀戮光环</li>
 *   <li>同一纵列技能垂直排列间隔 2 像素，按钮同尺寸；纵列水平间隔 30 像素</li>
 *   <li>淡灰背景 + 淡蓝边框；滚轮缩放；左键拖动</li>
 *   <li>右侧属性面板：实时显示属性与技能点</li>
 *   <li>开关面板：所有已学技能可点击启用/禁用；杀戮光环武器可切换目标模式</li>
 * </ul>
 */
public class SkillTreeScreen extends Screen {
    /**
     * 本地化快捷取翻译（客户端 GUI 用）：ui.zifeng_s_custom_skill_tree.&lt;key&gt;
     *
     * <p>⚠️ 2026-09-19 性能优化（用户：「显示不要实时渲染，按原版按钮那样做」）：
     * 原实现每次调用都 {@code Component.translatable(...)} 新建对象 + 走语言表 getString()。
     * 列表界面每帧要取上百次（类别行 10 次 + 行内固定文案 × 可见行数），纯 GC/查找开销。
     * 现在改为静态缓存——语言只会在资源重载时变，切换语言后界面会重建（{@link #init} 清缓存）。
     */
    private static String t(String key) {
        String cached = LANG_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String value = Component.translatable("ui.zifeng_s_custom_skill_tree." + key).getString();
        LANG_CACHE.put(key, value);
        return value;
    }

    /** 语言字符串缓存（key → 已翻译文本）；资源重载/重新打开界面时清空 */
    private static final Map<String, String> LANG_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 字体（供子界面访问，Screen.font 是 protected） */
    net.minecraft.client.gui.Font font() {
        return this.font;
    }

    // ============ 布局常量（2026-09-19 L4 分区重构） ============
    // ★ 结构（参考原版快捷键界面 + miui 的 ZListRow「一行一贴片」）：
    //     ┌──── 分区框1：标题行（技能点）──────────────────┐
    //     └──────────────────────────────────────────────┘
    //     ┌──── 分区框2：类别行（居中）───────────────────┐
    //     └──────────────────────────────────────────────┘
    //     ┌──── 分区框3：技能行列表 ─────────────────────┐
    //     │ 〔一整张贴片 = 技能内容区 │ Q │ E │ R 〕      │  ← 整行一个底色/一个外框，内部竖线分格
    //     └──────────────────────────────────────────────┘
    //   ★ 宽度自适应：贴片宽度随窗口拉伸 → 进度条吃弹性空间，Q/E/R 贴在行右端。

    /** 技能行高（紧凑列表行；★ 固定不随窗口变化 —— 与原版列表一致） */
    private static final int BUTTON_HEIGHT = 28;
    /** 行间距（贴片之间的缝；圆角贴片需要呼吸空间，Miuix 列表行间距 2） */
    private static final int VERTICAL_SPACING = 3;

    // ---- 行内布局 ----
    /** 图标（16×16，垂直居中） */
    private static final int R_ICON_X = 3;
    /** 名称区（固定宽） */
    private static final int R_NAME_X = 22;
    private static final int R_NAME_W = 118;
    /**
     * 下一级消耗区（★ 2026-09-20 改为<b>右对齐</b>）。
     *
     * <p>为什么必须改对齐：要让「消耗 → 等级」的间距固定，基准就必须是<b>文字右边缘</b>。
     * 原先左对齐时文字只占区域左半边，与右对齐的等级文字（只占区域右半边）
     * 两个空白叠在一起 → 视觉间距 58px，无法可控。
     * <p>右边界 = {@code R_COST_X + R_COST_W} = <b>266</b>。
     * <p>★ 2026-09-20 二次调整：列整体右移（原 144），使「消耗 → 等级 → 进度条」的
     * <b>区域间距</b>分别为 20 / 20，且进度条右端距快捷键格 10。
     */
    private static final int R_COST_X = 212;
    private static final int R_COST_W = 54;
    /**
     * 属性加成右对齐后，与消耗列左边界之间的固定间距（★ 2026-09-20 用户指定 5px）。
     * <p>布局变为：{@code [名称区] …空… [属性]──5──[消耗]──20──[等级]──20──[进度条]}
     */
    private static final int ATTR_RIGHT_GAP = 5;
    /**
     * 属性加成区的可用宽度上限 = 属性右对齐线 − 名称区右边界。
     * <pre>212 − 5 − (22 + 118) = 67</pre>
     * <p>属性改为独立右对齐区后，它不再占用名称区宽度；超宽时仍按本上限裁切。
     */
    private static final int ATTR_AREA_W =
            R_COST_X - ATTR_RIGHT_GAP - (R_NAME_X + R_NAME_W);
    /**
     * 等级文字区（★ 2026-09-20 位置调整：从「进度条右侧」移到「消耗数字后面」）。
     *
     * <p>列序：{@code 名称 │ 属性 │ 消耗 │ 等级 │ 进度条（弹性）}。
     * <p>区内<b>右对齐</b> → 右边界 = {@code R_LV_X + R_LV_W} = <b>338</b>。
     * <p>★ 2026-09-20 二次调整：随消耗列一同右移（原 206），保持「消耗→等级」区域间距 20。
     */
    private static final int R_LV_X = 286;
    private static final int R_LV_W = 52;

    /**
     * ★★ 2026-09-20 用户要求：<b>「消耗 / 等级 / 进度条」三者之间的间距统一 20px</b>。
     *
     * <p>用户原话：「我需要的是，等级消耗，等级显示，和进度条，要有个视觉上的和谐距离，
     * 因为这三个之间的间距完全不一致，甚至是突兀」→「三者之间的间距你改成 30px」。
     *
     * <p>改前的实际区域间距是 <b>−2px / 6px</b>（消耗区与等级区甚至重叠 2px），确实突兀。
     * <p>现在：
     * <pre>
     *   消耗文字右边缘 198 ──(≈30)──► 等级右边缘 258 ──(20)──► 进度条起点 278
     * </pre>
     * 注：等级文字宽度随内容变（"50/50"≈30px、"MAX"≈22px），右对齐时其<b>左</b>边浮动，
     * 所以「消耗→等级」实测在 <b>25~38px</b> 之间（典型值 30），而「等级→进度条」恒为 20。
     */
    private static final int COL_GAP = 20;

    /** 等级进度条：★ 弹性区。起点 = 等级区右边界 + {@link #COL_GAP} = 358 */
    private static final int R_BAR_X = R_LV_X + R_LV_W + COL_GAP;
    private static final int R_BAR_MIN_W = 40;
    /**
     * 等级进度条<b>长度上限</b>（★ 2026-09-20）。
     *
     * <p>用户原话：「我只是说让你把技能等级后的可以拖动调整等级的进度条缩短 25%」。
     * 因为进度条是弹性的（会吃掉内容区所有剩余宽度），要“缩短”它有两种做法：
     * <ul>
     *   <li>❌ 收窄整行（{@link #ROW_BASE_W}）—— 那会把<b>整个技能贴片</b>一起缩短，
     *       用户明确指出这不是他要的（已回退）。</li>
     *   <li>✅ 给进度条本身加长度上限（即本常量）—— 只影响那根可拖动的条。</li>
     * </ul>
     * <p>GUI 缩放 3（内容区 488）下自然长为 488−358−10 = 120，正好等于本上限。
     */
    private static final int R_BAR_MAX_W = 120;
    private static final int R_BAR_H = 4;
    /**
     * 进度条右侧留白。
     * <p>用户反馈过「进度条和右边的按键调整有点细微的重叠」—— 因为旧公式 {@code contentW() - R_BAR_X}
     * 会让条子右端<b>正好顶到</b> Q 键格边界。留白彻底避免。
     */
    private static final int BAR_RIGHT_PAD = 10;
    /** 按键格宽度（贴片内部分格，三格等宽） */
    private static final int KEY_BOX_WIDTH = 24;
    private static final int KEY2_BOX_WIDTH = 24;
    private static final int KEY3_BOX_WIDTH = 24;

    // ---- 分区框（三个独立框，参考原版界面边框）----
    /** 分区框距窗口边缘 */
    private static final int FRAME_MARGIN = 8;
    /** 分区框线宽 */
    private static final int FRAME_LINE = 1;
    /** 分区框之间的竖直间隙 */
    private static final int FRAME_GAP = 2;
    /** 分区框内元素距框线的左右内边距 */
    private static final int FRAME_PAD_X = 4;
    /** 大贴片 → 子贴片的内缩（形成嵌套观感） */
    private static final int SUB_INSET = 5;

    // ---- 嵌套贴片（2026-09-19：底层大贴片 → 三个子贴片 → 技能贴片）----
    //   层级：页面底（L5）→ 大贴片 → 子贴片 → 技能贴片（每行一张）
    //   风格沿用 Miuix 的做法：靠「色阶 + 描边」分层，不靠投影。
    /** 大贴片圆角 */
    private static final int R_BIG = 12;
    /** 子贴片圆角（比大贴片小一档，形成内嵌观感） */
    private static final int R_SUB = 8;
    /** 技能贴片圆角 */
    private static final int R_ROW = 6;
    /** 大贴片底色 */
    private static final int C_BIG_BG = 0xFFF4F5F8;
    /** 大贴片描边 */
    private static final int C_BIG_EDGE = 0xFFB9BDC8;
    /** 子贴片底色 */
    private static final int C_SUB_BG = 0xFFE8EAF0;
    /** 子贴片描边 */
    private static final int C_SUB_EDGE = 0xFFC9CCD5;
    /** 技能贴片内部格线（淡） */
    private static final int C_CELL_LINE = 0x40FFFFFF;
    /** 进度条轨道（半透明白 —— Miuix sliderBackground 的做法） */
    private static final int C_BAR_TRACK = 0x59FFFFFF;
    /** 进度条生效刻度 */
    private static final int C_BAR_TICK = 0xFFFFDD44;
    /** 进度条高（缩放后） */
    private static final int R_BAR_H2 = 6;

    // ---- 技能关闭角标（2026-09-20：改为贴图绘制）----
    /**
     * 技能关闭（开关关闭）时，名称左侧的角标贴图：红色禁止符（圆环 + 斜杠）。
     *
     * <p>用户要求原话：「技能关闭后的灰色符号太淡了，不够鲜艳，可以换成红色的相关醒目显示，
     * 毕竟技能激活了，这个图标就消失了」——原实现（还有更早的版本）用字形画，
     * 字形来自 Unifont 回退（点阵、形状糙），且受玩家资源包字体影响，形状也做不出描边。
     * 改成贴图后：形状可控、不受字体包影响、像素风格与 UI 统一。
     *
     * <p>贴图由 {@code tools/SkillDisabledIconGen.java} 程序化生成（12×12 RGBA，4× 超采样抗锯齿）。
     * ⚠️ 生成器与贴图必须同步改：改图标后要重新跑生成器，否则贴图与常量尺寸会对不上。
     */
    private static final net.minecraft.resources.ResourceLocation TEX_DISABLED_MARK =
            org.zifeng.skilltree.skill.Skills.id("textures/ui/skill_disabled.png");
    /** 角标贴图边长（px）—— 必须与生成器的 SIZE 一致 */
    private static final int MARK_SIZE = 12;
    /** 角标与名称之间的间距 */
    private static final int MARK_GAP = 3;

    /** 属性面板宽度（2026-08-29：150→200，英文 label/数值更长，防重叠） */
    private static final int PANEL_WIDTH = 200;

    // ---- 分类按钮行（第一行：10 个类别）----
    /** 分类按钮高 */
    private static final int CAT_BTN_H = 18;
    /** 分类按钮左右内边距 */
    private static final int CAT_BTN_PAD = 8;
    /** 分类按钮间距 */
    private static final int CAT_BTN_GAP = 3;

    /** 行区域顶部距分类行的间距 */
    private static final int LIST_TOP_GAP = 6;
    /** 右侧滚动条宽 */
    private static final int SCROLLBAR_W = 6;
    /** 滚动条距右边框的间距 */
    private static final int SCROLLBAR_MARGIN = 5;
    /** 贴片距分区框内顶的内边距（2026-09-19） */
    private static final int ROW_PAD_TOP = 3;
    /**
     * 基线设计空间宽度 —— 「1920×1080 显示器 + 游戏 GUI Scale 3」换算出的逻辑宽度
     * （{@code 1920 ÷ 3 = 640}）。
     *
     * <p>{@link #uiScale()} 里 {@code z = 1}（基准）时设计空间恰好是这个宽度；
     * <b>缩小</b>（{@code z < 1}）时比它大，<b>放大</b>（{@code z > 1}）时比它小。
     */
    private static final int BASE_DESIGN_W = 640;
    /**
     * 技能贴片的<b>基准长度</b>（= 基线设计空间下可用的行宽，约 603）。
     *
     * <p>★ 2026-09-21 用户要求：「单个技能等级贴片就固定长度了，左对齐」——
     * <ul>
     *   <li><b>缩小</b>时贴片长度<b>固定不变</b>：多出来的屏幕空间全部留在贴片右侧
     *       （即「Q/E/R 最右格 ←→ 竖向滚动条」之间，从基准 {@link #SCROLLBAR_MARGIN} 的 5px 往上增）。</li>
     *   <li><b>放大</b>时整体物理尺寸自然变大（可用缩放）。</li>
     * </ul>
     *
     * <p><b>为什么必须固定</b>：贴片原本铺满可用宽度（旧上限是 {@code Integer.MAX_VALUE}，等于没上限）。
     * 而缩小时设计空间会变大（{@code 屏物理宽 / (3z)}），贴片被跟着拉得很宽，
     * 但内部固定列（名称 22 / 属性 / 消耗 212 / 等级 286 / 进度条 358）是
     * <b>常量、不会跟着平移</b> → 左侧内容全挤在 358 个设计单位里、Q/E/R 被推到很右边，
     * 中间空出一大片（用户反馈的「图标和文字全挤在一起」）。
     */
    private static final int ROW_BASE_W = BASE_DESIGN_W
            - FRAME_MARGIN * 2 - FRAME_LINE * 2 - FRAME_PAD_X * 2
            - SCROLLBAR_W - SCROLLBAR_MARGIN;

    /**
     * 收缩阶梯能接受的<b>最小内容区宽</b>（= 属性区/列间距全部牺牲后的底线）。
     *
     * <p>组成：名称列 140 + 消耗列 54 + 等级列 52 + 进度条最小宽 40 + 右留白 10 = 296。
     * <p>低于它的内容区会连「名称+消耗+等级」都摆不下 → 视为不可用。
     */
    private static final int MIN_VIABLE_CONTENT_W =
            R_NAME_X + R_NAME_W + R_COST_W + R_LV_W + R_BAR_MIN_W + BAR_RIGHT_PAD;
    /**
     * 技能行能正常显示所需的<b>最小设计空间宽</b>（★ 2026-09-21 替代旧的
     * {@code MIN_SAFE_DESIGN_W=528}）。
     *
     * <p>= 最小内容区宽 + 三格快捷键 + 分区框/滚动条开销（{@code 37}）= <b>405</b>。
     *
     * <p><b>为什么要改</b>：旧值 528 是按「固定列 408 + 三格 72 + 留白 48」拍的，
     * 比实际需要的 405 大很多 → {@code uiScale()} 里 {@code z ≤ 屏宽/(3×528)}，
     * 用户的 1920 宽窗口上封顶 {@code z=1.2121}，于是 <b>{@code +22 ~ +100} 全是死区</b>
     * （实测 +50 与 +100 看到的完全一样）。
     * 现在改用阶梯的可用下限：既不再出现死区，又能保证不会挤成重叠。
     */
    private static final int MIN_VIABLE_DESIGN_W = MIN_VIABLE_CONTENT_W
            + KEY_BOX_WIDTH + KEY2_BOX_WIDTH + KEY3_BOX_WIDTH
            + FRAME_MARGIN * 2 + FRAME_LINE * 2 + FRAME_PAD_X * 2 + SCROLLBAR_W + SCROLLBAR_MARGIN;
    /**
     * 贴片长度的<b>下限</b>（= 最小内容区宽 + 三格快捷键 = 368）。
     *
     * <p>⚠️ 没有它会把布局压坏：实测 offset=-50 时按比例算出的目标长是 301 →
     * 内容区只剩 229（< 296）→ 连「名称+消耗+等级」都摆不下，
     * 等级列（194~246）会反过来越界 17px。
     * <p>有了下限后，贴片最窄停在 368 → 内容区恰好 296 → 刚好容下最小可用布局
     * （属性区/列间距已全部牺牲，进度条保持最小宽 40）。
     */
    private static final int ROW_MIN_W = MIN_VIABLE_CONTENT_W
            + KEY_BOX_WIDTH + KEY2_BOX_WIDTH + KEY3_BOX_WIDTH;

    /**
     * 贴片的<b>目标长度</b>（★ 2026-09-21 用户要求）。
     *
     * <p>用户原话：「向 +10 这么加的时侯可以使用缩放，但是 -10 这么减小的时候全都不用缩放，
     * 只用固定宽度，增加右边的空间就行」+「0 到 -50 之间……维持 0 基准数字的大小，
     * 只加大右边的空间」。
     *
     * <p>于是负向（缩小）时：
     * <ul>
     *   <li>元素大小<b>不缩</b>（文字/图标/行高维持基准）→ 因为 {@code r} 已被基础倍率钉住</li>
     *   <li>把「贴片目标长度」按比例变短 → 多余空间全留在贴片右侧
     *       （即 Q/E/R 最右格 ←→ 滚动条 之间的间距变大）</li>
     * </ul>
     * <p>正向（放大）时返回 {@link #ROW_BASE_W}：此时设计空间变小、
     * 可用宽度自然低于基准，贴片会被框住不会溢出。
     * <p>自适应模式不受影响（它自己会缩到刚好装下）。
     */
    private int rowTargetW() {
        if (org.zifeng.skilltree.client.SkillKeyBinds.isSkillTreeGuiAdaptive()) {
            return ROW_BASE_W;
        }
        final int off = org.zifeng.skilltree.client.SkillKeyBinds.getSkillTreeGuiOffset();
        if (off >= 0) {
            return ROW_BASE_W;
        }
        // off ∈ [-50, 0) → 目标长度 603 → 301（线性），但不低于 ROW_MIN_W
        //   （下限定为「刚好容下最小可用布局」，否则内容区会被压坏）
        return Math.max(ROW_MIN_W, ROW_BASE_W * (100 + off) / 100);
    }

    // ---- 搜索框（2026-09-20 用户要求：标题行右侧空旷，加搜索功能）----
    /** 搜索框宽 */
    private static final int SEARCH_W = 150;
    /** 搜索框高（标题行内高 21，留上下各 3px） */
    private static final int SEARCH_H = 15;
    /** 搜索框内左右内边距 */
    private static final int SEARCH_PAD = 4;
    /** 搜索框圆角 */
    private static final int SEARCH_RADIUS = 4;
    /**
     * ✕ 清空按钮的占位宽度（★ 2026-09-20 用户要求：搜索框要有可视的清空按钮）。
     *
     * <p>无论有没有关键词都<b>始终预留</b>这块宽度 —— 否则输入第一个字时
     * 文本会被 ✕ 挤得左右跳动。
     */
    private static final int SEARCH_CLEAR_W = 13;
    /**
     * 搜索历史保留条数（★ 2026-09-20 用户要求：JEI 式操作，↑↓ 翻历史关键词）。
     * <p>超出后从最旧一条开始丢。
     */
    private static final int SEARCH_HISTORY_MAX = 20;

    private double skillPoints;
    private final Map<String, Integer> learnedSkills = new HashMap<>();
    private final Map<String, Boolean> toggles = new HashMap<>();
    private final Map<String, Integer> activeLevels = new HashMap<>();
    private boolean auraEnabled = true;
    /** 各光环目标模式（2026-08-13：每个光环独立，技能ID → 0敌对/1友好/2所有） */
    private final Map<String, Integer> auraTargetModes = new HashMap<>();
    private final List<SkillButton> buttons = new ArrayList<>();

    private int lastMouseX;
    private int lastMouseY;
    private int panelScroll = 0; // 属性面板滚动偏移（0 = 顶部）
    /** 当前悬停按钮的 tooltip 边界 [x, y, w, h]（屏幕坐标，预计算供图标跳过判定） */
    private int[] activeTooltipBounds = null;
    /** 当前悬停按钮的 tooltip 行列表（2026-09-15：与 activeTooltipBounds 同帧构建，绘制时直接复用） */
    private java.util.List<TooltipLine> activeTooltipLines = null;
    /** 悬停 tooltip 的跨帧缓存；鼠标移动时只重算位置，不重复创建文本组件。 */
    private String tooltipCacheSkillId = null;
    private int tooltipCacheFingerprint = Integer.MIN_VALUE;
    private java.util.List<TooltipLine> tooltipCacheLines = null;
    /** 按键设置窗口：当前正在设置按键的技能（null = 窗口关闭）；窗口与技能界面同一图层 */
    private String keyBindSkillId = null;
    /** 按键设置窗口：是否正在监听按键输入（点击"设置"后为 true） */
    private boolean keyBindListening = false;
    /** 第二列按键框（等级/目标循环）监听状态：当前正在设置的技能（null = 无） */
    private String levelKeyBindSkillId = null;
    private boolean levelKeyBindListening = false;
    /** 第三列按键框（功能触发键）监听状态：当前正在设置的技能（null = 无；2026-09-07） */
    private String triggerKeyBindSkillId = null;
    private boolean triggerKeyBindListening = false;

    /** 当前打开的子界面（null = 无；2026-09-01 子界面系统） */
    private SkillSubScreen activeSubScreen = null;

    // ============ 列表式布局状态（2026-09-19） ============
    /** 当前选中的技能类别索引（0~9，对应 10 列；默认 0） */
    private int selectedCategory = 0;
    /** 构造阶段先缓存配置，等 init() 算出真实列表视口后再恢复滚动位置。 */
    private int pendingScrollY = 0;
    private int pendingCatScrollX = 0;
    private boolean pendingViewStateRestore = false;
    /** 最近一次正常类别视图的滚动位置；搜索期间的临时滚动不会覆盖它。 */
    private int lastNormalScrollY = 0;
    /** 视图状态有未落盘变更时置位；关闭/移除时做最后一次兜底保存。 */
    private boolean viewStateNeedsSave = false;
    /**
     * 搜索关键词（★ 2026-09-20 新增）。
     * <p>空串 = 未搜索 → 列表显示 {@link #selectedCategory} 的技能；
     * 非空 = 列表切成<b>跨类别搜索结果</b>（清空后自动恢复原类别）。
     */
    private String searchQuery = "";
    /** 搜索框是否处于输入状态（聚焦）：聚焦时键盘输入进搜索框，并拦截快捷键 */
    private boolean searchFocused = false;
    /**
     * 搜索框的<b>真实输入控件</b>（★ 2026-09-20 改用原版 EditBox 承载输入）。
     *
     * <p>改动原因（输入法问题）：原来的纯自绘搜索框从未调用过
     * {@code Screen.setFocused(...)}，导致 {@code getFocused()} 恒为 {@code null}；
     * 输入法类模组（IMBlocker 等）据此判定「当前不是文本输入场景」，
     * 于是强制把系统输入法压成英文并锁住切换 —— 中文玩家完全打不了中文。
     * <p>换成原版 EditBox 后 {@code getFocused()} 会返回本控件，输入法模组即可放行。
     *
     * <p>控件详情（含 AE2 来源说明）见 {@link SkillSearchBox}。
     * <p>{@code null} = 尚未初始化（{@link #init()} 之前）。
     */
    private SkillSearchBox searchBox = null;
    /**
     * 「正在由程序回写关键词」标志（见 {@link #applySearchQuery}）。
     *
     * <p>回写会触发 {@code EditBox} 的 responder，不拦住就会
     * 「回写 → responder → 再 rebuildButtons」白跑一轮；
     * 更糟的是可能递归。置位期间 responder 直接返回。
     */
    private boolean syncingSearchBox = false;
    /**
     * 「已向服务端退订全局状态」标志（★ 2026-09-20）。
     *
     * <p>{@link #onClose()} 之后必定还会走 {@link #removed()}，用本标志保证只发一次退订包。
     */
    private boolean subscriptionClosed = false;
    /**
     * 搜索结果里「当前定位到」的技能 ID（★ 2026-09-20 用户要求：回车可在结果间顺序切换并定位）。
     *
     * <p>存 <b>ID 而不是索引</b>：改关键词后结果列表会变，索引会错位；
     * 存 ID 则「原选中技能仍在新结果里 → 继续选中它」，不在则自然失高亮。
     * <p>{@code null} = 未选中任何项。
     */
    private String searchSelectedId = null;
    /**
     * 搜索历史（★ 2026-09-20 用户要求：JEI 式操作 —— ↑↓ 翻历史关键词）。
     *
     * <p>只在<b>回车确认 / 失焦</b>时记录（用户指定），不是每敲一个字都记 ——
     * 否则历史会被「护」「护甲」「护甲x」这类半成品刷满，↑↓ 完全没法用。
     *
     * <p>用 {@code static}：界面重开（换维度、重登）后历史仍在，
     * 符合玩家对搜索框的预期。连续重复的关键词不会重复入栈
     * （否则 ↑ 连按几下都停在同一个词上）。
     */
    private static final List<String> SEARCH_HISTORY = new ArrayList<>();
    /** 历史游标：-1 = 未在翻历史（停在「当前输入」上）；>=0 = 指向 {@link #SEARCH_HISTORY} 的某项 */
    private static int searchHistoryIndex = -1;
    /** 开始翻历史前的当前输入；↓ 翻回最新一项之后时恢复它（否则原来的输入就丢了） */
    private static String searchHistoryDraft = "";

    /** 技能列表竖向滚动偏移（像素） */
    private int scrollY = 0;
    /** 滚动上限（= max(0, 内容高 - 视口高)），重建时重算 */
    private int scrollMax = 0;
    /** 正在拖动生效等级滑块的技能；拖动时等级文字、填充和服务端状态同步更新。 */
    private String draggingLevelSkillId = null;
    /** 是否正在拖动右侧竖向滚动条（★ 2026-09-21 新增） */
    private boolean listScrollbarDragging = false;
    /** 拖动开始时「光标相对滑块顶部」的偏移（保持抓住哪儿就从哪儿拖，不跳） */
    private double listScrollbarGrabDy = 0;
    /** 分类按钮行横向滚动偏移（像素；按钮太多时可滚） */
    private int catScrollX = 0;
    /** 分类按钮行内容总宽（重建时重算） */
    private int catContentW = 0;
    /** 类别栏上次采用的可用宽；避免不同事件路径用不同坐标系重算造成左右跳动。 */
    private int catLayoutAvailW = -1;
    /** 分类按钮行的按钮左边界（与 {@link #catContentW} 配合做横向滚动，屏幕坐标） */
    private final int[] catButtonX = new int[10];
    /** 分类按钮行的按钮宽度（各按钮宽度不同，随文字长短） */
    private final int[] catButtonW = new int[10];
    /** 分类按钮标题文本（重建时解析一次并缓存，避免每帧 10 次语言查询；2026-09-19 性能优化） */
    private final String[] catTitles = new String[10];

    /**
     * 字体行高（安全取）。
     * <p>⚠️ 2026-09-19 崩溃修复：`Screen.font` 要到 {@link #init()} 才被赋值，
     * 而 {@link #rebuildButtons()} 在<b>构造函数</b>里就会被调（updateData）——
     * 此时直接读 {@code font.lineHeight} 会 NPE（直接进游戏一点开界面就崩）。
     * 字体未就绪时回退到原版默认行高 9，init() 后会用真实值重建。
     */
    private int lineHeight() {
        return font != null ? font.lineHeight : 9;
    }

    // ========================================================================
    // 几何（2026-09-19 贴片嵌套）：大贴片 → 三个子贴片 → 技能贴片 —— 全部从窗口尺寸推导
    // ========================================================================

    // ---- 底层大贴片（最下面那一张，居窗口内边距）----
    private int bigLeft() {
        return FRAME_MARGIN;
    }

    private int bigRight() {
        return width - FRAME_MARGIN;
    }

    private int bigTop() {
        return FRAME_MARGIN;
    }

    private int bigBottom() {
        return height - FRAME_MARGIN;
    }

    // ---- 子贴片（大贴片向内缩 SUB_INSET，三个等宽）----
    private int frameLeft() {
        return bigLeft() + SUB_INSET;
    }

    private int frameRight() {
        return bigRight() - SUB_INSET;
    }

    /** 分区框内宽（框线内侧到内侧） */
    private int frameInnerW() {
        return Math.max(0, frameRight() - frameLeft() - FRAME_LINE * 2);
    }

    // ---- 子贴片 1：标题行（技能点）----
    private int titleFrameTop() {
        return bigTop() + SUB_INSET;
    }

    private int titleFrameH() {
        return HEADER_PAD * 2 + lineHeight();
    }

    private int titleFrameBottom() {
        return titleFrameTop() + titleFrameH();
    }

    // ---- 分区框 2：类别行 ----
    private int catFrameTop() {
        return titleFrameBottom() + FRAME_GAP;
    }

    private int catFrameH() {
        return CAT_BTN_H + LIST_TOP_GAP * 2;
    }

    private int catFrameBottom() {
        return catFrameTop() + catFrameH();
    }

    // ---- 分区框 3：技能行列表 ----
    private int listFrameTop() {
        return catFrameBottom() + FRAME_GAP;
    }

    /** 技能行子贴片底部（留出右下角两个功能按钮的高度） */
    private int listFrameBottom() {
        return bigBottom() - SUB_INSET - BOTTOM_BTN_H - BOTTOM_BTN_MARGIN - 4;
    }

    private int listFrameH() {
        return Math.max(0, listFrameBottom() - listFrameTop());
    }

    // ---- 行（贴片）----

    /** 贴片左边界（屏幕坐标）：分区框内 + 内边距 */
    private int rowLeft() {
        return frameLeft() + FRAME_LINE + FRAME_PAD_X;
    }

    /** 贴片右边界（屏幕坐标）：分区框内 - 内边距 - 让出竖向滚动条 */
    private int rowRight() {
        return frameRight() - FRAME_LINE - FRAME_PAD_X - SCROLLBAR_W - SCROLLBAR_MARGIN;
    }

    /**
     * 贴片总宽（= 内容区 + Q + E + R 三格，整行一张贴片）。
     *
     * <p>★ 2026-09-21：贴片长度以 {@link #ROW_BASE_W} 为基准（用户要求「固定长度、左对齐」）。
     * <ul>
     *   <li><b>放大</b>（{@code z > 1}）→ 设计空间变小，可用宽度可能低于基准 → 取可用宽度
     *       （贴片仍会随窗口撑开，不会跑到框外）。</li>
     *   <li><b>缩小</b>（{@code z < 1}）→ 设计空间变大、可用宽度超过基准 → 取基准，
     *       贴片长度不变，多余空间全部留在右侧（用户要求：「增加右边的空间就行」）。</li>
     * </ul>
     */
    int rowW() {
        final int avail = Math.max(0, rowRight() - rowLeft());
        return Math.min(avail, rowTargetW());
    }

    /** 贴片内「技能内容区」宽（= 贴片总宽 - 三格宽）—— 进度条的弹性空间来源 */
    int contentW() {
        int w = rowW() - (KEY_BOX_WIDTH + KEY2_BOX_WIDTH + KEY3_BOX_WIDTH);
        // 内容列不能硬撑到盖住 Q/E/R；窄窗口时由 barW() 压缩进度条。
        return Math.max(0, w);
    }

    // ========================================================================
    // ★★ 收缩阶梯（2026-09-21 用户要求）
    //   用户原话：「技能贴片上的属性空间不够的时候是可以牺牲的」+「贴片上的各种
    //   间距都没有牺牲，反而在空间不够的时候会压缩技能条直到和快捷键的区域重叠」
    //
    //   缺口（实测）：窗口 719×534、guiScale 3、offset=-50 → 设计空间仅 478
    //   → contentW=369，而完整布局需要 408（= R_BAR_X 358 + 条 40 + 留白 10）
    //   → 旧 barW() 的 Math.max(R_BAR_MIN_W, ...) 把进度条兜到 40 宽，
    //     右端冲到 x0+398 > contentW 369 → 压进 Q 键格 29px。
    //
    //   阶梯顺序（用户选定）：① 列间距 → ② 属性区 → ③ 进度条
    //   名称列【不动】（用户明确不牺牲）。任何阶段都不允许压进 Q/E/R。
    // ========================================================================

    /** 阶梯缓存的 contentW（值不变就不重算） */
    private int ladderForContentW = -1;
    /** 阶梯结果：属性区可用宽（ATTR_AREA_W → 0） */
    private int ladderAttrW = ATTR_AREA_W;
    /** 阶梯结果：属性区↔消耗列 间距（ATTR_RIGHT_GAP → 0） */
    private int ladderGapAttr = ATTR_RIGHT_GAP;
    /** 阶梯结果：列间距（COL_GAP → 0） */
    private int ladderGapCol = COL_GAP;

    /**
     * 按当前内容区宽度重算收缩阶梯。
     *
     * <p>固定部分（不参与收缩）= 名称列 {@code R_NAME_X+R_NAME_W}=140 + 消耗列 54 + 等级列 52
     * = <b>246</b>；再留出进度条最小宽 {@link #R_BAR_MIN_W}=40 与右留白
     * {@link #BAR_RIGHT_PAD}=10 → 可自由分配的余额 {@code flex = contentW - 296}。
     *
     * <p>余额上限 {@code maxFlex = ATTR_AREA_W + ATTR_RIGHT_GAP + 2*COL_GAP} = 112
     * （属性 67 + 间距 45）。按余额依次牺牲：
     * <ol>
     *   <li>{@code flex >= 112} → 完整布局，什么都不牺牲</li>
     *   <li>{@code 67 <= flex < 112} → <b>只牺牲间距</b>（属性和名称都不动）</li>
     *   <li>{@code flex < 67} → 间距归零后，<b>牺牲属性区</b>（可一路缩到 0 = 不显示）</li>
     * </ol>
     */
    private void ensureLadder() {
        final int cw = contentW();
        if (cw == ladderForContentW) {
            return;
        }
        ladderForContentW = cw;
        final int flex = cw - (R_NAME_X + R_NAME_W + R_COST_W + R_LV_W) - (R_BAR_MIN_W + BAR_RIGHT_PAD);
        final int gapMax = ATTR_RIGHT_GAP + COL_GAP * 2;          // 45
        final int maxFlex = ATTR_AREA_W + gapMax;                 // 112
        if (flex >= maxFlex) {
            // ① 空间充足：完整布局
            ladderAttrW = ATTR_AREA_W;
            ladderGapAttr = ATTR_RIGHT_GAP;
            ladderGapCol = COL_GAP;
        } else if (flex >= ATTR_AREA_W) {
            // ② 先牺牲【列间距】（按比例压，属性区仍完整）
            final int gapTotal = flex - ATTR_AREA_W;
            ladderAttrW = ATTR_AREA_W;
            ladderGapAttr = ATTR_RIGHT_GAP * gapTotal / gapMax;
            ladderGapCol = COL_GAP * gapTotal / gapMax;
        } else {
            // ③ 间距已归零 → 再牺牲【属性区】（可缩到 0）
            ladderAttrW = Math.max(0, flex);
            ladderGapAttr = 0;
            ladderGapCol = 0;
        }
    }

    /** 当前属性区可用宽（收缩阶梯） */
    private int attrAreaW() {
        ensureLadder();
        return ladderAttrW;
    }

    /** 属性区↔消耗列 当前间距（收缩阶梯） */
    private int gapAttr() {
        ensureLadder();
        return ladderGapAttr;
    }

    /** 列间距当前值（收缩阶梯） */
    private int gapCol() {
        ensureLadder();
        return ladderGapCol;
    }

    /** 消耗列左边界（随阶梯左移；名称列不动） */
    private int costX() {
        ensureLadder();
        return R_NAME_X + R_NAME_W + ladderAttrW + ladderGapAttr;
    }

    /**
     * 等级进度条宽度（弹性 + 硬性不越界）。
     *
     * <p>★ 2026-09-21 用户要求：「硬性不得越过 Q/E/R」—— 旧实现用
     * {@code Math.max(R_BAR_MIN_W, ...)} 兜底，空间不足时条子会冲出内容区、
     * 糊在 Q 键格上（实测越界 29px）。现在改为<b>以内容区右边界为硬上限</b>：
     * 宁可条子变短（极端情况到 0 = 不可见），也绝不压进快捷键格。
     * <p>正常情况阶梯已把空间腾出来，条子不会低于 {@link #R_BAR_MIN_W}。
     */
    private int barW() {
        final int avail = contentW() - barX();
        if (avail <= 0) {
            return 0;   // ★ 硬性：绝不越界
        }
        final int pad = Math.min(BAR_RIGHT_PAD, Math.max(0, avail - 1));
        return Math.max(0, Math.min(R_BAR_MAX_W, avail - pad));
    }

    /** 等级文字左边界（★ 2026-09-20：固定在消耗数字之后；随阶梯左移） */
    private int lvX() {
        ensureLadder();
        return costX() + R_COST_W + ladderGapCol;
    }

    /** 进度条起点（随阶梯左移） */
    private int barX() {
        ensureLadder();
        return lvX() + R_LV_W + ladderGapCol;
    }

    /** 行内「属性加成」右对齐的右边界（局部坐标，与 x0 同基准）：紧邻消耗列左侧 */
    private int attrRight() {
        return costX() - gapAttr();
    }

    /** 内容区右边界 */
    private int contentRight() {
        return contentW();
    }

    /** 行区域上边界（屏幕坐标） */
    private int listTop() {
        return listFrameTop() + FRAME_LINE + ROW_PAD_TOP;
    }

    /** 列表视区高度（屏幕坐标） */
    private int listViewH() {
        return Math.max(0, listBottom() - listTop());
    }

    /** 列表视区底部（分区框内底） */
    private int listBottom() {
        return listFrameBottom() - FRAME_LINE;
    }

    boolean isMouseOverSkillList(double mouseX, double mouseY) {
        return mouseX >= frameLeft() + FRAME_LINE && mouseX < frameRight() - FRAME_LINE
                && mouseY >= listTop() && mouseY < listBottom()
                && !isOverUI(mouseX, mouseY);
    }

    private boolean isMouseOverCategories(double mouseX, double mouseY) {
        return mouseX >= frameLeft() + FRAME_LINE && mouseX < frameRight() - FRAME_LINE
                && mouseY >= catFrameTop() + FRAME_LINE && mouseY < catFrameBottom() - FRAME_LINE
                && !isOverUI(mouseX, mouseY);
    }

    /**
     * 屏幕逻辑尺寸（Screen 尺寸，<b>未</b>做「设计空间」换算）。
     *
     * <p>⚠️ 本类的鼠标/键盘回调都在 {@link #runInLayoutSpace} 内执行，
     * 期间 {@code width/height} 已被除以 r（变成设计空间尺寸）。
     * 但 {@link #openSubScreen} 需要的是<b>换算前</b>的屏幕尺寸 —— 两者不能混。
     */
    private int screenLogicalW() {
        final var win = this.minecraft != null ? this.minecraft.getWindow() : null;
        if (win != null) {
            return win.getGuiScaledWidth();
        }
        return inLayoutSpace ? Math.max(1, (int) Math.round(width * uiScale())) : width;
    }

    /** 屏幕逻辑高（见 {@link #screenLogicalW()}） */
    private int screenLogicalH() {
        final var win = this.minecraft != null ? this.minecraft.getWindow() : null;
        if (win != null) {
            return win.getGuiScaledHeight();
        }
        return inLayoutSpace ? Math.max(1, (int) Math.round(height * uiScale())) : height;
    }

    /** 打开子界面（同类型已打开则关闭切换） */
    void openSubScreen(SkillSubScreen sub) {
        if (activeSubScreen != null) {
            activeSubScreen.onClose();
        }
        activeSubScreen = sub;
        if (activeSubScreen != null) {
            // ★ 2026-09-21：子界面按「设计空间」初始化（与其渲染坐标系一致，见 uiScale）
            //   例外：设置子界面脱离缩放 → 用屏幕逻辑尺寸
            //
            // ★★ 2026-09-22 修正（用户反馈「可移动范围不对 / 只允许 25% 出屏」）：
            //   本方法由 mouseClickedInner 调用，而那里【已在设计空间内】——
            //   width/height 早已被除以 r。原代码再除一次 r 属【双重换算】：
            //     · 非设置子界面：screenW = (W/r)/r = W/r² → 比真实设计空间大 1/r 倍
            //     · 设置子界面：screenW = W/r（设计宽）→ 它 1:1 渲染，本应为 W
            //   两者都偏大 1/r 倍 → r<1（窗口模式 + 自适应）时可把面板拖出屏幕远超 25%
            //   （r=0.833 实测可拖到 ~83% 出屏，r=0.667 时能整块拖出屏幕）。
            //   改为显式取「屏幕逻辑尺寸」，与调用点是否在 layout space 无关。
            final double r = uiScale();
            if (sub instanceof SettingsSubScreen) {
                // 脱离缩放、1:1 渲染 → 用屏幕逻辑尺寸
                sub.init(screenLogicalW(), screenLogicalH());
            } else {
                // 按设计空间初始化（与其渲染坐标系一致）
                sub.init(Math.max(1, (int) Math.round(screenLogicalW() / r)),
                        Math.max(1, (int) Math.round(screenLogicalH() / r)));
            }
        }
    }

    /** 关闭当前子界面 */
    void closeSubScreen() {
        if (activeSubScreen != null) {
            activeSubScreen.onClose();
            activeSubScreen = null;
        }
    }

    /** 当前子界面（供渲染/输入转发） */
    SkillSubScreen activeSubScreen() {
        return activeSubScreen;
    }

    /** 是否可设置等级/目标循环快捷键（2026-08-13 优化）：
     *  ⚠️ 2026-09-07 规范 v1.0 收编：直接读 Skills 集中注册表 hasSub2
     *  （子2 = 有模式循环(敌我/天气/搬运) 或 可调等级>1），不再本地散落特判。 */
    private boolean isLevelBindable(String skillId) {
        return Skills.hasSub2(skillId);
    }

    /** 指定光环技能的目标模式文字（0 敌对 / 1 友好 / 2 所有） */
    private String modeTextOf(String skillId) {
        // ★ 2026-09-30：战利品大爆发是 4 态模式，语义与光环敌我完全不同 → 单独分派
        if (Skills.LOOT_BOMB.equals(skillId)) {
            return lootModeTextOf(skillId);
        }
        return switch (auraTargetModes.getOrDefault(skillId, 0)) {
            case 1 -> t("mode_friendly");
            case 2 -> t("mode_all");
            default -> t("mode_hostile");
        };
    }

    /** 战利品大爆发模式 → 语言 key（★ 2026-09-30；静态，贴片属性区与 tooltip 共用） */
    private static String lootModeKey(int mode) {
        return switch (mode) {
            case 1 -> "loot_mode_stackable";
            case 2 -> "loot_mode_unstackable";
            case 3 -> "loot_mode_blacklist";
            default -> "loot_mode_all";
        };
    }

    /** 战利品大爆发模式文字（★ 2026-09-30：0 全 / 1 仅堆叠 / 2 仅不堆叠 / 3 黑名单） */
    private String lootModeTextOf(String skillId) {
        return t(lootModeKey(auraTargetModes.getOrDefault(skillId, 0)));
    }

    /** 子2 能力类型（2026-09-07 规范 v1.0：第二框配色/文案分派）：0=等级循环 1=敌我目标 2=天气 3=搬运 */
    private int modeKindOf(String skillId) {
        if (Skills.isAuraTargetSkill(skillId)) {
            return 1;
        }
        if (Skills.AURA_WEATHER.equals(skillId)) {
            return 2;
        }
        if (Skills.isContainerHaul(skillId)) {
            return 3;
        }
        return 0;
    }

    /** 子枫的搬运术模式文字（2026-09-07：0 自动 / 1 手动） */
    private String haulModeTextOf(String skillId) {
        return auraTargetModes.getOrDefault(skillId, 0) == 1
                ? t("haul_mode_manual")
                : t("haul_mode_auto");
    }

    /** 晴空环天气模式文字（0 晴 / 1 雨 / 2 雷暴；2026-08-28：优先服务器全局值，未同步回退本地缓存） */
    private String weatherTextOf() {
        int global = org.zifeng.skilltree.client.ClientGlobalState.getWeatherMode();
        if (global >= 0) {
            return org.zifeng.skilltree.client.ClientGlobalState.getWeatherModeText();
        }
        return switch (org.zifeng.skilltree.client.ModKeyBindingEvents.getWeatherModeClient()) {
            case 1 -> t("weather_rain");
            case 2 -> t("weather_thunder");
            default -> t("weather_sunny");
        };
    }

    public SkillTreeScreen(int skillPoints, Map<String, Integer> learnedSkills, Map<String, Boolean> toggles,
                           Map<String, Integer> activeLevels, boolean auraEnabled, Map<String, Integer> auraTargetModes) {
        super(Component.translatable("ui.zifeng_s_custom_skill_tree.title"));
        // 客户端配置先加载；滚动位置等到 init() 有真实的列表视区后再恢复并钳制。
        org.zifeng.skilltree.client.SkillKeyBinds.load();
        int[] viewState = org.zifeng.skilltree.client.SkillKeyBinds.getSkillTreeViewState();
        selectedCategory = viewState[0];
        pendingScrollY = viewState[1];
        pendingCatScrollX = viewState[2];
        lastNormalScrollY = pendingScrollY;
        pendingViewStateRestore = true;
        // 每次开界面重建一次本地化缓存（语言可能中途切换过；建完就整局复用）
        LANG_CACHE.clear();
        KEY_NAME_CACHE.clear();
        clearSearchCaches();
        updateData(skillPoints, learnedSkills, toggles, activeLevels, auraEnabled, auraTargetModes);
    }

    public void updateData(double skillPoints, Map<String, Integer> learnedSkills, Map<String, Boolean> toggles,
                           Map<String, Integer> activeLevels, boolean auraEnabled, Map<String, Integer> auraTargetModes) {
        // 服务端会定期发送完整快照。快照没有变化时不重建按钮、不递增视觉版本，
        // 避免每次同步都让所有可见行重新计算属性、前置条件、文本和图标。
        Map<String, Integer> nextAuraTargetModes = auraTargetModes != null
                ? auraTargetModes : java.util.Collections.emptyMap();
        boolean changed = Double.compare(this.skillPoints, skillPoints) != 0
                || !this.learnedSkills.equals(learnedSkills)
                || !this.toggles.equals(toggles)
                || !this.activeLevels.equals(activeLevels)
                || this.auraEnabled != auraEnabled
                || !this.auraTargetModes.equals(nextAuraTargetModes);
        if (!changed) {
            return;
        }
        this.skillPoints = skillPoints;
        this.learnedSkills.clear();
        this.learnedSkills.putAll(learnedSkills);
        this.toggles.clear();
        this.toggles.putAll(toggles);
        this.activeLevels.clear();
        this.activeLevels.putAll(activeLevels);
        this.auraEnabled = auraEnabled;
        this.auraTargetModes.clear();
        this.auraTargetModes.putAll(nextAuraTargetModes);
        cachedRows = null;
        lastRowsTick = -1;
        textVersion++; // 2026-09-15 性能优化：数据变化 → 按钮文本缓存全部失效重建
        rebuildButtons();
    }

    /** 每 40 tick 向服务端请求一次技能数据（降低与点击乐观更新的竞态） */
    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        if (tickCounter >= 40) {
            tickCounter = 0;
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.OpenSkillTreeC2SPacket(true));
        }
        // ⚠️ 这里刻意<b>不</b>转发 tick 给 searchBox（用户 2026-09-20 确认：不需要光标闪烁）。
        //   原因：EditBox 的光标闪烁在 1.20.1 靠内部计数器 frame（仅由 tick() 自增）驱动，
        //   而 1.21.1 的 AbstractWidget 已移除 tick() —— 两版本没有公共的 tick 入口，
        //   写版本分支只为换一个光标闪烁并不值。
        //   影响：1.20.1 的光标会保持常亮而不闪，可见性不受影响。
    }

    private int tickCounter = 0;

    // ========================================================================
    // 2026-09-15 界面性能优化：帧内共享缓存 + 按钮文本缓存 + 视口剔除
    //   掉帧根因（按开销排序）：
    //     ① headerBounds() 被每个按钮的 isIconUnderUI() 重复调用（≈120 次/帧）
    //        → 每帧上千次语言查询 + 数百次字体测量
    //     ② 按钮三行文本的「逐字符裁剪」while 循环是 O(n²)（3 处 × 120 按钮）
    //     ③ 按钮三行文本每帧重建（≈10 次语言查询 ×120 按钮 → 纯 GC 压力）
    //     ④ 屏幕外的整套按钮（背景/边框/图标/文字/按键框）照常绘制
    //   原则：只缓存「输入不变就不会变」的东西，显示结果与优化前逐像素一致。
    // ========================================================================

    /** 帧序号（render 开头 +1；帧内只算一次的缓存据此判断失效） */
    private int frameStamp = 0;
    /** 顶部信息区缓存帧号（-1 = 未构建） */
    private int headerCacheFrame = -1;
    /**
     * 顶部信息区文本（2026-09-19 起：<b>只有技能点一行</b>）。
     * <p>原四行（标题/光环状态/快捷键提示/未绑定警告）已按用户要求删除——
     * 它们占掉顶部大片空间且信息密度低（多为常驻不变的说明文字）。
     */
    private String cachedHeaderText = "";
    private int cachedHeaderMaxWidth = 0;
    private int[] cachedHeaderBounds = null;

    /** 顶部技能点行：距边框内侧的内边距（2026-09-19） */
    private static final int HEADER_PAD = 6;
    /** 按钮文本缓存版本号：技能数据变化时 +1（updateData） */
    private int textVersion = 0;
    /** 按钮三行文本缓存（技能ID → 文本） */
    // 2026-09-19：已由 rowVisualCache 统一接管（文本也属于「行静态视觉」），此处不再单独缓存

    // ========================================================================
    // 2026-09-19 列表式改版【性能优化】——用户反馈：「相关显示不要实时渲染，太卡、太掉帧，按原版按钮那样做」
    //
    //   旧实现（改版后）：renderSkillButton 每帧、对每一可见行重算：
    //     颜色分组 switch / Skills.getType / canLearn（内部走 getPrerequisites + missingModName + 代价阶梯）
    //     prereqMetFor（遍历前置表）/ 进度条除算 / Skills.getMaxPoints / Skills.getIcon + new ItemStack
    //     nextCostDisplay（String.format + 字体测宽）/ 3 个按键槽的 getDisplayName().getString()
    //   → 这些东西【只跟技能数据有关，跟鼠标无关】，却被算了 60 次/秒 × 可见行数，纯属浪费。
    //
    //   现在：把它们整佰算成一条 {@link RowVisual} 缓存（「数据变才重算」），
    //   每帧渲染只剩：5 次 fill（行底/边框）+ 3 次 drawString + 1 次图标 blit + 3 个按键槽，
    //   与「原版按钮」的开销同一量级（原版按钮就只是 fill + drawString，不重算逻辑）。
    // ========================================================================

    /** 技能行静态视觉缓存（技能ID → 行视觉）；数据变（rebuildButtons）时失效，或指纹对不上时重建 */
    private final Map<String, RowVisual> rowVisualCache = new HashMap<>();

    /**
     * 按键显示名缓存（InputConstants.Key → 显示文本）。
     * <p>原实现每帧对每行的 3 个槽位都调 {@code getDisplayName().getString()}（走语言表 + 新建 Component）——
     * 可见 20 行就是 60 次/帧。按键显示名是静态文本，缓存后只剩查表。
     */
    private static final Map<com.mojang.blaze3d.platform.InputConstants.Key, String> KEY_NAME_CACHE = new HashMap<>();

    /** 取按键显示名（带缓存） */
    private static String keyName(com.mojang.blaze3d.platform.InputConstants.Key key) {
        if (key == null) {
            return null;
        }
        String cached = KEY_NAME_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String name = key.getDisplayName().getString();
        KEY_NAME_CACHE.put(key, name);
        return name;
    }

    /** 行视觉指纹：这些输入不变 → 行视觉不会变（不需要重建） */
    private static int rowFingerprint(int version, int points, int activeLevel, boolean enabled, int toolMode, int pointsX10) {
        int h = version;
        h = h * 31 + points;
        h = h * 31 + activeLevel;
        h = h * 31 + (enabled ? 1 : 0);
        h = h * 31 + toolMode;
        h = h * 31 + pointsX10;
        return h;
    }
    /** 技能树可视区域（面板局部坐标，含余量）——屏幕外按钮整块跳过渲染 */
    private double viewLeft;
    private double viewTop;
    private double viewRight;
    private double viewBottom;

    /**
     * 按钮三行文本（名称 / 等级 / 消耗）——纯计算结果，仅由 {@link #buildRowVisual} 调用。
     *
     * <p>⚠️ 2026-09-20 注意：第 3 个字段 {@code cost} <b>目前从未被渲染使用</b> ——
     * {@code buildRowVisual} 只读了 {@code name()} 与 {@code effect()}，
     * 行内消耗列用的是 {@link #nextCostDisplay}（见 {@code renderSkillButton} 的「② 下一级消耗」）。
     * 因此在 {@code buildButtonTexts} 里为它拼的那一大堆字符串（含时间馈赠的「需1小时」）
     * <b>都是死代码、玩家看不到</b>。保留字段是为了不做大改动；改动消耗显示请改
     * {@link #nextCostDisplay}，不要改这里。
     */
    private record ButtonTexts(String name, String effect, String cost) {
    }

    /**
     * 技能行「静态视觉」缓存条目（2026-09-19 性能优化）。
     *
     * <p>只装「跟技能数据有关、跟鼠标无关」的东西：类型/开关/已学/可学/前置、行底色与描边的常态值与悬停值、
     * 三处文字颜色、进度条几何（填充宽/刻度位置/颜色）、两个槽位的可用性、图标（ItemStack 已缓存）。
     * <p>渲染时只需根据 {@code hovered} 二选一取底色/描边，其余直接画 —— 不再每帧跑判定逻辑。
     */
    private record RowVisual(int fp, Skills.SkillType type, boolean isTool, boolean enabled, boolean learned,
                             int bg, int bgHover, int border, int borderHover,
                             int nameColor, int costColor, int lvColor,
                             float barFillR, float barTickR, int barColor,
                             boolean slot2Usable, boolean slot3Usable,
                             net.minecraft.world.item.ItemStack iconStack,
                             net.minecraft.resources.ResourceLocation iconTex,
                             String name, int nameW, String effect, int effectW, String costText, int costW,
                             String attrText, int attrW, int markW, int accent, boolean attrWarn) {
    }

    /** 颜色向白色混合（lighten）；t=0 原色，t=1 纯白 */
    private static int lighten(int color, float t) {
        int a = color >>> 24;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * t));
        g = Math.min(255, (int) (g + (255 - g) * t));
        b = Math.min(255, (int) (b + (255 - b) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 颜色向黑色混合（darken）；t=0 原色，t=1 纯黑（保留原 alpha） */
    private static int darken(int color, float t) {
        int a = color >>> 24;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = (int) (r * (1 - t));
        g = (int) (g * (1 - t));
        b = (int) (b * (1 - t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 帧开始：推进帧序号（帧内缓存据此判断是否需要重新计算） */
    private void beginFrame() {
        frameStamp++;
    }

    /**
     * 重建顶部信息区缓存（2026-09-19 起：<b>只剩【技能点】一行</b>）。
     *
     * <p>⚠️ 2026-09-19 策划更改（用户）：删除原先的 4 行内容——
     * 「子枫的百宝箱」（标题）/ 光环开关状态 / 目标模式 / 快捷键提示行 / 未绑定警告行，
     * <b>只保留技能点数量</b>，且<b>固定靠左上角</b>（不随列表滚动）。
     * 原 4 行占掉顶部大片空间、信息密度低，是本次要砍掉的「臃肿」部分。
     *
     * <p>性能：每帧最多调用一次（frameStamp 判定），且语言查询从 5 次降为 1 次。
     */
    private void refreshHeaderCache() {
        if (headerCacheFrame == frameStamp) {
            return;
        }
        headerCacheFrame = frameStamp;
        cachedHeaderText = t("status_skill_point") + String.format("%.1f", Math.max(0, skillPoints));
        cachedHeaderMaxWidth = lw(cachedHeaderText);
        // ★ 2026-09-19：标题行现在是「分区框 1」内的一行普通文字（去掉原先那圈独立圆角小框）
        int x = frameLeft() + FRAME_LINE + FRAME_PAD_X + 2;
        int y = titleFrameTop() + FRAME_LINE + (titleFrameH() - FRAME_LINE * 2 - font.lineHeight) / 2;
        cachedHeaderBounds = new int[]{x - 2, titleFrameTop() + 1, x + cachedHeaderMaxWidth + 2, titleFrameBottom() - 1};
    }

    /** 计算技能树可视区域（面板局部坐标，留 8px 余量）——用于剔除屏幕外的技能行。
     *  <p>2026-09-19：原点 = 贴片左上角（固定），无缩放；竖向叠加滚动偏移。
     *  竖向同时被【技能行分区框】限制 —— 滚出框的行不绘制。 */
    private void updateViewport() {
        double ox = rowLeft();
        double oy = listTop() - scrollY;
        viewLeft = (0 - ox) - 8;
        viewTop = scrollY - 8;
        viewRight = (width - ox) + 8;
        viewBottom = scrollY + listViewH() + 8;
    }

    /** 贴片是否与可视区域相交；不相交 → 整块跳过（绘制结果与原本被裁掉一致） */
    private boolean isButtonVisible(SkillButton button) {
        int right = button.x() + rowW();
        return button.x() <= viewRight && right >= viewLeft
                && button.y() <= viewBottom && button.y() + BUTTON_HEIGHT >= viewTop;
    }

    /**
     * 布局空间里的文字宽度（★ 2026-09-22）。
     *
     * <p>⚠️ 本界面的文字用 {@link #drawCrispString}/{@link #drawCrispScaledString}
     * 在<b>屏幕像素</b>上绘制（不跟外层 {@code pose.scale(r)} 走，为的是字号清晰），
     * 而容器/背景走 fill <b>会</b>乘 r —— 于是「用 {@code font.width} 量宽、又拿去排
     * 设计空间几何」的地方全部差了 r 倍：{@code r < 1}（窗口模式 + 自适应）时
     * 文字比容器<b>宽 1/r 倍</b> → 溢出。
     *
     * <p>用户报的「悬浮描述会超出悬浮框」就是这一条：描述越长溢出越多，
     * 所以只有长描述的技能（Goety 流派精通、搬运术、选区挖掘…）看得出。
     *
     * <p>换算回设计空间即 {@code font.width × scale ÷ r}，容器从此刚好装下文字。
     * 原来只有 {@code r == 1}（全屏、或非自适应且 offset=0）时两者才恰好相等。
     */
    private int lw(String text, float scale) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        final double r = uiScale();
        final double w = font.width(text) * scale;
        return r <= 0 ? (int) Math.ceil(w) : (int) Math.ceil(w / r);
    }

    /** 布局空间里的文字宽度（相对字号 1.0，见 {@link #lw(String, float)}） */
    private int lw(String text) {
        return lw(text, 1.0F);
    }

    /** tooltip 行间距（设计空间；屏幕实际间距由 {@link #lh(float)} 换算） */
    private static final int TOOLTIP_LINE_GAP = 2;

    /**
     * 布局空间里的行高（字号 + 行间距）——与 {@link #lw(String, float)} 同理。
     * <p>不换算的话，屏幕行推进只有 {@code (lineHeight+gap) × s × r} 像素，
     * 而文字实际高 {@code lineHeight × s} —— {@code r < 0.818} 时行与行开始重叠。
     */
    private int lh(float scale) {
        final double r = uiScale();
        final double h = (font.lineHeight + TOOLTIP_LINE_GAP) * scale;
        return r <= 0 ? (int) Math.ceil(h) : (int) Math.ceil(h / r);
    }

    // ══════════ ★ 2026-09-22 方案 B：「容器 + 文字」整体绘制单元 ══════════
    //
    // 【为什么需要】本界面文字走 drawCrispString（屏幕像素绘制，不乘 r），
    //   而容器（fill/fillRound）走设计空间坐标（会乘 r）—— 两者不在同一空间。
    //   所以任何「用 font.width() 定容器宽」的地方都必须先 ÷r（即 lw()），
    //   漏一处就文字溢出容器。已两次踩坑：tooltip 描述溢出、右下角三按钮文字溢出。
    //
    // 【本单元解决什么】把「算宽 → 画框 → 画字 → 命中判定」绑在同一个 w 上：
    //   宽度只在 autoBoxW() 里算一次（内部走 lw()），
    //   框、字、命中全部读 AutoBox.w() —— 结构上不可能出现「框字不同宽」。
    //
    // 【新增界面照抄这个配方】
    //   AutoBox box = new AutoBox(x, y, autoBoxW(PAD_X, MIN_W, text), H, text);
    //   drawAutoBoxCapsule(g, box, bg, border, color);   // 或 drawAutoBoxRect
    //   if (box.contains(mx, my)) { ... }                // 命中与视觉必定一致
    //   ★ 按钮代码里不该再出现 font.width() —— 宽度一律走 autoBoxW()。
    //
    // ⚠️ 只能用于【设计空间】（本 Screen 的缩放 pose 内）。
    //   脱离缩放的部分（如 SettingsSubScreen，它在 popPose 后 1:1 渲染）
    //   要直接用 font.width() —— 那些地方再套 lw() 会反除一次 r。
    //
    // ★ 新增按钮/胶囊类界面一律用它，不要再自己调 font.width() 排几何。

    /** 设计空间里的矩形 + 文字：宽度由文字推导，框 / 字 / 命中判定共用 */
    private record AutoBox(int x, int y, int w, int h, String text) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int right() {
            return x + w;
        }

        int bottom() {
            return y + h;
        }
    }

    /** 并排多个单元共用的宽度（取最宽文字）——单个按钮直接传一个文本即可 */
    private int autoBoxW(int padX, int minW, String... texts) {
        int widest = 0;
        for (String s : texts) {
            widest = Math.max(widest, lw(s));
        }
        return Math.max(minW, widest + padX * 2);
    }

    /**
     * 画直角方框按钮：底色 + 1px 四边框 + 水平居中文字。
     *
     * @param textDy 文字相对 y 的纵向偏移（各处的观感偏移不同，显式传入以免偷偷改版式）
     */
    private void drawAutoBoxRect(GuiGraphics g, AutoBox b, int fill, int border,
                                 int textColor, int textDy) {
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        g.fill(overlay, b.x(), b.y(), b.right(), b.bottom(), fill);
        g.fill(overlay, b.x(), b.y(), b.right(), b.y() + 1, border);
        g.fill(overlay, b.x(), b.bottom() - 1, b.right(), b.bottom(), border);
        g.fill(overlay, b.x(), b.y(), b.x() + 1, b.bottom(), border);
        g.fill(overlay, b.right() - 1, b.y(), b.right(), b.bottom(), border);
        drawCrispString(g, b.text(), b.x() + b.w() / 2, b.y() + textDy, textColor, true);
    }

    /** 画圆角胶囊按钮：底色 + 描边 + 垂直/水平居中文字（半径 = 高/2） */
    private void drawAutoBoxCapsule(GuiGraphics g, AutoBox b, int fill, int border, int textColor) {
        final int r = b.h() / 2;
        fillRound(g, b.x(), b.y(), b.right(), b.bottom(), r, fill);
        strokeRound(g, b.x(), b.y(), b.right(), b.bottom(), r, border);
        drawCrispString(g, b.text(), b.x() + b.w() / 2,
                b.y() + (b.h() - font.lineHeight) / 2, textColor, true);
    }

    /**
     * 按像素宽度裁剪文本（超出部分截掉）。
     * ⚠️ 原实现是「逐字符 + 每次重新测宽」的 while 循环 → O(n²)（每帧 3 处 × 120 按钮，
     *    最坏上万次字形查询）；这里改为二分查找 O(n log n)，结果完全相同（最长可容纳前缀）。
     */
    private String clipToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || lw(text) <= maxWidth) {
            return text;
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lw(text.substring(0, mid)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo);
    }

    /**
     * 十个技能类别（2026-09-19 列表式改版）。
     *
     * <p>改版前是「10 列并排铺开约 2700px，必须缩放/拖动才能看全、且列高极不均衡（最长的特殊被动 24 项
     * vs 最短的寰宇法则 3 项）」；现在是「顶部类别按钮行 + 单列技能列表」——
     * 天然有阅读顺序、无需缩放拖动，一屏能完整看完一个类别。
     *
     * <p>顺序 = 原列顺序（0 魔法 / 1 基础 / 2 增幅 / 3 终极 / 4 被动 / 5 光环 / 6 寰宇 / 7 机械 / 8 馈赠 / 9 工具）。
     */
    private static final String[] CATEGORY_TITLE_KEYS = {"col_magic", "col_base", "col_amplify", "col_ultimate",
            "col_special", "col_aura", "col_global", "col_machine", "col_gift", "col_tool"};
    /** 十个类别的强调色（沿用改版前各列标题配色，保持视觉延续） */
    private static final int[] CATEGORY_COLORS = {0xFF55FFAA, 0xFF87CEEB, 0xFFFFAA55, 0xFFFF5555, 0xFFD7A55A,
            0xFFAA55FF, 0xFF66CCFF, 0xFFD7D7D7, 0xFFE0B6C8, 0xFFC8A87C};
    /** 类别总数 */
    private static final int CATEGORY_COUNT = 10;

    /** 取第 i 个类别的技能列表 */
    private static List<String> categorySkills(int i) {
        return switch (i) {
            case 0 -> Skills.MAGIC_SKILLS;
            case 1 -> Skills.BASE_SKILLS;
            case 2 -> Skills.AMPLIFY_SKILLS;
            case 3 -> Skills.ULTIMATE_SKILLS;
            case 4 -> Skills.SPECIAL_SKILLS;
            case 5 -> Skills.AURA_SKILLS;
            case 6 -> Skills.GLOBAL_SKILLS;
            case 7 -> Skills.MACHINE_SKILLS;
            case 8 -> Skills.GIFT_SKILLS;
            default -> Skills.TOOL_SKILLS;
        };
    }

    /**
     * 重建技能行 —— <b>只建当前选中类别</b>，单列纵向排列。
     *
     * <p>⚠️ 行坐标在「面板局部坐标系」（与 {@link #toPanelX}/{@link #toPanelY} 同一坐标系，
     * 原点 = 列表左上角），因此按钮 x 恒为 0、y 逐行递增 —— 所有命中判定代码无需改动。
     */
    private void rebuildButtons() {
        // ★★ 2026-09-21 重要修复：本方法【自带】设计空间保证。
        //   原因：它的调用点很多（init/resize/render/模糊搜索/点击类别/加点…），
        //   而几何全部从 width/height 推导。之前只有部分调用点在外面包了 runInLayoutSpace，
        //   漏掉的那些（典型：搜索框打字 → responder → onSearchBoxChanged）会用【屏幕尺寸】
        //   算布局 → 类别行 availW 变小 → 判定“放不下”而左靠齐；
        //   鼠标点击路径又是设计空间 → 居中。两者交替 ⇒ **类别按钮反复跳动**。
        //   现在统一在这里保证，调用方无需再关心（runInLayoutSpace 有防重入）。
        runInLayoutSpace(this::rebuildButtonsInner);
    }

    /** {@link #rebuildButtons} 的实际实现（保证在「设计空间」内执行） */
    private void rebuildButtonsInner() {
        buttons.clear();
        // 不在这里清空行视觉缓存：类别切换、搜索、窗口尺寸变化都会重排按钮，
        // 但技能数据本身未必变化。行缓存由 rowFingerprint 按数据版本懒更新，
        // 可避免无意义地重新计算整类技能。
        int y = 0;
        // ★ 2026-09-20：搜索中 → 跨类别结果；否则 → 当前选中类别
        for (String skill : currentSkillSource()) {
            buttons.add(new SkillButton(skill, 0, y));
            y += BUTTON_HEIGHT + VERTICAL_SPACING;
        }
        // 内容总高 → 滚动上限（视口高随窗口尺寸变化，故在此重算）
        int contentH = buttons.isEmpty() ? 0 : y - VERTICAL_SPACING;
        scrollMax = Math.max(0, contentH - listViewH());
        if (scrollY > scrollMax) {
            scrollY = scrollMax;
        }
        rebuildCategoryRow();
    }

    /**
     * 类别按钮行的可用宽度 = 界面内宽（左边框到右边框之间）。
     * <p>2026-09-19：键位小标题已按用户要求去掉，类别行独占一条横带，可以铺满整宽。
     */
    private int catViewW() {
        return frameInnerW();
    }

    /**
     * 重建类别按钮行的几何（屏幕坐标）。
     *
     * <p>★ 2026-09-19 用户要求：类别行<b>水平居中</b>——整组按钮在界面内宽里居中；
     * 如果宽到放不下（中英文差异 + 小窗口），才退化为「从左边框起、可横向滚动」。
     * <p>按钮宽度随文案长短自适应。
     */
    private void rebuildCategoryRow() {
        // 类别栏也必须和技能列表使用同一套设计坐标。搜索框 responder、滚轮和类别点击
        // 都可能直接调用这里，不能假设调用者已经进入 runInLayoutSpace。
        if (!inLayoutSpace) {
            runInLayoutSpace(this::rebuildCategoryRow);
            return;
        }
        if (font == null) {
            return; // 字体未就绪（构造函数阶段）→ 等 init() 后再测宽
        }
        int total = 0;
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            String title = catTitles[i] != null ? catTitles[i]
                    : Component.translatable("ui.zifeng_s_custom_skill_tree." + CATEGORY_TITLE_KEYS[i]).getString();
            catTitles[i] = title; // 缓存（只在此处解析，每帧不再查语言表）
            catButtonW[i] = autoBoxW(CAT_BTN_PAD, 0, title);
            total += catButtonW[i] + CAT_BTN_GAP;
        }
        catContentW = Math.max(0, total - CAT_BTN_GAP);
        int availW = catViewW();
        // 类别栏只接受已经换算到设计空间的布局宽度。若调用发生在初始化中间阶段，
        // 保留上一份稳定几何，等正式 init/resize 再重建，避免 0/屏幕宽/设计宽交替导致跳动。
        if (availW <= 0 && catLayoutAvailW > 0) {
            return;
        }
        catLayoutAvailW = availW;
        int baseX;
        int overflow = Math.max(0, catContentW - availW);
        catScrollX = Math.max(0, Math.min(catScrollX, overflow));
        // 初始布局永远以整组按钮的中心为基准；只有横向滚轮才改变 catScrollX。
        // 这样语言切换、搜索重建或缩放重算时不会在“居中/左起”两种布局间跳动。
        baseX = frameLeft() + FRAME_LINE + (availW - catContentW) / 2 - catScrollX;
        int x = baseX;
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            catButtonX[i] = x;
            x += catButtonW[i] + CAT_BTN_GAP;
        }
    }

    /**
     * 兜底清理残留的「进度条拖拽」状态（2026-09-20 修复）。
     *
     * <p>⚠️ 问题：{@code draggingLevelSkillId} 原先只在 {@link #mouseReleased} 里清。
     * 若松开事件没送达（窗口失焦 / 弹出系统对话框 / 鼠标被强制释放等），它会一直非空 ——
     * 而 {@link #mouseScrolled} 开头就是「拖拽中 → 直接 return」，
     * 于是 <b>滚轮永久失效</b>（必须重开界面才能恢复）。
     *
     * <p>这里每帧对一次<b>物理</b>鼠标左键状态：拖拽中但左键已松开 = 状态残留 → 立刻清掉。
     *
     * <p>⚠️⚠️ <b>不能用 {@code minecraft.mouseHandler.isLeftPressed()}</b>！
     * 反编译实证（1.20.1 MouseHandler.onPress）：
     * <pre>
     *   if (!handled && minecraft.screen == null &amp;&amp; minecraft.getOverlay() == null) {
     *       if (button == 0) this.activeButtonLeft = pressed;   // ← 只有没打开界面时才更新
     * </pre>
     * 也就是<b>本界面打开期间该标志恒为初始值 false</b> →
     * 拿它做兜底会把状态每帧清掉，<b>拖拽直接失效</b>。
     * 所以必须绕开 MC 的缓存，直接问 GLFW 要物理按键状态。
     */
    private void releaseStaleLevelDrag() {
        if (draggingLevelSkillId == null) {
            return;
        }
        if (!isPhysicalLeftMouseDown()) {
            draggingLevelSkillId = null;
        }
    }

    /** 直接查 GLFW 的物理鼠标左键状态（不受「是否打开界面」影响） */
    private boolean isPhysicalLeftMouseDown() {
        if (minecraft == null || minecraft.getWindow() == null) {
            return false;
        }
        return org.lwjgl.glfw.GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /**
     * 屏幕初始化：字体/尺寸此时才就绪。
     * <p>⚠️ 2026-09-19：行高、列表视区、类别按钮宽度都依赖字体，所以在构造函数里算不准——
     * 这里用真实字体重算一遍（顺带清语言/按键名缓存，防中途切语言）。
     */
    @Override
    protected void init() {
        super.init();
        LANG_CACHE.clear();
        KEY_NAME_CACHE.clear();
        tooltipCacheSkillId = null;
        tooltipCacheLines = null;
        activeTooltipLines = null;
        clearSearchCaches();   // ★ 2026-09-20：搜索文本含翻译，语言重载后必须重建
        java.util.Arrays.fill(catTitles, null);
        // ★ 2026-09-20：创建搜索输入控件（需 font / 布局已就绪，所以放在 super.init() 之后）
        // ★ 2026-09-21：几何必须在「设计空间」里算（缩放后 width/height 已不是设计尺寸）
        runInLayoutSpace(() -> {
            ensureSearchBox();
            rebuildButtons();
            restorePersistedViewState();
        });
    }

    /**
     * 恢复客户端保存的类别和滚动位置。
     *
     * <p>构造函数阶段尚未有可靠的 {@code width/height/font}，列表上限可能暂时是 0，
     * 所以滚动值必须延迟到第一次真实布局完成后再钳制。
     */
    private void restorePersistedViewState() {
        if (!pendingViewStateRestore) {
            return;
        }
        scrollY = Math.max(0, Math.min(scrollMax, pendingScrollY));
        lastNormalScrollY = scrollY;
        int catScrollMax = Math.max(0, catContentW - catViewW());
        catScrollX = Math.max(0, Math.min(catScrollMax, pendingCatScrollX));
        pendingViewStateRestore = false;
        viewStateNeedsSave = false;
        rebuildCategoryRow();
    }

    /** 标记视图变更，实际写盘由事件结束或关闭时完成。 */
    private void markViewStateChanged() {
        viewStateNeedsSave = true;
    }

    /**
     * 保存当前视图状态。搜索期间只保存搜索前的正常列表滚动位置，
     * 避免搜索结果的临时滚动把下次进入时的位置覆盖成 0。
     */
    private void persistViewState() {
        if (!viewStateNeedsSave) {
            return;
        }
        if (!isSearching()) {
            lastNormalScrollY = scrollY;
        }
        org.zifeng.skilltree.client.SkillKeyBinds.setSkillTreeViewState(
                selectedCategory, lastNormalScrollY, catScrollX);
        viewStateNeedsSave = false;
    }

    /** 进入搜索前记住当前类别列表的滚动位置，避免搜索结果的临时滚动污染下次恢复。 */
    private void rememberNormalScrollBeforeSearch() {
        if (!isSearching()) {
            lastNormalScrollY = scrollY;
        }
    }

    /** 退出搜索后回到上次正常类别列表的滚动位置，并按当前视口上限钳制。 */
    private int restoreNormalScrollY() {
        return Math.max(0, lastNormalScrollY);
    }

    /**
     * 窗口尺寸变化（★ 用户要求「随窗口大小改变 UI 大小」——宽度自适应）。
     *
     * <p>分区框位置、贴片宽度、滚动上限、类别按钮横向居中位置全都由 width/height 推导，
     * 所以 resize 时必须重算一遍，否则拉完窗口后「画出来的」和「算出来的」会不一致。
     */
    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        // ★ 2026-09-20 平台差异：1.20.1 的 Screen.resize 会回调 init()，
        //   而 1.21.1(NeoForge) 的 resize 只改尺寸 + repositionElements()、<b>不</b>回调 init()。
        //   搜索框的坐标依赖 width/height 推导，所以两边都必须在这里重建一次。
        //   ensureSearchBox() 内部会比较几何，尺寸没变就不重建，避免 1.20.1 上白做一次。
        runInLayoutSpace(() -> {
            ensureSearchBox();
            rebuildButtons();
            restorePersistedViewState();
        });
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // ★ 2026-09-21 界面缩放：整体套一次缩放变换，并把 width/height 换成「设计空间」。
        //   布局代码全部从 width/height 推导 → 两者一起换，布局代码一行不用改。
        //   缩放后布局占据屏幕的逻辑尺寸 = 设计空间 × r = 原逻辑尺寸 → 恰好铺满（自然居中，无需偏移）。
        final double r = uiScale();
        // 界面尺寸刚变化 → 主界面布局几何（行坐标/类别行居中/搜索框/滚动上限）必须重算，
        // 否则「画出来的」与「算出来的」不一致。
        // ★ 2026-09-21 用户要求：子界面【不】跟着实时变，下次打开时在 openSubScreen 里同步即可
        //   （且 activeSubScreen 是单例 —— 缩放只能从设置面板改，而它自己豁免缩放，
        //    所以这里重建子界面本就是死代码）。
        if (r != lastAppliedScale) {
            lastAppliedScale = r;
            runInLayoutSpace(() -> {
                ensureSearchBox();
                rebuildButtons();
                restorePersistedViewState();
            });
        }
        final int savedW = width;
        final int savedH = height;
        final boolean savedInLayoutSpace = inLayoutSpace;
        final boolean scaled = r != 1.0;
        if (scaled) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale((float) r, (float) r, 1.0F);
            width = Math.max(1, (int) Math.round(savedW / r));
            height = Math.max(1, (int) Math.round(savedH / r));
        }
        inLayoutSpace = true;
        try {
            // 鼠标坐标反算：外部给的是屏幕逻辑坐标，布局/命中判定用的是设计空间坐标
            renderInner(guiGraphics, (int) (mouseX / r), (int) (mouseY / r), partialTick);
        } finally {
            inLayoutSpace = savedInLayoutSpace;
            width = savedW;
            height = savedH;
            if (scaled) {
                guiGraphics.pose().popPose();
            }
        }
        // ★ 2026-09-21 用户要求：设置子界面【脱离缩放】，在 1:1 坐标系里渲染。
        //   原因：设置面板里就是「界面尺寸」滑块，而面板本身又在这个缩放的坐标系里 →
        //   拖动时改值会改变鼠标换算基准，形成自反馈（手柄抓不住光标）。
        //   不参与缩放后它坐标系恒定 → 拖动天然线性，无需冻结整棵树。
        if (activeSubScreen instanceof SettingsSubScreen) {
            activeSubScreen.render(guiGraphics, mouseX, mouseY);
            guiGraphics.flush();
        }
    }

    // ========================================================================
    // 界面缩放（★ 2026-09-21 设置面板「界面尺寸」联动，用户要求独立于原版 GUI 尺寸）
    // ========================================================================

    /**
     * 当前界面绘制倍率 {@code r = 3z / S}。
     *
     * <ul>
     *   <li>{@code z = 1 + offset/100}——offset 来自设置面板，0 = 基准；
     *       自适应模式下 {@code z} 由窗口物理尺寸决定</li>
     *   <li>{@code S} = 游戏实际 GUI Scale（{@code Window#getGuiScale()}），
     *       除以它是为了<b>抵消原版</b> → 界面大小只由我们的设置决定，与原版视频设置无关</li>
     * </ul>
     *
     * <p>贴片、进度条、图标仍按连续倍率缩放。文字不走这条 pose 缩放，
     * 而是用 {@link #drawCrispString} 在屏幕坐标上按原版整数 GUI Scale 绘制，
     * 所以字号不会跟着窗口/自定义缩放变糊。
     */
    double uiScale() {
        final boolean adaptive = org.zifeng.skilltree.client.SkillKeyBinds.isSkillTreeGuiAdaptive();
        final int off = org.zifeng.skilltree.client.SkillKeyBinds.getSkillTreeGuiOffset();
        double s = 3.0;
        double z;
        if (this.minecraft != null && this.minecraft.getWindow() != null) {
            var window = this.minecraft.getWindow();
            s = window.getGuiScale();
            if (adaptive) {
                z = Math.min(1.0, Math.min(window.getWidth() / 1920.0, window.getHeight() / 1080.0));
            } else {
                z = (100 + off) / 100.0;
            }
        } else {
            z = adaptive ? 1.0 : (100 + off) / 100.0;
        }
        // ★ 2026-09-21：钳制改为按「收缩阶梯可用下限」推导（原来是拍脑袋的 528）。
        //   旧值过大 → 1920 宽窗口上 z 封顶 1.2121 → +22~+100 全是死区。
        //   ⚠️ Math.max(0.5, …) 保留：极端小窗口上不让 z 掉到失去意义的值。
        //   贴片内不会再重叠 —— 收缩阶梯负责牺牲间距/属性，进度条硬性不得越过 Q/E/R。
        if (this.minecraft != null && this.minecraft.getWindow() != null) {
            double maxZByWidth = this.minecraft.getWindow().getWidth() / (3.0 * MIN_VIABLE_DESIGN_W);
            z = Math.min(z, Math.max(0.5, maxZByWidth));
        }
        // offset=0 仍然必须使用 3/S 抵消原版 GUI Scale；只有 S=3 时才恰好等于 1。
        return s <= 0 ? 1.0 : (3.0 * z) / s;
    }

    /**
     * 把设计空间里的文字画到屏幕逻辑坐标上，避开外层 {@code pose.scale(r)}。
     *
     * <p>Minecraft 默认字体是位图字形，经过非整数 pose 缩放后边缘会发糊。
     * 这里先取出当前 pose 的平移，再把缩放抵消掉，让字形按原版整数 GUI Scale 绘制。
     * 字号因此保持原版清晰度，不会跟着窗口或自定义缩放变小。
     *
     * <p>★ 2026-09-21（用户要求）：<b>全部文字不带投影</b>（{@code dropShadow = false}）。
     * 本界面底色多为浅色（搜索框、浅色胶囊按钮、子贴片），而原版投影是右下 (+1,+1)
     * 的深色硬边字，压在浅底上会变成清清楚楚的「第二层字」——看上去像重影。
     */
    private void drawCrispString(GuiGraphics guiGraphics, String text, int x, int y, int color, boolean centered) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final var pose = guiGraphics.pose();
        final org.joml.Matrix4f m = pose.last().pose();
        final float sx = m.m00();
        final float sy = m.m11();
        final float tx = m.m30();
        final float ty = m.m31();
        if (Math.abs(sx - 1.0F) < 0.001F && Math.abs(sy - 1.0F) < 0.001F) {
            if (centered) {
                // drawCenteredString 没有阴影开关（内部固定 true）→ 自己算居中
                guiGraphics.drawString(font, text, x - font.width(text) / 2, y, color, false);
            } else {
                guiGraphics.drawString(font, text, x, y, color, false);
            }
            return;
        }
        pose.pushPose();
        pose.setIdentity();
        // 文字必须落在整数屏幕像素上。外层技能树缩放通常是非整数，直接使用
        // x*s/y*s 会让位图字形落在半像素边界，驱动层再做线性过滤后依然发糊。
        // 这里只吸附平移后的最终坐标，不改变布局空间中的测量结果。
        final float pixelX = Math.round(tx + x * sx);
        final float pixelY = Math.round(ty + y * sy);
        pose.translate(pixelX, pixelY, 0.0F);
        if (centered) {
            guiGraphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        } else {
            guiGraphics.drawString(font, text, 0, 0, color, false);
        }
        pose.popPose();
    }

    /**
     * tooltip 专用：按相对字号绘制，但最终仍落在原版整数 GUI Scale 上。
     * 标题 1.15、正文 1.0、提示 0.8 这些相对关系保留，只是不再叠一层非整数 pose 缩放。
     * <p>★ 2026-09-21：同样不带投影（见 {@link #drawCrispString}）。
     */
    private void drawCrispScaledString(GuiGraphics guiGraphics, String text, int x, int y, int color, float extraScale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final var pose = guiGraphics.pose();
        final org.joml.Matrix4f m = pose.last().pose();
        final float sx = m.m00();
        final float sy = m.m11();
        final float tx = m.m30();
        final float ty = m.m31();
        if (extraScale == 1.0F && Math.abs(sx - 1.0F) < 0.001F && Math.abs(sy - 1.0F) < 0.001F) {
            guiGraphics.drawString(font, text, x, y, color, false);
            return;
        }
        pose.pushPose();
        pose.setIdentity();
        final float pixelX = Math.round(tx + x * sx);
        final float pixelY = Math.round(ty + y * sy);
        pose.translate(pixelX, pixelY, 0.0F);
        if (extraScale != 1.0F) {
            pose.scale(extraScale, extraScale, 1.0F);
        }
        guiGraphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    /** 上一帧实际使用的倍率（用于检测「界面尺寸变了」→ 重算布局几何） */
    private double lastAppliedScale = Double.NaN;

    /**
     * 在「设计空间」里执行一段布局计算（临时把 width/height 换算成设计空间尺寸）。
     *
     * <p>用于 {@link #init()}/{@link #resize} —— 行坐标、类别行居中、搜索框几何、
     * 滚动上限都从 width/height 推导，都必须在设计空间里算，否则缩放后会与渲染不一致。
     */
    private void runInLayoutSpace(Runnable action) {
        // ★ 2026-09-21 防重入：已在设计空间内 → 直接执行。
        //   否则嵌套调用会【重复除 r】→ 尺寸被算小两次（类别行会因此判定“放不下”而左靠齐）。
        if (inLayoutSpace) {
            action.run();
            return;
        }
        final double r = uiScale();
        final int savedW = width;
        final int savedH = height;
        if (r != 1.0) {
            width = Math.max(1, (int) Math.round(savedW / r));
            height = Math.max(1, (int) Math.round(savedH / r));
        }
        inLayoutSpace = true;
        try {
            action.run();
        } finally {
            inLayoutSpace = false;
            width = savedW;
            height = savedH;
        }
    }

    /** 是否已处于「设计空间」内（{@link #runInLayoutSpace} 防重入用） */
    private boolean inLayoutSpace = false;

    /**
     * 把屏幕逻辑坐标换算到设计空间后执行。
     * 鼠标事件必须和 {@link #runInLayoutSpace} 共用同一套 width/height 与防重入标志，
     * 否则点击类别/搜索框时会再除一次 r，类别栏就会在居中和左靠齐之间跳动。
     */
    private boolean runInLayoutSpace(double mouseX, double mouseY,
                                     java.util.function.BiFunction<Double, Double, Boolean> action) {
        if (inLayoutSpace) {
            return action.apply(mouseX, mouseY);
        }
        final double r = uiScale();
        final int savedW = width;
        final int savedH = height;
        double layoutX = mouseX;
        double layoutY = mouseY;
        if (r != 1.0) {
            layoutX = mouseX / r;
            layoutY = mouseY / r;
            width = Math.max(1, (int) Math.round(savedW / r));
            height = Math.max(1, (int) Math.round(savedH / r));
        }
        inLayoutSpace = true;
        try {
            return action.apply(layoutX, layoutY);
        } finally {
            inLayoutSpace = false;
            width = savedW;
            height = savedH;
        }
    }

    /**
     * 开启裁剪（★ 缩放感知）。
     *
     * <p>⚠️ 1.21.1 的 {@code GuiGraphics.enableScissor} 同样<b>不套用 pose</b>
     * （字节码确认：直接拿参数构造 ScreenRectangle，再由 {@code applyScissor} 用
     * {@code Window.getGuiScale()} 换算成物理像素）→ 整体缩放后会错位，
     * 所以这里手动乘 r 换回屏幕逻辑坐标。
     */
    private void enableScissorScaled(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
        final double r = uiScale();
        if (r == 1.0) {
            guiGraphics.enableScissor(x1, y1, x2, y2);
        } else {
            guiGraphics.enableScissor((int) Math.floor(x1 * r), (int) Math.floor(y1 * r),
                    (int) Math.ceil(x2 * r), (int) Math.ceil(y2 * r));
        }
    }

    /** 界面主体渲染（在缩放变换与设计空间内执行；★ 2026-09-21 从 render 拆出） */
    private void renderInner(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        beginFrame();      // 2026-09-15：帧序号 +1（帧内缓存失效判断）
        releaseStaleLevelDrag(); // 2026-09-20：兜底清理残留的进度条拖拽状态（否则滚轮会永久失效）
        updateViewport();  // 2026-09-15：可视区域（屏幕外按钮/列标题整块跳过）
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // ============ 显示层级（统一逻辑，以后所有界面元素都按此分层） ============
        // 第五图层（最底）：技能树界面背景（纯底色，无边框）
        renderLayer5Background(guiGraphics);
        guiGraphics.flush();

        // 预计算当前悬停按钮的 tooltip 边界（供图标跳过判定：被 tooltip 覆盖的图标不渲染，避免半透明透出）
        updateActiveTooltipBounds(mouseX, mouseY);

        // 第四图层：技能树本体（列标题 + 技能按钮，含图标/文字）+ 按键设置窗口（同图层，2026-08-13 需求）
        renderLayer4SkillTree(guiGraphics);
        guiGraphics.flush();

        // 第三图层（中间）：所有悬浮显示（技能悬停提示 tooltip）
        renderLayer3Tooltips(guiGraphics, mouseX, mouseY);
        guiGraphics.flush();

        // 第二图层：2026-09-19 起贴片（大贴片+子贴片）改在 L4 开头统一绘制，此处不再画框线

        // 第一图层（最顶）：右下角功能按钮等顶部 UI
        renderLayer1HeaderAndPanels(guiGraphics);
        guiGraphics.flush();

        // 子界面图层（2026-09-01 子界面系统）：最上层渲染当前打开的子界面（不透明面板覆盖一切）
        // ★ 2026-09-21：设置子界面脱离缩放，在 render 末尾以 1:1 渲染，此处跳过
        if (activeSubScreen != null && !(activeSubScreen instanceof SettingsSubScreen)) {
            activeSubScreen.render(guiGraphics, mouseX, mouseY);
            guiGraphics.flush();
        }

        // 右下角功能按钮（HUD调整 + 属性面板）：子界面之上，始终可点（2026-09-01 统一风格）
        renderBottomButtons(guiGraphics);
    }

    /**
     * 第五图层（最底）：技能树界面背景（纯全屏底色，不含边框）。
     * 被第四图层及以上的所有元素覆盖。
     */
    private void renderLayer5Background(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, width, height, Config.SKILL_TREE_BACKGROUND_COLOR.get());
    }

    /**
     * 嵌套贴片：<b>底层大贴片 + 三个子贴片</b>（2026-09-19 用户给出的结构）。
     *
     * <pre>
     *   底层大贴片（整块 UI）
     *     ├─ 子-标题贴片（技能点）
     *     ├─ 子-类别贴片（类别按钮）
     *     └─ 子-技能贴片（每行一张，另见 renderSkillButton）
     * </pre>
     *
     * <p>分层手法沿用 Miuix 的 {@code ZSurface}：靠「色阶 + 1px 描边」体现层级，不用投影。
     * <p>⚠️ 直接在 L4 开头画（而不是原来的「第二图层画分隔线」）——
     * 这样 tooltip（L3）能干净地浮在贴片之上，不会被框线切边。
     */
    private void renderPatches(GuiGraphics guiGraphics) {
        // ① 底层大贴片
        fillRound(guiGraphics, bigLeft(), bigTop(), bigRight(), bigBottom(), R_BIG, C_BIG_BG);
        // ② 三个子贴片
        subPatch(guiGraphics, titleFrameTop(), titleFrameH());
        subPatch(guiGraphics, catFrameTop(), catFrameH());
        subPatch(guiGraphics, listFrameTop(), listFrameH());
    }

    /** 一张子贴片：圆角底 + 1px 圆角描边（外圈色块 + 内缩一像素的底色） */
    private void subPatch(GuiGraphics guiGraphics, int top, int h) {
        if (h <= 0) {
            return;
        }
        final int l = frameLeft();
        final int r = frameRight();
        final int b = top + h;
        fillRound(guiGraphics, l, top, r, b, R_SUB, C_SUB_EDGE);
        fillRound(guiGraphics, l + FRAME_LINE, top + FRAME_LINE, r - FRAME_LINE, b - FRAME_LINE,
                Math.max(0, R_SUB - FRAME_LINE), C_SUB_BG);
    }

    /**
     * 第四图层：技能树本体（2026-09-19 L4 分区重构）。
     *
     * <p>★ 用户明确要求：<b>标题行、类别行、技能行这三行都属于 L4 区域</b>，
     * 整个「技能 UI 界面」的版式参考原版快捷键界面 —— 宽度自适应、行高与字号固定。
     *
     * <p>三个分区框的框线画在第二图层（{@link #renderLayer2Border}），
     * 这里负责框内的三块内容：①技能点标题行（左对齐）②类别行（水平居中）③技能行列表（左对齐）。
     */
    private void renderLayer4SkillTree(GuiGraphics guiGraphics) {
        // ---------- ① 嵌套贴片底/描边（大贴片 + 三个子贴片，先铺底）----------
        renderPatches(guiGraphics);

        // ---------- ② 标题行：技能点（左对齐，固定不滚动）----------
        renderHeaderInfo(guiGraphics);

        // ---------- ③ 类别行（水平居中，固定不滚动）----------
        renderCategoryRow(guiGraphics);

        // ---------- ③ 技能行列表（左对齐；★ 用剪裁框限制在技能行分区框内）----------
        enableScissorScaled(guiGraphics, frameLeft() + FRAME_LINE, listTop(),
                frameRight() - FRAME_LINE, listBottom());
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(rowLeft(), listTop() - scrollY, 0);
        final double panelMouseX = toPanelX(lastMouseX);
        final double panelMouseY = toPanelY(lastMouseY);
        final boolean mouseOverList = isMouseOverSkillList(lastMouseX, lastMouseY);
        final Map<String, com.mojang.blaze3d.platform.InputConstants.Key> keyBinds =
                org.zifeng.skilltree.client.SkillKeyBinds.allBinds();
        final Map<String, com.mojang.blaze3d.platform.InputConstants.Key> levelBinds =
                org.zifeng.skilltree.client.SkillKeyBinds.allLevelBinds();
        final Map<String, com.mojang.blaze3d.platform.InputConstants.Key> triggerBinds =
                org.zifeng.skilltree.client.SkillKeyBinds.allTriggerBinds();
        for (SkillButton button : buttons) {
            if (!isButtonVisible(button)) {
                continue; // 视口剔除：屏幕外的行整块跳过（贴片/图标/文字/格）
            }
            renderSkillButton(guiGraphics, button, panelMouseX, panelMouseY, mouseOverList,
                    keyBinds, levelBinds, triggerBinds);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();

        // ---------- ④ 右侧滚动条（固定，屏幕坐标）----------
        renderListScrollbar(guiGraphics);
    }

    /**
     * 类别按钮行（分区框 2 内：十个类别）。
     *
     * <p>★ 用户要求：这一行<b>水平居中</b>（整组按钮在分区框内居中）。
     * 样式沿用本界面既有的按钮风格：深蓝底 + 淡蓝边框 + 选中高亮。
     * <p>选中态 = 类别强调色边框 + 提亮底色 + 白色文字。
     */
    private void renderCategoryRow(GuiGraphics guiGraphics) {
        int y = catFrameTop() + FRAME_LINE + LIST_TOP_GAP;
        enableScissorScaled(guiGraphics, frameLeft() + FRAME_LINE, catFrameTop() + FRAME_LINE,
                frameRight() - FRAME_LINE, catFrameBottom() - FRAME_LINE);
        // ⚠️ 2026-09-19 性能优化：文本在建行时缓存 + 统一用默认的 gui 批次 fill —— 与「原版按钮」同一套做法。
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            int x = catButtonX[i];
            int w = catButtonW[i];
            if (x + w < frameLeft() || x > frameRight()) {
                continue; // 横向滚动到框外的跳过
            }
            String title = catTitles[i] != null ? catTitles[i] : "";
            // ★ 2026-09-22 方案 B：框与字由同一个 AutoBox 决定宽度，不可能错位
            AutoBox box = new AutoBox(x, y, w, CAT_BTN_H, title);
            boolean selected = (i == selectedCategory);
            boolean hovered = isMouseOverCategories(lastMouseX, lastMouseY) && box.contains(lastMouseX, lastMouseY);
            // ★ 2026-09-19：类别按钮改成【浅色圆角胶囊】（子贴片现在是浅色了，深色块会显重）
            int accent = CATEGORY_COLORS[i];
            int bg = selected ? darken(accent, 0.5f) : (hovered ? 0xFFFFFFFF : 0xFFF6F6FA);
            int border = selected ? accent : (hovered ? accent : ((accent & 0x00FFFFFF) | 0x70000000));
            int color = selected ? 0xFFFFFFFF : (hovered ? 0xFF2A2A34 : 0xFF4A4A56);
            drawAutoBoxCapsule(guiGraphics, box, bg, border, color);
        }
        // 类别行横向滚动条（2px，仅放不下时出现；与右侧竖向滚动条同色，便于发现可左右滚）
        int catScrollMax = Math.max(0, catContentW - catViewW());
        if (catScrollMax > 0) {
            int viewW = catViewW();
            int trackX = frameLeft() + FRAME_LINE;
            int trackY = y + CAT_BTN_H;
            guiGraphics.fill(trackX, trackY, trackX + viewW, trackY + 2, 0x55000000);
            int thumbW = Math.max(12, (int) ((long) viewW * viewW / Math.max(1, viewW + catScrollMax)));
            int thumbX = trackX + (int) ((long) (viewW - thumbW) * catScrollX / Math.max(1, catScrollMax));
            guiGraphics.fill(thumbX, trackY, thumbX + thumbW, trackY + 2, 0xFF87CEEB);
        }
        guiGraphics.disableScissor();
    }

    /**
     * 右侧竖向滚动条（技能行分区框内右端）。
     * <p>滑块高度按「视口高 / 内容高」比例；无可滚动内容时不绘制。
     * <p>2026-09-19：去掉 guiOverlay，用默认批次 fill（与列表同层，减少渲染类型切换）。
     */
    private void renderListScrollbar(GuiGraphics guiGraphics) {
        if (scrollMax <= 0) {
            return;
        }
        int x = rowRight() + SCROLLBAR_MARGIN;
        int top = listTop();
        int viewH = listViewH();
        int barH = listScrollbarThumbH();
        int barY = listScrollbarThumbY();
        boolean active = listScrollbarDragging;
        // ★ 2026-09-21：悬停/拖动时提亮，让“可拖动”变得可发现（用户反馈之前不知道能拖）
        boolean hovered = !active && isOverListScrollbar(lastMouseX, lastMouseY);
        guiGraphics.fill(x, top, x + SCROLLBAR_W, top + viewH, active ? 0x77000000 : 0x55000000);
        guiGraphics.fill(x, barY, x + SCROLLBAR_W, barY + barH,
                active ? 0xFFB8E4FF : (hovered ? 0xFFAEE6FF : 0xFF87CEEB));
    }

    /** 滚动条滑块高度（可视高 / 内容高 比例；最小 16px） */
    private int listScrollbarThumbH() {
        int viewH = listViewH();
        return Math.max(16, (int) ((long) viewH * viewH / Math.max(1, viewH + scrollMax)));
    }

    /** 滚动条滑块顶部 y */
    private int listScrollbarThumbY() {
        int viewH = listViewH();
        int barH = listScrollbarThumbH();
        return listTop() + (int) ((long) (viewH - barH) * scrollY / Math.max(1, scrollMax));
    }

    /**
     * 滚动条命中区（★ 2026-09-21 新增，供鼠标拖动）。
     *
     * <p>命中区比视觉宽（{@link #SCROLLBAR_W}）左右各扩 4px —— 条子只有 6px 宽，
     * 按视觉宽度命中的话很难点中。
     */
    private boolean isOverListScrollbar(double mouseX, double mouseY) {
        if (scrollMax <= 0) {
            return false;
        }
        int x = rowRight() + SCROLLBAR_MARGIN;
        int top = listTop();
        int bottom = top + listViewH();
        return mouseX >= x - SCROLLBAR_HIT_PAD && mouseX <= x + SCROLLBAR_W + SCROLLBAR_HIT_PAD
                && mouseY >= top && mouseY <= bottom;
    }

    /** 滚动条命中区的左右外扩量 */
    private static final int SCROLLBAR_HIT_PAD = 4;

    /** 滚动条命中区（拖拽用） */
    private boolean isOverListScrollbarThumb(double mouseX, double mouseY) {
        if (scrollMax <= 0) {
            return false;
        }
        int barY = listScrollbarThumbY();
        int x = rowRight() + SCROLLBAR_MARGIN;
        return mouseX >= x - SCROLLBAR_HIT_PAD && mouseX <= x + SCROLLBAR_W + SCROLLBAR_HIT_PAD
                && mouseY >= barY && mouseY <= barY + listScrollbarThumbH();
    }

    /** 拖动中：根据鼠标 y 位置反算 scrollY（拖动跟随，滑块中心对齐光标） */
    private void dragListScrollbarTo(double mouseY) {
        if (scrollMax <= 0) {
            return;
        }
        int top = listTop();
        int viewH = listViewH();
        int barH = listScrollbarThumbH();
        int travel = Math.max(1, viewH - barH);
        // 鼠标相对轨道顶部的偏移，减去拖动开始时“光标在滑块内的位置”→ 滑块顶部
        double thumbTop = mouseY - top - listScrollbarGrabDy;
        int newY = (int) Math.round(thumbTop * scrollMax / travel);
        scrollY = Math.max(0, Math.min(scrollMax, newY));
        if (!isSearching()) {
            lastNormalScrollY = scrollY;
        }
        markViewStateChanged();
    }

    /**
     * 第三图层（中间）：所有悬浮显示（技能悬停提示）。
     * 背景用 guiOverlay 渲染 → 盖住第四图层按钮；但先于第二图层（边框）/第一图层（面板）提交 → 被它们盖住。
     * ⚠️ 鼠标在第一图层任何 UI 元素上（属性面板/顶部标题/底部提示条/右下角开关按钮）时不显示技能提示。
     */
    private void renderLayer3Tooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!isMouseOverSkillList(mouseX, mouseY)) {
            return;
        }
        // 按键框悬停提示（2026-08-13 修复）：必须在无变换的 L3 层用屏幕坐标绘制，
        // 否则 renderTooltip 在 L4 技能树变换内坐标错乱（提示偏离鼠标）
        SkillButton button = findHoveredSkillButton(mouseX, mouseY);
        if (button != null) {
            boolean togglable = Skills.isTogglable(button.skillId());
            // 2026-09-19：贴片内四段几何（内容区 │ Q │ E │ R，与 renderSkillButton / mouseClicked 完全一致）
            final int cw = contentW();
            int kx = button.x() + cw;
            int k2x = kx + KEY_BOX_WIDTH;
            int k3x = k2x + KEY2_BOX_WIDTH;
            int ky = button.y();
            double lx = toPanelX(mouseX);
            double ly = toPanelY(mouseY);
            boolean onRowY = ly >= ky && ly <= ky + BUTTON_HEIGHT;
            // 第一框悬停提示（仅可开关技能；显示开关状态 + 清空快捷键说明）
            if (togglable && onRowY && lx >= kx && lx <= kx + KEY_BOX_WIDTH) {
                boolean enabled = toggles.getOrDefault(button.skillId(), Boolean.TRUE);
                var bound = org.zifeng.skilltree.client.SkillKeyBinds.getKey(button.skillId());
                String boundText = bound != null ? t("tip_bound") + ": " + bound.getDisplayName().getString() : t("tip_unbound");
                renderCrispTooltip(guiGraphics, java.util.List.of(
                                Component.translatable("ui.zifeng_s_custom_skill_tree.key_toggle", Skills.getDisplayNameComponent(button.skillId())),
                                Component.literal(t("tip_current_status") + (enabled ? t("status_on") : t("status_off"))),
                                Component.literal(boundText),
                                Component.literal(t("tip_bind_hint")),
                                Component.literal(t("tip_clear_hint") + "(Backspace/Delete)")),
                        mouseX, mouseY + 12);
                return;
            }
            // 第二列按键框悬停提示（2026-08-13：每个技能独立描述 + 清空快捷键说明）
            if (isLevelBindable(button.skillId())) {
                if (onRowY && lx >= k2x && lx <= k2x + KEY2_BOX_WIDTH) {
                    String skillId = button.skillId();
                    var bound = org.zifeng.skilltree.client.SkillKeyBinds.getLevelKey(skillId);
                    String boundText = bound != null ? t("tip_bound") + ": " + bound.getDisplayName().getString() : t("tip_unbound");
                    // 按技能独立描述（每个技能文案不同）
                    java.util.List<Component> lines;
                    if (Skills.AURA_DAMAGE.equals(skillId)) {
                        String modeText = modeTextOf(skillId);
                        lines = java.util.List.of(
                                Component.translatable("ui.zifeng_s_custom_skill_tree.key_mode", Skills.getDisplayNameComponent(skillId)),
                                Component.literal(t("tip_aura_dmg_target") + "【" + modeText + "】"),
                                Component.literal(t("tip_aura_mode_desc")),
                                Component.literal(boundText),
                                Component.literal(t("tip_bind_hint")),
                                Component.literal(t("tip_clear_hint") + "(Backspace/Delete)"));
                    } else if (Skills.AURA_SPEED.equals(skillId)) {
                        String modeText = modeTextOf(skillId);
                        lines = java.util.List.of(
                                Component.translatable("ui.zifeng_s_custom_skill_tree.key_mode", Skills.getDisplayNameComponent(skillId)),
                                Component.literal(t("tip_aura_speed_target") + "【" + modeText + "】"),
                                Component.literal(t("tip_aura_speed_desc")),
                                Component.literal(boundText),
                                Component.literal(t("tip_bind_hint")),
                                Component.literal(t("tip_clear_hint") + "(Backspace/Delete)"));
                    } else if (Skills.AURA_HEAL.equals(skillId)) {
                        String modeText = modeTextOf(skillId);
                        lines = java.util.List.of(
                                Component.translatable("ui.zifeng_s_custom_skill_tree.key_mode", Skills.getDisplayNameComponent(skillId)),
                                Component.literal(t("tip_aura_heal_target") + "【" + modeText + "】"),
                                Component.literal(t("tip_aura_heal_desc")),
                                Component.literal(boundText),
                                Component.literal(t("tip_bind_hint")),
                                Component.literal(t("tip_clear_hint") + "(Backspace/Delete)"));
                    } else if (Skills.isContainerHaul(skillId)) {
                        // 子枫的搬运术：第二键 = 搬运模式循环（自动⇄手动）
                        String haulMode = haulModeTextOf(skillId);
                        lines = java.util.List.of(
                                Component.translatable("ui.zifeng_s_custom_skill_tree.key_mode", Skills.getDisplayNameComponent(skillId)),
                                Component.literal(t("tip_haul_mode") + "【" + haulMode + "】"),
                                Component.literal(t("tip_haul_mode_desc")),
                                Component.literal(boundText),
                                Component.literal(t("tip_bind_hint")),
                                Component.literal(t("tip_clear_hint") + "(Backspace/Delete)"));
                    } else if (Skills.LOOT_BOMB.equals(skillId)) {
                        // ★ 2026-09-30：战利品大爆发：第二键 = 4 态模式循环（全 / 仅堆叠 / 仅不堆叠 / 黑名单）
                        String lootMode = lootModeTextOf(skillId);
                        lines = java.util.List.of(
                                Component.translatable("ui.zifeng_s_custom_skill_tree.key_mode", Skills.getDisplayNameComponent(skillId)),
                                Component.literal(t("tip_loot_mode") + "【" + lootMode + "】"),
                                Component.literal(t("tip_loot_mode_desc")),
                                Component.literal(boundText),
                                Component.literal(t("tip_bind_hint")),
                                Component.literal(t("tip_clear_hint") + "(Backspace/Delete)"));
                    } else {
                        // 可调等级技能：显示当前生效/已学 + 操作说明（Shift=10级 Ctrl+Shift=100级 Alt反向）
                        int learned = learnedSkills.getOrDefault(skillId, 0);
                        int active = activeLevels.getOrDefault(skillId, learned);
                        lines = java.util.List.of(
                                Component.translatable("ui.zifeng_s_custom_skill_tree.key_level", Skills.getDisplayNameComponent(skillId)),
                                Component.literal(t("tip_current_active") + active + " / " + t("tip_learned") + learned),
                                Component.literal(boundText),
                                Component.literal(t("tip_key_steps")),
                                Component.literal(t("tip_key_alt")),
                                Component.literal(t("tip_bind_hint")),
                                Component.literal(t("tip_clear_hint") + "(Backspace/Delete)"));
                    }
                    renderCrispTooltip(guiGraphics, lines, mouseX, mouseY + 12);
                    return;
                }
            }
            // 第三列按键框悬停提示（2026-09-07：功能触发键——主动技场景内按一下触发一次）
            if (Skills.isTriggerBindable(button.skillId())) {
                if (onRowY && lx >= k3x && lx <= k3x + KEY3_BOX_WIDTH) {
                    String skillId = button.skillId();
                    var trig = org.zifeng.skilltree.client.SkillKeyBinds.getTriggerKey(skillId);
                    String trigText = trig != null ? t("tip_bound") + ": " + trig.getDisplayName().getString() : t("tip_unbound");
                    if (Skills.isContainerHaul(skillId)) {
                        renderCrispTooltip(guiGraphics, java.util.List.of(
                                        Component.translatable("ui.zifeng_s_custom_skill_tree.key_trigger", Skills.getDisplayNameComponent(skillId)),
                                        Component.literal(t("tip_haul_trigger")),
                                        Component.literal(trigText),
                                        Component.literal(t("tip_bind_hint")),
                                        Component.literal(t("tip_clear_hint") + "(Backspace/Delete)")),
                                mouseX, mouseY + 12);
                    } else if (Skills.BLINK.equals(skillId)) {
                        // 闪现（2026-09-07 规范改造）：触发键 = 按下向视线方向传送一次
                        renderCrispTooltip(guiGraphics, java.util.List.of(
                                        Component.translatable("ui.zifeng_s_custom_skill_tree.key_trigger", Skills.getDisplayNameComponent(skillId)),
                                        Component.literal(t("tip_blink_trigger")),
                                        Component.literal(trigText),
                                        Component.literal(t("tip_bind_hint")),
                                        Component.literal(t("tip_clear_hint") + "(Backspace/Delete)")),
                                mouseX, mouseY + 12);
                    } else {
                        renderCrispTooltip(guiGraphics, java.util.List.of(
                                        Component.translatable("ui.zifeng_s_custom_skill_tree.key_trigger", Skills.getDisplayNameComponent(skillId)),
                                        Component.literal(trigText),
                                        Component.literal(t("tip_bind_hint")),
                                        Component.literal(t("tip_clear_hint") + "(Backspace/Delete)")),
                                mouseX, mouseY + 12);
                    }
                    return;
                }
            }
        }
        SkillButton hoveredButton = findHoveredSkillButton(mouseX, mouseY);
        if (hoveredButton != null && overNameArea(mouseX, mouseY, hoveredButton)) {
            renderSkillTooltip(guiGraphics, hoveredButton, mouseX, mouseY);
        }
    }

    /**
     * 第一图层（最顶）。
     * <p>2026-09-19：技能点标题行已并入 L4（用户明确「标题行/类别行/技能行同属 L4」），
     * 本层不再重复绘制（否则会叠两层）。
     */
    private void renderLayer1HeaderAndPanels(GuiGraphics guiGraphics) {
        // 目前无内容：右下角功能按钮在 render() 末尾单独绘制
    }

    // ============ 右下角功能按钮（2026-09-01 统一风格：HUD调整 左、属性面板 右，平行并排，固定文字） ============

    /**
     * 按钮左右内边距（文字两侧留白）。
     */
    private static final int BOTTOM_BTN_PAD_X = 6;
    /** 按钮最小宽（中文短文案时保持原本 58px 的观感） */
    private static final int BOTTOM_BTN_MIN_W = 58;
    private static final int BOTTOM_BTN_H = 16;
    private static final int BOTTOM_BTN_GAP = 4;
    private static final int BOTTOM_BTN_MARGIN = 8;

    /**
     * 底部三个按钮的宽度（★ 2026-09-22 修正：改为按当前语言<b>实测</b>文字宽）。
     *
     * <p><b>为什么必须先修</b>：此前写死 {@code 58px}（按中文 5 字估算），
     * 而英文文案宽得多 —— {@code ≡ Attribute Panel} 实测 <b>105px</b>、
     * {@code ⚙` HUD Adjust} <b>75px</b> → 文字直接溢出按钮外（用户反馈「英文名字有点太长」）。
     *
     * <p>现在取三个文案中最宽的一个 + 两侧留白，<b>任何语言都不会溢出</b>；
     * 三个按钮共用同一宽度以保持对齐；中文仍保持原本 58px 的观感。
     */
    private int bottomBtnW() {
        // ★ 2026-09-22 方案 B：宽度走 autoBoxW（内部 lw() ÷r），
        //   与 drawAutoBoxRect 的框、contains 的命中均出自这一个值。
        //   用户曾报「窗口变小后右下角三按钮文字超出」——根因就是这里用了 font.width（屏幕基准）。
        return autoBoxW(BOTTOM_BTN_PAD_X, BOTTOM_BTN_MIN_W,
                t("settings_btn"), t("hud_adjust"), t("panel_btn_open"));
    }

    /** 属性面板按钮（右下角，右侧） */
    private int panelToggleX() {
        return width - BOTTOM_BTN_MARGIN - bottomBtnW();
    }

    private int panelToggleY() {
        return height - BOTTOM_BTN_MARGIN - BOTTOM_BTN_H;
    }

    /** HUD 调整按钮（属性面板按钮左边，平行同高） */
    private int hudBtnX() {
        return panelToggleX() - BOTTOM_BTN_GAP - bottomBtnW();
    }

    private int hudBtnY() {
        return panelToggleY();
    }

    /**
     * 设置按钮（HUD调整按钮<b>左边</b>，平行同高；★ 2026-09-21 新增）。
     * <p>用户要求：「在 hud调整左边新增一个按钮，叫设置」。
     * <p>布局：{@code [设置] [⚙ HUD调整] [≡ 属性面板]}（从右边缘往左依次排列）。
     */
    private int settingsBtnX() {
        return hudBtnX() - BOTTOM_BTN_GAP - bottomBtnW();
    }

    private int settingsBtnY() {
        return hudBtnY();
    }

    /** 统一按钮绘制：底色 + 边框 + 悬停 + 打开子界面高亮 + 居中固定文字 */
    private void drawBottomButton(GuiGraphics guiGraphics, int x, int y, String text, boolean active) {
        // ★ 2026-09-22 方案 B：框 / 字 / 命中判定共用同一个 AutoBox
        AutoBox box = new AutoBox(x, y, bottomBtnW(), BOTTOM_BTN_H, text);
        boolean hovered = box.contains(lastMouseX, lastMouseY);
        int bg = active ? 0xFF2A6A8A : (hovered ? 0xFF3A6EA5 : 0xFF24476E);
        int border = active ? 0xFF66CCFF : (hovered ? 0xFFB0D8FF : 0xFF87CEEB);
        drawAutoBoxRect(guiGraphics, box, bg, border, active ? 0xFF66CCFF : 0xFF87CEEB, 4);
    }

    /** 右下角功能按钮（★ 2026-09-21：设置 + HUD调整 + 属性面板，统一风格） */
    private void renderBottomButtons(GuiGraphics guiGraphics) {
        boolean panelOpen = activeSubScreen instanceof AttributePanelSubScreen;
        boolean hudOpen = activeSubScreen instanceof HudAdjustSubScreen;
        boolean settingsOpen = activeSubScreen instanceof SettingsSubScreen;
        drawBottomButton(guiGraphics, settingsBtnX(), settingsBtnY(), t("settings_btn"), settingsOpen);
        drawBottomButton(guiGraphics, hudBtnX(), hudBtnY(), t("hud_adjust"), hudOpen);
        drawBottomButton(guiGraphics, panelToggleX(), panelToggleY(), t("panel_btn_open"), panelOpen);
    }


    /**
     * 标题行（分区框 1 内）：只剩【技能点】一行，★ <b>左对齐</b>，固定不滚动。
     *
     * <p>⚠️ 2026-09-19 L4 分区重构：<b>去掉了原先那个独立的圆角小框</b>——
     * 用户要求标题行、类别行、技能行同属 L4 区域，由三个分区框统一框起来，
     * 标题行只是框内的一行普通文字（不再自带边框/底色）。
     *
     * <p>已删除（用户要求）：标题「子枫的百宝箱」、光环开关状态、目标模式、快捷键提示行、未绑定警告行。
     */
    private void renderHeaderInfo(GuiGraphics guiGraphics) {
        refreshHeaderCache();
        int[] b = cachedHeaderBounds;
        int textY = titleFrameTop() + FRAME_LINE
                + (titleFrameH() - FRAME_LINE * 2 - font.lineHeight) / 2;
        drawCrispString(guiGraphics, cachedHeaderText, b[0], textY, 0xFF55FF55, false);
        // ★ 2026-09-20：标题行右侧原先空旷 → 放搜索框
        renderSearchBox(guiGraphics);
    }

    /** 悬停提示行（文本 + 颜色 + 字号倍率） */
    private record TooltipLine(String text, int color, float scale) {
    }

    /**
     * 构建技能悬停提示行列表：标题（类型色大字号）→ 描述 → 消耗（金）→ 模组缺失红字 → 前置状态 → 操作提示。
     * 不同类型配色：基础=天蓝 / 增幅=橙 / 终极=红 / 光环=紫 / 魔法=青绿（与列标题一致）。
     * 纯数据构建（无绘制），供预计算 tooltip 边界与绘制共用。
     */
    /**
     * 技能所属类别的显示名（★ 2026-09-20 新增，供 tooltip 前置需求里标注分类）。
     *
     * <p>返回形如 {@code "（基础属性）"} 的短标记；查不到类别时返回空串（不显示）。
     * <p>文案复用 {@link #CATEGORY_TITLE_KEYS} 那 10 个 key（与顶部类别按钮同一套，天然中英双语）。
     */
    private static String categoryHintOf(String skillId) {
        int ci = categoryIndexOf(skillId);
        return ci < 0 ? "" : "（" + t(CATEGORY_TITLE_KEYS[ci]) + "）";   // t() 带翻译缓存
    }

    private java.util.List<TooltipLine> buildTooltipLines(SkillButton button) {
        Skills.SkillType type = Skills.getType(button.skillId());
        String skillId = button.skillId();
        int points = learnedSkills.getOrDefault(skillId, 0);

        // ===== 木棍工具占位（2026-09-08）：专属 tooltip——不算技能不显示学习/消耗 =====
        if (Skills.isStickTool(skillId)) {
            boolean toolOn = org.zifeng.skilltree.client.ModKeyBindingEvents.isStickToolOnClient();
            int toolMode = org.zifeng.skilltree.client.ModKeyBindingEvents.getStickToolModeClient();
            boolean magnet = org.zifeng.skilltree.client.ModKeyBindingEvents.isMagnetLearnedClientOnly();
            boolean bindSkill = org.zifeng.skilltree.client.ModKeyBindingEvents.hasAnyStickToolSkillClient();
            java.util.List<TooltipLine> lines = new java.util.ArrayList<>();
            lines.add(new TooltipLine("[" + t("type_tool") + "] " + Skills.getDisplayNameComponent(skillId).getString(),
                    0xFFC8A87C, 1.15F));
            lines.add(new TooltipLine("———————————————————", 0xFF555555, 1.0F));
            for (String line : Skills.getDescriptionComponent(skillId).getString().split("\\n")) {
                lines.add(new TooltipLine(line, 0xFFDDDDDD, 1.0F));
            }
            // 已学功能模块（决定各模式手势是否可用；6 模式按解锁技能勾选）
            lines.add(new TooltipLine(t("stick_tool_modules"), 0xFF888888, 1.0F));
            for (int m = 0; m < Skills.stickModeCount(); m++) {
                if (m == Skills.STICK_MODE_BIND) { // 绑定模块=两绑技能
                    String label = "  " + (bindSkill ? "✓ " : "✗ ") + Component.translatable(
                            "skill.zifeng_s_custom_skill_tree.aura_loot_vacuum.name").getString() + " / "
                            + Component.translatable("skill.zifeng_s_custom_skill_tree.container_haul.name").getString()
                            + " (" + t("stick_tool_mod_bind") + ")";
                    lines.add(new TooltipLine(label, bindSkill ? 0xFF55FF55 : 0xFF777777, 1.0F));
                    continue;
                }
                if (m == Skills.STICK_MODE_RANGE) { // RANGE=磁铁
                    String label = "  " + (magnet ? "✓ " : "✗ ") + Component.translatable(
                            "skill.zifeng_s_custom_skill_tree.aura_magnet.name").getString()
                            + " (" + t("stick_tool_mod_range") + ")";
                    lines.add(new TooltipLine(label, magnet ? 0xFF55FF55 : 0xFF777777, 1.0F));
                    continue;
                }
                // 区块技能模式（放置/挖掘/攻击/防护）：勾选 = 对应技能已学
                String zSkill = Skills.skillForStickMode(m);
                boolean zLearned = zSkill != null && org.zifeng.skilltree.client.ModKeyBindingEvents
                        .isSkillLearnedAnywhere(zSkill);
                String zLabel = "  " + (zLearned ? "✓ " : "✗ ") + Component.translatable(
                        "skill.zifeng_s_custom_skill_tree." + zSkill + ".name").getString()
                        + " (" + t(org.zifeng.skilltree.client.StickToolModes.modLang(m)) + ")";
                lines.add(new TooltipLine(zLabel, zLearned ? 0xFF55FF55 : 0xFF777777, 1.0F));
            }
            lines.add(new TooltipLine(" ", 0xFF000000, 0.6F));
            lines.add(new TooltipLine(t("stick_tool_state") + "：" + t(toolOn ? "stick_tool_on" : "stick_tool_off"),
                    toolOn ? 0xFF55FF55 : 0xFFAAAAAA, 0.9F));
            lines.add(new TooltipLine(t("stick_tool_mode") + "："
                            + t(org.zifeng.skilltree.client.StickToolModes.modeLang(toolMode)),
                    org.zifeng.skilltree.client.StickToolModes.colorOfMode(toolMode), 0.9F));
            lines.add(new TooltipLine(" ", 0xFF000000, 0.6F));
            lines.add(new TooltipLine(t("hint_tool_switch") + "   " + t("hint_tool_mode"), 0xFFD7A55A, 0.9F));
            return lines;
        }
        boolean enabled = toggles.getOrDefault(skillId, Boolean.TRUE);
        java.util.List<TooltipLine> lines = new java.util.ArrayList<>();

        int titleColor = switch (type) {
            case BASE -> 0xFF87CEEB;
            case AMPLIFY -> 0xFFFFAA55;
            case ULTIMATE -> 0xFFFF5555;
            case SPECIAL -> 0xFFD7A55A;
            case AURA -> 0xFFAA55FF;
            case GLOBAL -> 0xFF66CCFF; // 寰宇法则：天蓝（世界/全局主题）
            case MAGIC -> 0xFF55FFAA;
            case MACHINE -> 0xFFD7D7D7;
            case GIFT -> 0xFFE0B6C8; // 子枫的馈赠：柔和藕粉
        };
        String typeTag = switch (type) {
            case BASE -> "[" + Component.translatable("ui.zifeng_s_custom_skill_tree.type_base").getString() + "]";
            case AMPLIFY -> "[" + Component.translatable("ui.zifeng_s_custom_skill_tree.type_amplify").getString() + "]";
            case ULTIMATE -> "[" + Component.translatable("ui.zifeng_s_custom_skill_tree.type_ultimate").getString() + "]";
            case SPECIAL -> "[" + Component.translatable("ui.zifeng_s_custom_skill_tree.type_special").getString() + "]";
            case AURA -> "[" + Component.translatable("ui.zifeng_s_custom_skill_tree.type_aura").getString() + "]";
            case GLOBAL -> "[" + Component.translatable("ui.zifeng_s_custom_skill_tree.type_global").getString() + "]";
            case MAGIC -> "[" + Component.translatable("ui.zifeng_s_custom_skill_tree.type_magic").getString() + "]";
            case MACHINE -> "[" + Component.translatable("ui.zifeng_s_custom_skill_tree.type_machine").getString() + "]";
            case GIFT -> "[" + Component.translatable("ui.zifeng_s_custom_skill_tree.type_gift").getString() + "]";
        };
        // 1. 标题行（大字号 + 类型色）
        lines.add(new TooltipLine(typeTag + " " + Skills.getDisplayNameComponent(skillId).getString(), titleColor, 1.15F));
        lines.add(new TooltipLine("———————————————————", 0xFF555555, 1.0F));

        // 2. 描述正文（白灰，正常字号）
        for (String line : Skills.getDescriptionComponent(skillId).getString().split("\\n")) {
            lines.add(new TooltipLine(line, 0xFFDDDDDD, 1.0F));
        }
        // 2.4 机械共鸣系列：弱兼容红字警告（2026-09-05 用户需求）
        //  共鸣技能靠"模拟玩家机器触发游戏事件"的弱兼容机制生效，非强兼容——不保证所有机器生效
        //  ⚠️ 2026-09-08：木棍工具区块技能（放置/挖掘/攻击/防护）不是机器兼容类，不显示该警告
        if (Skills.MACHINE_SKILLS.contains(skillId) && !Skills.isStickZoneSkill(skillId)) {
            for (String line : t("machine_weak_warn").split("\\n")) {
                lines.add(new TooltipLine(line, 0xFFFF5555, 0.9F));
            }
        }
        // 2.45 选区挖掘：红字警示——会一并清除基岩与流体（2026-09-15 用户需求）
        //  基岩走「直接清除」分支（同流体），无掉落；提醒玩家避免误删重大建筑基座
        if (Skills.MACHINE_ZONE_EXCAVATE.equals(skillId)) {
            for (String line : t("warn_zone_excavate").split("\\n")) {
                lines.add(new TooltipLine(line, 0xFFFF5555, 0.9F));
            }
        }
        // 2.5 容器绑定技能（子枫挪移术/子枫的搬运术）：显示当前绑定目标（2026-08-24 需求；2026-09-07 扩展两技能共用绑定）
        if (Skills.isContainerBindSkill(skillId)) {
            String bind = org.zifeng.skilltree.client.ModKeyBindingEvents.getLootVacuumBindClient();
            if (bind == null) {
                lines.add(new TooltipLine("§7" + t("tip_no_container"), 0xFF888888, 1.0F));
            } else {
                // ⚠️ 2026-09-12（1.4.1）：绑定的是 AE2 无线访问点时用紫色 + [AE] 前缀，与普通容器的悬色一眼区分
                boolean aeBind = org.zifeng.skilltree.client.ModKeyBindingEvents.getBindTypeClient()
                        == org.zifeng.skilltree.compat.Ae2StorageCompat.TYPE_AE;
                lines.add(new TooltipLine(t("tip_container") + "：" + (aeBind ? "[AE] " : "") + bind,
                        aeBind ? 0xFFAA55FF : 0xFF55FF55, 1.0F));
            }
        }

        // 2.5b 寰宇法则（全局技能）：显眼的服务器当前状态提示（2026-08-27 需求）
        if (Skills.isGlobalSkill(skillId)) {
            // 晴空环：当前锁定天气 + 按键切换提示
            // ⚠️ 2026-08-28 修复：显示【全局天气模式】而非 gamerule 锁定状态——
            //   currentWeatherMode 是服务器全局变量（最后切换者生效），与 gamerule 可能不一致
            //   （有人开晴空环但另一人关掉恢复 gamerule 后，模式仍在）。
            if (Skills.AURA_WEATHER.equals(skillId)) {
                boolean locked = org.zifeng.skilltree.client.ClientGlobalState.isWeatherLocked();
                String weather = weatherTextOf();
                // 天气模式已同步（>=0）且非未知 → 显示"已锁定为 X"；否则按 gamerule 显示
                boolean modeKnown = org.zifeng.skilltree.client.ClientGlobalState.getWeatherMode() >= 0;
                lines.add(new TooltipLine(t("tip_server") + (modeKnown ? t("tip_locked_as") + " " + weather
                                : (locked ? t("tip_weather_locked") : t("tip_weather_normal"))),
                        (modeKnown || locked) ? 0xFFFFD700 : 0xFF888888, 1.0F));
                if (modeKnown || locked) {
                    lines.add(new TooltipLine("   " + t("tip_weather_switch"), 0xFF66CCFF, 0.9F));
                }
            }
            // 时之环：当前时间是否锁定
            if (Skills.AURA_TIME.equals(skillId)) {
                boolean locked = org.zifeng.skilltree.client.ClientGlobalState.isTimeLocked();
                lines.add(new TooltipLine(t("tip_server") + (locked ? t("tip_time_locked") : t("tip_time_normal")),
                        locked ? 0xFFFFD700 : 0xFF888888, 1.0F));
            }
            // 无限回路：服务器 AE 频道模式
            if (Skills.AE_INFINITE_CHANNEL.equals(skillId)) {
                String aeMode = org.zifeng.skilltree.client.ClientGlobalState.getAeChannelModeText();
                lines.add(new TooltipLine(t("tip_server") + " AE: " + aeMode,
                        aeMode.startsWith(t("weather_unknown")) ? 0xFF888888 : 0xFFFFD700, 1.0F));
            }
        }

        // 3. 消耗信息：2026-09-19 策划更改（用户）——「悬停说明不用包含技能树等级和技能点消耗相关的内容」
        //    所以这里不再拼接「[已满级 X/Y 级]」「[下次消耗 X 点]」。
        //    消耗已由行内的「+50」列与进度条承担，不需要在悬停说明里重复。

        // 2.6 子枫的馈赠：显示激活条件（2026-08-25）
        if (Skills.isGiftSkill(skillId)) {
            if (Skills.GIFT_TIME_BAPTISM.equals(skillId) || Skills.GIFT_TIME_STORM.equals(skillId)
                    || Skills.GIFT_TIME_FLOOD.equals(skillId)) {
                long need = Skills.getGiftRequirementTicks(skillId);
                lines.add(new TooltipLine(t("tip_require_time") + (need / 72000) + " " + t("unit_hour"), 0xFFAAFF55, 0.9F));
            } else if (Skills.isGiftDistanceBaptism(skillId)) {
                // ★ 2026-09-22：击杀馈赠原本落到 unit_meter（米）——需求实际是【击杀数】，
                //   与属性面板（unit_kill =「杀」）口径不一致（用户反馈「提示单位是米」）。补 KILL 分支。
                String unit = Skills.GIFT_KILL_BAPTISM.equals(skillId) ? t("unit_kill")
                        : (Skills.GIFT_MINE_BAPTISM.equals(skillId) ? t("unit_blocks") : t("unit_meter"));
                // 2026-09-14：改为「当前等级需求 + 下一级需求」，不再写死 2/3 级（洗礼已扩到 9 级）
                int curLv = Math.max(1, Math.min(Skills.getGiftMaxPoints(skillId), points));
                lines.add(new TooltipLine(t("tip_req") + " " + Skills.getGiftDistanceRequirement(skillId, curLv) + unit + " / 1", 0xFFAAFF55, 0.9F));
                if (curLv < Skills.getGiftMaxPoints(skillId)) {
                    lines.add(new TooltipLine("   " + t("tip_next_req") + " " + Skills.getGiftDistanceRequirement(skillId, curLv + 1) + unit, 0xFF88AA88, 0.9F));
                }
            }
        }

        // 4. 模组缺失红字（MAGIC 且对应模组未装）
        String missingMod = missingModName(skillId);
        if (missingMod != null) {
            lines.add(new TooltipLine(t("tip_no_mod") + missingMod, 0xFFFF5555, 0.95F));
        }

        // 5. 前置需求（金色标题 + 绿/红状态 + ★ 所属类别）
        //   ★ 2026-09-20 用户要求：「前置里没有增加对应技能的前置分类提示」——
        //     现在每个前置技能后面标出它属于哪个类别（如「（基础属性）」），
        //     因为前置可能跨类别（如采掘熟稔在「基础属性」，却可能是「机械共鸣」的前置），
        //     玩家看到名字后不用再去猜/翻哪个分类。
        //   类别文案复用 CATEGORY_TITLE_KEYS 那 10 个 key（已有中英双语，无需新增语言条目）。
        java.util.List<Map.Entry<String, Integer>> prereqs = Skills.getPrerequisites(skillId);
        if (!prereqs.isEmpty()) {
            lines.add(new TooltipLine(" ", 0xFF000000, 0.6F));
            lines.add(new TooltipLine(t("tip_prereq"), 0xFFFFD700, 0.9F));
            for (Map.Entry<String, Integer> entry : prereqs) {
                String required = entry.getKey();
                int need = entry.getValue();
                int have = learnedSkills.getOrDefault(required, 0);
                boolean met = have >= need;
                lines.add(new TooltipLine((met ? "✓ " : "✗ ")
                        + Skills.getDisplayNameComponent(required).getString()
                        + categoryHintOf(required) + " " + have + "/" + need,
                        met ? 0xFF55FF55 : 0xFFFF5555, 0.9F));
            }
        }

        // 6. 底部操作/状态提示（小字号灰）
        lines.add(new TooltipLine(" ", 0xFF000000, 0.6F));
        lines.add(new TooltipLine(buildStatusText(skillId, type, points, enabled), 0xFF888888, 0.8F));
        return lines;
    }

    /** 计算 tooltip 依赖的数据指纹，避免状态变化后继续复用旧提示。 */
    private int tooltipFingerprint(SkillButton button) {
        String skillId = button.skillId();
        int points = learnedSkills.getOrDefault(skillId, 0);
        int activeLevel = activeLevels.getOrDefault(skillId, points);
        boolean isTool = Skills.isStickTool(skillId);
        boolean enabled = isTool
                ? org.zifeng.skilltree.client.ModKeyBindingEvents.isStickToolOnClient()
                : toggles.getOrDefault(skillId, Boolean.TRUE);
        int toolMode = isTool
                ? org.zifeng.skilltree.client.ModKeyBindingEvents.getStickToolModeClient() : 0;
        int h = rowFingerprint(textVersion, points, activeLevel, enabled, toolMode, (int) (skillPoints * 10));
        h = h * 31 + java.util.Objects.hashCode(org.zifeng.skilltree.client.SkillKeyBinds.getKey(skillId));
        h = h * 31 + java.util.Objects.hashCode(org.zifeng.skilltree.client.SkillKeyBinds.getLevelKey(skillId));
        h = h * 31 + java.util.Objects.hashCode(org.zifeng.skilltree.client.SkillKeyBinds.getTriggerKey(skillId));
        h = h * 31 + auraTargetModes.getOrDefault(skillId, 0);
        h = h * 31 + java.util.Objects.hashCode(
                org.zifeng.skilltree.client.ModKeyBindingEvents.getLootVacuumBindClient());
        h = h * 31 + org.zifeng.skilltree.client.ClientGlobalState.getWeatherMode();
        h = h * 31 + (org.zifeng.skilltree.client.ClientGlobalState.isWeatherLocked() ? 1 : 0);
        h = h * 31 + (org.zifeng.skilltree.client.ClientGlobalState.isTimeLocked() ? 1 : 0);
        h = h * 31 + java.util.Objects.hashCode(
                org.zifeng.skilltree.client.ClientGlobalState.getAeChannelModeText());
        if (isTool) {
            h = h * 31 + (org.zifeng.skilltree.client.ModKeyBindingEvents.isMagnetLearnedClientOnly() ? 1 : 0);
            h = h * 31 + (org.zifeng.skilltree.client.ModKeyBindingEvents.hasAnyStickToolSkillClient() ? 1 : 0);
        }
        return h;
    }

    /** 绘制技能悬停提示（2026-09-15：复用 updateActiveTooltipBounds 已构建的行列表，避免每帧重复构建） */
    private void renderSkillTooltip(GuiGraphics guiGraphics, SkillButton button, int mouseX, int mouseY) {
        renderTooltipLines(guiGraphics, activeTooltipLines != null ? activeTooltipLines : buildTooltipLines(button), mouseX, mouseY);
    }

    /**
     * 底部状态行：只保留<b>操作提示</b>。
     * <p>⚠️ 2026-09-19 策划更改（用户）：悬停说明不含「技能树等级」与「技能点消耗」相关内容，
     * 故原「已学 X/Y 级 ·」前缀与「点数不足（需 X 点）」整句已移除。
     */
    private String buildStatusText(String skillId, Skills.SkillType type, int points, boolean enabled) {
        if (points > 0) {
            return t("status_rbtn_toggle") + " · " + t("status_scroll");
        }
        if (Skills.isGiftSkill(skillId)) {
            // 时间系列：游戏时长门槛（不是技能点消耗）；洗礼/增幅 → 单纯操作提示
            if (Skills.GIFT_TIME_BAPTISM.equals(skillId) || Skills.GIFT_TIME_STORM.equals(skillId) || Skills.GIFT_TIME_FLOOD.equals(skillId)) {
                long need = Skills.getGiftRequirementTicks(skillId);
                return t("status_need_time") + (need / 72000) + " " + t("unit_hour") + "（" + t("status_lbtn_activate") + "）";
            }
        }
        return t("status_lbtn_learn") + " · " + t("status_rbtn_toggle");
    }

    /**
     * 计算 tooltip 布局 [x, y, w, h]（屏幕坐标，含边界钳制）。纯计算，供预计算与绘制共用。
     * w/h 为内容尺寸（不含 padding），x/y 为背景左上角（含 padding）。
     */
    private int[] computeTooltipLayout(java.util.List<TooltipLine> lines, int mouseX, int mouseY) {
        int padX = 6, padY = 4;
        int maxWidth = 0;
        int totalHeight = 0;
        for (TooltipLine line : lines) {
            // ★ 2026-09-22：宽/高必须按【设计空间】量 —— 文字是不缩放的屏幕像素绘制，
            //   容器却会乘 r，不换算则 r<1 时文字比框宽 1/r 倍（用户报的溢出）
            int w = lw(line.text(), line.scale());
            maxWidth = Math.max(maxWidth, w);
            totalHeight += lh(line.scale());
        }
        int x = mouseX + 12;
        int y = mouseY - 12;
        // 屏幕边界钳制（留出分区框边距，避免被框线盖住）
        int m = FRAME_MARGIN + 2;
        if (x + maxWidth + padX * 2 > width - m) {
            x = mouseX - maxWidth - padX * 2 - 4;
        }
        if (x < m) {
            x = m;
        }
        if (y + totalHeight + padY * 2 > height - m) {
            y = height - totalHeight - padY * 2 - 2;
        }
        if (y < m) {
            y = m;
        }
        return new int[]{x, y, maxWidth, totalHeight};
    }

    /** 绘制自定义悬停提示：半透明背景 + 边框 + 每行独立字号/颜色（屏幕边界自动钳制） */
    private void renderTooltipLines(GuiGraphics guiGraphics, java.util.List<TooltipLine> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        int padX = 6, padY = 4;
        int[] layout = computeTooltipLayout(lines, mouseX, mouseY);
        int x = layout[0], y = layout[1], maxWidth = layout[2], totalHeight = layout[3];
        // 半透明背景 + 边框（guiOverlay：盖住第四图层按钮，但先于边框/面板提交 → 被它们盖住）
        net.minecraft.client.renderer.RenderType overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        guiGraphics.fill(overlay, x, y, x + maxWidth + padX * 2, y + totalHeight + padY * 2, 0xF0100010);
        int border = 0xFF2E2E5E;
        guiGraphics.fill(overlay, x, y, x + maxWidth + padX * 2, y + 1, border);
        guiGraphics.fill(overlay, x, y + totalHeight + padY * 2 - 1, x + maxWidth + padX * 2, y + totalHeight + padY * 2, border);
        guiGraphics.fill(overlay, x, y, x + 1, y + totalHeight + padY * 2, border);
        guiGraphics.fill(overlay, x + maxWidth + padX * 2 - 1, y, x + maxWidth + padX * 2, y + totalHeight + padY * 2, border);
        // 逐行绘制（独立字号/颜色）
        int curY = y + padY;
        for (TooltipLine line : lines) {
            float s = line.scale();
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x + padX, curY, 0);
            drawCrispScaledString(guiGraphics, line.text(), 0, 0, line.color(), s);
            guiGraphics.pose().popPose();
            curY += lh(s);
        }
    }

    /** 快捷键框的原版 tooltip：文字走清晰绘制，避免非整数 pose 缩放把提示也糊掉。 */
    private void renderCrispTooltip(GuiGraphics guiGraphics, java.util.List<Component> lines, int mouseX, int mouseY) {
        java.util.List<TooltipLine> crisp = new java.util.ArrayList<>(lines.size());
        for (Component line : lines) {
            crisp.add(new TooltipLine(line.getString(), 0xFFFFFFFF, 1.0F));
        }
        renderTooltipLines(guiGraphics, crisp, mouseX, mouseY);
    }

    /**
     * 鼠标是否在第一图层任何 UI 元素上（tooltip 穿透检查 + 图标跳过共用）：
     * 属性面板区域 / 顶部标题区 / 底部提示条 / 右下角面板开关按钮。
     */
    private boolean isOverUI(double mouseX, double mouseY) {
        // 子界面打开：仅面板内区域视为 UI（tooltip 不透传到面板上）；面板外正常
        if (activeSubScreen != null && activeSubScreen.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        if (isMouseOverPanel(mouseX, mouseY)) {
            return true;
        }
        if (isMouseOverHeader(mouseX, mouseY)) {
            return true;
        }
        if (isOverSearchBox(mouseX, mouseY)) {
            return true; // ★ 2026-09-20：搜索框也是 UI（点击/悬停不透传到技能列表）
        }
        // 右下角功能按钮（设置 + HUD调整 + 属性面板，2026-09-01 统一风格；设置按钮 2026-09-21 新增）
        if (mouseX >= panelToggleX() && mouseX <= panelToggleX() + bottomBtnW()
                && mouseY >= panelToggleY() && mouseY <= panelToggleY() + BOTTOM_BTN_H) {
            return true;
        }
        if (isHudPanelHit(mouseX, mouseY)) {
            return true;
        }
        if (isSettingsPanelHit(mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    /** 设置按钮区域命中（HUD调整按钮左边，平行同高；★ 2026-09-21） */
    private boolean isSettingsPanelHit(double mouseX, double mouseY) {
        return mouseX >= settingsBtnX() && mouseX <= settingsBtnX() + bottomBtnW()
                && mouseY >= settingsBtnY() && mouseY <= settingsBtnY() + BOTTOM_BTN_H;
    }

    /** HUD 调整按钮区域命中（属性面板按钮左边，平行同高） */
    private boolean isHudPanelHit(double mouseX, double mouseY) {
        return mouseX >= hudBtnX() && mouseX <= hudBtnX() + bottomBtnW()
                && mouseY >= hudBtnY() && mouseY <= hudBtnY() + BOTTOM_BTN_H;
    }

    /** 图标被 UI/tooltip 覆盖时跳过的面积比例阈值（图标被遮 ≥15% 才跳过渲染） */
    private static final float ICON_OVERLAP_SKIP_RATIO = 0.15f;

    /**
     * 鼠标是否在某一行的【名称区】上。
     * <p>⚠️ 2026-09-19 用户要求：技能悬停说明<b>只在鼠标位于技能名称上时</b>弹出（不再整行都弹），
     * 这样鼠标在进度条/消耗/按键框上时不会弹出大块提示挡住界面。
     */
    private boolean overNameArea(double mouseX, double mouseY, SkillButton button) {
        if (!isMouseOverSkillList(mouseX, mouseY)) {
            return false;
        }
        double lx = toPanelX(mouseX);
        double ly = toPanelY(mouseY);
        return lx >= button.x() + R_NAME_X && lx <= button.x() + R_NAME_X + R_NAME_W
                && ly >= button.y() && ly <= button.y() + BUTTON_HEIGHT;
    }

    /**
     * 通过统一行高直接定位鼠标所在技能行。
     *
     * <p>技能列表是单列、固定行距，命中测试无需每帧扫描全部按钮；
     * 这套计算使用设计空间坐标，因此对所有 GUI Scale 和自定义缩放一致。
     */
    private SkillButton findHoveredSkillButton(double mouseX, double mouseY) {
        if (!isMouseOverSkillList(mouseX, mouseY)) {
            return null;
        }
        double localY = toPanelY(mouseY);
        int rowStep = BUTTON_HEIGHT + VERTICAL_SPACING;
        int index = (int) Math.floor(localY / Math.max(1, rowStep));
        if (index < 0 || index >= buttons.size()) {
            return null;
        }
        SkillButton button = buttons.get(index);
        return button.isHovered(mouseX, mouseY, this) ? button : null;
    }

    /**
     * 预计算当前悬停按钮的 tooltip 边界 [x, y, w, h]（屏幕坐标，含钳制）。
     * 在第四图层渲染前调用，供图标跳过判定：被 tooltip 覆盖的图标不渲染（tooltip 背景半透明，否则图标会透出混合）。
     */
    private void updateActiveTooltipBounds(int mouseX, int mouseY) {
        activeTooltipBounds = null;
        activeTooltipLines = null;
        SkillButton button = findHoveredSkillButton(mouseX, mouseY);
        if (button == null || !overNameArea(mouseX, mouseY, button)) {
            return;
        }
        int fingerprint = tooltipFingerprint(button);
        if (!button.skillId().equals(tooltipCacheSkillId)
                || fingerprint != tooltipCacheFingerprint
                || tooltipCacheLines == null) {
            tooltipCacheSkillId = button.skillId();
            tooltipCacheFingerprint = fingerprint;
            tooltipCacheLines = buildTooltipLines(button);
        }
        activeTooltipLines = tooltipCacheLines;
        activeTooltipBounds = computeTooltipLayout(activeTooltipLines, mouseX, mouseY);
    }

    /**
     * 技能按钮的图标（左上角 16×16 区域）是否应跳过渲染。
     * 判断依据：图标 AABB 与【第一图层 UI 元素】或【当前 tooltip】的相交面积占图标面积比例 ≥ 阈值。
     * 原因：
     *   - renderItem 用物品渲染管线（gui() 带深度），而面板/tooltip 背景是半透明 guiOverlay → 不跳过的话图标会从半透明背景透出（混合）；
     *   - 只按面积比例判定：边缘轻微重叠（<15%）仍渲染图标，不会"碰一点就消失"；大部分被遮才跳过，杜绝透出混合。
     */
    private boolean isIconUnderUI(SkillButton button) {
        float ox = (float) rowLeft();
        float oy = (float) (listTop() - scrollY);
        // 图标局部 AABB（图标 16×16，起点 x+3,y+4）
        float ix1 = ox + button.x() + R_ICON_X;
        float iy1 = oy + button.y() + (BUTTON_HEIGHT - 16) / 2f;
        float ix2 = ix1 + 16;
        float iy2 = iy1 + 16;
        // 1. 当前打开的子界面（不透明面板覆盖 → 被覆盖图标必须跳过）
        if (activeSubScreen != null && activeSubScreen.isMouseOver(ix1, iy1)) {
            return true;
        }
        // 2. 底部提示条
        if (Config.PANEL_VISIBLE.get()) {
            if (overlapRatio(ix1, iy1, ix2, iy2, width - PANEL_WIDTH - 12, height - 32, width - 6, height) >= ICON_OVERLAP_SKIP_RATIO) return true;
        }
        // 3. 顶部标题区
        int[] hb = headerBounds();
        if (overlapRatio(ix1, iy1, ix2, iy2, hb[0], hb[1], hb[2], hb[3]) >= ICON_OVERLAP_SKIP_RATIO) return true;
        // 4. 右下角三个功能按钮（设置 + 属性面板 + HUD调整，2026-09-01 / 设置 2026-09-21）
        if (overlapRatio(ix1, iy1, ix2, iy2, panelToggleX(), panelToggleY(), panelToggleX() + bottomBtnW(), panelToggleY() + BOTTOM_BTN_H) >= ICON_OVERLAP_SKIP_RATIO) return true;
        if (overlapRatio(ix1, iy1, ix2, iy2, hudBtnX(), hudBtnY(), hudBtnX() + bottomBtnW(), hudBtnY() + BOTTOM_BTN_H) >= ICON_OVERLAP_SKIP_RATIO) return true;
        if (overlapRatio(ix1, iy1, ix2, iy2, settingsBtnX(), settingsBtnY(), settingsBtnX() + bottomBtnW(), settingsBtnY() + BOTTOM_BTN_H) >= ICON_OVERLAP_SKIP_RATIO) return true;
        // 5. 当前 tooltip（背景半透明，被覆盖图标必须跳过）
        if (activeTooltipBounds != null) {
            int[] t = activeTooltipBounds;
            if (overlapRatio(ix1, iy1, ix2, iy2, t[0], t[1], t[0] + t[2], t[1] + t[3]) >= ICON_OVERLAP_SKIP_RATIO) return true;
        }
        return false;
    }

    /** 两个 AABB 的相交面积占图标面积的比例（0~1）；无相交返回 0 */
    private static float overlapRatio(float ax1, float ay1, float ax2, float ay2,
                                      double bx1, double by1, double bx2, double by2) {
        float ow = Math.min(ax2, (float) bx2) - Math.max(ax1, (float) bx1);
        float oh = Math.min(ay2, (float) by2) - Math.max(ay1, (float) by1);
        if (ow <= 0 || oh <= 0) {
            return 0;
        }
        float iconArea = (ax2 - ax1) * (ay2 - ay1);
        if (iconArea <= 0) {
            return 0;
        }
        return Math.min(1.0f, (ow * oh) / iconArea);
    }

    private void renderSkillButton(GuiGraphics guiGraphics, SkillButton button,
                                   double panelMouseX, double panelMouseY, boolean mouseOverList,
                                   Map<String, com.mojang.blaze3d.platform.InputConstants.Key> keyBinds,
                                   Map<String, com.mojang.blaze3d.platform.InputConstants.Key> levelBinds,
                                   Map<String, com.mojang.blaze3d.platform.InputConstants.Key> triggerBinds) {
        final String skillId = button.skillId();
        final int points = learnedSkills.getOrDefault(skillId, 0);
        final boolean isTool = Skills.isStickTool(skillId); // 木棍工具占位（不算技能）
        // 工具卡状态 = 工具层总开关（服务端校准缓存）；其余技能 = toggles
        final boolean enabled = isTool ? org.zifeng.skilltree.client.ModKeyBindingEvents.isStickToolOnClient()
                : toggles.getOrDefault(skillId, Boolean.TRUE);
        // 木棍工具：当前模式（BIND 绑定 ↔ RANGE 范围）颜色区分
        final int toolMode = isTool ? org.zifeng.skilltree.client.ModKeyBindingEvents.getStickToolModeClient() : 0;
        final int activeLevel = activeLevels.getOrDefault(skillId, points);

        // ---------- 静态视觉：查缓存（数据不变就只查表，不再重算）----------
        // ★ 2026-09-21：属性区宽随【收缩阶梯】变化 → 必须进指纹，
        //   否则缓存里按旧宽度裁切的属性文本不会更新（会改用新位置但文本仍是旧的）。
        final int fp = rowFingerprint(textVersion, points, activeLevel, enabled, toolMode, (int) (skillPoints * 10))
                * 31 + attrAreaW();
        RowVisual v = rowVisualCache.get(skillId);
        if (v == null || v.fp() != fp) {
            v = buildRowVisual(skillId, fp, points, activeLevel, enabled, toolMode, isTool);
            rowVisualCache.put(skillId, v);
        }
        // ---------- 整行 = 一张【圆角技能贴片】（内容区 │ Q │ E │ R）----------
        //   用户要求：① Q/E/R 不要独立的，和技能为一张大贴片；
        //            ② 不同类别颜色/边框不一样；③ 直角要改圆角。
        final int x0 = button.x();
        final int y0 = button.y();
        final int h = BUTTON_HEIGHT;
        final int cw = contentW();
        final int total = cw + KEY_BOX_WIDTH + KEY2_BOX_WIDTH + KEY3_BOX_WIDTH;
        final boolean onRow = mouseOverList && panelMouseY >= y0 && panelMouseY < y0 + h;
        final boolean hovered = onRow && panelMouseX >= x0 && panelMouseX <= x0 + total;

        // ★ 2026-09-20 搜索结果定位项：高亮边框（用户选的方案 A）
        final boolean searchHit = isSearchHit(skillId);
        final int bg = hovered ? v.bgHover() : v.bg();
        final int borderColor = searchHit ? 0xFFFFFFFF
                : (hovered ? v.borderHover() : v.border());

        // ① 整张贴片底色（圆角；四段共用一个底）
        fillRound(guiGraphics, x0, y0, x0 + total, y0 + h, R_ROW, bg);
        // 极淡顶部高光让贴片有层次，但保持像素 UI 的清晰边缘。
        guiGraphics.fill(x0 + R_ROW, y0 + 1, x0 + total - R_ROW, y0 + 2, 0x22FFFFFF);

        // ② 左侧类别色条（3px，顶部/底部跟着圆角内缩）—— 横向扫一眼就能分出类别
        final int accent = v.accent();   // ★ 按本技能取色（搜索结果跨类别）
        final int stripeColor = v.enabled() ? (v.learned() ? accent : (accent & 0x00FFFFFF) | 0x77000000)
                : 0xFF7A7A7A;
        // ★ 2026-09-20：搜索定位项 → 左侧色条加粗（3px → 5px）并提亮，配合白色边框形成"选中"观感
        if (searchHit) {
            fillRound(guiGraphics, x0 + 1, y0 + 1, x0 + 6, y0 + h - 1, 2, 0xFFFFFFFF);
        } else {
            fillRound(guiGraphics, x0 + 1, y0 + 1, x0 + 4, y0 + h - 1, 2, stripeColor);
        }

        // ③ 三个按键格（贴片【内部】的格，各自画一层底色，不画独立外框）
        final int qx = x0 + cw;
        final int ex = qx + KEY_BOX_WIDTH;
        final int rx = ex + KEY2_BOX_WIDTH;
        renderKeyCell(guiGraphics, skillId, qx, y0, KEY_BOX_WIDTH, h, onRow && panelMouseX >= qx && panelMouseX < ex,
                true, 1, keyBindSkillId, keyBindListening, keyBinds.get(skillId));
        renderKeyCell(guiGraphics, skillId, ex, y0, KEY2_BOX_WIDTH, h, onRow && panelMouseX >= ex && panelMouseX < rx,
                v.slot2Usable(), 2, levelKeyBindSkillId, levelKeyBindListening, levelBinds.get(skillId));
        renderKeyCell(guiGraphics, skillId, rx, y0, KEY3_BOX_WIDTH, h, onRow && panelMouseX >= rx && panelMouseX < x0 + total,
                v.slot3Usable(), 3, triggerKeyBindSkillId, triggerKeyBindListening, triggerBinds.get(skillId));

        // ④ 内部分格竖线（内容区│Q│E│R）—— ★ 改淡：Miuix 的分割线是「几乎看不见」的量级，
        //    太粗会把一张贴片切成三个小按钮
        guiGraphics.fill(qx, y0 + 3, qx + 1, y0 + h - 3, C_CELL_LINE);
        guiGraphics.fill(ex, y0 + 3, ex + 1, y0 + h - 3, C_CELL_LINE);
        guiGraphics.fill(rx, y0 + 3, rx + 1, y0 + h - 3, C_CELL_LINE);

        // ⑤ 整张贴片的圆角外边框（1px；用类别色，把四段包成一个整体）
        strokeRound(guiGraphics, x0, y0, x0 + total, y0 + h, R_ROW, borderColor);

        // 图标是否会被第一图层 UI 或当前 tooltip 覆盖（面积比例 ≥ 阈值）→ 跳过 renderItem（半透明背景透出会混合）
        boolean iconOverlapped = isIconUnderUI(button);
        // 技能图标（行左侧 16×16，垂直居中；有自定义贴图用 blit，否则用原版物品图标）
        int iconX = x0 + R_ICON_X;
        int iconY = y0 + (h - 16) / 2;
        if (!iconOverlapped) {
            if (v.iconTex() != null) {
                // 自定义贴图（16x16 PNG，直接按资源路径 blit）
                guiGraphics.blit(v.iconTex(), iconX, iconY, 0, 0, 16, 16, 16, 16);
            } else {
                // ⚠️ ItemStack 已在 buildRowVisual 里建好并缓存 —— 原实现每帧对每行 new 一个（纯 GC 压力）
                guiGraphics.renderItem(v.iconStack(), iconX, iconY);
            }
            // 机械共鸣：图标外圈【钢灰机械边框】+ 右下角【螺丝角标】（与原技能区分，机械主题辨识度高）
            // 图标绘制区域 = iconX..iconX+16 × iconY..iconY+16，边框包在四周 1px
            if (v.type() == Skills.SkillType.MACHINE) {
                int ix = iconX - 1, iy = iconY - 1, iw = 18, ih = 18;
                int steel = enabled ? 0xFF9AA4AE : 0xFF5A5A5A; // 开启=钢灰亮边，关闭=暗灰
                guiGraphics.fill(ix, iy, ix + iw, iy + 1, steel);
                guiGraphics.fill(ix, iy + ih - 1, ix + iw, iy + ih, steel);
                guiGraphics.fill(ix, iy, ix + 1, iy + ih, steel);
                guiGraphics.fill(ix + iw - 1, iy, ix + iw, iy + ih, steel);
                // 四角铆钉（机械质感）
                guiGraphics.fill(ix, iy, ix + 2, iy + 2, 0xFFD0D5DA);
                guiGraphics.fill(ix + iw - 2, iy, ix + iw, iy + 2, 0xFFD0D5DA);
                guiGraphics.fill(ix, iy + ih - 2, ix + 2, iy + ih, 0xFFD0D5DA);
                guiGraphics.fill(ix + iw - 2, iy + ih - 2, ix + iw, iy + ih, 0xFFD0D5DA);
                // 右下角螺丝角标（4×4：钢灰螺丝头 + 十字高光）
                int sx = iconX + 13, sy = iconY + 13;
                guiGraphics.fill(sx, sy, sx + 4, sy + 4, 0xFF7A848E);   // 螺丝头
                guiGraphics.fill(sx + 1, sy + 1, sx + 3, sy + 3, 0xFFAEB6BE); // 内圈
                guiGraphics.fill(sx + 1, sy + 1, sx + 2, sy + 2, 0xFFF0F3F5); // 高光十字
                guiGraphics.fill(sx + 2, sy + 2, sx + 3, sy + 3, 0xFFF0F3F5);
            }
            // 虚空系技能（虚空之矛/虚空之躯）：图标外圈金色边框（伤害吸收金边主题）
            if (Skills.AURA_VOID.equals(skillId) || Skills.ULT_VOID_BODY.equals(skillId)) {
                int ix = iconX - 1, iy = iconY - 1, iw = 18, ih = 18; // 图标外扩 1px 边界
                int gold = enabled ? 0xFFFFD700 : 0xFFB8860B; // 开启=亮金，关闭=暗金
                guiGraphics.fill(ix, iy, ix + iw, iy + 1, gold);
                guiGraphics.fill(ix, iy + ih - 1, ix + iw, iy + ih, gold);
                guiGraphics.fill(ix, iy, ix + 1, iy + ih, gold);
                guiGraphics.fill(ix + iw - 1, iy, ix + iw, iy + ih, gold);
                // 四角提亮（伤害吸收黄心质感）
                guiGraphics.fill(ix, iy, ix + 2, iy + 2, 0xFFFFFFAA);
                guiGraphics.fill(ix + iw - 2, iy, ix + iw, iy + 2, 0xFFFFFFAA);
                guiGraphics.fill(ix, iy + ih - 2, ix + 2, iy + ih, 0xFFFFFFAA);
                guiGraphics.fill(ix + iw - 2, iy + ih - 2, ix + iw, iy + ih, 0xFFFFFFAA);
            }
        }

        // ---- 文字统一垂直居中 ----
        final int textY = y0 + (h - font.lineHeight) / 2;

        // ---- ① 名称（图标右侧；关闭时左侧先画禁用角标贴图）----
        int nameX = x0 + R_NAME_X;
        if (v.markW() > 0) {
            // ★ 2026-09-20：改用贴图（TEX_DISABLED_MARK）。垂直居中：整行高 h，贴图 MARK_SIZE。
            //   注意用 (h - MARK_SIZE) / 2 而不是文字基线 —— 贴图比文字行高大，对齐的是「行」不是「字」。
            guiGraphics.blit(TEX_DISABLED_MARK, nameX, y0 + (h - MARK_SIZE) / 2,
                    0, 0, MARK_SIZE, MARK_SIZE, MARK_SIZE, MARK_SIZE);
            nameX += v.markW();
        }
        drawCrispString(guiGraphics, v.name(), nameX, textY, v.nameColor(), false);

        // ---- ①b 属性加成（★ 2026-09-20 用户要求：改为「右对齐到消耗列之前」，间距 5px）----
        //   之前是「紧贴名字、左对齐」→ 每行属性起点随名字长短乱跳（实测左缘在 73~109 之间），
        //   而且属性宽度会反过来挤掉名字的可用宽。现在属性占独立右对齐区，位置恒定。
        if (v.attrW() > 0) {
            // ★ 2026-09-20：未装模组提示用警告红（与 tooltip 的未安装提示同色）；其余属性沿用类别色提亮
            int attrColor = v.attrWarn() ? 0xFFFF5555 : lighten(v.accent(), 0.5f);
            drawCrispString(guiGraphics, v.attrText(), x0 + attrRight() - v.attrW(), textY, attrColor, false);
        }

        // ---- ② 下一级消耗（★ 2026-09-20 改为右对齐：右边界 = R_COST_X + R_COST_W）----
        drawCrispString(guiGraphics, v.costText(),
                x0 + costX() + R_COST_W - v.costW(), textY, v.costColor(), false);

        // ---- ③ 等级进度条（★ 胶囊 + 类别色；宽度弹性随窗口变化；鼠标悬停其上才响应滚轮调级）----
        renderLevelBar(guiGraphics, x0 + barX(), y0 + (h - R_BAR_H2) / 2, barW(), v);

        // ---- ④ 等级文字（★ 2026-09-20：移到消耗数字后面；区内右对齐）----
        drawCrispString(guiGraphics, v.effect(),
                x0 + lvX() + R_LV_W - v.effectW(), textY, v.lvColor(), false);

        // 禁用（开关关闭）时给图标加半透明暗色遮罩
        if (!enabled) {
            guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x88000000);
        }
    }

    /**
     * 构建一行的「静态视觉」（2026-09-19 性能优化）。
     *
     * <p>只在数据变化（{@code rebuildButtons} 清缓存后）或行指纹变化（开关/等级/工具模式/技能点变化）时调用，
     * 不在每帧调用。
     */
    private RowVisual buildRowVisual(String skillId, int fp, int points, int activeLevel,
                                     boolean enabled, int toolMode, boolean isTool) {
        Skills.SkillType type = Skills.getType(skillId);
        boolean learned = isTool || points > 0;
        boolean canLearn = canLearn(skillId);
        // ★ 2026-09-19 前置未满足 → 整行变灰（用户要求：一眼看出哪些能点）
        boolean prereqMet = prereqMetFor(skillId);

        int bg;
        int bgHover;
        switch (type) {
            case MAGIC -> { bg = 0xFF183D35; bgHover = 0xFF215247; }
            case BASE -> { bg = 0xFF203A55; bgHover = 0xFF2B4B6D; }
            case AMPLIFY -> { bg = 0xFF503A27; bgHover = 0xFF674A30; }
            case ULTIMATE -> { bg = 0xFF512B34; bgHover = 0xFF693641; }
            case SPECIAL -> { bg = 0xFF4C3B27; bgHover = 0xFF625035; }
            case AURA -> { bg = 0xFF392D59; bgHover = 0xFF4A3A70; }
            case GLOBAL -> { bg = 0xFF203F52; bgHover = 0xFF2B526A; }
            case MACHINE -> { bg = 0xFF3E4248; bgHover = 0xFF515760; }
            default -> { bg = 0xFF624955; bgHover = 0xFF795966; }
        }
        if (isTool) { // 木棍工具：木褐色系（2026-09-08）
            bg = 0xFF5E4430;
            bgHover = 0xFF8A6A3E;
        }
        // 边框：★ 2026-09-19 用户要求「边框也要有不一样」→ 改用【类别色】；
        //   已学 = 满不透明；未学 = 半透明（一眼看出学没学）；悬停 = 提亮。
        //   ★ 2026-09-20：改为按【本技能】取色（搜索结果是跨类别的，不能统一用当前类别色）。
        final int accent = accentOf(skillId);
        int border;
        int borderHover;
        if (!enabled) {
            border = 0xFF8A8A8A;
            borderHover = 0xFF8A8A8A;
        } else if (learned) {
            border = (accent & 0x00FFFFFF) | 0xB0000000;
            borderHover = lighten(accent, 0.45f);
        } else {
            border = (accent & 0x00FFFFFF) | 0x66000000;
            borderHover = accent;
        }

        // 文字颜色
        //   已学 = 白（贴片描边/左侧色条已经表达了「学没学」，文字不再重复变灰）
        //   未学 = 略暗；前置未满足 / 禁用 = 更暗
        int nameColor = (!enabled || !prereqMet) ? 0xFF9A9A9A : (points > 0 ? 0xFFFFFFFF : 0xFFD8D8E8);
        int costColor = !prereqMet ? 0xFF7A7A7A : (canLearn ? 0xFFFFAA55 : 0xFFAAAAAA);
        int lvColor = !prereqMet ? 0xFF7A7A7A
                : (isTool ? org.zifeng.skilltree.client.StickToolModes.colorOfMode(toolMode) : 0xFF55FF55);

        // 等级进度条几何（★ 存比例而非像素 —— 条宽随窗口变化，渲染时再乘实际宽）：
        //   barFillR = 已学占比(0~1)，barTickR = 生效等级占比(<0 表示不画刻度)
        int max = Skills.getMaxPoints(skillId);
        float barFillR = -1f;
        float barTickR = -1f;
        int barColor = 0xFF55AAFF;
        if (max > 0 && points > 0) {
            barFillR = Math.min(1f, (float) points / max);
            barColor = !enabled ? 0xFF8A8A8A : (prereqMet ? accent : 0xFF9A9A9A);
            // 始终显示生效等级手柄；满生效时停在已学填充末端，拖动反馈也不会突然消失。
            barTickR = Math.min(1f, (float) activeLevel / max);
        }

        // 图标：自定义贴图优先；否则缓存 ItemStack（原实现每帧 new 一个）
        var iconTex = Skills.getIconTexture(skillId);
        net.minecraft.world.item.ItemStack iconStack = iconTex == null
                ? new net.minecraft.world.item.ItemStack(Skills.getIcon(skillId)) : null;

        // 三行文本（沿用原有构建逻辑，这里只是改成「只在需要时构建」）
        double nextCost = recordNextCost(skillId);
        // ★ 属性加成文本（用户要求：名称与消耗之间显示「单技能增加属性」）
        // ★ 2026-09-20 用户要求：未装对应模组（新生魔艺/铁魔法/Goety）时，属性区改为显示「⚠ 未装模组」
        //   提示（这些技能必然不可学），否则玩家只看到空白，会误以为「这技能没有加成」。
        //   只查一次缺模组结果，避免 skillAttrText 内部再查一遍。
        final boolean attrNoMod = missingModName(skillId) != null;
        String attrText = skillAttrText(skillId, points, attrNoMod);
        if (!attrText.isEmpty()) {
            // ★ 2026-09-21：按【收缩阶梯】的动态宽度裁切（原来写死 ATTR_AREA_W，
            //   小窗口上属性区会溢出到消耗列上）
            attrText = clipToWidth(attrText, attrAreaW());
        }
        int attrW = attrText.isEmpty() ? 0 : lw(attrText);
        // ★ 2026-09-20：技能关闭角标的占位宽（贴图，显示在名称左边）
        int markW = enabled ? 0 : MARK_SIZE + MARK_GAP;
        // ★ 2026-09-20：属性已改为「独立右对齐区」，不再占用名称区宽度
        //   （旧写法会按 attrW 扣减 nameMaxW，导致同一个技能开关属性后名字被裁得更短）
        int nameMaxW = Math.max(24, R_NAME_W - markW - 2);
        ButtonTexts texts = buildButtonTexts(skillId, type, isTool, enabled, toolMode, points, activeLevel, nextCost, nameMaxW);
        String costText = nextCostDisplay(skillId, points, nextCost);

        return new RowVisual(fp, type, isTool, enabled, learned, bg, bgHover, border, borderHover,
                nameColor, costColor, lvColor, barFillR, barTickR, barColor,
                isLevelBindable(skillId), Skills.isTriggerBindable(skillId), iconStack, iconTex,
                texts.name(), lw(texts.name()), texts.effect(), lw(texts.effect()), costText, lw(costText),
                attrText, attrW, markW, accent, attrNoMod);
    }

    /**
     * 绘制整张贴片【内部】的一个按键格（2026-09-19 L4 分区重构）。
     *
     * <p>★ 用户要求：Q/E/R <b>不是独立的方框</b>，「要和单个大的技能为一个大的贴片」——
     * 所以本方法<b>不画自己的外边框</b>（外框由整张贴片统一画），
     * 只画格子底色 + 悬停/监听高亮 + 键名文字；分格竖线由 {@link #renderSkillButton} 统一画。
     *
     * <p>配色：格底用半透明叠加（不做成实心块，避免把整张贴片切成三个「小按钮」的观感）；
     * 按键已绑定时给一层淡金底，监听中给亮橙底。
     *
     * @param hovered 该格是否被鼠标悬停（由调用方统一算，避免重复坐标变换）
     * @param usable  该技能在此格是否有功能；<b>false = 空格</b>（只留底、无文字）
     * @param slot    1=开关(金) / 2=模式·等级(蓝) / 3=触发(绿)
     */
    private void renderKeyCell(GuiGraphics guiGraphics, String skillId, int x, int y, int w, int h,
                               boolean hovered, boolean usable, int slot, String bindSkillId,
                               boolean bindListening, com.mojang.blaze3d.platform.InputConstants.Key key) {
        boolean listening = usable && skillId.equals(bindSkillId) && bindListening;
        // 格底：半透明叠加（默认极淡，悬停/绑定/监听逐级加亮）
        int bg;
        if (listening) {
            bg = 0x99B06A00;
        } else if (hovered && usable) {
            bg = switch (slot) {
                case 1 -> 0x55FFD700;
                case 2 -> 0x5588BBFF;
                default -> 0x5566EE66;
            };
        } else if (key != null) {
            bg = 0x44FFD700; // 已绑定：淡金
        } else {
            bg = 0x22000000; // 未绑定空格：极淡
        }
        // 从 x+1/y+1 起画，避开外框与分格竖线
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);

        // 空格：不画任何文字（用户要求没有功能就不显示）
        if (!usable) {
            return;
        }
        String text;
        int color;
        // 按键显示名走缓存（原实现每帧每槽位都 getDisplayName().getString()）
        String boundName = keyName(key);
        if (listening) {
            text = "> " + (boundName != null ? boundName : "?") + " <";
            color = 0xFFFFFF55;
        } else if (boundName != null) {
            text = boundName;
            color = 0xFFFFFFFF;
        } else {
            text = t("tip_unbound");
            color = 0xFF888888;
        }
        text = clipToWidth(text, w - 4);
        drawCrispString(guiGraphics, text, x + w / 2, y + (h - font.lineHeight) / 2, color, true);
    }

    /**
     * 构建按钮三行文本（名称 / 等级 / 消耗）——纯计算、无绘制。
     *
     * <p>⚠️ 2026-09-19 性能优化：本方法现在<b>只由 {@link #buildRowVisual} 调用</b>——
     * 即「数据变了才算一次」，不再每帧对每行调用。
     * （原先每帧重建：约 10 次语言查询 + 3 次 O(n²) 逐字符裁剪 × 可见行数 → 纯 GC 压力。）
     */
    private ButtonTexts buildButtonTexts(String skillId, Skills.SkillType type, boolean isTool, boolean enabled,
                                         int toolMode, int points, int activeLevel, double nextCost, int nameMaxW) {
        // 名称（列表行：按名称区宽裁剪）
        // ★ 2026-09-20：禁用角标「⊘」<b>不再拼进名称字符串</b> —— 它要单独用亮红绘制。
        //   拼进来会跟名称同色（灰），用户反馈「太淡不够醒目」。见 {@link #renderSkillButton}。
        String name = clipToWidth(Skills.getDisplayNameComponent(skillId).getString(), nameMaxW);
        // 等级文字（列表行：如 "99/100"；工具卡 = 当前模式名）
        String effectText = clipToWidth(isTool
                ? t(org.zifeng.skilltree.client.StickToolModes.modeLang(toolMode))
                : Skills.AE_INFINITE_CHANNEL.equals(skillId)
                    ? aeChannelLevelText(points > 0 ? activeLevel : 0)
                : (isLevelBindable(skillId) && points > 0
                    ? activeLevel + "/" + points
                    : points + "/" + Skills.getMaxPoints(skillId)), R_LV_W);
        // 第3行：消耗总数量（工具卡 = 当前模式模块说明；其余按类别）
        String costText;
        if (isTool) {
            costText = t(org.zifeng.skilltree.client.StickToolModes.lineLang(toolMode));
        } else if (type == Skills.SkillType.AURA) {
            if (Skills.AURA_MAGNET.equals(skillId)) {
                costText = points > 0 ? t("btn_unlocked") : t("btn_need") + (long) (double) org.zifeng.skilltree.Config.MAGNET_COST.get() + t("btn_pt");
            } else if (Skills.AURA_LOCK.equals(skillId)) {
                costText = points > 0 ? t("btn_unlocked") : t("btn_need") + (long) (double) org.zifeng.skilltree.Config.LOCK_COST.get() + t("btn_pt");
            } else {
                long total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getAuraCost(skillId, i);
                }
                // 有等级的光环（伤害/速度/治愈）：显示生效:X/Y（与基础技能一致，滚轮可调）
                if (Skills.getAuraMaxPoints(skillId) > 1) {
                    costText = t("btn_active") + ":" + activeLevel + "/" + points + " " + t("btn_next") + ":" + (long) nextCost + t("btn_pt");
                } else {
                    costText = t("btn_spent") + total + t("btn_pt") + " " + t("btn_next") + ":" + (long) nextCost + t("btn_pt");
                }
            }
        } else if (type == Skills.SkillType.GLOBAL) {
            // 寰宇法则：时之环/晴空环单级；无限回路 4 级（生效:X/Y + 下一级）
            if (Skills.getGlobalMaxPoints(skillId) > 1) {
                costText = points > 0
                        ? t("btn_active") + ":" + activeLevel + "/" + points + " " + t("btn_next") + ":" + fmtCost(Skills.getGlobalCost(skillId, points)) + t("btn_pt")
                        : t("btn_need") + fmtCost(Skills.getGlobalCost(skillId, 0)) + t("btn_pt");
            } else {
                costText = points > 0 ? t("btn_unlocked") : t("btn_need") + fmtCost(Skills.getGlobalCost(skillId, 0)) + t("btn_pt");
            }
        } else if (type == Skills.SkillType.BASE || type == Skills.SkillType.AMPLIFY || type == Skills.SkillType.MAGIC) {
            // 生效等级（滚轮可调，实时显示）+ 下一级真实消耗（线性增长：基础 +1/级、增幅/魔法 +2/级）
            double unitCost = switch (type) {
                case BASE -> Skills.getBaseCostAtLevel(points);
                case AMPLIFY -> Skills.getAmplifyCostAtLevel(points);
                case MAGIC -> Skills.getMagicCostAtLevel(skillId, points);
                default -> 0;
            };
            costText = t("btn_active") + ":" + activeLevel + "/" + points + " " + t("btn_next") + ":" + fmtCost(unitCost) + t("btn_pt");
        } else if (type == Skills.SkillType.ULTIMATE || type == Skills.SkillType.SPECIAL) {
            // 终极节点：单次解锁消耗（浴血/金身/涅槃=500，死神=1000，全能精通=5000，宇宙的青睐=1000，夜视/饱食=100）
            // 多级终极（节点类）：村庄英雄10点/级、接触距离1点/级、发光1点，显示已耗+下一级
            // ⚠️ SPECIAL（特殊被动）：同终极成本体系（从终极列拆出）
            if (Skills.ULT_FAVOR.equals(skillId)) {
                costText = points > 0 ? t("btn_unlocked") : t("btn_need") + Skills.ultFavorCost() + t("btn_pt");
            } else if (Skills.NIGHT_VISION.equals(skillId) || Skills.SATURATION.equals(skillId)) {
                costText = points > 0 ? t("btn_unlocked") : t("btn_need") + Skills.minorUltCost() + t("btn_pt");
            } else if (Skills.getUltimateMaxPoints(skillId) > 1) {
                // 多级终极（节点类，阶梯递增消耗，可滚轮调生效等级）：生效:X/Y + 下一级（与基础技能一致）
                double unitCost = Skills.getUltimateLevelCost(skillId, points);
                costText = points > 0
                        ? t("btn_active") + ":" + activeLevel + "/" + points + " " + t("btn_next") + ":" + fmtCost(unitCost) + t("btn_pt")
                        : t("btn_need") + fmtCost(unitCost) + t("btn_pt");
            } else {
                costText = points > 0 ? t("btn_unlocked") : t("btn_need") + Skills.ultimateCost(skillId) + t("btn_pt");
            }
        } else if (type == Skills.SkillType.MACHINE) {
            // 机械共鸣：单级解锁（机械之星 1000 / 其余共鸣 5000）
            costText = points > 0 ? t("btn_unlocked") : t("btn_need") + (long) Skills.getMachineCost(skillId) + t("btn_pt");
        } else if (type == Skills.SkillType.GIFT) {
            // 子枫的馈赠：时间系列=按游戏时长激活（0点）；洗礼=阶梯消耗；增幅=指数消耗
            if (Skills.GIFT_TIME_BAPTISM.equals(skillId)
                    || Skills.GIFT_TIME_STORM.equals(skillId)
                    || Skills.GIFT_TIME_FLOOD.equals(skillId)) {
                // 时间系列：单级，已激活显示"已激活"
                costText = points > 0 ? t("btn_activated") : t("btn_need") + (Skills.getGiftRequirementTicks(skillId) / 72000) + t("unit_hour");
            } else if (points > 0 && points < Skills.getGiftMaxPoints(skillId)) {
                // 已学未满级：显示下一级消耗（与其他类别技能一致，2026-08-25）
                costText = t("btn_learned") + points + "/" + Skills.getGiftMaxPoints(skillId)
                        + " " + t("btn_next") + ":" + fmtCost(Skills.getGiftCost(skillId, points)) + t("btn_pt");
            } else if (points > 0) {
                costText = t("btn_maxed") + " " + points + "/" + Skills.getGiftMaxPoints(skillId);
            } else {
                costText = t("btn_need") + fmtCost(Skills.getGiftCost(skillId, points)) + t("btn_pt");
            }
        } else {
            costText = points + t("unit_lv");
        }
        costText = clipToWidth(costText, R_NAME_W);
        return new ButtonTexts(name, effectText, costText);
    }

    /**
     * 无限回路的等级栏显示实际频道模式；0 级不是「没有数值」，而是 AE2 原版默认 8 频道。
     * <p>这里读取玩家自己的生效等级，不读取服务器全局模式：多人场景下服务器可能由最后激活者决定，
     * 技能行仍应准确显示当前玩家选择的等级。
     */
    private String aeChannelLevelText(int activeLevel) {
        return switch (Math.max(0, Math.min(4, activeLevel))) {
            case 1 -> t("ae_x2_short");
            case 2 -> t("ae_x3_short");
            case 3 -> t("ae_x4_short");
            case 4 -> t("ae_infinite_short");
            default -> t("ae_default_short");
        };
    }

    /**
     * 等级进度条（2026-09-19 列表式改版新增）。
     *
     * <p>显示「已学等级 / 等级上限」，并在已学超过生效等级时用一条黄色刻度标出生效位置。
     *
     * <p>⚠️ 2026-09-19 性能优化：填充宽/刻度位置/填充色 已在 {@link #buildRowVisual} 算好并缓存在
     * {@link RowVisual} 里，这里只负责 fill —— 不再每帧做除法与 {@code Skills.getMaxPoints} 查询。
     *
     * <p>鼠标是否在条上由 {@link #overLevelBar(double, double, SkillButton)} 用同一套几何判断
     * （避免两处算法漂移）。
     */
    private void renderLevelBar(GuiGraphics guiGraphics, int x, int y, int w, RowVisual v) {
        // 两层轨道让细条在深浅背景上都保持清楚，避免截图中大块、发糊的观感。
        fillRound(guiGraphics, x, y, x + w, y + R_BAR_H2, R_BAR_H2 / 2, 0x42000000);
        fillRound(guiGraphics, x + 1, y + 1, x + w - 1, y + R_BAR_H2 - 1,
                Math.max(1, R_BAR_H2 / 2 - 1), C_BAR_TRACK);
        // 已学等级填充。
        if (v.barFillR() > 0f) {
            int fw = Math.max(R_BAR_H2, Math.round(w * v.barFillR()));
            fillRound(guiGraphics, x, y, x + Math.min(w, fw), y + R_BAR_H2, R_BAR_H2 / 2, v.barColor());
        }
        // 生效等级手柄：位置与右侧 active/learned 数字使用同一个 activeLevel。
        if (v.barTickR() >= 0f) {
            int tx = x + Math.round(w * v.barTickR());
            fillRound(guiGraphics, tx - 2, y - 2, tx + 3, y + R_BAR_H2 + 2, 2, 0xCC202028);
            fillRound(guiGraphics, tx - 1, y - 1, tx + 2, y + R_BAR_H2 + 1, 1, C_BAR_TICK);
        }
    }

    /** 按鼠标在进度条上的位置设置生效等级，并立即同步文字、条形和服务端。 */
    private boolean setActiveLevelFromMouse(SkillButton button, double mouseX) {
        String skillId = button.skillId();
        int learned = learnedSkills.getOrDefault(skillId, 0);
        if (learned <= 0 || Skills.getMaxPoints(skillId) <= 1) {
            return false;
        }
        double localX = toPanelX(mouseX) - (button.x() + barX());
        int max = Math.max(1, Skills.getMaxPoints(skillId));
        int next = (int) Math.round(Math.max(0.0, Math.min(1.0, localX / Math.max(1, barW()))) * max);
        next = Math.max(0, Math.min(learned, next));
        int old = activeLevels.getOrDefault(skillId, learned);
        if (next != old) {
            activeLevels.put(skillId, next);
            rowVisualCache.remove(skillId);
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new SetSkillLevelC2SPacket(skillId, next));
        }
        return true;
    }

    /**
     * 鼠标是否悬停在【该行的等级进度条】上（2026-09-19）。
     *
     * <p>用户明确要求：<b>只有鼠标在进度条上时，滚轮才调等级</b>；否则滚轮一律滚动列表。
     * <p>容差 ±3px（横向）与 ±4px（纵向）—— 条子只有 4px 高，严格判定很难命中。
     * <p>★ 条宽是弹性的（随窗口变化），这里用同一个 {@link #barW()} 算，避免两处算法漂移。
     */
    private boolean overLevelBar(double mouseX, double mouseY, SkillButton button) {
        if (!isMouseOverSkillList(mouseX, mouseY)) {
            return false;
        }
        double lx = toPanelX(mouseX);
        double ly = toPanelY(mouseY);
        int barX = button.x() + barX();
        int w = barW();
        int barY = button.y() + (BUTTON_HEIGHT - R_BAR_H2) / 2;
        return lx >= barX - 3 && lx <= barX + w + 3
                && ly >= barY - 4 && ly <= barY + R_BAR_H2 + 4;
    }

    /**
     * 前置条件是否已满足（2026-09-19 新增：未满足则整行变灰）。
     *
     * <p>只判前置，<b>不判</b>技能点是否够/是否已满级——那两项行内已有颜色表达
     * （消耗文字灰/金、等级文字颜色），避免一次表达太多信息反而看不清。
     */
    private boolean prereqMetFor(String skillId) {
        for (Map.Entry<String, Integer> e : Skills.getPrerequisites(skillId)) {
            if (learnedSkills.getOrDefault(e.getKey(), 0) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 行内「下一级消耗」短文本（2026-09-19）。
     *
     * <p>列表行空间紧，所以只显示一个带前缀的数字（如 {@code -50}），详细单位/含义由 tooltip 承担。
     * 已满级显示 {@code MAX}。
     *
     * <p>⚠️ 2026-09-20 修复 1：前缀原本是 {@code "+"}，但这里返回的是**消耗**而不是收益 ——
     * 玩家反馈「技能上有技能点消耗，但是显示的是 + 号」，读起来像是“这个技能会给我 50 点”。
     * 改为 {@code "-"}：同一行里名称后的属性加成是 {@code +10♥}（真正的收益），
     * 右侧消耗是 {@code -50}（支出），同屏对比即可分清「获得/支出」。
     *
     * <p>⚠️ 2026-09-20 修复 2：<b>时间系列馈赠</b>（时间洗礼/风暴/洪流）的“消耗”是游戏时长，
     * 技能点消耗为 0 → 原先落到 {@code nextCost <= 0} 分支显示成 {@code "—"}，玩家看不出任何信息。
     * 改为显示激活门槛（中：需1小时　英：Need 1h），复用已有的
     * {@code btn_need} + {@code unit_hour} 翻译，无需新增语言 key。
     * <p>已激活时仍由上面的 {@code MAX} 分支拦下（上限 1 级），符合用户选择。
     */
    private String nextCostDisplay(String skillId, int points, double nextCost) {
        if (Skills.isStickTool(skillId)) {
            return "";
        }
        int max = Skills.getMaxPoints(skillId);
        if (max > 0 && points >= max) {
            return "MAX";
        }
        // ★ 时间系列馈赠：消耗是游戏时长，改为显示激活门槛
        if (Skills.isGiftTimeSkill(skillId)) {
            long hours = Skills.getGiftRequirementTicks(skillId) / 72000L; // 72000 tick = 1 小时
            return clipToWidth(t("btn_need") + hours + t("unit_hour"), R_COST_W - 2);
        }
        if (nextCost <= 0) {
            return "—"; // 其余无消耗技能（理论上不该出现）
        }
        String num = fmtCost(nextCost);
        return "-" + clipToWidth(num, R_COST_W - 2);
    }

    /** 估算下一级消耗（客户端显示用） */
    private double recordNextCost(String skillId) {
        return nextCostLocal(skillId);
    }

    /** 消耗数值显示：整数不带小数（1 点），非整数保留 1 位小数（1.5 点） */
    private static String fmtCost(double cost) {
        return cost == Math.floor(cost) ? String.valueOf((long) cost) : String.format("%.1f", cost);
    }

    /** 客户端可学判定 */
    private boolean canLearn(String skillId) {
        if (Skills.isStickTool(skillId)) {
            return false; // 木棍工具占位：不算技能不可学（2026-09-08）
        }
        Skills.SkillType type = Skills.getType(skillId);
        int current = learnedSkills.getOrDefault(skillId, 0);
        if (type == Skills.SkillType.BASE && current >= Skills.BASE_MAX_POINTS) return false;
        if (type == Skills.SkillType.AMPLIFY && current >= Skills.AMPLIFY_MAX_POINTS) return false;
        if (type == Skills.SkillType.ULTIMATE && current >= Skills.getUltimateMaxPoints(skillId)) return false;
        if (type == Skills.SkillType.SPECIAL && current >= Skills.getUltimateMaxPoints(skillId)) return false; // 特殊被动：复用终极上限
        if (type == Skills.SkillType.AURA && current >= Skills.getAuraMaxPoints(skillId)) return false;
        if (type == Skills.SkillType.MAGIC) {
            if (current >= Skills.getMagicMaxPoints(skillId)) return false;
            // 其余模组兼容技能：对应模组未安装 → 不可学（红字提示见悬停说明）
            if (missingModName(skillId) != null) return false;
        }
        if (type == Skills.SkillType.MACHINE && current >= Skills.getMachineMaxPoints(skillId)) return false;
        if (type == Skills.SkillType.GIFT && current >= Skills.getGiftMaxPoints(skillId)) return false; // 子枫的馈赠：单级
        // 前置需求（终极/光环通用：前置技能 → 所需等级）
        for (Map.Entry<String, Integer> entry : Skills.getPrerequisites(skillId)) {
            if (learnedSkills.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        // 技能点足够
        if (skillPoints < nextCostLocal(skillId) - 1e-9) return false;
        return true;
    }

    /**
     * 其余模组兼容技能：返回缺失模组的中文名；模组已装或非兼容技能 → null。
     * 新生魔艺（Ars Nouveau）/ 铁魔法（Iron's Spells）系列。
     */
    private static String missingModName(String skillId) {
        if (Skills.MANA_AMP.equals(skillId) || Skills.ARS_MANA_REGEN.equals(skillId)) {
            return org.zifeng.skilltree.compat.ArsNouveauCompat.isLoaded() ? null : net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.mod_ars").getString();
        }
        if (Skills.IRON_MANA_AMP.equals(skillId) || Skills.IRON_MANA_REGEN.equals(skillId)
                || Skills.IRON_CAST_TIME.equals(skillId) || Skills.IRON_COOLDOWN.equals(skillId)
                || Skills.IRON_FIRE.equals(skillId) || Skills.IRON_ICE.equals(skillId) || Skills.IRON_LIGHTNING.equals(skillId)
                || Skills.IRON_HOLY.equals(skillId) || Skills.IRON_ENDER.equals(skillId)
                || Skills.IRON_BLOOD.equals(skillId) || Skills.IRON_EVOCATION.equals(skillId)
                || Skills.IRON_NATURE.equals(skillId) || Skills.IRON_ELDRITCH.equals(skillId)) {
            return org.zifeng.skilltree.compat.IronSpellsCompat.isLoaded() ? null : net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.mod_iron").getString();
        }
        // Goety（诡术，2026-09-20）：通用强度 / 通用灵魂折扣 / 9 流派精通，均需装 Goety 才能学
        if (Skills.GOETY_POTENCY.equals(skillId) || Skills.GOETY_SOUL_DISCOUNT.equals(skillId)
                || Skills.GOETY_ABYSS.equals(skillId) || Skills.GOETY_FROST.equals(skillId)
                || Skills.GOETY_GEOMANCY.equals(skillId) || Skills.GOETY_NECROMANCY.equals(skillId)
                || Skills.GOETY_NETHER.equals(skillId) || Skills.GOETY_STORM.equals(skillId)
                || Skills.GOETY_VOID.equals(skillId) || Skills.GOETY_WILD.equals(skillId)
                || Skills.GOETY_WIND.equals(skillId)) {
            return org.zifeng.skilltree.compat.GoetyCompat.isLoaded() ? null : net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.mod_goety").getString();
        }
        return null;
    }

    // ============ 属性面板 ============

    /** 面板可视行数（右侧布局，减去标题/技能点行） */
    int panelVisibleRows() {
        // 滚动区域：标题(18px) + 可见行 + 底部提示(20px)；每行 12px
        return (height - 50 - 30 - 18 - 20) / 12;
    }

    /** 鼠标是否在属性面板区域内（2026-09-01：属性面板已改为子界面，内嵌区域不再是 UI → 恒 false，右侧/底部恢复为技能树区域） */
    private boolean isMouseOverPanel(double mouseX, double mouseY) {
        return false;
    }

    /**
     * 鼠标是否在顶部信息区背景内（tooltip 穿透检查用）。
     * 与 render 中绘制背景时相同的包围盒计算：以文字最大宽度为中心外扩 10px，×0.8 缩放系数。
     */
    private boolean isMouseOverHeader(double mouseX, double mouseY) {
        int[] b = headerBounds();
        return mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3];
    }

    /**
     * 顶部信息区包围盒 [left, top, right, bottom]（屏幕坐标，与 renderHeaderInfo 绘制一致）。
     * ⚠️ 2026-09-15 性能优化：原实现每次调用都重建文字 + 测宽，而本方法被 isIconUnderUI() 对每个按钮调用
     *    （≈120 次/帧）→ 改为读每帧只算一次的共享缓存；顺带修掉原先硬编码中文测宽（英文环境宽度不准）。
     */
    private int[] headerBounds() {
        refreshHeaderCache();
        return cachedHeaderBounds;
    }

    /** 属性行缓存（2026-08-27 性能优化：原每帧 collectRows → 每个 getComputedValue 遍历 29 个 BaseSkill 条目，
     *  84 技能满配时每帧数千次查询；改为每 20 tick（1 秒）重建一次——界面打开时数据变化频率低） */
    private java.util.List<String[]> cachedRows;
    private long lastRowsTick = -1;

    /** 属性行收集（共用逻辑，右侧/底部布局都展示同一份数据） */
    java.util.List<String[]> collectRows() {
        long tick = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        if (cachedRows != null && tick - lastRowsTick < 20) {
            return cachedRows;
        }
        lastRowsTick = tick;
        var player = minecraft != null ? minecraft.player : null;
        if (player == null) {
            cachedRows = java.util.List.of();
            return cachedRows;
        }
        // 本地技能记录（含生效等级），属性值全部本地计算 → 加点立即实时刷新，不依赖服务端属性同步
        org.zifeng.skilltree.data.PlayerSkillRecord rec = learnedAsRecord();
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        // 分隔标题行（灰色小字，按功能分组）
        rows.add(new String[]{"—— " + t("panel_cat_combat") + " ——", "", "#777777"});
        addRow(rows, t("panel_atk_dmg"), attrVal(player, Attributes.ATTACK_DAMAGE, rec), "%.1f");
        addRow(rows, t("panel_atk_speed"), attrVal(player, Attributes.ATTACK_SPEED, rec), "%.2f");
        addRow(rows, t("panel_knockback"), attrVal(player, Attributes.ATTACK_KNOCKBACK, rec), "%.1f");
        addRow(rows, t("panel_crit_chance"), SkillEffects.getCritChance(rec) * 100, "%.0f%%");
        addRow(rows, t("panel_crit_dmg"), SkillEffects.getCritMultiplier(rec), "%.1f" + t("unit_x"));
        addRow(rows, t("panel_armor_pen"), SkillEffects.getArmorPenPercent(rec) * 100, "%.0f%%");
        addRow(rows, t("panel_lifesteal"), SkillEffects.getLifestealRate(rec) * 100, "%.0f%%");
        addRow(rows, t("panel_thorns"), SkillEffects.getThornsDamage(rec), "%.1f");

        rows.add(new String[]{"—— " + t("panel_cat_defense") + " ——", "", "#777777"});
        addRow(rows, t("panel_hp"), attrVal(player, Attributes.MAX_HEALTH, rec), "%.0f");
        addRow(rows, t("panel_armor"), attrVal(player, Attributes.ARMOR, rec), "%.1f");
        addRow(rows, t("panel_toughness"), attrVal(player, Attributes.ARMOR_TOUGHNESS, rec), "%.1f");
        // 物理减伤（自定义属性）：护甲减伤 80% 封顶后继续叠的独立减伤层
        addRow(rows, t("panel_dmg_reduce"), attrVal(player, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION, rec) * 100, "%.0f%%");

        // ═══ 奥术防护（2026-09-14）：魔法减伤与反制 ═══
        // 魔法减伤：对「护甲无效」的伤害生效（魔法/凋零/龙息等）。公式 D/(D+K) 永不达到 100%
        // ★ 常显（未学显示 0%）：让玩家知道有这项防护可加，且面板行数稳定不跳动
        addRow(rows, t("panel_magic_reduce"), SkillEffects.getMagicReduction(rec) * 100, "%.0f%%");
        // 法术抑制：仅对间接伤害（箭矢/法术弹射物）生效的额外减伤（同样常显）
        addRow(rows, t("panel_spell_dampen"), SkillEffects.getDampenReduction(rec) * 100, "%.0f%%");
        // 法术反射：触发几率（一次性技能，学了就显示）
        if (rec.getLearnedPoints(Skills.SPELL_REFLECT) > 0 && rec.isEnabled(Skills.SPELL_REFLECT)) {
            addRow(rows, t("panel_spell_reflect"),
                    org.zifeng.skilltree.Config.SPELL_REFLECT_CHANCE.get() * 100, "%.0f%%");
        }
        // 法力虹吸：魔法伤害转化为回血的比例
        if (rec.getLearnedPoints(Skills.MANA_SIPHON) > 0 && rec.isEnabled(Skills.MANA_SIPHON)) {
            addRow(rows, t("panel_mana_siphon"),
                    org.zifeng.skilltree.Config.MANA_SIPHON_RATIO.get() * 100, "%.0f%%");
        }
        // 全能精通：全伤害减免（对所有伤害类型生效，含真伤/混沌/指令）
        boolean masterOn = rec.getLearnedPoints(Skills.ULT_MASTER) > 0 && rec.isEnabled(Skills.ULT_MASTER);
        if (masterOn) {
            addRow(rows, t("panel_all_reduce"), org.zifeng.skilltree.Config.MASTER_DAMAGE_REDUCTION.get() * 100, "%.0f%%");
        }
        addRow(rows, t("panel_kb_resist"), attrVal(player, Attributes.KNOCKBACK_RESISTANCE, rec), "%.1f");
        // 耐久减免：未满显示百分比，封顶（100%）显示"工具不毁"
        double durReduction = SkillEffects.getToolDurabilityReduction(rec);
        if (durReduction >= 1.0) {
            rows.add(new String[]{t("panel_dur_reduce"), t("panel_unbreaking"), "#FFFFD700"});
        } else if (durReduction > 0) {
            addRow(rows, t("panel_dur_reduce"), durReduction * 100, "%.0f%%");
        }

        rows.add(new String[]{"—— " + t("panel_cat_movement") + " ——", "", "#777777"});
        // 速度显示为每秒方块数：移速 0.1→4.317方/秒，飞行 0.05→10.8方/秒，游泳→3.35方/秒
        addRow(rows, t("panel_move_speed"), attrVal(player, Attributes.MOVEMENT_SPEED, rec) * 43.17, "%.2f" + t("unit_bps"));
        // 飞速：实际飞行速度 = abilities.flyingSpeed（每 tick 由 FLYING_SPEED 属性÷8 同步）；0.05 → 10.8 方/秒
        addRow(rows, t("panel_fly_speed"), player.getAbilities().getFlyingSpeed() * 216, "%.2f" + t("unit_bps"));
        // 游泳：SWIM_SPEED 默认 1.0 → 原版游泳 ≈ 3.35 方/秒
        double swim = player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED) != null
                ? attrVal(player, net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED, rec) * 3.35 : 0;
        addRow(rows, t("panel_swim"), swim, "%.2f" + t("unit_bps"));
        // 跳跃高度（格）= JUMP_STRENGTH² × 6.25（无药水时）
        double jump = attrVal(player, Attributes.JUMP_STRENGTH, rec);
        addRow(rows, t("panel_jump"), jump * jump * 6.25, "%.2f" + t("unit_block"));

        rows.add(new String[]{"—— " + t("panel_cat_production") + " ——", "", "#777777"});
        // 挖速用自定义 ModAttributes.MINING_EFFICIENCY（1.20.1 原版无此属性，直接反映实际挖掘加速）
        addRow(rows, t("panel_mining"), attrVal(player, net.minecraft.world.entity.ai.attributes.Attributes.MINING_EFFICIENCY, rec), "%.1f");
        addRow(rows, t("panel_luck"), attrVal(player, Attributes.LUCK, rec), "%.1f");
        addRow(rows, t("panel_regen"), SkillEffects.getRegenPerSecond(rec), "%.1f");
        // ★ 2026-09-30：原「猎魂丰收」行已删除（合并进战利品大爆发，其行见下方 loot_bomb）
        addRow(rows, t("panel_block_drop"), SkillEffects.getBlockDropMultiplier(rec), "%.2f" + t("unit_x"));
        addRow(rows, t("panel_xp"), SkillEffects.getExperienceMultiplier(rec), "%.2f" + t("unit_x"));
        // 掉落节点类终极：刷怪蛋/头颅概率 + 战利品爆炸倍率（没学不显示）
        int spawnEgg = rec.isEnabled(Skills.MOB_SPAWN_EGG) ? rec.getActiveLevel(Skills.MOB_SPAWN_EGG) : 0;
        if (spawnEgg > 0) addRow(rows, t("panel_spawn_egg"), spawnEgg * 20, "%.0f%%");
        int mobHead = rec.isEnabled(Skills.MOB_HEAD) ? rec.getActiveLevel(Skills.MOB_HEAD) : 0;
        if (mobHead > 0) addRow(rows, t("panel_mob_head"), mobHead * 10, "%.0f%%");
        int lootBomb = rec.isEnabled(Skills.LOOT_BOMB) ? rec.getActiveLevel(Skills.LOOT_BOMB) : 0;
        if (lootBomb > 0) addRow(rows, t("panel_loot_bomb"), 1.0 + lootBomb, "%.0f" + t("unit_x"));

        // ============ 光环类（技能没点不显示） ============
        boolean hasAuraDamage = rec.isEnabled(Skills.AURA_DAMAGE) && rec.getActiveLevel(Skills.AURA_DAMAGE) > 0;
        boolean hasAuraSpeed = rec.isEnabled(Skills.AURA_SPEED) && rec.getActiveLevel(Skills.AURA_SPEED) > 0;
        boolean hasAnyAura = hasAuraDamage || hasAuraSpeed
                || rec.getLearnedPoints(Skills.AURA_VOID) > 0;
        if (hasAnyAura) {
            rows.add(new String[]{"—— " + t("panel_cat_aura") + " ——", "", "#777777"});
            if (hasAuraDamage) {
                addRow(rows, t("panel_aura_dmg"), attrVal(player, Attributes.ATTACK_DAMAGE, rec), "%.1f");
            }
            if (hasAuraSpeed) {
                // 光环攻击频率 = 基础间隔(10秒) × 0.9^光环速度等级（乘法递减）
                int baseInterval = org.zifeng.skilltree.Config.AURA_BASE_INTERVAL_TICKS.get();
                int speedLevel = rec.getActiveLevel(Skills.AURA_SPEED);
                double reduction = org.zifeng.skilltree.Config.AURA_SPEED_INTERVAL_REDUCTION.get();
                int interval = Math.max(10, (int) Math.round(baseInterval * Math.pow(1 - reduction, speedLevel)));
                addRow(rows, t("panel_aura_freq"), Math.round(20.0 / interval * 10.0) / 10.0, "%.1f");
            }
            // 光环范围半径（学了虚空之矛 → 范围放大到 50 格）
            double auraRadius = rec.getLearnedPoints(Skills.AURA_VOID) > 0 && rec.isEnabled(Skills.AURA_VOID)
                    ? org.zifeng.skilltree.Config.VOID_AURA_RADIUS.get()
                    : org.zifeng.skilltree.Config.AURA_ATTACK_RADIUS.get();
            addRow(rows, t("panel_aura_radius"), auraRadius, "%.0f" + t("unit_block"));
        }

        // ============ 魔法增幅（纵列0，其余模组兼容；没点不显示） ============
        boolean hasArs = rec.getLearnedPoints(Skills.MANA_AMP) > 0 || rec.getLearnedPoints(Skills.ARS_MANA_REGEN) > 0;
        boolean hasIron = rec.getLearnedPoints(Skills.IRON_MANA_AMP) > 0 || rec.getLearnedPoints(Skills.IRON_MANA_REGEN) > 0
                || rec.getLearnedPoints(Skills.IRON_CAST_TIME) > 0 || rec.getLearnedPoints(Skills.IRON_COOLDOWN) > 0
                || rec.getLearnedPoints(Skills.IRON_FIRE) > 0 || rec.getLearnedPoints(Skills.IRON_ICE) > 0
                || rec.getLearnedPoints(Skills.IRON_LIGHTNING) > 0 || rec.getLearnedPoints(Skills.IRON_HOLY) > 0
                || rec.getLearnedPoints(Skills.IRON_ENDER) > 0 || rec.getLearnedPoints(Skills.IRON_BLOOD) > 0
                || rec.getLearnedPoints(Skills.IRON_EVOCATION) > 0 || rec.getLearnedPoints(Skills.IRON_NATURE) > 0
                || rec.getLearnedPoints(Skills.IRON_ELDRITCH) > 0;
        // Goety（诡术，2026-09-20）：通用强度/通用灵魂折扣 + 9 流派精通
        boolean hasGoety = rec.getLearnedPoints(Skills.GOETY_POTENCY) > 0
                || rec.getLearnedPoints(Skills.GOETY_SOUL_DISCOUNT) > 0
                || rec.getLearnedPoints(Skills.GOETY_ABYSS) > 0 || rec.getLearnedPoints(Skills.GOETY_FROST) > 0
                || rec.getLearnedPoints(Skills.GOETY_GEOMANCY) > 0 || rec.getLearnedPoints(Skills.GOETY_NECROMANCY) > 0
                || rec.getLearnedPoints(Skills.GOETY_NETHER) > 0 || rec.getLearnedPoints(Skills.GOETY_STORM) > 0
                || rec.getLearnedPoints(Skills.GOETY_VOID) > 0 || rec.getLearnedPoints(Skills.GOETY_WILD) > 0
                || rec.getLearnedPoints(Skills.GOETY_WIND) > 0;
        if (hasArs || hasIron || hasGoety) {
            rows.add(new String[]{"—— " + t("panel_cat_magic") + " ——", "", "#777777"});
        }
        // 新生魔艺（装了显示数值；学了但没装显示红字）
        if (org.zifeng.skilltree.compat.ArsNouveauCompat.isLoaded()) {
            double arsAmp = SkillEffects.getManaAmpPercent(rec);
            if (arsAmp > 0) addRow(rows, t("panel_ars_amp"), arsAmp * 100, "+%.0f%%");
            double arsRegen = SkillEffects.getArsManaRegenPercent(rec);
            if (arsRegen > 0) addRow(rows, t("panel_ars_regen"), arsRegen * 100, "+%.0f%%");
        } else if (hasArs) {
            rows.add(new String[]{t("panel_ars"), t("panel_not_installed"), "#FF5555"});
        }
        // 铁魔法（装了显示数值；学了但没装显示红字）
        if (org.zifeng.skilltree.compat.IronSpellsCompat.isLoaded()) {
            double ironAmp = SkillEffects.getIronManaAmpPercent(rec);
            if (ironAmp > 0) addRow(rows, t("panel_iron_amp"), ironAmp * 100, "+%.0f%%");
            double ironRegen = SkillEffects.getIronManaRegenPercent(rec);
            if (ironRegen > 0) addRow(rows, t("panel_iron_regen"), ironRegen * 100, "+%.0f%%");
            double castTime = SkillEffects.getIronCastTimePercent(rec);
            if (castTime > 0) addRow(rows, t("panel_iron_cast"), castTime * 100, "-%.0f%%");
            double cooldown = SkillEffects.getIronCooldownPercent(rec);
            if (cooldown > 0) addRow(rows, t("panel_iron_cd"), cooldown * 100, "-%.0f%%");
            // 9 流派强度
            double fire = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_FIRE);
            if (fire > 0) addRow(rows, t("panel_school_fire"), fire * 100, "+%.0f%%");
            double ice = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_ICE);
            if (ice > 0) addRow(rows, t("panel_school_ice"), ice * 100, "+%.0f%%");
            double lightning = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_LIGHTNING);
            if (lightning > 0) addRow(rows, t("panel_school_lightning"), lightning * 100, "+%.0f%%");
            double holy = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_HOLY);
            if (holy > 0) addRow(rows, t("panel_school_holy"), holy * 100, "+%.0f%%");
            double ender = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_ENDER);
            if (ender > 0) addRow(rows, t("panel_school_ender"), ender * 100, "+%.0f%%");
            double blood = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_BLOOD);
            if (blood > 0) addRow(rows, t("panel_school_blood"), blood * 100, "+%.0f%%");
            double evocation = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_EVOCATION);
            if (evocation > 0) addRow(rows, t("panel_school_evocation"), evocation * 100, "+%.0f%%");
            double nature = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_NATURE);
            if (nature > 0) addRow(rows, t("panel_school_nature"), nature * 100, "+%.0f%%");
            double eldritch = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_ELDRITCH);
            if (eldritch > 0) addRow(rows, t("panel_school_eldritch"), eldritch * 100, "+%.0f%%");
        } else if (hasIron) {
            rows.add(new String[]{t("panel_iron"), t("panel_not_installed"), "#FF5555"});
        }
        // Goety（诡术，2026-09-20）：装了显示数值；学了但没装显示红字
        if (org.zifeng.skilltree.compat.GoetyCompat.isLoaded()) {
            double goetyPotency = SkillEffects.getGoetyPotency(rec);
            if (goetyPotency > 0) addRow(rows, t("panel_goety_potency"), goetyPotency, "+%.0f");
            double goetySoul = SkillEffects.getGoetySoulDiscountPercent(rec);
            if (goetySoul > 0) addRow(rows, t("panel_goety_soul"), goetySoul * 100, "-%.0f%%");
            // 9 流派精通：每个技能同时给该流派的【强度】与【灵魂折扣】→ 合成一行显示，
            //   否则 9 个技能要占 18 行，面板太长。
            addGoetySchoolRow(rows, rec, Skills.GOETY_ABYSS, "panel_goety_abyss");
            addGoetySchoolRow(rows, rec, Skills.GOETY_FROST, "panel_goety_frost");
            addGoetySchoolRow(rows, rec, Skills.GOETY_GEOMANCY, "panel_goety_geomancy");
            addGoetySchoolRow(rows, rec, Skills.GOETY_NECROMANCY, "panel_goety_necromancy");
            addGoetySchoolRow(rows, rec, Skills.GOETY_NETHER, "panel_goety_nether");
            addGoetySchoolRow(rows, rec, Skills.GOETY_STORM, "panel_goety_storm");
            addGoetySchoolRow(rows, rec, Skills.GOETY_VOID, "panel_goety_void");
            addGoetySchoolRow(rows, rec, Skills.GOETY_WILD, "panel_goety_wild");
            addGoetySchoolRow(rows, rec, Skills.GOETY_WIND, "panel_goety_wind");
        } else if (hasGoety) {
            rows.add(new String[]{t("panel_goety"), t("panel_not_installed"), "#FF5555"});
        }

        // ============ 子枫的馈赠（纵列7，2026-08-25：时间/移动/飞行/挖掘洗礼 + 增幅） ============
        boolean hasGift = false;
        for (String g : Skills.GIFT_SKILLS) {
            if (rec.getLearnedPoints(g) > 0) {
                hasGift = true;
                break;
            }
        }
        if (hasGift) {
            rows.add(new String[]{"—— " + t("panel_cat_gift") + " ——", "", "#777777"});
            // 时间洗礼/风暴/洪流：等级
            for (String timeSkill : java.util.List.of(Skills.GIFT_TIME_BAPTISM, Skills.GIFT_TIME_STORM, Skills.GIFT_TIME_FLOOD)) {
                int t = rec.getLearnedPoints(timeSkill);
                if (t > 0) {
                    String enabledMark = rec.isEnabled(timeSkill) ? "" : t("panel_off");
                    long interval = Skills.getGiftIntervalTicks(timeSkill) / 20;
                    // 2026-08-25：简短格式避免叠加（时间风暴每次+5、洪流+10）
                    int pts = Skills.GIFT_TIME_FLOOD.equals(timeSkill) ? 10 : Skills.GIFT_TIME_STORM.equals(timeSkill) ? 5 : 1;
                    rows.add(new String[]{Skills.getDisplayNameComponent(timeSkill).getString() + enabledMark,
                            t + t("unit_lv") + "/" + (interval / 60) + t("unit_min") + "+" + pts, "#E0B6C8"});
                }
            }
            // 移动/飞行/挖掘/击杀洗礼：等级 + 当前需求（简短格式）
            for (String dSkill : java.util.List.of(Skills.GIFT_MOVE_BAPTISM, Skills.GIFT_FLY_BAPTISM, Skills.GIFT_MINE_BAPTISM, Skills.GIFT_KILL_BAPTISM)) {
                int lv = rec.getLearnedPoints(dSkill);
                if (lv > 0) {
                    String enabledMark = rec.isEnabled(dSkill) ? "" : t("panel_off");
                    String unit = Skills.GIFT_KILL_BAPTISM.equals(dSkill) ? t("unit_kill") : (Skills.GIFT_MINE_BAPTISM.equals(dSkill) ? t("unit_block") : t("unit_meter"));
                    long need = Skills.getGiftDistanceRequirement(dSkill, Math.max(1, rec.getActiveLevel(dSkill)));
                    // 增幅等级
                    String ampSkill = Skills.getGiftAmpSkill(dSkill);
                    int ampLv = ampSkill != null && rec.isEnabled(ampSkill) ? rec.getActiveLevel(ampSkill) : 0;
                    rows.add(new String[]{Skills.getDisplayNameComponent(dSkill).getString() + enabledMark,
                            lv + t("unit_lv") + "/" + need + unit + "+" + (1 + ampLv), "#E0B6C8"});
                }
            }
            // 增幅单独行（简短）
            for (String aSkill : java.util.List.of(Skills.GIFT_MOVE_AMP, Skills.GIFT_FLY_AMP, Skills.GIFT_MINE_AMP, Skills.GIFT_KILL_AMP)) {
                int alv = rec.getLearnedPoints(aSkill);
                if (alv > 0) {
                    rows.add(new String[]{Skills.getDisplayNameComponent(aSkill).getString() + (rec.isEnabled(aSkill) ? "" : t("panel_off")),
                            alv + "/" + Skills.getGiftMaxPoints(aSkill), "#E0B6C8"});
                }
            }
        }
        rows.add(new String[]{t("panel_skill_point"), String.format("%.1f", Math.max(0, skillPoints)), "#FFFFD700"});
        cachedRows = rows; // 缓存到下次重建（1 秒）
        return rows;
    }

    private void renderAttributesPanel(GuiGraphics guiGraphics) {
        // 面板隐藏开关：关闭时直接不渲染（仅保留恢复按钮）
        if (!Config.PANEL_VISIBLE.get()) {
            return;
        }
        // 先提交按钮文字批次，再用 guiOverlay（无深度测试，无条件覆盖）画面板背景，彻底盖住下层文字
        guiGraphics.flush();
        java.util.List<String[]> rows = collectRows();
        if (Config.PANEL_POSITION.get() == 1) {
            renderPanelBottom(guiGraphics, rows);
        } else {
            renderPanelRight(guiGraphics, rows);
        }
    }

    /** 右侧竖版面板（默认）：带滚动区域边框 + 右侧滚动条 */
    private void renderPanelRight(GuiGraphics guiGraphics, java.util.List<String[]> rows) {
        int x = width - PANEL_WIDTH - 10;
        int y = 50;
        int panelTop = y;
        int panelBottom = height - 30;
        // guiOverlay = NO_DEPTH_TEST + COLOR_WRITE（原版 tooltip 背景同款）→ 面板永远在最上层，下层按钮文字/描述透不过来
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, panelTop - 2, x + PANEL_WIDTH + 2, panelBottom, 0xFF101010);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, panelTop - 2, x + PANEL_WIDTH + 2, panelTop, 0xFF87CEEB);
        drawCrispString(guiGraphics, t("panel_title"), x + 4, panelTop + 4, 0xFFFFD700, false);

        // 计算滚动范围并钳制
        int visible = panelVisibleRows();
        int maxScroll = Math.max(0, rows.size() - visible);
        panelScroll = Math.max(0, Math.min(maxScroll, panelScroll));

        // 滚动区域：从标题下沿到面板底部（底部留 22px 给"滚轮滚动"提示）
        int scrollTop = panelTop + 18;
        int scrollBottom = panelBottom - 20;
        // 滚动区域边框（左右淡蓝竖线 + 上下横线，圈出可滚动区域）
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, scrollTop - 1, x + PANEL_WIDTH + 2, scrollTop, 0x554488AA);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, scrollBottom, x + PANEL_WIDTH + 2, scrollBottom + 1, 0x554488AA);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, scrollTop, x - 1, scrollBottom, 0x554488AA);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x + PANEL_WIDTH + 1, scrollTop, x + PANEL_WIDTH + 2, scrollBottom, 0x554488AA);

        // 绘制可见行（从 panelScroll 开始）
        int line = scrollTop;
        for (int i = panelScroll; i < rows.size() && i < panelScroll + visible; i++) {
            String[] row = rows.get(i);
            String color = row.length > 2 ? row[2] : "#FFFFFFFF";
            int c = parseColor(color);
            if (row[1].isEmpty()) {
                // 分隔标题行：居中灰色小字
                drawCrispString(guiGraphics, row[0], x + PANEL_WIDTH / 2, line, c, true);
            } else {
                // label 左对齐（超宽裁剪，防止与右侧数值重叠）
                String label = row[0];
                int labelMaxW = PANEL_WIDTH - 70; // 留出数值区（右对齐 ~60px + 边距）
                label = clipToWidth(label, labelMaxW);
                drawCrispString(guiGraphics, label, x + 4, line, 0xFFAAAAAA, false);
                // 数值右对齐到滚动条左侧（滚动条在 x+PANEL_WIDTH-6，留 4px 间隔 → 数值起点 = x+PANEL_WIDTH-10-字体宽度）
                String value = row[1];
                drawCrispString(guiGraphics, value, x + PANEL_WIDTH - 10 - lw(value), line, c, false);
            }
            line += 12;
        }
        // 右侧滚动条（轨道 + 滑块；滑块高度按可见比例，位置随 panelScroll 移动）
        // 轨道贴右缘（x+PANEL_WIDTH-3 到 x+PANEL_WIDTH-1），数值列右对齐到轨道左侧 x+PANEL_WIDTH-10
        int barX = x + PANEL_WIDTH - 3;
        int barTrackTop = scrollTop;
        int barTrackBottom = scrollBottom - 2;
        int barTrackH = barTrackBottom - barTrackTop;
        // 轨道
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), barX, barTrackTop, barX + 2, barTrackBottom, 0xFF333333);
        if (maxScroll > 0) {
            // 滑块：高度 = 可见比例 × 轨道高，位置 = panelScroll/maxScroll 映射
            int thumbH = Math.max(10, barTrackH * visible / rows.size());
            int thumbY = barTrackTop + (int) ((double) panelScroll / maxScroll * (barTrackH - thumbH));
            guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), barX, thumbY, barX + 2, thumbY + thumbH, 0xFF87CEEB);
        } else {
            // 无滚动时滑块占满轨道（淡色表示无需滚动）
            guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), barX, barTrackTop, barX + 2, barTrackBottom, 0xFF446688);
        }
        // 滚动指示
        if (maxScroll > 0) {
            drawCrispString(guiGraphics, t("panel_scroll_hint"), x + 4, panelBottom - 14, 0xFF888888, false);
        }
    }

    /** 底部横版面板（3 列分页） */
    private void renderPanelBottom(GuiGraphics guiGraphics, java.util.List<String[]> rows) {
        int h = 130;
        int x = 10;
        int y = height - h - 10;
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, width - 10 + 2, y + h + 2, 0xF0101010);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, width - 10 + 2, y, 0xFF87CEEB);
        drawCrispString(guiGraphics, t("panel_title"), x + 4, y + 4, 0xFFFFD700, false);

        // 每行 3 列；可见行数 = (h - 24) / 12
        int cols = 3;
        int colW = (width - 20 - 20) / cols;
        int visibleRows = (h - 24) / 12;
        int pageRows = visibleRows * cols;
        int maxScroll = Math.max(0, rows.size() - pageRows);
        panelScroll = Math.max(0, Math.min(maxScroll, panelScroll));

        for (int i = panelScroll; i < rows.size() && i < panelScroll + pageRows; i++) {
            String[] row = rows.get(i);
            int idx = i - panelScroll;
            int col = idx % cols;
            int r = idx / cols;
            String color = row.length > 2 ? row[2] : "#FFFFFFFF";
            int c = parseColor(color);
            int px = x + 4 + col * colW;
            int py = y + 18 + r * 12;
            if (row[1].isEmpty()) {
                // 分隔标题行：灰色小字（跨整行 3 列居中）
                drawCrispString(guiGraphics, row[0], x + width / 2 - 10, py, c, true);
            } else {
                // label 超宽裁剪（防与右侧数值重叠）
                String label = row[0];
                label = clipToWidth(label, 56);
                drawCrispString(guiGraphics, label, px, py, 0xFFAAAAAA, false);
                drawCrispString(guiGraphics, row[1], px + 62, py, c, false);
            }
        }
        if (maxScroll > 0) {
            drawCrispString(guiGraphics, t("panel_page_hint"), x + 4, y + h - 12, 0xFF888888, false);
        }
    }

    private void addRow(java.util.List<String[]> rows, String name, double value, String fmt) {
        rows.add(new String[]{name, String.format(fmt, value)});
    }

    /**
     * Goety 流派精通行（★ 2026-09-20）：该技能同时给流派的<b>强度</b>（flat 整数）与<b>灵魂折扣</b>（比例），
     * 合成一行显示为 {@code +80 / -80%}，避免 9 个技能占 18 行把面板拖太长。
     * <p>未学该流派（强度为 0）→ 不显示该行。
     */
    private void addGoetySchoolRow(java.util.List<String[]> rows,
                                   org.zifeng.skilltree.data.PlayerSkillRecord rec,
                                   String schoolSkillId, String labelKey) {
        double potency = SkillEffects.getGoetySchoolPotency(rec, schoolSkillId);
        if (potency <= 0) {
            return;
        }
        double discount = SkillEffects.getGoetySchoolDiscountPercent(rec, schoolSkillId);
        rows.add(new String[]{t(labelKey), String.format("+%.0f / -%.0f%%", potency, discount * 100)});
    }

    /**
     * 属性值读取（数据源可切换，2026-09-11）：
     * <ul>
     *   <li>{@code true}（默认）：直接读<b>原版实时属性值</b>
     *       （{@code AttributeInstance.getValue()}）——自动包含装备 / 药水 / 其他模组的属性修饰符</li>
     *   <li>{@code false}：旧行为，仅按技能内部计算（不含外部修饰符）</li>
     * </ul>
     * 客户端属性值由 Forge 的属性同步包下发，因此能读到服务端技能加成。
     * 若某个属性未被同步，读到的会是基础值 → 可用配置项切回旧行为。
     */
    private double attrVal(net.minecraft.world.entity.player.Player player,
                           net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                           org.zifeng.skilltree.data.PlayerSkillRecord rec) {
        if (org.zifeng.skilltree.client.SkillKeyBinds.isPanelUseVanillaAttr()) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(attribute);
            if (inst != null) {
                return inst.getValue();
            }
        }
        // 回退：技能内部计算（旧行为）。⚠️ 必须直调 SkillEffects，不能递归调用本方法。
        return SkillEffects.getComputedValue(player, attribute, rec);
    }

    /** 解析 #RRGGBB 颜色字符串 → ARGB int（默认白色） */
    static int parseColor(String hex) {
        try {
            if (hex != null && hex.startsWith("#") && hex.length() == 7) {
                return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFFFFFFFF;
    }

    /**
     * 圆角矩形填充（主体矩形 + 四角阶梯近似，radius=圆角半径）；默认用 guiOverlay（无深度测试，供 L1/L3 层用）。
     * ⚠️ L4 技能树本体内容（如列标题背景）必须传 RenderType.gui() 重载，否则叠加到最上层！
     */
    private void fillRoundedRect(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int radius, int color) {
        fillRoundedRect(guiGraphics, left, top, right, bottom, radius, color, net.minecraft.client.renderer.RenderType.guiOverlay());
    }

    /** 圆角矩形填充，可指定 RenderType（列标题必须传 RenderType.gui()，与按钮同层、带深度测试） */
    private void fillRoundedRect(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int radius, int color,
                                 net.minecraft.client.renderer.RenderType type) {
        if (right <= left || bottom <= top) {
            return;
        }
        int r = Math.max(1, Math.min(radius, (right - left) / 2));
        r = Math.min(r, (bottom - top) / 2);
        // 主体
        guiGraphics.fill(type, left + r, top, right - r, bottom, color);
        guiGraphics.fill(type, left, top + r, right, bottom - r, color);
        // 四角阶梯近似（每角 2 个方块，形成圆角）
        int half = r / 2;
        // 左上
        guiGraphics.fill(type, left, top + half, left + half, top + r, color);
        guiGraphics.fill(type, left + half, top, left + r, top + half, color);
        // 右上
        guiGraphics.fill(type, right - half, top, right, top + half, color);
        guiGraphics.fill(type, right - r, top + half, right - half, top + r, color);
        // 左下
        guiGraphics.fill(type, left, bottom - r, left + half, bottom - half, color);
        guiGraphics.fill(type, left + half, bottom - half, left + r, bottom, color);
        // 右下
        guiGraphics.fill(type, right - r, bottom - half, right - half, bottom, color);
        guiGraphics.fill(type, right - half, bottom - half, right, bottom, color);
    }

    /** 某一行在圆角矩形里的左内缩量（与旧实现逐行等价；调用方自行处理 i 的上下分支） */
    private static int roundInset(int radius, int cy) {
        if (cy <= 0) {
            return 0;
        }
        final double rr = radius;
        return (int) Math.round(rr - Math.sqrt(Math.max(0.0, rr * rr - (double) cy * cy)));
    }

    /**
     * 圆角矩形填充（真圆角，扫描线法；2026-09-19 新增，2026-09-20 性能优化）。
     *
     * <p>相比 {@link #fillRoundedRect} 的「四角阶梯近似」，这里每行都按圆的方程算左右内缩量，
     * 圆弧是平滑的（1px 精度）。用 {@code RenderType.gui()}（与技能贴片同层、有深度），不是 guiOverlay。
     *
     * <h2>★ 2026-09-20 性能优化：合并中间同宽的行</h2>
     * 旧实现无条件逐行 {@code fill}，但中间段的 {@code cy} 恒为 0、inset 恒为 0 ——
     * <b>同一个矩形被拆成了几百次 fill</b>：
     * <pre>
     *   大贴片（H≈480） : 464 次 → 25 次   （18×）
     *   技能子贴片       : 742 次 → 34 次   （22×）
     *   背景贴片合计     : 1308 次 → 127 次 （10×）
     * </pre>
     * 现在把 {@code cy == 0} 的<b>连续行段</b>一次性画完（它们本来就是同一个矩形）。
     *
     * <p>⚠️ <b>严格保持与旧实现逐行等价</b>：旧代码是 {@code if (i < r) ... else if (i >= h-r) ...}，
     * 即 {@code i < r} 优先 —— 当 {@code h <= 2r} 时上下圆角区间会重叠，此时重叠行按「顶部」算。
     * 所以这里不搞「顶部 r 行 + 底部 r 行」的分块写法，而是逐行判定 + 收集中间段，
     * 让 {@code h <= 2r} 的情况自然退化为逐行绘制（不合并），结果与旧版完全一致。
     */
    private void fillRound(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int radius, int color) {
        final int w = x1 - x0;
        final int h = y1 - y0;
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            guiGraphics.fill(x0, y0, x1, y1, color);
            return;
        }
        // 中间段（cy == 0、inset == 0 的连续行）的起止；-1 = 尚未遇到
        int midFrom = -1;
        int midTo = -1;
        for (int i = 0; i < h; i++) {
            // 与旧实现完全相同的分支优先级（i < r 优先）
            final int cy;
            if (i < r) {
                cy = r - i;
            } else if (i >= h - r) {
                cy = r - (h - 1 - i);
            } else {
                cy = 0;
            }
            if (cy != 0) {
                int inset = roundInset(r, cy);
                guiGraphics.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, color);
            } else {
                if (midFrom < 0) {
                    midFrom = i;
                }
                midTo = i + 1;
            }
        }
        // 中间同宽段：一次 fill 代替 (midTo - midFrom) 次
        if (midFrom >= 0) {
            guiGraphics.fill(x0, y0 + midFrom, x1, y0 + midTo, color);
        }
    }

    /**
     * 圆角矩形描边（真 1px 圆环，扫描线法）。
     *
     * <p>⚠️ 不能用「外圈色块 + 内缩底色盖回」的偷懒做法 —— 贴片内部先画了按键格底色，
     * 盖回会把格底色一起擦掉。所以这里逐行只画最左/最右一像素，四角按圆的方程描点。
     */
    private void strokeRound(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int radius, int color) {
        final int w = x1 - x0;
        final int h = y1 - y0;
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            guiGraphics.fill(x0, y0, x1, y0 + 1, color);
            guiGraphics.fill(x0, y1 - 1, x1, y1, color);
            guiGraphics.fill(x0, y0, x0 + 1, y1, color);
            guiGraphics.fill(x1 - 1, y0, x1, y1, color);
            return;
        }
        final double rr = r;
        // 四条直边
        guiGraphics.fill(x0 + r, y0, x1 - r, y0 + 1, color);
        guiGraphics.fill(x0 + r, y1 - 1, x1 - r, y1, color);
        guiGraphics.fill(x0, y0 + r, x0 + 1, y1 - r, color);
        guiGraphics.fill(x1 - 1, y0 + r, x1, y1 - r, color);
        // 四角弧（逐行描两个像素）
        for (int i = 0; i < r; i++) {
            int cy = r - i;
            int inset = (int) Math.round(rr - Math.sqrt(Math.max(0.0, rr * rr - (double) cy * cy)));
            int lx = x0 + inset;
            int rx = x1 - inset - 1;
            guiGraphics.fill(lx, y0 + i, lx + 1, y0 + i + 1, color);
            guiGraphics.fill(rx, y0 + i, rx + 1, y0 + i + 1, color);
            guiGraphics.fill(lx, y1 - i - 1, lx + 1, y1 - i, color);
            guiGraphics.fill(rx, y1 - i - 1, rx + 1, y1 - i, color);
        }
    }

    /** 取当前选中类别的强调色（★ 仅供类别按钮行使用；技能行请用 {@link #accentOf}） */
    private int categoryAccent() {
        return CATEGORY_COLORS[Math.max(0, Math.min(CATEGORY_COLORS.length - 1, selectedCategory))];
    }
    // ========================================================================
    // 搜索（2026-09-20 新增）
    //   用户需求：标题行右侧空旷 → 做一个搜索功能；结果为跨类别，清空恢复原类别。
    // ========================================================================

    /** 是否处于搜索态（有关键词） */
    private boolean isSearching() {
        return !searchQuery.isEmpty();
    }

    /** 搜索框左边界（右对齐到标题行右内边界） */
    private int searchX() {
        return frameRight() - FRAME_LINE - FRAME_PAD_X - SEARCH_W;
    }

    /** 搜索框上边界（标题行内垂直居中） */
    private int searchY() {
        return titleFrameTop() + (titleFrameH() - SEARCH_H) / 2;
    }

    /** 鼠标是否在搜索框内 */
    private boolean isOverSearchBox(double mouseX, double mouseY) {
        int x = searchX();
        int y = searchY();
        return mouseX >= x && mouseX <= x + SEARCH_W && mouseY >= y && mouseY <= y + SEARCH_H;
    }

    /** ✕ 清空按钮的左边界（搜索框最右侧，宽度恒定预留，见 {@link #SEARCH_CLEAR_W}） */
    private int searchClearX() {
        return searchX() + SEARCH_W - SEARCH_CLEAR_W;
    }

    /**
     * 鼠标是否在 ✕ 清空按钮上。
     * <p>★ 2026-09-20 用户要求：无关键词时不显示也不可点 —— 没东西可清的时候
     * 摆一个按钮只会干扰视线（占位宽度仍然保留，避免文本左右跳）。
     */
    private boolean isOverSearchClear(double mouseX, double mouseY) {
        if (!isSearching()) {
            return false;
        }
        final int x = searchClearX();
        final int y = searchY();
        return mouseX >= x && mouseX <= x + SEARCH_CLEAR_W
                && mouseY >= y && mouseY <= y + SEARCH_H;
    }

    // =======================================================================
    // 搜索输入控件（★ 2026-09-20 改用原版 EditBox 承载，解决输入法打不了中文）
    // =======================================================================

    /**
     * 输入控件右端需要让出的宽度 = ✕ 清空按钮占位 + 与文字的最小间距。
     * <p>避让 ✕ 是必需的：否则长关键词会画到 ✕ 底下，视觉上重叠。
     */
    private int searchBoxRightReserve() {
        return SEARCH_CLEAR_W + (SEARCH_PAD - 1);
    }

    /**
     * 按当前布局建立/重建输入控件，并把焦点状态同步回去。
     *
     * <p>调用时机：{@link #init()}（开界面 / 1.20.1 的 resize）与
     * {@link #resize(net.minecraft.client.Minecraft, int, int)}（1.21.1 的 resize 不回调 init）。
     * <p>若几何没变则<b>不重建</b> —— 重建会丢掉光标位置与选区，
     * 而 1.20.1 上 resize 会先经 super 触发 init、再被我们的 resize 调一次，
     * 不做这个判断就会白重建一轮。
     */
    private void ensureSearchBox() {
        final int x = searchX();
        final int y = searchY();
        final int w = SEARCH_W;
        final int h = SEARCH_H;
        if (searchBox != null
                // ★ 2026-09-21：字体实例变化时必须重建。Font 在资源重载 / 字体类模组
                //   异步初始化后会被整体替换，而原版 EditBox 的 font 字段是 final 无法更新；
                //   不重建的话，控件内部仍用旧字体（点击定位光标会偏，且字形图集可能过期）。
                && searchBox.getTextFont() == font
                && searchBox.getX() == x + SkillSearchBox.PAD_X
                && searchBox.getY() == y + SkillSearchBox.PAD_Y
                && searchBox.getWidth() == Math.max(1, w - SkillSearchBox.PAD_X - searchBoxRightReserve())
                && searchBox.getHeight() == Math.max(1, h - SkillSearchBox.PAD_Y * 2)) {
            // 几何未变：只需刷新占位文案（语言可能刚被重载过）
            searchBox.setPlaceholder(t("search_hint"));
            return;
        }

        searchBox = new SkillSearchBox(font, x, y, w, h,
                searchBoxRightReserve(),
                t("search_hint"),
                0xFF2A2A34,     // 正文色（与原自绘方案一致）
                0xFF9A9AA6);    // 占位色（与原自绘方案一致）
        // ⚠️ 先 setValue 再 setResponder：否则建立时就触发一次 responder，
        //   而此时 LANG_CACHE 等可能还没清好，会白跑一轮搜索。
        syncingSearchBox = true;
        searchBox.setValue(searchQuery);
        syncingSearchBox = false;
        // ★ 学自 AE2：EditBox 的 responder 是「内容变化」的唯一出口，
        //   在这里把值收回 searchQuery 并刷新列表。
        searchBox.setResponder(this::onSearchBoxChanged);

        // 重建后必须重新施加焦点（见 rebuildWidgets/clearFocus 会清空焦点）
        applySearchFocus();
    }

    /** 输入控件内容变化 → 更新关键词并刷新列表（由 {@link #ensureSearchBox} 注册到 responder） */
    private void onSearchBoxChanged(String text) {
        if (syncingSearchBox) {
            return; // 程序回写触发：{@link #applySearchQuery} 自己会刷新，不必重入
        }
        if (text.equals(searchQuery)) {
            return;
        }
        rememberNormalScrollBeforeSearch();
        searchQuery = text;
        searchHistoryIndex = -1;   // 又动手打字了 → 不再继续翻历史
        scrollY = text.isEmpty() ? restoreNormalScrollY() : 0;
        rebuildButtons();
    }

    /**
     * 程序性修改关键词（清空 / 翻历史 / Esc 都用它），同时同步到输入控件。
     *
     * <p>用 {@link #syncingSearchBox} 拦住 responder，保证「写值」与「刷新列表」
     * 各只发生一次，不会因为回写而重复 rebuild。
     */
    private void applySearchQuery(String q) {
        rememberNormalScrollBeforeSearch();
        searchQuery = q;
        if (searchBox != null && !q.equals(searchBox.getValue())) {
            syncingSearchBox = true;
            searchBox.setValue(q);      // 会把光标移到末尾（符合「换了个词→接着编辑」的直觉）
            syncingSearchBox = false;
        }
        scrollY = q.isEmpty() ? restoreNormalScrollY() : 0;
        rebuildButtons();
    }

    /**
     * 切换搜索框的输入焦点状态。
     *
     * <p><b>这是修复输入法问题的关键</b>：{@code Screen.setFocused(控件)} 会让
     * {@code screen.getFocused()} 返回该控件，输入法类模组（IMBlocker 等）据此
     * 才会放行系统输入法、允许切中文。
     *
     * <p>失焦时要显式把 Screen 的焦点清掉，否则 {@code getFocused()} 仍指向输入框，
     * 输入法模组会一直以为还在文本输入场景。
     */
    private void setSearchFocus(boolean focused) {
        searchFocused = focused;
        applySearchFocus();
    }

    /** 把 {@link #searchFocused} 施加到屏幕焦点系统（控件不存在时什么也不做） */
    private void applySearchFocus() {
        if (searchBox == null) {
            return;
        }
        if (searchFocused) {
            // setFocused(GuiEventListener) 来自 AbstractContainerEventHandler（public）
            this.setFocused(searchBox);
        } else {
            if (this.getFocused() == searchBox) {
                this.setFocused((net.minecraft.client.gui.components.events.GuiEventListener) null);
            }
            searchBox.setFocused(false);
        }
    }

    /**
     * 技能所属类别的索引（0~9）；未归类返回 -1。
     * <p>★ 搜索 / 每行类别色 / tooltip 前置分类提示 三处共用，避免各写一份遍历。
     */
    private static int categoryIndexOf(String skillId) {
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            if (categorySkills(i).contains(skillId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 某技能的类别强调色。
     *
     * <p>★ 为什么不能用 {@link #categoryAccent()}：搜索结果是<b>跨类别</b>的，
     * 若统一取「当前选中类别」的颜色，整屏会是一个颜色但技能来自不同类别 ——
     * 视觉上完全错乱。所以每行按自己的 skillId 取色。
     */
    private static int accentOf(String skillId) {
        int ci = categoryIndexOf(skillId);
        return ci < 0 ? 0xFF9A9A9A : CATEGORY_COLORS[ci];
    }

    /**
     * 搜索用文本缓存（技能ID → 小写的「ID \u0000 名称 \u0000 描述」）。
     *
     * <p>⚠️ 2026-09-20 性能：原实现每次匹配都对 单个技能 做 3 次 {@code getString()} + {@code toLowerCase}。
     * 一次搜索 = 132 个技能 × 3 次语言查询 ≈ 400 次，且<b>每敲一个字符都要重来一遍</b>。
     * 现在把拼接结果按技能缓存，后续只做一次 {@code contains}。
     *
     * <p>用 {@code \u0000} 分隔字段：避免「ID 尾部 + 名称开头」被跨字段拼出假匹配。
     * <p>语言重载时需清空（与 {@link #LANG_CACHE} 同步处理，见 {@link #init()}）。
     */
    private static final Map<String, String> SEARCH_TEXT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 搜索结果缓存（★ 2026-09-20 性能）。
     *
     * <p>为什么必须有：{@link #searchResults} 会被 {@link #rebuildButtons()} 调用，
     * 而 {@code rebuildButtons} 不只按键触发 —— <b>服务端每同步一次技能点/等级就会重建一次</b>
     * （拖动进度条时几乎每 tick 都有），加上窗口 resize 也会重建。
     * 不缓存的话，搜索态下每 tick 都要把 134 个技能的全部文本重新完整扫描一遍。
     *
     * <p>只缓存<b>最后一次</b>关键词就够：关键词只在按键时变，
     * 一次搜索引发的多次重复调用必然是同词。
     */
    private static String searchCacheQuery = null;
    private static List<String> searchCacheResult = null;

    /** 清掉搜索相关的全部缓存（语言重载 / 界面重开时调用，否则会拿着旧语言的文本继续匹配） */
    private static void clearSearchCaches() {
        SEARCH_TEXT_CACHE.clear();
        searchCacheQuery = null;
        searchCacheResult = null;
    }

    /** 取某技能的搜索文本（小写，含 ID/名称/描述），带缓存 */
    private static String searchTextOf(String skillId) {
        String cached = SEARCH_TEXT_CACHE.get(skillId);
        if (cached != null) {
            return cached;
        }
        String text = (skillId
                + "\u0000" + Skills.getDisplayNameComponent(skillId).getString()
                + "\u0000" + Skills.getDescriptionComponent(skillId).getString())
                .toLowerCase(java.util.Locale.ROOT);
        SEARCH_TEXT_CACHE.put(skillId, text);
        return text;
    }

    /**
     * 技能是否匹配关键词（名称 / 描述 / 技能 ID；大小写不敏感）。
     *
     * <p>行为：把小写后的关键词在「技能 ID + 显示名 + 描述」里找子串。
     * 覆盖「直接打汉字」「打英文名」「打技能 ID 片段」三种习惯。
     *
     * <p>⚠️ 必须走 {@code Locale.ROOT} 小写，不能用默认 Locale ——
     * 土耳其语环境下 {@code "I".toLowerCase()} 会变成 ı（无点小写 i），
     * 与关键词里打出的普通 {@code i} 不相等，导致含大写 I 的技能 ID / 英文名搜不到。
     *
     * <p>★ 2026-09-20：曾经的拼音匹配（移植 PinIn）已按用户要求移除 ——
     * 本模组有纯英文玩家，拼音对他们无用，不值得为此多背一个词典资源。
     */
    private static boolean matchesQuery(String skillId, String q) {
        if (q == null || q.isEmpty()) {
            return true;
        }
        return searchTextOf(skillId).contains(q.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * 搜索结果：遍历全部 10 个类别，收集匹配的技能。
     * <p>顺序 = 类别顺序（0~9）+ 类别内原顺序 → 稳定可预期，不随输入抖动。
     * <p>带一层「上次关键词」缓存，见 {@link #searchCacheQuery}。
     */
    private static List<String> searchResults(String q) {
        if (q.equals(searchCacheQuery) && searchCacheResult != null) {
            return searchCacheResult;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            for (String s : categorySkills(i)) {
                if (matchesQuery(s, q)) {
                    out.add(s);
                }
            }
        }
        searchCacheQuery = q;
        searchCacheResult = out;
        return out;
    }

    /** 当前列表数据源：搜索中 → 跨类别结果；否则 → 当前选中的类别 */
    private List<String> currentSkillSource() {
        return isSearching() ? searchResults(searchQuery) : categorySkills(selectedCategory);
    }

    /**
     * 只清空关键词，<b>保持输入焦点</b>（★ 2026-09-20 用户要求：右键清空 / ✕ 按钮）。
     *
     * <p>用于「词打错了、想马上重打一个」的场景 —— 没必要逼玩家再点一次输入框。
     * <p>★ 清空<b>不</b>写入历史：玩家按清空就是想扔掉这个词，再记进历史只会碍事。
     */
    private void clearSearchQuery() {
        if (searchQuery.isEmpty()) {
            return;
        }
        searchSelectedId = null;
        searchHistoryIndex = -1;
        // ★ 2026-09-20：必须走 applySearchQuery —— 它会把输入控件的内容一起清掉，
        //   否则控件里还留着旧文字，再敲一个字就会连带旧词一起触发搜索。
        applySearchQuery("");
    }

    /** 清空搜索关键词并退出输入状态，恢复原类别列表（Esc / 彻底退出搜索用） */
    private void clearSearch() {
        if (searchQuery.isEmpty() && !searchFocused) {
            return;
        }
        searchSelectedId = null;   // ★ 同时清掉选中项
        searchHistoryIndex = -1;   // ★ 历史游标归位
        setSearchFocus(false);     // ★ 同步退出输入焦点（否则输入法仍被放行）
        applySearchQuery("");
    }

    // =======================================================================
    // 搜索历史（★ 2026-09-20 用户要求：JEI 式操作 —— ↑↓ 翻历史关键词）
    // =======================================================================

    /**
     * 把当前关键词写入搜索历史。
     *
     * <p>只在<b>回车确认</b>或<b>失焦</b>时调用（用户指定）—— 若每敲一个字都记，
     * 历史会被「护」「护甲」「护甲x」这类半成品刷满，↑↓ 就没有任何可用性了。
     * <p>与上一条完全相同时不入栈（否则 ↑ 连按几下都停在同一个词上）。
     */
    private void commitSearchHistory() {
        searchHistoryIndex = -1;
        final String q = searchQuery.trim();
        if (q.isEmpty()) {
            return;
        }
        if (!SEARCH_HISTORY.isEmpty()
                && SEARCH_HISTORY.get(SEARCH_HISTORY.size() - 1).equals(q)) {
            return;
        }
        SEARCH_HISTORY.add(q);
        while (SEARCH_HISTORY.size() > SEARCH_HISTORY_MAX) {
            SEARCH_HISTORY.remove(0);
        }
    }

    /**
     * ↑↓ 翻搜索历史。
     *
     * <p>行为对齐 shell / JEI 的习惯：
     * <ul>
     *   <li>{@code ↑}：往更旧的方向走；第一次按时先把「当前输入」存成草稿</li>
     *   <li>{@code ↓}：往更新的方向走；已停在最新一条时再按 ↓ → 恢复草稿并退出历史模式</li>
     *   <li>已到最旧一条再按 ↑ → <b>停住不回绕</b>（回绕会让人分不清自己在历史的哪端）</li>
     * </ul>
     *
     * @param delta -1 = ↑（更旧）；+1 = ↓（更新）
     */
    private void navigateSearchHistory(int delta) {
        if (SEARCH_HISTORY.isEmpty()) {
            return;                       // 没有历史：什么都不做
        }
        final int last = SEARCH_HISTORY.size() - 1;
        if (searchHistoryIndex < 0) {
            if (delta > 0) {
                return;                   // 本来就在最新位置，↓ 无意义
            }
            searchHistoryDraft = searchQuery;   // 记住当前输入，供 ↓ 翻回来
            searchHistoryIndex = last;
        } else {
            final int next = searchHistoryIndex + delta;
            if (next > last) {
                // 越过最新一条 → 恢复玩家原本的输入，退出历史模式
                searchHistoryIndex = -1;
                searchSelectedId = null;
                applySearchQuery(searchHistoryDraft);
                return;
            }
            if (next < 0) {
                return;                   // 已到最旧一条，停住
            }
            searchHistoryIndex = next;
        }
        // ⚠️ 这里刻意不调 clearSearchQuery() 那类封装：它们会顺手把 searchHistoryIndex
        //   归位，而翻历史恰恰要保住这个游标。
        //   applySearchQuery 只改「内容 + 列表」，不碰历史游标，所以可以安全使用。
        searchSelectedId = null;
        applySearchQuery(SEARCH_HISTORY.get(searchHistoryIndex));
    }

    /**
     * 在搜索结果里顺序切换定位项（★ 2026-09-20 用户要求）。
     *
     * <p>用户原话：「回车需要可以在多个技能之间顺序切换，并且定位」。
     * <ul>
     *   <li>首次（未选中）→ 选第 1 项（{@code delta > 0}）或最后一项（{@code delta < 0}）</li>
     *   <li>已选中 → 移动 {@code delta}；<b>循环</b>（到底回第一个，反向同理，用户指定）</li>
     *   <li>切换后自动把该行<b>滚进视区</b>（定位）</li>
     * </ul>
     *
     * @param delta +1 = 下一个；-1 = 上一个
     */
    private void cycleSearchSelection(int delta) {
        final int n = buttons.size();
        if (n <= 0) {
            return; // 无结果：不动作
        }
        int idx = -1;
        if (searchSelectedId != null) {
            for (int i = 0; i < n; i++) {
                if (searchSelectedId.equals(buttons.get(i).skillId())) {
                    idx = i;
                    break;
                }
            }
        }
        final int next;
        if (idx < 0) {
            // 还没选中（或被结果变化挤掉）→ 从头（正）或从尾（反）开始
            next = delta > 0 ? 0 : n - 1;
        } else {
            next = ((idx + delta) % n + n) % n; // 循环
        }
        searchSelectedId = buttons.get(next).skillId();
        scrollRowIntoView(next);
    }

    /** 把第 {@code index} 行滚进列表视区（已在视区内则不动） */
    private void scrollRowIntoView(int index) {
        if (index < 0 || index >= buttons.size()) {
            return;
        }
        final int rowTop = buttons.get(index).y();   // 行局部坐标
        final int rowBottom = rowTop + BUTTON_HEIGHT;
        final int viewH = listViewH();
        if (rowTop < scrollY) {
            scrollY = rowTop;                        // 行在上方视野外 → 顶到视区顶部
        } else if (rowBottom > scrollY + viewH) {
            scrollY = rowBottom - viewH;             // 行在下方视野外 → 贴到视区底部
        }
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
    }

    /** 当前是否命中了「搜索定位项」（供行渲染高亮） */
    private boolean isSearchHit(String skillId) {
        return isSearching() && searchSelectedId != null && searchSelectedId.equals(skillId);
    }

    /**
     * 绘制搜索框：圆角底 + 描边（自绘）+ 输入内容（交给原版 EditBox）+ ✕ 清空按钮 + 结果计数。
     *
     * <p>★ 2026-09-20 分工调整：<b>文字、光标、选区高亮</b>不再自己画，
     * 改由 {@link SkillSearchBox}（原版 EditBox）绘制 —— 这样才有原版的光标移动、
     * Shift 选区、Ctrl+A/C/V 等能力，也才能让输入法模组认出焦点。
     * 背景与描边仍由本方法自绘，保持原有外观不变。
     */
    private void renderSearchBox(GuiGraphics guiGraphics) {
        final int x = searchX();
        final int y = searchY();
        final boolean hovered = lastMouseX >= x && lastMouseX <= x + SEARCH_W
                && lastMouseY >= y && lastMouseY <= y + SEARCH_H;
        int bg = searchFocused ? 0xFFFFFFFF : (hovered ? 0xFFF6F6FA : 0xFFE8EAF0);
        int edge = searchFocused ? 0xFF3482FF : 0xFFC9CCD5;
        fillRound(guiGraphics, x, y, x + SEARCH_W, y + SEARCH_H, SEARCH_RADIUS, bg);
        strokeRound(guiGraphics, x, y, x + SEARCH_W, y + SEARCH_H, SEARCH_RADIUS, edge);

        // 输入内容（含占位文案与闪烁光标）由控件自己画
        if (searchBox != null) {
            // ★ 2026-09-21 修复：Font 实例在资源重载 / 字体类模组异步初始化后会被整体替换。
            //   搜索框自己缓存了 font 引用，不同步的话会一直用旧 Font →
            //   字形图集/字距表过期 → 输入英文时字母糊在一起（而界面其余文字正常）。
            searchBox.setTextFont(font);
            searchBox.render(guiGraphics, lastMouseX, lastMouseY, 0f);
        }

        // ★ 2026-09-20 用户要求：搜索框内加可视的 ✕ 清空按钮
        if (isSearching()) {
            renderSearchClearButton(guiGraphics);
        }
        // 结果计数：搜索中时显示在搜索框左侧（让玩家知道命中多少）
        if (isSearching()) {
            String cnt = String.format(t("search_count"), buttons.size());
            final int ty = y + (SEARCH_H - font.lineHeight) / 2;
            drawCrispString(guiGraphics, cnt, x - lw(cnt) - 6, ty, 0xFF66666E, false);
        }
    }

    /**
     * 绘制搜索框内的 ✕ 清空按钮（★ 2026-09-20 用户要求）。
     *
     * <p>字符本身也走语言文件（{@code ui.zifeng_s_custom_skill_tree.search_clear}），
     * 与全项目「界面文本禁止硬编码」的约定一致。
     * <p>悬停时补一层浅红底，让玩家看出「这是个能点的按钮」，而不是装饰。
     */
    private void renderSearchClearButton(GuiGraphics guiGraphics) {
        final int x = searchClearX();
        final int y = searchY();
        final boolean hovered = isOverSearchClear(lastMouseX, lastMouseY);
        if (hovered) {
            fillRound(guiGraphics, x + 1, y + 1, x + SEARCH_CLEAR_W - 1, y + SEARCH_H - 1,
                    3, 0x33FF5555);
        }
        final String glyph = t("search_clear");
        final int gx = x + (SEARCH_CLEAR_W - lw(glyph)) / 2;
        final int gy = y + (SEARCH_H - font.lineHeight) / 2;
        drawCrispString(guiGraphics, glyph, gx, gy, hovered ? 0xFFD02B2B : 0xFF8A8A96, false);
    }


    /**
     * 某技能的属性加成文本（2026-09-19 新增，用户要求：「单技能增加属性如（+10♥）」）。
     *
     * <p>数据来源 = {@link org.zifeng.skilltree.skill.SkillEffects#attrEntriesOf}（服务端真正生效的那张表），
     * 所以<b>行里显示的数字与实际加成永远一致</b>。
     * <ul>
     *   <li>加算（ADD）：显示原始数值，如 {@code +2.5♥}</li>
     *   <li>乘算（MULT）：换算成百分比，如 {@code +10%♥}</li>
     * </ul>
     * <p>每个技能最多显示两项（贴片宽度有限）；无属性加成的技能返回空串。
     *
     * @param points 已学等级
     */
    private String skillAttrText(String skillId, int points, boolean noMod) {
        if (Skills.isStickTool(skillId)) {
            return "";
        }
        // ★ 2026-09-20 用户要求：未装对应模组 → 属性区改为提示（这些技能在未装模组时必然 points=0）
        if (noMod) {
            return t("attr_no_mod");
        }
        if (points <= 0) {
            return "";
        }
        var record = learnedAsRecord();
        // ★ 2026-09-20：生效等级提到循环外（原先每个属性项各取一次，值相同）
        final double lv = org.zifeng.skilltree.skill.SkillEffects.effLevel(record, skillId);
        // 最多 2 项（行内空间有限）；改成先收集再拼接，便于从多个来源汇入
        final java.util.List<String> parts = new java.util.ArrayList<>(2);

        // ---- ① 原版属性（ATTR_TABLE 里登记的那些）----
        for (var e : org.zifeng.skilltree.skill.SkillEffects.attrEntriesOf(skillId)) {
            if (parts.size() >= 2) {
                break;
            }
            // ★ 2026-09-20：显示信息（符号 / 是否百分比）一次查表拿到，不再走 14 分支的 equals 链
            AttrDisplay d = ATTR_DISPLAY.get(e.attribute());
            // 是否按百分比显示 = 乘算（原值是倍率）或该属性的值本身是比例（0~1 即 0~100%）
            boolean pct = e.op() == org.zifeng.skilltree.skill.SkillEffects.Op.MULT
                    || (d != null && d.percent());
            double amount = lv * e.perPoint().getAsDouble() * (pct ? 100.0 : 1.0);
            if (Math.abs(amount) < 1e-6) {
                continue;
            }
            // ★ 2026-09-22：同一技能登记的多个属性若算出【完全相同的显示文本】，只保留一项。
            //   长臂善舞 = 实体交互距离 + 方块交互距离，两者每级数值相同 →
            //   原本显示 `+0.5↔ +0.5↔`，看着像重复/看不清（用户反馈）。其余技能无重名项，不受影响。
            String item = "+" + fmtAttr(amount, pct) + (d == null ? "•" : d.symbol());
            if (!parts.contains(item)) {
                parts.add(item);
            }
        }

        // ---- ② 改原版属性、但走 applyAll 特殊路径的技能（★ 2026-09-20 补全）----
        //   这三个不登记在 ATTR_TABLE（否则服务端会重复应用 → 属性翻倍），
        //   所以显示层必须单独认它们，否则就是“技能真的加了属性但行里看不到”。
        if (Skills.ULT_MASTER.equals(skillId)) {
            // 全能精通影响 13 个属性但共用一个倍率 → 汇总为一项（用户指定）
            double m = org.zifeng.skilltree.skill.SkillEffects.getMasterBonusPercent(record);
            if (m > 0) {
                parts.add("+" + fmtAttr(m * 100, true) + "✦");
            }
        } else if (Skills.AURA_DAMAGE.equals(skillId)) {
            double a = org.zifeng.skilltree.skill.SkillEffects.getAuraDamagePercent(record);
            if (a > 0) {
                parts.add("+" + fmtAttr(a * 100, true) + "⚔");
            }
        } else if (Skills.ULT_BLOOD.equals(skillId)) {
            double atk = org.zifeng.skilltree.skill.SkillEffects.getBloodAttackPercent(record);
            if (atk > 0) {
                parts.add("+" + fmtAttr(atk * 100, true) + "⚔");
            }
            double hp = org.zifeng.skilltree.skill.SkillEffects.getBloodHealthPercent(record);
            if (hp > 0 && parts.size() < 2) {
                parts.add("+" + fmtAttr(hp * 100, true) + "♥");
            }
        }

        // ---- ③ 模组属性（★ 2026-09-20 补全）----
        //   铁魔法 / Goety / 新生魔艺设的是【别的模组的属性】，不是原版 Attribute，
        //   无法走 ATTR_DISPLAY（键是原版 Attribute），所以在这里单独认。
        //   未装对应模组时这些技能不可学（points=0，本方法已在开头返回空），不会误显示。
        appendModAttr(parts, skillId, record);

        // ---- ④ 特殊数值型（★ 2026-09-20 补全）：效果走事件/每 tick，无 Attribute 承载、
        //   也不是模组属性（暴击/吸血/荆棘/破甲/回血/掉落倍率/魔法减伤/处决/范围…）----
        appendSpecialAttr(parts, skillId, record);

        // ---- ⑤ 功能型短标签（★ 2026-09-20 补全）：毫无数值的奇技/共鸣/馈赠
        //   （夜视/饱食/闪现/搬运/机械共鸣…）属性区原本一片空白，这里显示一个功能词 ----
        if (parts.isEmpty()) {
            String fn = funcLabel(skillId);
            if (fn != null) {
                return fn;
            }
        }

        // ---- ⑥ 溢出保护（★ 2026-09-20）：多项拼起来超过属性区宽度时，只保留第 1 项。
        //   不能靠 buildRowVisual 里的 clipToWidth —— 它是【字符级】硬截断，
        //   会把 `+100%⊞` 切成 `+10`，看起来像数值算错了；整项丢弃至少每项都完整可读。
        if (parts.size() > 1 && lw(String.join(" ", parts)) > ATTR_AREA_W) {
            return parts.get(0);
        }
        return String.join(" ", parts);
    }

    /** 追加一个模组属性项（比例型，ratio = 0.1 即 +10%） */
    private static void addModPct(java.util.List<String> parts, double ratio, String symbol) {
        if (parts.size() >= 2 || ratio <= 0) {
            return;
        }
        parts.add("+" + fmtAttr(ratio * 100, true) + symbol);
    }

    /** 追加一个模组属性项（flat 整数型，如 Goety 法术强度 +80） */
    private static void addModFlat(java.util.List<String> parts, double value, String symbol) {
        if (parts.size() >= 2 || value <= 0) {
            return;
        }
        parts.add("+" + fmtAttr(value, false) + symbol);
    }

    /** Goety 流派精通：同一技能既给该流派【强度】（flat）又给【灵魂折扣】（比例） */
    private static void addGoetySchool(java.util.List<String> parts,
                                       org.zifeng.skilltree.data.PlayerSkillRecord record,
                                       String schoolSkillId) {
        addModFlat(parts, org.zifeng.skilltree.skill.SkillEffects.getGoetySchoolPotency(record, schoolSkillId), "✧");
        addModPct(parts, org.zifeng.skilltree.skill.SkillEffects.getGoetySchoolDiscountPercent(record, schoolSkillId), "❂");
    }

    /**
     * 模组属性行内显示（★ 2026-09-20）。
     *
     * <p>符号约定（均为 BMP 内字形，与原版属性那套区分开）：
     * <pre>
     *   ◉ 魔力上限      ◍ 魔力恢复      ⏱ 吟唱缩减      ⟳ 冷却缩减
     *   ✧ 法术强度（含铁魔法 9 流派 / Goety 通用强度 / Goety 9 流派强度）
     *   ❂ 灵魂折扣（Goety）
     * </pre>
     * <p>数值均取「生效等级」且会跟着技能开关变化（与属性面板、服务端 applyAll 一致）。
     */
    private static void appendModAttr(java.util.List<String> parts, String skillId,
                                    org.zifeng.skilltree.data.PlayerSkillRecord record) {
        switch (skillId) {
            // ---- 新生魔艺（Ars Nouveau）----
            case Skills.MANA_AMP -> addModPct(parts, org.zifeng.skilltree.skill.SkillEffects.getManaAmpPercent(record), "◉");
            case Skills.ARS_MANA_REGEN -> addModPct(parts, org.zifeng.skilltree.skill.SkillEffects.getArsManaRegenPercent(record), "◍");
            // ---- 铁魔法（Iron's Spells）----
            case Skills.IRON_MANA_AMP -> addModPct(parts, org.zifeng.skilltree.skill.SkillEffects.getIronManaAmpPercent(record), "◉");
            case Skills.IRON_MANA_REGEN -> addModPct(parts, org.zifeng.skilltree.skill.SkillEffects.getIronManaRegenPercent(record), "◍");
            case Skills.IRON_CAST_TIME -> addModPct(parts, org.zifeng.skilltree.skill.SkillEffects.getIronCastTimePercent(record), "⏱");
            case Skills.IRON_COOLDOWN -> addModPct(parts, org.zifeng.skilltree.skill.SkillEffects.getIronCooldownPercent(record), "⟳");
            case Skills.IRON_FIRE, Skills.IRON_ICE, Skills.IRON_LIGHTNING, Skills.IRON_HOLY,
                    Skills.IRON_ENDER, Skills.IRON_BLOOD, Skills.IRON_EVOCATION,
                    Skills.IRON_NATURE, Skills.IRON_ELDRITCH ->
                    addModPct(parts, org.zifeng.skilltree.skill.SkillEffects.getIronSchoolPercent(record, skillId), "✧");
            // ---- Goety（诡术）----
            case Skills.GOETY_POTENCY -> addModFlat(parts, org.zifeng.skilltree.skill.SkillEffects.getGoetyPotency(record), "✧");
            case Skills.GOETY_SOUL_DISCOUNT -> addModPct(parts, org.zifeng.skilltree.skill.SkillEffects.getGoetySoulDiscountPercent(record), "❂");
            case Skills.GOETY_ABYSS -> addGoetySchool(parts, record, Skills.GOETY_ABYSS);
            case Skills.GOETY_FROST -> addGoetySchool(parts, record, Skills.GOETY_FROST);
            case Skills.GOETY_GEOMANCY -> addGoetySchool(parts, record, Skills.GOETY_GEOMANCY);
            case Skills.GOETY_NECROMANCY -> addGoetySchool(parts, record, Skills.GOETY_NECROMANCY);
            case Skills.GOETY_NETHER -> addGoetySchool(parts, record, Skills.GOETY_NETHER);
            case Skills.GOETY_STORM -> addGoetySchool(parts, record, Skills.GOETY_STORM);
            case Skills.GOETY_VOID -> addGoetySchool(parts, record, Skills.GOETY_VOID);
            case Skills.GOETY_WILD -> addGoetySchool(parts, record, Skills.GOETY_WILD);
            case Skills.GOETY_WIND -> addGoetySchool(parts, record, Skills.GOETY_WIND);
            default -> {
                // 其余技能无模组属性可显示
            }
        }
    }

    // ══════════ ④ 特殊数值型技能（★ 2026-09-20 用户要求「检查每个技能，把每个显示补全」） ══════════
    //   这些技能的效果走【事件 / 每 tick】逻辑（暴击、吸血、荆棘、破甲、回血、掉落倍率、
    //   魔法减伤、处决、范围、发光…），**没有任何 Attribute 承载**，所以既不在
    //   SkillEffects#attrEntriesOf（键是原版 Attribute）里，也不在 appendModAttr（模组属性）里。
    //   每个分支只取【该技能自身的贡献】（生效等级 × 每级系数）——不是角色总值，
    //   与属性面板、服务端口径一致；系数全部读 Config，改配置后行内自动跟着变。
    //
    //   符号：✷爆击率 ✸爆击伤害 ⊘破甲 ❥吸血 ✹荆棘 ✚回血 ⊛魔法减伤 ⊜法术抑制
    //         ⇄法术反射 ❦法力虹吸 ⊞全伤减免 ❖掉落 ✳经验 ⛭耐久 ☠处决 ⛊抗性
    //         ⌖横扫 ☺效果等级 ◎半径 ↻频率
    private static void appendSpecialAttr(java.util.List<String> parts, String skillId,
                                          org.zifeng.skilltree.data.PlayerSkillRecord record) {
        if (parts.size() >= 2) {
            return; // 行内最多 2 项（用户确认：保持 2 项，超出省略）
        }
        final double lv = SkillEffects.effLevel(record, skillId);
        switch (skillId) {
            // ── 战斗 ──
            case Skills.CRIT -> addModPct(parts, Math.min(1.0, lv * Config.CRIT_CHANCE_PER_POINT.get()), "✷");
            case Skills.AMP_CRIT -> addModPct(parts, lv * Config.CRIT_DAMAGE_PER_POINT.get(), "✸");
            case Skills.LIFESTEAL -> addModPct(parts, Math.min(1.0, lv * Config.LIFESTEAL_PER_POINT.get()), "❥");
            case Skills.AMP_LIFESTEAL -> addModPct(parts, lv * Config.LIFESTEAL_AMP_PER_POINT.get(), "❥");
            case Skills.THORNS -> addModFlat(parts, lv * 0.05, "✹");
            case Skills.AMP_THORNS -> addModPct(parts, lv * 0.04, "✹");
            case Skills.ARMOR_PEN -> addModPct(parts, lv * 0.0015, "⊘");
            case Skills.AMP_ARMOR_PEN -> addModPct(parts, lv * 0.04, "⊘");
            case Skills.ULT_REAPER -> addModPct(parts, Config.REAPER_THRESHOLD.get(), "☠");
            case Skills.ULT_GOLDEN -> addModFlat(parts, Config.GOLDEN_RESISTANCE_LEVEL.get(), "⛊");
            case Skills.ULT_SWEEP -> addModFlat(parts, lv, "⌖");

            // ── 防御 / 奥术防护 ──
            case Skills.ARCANE_BULWARK -> addModPct(parts, SkillEffects.defenseToReduction(
                    lv * Config.ARCANE_DEF_PER_LEVEL.get(), Config.ARCANE_K.get()), "⊛");
            case Skills.ARCANE_AMP -> {
                // 自身贡献 = 追加防御值带来的【增量】减伤（不是总值，避开与奥术壁垒重复）
                double bulwarkDef = SkillEffects.effLevel(record, Skills.ARCANE_BULWARK)
                        * Config.ARCANE_DEF_PER_LEVEL.get();
                double without = SkillEffects.defenseToReduction(bulwarkDef, Config.ARCANE_K.get());
                double with = SkillEffects.defenseToReduction(
                        bulwarkDef + lv * Config.ARCANE_AMP_DEF_PER_LEVEL.get(), Config.ARCANE_K.get());
                addModPct(parts, with - without, "⊛");
            }
            case Skills.ULT_ARCANE_BODY -> addModPct(parts, Config.ARCANE_ULT_REDUCTION.get(), "⊛");
            // ★ 2026-09-22：破法之刃原本【贴片完全空白】（全表唯一），但描述写着「按目标增益数量增伤
            //   （每个 +15%，上限 +60%）」→ 补显示增伤上限（用户指定显示上限值）。
            case Skills.SPELLBREAK_BLADE -> addModPct(parts, Config.SPELLBREAK_MAX.get(), "⚔");
            case Skills.SPELL_DAMPEN -> addModPct(parts, SkillEffects.defenseToReduction(
                    lv * Config.SPELL_DAMPEN_PER_LEVEL.get(), Config.SPELL_DAMPEN_K.get()), "⊜");
            case Skills.SPELL_REFLECT -> addModPct(parts, Config.SPELL_REFLECT_CHANCE.get(), "⇄");
            case Skills.MANA_SIPHON -> addModPct(parts, Config.MANA_SIPHON_RATIO.get(), "❦");
            case Skills.ULT_MASTER -> addModPct(parts, Config.MASTER_DAMAGE_REDUCTION.get(), "⊞");
            case Skills.UNBREAKABLE ->
                    addModPct(parts, SkillEffects.getToolDurabilityReduction(record), "⛭");

            // ── 生产 / 掉落 ──
            case Skills.REGEN -> addModFlat(parts, lv * 0.2, "✚");
            case Skills.AMP_REGEN -> addModPct(parts, lv * 0.1, "✚");
            case Skills.BLOCK_DROP -> addModFlat(parts, lv, "❖");
            case Skills.XP_GAIN -> addModFlat(parts, lv * 2, "✳");
            case Skills.MOB_SPAWN_EGG -> addModPct(parts, lv * 0.2, "❖");
            case Skills.MOB_HEAD -> addModPct(parts, lv * 0.1, "❖");
            // ★ 2026-09-30：战利品大爆发 = 倍率(1+等级) + 当前模式短标签（4 模式必须一眼可见）
            case Skills.LOOT_BOMB -> {
                addModFlat(parts, 1.0 + lv, "❖");
                parts.add(t(lootModeKey(record.getAuraTargetMode(Skills.LOOT_BOMB))));
            }

            // ── 特殊 / 光环 ──
            case Skills.VILLAGE_HERO -> addModFlat(parts, lv, "☺");
            case Skills.GLOW -> addModFlat(parts, Config.GLOW_RADIUS.get(), "◎");
            // ★ 2026-09-22（用户要求）：原显示「攻击间隔缩减 X%↻」——玩家看不懂那是什么。
            //   改为【每秒攻击次数】，公式与 AuraEvents.auraAttackInterval 完全一致：
            //   interval = max(10, round(base × (1-reduction)^lv))，次数 = 20 ÷ interval。
            case Skills.AURA_SPEED -> {
                int baseInterval = Config.AURA_BASE_INTERVAL_TICKS.get();
                double reduction = Config.AURA_SPEED_INTERVAL_REDUCTION.get();
                int interval = Math.max(10, (int) Math.round(
                        baseInterval * Math.pow(1.0 - reduction, Math.max(0.0, lv))));
                addModFlat(parts, 20.0 / interval, t("unit_per_sec"));
            }
            case Skills.AURA_HEAL -> {
                addModFlat(parts, Config.AURA_HEAL_RADIUS.get(), "◎");
                // ★ 2026-09-20：7 级起额外直接治疗量（与 AuraEvents 的 amp 封顶 6 配套，
                //   否则玩家看到 radius 一直不变、以为升级无用）
                addModFlat(parts, Math.max(0.0, lv - 6.0), "✚");
            }
            case Skills.AURA_VOID -> addModFlat(parts, Config.VOID_AURA_RADIUS.get(), "◎");
            case Skills.AURA_XP -> addModFlat(parts, 1000.0 * lv, "✳");
            case Skills.AURA_EMPOWER -> addModPct(parts, Config.AURA_CHAOS_DAMAGE_RATIO.get(), "⚔");
            // ★ 2026-09-20：净化领域间隔随等级缩短（与 ArcaneEvents.purifyInterval 同式）——
            //   它原本 100 级但效果恒定（半径/间隔都是常量），行内又只显示「净化领域」四个字，
            //   玩家完全看不出升级有什么用
            case Skills.PURIFY_FIELD -> addModPct(parts,
                    1.0 - Math.pow(0.97, Math.max(0.0, lv - 1.0)), "↻");
            default -> {
                // 其余技能无特殊数值可显示
            }
        }
    }

    /**
     * 功能型技能的属性区短标签（★ 2026-09-20 用户要求：「显示功能短标签」）。
     *
     * <p>这些技能没有任何数值可算（夜视 / 饱食 / 闪现 / 搬运 / 机械共鸣 / 洗礼…），
     * 属性区原本一片空白，玩家看不出这个技能干什么。这里改为显示一个 2~6 字的功能词，
     * 文案全部走语言文件 {@code ui.zifeng_s_custom_skill_tree.fn_&lt;skillId&gt;}。
     *
     * @return 本地化功能词；非功能型技能 → {@code null}（属性区保持空白）
     */
    private static String funcLabel(String skillId) {
        return FUNC_LABEL_IDS.contains(skillId) ? t("fn_" + skillId) : null;
    }

    /** 需要显示功能短标签的技能（毫无数值可显示的那些；★ 2026-09-20） */
    private static final java.util.Set<String> FUNC_LABEL_IDS = java.util.Set.of(
            // 终极·功能
            Skills.ULT_REVIVE, Skills.ULT_VOID_BODY, Skills.ULT_FAVOR,
            Skills.AUTO_SMELT, Skills.ULT_BREAK_ALL, Skills.ULT_UNBREAK_TAG,
            // 特殊·功能
            Skills.NIGHT_VISION, Skills.SATURATION, Skills.FLY_NO_INERTIA, Skills.FLY_MINING,
            Skills.FIRE_PROTECT, Skills.WATER_BREATH, Skills.DARK_VISION, Skills.UNDERWATER_VISION,
            Skills.ENCHANT_RANDOM, Skills.ENCHANT_BREAK, Skills.ENCHANT_OVER,
            Skills.UNLIMITED_TRADES, Skills.VILLAGER_MASTER, Skills.TREASURE_HUNTER,
            Skills.GLUTTONY, Skills.BLINK, Skills.ARCANE_ADAPT, Skills.SPELL_PURGE,
            // 光环·功能
            Skills.AURA_MAGNET, Skills.AURA_LOCK, Skills.AURA_LOOT_VACUUM,
            Skills.CONTAINER_HAUL,
            // 净化领域已有数值可显示（间隔随等级缩短）→ 不再走功能标签
            // 宙宇法则
            Skills.AURA_TIME, Skills.AURA_WEATHER, Skills.AE_INFINITE_CHANNEL,
            // 机械共鸣
            Skills.MACHINE_STAR, Skills.MACHINE_LOOT_BOMB, Skills.MACHINE_UNBREAKABLE,
            Skills.MACHINE_BLOCK_DROP, Skills.MACHINE_XP_GAIN,
            Skills.MACHINE_SPAWN_EGG, Skills.MACHINE_MOB_HEAD, Skills.MACHINE_AUTO_SMELT,
            Skills.MACHINE_ZONE_PLACE, Skills.MACHINE_ZONE_EXCAVATE,
            Skills.MACHINE_ZONE_ATTACK, Skills.MACHINE_ZONE_PROTECT,
            // 子枫的馈赠
            Skills.GIFT_TIME_BAPTISM, Skills.GIFT_TIME_STORM, Skills.GIFT_TIME_FLOOD,
            Skills.GIFT_MOVE_BAPTISM, Skills.GIFT_MOVE_AMP,
            Skills.GIFT_FLY_BAPTISM, Skills.GIFT_FLY_AMP,
            Skills.GIFT_MINE_BAPTISM, Skills.GIFT_MINE_AMP,
            Skills.GIFT_KILL_BAPTISM, Skills.GIFT_KILL_AMP);

    /** 属性数值格式：百分比取一位小数（整则不带小数），普通值保留最多两位 */
    private static String fmtAttr(double v, boolean percent) {
        double a = Math.abs(v);
        if (a >= 10 || a == Math.floor(a)) {
            return String.valueOf(Math.round(a));
        }
        return percent ? String.format("%.1f", a) : String.format("%.2f", a);
    }

    /**
     * 属性显示信息：符号 + 是否按百分比显示。
     *
     * <p>★ 2026-09-20：用户提醒「这些符号是固定显示的，不用每次实时算」——
     * 原 {@code attrSymbol} 是 14 分支的 {@code attr.equals(...)} 链，每次重建行都线性比一遍。
     * 改成<b>静态查表</b>后为 O(1)，且新增属性只改 {@link #buildAttrDisplay()} 一处。
     *
     * <p>{@code percent} 的依据是<b>数值语义</b>而非 {@code Op}（Op 只描述服务端怎么加到属性上）：
     * {@code DAMAGE_REDUCTION}（金身真解每级 1%）与 {@code KNOCKBACK_RESISTANCE}（稳如泰山每级 10%）
     * 的值域就是 0~1，即 0~100%。此前只看 Op，导致它们显示为 {@code +0.80✪} / {@code +1⚓}，
     * 玩家看不出是百分比（用户反馈：「之前有些百分比的都不显示」）。
     */
    private record AttrDisplay(String symbol, boolean percent) {
    }

    /** 属性 → 显示信息（静态常量表） */
    private static final java.util.Map<
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, AttrDisplay> ATTR_DISPLAY = buildAttrDisplay();

    private static java.util.Map<
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, AttrDisplay> buildAttrDisplay() {
        var m = new java.util.HashMap<
                net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, AttrDisplay>(20);
        // ---- 普通数值属性（加到基础值上的量，不带 %）----
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, new AttrDisplay("♥", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, new AttrDisplay("✜", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS, new AttrDisplay("◈", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, new AttrDisplay("⚔", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, new AttrDisplay("⚡", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, new AttrDisplay("➤", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH, new AttrDisplay("↑", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED, new AttrDisplay("✈", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.LUCK, new AttrDisplay("★", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.MINING_EFFICIENCY, new AttrDisplay("⛏", false));
        m.put(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED, new AttrDisplay("≈", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE, new AttrDisplay("↔", false));
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE, new AttrDisplay("↔", false));
        // ---- 比例型属性（值 0~1 = 0~100%，必须带 %）----
        m.put(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, new AttrDisplay("⚓", true));
        m.put(org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION, new AttrDisplay("✪", true));
        return java.util.Map.copyOf(m);
    }

    /**
     * 临时记录的占位 UUID（★ 2026-09-20 性能）。
     *
     * <p>{@link #learnedAsRecord()} 需要一个 {@code PlayerSkillRecord} 来跑
     * {@code SkillEffects} 的纯计算查询，而这个 UUID 只是身份标识、不参与任何判断。
     * <p>⚠️ 原实现用 {@code UUID.randomUUID()}（SecureRandom，带全局锁、每次取 128 位熵），
     * 而本方法会被 {@link #buildRowVisual} 对<b>每一行</b>调用 —— 叠加
     * 「服务端每 40 tick 全量同步 → 整表视觉缓存失效重建」，就表现为界面开着时
     * 每 2 秒一次的可见微顿。改为固定值后该开销完全消失。
     */
    private static final java.util.UUID SCRATCH_RECORD_UUID = new java.util.UUID(0L, 0L);

    org.zifeng.skilltree.data.PlayerSkillRecord learnedAsRecord() {
        org.zifeng.skilltree.data.PlayerSkillRecord record = new org.zifeng.skilltree.data.PlayerSkillRecord(SCRATCH_RECORD_UUID);
        // 直接设置点数（不能用 learnSkill：AURA 消耗递增会因点数不足提前失败，导致光环永远只显示 1 级）
        learnedSkills.forEach(record::setLearnedPoints);
        toggles.forEach(record::setEnabled);
        activeLevels.forEach(record::setActiveLevel);
        return record;
    }

    // ============ 交互 ============

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ★ 2026-09-21：设置子界面脱离缩放 → 用【未换算】的屏幕坐标直接处理
        if (activeSubScreen instanceof SettingsSubScreen
                && activeSubScreen.isMouseOver(mouseX, mouseY)) {
            activeSubScreen.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return runInLayoutSpace(mouseX, mouseY,
                (x, y) -> mouseClickedInner(x, y, button));
    }

    /** mouseClicked 的实际实现（在「设计空间」内执行，见 {@link #mouseClicked}） */
    private boolean mouseClickedInner(double mouseX, double mouseY, int button) {
        // 属性面板按钮（右下角右侧）：优先响应（即使子界面打开，再点可关闭）Shift+点击切换位置
        if (button == 0 && mouseX >= panelToggleX() && mouseX <= panelToggleX() + bottomBtnW()
                && mouseY >= panelToggleY() && mouseY <= panelToggleY() + BOTTOM_BTN_H) {
            if (isShiftHeld()) {
                Config.PANEL_POSITION.set(1 - Config.PANEL_POSITION.get());
                Config.PANEL_POSITION.save();
            } else {
                // 点击：打开/关闭属性面板子界面（替代旧的直接显示/隐藏）
                if (activeSubScreen instanceof AttributePanelSubScreen) {
                    closeSubScreen();
                } else {
                    openSubScreen(new AttributePanelSubScreen(this));
                }
            }
            return true;
        }
        // 设置按钮（HUD调整按钮左边，★ 2026-09-21 新增）：优先响应（即使子界面打开，再点可关闭）
        if (button == 0 && isSettingsPanelHit(mouseX, mouseY)) {
            if (activeSubScreen instanceof SettingsSubScreen) {
                closeSubScreen();
            } else {
                openSubScreen(new SettingsSubScreen(this));
            }
            return true;
        }
        // HUD 调整按钮（属性面板按钮左边）：优先响应（即使子界面打开，再点可关闭）
        if (button == 0 && isHudPanelHit(mouseX, mouseY)) {
            if (activeSubScreen instanceof HudAdjustSubScreen) {
                closeSubScreen();
            } else {
                openSubScreen(new HudAdjustSubScreen(this));
            }
            return true;
        }
        // 子界面打开：仅面板内区域交给子界面；面板外正常走技能树逻辑（不干扰操作）
        if (activeSubScreen != null && activeSubScreen.isMouseOver(mouseX, mouseY)) {
            activeSubScreen.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        // ★ 2026-09-20 用户要求：右键点搜索框 → 一次清空关键词（JEI 式操作）
        //   必须和左键分支一样放在子界面 / isOverUI 之前，否则会被当作普通 UI 吞掉
        if (button == 1 && isOverSearchBox(mouseX, mouseY)) {
            clearSearchQuery();
            setSearchFocus(true);    // 清空后留在输入态：多半是想马上换个词
            searchHistoryIndex = -1;
            return true;
        }
        // ★ 2026-09-20 搜索框：左键点击 → 聚焦输入；点其他位置 → 失焦
        //   （必须放在 isOverUI 检查之前，否则会被当作普通 UI 吞掉）
        if (button == 0) {
            // ★ 2026-09-20 用户要求：✕ 按钮清空关键词。优先级高于「聚焦」
            //（✕ 区域在搜索框内部，不先判就会被聚焦分支吃掉）
            if (isOverSearchClear(mouseX, mouseY)) {
                clearSearchQuery();
                setSearchFocus(true);   // 同右键：清完继续处于输入态
                searchHistoryIndex = -1;
                return true;
            }
            if (isOverSearchBox(mouseX, mouseY)) {
                searchHistoryIndex = -1;   // ★ 重新聚焦 → 退出历史浏览
                setSearchFocus(true);
                // ★ 2026-09-20：把点击转发给 EditBox，让它把光标定位到点中的位置
                //   （否则每次点都只能把光标留在原处，很不符合直觉）。
                //   控件重写了 isMouseOver 覆盖整个可视框，所以点在内边距上也有效。
                if (searchBox != null) {
                    searchBox.mouseClicked(mouseX, mouseY, button);
                }
                return true;
            }
            if (searchFocused) {
                // ★ 2026-09-20 用户要求：失焦时把关键词记入历史（否则 ↑↓ 永远翻不到东西）
                commitSearchHistory();
            }
            setSearchFocus(false); // 点别处 → 失焦（保留关键词与结果列表）
        }
        // 中键：新列表布局下不再用于拖动，忽略
        if (button == 2) {
            return true;
        }
        if (button == 0) {
            // 鼠标在第一图层 UI 区域（属性面板/标题/提示条）→ 不透传到下层技能（不透过面板操作）
            if (isOverUI(mouseX, mouseY)) {
                return true;
            }
            // ① 类别按钮行（分区框 2 内）：切换类别 → 回到顶部
            int catY = catFrameTop() + FRAME_LINE + LIST_TOP_GAP;
            if (isMouseOverCategories(mouseX, mouseY)) {
                for (int i = 0; i < CATEGORY_COUNT; i++) {
                    if (mouseY >= catY && mouseY < catY + CAT_BTN_H
                            && mouseX >= catButtonX[i] && mouseX < catButtonX[i] + catButtonW[i]) {
                        if (selectedCategory != i) {
                            selectedCategory = i;
                        }
                        searchSelectedId = null;   // ★ 2026-09-20：切类别清掉搜索定位项
                        scrollY = 0;
                        lastNormalScrollY = 0;
                        markViewStateChanged();
                        rebuildButtons();
                        persistViewState();
                        return true;
                    }
                }
                return true; // 类别行空白处：吞掉，避免点到列表
            }
            // ★ 2026-09-21 新增：右侧竖向滚动条可拖动（用户反馈之前只能滚轮）
            //   必須在技能行循环【之前】判定，否则会被整行“学习技能”抢先命中。
            if (isOverListScrollbar(mouseX, mouseY)) {
                listScrollbarDragging = true;
                // 记录光标在滑块内的相对位置：抓滑块上半部就拖上半部（不跳变）
                listScrollbarGrabDy = mouseY - listScrollbarThumbY();
                // 点在滑轨空白处 → 先把滑块拉过去，手感接近原版
                if (!isOverListScrollbarThumb(mouseX, mouseY)) {
                    listScrollbarGrabDy = listScrollbarThumbH() / 2.0;
                }
                dragListScrollbarTo(mouseY);
                return true;
            }
            if (!isMouseOverSkillList(mouseX, mouseY)) {
                return true;
            }
            // ② 技能行（行内：名称/消耗/进度条/等级 + 固定 3 槽位按键框）
            final double lx = toPanelX(mouseX);
            final double ly = toPanelY(mouseY);
            SkillButton skillButton = findHoveredSkillButton(mouseX, mouseY);
            if (skillButton != null) {
                String sid = skillButton.skillId();
                boolean togglable = Skills.isTogglable(sid);
                // 贴片内四段（内容区 │ Q │ E │ R；与 renderKeyCell 完全同一套几何）
                final int cw = contentW();
                int kx = skillButton.x() + cw;
                int k2x = kx + KEY_BOX_WIDTH;
                int k3x = k2x + KEY2_BOX_WIDTH;
                int ky = skillButton.y();
                boolean onRowY = ly >= ky && ly <= ky + BUTTON_HEIGHT;
                // 进度条优先于整行“学习技能”命中：点击任意位置即定位生效等级，随后可连续拖动。
                if (overLevelBar(mouseX, mouseY, skillButton)
                        && learnedSkills.getOrDefault(sid, 0) > 0
                        && Skills.getMaxPoints(sid) > 1) {
                    draggingLevelSkillId = sid;
                    setActiveLevelFromMouse(skillButton, mouseX);
                    return true;
                }
                // 第一框（开关键）：仅可开关技能可点击；空槽位不响应
                if (togglable && onRowY && lx >= kx && lx <= kx + KEY_BOX_WIDTH) {
                    if (keyBindSkillId != null && keyBindSkillId.equals(sid) && keyBindListening) {
                        // 再次点击同一按键框 → 退出监听（不改变绑定）
                        keyBindListening = false;
                    } else {
                        // 进入监听态（点击该技能按键框，等待按键输入）
                        keyBindSkillId = sid;
                        keyBindListening = true;
                    }
                    return true;
                }
                // 第二列按键框（2026-08-13：光环=目标循环键，可调等级技能=等级循环键）
                if (isLevelBindable(sid) && onRowY && lx >= k2x && lx <= k2x + KEY2_BOX_WIDTH) {
                    if (levelKeyBindSkillId != null && levelKeyBindSkillId.equals(sid) && levelKeyBindListening) {
                        levelKeyBindListening = false;
                    } else {
                        levelKeyBindSkillId = sid;
                        levelKeyBindListening = true;
                    }
                    return true;
                }
                // 第三列按键框（2026-09-07：功能触发键——主动技场景内触发一次）
                if (Skills.isTriggerBindable(sid) && onRowY && lx >= k3x && lx <= k3x + KEY3_BOX_WIDTH) {
                    if (triggerKeyBindSkillId != null && triggerKeyBindSkillId.equals(sid) && triggerKeyBindListening) {
                        triggerKeyBindListening = false;
                    } else {
                        triggerKeyBindSkillId = sid;
                        triggerKeyBindListening = true;
                    }
                    return true;
                }
                if (skillButton.isHovered(mouseX, mouseY, this)) {
                    if (canLearn(skillButton.skillId())) {
                        if (Screen.hasShiftDown() && Screen.hasControlDown()) {
                            // Ctrl+Shift+点击：一次加 100 级（受技能点与上限约束，乐观连加，2026-08-13 需求）
                            int added = 0;
                            for (int i = 0; i < 100; i++) {
                                if (!canLearn(skillButton.skillId())) break;
                                skillPoints = Math.max(0, skillPoints - nextCostLocal(skillButton.skillId()));
                                learnedSkills.merge(skillButton.skillId(), 1, Integer::sum);
                                added++;
                            }
                            if (added > 0) {
                                // 升级后自动生效最高等级
                                activeLevels.put(skillButton.skillId(), learnedSkills.getOrDefault(skillButton.skillId(), 0));
                                org.zifeng.skilltree.network.ModNetwork.sendToServer(new LearnSkillC2SPacket(skillButton.skillId(), added));
                            }
                        } else if (isShiftHeld()) {
                            // Shift+点击：一次加 10 级（受技能点与上限约束，乐观连加）
                            int added = 0;
                            for (int i = 0; i < 10; i++) {
                                if (!canLearn(skillButton.skillId())) break;
                                skillPoints = Math.max(0, skillPoints - nextCostLocal(skillButton.skillId()));
                                learnedSkills.merge(skillButton.skillId(), 1, Integer::sum);
                                added++;
                            }
                            if (added > 0) {
                                // 升级后自动生效最高等级
                                activeLevels.put(skillButton.skillId(), learnedSkills.getOrDefault(skillButton.skillId(), 0));
                                org.zifeng.skilltree.network.ModNetwork.sendToServer(new LearnSkillC2SPacket(skillButton.skillId(), added));
                            }
                        } else {
                            // 单次加点（乐观更新）
                            double cost = nextCostLocal(skillButton.skillId());
                            skillPoints = Math.max(0, skillPoints - cost);
                            learnedSkills.merge(skillButton.skillId(), 1, Integer::sum);
                            activeLevels.put(skillButton.skillId(), learnedSkills.getOrDefault(skillButton.skillId(), 0));
                            org.zifeng.skilltree.network.ModNetwork.sendToServer(new LearnSkillC2SPacket(skillButton.skillId(), 1));
                        }
                        rebuildButtons();
                    }
                    return true;
                }
            }
        } else if (button == 1) {
            // 右键：切换技能开关；光环技能 Shift+右键 切换目标模式（敌对/友好/所有）
            // 鼠标在第一图层 UI 区域 → 不透传
            if (!isMouseOverSkillList(mouseX, mouseY)) {
                return true;
            }
            SkillButton skillButton = findHoveredSkillButton(mouseX, mouseY);
            if (skillButton != null) {
                String skillId = skillButton.skillId();
                // 木棍工具占位：右键=工具总开关，Shift+右键=切模式（按已解锁模式循环，2026-09-08）
                if (Skills.isStickTool(skillId)) {
                    if (isShiftHeld()) {
                        int next = org.zifeng.skilltree.client.ModKeyBindingEvents.nextUnlockedStickMode(
                                org.zifeng.skilltree.client.ModKeyBindingEvents.getStickToolModeClient());
                        org.zifeng.skilltree.client.ModKeyBindingEvents.setStickToolModeClient(next);
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.StickToolC2SPacket(1, next));
                    } else {
                        boolean now = !org.zifeng.skilltree.client.ModKeyBindingEvents.isStickToolOnClient();
                        org.zifeng.skilltree.client.ModKeyBindingEvents.setStickToolOnClient(now);
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.StickToolC2SPacket(0, 0));
                    }
                    return true;
                }
                // Shift+右键：有模式循环的技能循环其模式（2026-09-07 规范 v1.0 收编读注册表）；
                // 晴空环=天气循环（走全局通道）；敌我/搬运=auraTargetModes；其余技能 → 普通开关
                if (isShiftHeld() && Skills.hasModeCycle(skillId)) {
                    if (Skills.AURA_WEATHER.equals(skillId)) {
                        // 晴空环：循环天气（0晴→1雨→2雷暴；客户端乐观 + 服务端 WeatherMode 通道）
                        int cur = org.zifeng.skilltree.client.ModKeyBindingEvents.getWeatherModeClient();
                        int next = (cur + 1) % 3;
                        org.zifeng.skilltree.client.ModKeyBindingEvents.setWeatherModeClient(next);
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.WeatherModeC2SPacket(next));
                    } else {
                        int span = Math.max(2, Skills.getModeCount(skillId));
                        int cur = auraTargetModes.getOrDefault(skillId, 0);
                        int mode = (cur + 1) % span;
                        auraTargetModes.put(skillId, mode);
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new AuraTargetC2SPacket(skillId, mode));
                    }
                } else {
                    boolean now = !toggles.getOrDefault(skillId, Boolean.TRUE);
                    toggles.put(skillId, now);
                    org.zifeng.skilltree.network.ModNetwork.sendToServer(new SetSkillToggleC2SPacket(skillId, now));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 搜索输入：把字符交给原版 EditBox 处理（★ 2026-09-20 改为委托）。
     *
     * <p>原先自己往 {@code searchQuery} 上拼字符，现在由控件负责插入、限制长度、
     * 推进光标，并通过 responder 回调 {@link #onSearchBoxChanged} 同步回关键词。
     * <p>中文（含输入法上屏的字符）也是从这里进来的，{@code EditBox.charTyped}
     * 用 {@code StringUtil.isAllowedChatCharacter} 过滤，汉字能正常通过。
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused) {
            if (searchBox != null && searchBox.charTyped(codePoint, modifiers)) {
                return true;
            }
            // 控件不接受（如长度已满 / 不可见字符）→ 仍然吞掉，
            // 避免字符透传给界面其它逻辑（例如按字母触发别的快捷键）。
            return true;
        }
        // ★ 2026-09-21：子界面（如设置面板的数字输入框）优先收取字符
        if (activeSubScreen != null && activeSubScreen.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ★ 2026-09-20 搜索聚焦时优先吃掉按键（否则退格/Esc/回车会触发界面其它行为）
        if (searchFocused) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                // Esc：有关键词 → 先清空；已空 → 再失焦
                if (isSearching()) {
                    searchHistoryIndex = -1;
                    applySearchQuery("");
                } else {
                    setSearchFocus(false);
                }
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
                // ★ 2026-09-20 用户要求（JEI 式操作）：Ctrl+退格 = 一次清空全部
                //   必须放在委托之前：原版 EditBox 对 Ctrl+退格 的语义是「删一个词」，
                //   与用户要求的「全清」不同，所以要抢在它前面处理。
                if (Screen.hasControlDown()) {
                    clearSearchQuery();
                    return true;
                }
                // 普通退格 / Delete 交给 EditBox（它支持 Shift/Ctrl 修饰的更细粒度删除）
            }
            // ★ 2026-09-20 用户要求（JEI 式操作）：↑↓ 翻历史关键词
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                navigateSearchHistory(keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                // ★ 2026-09-20 用户要求：回车 = 失焦 + 选中第 1 项 + 定位
                //   （再按回车 = 下一个，见下方「非聚焦」分支）
                // ★ 2026-09-20 用户要求：回车也是「确认关键词」，此时才记入历史
                commitSearchHistory();
                setSearchFocus(false);
                cycleSearchSelection(1);
                return true;
            }
            // ★ 2026-09-20：上面几类是我们自己定义的键，其余全部委托给原版 EditBox ——
            //   光标左右移动 / Shift 选区 / Ctrl+A 全选 / Ctrl+C,V,X 复制粘贴剪切 /
            //   Home,End 跳首尾 / 普通退格删字，这些能力都是改用 EditBox 才有的。
            if (searchBox != null && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            // ★ 2026-09-20（学自 AE2 的关键一笔）：EditBox 没消费的按键一律吞掉。
            //   否则会透传到界面其它逻辑 —— 典型后果是「按 E 变成关界面 / 开背包，
            //   而不是往搜索框里打一个字母 e」。AE2 的 AETextField 对此有完全相同的处理，
            //   其原注释：prevent "e" from closing the window instead of typing into the text field。
            return true; // 其余按键在搜索态下一律吞掉（含 Ctrl+R 等界面快捷键）
        }
        // ★ 2026-09-20 用户要求（JEI 式操作）：Ctrl+F 把输入焦点切到搜索框
        //   放在子界面分发之前：搜索框在标题行、不被子界面遮挡，任何时候都应该能用
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F && Screen.hasControlDown()) {
            searchHistoryIndex = -1;
            setSearchFocus(true);
            return true;
        }
        // ★ 2026-09-20：非聚焦但有关键词时，ESC 先清空搜索（再按才关闭界面）——
        //   否则玩家按回车确认结果后，想清空只能回去点搜索框，太绕。
        if (isSearching() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            clearSearch();
            return true;
        }
        // ★ 2026-09-20 用户要求：搜索结果里回车顺序切换 + 定位（Shift+回车 反向）。
        //   位置说明：放在「按键绑定监听」之后（见下方）—— 玩家若正在绑键，回车应归绑键。
        //   这里先记下意图，实际处理在 bind 监听之后。
        // ESC：子界面打开时先关子界面（逐层关闭）；其他键走子界面逻辑，子界面不处理则继续技能树逻辑
        if (activeSubScreen != null) {
            if (activeSubScreen.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            // 子界面未处理该键（如非 ESC）→ 继续走技能树逻辑
        }
        // 按键设置监听：仿原版按键设置——按任意键绑定，Esc 取消监听，Backspace 清除
        if (keyBindSkillId != null && keyBindListening) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                keyBindListening = false; // Esc 取消监听（不关闭窗口）
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
                org.zifeng.skilltree.client.SkillKeyBinds.clearKey(keyBindSkillId);
                keyBindListening = false;
            } else {
                // 绑定按键（与 ModKeyBindings 相同的键类型：KEYSYM）
                var key = com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode);
                org.zifeng.skilltree.client.SkillKeyBinds.setKey(keyBindSkillId, key);
                keyBindListening = false;
            }
            return true;
        }
        // 第二列按键框监听（2026-08-13：等级/目标循环键）
        if (levelKeyBindSkillId != null && levelKeyBindListening) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                levelKeyBindListening = false;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
                org.zifeng.skilltree.client.SkillKeyBinds.clearLevelKey(levelKeyBindSkillId);
                levelKeyBindListening = false;
            } else {
                var key = com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode);
                org.zifeng.skilltree.client.SkillKeyBinds.setLevelKey(levelKeyBindSkillId, key);
                levelKeyBindListening = false;
            }
            return true;
        }
        // 第三列按键框监听（2026-09-07：功能触发键——主动技场景内触发一次）
        if (triggerKeyBindSkillId != null && triggerKeyBindListening) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                triggerKeyBindListening = false;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
                org.zifeng.skilltree.client.SkillKeyBinds.clearTriggerKey(triggerKeyBindSkillId);
                triggerKeyBindListening = false;
            } else {
                var key = com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode);
                org.zifeng.skilltree.client.SkillKeyBinds.setTriggerKey(triggerKeyBindSkillId, key);
                triggerKeyBindListening = false;
            }
            return true;
        }
        // ★ 2026-09-20 用户要求：搜索结果显示时，回车 = 顺序切换定位项（Shift+回车 = 反向，循环）。
        //   放在按键绑定监听之后：若玩家正在绑键，回车应作为被绑的键，而不是切换搜索结果。
        if (isSearching() && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            cycleSearchSelection(Screen.hasShiftDown() ? -1 : 1);
            return true;
        }
        // Ctrl+R：重置鼠标指着的技能（防误触；服务端按该技能返还率加回技能点后回发校准）
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R && Screen.hasControlDown()) {
            // 找到鼠标悬停的技能（第一图层 UI 区域不响应，避免透过面板重置）
            SkillButton skillButton = findHoveredSkillButton(lastMouseX, lastMouseY);
            if (skillButton != null && !isOverUI(lastMouseX, lastMouseY)) {
                String skillId = skillButton.skillId();
                if (learnedSkills.getOrDefault(skillId, 0) <= 0) {
                    // 未学的技能无法重置
                    return true;
                }
                org.zifeng.skilltree.network.ModNetwork.sendToServer(new ResetSkillC2SPacket(skillId));
                // 本地乐观移除该技能，等待服务端回发校准
                skillPoints += nextCostLocal(skillId); // 乐观加回（服务端按返还率精确计算后回发覆盖）
                learnedSkills.remove(skillId);
                toggles.remove(skillId);
                activeLevels.remove(skillId);
                rebuildButtons();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private double nextCostLocal(String skillId) {
        if (Skills.ULT_FAVOR.equals(skillId)) return Skills.ultFavorCost();
        if (Skills.NIGHT_VISION.equals(skillId) || Skills.SATURATION.equals(skillId)) return Skills.minorUltCost();
        if (Skills.AURA_MAGNET.equals(skillId)) return org.zifeng.skilltree.Config.MAGNET_COST.get();
        if (Skills.AURA_LOCK.equals(skillId)) return org.zifeng.skilltree.Config.LOCK_COST.get();
        if (Skills.AURA_VOID.equals(skillId)) return org.zifeng.skilltree.Config.VOID_AURA_COST.get();
        Skills.SkillType type = Skills.getType(skillId);
        if (type == Skills.SkillType.GLOBAL) {
            return Skills.getGlobalCost(skillId, learnedSkills.getOrDefault(skillId, 0)); // 寰宇法则：时之环/晴空环 100；无限回路阶梯
        }
        if (type == Skills.SkillType.AURA) {
            return Skills.getAuraCost(skillId, learnedSkills.getOrDefault(skillId, 0));
        }
        if (type == Skills.SkillType.BASE) return Skills.getBaseCostAtLevel(learnedSkills.getOrDefault(skillId, 0)); // 线性 +1/级
        if (type == Skills.SkillType.AMPLIFY) return Skills.getAmplifyCostAtLevel(learnedSkills.getOrDefault(skillId, 0)); // 线性 +2/级
        if (type == Skills.SkillType.MAGIC) return Skills.getMagicCostAtLevel(skillId, learnedSkills.getOrDefault(skillId, 0)); // 线性（默认+2/级，吟唱缩减+5/级）
        if (type == Skills.SkillType.MACHINE) return Skills.getMachineCost(skillId); // 机械共鸣：一次性（机械之星 1000 / 其余 5000）
        if (type == Skills.SkillType.GIFT) return Skills.getGiftCost(skillId, learnedSkills.getOrDefault(skillId, 0)); // 子枫的馈赠：时间系列 0 / 洗礼阶梯 / 增幅指数
        return Skills.getUltimateLevelCost(skillId, learnedSkills.getOrDefault(skillId, 0)); // 终极节点/特殊被动（单次或节点类阶梯递增）
    }

    private boolean isShiftHeld() {
        return Screen.hasShiftDown();
    }

    private boolean isControlHeld() {
        return Screen.hasControlDown();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // ★ 2026-09-21：设置子界面脱离缩放 —— 面板内或拖动中，直接用未换算坐标转发
        if (activeSubScreen instanceof SettingsSubScreen
                && (activeSubScreen.isMouseOver(mouseX, mouseY) || activeSubScreen.isDragging())) {
            activeSubScreen.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return true;
        }
        return runInLayoutSpace(mouseX, mouseY,
                (x, y) -> mouseDraggedInner(x, y, button, dragX, dragY));
    }

    /** mouseDragged 的实际实现（在「设计空间」内执行） */
    private boolean mouseDraggedInner(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // ★ 2026-09-21：滚动条拖动中 → 优先处理（即使鼠标移出滚动条也持续跟手）
        if (listScrollbarDragging && button == 0) {
            dragListScrollbarTo(mouseY);
            return true;
        }
        // 子界面打开：面板内 或 正在拖动面板 → 手势转发给子界面（拖动中鼠标移出面板也持续跟手）
        if (activeSubScreen != null && (activeSubScreen.isMouseOver(mouseX, mouseY) || activeSubScreen.isDragging())) {
            draggingLevelSkillId = null;
            activeSubScreen.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return true;
        }
        if (button == 0 && draggingLevelSkillId != null) {
            if (!isMouseOverSkillList(mouseX, mouseY)) {
                draggingLevelSkillId = null;
                return true;
            }
            SkillButton skillButton = findHoveredSkillButton(mouseX, mouseY);
            if (skillButton != null && draggingLevelSkillId.equals(skillButton.skillId())) {
                setActiveLevelFromMouse(skillButton, mouseX);
                return true;
            }
            draggingLevelSkillId = null;
        }
        // 2026-09-19 列表式改版：不再有平移/缩放，拖动一律交给列表滚动（滚轮）；
        // 保留子界面转发即可，其余丢弃，避免误拖把整张表拖跑。
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 鼠标释放（子界面拖动结束后保存位置，2026-09-01） */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // ★ 2026-09-21：设置子界面脱离缩放 → 用未换算坐标转发（它需要结束滑块/标题栏拖动）
        if (activeSubScreen instanceof SettingsSubScreen) {
            activeSubScreen.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        return runInLayoutSpace(mouseX, mouseY,
                (x, y) -> mouseReleasedInner(x, y, button));
    }

    /** mouseReleased 的实际实现（在「设计空间」内执行） */
    private boolean mouseReleasedInner(double mouseX, double mouseY, int button) {
        // ★ 2026-09-21：结束滚动条拖动
        if (listScrollbarDragging) {
            listScrollbarDragging = false;
            persistViewState();
            return true;
        }
        if (button == 0 && draggingLevelSkillId != null) {
            draggingLevelSkillId = null;
            return true;
        }
        if (activeSubScreen != null) {
            activeSubScreen.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // ★ 2026-09-21：设置子界面脱离缩放 → 用未换算坐标处理
        if (activeSubScreen instanceof SettingsSubScreen
                && activeSubScreen.isMouseOver(mouseX, mouseY)) {
            activeSubScreen.mouseScrolled(mouseX, mouseY, verticalAmount);
            return true;
        }
        return runInLayoutSpace(mouseX, mouseY,
                (x, y) -> mouseScrolledInner(x, y, horizontalAmount, verticalAmount));
    }

    /** mouseScrolled 的实际实现（在「设计空间」内执行） */
    private boolean mouseScrolledInner(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 子界面打开：仅面板内滚轮交给子界面（面板外正常滚动）
        if (activeSubScreen != null && activeSubScreen.isMouseOver(mouseX, mouseY)) {
            activeSubScreen.mouseScrolled(mouseX, mouseY, verticalAmount);
            return true;
        }
        if (verticalAmount == 0 || draggingLevelSkillId != null) {
            return true;
        }
        // ============ 滚轮三向分派（2026-09-19 用户要求：★ 必须在【对应分区】内才生效）============
        //   ① 鼠标在【类别行分区】   → 只横向滚动类别按钮（放不下时）
        //   ② 鼠标在【技能行分区】的【等级进度条】上 → 只调生效等级
        //   ③ 鼠标在【技能行分区】其余位置 → 只竖向滚动技能列表
        //   ④ 鼠标在【标题行分区】   → 不响应（该分区没有对应滚动）
        if (isMouseOverCategories(mouseX, mouseY)) {
            int catScrollMax = Math.max(0, catContentW - catViewW());
            if (catScrollMax > 0) {
                catScrollX = Math.max(0, Math.min(catScrollMax, catScrollX + (verticalAmount > 0 ? -CAT_BTN_GAP * 4 : CAT_BTN_GAP * 4)));
                rebuildCategoryRow();
                markViewStateChanged();
                persistViewState();
            }
            return true;
        }
        if (!isMouseOverSkillList(mouseX, mouseY)) {
            return true; // ④ 标题行分区（或界面外）：不响应滚轮
        }
        // ② 进度条上 → 调级
        SkillButton button = findHoveredSkillButton(mouseX, mouseY);
        if (button != null && overLevelBar(mouseX, mouseY, button)) {
            int maxLevel = Skills.getMaxPoints(button.skillId());
            int points = learnedSkills.getOrDefault(button.skillId(), 0);
            if (maxLevel > 1 && points > 0) {
                int active = activeLevels.getOrDefault(button.skillId(), points);
                int step;
                if (Screen.hasShiftDown() && Screen.hasControlDown()) {
                    step = 100;
                } else if (Screen.hasShiftDown()) {
                    step = 10;
                } else {
                    step = 1;
                }
                int next = Math.max(0, Math.min(points, active + (verticalAmount > 0 ? step : -step)));
                activeLevels.put(button.skillId(), next);
                rowVisualCache.remove(button.skillId());
                org.zifeng.skilltree.network.ModNetwork.sendToServer(new SetSkillLevelC2SPacket(button.skillId(), next));
                return true;
            }
        }
        // ③ 竖向滚动技能列表（滚轮一格 = 3 行，与常见列表一致）
        int step = (BUTTON_HEIGHT + VERTICAL_SPACING) * 3;
        scrollY = Math.max(0, Math.min(scrollMax, scrollY + (verticalAmount > 0 ? -step : step)));
        if (!isSearching()) {
            lastNormalScrollY = scrollY;
        }
        markViewStateChanged();
        persistViewState();
        return true;
    }

    /**
     * 跳过原版菜单模糊背景（★ 2026-09-20 渲染性能，1.21.1 专属）。
     *
     * <p>{@code Screen.renderBackground(...)} 会无条件调用
     * {@code renderBlurredBackground(partialTick)} → {@code GameRenderer.processBlurEffect(...)}，
     * 那是一整条<b>全屏后处理模糊链</b>（blur.json 多趟 ping-pong）+ 一次模糊纹理铺屏。
     *
     * <p>而本界面紧接着就用 {@code Config.SKILL_TREE_BACKGROUND_COLOR}
     * （默认 {@code 0xFFBEBEBE}，alpha 全不透明，配置注释也明确要求
     * "must be fully opaque"）铺满整屏（见 {@code renderLayer5Background}），
     * 把上面画的一切 100% 盖住 —— 也就是每帧白烧一次全屏模糊。
     * 1.20.1 没有模糊机制（模糊是 1.20.5+ 才加入的），所以这是 1.21.1 独有的浪费。
     *
     * <p>⚠️ 只在<b>背景确实不透明</b>时才跳过：若玩家把背景色配成半透明，
     * 模糊是能透过来的，那就保持原版行为，避免改变观感。
     */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        if (((Config.SKILL_TREE_BACKGROUND_COLOR.get() >>> 24) & 0xFF) == 0xFF) {
            return; // 不透明底色会完全覆盖 → 模糊纯属浪费，跳过
        }
        super.renderBlurredBackground(partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 关闭界面时取消全局状态订阅（2026-08-28）；2026-09-19 起不再保存视图位置/缩放 */
    @Override
    public void onClose() {
        // 关闭技能树 → 服务端取消全局状态订阅（SUB_ALL→0，不再推送，省流量）
        persistViewState();
        sendCloseSubscription();
        super.onClose();
    }

    /**
     * 屏幕被移除（任何路径）时兜底退订（★ 2026-09-20）。
     *
     * <p>原来只在 {@link #onClose()} 里退订，而 {@code onClose()} 只在
     * 「玩家自己关界面」（Esc / 关闭按钮）时被调 —— 其他模组直接
     * {@code Minecraft.setScreen(...)} 顶掉本界面时只会触发 {@code Screen.removed()}，
     * 订阅会一直留到玩家登出（服务端有 removeSubscription 兜底，所以不是无限泄漏，
     * 但会白推流量，也让「订阅 = 界面驱动」的语义不完整）。
     */
    @Override
    public void removed() {
        super.removed();
        persistViewState();
        sendCloseSubscription();
    }

    /** 幂等退订：告知服务端本玩家不再需要全局状态推送 */
    private void sendCloseSubscription() {
        if (subscriptionClosed) {
            return;
        }
        subscriptionClosed = true;
        org.zifeng.skilltree.network.ModNetwork.sendToServer(
                new org.zifeng.skilltree.network.OpenSkillTreeC2SPacket(false));
    }

    double toPanelX(double screenX) {
        return screenX - rowLeft();
    }

    /** 行内局部坐标的 Y（原点 = 列表左上角，叠加滚动偏移；2026-09-19 列表式改版，不再有平移/缩放） */
    double toPanelY(double screenY) {
        return screenY - (listTop() - scrollY);
    }
}
