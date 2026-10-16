package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.zifeng.skilltree.skill.Skills;

/**
 * 碧波清眸（UNDERWATER_VISION，2026-08-27）：水底/岩浆中清晰视野。
 * <p>客户端渲染事件（双层修复，2026-08-27 测试反馈）：
 * <ul>
 *   <li>{@link ViewportEvent.RenderFog}：雾距拉远（水下原版 end=96、岩浆 end=1，拉远到 128）</li>
 *   <li>{@link ViewportEvent.ComputeFogColor}：雾色改浅——水下原版是生物群系深蓝滤镜、
 *       岩浆是深红滤镜，远景被滤镜盖住看不远。改成接近空气的浅色，消除"有色滤镜"</li>
 * </ul>
 * 技能状态用客户端本地缓存（服务端 S2CPacket 校准）。
 */
public class ClientVisionEvents {

    /** 碧波清眸生效时的雾距离（远 = 清晰；128 格超过渲染距离会被引擎截断，无副作用） */
    private static final float CLEAR_FOG_DISTANCE = 128.0F;

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        FogType type = event.getType();
        if (type != FogType.WATER && type != FogType.LAVA) {
            return; // 只处理水下/岩浆雾
        }
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.UNDERWATER_VISION)) {
            return; // 技能未学/未开启
        }
        // 玩家视角雾效（多人下事件参数来自当前渲染相机，无需额外玩家判定）
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // ★ 26.1.2：从「只调近远平面」升级为「直接把 FogData 的四个距离全部拉满」。
        //   新模型下 ViewportEvent.RenderFog 是在 FogRenderer#setupFog 内部、
        //   雾环境算完之后触发的，并直接持有将要送进着色器的可变 FogData ——
        //   所以这里改的就是最终结果，等同于「完全无雾」。
        //   （旧版 1.21.1 因为事件时机偏早、水下球体指数雾改 start/end 无效，
        //     才不得不额外写一个 FogRendererMixin 去拦 setupFog；
        //     26.1.2 的事件时机已经够用，那个 mixin 已删除。）
        event.setNearPlaneDistance(Float.MAX_VALUE);
        event.setFarPlaneDistance(Float.MAX_VALUE);
        net.minecraft.client.renderer.fog.FogData fog = event.getFogData();
        fog.environmentalStart = Float.MAX_VALUE;
        fog.environmentalEnd = Float.MAX_VALUE;
        fog.renderDistanceStart = Float.MAX_VALUE;
        fog.renderDistanceEnd = Float.MAX_VALUE;
    }

    /**
     * 雾色改浅：水下/岩浆的"有色滤镜"来自雾色（水下=生物群系深蓝、岩浆=深红）。
     * 只改雾距不改雾色，远景仍被滤镜盖住。改成接近空气的浅色后滤镜消失，视野清晰。
     */
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.UNDERWATER_VISION)) {
            return;
        }
        FogType type = event.getCamera().getFluidInCamera();
        if (type == FogType.WATER) {
            // 水下：浅亮蓝（接近水面可见的空气色，消除深蓝滤镜）
            event.setRed(0.55F);
            event.setGreen(0.72F);
            event.setBlue(0.95F);
        } else if (type == FogType.LAVA) {
            // 岩浆：浅橙（原版深红 0.6/0.1/0 太压抑，改浅色可见远景）
            event.setRed(1.0F);
            event.setGreen(0.72F);
            event.setBlue(0.35F);
        }
    }
}
