package com.ptmuk.fare;

import com.ptmuk.PtmUk;
import com.rinventor.ptm2.objects.entities.vehicle.Vehicle;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Card taps on vehicles: right-clicking with a card near a PTM2 vehicle's card reader (its
 * validator location) pays the fare, whatever the crosshair is on at the time.
 */
@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID)
public final class FareEvents {
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (tapVehicle(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block instanceof CardReaderBlock || block instanceof TopUpMachineBlock) {
            return;     // they take cards themselves
        }
        if (tapVehicle(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Vehicle && tapVehicle(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof Vehicle && tapVehicle(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    /** True if the player is tapping a card on a vehicle's reader (the tap itself is server side). */
    private static boolean tapVehicle(Player player, ItemStack stack) {
        if (Fares.card(stack) == null) {
            return false;
        }
        Vec3 reader = Fares.vehicleReader(player);
        if (reader == null) {
            return false;
        }
        if (!player.level().isClientSide && !player.getCooldowns().isOnCooldown(stack.getItem())) {
            Fares.tap(player, stack, reader);
        }
        return true;
    }

    private FareEvents() {
    }
}
