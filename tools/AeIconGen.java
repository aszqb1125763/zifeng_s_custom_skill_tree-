import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 贴图生成器：程序化生成「无限回路」（AE 无限频道）技能图标 16x16。
 * 风格：AE2 主题——深紫水晶能量底 + 青色/亮青「∞」无限回路符号 + 频道节点光点。
 * <p>
 * 运行：java AeIconGen.java（JDK 21，工作目录 = 项目 tools/ 上一级）
 * 输出：src/main/resources/assets/zifeng_s_custom_skill_tree/textures/skill/ae_infinite_channel.png
 */
public class AeIconGen {

    // AE2 紫水晶能量色系
    static final int AMETHYST_DARK = 0xFF2A1440;   // 深紫底
    static final int AMETHYST_MID = 0xFF4A2A6A;    // 中紫
    static final int AMETHYST_LIGHT = 0xFF6E3FA0;  // 亮紫（能量水晶）
    // 频道能量青色（AE2 频道高亮色）
    static final int CHANNEL = 0xFF45E8FF;         // 亮青（频道能量）
    static final int CHANNEL_LIGHT = 0xFFB8F7FF;   // 白青高光
    static final int CHANNEL_DARK = 0xFF1FA8C8;    // 暗青
    static final int EDGE = 0xFF1A0F28;            // 边缘深紫

    public static void main(String[] args) throws Exception {
        File outDir = new File("src/main/resources/assets/zifeng_s_custom_skill_tree/textures/skill");
        outDir.mkdirs();
        ImageIO.write(icon(), "png", new File(outDir, "ae_infinite_channel.png"));
        System.out.println("图标已生成: " + outDir.getAbsolutePath());
    }

    static BufferedImage icon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // 深紫水晶底（对角渐变：左上亮紫 → 右下深紫）
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int t = Math.max(0, Math.min(15, (x + y) / 2));
                int c = t < 5 ? AMETHYST_LIGHT : (t < 10 ? AMETHYST_MID : AMETHYST_DARK);
                img.setRGB(x, y, c);
            }
        }
        // 边缘暗紫描边（1px）
        for (int x = 0; x < 16; x++) {
            img.setRGB(x, 0, EDGE);
            img.setRGB(x, 15, EDGE);
        }
        for (int y = 0; y < 16; y++) {
            img.setRGB(0, y, EDGE);
            img.setRGB(15, y, EDGE);
        }
        // 紫水晶簇小点（四角能量晶体）
        crystal(img, 2, 2);
        crystal(img, 13, 2);
        crystal(img, 2, 13);
        crystal(img, 13, 13);
        // 中央「∞」无限回路符号（横 8 字，双环）
        // 左环圆心 (5,8)，右环圆心 (11,8)，环半径 2.5，环壁厚约 1
        infinity(img, 5, 8, 11, 8);
        return img;
    }

    /** 小能量晶体（3x3 对角，右下高光） */
    static void crystal(BufferedImage img, int x, int y) {
        img.setRGB(x, y, CHANNEL_DARK);
        img.setRGB(x + 1, y, CHANNEL);
        img.setRGB(x, y + 1, CHANNEL);
        img.setRGB(x + 1, y + 1, CHANNEL_LIGHT);
    }

    /** 画 ∞ 符号：两个环（像素距离 1.5~3.2 为环），青色能量；两环间横向连接线加粗 */
    static void infinity(BufferedImage img, int c1x, int c1y, int c2x, int c2y) {
        for (int y = 2; y <= 13; y++) {
            for (int x = 1; x <= 14; x++) {
                double d1 = Math.hypot(x - c1x, y - c1y);
                double d2 = Math.hypot(x - c2x, y - c2y);
                // 环：1.5~3.2 环形带；两环之间（中央连接区）填色连成一体
                boolean inRing = (d1 >= 1.5 && d1 <= 3.2) || (d2 >= 1.5 && d2 <= 3.2);
                boolean inBridge = (x >= c1x + 1 && x <= c2x - 1 && Math.abs(y - c1y) <= 1);
                if (inRing || inBridge) {
                    double dd = Math.min(d1, d2);
                    // 桥中心/环内沿最亮，环外沿稍暗
                    int c = dd < 1.9 ? CHANNEL_LIGHT : (dd < 2.6 ? CHANNEL : CHANNEL_DARK);
                    img.setRGB(x, y, c);
                }
            }
        }
        // 环心暗青（能量核）
        img.setRGB(c1x, c1y, CHANNEL_DARK);
        img.setRGB(c1x, c1y - 1, CHANNEL_DARK);
        img.setRGB(c1x, c1y + 1, CHANNEL_DARK);
        img.setRGB(c2x, c2y, CHANNEL_DARK);
        img.setRGB(c2x, c2y - 1, CHANNEL_DARK);
        img.setRGB(c2x, c2y + 1, CHANNEL_DARK);
    }
}
