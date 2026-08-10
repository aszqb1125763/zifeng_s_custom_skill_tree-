package org.zifeng.skilltree.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.init.ModMenus;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;
import org.zifeng.skilltree.network.ConverterRateC2SPacket;
import org.zifeng.skilltree.network.ConverterUnlimitedC2SPacket;

/**
 * 技能点转换机界面（纯代码绘制，无贴图）：
 * 进度条 / 已转换 / 绑定 / 每点消耗 / 能量缓冲 / 红石状态。
 * 输入速率：专属数字输入框（EditBox，支持玩家自定义数字，回车提交）。
 * 无限制输入按钮：位于速率输入框下方，全部左对齐。
 */
public class SkillPointConverterScreen extends AbstractContainerScreen<SkillPointConverterMenu> {

    /** 无限制输入按钮区域（相对 GUI 左上角的局部坐标） */
    private int btnX = 0, btnY = 0, btnW = 130, btnH = 16;
    /** 输入速率编辑框（GUI 局部坐标区域，init 时创建） */
    private EditBox rateBox;
    private int rateBoxX = 0, rateBoxY = 0, rateBoxW = 120;
    /** 无限制开关客户端乐观状态（null = 跟随服务端；点击后立即本地翻转，等待同步） */
    private Boolean localUnlimited = null;
    /** 输入速率客户端乐观值（null = 跟随服务端；提交后立即生效显示，等待同步） */
    private Long localRate = null;

    /** 当前无限制状态（乐观值优先，否则服务端同步值） */
    private boolean currentUnlimited() {
        return localUnlimited != null ? localUnlimited : menu.isUnlimitedInput();
    }

    public SkillPointConverterScreen(SkillPointConverterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 196;
        this.imageHeight = 190;
        this.inventoryLabelY = 10000; // 隐藏默认物品栏标签
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        // 输入速率编辑框（GUI 局部坐标）：只允许数字输入
        rateBoxX = x + 14;
        rateBoxY = y + 118;
        rateBoxW = 120;
        rateBox = new EditBox(font, rateBoxX, rateBoxY, rateBoxW, 14, Component.literal("输入速率"));
        rateBox.setValue(String.valueOf(menu.getInputRate()));
        rateBox.setMaxLength(13);
        rateBox.setFilter(s -> s.matches("\\d*")); // 仅数字
        addRenderableWidget(rateBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // 深色面板 + 淡蓝边框
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xF0101010);
        guiGraphics.fill(x, y, x + imageWidth, y + 2, 0xFF87CEEB);
        guiGraphics.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, 0xFF87CEEB);
        guiGraphics.fill(x, y, x + 2, y + imageHeight, 0xFF87CEEB);
        guiGraphics.fill(x + imageWidth - 2, y, x + imageWidth, y + imageHeight, 0xFF87CEEB);

        guiGraphics.drawCenteredString(font, title.getString(), x + imageWidth / 2, y + 8, 0xFFFFFFFF);

        // 进度条
        int pct = menu.getProgressPercent();
        int barX = x + 14;
        int barY = y + 26;
        int barW = imageWidth - 28;
        int barH = 12;
        guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        int fillW = barW * pct / 100;
        if (fillW > 0) {
            guiGraphics.fill(barX, barY, barX + fillW, barY + barH, Config.MACHINE_PROGRESS_COLOR.get());
        }
        guiGraphics.drawCenteredString(font, "能量转换进度: " + pct + "%", x + imageWidth / 2, barY + barH + 5, 0xFFFFFFFF);

        int line = barY + barH + 19;
        int lineH = 11;
        int left = x + 14;
        // 已转换技能点（玩家整体累计，跨机器共享，阶梯消耗依据）
        guiGraphics.drawString(font, "玩家累计已转换: " + fmtBig(menu.getTotalConverted()) + " 点", left, line, 0xFFFFD700);
        line += lineH;
        // 绑定状态
        String bindText = menu.isBound() ? "✓ 已绑定放置者" : "✗ 未绑定（请重新放置）";
        int bindColor = menu.isBound() ? 0xFF55FF55 : 0xFFFF5555;
        guiGraphics.drawString(font, bindText, left, line, bindColor);
        line += lineH;
        // 红石状态
        String redstoneText = menu.isRedstoneBlocked() ? "⛔ 红石激活: 机器已关闭" : "✓ 红石未激活: 机器运行中";
        guiGraphics.drawString(font, redstoneText, left, line, menu.isRedstoneBlocked() ? 0xFFFF5555 : 0xFF55FF55);
        line += lineH + 2;

        // 输入速率标签 + 数字输入框（EditBox，回车提交；无限制输入开启时显示无限制）
        guiGraphics.drawString(font, "输入速率 (FE/t):", left, line, 0xFFFFD700);
        line += lineH + 2;
        // 刷新编辑框位置与当前值（菜单数据变化时同步；本地乐观值优先，避免同步延迟导致输入被重置）
        if (rateBox != null) {
            rateBoxX = left;
            rateBoxY = line;
            rateBox.setX(rateBoxX);
            rateBox.setY(rateBoxY);
            // 仅在未聚焦时不覆盖用户输入
            if (!rateBox.isFocused()) {
                long cur = localRate != null ? localRate : menu.getInputRate();
                rateBox.setValue(String.valueOf(cur));
            }
        }
        line += 18;

