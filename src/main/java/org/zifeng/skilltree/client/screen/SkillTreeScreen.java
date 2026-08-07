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

    /** 四纵列布局：每列按自身技能数居中 */
    private void rebuildButtons() {
        buttons.clear();
        // 4 列中心 x：列宽 150，间隔 30
        int[] colCenters = {-270, -90, 90, 270};
        placeColumn(Skills.BASE_SKILLS, colCenters[0]);
        placeColumn(Skills.AMPLIFY_SKILLS, colCenters[1]);
        placeColumn(Skills.ULTIMATE_SKILLS, colCenters[2]);
        placeColumn(Skills.AURA_SKILLS, colCenters[3]);
    }

    /** 单列垂直居中摆放（列高 = 数量×(按钮高+间距)，上下留 40px 给列标题） */
    private void placeColumn(List<String> skills, int centerX) {
        int count = skills.size();
        int colHeight = count * BUTTON_HEIGHT + Math.max(0, count - 1) * VERTICAL_SPACING;
        int top = -colHeight / 2 + 20; // 顶部留列标题空间
        int y = top;
        for (String skill : skills) {
            buttons.add(new SkillButton(skill, centerX - BUTTON_WIDTH / 2, y));
            y += BUTTON_HEIGHT + VERTICAL_SPACING;
        }
    }

    /** 列顶部 y（列标题用） */
    private int colTop(List<String> skills) {
        int count = skills.size();
        int colHeight = count * BUTTON_HEIGHT + Math.max(0, count - 1) * VERTICAL_SPACING;
        return -colHeight / 2 + 20;
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

        guiGraphics.drawCenteredString(font, "子枫 · 技能树", width / 2, 10, 0xFFFFFFFF);
        String modeText = switch (auraTargetMode) {
            case 1 -> "友好";
            case 2 -> "所有";
            default -> "敌对";
        };
        guiGraphics.drawCenteredString(font, "技能点：" + String.format("%.1f", skillPoints)
                + "  ·  光环:" + (auraEnabled ? "开" : "关")
                + "  ·  目标:" + modeText
                + "  ·  左键加点 / 右键开关注 / 悬停滚轮调生效等级 / Shift+滚轮翻倍", width / 2, 30, 0xFFFFD700);

        // 技能面板（屏幕中心偏左）
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(width / 2.0 - 60 + panX, height / 2.0 + 10 + panY, 0);
        guiGraphics.pose().scale((float) scale, (float) scale, 1.0F);

        // 列标题（跟随各列顶部）
        int[] colCenters = {-270, -90, 90, 270};
        guiGraphics.drawCenteredString(font, "基础属性", colCenters[0], colTop(Skills.BASE_SKILLS) - 8, 0xFF87CEEB);
        guiGraphics.drawCenteredString(font, "特殊增幅", colCenters[1], colTop(Skills.AMPLIFY_SKILLS) - 8, 0xFFFFAA55);
        guiGraphics.drawCenteredString(font, "终极节点", colCenters[2], colTop(Skills.ULTIMATE_SKILLS) - 8, 0xFFFF5555);
        guiGraphics.drawCenteredString(font, "杀戮光环", colCenters[3], colTop(Skills.AURA_SKILLS) - 8, 0xFFAA55FF);

        for (SkillButton button : buttons) {
            renderSkillButton(guiGraphics, button);
        }
        guiGraphics.pose().popPose();

        renderAttributesPanel(guiGraphics);
        renderTogglePanel(guiGraphics);

        // 悬停提示（屏幕坐标绘制，避免变换坐标系错位）：技能描述 + 每级消耗 + 终极前置需求状态
        for (SkillButton button : buttons) {
            if (button.isHovered(mouseX, mouseY, this)) {
                Skills.SkillType type = Skills.getType(button.skillId());
                java.util.List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(Skills.getDescription(button.skillId())));
                if (type == Skills.SkillType.BASE) {
                    lines.add(Component.literal("[每级消耗 " + String.format("%.1f", Skills.BASE_POINT_COST) + " 点]"));
                } else if (type == Skills.SkillType.AMPLIFY) {
                    lines.add(Component.literal("[每级消耗 " + String.format("%.1f", Skills.AMPLIFY_POINT_COST) + " 点]"));
                } else if (type == Skills.SkillType.AURA) {
                    lines.add(Component.literal("[下次消耗 " + (long) recordNextCost(button.skillId()) + " 点]"));
                } else if (type == Skills.SkillType.ULTIMATE) {
                    // 终极节点：动态显示每个前置技能的满足状态（✓ 已满足 / ✗ 未满足）
                    List<String> reqs = Skills.getUltimateRequirements(button.skillId());
                    if (!reqs.isEmpty()) {
                        lines.add(Component.literal("—— 前置需求 ——").withColor(0xFFFFD700));
                        for (String required : reqs) {
                            // 前置是终极节点时只需解锁（1点）；是基础/增幅技能时需 500 点
                            int need = Skills.getType(required) == Skills.SkillType.ULTIMATE ? 1 : Skills.ULTIMATE_REQUIRE_POINTS;
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

        // 名称 + 开关标记
        String name = (enabled ? "" : "⛔ ") + Skills.getDisplayName(button.skillId());
        guiGraphics.drawString(font, name, button.x() + 4, button.y() + 3, enabled ? 0xFFFFFFFF : 0xFF888888);
        // 数据
        int points = learnedSkills.getOrDefault(button.skillId(), 0);
        double nextCost = recordNextCost(button.skillId());

        // 第2行：等级/上限显示（所有技能统一，与杀戮光环风格一致）
        String effectText = points + "级/" + Skills.getMaxPoints(button.skillId());
        while (!effectText.isEmpty() && font.width(effectText) > BUTTON_WIDTH - 8) {
            effectText = effectText.substring(0, effectText.length() - 1);
        }
        guiGraphics.drawString(font, effectText, button.x() + 4, button.y() + 17, 0xFF55FF55);

        // 第3行：消耗总数量（已消耗 + 下一级）
        String costText;
        if (type == Skills.SkillType.AURA) {
            long total = 0;
            for (int i = 0; i < points; i++) {
                total += Skills.getAuraCost(button.skillId(), i);
            }
            costText = "已耗" + total + "点 下1级:" + (long) nextCost + "点";
        } else if (type == Skills.SkillType.BASE || type == Skills.SkillType.AMPLIFY) {
            double unitCost = type == Skills.SkillType.BASE ? Skills.BASE_POINT_COST : Skills.AMPLIFY_POINT_COST;
            // 生效等级（滚轮可调，实时显示）+ 下一级总消耗
            int active = activeLevels.getOrDefault(button.skillId(), points);
            costText = "生效:" + active + "/" + points + " 点出下1级共需" + String.format("%.1f", (points + 1) * unitCost) + "点";
        } else if (type == Skills.SkillType.ULTIMATE) {
            // 终极节点：单次解锁消耗（浴血/连斩/金身/挖/精通=1点，宇宙的青睐=1000，夜视/饱食=100）
            if (Skills.ULT_FAVOR.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需1000点";
            } else if (Skills.NIGHT_VISION.equals(button.skillId()) || Skills.SATURATION.equals(button.skillId())) {
                costText = points > 0 ? "已解锁" : "需100点";
            } else {
                costText = points > 0 ? "已解锁" : "需1点";
            }
        } else {
            costText = points + "级";
        }
        while (!costText.isEmpty() && font.width(costText) > BUTTON_WIDTH - 8) {
            costText = costText.substring(0, costText.length() - 1);
        }
        guiGraphics.drawString(font, costText, button.x() + 4, button.y() + 31, canLearn ? 0xFFFFAA55 : 0xFFAAAAAA);
    }

    /** 估算下一级消耗（客户端显示用） */
    private double recordNextCost(String skillId) {
        return nextCostLocal(skillId);
    }

    /** 客户端可学判定 */
    private boolean canLearn(String skillId) {
        Skills.SkillType type = Skills.getType(skillId);
        int current = learnedSkills.getOrDefault(skillId, 0);
        if (type == Skills.SkillType.BASE && current >= Skills.BASE_MAX_POINTS) return false;
        if (type == Skills.SkillType.AMPLIFY && current >= Skills.AMPLIFY_MAX_POINTS) return false;
        if (type == Skills.SkillType.ULTIMATE && current >= 1) return false;
        if (type == Skills.SkillType.AURA && current >= Skills.getAuraMaxPoints(skillId)) return false;
        // 终极前置：前置是终极节点只需解锁（1点）；是基础/增幅技能需 500 点
        if (type == Skills.SkillType.ULTIMATE && !Skills.ULT_FAVOR.equals(skillId)) {
            for (String required : Skills.getUltimateRequirements(skillId)) {
                int need = Skills.getType(required) == Skills.SkillType.ULTIMATE ? 1 : Skills.ULTIMATE_REQUIRE_POINTS;
                if (learnedSkills.getOrDefault(required, 0) < need) return false;
            }
        }
        // 技能点足够
        if (skillPoints < nextCostLocal(skillId) - 1e-9) return false;
        return true;
    }

    // ============ 属性面板 ============

    /** 面板可视行数（减去标题/技能点行） */
    private int panelVisibleRows() {
        return (height - 50 - 30 - 40) / 12 - 2;
    }

    private void renderAttributesPanel(GuiGraphics guiGraphics) {
        // 先提交按钮文字批次，再用 guiOverlay（无深度测试，无条件覆盖）画面板背景，彻底盖住下层文字
        guiGraphics.flush();
        int x = width - PANEL_WIDTH - 10;
        int y = 50;
        // guiOverlay = NO_DEPTH_TEST + COLOR_WRITE（原版 tooltip 背景同款）→ 面板永远在最上层，下层按钮文字/描述透不过来
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, x + PANEL_WIDTH + 2, height - 30, 0xFF101010);
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, x + PANEL_WIDTH + 2, y, 0xFF87CEEB);
        guiGraphics.drawString(font, "≡ 属性加成", x + 4, y + 4, 0xFFFFD700);

        var player = minecraft != null ? minecraft.player : null;
        if (player == null) return;

        // 本地技能记录（含生效等级），属性值全部本地计算 → 加点立即实时刷新，不依赖服务端属性同步
        org.zifeng.skilltree.data.PlayerSkillRecord rec = learnedAsRecord();

        // 收集所有属性行
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        addRow(rows, "生命", SkillEffects.getComputedValue(player, Attributes.MAX_HEALTH, rec), "%.0f");
        addRow(rows, "护甲", SkillEffects.getComputedValue(player, Attributes.ARMOR, rec), "%.1f");
        addRow(rows, "韧性", SkillEffects.getComputedValue(player, Attributes.ARMOR_TOUGHNESS, rec), "%.1f");
        addRow(rows, "击退抗性", SkillEffects.getComputedValue(player, Attributes.KNOCKBACK_RESISTANCE, rec), "%.1f");
        addRow(rows, "攻伤", SkillEffects.getComputedValue(player, Attributes.ATTACK_DAMAGE, rec), "%.1f");
        addRow(rows, "攻速", SkillEffects.getComputedValue(player, Attributes.ATTACK_SPEED, rec), "%.2f");
        addRow(rows, "攻击退", SkillEffects.getComputedValue(player, Attributes.ATTACK_KNOCKBACK, rec), "%.1f");
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
        addRow(rows, "掉落倍率", SkillEffects.getDropMultiplier(rec), "%.2f");
        addRow(rows, "经验倍率", SkillEffects.getExperienceMultiplier(rec), "%.2f");
        addRow(rows, "耐久减免", SkillEffects.getToolDurabilityReduction(rec), "%.0f");
        addRow(rows, "光环伤害", SkillEffects.getComputedValue(player, Attributes.ATTACK_DAMAGE, rec), "%.1f");
        // 光环攻击频率 = 实际攻速属性 - 3（ATTACK_SPEED 基础 4.0 → 1 次/秒，100 级光环速度 = 20 次/秒）
        addRow(rows, "光环频率/秒", Math.max(0, SkillEffects.getComputedValue(player, Attributes.ATTACK_SPEED, rec) - 3), "%.1f");
        addRow(rows, "光环剑数", SkillEffects.getAuraSwordCount(rec), "%.0f");
        rows.add(new String[]{"技能点", String.format("%.1f", skillPoints), "#FFFFD700"});

        // 计算滚动范围并钳制
        int visible = panelVisibleRows();
        int maxScroll = Math.max(0, rows.size() - visible);
        panelScroll = Math.max(0, Math.min(maxScroll, panelScroll));

        // 绘制可见行（从 panelScroll 开始）
        int line = y + 18;
        for (int i = panelScroll; i < rows.size() && i < panelScroll + visible; i++) {
            String[] row = rows.get(i);
            String color = row.length > 2 ? row[2] : "#FFFFFFFF";
            int c = color.equals("#FFFFD700") ? 0xFFFFD700 : 0xFFFFFFFF;
            guiGraphics.drawString(font, row[0], x + 4, line, 0xFFAAAAAA);
            guiGraphics.drawString(font, row[1], x + PANEL_WIDTH - 44, line, c);
            line += 12;
        }
        // 滚动指示
        if (maxScroll > 0) {
            guiGraphics.drawString(font, "▼ 滚轮滚动", x + 4, height - 44, 0xFF888888);
        }
    }

    private void addRow(java.util.List<String[]> rows, String name, double value, String fmt) {
        rows.add(new String[]{name, String.format(fmt, value)});
    }

    private org.zifeng.skilltree.data.PlayerSkillRecord learnedAsRecord() {
        org.zifeng.skilltree.data.PlayerSkillRecord record = new org.zifeng.skilltree.data.PlayerSkillRecord(java.util.UUID.randomUUID());
        // 直接设置点数（不能用 learnSkill：AURA 消耗递增会因点数不足提前失败，导致光环永远只显示 1 级）
        learnedSkills.forEach(record::setLearnedPoints);
        toggles.forEach(record::setEnabled);
        activeLevels.forEach(record::setActiveLevel);
        return record;
    }

    // ============ 开关面板 ============

    private void renderTogglePanel(GuiGraphics guiGraphics) {
        guiGraphics.flush();
        int x = width - PANEL_WIDTH - 10;
        int y = height - 30;
        // 从下往上画：先画底部说明（guiOverlay 无条件覆盖下层）
        guiGraphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x - 2, y - 2, x + PANEL_WIDTH + 2, y + PANEL_WIDTH / 3, 0xCC101010);
        guiGraphics.drawCenteredString(font, "右键技能=开关", x + PANEL_WIDTH / 2, y + 2, 0xFFFFAA55);
    }

    // ============ 交互 ============

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (SkillButton skillButton : buttons) {
                if (skillButton.isHovered(mouseX, mouseY, this)) {
                    if (canLearn(skillButton.skillId())) {
                        // 乐观更新（杀戮光环扣消耗，普通扣1）
                        double cost = nextCostLocal(skillButton.skillId());
                        skillPoints -= cost;
                        learnedSkills.merge(skillButton.skillId(), 1, Integer::sum);
                        PacketDistributor.sendToServer(new LearnSkillC2SPacket(skillButton.skillId()));
                    }
                    return true;
                }
            }
        } else if (button == 1) {
            // 右键：切换技能开关；杀戮光环武器额外支持目标模式（Shift+右键）
            for (SkillButton skillButton : buttons) {
                if (skillButton.isHovered(mouseX, mouseY, this)) {
                    String skillId = skillButton.skillId();
                    if (Skills.AURA_WEAPON.equals(skillId) && isShiftHeld()) {
                        // 切换目标模式（本地乐观更新，重进时由服务端回发）
                        int mode = lastAuraMode;
                        lastAuraMode = (mode + 1) % 3;
                        PacketDistributor.sendToServer(new AuraTargetC2SPacket(lastAuraMode));
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

    private double nextCostLocal(String skillId) {
        if (Skills.ULT_FAVOR.equals(skillId)) return Skills.ULT_FAVOR_COST;
        if (Skills.NIGHT_VISION.equals(skillId) || Skills.SATURATION.equals(skillId)) return 100;
        Skills.SkillType type = Skills.getType(skillId);
        if (type == Skills.SkillType.AURA) {
            return Skills.getAuraCost(skillId, learnedSkills.getOrDefault(skillId, 0));
        }
        if (type == Skills.SkillType.BASE) return Skills.BASE_POINT_COST;
        if (type == Skills.SkillType.AMPLIFY) return Skills.AMPLIFY_POINT_COST;
        return 1;
    }

    private int lastAuraMode = 0;

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
        // 鼠标在右侧属性面板区域 → 滚动面板
        int px = width - PANEL_WIDTH - 10;
        if (mouseX >= px - 2 && mouseX <= px + PANEL_WIDTH + 2 && mouseY >= 48 && mouseY <= height - 32) {
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
