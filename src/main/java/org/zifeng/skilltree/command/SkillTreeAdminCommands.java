package org.zifeng.skilltree.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.event.GiftEvents;
import org.zifeng.skilltree.event.UltimateEvents;
import org.zifeng.skilltree.network.SkillTreeDataS2CPacket;
import org.zifeng.skilltree.skill.SkillEffects;

import java.util.Collection;
import java.util.UUID;

/**
 * 玩家技能数据管理指令（2026-09-04，1.3.7 新增；参照原版 Passive Skill Tree 的 PSTCommands
 * 语义，适配本模组的 PlayerSkillSavedData 存档体系）：
 * <ul>
 *   <li><b>/skilltree reset &lt;player&gt;</b> —— 硬清空目标玩家的技能数据（全部技能移除 + 技能点归零，不返还）</li>
 *   <li><b>/skilltree points set &lt;player&gt; &lt;amount&gt;</b> —— 设置目标玩家技能点（0 = 归零）</li>
 *   <li><b>/skilltree points add &lt;player&gt; &lt;amount&gt;</b> —— 增加/扣除目标玩家技能点（负数 = 扣除）</li>
 * </ul>
 * 权限等级 2（OP / 服务器管理员 / 单机开作弊）。<br>
 * 目标玩家用 {@link GameProfileArgument}：支持在线选择器（@s/@p/@a 等）、玩家名（需在本服 usercache
 * 出现过）与 UUID —— 因此<b>可以清空离线玩家的数据</b>：离线只改主世界 SavedData，下次登录自动生效；
 * 在线玩家则即时移除属性修饰符/回收飞行/解除光环全局锁定并全量同步。
 */
