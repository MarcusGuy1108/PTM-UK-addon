package com.ptmuk.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ptmuk.PtmUk;
import com.ptmuk.block.PoleType;
import com.ptmuk.block.SignalAccessory;
import com.ptmuk.block.UkPole;
import com.rinventor.ptm2.core.properties.TrafficLightStates;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import com.ptmuk.block.UkTrafficSignal;
import com.rinventor.ptm2.objects.blockentities.traffic_light.TrafficLight;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Builds the block models for UK signals and accessories from parts.
 *
 * <p>Each head is split into a housing, an optional backing board, and one part per lens per
 * look (on / off / flashing), written pre-rotated by tools/generate_assets.py together with
 * {@code signal_parts.json}, which says which look each lens has for every PTM2 light state.
 * Every part is baked once and shared; each block state gets a {@link CombinedModel} of the
 * parts it needs. Expressing this as blockstate JSON instead makes Minecraft bake the same
 * quads thousands of times over.
 */
@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SignalModels {
    private static final ResourceLocation INDEX = PtmUk.id("signal_parts.json");
    private static final List<String> ATTACHMENTS = List.of("center", "wall", "left", "right", "post", "left_post", "right_post");
    private static final List<String> DIAGONAL_ATTACHMENTS = List.of("center", "wall", "post");

    /** Geometry used when a free-standing head or sign sits on top of a UK pole. */
    static final String ON_POLE = "onpole";

    private static Map<String, BlockParts> blocks = Map.of();

    /** fade: lenses are drawn lit by {@link ClassicLampRenderer} (bulb fade), so the block model keeps them unlit. */
    record BlockParts(boolean board, boolean fade, boolean waitLamp, List<String> aspects, Map<String, Map<String, String>> looks) {
    }

    static BlockParts index(String block) {
        return blocks.get(block);
    }

    private SignalModels() {
    }

    /** The pole section that continues up behind a pole-top mounted head. */
    static ResourceLocation poleExtension(PoleType type) {
        return PtmUk.id("block/" + type.id() + "/mid_cap");
    }

    @SubscribeEvent
    public static void registerParts(ModelEvent.RegisterAdditional event) {
        for (PoleType type : PoleType.values()) {
            event.register(poleExtension(type));
        }
        blocks = loadIndex();
        blocks.forEach((block, parts) -> {
            List<String> names = new ArrayList<>();
            names.add("body");
            if (parts.board) {
                names.add("board");
            }
            if (parts.waitLamp) {
                names.add("wait");
            }
            parts.looks.values().forEach(looks -> looks.forEach((aspect, look) -> {
                if (!names.contains(aspect + "_" + look)) {
                    names.add(aspect + "_" + look);
                }
            }));
            for (String name : names) {
                for (String geometry : geometries()) {
                    for (int y = 0; y < 360; y += 90) {
                        event.register(part(block, name, geometry, y));
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public static void assemble(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        Map<List<ResourceLocation>, CombinedModel> combined = new HashMap<>();
        java.util.function.Function<List<ResourceLocation>, CombinedModel> bake = list -> combined.computeIfAbsent(list, l -> {
            List<BakedModel> baked = new ArrayList<>(l.size());
            for (ResourceLocation location : l) {
                BakedModel part = models.get(location);
                if (part == null) {
                    PtmUk.LOGGER.error("Missing signal model part {}", location);
                } else {
                    baked.add(part);
                }
            }
            return new CombinedModel(baked);
        });
        blocks.forEach((name, parts) -> {
            Block block = ForgeRegistries.BLOCKS.getValue(PtmUk.id(name));
            if (block == null) {
                PtmUk.LOGGER.warn("signal_parts.json lists unknown block {}", name);
                return;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                CombinedModel normal = bake.apply(partsFor(name, parts, state, false));
                Map<PoleType, CombinedModel> onPole = new EnumMap<>(PoleType.class);
                if (canSitOnPole(state)) {
                    List<ResourceLocation> mounted = partsFor(name, parts, state, true);
                    for (PoleType type : PoleType.values()) {
                        List<ResourceLocation> withPole = new ArrayList<>(mounted);
                        withPole.add(poleExtension(type));
                        onPole.put(type, bake.apply(withPole));
                    }
                }
                models.put(BlockModelShaper.stateToModelLocation(state),
                        onPole.isEmpty() ? normal : new PoleMountedModel(normal, onPole));
            }
        });
    }

    /** Free-standing heads and signs can sit on top of a pole; wall and post mounts can't. */
    static boolean canSitOnPole(BlockState state) {
        return state.getValue(TrafficLight.ATTACHMENT) == TrafficLightStates.CENTER;
    }

    /** The UK pole directly below, if this head or sign sits on top of one. */
    static PoleType poleBelow(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (!canSitOnPole(state)) {
            return null;
        }
        return level.getBlockState(pos.below()).getBlock() instanceof UkPole pole ? pole.getType() : null;
    }

    private static List<ResourceLocation> partsFor(String block, BlockParts parts, BlockState state, boolean onPole) {
        String geometry = geometry(state, onPole);
        int y = yRotation(state);
        List<ResourceLocation> out = new ArrayList<>();
        out.add(part(block, "body", geometry, y));
        if (parts.board && state.getValue(UkTrafficSignal.BOARD)) {
            out.add(part(block, "board", geometry, y));
        }
        if (parts.waitLamp && state.getValue(SignalAccessory.WAIT)) {
            out.add(part(block, "wait", geometry, y));
        }
        if (!parts.aspects.isEmpty()) {
            Map<String, String> looks = looks(parts, state);
            for (String aspect : parts.aspects) {
                out.add(part(block, aspect + "_" + (parts.fade ? "off" : looks.get(aspect)), geometry, y));
            }
        }
        return out;
    }

    /** Lens look (on / off / flash) per aspect for this signal state. */
    static Map<String, String> looks(BlockParts parts, BlockState state) {
        int colour = state.getValue(TrafficLight.STATE).getID();
        return state.getValue(UkTrafficSignal.RED_AMBER) && parts.looks.containsKey(colour + "+ra")
                ? parts.looks.get(colour + "+ra")
                : parts.looks.get(String.valueOf(colour));
    }

    static String geometry(BlockState state, boolean onPole) {
        String attachment = onPole ? ON_POLE : state.getValue(TrafficLight.ATTACHMENT).getSerializedName();
        int rotation = state.getValue(TrafficLight.ROTATION);
        boolean diagonal = rotation % 2 == 1 && (onPole || DIAGONAL_ATTACHMENTS.contains(attachment));
        return attachment + (diagonal ? "45" : "");
    }

    static int yRotation(BlockState state) {
        return 90 * (state.getValue(TrafficLight.ROTATION) / 2);
    }

    private static List<String> geometries() {
        List<String> out = new ArrayList<>(ATTACHMENTS);
        DIAGONAL_ATTACHMENTS.forEach(a -> out.add(a + "45"));
        out.add(ON_POLE);
        out.add(ON_POLE + "45");
        return out;
    }

    static ResourceLocation part(String block, String name, String geometry, int y) {
        return PtmUk.id("block/" + block + "/" + name + "_" + geometry + "_y" + y);
    }

    private static Map<String, BlockParts> loadIndex() {
        Map<String, BlockParts> out = new LinkedHashMap<>();
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(INDEX)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (String block : root.keySet()) {
                JsonObject entry = root.getAsJsonObject(block);
                List<String> aspects = new ArrayList<>();
                entry.getAsJsonArray("aspects").forEach(a -> aspects.add(a.getAsString()));
                Map<String, Map<String, String>> looks = new HashMap<>();
                JsonObject looksJson = entry.getAsJsonObject("looks");
                for (String key : looksJson.keySet()) {
                    Map<String, String> perAspect = new HashMap<>();
                    looksJson.getAsJsonObject(key).entrySet().forEach(e -> perAspect.put(e.getKey(), e.getValue().getAsString()));
                    looks.put(key, perAspect);
                }
                boolean fade = entry.has("fade") && entry.get("fade").getAsBoolean();
                boolean wait = entry.has("wait") && entry.get("wait").getAsBoolean();
                out.put(block, new BlockParts(entry.get("board").getAsBoolean(), fade, wait, aspects, looks));
            }
        } catch (Exception e) {
            PtmUk.LOGGER.error("Could not read {}; UK signals will render without models", INDEX, e);
        }
        return out;
    }
}
