package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.client.screen.SkillTreeScreen;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 技能树数据（服务端 → 客户端）：技能点 + 已学 + 开关 + 生效等级 + 光环状态。
 */
public class SkillTreeDataS2CPacket {
    private final double skillPoints;
    private final Map<String, Integer> learnedSkills;
    private final Map<String, Boolean> toggles;
    private final Map<String, Integer> activeLevels;
    private final boolean auraEnabled;
    private final Map<String, Integer> auraTargetModes;
    private final String lootVacuumBind;
    private final int weatherMode;
    /** 木棍工具层（2026-09-08）：总开关 + 模式（0=BIND 1=RANGE） */
    private final boolean stickToolOn;
    private final int stickToolMode;
    /** 机械共鸣·操作区（2026-09-08）：技能ID → 操作区（客户端渲染框用） */
    private final Map<String, org.zifeng.skilltree.data.OperZone> operZones;
    /** 防护区多块列表（2026-09-08：客户端渲染全部防护区框用） */
    private final java.util.List<org.zifeng.skilltree.data.OperZone> protectZones;

    public SkillTreeDataS2CPacket(double skillPoints, Map<String, Integer> learnedSkills, Map<String, Boolean> toggles,
                                  Map<String, Integer> activeLevels, boolean auraEnabled, Map<String, Integer> auraTargetModes,
                                  String lootVacuumBind, int weatherMode, boolean stickToolOn, int stickToolMode,
                                  Map<String, org.zifeng.skilltree.data.OperZone> operZones,
                                  java.util.List<org.zifeng.skilltree.data.OperZone> protectZones) {
        this.skillPoints = skillPoints;
        this.learnedSkills = learnedSkills;
        this.toggles = toggles;
        this.activeLevels = activeLevels;
        this.auraEnabled = auraEnabled;
        this.auraTargetModes = auraTargetModes;
        this.lootVacuumBind = lootVacuumBind;
        this.weatherMode = weatherMode;
        this.stickToolOn = stickToolOn;
        this.stickToolMode = stickToolMode;
        this.operZones = operZones != null ? operZones : new HashMap<>();
        this.protectZones = protectZones != null ? protectZones : new java.util.ArrayList<>();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(skillPoints);
        buf.writeMap(learnedSkills, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
        buf.writeMap(toggles, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeBoolean);
        buf.writeMap(activeLevels, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
        buf.writeBoolean(auraEnabled);
        buf.writeMap(auraTargetModes, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
        buf.writeBoolean(lootVacuumBind != null);
        if (lootVacuumBind != null) {
            buf.writeUtf(lootVacuumBind);
        }
        buf.writeVarInt(weatherMode);
        buf.writeBoolean(stickToolOn);
        buf.writeVarInt(stickToolMode);
        buf.writeVarInt(operZones.size());
        for (Map.Entry<String, org.zifeng.skilltree.data.OperZone> e : operZones.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue().dim());
            buf.writeVarInt(e.getValue().ax());
            buf.writeVarInt(e.getValue().ay());
            buf.writeVarInt(e.getValue().az());
            buf.writeVarInt(e.getValue().bx());
            buf.writeVarInt(e.getValue().by());
            buf.writeVarInt(e.getValue().bz());
        }
        buf.writeVarInt(protectZones.size());
        for (org.zifeng.skilltree.data.OperZone z : protectZones) {
            buf.writeUtf(z.dim());
            buf.writeVarInt(z.ax());
            buf.writeVarInt(z.ay());
            buf.writeVarInt(z.az());
            buf.writeVarInt(z.bx());
            buf.writeVarInt(z.by());
            buf.writeVarInt(z.bz());
        }
    }

    public static SkillTreeDataS2CPacket decode(FriendlyByteBuf buf) {
        double skillPoints = buf.readDouble();
        Map<String, Integer> learnedSkills = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt);
        Map<String, Boolean> toggles = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readBoolean);
        Map<String, Integer> activeLevels = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt);
        boolean auraEnabled = buf.readBoolean();
        Map<String, Integer> auraTargetModes = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt);
        String lootVacuumBind = buf.readBoolean() ? buf.readUtf() : null;
        int weatherMode = buf.readVarInt();
        boolean stickToolOn = buf.readBoolean();
        int stickToolMode = buf.readVarInt();
        int zoneCount = buf.readVarInt();
        Map<String, org.zifeng.skilltree.data.OperZone> operZones = new HashMap<>();
        for (int i = 0; i < zoneCount; i++) {
            String skill = buf.readUtf();
            String dim = buf.readUtf();
            operZones.put(skill, new org.zifeng.skilltree.data.OperZone(dim,
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        int pzCount = buf.readVarInt();
        java.util.List<org.zifeng.skilltree.data.OperZone> protectZones = new java.util.ArrayList<>();
        for (int i = 0; i < pzCount; i++) {
            String dim = buf.readUtf();
            protectZones.add(new org.zifeng.skilltree.data.OperZone(dim,
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new SkillTreeDataS2CPacket(skillPoints, learnedSkills, toggles, activeLevels, auraEnabled, auraTargetModes, lootVacuumBind, weatherMode, stickToolOn, stickToolMode, operZones, protectZones);
    }

    /**
     * 从玩家技能记录构建数据包（统一便捷入口）。
     * <p><b>⚠️ 2026-09-10 修复（严重）：所有集合必须【深拷贝快照】。</b>
     * 原因：{@code record.getXxx()} 返回的是 {@code Collections.unmodifiableMap(...)} 的
     * <b>视图</b>而非拷贝；而本包 encode 在 <b>Netty 网络线程</b> 执行，服务端线程同时在改
     * 底层 HashMap（学技能/加点/切开关）→ 遍历时抛 {@code ConcurrentModificationException}
     * → 编码失败 → <b>玩家被踢下线</b>。
     */
    public static SkillTreeDataS2CPacket from(org.zifeng.skilltree.data.PlayerSkillRecord record) {
        // ⚠️ 2026-08-15 需求：光环状态只跟伤害光环（开关分离——速度只加速不决定是否攻击）
        boolean auraOn = record.getLearnedPoints(org.zifeng.skilltree.skill.Skills.AURA_DAMAGE) > 0
                && record.isEnabled(org.zifeng.skilltree.skill.Skills.AURA_DAMAGE);
        return new SkillTreeDataS2CPacket(record.getSkillPoints(),
                new java.util.HashMap<>(record.getLearnedSkills()),
                new java.util.HashMap<>(record.getToggles()),
                new java.util.HashMap<>(record.getActiveLevels()),
                auraOn,
                new java.util.HashMap<>(record.getAuraTargetModes()),
                record.hasLootVacuumBind()
                        ? record.getLootVacuumDim() + "|" + record.getLootVacuumName() + "|" + record.getLootVacuumX()
                        + "|" + record.getLootVacuumY() + "|" + record.getLootVacuumZ()
                        : null,
                record.getWeatherMode(), record.isStickToolOn(), record.getStickToolMode(),
                new java.util.HashMap<>(record.getOperZones()),
                new java.util.ArrayList<>(record.getProtectZones()));
    }

    public static void handle(SkillTreeDataS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            // 校准光环目标模式本地状态（各光环独立）
            org.zifeng.skilltree.client.ModKeyBindingEvents.setAuraTargetModes(packet.auraTargetModes);
            // 校准晴空环天气模式（2026-08-27：0=晴 1=雨 2=雷暴）
            org.zifeng.skilltree.client.ModKeyBindingEvents.setWeatherModeClient(packet.weatherMode);
            // 校准光环总开关本地状态（圆环渲染器判断是否显示淡红光环）
            org.zifeng.skilltree.client.ModKeyBindingEvents.setAuraEnabledClient(packet.auraEnabled);
            // 校准光环技能开关缓存（独立快捷键取反发送用）
            org.zifeng.skilltree.client.ModKeyBindingEvents.updateAuraToggles(packet.toggles);
            // 校准杀戮光环已学状态（圆环渲染防重置残留）
            org.zifeng.skilltree.client.ModKeyBindingEvents.updateAuraLearned(packet.learnedSkills);
            // 校准生效等级缓存（2026-08-13 第二快捷键循环等级用）
            org.zifeng.skilltree.client.ModKeyBindingEvents.updateActiveLevels(packet.activeLevels);
            // 校准磁力光环已学状态（蓝色圆环显示用）
            org.zifeng.skilltree.client.ModKeyBindingEvents.setMagnetLearnedClient(
                    packet.learnedSkills.getOrDefault(org.zifeng.skilltree.skill.Skills.AURA_MAGNET, 0) > 0);
            // 校准凋落物挪移绑定容器（技能树 tooltip 显示用）
            org.zifeng.skilltree.client.ModKeyBindingEvents.setLootVacuumBindClient(packet.lootVacuumBind);
            // 校准木棍工具层（2026-09-08：总开关 + 模式；渲染/手势路由/信息栏用）
            org.zifeng.skilltree.client.ModKeyBindingEvents.setStickToolStateClient(packet.stickToolOn, packet.stickToolMode);
            // 校准机械共鸣·操作区（2026-09-08：渲染操作区框用）
            org.zifeng.skilltree.client.ModKeyBindingEvents.setOperZonesClient(packet.operZones);
            // 校准防护区多块列表（2026-09-08：渲染全部防护区框用）
            org.zifeng.skilltree.client.ModKeyBindingEvents.setProtectZonesClient(packet.protectZones);
            // 校准技能点 HUD 常驻显示（2026-08-25：左下角总技能点绿色常驻）
            org.zifeng.skilltree.client.SkillPointHudRenderer.updateTotal(packet.skillPoints);
            // 只在技能树界面已打开时更新数据，绝不强制打开界面
            // （否则 K/L 键切换光环/目标时回发的数据包会把技能树界面弹出来）
            if (mc.screen instanceof SkillTreeScreen screen) {
                screen.updateData(packet.skillPoints, packet.learnedSkills, packet.toggles, packet.activeLevels, packet.auraEnabled, packet.auraTargetModes);
            }
        });
        ctx.setPacketHandled(true);
    }
}
