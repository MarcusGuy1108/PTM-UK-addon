package com.ptmuk.bus;

import com.ptmuk.PtmUk;
import com.rinventor.ptm2.core.properties.EngineTypes;
import com.rinventor.ptm2.core.properties.VehicleTypes;
import com.rinventor.ptm2.engine.vehicle.VehicleSpecification;
import com.rinventor.ptm2.objects.items.TransportItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * UK buses for PTM2. PTM2 identifies a vehicle by a four-character model code taken from its
 * entity id ("ptm_<code>_..."): the 2nd character is the generation, the 3rd the capacity class
 * and the 4th the engine. Our codes end in a letter so they can never clash with PTM2's own
 * numeric ones. The mixins in com.ptmuk.mixin teach PTM2 these codes.
 */
public final class UkBuses {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PtmUk.MOD_ID);

    /** Model code -> specification. */
    public static final Map<String, VehicleSpecification> SPECS = new LinkedHashMap<>();
    /** Model code -> entity type. */
    public static final Map<String, RegistryObject<? extends EntityType<? extends DoubleDeckerBus>>> TYPES = new LinkedHashMap<>();

    public static final String ALX400_CODE = "124d";
    public static final String UKDD_CODE = "124e";

    public static final RegistryObject<EntityType<ALX400>> ALX400_TYPE = doubleDecker(ALX400_CODE, "alx400", ALX400::new,
            ALX400Layout.SEAT_COUNT);
    public static final RegistryObject<EntityType<UkDoubleDecker>> UKDD_TYPE = doubleDecker(UKDD_CODE, "ukdd", UkDoubleDecker::new,
            UkddLayout.SEAT_COUNT);

    private UkBuses() {
    }

    /** A 10.2 m dual-door double decker, 4.4 m tall, diesel with an automatic gearbox. */
    private static <T extends DoubleDeckerBus> RegistryObject<EntityType<T>> doubleDecker(String code, String name,
                                                                                         EntityType.EntityFactory<T> factory, int seats) {
        String id = "ptm_" + code + "_" + name;
        RegistryObject<EntityType<T>> type = ENTITIES.register(id, () -> EntityType.Builder.of(factory, MobCategory.AMBIENT)
                .setUpdateInterval(3).sized(2.5f, 0.7f).setTrackingRange(192).build(PtmUk.id(id).toString()));
        SPECS.put(code, new VehicleSpecification(3.0f, 4.4f, 10.2f, 0.0f, seats, VehicleTypes.BUS, 1, false, false,
                EngineTypes.DIESEL, true, 4, 80.0f, 80.0f, 6.0f, 2.2f, 5.0f));
        TYPES.put(code, type);
        return type;
    }

    /** PTM2 passes "124d", "ptm_124d" or a full entity name like "ptm_124d_alx400". */
    public static String code(String model) {
        String m = model.replace("ptm_", "");
        int i = m.indexOf('_');
        return i >= 0 ? m.substring(0, i) : m;
    }

    public static VehicleSpecification specification(String model) {
        return SPECS.get(code(model));
    }

    public static List<String> models() {
        return List.copyOf(SPECS.keySet());
    }

    /** The spawn item is PTM2's own transport item, so it behaves exactly like PTM2's buses. */
    public static Item transportItem(String code) {
        return new TransportItem(new Item.Properties(), code);
    }
}
