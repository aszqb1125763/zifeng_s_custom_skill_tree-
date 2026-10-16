package org.zifeng.skilltree.client;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * 输入事件构造工具（★ 26.1.2 移植新增）。
 *
 * <p><b>为什么需要它</b>：26.1.2 把 GUI 输入回调从基本类型改成了记录类型 ——
 * {@code mouseClicked(double,double,int)} → {@code mouseClicked(MouseButtonEvent, boolean)}、
 * {@code keyPressed(int,int,int)} → {@code keyPressed(KeyEvent)} 等。
 *
 * <p>绝大多数调用点都是「框架 → 屏幕」的方向，直接改成收记录即可。
 * 但技能树里还有一批**内部转发**：例如 {@code mouseClickedInner(double mouseX, double mouseY, int button, …)}
 * 会把手势转给子界面 / 搜索框 —— 这些方法手里只有拆开的坐标，需要把记录重新拼出来。
 *
 * <p>修饰键统一填 0：这些内部转发只关心「点了哪个键、点了哪里」，
 * 而修饰键在技能树里一律通过 {@code Minecraft#hasShiftDown()} 等实时查询（见 {@code isShiftHeld()}），
 * 不依赖事件里的快照值。
 */
public final class InputEvents {

    private InputEvents() {
    }

    /** 由拆开的坐标与按键号重建一次鼠标事件。 */
    public static MouseButtonEvent mouse(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    /** 由拆开的键号重建一次键盘事件。 */
    public static KeyEvent key(int keyCode, int scanCode, int modifiers) {
        return new KeyEvent(keyCode, scanCode, modifiers);
    }

    /** 由字符重建一次文本输入事件。 */
    public static CharacterEvent character(char codePoint) {
        return new CharacterEvent(codePoint);
    }
}
