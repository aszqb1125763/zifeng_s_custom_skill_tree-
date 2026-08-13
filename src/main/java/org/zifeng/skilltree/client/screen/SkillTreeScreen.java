package org.zifeng.skilltree.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;
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
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 46;
    private static final int VERTICAL_SPACING = 2;
    /** 按键框宽度（2026-08-13 内联按键框：位于技能按钮右侧，显示/设置该技能开关快捷键） */
    private static final int KEY_BOX_WIDTH = 44;
    /** 第二列按键框宽度（2026-08-13：等级/目标循环快捷键，位于第一框右侧） */
    private static final int KEY2_BOX_WIDTH = 44;
    /** 按键框与按钮间隙 */
    private static final int KEY_BOX_GAP = 3;
    private static final int HORIZONTAL_SPACING = 30;
    private static final int BORDER_THICKNESS = 4;
    private static final int PANEL_WIDTH = 150;
    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 2.5;

    private double skillPoints;
    private final Map<String, Integer> learnedSkills = new HashMap<>();
    private final Map<String, Boolean> toggles = new HashMap<>();
    private final Map<String, Integer> activeLevels = new HashMap<>();
    private boolean auraEnabled = true;
    /** 各光环目标模式（2026-08-13：每个光环独立，技能ID → 0敌对/1友好/2所有） */
    private final Map<String, Integer> auraTargetModes = new HashMap<>();
    private final List<SkillButton> buttons = new ArrayList<>();

    private double scale = 1.0;
    private double panX = 0;
    private double panY = 0;
    private int lastMouseX;
    private int lastMouseY;
    private int panelScroll = 0; // 属性面板滚动偏移（0 = 顶部）
    /** 当前悬停按钮的 tooltip 边界 [x, y, w, h]（屏幕坐标，预计算供图标跳过判定） */
    private int[] activeTooltipBounds = null;
    /** 按键设置窗口：当前正在设置按键的技能（null = 窗口关闭）；窗口与技能界面同一图层 */
    private String keyBindSkillId = null;
    /** 按键设置窗口：是否正在监听按键输入（点击"设置"后为 true） */
    private boolean keyBindListening = false;
    /** 第二列按键框（等级/目标循环）监听状态：当前正在设置的技能（null = 无） */
    private String levelKeyBindSkillId = null;
    private boolean levelKeyBindListening = false;

    /** 是否可设置等级/目标循环快捷键（2026-08-13 优化）：
     *  光环仅 伤害/速度/治愈 使用目标模式（敌我过滤）；时之环/晴空环/磁力/锁定/强化/虚空之矛 无目标模式不显示。
     *  其余技能：可调等级（上限>1）可循环生效等级。 */
    private boolean isLevelBindable(String skillId) {
        if (Skills.AURA_SKILLS.contains(skillId)) {
            return Skills.AURA_DAMAGE.equals(skillId) || Skills.AURA_SPEED.equals(skillId) || Skills.AURA_HEAL.equals(skillId);
        }
        return Skills.getMaxPoints(skillId) > 1; // 可调等级技能（基础/增幅/多级终极/魔法/多级光环）
    }

    /** 指定光环技能的目标模式文字（0 敌对 / 1 友好 / 2 所有） */
    private String modeTextOf(String skillId) {
        return switch (auraTargetModes.getOrDefault(skillId, 0)) {
            case 1 -> "友好";
            case 2 -> "所有";
            default -> "敌对";
        };
    }

    public SkillTreeScreen(int skillPoints, Map<String, Integer> learnedSkills, Map<String, Boolean> toggles,
                           Map<String, Integer> activeLevels, boolean auraEnabled, Map<String, Integer> auraTargetModes) {
        super(Component.literal("技能树"));
        // 恢复上次退出时的位置/缩放（2026-08-13 需求：上次什么位置退出下次就什么位置）
        org.zifeng.skilltree.client.SkillKeyBinds.load();
        this.panX = org.zifeng.skilltree.client.SkillKeyBinds.getLastPanX();
        this.panY = org.zifeng.skilltree.client.SkillKeyBinds.getLastPanY();
        this.scale = org.zifeng.skilltree.client.SkillKeyBinds.getLastScale();
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
        rebuildButtons();
    }

    /** 每 40 tick 向服务端请求一次技能数据（降低与点击乐观更新的竞态） */
    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        if (tickCounter >= 40) {
            tickCounter = 0;
            PacketDistributor.sendToServer(new OpenSkillTreeC2SPacket());
        }
    }

    private int tickCounter = 0;

    /** 六纵列布局：六列顶部对齐（上方对齐），魔法增幅列在最左，机械共鸣列在最右 */
    private void rebuildButtons() {
        buttons.clear();
        // 6 列中心 x：按钮 150 + 开关框 44 + 等级框 44 + 间隙 = 每列约 250 宽，列中心间隔 280（防第二框与下柱重叠）
        int[] colCenters = {-700, -420, -140, 140, 420, 700};
        placeColumn(Skills.MAGIC_SKILLS, colCenters[0]);
        placeColumn(Skills.BASE_SKILLS, colCenters[1]);
        placeColumn(Skills.AMPLIFY_SKILLS, colCenters[2]);
        placeColumn(Skills.ULTIMATE_SKILLS, colCenters[3]);
        placeColumn(Skills.AURA_SKILLS, colCenters[4]);
        placeColumn(Skills.MACHINE_SKILLS, colCenters[5]);
    }

    /** 五列统一顶部 y（上方对齐）：按钮区上方留空间给列标题（加大后标题占 30px 高） */
    private static final int COLUMN_TOP = -170;

    /** 列标题顶部 y（标题背景上沿，比按钮顶部高 40px，间距充足） */
    private static final int COLUMN_TITLE_TOP = -206;

    /** 单列从上往下摆放（顶部对齐，列高不再影响起始位置） */
    private void placeColumn(List<String> skills, int centerX) {
        int y = COLUMN_TOP;
        for (String skill : skills) {
            buttons.add(new SkillButton(skill, centerX - BUTTON_WIDTH / 2, y));
            y += BUTTON_HEIGHT + VERTICAL_SPACING;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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

        // 第二图层：边框（淡蓝色四边框，在第一图层之下、悬浮显示之上）
        renderLayer2Border(guiGraphics);
        guiGraphics.flush();

        // 第一图层（最顶）：顶部标题区 + 属性面板 + 开关面板 + 面板开关按钮
        renderLayer1HeaderAndPanels(guiGraphics);
    }

    /**
     * 第五图层（最底）：技能树界面背景（纯全屏底色，不含边框）。
     * 被第四图层及以上的所有元素覆盖。
     */
    private void renderLayer5Background(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, width, height, Config.SKILL_TREE_BACKGROUND_COLOR.get());
    }

    /**
     * 第二图层：边框（淡蓝色四边框）。
     * 在第一图层之下、第三图层（悬浮显示）之上 → 盖住悬浮提示边缘、被 UI 面板盖住。
     */
    private void renderLayer2Border(GuiGraphics guiGraphics) {
        int border = Config.SKILL_TREE_BORDER_COLOR.get();
        net.minecraft.client.renderer.RenderType overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        guiGraphics.fill(overlay, 0, 0, width, BORDER_THICKNESS, border);
        guiGraphics.fill(overlay, 0, height - BORDER_THICKNESS, width, height, border);
        guiGraphics.fill(overlay, 0, BORDER_THICKNESS, BORDER_THICKNESS, height - BORDER_THICKNESS, border);
        guiGraphics.fill(overlay, width - BORDER_THICKNESS, BORDER_THICKNESS, width, height - BORDER_THICKNESS, border);
    }

    /**
     * 第四图层：技能树本体（列标题 + 技能按钮）。
     * 属于技能树本体的所有内容（按钮、图标、文字、列标题）都画在这一层。
     */
    private void renderLayer4SkillTree(GuiGraphics guiGraphics) {
        // 技能面板（屏幕中心偏左）
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(width / 2.0 - 60 + panX, height / 2.0 + 10 + panY, 0);
        guiGraphics.pose().scale((float) scale, (float) scale, 1.0F);

        // 列标题（大字号 + 类型色边框背景，跟随各列顶部；与按钮区保持间距）
        int[] colCenters = {-700, -420, -140, 140, 420, 700};
        renderColumnTitle(guiGraphics, "魔法增幅", colCenters[0], 0xFF55FFAA);
        renderColumnTitle(guiGraphics, "基础属性", colCenters[1], 0xFF87CEEB);
        renderColumnTitle(guiGraphics, "特殊增幅", colCenters[2], 0xFFFFAA55);
        renderColumnTitle(guiGraphics, "终极节点", colCenters[3], 0xFFFF5555);
        renderColumnTitle(guiGraphics, "光环", colCenters[4], 0xFFAA55FF);
        renderColumnTitle(guiGraphics, "机械共鸣", colCenters[5], 0xFFD7D7D7);

        // 按键框列标题（2026-08-13 需求：按键框上方加标题，标明两列用途）
        // 第一框（开关）：按钮右缘 + 3；第二框（等级/目标）：再右移 44+3
        // 位置：按钮区顶部上方 24px（列标题下方），小字号 ×0.9
        for (int i = 0; i < colCenters.length; i++) {
            int btnRight = colCenters[i] + BUTTON_WIDTH / 2;
            int k1x = btnRight + KEY_BOX_GAP;
            int k2x = btnRight + KEY_BOX_GAP + KEY_BOX_WIDTH + KEY_BOX_GAP;
            int titleY = COLUMN_TOP - 24;
            renderKeyColumnTitle(guiGraphics, "开关", k1x, titleY, 0xFFFFD700, KEY_BOX_WIDTH);
            // 该列是否有可绑定第二键的技能（光环 或 可调等级技能）
            boolean hasLevelBindable = columnHasLevelBindable(i);
            if (hasLevelBindable) {
                boolean auraCol = (i == 4); // 光环列 → 目标循环
                renderKeyColumnTitle(guiGraphics, auraCol ? "目标" : "等级", k2x, titleY,
                        auraCol ? 0xFFBB77FF : 0xFF87CEEB, KEY2_BOX_WIDTH);
            }
        }

        for (SkillButton button : buttons) {
            renderSkillButton(guiGraphics, button);
        }
        guiGraphics.pose().popPose();
    }

    /** 该列是否含可绑定第二键的技能（光环 或 上限>1 的可调等级技能） */
    private boolean columnHasLevelBindable(int colIndex) {
        List<String> col = switch (colIndex) {
            case 0 -> Skills.MAGIC_SKILLS;
            case 1 -> Skills.BASE_SKILLS;
            case 2 -> Skills.AMPLIFY_SKILLS;
            case 3 -> Skills.ULTIMATE_SKILLS;
            case 4 -> Skills.AURA_SKILLS;
            default -> Skills.MACHINE_SKILLS;
        };
        for (String skillId : col) {
            if (isLevelBindable(skillId)) {
                return true;
            }
        }
        return false;
    }

    /** 按键框列小标题（×0.9 字号 + 深色底 + 类型色文字，宽与按键框一致） */
    private void renderKeyColumnTitle(GuiGraphics guiGraphics, String title, int x, int top, int color, int width) {
        int h = 14;
        var gui = net.minecraft.client.renderer.RenderType.gui();
        fillRoundedRect(guiGraphics, x - 1, top - 1, x + width + 1, top + h + 1, 4, color, gui);
        fillRoundedRect(guiGraphics, x, top, x + width, top + h, 4, 0xCC101018, gui);
        float s = 0.9f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + width / 2.0f, top + h / 2.0f - font.lineHeight * s / 2.0f, 0);
        guiGraphics.pose().scale(s, s, 1);
        guiGraphics.drawCenteredString(font, title, 0, 0, color);
        guiGraphics.pose().popPose();
    }

    /**
     * 列标题：大字号（×1.6）+ 深色圆角背景 + 类型色边框，与按钮区间距 36px。
     * 背景矩形：宽 140（与按钮同宽），高 30；文字放大居中。
     */
    private void renderColumnTitle(GuiGraphics guiGraphics, String title, int centerX, int color) {
        int left = centerX - 70;
        int top = COLUMN_TITLE_TOP;
        int right = centerX + 70;
        int bottom = top + 30;
        // 类型色边框（外扩 1px）+ 深色背景（圆角 8）
        // ⚠️ 必须用普通 GUI 渲染（RenderType.gui，与按钮同层、带深度测试）——
        //    不能用 guiOverlay（无深度测试+最后批次），否则列标题叠加到最上层、盖住所有 UI
        var gui = net.minecraft.client.renderer.RenderType.gui();
        fillRoundedRect(guiGraphics, left - 1, top - 1, right + 1, bottom + 1, 8, color, gui);
        fillRoundedRect(guiGraphics, left, top, right, bottom, 8, 0xCC101018, gui);
        // 大字号文字（1.6×，居中）
        float s = 1.6f;
        float tx = centerX;
        float ty = top + (bottom - top) / 2.0f - font.lineHeight * s / 2.0f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(tx, ty, 0);
        guiGraphics.pose().scale(s, s, 1);
        guiGraphics.drawCenteredString(font, title, 0, 0, color);
        guiGraphics.pose().popPose();
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
            boolean togglable = Skills.isTogglable(button.skillId());
            int kx = togglable ? button.x() + BUTTON_WIDTH + KEY_BOX_GAP
                    : button.x() + BUTTON_WIDTH + KEY_BOX_GAP;
            int ky = button.y();
            double lx = toPanelX(mouseX);
            double ly = toPanelY(mouseY);
            // 第一框悬停提示（仅可开关技能；显示开关状态 + 清空快捷键说明）
            if (togglable && lx >= kx && lx <= kx + KEY_BOX_WIDTH && ly >= ky && ly <= ky + BUTTON_HEIGHT) {
                boolean enabled = toggles.getOrDefault(button.skillId(), Boolean.TRUE);
                var bound = org.zifeng.skilltree.client.SkillKeyBinds.getKey(button.skillId());
                String boundText = bound != null ? "已绑定: " + bound.getDisplayName().getString() : "未绑定";
                guiGraphics.renderTooltip(font, java.util.List.of(
                                Component.literal("⚡ " + Skills.getDisplayName(button.skillId()) + " · 开关键"),
                                Component.literal("当前状态：" + (enabled ? "开启中" : "已关闭")),
                                Component.literal(boundText),
                                Component.literal("点击进入绑定，按任意键设置"),
                                Component.literal("清空：绑定状态下按 退格键(Backspace) 或 删除键(Delete)")),
                        java.util.Optional.empty(), mouseX, mouseY + 12);
                return;
            }
            // 第二列按键框悬停提示（2026-08-13：每个技能独立描述 + 清空快捷键说明）
            if (isLevelBindable(button.skillId())) {
                int k2x = kx + KEY_BOX_WIDTH + KEY_BOX_GAP;
                if (lx >= k2x && lx <= k2x + KEY2_BOX_WIDTH && ly >= ky && ly <= ky + BUTTON_HEIGHT) {
                    String skillId = button.skillId();
                    var bound = org.zifeng.skilltree.client.SkillKeyBinds.getLevelKey(skillId);
                    String boundText = bound != null ? "已绑定: " + bound.getDisplayName().getString() : "未绑定";
                    // 按技能独立描述（每个技能文案不同）
                    java.util.List<Component> lines;
                    if (Skills.AURA_DAMAGE.equals(skillId)) {
                        String modeText = modeTextOf(skillId);
                        lines = java.util.List.of(
                                Component.literal("🎯 杀戮光环·伤害 · 目标循环键"),
                                Component.literal("决定光环攻击打到谁：当前【" + modeText + "】"),
                                Component.literal("敌对：只打怪物 ／ 友好：只打动物 ／ 所有：全打"),
                                Component.literal(boundText),
                                Component.literal("点击进入绑定，按任意键设置"),
                                Component.literal("清空：退格键 或 删除键"));
                    } else if (Skills.AURA_SPEED.equals(skillId)) {
                        String modeText = modeTextOf(skillId);
                        lines = java.util.List.of(
                                Component.literal("🎯 杀戮光环·速度 · 目标循环键"),
                                Component.literal("本光环独立目标：当前【" + modeText + "】"),
                                Component.literal("只影响光环攻击频率，与伤害光环互不干扰"),
                                Component.literal(boundText),
                                Component.literal("点击进入绑定，按任意键设置"),
                                Component.literal("清空：退格键 或 删除键"));
                    } else if (Skills.AURA_HEAL.equals(skillId)) {
                        String modeText = modeTextOf(skillId);
                        lines = java.util.List.of(
                                Component.literal("🎯 治愈光环 · 目标循环键"),
                                Component.literal("决定光环治疗谁：当前【" + modeText + "】"),
                                Component.literal("友好：只奶动物 ／ 所有：连敌对也奶（离谱但可行）"),
                                Component.literal(boundText),
                                Component.literal("点击进入绑定，按任意键设置"),
                                Component.literal("清空：退格键 或 删除键"));
                    } else {
                        // 可调等级技能：显示当前生效/已学 + 操作说明（Shift=10级 Ctrl+Shift=100级 Alt反向）
                        int learned = learnedSkills.getOrDefault(skillId, 0);
                        int active = activeLevels.getOrDefault(skillId, learned);
                        lines = java.util.List.of(
                                Component.literal("📶 " + Skills.getDisplayName(skillId) + " · 等级循环键"),
                                Component.literal("当前生效：" + active + " / 已学：" + learned),
                                Component.literal(boundText),
                                Component.literal("按键：+1级  Shift：+10级  Ctrl+Shift：+100级"),
                                Component.literal("按住Alt：反向调整 ／ 到上限后回 0 循环"),
                                Component.literal("点击进入绑定，按任意键设置"),
                                Component.literal("清空：退格键 或 删除键"));
                    }
                    guiGraphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY + 12);
                    return;
                }
            }
        }
        for (SkillButton button : buttons) {
            if (button.isHovered(mouseX, mouseY, this)) {
                renderSkillTooltip(guiGraphics, button, mouseX, mouseY);
                break;
            }
        }
    }

    /**
     * 第一图层（最顶）：顶部标题区 + 属性面板 + 开关面板 + 面板开关按钮。
     * 全部用 guiOverlay 渲染（无深度测试、最后提交）→ 永远覆盖第二/第三图层。
     */
    private void renderLayer1HeaderAndPanels(GuiGraphics guiGraphics) {
        renderAttributesPanel(guiGraphics);
        renderTogglePanel(guiGraphics);
        // 面板开关按钮（右下角，保证在最上层不被提示条盖住）
        if (Config.PANEL_VISIBLE.get()) {
            renderPanelToggleButton(guiGraphics);
        } else {
            renderPanelRestoreButton(guiGraphics);
        }
        renderHeaderInfo(guiGraphics);
    }

    /** 顶部信息区（第一图层最顶）：技能树标题 + 技能点/状态行 + 快捷键提示行 + 未绑定快捷键警告 */
    private void renderHeaderInfo(GuiGraphics guiGraphics) {
        // 字体缩小 0.2（×0.8），行间隔 15 像素，首行离上边框 10 像素
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(width / 2.0, 10.0, 0);
        guiGraphics.pose().scale(0.8f, 0.8f, 1.0f);
        String title = "子枫 · 技能树";
        // 顶部状态行：显示伤害光环的目标模式（各光环独立后以伤害光环为代表）
        String modeText = modeTextOf(Skills.AURA_DAMAGE);
        // 第一行：状态信息（黄字，简短）
        String statusLine = "技能点：" + String.format("%.1f", Math.max(0, skillPoints))
                + "   ·   光环:" + (auraEnabled ? "开" : "关")
                + "   ·   目标:" + modeText;
        // 第二行：快捷键提示（灰字，紧凑排列，不再一行塞满）
        String hintLine = "左键加点  Shift+左键×10  Ctrl+Shift×100  右键开关  滚轮调级  Ctrl+R重置";
        // 光环快捷键未绑定提示（默认空键，引导玩家自行设置）
        boolean showWarn = org.zifeng.skilltree.client.ModKeyBindingEvents.hasUnboundAuraKeys();
        String warnLine = showWarn ? "⚠ 光环技能默认无快捷键：点击技能右下角 🔑 可设置开关快捷键" : null;
        // 以文字包围盒为中心外扩 10px 的圆角背景 + 边框（不超出文字范围）
        int maxWidth = Math.max(font.width(title), Math.max(font.width(statusLine), font.width(hintLine)));
        if (warnLine != null) {
            maxWidth = Math.max(maxWidth, font.width(warnLine));
        }
        int bgLeft = -maxWidth / 2 - 10;
        int bgRight = maxWidth / 2 + 10;
        int bgTop = -10;
        int bgBottom = (warnLine != null ? 45 : 30) + 10;
        // 边框（外扩 1px）+ 背景（圆角半径 10）
        fillRoundedRect(guiGraphics, bgLeft - 1, bgTop - 1, bgRight + 1, bgBottom + 1, 10, 0xFF87CEEB);
        fillRoundedRect(guiGraphics, bgLeft, bgTop, bgRight, bgBottom, 10, 0xCC000000);
        guiGraphics.drawCenteredString(font, title, 0, 0, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, statusLine, 0, 15, 0xFFFFD700);
        guiGraphics.drawCenteredString(font, hintLine, 0, 30, 0xFFAAAAAA);
        if (warnLine != null) {
            guiGraphics.drawCenteredString(font, warnLine, 0, 45, 0xFFFF5555);
        }
        guiGraphics.pose().popPose();
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
        boolean enabled = toggles.getOrDefault(skillId, Boolean.TRUE);
        java.util.List<TooltipLine> lines = new java.util.ArrayList<>();

        int titleColor = switch (type) {
            case BASE -> 0xFF87CEEB;
            case AMPLIFY -> 0xFFFFAA55;
            case ULTIMATE -> 0xFFFF5555;
            case AURA -> 0xFFAA55FF;
            case MAGIC -> 0xFF55FFAA;
            case MACHINE -> 0xFFD7D7D7;
        };
        String typeTag = switch (type) {
            case BASE -> "[基础]";
            case AMPLIFY -> "[增幅]";
            case ULTIMATE -> "[终极]";
            case AURA -> "[光环]";
            case MAGIC -> "[魔法]";
            case MACHINE -> "[共鸣]";
        };
        // 1. 标题行（大字号 + 类型色）
        lines.add(new TooltipLine(typeTag + " " + Skills.getDisplayName(skillId), titleColor, 1.15F));
        lines.add(new TooltipLine("———————————————————", 0xFF555555, 1.0F));

        // 2. 描述正文（白灰，正常字号）
        for (String line : Skills.getDescription(skillId).split("\\n")) {
            lines.add(new TooltipLine(line, 0xFFDDDDDD, 1.0F));
        }

        // 3. 消耗信息（金色，小一号）
        String costText = switch (type) {
            case BASE -> "[每级消耗 " + fmtCost(Skills.basePointCost()) + " 点]";
            case AMPLIFY -> "[每级消耗 " + fmtCost(Skills.amplifyPointCost()) + " 点]";
            case MAGIC -> "[每级消耗 " + fmtCost(Skills.getMagicCostAtLevel(skillId, 0)) + " 点，线性增长]";
            case AURA -> "[下次消耗 " + (long) recordNextCost(skillId) + " 点]";
            case ULTIMATE -> "[消耗 " + fmtCost(recordNextCost(skillId)) + " 点]";
            case MACHINE -> "[消耗 " + fmtCost(recordNextCost(skillId)) + " 点，一次性]";
        };
        lines.add(new TooltipLine(" ", 0xFF000000, 0.6F));
        lines.add(new TooltipLine(costText, 0xFFFFD700, 0.9F));

        // 4. 模组缺失红字（MAGIC 且对应模组未装）
        String missingMod = missingModName(skillId);
        if (missingMod != null) {
            lines.add(new TooltipLine("⚠ 未安装" + missingMod + "，学习无效", 0xFFFF5555, 0.95F));
        }

        // 5. 前置需求（金色标题 + 绿/红状态）
        java.util.List<Map.Entry<String, Integer>> prereqs = Skills.getPrerequisites(skillId);
        if (!prereqs.isEmpty()) {
            lines.add(new TooltipLine(" ", 0xFF000000, 0.6F));
            lines.add(new TooltipLine("—— 前置需求 ——", 0xFFFFD700, 0.9F));
            for (Map.Entry<String, Integer> entry : prereqs) {
                String required = entry.getKey();
                int need = entry.getValue();
                int have = learnedSkills.getOrDefault(required, 0);
                boolean met = have >= need;
                lines.add(new TooltipLine((met ? "✓ " : "✗ ") + Skills.getDisplayName(required) + " " + have + "/" + need,
                        met ? 0xFF55FF55 : 0xFFFF5555, 0.9F));
            }
        }

        // 6. 底部操作/状态提示（小字号灰）
        lines.add(new TooltipLine(" ", 0xFF000000, 0.6F));
        lines.add(new TooltipLine(buildStatusText(skillId, type, points, enabled), 0xFF888888, 0.8F));
        return lines;
    }

    /** 绘制技能悬停提示 */
    private void renderSkillTooltip(GuiGraphics guiGraphics, SkillButton button, int mouseX, int mouseY) {
        renderTooltipLines(guiGraphics, buildTooltipLines(button), mouseX, mouseY);
    }

    /** 底部状态行：已学 / 可学 / 不可学原因 */
    private String buildStatusText(String skillId, Skills.SkillType type, int points, boolean enabled) {
        if (points > 0) {
            String toggle = enabled ? "开启中" : "已关闭";
            return "已学 " + points + "/" + Skills.getMaxPoints(skillId) + " 级 · " + toggle
                    + " · 右键开关 · 滚轮调级";
        }
        if (missingModName(skillId) != null) {
            return "⚠ 未安装对应模组，无法学习";
        }
        if (skillPoints < nextCostLocal(skillId) - 1e-9) {
            return "技能点不足（需 " + fmtCost(nextCostLocal(skillId)) + " 点）";
        }
        if (!canLearn(skillId)) {
            return "前置/上限未满足，无法学习";
        }
        return "左键学习 · Shift+左键 ×10 · Ctrl+Shift+左键 ×100";
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
        // 屏幕边界钳制（留出边框宽度，避免被边框图层盖住）
        if (x + maxWidth + padX * 2 > width - BORDER_THICKNESS) {
            x = mouseX - maxWidth - padX * 2 - 4;
        }
        if (x < BORDER_THICKNESS + 2) {
            x = BORDER_THICKNESS + 2;
        }
        if (y + totalHeight + padY * 2 > height - BORDER_THICKNESS) {
            y = height - totalHeight - padY * 2 - 2;
        }
        if (y < BORDER_THICKNESS + 2) {
            y = BORDER_THICKNESS + 2;
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
        if (isMouseOverPanel(mouseX, mouseY)) {
            return true;
        }
        if (isMouseOverHeader(mouseX, mouseY)) {
            return true;
        }
        // 底部提示条（renderTogglePanel：height-32 ~ height，横跨面板宽度区域）
        if (Config.PANEL_VISIBLE.get()) {
            int tx = width - PANEL_WIDTH - 10;
            if (mouseX >= tx - 2 && mouseX <= tx + PANEL_WIDTH + 2 && mouseY >= height - 32 && mouseY <= height) {
                return true;
            }
        }
        // 右下角面板开关按钮（14×14）
        if (mouseX >= panelToggleX() && mouseX <= panelToggleX() + 14
                && mouseY >= panelToggleY() && mouseY <= panelToggleY() + 14) {
            return true;
        }
        return false;
    }

    /** 图标被 UI/tooltip 覆盖时跳过的面积比例阈值（图标被遮 ≥15% 才跳过渲染） */
    private static final float ICON_OVERLAP_SKIP_RATIO = 0.15f;

    /**
     * 预计算当前悬停按钮的 tooltip 边界 [x, y, w, h]（屏幕坐标，含钳制）。
     * 在第四图层渲染前调用，供图标跳过判定：被 tooltip 覆盖的图标不渲染（tooltip 背景半透明，否则图标会透出混合）。
     */
    private void updateActiveTooltipBounds(int mouseX, int mouseY) {
        activeTooltipBounds = null;
        if (isOverUI(mouseX, mouseY)) {
            return;
        }
        for (SkillButton button : buttons) {
            if (button.isHovered(mouseX, mouseY, this)) {
                activeTooltipBounds = computeTooltipLayout(buildTooltipLines(button), mouseX, mouseY);
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
        float ox = (float) (width / 2.0 - 60 + panX);
        float oy = (float) (height / 2.0 + 10 + panY);
        // 图标屏幕 AABB（图标 16×16，起点 x+3,y+3）
        float ix1 = ox + (button.x() + 3) * (float) scale;
        float iy1 = oy + (button.y() + 3) * (float) scale;
        float ix2 = ox + (button.x() + 19) * (float) scale;
        float iy2 = oy + (button.y() + 19) * (float) scale;
        // 1. 属性面板（右侧 / 底部布局）
        if (Config.PANEL_VISIBLE.get()) {
            if (Config.PANEL_POSITION.get() == 1) {
                if (overlapRatio(ix1, iy1, ix2, iy2, 6, height - 140, width - 6, height - 8) >= ICON_OVERLAP_SKIP_RATIO) return true;
            } else {
                if (overlapRatio(ix1, iy1, ix2, iy2, width - PANEL_WIDTH - 12, 46, width - 6, height - 30) >= ICON_OVERLAP_SKIP_RATIO) return true;
            }
            // 2. 底部提示条
            if (overlapRatio(ix1, iy1, ix2, iy2, width - PANEL_WIDTH - 12, height - 32, width - 6, height) >= ICON_OVERLAP_SKIP_RATIO) return true;
        }
        // 3. 顶部标题区
        int[] hb = headerBounds();
        if (overlapRatio(ix1, iy1, ix2, iy2, hb[0], hb[1], hb[2], hb[3]) >= ICON_OVERLAP_SKIP_RATIO) return true;
        // 4. 右下角面板开关按钮（14×14）
        if (overlapRatio(ix1, iy1, ix2, iy2, panelToggleX(), panelToggleY(), panelToggleX() + 14, panelToggleY() + 14) >= ICON_OVERLAP_SKIP_RATIO) return true;
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
        boolean hovered = button.isHovered(lastMouseX, lastMouseY, this);
        Skills.SkillType type = Skills.getType(button.skillId());
        boolean canLearn = canLearn(button.skillId());
        boolean learned = learnedSkills.getOrDefault(button.skillId(), 0) > 0;
        boolean enabled = toggles.getOrDefault(button.skillId(), Boolean.TRUE);

        int bg = switch (type) {
            case MAGIC -> hovered ? 0xFF2A8A6A : 0xFF1E6E4E;
            case BASE -> hovered ? 0xFF3A5A8A : 0xFF24476E;
            case AMPLIFY -> hovered ? 0xFF8A5A2A : 0xFF6E4424;
            case ULTIMATE -> hovered ? 0xFF8A2A3A : 0xFF6E242E;
            case AURA -> hovered ? 0xFF5A3A8A : 0xFF3E2470;
            case MACHINE -> hovered ? 0xFF6A6A6A : 0xFF4A4A4A; // 机械共鸣：铁灰（机械主题）
        };
        int borderColor;
        if (!enabled) {
            borderColor = 0xFF444444; // 禁用：暗灰无金色描边
        } else if (learned) {
            borderColor = hovered ? 0xFFFFFF55 : 0xFFFFD700; // 有点数：金色描边（悬停提亮）
        } else if (hovered) {
            borderColor = 0xFF87CEEB;
        } else {
            borderColor = 0xFF3A3A6E; // 未学：暗蓝
        }

        guiGraphics.fill(button.x(), button.y(), button.x() + BUTTON_WIDTH, button.y() + BUTTON_HEIGHT, bg);
        guiGraphics.fill(button.x(), button.y(), button.x() + BUTTON_WIDTH, button.y() + 1, borderColor);
        guiGraphics.fill(button.x(), button.y() + BUTTON_HEIGHT - 1, button.x() + BUTTON_WIDTH, button.y() + BUTTON_HEIGHT, borderColor);
        guiGraphics.fill(button.x(), button.y(), button.x() + 1, button.y() + BUTTON_HEIGHT, borderColor);
        guiGraphics.fill(button.x() + BUTTON_WIDTH - 1, button.y(), button.x() + BUTTON_WIDTH, button.y() + BUTTON_HEIGHT, borderColor);

        // 图标是否会被第一图层 UI 或当前 tooltip 覆盖（面积比例 ≥ 阈值）→ 跳过 renderItem（半透明背景透出会混合）
        boolean iconOverlapped = isIconUnderUI(button);
        // 技能图标（左侧 16×16，用原版物品图标；跟随技能树整体缩放）
        if (!iconOverlapped) {
            guiGraphics.renderItem(new net.minecraft.world.item.ItemStack(Skills.getIcon(button.skillId())), button.x() + 3, button.y() + 3);
            // 机械共鸣：图标外圈【钢灰机械边框】+ 右下角【螺丝角标】（与原技能区分，机械主题辨识度高）
            // 图标绘制区域 = (x+3, y+3) ~ (x+19, y+19)，边框包在四周 1px
            if (type == Skills.SkillType.MACHINE) {
                int ix = button.x() + 2, iy = button.y() + 2, iw = 18, ih = 18;
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
                int sx = button.x() + 15, sy = button.y() + 15;
                guiGraphics.fill(sx, sy, sx + 4, sy + 4, 0xFF7A848E);   // 螺丝头
                guiGraphics.fill(sx + 1, sy + 1, sx + 3, sy + 3, 0xFFAEB6BE); // 内圈
                guiGraphics.fill(sx + 1, sy + 1, sx + 2, sy + 2, 0xFFF0F3F5); // 高光十字
                guiGraphics.fill(sx + 2, sy + 2, sx + 3, sy + 3, 0xFFF0F3F5);
            }
            // 虚空系技能（虚空之矛/虚空之躯）：图标外圈金色边框（伤害吸收金边主题）
            // 图标绘制区域 = (x+3, y+3) ~ (x+19, y+19)，金边包在图标四周 1px
            if (Skills.AURA_VOID.equals(button.skillId()) || Skills.ULT_VOID_BODY.equals(button.skillId())) {
                int ix = button.x() + 2, iy = button.y() + 2, iw = 18, ih = 18; // 图标外扩 1px 边界
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
        // 图标左移提示：名称从图标右侧开始（x+22）
        // 名称 + 开关标记
        String name = (enabled ? "" : "⛔ ") + Skills.getDisplayName(button.skillId());
        guiGraphics.drawString(font, name, button.x() + 22, button.y() + 3, enabled ? 0xFFFFFFFF : 0xFF888888);
        // 数据
        int points = learnedSkills.getOrDefault(button.skillId(), 0);
        double nextCost = recordNextCost(button.skillId());

        // 第2行：等级/上限显示（所有技能统一，与杀戮光环风格一致）
        String effectText = points + "级/" + Skills.getMaxPoints(button.skillId());
        while (!effectText.isEmpty() && font.width(effectText) > BUTTON_WIDTH - 30) {
            effectText = effectText.substring(0, effectText.length() - 1);
        }
        guiGraphics.drawString(font, effectText, button.x() + 22, button.y() + 17, 0xFF55FF55);

        // 第3行：消耗总数量（已消耗 + 下一级）
        String costText;
        if (type == Skills.SkillType.AURA) {
            if (Skills.AURA_MAGNET.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需" + (long) (double) org.zifeng.skilltree.Config.MAGNET_COST.get() + "点";
            } else if (Skills.AURA_LOCK.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需" + (long) (double) org.zifeng.skilltree.Config.LOCK_COST.get() + "点";
            } else if (Skills.AURA_TIME.equals(button.skillId()) || Skills.AURA_WEATHER.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需" + Skills.minorUltCost() + "点";
            } else {
                long total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getAuraCost(button.skillId(), i);
                }
                // 有等级的光环（伤害/速度/治愈）：显示生效:X/Y（与基础技能一致，滚轮可调）
                if (Skills.getAuraMaxPoints(button.skillId()) > 1) {
                    int active = activeLevels.getOrDefault(button.skillId(), points);
                    costText = "生效:" + active + "/" + points + " 下1级:" + (long) nextCost + "点";
                } else {
                    costText = "已耗" + total + "点 下1级:" + (long) nextCost + "点";
                }
            }
        } else if (type == Skills.SkillType.BASE || type == Skills.SkillType.AMPLIFY || type == Skills.SkillType.MAGIC) {
            // 生效等级（滚轮可调，实时显示）+ 下一级真实消耗（线性增长：基础 +1/级、增幅/魔法 +2/级）
            int active = activeLevels.getOrDefault(button.skillId(), points);
            double unitCost = switch (type) {
                case BASE -> Skills.getBaseCostAtLevel(points);
                case AMPLIFY -> Skills.getAmplifyCostAtLevel(points);
                case MAGIC -> Skills.getMagicCostAtLevel(button.skillId(), points);
                default -> 0;
            };
            costText = "生效:" + active + "/" + points + " 下1级:" + fmtCost(unitCost) + "点";
        } else if (type == Skills.SkillType.ULTIMATE) {
            // 终极节点：单次解锁消耗（浴血/金身/涅槃=500，死神=1000，全能精通=5000，宇宙的青睐=1000，夜视/饱食=100）
            // 多级终极（节点类）：村庄英雄10点/级、接触距离1点/级、发光1点，显示已耗+下一级
            if (Skills.ULT_FAVOR.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需" + Skills.ultFavorCost() + "点";
            } else if (Skills.NIGHT_VISION.equals(button.skillId()) || Skills.SATURATION.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需" + Skills.minorUltCost() + "点";
            } else if (Skills.getUltimateMaxPoints(button.skillId()) > 1) {
                // 多级终极（节点类，阶梯递增消耗，可滚轮调生效等级）：生效:X/Y + 下一级（与基础技能一致）
                double unitCost = Skills.getUltimateLevelCost(button.skillId(), points);
                int active = activeLevels.getOrDefault(button.skillId(), points);
                costText = points > 0
                        ? "生效:" + active + "/" + points + " 下1级:" + fmtCost(unitCost) + "点"
                        : "需" + fmtCost(unitCost) + "点";
            } else {
                costText = points > 0 ? "已解锁" : "需" + Skills.ultimateCost(button.skillId()) + "点";
            }
        } else if (type == Skills.SkillType.MACHINE) {
            // 机械共鸣：单级解锁（机械之星 1000 / 其余共鸣 5000）
            costText = points > 0 ? "已解锁" : "需" + (long) Skills.getMachineCost(button.skillId()) + "点";
        } else {
            costText = points + "级";
        }
        while (!costText.isEmpty() && font.width(costText) > BUTTON_WIDTH - 30) {
            costText = costText.substring(0, costText.length() - 1);
        }
        guiGraphics.drawString(font, costText, button.x() + 22, button.y() + 31, canLearn ? 0xFFFFAA55 : 0xFFAAAAAA);
        // 禁用（开关关闭）时给图标加半透明暗色遮罩
        if (!enabled) {
            guiGraphics.fill(button.x() + 3, button.y() + 3, button.x() + 19, button.y() + 19, 0x88000000);
        }
        // ============ 内联按键框（2026-08-13 需求：按钮右侧直接显示/设置该技能开关快捷键，仿原版按键设置） ============
        // ⚠️ 无需开关的技能（时之环/晴空环常驻被动）不显示开关键；第二框 x 位置相应左移到按钮旁
        boolean togglable = Skills.isTogglable(button.skillId());
        int keyShift = 0;
        int kx = togglable ? button.x() + BUTTON_WIDTH + KEY_BOX_GAP + keyShift
                : button.x() + BUTTON_WIDTH + KEY_BOX_GAP; // 无开关键时第二框从按钮右缘起
        int ky = button.y();
        int kw = KEY_BOX_WIDTH, kh = BUTTON_HEIGHT;
        // ⚠️ 屏幕坐标 → 技能树局部坐标再比较（lastMouseX 是屏幕坐标，kx/ky 是局部坐标）
        boolean keyHovered = lastMouseX >= 0 && lastMouseY >= 0
                && toPanelX(lastMouseX) >= kx && toPanelX(lastMouseX) <= kx + kw
                && toPanelY(lastMouseY) >= ky && toPanelY(lastMouseY) <= ky + kh;
        boolean listening = button.skillId().equals(keyBindSkillId) && keyBindListening;
        var key = org.zifeng.skilltree.client.SkillKeyBinds.getKey(button.skillId());
        // 第一框（开关键）：仅可开关技能渲染；不可开关技能跳过（第二框左移到按钮旁）
        if (togglable) {
            // 背景（监听=高亮橙，有绑定=暗金，悬停提亮，默认=深灰）
            int kbg = listening ? 0xFF7A4A00
                    : keyHovered ? (key != null ? 0xFF6E5A00 : 0xFF3A3A4A)
                    : key != null ? 0xFF4A4200 : 0xFF2A2A3A;
            guiGraphics.fill(kx, ky, kx + kw, ky + kh, kbg);
            // 边框（监听=橙，有绑定=金，默认=暗蓝灰）
            int kbord = listening ? 0xFFFFAA55 : (key != null ? 0xFFFFD700 : 0xFF555566);
            guiGraphics.fill(kx, ky, kx + kw, ky + 1, kbord);
            guiGraphics.fill(kx, ky + kh - 1, kx + kw, ky + kh, kbord);
            guiGraphics.fill(kx, ky, kx + 1, ky + kh, kbord);
            guiGraphics.fill(kx + kw - 1, ky, kx + kw, ky + kh, kbord);
            // 按键文字（监听态显示原版 "> 键名 <" 样式；否则显示绑定键名/未绑定）
            String keyText;
            int keyColor;
            if (listening) {
                keyText = "> " + (key != null ? key.getDisplayName().getString() : "?") + " <";
                keyColor = 0xFFFFFF55;
            } else if (key != null) {
                keyText = key.getDisplayName().getString();
                keyColor = 0xFFFFFFFF;
            } else {
                keyText = "未绑定";
                keyColor = 0xFF888888;
            }
            // 过长截断
            while (!keyText.isEmpty() && font.width(keyText) > kw - 6) {
                keyText = keyText.substring(0, keyText.length() - 1);
            }
            guiGraphics.drawCenteredString(font, keyText, kx + kw / 2, ky + (kh - font.lineHeight) / 2, keyColor);
        }

        // ============ 第二列按键框（2026-08-13 需求：光环=目标循环键，可调等级技能=等级循环键） ============
        if (isLevelBindable(button.skillId())) {
            // 位置：紧跟第一框右侧（无开关键时第一框 x 即按钮右缘 → 自动对齐）
            int k2x = kx + kw + KEY_BOX_GAP;
            int k2w = KEY2_BOX_WIDTH, k2h = BUTTON_HEIGHT;
            boolean k2Hovered = lastMouseX >= 0 && lastMouseY >= 0
                    && toPanelX(lastMouseX) >= k2x && toPanelX(lastMouseX) <= k2x + k2w
                    && toPanelY(lastMouseY) >= ky && toPanelY(lastMouseY) <= ky + k2h;
            boolean k2Listening = button.skillId().equals(levelKeyBindSkillId) && levelKeyBindListening;
            var k2key = org.zifeng.skilltree.client.SkillKeyBinds.getLevelKey(button.skillId());
            // 背景/边框（与第一框同风格；光环用紫调区分目标模式）
            boolean isAura = Skills.AURA_SKILLS.contains(button.skillId());
            int k2bg = k2Listening ? (isAura ? 0xFF5A2A6A : 0xFF7A4A00)
                    : k2Hovered ? (k2key != null ? (isAura ? 0xFF5A3A6A : 0xFF6E5A00) : 0xFF3A3A4A)
                    : k2key != null ? (isAura ? 0xFF3A2A4A : 0xFF4A4200) : 0xFF2A2A3A;
            guiGraphics.fill(k2x, ky, k2x + k2w, ky + k2h, k2bg);
            int k2bord = k2Listening ? (isAura ? 0xFFCC88FF : 0xFFFFAA55)
                    : (k2key != null ? (isAura ? 0xFFBB77FF : 0xFFFFD700) : 0xFF555566);
            guiGraphics.fill(k2x, ky, k2x + k2w, ky + 1, k2bord);
            guiGraphics.fill(k2x, ky + k2h - 1, k2x + k2w, ky + k2h, k2bord);
            guiGraphics.fill(k2x, ky, k2x + 1, ky + k2h, k2bord);
            guiGraphics.fill(k2x + k2w - 1, ky, k2x + k2w, ky + k2h, k2bord);
            // 文字：监听态显示 "> 键名 <"；已绑定显示键名；未绑定显示"未绑定"
            String k2Text;
            int k2Color;
            if (k2Listening) {
                k2Text = "> " + (k2key != null ? k2key.getDisplayName().getString() : "?") + " <";
                k2Color = 0xFFFFFF55;
            } else if (k2key != null) {
                k2Text = k2key.getDisplayName().getString();
                k2Color = 0xFFFFFFFF;
            } else {
                k2Text = "未绑定";
                k2Color = 0xFF888888;
            }
            while (font.width(k2Text) > k2w - 6) {
                k2Text = k2Text.substring(0, k2Text.length() - 1);
            }
            guiGraphics.drawCenteredString(font, k2Text, k2x + k2w / 2, ky + (k2h - font.lineHeight) / 2, k2Color);
        }
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
        Skills.SkillType type = Skills.getType(skillId);
        int current = learnedSkills.getOrDefault(skillId, 0);
        if (type == Skills.SkillType.BASE && current >= Skills.BASE_MAX_POINTS) return false;
        if (type == Skills.SkillType.AMPLIFY && current >= Skills.AMPLIFY_MAX_POINTS) return false;
        if (type == Skills.SkillType.ULTIMATE && current >= Skills.getUltimateMaxPoints(skillId)) return false;
        if (type == Skills.SkillType.AURA && current >= Skills.getAuraMaxPoints(skillId)) return false;
        if (type == Skills.SkillType.MAGIC) {
            if (current >= Skills.getMagicMaxPoints(skillId)) return false;
            // 其余模组兼容技能：对应模组未安装 → 不可学（红字提示见悬停说明）
            if (missingModName(skillId) != null) return false;
        }
        if (type == Skills.SkillType.MACHINE && current >= Skills.getMachineMaxPoints(skillId)) return false;
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
            return org.zifeng.skilltree.compat.ArsNouveauCompat.isLoaded() ? null : "新生魔艺（Ars Nouveau）";
        }
        if (Skills.IRON_MANA_AMP.equals(skillId) || Skills.IRON_MANA_REGEN.equals(skillId)
                || Skills.IRON_CAST_TIME.equals(skillId) || Skills.IRON_COOLDOWN.equals(skillId)
                || Skills.IRON_FIRE.equals(skillId) || Skills.IRON_ICE.equals(skillId) || Skills.IRON_LIGHTNING.equals(skillId)
                || Skills.IRON_HOLY.equals(skillId) || Skills.IRON_ENDER.equals(skillId)
                || Skills.IRON_BLOOD.equals(skillId) || Skills.IRON_EVOCATION.equals(skillId)
                || Skills.IRON_NATURE.equals(skillId) || Skills.IRON_ELDRITCH.equals(skillId)) {
            return org.zifeng.skilltree.compat.IronSpellsCompat.isLoaded() ? null : "铁魔法（Iron's Spells）";
        }
        return null;
    }

    // ============ 属性面板 ============

    /** 面板可视行数（右侧布局，减去标题/技能点行） */
    private int panelVisibleRows() {
        // 滚动区域：标题(18px) + 可见行 + 底部提示(20px)；每行 12px
        return (height - 50 - 30 - 18 - 20) / 12;
    }

    /** 面板开关按钮（右侧标题栏右上角的小方块，12×12） */
    /** 面板开关按钮位置（右下角） */
    private int panelToggleX() {
        return width - 26;
    }

    private int panelToggleY() {
        return height - 26;
    }

    /** 鼠标是否在属性面板区域内（tooltip 穿透检查用；面板隐藏时该区域不视为 UI → 恢复为技能树区域） */
    private boolean isMouseOverPanel(double mouseX, double mouseY) {
        if (!Config.PANEL_VISIBLE.get()) {
            return false;
        }
        if (Config.PANEL_POSITION.get() == 1) {
            // 底部布局
            return mouseY >= height - 140 && mouseY <= height - 8 && mouseX >= 6 && mouseX <= width - 6;
        }
        // 右侧布局
        return mouseX >= width - PANEL_WIDTH - 12 && mouseX <= width - 6 && mouseY >= 46 && mouseY <= height - 30;
    }

    /**
     * 鼠标是否在顶部信息区背景内（tooltip 穿透检查用）。
     * 与 render 中绘制背景时相同的包围盒计算：以文字最大宽度为中心外扩 10px，×0.8 缩放系数。
     */
    private boolean isMouseOverHeader(double mouseX, double mouseY) {
        int[] b = headerBounds();
        return mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3];
    }

    /** 顶部信息区包围盒 [left, top, right, bottom]（屏幕坐标，与 renderHeaderInfo 绘制一致） */
    private int[] headerBounds() {
        String title = "子枫 · 技能树";
        String modeText = modeTextOf(Skills.AURA_DAMAGE);
        String statusLine = "技能点：" + String.format("%.1f", Math.max(0, skillPoints))
                + "   ·   光环:" + (auraEnabled ? "开" : "关")
                + "   ·   目标:" + modeText;
        String hintLine = "左键加点  Shift+左键×10  Ctrl+Shift×100  右键开关  滚轮调级  Ctrl+R重置";
        int maxWidth = Math.max(font.width(title), Math.max(font.width(statusLine), font.width(hintLine)));
        if (org.zifeng.skilltree.client.ModKeyBindingEvents.hasUnboundAuraKeys()) {
            maxWidth = Math.max(maxWidth, font.width("⚠ 光环技能默认无快捷键：点击技能右下角 🔑 可设置开关快捷键"));
        }
        // 渲染时：translate(width/2, 10) + scale(0.8) → 屏幕坐标换算
        double halfW = (maxWidth / 2.0 + 10) * 0.8;
        int top = (int) Math.floor(10 - 10 * 0.8);
        int bottom = (int) Math.ceil(10 + ((org.zifeng.skilltree.client.ModKeyBindingEvents.hasUnboundAuraKeys() ? 45 : 30) + 10) * 0.8);
        return new int[]{width / 2 - (int) Math.ceil(halfW), top, width / 2 + (int) Math.ceil(halfW), bottom};
    }

    /** 属性行收集（共用逻辑，右侧/底部布局都展示同一份数据） */
    private java.util.List<String[]> collectRows() {
        var player = minecraft != null ? minecraft.player : null;
        if (player == null) return java.util.List.of();
        // 本地技能记录（含生效等级），属性值全部本地计算 → 加点立即实时刷新，不依赖服务端属性同步
        org.zifeng.skilltree.data.PlayerSkillRecord rec = learnedAsRecord();
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        // 分隔标题行（灰色小字，按功能分组）
        rows.add(new String[]{"—— 战斗 ——", "", "#777777"});
        addRow(rows, "攻伤", SkillEffects.getComputedValue(player, Attributes.ATTACK_DAMAGE, rec), "%.1f");
        addRow(rows, "攻速", SkillEffects.getComputedValue(player, Attributes.ATTACK_SPEED, rec), "%.2f");
        addRow(rows, "击退", SkillEffects.getComputedValue(player, Attributes.ATTACK_KNOCKBACK, rec), "%.1f");
        addRow(rows, "暴击率", SkillEffects.getCritChance(rec) * 100, "%.0f%%");
        addRow(rows, "暴击伤害", SkillEffects.getCritMultiplier(rec), "%.1f倍");
        addRow(rows, "破甲增伤", SkillEffects.getArmorPenPercent(rec) * 100, "%.0f%%");
        addRow(rows, "吸血", SkillEffects.getLifestealRate(rec) * 100, "%.0f%%");
        addRow(rows, "荆棘反伤", SkillEffects.getThornsDamage(rec), "%.1f");

        rows.add(new String[]{"—— 防御 ——", "", "#777777"});
        addRow(rows, "生命", SkillEffects.getComputedValue(player, Attributes.MAX_HEALTH, rec), "%.0f");
        addRow(rows, "护甲", SkillEffects.getComputedValue(player, Attributes.ARMOR, rec), "%.1f");
        addRow(rows, "韧性", SkillEffects.getComputedValue(player, Attributes.ARMOR_TOUGHNESS, rec), "%.1f");
        // 物理减伤（自定义属性）：护甲减伤 80% 封顶后继续叠的独立减伤层
        addRow(rows, "物理减伤", SkillEffects.getComputedValue(player, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION, rec) * 100, "%.0f%%");
        // 全能精通：全伤害减免（对所有伤害类型生效，含真伤/混沌/指令）
        boolean masterOn = rec.getLearnedPoints(Skills.ULT_MASTER) > 0 && rec.isEnabled(Skills.ULT_MASTER);
        if (masterOn) {
            addRow(rows, "全伤减免", org.zifeng.skilltree.Config.MASTER_DAMAGE_REDUCTION.get() * 100, "%.0f%%");
        }
        addRow(rows, "击退抗性", SkillEffects.getComputedValue(player, Attributes.KNOCKBACK_RESISTANCE, rec), "%.1f");
        // 耐久减免：未满显示百分比，封顶（100%）显示"工具不毁"
        double durReduction = SkillEffects.getToolDurabilityReduction(rec);
        if (durReduction >= 1.0) {
            rows.add(new String[]{"耐久减免", "工具不毁", "#FFFFD700"});
        } else if (durReduction > 0) {
            addRow(rows, "耐久减免", durReduction * 100, "%.0f%%");
        }

        rows.add(new String[]{"—— 移动 ——", "", "#777777"});
        // 速度显示为每秒方块数：移速 0.1→4.317方/秒，飞行 0.05→10.8方/秒，游泳→3.35方/秒
        addRow(rows, "移速", SkillEffects.getComputedValue(player, Attributes.MOVEMENT_SPEED, rec) * 43.17, "%.2f方/秒");
        // 飞速：实际飞行速度 = abilities.flyingSpeed（每 tick 由 FLYING_SPEED 属性÷8 同步）；0.05 → 10.8 方/秒
        addRow(rows, "飞速", player.getAbilities().getFlyingSpeed() * 216, "%.2f方/秒");
        // 游泳：SWIM_SPEED 默认 1.0 → 原版游泳 ≈ 3.35 方/秒
        double swim = player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED) != null
                ? SkillEffects.getComputedValue(player, net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED, rec) * 3.35 : 0;
        addRow(rows, "游泳", swim, "%.2f方/秒");
        // 跳跃高度（格）= JUMP_STRENGTH² × 6.25（无药水时）
        double jump = SkillEffects.getComputedValue(player, Attributes.JUMP_STRENGTH, rec);
        addRow(rows, "跳高", jump * jump * 6.25, "%.2f格");

        rows.add(new String[]{"—— 生产 ——", "", "#777777"});
        // 挖速用原版 Attributes.MINING_EFFICIENCY（NeoForge 合入的加数属性，直接反映实际挖掘加速）
        addRow(rows, "挖速", SkillEffects.getComputedValue(player, Attributes.MINING_EFFICIENCY, rec), "%.1f");
        addRow(rows, "幸运", SkillEffects.getComputedValue(player, Attributes.LUCK, rec), "%.1f");
        addRow(rows, "回血/秒", SkillEffects.getRegenPerSecond(rec), "%.1f");
        addRow(rows, "生物掉落", SkillEffects.getMobDropMultiplier(rec), "%.2f倍");
        addRow(rows, "方块掉落", SkillEffects.getBlockDropMultiplier(rec), "%.2f倍");
        addRow(rows, "经验倍率", SkillEffects.getExperienceMultiplier(rec), "%.2f倍");
        // 掉落节点类终极：刷怪蛋/头颅概率 + 战利品爆炸倍率（没学不显示）
        int spawnEgg = rec.isEnabled(Skills.MOB_SPAWN_EGG) ? rec.getActiveLevel(Skills.MOB_SPAWN_EGG) : 0;
        if (spawnEgg > 0) addRow(rows, "刷怪蛋掉落", spawnEgg * 10, "%.0f%%");
        int mobHead = rec.isEnabled(Skills.MOB_HEAD) ? rec.getActiveLevel(Skills.MOB_HEAD) : 0;
        if (mobHead > 0) addRow(rows, "头颅掉落", mobHead * 10, "%.0f%%");
        int lootBomb = rec.isEnabled(Skills.LOOT_BOMB) ? rec.getActiveLevel(Skills.LOOT_BOMB) : 0;
        if (lootBomb > 0) addRow(rows, "战利品爆炸", 1.0 + lootBomb, "%.0f倍");

        // ============ 光环类（技能没点不显示） ============
        boolean hasAuraDamage = rec.isEnabled(Skills.AURA_DAMAGE) && rec.getActiveLevel(Skills.AURA_DAMAGE) > 0;
        boolean hasAuraSpeed = rec.isEnabled(Skills.AURA_SPEED) && rec.getActiveLevel(Skills.AURA_SPEED) > 0;
        boolean hasAnyAura = hasAuraDamage || hasAuraSpeed
                || rec.getLearnedPoints(Skills.AURA_VOID) > 0;
        if (hasAnyAura) {
            rows.add(new String[]{"—— 光环 ——", "", "#777777"});
            if (hasAuraDamage) {
                addRow(rows, "光环伤害", SkillEffects.getComputedValue(player, Attributes.ATTACK_DAMAGE, rec), "%.1f");
            }
            if (hasAuraSpeed) {
                // 光环攻击频率 = 基础间隔(10秒) × 0.9^光环速度等级（乘法递减）
                int baseInterval = org.zifeng.skilltree.Config.AURA_BASE_INTERVAL_TICKS.get();
                int speedLevel = rec.getActiveLevel(Skills.AURA_SPEED);
                double reduction = org.zifeng.skilltree.Config.AURA_SPEED_INTERVAL_REDUCTION.get();
                int interval = Math.max(10, (int) Math.round(baseInterval * Math.pow(1 - reduction, speedLevel)));
                addRow(rows, "光环频率/秒", Math.round(20.0 / interval * 10.0) / 10.0, "%.1f");
            }
            // 光环范围半径（学了虚空之矛 → 范围放大到 50 格）
            double auraRadius = rec.getLearnedPoints(Skills.AURA_VOID) > 0 && rec.isEnabled(Skills.AURA_VOID)
                    ? org.zifeng.skilltree.Config.VOID_AURA_RADIUS.get()
                    : org.zifeng.skilltree.Config.AURA_ATTACK_RADIUS.get();
            addRow(rows, "光环半径", auraRadius, "%.0f格");
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
            rows.add(new String[]{"—— 魔法 ——", "", "#777777"});
        }
        // 新生魔艺（装了显示数值；学了但没装显示红字）
        if (org.zifeng.skilltree.compat.ArsNouveauCompat.isLoaded()) {
            double arsAmp = SkillEffects.getManaAmpPercent(rec);
            if (arsAmp > 0) addRow(rows, "新生魔艺魔力增幅", arsAmp * 100, "+%.0f%%");
            double arsRegen = SkillEffects.getArsManaRegenPercent(rec);
            if (arsRegen > 0) addRow(rows, "新生魔艺魔力恢复", arsRegen * 100, "+%.0f%%");
        } else if (hasArs) {
            rows.add(new String[]{"新生魔艺", "未安装，学习无效", "#FF5555"});
        }
        // 铁魔法（装了显示数值；学了但没装显示红字）
        if (org.zifeng.skilltree.compat.IronSpellsCompat.isLoaded()) {
            double ironAmp = SkillEffects.getIronManaAmpPercent(rec);
            if (ironAmp > 0) addRow(rows, "铁魔法魔力增幅", ironAmp * 100, "+%.0f%%");
            double ironRegen = SkillEffects.getIronManaRegenPercent(rec);
            if (ironRegen > 0) addRow(rows, "铁魔法魔力恢复", ironRegen * 100, "+%.0f%%");
            double castTime = SkillEffects.getIronCastTimePercent(rec);
            if (castTime > 0) addRow(rows, "铁魔法吟唱缩减", castTime * 100, "-%.0f%%");
            double cooldown = SkillEffects.getIronCooldownPercent(rec);
            if (cooldown > 0) addRow(rows, "铁魔法冷却缩减", cooldown * 100, "-%.0f%%");
            // 9 流派强度
            double fire = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_FIRE);
            if (fire > 0) addRow(rows, "火焰法术强度", fire * 100, "+%.0f%%");
            double ice = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_ICE);
            if (ice > 0) addRow(rows, "冰霜法术强度", ice * 100, "+%.0f%%");
            double lightning = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_LIGHTNING);
            if (lightning > 0) addRow(rows, "雷电法术强度", lightning * 100, "+%.0f%%");
            double holy = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_HOLY);
            if (holy > 0) addRow(rows, "神圣法术强度", holy * 100, "+%.0f%%");
            double ender = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_ENDER);
            if (ender > 0) addRow(rows, "末影法术强度", ender * 100, "+%.0f%%");
            double blood = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_BLOOD);
            if (blood > 0) addRow(rows, "鲜血法术强度", blood * 100, "+%.0f%%");
            double evocation = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_EVOCATION);
            if (evocation > 0) addRow(rows, "召唤法术强度", evocation * 100, "+%.0f%%");
            double nature = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_NATURE);
            if (nature > 0) addRow(rows, "自然法术强度", nature * 100, "+%.0f%%");
            double eldritch = SkillEffects.getIronSchoolPercent(rec, Skills.IRON_ELDRITCH);
            if (eldritch > 0) addRow(rows, "异界法术强度", eldritch * 100, "+%.0f%%");
        } else if (hasIron) {
            rows.add(new String[]{"铁魔法", "未安装，学习无效", "#FF5555"});
        }
        rows.add(new String[]{"技能点", String.format("%.1f", Math.max(0, skillPoints)), "#FFFFD700"});
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
        guiGraphics.drawString(font, "≡ 属性加成", x + 4, panelTop + 4, 0xFFFFD700);

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
                guiGraphics.drawString(font, row[0], x + 4, line, 0xFFAAAAAA);
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
            guiGraphics.drawString(font, "▼ 滚轮滚动", x + 4, panelBottom - 14, 0xFF888888);
        }
    }

    /** 底部横版面板（3 列分页） */
    private void renderPanelBottom(GuiGraphics guiGraphics, java.util.List<String[]> rows) {
        int h = 130;
        int x = 10;
        int y = height - h - 10;
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, width - 10 + 2, y + h + 2, 0xF0101010);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, width - 10 + 2, y, 0xFF87CEEB);
        guiGraphics.drawString(font, "≡ 属性加成", x + 4, y + 4, 0xFFFFD700);

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
                guiGraphics.drawString(font, row[0], px, py, 0xFFAAAAAA);
                guiGraphics.drawString(font, row[1], px + 62, py, c);
            }
        }
        if (maxScroll > 0) {
            guiGraphics.drawString(font, "◀▶ 滚轮翻页", x + 4, y + h - 12, 0xFF888888);
        }
    }

    /** 面板开关按钮：右下角 ✕/▦ 图标；Shift+点击切换位置 */
    private void renderPanelToggleButton(GuiGraphics guiGraphics) {
        int tx = panelToggleX();
        int ty = panelToggleY();
        boolean hovered = lastMouseX >= tx && lastMouseX <= tx + 14 && lastMouseY >= ty && lastMouseY <= ty + 14;
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), tx, ty, tx + 14, ty + 14, hovered ? 0xFF3A6EA5 : 0xFF24476E);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), tx, ty, tx + 14, ty + 1, 0xFF87CEEB);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), tx, ty + 13, tx + 14, ty + 14, 0xFF87CEEB);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), tx, ty, tx + 1, ty + 14, 0xFF87CEEB);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), tx + 13, ty, tx + 14, ty + 14, 0xFF87CEEB);
        // ✕ 图标（点击隐藏面板）
        int cx = tx + 7, cy = ty + 7;
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx - 3, cy - 3, cx - 2, cy + 3, 0xFFFFFFFF);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx + 2, cy - 3, cx + 3, cy + 3, 0xFFFFFFFF);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx - 3, cy - 3, cx + 3, cy - 2, 0xFFFFFFFF);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx - 3, cy + 2, cx + 3, cy + 3, 0xFFFFFFFF);
        // 悬停提示
        if (hovered) {
            java.util.List<Component> tips = new ArrayList<>();
            tips.add(Component.literal("点击：隐藏/显示属性面板").withColor(0xFFFFFFFF));
            tips.add(Component.literal("Shift+点击：切换 右侧/底部 位置").withColor(0xFFAAAAAA));
            guiGraphics.renderTooltip(font, tips, java.util.Optional.empty(), lastMouseX, lastMouseY - 14);
        }
    }

    /** 面板隐藏时的恢复按钮（右下角） */
    private void renderPanelRestoreButton(GuiGraphics guiGraphics) {
        int tx = panelToggleX();
        int ty = panelToggleY();
        boolean hovered = lastMouseX >= tx && lastMouseX <= tx + 14 && lastMouseY >= ty && lastMouseY <= ty + 14;
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), tx, ty, tx + 14, ty + 14, hovered ? 0xFF3A6EA5 : 0xFF24476E);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), tx, ty, tx + 14, ty + 1, 0xFF87CEEB);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), tx, ty + 13, tx + 14, ty + 14, 0xFF87CEEB);
        // ▦ 图标（恢复显示）
        int cx = tx + 2, cy = ty + 2;
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx, cy, cx + 10, cy + 10, 0xFFFFFFFF);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), cx + 2, cy + 2, cx + 8, cy + 8, 0xFF24476E);
        if (hovered) {
            guiGraphics.renderTooltip(font, java.util.List.of(Component.literal("点击：显示属性面板")),
                    java.util.Optional.empty(), lastMouseX, lastMouseY - 14);
        }
    }

    private void addRow(java.util.List<String[]> rows, String name, double value, String fmt) {
        rows.add(new String[]{name, String.format(fmt, value)});
    }

    /** 解析 #RRGGBB 颜色字符串 → ARGB int（默认白色） */
    private static int parseColor(String hex) {
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

    private org.zifeng.skilltree.data.PlayerSkillRecord learnedAsRecord() {
        org.zifeng.skilltree.data.PlayerSkillRecord record = new org.zifeng.skilltree.data.PlayerSkillRecord(java.util.UUID.randomUUID());
        // 直接设置点数（不能用 learnSkill：AURA 消耗递增会因点数不足提前失败，导致光环永远只显示 1 级）
        learnedSkills.forEach(record::setLearnedPoints);
        toggles.forEach(record::setEnabled);
        activeLevels.forEach(record::setActiveLevel);
        return record;
    }

    /** 开关面板：属性面板隐藏时只保留右下角开关按钮，不显示提示条 */
    private void renderTogglePanel(GuiGraphics guiGraphics) {
        // 属性面板隐藏时只保留开关按钮（renderPanelRestoreButton），其余提示条全部隐藏
        if (!Config.PANEL_VISIBLE.get()) {
            return;
        }
        guiGraphics.flush();
        int x = width - PANEL_WIDTH - 10;
        int y = height - 30;
        // 从下往上画：先画底部说明（guiOverlay 无条件覆盖下层）；文字左对齐，给右下角开关按钮留空间
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, x + PANEL_WIDTH + 2, y + PANEL_WIDTH / 3, 0xCC101010);
        guiGraphics.drawString(font, "右键技能=开关", x + 4, y + 2, 0xFFFFAA55);
    }

    // ============ 交互 ============

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 面板开关按钮（右下角）：点击隐藏/显示；Shift+点击切换位置（优先级最高，避免误触技能）
        if (button == 0 && mouseX >= panelToggleX() && mouseX <= panelToggleX() + 14
                && mouseY >= panelToggleY() && mouseY <= panelToggleY() + 14) {
            if (isShiftHeld()) {
                // Shift+点击：切换位置（右侧/底部）
                Config.PANEL_POSITION.set(1 - Config.PANEL_POSITION.get());
            } else {
                // 点击：隐藏/显示
                Config.PANEL_VISIBLE.set(!Config.PANEL_VISIBLE.get());
            }
            return true;
        }
        // 中键：仅用于拖动技能树（任意位置），按下即接管
        if (button == 2) {
            return true;
        }
        if (button == 0) {
            // 鼠标在第一图层 UI 区域（属性面板/标题/提示条）→ 不透传到下层技能（不透过面板操作）
            if (isOverUI(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            for (SkillButton skillButton : buttons) {
                double lx = toPanelX(mouseX);
                double ly = toPanelY(mouseY);
                boolean togglable = Skills.isTogglable(skillButton.skillId());
                int kx = skillButton.x() + BUTTON_WIDTH + KEY_BOX_GAP;
                int ky = skillButton.y();
                // 第一框（开关键）：仅可开关技能可点击
                if (togglable && lx >= kx && lx <= kx + KEY_BOX_WIDTH && ly >= ky && ly <= ky + BUTTON_HEIGHT) {
                    if (keyBindSkillId != null && keyBindSkillId.equals(skillButton.skillId()) && keyBindListening) {
                        // 再次点击同一按键框 → 退出监听（不改变绑定）
                        keyBindListening = false;
                    } else {
                        // 进入监听态（点击该技能按键框，等待按键输入）
                        keyBindSkillId = skillButton.skillId();
                        keyBindListening = true;
                    }
                    return true;
                }
                // 第二列按键框（2026-08-13：光环=目标循环键，可调等级技能=等级循环键）
                if (isLevelBindable(skillButton.skillId())) {
                    int k2x = kx + KEY_BOX_WIDTH + KEY_BOX_GAP;
                    if (lx >= k2x && lx <= k2x + KEY2_BOX_WIDTH && ly >= ky && ly <= ky + BUTTON_HEIGHT) {
                        if (levelKeyBindSkillId != null && levelKeyBindSkillId.equals(skillButton.skillId()) && levelKeyBindListening) {
                            levelKeyBindListening = false;
                        } else {
                            levelKeyBindSkillId = skillButton.skillId();
                            levelKeyBindListening = true;
                        }
                        return true;
                    }
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
                                PacketDistributor.sendToServer(new LearnSkillC2SPacket(skillButton.skillId(), added));
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
                                PacketDistributor.sendToServer(new LearnSkillC2SPacket(skillButton.skillId(), added));
                            }
                        } else {
                            // 单次加点（乐观更新）
                            double cost = nextCostLocal(skillButton.skillId());
                            skillPoints = Math.max(0, skillPoints - cost);
                            learnedSkills.merge(skillButton.skillId(), 1, Integer::sum);
                            activeLevels.put(skillButton.skillId(), learnedSkills.getOrDefault(skillButton.skillId(), 0));
                            PacketDistributor.sendToServer(new LearnSkillC2SPacket(skillButton.skillId(), 1));
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
                    if (Skills.getType(skillId) == Skills.SkillType.AURA && isShiftHeld()) {
                        // 切换该光环自己的目标模式（本地乐观更新，重进时由服务端回发校准）
                        int cur = auraTargetModes.getOrDefault(skillId, 0);
                        int mode = (cur + 1) % 3;
                        auraTargetModes.put(skillId, mode);
                        PacketDistributor.sendToServer(new AuraTargetC2SPacket(skillId, mode));
                    } else {
                        boolean now = !toggles.getOrDefault(skillId, Boolean.TRUE);
                        toggles.put(skillId, now);
                        PacketDistributor.sendToServer(new SetSkillToggleC2SPacket(skillId, now));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
                    PacketDistributor.sendToServer(new ResetSkillC2SPacket(skillId));
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
        if (Skills.AURA_TIME.equals(skillId) || Skills.AURA_WEATHER.equals(skillId)) return Skills.minorUltCost();
        Skills.SkillType type = Skills.getType(skillId);
        if (type == Skills.SkillType.AURA) {
            return Skills.getAuraCost(skillId, learnedSkills.getOrDefault(skillId, 0));
        }
        if (type == Skills.SkillType.BASE) return Skills.getBaseCostAtLevel(learnedSkills.getOrDefault(skillId, 0)); // 线性 +1/级
        if (type == Skills.SkillType.AMPLIFY) return Skills.getAmplifyCostAtLevel(learnedSkills.getOrDefault(skillId, 0)); // 线性 +2/级
        if (type == Skills.SkillType.MAGIC) return Skills.getMagicCostAtLevel(skillId, learnedSkills.getOrDefault(skillId, 0)); // 线性（默认+2/级，吟唱缩减+5/级）
        if (type == Skills.SkillType.MACHINE) return Skills.getMachineCost(skillId); // 机械共鸣：一次性（机械之星 1000 / 其余 5000）
        return Skills.getUltimateLevelCost(skillId, learnedSkills.getOrDefault(skillId, 0)); // 终极节点（单次或节点类阶梯递增）
    }

    private boolean isShiftHeld() {
        return Screen.hasShiftDown();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 中键：任意位置直接拖动技能树（包括按钮上/面板上）
        if (button == 2) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        // 左键：仅空白处拖动（不在技能按钮上、不在第一图层 UI 区域）
        if (button == 0 && !isOverUI(mouseX, mouseY) && !isHoveringAnyButton(mouseX, mouseY)) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 鼠标当前是否悬停在任一技能按钮上 */
    private boolean isHoveringAnyButton(double mouseX, double mouseY) {
        for (SkillButton b : buttons) {
            if (b.isHovered(mouseX, mouseY, this)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 鼠标在底部属性面板区域 → 翻页
        if (Config.PANEL_POSITION.get() == 1 && Config.PANEL_VISIBLE.get()
                && mouseY >= height - 140 && mouseY <= height - 10) {
            int step = Screen.hasShiftDown() ? 5 : 1;
            panelScroll -= (int) (verticalAmount * step);
            return true;
        }
        // 鼠标在右侧属性面板区域 → 滚动面板（仅面板可见时拦截；隐藏后滚轮恢复正常缩放）
        int px = width - PANEL_WIDTH - 10;
        if (Config.PANEL_POSITION.get() != 1 && Config.PANEL_VISIBLE.get()
                && mouseX >= px - 2 && mouseX <= px + PANEL_WIDTH + 2 && mouseY >= 48 && mouseY <= height - 32) {
            int step = Screen.hasShiftDown() ? 5 : 1;
            panelScroll -= (int) (verticalAmount * step);
            return true;
        }
        // 悬停在基础/增幅/多级终极技能上：滚轮调节生效等级（0 ~ 已学等级）
        for (SkillButton skillButton : buttons) {
            if (skillButton.isHovered(mouseX, mouseY, this)) {
                // 鼠标在第一图层 UI 区域（标题/提示条等）→ 不透过面板调级
                if (isOverUI(mouseX, mouseY)) {
                    break;
                }
                // 多级判定：等级上限 > 1（基础/增幅/多级终极/束域扩幅/谐振理论均可滚轮调生效等级）
                int maxLevel = Skills.getMaxPoints(skillButton.skillId());
                if (maxLevel > 1) {
                    int points = learnedSkills.getOrDefault(skillButton.skillId(), 0);
                    int active = activeLevels.getOrDefault(skillButton.skillId(), points);
                    // Shift+Ctrl 同时按下 → 一次调整 100 级；Shift → 10 级；否则 1 级
                    int step;
                    if (Screen.hasShiftDown() && Screen.hasControlDown()) {
                        step = 100;
                    } else if (Screen.hasShiftDown()) {
                        step = 10;
                    } else {
                        step = 1;
                    }
                    int delta = verticalAmount > 0 ? step : -step;
                    int next = Math.max(0, Math.min(points, active + delta));
                    activeLevels.put(skillButton.skillId(), next);
                    PacketDistributor.sendToServer(new SetSkillLevelC2SPacket(skillButton.skillId(), next));
                    return true;
                }
                break;
            }
        }
        // 否则缩放：以鼠标位置为缩放中心（鼠标指向的点保持不动，界面不漂移）
        // 原理：屏幕坐标 = 变换原点 + scale × 局部坐标；缩放前后保持鼠标下的局部坐标不变，
        //       反解出新的 panX/panY，使鼠标指向的技能/位置在缩放后仍在鼠标处。
        double factor = verticalAmount > 0 ? 1.1 : 1.0 / 1.1;
        double newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        if (newScale != scale) {
            double ox = width / 2.0 - 60 + panX;
            double oy = height / 2.0 + 10 + panY;
            double localX = (mouseX - ox) / scale;
            double localY = (mouseY - oy) / scale;
            scale = newScale;
            panX = mouseX - (width / 2.0 - 60) - newScale * localX;
            panY = mouseY - (height / 2.0 + 10) - newScale * localY;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 关闭界面时保存位置/缩放（下次打开恢复，2026-08-13 需求） */
    @Override
    public void onClose() {
        org.zifeng.skilltree.client.SkillKeyBinds.saveViewState(panX, panY, scale);
        super.onClose();
    }

    double toPanelX(double screenX) {
        return (screenX - (width / 2.0 - 60) - panX) / scale;
    }

    double toPanelY(double screenY) {
        return (screenY - (height / 2.0 + 10) - panY) / scale;
    }
}
