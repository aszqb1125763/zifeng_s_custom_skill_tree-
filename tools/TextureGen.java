import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 贴图生成器：程序化生成"星能转换机"与"创造能量方块"的 16x16 方块贴图。
 * 风格：原版熔炉轮廓 + 铁块亮边框 + 红石能量线条（杂交）。
 * <p>
 * 运行：java TextureGen.java（JDK 21，工作目录任意）
 * 输出：src/main/resources/assets/zifeng_s_custom_skill_tree/textures/block/*.png
 */
public class TextureGen {

    static final int IRON_LIGHT = 0xFFD8D8D8;
    static final int IRON_MID = 0xFFC0C0C0;
    static final int IRON_DARK = 0xFF9A9A9A;
    static final int FURNACE_LIGHT = 0xFF9C8A6E;
    static final int FURNACE_MID = 0xFF8A7A62;
    static final int FURNACE_DARK = 0xFF6E6150;
    static final int REDSTONE = 0xFFC03030;
    static final int REDSTONE_LIGHT = 0xFFE04040;
    static final int REDSTONE_DARK = 0xFFA02020;
    static final int HOLE = 0xFF1A1A1A;

    public static void main(String[] args) throws Exception {
        File outDir = new File("src/main/resources/assets/zifeng_s_custom_skill_tree/textures/block");
        outDir.mkdirs();

        write(outDir, "star_energy_converter_front", front());
        write(outDir, "star_energy_converter_side", side());
        write(outDir, "star_energy_converter_top", top());
        write(outDir, "star_energy_converter_bottom", bottom());
        write(outDir, "creative_energy_block", creative());

        System.out.println("贴图已生成到: " + outDir.getAbsolutePath());
    }

