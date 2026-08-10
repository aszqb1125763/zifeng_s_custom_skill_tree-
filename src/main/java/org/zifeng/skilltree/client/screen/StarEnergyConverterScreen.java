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
import org.zifeng.skilltree.menu.StarEnergyConverterMenu;
import org.zifeng.skilltree.network.SetConverterRateC2SPacket;

/**
 * 星能转换机界面（纯代码绘制，无贴图）：
 * 显示进度条、已转换技能点、绑定状态、每点能量说明。
 * "当前每点消耗"下方提供：
 * <ul>
 *   <li>输入速率设置：点击弹出输入框，可自行输入每 tick 接收的 FE（0 = 关闭输入）</li>
 *   <li>红石控制开关：点击切换，开启后仅在有红石信号时接收能量</li>
 * </ul>
 */
public class StarEnergyConverterScreen extends AbstractContainerScreen<StarEnergyConverterMenu> {

    /** 进度条相对 y */
    private static final int BAR_Y = 28;
    private static final int BAR_H = 12;

    /** "当前每点消耗" 相对 y */
    private static final int COST_Y = BAR_Y + BAR_H + 54;

    /** 速率按钮：位于 "当前每点消耗" 下方 10 像素 */
    private static final int RATE_BTN_X = 14;
    private static final int RATE_BTN_Y = COST_Y + 10;
    private static final int RATE_BTN_W = 148;
    private static final int RATE_BTN_H = 18;

    /** 红石控制按钮：位于速率按钮下方 */
    private static final int REDSTONE_BTN_Y = RATE_BTN_Y + RATE_BTN_H + 4;
    private static final int REDSTONE_BTN_H = 14;

    /** 输入框（点击速率按钮后显示） */
    private EditBox rateInput;
    /** 是否显示输入框 */
    private boolean inputVisible = false;

    public StarEnergyConverterScreen(StarEnergyConverterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = REDSTONE_BTN_Y + REDSTONE_BTN_H + 6; // 容纳速率 + 红石按钮
        this.inventoryLabelY = 10000; // 隐藏默认物品栏标签
    }

