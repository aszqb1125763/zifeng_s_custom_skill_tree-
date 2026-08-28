package org.zifeng.skilltree.command;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;

import java.util.concurrent.CompletableFuture;

/**
 * 自动熔炼黑名单指令（2026-08-13 简化版）：
 * <ul>
 *   <li>/hmd           —— 直接回车：把手持物品加入黑名单</li>
 *   <li>/hmd + Tab     —— 补全当前手持物品的 id（如 minecraft:raw_gold），回车确认加入（粗矿也能快速添加）</li>
 *   <li>/delhmd &lt;id&gt; —— 从黑名单移除（Tab 只补全当前已加入黑名单的 id，不会误删/删错）</li>
 * </ul>
 */
public class ModCommands {

    /** /hmd 补全：只显示当前手持物品的 id（粗矿等不能放地上的掉落物专用） */
    private static final SuggestionProvider<CommandSourceStack> HELD_SUGGESTIONS = ModCommands::suggestHeld;
    /** /delhmd 补全：只显示已加入黑名单的物品 id */
    private static final SuggestionProvider<CommandSourceStack> BLACKLIST_SUGGESTIONS = ModCommands::suggestBlacklist;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /hmd：直接回车 → 手持添加；/hmd <id> → Tab 补全手持物品 id 后回车添加
        // 权限等级 0：所有玩家可用（无需作弊/op，含单人未开作弊）
        dispatcher.register(Commands.literal("hmd")
                .requires(source -> source.hasPermission(0))
                .executes(ModCommands::addHeldItem)
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests(HELD_SUGGESTIONS)
                        .executes(ctx -> modifyBlacklist(ctx, true))));
        dispatcher.register(Commands.literal("delhmd")
                .requires(source -> source.hasPermission(0))
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests(BLACKLIST_SUGGESTIONS)
                        .executes(ctx -> removeBlacklist(ctx))));
    }

    /** /hmd：把手持物品加入黑名单 */
    private static int addHeldItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("chat.zifeng_s_custom_skill_tree.hmd_empty"));
            return 0;
        }
        Item item = held.getItem();
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
        PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
        if (record.addAutoSmeltBlacklist(item)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("chat.zifeng_s_custom_skill_tree.hmd_added", id), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("chat.zifeng_s_custom_skill_tree.hmd_exists", id), false);
        }
        data.setDirty();
        return 1;
    }

    /** /hmd <id>：按参数添加（Tab 补全的是手持物品 id） */
    private static int modifyBlacklist(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        Item item = resolveItem(id);
        if (item == null || item == Items.AIR) {
            ctx.getSource().sendFailure(Component.translatable("chat.zifeng_s_custom_skill_tree.hmd_not_found", id));
            return 0;
        }
        PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
        PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
        if (record.addAutoSmeltBlacklist(item)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("chat.zifeng_s_custom_skill_tree.hmd_added", id), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("chat.zifeng_s_custom_skill_tree.hmd_exists", id), false);
        }
        data.setDirty();
        return 1;
    }

    /** /delhmd：从黑名单移除指定 id（物品或方块都行） */
    private static int removeBlacklist(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        Item item = resolveItem(id);
        if (item == null || item == Items.AIR) {
            ctx.getSource().sendFailure(Component.translatable("chat.zifeng_s_custom_skill_tree.hmd_not_found", id));
            return 0;
        }
        PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
        PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
        if (record.removeAutoSmeltBlacklist(item)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("chat.zifeng_s_custom_skill_tree.hmd_removed", id), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("chat.zifeng_s_custom_skill_tree.hmd_not_in", id), false);
        }
        data.setDirty();
        return 1;
    }

    /** 解析 id 为物品：优先物品注册表，其次方块注册表（方块 → asItem） */
    private static Item resolveItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item != null && item != Items.AIR) {
            return item;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block != null && block != Blocks.AIR) {
            Item blockItem = block.asItem();
            if (blockItem != Items.AIR) {
                return blockItem;
            }
        }
        return null;
    }

    /** /hmd 补全：只显示当前手持物品的 id（空手无建议；粗矿不能放地上也能快速添加） */
    private static CompletableFuture<Suggestions> suggestHeld(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            net.minecraft.world.item.ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                String s = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                if (s.startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(s);
                }
            }
        }
        return builder.buildFuture();
    }

    /** /delhmd 补全：只显示当前已加入黑名单的物品 id（方便直接 Tab 删除） */
    private static CompletableFuture<Suggestions> suggestBlacklist(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
            for (Item item : record.getAutoSmeltBlacklist()) {
                String s = BuiltInRegistries.ITEM.getKey(item).toString();
                if (s.startsWith(remaining)) {
                    builder.suggest(s);
                }
            }
        }
        return builder.buildFuture();
    }
}
