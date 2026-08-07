package org.zifeng.skilltree.init;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.block.CreativeEnergyBlock;
import org.zifeng.skilltree.block.StarEnergyConverterBlock;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkillTreeMod.MOD_ID);

    /** 星能转换机：FE 能量 → 技能点（参考原版熔炉模型，红石+铁块细节） */
    public static final DeferredBlock<StarEnergyConverterBlock> STAR_ENERGY_CONVERTER = BLOCKS.register(
            "star_energy_converter",
            () -> new StarEnergyConverterBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)));

    /** 创造能量方块（测试无限能源） */
    public static final DeferredBlock<CreativeEnergyBlock> CREATIVE_ENERGY = BLOCKS.register(
            "creative_energy_block",
            () -> new CreativeEnergyBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(2.0F)
                            .sound(SoundType.METAL)
                            .lightLevel(state -> 15)));
}
