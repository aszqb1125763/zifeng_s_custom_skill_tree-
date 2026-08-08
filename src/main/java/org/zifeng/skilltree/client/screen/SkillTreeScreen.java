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
import org.zifeng.skilltree.network.ResetSkillsC2SPacket;
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
    private int auraTargetMode;
    private final List<SkillButton> buttons = new ArrayList<>();

    private double scale = 1.0;
    private double panX = 0;
    private double panY = 0;
    private int lastMouseX;
    private int lastMouseY;
    private int panelScroll = 0; // 属性面板滚动偏移（0 = 顶部）

    public SkillTreeScreen(int skillPoints, Map<String, Integer> learnedSkills, Map<String, Boolean> toggles,
                           Map<String, Integer> activeLevels, boolean auraEnabled, int auraTargetMode) {
        super(Component.literal("技能树"));
        updateData(skillPoints, learnedSkills, toggles, activeLevels, auraEnabled, auraTargetMode);
    }

    public void updateData(double skillPoints, Map<String, Integer> learnedSkills, Map<String, Boolean> toggles,
                           Map<String, Integer> activeLevels, boolean auraEnabled, int auraTargetMode) {
        this.skillPoints = skillPoints;
        this.learnedSkills.clear();
        this.learnedSkills.putAll(learnedSkills);
        this.toggles.clear();
        this.toggles.putAll(toggles);
        this.activeLevels.clear();
        this.activeLevels.putAll(activeLevels);
        this.auraEnabled = auraEnabled;
        this.auraTargetMode = auraTargetMode;
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

    /** 四纵列布局：四列顶部对齐（上方对齐），不再按技能数垂直居中 */
    private void rebuildButtons() {
        buttons.clear();
        // 4 列中心 x：列宽 150，间隔 30
        int[] colCenters = {-270, -90, 90, 270};
        placeColumn(Skills.BASE_SKILLS, colCenters[0]);
        placeColumn(Skills.AMPLIFY_SKILLS, colCenters[1]);
        placeColumn(Skills.ULTIMATE_SKILLS, colCenters[2]);
        placeColumn(Skills.AURA_SKILLS, colCenters[3]);
    }

    /** 四列统一顶部 y（上方对齐）：按钮区上方留空间给列标题 */
    private static final int COLUMN_TOP = -160;

    /** 单列从上往下摆放（顶部对齐，列高不再影响起始位置） */
    private void placeColumn(List<String> skills, int centerX) {
        int y = COLUMN_TOP;
        for (String skill : skills) {
            buttons.add(new SkillButton(skill, centerX - BUTTON_WIDTH / 2, y));
            y += BUTTON_HEIGHT + VERTICAL_SPACING;
        }
    }

    /** 列顶部 y（列标题用，四列统一） */
    private int colTop(List<String> skills) {
        return COLUMN_TOP;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        guiGraphics.fill(0, 0, width, height, Config.SKILL_TREE_BACKGROUND_COLOR.get());
        int border = Config.SKILL_TREE_BORDER_COLOR.get();
        guiGraphics.fill(0, 0, width, BORDER_THICKNESS, border);
        guiGraphics.fill(0, height - BORDER_THICKNESS, width, height, border);
        guiGraphics.fill(0, BORDER_THICKNESS, BORDER_THICKNESS, height - BORDER_THICKNESS, border);
        guiGraphics.fill(width - BORDER_THICKNESS, BORDER_THICKNESS, width, height - BORDER_THICKNESS, border);

        // 技能面板（屏幕中心偏左）
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(width / 2.0 - 60 + panX, height / 2.0 + 10 + panY, 0);
        guiGraphics.pose().scale((float) scale, (float) scale, 1.0F);

        // 列标题（跟随各列顶部）
        int[] colCenters = {-270, -90, 90, 270};
        guiGraphics.drawCenteredString(font, "基础属性", colCenters[0], colTop(Skills.BASE_SKILLS) - 8, 0xFF87CEEB);
        guiGraphics.drawCenteredString(font, "特殊增幅", colCenters[1], colTop(Skills.AMPLIFY_SKILLS) - 8, 0xFFFFAA55);
        guiGraphics.drawCenteredString(font, "终极节点", colCenters[2], colTop(Skills.ULTIMATE_SKILLS) - 8, 0xFFFF5555);
        guiGraphics.drawCenteredString(font, "光环", colCenters[3], colTop(Skills.AURA_SKILLS) - 8, 0xFFAA55FF);

        for (SkillButton button : buttons) {
            renderSkillButton(guiGraphics, button);
        }
        guiGraphics.pose().popPose();

        renderAttributesPanel(guiGraphics);
        renderTogglePanel(guiGraphics);
        // 面板开关按钮（右下角，保证在最上层不被提示条盖住）
        if (Config.PANEL_VISIBLE.get()) {
            renderPanelToggleButton(guiGraphics);
        } else {
            renderPanelRestoreButton(guiGraphics);
        }
        // 顶部信息区：字体缩小 0.2（×0.8），行间隔 15 像素，首行离上边框 10 像素
        // ⚠️ 必须最后绘制：放在所有面板/按钮之后（悬停提示之前），保证圆角背景+边框在最顶层不被覆盖
        guiGraphics.flush(); // 先提交前面所有内容，确保顶部信息区最后叠加在最上层
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(width / 2.0, 10.0, 0);
        guiGraphics.pose().scale(0.8f, 0.8f, 1.0f);
        String title = "子枫 · 技能树";
        String modeText = switch (auraTargetMode) {
            case 1 -> "友好";
            case 2 -> "所有";
            default -> "敌对";
        };
        String statusLine = "技能点：" + String.format("%.1f", skillPoints)
                + "  ·  光环:" + (auraEnabled ? "开" : "关")
                + "  ·  目标:" + modeText
                + "  ·  左键加点 / Shift+左键×10 / 右键开关 / 滚轮调级 / Ctrl+R 重洗";
        // 光环快捷键未绑定提示（默认空键，引导玩家自行设置）
        boolean showWarn = org.zifeng.skilltree.client.ModKeyBindingEvents.hasUnboundAuraKeys();
        String warnLine = showWarn ? "⚠ 光环技能快捷键未绑定（伤害/速度/治愈/时环/晴空/守卫），请在 设置→控制 中设置" : null;
        // 以文字包围盒为中心外扩 10px 的圆角背景 + 边框（不超出文字范围）
        int maxWidth = Math.max(font.width(title), font.width(statusLine));
        if (warnLine != null) {
            maxWidth = Math.max(maxWidth, font.width(warnLine));
        }
        int bgLeft = -maxWidth / 2 - 10;
        int bgRight = maxWidth / 2 + 10;
        int bgTop = -10;
        int bgBottom = (warnLine != null ? 30 : 15) + 10;
        // 边框（外扩 1px）+ 背景（圆角半径 10）
        fillRoundedRect(guiGraphics, bgLeft - 1, bgTop - 1, bgRight + 1, bgBottom + 1, 10, 0xFF87CEEB);
        fillRoundedRect(guiGraphics, bgLeft, bgTop, bgRight, bgBottom, 10, 0xCC000000);
        guiGraphics.drawCenteredString(font, title, 0, 0, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, statusLine, 0, 15, 0xFFFFD700);
        if (warnLine != null) {
            guiGraphics.drawCenteredString(font, warnLine, 0, 30, 0xFFFF5555);
        }
        guiGraphics.pose().popPose();
        // 悬停提示（屏幕坐标绘制，避免变换坐标系错位）：技能描述 + 每级消耗 + 终极前置需求状态
        // 注意：鼠标在属性面板区域 或 顶部信息区时不显示技能提示（面板/背景不透明，背后的按钮描述不该穿透）
        boolean overPanel = Config.PANEL_VISIBLE.get() && isMouseOverPanel(mouseX, mouseY);
        boolean overHeader = isMouseOverHeader(mouseX, mouseY);
        for (SkillButton button : buttons) {
            if (!overPanel && !overHeader && button.isHovered(mouseX, mouseY, this)) {
                Skills.SkillType type = Skills.getType(button.skillId());
                java.util.List<Component> lines = new ArrayList<>();
                // 描述按 \n 拆成多行（适配小屏，避免单行过长超出屏幕）
                for (String line : Skills.getDescription(button.skillId()).split("\\n")) {
                    lines.add(Component.literal(line));
                }
                if (type == Skills.SkillType.BASE) {
                    lines.add(Component.literal("[每级消耗 " + fmtCost(Skills.basePointCost()) + " 点]"));
                } else if (type == Skills.SkillType.AMPLIFY) {
                    lines.add(Component.literal("[每级消耗 " + fmtCost(Skills.amplifyPointCost()) + " 点]"));
                } else if (type == Skills.SkillType.AURA) {
                    lines.add(Component.literal("[下次消耗 " + (long) recordNextCost(button.skillId()) + " 点]"));
                } else if (type == Skills.SkillType.ULTIMATE) {
                    // 终极节点：动态显示每个前置技能的满足状态（✓ 已满足 / ✗ 未满足）
                    List<String> reqs = Skills.getUltimateRequirements(button.skillId());
                    if (!reqs.isEmpty()) {
                        lines.add(Component.literal("—— 前置需求 ——").withColor(0xFFFFD700));
                        for (String required : reqs) {
                            // 前置是终极节点时只需解锁（1点）；是基础/增幅技能时需 500 点（Config 可调）
                            int need = Skills.getType(required) == Skills.SkillType.ULTIMATE ? 1 : Skills.ultimateRequirePoints();
                            int have = learnedSkills.getOrDefault(required, 0);
                            boolean met = have >= need;
                            lines.add(Component.literal((met ? "✓ " : "✗ ") + Skills.getDisplayName(required) + " " + have + "/" + need)
                                    .withColor(met ? 0xFF55FF55 : 0xFFFF5555));
                        }
                    }
                }
                guiGraphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY - 20);
                break;
            }
        }
    }

    private void renderSkillButton(GuiGraphics guiGraphics, SkillButton button) {
        boolean hovered = button.isHovered(lastMouseX, lastMouseY, this);
        Skills.SkillType type = Skills.getType(button.skillId());
        boolean canLearn = canLearn(button.skillId());
        boolean learned = learnedSkills.getOrDefault(button.skillId(), 0) > 0;
        boolean enabled = toggles.getOrDefault(button.skillId(), Boolean.TRUE);

        int bg = switch (type) {
            case BASE -> hovered ? 0xFF3A5A8A : 0xFF24476E;
            case AMPLIFY -> hovered ? 0xFF8A5A2A : 0xFF6E4424;
            case ULTIMATE -> hovered ? 0xFF8A2A3A : 0xFF6E242E;
            case AURA -> hovered ? 0xFF5A3A8A : 0xFF3E2470;
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

        // 技能图标（左侧 16×16，用原版物品图标）
        guiGraphics.renderItem(new net.minecraft.world.item.ItemStack(Skills.getIcon(button.skillId())), button.x() + 3, button.y() + 3);
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
            } else if (Skills.AURA_TIME.equals(button.skillId()) || Skills.AURA_WEATHER.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需" + Skills.minorUltCost() + "点";
            } else {
                long total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getAuraCost(button.skillId(), i);
                }
                costText = "已耗" + total + "点 下1级:" + (long) nextCost + "点";
            }
        } else if (type == Skills.SkillType.BASE || type == Skills.SkillType.AMPLIFY) {
            double unitCost = type == Skills.SkillType.BASE ? Skills.basePointCost() : Skills.amplifyPointCost();
            // 生效等级（滚轮可调，实时显示）+ 下一级真实消耗（与 learnSkill 实际扣除一致：基础 1 点 / 增幅 2 点）
            int active = activeLevels.getOrDefault(button.skillId(), points);
            costText = "生效:" + active + "/" + points + " 下1级:" + fmtCost(unitCost) + "点";
        } else if (type == Skills.SkillType.ULTIMATE) {
            // 终极节点：单次解锁消耗（浴血/连斩/金身/挖/精通=1点，宇宙的青睐=1000，夜视/饱食=100）
            if (Skills.ULT_FAVOR.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需" + Skills.ultFavorCost() + "点";
            } else if (Skills.NIGHT_VISION.equals(button.skillId()) || Skills.SATURATION.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需" + Skills.minorUltCost() + "点";
            } else {
                costText = points > 0 ? "已解锁" : "需1点";
            }
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
        if (type == Skills.SkillType.ULTIMATE && current >= 1) return false;
        if (type == Skills.SkillType.AURA && current >= Skills.getAuraMaxPoints(skillId)) return false;
        // 终极前置：前置是终极节点只需解锁（1点）；是基础/增幅技能需 500 点（Config 可调）
        if (type == Skills.SkillType.ULTIMATE && !Skills.ULT_FAVOR.equals(skillId)) {
            for (String required : Skills.getUltimateRequirements(skillId)) {
                int need = Skills.getType(required) == Skills.SkillType.ULTIMATE ? 1 : Skills.ultimateRequirePoints();
                if (learnedSkills.getOrDefault(required, 0) < need) return false;
            }
        }
        // 技能点足够
        if (skillPoints < nextCostLocal(skillId) - 1e-9) return false;
        return true;
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

    /** 鼠标是否在属性面板区域内（tooltip 穿透检查用） */
    private boolean isMouseOverPanel(double mouseX, double mouseY) {
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
        String title = "子枫 · 技能树";
        String modeText = switch (auraTargetMode) {
            case 1 -> "友好";
            case 2 -> "所有";
            default -> "敌对";
        };
        String statusLine = "技能点：" + String.format("%.1f", skillPoints)
                + "  ·  光环:" + (auraEnabled ? "开" : "关")
                + "  ·  目标:" + modeText
                + "  ·  左键加点 / Shift+左键×10 / 右键开关 / 滚轮调级 / Ctrl+R 重洗";
        int maxWidth = Math.max(font.width(title), font.width(statusLine));
        if (org.zifeng.skilltree.client.ModKeyBindingEvents.hasUnboundAuraKeys()) {
            maxWidth = Math.max(maxWidth, font.width("⚠ 光环技能快捷键未绑定（伤害/速度/治愈/时环/晴空/守卫），请在 设置→控制 中设置"));
        }
        // 渲染时：translate(width/2, 10) + scale(0.8) → 屏幕坐标换算
        double halfW = (maxWidth / 2.0 + 10) * 0.8;
        double topY = 10 - 10 * 0.8;
        double bottomY = 10 + ((org.zifeng.skilltree.client.ModKeyBindingEvents.hasUnboundAuraKeys() ? 30 : 15) + 10) * 0.8;
        return mouseX >= width / 2.0 - halfW && mouseX <= width / 2.0 + halfW
                && mouseY >= topY && mouseY <= bottomY;
    }

    /** 属性行收集（共用逻辑，右侧/底部布局都展示同一份数据） */
    private java.util.List<String[]> collectRows() {
        var player = minecraft != null ? minecraft.player : null;
        if (player == null) return java.util.List.of();
        // 本地技能记录（含生效等级），属性值全部本地计算 → 加点立即实时刷新，不依赖服务端属性同步
        org.zifeng.skilltree.data.PlayerSkillRecord rec = learnedAsRecord();
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        addRow(rows, "生命", SkillEffects.getComputedValue(player, Attributes.MAX_HEALTH, rec), "%.0f");
        addRow(rows, "护甲", SkillEffects.getComputedValue(player, Attributes.ARMOR, rec), "%.1f");
        addRow(rows, "韧性", SkillEffects.getComputedValue(player, Attributes.ARMOR_TOUGHNESS, rec), "%.1f");
        addRow(rows, "击退抗性", SkillEffects.getComputedValue(player, Attributes.KNOCKBACK_RESISTANCE, rec), "%.1f");
        // 物理减伤（自定义属性）：护甲减伤 80% 封顶后继续叠的独立减伤层
        addRow(rows, "物理减伤", SkillEffects.getComputedValue(player, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION, rec) * 100, "%.0f%%");
        addRow(rows, "攻伤", SkillEffects.getComputedValue(player, Attributes.ATTACK_DAMAGE, rec), "%.1f");
        addRow(rows, "攻速", SkillEffects.getComputedValue(player, Attributes.ATTACK_SPEED, rec), "%.2f");
        addRow(rows, "击退", SkillEffects.getComputedValue(player, Attributes.ATTACK_KNOCKBACK, rec), "%.1f");
        // 挖速用原版 Attributes.MINING_EFFICIENCY（NeoForge 合入的加数属性，直接反映实际挖掘加速；BLOCK_BREAK_SPEED 是乘数语义不同）
        addRow(rows, "挖速", SkillEffects.getComputedValue(player, Attributes.MINING_EFFICIENCY, rec), "%.1f");
        // 速度显示为每秒方块数：移速 0.1→4.317方/秒，飞行 0.05→10.8方/秒，游泳→3.35方/秒
        addRow(rows, "移速", SkillEffects.getComputedValue(player, Attributes.MOVEMENT_SPEED, rec) * 43.17, "%.2f方/秒");
        // 飞速：实际飞行速度 = abilities.flyingSpeed（每 tick 由 FLYING_SPEED 属性÷8 同步）；0.05 → 10.8 方/秒
        addRow(rows, "飞速", player.getAbilities().getFlyingSpeed() * 216, "%.2f方/秒");
        // 游泳：SWIM_SPEED 默认 1.0 → 原版游泳 ≈ 3.35 方/秒（此前 ×111.7 错误地把默认显示成 111）
        double swim = player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED) != null
                ? SkillEffects.getComputedValue(player, net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED, rec) * 3.35 : 0;
        addRow(rows, "游泳", swim, "%.2f方/秒");
        // 跳跃高度（格）= JUMP_STRENGTH² × 6.25（无药水时）
        double jump = SkillEffects.getComputedValue(player, Attributes.JUMP_STRENGTH, rec);
        addRow(rows, "跳高", jump * jump * 6.25, "%.2f格");
        addRow(rows, "回血/秒", SkillEffects.getRegenPerSecond(rec), "%.1f");
        addRow(rows, "幸运", SkillEffects.getComputedValue(player, Attributes.LUCK, rec), "%.1f");
        addRow(rows, "暴击率", SkillEffects.getCritChance(rec) * 100, "%.0f%%");
        addRow(rows, "暴击伤害", SkillEffects.getCritMultiplier(rec), "%.1f倍");
        addRow(rows, "吸血", SkillEffects.getLifestealRate(rec) * 100, "%.0f%%");
        addRow(rows, "荆棘反伤", SkillEffects.getThornsDamage(rec), "%.1f");
        addRow(rows, "破甲增伤", SkillEffects.getArmorPenPercent(rec) * 100, "%.0f%%");
        addRow(rows, "掉落倍率", SkillEffects.getDropMultiplier(rec), "%.2f");
        addRow(rows, "经验倍率", SkillEffects.getExperienceMultiplier(rec), "%.2f");
        // 耐久减免：未满显示百分比，封顶（100%）显示"工具不毁"
        double durReduction = SkillEffects.getToolDurabilityReduction(rec);
        if (durReduction >= 1.0) {
            rows.add(new String[]{"耐久减免", "工具不毁", "#FFFFD700"});
        } else {
            addRow(rows, "耐久减免", durReduction * 100, "%.0f%%");
        }
        addRow(rows, "光环伤害", SkillEffects.getComputedValue(player, Attributes.ATTACK_DAMAGE, rec), "%.1f");
        // 光环攻击频率 = 实际攻速属性 - 3（ATTACK_SPEED 基础 4.0 → 1 次/秒，100 级光环速度 = 20 次/秒）
        addRow(rows, "光环频率/秒", Math.max(0, SkillEffects.getComputedValue(player, Attributes.ATTACK_SPEED, rec) - 3), "%.1f");
        // 光环范围半径（Config 可调，360° 球形覆盖）
        addRow(rows, "光环半径", org.zifeng.skilltree.Config.AURA_ATTACK_RADIUS.get(), "%.0f格");
        // 守卫光环：全伤害防护（含真伤/混沌/指令）
        int guardLevel = rec.isEnabled(Skills.AURA_GUARD) ? rec.getActiveLevel(Skills.AURA_GUARD) : 0;
        addRow(rows, "守卫减伤", Math.min(1.0, guardLevel * org.zifeng.skilltree.Config.AURA_GUARD_REDUCTION_PER_LEVEL.get()) * 100, "%.0f%%");
        rows.add(new String[]{"技能点", String.format("%.1f", skillPoints), "#FFFFD700"});
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
            int c = color.equals("#FFFFD700") ? 0xFFFFD700 : 0xFFFFFFFF;
            guiGraphics.drawString(font, row[0], x + 4, line, 0xFFAAAAAA);
            // 数值右对齐到滚动条左侧（滚动条在 x+PANEL_WIDTH-6，留 4px 间隔 → 数值起点 = x+PANEL_WIDTH-10-字体宽度）
            String value = row[1];
            guiGraphics.drawString(font, value, x + PANEL_WIDTH - 10 - font.width(value), line, c);
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
            int c = color.equals("#FFFFD700") ? 0xFFFFD700 : 0xFFFFFFFF;
            int px = x + 4 + col * colW;
            int py = y + 18 + r * 12;
            guiGraphics.drawString(font, row[0], px, py, 0xFFAAAAAA);
            guiGraphics.drawString(font, row[1], px + 62, py, c);
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

    /** 圆角矩形填充（主体矩形 + 四角阶梯近似，radius=圆角半径） */
    private void fillRoundedRect(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int radius, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        int r = Math.max(1, Math.min(radius, (right - left) / 2));
        r = Math.min(r, (bottom - top) / 2);
        // 主体
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), left + r, top, right - r, bottom, color);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), left, top + r, right, bottom - r, color);
        // 四角阶梯近似（每角 2 个方块，形成圆角）
        int half = r / 2;
        // 左上
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), left, top + half, left + half, top + r, color);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), left + half, top, left + r, top + half, color);
        // 右上
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), right - half, top, right, top + half, color);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), right - r, top + half, right - half, top + r, color);
        // 左下
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), left, bottom - r, left + half, bottom - half, color);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), left + half, bottom - half, left + r, bottom, color);
        // 右下
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), right - r, bottom - half, right - half, bottom, color);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), right - half, bottom - half, right, bottom, color);
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
        if (button == 0) {
            for (SkillButton skillButton : buttons) {
                if (skillButton.isHovered(mouseX, mouseY, this)) {
                    if (canLearn(skillButton.skillId())) {
                        if (isShiftHeld()) {
                            // Shift+点击：一次加 10 级（受技能点与上限约束，乐观连加）
                            int added = 0;
                            for (int i = 0; i < 10; i++) {
                                if (!canLearn(skillButton.skillId())) break;
                                skillPoints -= nextCostLocal(skillButton.skillId());
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
                            skillPoints -= cost;
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
            for (SkillButton skillButton : buttons) {
                if (skillButton.isHovered(mouseX, mouseY, this)) {
                    String skillId = skillButton.skillId();
                    if (Skills.getType(skillId) == Skills.SkillType.AURA && isShiftHeld()) {
                        // 切换目标模式（本地乐观更新，重进时由服务端回发校准）
                        int mode = (auraTargetMode + 1) % 3;
                        auraTargetMode = mode;
                        PacketDistributor.sendToServer(new AuraTargetC2SPacket(mode));
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
        // Ctrl+R：技能重洗（防误触；服务端按返还率加回技能点后回发校准）
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R && Screen.hasControlDown()) {
            PacketDistributor.sendToServer(new ResetSkillsC2SPacket());
            // 本地乐观清空，等待服务端回发（tick 轮询 40 tick 内校准）
            skillPoints = 0;
            learnedSkills.clear();
            toggles.clear();
            activeLevels.clear();
            rebuildButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private double nextCostLocal(String skillId) {
        if (Skills.ULT_FAVOR.equals(skillId)) return Skills.ultFavorCost();
        if (Skills.NIGHT_VISION.equals(skillId) || Skills.SATURATION.equals(skillId)) return Skills.minorUltCost();
        if (Skills.AURA_MAGNET.equals(skillId)) return org.zifeng.skilltree.Config.MAGNET_COST.get();
        if (Skills.AURA_TIME.equals(skillId) || Skills.AURA_WEATHER.equals(skillId)) return Skills.minorUltCost();
        Skills.SkillType type = Skills.getType(skillId);
        if (type == Skills.SkillType.AURA) {
            return Skills.getAuraCost(skillId, learnedSkills.getOrDefault(skillId, 0));
        }
        if (type == Skills.SkillType.BASE) return Skills.basePointCost();
        if (type == Skills.SkillType.AMPLIFY) return Skills.amplifyPointCost();
        return 1;
    }

    private boolean isShiftHeld() {
        return Screen.hasShiftDown();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
        // 悬停在基础/增幅技能上：滚轮调节生效等级（0 ~ 已学等级）
        for (SkillButton skillButton : buttons) {
            if (skillButton.isHovered(mouseX, mouseY, this)) {
                Skills.SkillType type = Skills.getType(skillButton.skillId());
                if (type == Skills.SkillType.BASE || type == Skills.SkillType.AMPLIFY) {
                    int points = learnedSkills.getOrDefault(skillButton.skillId(), 0);
                    int active = activeLevels.getOrDefault(skillButton.skillId(), points);
                    // Shift 按下 → 翻倍步进
                    int step = Screen.hasShiftDown() ? 10 : 1;
                    int delta = verticalAmount > 0 ? step : -step;
                    int next = Math.max(0, Math.min(points, active + delta));
                    activeLevels.put(skillButton.skillId(), next);
                    PacketDistributor.sendToServer(new SetSkillLevelC2SPacket(skillButton.skillId(), next));
                    return true;
                }
                break;
            }
        }
        // 否则缩放
        double factor = verticalAmount > 0 ? 1.1 : 1.0 / 1.1;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    double toPanelX(double screenX) {
        return (screenX - (width / 2.0 - 60) - panX) / scale;
    }

    double toPanelY(double screenY) {
        return (screenY - (height / 2.0 + 10) - panY) / scale;
    }

    private record SkillButton(String skillId, int x, int y) {
        boolean isHovered(double mouseX, double mouseY, SkillTreeScreen screen) {
            double px = screen.toPanelX(mouseX);
            double py = screen.toPanelY(mouseY);
            return px >= x && px <= x + BUTTON_WIDTH && py >= y && py <= y + BUTTON_HEIGHT;
        }
    }
}
