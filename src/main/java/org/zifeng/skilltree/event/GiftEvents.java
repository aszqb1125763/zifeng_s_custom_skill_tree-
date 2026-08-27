package org.zifeng.skilltree.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.network.SkillTreeDataS2CPacket;
import org.zifeng.skilltree.skill.Skills;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 子枫的馈赠（2026-08-25）：按游戏行为自动获得技能点的技能系统。
 * <p>可叠加（各自独立累计，各自发放）：
 * <ul>
 *   <li>时间洗礼/风暴/洪流：按在线时长（每 10/5/1 分钟 +1 点）</li>
 *   <li>移动洗礼：统计行走+疾跑距离（1级每1000米/2级500米/3级100米 +1 点）</li>
 *   <li>飞行洗礼：统计飞行距离（1级每1000米/2级500米/3级100米 +1 点）</li>
 *   <li>挖掘洗礼：统计挖掘方块数（1级每1000块/2级500块/3级100块 +1 点）</li>
 * </ul>
 * 各洗礼增幅：每级让对应洗礼每次获得 +1 技能点。
 * <p>⚠️ 累计值只存内存（登出清理，重新进世界从 0 起算）；激活/升级状态存技能存档。
 * 时间类用 per-tick 累计；移动/飞行用原版统计差值（cm）；挖掘用 BlockEvent.BreakEvent 计数。
 */
public final class GiftEvents {
    private GiftEvents() {
    }

    /** 时间类累计：技能 → 玩家 UUID → 已累计 tick（在线计时） */
    private static final Map<String, Map<UUID, Long>> timeAcc = new HashMap<>();
    /** 距离类：技能 → 玩家 UUID → 上次统计值（cm） */
    private static final Map<String, Map<UUID, Long>> lastStat = new HashMap<>();
    /** 距离类：技能 → 玩家 UUID → 已累计距离（cm，达需求发点后扣减） */
    private static final Map<String, Map<UUID, Long>> distAcc = new HashMap<>();
    /** 挖掘：玩家 UUID → 已累计方块数（达需求发点后扣减） */
    private static final Map<UUID, Long> mineAcc = new HashMap<>();
    /** 击杀：玩家 UUID → 已累计击杀数（达需求发点后扣减） */
    private static final Map<UUID, Long> killAcc = new HashMap<>();
    /** 各洗礼上次开关状态（玩家 UUID → 技能 → 是否开启；2026-08-25：从开到关清累计，防残留立即触发） */
    private static final Map<UUID, Map<String, Boolean>> lastEnabled = new HashMap<>();

    // 常量化技能列表（2026-08-27 性能优化：原每 tick List.of 分配 4 次小数组）
    private static final List<String> TIME_SKILLS = List.of(Skills.GIFT_TIME_BAPTISM, Skills.GIFT_TIME_STORM, Skills.GIFT_TIME_FLOOD);
    private static final List<String> DISTANCE_SKILLS = List.of(Skills.GIFT_MOVE_BAPTISM, Skills.GIFT_FLY_BAPTISM);
    private static final List<String> ALL_BAPTISM_SKILLS = List.of(Skills.GIFT_MOVE_BAPTISM, Skills.GIFT_FLY_BAPTISM,
            Skills.GIFT_MINE_BAPTISM, Skills.GIFT_KILL_BAPTISM);

    /** 检测洗礼开关状态变化：关闭时清空该玩家对应累计（防重新开启立即触发残留） */
    private static void checkToggleChanged(PlayerSkillRecord record, UUID uuid) {
        Map<String, Boolean> last = lastEnabled.computeIfAbsent(uuid, k -> new HashMap<>());
        // 全部洗礼技能（含时间三件套）：从开到关 → 清累计
        for (String skill : ALL_BAPTISM_SKILLS) {
            boolean on = record.getLearnedPoints(skill) > 0 && record.isEnabled(skill);
            Boolean prev = last.put(skill, on);
            if (prev != null && prev && !on) {
                // 从开到关：清空该技能累计（时间/距离/挖掘/击杀）
                timeAcc.getOrDefault(skill, new HashMap<>()).remove(uuid);
                lastStat.getOrDefault(skill, new HashMap<>()).remove(uuid);
                distAcc.getOrDefault(skill, new HashMap<>()).remove(uuid);
                mineAcc.remove(uuid);
                killAcc.remove(uuid);
            }
        }
        // 时间三件套（风暴/洪流若关闭也清理；用常量 TIME_SKILLS 避免每 tick List.of 分配）
        for (String skill : TIME_SKILLS) {
            boolean on = record.getLearnedPoints(skill) > 0 && record.isEnabled(skill);
            Boolean prev = last.put(skill, on);
            if (prev != null && prev && !on) {
                timeAcc.getOrDefault(skill, new HashMap<>()).remove(uuid);
            }
        }
    }

