package org.zifeng.skilltree.client;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import org.zifeng.skilltree.client.screen.SkillPointConverterScreen;

/**
 * 客户端 MOD 总线事件手动注册（NeoForge 1.21 推荐方式，避免 @EventBusSubscriber 扫描顺序问题）。
 */
public class ClientRegistrar {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModKeyBindings::registerKeyBindings);
        // ⚠️ 1.20.1：MenuScreens.register 需在 FMLClientSetupEvent（注册表冻结后）执行，
        //    直接调用会在 mod 构造阶段 RegistryObject.get() 崩溃（原 NeoForge 1.21 直调方式不可用）
        modEventBus.addListener(SkillPointConverterScreen::registerScreens);
        // GAME 总线客户端事件手动注册（确保触发）
        MinecraftForge.EVENT_BUS.register(ModKeyBindingEvents.class);
        // 客户端会话层（2026-09-09：认链接/世界时钟/会话回收广播——死亡重生等场景清模块临时态，修木棍概率失效）
        MinecraftForge.EVENT_BUS.register(ClientSession.class);
        // ⚠️ 玩家渲染层（杀戮光环星空环）：EntityRenderersEvent.AddLayers 是 MOD 总线事件（RegisterEvent 类），
        //    必须注册到 modEventBus！注册到 GAME 总线永不触发（这就是之前第三人称看不到光环的原因）
        modEventBus.register(ClientRenderLayers.class);
        // 凤凰涅槃冷却 HUD 提示（RenderGuiEvent.Post）
        MinecraftForge.EVENT_BUS.register(org.zifeng.skilltree.client.ReviveHudRenderer.class);
        // 碧波清眸：水下/岩浆清晰视野（2026-08-27，ViewportEvent.RenderFog）
        MinecraftForge.EVENT_BUS.register(org.zifeng.skilltree.client.ClientVisionEvents.class);
        // 御风止步：飞行无惯性（2026-08-27，客户端输入驱动）
        MinecraftForge.EVENT_BUS.register(org.zifeng.skilltree.client.ClientFlightEvents.class);
        // 寻宝大师：64格内战利品容器/考古点发光轮廓（2026-08-27，RenderLevelStageEvent）
        MinecraftForge.EVENT_BUS.register(org.zifeng.skilltree.client.ClientTreasureEvents.class);
        // 技能点变动左下角 HUD 提示（2026-08-25：不刷聊天栏，显示在聊天栏下方）
        MinecraftForge.EVENT_BUS.register(org.zifeng.skilltree.client.SkillPointHudRenderer.class);
        // 磁铁屏蔽区：木棍左键选区 + 选区渲染（2026-09-07）
        MinecraftForge.EVENT_BUS.register(MagnetExclusionInputHandler.class);
        MinecraftForge.EVENT_BUS.register(MagnetExclusionRenderer.class);
        // 机械共鸣·操作区：框选 + 渲染（2026-09-08，木棍工具模式 2-5）
        MinecraftForge.EVENT_BUS.register(ZoneSelectionInputHandler.class);
        MinecraftForge.EVENT_BUS.register(ZoneSkillRenderer.class);
        // 木棍工具层屏幕信息栏（2026-09-08：右上角状态/模式/操作提示）
        MinecraftForge.EVENT_BUS.register(org.zifeng.skilltree.client.StickToolHudRenderer.class);
        // 容器绑定瞄准提示（2026-09-08：BIND 模式下准星指向可绑容器时黄绿选框）
        MinecraftForge.EVENT_BUS.register(BindTargetRenderer.class);
        // 断开连接清空客户端缓存（2026-08-25 多人防跨服数据残留：HUD 技能点/凤凰涅槃冷却/技能缓存）
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> {
            org.zifeng.skilltree.client.SkillPointHudRenderer.onDisconnect();
            org.zifeng.skilltree.client.ReviveHudRenderer.setCooldown(false, 0);
            org.zifeng.skilltree.client.ModKeyBindingEvents.onDisconnect();
            org.zifeng.skilltree.client.MagnetExclusionClientState.onDisconnect(); // 磁铁屏蔽区缓存重置（防跨服残留）
            org.zifeng.skilltree.client.ZoneExclusionClientState.onDisconnect(); // 机械共鸣选区第一角重置（防跨服残留）
            org.zifeng.skilltree.client.ClientGlobalState.reset(); // 2026-08-28：全局状态缓存重置（防跨服残留）
        });
    }
}
