package org.zifeng.skilltree.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.event.GiftEvents;
import org.zifeng.skilltree.event.UltimateEvents;
import org.zifeng.skilltree.network.ModNetwork;
import org.zifeng.skilltree.network.SkillTreeDataS2CPacket;
import org.zifeng.skilltree.skill.SkillEffects;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家技能数据管理指令（2026-09-04，1.3.7 新增；参照原版 Passive Skill Tree 的 PSTCommands
 * 语义，适配本模组的 PlayerSkillSavedData 存档体系）：
 * <ul>
 *   <li><b>/zifengskilltree reset &lt;player&gt;</b> —— 硬清空目标玩家的技能数据（全部技能移除 + 技能点归零，不返还）</li>
 *   <li><b>/zifengskilltree points set &lt;player&gt; &lt;amount&gt;</b> —— 设置目标玩家技能点（0 = 归零）</li>
 *   <li><b>/zifengskilltree points add &lt;player&gt; &lt;amount&gt;</b> —— 增加/扣除目标玩家技能点（负数 = 扣除）</li>
 * </ul>
 * 权限等级 2（OP / 服务器管理员 / 单机开作弊）。<br>
 * 目标玩家用 {@link GameProfileArgument}：支持在线选择器（@s/@p/@a 等）、玩家名（需在本服 usercache
 * 出现过）与 UUID —— 因此<b>可以清空离线玩家的数据</b>：离线只改主世界 SavedData，下次登录自动生效；
 * 在线玩家则即时移除属性修饰符/回收飞行/解除光环全局锁定并全量同步。
 *
 * <p>⚠️ <b>2026-09-30 指令改名（修复与 Passive Skill Tree 的冲突）</b>：
 * 原指令根为 {@code /skilltree}，而 Passive Skill Tree 用的也是 {@code /skilltree}
 * 且子指令路径<b>逐字相同</b>（{@code reset} / {@code points set} / {@code points add}）——
 * Brigadier 对同名节点是<b>合并</b>的，先注册者优先，导致安装两个模组时
 * {@code /skilltree points add} 落到本模组上（PST 的技能点不加、加的是本模组的）。
 * 现改为独立命名空间 {@code /zifengskilltree} 彻底避开。
 * <p>⚠️ <b>不能再保留 {@code /skilltree} 作为兼容别名</b>：只要还注册该名字，
 * 合并冲突就依旧存在（这正是本 bug 的成因）。改后 {@code /skilltree} 完全归 Passive Skill Tree。
 */
public class SkillTreeAdminCommands {
    private static final String LANG = "chat.zifeng_s_custom_skill_tree.";
    private static final String PLAYER_ARGUMENT_NAME = "player";
    private static final String AMOUNT_ARGUMENT_NAME = "amount";
    /** 掉落黑名单条目的参数名（物品 id 或 #标签） */
    private static final String ENTRY_ARGUMENT_NAME = "entry";

