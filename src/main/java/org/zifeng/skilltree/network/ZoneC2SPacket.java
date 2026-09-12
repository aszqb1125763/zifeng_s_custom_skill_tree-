package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.OperZone;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

/**
 * 机械共鸣·木棍工具操作区（客户端 → 服务端，2026-09-08）：
 * <ul>
 *   <li>action 0-3：设置对应技能（放置/挖掘/攻击/防护）的操作区（携带维度+两角；允许单格区）</li>
 *   <li>action 20-23：清除对应技能（0-3 同序+20）的操作区</li>
 *   <li>action 30-33：微调对应技能（0-3 同序+30）的操作区（滚轮；bx=面序号、by=带符号步进）</li>
 *   <li>action 10：触发「选区放置」（手持物品标签，按触发键执行一次）</li>
 *   <li>action 11：触发「选区挖掘」（手持附魔工具标签，按触发键瞬间挖空全区）</li>
 * </ul>
 * 单人生效：操作区存该玩家 record；服务端做技能已学校验。
 */
public record ZoneC2SPacket(int action, String dim,
                            int ax, int ay, int az,
                            int bx, int by, int bz) implements CustomPacketPayload {
    public static final Type<ZoneC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "zone_op"));
    public static final StreamCodec<FriendlyByteBuf, ZoneC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ZoneC2SPacket decode(FriendlyByteBuf buf) {
            return new ZoneC2SPacket(buf.readVarInt(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buf, ZoneC2SPacket p) {
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

    public static void handle(ZoneC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                if (player.serverLevel() == null) {
                    return;
                }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                // ===== 清除操作区（action 20-23）=====
                if (packet.action() >= 20 && packet.action() <= 23) {
                    String skillId = switch (packet.action()) {
                        case 20 -> Skills.MACHINE_ZONE_PLACE;
                        case 21 -> Skills.MACHINE_ZONE_EXCAVATE;
                        case 22 -> Skills.MACHINE_ZONE_ATTACK;
                        default -> Skills.MACHINE_ZONE_PROTECT;
                    };
                    if (packet.action() == 23) {
                        // 防护区多块：按坐标删单块（客户端射线命中的那块）
                        record.removeProtectZoneAt(packet.dim(), packet.ax(), packet.ay(), packet.az());
                    } else {
                        record.setOperZone(skillId, null); // 单块技能：清除整块
                    }
                    data.setDirty();
                    org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                            SkillTreeDataS2CPacket.from(record));
                    return;
                }
                // ===== 设置操作区（action 0-3）=====
                if (packet.action() >= 0 && packet.action() <= 3) {
                    String skillId = switch (packet.action()) {
                        case 0 -> Skills.MACHINE_ZONE_PLACE;
                        case 1 -> Skills.MACHINE_ZONE_EXCAVATE;
                        case 2 -> Skills.MACHINE_ZONE_ATTACK;
                        default -> Skills.MACHINE_ZONE_PROTECT;
                    };
                    if (record.getLearnedPoints(skillId) <= 0) {
                        return; // 未学（框选是配置，仅要求已学——与磁铁选区同款）
                    }
                    String curDim = player.serverLevel().dimension().location().toString();
                    if (!curDim.equals(packet.dim())) {
                        return; // 维度必须=当前
                    }
                    int minX = Math.min(packet.ax(), packet.bx()), maxX = Math.max(packet.ax(), packet.bx());
                    int minY = Math.min(packet.ay(), packet.by()), maxY = Math.max(packet.ay(), packet.by());
                    int minZ = Math.min(packet.az(), packet.bz()), maxZ = Math.max(packet.az(), packet.bz());
                    if (maxX - minX > org.zifeng.skilltree.data.OperZone.MAX_SIDE
                            || maxY - minY > org.zifeng.skilltree.data.OperZone.MAX_SIDE
                            || maxZ - minZ > org.zifeng.skilltree.data.OperZone.MAX_SIDE) {
                        return;
                    }
                    if (packet.action() == 3) {
                        // 防护区多块：新增一块（上限 10，重复/满则忽略）
                        record.addProtectZone(new OperZone(curDim, minX, minY, minZ, maxX, maxY, maxZ));
                    } else {
                        record.setOperZone(skillId, new OperZone(curDim, minX, minY, minZ, maxX, maxY, maxZ));
                    }
                    data.setDirty();
                    // 回发校准（客户端渲染框）
                    org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                            SkillTreeDataS2CPacket.from(record));
                    return;
                }
                // ===== 微调操作区（action 30-33，木棍滚轮；2026-09-12 1.4.1）=====
                //   ax,ay,az = 射线命中到的区坐标（防护多块时用于定位；单块技能仅作冗余）
                //   bx = 面序号（Direction ordinal，即“正对你的近面”）
                //   by = 带符号步进（正=该面向外扩，负=向内缩）
                //   服务端权威计算新区（不直接信任客户端传来的一整块区）
                if (packet.action() >= 30 && packet.action() <= 33) {
                    int idx = packet.action() - 30;
                    String skillId = switch (idx) {
                        case 0 -> Skills.MACHINE_ZONE_PLACE;
                        case 1 -> Skills.MACHINE_ZONE_EXCAVATE;
                        case 2 -> Skills.MACHINE_ZONE_ATTACK;
                        default -> Skills.MACHINE_ZONE_PROTECT;
                    };
                    if (record.getLearnedPoints(skillId) <= 0) {
                        return; // 未学（框选/微调都是配置，仅要求已学）
                    }
                    String curDim = player.serverLevel().dimension().location().toString();
                    if (!curDim.equals(packet.dim())) {
                        return;
                    }
                    OperZone old = idx == 3
                            ? record.findProtectZoneAt(curDim, packet.ax(), packet.ay(), packet.az())
                            : record.getOperZone(skillId);
                    if (old == null) {
                        return;
                    }
                    int faceOrd = packet.bx();
                    if (faceOrd < 0 || faceOrd >= net.minecraft.core.Direction.values().length) {
                        return;
                    }
                    OperZone updated = old.adjust(
                            net.minecraft.core.Direction.values()[faceOrd], packet.by());
                    if (updated == null) {
                        // 非法：向外扩失败只可能是超单边上限；向内缩失败只可能是到最小（会翻转）
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                packet.by() > 0
                                        ? "chat.zifeng_s_custom_skill_tree.zone_adjust_limit"
                                        : "chat.zifeng_s_custom_skill_tree.zone_adjust_min",
                                String.valueOf(OperZone.MAX_SIDE)), true);
                        return;
                    }
                    if (idx == 3) {
                        record.replaceProtectZone(old, updated);
                    } else {
                        record.setOperZone(skillId, updated);
                    }
                    data.setDirty();
                    // 动作栏反馈新尺寸（玩家可看着数字微调）
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "chat.zifeng_s_custom_skill_tree.zone_adjust_size",
                            String.valueOf(updated.maxX() - updated.minX() + 1),
                            String.valueOf(updated.maxY() - updated.minY() + 1),
                            String.valueOf(updated.maxZ() - updated.minZ() + 1)), true);
                    org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                            SkillTreeDataS2CPacket.from(record));
                    return;
                }
                // ===== 触发 放置/挖掘 =====
                if (packet.action() == 10) {
                    org.zifeng.skilltree.event.ZoneSkillEvents.triggerPlace(player, record);
                } else if (packet.action() == 11) {
                    org.zifeng.skilltree.event.ZoneSkillEvents.triggerExcavate(player, record);
                }
            }
        });
    }
}
