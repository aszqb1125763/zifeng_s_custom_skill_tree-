package org.zifeng.skilltree;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.zifeng.skilltree.client.ClientRegistrar;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.event.GiftEvents;
import org.zifeng.skilltree.event.LockEvents;
import org.zifeng.skilltree.event.LootVacuumEvents;
import org.zifeng.skilltree.event.MagnetEvents;
import org.zifeng.skilltree.event.SkillEvents;
import org.zifeng.skilltree.event.UltimateEvents;
import org.zifeng.skilltree.init.ModAttributes;
import org.zifeng.skilltree.init.ModBlockEntities;
import org.zifeng.skilltree.init.ModBlocks;
import org.zifeng.skilltree.init.ModCreativeTabs;
import org.zifeng.skilltree.init.ModItems;
import org.zifeng.skilltree.init.ModMenus;
import org.zifeng.skilltree.network.ModNetwork;

@Mod(SkillTreeMod.MOD_ID)
public class SkillTreeMod {
    public static final String MOD_ID = "zifeng_s_custom_skill_tree";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkillTreeMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        // 方块掉落修改器注册（Global Loot Modifier，2026-09-01：点石成金/自动熔炼/万物挖掘/挪移）
        org.zifeng.skilltree.loot.ModLootModifiers.register(modEventBus);

        modEventBus.addListener(SkillEvents::registerPlayerAttributes);

        // 自动熔炼黑名单指令（/hmd 添加、/delhmd 删除，2026-08-13 恢复）
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) ->
                org.zifeng.skilltree.command.ModCommands.register(event.getDispatcher()));

        // 玩家技能数据管理指令（/skilltree reset | points set | points add，2026-09-04 1.3.7）
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) ->
                org.zifeng.skilltree.command.SkillTreeAdminCommands.register(event.getDispatcher()));

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Config 热重载/加载后重挂在线玩家属性（2026-08-29：属性每点加成进 Config 后需即时生效）
        modEventBus.addListener(Config::onConfigChanged);

        // 网络注册（1.20.1 SimpleChannel）
        ModNetwork.register();

        // GAME 总线手动注册（避免 @EventBusSubscriber 双重注册）
        MinecraftForge.EVENT_BUS.register(SkillEvents.class);
        MinecraftForge.EVENT_BUS.register(UltimateEvents.class);
        MinecraftForge.EVENT_BUS.register(AuraEvents.class);
        MinecraftForge.EVENT_BUS.register(MagnetEvents.class);
        MinecraftForge.EVENT_BUS.register(LockEvents.class);
        MinecraftForge.EVENT_BUS.register(LootVacuumEvents.class);
        MinecraftForge.EVENT_BUS.register(GiftEvents.class);
        // 主系统 Tick 末合并推送（2026-08-28 架构升级：一 tick 内多次 markDirty → 末尾合并成一次）
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent event) -> {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                org.zifeng.skilltree.GlobalStateSync.onServerTickEnd();
            }
        });

        if (FMLLoader.getDist().isClient()) {
            ClientRegistrar.register(modEventBus);
        }
        LOGGER.info("[{}] 模组加载完成！", MOD_ID);
    }
}
