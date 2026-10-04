package com.ptmuk.registry;

import com.ptmuk.PtmUk;
import com.ptmuk.block.ClassicLampBlockEntity;
import com.ptmuk.block.UkTrafficSignal;
import com.ptmuk.motorway.VmsBlockEntity;
import com.ptmuk.sign.DirectionSignBlockEntity;
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

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<DirectionSignBlockEntity>> DIRECTION_SIGN = BLOCK_ENTITIES.register(
            "direction_sign", () -> BlockEntityType.Builder.of(DirectionSignBlockEntity::new, ModBlocks.DIRECTION_SIGNS.values().stream()
                    .map(o -> (Block) o.get()).toArray(Block[]::new)).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<VmsBlockEntity>> VMS = BLOCK_ENTITIES.register(
            "matrix_sign", () -> BlockEntityType.Builder.of(VmsBlockEntity::new, ModBlocks.VMS.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.ptmuk.sign.BusStopBlockEntity>> BUS_STOP = BLOCK_ENTITIES.register(
            "london_bus_stop", () -> BlockEntityType.Builder.of(com.ptmuk.sign.BusStopBlockEntity::new, ModBlocks.BUS_STOP.get()).build(null));

    private ModBlockEntities() {
    }
}