    /**
     * 指令帮助的行顺序（★ 2026-09-30 新增）。
     * <p>文本全部走语言文件（{@code chat.zifeng_s_custom_skill_tree.help_*}），
     * 会跟随游戏语言设置自动切中/英 —— 不要在 Java 里写任何显示文本。
     */
    private static final String[] HELP_KEYS = {
            "help_title",
            "help_l_help",
            "help_l_reset",
            "help_l_points_set",
            "help_l_points_add",
            "help_l_hmd",
            "help_l_delhmd",
            "help_l_lootbl",
            "help_hint"
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zifengskilltree")
                // ⚠️ 2026-09-30：根节点权限改为 0（所有人可用）—— 这样【帮助】对普通玩家开放；
                //    管理子指令各自再声明 requires(2)，权限语义与改动前完全一致。
                .requires(source -> source.hasPermission(0))
                // 裸 /zifengskilltree → 直接显示帮助（对玩家友好，不用记子指令名）
                .executes(SkillTreeAdminCommands::executeHelp)
                // /zifengskilltree help
                .then(Commands.literal("help")
                        .executes(SkillTreeAdminCommands::executeHelp))
                // /zifengskilltree reset <player>（权限 2）
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument(PLAYER_ARGUMENT_NAME, GameProfileArgument.gameProfile())
                                .executes(SkillTreeAdminCommands::executeReset)))
                // /zifengskilltree points set|add（权限 2）
                .then(Commands.literal("points")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument(PLAYER_ARGUMENT_NAME, GameProfileArgument.gameProfile())
                                        .then(Commands.argument(AMOUNT_ARGUMENT_NAME, IntegerArgumentType.integer(0))
                                                .executes(SkillTreeAdminCommands::executeSetPoints))))
                        // /zifengskilltree points add <player> <amount>（负数 = 扣除）
                        .then(Commands.literal("add")
                                .then(Commands.argument(PLAYER_ARGUMENT_NAME, GameProfileArgument.gameProfile())
                                        .then(Commands.argument(AMOUNT_ARGUMENT_NAME, IntegerArgumentType.integer())
                                                .executes(SkillTreeAdminCommands::executeAddPoints)))))
                // /zifengskilltree lootblacklist [add|remove] <物品id 或 #标签>（★ 2026-09-30）
                // 权限 0：这是玩家自己的掉落过滤配置，不需 OP
                .then(Commands.literal("lootblacklist")
                        .executes(SkillTreeAdminCommands::executeLootBlacklistList)
                        .then(Commands.literal("add")
                                .then(Commands.argument(ENTRY_ARGUMENT_NAME, StringArgumentType.greedyString())
                                        .suggests(SkillTreeAdminCommands::suggestHeldItemAndTags)
                                        .executes(ctx -> modifyLootBlacklist(ctx, true))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ENTRY_ARGUMENT_NAME, StringArgumentType.greedyString())
                                        .suggests(SkillTreeAdminCommands::suggestLootBlacklist)
                                        .executes(ctx -> modifyLootBlacklist(ctx, false))))));
    }

    /**
     * 显示本模组的指令帮助（★ 2026-09-30 新增）。
     *
     * <p>逐行发送，每行一个 {@code Component.translatable} → 自动跟随客户端游戏语言
     * （中文客户看中文，英文客户看英文）。权限 0，普通玩家也能看。
     */
    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        for (String key : HELP_KEYS) {
            source.sendSuccess(() -> Component.translatable(LANG + key), false);
        }
        return 1;
    }

    // ══════════ 战利品大爆发·掉落黑名单指令（★ 2026-09-30）══════════
    //   用于「战利品大爆发」模式 3（自定义黑名单）：命中黑名单的掉落不参与爆发。
    //   条目两种形式：① 物品 id（ns:path）② 标签（#ns:path，含 forge: 等通用标签）。
    //   数据存在 PlayerSkillRecord（每玩家），与 /hmd 同一套思路。

    /**
     * {@code add} 的候选框：当前【手持物品】的注册名 + 它的【全部标签】（★ 用户重点要求）。
     *
     * <p>一个物品会带很多标签（含 {@code forge:} 等通用标签），全部列出并去重，
     * 玩家可直接用 id 或任一标签添加。非玩家执行（命令方块/控制台）时不给候选。
     */
    private static CompletableFuture<Suggestions> suggestHeldItemAndTags(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        // ⚠️ 2026-09-30：参数是 greedyString——word() 的字符集不允许【:】与【#】，
        //    所以 /... lootblacklist add #forge:tools 会直接报错（红字），必须换掉。
        //    这里按已输入前缀过滤（标签很多，不过滤会刷屏）。
        final String typed = builder.getRemainingLowerCase();
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                // ① 物品注册名
                String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                if (id.toLowerCase(java.util.Locale.ROOT).startsWith(typed)) {
                    builder.suggest(id);
                }
                // ② 全部标签（带 # 前缀；同一标签不会重复出现，ItemStack.getTags() 本身去重）
                java.util.Iterator<net.minecraft.tags.TagKey<Item>> it = held.getTags().iterator();
                while (it.hasNext()) {
                    String tag = "#" + it.next().location();
                    if (tag.toLowerCase(java.util.Locale.ROOT).startsWith(typed)) {
                        builder.suggest(tag);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 取不到玩家（控制台/命令方块）→ 不给候选，手输也能用
        }
        return builder.buildFuture();
    }

    /** {@code remove} 的候选框：当前黑名单里的现有条目 */
    private static CompletableFuture<Suggestions> suggestLootBlacklist(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        final String typed = builder.getRemainingLowerCase();
        PlayerSkillRecord record = lootBlacklistRecord(ctx);
        if (record != null) {
            for (String e : record.getLootBlacklist()) {
                if (e.toLowerCase(java.util.Locale.ROOT).startsWith(typed)) {
                    builder.suggest(e);
                }
            }
        }
        return builder.buildFuture();
    }

    /** 取执行玩家的技能记录（非玩家/无 serverLevel 返回 null） */
    private static PlayerSkillRecord lootBlacklistRecord(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 把玩家输入规范化成黑名单条目。
     *
     * <ul>
     *   <li>{@code #ns:path} → 标签，只校验格式（标签是否真实存在无法廉价校验，匹配时自然失效）；</li>
     *   <li>{@code ns:path} → 物品 id，要求注册表里真的存在（顺手支持方块 id → asItem，与 /hmd 一致），
     *       并存成【规范化后的物品注册名】以保证匹配命中。</li>
     * </ul>
     *
     * @return 规范化条目；非法返回 null
     */
    private static String normalizeLootEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(s.substring(1));
            return tag == null ? null : "#" + tag;
        }
        ResourceLocation loc = ResourceLocation.tryParse(s);
        if (loc == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(loc);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            // 方块 id 兼容（如 minecraft:stone）——与 /hmd 的 resolveItem 一致
            var block = BuiltInRegistries.BLOCK.get(loc);
            item = (block == null) ? null : block.asItem();
        }
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** 新增/移除一条掉落黑名单（add=true 新增） */
    private static int modifyLootBlacklist(CommandContext<CommandSourceStack> ctx, boolean add) {
        CommandSourceStack source = ctx.getSource();
        String raw = StringArgumentType.getString(ctx, ENTRY_ARGUMENT_NAME);
        String entry = normalizeLootEntry(raw);
        if (entry == null) {
            source.sendFailure(Component.translatable(LANG + "lootbl_bad_entry", raw));
            return 0;
        }
        PlayerSkillRecord record = lootBlacklistRecord(ctx);
        if (record == null) {
            return 0;
        }
        final String shown = entry;
        if (add) {
            boolean added = record.addLootBlacklist(entry);
            source.sendSuccess(() -> Component.translatable(
                    LANG + (added ? "lootbl_added" : "lootbl_exists"), shown), false);
        } else {
            boolean removed = record.removeLootBlacklist(entry);
            source.sendSuccess(() -> Component.translatable(
                    LANG + (removed ? "lootbl_removed" : "lootbl_not_in"), shown), false);
        }
        return 1;
    }

    /** 无参数时列出当前黑名单 */
    private static int executeLootBlacklistList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        PlayerSkillRecord record = lootBlacklistRecord(ctx);
        if (record == null) {
            return 0;
        }
        var entries = record.getLootBlacklist();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(LANG + "lootbl_empty"), false);
        } else {
            source.sendSuccess(() -> Component.translatable(LANG + "lootbl_list",
                    entries.size(), String.join(", ", entries)), false);
        }
        return 1;
    }

    /** /zifengskilltree reset：硬清空目标玩家技能数据（全部技能 + 技能点归零，不返还） */
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
            ModNetwork.sendToPlayer(online, SkillTreeDataS2CPacket.from(record)); // 7) 全量同步客户端
            source.sendSuccess(() -> Component.translatable(LANG + "admin_reset_done_online", displayName(profile)), false);
        } else {
            source.sendSuccess(() -> Component.translatable(LANG + "admin_reset_done_offline", displayName(profile)), false);
        }
        return 1;
    }

    /** /zifengskilltree points set：设置目标玩家技能点 */
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

    /** /zifengskilltree points add：增加/扣除目标玩家技能点（负数 = 扣除） */
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
            ModNetwork.sendToPlayer(online, SkillTreeDataS2CPacket.from(record));
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
