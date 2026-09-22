package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 磁铁屏蔽区操作（客户端 → 服务端，2026-09-07 / 2026-09-08 全局共享）：
 * <ul>
 *   <li>action=0 ADD：客户端已选两角 → 服务端写入【全服全局】屏蔽区（任何磁铁玩家都吸不了区内）</li>
 *   <li>action=1 REMOVE：客户端潜行+左键对着一屏蔽区 → 服务端按玩家视线射线从全局删除命中区</li>
 *   <li>action=2 ADJUST（★ 2026-09-22 新增）：木棍滚轮微调一个屏蔽区的单个面，
 *       与机械共鸣区块技能同款；客户端只陈述「改哪个区、哪个面、多少格」，服务端权威计算</li>
 * </ul>
 * 校验：磁铁已学 + 工具开 + RANGE 模块；增删后 MagnetZoneGlobalData 自动持久化 + 全服广播。
 */
public record MagnetExclusionC2SPacket(int action, String dim,
                                       int ax, int ay, int az,
                                       int bx, int by, int bz) implements CustomPacketPayload {
    public static final Type<MagnetExclusionC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "magnet_exclusion"));
    /** 手写编解码（8 字段 composite 泛型推断易失败，与 S2C 同款写法） */
    public static final StreamCodec<FriendlyByteBuf, MagnetExclusionC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MagnetExclusionC2SPacket decode(FriendlyByteBuf buf) {
            return new MagnetExclusionC2SPacket(buf.readVarInt(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buf, MagnetExclusionC2SPacket p) {
            buf.writeVarInt(p.action());
            buf.writeUtf(p.dim());
            buf.writeVarInt(p.ax());
            buf.writeVarInt(p.ay());
            buf.writeVarInt(p.az());
            buf.writeVarInt(p.bx());
            buf.writeVarInt(p.by());
            buf.writeVarInt(p.bz());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MagnetExclusionC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                if (player.serverLevel() == null) {
                    return;
                }
                org.zifeng.skilltree.data.PlayerSkillRecord record =
                        org.zifeng.skilltree.data.PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
                // 技能校验：磁铁需已学 + 工具开 + RANGE 模块（服务端权威）
                if (record.getLearnedPoints(org.zifeng.skilltree.skill.Skills.AURA_MAGNET) <= 0
                        || !record.isStickToolOn()
                        || record.getStickToolMode() != org.zifeng.skilltree.skill.Skills.STICK_MODE_RANGE) {
                    return;
                }
                org.zifeng.skilltree.data.MagnetZoneGlobalData global =
                        org.zifeng.skilltree.data.MagnetZoneGlobalData.get(player.serverLevel());
                String currentDim = player.serverLevel().dimension().location().toString();
                if (packet.action() == 0) {
                    // ADD：维度必须与玩家当前所在维度一致（防作弊乱存他维区）
                    if (!currentDim.equals(packet.dim())) {
                        return;
                    }
                    // ⚠️ 2026-09-12（1.4.1）新增服务端权威校验：单边 ≤ OperZone.MAX_SIDE
                    //    （原先只靠客户端本地校验 → 改造客户端可存超大区；与区块技能校验对齐）
                    int zMinX = Math.min(packet.ax(), packet.bx()), zMaxX = Math.max(packet.ax(), packet.bx());
                    int zMinY = Math.min(packet.ay(), packet.by()), zMaxY = Math.max(packet.ay(), packet.by());
                    int zMinZ = Math.min(packet.az(), packet.bz()), zMaxZ = Math.max(packet.az(), packet.bz());
                    if (zMaxX - zMinX > org.zifeng.skilltree.data.OperZone.MAX_SIDE
                            || zMaxY - zMinY > org.zifeng.skilltree.data.OperZone.MAX_SIDE
                            || zMaxZ - zMinZ > org.zifeng.skilltree.data.OperZone.MAX_SIDE) {
                        return;
                    }
                    global.addZone(packet.dim(),
                            packet.ax(), packet.ay(), packet.az(),
                            packet.bx(), packet.by(), packet.bz()); // 内部处理持久化+全服广播
                } else if (packet.action() == 1) {
                    // REMOVE：服务端按玩家当前视线做射线删除（不信任客户端传来的删除对象/维度）
                    net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
                    net.minecraft.world.phys.Vec3 look = player.getLookAngle();
                    global.removeZoneAt(currentDim, eye, look); // 内部处理持久化+全服广播
                } else if (packet.action() == 2) {
                    // ★ 2026-09-22 ADJUST（木棍滚轮微调单个面）：
                    //   ax,ay,az = 要调整的区的最小角（客户端命中判定后定位用）
                    //   bx = 面序号（Direction ordinal，即“正对你的近面”）
                    //   by = 带符号步进（正 = 该面向外扩，负 = 向内缩）
                    if (!currentDim.equals(packet.dim())) {
                        return; // 只能改当前维度的区
                    }
                    int faceOrd = packet.bx();
                    if (faceOrd < 0 || faceOrd >= net.minecraft.core.Direction.values().length) {
                        return;
                    }
                    net.minecraft.core.Direction face = net.minecraft.core.Direction.values()[faceOrd];
                    int steps = packet.by();
                    if (steps == 0) {
                        return;
                    }
                    org.zifeng.skilltree.data.MagnetExclusionZone updated = global.adjustZoneAt(
                            currentDim, packet.ax(), packet.ay(), packet.az(), face, steps);
                    if (updated == null) {
                        // 失败分支：区分「越扩越大到上限」与「越缩越小到最小」
                        //   向外扩失败只可能是超单边上限；向内缩失败只可能是到最小（会翻转）
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                steps > 0
                                        ? "chat.zifeng_s_custom_skill_tree.zone_adjust_limit"
                                        : "chat.zifeng_s_custom_skill_tree.zone_adjust_min",
                                String.valueOf(org.zifeng.skilltree.data.OperZone.MAX_SIDE)), true);
                        return;
                    }
                    // 成功：动作栏反馈新尺寸（与区块技能同款文案）
                    //   注：adjustZoneAt 内部已持久化 + 全服广播，此处无需额外同步
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "chat.zifeng_s_custom_skill_tree.zone_adjust_size",
                            String.valueOf(updated.maxX() - updated.minX() + 1),
                            String.valueOf(updated.maxY() - updated.minY() + 1),
                            String.valueOf(updated.maxZ() - updated.minZ() + 1)), true);
                }
            }
        });
    }
}
