package com.ptmuk.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ptmuk.PtmUk;
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

    private static Map<String, BlockParts> blocks = Map.of();

    private record BlockParts(boolean board, List<String> aspects, Map<String, Map<String, String>> looks) {
    }

    private SignalModels() {
    }

    @SubscribeEvent
    public static void registerParts(ModelEvent.RegisterAdditional event) {
        blocks = loadIndex();
        blocks.forEach((block, parts) -> {
            List<String> names = new ArrayList<>();
            names.add("body");
            if (parts.board) {
                names.add("board");
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
        Map<List<ResourceLocation>, BakedModel> combined = new HashMap<>();
        blocks.forEach((name, parts) -> {
            Block block = ForgeRegistries.BLOCKS.getValue(PtmUk.id(name));
            if (block == null) {
                PtmUk.LOGGER.warn("signal_parts.json lists unknown block {}", name);
                return;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                List<ResourceLocation> needed = partsFor(name, parts, state);
                BakedModel model = combined.computeIfAbsent(needed, list -> {
                    List<BakedModel> baked = new ArrayList<>(list.size());
                    for (ResourceLocation location : list) {
                        BakedModel part = models.get(location);
                        if (part == null) {
                            PtmUk.LOGGER.error("Missing signal model part {}", location);
                        } else {
                            baked.add(part);
                        }
                    }
                    return new CombinedModel(baked);
                });
                models.put(BlockModelShaper.stateToModelLocation(state), model);
            }
        });
    }

    private static List<ResourceLocation> partsFor(String block, BlockParts parts, BlockState state) {
        String attachment = state.getValue(TrafficLight.ATTACHMENT).getSerializedName();
        int rotation = state.getValue(TrafficLight.ROTATION);
        boolean diagonal = rotation % 2 == 1 && DIAGONAL_ATTACHMENTS.contains(attachment);
        String geometry = attachment + (diagonal ? "45" : "");
        int y = 90 * (rotation / 2);

        List<ResourceLocation> out = new ArrayList<>();
        out.add(part(block, "body", geometry, y));
        if (parts.board && state.getValue(UkTrafficSignal.BOARD)) {
            out.add(part(block, "board", geometry, y));
        }
        if (!parts.aspects.isEmpty()) {
            int colour = state.getValue(TrafficLight.STATE).getID();
            Map<String, String> looks = state.getValue(UkTrafficSignal.RED_AMBER) && parts.looks.containsKey(colour + "+ra")
                    ? parts.looks.get(colour + "+ra")
                    : parts.looks.get(String.valueOf(colour));
            for (String aspect : parts.aspects) {
                out.add(part(block, aspect + "_" + looks.get(aspect), geometry, y));
            }
        }
        return out;
    }

    private static List<String> geometries() {
        List<String> out = new ArrayList<>(ATTACHMENTS);
        DIAGONAL_ATTACHMENTS.forEach(a -> out.add(a + "45"));
        return out;
    }

    private static ResourceLocation part(String block, String name, String geometry, int y) {
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
                out.put(block, new BlockParts(entry.get("board").getAsBoolean(), aspects, looks));
            }
        } catch (Exception e) {
            PtmUk.LOGGER.error("Could not read {}; UK signals will render without models", INDEX, e);
        }
        return out;
    }
}
