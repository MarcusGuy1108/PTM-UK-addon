package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import com.ptmuk.block.ClassicLampBlockEntity;
import com.ptmuk.block.UkTrafficSignal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PtmUk.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ClassicLampBlockEntity>> CLASSIC_LAMP = BLOCK_ENTITIES.register(
            "classic_lamp", () -> BlockEntityType.Builder.of(ClassicLampBlockEntity::new, ModBlocks.SIGNALS.values().stream()
                    .map(o -> (Block) o.get())
                    .filter(b -> b instanceof UkTrafficSignal s && s.getStyle().isBulb())
                    .toArray(Block[]::new)).build(null));

    private ModBlockEntities() {
    }
}
