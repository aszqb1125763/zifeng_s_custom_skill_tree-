package org.zifeng.skilltree.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.zifeng.skilltree.SkillTreeMod;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 技能独立开关快捷键绑定 + 技能树界面位置/缩放持久化（客户端本地，存 config 目录 JSON）。
 * <ul>
 *   <li>每个技能可单独绑定一个开关快捷键（默认空键，在技能树界面点击技能后的 🔑 按钮设置）</li>
 *   <li>技能树界面退出时记录 panX/panY/scale，下次打开恢复到上次位置（2026-08-13 需求）</li>
 *   <li>存储文件：config/zifeng_s_custom_skill_tree_client.json</li>
 * </ul>
 */
public class SkillKeyBinds {

    /** 技能ID → 按键（KEYSYM，未绑定则无条目） */
    private static final Map<String, InputConstants.Key> BINDS = new HashMap<>();
    /** 技能树界面 panX / panY / scale（上次退出时的状态） */
    private static double lastPanX = 0;
    private static double lastPanY = 0;
    private static double lastScale = 1.0;

    private static boolean loaded = false;

    // ============ 存取 ============

    public static InputConstants.Key getKey(String skillId) {
        return BINDS.get(skillId);
    }

    public static boolean hasKey(String skillId) {
        return BINDS.containsKey(skillId);
    }

    /** 设置绑定（key 为 null/UNKNOWN 视为清除） */
    public static void setKey(String skillId, InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) {
            BINDS.remove(skillId);
        } else {
            BINDS.put(skillId, key);
        }
        save();
    }

    /** 清除绑定 */
    public static void clearKey(String skillId) {
        BINDS.remove(skillId);
        save();
    }

    /** 上次 tick 各键按下状态（边沿检测：按下瞬间返回 true 一次） */
    private static final java.util.Set<String> lastPressed = new java.util.HashSet<>();

    /** 边沿检测：该技能绑定键本 tick 是否刚按下（自动更新状态） */
    public static boolean consumeClick(String skillId) {
        InputConstants.Key key = BINDS.get(skillId);
        if (key == null || key.getValue() < 0) {
            return false;
        }
        boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        boolean prev = lastPressed.contains(skillId);
        if (down && !prev) {
            lastPressed.add(skillId);
            return true;
        }
        if (!down) {
            lastPressed.remove(skillId);
        }
        return false;
    }

    /** 按住检测：返回该技能绑定键当前是否被按住 */
    public static boolean isKeyDown(String skillId) {
        InputConstants.Key key = BINDS.get(skillId);
        if (key == null || key.getValue() < 0) {
            return false;
        }
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }

    public static Map<String, InputConstants.Key> allBinds() {
        return Map.copyOf(BINDS);
    }

    public static double getLastPanX() {
        return lastPanX;
    }

    public static double getLastPanY() {
        return lastPanY;
    }

    public static double getLastScale() {
        return lastScale;
    }

    public static void saveViewState(double panX, double panY, double scale) {
        lastPanX = panX;
        lastPanY = panY;
        lastScale = scale;
        save();
    }

    // ============ 文件读写 ============

    private static File file() {
        return new File(Minecraft.getInstance().gameDirectory, "config/" + SkillTreeMod.MOD_ID + "_client.json");
    }

    /** 加载（幂等，重复调用不重读） */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            File f = file();
            if (!f.isFile()) {
                return;
            }
            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            Data data = new Gson().fromJson(json, Data.class);
            if (data == null) {
                return;
            }
            BINDS.clear();
            if (data.binds != null) {
                for (Map.Entry<String, String> e : data.binds.entrySet()) {
                    InputConstants.Key key = InputConstants.getKey(e.getValue());
                    if (key != null && key != InputConstants.UNKNOWN) {
                        BINDS.put(e.getKey(), key);
                    }
                }
            }
            lastPanX = data.panX;
            lastPanY = data.panY;
            lastScale = data.scale > 0 ? data.scale : 1.0;
        } catch (Exception ignored) {
            // 读取失败（文件损坏等）→ 用默认值，不崩溃
        }
    }

    private static void save() {
        try {
            File f = file();
            if (f.getParentFile() != null && !f.getParentFile().isDirectory()) {
                f.getParentFile().mkdirs();
            }
            Data data = new Data();
            data.binds = new HashMap<>();
            for (Map.Entry<String, InputConstants.Key> e : BINDS.entrySet()) {
                data.binds.put(e.getKey(), e.getValue().getName());
            }
            data.panX = lastPanX;
            data.panY = lastPanY;
            data.scale = lastScale;
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(data);
            Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 写入失败不崩溃（仅丢失本次绑定）
        }
    }

    /** JSON 数据结构（Gson 映射） */
    private static class Data {
        Map<String, String> binds;
        double panX;
        double panY;
        double scale = 1.0;
    }
}
