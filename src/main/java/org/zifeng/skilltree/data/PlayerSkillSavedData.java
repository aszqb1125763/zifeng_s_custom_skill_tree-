package org.zifeng.skilltree.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
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
    public static final String DATA_NAME = SkillTreeMod.MOD_ID + "_player_data";

    private final Map<UUID, PlayerSkillRecord> players = new HashMap<>();
    private final Map<String, UUID> machineOwnerMap = new HashMap<>();

    public PlayerSkillRecord getOrCreatePlayer(UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerSkillRecord::new);
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

    public static PlayerSkillSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(PlayerSkillSavedData::new, PlayerSkillSavedData::load), DATA_NAME);
    }

    public static PlayerSkillSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerSkillSavedData data = new PlayerSkillSavedData();
        ListTag playersList = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playersList.size(); i++) {
            PlayerSkillRecord record = PlayerSkillRecord.load(playersList.getCompound(i));
            // 防御：load 可能返回 null（Owner 缺失/损坏），跳过避免 NPE
            if (record != null) {
                data.players.put(record.getOwner(), record);
            }
        }
        ListTag machineList = tag.getList("MachineOwners", Tag.TAG_COMPOUND);
        for (int i = 0; i < machineList.size(); i++) {
            CompoundTag machineTag = machineList.getCompound(i);
            String key = machineTag.getString("Key");
            if (!key.isBlank() && machineTag.hasUUID("Owner")) {
                data.machineOwnerMap.put(key, machineTag.getUUID("Owner"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playersList = new ListTag();
        for (PlayerSkillRecord record : players.values()) {
            playersList.add(record.save());
        }
        tag.put("Players", playersList);

        ListTag machineList = new ListTag();
        for (Map.Entry<String, UUID> entry : machineOwnerMap.entrySet()) {
            CompoundTag machineTag = new CompoundTag();
            machineTag.putString("Key", entry.getKey());
            machineTag.putUUID("Owner", entry.getValue());
            machineList.add(machineTag);
        }
        tag.put("MachineOwners", machineList);
        return tag;
    }
}
