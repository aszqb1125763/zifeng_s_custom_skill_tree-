package org.zifeng.skilltree.jade;

import org.zifeng.skilltree.block.CreativeEnergyBlock;
import org.zifeng.skilltree.block.SkillPointConverterBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * 技能树模组 Jade 插件入口（技能点转换机 64 位能量 + 创造能量方块无限显示）。
 * 仅当 Jade 安装时，Jade 才会读取 META-INF/services 加载本类 → 未安装 Jade 绝不加载、绝不崩溃。
 */
@WailaPlugin
public class SkillPointJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(SkillPointConverterDataProvider.INSTANCE, SkillPointConverterBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SkillPointConverterProvider.INSTANCE, SkillPointConverterBlock.class);
        registration.registerBlockComponent(CreativeEnergyProvider.INSTANCE, CreativeEnergyBlock.class);
    }
}
