package org.zifeng.skilltree.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import org.zifeng.skilltree.skill.Skills;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 单个玩家的技能数据记录：
 * <ul>
 *   <li>剩余技能点 skillPoints（无上限）</li>
 *   <li>已学技能 learnedSkills：技能ID -> 已投入点数</li>
 *   <li>技能开关 toggles：技能ID -> 是否启用（默认 true，关闭后该技能加成不生效）</li>
 *   <li>加点规则：无等级锁；基础类每项上限 {@link Skills#BASE_MAX_POINTS}；增幅类上限 {@link Skills#AMPLIFY_MAX_POINTS}；终极单次解锁；杀戮光环特殊消耗</li>
 *   <li>生效等级 activeLevels：每个技能独立设置启用几级（默认=已学，可低于已学）</li>
 *   <li>杀戮光环总开关 auraEnabled：快捷键切换是否开始杀戮光环</li>
 * </ul>
 */
public class PlayerSkillRecord {
    private final UUID owner;
    /** 剩余技能点（支持小数：基础每级 1，增幅每级 2，终极/光环 1 或更多） */
    private double skillPoints;
    private final Map<String, Integer> learnedSkills = new HashMap<>();
    private final Map<String, Boolean> toggles = new HashMap<>();
    /** 生效等级：技能ID -> 启用的等级数（<=已学等级） */
    private final Map<String, Integer> activeLevels = new HashMap<>();
    /** 杀戮光环目标模式（2026-08-13 需求：每个光环独立）：技能ID -> 0=敌对 1=友好 2=所有 */
    private final Map<String, Integer> auraTargetModes = new HashMap<>();
    /** 杀戮光环总开关（默认开启） */
    private boolean auraEnabled = true;
    /** 玩家整体累计转换的技能点数（原始整数，技能点转换机阶梯消耗按此计算，跨机器共享） */
    private long totalConvertedPoints;
    /** 自动熔炼黑名单（2026-08-13 恢复）：黑名单中的掉落物不参与熔炼判定，当正常方块处理（Item 注册名集合） */
    private final Set<Item> autoSmeltBlacklist = new HashSet<>();
    /** 凋落物挪移绑定（2026-08-24）：绑定信息存玩家存档（木棍只是绑定媒介，绑定后无需手持木棍）
     *  null = 未绑定；维度字符串 + 坐标 + 朝向 + 容器显示名 */
    private String lootVacuumDim;
    private int lootVacuumX;
    private int lootVacuumY;
    private int lootVacuumZ;
    private int lootVacuumFace = 0;
    private String lootVacuumName = ""; // 容器方块显示名（如“箱子”）

    public Set<Item> getAutoSmeltBlacklist() {
        return Collections.unmodifiableSet(autoSmeltBlacklist);
    }

    /** 添加黑名单物品（返回是否新增） */
    public boolean addAutoSmeltBlacklist(Item item) {
        return autoSmeltBlacklist.add(item);
    }

    /** 移除黑名单物品（返回是否移除） */
    public boolean removeAutoSmeltBlacklist(Item item) {
        return autoSmeltBlacklist.remove(item);
    }

    // ============ 凋落物挪移绑定（2026-08-24） ============

    /** 是否已绑定容器 */
    public boolean hasLootVacuumBind() {
        return lootVacuumDim != null && !lootVacuumDim.isBlank();
    }

    /** 绑定容器（维度字符串 + 坐标 + 朝向 + 容器显示名） */
    public void setLootVacuumBind(String dim, int x, int y, int z, int face, String name) {
        this.lootVacuumDim = dim;
        this.lootVacuumX = x;
        this.lootVacuumY = y;
        this.lootVacuumZ = z;
        this.lootVacuumFace = face;
        this.lootVacuumName = name != null ? name : "";
    }

    /** 解除绑定 */
    public void clearLootVacuumBind() {
        this.lootVacuumDim = null;
    }

    public String getLootVacuumDim() {
        return lootVacuumDim;
    }

    public int getLootVacuumX() {
        return lootVacuumX;
    }

    public int getLootVacuumY() {
        return lootVacuumY;
    }

    public int getLootVacuumZ() {
        return lootVacuumZ;
    }

    public int getLootVacuumFace() {
        return lootVacuumFace;
    }

    /** 容器显示名（如“箱子”） */
    public String getLootVacuumName() {
        return lootVacuumName;
    }

    public PlayerSkillRecord(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    public double getSkillPoints() {
        return skillPoints;
    }

    public void setSkillPoints(double skillPoints) {
        this.skillPoints = Math.max(0, skillPoints);
    }

    public void addSkillPoints(double amount) {
        this.skillPoints = Math.max(0, this.skillPoints + amount);
    }

    public Map<String, Integer> getLearnedSkills() {
        return Collections.unmodifiableMap(learnedSkills);
    }

    public int getLearnedPoints(String skillId) {
        return learnedSkills.getOrDefault(skillId, 0);
    }

    // ============ 生效等级（独立设置开启的等级） ============

    /**
     * 当前生效等级：<= 已学等级。未设置时默认=已学等级。
     */
    public int getActiveLevel(String skillId) {
        return Math.max(0, Math.min(getLearnedPoints(skillId), activeLevels.getOrDefault(skillId, Integer.MAX_VALUE)));
    }

    /** 设置生效等级（0 = 完全不生效，可低于已学等级） */
    public void setActiveLevel(String skillId, int level) {
        activeLevels.put(skillId, Math.max(0, Math.min(getLearnedPoints(skillId), level)));
    }

    public Map<String, Integer> getActiveLevels() {
        return Collections.unmodifiableMap(activeLevels);
    }

    // ============ 技能开关 ============

    /** 技能是否启用（默认启用） */
    public boolean isEnabled(String skillId) {
        return toggles.getOrDefault(skillId, Boolean.TRUE);
    }

    /** 设置技能开关 */
    public void setEnabled(String skillId, boolean enabled) {
        toggles.put(skillId, enabled);
    }

    public Map<String, Boolean> getToggles() {
        return Collections.unmodifiableMap(toggles);
    }

    /** 直接设置已学点数（客户端显示用；服务端加点请用 learnSkill 保证消耗/上限校验） */
    public void setLearnedPoints(String skillId, int points) {
        learnedSkills.put(skillId, Math.max(0, points));
    }

    // ============ 杀戮光环目标模式与总开关 ============

    /** 指定光环技能的目标模式（0 敌对 / 1 友好 / 2 所有）；未设置默认敌对 */
    public int getAuraTargetMode(String skillId) {
        return auraTargetModes.getOrDefault(skillId, 0);
    }

    /** 设置指定光环技能的目标模式 */
    public void setAuraTargetMode(String skillId, int mode) {
        auraTargetModes.put(skillId, Math.max(0, Math.min(2, mode)));
    }

    /** 全部光环目标模式（供网络同步） */
    public Map<String, Integer> getAuraTargetModes() {
        return Collections.unmodifiableMap(auraTargetModes);
    }

    // ============ 晴空环天气模式（2026-08-27：0=晴 1=雨 2=雷暴） ============

    /** 晴空环天气模式（0 晴天 / 1 雨天 / 2 雷暴）；默认晴天 */
    private int weatherMode = 0;

    /** 当前选择的晴空环天气模式 */
    public int getWeatherMode() {
        return weatherMode;
    }

    /** 设置晴空环天气模式（clamp 0-2） */
    public void setWeatherMode(int mode) {
        weatherMode = Math.max(0, Math.min(2, mode));
    }

    /** 杀戮光环总开关 */
    public boolean isAuraEnabled() {
        return auraEnabled;
    }

    public void setAuraEnabled(boolean auraEnabled) {
        this.auraEnabled = auraEnabled;
    }

    // ============ 玩家整体累计转换（阶梯消耗用，跨机器共享） ============

    /** 玩家全部技能点转换机累计转换的技能点数（原始整数） */
    public long getTotalConvertedPoints() {
        return totalConvertedPoints;
    }

    /** 累计转换点数（仅增加；技能重洗不影响，属于机器产出历史） */
    public void addTotalConvertedPoints(long amount) {
        this.totalConvertedPoints = Math.max(0, this.totalConvertedPoints + amount);
    }

    /**
     * 判断某技能当前是否还能继续加点。
     * 基础类：上限 {@link Skills#BASE_MAX_POINTS}；终极/宇宙的青睐：单次解锁；杀戮光环：各自上限。
     */
    public boolean canLearn(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return false;
        }
        int current = getLearnedPoints(skillId);
        Skills.SkillType type = Skills.getType(skillId);
        if (type == Skills.SkillType.BASE) {
            return current < Skills.BASE_MAX_POINTS;
        }
        if (type == Skills.SkillType.AMPLIFY) {
            return current < Skills.AMPLIFY_MAX_POINTS;
        }
        if (type == Skills.SkillType.ULTIMATE) {
            return current < Skills.getUltimateMaxPoints(skillId);
        }
        if (type == Skills.SkillType.SPECIAL) {
            return current < Skills.getUltimateMaxPoints(skillId); // 特殊被动：复用终极等级上限
        }
        if (type == Skills.SkillType.AURA) {
            return current < Skills.getAuraMaxPoints(skillId);
        }
        if (type == Skills.SkillType.GLOBAL) {
            return current < Skills.getGlobalMaxPoints(skillId);
        }
        if (type == Skills.SkillType.MAGIC) {
            return current < Skills.getMagicMaxPoints(skillId);
        }
        if (type == Skills.SkillType.MACHINE) {
            return current < Skills.getMachineMaxPoints(skillId);
        }
        if (type == Skills.SkillType.GIFT) {
            return current < Skills.getGiftMaxPoints(skillId); // 子枫的馈赠：单级解锁
        }
        return true;
    }

    /** 下一级需要的技能点数：基础 1 / 增幅 2 / 终极 1 / 光环按消耗公式 / 宇宙的青睐 1000 / 夜视·饱食 100（数值走 Config） */
    public double getNextCost(String skillId) {
        if (Skills.ULT_FAVOR.equals(skillId)) {
            return Skills.ultFavorCost();
        }
        if (Skills.NIGHT_VISION.equals(skillId) || Skills.SATURATION.equals(skillId)) {
            return Skills.minorUltCost();
        }
        if (Skills.AURA_MAGNET.equals(skillId)) {
            return org.zifeng.skilltree.Config.MAGNET_COST.get(); // 磁力光环：一次性解锁
        }
        if (Skills.AURA_LOCK.equals(skillId)) {
            return org.zifeng.skilltree.Config.LOCK_COST.get(); // 光环锁定：一次性解锁
        }
        if (Skills.AURA_VOID.equals(skillId)) {
            return org.zifeng.skilltree.Config.VOID_AURA_COST.get(); // 杀戮光环·虚空之矛：一次性解锁
        }
        Skills.SkillType type = Skills.getType(skillId);
        if (type == Skills.SkillType.GLOBAL) {
            return Skills.getGlobalCost(skillId, getLearnedPoints(skillId)); // 寰宇法则：时之环/晴空环 100；无限回路阶梯
        }
        if (type == Skills.SkillType.AURA) {
            return Skills.getAuraCost(skillId, getLearnedPoints(skillId));
        }
        if (type == Skills.SkillType.BASE) {
            return Skills.getBaseCostAtLevel(getLearnedPoints(skillId)); // 线性：第 n 级消耗 = n
        }
        if (type == Skills.SkillType.AMPLIFY) {
            return Skills.getAmplifyCostAtLevel(getLearnedPoints(skillId)); // 线性：第 n 级消耗 = 2n
        }
        if (type == Skills.SkillType.MAGIC) {
            return Skills.getMagicCostAtLevel(skillId, getLearnedPoints(skillId)); // 线性：默认第 n 级 = 2n，吟唱缩减 = 5n
        }
        if (type == Skills.SkillType.MACHINE) {
            return Skills.getMachineCost(skillId); // 机械共鸣：一次性（机械之星 1000 / 其余 5000）
        }
        if (type == Skills.SkillType.GIFT) {
            return Skills.getGiftCost(skillId, getLearnedPoints(skillId)); // 子枫的馈赠：时间系列 0 / 洗礼 10-10000 / 增幅指数
        }
        return Skills.getUltimateLevelCost(skillId, getLearnedPoints(skillId)); // 终极节点（单次或节点类阶梯递增）
    }

    /**
     * 学习/加点：扣除对应技能点（小数）并记录。返回是否成功。
     */
    public boolean learnSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return false;
        }
        if (!canLearn(skillId)) {
            return false;
        }
        double cost = getNextCost(skillId);
        if (skillPoints < cost - 1e-9) {
            return false;
        }
        skillPoints -= cost;
        learnedSkills.merge(skillId, 1, Integer::sum);
        return true;
    }

    // ============ 技能重洗 ============

    /**
     * 重洗全部技能：按总消耗 × 返还率（Config）加回技能点，清空所有已学/开关/生效等级/光环状态。
     * 返回返还的技能点数（不含原有剩余）。
     */
    public double resetAll() {
        double refund = 0;
        for (Map.Entry<String, Integer> entry : learnedSkills.entrySet()) {
            refund += totalSpent(entry.getKey(), entry.getValue());
        }
        if (refund > 0) {
            skillPoints += refund * org.zifeng.skilltree.Config.RESET_REFUND_RATE.get();
        }
        learnedSkills.clear();
        toggles.clear();
        activeLevels.clear();
        auraTargetModes.clear();
        auraEnabled = true;
        return refund;
    }

    /** 单技能已投入总消耗（对外公开，供单技能重置包使用） */
    public double totalSpentOf(String skillId) {
        return totalSpent(skillId, getLearnedPoints(skillId));
    }

    /** 重置单个技能：返还该技能消耗 × 返还率，移除该技能的已学/开关/生效等级。返回返还点数。 */
    public double resetSkill(String skillId) {
        int points = getLearnedPoints(skillId);
        if (points <= 0) {
            return 0;
        }
        double refund = totalSpent(skillId, points) * org.zifeng.skilltree.Config.RESET_REFUND_RATE.get();
        if (refund > 0) {
            skillPoints += refund;
        }
        learnedSkills.remove(skillId);
        toggles.remove(skillId);
        activeLevels.remove(skillId);
        return refund;
    }

    /** 某技能已投入 points 点的总消耗（与 getNextCost 的消耗规则一致，含递增光环） */
    private static double totalSpent(String skillId, int points) {        if (points <= 0) {
            return 0;
        }
        if (Skills.ULT_FAVOR.equals(skillId)) {
            return Skills.ultFavorCost();
        }
        if (Skills.NIGHT_VISION.equals(skillId) || Skills.SATURATION.equals(skillId)) {
            return Skills.minorUltCost();
        }
        if (Skills.AURA_MAGNET.equals(skillId)) {
            return org.zifeng.skilltree.Config.MAGNET_COST.get();
        }
        if (Skills.AURA_LOCK.equals(skillId)) {
            return org.zifeng.skilltree.Config.LOCK_COST.get();
        }
        if (Skills.AURA_VOID.equals(skillId)) {
            return org.zifeng.skilltree.Config.VOID_AURA_COST.get();
        }
        return switch (Skills.getType(skillId)) {
            case BASE -> { // 线性消耗累加：1+2+3+...+n（double 防 64 位溢出，2026-08-12 统一）
                double total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getBaseCostAtLevel(i);
                }
                yield total;
            }
            case AMPLIFY -> { // 线性消耗累加：2+4+6+...+2n（double 防 64 位溢出）
                double total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getAmplifyCostAtLevel(i);
                }
                yield total;
            }
            case MAGIC -> { // 线性消耗累加（默认 +2/级；吟唱缩减 +5/级，double 防 64 位溢出）
                double total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getMagicCostAtLevel(skillId, i);
                }
                yield total;
            }
            case MACHINE -> (double) Skills.getMachineCost(skillId) * points; // 机械共鸣：一次性固定消耗（单级）
            case ULTIMATE, SPECIAL -> { // 单次解锁或节点类阶梯递增（逐级累加 double，与学习时实际扣除一致；SPECIAL 复用终极成本）
                double total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getUltimateLevelCost(skillId, i);
                }
                yield total;
            }
            case AURA -> { // 64 位累加（double 防 long 溢出：1.05^1000 远超 Long.MAX）
                double total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getAuraCost(skillId, i);
                }
                yield total;
            }
            case GLOBAL -> { // 寰宇法则：时之环/晴空环固定 100；无限回路阶梯 200/500/1000/2000
                double total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getGlobalCost(skillId, i);
                }
                yield total;
            }
            case GIFT -> { // 子枫的馈赠：逐级累加（时间系列 0；洗礼 10/1000/10000；增幅 1000×1.5^n）
                double total = 0;
                for (int i = 0; i < points; i++) {
                    total += Skills.getGiftCost(skillId, i);
                }
                yield total;
            }
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", owner);
        tag.putDouble("SkillPoints", skillPoints);
        ListTag learned = new ListTag();
        for (Map.Entry<String, Integer> entry : learnedSkills.entrySet()) {
            CompoundTag skillTag = new CompoundTag();
            skillTag.putString("Id", entry.getKey());
            skillTag.putInt("Points", entry.getValue());
            learned.add(skillTag);
        }
        tag.put("LearnedSkills", learned);
        ListTag togglesList = new ListTag();
        for (Map.Entry<String, Boolean> entry : toggles.entrySet()) {
            CompoundTag toggleTag = new CompoundTag();
            toggleTag.putString("Id", entry.getKey());
            toggleTag.putBoolean("Enabled", entry.getValue());
            togglesList.add(toggleTag);
        }
        tag.put("Toggles", togglesList);
        // 光环目标模式（2026-08-13：每个光环独立）
        ListTag modeList = new ListTag();
        for (Map.Entry<String, Integer> entry : auraTargetModes.entrySet()) {
            CompoundTag modeTag = new CompoundTag();
            modeTag.putString("Id", entry.getKey());
            modeTag.putInt("Mode", entry.getValue());
            modeList.add(modeTag);
        }
        tag.put("AuraTargetModes", modeList);
        // 晴空环天气模式（2026-08-27：0=晴 1=雨 2=雷暴）
        tag.putInt("WeatherMode", weatherMode);
        // 旧字段兼容（旧存档读取用）
        tag.putInt("AuraTargetMode", auraTargetModes.getOrDefault(Skills.AURA_DAMAGE, 0));
        tag.putBoolean("AuraEnabled", auraEnabled);
        tag.putLong("TotalConvertedPoints", totalConvertedPoints);
        ListTag activeList = new ListTag();
        for (Map.Entry<String, Integer> entry : activeLevels.entrySet()) {
            CompoundTag activeTag = new CompoundTag();
            activeTag.putString("Id", entry.getKey());
            activeTag.putInt("Level", entry.getValue());
            activeList.add(activeTag);
        }
        tag.put("ActiveLevels", activeList);
        // 自动熔炼黑名单（存 Item 注册名）
        ListTag blacklist = new ListTag();
        for (Item item : autoSmeltBlacklist) {
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getResourceKey(item)
                    .ifPresent(key -> blacklist.add(StringTag.valueOf(key.location().toString())));
        }
        tag.put("AutoSmeltBlacklist", blacklist);
        // 凋落物挪移绑定
        if (lootVacuumDim != null) {
            tag.putString("LootVacuumDim", lootVacuumDim);
            tag.putInt("LootVacuumX", lootVacuumX);
            tag.putInt("LootVacuumY", lootVacuumY);
            tag.putInt("LootVacuumZ", lootVacuumZ);
            tag.putInt("LootVacuumFace", lootVacuumFace);
            tag.putString("LootVacuumName", lootVacuumName);
        }
        return tag;
    }

    public static PlayerSkillRecord load(CompoundTag tag) {
        // 防御：Owner 缺失/损坏（其他模组污染存档）时返回 null，调用方跳过该记录，避免脏数据崩溃
        if (tag == null || !tag.hasUUID("Owner")) {
            return null;
        }
        UUID owner = tag.getUUID("Owner");
        PlayerSkillRecord record = new PlayerSkillRecord(owner);
        record.skillPoints = tag.contains("SkillPoints", Tag.TAG_DOUBLE) ? tag.getDouble("SkillPoints") : tag.getInt("SkillPoints");
        if (tag.contains("LearnedSkills", Tag.TAG_LIST)) {
            ListTag learned = tag.getList("LearnedSkills", Tag.TAG_COMPOUND);
            for (int i = 0; i < learned.size(); i++) {
                CompoundTag skillTag = learned.getCompound(i);
                String id = skillTag.getString("Id");
                if (!id.isBlank()) {
                    record.learnedSkills.put(id, skillTag.getInt("Points"));
                }
            }
        }
        if (tag.contains("Toggles", Tag.TAG_LIST)) {
            ListTag togglesList = tag.getList("Toggles", Tag.TAG_COMPOUND);
            for (int i = 0; i < togglesList.size(); i++) {
                CompoundTag toggleTag = togglesList.getCompound(i);
                String id = toggleTag.getString("Id");
                if (!id.isBlank()) {
                    record.toggles.put(id, toggleTag.getBoolean("Enabled"));
                }
            }
        }
        // 光环目标模式（旧字段 AuraTargetMode 兼容：迁移到 AURA_DAMAGE）
        if (tag.contains("AuraTargetModes", Tag.TAG_LIST)) {
            ListTag modeList = tag.getList("AuraTargetModes", Tag.TAG_COMPOUND);
            for (int i = 0; i < modeList.size(); i++) {
                CompoundTag modeTag = modeList.getCompound(i);
                String id = modeTag.getString("Id");
                if (!id.isBlank()) {
                    record.auraTargetModes.put(id, modeTag.getInt("Mode"));
                }
            }
        } else if (tag.contains("AuraTargetMode", Tag.TAG_INT)) {
            record.auraTargetModes.put(Skills.AURA_DAMAGE, tag.getInt("AuraTargetMode"));
        }
        // 晴空环天气模式（旧存档无此字段默认 0=晴）
        record.weatherMode = tag.contains("WeatherMode", Tag.TAG_INT) ? Math.max(0, Math.min(2, tag.getInt("WeatherMode"))) : 0;
        record.auraEnabled = !tag.contains("AuraEnabled") || tag.getBoolean("AuraEnabled");
        record.totalConvertedPoints = tag.getLong("TotalConvertedPoints"); // 旧存档无此字段默认 0
        if (tag.contains("ActiveLevels", Tag.TAG_LIST)) {
            ListTag activeList = tag.getList("ActiveLevels", Tag.TAG_COMPOUND);
            for (int i = 0; i < activeList.size(); i++) {
                CompoundTag activeTag = activeList.getCompound(i);
                String id = activeTag.getString("Id");
                if (!id.isBlank()) {
                    record.activeLevels.put(id, activeTag.getInt("Level"));
                }
            }
        }
        // 自动熔炼黑名单（旧存档无此字段默认空）
        if (tag.contains("AutoSmeltBlacklist", Tag.TAG_LIST)) {
            ListTag blacklist = tag.getList("AutoSmeltBlacklist", Tag.TAG_STRING);
            for (int i = 0; i < blacklist.size(); i++) {
                String name = blacklist.getString(i);
                if (name.isBlank()) {
                    continue;
                }
                net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(name);
                if (loc != null) {
                    Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc);
                    if (item != net.minecraft.world.item.Items.AIR) {
                        record.autoSmeltBlacklist.add(item);
                    }
                }
            }
        }
        // 凋落物挪移绑定（旧存档无此字段默认未绑定）
        if (tag.contains("LootVacuumDim", Tag.TAG_STRING)) {
            record.lootVacuumDim = tag.getString("LootVacuumDim");
            record.lootVacuumX = tag.getInt("LootVacuumX");
            record.lootVacuumY = tag.getInt("LootVacuumY");
            record.lootVacuumZ = tag.getInt("LootVacuumZ");
            record.lootVacuumFace = tag.getInt("LootVacuumFace");
            record.lootVacuumName = tag.contains("LootVacuumName", Tag.TAG_STRING) ? tag.getString("LootVacuumName") : "";
        }
        return record;
    }
}
