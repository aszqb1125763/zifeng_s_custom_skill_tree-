package org.zifeng.skilltree.init;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.block.CreativeEnergyBlock;
import org.zifeng.skilltree.block.SkillPointConverterBlock;

public class ModBlocks {
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SkillTreeMod.MOD_ID);

    /**
     * 技能点转换机：FE 能量 → 技能点。硬度=黑曜石25%（12.5），防爆 1200，需铁镐以上挖掘，红石可关闭，工作发光。
     * ⚠️ 注册名保持 star_energy_converter（旧注册名）：1.2.3 曾更名 skill_point_converter 导致旧存档方块消失，
     *    注册名是存档级标识，绝不能改（显示名"技能点转换机"在 lang 文件）。
     */
    public static final RegistryObject<SkillPointConverterBlock> STAR_ENERGY_CONVERTER = BLOCKS.register(
            "star_energy_converter",
            () -> new SkillPointConverterBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(12.5F, 1200.0F) // 硬度黑曜石25%，爆炸抗性与黑曜石同级（防爆）
                            .requiresCorrectToolForDrops() // 需正确工具（铁镐以上，needs_iron_tool tag）
                            .lightLevel(state -> state.getValue(SkillPointConverterBlock.LIT) ? 14 : 0) // 工作发光
                            .sound(SoundType.METAL)));

    /** 创造能量方块（测试无限能源） */
    public static final RegistryObject<CreativeEnergyBlock> CREATIVE_ENERGY = BLOCKS.register(
            "creative_energy_block",
            () -> new CreativeEnergyBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(2.0F)
                            .sound(SoundType.METAL)
                            .lightLevel(state -> 15)));
}
