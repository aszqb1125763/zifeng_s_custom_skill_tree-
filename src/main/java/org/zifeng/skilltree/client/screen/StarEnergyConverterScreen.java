package org.zifeng.skilltree.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.init.ModMenus;
import org.zifeng.skilltree.menu.StarEnergyConverterMenu;

/**
 * 星能转换机界面（纯代码绘制，无贴图）：
 * 显示进度条、已转换技能点、绑定状态、每点能量说明。
 */
public class StarEnergyConverterScreen extends AbstractContainerScreen<StarEnergyConverterMenu> {

    public StarEnergyConverterScreen(StarEnergyConverterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 110;
        this.inventoryLabelY = 10000; // 隐藏默认物品栏标签
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
        int barY = y + 28;
        int barW = imageWidth - 28;
        int barH = 12;
        guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        int fillW = barW * pct / 100;
        if (fillW > 0) {
            guiGraphics.fill(barX, barY, barX + fillW, barY + barH, Config.MACHINE_PROGRESS_COLOR.get());
        }
        guiGraphics.drawCenteredString(font, "能量转换进度: " + pct + "%", x + imageWidth / 2, barY + barH + 5, 0xFFFFFFFF);

        // 已转换技能点
        guiGraphics.drawCenteredString(font, "已转换技能点: " + menu.getTotalConverted(),
                x + imageWidth / 2, barY + barH + 22, 0xFFFFD700);

        // 绑定状态
        String bindText = menu.isBound() ? "✓ 已绑定放置者" : "✗ 未绑定（请重新放置）";
        int bindColor = menu.isBound() ? 0xFF55FF55 : 0xFFFF5555;
        guiGraphics.drawCenteredString(font, bindText, x + imageWidth / 2, barY + barH + 38, bindColor);

        // 说明
        guiGraphics.drawCenteredString(font,
                "每点技能点消耗: " + Config.ENERGY_PER_SKILL_POINT.get() + " FE · 能量中断进度清空",
                x + imageWidth / 2, barY + barH + 54, 0xFFAAAAAA);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.STAR_ENERGY_CONVERTER.get(), StarEnergyConverterScreen::new);
    }
}
