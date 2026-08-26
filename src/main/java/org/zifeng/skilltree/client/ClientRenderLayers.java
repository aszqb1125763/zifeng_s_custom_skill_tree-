package org.zifeng.skilltree.client;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.client.renderer.PlayerRingLayer;

/**
 * 玩家渲染层注册（1.20.1 Forge 官方 EntityRenderersEvent.AddLayers 事件）：
 * 给玩家默认/纤细模型都添加杀戮光环星空环渲染层（Avaritia 式绑定玩家模型）。
 * ⚠️ 比 Mixin 注入 PlayerRenderer 构造更可靠（Forge 1.20.1 标准做法），
 *    兼容 OptiFine/Iris 等渲染修改模组。
 */
public class ClientRenderLayers {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        org.zifeng.skilltree.SkillTreeMod.LOGGER.info("[zifeng] AddLayers 事件触发！skins={}", event.getSkins());
        // default（原版/宽）与 slim（纤细）两种玩家模型都要添加
        for (String skin : new String[]{"default", "slim"}) {
            try {
                net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new PlayerRingLayer(renderer));
                    org.zifeng.skilltree.SkillTreeMod.LOGGER.info("[zifeng] 已添加星空环渲染层 (skin={})", skin);
                }
            } catch (Exception e) {
                // 防御：渲染层添加失败不影响游戏
                org.zifeng.skilltree.SkillTreeMod.LOGGER.warn("[zifeng] 玩家渲染层添加失败 (skin={})", skin, e);
            }
        }
    }
}
