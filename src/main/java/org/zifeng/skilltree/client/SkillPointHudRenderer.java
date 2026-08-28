package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技能点左下角 HUD（2026-08-25 动画版）：
 * <ul>
 *   <li>三类行：通用变动（⚡技能点）、馈赠（🎁/⏳）、转换机（⚡星能转换机），
 *       全部 1 秒无更新即消失</li>
 *   <li>覆盖动画（符合常规 UX）：新值到来时——旧行淡出下沉 + 新行从上方滑入放大
 *       （scale 0.8→1.0 + 位移，300ms 缓动），颜色同步从高亮过渡到常色</li>
 *   <li>总技能点绿色常驻，锚定固定位置</li>
 * </ul>
 */
public class SkillPointHudRenderer {

    /** 单行状态（值 + 出现时间 + 是否刚更新过） */
    private static final class Line {
        double value;
        long time;      // 最后一次更新（毫秒）
        boolean fresh;  // 刚更新（动画播放中）

        Line(double value) {
            this.value = value;
            this.time = System.currentTimeMillis();
            this.fresh = true;
        }
    }

    /** 来源 → 行状态（覆盖式：同来源更新覆盖，无更新 1 秒消失） */
    private static final Map<String, Line> lines = new LinkedHashMap<>();

    /** 总技能点（绿色常驻） */
    private static double totalSkillPoints = 0;
    /** HUD 显示开关 */
    private static boolean hudVisible = true;

    /** 位置偏移（持久化） */
    private static int hudOffsetX = 0;
    private static int hudOffsetY = 0;
    /** 基准点 */
    private static final int BASE_X = 4 + 10;
    private static final int BASE_Y_OFFSET = -115 + 70 + 15;

    /** 行消失时长：1 秒（馈赠/通用） */
    private static final long LIFETIME_MS = 1000;
    /** 转换机行消失时长：5 秒（恒定速率，5 秒无技能点增加才隐藏，2026-08-25） */
    private static final long CONVERTER_LIFETIME_MS = 5000;
    /** 覆盖动画时长：400ms（2026-08-25 流畅版） */
    private static final long ANIM_MS = 400;

    static {
        org.zifeng.skilltree.client.SkillKeyBinds.load();
        hudOffsetX = org.zifeng.skilltree.client.SkillKeyBinds.getHudOffsetX();
        hudOffsetY = org.zifeng.skilltree.client.SkillKeyBinds.getHudOffsetY();
    }

    private static final int COLOR_GIFT = 0xFFE0B6C8;
    private static final int COLOR_CONVERTER = 0xFF9AC8FF;
    private static final int COLOR_TOTAL = 0xFF55FF55;
    private static final int COLOR_DECREASE = 0xFFFF5555;

