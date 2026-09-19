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
    /** 下一级消耗区（固定宽） */
    private static final int R_COST_X = 144;
    private static final int R_COST_W = 56;
    /** 等级进度条：★ 弹性区（左边界固定，宽度 = 内容区宽 - 左边界 - 等级区） */
    private static final int R_BAR_X = 204;
    private static final int R_BAR_MIN_W = 40;
    private static final int R_BAR_H = 4;
    /** 等级文字（右对齐：99/100；贴内容区右端） */
    private static final int R_LV_W = 52;
    /** 进度条与等级文字之间的间距 */
    private static final int R_LV_GAP = 6;
    /** 内容区最小宽（窗口太小时到此为止，不再压缩） */
    private static final int CONTENT_MIN_W = 204 + R_BAR_MIN_W + R_LV_GAP + R_LV_W + 4;

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
    private static final int SCROLLBAR_MARGIN = 3;
    /** 贴片距分区框内顶的内边距（2026-09-19） */
    private static final int ROW_PAD_TOP = 3;
    /**
     * 贴片宽度上限（2026-09-19）。
     * <p>窗口拉很宽时不让贴片一路铺到屏幕右端 —— 否则等级进度条被拉成上千像素的长条，反而看不清。
     * 原版列表（快捷键界面等）同样有宽度上限。
     */
    private static final int ROW_MAX_W = 560;

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
    /** 技能列表竖向滚动偏移（像素） */
    private int scrollY = 0;
    /** 滚动上限（= max(0, 内容高 - 视口高)），重建时重算 */
    private int scrollMax = 0;
    /** 正在拖动生效等级滑块的技能；拖动时等级文字、填充和服务端状态同步更新。 */
    private String draggingLevelSkillId = null;
    /** 分类按钮行横向滚动偏移（像素；按钮太多时可滚） */
    private int catScrollX = 0;
    /** 分类按钮行内容总宽（重建时重算） */
    private int catContentW = 0;
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
     * 贴片总宽（= 内容区 + Q + E + R 三格，整行一张贴片；左对齐，宽度横向上限 {@link #ROW_MAX_W}）。
     *
     * <p>★ 为什么要设上限：窗口拉得很宽时，如果贴片一路铺到屏幕右端，
     * 中间的等级进度条会被拉成一千多像素的长条 —— 反而<b>更看不清</b>。
     * 原版列表（如快捷键界面）同样给列表宽度设了上限，不会铺满整屏。
     * <p>超过上限的部分留白，贴片仍然靠左（用户要求技能行左对齐）。
     */
    int rowW() {
        return Math.min(ROW_MAX_W, Math.max(0, rowRight() - rowLeft()));
    }

    /** 贴片内「技能内容区」宽（= 贴片总宽 - 三格宽）—— 进度条的弹性空间来源 */
    int contentW() {
        int w = rowW() - (KEY_BOX_WIDTH + KEY2_BOX_WIDTH + KEY3_BOX_WIDTH);
        return Math.max(CONTENT_MIN_W, w);
    }

    /** 等级进度条宽度（弹性） */
    private int barW() {
        return Math.max(R_BAR_MIN_W, contentW() - R_BAR_X - R_LV_GAP - R_LV_W);
    }

    /** 等级文字左边界（内容区右端左移 R_LV_W） */
    private int lvX() {
        return contentW() - R_LV_W;
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

    /** 打开子界面（同类型已打开则关闭切换） */
    void openSubScreen(SkillSubScreen sub) {
        if (activeSubScreen != null) {
            activeSubScreen.onClose();
        }
        activeSubScreen = sub;
        if (activeSubScreen != null) {
            activeSubScreen.init(width, height);
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
        return switch (auraTargetModes.getOrDefault(skillId, 0)) {
            case 1 -> t("mode_friendly");
            case 2 -> t("mode_all");
            default -> t("mode_hostile");
        };
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
        // 2026-09-19 列表式改版：不再有平移/缩放，故不再恢复上次视图状态（只需恢复按键绑定）
        org.zifeng.skilltree.client.SkillKeyBinds.load();
        // 每次开界面重建一次本地化缓存（语言可能中途切换过；建完就整局复用）
        LANG_CACHE.clear();
        KEY_NAME_CACHE.clear();
        updateData(skillPoints, learnedSkills, toggles, activeLevels, auraEnabled, auraTargetModes);
    }

    public void updateData(double skillPoints, Map<String, Integer> learnedSkills, Map<String, Boolean> toggles,
                           Map<String, Integer> activeLevels, boolean auraEnabled, Map<String, Integer> auraTargetModes) {
        this.skillPoints = skillPoints;
        this.learnedSkills.clear();
        this.learnedSkills.putAll(learnedSkills);
        this.toggles.clear();
        this.toggles.putAll(toggles);
        this.activeLevels.clear();
        this.activeLevels.putAll(activeLevels);
        this.auraEnabled = auraEnabled;
        this.auraTargetModes.clear();
        if (auraTargetModes != null) {
            this.auraTargetModes.putAll(auraTargetModes);
        }
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
                             String name, int nameW, String effect, int effectW, String costText,
                             String attrText, int attrW) {
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
        cachedHeaderMaxWidth = font.width(cachedHeaderText);
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
     * 按像素宽度裁剪文本（超出部分截掉）。
     * ⚠️ 原实现是「逐字符 + 每次重新测宽」的 while 循环 → O(n²)（每帧 3 处 × 120 按钮，
     *    最坏上万次字形查询）；这里改为二分查找 O(n log n)，结果完全相同（最长可容纳前缀）。
     */
    private String clipToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || font.width(text) <= maxWidth) {
            return text;
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (font.width(text.substring(0, mid)) <= maxWidth) {
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
        buttons.clear();
        // 数据变了 → 行视觉缓存全部失效（含技能点变化引起的可学/颜色变化）
        rowVisualCache.clear();
        int y = 0;
        for (String skill : categorySkills(selectedCategory)) {
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
        return Math.max(0, width - FRAME_MARGIN * 2 - FRAME_LINE * 2);
    }

    /**
     * 重建类别按钮行的几何（屏幕坐标）。
     *
     * <p>★ 2026-09-19 用户要求：类别行<b>水平居中</b>——整组按钮在界面内宽里居中；
     * 如果宽到放不下（中英文差异 + 小窗口），才退化为「从左边框起、可横向滚动」。
     * <p>按钮宽度随文案长短自适应。
     */
    private void rebuildCategoryRow() {
        if (font == null) {
            return; // 字体未就绪（构造函数阶段）→ 等 init() 后再测宽
        }
        int total = 0;
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            String title = catTitles[i] != null ? catTitles[i]
                    : Component.translatable("ui.zifeng_s_custom_skill_tree." + CATEGORY_TITLE_KEYS[i]).getString();
            catTitles[i] = title; // 缓存（只在此处解析，每帧不再查语言表）
            catButtonW[i] = font.width(title) + CAT_BTN_PAD * 2;
            total += catButtonW[i] + CAT_BTN_GAP;
        }
        catContentW = Math.max(0, total - CAT_BTN_GAP);
        int availW = catViewW();
        int baseX;
        if (catContentW <= availW) {
            catScrollX = 0; // 放得下 → 不滚动，整组居中
            baseX = frameLeft() + FRAME_LINE + (availW - catContentW) / 2;
        } else {
            catScrollX = Math.max(0, Math.min(catScrollX, catContentW - availW));
            baseX = frameLeft() + FRAME_LINE - catScrollX;
        }
        int x = baseX;
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            catButtonX[i] = x;
            x += catButtonW[i] + CAT_BTN_GAP;
        }
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
        java.util.Arrays.fill(catTitles, null);
        rebuildButtons();
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
        rebuildButtons();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        beginFrame();      // 2026-09-15：帧序号 +1（帧内缓存失效判断）
        updateViewport();  // 2026-09-15：可视区域（屏幕外按钮/列标题整块跳过）
        renderBackground(guiGraphics);
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
        if (activeSubScreen != null) {
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
        guiGraphics.enableScissor(frameLeft() + FRAME_LINE, listTop(),
                frameRight() - FRAME_LINE, listBottom());
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(rowLeft(), listTop() - scrollY, 0);
        for (SkillButton button : buttons) {
            if (!isButtonVisible(button)) {
                continue; // 视口剔除：屏幕外的行整块跳过（贴片/图标/文字/格）
            }
            renderSkillButton(guiGraphics, button);
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
        // ⚠️ 2026-09-19 性能优化：文本在建行时缓存 + 统一用默认的 gui 批次 fill —— 与「原版按钮」同一套做法。
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            int x = catButtonX[i];
            int w = catButtonW[i];
            if (x + w < frameLeft() || x > frameRight()) {
                continue; // 横向滚动到框外的跳过
            }
            boolean selected = (i == selectedCategory);
            boolean hovered = lastMouseX >= x && lastMouseX <= x + w
                    && lastMouseY >= y && lastMouseY <= y + CAT_BTN_H;
            // ★ 2026-09-19：类别按钮改成【浅色圆角胶囊】（子贴片现在是浅色了，深色块会显重）
            int accent = CATEGORY_COLORS[i];
            int bg = selected ? darken(accent, 0.5f) : (hovered ? 0xFFFFFFFF : 0xFFF6F6FA);
            int border = selected ? accent : (hovered ? accent : ((accent & 0x00FFFFFF) | 0x70000000));
            fillRound(guiGraphics, x, y, x + w, y + CAT_BTN_H, CAT_BTN_H / 2, bg);
            strokeRound(guiGraphics, x, y, x + w, y + CAT_BTN_H, CAT_BTN_H / 2, border);
            String title = catTitles[i] != null ? catTitles[i] : "";
            int color = selected ? 0xFFFFFFFF : (hovered ? 0xFF2A2A34 : 0xFF4A4A56);
            guiGraphics.drawCenteredString(font, title, x + w / 2, y + (CAT_BTN_H - font.lineHeight) / 2, color);
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
        int barH = Math.max(16, (int) ((long) viewH * viewH / Math.max(1, viewH + scrollMax)));
        int barY = top + (int) ((long) (viewH - barH) * scrollY / Math.max(1, scrollMax));
        guiGraphics.fill(x, top, x + SCROLLBAR_W, top + viewH, 0x55000000);
        guiGraphics.fill(x, barY, x + SCROLLBAR_W, barY + barH, 0xFF87CEEB);
    }

    /**
     * 第三图层（中间）：所有悬浮显示（技能悬停提示）。
     * 背景用 guiOverlay 渲染 → 盖住第四图层按钮；但先于第二图层（边框）/第一图层（面板）提交 → 被它们盖住。
     * ⚠️ 鼠标在第一图层任何 UI 元素上（属性面板/顶部标题/底部提示条/右下角开关按钮）时不显示技能提示。
     */
    private void renderLayer3Tooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (isOverUI(mouseX, mouseY)) {
            return;
        }
        // 按键框悬停提示（2026-08-13 修复）：必须在无变换的 L3 层用屏幕坐标绘制，
        // 否则 renderTooltip 在 L4 技能树变换内坐标错乱（提示偏离鼠标）
        for (SkillButton button : buttons) {
            if (!isButtonVisible(button)) {
                continue; // 视口剔除（2026-09-15）
            }
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
                guiGraphics.renderTooltip(font, java.util.List.of(
                                Component.translatable("ui.zifeng_s_custom_skill_tree.key_toggle", Skills.getDisplayNameComponent(button.skillId())),
                                Component.literal(t("tip_current_status") + (enabled ? t("status_on") : t("status_off"))),
                                Component.literal(boundText),
                                Component.literal(t("tip_bind_hint")),
                                Component.literal(t("tip_clear_hint") + "(Backspace/Delete)")),
                        java.util.Optional.empty(), mouseX, mouseY + 12);
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
                    guiGraphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY + 12);
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
                        guiGraphics.renderTooltip(font, java.util.List.of(
                                        Component.translatable("ui.zifeng_s_custom_skill_tree.key_trigger", Skills.getDisplayNameComponent(skillId)),
                                        Component.literal(t("tip_haul_trigger")),
                                        Component.literal(trigText),
                                        Component.literal(t("tip_bind_hint")),
                                        Component.literal(t("tip_clear_hint") + "(Backspace/Delete)")),
                                java.util.Optional.empty(), mouseX, mouseY + 12);
                    } else if (Skills.BLINK.equals(skillId)) {
                        // 闪现（2026-09-07 规范改造）：触发键 = 按下向视线方向传送一次
                        guiGraphics.renderTooltip(font, java.util.List.of(
                                        Component.translatable("ui.zifeng_s_custom_skill_tree.key_trigger", Skills.getDisplayNameComponent(skillId)),
                                        Component.literal(t("tip_blink_trigger")),
                                        Component.literal(trigText),
                                        Component.literal(t("tip_bind_hint")),
                                        Component.literal(t("tip_clear_hint") + "(Backspace/Delete)")),
                                java.util.Optional.empty(), mouseX, mouseY + 12);
                    } else {
                        guiGraphics.renderTooltip(font, java.util.List.of(
                                        Component.translatable("ui.zifeng_s_custom_skill_tree.key_trigger", Skills.getDisplayNameComponent(skillId)),
                                        Component.literal(trigText),
                                        Component.literal(t("tip_bind_hint")),
                                        Component.literal(t("tip_clear_hint") + "(Backspace/Delete)")),
                                java.util.Optional.empty(), mouseX, mouseY + 12);
                    }
                    return;
                }
            }
        }
        for (SkillButton button : buttons) {
            if (!isButtonVisible(button)) {
                continue; // 视口剔除（2026-09-15）
            }
            if (overNameArea(mouseX, mouseY, button)) {
                renderSkillTooltip(guiGraphics, button, mouseX, mouseY);
                break;
            }
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

    /** 按钮宽：5 字符（约 30px）+ 左右留白 ≈ 58px；样式固定不随文字变化 */
    private static final int BOTTOM_BTN_W = 58;
    private static final int BOTTOM_BTN_H = 16;
    private static final int BOTTOM_BTN_GAP = 4;
    private static final int BOTTOM_BTN_MARGIN = 8;

    /** 属性面板按钮（右下角，右侧） */
    private int panelToggleX() {
        return width - BOTTOM_BTN_MARGIN - BOTTOM_BTN_W;
    }

    private int panelToggleY() {
        return height - BOTTOM_BTN_MARGIN - BOTTOM_BTN_H;
    }

    /** HUD 调整按钮（属性面板按钮左边，平行同高） */
    private int hudBtnX() {
        return panelToggleX() - BOTTOM_BTN_GAP - BOTTOM_BTN_W;
    }

    private int hudBtnY() {
        return panelToggleY();
    }

    /** 统一按钮绘制：底色 + 边框 + 悬停 + 打开子界面高亮 + 居中固定文字 */
    private void drawBottomButton(GuiGraphics guiGraphics, int x, int y, String text, boolean active) {
        boolean hovered = lastMouseX >= x && lastMouseX <= x + BOTTOM_BTN_W && lastMouseY >= y && lastMouseY <= y + BOTTOM_BTN_H;
        int bg = active ? 0xFF2A6A8A : (hovered ? 0xFF3A6EA5 : 0xFF24476E);
        int border = active ? 0xFF66CCFF : (hovered ? 0xFFB0D8FF : 0xFF87CEEB);
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        guiGraphics.fill(overlay, x, y, x + BOTTOM_BTN_W, y + BOTTOM_BTN_H, bg);
        guiGraphics.fill(overlay, x, y, x + BOTTOM_BTN_W, y + 1, border);
        guiGraphics.fill(overlay, x, y + BOTTOM_BTN_H - 1, x + BOTTOM_BTN_W, y + BOTTOM_BTN_H, border);
        guiGraphics.fill(overlay, x, y, x + 1, y + BOTTOM_BTN_H, border);
        guiGraphics.fill(overlay, x + BOTTOM_BTN_W - 1, y, x + BOTTOM_BTN_W, y + BOTTOM_BTN_H, border);
        guiGraphics.drawCenteredString(font, text, x + BOTTOM_BTN_W / 2, y + 4, active ? 0xFF66CCFF : 0xFF87CEEB);
    }

    /** 右下角两个功能按钮（HUD 调整 + 属性面板），统一风格 */
    private void renderBottomButtons(GuiGraphics guiGraphics) {
        boolean panelOpen = activeSubScreen instanceof AttributePanelSubScreen;
        boolean hudOpen = activeSubScreen instanceof HudAdjustSubScreen;
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
        guiGraphics.drawString(font, cachedHeaderText, b[0], textY, 0xFF55FF55);
    }

    /** 悬停提示行（文本 + 颜色 + 字号倍率） */
    private record TooltipLine(String text, int color, float scale) {
    }

    /**
     * 构建技能悬停提示行列表：标题（类型色大字号）→ 描述 → 消耗（金）→ 模组缺失红字 → 前置状态 → 操作提示。
     * 不同类型配色：基础=天蓝 / 增幅=橙 / 终极=红 / 光环=紫 / 魔法=青绿（与列标题一致）。
     * 纯数据构建（无绘制），供预计算 tooltip 边界与绘制共用。
     */
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
                String unit = Skills.GIFT_MINE_BAPTISM.equals(skillId) ? t("unit_blocks") : t("unit_meter");
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

        // 5. 前置需求（金色标题 + 绿/红状态）
        java.util.List<Map.Entry<String, Integer>> prereqs = Skills.getPrerequisites(skillId);
        if (!prereqs.isEmpty()) {
            lines.add(new TooltipLine(" ", 0xFF000000, 0.6F));
            lines.add(new TooltipLine(t("tip_prereq"), 0xFFFFD700, 0.9F));
            for (Map.Entry<String, Integer> entry : prereqs) {
                String required = entry.getKey();
                int need = entry.getValue();
                int have = learnedSkills.getOrDefault(required, 0);
                boolean met = have >= need;
                lines.add(new TooltipLine((met ? "✓ " : "✗ ") + Skills.getDisplayNameComponent(required).getString() + " " + have + "/" + need,
                        met ? 0xFF55FF55 : 0xFFFF5555, 0.9F));
            }
        }

        // 6. 底部操作/状态提示（小字号灰）
        lines.add(new TooltipLine(" ", 0xFF000000, 0.6F));
        lines.add(new TooltipLine(buildStatusText(skillId, type, points, enabled), 0xFF888888, 0.8F));
        return lines;
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
        int padX = 6, padY = 4, gap = 2;
        int maxWidth = 0;
        int totalHeight = 0;
        for (TooltipLine line : lines) {
            int w = (int) Math.ceil(font.width(line.text()) * line.scale());
            maxWidth = Math.max(maxWidth, w);
            totalHeight += (int) Math.ceil((font.lineHeight + gap) * line.scale());
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
        int padX = 6, padY = 4, gap = 2;
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
            guiGraphics.pose().scale(s, s, 1);
            guiGraphics.drawString(font, line.text(), 0, 0, line.color());
            guiGraphics.pose().popPose();
            curY += (int) Math.ceil((font.lineHeight + gap) * s);
        }
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
        // 右下角两个功能按钮（HUD调整 + 属性面板，2026-09-01 统一风格）
        if (mouseX >= panelToggleX() && mouseX <= panelToggleX() + BOTTOM_BTN_W
                && mouseY >= panelToggleY() && mouseY <= panelToggleY() + BOTTOM_BTN_H) {
            return true;
        }
        if (isHudPanelHit(mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    /** HUD 调整按钮区域命中（属性面板按钮左边，平行同高） */
    private boolean isHudPanelHit(double mouseX, double mouseY) {
        return mouseX >= hudBtnX() && mouseX <= hudBtnX() + BOTTOM_BTN_W
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
        double lx = toPanelX(mouseX);
        double ly = toPanelY(mouseY);
        return lx >= button.x() + R_NAME_X && lx <= button.x() + R_NAME_X + R_NAME_W
                && ly >= button.y() && ly <= button.y() + BUTTON_HEIGHT;
    }

    /**
     * 预计算当前悬停按钮的 tooltip 边界 [x, y, w, h]（屏幕坐标，含钳制）。
     * 在第四图层渲染前调用，供图标跳过判定：被 tooltip 覆盖的图标不渲染（tooltip 背景半透明，否则图标会透出混合）。
     */
    private void updateActiveTooltipBounds(int mouseX, int mouseY) {
        activeTooltipBounds = null;
        // 2026-09-15 性能优化：tooltip 行列表在此构建后缓存，renderSkillTooltip 直接复用
        //（原先每帧构建两次：一次算边界、一次绘制 → 200 行方法跑两遍）
        activeTooltipLines = null;
        if (isOverUI(mouseX, mouseY)) {
            return;
        }
        for (SkillButton button : buttons) {
            if (!isButtonVisible(button)) {
                continue; // 视口剔除（2026-09-15）
            }
            if (overNameArea(mouseX, mouseY, button)) {
                activeTooltipLines = buildTooltipLines(button);
                activeTooltipBounds = computeTooltipLayout(activeTooltipLines, mouseX, mouseY);
                return;
            }
        }
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
        // 4. 右下角两个功能按钮（属性面板 + HUD调整，2026-09-01）
        if (overlapRatio(ix1, iy1, ix2, iy2, panelToggleX(), panelToggleY(), panelToggleX() + BOTTOM_BTN_W, panelToggleY() + BOTTOM_BTN_H) >= ICON_OVERLAP_SKIP_RATIO) return true;
        if (overlapRatio(ix1, iy1, ix2, iy2, hudBtnX(), hudBtnY(), hudBtnX() + BOTTOM_BTN_W, hudBtnY() + BOTTOM_BTN_H) >= ICON_OVERLAP_SKIP_RATIO) return true;
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

    private void renderSkillButton(GuiGraphics guiGraphics, SkillButton button) {
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
        final int fp = rowFingerprint(textVersion, points, activeLevel, enabled, toolMode, (int) (skillPoints * 10));
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
        final double mx = toPanelX(lastMouseX);
        final double my = toPanelY(lastMouseY);
        final boolean onRow = my >= y0 && my <= y0 + h;
        final boolean hovered = onRow && mx >= x0 && mx <= x0 + total;

        final int bg = hovered ? v.bgHover() : v.bg();
        final int borderColor = hovered ? v.borderHover() : v.border();

        // ① 整张贴片底色（圆角；四段共用一个底）
        fillRound(guiGraphics, x0, y0, x0 + total, y0 + h, R_ROW, bg);
        // 极淡顶部高光让贴片有层次，但保持像素 UI 的清晰边缘。
        guiGraphics.fill(x0 + R_ROW, y0 + 1, x0 + total - R_ROW, y0 + 2, 0x22FFFFFF);

        // ② 左侧类别色条（3px，顶部/底部跟着圆角内缩）—— 横向扫一眼就能分出类别
        final int accent = categoryAccent();
        final int stripeColor = v.enabled() ? (v.learned() ? accent : (accent & 0x00FFFFFF) | 0x77000000)
                : 0xFF7A7A7A;
        fillRound(guiGraphics, x0 + 1, y0 + 1, x0 + 4, y0 + h - 1, 2, stripeColor);

        // ③ 三个按键格（贴片【内部】的格，各自画一层底色，不画独立外框）
        final int qx = x0 + cw;
        final int ex = qx + KEY_BOX_WIDTH;
        final int rx = ex + KEY2_BOX_WIDTH;
        renderKeyCell(guiGraphics, skillId, qx, y0, KEY_BOX_WIDTH, h, onRow && mx >= qx && mx < ex,
                true, 1, keyBindSkillId, keyBindListening, org.zifeng.skilltree.client.SkillKeyBinds.getKey(skillId));
        renderKeyCell(guiGraphics, skillId, ex, y0, KEY2_BOX_WIDTH, h, onRow && mx >= ex && mx < rx,
                v.slot2Usable(), 2, levelKeyBindSkillId, levelKeyBindListening, org.zifeng.skilltree.client.SkillKeyBinds.getLevelKey(skillId));
        renderKeyCell(guiGraphics, skillId, rx, y0, KEY3_BOX_WIDTH, h, onRow && mx >= rx && mx < x0 + total,
                v.slot3Usable(), 3, triggerKeyBindSkillId, triggerKeyBindListening, org.zifeng.skilltree.client.SkillKeyBinds.getTriggerKey(skillId));

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

        // ---- ① 名称（图标右侧）----
        guiGraphics.drawString(font, v.name(), x0 + R_NAME_X, textY, v.nameColor());

        // ---- ①b 属性加成（★ 2026-09-19 用户要求：名称后面显示「单技能增加属性」，把原来浪费的空位用起来）----
        if (v.attrW() > 0) {
            guiGraphics.drawString(font, v.attrText(), x0 + R_NAME_X + v.nameW() + 5, textY,
                    lighten(categoryAccent(), 0.5f));
        }

        // ---- ② 下一级消耗（行内已显示，故 tooltip 不再重复）----
        guiGraphics.drawString(font, v.costText(), x0 + R_COST_X, textY, v.costColor());

        // ---- ③ 等级进度条（★ 胶囊 + 类别色；宽度弹性随窗口变化；鼠标悬停其上才响应滚轮调级）----
        renderLevelBar(guiGraphics, x0 + R_BAR_X, y0 + (h - R_BAR_H2) / 2, barW(), v);

        // ---- ④ 等级文字（右对齐到内容区右端）----
        guiGraphics.drawString(font, v.effect(),
                x0 + lvX() + R_LV_W - v.effectW(), textY, v.lvColor());

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
        final int accent = categoryAccent();
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
            barColor = !enabled ? 0xFF8A8A8A : (prereqMet ? categoryAccent() : 0xFF9A9A9A);
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
        String attrText = skillAttrText(skillId, points);
        if (!attrText.isEmpty()) {
            // 名称区就那么大，属性文本超长先裁掉（防止压到右侧消耗列）
            attrText = clipToWidth(attrText, R_NAME_W - 34);
        }
        int attrW = attrText.isEmpty() ? 0 : font.width(attrText);
        // 名称让位给属性文本（名称区宽 - 属性宽 - 间距）
        int nameMaxW = Math.max(24, R_NAME_W - (attrW > 0 ? attrW + 6 : 2));
        ButtonTexts texts = buildButtonTexts(skillId, type, isTool, enabled, toolMode, points, activeLevel, nextCost, nameMaxW);
        String costText = nextCostDisplay(skillId, points, nextCost);

        return new RowVisual(fp, type, isTool, enabled, learned, bg, bgHover, border, borderHover,
                nameColor, costColor, lvColor, barFillR, barTickR, barColor,
                isLevelBindable(skillId), Skills.isTriggerBindable(skillId), iconStack, iconTex,
                texts.name(), font.width(texts.name()), texts.effect(), font.width(texts.effect()), costText,
                attrText, attrW);
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
        guiGraphics.drawCenteredString(font, text, x + w / 2, y + (h - font.lineHeight) / 2, color);
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
        // 名称（列表行：按名称区宽裁剪；禁用时加「⊘」前缀）
        String name = clipToWidth((enabled ? "" : "⊘ ") + Skills.getDisplayNameComponent(skillId).getString(), nameMaxW);
        // 等级文字（列表行：如 "99/100"；工具卡 = 当前模式名）
        String effectText = clipToWidth(isTool
                ? t(org.zifeng.skilltree.client.StickToolModes.modeLang(toolMode))
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
        double localX = toPanelX(mouseX) - (button.x() + R_BAR_X);
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
        double lx = toPanelX(mouseX);
        double ly = toPanelY(mouseY);
        int barX = button.x() + R_BAR_X;
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
     * <p>列表行空间紧，所以只显示一个带前缀的数字（如 {@code +50}），详细单位/含义由 tooltip 承担。
     * 已满级显示 {@code MAX}；一次性已解锁显示 {@code OK}。
     */
    private String nextCostDisplay(String skillId, int points, double nextCost) {
        if (Skills.isStickTool(skillId)) {
            return "";
        }
        int max = Skills.getMaxPoints(skillId);
        if (max > 0 && points >= max) {
            return "MAX";
        }
        if (nextCost <= 0) {
            return "—"; // 时间系列馈赠：按游戏时长激活，不消耗点数
        }
        String num = fmtCost(nextCost);
        return "+" + clipToWidth(num, R_COST_W - 2);
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
        addRow(rows, t("panel_dmg_reduce"), attrVal(player, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION.get(), rec) * 100, "%.0f%%");

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
        double swim = player.getAttribute(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get()) != null
                ? attrVal(player, net.minecraftforge.common.ForgeMod.SWIM_SPEED.get(), rec) * 3.35 : 0;
        addRow(rows, t("panel_swim"), swim, "%.2f" + t("unit_bps"));
        // 跳跃高度（格）= JUMP_STRENGTH² × 6.25（无药水时）
        double jump = attrVal(player, Attributes.JUMP_STRENGTH, rec);
        addRow(rows, t("panel_jump"), jump * jump * 6.25, "%.2f" + t("unit_block"));

        rows.add(new String[]{"—— " + t("panel_cat_production") + " ——", "", "#777777"});
        // 挖速用自定义 ModAttributes.MINING_EFFICIENCY（1.20.1 原版无此属性，直接反映实际挖掘加速）
        addRow(rows, t("panel_mining"), attrVal(player, org.zifeng.skilltree.init.ModAttributes.MINING_EFFICIENCY.get(), rec), "%.1f");
        addRow(rows, t("panel_luck"), attrVal(player, Attributes.LUCK, rec), "%.1f");
        addRow(rows, t("panel_regen"), SkillEffects.getRegenPerSecond(rec), "%.1f");
        addRow(rows, t("panel_mob_drop"), SkillEffects.getMobDropMultiplier(rec), "%.2f" + t("unit_x"));
        addRow(rows, t("panel_block_drop"), SkillEffects.getBlockDropMultiplier(rec), "%.2f" + t("unit_x"));
        addRow(rows, t("panel_xp"), SkillEffects.getExperienceMultiplier(rec), "%.2f" + t("unit_x"));
        // 掉落节点类终极：刷怪蛋/头颅概率 + 战利品爆炸倍率（没学不显示）
        int spawnEgg = rec.isEnabled(Skills.MOB_SPAWN_EGG) ? rec.getActiveLevel(Skills.MOB_SPAWN_EGG) : 0;
        if (spawnEgg > 0) addRow(rows, t("panel_spawn_egg"), spawnEgg * 10, "%.0f%%");
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
        if (hasArs || hasIron) {
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
        guiGraphics.drawString(font, t("panel_title"), x + 4, panelTop + 4, 0xFFFFD700);

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
                guiGraphics.drawCenteredString(font, row[0], x + PANEL_WIDTH / 2, line, c);
            } else {
                // label 左对齐（超宽裁剪，防止与右侧数值重叠）
                String label = row[0];
                int labelMaxW = PANEL_WIDTH - 70; // 留出数值区（右对齐 ~60px + 边距）
                while (!label.isEmpty() && font.width(label) > labelMaxW) {
                    label = label.substring(0, label.length() - 1);
                }
                guiGraphics.drawString(font, label, x + 4, line, 0xFFAAAAAA);
                // 数值右对齐到滚动条左侧（滚动条在 x+PANEL_WIDTH-6，留 4px 间隔 → 数值起点 = x+PANEL_WIDTH-10-字体宽度）
                String value = row[1];
                guiGraphics.drawString(font, value, x + PANEL_WIDTH - 10 - font.width(value), line, c);
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
            guiGraphics.drawString(font, t("panel_scroll_hint"), x + 4, panelBottom - 14, 0xFF888888);
        }
    }

    /** 底部横版面板（3 列分页） */
    private void renderPanelBottom(GuiGraphics guiGraphics, java.util.List<String[]> rows) {
        int h = 130;
        int x = 10;
        int y = height - h - 10;
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, width - 10 + 2, y + h + 2, 0xF0101010);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, width - 10 + 2, y, 0xFF87CEEB);
        guiGraphics.drawString(font, t("panel_title"), x + 4, y + 4, 0xFFFFD700);

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
                guiGraphics.drawCenteredString(font, row[0], x + width / 2 - 10, py, c);
            } else {
                // label 超宽裁剪（防与右侧数值重叠）
                String label = row[0];
                while (!label.isEmpty() && font.width(label) > 56) {
                    label = label.substring(0, label.length() - 1);
                }
                guiGraphics.drawString(font, label, px, py, 0xFFAAAAAA);
                guiGraphics.drawString(font, row[1], px + 62, py, c);
            }
        }
        if (maxScroll > 0) {
            guiGraphics.drawString(font, t("panel_page_hint"), x + 4, y + h - 12, 0xFF888888);
        }
    }

    private void addRow(java.util.List<String[]> rows, String name, double value, String fmt) {
        rows.add(new String[]{name, String.format(fmt, value)});
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
                           net.minecraft.world.entity.ai.attributes.Attribute attribute,
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

    /**
     * 圆角矩形填充（真圆角，扫描线法；2026-09-19）。
     *
     * <p>相比 {@link #fillRoundedRect} 的「四角阶梯近似」，这里每行都按圆的方程算左右内缩量，
     * 圆弧是平滑的（1px 精度），且只有一个循环 —— 贴片数量多时反而更省。
     *
     * <p>用 {@code RenderType.gui()}（与技能贴片同层、有深度），不是 guiOverlay。
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
        final double rr = r;
        for (int i = 0; i < h; i++) {
            int cy; // 到角部圆圆心的竖向距离
            if (i < r) {
                cy = r - i;
            } else if (i >= h - r) {
                cy = h - 1 - i;
            } else {
                cy = 0;
            }
            int inset = 0;
            if (cy > 0) {
                inset = (int) Math.round(rr - Math.sqrt(Math.max(0.0, rr * rr - (double) cy * cy)));
            }
            guiGraphics.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, color);
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

    /** 取当前类别的强调色（贴片描边/左侧色条/进度条填充都用它） */
    private int categoryAccent() {
        return CATEGORY_COLORS[Math.max(0, Math.min(CATEGORY_COLORS.length - 1, selectedCategory))];
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
    private String skillAttrText(String skillId, int points) {
        if (points <= 0 || Skills.isStickTool(skillId)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        var record = learnedAsRecord();
        for (var e : org.zifeng.skilltree.skill.SkillEffects.attrEntriesOf(skillId)) {
            if (shown >= 2) {
                break;
            }
            double amount;
            boolean pct;
            if (e.op() == org.zifeng.skilltree.skill.SkillEffects.Op.MULT) {
                // 乘算：量 = 技能等级 × 每点倍率 → 百分比
                amount = org.zifeng.skilltree.skill.SkillEffects.effLevel(record, skillId) * e.perPoint().getAsDouble() * 100.0;
                pct = true;
            } else {
                amount = org.zifeng.skilltree.skill.SkillEffects.effLevel(record, skillId) * e.perPoint().getAsDouble();
                pct = false;
            }
            if (Math.abs(amount) < 1e-6) {
                continue;
            }
            if (shown > 0) {
                sb.append(' ');
            }
            sb.append('+').append(fmtAttr(amount, pct)).append(attrSymbol(e.attribute()));
            shown++;
        }
        return sb.toString();
    }

    /** 属性数值格式：百分比取一位小数（整则不带小数），普通值保留最多两位 */
    private static String fmtAttr(double v, boolean percent) {
        double a = Math.abs(v);
        if (a >= 10 || a == Math.floor(a)) {
            return String.valueOf(Math.round(a));
        }
        return percent ? String.format("%.1f", a) : String.format("%.2f", a);
    }

    /**
     * 属性 → 符号（BMP 范围内的字形，MC 自带 Unicode 字体可渲染）。
     * <p>用符号而不是文字，是为了在 24px 行内极窄的空间里塞下两项加成，同时不引入语言差异。
     */
    private static String attrSymbol(net.minecraft.world.entity.ai.attributes.Attribute attr) {
        if (attr == null) {
            return "•";
        }
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)) return "♥";
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)) return "✜";
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)) return "◈";
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) return "⚔";
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) return "⚡";
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)) return "➤";
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH)) return "↑";
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED)) return "✈";
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.LUCK)) return "★";
        if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)) return "⚓";
        if (attr.equals(org.zifeng.skilltree.init.ModAttributes.MINING_EFFICIENCY.get())) return "⛏";
        if (attr.equals(org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION.get())) return "✪";
        if (attr.equals(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get())) return "≈";
        if (attr.equals(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get())
                || attr.equals(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get())) return "↔";
        return "•";
    }

    org.zifeng.skilltree.data.PlayerSkillRecord learnedAsRecord() {
        org.zifeng.skilltree.data.PlayerSkillRecord record = new org.zifeng.skilltree.data.PlayerSkillRecord(java.util.UUID.randomUUID());
        // 直接设置点数（不能用 learnSkill：AURA 消耗递增会因点数不足提前失败，导致光环永远只显示 1 级）
        learnedSkills.forEach(record::setLearnedPoints);
        toggles.forEach(record::setEnabled);
        activeLevels.forEach(record::setActiveLevel);
        return record;
    }

    // ============ 交互 ============

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 属性面板按钮（右下角右侧）：优先响应（即使子界面打开，再点可关闭）Shift+点击切换位置
        if (button == 0 && mouseX >= panelToggleX() && mouseX <= panelToggleX() + BOTTOM_BTN_W
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
            return activeSubScreen.mouseClicked(mouseX, mouseY, button);
        }
        // 中键：新列表布局下不再用于拖动，忽略
        if (button == 2) {
            return true;
        }
        if (button == 0) {
            // 鼠标在第一图层 UI 区域（属性面板/标题/提示条）→ 不透传到下层技能（不透过面板操作）
            if (isOverUI(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            // ① 类别按钮行（分区框 2 内）：切换类别 → 回到顶部
            int catY = catFrameTop() + FRAME_LINE + LIST_TOP_GAP;
            if (mouseY >= catY && mouseY <= catY + CAT_BTN_H) {
                for (int i = 0; i < CATEGORY_COUNT; i++) {
                    if (mouseX >= catButtonX[i] && mouseX <= catButtonX[i] + catButtonW[i]) {
                        if (selectedCategory != i) {
                            selectedCategory = i;
                        }
                        scrollY = 0;
                        rebuildButtons();
                        return true;
                    }
                }
                return true; // 类别行空白处：吞掉，避免点到列表
            }
            // ② 技能行（行内：名称/消耗/进度条/等级 + 固定 3 槽位按键框）
            final double lx = toPanelX(mouseX);
            final double ly = toPanelY(mouseY);
            for (SkillButton skillButton : buttons) {
                if (!isButtonVisible(skillButton)) {
                    continue; // 视口剔除：滚出列表区的行不参与命中
                }
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
            if (isOverUI(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            for (SkillButton skillButton : buttons) {
                if (skillButton.isHovered(mouseX, mouseY, this)) {
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
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        // Ctrl+R：重置鼠标指着的技能（防误触；服务端按该技能返还率加回技能点后回发校准）
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R && Screen.hasControlDown()) {
            // 找到鼠标悬停的技能（第一图层 UI 区域不响应，避免透过面板重置）
            for (SkillButton skillButton : buttons) {
                if (skillButton.isHovered(lastMouseX, lastMouseY, this) && !isOverUI(lastMouseX, lastMouseY)) {
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
        // 子界面打开：面板内 或 正在拖动面板 → 手势转发给子界面（拖动中鼠标移出面板也持续跟手）
        if (activeSubScreen != null && (activeSubScreen.isMouseOver(mouseX, mouseY) || activeSubScreen.isDragging())) {
            return activeSubScreen.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (button == 0 && draggingLevelSkillId != null) {
            for (SkillButton skillButton : buttons) {
                if (draggingLevelSkillId.equals(skillButton.skillId())) {
                    setActiveLevelFromMouse(skillButton, mouseX);
                    return true;
                }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 子界面打开：仅面板内滚轮交给子界面（面板外正常滚动）
        if (activeSubScreen != null && activeSubScreen.isMouseOver(mouseX, mouseY)) {
            return activeSubScreen.mouseScrolled(mouseX, mouseY, delta);
        }
        // ============ 滚轮三向分派（2026-09-19 用户要求：★ 必须在【对应分区】内才生效）============
        //   ① 鼠标在【类别行分区】   → 只横向滚动类别按钮（放不下时）
        //   ② 鼠标在【技能行分区】的【等级进度条】上 → 只调生效等级
        //   ③ 鼠标在【技能行分区】其余位置 → 只竖向滚动技能列表
        //   ④ 鼠标在【标题行分区】   → 不响应（该分区没有对应滚动）
        if (mouseY >= catFrameTop() && mouseY < catFrameBottom()) {
            int catScrollMax = Math.max(0, catContentW - catViewW());
            if (catScrollMax > 0) {
                catScrollX = Math.max(0, Math.min(catScrollMax, catScrollX + (delta > 0 ? -CAT_BTN_GAP * 4 : CAT_BTN_GAP * 4)));
                rebuildCategoryRow();
            }
            return true;
        }
        if (mouseY < listTop() || mouseY > listBottom()) {
            return true; // ④ 标题行分区（或界面外）：不响应滚轮
        }
        // ② 进度条上 → 调级
        for (SkillButton button : buttons) {
            if (!isButtonVisible(button)) {
                continue;
            }
            if (!overLevelBar(mouseX, mouseY, button) || !button.isHovered(mouseX, mouseY, this)) {
                continue;
            }
            int maxLevel = Skills.getMaxPoints(button.skillId());
            if (maxLevel <= 1) {
                break; // 单级技能无等级可调 → 落到列表滚动
            }
            int points = learnedSkills.getOrDefault(button.skillId(), 0);
            if (points <= 0) {
                break; // 未学 → 无生效等级可调
            }
            int active = activeLevels.getOrDefault(button.skillId(), points);
            int step;
            if (Screen.hasShiftDown() && Screen.hasControlDown()) {
                step = 100;
            } else if (Screen.hasShiftDown()) {
                step = 10;
            } else {
                step = 1;
            }
            int next = Math.max(0, Math.min(points, active + (delta > 0 ? step : -step)));
            activeLevels.put(button.skillId(), next);
            rowVisualCache.remove(button.skillId());
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new SetSkillLevelC2SPacket(button.skillId(), next));
            return true;
        }
        // ③ 竖向滚动技能列表（滚轮一格 = 3 行，与常见列表一致）
        int step = (BUTTON_HEIGHT + VERTICAL_SPACING) * 3;
        scrollY = Math.max(0, Math.min(scrollMax, scrollY + (delta > 0 ? -step : step)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 关闭界面时取消全局状态订阅（2026-08-28）；2026-09-19 起不再保存视图位置/缩放 */
    @Override
    public void onClose() {
        // 关闭技能树 → 服务端取消全局状态订阅（SUB_ALL→0，不再推送，省流量）
        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.OpenSkillTreeC2SPacket(false));
        super.onClose();
    }

    double toPanelX(double screenX) {
        return screenX - rowLeft();
    }

    /** 行内局部坐标的 Y（原点 = 列表左上角，叠加滚动偏移；2026-09-19 列表式改版，不再有平移/缩放） */
    double toPanelY(double screenY) {
        return screenY - (listTop() - scrollY);
    }
}
