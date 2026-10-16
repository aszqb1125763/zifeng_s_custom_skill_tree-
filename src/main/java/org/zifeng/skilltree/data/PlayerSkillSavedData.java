package org.zifeng.skilltree.data;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.zifeng.skilltree.SkillTreeMod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全局玩家技能数据（SavedData，存主世界）。
 * <ul>
 *   <li>玩家技能点 / 已学技能（PlayerSkillRecord）</li>
 *   <li>机器绑定关系：机器key(维度|x|y|z) -> 放置者 UUID（仿 wmp-1.7.0）</li>
 * </ul>
 */
public class PlayerSkillSavedData extends SavedData {
    /**
     * 26.1.2：SavedData 的标识从「字符串文件名」改为 {@link Identifier}
     * （落盘位置由 {@code data/<命名空间>/<路径>.dat} 决定，见
     * {@code net.minecraft.world.level.storage.SavedDataStorage#getDataFile}）。
     * <p>⚠️ 因此落盘文件从 {@code data/zifeng_s_custom_skill_tree_player_data.dat}
     * <b>变为</b> {@code data/zifeng_s_custom_skill_tree/player_data.dat}，
     * 且内容多包了一层 {@code data} 键 + DataVersion（见 SavedDataStorage#encodeUnchecked）。
     * 新 API 无法指定旧文件名 → 旧世界的技能数据无法被自动读回（平台级变更，所有模组的 SavedData 都受影响）。
     * 注意：{@link PlayerSkillRecord} 内部的 NBT 结构<b>完全没变</b>（Owner 仍是 4-int 数组），
     * 所以如有需要可手工搬移旧文件内容。
     */
    public static final Identifier DATA_ID = Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "player_data");

    /**
     * 26.1.2 存档编解码：用 {@link CompoundTag#CODEC} 把既有的裸 NBT 读写逻辑原样包成 Codec
     * （{@code CompoundTag.CODEC.xmap(load, save)}）——
     * <b>存档结构与全部旧存档迁移/校验逻辑零改动</b>（迁移代码见 {@link PlayerSkillRecord#load}）。
     * <p>⚠️ 静态域顺序敏感：必须声明在 {@link #TYPE} 之前，否则 TYPE 会拿到 null codec。
     */
    private static final Codec<PlayerSkillSavedData> CODEC =
            CompoundTag.CODEC.xmap(PlayerSkillSavedData::load, PlayerSkillSavedData::save);

    /**
     * 26.1.2 注册表项：{@code SavedDataType(Identifier, 构造器, Codec)}。
     * <p>⚠️ 26.1.2 移除了 {@code SavedData.Factory} 与
     * {@code save(CompoundTag, HolderLookup.Provider)} / {@code load(CompoundTag, HolderLookup.Provider)}，
     * 改由 {@link SavedDataType} + {@code Codec} 承担「读取-构造 / 序列化」两端。
     */
    public static final SavedDataType<PlayerSkillSavedData> TYPE = new SavedDataType<>(
            DATA_ID, PlayerSkillSavedData::new, CODEC);

    private final Map<UUID, PlayerSkillRecord> players = new HashMap<>();
    private final Map<String, UUID> machineOwnerMap = new HashMap<>();

    public PlayerSkillRecord getOrCreatePlayer(UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerSkillRecord::new);
    }

    /**
     * 只读取玩家记录（2026-09-09 性能审计）：null = 该玩家从无任何技能数据。
     * ⚠️ 模块 activeCondition 用本方法判活跃，避免 getOrCreatePlayer 给
     * 未学任何技能的玩家创建空记录 → 内存 map 与存档无谓膨胀。
     */
    public PlayerSkillRecord getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    /** 已存档的玩家记录数（⚠️ 临时诊断用：排查“技能不生效”时验证数据是否正确加载） */
    public int playerCount() {
        return players.size();
    }

    public void bindMachine(String machineKey, UUID owner) {
        if (machineKey == null || machineKey.isBlank() || owner == null) {
            return;
        }
        machineOwnerMap.put(machineKey, owner);
        setDirty();
    }

    public void unbindMachine(String machineKey) {
        if (machineKey == null || machineKey.isBlank()) {
            return;
        }
        machineOwnerMap.remove(machineKey);
        setDirty();
    }

    /** 静态缓存（2026-08-13 性能优化）：SavedData 实例在服务器生命周期内不变，缓存避免
     *  每 tick 每个玩家 getRecord 时重复 new Factory + computeIfAbsent（原实现每 tick 3+ 次垃圾分配）。 */
    private static volatile net.minecraft.server.MinecraftServer cachedServer;
    private static volatile PlayerSkillSavedData cachedData;

    public static PlayerSkillSavedData get(ServerLevel level) {
        net.minecraft.server.MinecraftServer server = level != null ? level.getServer() : null;
        if (server == null) {
            // ⚠️ 2026-09-10 防御：登出瞬间 / level 未挂服务器（多模组环境时序不可控）
            //    → 返回临时实例，绝不 NPE 崩玩家（数据不落盘，仅本 tick 兜底）
            return new PlayerSkillSavedData();
        }
        PlayerSkillSavedData cached = cachedData;
        if (cached != null && cachedServer == server) {
            return cached;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            // ⚠️ 2026-09-10 防御：原版保证主世界存在（缺失服务器会直接启动失败），
            //    此处仅为极端情况兜底——降级到第一个可用维度，再不行返回临时实例。
            for (ServerLevel l : server.getAllLevels()) {
                overworld = l;
                break;
            }
            if (overworld == null) {
                return new PlayerSkillSavedData();
            }
        }
        PlayerSkillSavedData data = overworld.getDataStorage().computeIfAbsent(TYPE);
        cachedServer = server;
        cachedData = data;
        return data;
    }

    public static PlayerSkillSavedData load(CompoundTag tag) {
        PlayerSkillSavedData data = new PlayerSkillSavedData();
        ListTag playersList = tag.getListOrEmpty("Players");
        for (int i = 0; i < playersList.size(); i++) {
            PlayerSkillRecord record = PlayerSkillRecord.load(playersList.getCompoundOrEmpty(i));
            // 防御：load 可能返回 null（Owner 缺失/损坏），跳过避免 NPE
            if (record != null) {
                data.players.put(record.getOwner(), record);
            }
        }
        ListTag machineList = tag.getListOrEmpty("MachineOwners");
        for (int i = 0; i < machineList.size(); i++) {
            CompoundTag machineTag = machineList.getCompoundOrEmpty(i);
            String key = machineTag.getStringOr("Key", "");
            // 26.1.2：hasUUID/getUUID 已移除 → 统一用 UUIDUtil 的 int[4] 表示（与 PlayerSkillRecord 一致）。
            // ⚠️ uuidFromIntArray 不做长度校验（越界会抛 AIOOBE），必须自己判 length == 4（旧版 hasUUID 的语义）。
            int[] ownerArr = machineTag.getIntArray("Owner").orElse(null);
            if (!key.isBlank() && ownerArr != null && ownerArr.length == 4) {
                data.machineOwnerMap.put(key, net.minecraft.core.UUIDUtil.uuidFromIntArray(ownerArr));
            }
        }
        return data;
    }

    public static CompoundTag save(PlayerSkillSavedData data) {
        CompoundTag tag = new CompoundTag();
        ListTag playersList = new ListTag();
        for (PlayerSkillRecord record : data.players.values()) {
            playersList.add(record.save());
        }
        tag.put("Players", playersList);

        ListTag machineList = new ListTag();
        for (Map.Entry<String, UUID> entry : data.machineOwnerMap.entrySet()) {
            CompoundTag machineTag = new CompoundTag();
            machineTag.putString("Key", entry.getKey());
            machineTag.putIntArray("Owner", net.minecraft.core.UUIDUtil.uuidToIntArray(entry.getValue()));
            machineList.add(machineTag);
        }
        tag.put("MachineOwners", machineList);
        return tag;
    }
}
