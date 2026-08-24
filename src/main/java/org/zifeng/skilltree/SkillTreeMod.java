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
import org.zifeng.skilltree.event.AuraEvents;
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

        // 自动熔炼黑名单指令（/hmd 添加、/delhmd 删除，2026-08-13 恢复）
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                org.zifeng.skilltree.command.ModCommands.register(event.getDispatcher()));

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.register(SkillEvents.class);
        NeoForge.EVENT_BUS.register(UltimateEvents.class);
        NeoForge.EVENT_BUS.register(AuraEvents.class);
        NeoForge.EVENT_BUS.register(MagnetEvents.class);
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.event.LockEvents.class);
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.event.LootVacuumEvents.class);

        if (FMLLoader.getDist().isClient()) {
            ClientRegistrar.register(modEventBus);
        }
        LOGGER.info("[{}] 模组加载完成！", MOD_ID);
    }
}
