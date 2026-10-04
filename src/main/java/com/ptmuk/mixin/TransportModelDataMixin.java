package com.ptmuk.mixin;

import com.ptmuk.bus.UkBuses;
import com.rinventor.ptm2.core.properties.VehicleTypes;
import com.rinventor.ptm2.dimension.physical.TransportModelData;
import com.rinventor.ptm2.engine.vehicle.VehicleSpecification;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PTM2 keeps its vehicle specifications in a hard-coded list; add ours. */
@Mixin(value = TransportModelData.class, remap = false)
public abstract class TransportModelDataMixin {
    @Inject(method = "specification", at = @At("HEAD"), cancellable = true)
    private static void ptmuk$specification(String model, CallbackInfoReturnable<VehicleSpecification> cir) {
        VehicleSpecification spec = UkBuses.specification(model);
        if (spec != null) {
            cir.setReturnValue(spec);
        }
    }

    /** So transport companies and depots can run our buses too. */
    @Inject(method = "models", at = @At("RETURN"))
    private static void ptmuk$models(VehicleTypes type, CallbackInfoReturnable<List<String>> cir) {
        if (type == VehicleTypes.BUS) {
            for (String code : UkBuses.models()) {
                if (!cir.getReturnValue().contains(code)) {
                    cir.getReturnValue().add(code);
                }
            }
        }
    }
}
