import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 技能「已关闭」角标贴图生成器（2026-09-20）。
 *
 * <p>为什么做成贴图而不是字形：原先用「⊘」(U+2298) 通过 {@code drawString} 绘制，
 * 该字形来自 Unifont 回退（点阵、形状糙），且受玩家资源包影响，形状也做不出描边。
 * 改成贴图后：形状可控（标准禁止符：圆环 + 斜杠）、不受字体包影响、像素风格与 UI 统一。
 *
 * <p>规格：12×12 RGBA，透明底，亮红禁止符 + 暗红外描边。
 * <p>用 4× 超采样抗锯齿（MC 是最近邻放大，边缘必须自带平滑否则放大后锯齿明显）。
 *
 * <h2>⚠️ 两个反直觉的坑（都踩过）</h2>
 * <b>坑 1：描边不能「各分量向外扩」</b>。第一版用 {@code r <= OUT_R + PAD && r >= IN_R - PAD}，
 * 结果把圆环<b>向内</b>也扩了，而圆环内径只有 3.4px —— 中间的洞几乎被描边吃光，
 * 成品变成「实心圆 + 细缝」，完全不是禁止符该有的样子。
 * <p>正解：用带符号距离场（SDF），{@code d <= 0} 为主体、{@code 0 < d <= EDGE_W} 才描边。
 *
 * <p><b>坑 2：12px 尺度下描边必须很细（≤ 0.3px）</b>。改成 SDF 后仍发现洞是闭合的 ——
 * 因为描边的膨胀会把「圆环内缘」与「斜杠」之间那条窄缝<b>桥接</b>掉：
 * 环内缘向内 0.75 + 斜杠向外 1.06 已经重叠。所以这里把环做薄（1.5px）、洞做大（半径 3.55px）、
 * 描边收到 0.3px。小尺寸图标里「描边宽度」是生死线，不能照搬大图的比例。
 *
 * <p>运行：javac + java（JDK 17+），工作目录 = 项目根
 * 输出：src/main/resources/assets/zifeng_s_custom_skill_tree/textures/ui/skill_disabled.png
 */
public class SkillDisabledIconGen {

    /** 输出尺寸（px） */
    static final int SIZE = 12;
    /** 超采样倍率（抗锯齿） */
    static final int SS = 4;

    /** 主体亮红（与 SkillTreeScreen.C_DISABLED_MARK = 0xFFFF3B3B 一致） */
    static final int RED = 0xFF3B3B;
    /** 描边暗红（保证在浅色贴片上也分得出边界） */
    static final int EDGE = 0xA81C1C;
    /** 描边宽度（px）—— ⚠️ 12px 图标必须很细，否则会把洞封住（见类注释坑 2） */
    static final double EDGE_W = 0.3;

    /** 圆心（画布 12×12，中心 6,6） */
    static final double CX = SIZE / 2.0;
    static final double CY = SIZE / 2.0;
    /** 圆环中心线半径 */
    static final double R_MID = 4.3;
    /** 圆环半厚（环厚 = 2 × R_HALF = 1.5px，洞半径 = R_MID - R_HALF = 3.55px） */
    static final double R_HALF = 0.75;
    /** 斜杠半宽 */
    static final double BAR_HALF = 0.75;
    /** 斜杠端点：沿 45° 到圆环中心线（× 1/√2 是 45° 投影），再加一点重叠量 */
    static final double BAR_LEN = R_MID / Math.sqrt(2.0) + 0.2;

    public static void main(String[] args) throws Exception {
        File outDir = new File("src/main/resources/assets/zifeng_s_custom_skill_tree/textures/ui");
        outDir.mkdirs();
        File out = new File(outDir, "skill_disabled.png");
        ImageIO.write(gen(), "png", out);
        System.out.println("已生成: " + out.getAbsolutePath());
    }

    /** 点到线段的距离 */
    static double distToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax;
        double vy = by - ay;
        double wx = px - ax;
        double wy = py - ay;
        double len2 = vx * vx + vy * vy;
        double t = len2 <= 1e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, (wx * vx + wy * vy) / len2));
        double qx = ax + t * vx;
        double qy = ay + t * vy;
        return Math.hypot(px - qx, py - qy);
    }

    /**
     * 禁止符 SDF：返回点到「圆环 ∪ 斜杠」的最短距离（负值 = 形状内，正值 = 形状外）。
     * <p>两个子形状各取「距离 - 半厚」，再取最小值 = 并集 SDF。
     */
    static double sdf(double x, double y) {
        double r = Math.hypot(x - CX, y - CY);
        // 圆环：到中心线圆的距离 - 半厚
        double ring = Math.abs(r - R_MID) - R_HALF;
        // 斜杠：左上→右下 45°（标准禁止符方向）
        double bar = distToSegment(x, y, CX - BAR_LEN, CY - BAR_LEN, CX + BAR_LEN, CY + BAR_LEN) - BAR_HALF;
        return Math.min(ring, bar);
    }

    static BufferedImage gen() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        final int per = SS * SS;

        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                int coreHit = 0;
                int edgeHit = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        double x = px + (sx + 0.5) / SS;
                        double y = py + (sy + 0.5) / SS;
                        double d = sdf(x, y);
                        if (d <= 0) {
                            coreHit++;
                        } else if (d <= EDGE_W) {
                            edgeHit++;
                        }
                    }
                }
                if (coreHit == 0 && edgeHit == 0) {
                    img.setRGB(px, py, 0x00000000);
                    continue;
                }
                // 主体覆盖优先，剩余覆盖率用描边色补 → 边缘自然过渡
                double coreA = (double) coreHit / per;
                double edgeA = (double) edgeHit / per;
                double total = Math.min(1.0, coreA + edgeA);
                double t = (coreA + edgeA) <= 0 ? 0 : coreA / (coreA + edgeA);
                int r = (int) Math.round(lerp((EDGE >> 16) & 0xFF, (RED >> 16) & 0xFF, t));
                int g = (int) Math.round(lerp((EDGE >> 8) & 0xFF, (RED >> 8) & 0xFF, t));
                int b = (int) Math.round(lerp(EDGE & 0xFF, RED & 0xFF, t));
                int a = (int) Math.round(total * 255);
                img.setRGB(px, py, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
