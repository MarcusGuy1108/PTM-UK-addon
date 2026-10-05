package com.ptmuk.bus;

import com.ptmuk.PtmUk;
import com.rinventor.ptm2.core.properties.EngineTypes;
import com.rinventor.ptm2.core.properties.VehicleTypes;
import com.rinventor.ptm2.engine.vehicle.VehicleSpecification;
import com.rinventor.ptm2.objects.entities.vehicle.Vehicle;
import com.rinventor.ptm2.objects.items.CarItem;
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
    public static final Map<String, RegistryObject<? extends EntityType<? extends Vehicle>>> TYPES = new LinkedHashMap<>();

    public static final String ALX400_CODE = "124d";
    public static final String UKDD_CODE = "124e";
    public static final String E400_CODE = "124f";
    /** Cars: 2nd character 3 = modern generation. */
    public static final String K4_CODE = "035k";

    public static final RegistryObject<EntityType<ALX400>> ALX400_TYPE = doubleDecker(ALX400_CODE, "alx400", ALX400::new,
            ALX400Layout.SEAT_COUNT);
    public static final RegistryObject<EntityType<UkDoubleDecker>> UKDD_TYPE = doubleDecker(UKDD_CODE, "ukdd", UkDoubleDecker::new,
            UkddLayout.SEAT_COUNT);
    public static final RegistryObject<EntityType<Enviro400>> E400_TYPE = doubleDecker(E400_CODE, "e400", Enviro400::new,
            E400Layout.SEAT_COUNT);
    public static final RegistryObject<EntityType<KiaK4>> K4_TYPE = car(K4_CODE, "k4", KiaK4::new,
            // 2026 Kia K4 hatchback, built about 1.18x real size like PTM2's own cars; 1.6 T-GDi, auto
            new VehicleSpecification(2.2f, 1.7f, 5.24f, 3500.0f, 5, VehicleTypes.CAR, 1, false, false, EngineTypes.PETROL,
                    false, 7, 60.0f, 134.0f, 8.6f, 2.5f, 9.0f, 0.64f, 0));

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

    private static <T extends Vehicle> RegistryObject<EntityType<T>> car(String code, String name, EntityType.EntityFactory<T> factory,
                                                                     VehicleSpecification spec) {
        String id = "ptm_" + code + "_" + name;
        RegistryObject<EntityType<T>> type = ENTITIES.register(id, () -> EntityType.Builder.of(factory, MobCategory.AMBIENT)
                .setUpdateInterval(3).sized(2.0f, 0.7f).setTrackingRange(128).build(PtmUk.id(id).toString()));
        SPECS.put(code, spec);
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

    public static List<String> models(VehicleTypes type) {
        return SPECS.entrySet().stream().filter(e -> e.getValue().vehicleType == type).map(Map.Entry::getKey).toList();
    }

    /** The spawn item is PTM2's own transport item, so it behaves exactly like PTM2's buses. */
    public static Item transportItem(String code) {
        return new TransportItem(new Item.Properties(), code);
    }

    /** Cars spawn through PTM2's car item, which also hands over the keys. */
    public static Item carItem(String code, String colour) {
        return new CarItem(new Item.Properties(), code, colour, true);
    }
}