    @Override
    public void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        // 创建输入框（初始隐藏，点击速率按钮后弹出）
        this.rateInput = new EditBox(font, x + RATE_BTN_X + 4, y + RATE_BTN_Y + 4, RATE_BTN_W - 8, 12,
                Component.literal("输入速率"));
        this.rateInput.setMaxLength(15);
        this.rateInput.setFilter(s -> s.matches("[0-9]*")); // 仅允许数字
        this.rateInput.setVisible(false);
        // 用原版组件管理，保证鼠标/键盘事件可用
        addRenderableWidget(rateInput);
    }

    private boolean inRateArea(double mouseX, double mouseY) {
        return mouseX >= leftPos + RATE_BTN_X && mouseX <= leftPos + RATE_BTN_X + RATE_BTN_W
                && mouseY >= topPos + RATE_BTN_Y && mouseY <= topPos + RATE_BTN_Y + RATE_BTN_H;
    }

    private boolean inRedstoneArea(double mouseX, double mouseY) {
        return mouseX >= leftPos + RATE_BTN_X && mouseX <= leftPos + RATE_BTN_X + RATE_BTN_W
                && mouseY >= topPos + REDSTONE_BTN_Y && mouseY <= topPos + REDSTONE_BTN_Y + REDSTONE_BTN_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inRedstoneArea(mouseX, mouseY)) {
                // 红石控制切换：保留当前速率，切换红石开关
                PacketDistributor.sendToServer(new SetConverterRateC2SPacket(
                        menu.getInputRatePerTick(), !menu.isRedstoneControlled()));
                return true;
            }
            if (inRateArea(mouseX, mouseY)) {
                if (!inputVisible) {
                    inputVisible = true;
                    rateInput.setVisible(true);
                    rateInput.setValue(String.valueOf(menu.getInputRatePerTick()));
                    rateInput.setFocused(true);
                    setFocused(rateInput);
                    return true;
                }
                // 已显示输入框：点击输入框自身 → 交回给 EditBox 处理聚焦/光标
                return rateInput.mouseClicked(mouseX, mouseY, button);
            }
            // 点击其他区域且输入框显示中 → 提交并关闭
            if (inputVisible) {
                submitRate();
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 输入框聚焦时：Enter 提交，Esc 取消
        if (inputVisible && rateInput.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter
                submitRate();
                return true;
            }
            if (keyCode == 256) { // Esc
                closeInput();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 提交当前输入框数值并关闭 */
    private void submitRate() {
        String text = rateInput.getValue().trim();
        closeInput();
        if (text.isEmpty()) {
            return; // 空 → 取消
        }
        long rate;
        try {
            rate = Long.parseLong(text);
        } catch (NumberFormatException e) {
            return; // 非法 → 取消
        }
        PacketDistributor.sendToServer(new SetConverterRateC2SPacket(rate, menu.isRedstoneControlled()));
    }

    /** 关闭输入框 */
    private void closeInput() {
        inputVisible = false;
        rateInput.setVisible(false);
        rateInput.setFocused(false);
        setFocused(null);
    }

    /** 格式化速率显示（FE/tick） */
    private String formatRate(long rate) {
        if (rate == 0) {
            return "关闭输入";
        }
        if (rate == Long.MAX_VALUE) {
            return "不限速";
        }
        if (rate >= 1_000_000) {
            return (rate / 1_000_000) + "M FE/tick";
        }
        if (rate >= 1_000) {
            return (rate / 1_000) + "k FE/tick";
        }
        return rate + " FE/tick";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

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
        int barY = y + BAR_Y;
        int barW = imageWidth - 28;
        guiGraphics.fill(barX, barY, barX + barW, barY + BAR_H, 0xFF333333);
        int fillW = barW * pct / 100;
        if (fillW > 0) {
            guiGraphics.fill(barX, barY, barX + fillW, barY + BAR_H, Config.MACHINE_PROGRESS_COLOR.get());
        }
        guiGraphics.drawCenteredString(font, "能量转换进度: " + pct + "%", x + imageWidth / 2, barY + BAR_H + 5, 0xFFFFFFFF);

        // 已转换技能点（玩家整体累计，跨机器共享，阶梯消耗依据）
        guiGraphics.drawCenteredString(font, "玩家累计已转换: " + menu.getTotalConverted(),
                x + imageWidth / 2, barY + BAR_H + 22, 0xFFFFD700);

        // 绑定状态
        String bindText = menu.isBound() ? "✓ 已绑定放置者" : "✗ 未绑定（请重新放置）";
        int bindColor = menu.isBound() ? 0xFF55FF55 : 0xFFFF5555;
        guiGraphics.drawCenteredString(font, bindText, x + imageWidth / 2, barY + BAR_H + 38, bindColor);

        // 说明：当前每点消耗（阶梯制，按玩家整体累计计算）
        guiGraphics.drawCenteredString(font,
                "当前每点消耗: " + menu.getCurrentCost() + " FE · 按玩家累计 · 能量中断进度清空",
                x + imageWidth / 2, y + COST_Y, 0xFFAAAAAA);

        // 输入速率按钮（位于当前每点消耗下方 10 像素）
        int btnY = y + RATE_BTN_Y;
        guiGraphics.fill(x + RATE_BTN_X, btnY, x + RATE_BTN_X + RATE_BTN_W, btnY + RATE_BTN_H, 0xFF1A2A3A);
        if (inputVisible) {
            guiGraphics.drawCenteredString(font, "[Enter 确认 · Esc 取消]",
                    x + imageWidth / 2, btnY + RATE_BTN_H + 1, 0xFFAAAAAA);
        } else {
            guiGraphics.drawCenteredString(font,
                    "输入速率: " + formatRate(menu.getInputRatePerTick()) + "  [点击设置]",
                    x + imageWidth / 2, btnY + 5, 0xFF4FC3F7);
        }

        // 红石控制按钮
        int rsY = y + REDSTONE_BTN_Y;
        boolean rs = menu.isRedstoneControlled();
        guiGraphics.fill(x + RATE_BTN_X, rsY, x + RATE_BTN_X + RATE_BTN_W, rsY + REDSTONE_BTN_H, 0xFF1A2A3A);
        guiGraphics.drawCenteredString(font,
                rs ? "红石控制: 开启（有信号才输入）  [点击关闭]" : "红石控制: 关闭（始终输入）  [点击开启]",
                x + imageWidth / 2, rsY + 3, 0xFF4FC3F7);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.STAR_ENERGY_CONVERTER.get(), StarEnergyConverterScreen::new);
    }
}