        // 无限制输入按钮（位于速率输入框下方，左对齐；默认关闭）
        btnX = left;
        btnY = line;
        btnW = imageWidth - 28;
        btnH = 16;
        boolean unlimited = currentUnlimited();
        int btnBg = unlimited ? 0xFF1E7A4A : 0xFF3A3A3A;
        int btnBorder = unlimited ? 0xFF55FFAA : 0xFF888888;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + 1, btnBorder);
        guiGraphics.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, btnBorder);
        guiGraphics.fill(btnX, btnY, btnX + 1, btnY + btnH, btnBorder);
        guiGraphics.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, btnBorder);
        // 开启时左侧绿色状态圆点（明显变化）
        if (unlimited) {
            guiGraphics.fill(btnX + 4, btnY + 6, btnX + 9, btnY + 11, 0xFF55FFAA);
        } else {
            guiGraphics.fill(btnX + 4, btnY + 6, btnX + 9, btnY + 11, 0xFF666666);
        }
        String btnText = unlimited ? "✓ 无限制输入: 开启（点击关闭）" : "无限制输入: 关闭（点击开启）";
        guiGraphics.drawString(font, btnText, btnX + 13, btnY + 3, unlimited ? 0xFF55FFAA : 0xFFAAAAAA);
        // 当前生效输入速率（服务端同步实际值）
        line += btnH + 3;
        String rateText = unlimited ? "当前速率: 无限制"
                : "当前速率: " + fmtBig(menu.getInputRate()) + " FE/t";
        guiGraphics.drawString(font, rateText, left, line, unlimited ? 0xFF55FFAA : 0xFFFFD700);
    }

    /** 提交输入速率（回车/失焦调用）：解析数字并发送 C2S */
    private void applyRateInput() {
        if (rateBox == null) {
            return;
        }
        String text = rateBox.getValue().trim();
        if (text.isEmpty()) {
            // 空值恢复为当前
            rateBox.setValue(String.valueOf(menu.getInputRate()));
            return;
        }
        try {
            long rate = Long.parseLong(text);
            if (rate < 1) {
                rate = 1;
            }
            if (rate > 1_000_000_000_000L) {
                rate = 1_000_000_000_000L;
            }
            localRate = rate; // 本地乐观生效（立即显示），服务端同步后覆盖
            PacketDistributor.sendToServer(new ConverterRateC2SPacket(menu.getBlockPos(), rate));
            rateBox.setValue(String.valueOf(rate));
        } catch (NumberFormatException ignored) {
            // 非法输入恢复
            rateBox.setValue(String.valueOf(localRate != null ? localRate : menu.getInputRate()));
        }
    }

    /** 大数字缩写显示：≥1京→"X.XX京"，≥1万亿→"X.XX万亿"，≥1亿→"X.XX亿"，≥1万→"X.XX万" */
    private static String fmtBig(long value) {
        if (value >= 1_0000_0000_0000_0000L) { // 1 京 = 1e16
            return String.format("%.2f京", value / 1_0000_0000_0000_0000.0);
        }
        if (value >= 1_000_000_000_000L) {
            return String.format("%.2f万亿", value / 1_000_000_000_000.0);
        }
        if (value >= 100_000_000L) {
            return String.format("%.2f亿", value / 100_000_000.0);
        }
        if (value >= 10_000L) {
            return String.format("%.1f万", value / 10_000.0);
        }
        return String.valueOf(value);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 回车（Enter / 小键盘 Enter）提交输入速率
        if (rateBox != null && rateBox.isFocused()
                && (keyCode == 257 || keyCode == 335)) {
            applyRateInput();
            rateBox.setFocused(false); // 提交后失焦，显示确认
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点击无限制输入按钮 → 本地乐观翻转 + 发送切换包
        // ⚠️ btnX/btnY 已是屏幕坐标（renderBg 中 = x+14 / line），判定直接用，不要再加 x/y 偏移
        if (button == 0 && mouseX >= btnX && mouseX <= btnX + btnW
                && mouseY >= btnY && mouseY <= btnY + btnH) {
            boolean next = !currentUnlimited();
            localUnlimited = next; // 立即本地切换，视觉马上变化
            PacketDistributor.sendToServer(new ConverterUnlimitedC2SPacket(menu.getBlockPos(), next));
            return true;
        }
        // 点击编辑框外部 → 提交输入并失焦
        if (rateBox != null && rateBox.isFocused() && button == 0
                && !(mouseX >= rateBoxX && mouseX <= rateBoxX + rateBoxW && mouseY >= rateBoxY && mouseY <= rateBoxY + 14)) {
            applyRateInput();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        // 关闭界面时提交未保存的输入速率
        if (rateBox != null && rateBox.isFocused()) {
            applyRateInput();
        }
        super.removed();
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SKILL_POINT_CONVERTER.get(), SkillPointConverterScreen::new);
    }
}
