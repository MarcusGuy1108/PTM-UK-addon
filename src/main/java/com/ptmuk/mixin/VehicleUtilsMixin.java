package com.ptmuk.mixin;

import com.ptmuk.bus.UkBuses;
import com.rinventor.ptm2.dimension.physical.VehicleUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PTM2 only looks for vehicle entities in its own registry; point it at ours for our codes. */
@Mixin(value = VehicleUtils.class, remap = false)
public abstract class VehicleUtilsMixin {
    @Inject(method = "vehicleEntityFromModel", at = @At("HEAD"), cancellable = true)
    private static void ptmuk$entityFromModel(String model, CallbackInfoReturnable<List<EntityType<?>>> cir) {
        var type = UkBuses.TYPES.get(UkBuses.code(model));
        if (type != null) {
            List<EntityType<?>> list = new ArrayList<>();
            list.add(type.get());
            cir.setReturnValue(list);
        }
    }
}
