package org.zifeng.skilltree.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 搬运术·指令式黑白名单（2026-10-19 新增）。
 * <p>通过静态块把命令注册挂到游戏事件总线（{@code NeoForge.EVENT_BUS}），由 {@link Config} 加载本类时触发，
 * 因此<b>无需改动主类 {@code SkillTreeMod}</b>，也避开了 neoforge 的 api 注解模块（JarJar 嵌套，难进编译 classpath）。
 * <p>与手填 toml 的 {@code containerHaulExtraWhitelist} / {@code containerHaulBlacklist} 是<b>同一份</b>数据：
 * 本指令只是这两个 {@code ConfigValue} 的运行时编辑器，{@code add}/{@code remove} 后<b>立即生效并落盘</b>，重启不丢。
 * 通配符语法同 {@link org.zifeng.skilltree.event.ContainerHaulPolicy} 的 {@code matches}
 * （{@code *} 任意长度、{@code ?} 单字符、不分大小写）。
 *
 * <p>用法（权限 2 / OP）：
 * <ul>
 *   <li>{@code /zifenghaul} —— 概览当前严格模式 / 黑白名单</li>
 *   <li>{@code /zifenghaul blacklist add <匹配式>} —— 加入搬运黑名单（优先级最高，永远不搬）</li>
 *   <li>{@code /zifenghaul blacklist remove <匹配式>}</li>
 *   <li>{@code /zifenghaul blacklist list}</li>
 *   <li>{@code /zifenghaul whitelist add|remove|list <匹配式>} —— 同黑名单，但可被内置黑名单覆盖</li>
 * </ul>
 * 例：{@code /zifenghaul blacklist add botanypots:*} （不搬植物盆栽）<br>
 *      {@code /zifenghaul whitelist add minecraft:*shulker_box*} （放行全部潜影盒）
 */
public final class HaulListCommands {
    private HaulListCommands() {
    }

    static {
        NeoForge.EVENT_BUS.<RegisterCommandsEvent>addListener(
                (RegisterCommandsEvent ev) -> register(ev.getDispatcher()));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zifenghaul")
                .requires(source -> source.hasPermission(2))
                .executes(HaulListCommands::executeSummary)
                .then(Commands.literal("blacklist")
                        .executes(ctx -> list(ctx, false))
                        .then(Commands.literal("add")
                                .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                        .executes(ctx -> modify(ctx, false, true))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                        .executes(ctx -> modify(ctx, false, false)))))
                .then(Commands.literal("whitelist")
                        .executes(ctx -> list(ctx, true))
                        .then(Commands.literal("add")
                                .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                        .executes(ctx -> modify(ctx, true, true))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                        .executes(ctx -> modify(ctx, true, false))))));
    }

    private static int executeSummary(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6[搬运名单] 严格模式: " + (Config.CONTAINER_HAUL_STRICT.get() ? "开" : "关")), false);
        source.sendSuccess(() -> Component.literal("§c黑名单(" + size(false) + "): " + join(false)), false);
        source.sendSuccess(() -> Component.literal("§a白名单(" + size(true) + "): " + join(true)), false);
        source.sendSuccess(() -> Component.literal("§7/zifenghaul <blacklist|whitelist> <add|remove|list> <匹配式>"), false);
        source.sendSuccess(() -> Component.literal("§7例: /zifenghaul blacklist add botanypots:*"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, boolean white) {
        CommandSourceStack source = ctx.getSource();
        String head = white ? "§a白名单" : "§c黑名单";
        source.sendSuccess(() -> Component.literal(head + "(" + size(white) + "): " + join(white)), false);
        return 1;
    }

    private static int modify(CommandContext<CommandSourceStack> ctx, boolean white, boolean add) {
        CommandSourceStack source = ctx.getSource();
        String pattern = StringArgumentType.getString(ctx, "pattern").trim();
        boolean changed = white
                ? (add ? Config.addHaulWhitelist(pattern) : Config.removeHaulWhitelist(pattern))
                : (add ? Config.addHaulBlacklist(pattern) : Config.removeHaulBlacklist(pattern));
        String kind = white ? "白名单" : "黑名单";
        String verb = add ? (changed ? "已添加" : "已存在") : (changed ? "已移除" : "不在名单中");
        source.sendSuccess(() -> Component.literal("§e[搬运名单] " + kind + " " + verb + ": " + pattern), false);
        return 1;
    }

    private static int size(boolean white) {
        return (white ? Config.CONTAINER_HAUL_EXTRA_WHITELIST : Config.CONTAINER_HAUL_BLACKLIST).get().size();
    }

    private static String join(boolean white) {
        return String.join(", ", (white ? Config.CONTAINER_HAUL_EXTRA_WHITELIST : Config.CONTAINER_HAUL_BLACKLIST).get());
    }
}