    static void write(File dir, String name, BufferedImage img) throws Exception {
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    static BufferedImage front() {
        BufferedImage img = base();
        // 熔炉主体渐变（棕色系）
        for (int y = 2; y < 14; y++) {
            int c = y < 6 ? FURNACE_LIGHT : (y < 10 ? FURNACE_MID : FURNACE_DARK);
            for (int x = 1; x < 15; x++) {
                img.setRGB(x, y, c);
            }
        }
        // 中央熔炉嘴（黑口）
        for (int y = 6; y < 10; y++) {
            for (int x = 5; x < 11; x++) {
                img.setRGB(x, y, HOLE);
            }
        }
        // 嘴内红石微光
        for (int y = 7; y < 9; y++) {
            for (int x = 6; x < 10; x++) {
                img.setRGB(x, y, REDSTONE_DARK);
            }
        }
        // 底部红石能量线（两行）
        for (int x = 2; x < 14; x++) {
            img.setRGB(x, 13, REDSTONE);
        }
        for (int x = 2; x < 14; x++) {
            img.setRGB(x, 14, REDSTONE_DARK);
        }
        // 红石粉点缀（随机感但固定）
        img.setRGB(3, 3, REDSTONE_LIGHT);
        img.setRGB(12, 4, REDSTONE_LIGHT);
        img.setRGB(2, 11, REDSTONE_LIGHT);
        img.setRGB(13, 12, REDSTONE_LIGHT);
        // 铁块边框（外圈）
        border(img, IRON_LIGHT);
        return img;
    }

    static BufferedImage side() {
        BufferedImage img = base();
        // 主体熔炉棕
        for (int y = 2; y < 14; y++) {
            int c = y < 5 ? FURNACE_LIGHT : (y < 9 ? FURNACE_MID : FURNACE_DARK);
            for (int x = 1; x < 15; x++) {
                img.setRGB(x, y, c);
            }
        }
        // 中部铁块横带（模拟铁块加固）
        for (int x = 1; x < 15; x++) {
            img.setRGB(x, 7, IRON_MID);
            img.setRGB(x, 8, IRON_DARK);
        }
        // 铁块铆钉
        img.setRGB(4, 7, IRON_LIGHT);
        img.setRGB(11, 7, IRON_LIGHT);
        // 底部红石线
        for (int x = 2; x < 14; x++) {
            img.setRGB(x, 13, REDSTONE);
        }
        // 边框
        border(img, IRON_LIGHT);
        return img;
    }

    static BufferedImage top() {
        BufferedImage img = base();
        // 熔炉顶：深色渐变
        for (int y = 2; y < 14; y++) {
            for (int x = 1; x < 15; x++) {
                img.setRGB(x, y, y < 8 ? FURNACE_DARK : 0xFF5A4F40);
            }
        }
        // 中心红石能量核心（3x3 发光）
        for (int y = 6; y <= 8; y++) {
            for (int x = 6; x <= 8; x++) {
                img.setRGB(x, y, REDSTONE_LIGHT);
            }
        }
        img.setRGB(7, 7, 0xFFFF6060); // 核心最亮点
        // 边框
        border(img, IRON_LIGHT);
        return img;
    }

    static BufferedImage bottom() {
        BufferedImage img = base();
        // 纯铁块底
        for (int y = 1; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                img.setRGB(x, y, IRON_MID);
            }
        }
        // 铁块暗影
        for (int y = 10; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                img.setRGB(x, y, IRON_DARK);
            }
        }
        // 铆钉
        img.setRGB(4, 4, IRON_LIGHT);
        img.setRGB(11, 4, IRON_LIGHT);
        img.setRGB(4, 11, IRON_LIGHT);
        img.setRGB(11, 11, IRON_LIGHT);
        border(img, IRON_LIGHT);
        return img;
    }

    static BufferedImage creative() {
        BufferedImage img = base();
        // 深红黑底
        for (int y = 1; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                img.setRGB(x, y, 0xFF2A1010);
            }
        }
        // 中心能量核心（4x4，图案中心堆成）
        for (int y = 6; y <= 9; y++) {
            for (int x = 6; x <= 9; x++) {
                img.setRGB(x, y, REDSTONE_LIGHT);
            }
        }
        // 核心内部高亮（2x2）
        img.setRGB(7, 7, 0xFFFF8080);
        img.setRGB(8, 7, 0xFFFF8080);
        img.setRGB(7, 8, 0xFFFF8080);
        img.setRGB(8, 8, 0xFFFF8080);
        // 核心外圈辐射环（菱形，上下左右对称）
        img.setRGB(5, 5, REDSTONE_DARK);
        img.setRGB(10, 5, REDSTONE_DARK);
        img.setRGB(5, 10, REDSTONE_DARK);
        img.setRGB(10, 10, REDSTONE_DARK);
        // 四向能量纹（中心十字对称）
        img.setRGB(7, 4, REDSTONE);
        img.setRGB(7, 11, REDSTONE);
        img.setRGB(4, 7, REDSTONE);
        img.setRGB(11, 7, REDSTONE);
        // 四角小点缀（对称）
        img.setRGB(3, 3, REDSTONE_DARK);
        img.setRGB(12, 3, REDSTONE_DARK);
        img.setRGB(3, 12, REDSTONE_DARK);
        img.setRGB(12, 12, REDSTONE_DARK);
        // 暗铁边框
        border(img, 0xFF4A3A3A);
        return img;
    }

    static BufferedImage base() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // 默认底色（防透明）
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, 0xFF000000);
            }
        }
        return img;
    }

    static void border(BufferedImage img, int color) {
        for (int x = 0; x < 16; x++) {
            img.setRGB(x, 0, color);
            img.setRGB(x, 15, color);
        }
        for (int y = 0; y < 16; y++) {
            img.setRGB(0, y, color);
            img.setRGB(15, y, color);
        }
        // 内圈暗影增强立体感
        for (int x = 1; x < 15; x++) {
            img.setRGB(x, 1, IRON_DARK);
            img.setRGB(x, 14, IRON_DARK);
        }
        for (int y = 1; y < 15; y++) {
            img.setRGB(1, y, IRON_DARK);
            img.setRGB(14, y, IRON_DARK);
        }
    }
}