    static {
        for (String skill : Skills.GIFT_SKILLS) {
            timeAcc.put(skill, new HashMap<>());
            lastStat.put(skill, new HashMap<>());
            distAcc.put(skill, new HashMap<>());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        // ⚠️ 2026-08-25：馈赠只对真玩家生效——FakePlayer（模拟玩家机器）不累计/不发放
        if (player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        UUID uuid = player.getUUID();

        // 开关状态检测：关闭洗礼 → 清累计（防重新开启立即触发）；每 20 tick 检测一次（性能优化）
        if (player.tickCount % 20 == 0) {
            checkToggleChanged(record, uuid);
        }

        // ============ 时间类（在线 tick 累计；2026-08-25：风暴每次 +5 点，洪流每次 +10 点，洗礼 +1 点保底） ============
        for (String skill : TIME_SKILLS) {
            if (!isActive(record, skill)) {
                continue;
            }
            long acc = timeAcc.get(skill).getOrDefault(uuid, 0L) + 1;
            long interval = Skills.getGiftIntervalTicks(skill);
            if (acc >= interval) {
                timeAcc.get(skill).remove(uuid);
                // 每次发放点数：时间洗礼 +1（保底） / 时间风暴 +5 / 时间洪流 +10
                int points = Skills.GIFT_TIME_FLOOD.equals(skill) ? 10
                        : Skills.GIFT_TIME_STORM.equals(skill) ? 5 : 1;
                grant(player, record, skill, points);
            } else {
                timeAcc.get(skill).put(uuid, acc);
            }
        }

        // ============ 距离类（移动/飞行：原版统计差值，cm） ============
        for (String skill : DISTANCE_SKILLS) {
            if (!isActive(record, skill)) {
                continue;
            }
            long cur = getStatCm(player, skill);
            long last = lastStat.get(skill).getOrDefault(uuid, cur);
            long delta = cur - last;
            lastStat.get(skill).put(uuid, cur);
            if (delta <= 0) {
                continue;
            }
            long acc = distAcc.get(skill).getOrDefault(uuid, 0L) + delta;
            int level = record.getActiveLevel(skill);
            long needCm = Skills.getGiftDistanceRequirement(skill, Math.max(1, level)) * 100; // 米 → cm
            if (needCm > 0 && acc >= needCm) {
                int grants = (int) (acc / needCm);
                acc -= needCm * grants;
                distAcc.get(skill).put(uuid, acc);
                grant(player, record, skill, grants);
            } else {
                distAcc.get(skill).put(uuid, acc);
            }
        }
    }

    /** 挖掘计数（BlockEvent.BreakEvent：真玩家挖掘方块时计数） */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        // ⚠️ 2026-08-25：机器（FakePlayer）挖掘不计数
        if (player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        if (!isActive(record, Skills.GIFT_MINE_BAPTISM)) {
            return;
        }
        UUID uuid = player.getUUID();
        long acc = mineAcc.getOrDefault(uuid, 0L) + 1;
        int level = record.getActiveLevel(Skills.GIFT_MINE_BAPTISM);
        long need = Skills.getGiftDistanceRequirement(Skills.GIFT_MINE_BAPTISM, Math.max(1, level)); // 方块数
        if (need > 0 && acc >= need) {
            int grants = (int) (acc / need);
            acc -= need * grants;
            mineAcc.put(uuid, acc);
            grant(player, record, Skills.GIFT_MINE_BAPTISM, grants);
        } else {
            mineAcc.put(uuid, acc);
        }
    }

    /** 玩家登出清理累计值（防残留，下次进世界重新计时） */
    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        for (Map<UUID, Long> map : timeAcc.values()) {
            map.remove(uuid);
        }
        for (Map<UUID, Long> map : lastStat.values()) {
            map.remove(uuid);
        }
        for (Map<UUID, Long> map : distAcc.values()) {
            map.remove(uuid);
        }
        mineAcc.remove(uuid);
        killAcc.remove(uuid);
        lastEnabled.remove(uuid);
    }

    /** 击杀馈赠计数（LivingDeathEvent：真玩家击杀生物时计数） */
    @SubscribeEvent
    public static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getSource() == null) {
            return;
        }
        // 直接击杀者（武器/空手）或间接（箭等投射物射手）是真玩家才计数
        if (!(event.getSource().getDirectEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                && !(event.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player2)) {
            return;
        }
        net.minecraft.server.level.ServerPlayer killer = event.getSource().getDirectEntity() instanceof net.minecraft.server.level.ServerPlayer p
                ? p : (net.minecraft.server.level.ServerPlayer) event.getSource().getEntity();
        // ⚠️ 2026-08-25：机器（FakePlayer）击杀不计数
        if (killer instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        PlayerSkillRecord record = getRecord(killer);
        if (!isActive(record, Skills.GIFT_KILL_BAPTISM)) {
            return;
        }
        UUID uuid = killer.getUUID();
        long acc = killAcc.getOrDefault(uuid, 0L) + 1;
        int level = record.getActiveLevel(Skills.GIFT_KILL_BAPTISM);
        long need = Skills.getGiftDistanceRequirement(Skills.GIFT_KILL_BAPTISM, Math.max(1, level));
        if (need > 0 && acc >= need) {
            int grants = (int) (acc / need);
            acc -= need * grants;
            killAcc.put(uuid, acc);
            grant(killer, record, Skills.GIFT_KILL_BAPTISM, grants);
        } else {
            killAcc.put(uuid, acc);
        }
    }

    // ============ 辅助 ============

    /** 技能已学且开关开启 */
    private static boolean isActive(PlayerSkillRecord record, String skillId) {
        return record.getLearnedPoints(skillId) > 0 && record.isEnabled(skillId);
    }

    /** 发放技能点：基础 1 + 对应增幅等级（增幅需已学且开启，取生效等级） */
    private static void grant(ServerPlayer player, PlayerSkillRecord record, String baptismSkill, int times) {
        String ampSkill = Skills.getGiftAmpSkill(baptismSkill);
        int amp = 0;
        if (ampSkill != null && record.getLearnedPoints(ampSkill) > 0 && record.isEnabled(ampSkill)) {
            amp = record.getActiveLevel(ampSkill);
        }
        int perGrant = 1 + amp;
        int total = perGrant * times;
        record.addSkillPoints(total);
        // ⚠️ 2026-08-25：每次发放立即显示（覆盖式，不合并）——馈赠来源专用行每次增加都显示
        java.util.Map<String, Double> rates = new HashMap<>();
        rates.put("🎁" + Skills.getDisplayName(baptismSkill), (double) total);
        org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                new org.zifeng.skilltree.network.SkillPointRateS2CPacket(record.getSkillPoints(), rates));
        org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                SkillTreeDataS2CPacket.from(record));
    }

    /** 取统计距离（cm）：移动 = 行走 + 疾跑；飞行 = 飞行 */
    private static long getStatCm(ServerPlayer player, String skill) {
        if (Skills.GIFT_MOVE_BAPTISM.equals(skill)) {
            return player.getStats().getValue(Stats.CUSTOM, Stats.WALK_ONE_CM)
                    + player.getStats().getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM);
        }
        if (Skills.GIFT_FLY_BAPTISM.equals(skill)) {
            return player.getStats().getValue(Stats.CUSTOM, Stats.FLY_ONE_CM);
        }
        return 0;
    }

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : java.util.UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
