package org.zifeng.skilltree.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.zifeng.skilltree.client.screen.SkillPointConverterScreen;

/**
 * 客户端 MOD 总线事件手动注册（NeoForge 1.21 推荐方式，避免 @EventBusSubscriber 扫描顺序问题）。
 */
public class ClientRegistrar {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModKeyBindings::registerKeyBindings);
        modEventBus.addListener(SkillPointConverterScreen::registerScreens);
        // ★ 26.1.2：世界空间自绘渲染的自定义管线（「穿墙可见」线框）。
        //   1.21.1 那套 RenderSystem.disableDepthTest() 已随即时模式整体删除，
        //   现在改为注册一条深度测试恒通过、不写深度的 LINES 管线。
        modEventBus.addListener(org.zifeng.skilltree.client.render.WorldRender::registerPipelines);
        // ★ 26.1.2：星空环（护身星环类技能）改为官方渲染层注册。
        //   1.21.1 靠 PlayerRendererMixin 在构造时 addLayer，而 PlayerRenderer 在 26.1.2 已删除，
        //   故改用 EntityRenderersEvent.AddLayers 拿到每个皮肤模型的 AvatarRenderer 再挂层。
        modEventBus.addListener(ClientRegistrar::onAddLayers);
        // GAME 总线客户端事件手动注册（确保触发）
        NeoForge.EVENT_BUS.register(ModKeyBindingEvents.class);
        // 客户端会话层（2026-09-09：认链接/世界时钟/会话回收广播——死亡重生等场景清模块临时态，修木棍概率失效）
        NeoForge.EVENT_BUS.register(ClientSession.class);
        // 凤凰涅槃冷却 HUD 提示（RenderGuiEvent.Post）
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.client.ReviveHudRenderer.class);
        // 碧波清眸：水下/岩浆清晰视野（2026-08-27，ViewportEvent.RenderFog）
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.client.ClientVisionEvents.class);
        // 御风止步：飞行无惯性（2026-08-27，客户端输入驱动）
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.client.ClientFlightEvents.class);
        // 寻宝大师：64格内战利品容器/考古点发光轮廓（2026-08-27；★ 26.1.2 改用 SubmitCustomGeometryEvent）
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.client.ClientTreasureEvents.class);
        // 方块扫描：已标记方块 id 的同类方块高亮（★ 2026-10-11，主动扫描；★ 26.1.2 改用 SubmitCustomGeometryEvent）
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.client.ClientBlockScanEvents.class);
        // 技能点 HUD（2026-09-11）：改用 GUI 层注册（MOD 总线），层级在聊天栏之下、血条类层之上。
        // ⚠️ 必须从 GAME 总线的 register(...) 中移除——该类已无 @SubscribeEvent 方法，
        //    NeoForge 会直接崩："has no @SubscribeEvent methods, but register was called anyway"。
        modEventBus.addListener(org.zifeng.skilltree.client.SkillPointHudRenderer::registerGuiLayers);
        // 真实血量数字（2026-09-11）：同样改用 GUI 层注册，置于最顶层，避免被其他血条模组（layered draw）覆盖
        modEventBus.addListener(org.zifeng.skilltree.client.HealthNumberRenderer::registerGuiLayers);
        // 磁铁屏蔽区：木棍左键选区 + 选区渲染（2026-09-07）
        NeoForge.EVENT_BUS.register(MagnetExclusionInputHandler.class);
        NeoForge.EVENT_BUS.register(MagnetExclusionRenderer.class);
        // 机械共鸣·操作区：框选 + 渲染（2026-09-08，木棍工具模式 2-5）
        NeoForge.EVENT_BUS.register(ZoneSelectionInputHandler.class);
        NeoForge.EVENT_BUS.register(ZoneSkillRenderer.class);
        // 木棍工具层屏幕信息栏（2026-09-08：右上角状态/模式/操作提示）
        NeoForge.EVENT_BUS.register(org.zifeng.skilltree.client.StickToolHudRenderer.class);
        // 容器绑定瞄准提示（2026-09-08：BIND 模式下准星指向可绑容器时黄绿选框）
        NeoForge.EVENT_BUS.register(BindTargetRenderer.class);
        // 断开连接清空客户端缓存（2026-08-25 多人防跨服数据残留：HUD 技能点/凤凰涅槃冷却/技能缓存）
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> {
            org.zifeng.skilltree.client.SkillPointHudRenderer.onDisconnect();
            org.zifeng.skilltree.client.ReviveHudRenderer.setCooldown(false, 0);
            org.zifeng.skilltree.client.ModKeyBindingEvents.onDisconnect();
            org.zifeng.skilltree.client.MagnetExclusionClientState.onDisconnect(); // 磁铁屏蔽区缓存重置（防跨服残留）
            org.zifeng.skilltree.client.ZoneExclusionClientState.onDisconnect(); // 机械共鸣选区第一角重置（防跨服残留）
            org.zifeng.skilltree.client.ClientGlobalState.reset(); // 2026-08-28：全局状态缓存重置（防跨服残留）
        });
    }

    /**
     * 星空环渲染层注册（26.1.2）。
     *
     * <p>1.21.1 时代由 {@code PlayerRendererMixin} 在玩家渲染器构造时 addLayer；新版
     * {@code PlayerRenderer} 已被 {@code AvatarRenderer} + {@code AvatarRenderState} 取代，
     * 官方给出的挂载点是本事件：为每个皮肤模型（slim / wide）分别取渲染器并加层。
     */
    private static void onAddLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.world.entity.player.PlayerModelType skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer> renderer =
                    event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new org.zifeng.skilltree.client.renderer.PlayerRingLayer(renderer));
            }
        }
    }
}