public class SkillTreeAdminCommands {
    private static final String LANG = "chat.zifeng_s_custom_skill_tree.";
    private static final String PLAYER_ARGUMENT_NAME = "player";
    private static final String AMOUNT_ARGUMENT_NAME = "amount";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skilltree")
                .requires(source -> source.hasPermission(2))
                // /skilltree reset <player>
                .then(Commands.literal("reset")
                        .then(Commands.argument(PLAYER_ARGUMENT_NAME, GameProfileArgument.gameProfile())
                                .executes(SkillTreeAdminCommands::executeReset)))
                // /skilltree points set <player> <amount>
                .then(Commands.literal("points")
                        .then(Commands.literal("set")
                                .then(Commands.argument(PLAYER_ARGUMENT_NAME, GameProfileArgument.gameProfile())
                                        .then(Commands.argument(AMOUNT_ARGUMENT_NAME, IntegerArgumentType.integer(0))
                                                .executes(SkillTreeAdminCommands::executeSetPoints))))
                        // /skilltree points add <player> <amount>（负数 = 扣除）
                        .then(Commands.literal("add")
                                .then(Commands.argument(PLAYER_ARGUMENT_NAME, GameProfileArgument.gameProfile())
                                        .then(Commands.argument(AMOUNT_ARGUMENT_NAME, IntegerArgumentType.integer())
                                                .executes(SkillTreeAdminCommands::executeAddPoints))))));
    }

    /** /skilltree reset：硬清空目标玩家技能数据（全部技能 + 技能点归零，不返还） */
    private static int executeReset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        GameProfile profile = resolveProfile(ctx);
        if (profile == null || profile.getId() == null) {
            source.sendFailure(Component.translatable(LANG + "admin_player_not_found"));
            return 0;
        }
        UUID uuid = profile.getId();
        ServerLevel overworld = source.getServer().overworld();
        PlayerSkillSavedData data = PlayerSkillSavedData.get(overworld);
        PlayerSkillRecord record = data.getOrCreatePlayer(uuid);
        record.hardReset();
        data.setDirty();

        ServerPlayer online = source.getServer().getPlayerList().getPlayer(uuid);
        if (online != null) {
            // 顺序参照 SkillEvents.onPlayerLogout + ResetSkillC2SPacket（空 record 重挂 = 全移除）
            SkillEffects.applyAll(online, record); // 1) 移除全部技能属性修饰符
            UltimateEvents.resetFlyingSpeed(online); // 2) 还原原版飞行速度（防残留持久化）
            UltimateEvents.clearPlayerFlight(online); // 3) 回收技能飞行权限（非创造才关闭，不误关他模组飞行）
            UltimateEvents.clearPlayer(online); // 4) 清理终极被动 static 状态（连击/金身冷却等）
            AuraEvents.onPlayerLogout(online); // 5) 解除时之环/晴空环全局锁定计数并恢复 gamerule
            GiftEvents.onPlayerLogout(online); // 6) 清理子枫的馈赠在线计时
            PacketDistributor.sendToPlayer(online, SkillTreeDataS2CPacket.from(record)); // 7) 全量同步客户端
            source.sendSuccess(() -> Component.translatable(LANG + "admin_reset_done_online", displayName(profile)), false);
        } else {
            source.sendSuccess(() -> Component.translatable(LANG + "admin_reset_done_offline", displayName(profile)), false);
        }
        return 1;
    }

    /** /skilltree points set：设置目标玩家技能点 */
    private static int executeSetPoints(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        GameProfile profile = resolveProfile(ctx);
        if (profile == null || profile.getId() == null) {
            source.sendFailure(Component.translatable(LANG + "admin_player_not_found"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, AMOUNT_ARGUMENT_NAME);
        UUID uuid = profile.getId();
        PlayerSkillSavedData data = PlayerSkillSavedData.get(source.getServer().overworld());
        PlayerSkillRecord record = data.getOrCreatePlayer(uuid);
        record.setSkillPoints(amount);
        data.setDirty();
        syncIfOnline(source, uuid, record);
        source.sendSuccess(() -> Component.translatable(LANG + "admin_points_set", displayName(profile), fmt(record.getSkillPoints())), false);
        return 1;
    }

    /** /skilltree points add：增加/扣除目标玩家技能点（负数 = 扣除） */
    private static int executeAddPoints(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        GameProfile profile = resolveProfile(ctx);
        if (profile == null || profile.getId() == null) {
            source.sendFailure(Component.translatable(LANG + "admin_player_not_found"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, AMOUNT_ARGUMENT_NAME);
        UUID uuid = profile.getId();
        PlayerSkillSavedData data = PlayerSkillSavedData.get(source.getServer().overworld());
        PlayerSkillRecord record = data.getOrCreatePlayer(uuid);
        record.setSkillPoints(record.getSkillPoints() + amount);
        data.setDirty();
        syncIfOnline(source, uuid, record);
        String signed = amount >= 0 ? "+" + amount : String.valueOf(amount);
        source.sendSuccess(() -> Component.translatable(LANG + "admin_points_add", displayName(profile), signed, fmt(record.getSkillPoints())), false);
        return 1;
    }

    /** 目标玩家在线时全量同步技能数据（纯数值改动无需 applyAll，技能树缓存/UI 需要刷新） */
    private static void syncIfOnline(CommandSourceStack source, UUID uuid, PlayerSkillRecord record) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(uuid);
        if (online != null) {
            PacketDistributor.sendToPlayer(online, SkillTreeDataS2CPacket.from(record));
        }
    }

    /** 解析玩家参数：GameProfileArgument 支持 @选择器（在线）/玩家名（usercache）/UUID，失败返回 null */
    private static GameProfile resolveProfile(CommandContext<CommandSourceStack> ctx) {
        try {
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, PLAYER_ARGUMENT_NAME);
            if (profiles.isEmpty()) {
                return null;
            }
            return profiles.iterator().next();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    /** 显示名：优先玩家名，纯 UUID 参数时回退 UUID */
    private static String displayName(GameProfile profile) {
        String name = profile.getName();
        return name != null && !name.isBlank() ? name : profile.getId().toString();
    }

    /** 点数格式化：整数不带小数位（1 → "1"，1.5 → "1.5"） */
    private static String fmt(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
