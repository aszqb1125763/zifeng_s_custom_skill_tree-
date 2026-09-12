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
    /** 绑定目标类型（2026-09-12 1.4.1）：0=普通物品容器 1=AE2 无线访问点（ME 网络）。
     *  决定掉落物走 ItemHandler 还是走 AE 网络，也决定客户端绑定框配色。 */
    private int lootVacuumType = 0;
    // ============ 木棍工具层（2026-09-08：占位卡，不参与技能点体系） ============
    /** 木棍工具总开关：true=木棍作为工具使用（占用左/右键手势）；false=还原原版木棍。
     *  ⚠️ 只影响"木棍手势"，不影响任何技能被动逻辑（吸取/挪移直传/触发键等照常），
     *  已绑容器 / 已选屏蔽区数据不受影响。 */
    private boolean stickToolOn = true;
    /** 木棍工具模式：0=BIND（潜行右键绑容器） 1=RANGE（左键框选磁铁屏蔽区）。
     *  ⚠️ 仅决定"木棍手势当前路由给哪个功能模块"，模块间互相独立、切走不关闭任何功能。 */
    private int stickToolMode = 0;

    /** 木棍工具总开关是否开启（默认开） */
    public boolean isStickToolOn() {
        return stickToolOn;
    }

    public void setStickToolOn(boolean on) {
        this.stickToolOn = on;
    }

    /** 木棍工具当前模式（0=BIND 1=RANGE） */
    public int getStickToolMode() {
        return stickToolMode;
    }

    /** 切换木棍工具模式（BIND↔RANGE 循环；非法值忽略） */
    public void setStickToolMode(int mode) {
        if (mode == 0 || mode == 1 || mode == 2 || mode == 3 || mode == 4 || mode == 5) {
            this.stickToolMode = mode;
        }
    }

    // ============ 机械共鸣·操作区（2026-09-08：放置/挖掘/攻击/防护各一块，单人生效，存玩家） ============
    /** 技能ID → 该技能当前操作区（null=未框选） */
    private final Map<String, OperZone> operZones = new HashMap<>();

    /** 获取指定技能的操作区（放置/挖掘/攻击/防护；null=未框选） */
    public OperZone getOperZone(String skillId) {
        if (!Skills.isStickZoneSkill(skillId)) {
            return null;
        }
        return operZones.get(skillId);
    }

    /** 设置/清除指定技能的操作区（null=清除） */
    public void setOperZone(String skillId, OperZone zone) {
        if (!Skills.isStickZoneSkill(skillId)) {
            return;
        }
        if (zone == null) {
            operZones.remove(skillId);
        } else {
            operZones.put(skillId, zone);
        }
    }

    public Map<String, OperZone> getOperZones() {
        return Collections.unmodifiableMap(operZones);
    }

    // ============ 防护区·多块列表（2026-09-08：防护可框选多块，上限 10，区内所有生物免疫） ============
    /** 防护区上限 */
    public static final int MAX_PROTECT_ZONES = 10;
    /** 防护区列表（操作区 map 只承载 放置/挖掘/攻击 单块；防护区独立多块） */
    private final java.util.List<OperZone> protectZones = new java.util.ArrayList<>();

    /** 全部防护区（只读） */
    public java.util.List<OperZone> getProtectZones() {
        return java.util.Collections.unmodifiableList(protectZones);
    }

    /** 防护区当前数量 */
    public int protectZoneCount() {
        return protectZones.size();
    }

    /** 新增一块防护区（已达上限/重复返回 false） */
    public boolean addProtectZone(OperZone zone) {
        if (zone == null || protectZones.size() >= MAX_PROTECT_ZONES) {
            return false; // 满 10 块：忽略
        }
        for (OperZone z : protectZones) {
            if (z.minX() == zone.minX() && z.minY() == zone.minY() && z.minZ() == zone.minZ()
                    && z.maxX() == zone.maxX() && z.maxY() == zone.maxY() && z.maxZ() == zone.maxZ()
                    && z.dim().equals(zone.dim())) {
                return false; // 重复块
            }
        }
        protectZones.add(zone);
        return true;
    }

    /** 查找包含该坐标的防护区（滚轮微调用）；找不到返回 null */
    public OperZone findProtectZoneAt(String dim, int x, int y, int z) {
        for (OperZone oz : protectZones) {
            if (oz.dim().equals(dim)
                    && x >= oz.minX() && x <= oz.maxX()
                    && y >= oz.minY() && y <= oz.maxY()
                    && z >= oz.minZ() && z <= oz.maxZ()) {
                return oz;
            }
        }
        return null;
    }

    /**
     * 用新区替换旧的防护区（滚轮微调；2026-09-12）。
     * <p>按值比较定位（OperZone 是 record，equals 为逐字段比较）。
     * @return true = 找到并替换
     */
    public boolean replaceProtectZone(OperZone oldZone, OperZone newZone) {
        if (oldZone == null || newZone == null) {
            return false;
        }
        int i = protectZones.indexOf(oldZone);
        if (i < 0) {
            return false;
        }
        protectZones.set(i, newZone);
        return true;
    }

    /** 移除包含该坐标（任一角点/内部点）的防护区；返回是否移除 */
    public boolean removeProtectZoneAt(String dim, int x, int y, int z) {
        java.util.Iterator<OperZone> it = protectZones.iterator();
        while (it.hasNext()) {
            OperZone oz = it.next();
            if (oz.dim().equals(dim)
                    && x >= oz.minX() && x <= oz.maxX()
                    && y >= oz.minY() && y <= oz.maxY()
                    && z >= oz.minZ() && z <= oz.maxZ()) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** 目标是否落在任一防护区内（区内所有生物免疫/跳过，2026-09-08） */
    public boolean isInAnyProtectZone(String dim, net.minecraft.core.BlockPos pos) {
        for (OperZone z : protectZones) {
            if (z.dim().equals(dim) && z.contains(pos)) {
                return true;
            }
        }
        return false;
    }

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

    /** 绑定容器（维度字符串 + 坐标 + 朝向 + 容器显示名；默认类型 = 普通物品容器） */
    public void setLootVacuumBind(String dim, int x, int y, int z, int face, String name) {
        setLootVacuumBind(dim, x, y, z, face, name,
                org.zifeng.skilltree.compat.Ae2StorageCompat.TYPE_CONTAINER);
    }

    /**
     * 绑定目标（2026-09-12 1.4.1 新增类型参数）。
     * @param type 0 = 普通物品容器（箱子/漏斗/模组容器）；1 = AE2 无线访问点（ME 网络）
     */
    public void setLootVacuumBind(String dim, int x, int y, int z, int face, String name, int type) {
        this.lootVacuumDim = dim;
        this.lootVacuumX = x;
        this.lootVacuumY = y;
        this.lootVacuumZ = z;
        this.lootVacuumFace = face;
        this.lootVacuumName = name != null ? name : "";
        this.lootVacuumType = type;
    }

    /** 解除绑定 */
    public void clearLootVacuumBind() {
        this.lootVacuumDim = null;
        this.lootVacuumType = 0;
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

    /** 绑定目标类型（0=普通容器 1=AE 无线访问点；2026-09-12 1.4.1） */
    public int getLootVacuumType() {
        return lootVacuumType;
    }

    /** 绑定目标是否为 AE 网络（客户端渲染配色 / 提示文案分支用） */
    public boolean isLootVacuumAe() {
        return lootVacuumType == org.zifeng.skilltree.compat.Ae2StorageCompat.TYPE_AE;
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

    /**
     * 设置指定光环技能的目标模式。
     * ⚠️ 子枫的搬运术（CONTAINER_HAUL，2026-09-07）复用该字段存搬运模式：
     * 0=自动（开箱即搬） 1=手动（按键搬运）——clamp 上限按技能区分（光环 0-2，搬运术 0-1）。
     */
    public void setAuraTargetMode(String skillId, int mode) {
        int max = Skills.isContainerHaul(skillId) ? 1 : 2;
        auraTargetModes.put(skillId, Math.max(0, Math.min(max, mode)));
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

    /**
     * 管理指令硬清空（2026-09-04，/skilltree reset）：与游戏内重洗 {@link #resetAll()} 相反——
     * <b>不返还任何技能点</b>，清空全部已学/开关/生效等级/光环目标模式并把剩余技能点归零。
     * 保留非技能类数据：自动熔炼黑名单、凋落物挪移绑定、累计转换技能点数（totalConvertedPoints）。
     */
    public void hardReset() {
        learnedSkills.clear();
        toggles.clear();
        activeLevels.clear();
        auraTargetModes.clear();
        auraEnabled = true;
        skillPoints = 0;
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
        // 木棍工具层（2026-09-08）：总开关 + 模式
        tag.putBoolean("StickToolOn", stickToolOn);
        tag.putInt("StickToolMode", stickToolMode);
        // 机械共鸣·操作区（2026-09-08）：技能ID → 区
        ListTag operList = new ListTag();
        for (Map.Entry<String, OperZone> e : operZones.entrySet()) {
            CompoundTag zt = new CompoundTag();
            zt.putString("Skill", e.getKey());
            zt.putString("Dim", e.getValue().dim());
            zt.putInt("AX", e.getValue().ax());
            zt.putInt("AY", e.getValue().ay());
            zt.putInt("AZ", e.getValue().az());
            zt.putInt("BX", e.getValue().bx());
            zt.putInt("BY", e.getValue().by());
            zt.putInt("BZ", e.getValue().bz());
            operList.add(zt);
        }
        tag.put("OperZones", operList);
        // 防护区多块列表（2026-09-08）
        ListTag pzList = new ListTag();
        for (OperZone z : protectZones) {
            CompoundTag zt = new CompoundTag();
            zt.putString("Dim", z.dim());
            zt.putInt("AX", z.ax());
            zt.putInt("AY", z.ay());
            zt.putInt("AZ", z.az());
            zt.putInt("BX", z.bx());
            zt.putInt("BY", z.by());
            zt.putInt("BZ", z.bz());
            pzList.add(zt);
        }
        tag.put("ProtectZones", pzList);
        // 凋落物挪移绑定
        if (lootVacuumDim != null) {
            tag.putString("LootVacuumDim", lootVacuumDim);
            tag.putInt("LootVacuumX", lootVacuumX);
            tag.putInt("LootVacuumY", lootVacuumY);
            tag.putInt("LootVacuumZ", lootVacuumZ);
            tag.putInt("LootVacuumFace", lootVacuumFace);
            tag.putString("LootVacuumName", lootVacuumName);
            tag.putInt("LootVacuumType", lootVacuumType);
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
            record.lootVacuumType = tag.contains("LootVacuumType", Tag.TAG_INT) ? tag.getInt("LootVacuumType") : 0;
        }
        // 木棍工具层（2026-09-08：旧存档无此字段 → 默认开 + BIND 模式）
        record.stickToolOn = !tag.contains("StickToolOn") || tag.getBoolean("StickToolOn");
        record.stickToolMode = tag.contains("StickToolMode", Tag.TAG_INT)
                ? Math.max(0, Math.min(5, tag.getInt("StickToolMode"))) : 0;
        // 机械共鸣·操作区（2026-09-08：旧存档无此字段默认空）
        if (tag.contains("OperZones", Tag.TAG_LIST)) {
            ListTag operList = tag.getList("OperZones", Tag.TAG_COMPOUND);
            for (int i = 0; i < operList.size(); i++) {
                CompoundTag zt = operList.getCompound(i);
                String skill = zt.getString("Skill");
                String dim = zt.getString("Dim");
                if (skill.isBlank() || dim.isBlank()) {
                    continue;
                }
                record.operZones.put(skill, new OperZone(dim,
                        zt.getInt("AX"), zt.getInt("AY"), zt.getInt("AZ"),
                        zt.getInt("BX"), zt.getInt("BY"), zt.getInt("BZ")));
            }
        }
        // 防护区多块列表（2026-09-08：旧存档无此字段默认空）
        if (tag.contains("ProtectZones", Tag.TAG_LIST)) {
            ListTag pzList = tag.getList("ProtectZones", Tag.TAG_COMPOUND);
            for (int i = 0; i < pzList.size() && record.protectZones.size() < MAX_PROTECT_ZONES; i++) {
                CompoundTag zt = pzList.getCompound(i);
                String dim = zt.getString("Dim");
                if (dim.isBlank()) {
                    continue;
                }
                record.protectZones.add(new OperZone(dim,
                        zt.getInt("AX"), zt.getInt("AY"), zt.getInt("AZ"),
                        zt.getInt("BX"), zt.getInt("BY"), zt.getInt("BZ")));
            }
        }
        return record;
    }
}