    /** 服务端速率包（馈赠/转换专属行） */
    public static void updateRates(double total, Map<String, Double> newRates) {
        totalSkillPoints = total;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Double> e : newRates.entrySet()) {
            if (e.getValue() <= 0) {
                continue;
            }
            boolean converter = e.getKey().contains("转换");
            Line line = lines.get(e.getKey());
            if (line == null) {
                // 转换机：无动画常驻；馈赠：初始出现（带动画）
                lines.put(e.getKey(), new Line(e.getValue()));
                if (converter) {
                    lines.get(e.getKey()).fresh = false; // 转换机不播放出现动画
                }
            } else {
                if (converter) {
                    // 转换机：仅更新值（恒定速率，平滑更新不闪动）
                    line.value = e.getValue();
                    line.time = now;
                } else {
                    // ⚠️ 馈赠（2026-08-25）：同源 1 秒内累加（大范围秒杀连续触发显示累计），
                    //    超过 1 秒新值覆盖（单次激活）
                    if (now - line.time < 1000) {
                        line.value += e.getValue(); // 1 秒内合并
                    } else {
                        line.value = e.getValue(); // 覆盖
                    }
                    line.time = now;
                    line.fresh = true;
                }
            }
        }
    }

    /** 技能数据包同步（通用变动：仅扣点显示，增加由专属行覆盖） */
    public static void updateTotal(double total) {
        // 首次同步建立基准
        if (totalSkillPoints <= 0 && Math.abs(total) > 1e-9 && lines.isEmpty()) {
            totalSkillPoints = total;
            return;
        }
        double delta = total - totalSkillPoints;
        if (delta < -1e-9) {
            // 扣点：通用行显示（红色）
            putLine("⚡技能点", delta);
        } else if (delta > 1e-9) {
            // 增加：专属行已显示，不重复；清通用行
            lines.remove("⚡技能点");
        }
        totalSkillPoints = total;
    }

    /** 通用行（扣点）更新 */
    private static void putLine(String source, double value) {
        long now = System.currentTimeMillis();
        Line line = lines.get(source);
        if (line == null) {
            lines.put(source, new Line(value));
        } else {
            line.value += value; // 通用行同 1 秒内合并
            line.time = now;
            line.fresh = true;
        }
    }

    public static void onDisconnect() {
        totalSkillPoints = 0;
        lines.clear();
    }

    public static void setVisible(boolean visible) {
        hudVisible = visible;
    }

    public static boolean isVisible() {
        return hudVisible;
    }

    public static int getHudOffsetX() {
        return hudOffsetX;
    }

    public static int getHudOffsetY() {
        return hudOffsetY;
    }

    public static void adjustOffsetX(int delta) {
        hudOffsetX = Math.max(-800, Math.min(800, hudOffsetX + delta));
        saveOffset();
    }

    public static void adjustOffsetY(int delta) {
        hudOffsetY = Math.max(-400, Math.min(400, hudOffsetY + delta));
        saveOffset();
    }

    /** 重置位置偏移（X/Y 归零） */
    public static void resetOffset() {
        hudOffsetX = 0;
        hudOffsetY = 0;
        saveOffset();
    }

    private static void saveOffset() {
        org.zifeng.skilltree.client.SkillKeyBinds.setHudOffset(hudOffsetX, hudOffsetY);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!hudVisible) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        GuiGraphics gui = event.getGuiGraphics();
        int height = mc.getWindow().getGuiScaledHeight();
        int x = BASE_X + hudOffsetX;
        int totalY = height + BASE_Y_OFFSET + hudOffsetY;
        long now = System.currentTimeMillis();

        // 1. 清理过期行（馈赠/通用 1 秒；转换机 5 秒无增加隐藏）
        lines.entrySet().removeIf(e -> {
            boolean converter = e.getKey().contains("转换");
            long limit = converter ? CONVERTER_LIFETIME_MS : LIFETIME_MS;
            return now - e.getValue().time > limit;
        });

        // 2. 总技能点（绿色固定，先画——速率行向上堆叠不推它）
        gui.drawString(mc.font, "技能点：" + String.format("%.1f", totalSkillPoints), x, totalY, COLOR_TOTAL);

        // 3. 速率行从总数行往上排列（动画：新值滑入放大）
        int rateY = totalY - 10;
        for (Map.Entry<String, Line> e : lines.entrySet()) {
            Line line = e.getValue();
            String source = e.getKey();
            String text;
            int color;
            if (source.contains("转换")) {
                text = source + " +" + String.format("%.3f", line.value) + "/秒";
                color = COLOR_CONVERTER;
            } else if (source.contains("⚡")) {
                String sign = line.value >= 0 ? "+" : "";
                text = source + " " + sign + String.format("%.1f", line.value) + "点";
                color = line.value >= 0 ? COLOR_CONVERTER : COLOR_DECREASE;
            } else {
                text = source + " +" + String.format("%.0f", line.value) + "点";
                color = COLOR_GIFT;
            }

            // 覆盖动画（2026-08-25 流畅版）：400ms easeOutQuart —— 滑入 + 放大 + 淡入 + 高亮色渐变
            float anim = 1.0f;
            if (line.fresh) {
                long age = now - line.time;
                if (age < ANIM_MS) {
                    anim = (float) age / ANIM_MS; // 0→1
                } else {
                    line.fresh = false; // 动画结束
                }
            }
            // easeOutQuart（比 cubic 更柔和的减速，结尾不突兀）
            float t = 1.0f - anim;
            float ease = 1.0f - t * t * t * t;
            // 位移：从 12px 滑入（前 40% 完成大部分移动，结尾细微）
            int slideX = (int) (12 * (1.0f - ease));
            // 缩放：0.92 → 1.0（轻微放大，不夸张）
            float scale = 0.92f + 0.08f * ease;
            // 透明度淡入：0 → 255（前 30% 快速淡入，避免生硬）
            float alphaT = Math.min(1.0f, anim / 0.3f);
            int alpha = (int) (255 * alphaT);
            // 颜色过渡：动画期高亮 → 常色（同步渐变）
            int bright = source.contains("转换") ? 0xFFFFFFFF : 0xFFFF9AC8;
            int baseColor = anim < 1.0f ? lerpColor(color, bright, ease) : color;
            int curColor = (alpha << 24) | (baseColor & 0xFFFFFF);

            gui.pose().pushPose();
            gui.pose().translate(x + slideX, rateY, 0);
            gui.pose().scale(scale, scale, 1.0f);
            gui.drawString(mc.font, text, 0, 0, curColor);
            gui.pose().popPose();
            rateY -= 10;
            if (rateY < 10) {
                break;
            }
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int a = (int) (((from >> 24 & 0xFF) + ((to >> 24 & 0xFF) - (from >> 24 & 0xFF)) * t));
        int r = (int) (((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t));
        int g = (int) (((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t));
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
