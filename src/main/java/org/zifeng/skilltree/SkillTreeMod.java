package org.zifeng.skilltree;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.zifeng.skilltree.client.ClientRegistrar;
import org.zifeng.skilltree.event.MagnetEvents;
import org.zifeng.skilltree.event.SkillEvents;
import org.zifeng.skilltree.event.UltimateEvents;
import org.zifeng.skilltree.init.ModBlockEntities;
import org.zifeng.skilltree.init.ModBlocks;
import org.zifeng.skilltree.init.ModAttributes;
import org.zifeng.skilltree.init.ModCapabilities;
import org.zifeng.skilltree.init.ModCreativeTabs;
import org.zifeng.skilltree.init.ModItems;
import org.zifeng.skilltree.init.ModMenus;
import org.zifeng.skilltree.network.ModNetwork;

@Mod(SkillTreeMod.MOD_ID)
public class SkillTreeMod {
    public static final String MOD_ID = "zifeng_s_custom_skill_tree";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkillTreeMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        org.zifeng.skilltree.init.ModDamageTypes.DAMAGE_TYPES.register(modEventBus);

        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(ModCapabilities::registerCapabilities);
        modEventBus.addListener(SkillEvents::registerPlayerAttributes);
        // Flux-Networks long 能量兼容（2026-08-25：未装 Flux 时安全跳过）
        org.zifeng.skilltree.compat.FluxCompat.register(modEventBus);

        // 自动熔炼黑名单指令（/hmd 添加、/delhmd 删除，2026-08-13 恢复）
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                org.zifeng.skilltree.command.ModCommands.register(event.getDispatcher()));

        // 玩家技能数据管理指令（/skilltree reset | points set | points add，2026-09-04 1.3.7）
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                org.zifeng.skilltree.command.SkillTreeAdminCommands.register(event.getDispatcher()));

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Config 热重载/加载后重挂在线玩家属性（2026-08-29：属性每点加成进 Config 后需即时生效）
        modEventBus.addListener(Config::onConfigChanged);

        NeoForge.EVENT_BUS.register(SkillEvents.class);
        NeoForge.EVENT_BUS.register(UltimateEvents.class);
        // ⚠️ 2026-09-09 Z-Link 迁移后 AuraEvents/ZoneSkillEvents 已无 @SubscribeEvent 方法，
        //    NeoForge 注册空类会崩（has no @SubscribeEvent methods）→ 不再 register（逻辑已由 ZModules 模块接管）。
        NeoForge.EVENT_BUS.register(MagnetEvents.class);
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.event.LockEvents.class);
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.event.LootVacuumEvents.class);
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.event.ContainerHaulEvents.class);
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.event.GiftEvents.class);
        // 主系统 Tick 末合并推送（2026-08-28 架构升级：一 tick 内多次 markDirty → 末尾合并成一次）
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
            org.zifeng.skilltree.GlobalStateSync.onServerTickEnd();
        });
        // Z-Link 模块登记（2026-09-09）：集中登记所有功能模块（调度器按条件驱动/冬眠）。
        // 【系统清单】每迁一个技能在此 +1 行；未登记 = 该技能仍走原事件（调度零影响）。
        //   · 磁力光环（磁铁吸物/吸经验）—— 已迁 MagnetModule
        //   · 杀戮光环·伤害（含虚空之矛/强化）—— 已迁 AuraDamageModule
        //   · 治愈光环 —— 已迁 AuraHealModule
        //   · 汲灵之环 —— 已迁 AuraXpModule
        //   · 终极节点/常驻效果 tick 链 —— 已迁 UltimateTickModule（2026-09-09）
        //   · 子枫的馈赠（时间/移动/飞行累计）—— 已迁 GiftModule（2026-09-09）
        //   · 机械共鸣·选区攻击 —— 已迁 ZoneAttackModule（2026-09-09）
        //   · 时之环/晴空环（全局 gamerule 锁定）—— 已迁 GlobalRuleModule（2026-09-09）
        org.zifeng.skilltree.system.ZModules.registerAll(
                org.zifeng.skilltree.system.MagnetModule.INSTANCE,
                org.zifeng.skilltree.system.AuraDamageModule.INSTANCE,
                org.zifeng.skilltree.system.AuraHealModule.INSTANCE,
                org.zifeng.skilltree.system.AuraXpModule.INSTANCE,
                org.zifeng.skilltree.system.UltimateTickModule.INSTANCE,
                org.zifeng.skilltree.system.GiftModule.INSTANCE,
                org.zifeng.skilltree.system.ZoneAttackModule.INSTANCE,
                org.zifeng.skilltree.system.GlobalRuleModule.INSTANCE);
        // Z-Link 模块调度器（2026-09-09 骨架 v1）：每服务器 tick 末驱动一次全部已登记模块
        // （条件不满足的模块自动冬眠，零开销；未登记模块时整个调度直接 return）。
        // ⚠️ 调度器只负责"叫醒该跑的模块"，现有技能逻辑未迁移前不改变任何行为。
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
            net.minecraft.server.MinecraftServer server = event.getServer();
            if (server != null && server.overworld() != null) {
                org.zifeng.skilltree.system.ZModules.onServerTick(server.overworld());
            }
        });
        // Z-Link 玩家登出清理（2026-09-09）：广播模块清理该玩家临时状态（防跨服残留）
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                org.zifeng.skilltree.system.ZModules.onPlayerLogout(sp);
            }
        });
        // Z-Link 服务器停止清理（2026-09-09）：广播模块清理全局状态 + 清空注册表
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
            org.zifeng.skilltree.system.ZModules.onServerStop();
        });

        if (FMLLoader.getDist().isClient()) {
            ClientRegistrar.register(modEventBus);
        }
        LOGGER.info("[{}] 模组加载完成！", MOD_ID);
    }
}
