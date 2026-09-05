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
 * 技能独立开关快捷键绑定 + 等级/目标循环快捷键 + 技能树界面位置/缩放持久化（客户端本地，存 config 目录 JSON）。
 * <ul>
 *   <li>每个技能可绑定两个键：① 开关快捷键（默认空键）；② 等级/目标循环快捷键（光环=循环目标模式，可调等级技能=循环生效等级）</li>
 *   <li>技能树界面退出时记录 panX/panY/scale，下次打开恢复到上次位置（2026-08-13 需求）</li>
 *   <li>存储文件：config/zifeng_s_custom_skill_tree_client.json</li>
 * </ul>
 */
public class SkillKeyBinds {

    /** 技能ID → 开关按键（KEYSYM，未绑定则无条目） */
    private static final Map<String, InputConstants.Key> BINDS = new HashMap<>();
    /** 技能ID → 等级/目标循环按键（2026-08-13 新增第二快捷键：光环循环目标模式，可调等级技能循环生效等级） */
    private static final Map<String, InputConstants.Key> LEVEL_BINDS = new HashMap<>();
    /** 技能树界面 panX / panY / scale（上次退出时的状态） */
    private static double lastPanX = 0;
    private static double lastPanY = 0;
    /** 默认缩放 0.4（2026-08-25：八列技能树更宽，0.4 初始能看到全部列） */
    private static double lastScale = 0.4;
    /** 技能点 HUD 位置偏移（2026-08-25：持久化，重启不重置；基准点 = 用户测试调好的位置，默认 0,0） */
    private static int hudOffsetX = 0;
    private static int hudOffsetY = 0;
    /** 子界面位置（2026-09-01：子界面标识 → [x, y] 面板左上角，持久化拖动结果） */
    private static final Map<String, int[]> SUB_SCREEN_POS = new HashMap<>();

    private static boolean loaded = false;

    // ============ 开关键存取 ============

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

    // ============ 等级/目标循环键存取（2026-08-13 新增） ============

    public static InputConstants.Key getLevelKey(String skillId) {
        return LEVEL_BINDS.get(skillId);
    }

    public static boolean hasLevelKey(String skillId) {
        return LEVEL_BINDS.containsKey(skillId);
    }

    /** 设置等级/目标循环键（key 为 null/UNKNOWN 视为清除） */
    public static void setLevelKey(String skillId, InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) {
            LEVEL_BINDS.remove(skillId);
        } else {
            LEVEL_BINDS.put(skillId, key);
        }
        save();
    }

    public static void clearLevelKey(String skillId) {
        LEVEL_BINDS.remove(skillId);
        save();
    }

    /** 上次 tick 各键按下状态（边沿检测：按下瞬间返回 true 一次） */
    private static final java.util.Set<String> lastPressed = new java.util.HashSet<>();
    /** 等级键上次 tick 按下状态（独立，避免与开关键状态混淆） */
    private static final java.util.Set<String> lastLevelPressed = new java.util.HashSet<>();

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

    /** 等级/目标循环键边沿检测（2026-08-13 新增） */
    public static boolean consumeLevelClick(String skillId) {
        InputConstants.Key key = LEVEL_BINDS.get(skillId);
        if (key == null || key.getValue() < 0) {
            return false;
        }
        boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        boolean prev = lastLevelPressed.contains(skillId);
        if (down && !prev) {
            lastLevelPressed.add(skillId);
            return true;
        }
        if (!down) {
            lastLevelPressed.remove(skillId);
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

    public static Map<String, InputConstants.Key> allLevelBinds() {
        return Map.copyOf(LEVEL_BINDS);
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

    // ============ 技能点 HUD 位置偏移持久化（2026-08-25） ============

    public static int getHudOffsetX() {
        return hudOffsetX;
    }

    public static int getHudOffsetY() {
        return hudOffsetY;
    }

    public static void setHudOffset(int x, int y) {
        hudOffsetX = x;
        hudOffsetY = y;
        save();
    }

    // ============ 子界面位置持久化（2026-09-01） ============

    /** 获取子界面上次位置 [x, y]（null = 未保存过，用默认位置） */
    public static int[] getSubScreenPos(String key) {
        load();
        return SUB_SCREEN_POS.get(key);
    }

    /** 保存子界面位置（拖动后调用，重启恢复） */
    public static void setSubScreenPos(String key, int x, int y) {
        SUB_SCREEN_POS.put(key, new int[]{x, y});
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
            LEVEL_BINDS.clear();
            if (data.levelBinds != null) {
                for (Map.Entry<String, String> e : data.levelBinds.entrySet()) {
                    InputConstants.Key key = InputConstants.getKey(e.getValue());
                    if (key != null && key != InputConstants.UNKNOWN) {
                        LEVEL_BINDS.put(e.getKey(), key);
                    }
                }
            }
            lastPanX = data.panX;
            lastPanY = data.panY;
            lastScale = data.scale > 0 ? data.scale : 0.4;
            // 2026-08-28：加载时 clamp 到新调整范围（X ±400 / Y ±200），防止旧配置残留超范围值
            hudOffsetX = Math.max(-400, Math.min(400, data.hudOffsetX));
            hudOffsetY = Math.max(-200, Math.min(200, data.hudOffsetY));
            // 子界面位置（2026-09-01）
            SUB_SCREEN_POS.clear();
            if (data.subScreenPos != null) {
                for (Map.Entry<String, int[]> e : data.subScreenPos.entrySet()) {
                    if (e.getValue() != null && e.getValue().length == 2) {
                        SUB_SCREEN_POS.put(e.getKey(), new int[]{e.getValue()[0], e.getValue()[1]});
                    }
                }
            }
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
            data.levelBinds = new HashMap<>();
            for (Map.Entry<String, InputConstants.Key> e : LEVEL_BINDS.entrySet()) {
                data.levelBinds.put(e.getKey(), e.getValue().getName());
            }
            data.panX = lastPanX;
            data.panY = lastPanY;
            data.scale = lastScale;
            data.hudOffsetX = hudOffsetX;
            data.hudOffsetY = hudOffsetY;
            data.subScreenPos = new HashMap<>();
            for (Map.Entry<String, int[]> e : SUB_SCREEN_POS.entrySet()) {
                data.subScreenPos.put(e.getKey(), new int[]{e.getValue()[0], e.getValue()[1]});
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(data);
            Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 写入失败不崩溃（仅丢失本次绑定）
        }
    }

    /** JSON 数据结构（Gson 映射） */
    private static class Data {
        Map<String, String> binds;
        Map<String, String> levelBinds;
        double panX;
        double panY;
        double scale = 1.0;
        int hudOffsetX = 0; // 基准点 = 用户测试调好的位置，显示从 0 开始
        int hudOffsetY = 0;
        Map<String, int[]> subScreenPos; // 子界面位置（2026-09-01）
    }
}
