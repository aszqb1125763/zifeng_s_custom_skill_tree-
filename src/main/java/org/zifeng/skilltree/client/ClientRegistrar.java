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
        // 断开连接清空客户端缓存（2026-08-25 多人防跨服数据残留：HUD 技能点/凤凰涅槃冷却/技能缓存）
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> {
            org.zifeng.skilltree.client.SkillPointHudRenderer.onDisconnect();
            org.zifeng.skilltree.client.ReviveHudRenderer.setCooldown(false, 0);
            org.zifeng.skilltree.client.ModKeyBindingEvents.onDisconnect();
        });
    }
}
