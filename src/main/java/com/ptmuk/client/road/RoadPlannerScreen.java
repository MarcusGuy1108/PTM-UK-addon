package com.ptmuk.client.road;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ptmuk.PtmUk;
import com.ptmuk.network.BlockSwapListMessage;
import com.ptmuk.network.BlockSwapMessage;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.RoadPlannerMessage;
import com.ptmuk.network.TraceResultMessage;
import com.ptmuk.network.TraceRoadMessage;
import com.ptmuk.road.BlockSwap;
import com.ptmuk.road.MapBuildings;
import com.ptmuk.road.OsmMap;
import com.ptmuk.road.RoadPlannerItem;
import com.ptmuk.road.RoadPlans;
import com.ptmuk.road.layout.Curve;
import com.ptmuk.road.layout.Layout;
import com.ptmuk.road.layout.SiteLayout;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadPlan.Junction;
import com.ptmuk.road.plan.RoadPlan.Node;
import com.ptmuk.road.plan.RoadPlan.Segment;
import com.ptmuk.road.plan.RoadPlan.Site;
import com.ptmuk.road.plan.RoadPlan.SiteType;
import com.ptmuk.road.plan.RoadStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/**
 * The Road Planner: a top-down map of the area round you to draw roads on. Click to lay a road
 * point by point (it curves smoothly through them); end on another road to make a junction.
 * Select a point to drag it or change its junction (give way, stop, mini roundabout,
 * roundabout, traffic lights), select a road to bend it or change its lanes, pavements and
 * markings. Sites: drag out a car park, retail park or drive-thru; roads that reach its edge
 * become its ways in. Map: bring in a real place's roads and buildings from an OpenStreetMap
 * export, and measure. The map shows exactly what will be built; Build sends it to the server.
 */
public class RoadPlannerScreen extends Screen {
    private static final ResourceLocation PREVIEW = PtmUk.id("road_planner/preview");
    private static final int PANEL = 136;
    private static final int TOP = 22;
    private static final int ROW = 15;
    private static RoadStyle lastStyle = RoadStyle.mainRoad();
    private static double lastZoom = 2;

    private enum Mode { DRAW, EDIT, SITES, MAP, SWAP, MOVE }

    private static final ResourceLocation OUTLINES = PtmUk.id("road_planner/outlines");

    /** The smallest site that can be marked out. */
    private static final int MIN_SITE = 12;

    private final InteractionHand hand;
    private RoadPlan plan;
    private final List<byte[]> history = new ArrayList<>();
    private PlannerMap map;
    private Layout lines;
    private Layout preview;
    private CompletableFuture<Layout> pending;
    private boolean previewStale = true;
    private DynamicTexture previewTex;
    private int previewW, previewH, previewX0, previewZ0;

    private Mode mode = Mode.DRAW;
    private RoadStyle drawStyle;
    private Node chainLast;
    private Integer selNode, selSeg;
    /** A selected bus stop (by id), and whether the next click on the map places a new one. */
    private Integer selStop;
    private boolean placingStop;
    private EditBox stopName;
    /** What a motorway junction made at the selected point will be. */
    private static com.ptmuk.road.layout.Interchange.Kind interchange = com.ptmuk.road.layout.Interchange.Kind.AUTO;
    private boolean draggingNode, draggingBend, panning, moved;
    private double camX, camZ, zoom;
    private double pressX, pressY, lastMouseX, lastMouseY;
    private double mouseX, mouseY;
    private String message = "";
    private int messageTicks;

    private final List<Button> panelButtons = new ArrayList<>();

    // sites: the one selected, what a new one will be, and dragging one out, along or by a corner
    private Integer selSite;
    private static SiteType newSiteType = SiteType.CAR_PARK;
    private int siteDrag;               // 0 none, 1 marking out a new one, 2 moving, 3 resizing, 4 drawing round a town
    private int siteCorner;             // bit 0: the east side, bit 1: the south side
    private int dragX0, dragZ0, dragX1, dragZ1;
    private double grabX, grabZ;
    private int[] siteStart;
    private static boolean drawTown;    // a new town (or district) is drawn round by hand, not marked out as a box
    private static RoadPlan.District newDistrict = RoadPlan.District.HIGH_STREET;
    private final List<int[]> lasso = new ArrayList<>();

    // a real map: the files to pick from, one being read (and what to do with it), its buildings drawn over the map
    private List<String> mapFiles;
    private CompletableFuture<OsmMap> mapJob;
    private Consumer<OsmMap> mapThen;
    private OsmMap osm;
    private List<OsmMap.Outline> outlines;
    private DynamicTexture outlineTex;
    private double[] measureA, measureB;

    // block swap: a box of the world, what it's made of (from the server) and what each block becomes
    private static final class SwapRow {
        final String id;
        final int count;
        String to;                      // null: keep it

        SwapRow(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    /** A row of the list: its name and count over its button. */
    private static final int SWAP_ENTRY = ROW + 10;
    private static int[] swapBox;       // x0 z0 x1 z1, inclusive: kept for next time the planner's opened
    private boolean swapDragging;
    private static boolean swapGroundOnly = true;
    private final List<SwapRow> swapRows = new ArrayList<>();
    private int swapSel = -1, swapFirst, swapListTop, swapShown;
    private String swapNote = "";
    private EditBox swapEdit;
    // copying and pasting sections of the world (Sections) on the map
    private static int[] moveBox;                     // x0 z0 x1 z1, inclusive; x0 z0 is the corner it's pasted from
    private boolean moveDragging;
    private static List<com.ptmuk.building.Sections.Info> moveList = List.of();
    private static String movePicked, moveTyped = "";
    private static int moveTurns;
    private static boolean moveMirror, moveKeepAir, moveKeepHeight;
    /** Where the picked section will go (x z of its first corner), once clicked. */
    private static int[] movePaste;
    private EditBox moveName;
    /** Draw mode: a click follows the old road there instead of starting a new one. */
    private boolean tracing;
    /** Following only a piece of old road round the click (to join a new road to it), not all of it. */
    private static boolean tracePiece;
    /** How far each way a piece goes: room for a junction's corners and markings. */
    private static final int PIECE = 40;

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new RoadPlannerScreen(hand));
    }

    public RoadPlannerScreen(InteractionHand hand) {
        super(Component.literal("Road Planner"));
        this.hand = hand;
        ItemStack stack = stack();
        this.plan = stack == null ? new RoadPlan() : RoadPlans.read(stack);
        this.drawStyle = lastStyle.copy();
        Player p = Minecraft.getInstance().player;
        camX = p == null ? 0 : p.getX();
        camZ = p == null ? 0 : p.getZ();
        zoom = lastZoom;
    }

    private ItemStack stack() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        ItemStack s = mc.player.getItemInHand(hand);
        return s.getItem() instanceof RoadPlannerItem ? s : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ set up

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (map == null && mc.level != null && mc.player != null) {
            // the client draws the chunks it has loaded straight away; the server sends the rest
            int radius = MAP_RADIUS_CHUNKS * 16 + 8;
            int px = (int) Math.floor(mc.player.getX()), pz = (int) Math.floor(mc.player.getZ());
            map = new PlannerMap(mc.level, px, pz, radius, (int) Math.floor(mc.player.getY()));
            com.ptmuk.network.PtmUkNetwork.CHANNEL.sendToServer(new com.ptmuk.network.RoadMapRequestMessage(px, pz, MAP_RADIUS_CHUNKS));
        }
        lines = Layout.lines(plan);
        // top bar
        int x = PANEL + 4;
        drawButton = addRenderableWidget(Button.builder(Component.literal("Draw"), b -> setMode(Mode.DRAW)).bounds(x, 3, 28, 16).build());
        editButton = addRenderableWidget(Button.builder(Component.literal("Edit"), b -> setMode(Mode.EDIT)).bounds(x + 29, 3, 26, 16).build());
        sitesButton = addRenderableWidget(Button.builder(Component.literal("Sites"), b -> setMode(Mode.SITES)).bounds(x + 56, 3, 30, 16).build());
        mapButton = addRenderableWidget(Button.builder(Component.literal("Map"), b -> setMode(Mode.MAP)).bounds(x + 87, 3, 24, 16).build());
        swapButton = addRenderableWidget(Button.builder(Component.literal("Swap"), b -> setMode(Mode.SWAP)).bounds(x + 112, 3, 30, 16)
                .tooltip(Tooltip.create(Component.literal("Swap an old build's blocks for new ones: coal roads for tarmac, and so on"))).build());
        moveButton = addRenderableWidget(Button.builder(Component.literal("Move"), b -> setMode(Mode.MOVE)).bounds(x + 143, 3, 30, 16)
                .tooltip(Tooltip.create(Component.literal("Copy big parts of the world and paste them anywhere, in this world or another"))).build());
        if (plan.map != null && outlines == null) {
            readMap(plan.map.file, m -> showOutlines());
        }
        int right = width - 3;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(right - 36, 3, 36, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Build"), b -> build()).bounds(right - 76, 3, 38, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> {
            send(RoadPlannerMessage.Action.UNDO);
            // the roads are gone again, so the plan can be changed and built afresh
            plan.nodes.forEach(n -> n.built = false);
            plan.segments.forEach(sg -> sg.built = false);
            plan.sites.forEach(st -> st.built = false);
            changed();
            flash("Last build undone - the plan is still here to change and build again");
        }).bounds(right - 108, 3, 30, 16).tooltip(Tooltip.create(Component.literal("Undo the last build"))).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            remember();
            plan = new RoadPlan();
            chainLast = null;
            selNode = selSeg = null;
            selSite = null;
            changed();
        }).bounds(right - 140, 3, 30, 16).build());
        rebuildPanel();
    }

    private void setMode(Mode m) {
        mode = m;
        tracing = false;
        if (m == Mode.SWAP && swapBox != null && swapRows.isEmpty()) {
            swapSend(BlockSwapMessage.Action.SCAN);
        }
        if (m == Mode.MOVE) {
            moveSend(com.ptmuk.network.SectionMapMessage.Action.LIST, "");
        }
        chainLast = null;
        rebuildPanel();
    }

    private Button drawButton, editButton, sitesButton, mapButton, swapButton, moveButton;

    /** The left panel: the selected point's junction, or the selected (or next) road's style. */
    private void rebuildPanel() {
        for (Button b : panelButtons) {
            removeWidget(b);
        }
        panelButtons.clear();
        if (swapEdit != null) {
            removeWidget(swapEdit);
            swapEdit = null;
        }
        if (moveName != null) {
            moveTyped = moveName.getValue();
            removeWidget(moveName);
            moveName = null;
        }
        if (stopName != null) {
            removeWidget(stopName);
            stopName = null;
        }
        if (drawButton != null) {
            drawButton.active = mode != Mode.DRAW;
            editButton.active = mode != Mode.EDIT;
            sitesButton.active = mode != Mode.SITES;
            mapButton.active = mode != Mode.MAP;
            swapButton.active = mode != Mode.SWAP;
            moveButton.active = mode != Mode.MOVE;
        }
        int y = TOP + 14;
        if (mode == Mode.SITES) {
            sitePanel(y);
            return;
        }
        if (mode == Mode.MAP) {
            mapPanel(y);
            return;
        }
        if (mode == Mode.SWAP) {
            swapPanel(y);
            return;
        }
        if (mode == Mode.MOVE) {
            movePanel(y);
            return;
        }
        RoadPlan.BusStop stop = selStop == null ? null : plan.busStops.stream().filter(b -> b.id == selStop).findFirst().orElse(null);
        if (stop != null && mode == Mode.EDIT) {
            stopName = new EditBox(font, 4, y, PANEL - 8, ROW - 1, Component.literal("Stop name"));
            stopName.setMaxLength(32);
            stopName.setValue(stop.name);
            stopName.setHint(Component.literal("stop name"));
            stopName.setResponder(t -> {
                stop.name = t;
                stop.built = false;                          // (its PTM2 stop is set up again with the new name)
            });
            addRenderableWidget(stopName);
            y += ROW;
            halfButtons(y, () -> "Shelter: " + (stop.shelter ? "yes" : "no"), b -> {
                remember();
                stop.shelter = !stop.shelter;
                touchStop(stop);
            }, () -> "Other side", b -> {
                remember();
                stop.side = -stop.side;
                touchStop(stop);
            });
            y += ROW;
            button(y, () -> "Delete bus stop", b -> {
                remember();
                touchStop(stop);
                plan.busStops.remove(stop);
                selStop = null;
                rebuildPanel();
            });
            return;
        }
        Node n = selNode == null ? null : plan.node(selNode);
        if (n != null && mode == Mode.EDIT) {
            int arms = plan.segmentsAt(n.id).size();
            button(y, () -> "Junction: " + n.junction.title, b -> {
                Junction[] all = Junction.values();
                n.junction = all[(n.junction.ordinal() + 1) % all.length];
                plan.touch(n.id);
                changed();
            });
            y += ROW;
            button(y, () -> "Roundabout size: " + (n.radius == 0 ? "auto" : n.radius), b -> {
                n.radius = n.radius == 0 ? 12 : n.radius >= 30 ? 0 : n.radius + 2;
                plan.touch(n.id);
                changed();
            });
            y += ROW;
            if (n.junction == Junction.ROUNDABOUT) {
                halfButtons(y, () -> "Shape: " + (n.stretch <= 0 ? "round" : n.stretch < 20 ? "oval" : n.stretch < 32 ? "long oval" : "very long"), b -> {
                    remember();
                    n.stretch = n.stretch <= 0 ? 12 : n.stretch < 20 ? 24 : n.stretch < 32 ? 40 : 0;
                    n.axis = mainAxis(n);
                    plan.touch(n.id);
                    changed();
                }, () -> "Lanes round: " + Math.max(1, n.ringLanes), b -> {
                    remember();
                    n.ringLanes = n.ringLanes >= 2 ? 1 : 2;
                    plan.touch(n.id);
                    changed();
                });
                y += ROW;
            }
            button(y, () -> "Height: " + (Double.isNaN(n.y) ? "from ground" : (int) n.y), b -> {
                n.y = Double.NaN;
                plan.touch(n.id);
                changed();
            });
            y += ROW;
            halfButtons(y, "Raise", b -> nudgeHeight(n, 1), "Lower", b -> nudgeHeight(n, -1));
            y += ROW;
            if (arms >= 3) {
                button(y, () -> "Main road: " + (n.majorA < 0 ? "auto" : "set"), b -> cycleMajor(n));
                y += ROW;
                halfButtons(y, () -> "Motorway jn: " + interchange.title, b -> {
                    com.ptmuk.road.layout.Interchange.Kind[] all = com.ptmuk.road.layout.Interchange.Kind.values();
                    interchange = all[(interchange.ordinal() + 1) % all.length];
                }, () -> "Make it", b -> {
                    remember();
                    flash(com.ptmuk.road.layout.Interchange.make(plan, n.id, interchange));
                    selNode = null;
                    changed();
                    rebuildPanel();
                });
                y += ROW;
                halfButtons(y, () -> "Flare roads", b -> {
                    remember();
                    flash(com.ptmuk.road.layout.JunctionTools.flare(plan, n.id));
                    changed();
                }, () -> "Left filters", b -> {
                    remember();
                    flash(com.ptmuk.road.layout.JunctionTools.filters(plan, n.id));
                    changed();
                });
                y += ROW;
            }
            if (arms == 1) {
                button(y, () -> "Road end: " + (n.open ? "old road carries on" : n.turning ? "turning circle" : "ends here"), b -> {
                    remember();
                    // ends here -> turning circle -> an old road carries on -> ends here
                    if (n.open) {
                        n.open = false;
                    } else if (n.turning) {
                        n.turning = false;
                        n.open = true;
                    } else {
                        n.turning = true;
                    }
                    plan.touch(n.id);
                    changed();
                });
                y += ROW;
            }
            button(y, () -> "Delete point", b -> deleteSelection());
            return;
        }
        Segment seg = selSeg == null ? null : plan.segment(selSeg);
        RoadStyle st = seg != null ? seg.style : drawStyle;
        Consumer<Runnable> edit = r -> {
            remember();
            r.run();
            if (seg != null) {
                seg.built = false;
                plan.touch(seg.a);
                plan.touch(seg.b);
            } else {
                lastStyle = drawStyle.copy();
            }
            changed();
        };
        List<RoadStyle> presets = RoadStyle.presets();
        button(y, () -> "Type: " + st.name, b -> edit.accept(() -> {
            int i = 0;
            for (int k = 0; k < presets.size(); k++) {
                if (presets.get(k).name.equals(st.name)) {
                    i = (k + 1) % presets.size();
                }
            }
            copyStyle(presets.get(i), st);
        }));
        y += ROW;
        button(y, () -> "Lanes: " + st.lanes + (st.oneWay ? " (one way)" : " each way"), b -> edit.accept(() -> st.lanes = st.lanes % 3 + 1));
        y += ROW;
        button(y, () -> "Lane width: " + st.laneWidth, b -> edit.accept(() -> st.laneWidth = st.laneWidth >= 7 ? 3 : st.laneWidth + 1));
        y += ROW;
        button(y, () -> "Centre line: " + centreName(st.centre), b -> edit.accept(() -> {
            RoadStyle.Centre[] all = RoadStyle.Centre.values();
            st.centre = all[(st.centre.ordinal() + 1) % all.length];
        }));
        y += ROW;
        button(y, () -> "Pavement: " + (st.pavement == 0 ? "none" : st.pavement + (st.kerbs ? " (with kerb)" : "")),
                b -> edit.accept(() -> st.pavement = st.pavement >= 6 ? 0 : st.pavement + 1));
        y += ROW;
        halfButtons(y, () -> "Verge: " + (st.verge == 0 ? "none" : st.verge), b -> edit.accept(() -> st.verge = st.verge >= 4 ? 0 : st.verge + 1),
                () -> "Zebra: " + (st.zebra ? "yes" : "no"), b -> edit.accept(() -> st.zebra = !st.zebra));
        y += ROW;
        halfButtons(y, () -> "Kerbs: " + (st.kerbs ? "yes" : "no"), b -> edit.accept(() -> st.kerbs = !st.kerbs),
                () -> "Lights: " + (st.lights ? "yes" : "no"), b -> edit.accept(() -> st.lights = !st.lights));
        y += ROW;
        halfButtons(y, () -> "Yellow: " + yellowName(st.yellow), b -> edit.accept(() -> {
            RoadStyle.Yellow[] all = RoadStyle.Yellow.values();
            st.yellow = all[((st.yellow == null ? 0 : st.yellow.ordinal()) + 1) % all.length];
        }), () -> "Edges: " + (st.edgeLines ? "yes" : "no"), b -> edit.accept(() -> st.edgeLines = !st.edgeLines));
        y += ROW;
        halfButtons(y, () -> "Dual: " + (st.dual ? "yes" : "no"), b -> edit.accept(() -> {
            st.dual = !st.dual;
            if (st.dual) {
                st.oneWay = false;
            }
        }), () -> "One way: " + (st.oneWay ? "yes" : "no"), b -> edit.accept(() -> {
            st.oneWay = !st.oneWay;
            if (st.oneWay) {
                st.dual = false;
            }
        }));
        y += ROW;
        halfButtons(y, () -> "Paving: " + footName(st.footSurface), b -> edit.accept(() -> {
            int i = RoadStyle.FOOTWAYS.indexOf(st.footSurface);
            st.footSurface = RoadStyle.FOOTWAYS.get((i + 1) % RoadStyle.FOOTWAYS.size());
        }), () -> "Corners: " + st.cornerRadius, b -> edit.accept(() -> st.cornerRadius = st.cornerRadius >= 14 ? 3 : st.cornerRadius + 1));
        y += ROW;
        button(y, () -> "Cycle lanes: " + (st.cycleLane ? "yes" : "no"), b -> edit.accept(() -> st.cycleLane = !st.cycleLane));
        y += ROW;
        if (seg != null) {
            halfButtons(y, () -> "Straighten", b -> edit.accept(() -> seg.bend = 0), () -> "Reverse", b -> edit.accept(() -> {
                int t = seg.a;
                seg.a = seg.b;
                seg.b = t;
                seg.bend = -seg.bend;
            }));
            y += ROW;
            button(y, () -> "Goes under: " + (seg.under ? "yes (roads over it bridge it)" : "no"), b -> edit.accept(() -> seg.under = !seg.under));
            y += ROW;
            button(y, () -> placingStop ? "Click where the bus stop goes" : "Add a bus stop", b -> {
                placingStop = !placingStop;
                flash(placingStop ? "Click beside the road, on the side the bus stop goes" : "");
            });
            y += ROW;
            button(y, () -> "Delete road", b -> deleteSelection());
            y += ROW;
            button(y, () -> "Use for new roads", b -> {
                drawStyle = st.copy();
                lastStyle = drawStyle.copy();
                flash("New roads will be " + st.name);
            });
        } else if (mode == Mode.DRAW) {
            button(y, () -> tracing ? "Following old roads: on" : "Follow an old road", b -> {
                tracing = !tracing;
                chainLast = null;
                rebuildPanel();
            });
            if (tracing) {
                button(y + ROW, () -> tracePiece ? "Follow: just " + PIECE + " blocks each way" : "Follow: the whole road", b -> tracePiece = !tracePiece);
            }
        }
    }

    /** The left panel in Map mode: the maps to choose from, or the chosen one's scale, nudge and guides. */
    private void mapPanel(int y) {
        RoadPlan.MapImport mi = plan.map;
        if (mi == null) {
            if (mapFiles == null) {
                mapFiles = MapFiles.list();
            }
            for (int i = 0; i < mapFiles.size() && i < 8; i++) {
                String f = mapFiles.get(i);
                button(y, () -> f, b -> importMap(f));
                y += ROW;
            }
            halfButtons(y, "Open folder", b -> net.minecraft.Util.getPlatform().openFile(MapFiles.folder().toFile()),
                    "Refresh", b -> mapFiles = MapFiles.list());
            return;
        }
        halfButtons(y, () -> "Scale " + String.format(java.util.Locale.ROOT, "%.1f", mi.scale) + " -", b -> rescale(mi.scale - 0.1),
                () -> "Scale +", b -> rescale(mi.scale + 0.1));
        y += ROW;
        halfButtons(y, "Nudge west", b -> nudge(-1, 0), "Nudge east", b -> nudge(1, 0));
        y += ROW;
        halfButtons(y, "Nudge north", b -> nudge(0, -1), "Nudge south", b -> nudge(0, 1));
        y += ROW;
        button(y, () -> "Guides in world: " + (mi.guides ? "on" : "off"), b -> mi.guides = !mi.guides);
        y += ROW;
        button(y, () -> "Buildings: " + (mi.buildings ? "build them" : "outlines only"), b -> mi.buildings = !mi.buildings);
        y += ROW;
        button(y, () -> "Remove map", b -> {
            remember();
            removeMapRoads();
            plan.map = null;
            showOutlines();
            changed();
        });
    }

    /** The left panel in Swap mode: what's in the box, what each block becomes, and swap. */
    // ------------------------------------------------------------------ copying and pasting sections

    private void moveSend(com.ptmuk.network.SectionMapMessage.Action action, String name) {
        int[] b = moveBox == null ? new int[4] : moveBox, p = movePaste == null ? new int[2] : movePaste;
        boolean paste = action == com.ptmuk.network.SectionMapMessage.Action.PASTE;
        com.ptmuk.network.PtmUkNetwork.CHANNEL.sendToServer(new com.ptmuk.network.SectionMapMessage(action, name,
                paste ? p[0] : b[0], paste ? p[1] : b[1], b[2], b[3], moveTurns, moveMirror, moveKeepAir, moveKeepHeight,
                hand == InteractionHand.OFF_HAND));
    }

    /** The saved sections came from the server. */
    public static void sectionList(List<com.ptmuk.building.Sections.Info> list) {
        moveList = list;
        if (movePicked != null && list.stream().noneMatch(s -> s.name().equals(movePicked))) {
            movePicked = null;
            movePaste = null;
        }
        if (Minecraft.getInstance().screen instanceof RoadPlannerScreen s && s.mode == Mode.MOVE) {
            s.rebuildPanel();
        }
    }

    private com.ptmuk.building.Sections.Info picked() {
        return moveList.stream().filter(s -> s.name().equals(movePicked)).findFirst().orElse(null);
    }

    /** Copy: a name and the box dragged on the map. Paste: the saved sections, turn, mirror, where it goes. */
    private void movePanel(int y) {
        moveName = new EditBox(font, 4, y, PANEL - 8, ROW - 1, Component.literal("Name"));
        moveName.setMaxLength(40);
        moveName.setValue(moveTyped);
        moveName.setHint(Component.literal("name for the copy"));
        addRenderableWidget(moveName);
        y += ROW;
        button(y, () -> moveBox == null ? "Drag a box to copy" : "Copy the box", b -> {
            if (moveBox == null) {
                flash("Drag a box over the area on the map first");
            } else if (moveName.getValue().isBlank()) {
                flash("Type a name for it first");
            } else {
                moveSend(com.ptmuk.network.SectionMapMessage.Action.COPY, moveName.getValue());
            }
        });
        y += ROW + 6;
        int bottom = height - 14 - ROW * (movePicked != null ? 5 : 1);
        for (com.ptmuk.building.Sections.Info s : moveList) {
            if (y + ROW > bottom) {
                break;
            }
            button(y, () -> (s.name().equals(movePicked) ? "> " : "") + s.name() + "  " + s.sx() + "x" + s.sz(), b -> {
                movePicked = s.name().equals(movePicked) ? null : s.name();
                movePaste = null;
                rebuildPanel();
            });
            y += ROW;
        }
        y = Math.max(y + 4, bottom);
        if (movePicked != null) {
            halfButtons(y, () -> "Turn: " + moveTurns * 90, b -> moveTurns = (moveTurns + 1) % 4,
                    () -> "Mirror: " + (moveMirror ? "yes" : "no"), b -> moveMirror = !moveMirror);
            y += ROW;
            halfButtons(y, () -> "Air: " + (moveKeepAir ? "keeps" : "clears"), b -> moveKeepAir = !moveKeepAir,
                    () -> "Height: " + (moveKeepHeight ? "as copied" : "ground"), b -> moveKeepHeight = !moveKeepHeight);
            y += ROW;
            button(y, () -> movePaste == null ? "Click the map where it goes" : "Paste it here", b -> {
                if (movePaste == null) {
                    flash("Click the map where the section's first corner goes");
                } else {
                    moveSend(com.ptmuk.network.SectionMapMessage.Action.PASTE, movePicked);
                    movePaste = null;
                }
            });
            y += ROW;
            button(y, () -> "Delete " + movePicked, b -> {
                moveSend(com.ptmuk.network.SectionMapMessage.Action.DELETE, movePicked);
                movePicked = null;
                movePaste = null;
            });
            y += ROW;
        }
        button(y, () -> "Undo the last paste", b -> moveSend(com.ptmuk.network.SectionMapMessage.Action.UNDO, ""));
    }

    /** The box to copy, and where the picked section goes (following the mouse until clicked). */
    private void drawMoveBox(GuiGraphics g) {
        if (mode != Mode.MOVE) {
            return;
        }
        com.ptmuk.building.Sections.Info s = picked();
        if (s != null) {
            int[] at = movePaste != null ? movePaste : overMap(mouseX, mouseY)
                    ? new int[]{(int) Math.floor(worldX(mouseX)), (int) Math.floor(worldZ(mouseY))} : null;
            if (at == null) {
                return;
            }
            var bb = com.ptmuk.building.Sections.box(s.sx(), s.sy(), s.sz(), new net.minecraft.core.BlockPos(s.ax(), s.ay(), s.az()),
                    new net.minecraft.core.BlockPos(at[0], s.ay(), at[1]), net.minecraft.world.level.block.Rotation.values()[moveTurns],
                    moveMirror ? net.minecraft.world.level.block.Mirror.FRONT_BACK : net.minecraft.world.level.block.Mirror.NONE);
            int colour = movePaste != null ? 0xFFFFC040 : 0xA0FFC040;
            g.fill((int) screenX(bb.minX()), (int) screenY(bb.minZ()), (int) screenX(bb.maxX() + 1), (int) screenY(bb.maxZ() + 1),
                    movePaste != null ? 0x40FFC040 : 0x20FFC040);
            rect(g, bb.minX(), bb.minZ(), bb.maxX() + 1, bb.maxZ() + 1, 1, colour);
            dot(g, screenX(at[0] + 0.5), screenY(at[1] + 0.5), 3, 0xFFFF4040);    // the first corner
            return;
        }
        if (moveBox != null) {
            int x0 = Math.min(moveBox[0], moveBox[2]), x1 = Math.max(moveBox[0], moveBox[2]) + 1;
            int z0 = Math.min(moveBox[1], moveBox[3]), z1 = Math.max(moveBox[1], moveBox[3]) + 1;
            g.fill((int) screenX(x0), (int) screenY(z0), (int) screenX(x1), (int) screenY(z1), 0x3040FF80);
            rect(g, x0, z0, x1, z1, 1, 0xFF40FF80);
            dot(g, screenX(moveBox[0] + 0.5), screenY(moveBox[1] + 0.5), 3, 0xFFFF4040);
            if (moveDragging) {
                g.drawString(font, (x1 - x0) + " x " + (z1 - z0), (int) mouseX + 8, (int) mouseY - 4, 0xFF40FF80);
            }
        }
    }

    private void swapPanel(int y) {
        button(y, () -> "Look at: " + (swapGroundOnly ? "open ground" : "everything"), b -> {
            swapGroundOnly = !swapGroundOnly;
            swapSend(BlockSwapMessage.Action.SCAN);
        });
        y += ROW + 2;
        swapListTop = y;
        swapShown = 0;
        if (swapRows.isEmpty()) {
            return;
        }
        int bottom = height - 10 - ROW * 3 - 4;
        int per = Math.max(1, (bottom - y - ROW) / SWAP_ENTRY);
        swapFirst = Math.max(0, Math.min(swapFirst, swapRows.size() - 1));
        for (int i = swapFirst; i < Math.min(swapRows.size(), swapFirst + per); i++) {
            SwapRow r = swapRows.get(i);
            int k = i;
            button(y + (i - swapFirst) * SWAP_ENTRY + 10, () -> "\u2192 " + blockName(r.to), b -> {
                List<String> options = new ArrayList<>();
                options.add(null);
                options.addAll(BlockSwap.suggestions(r.id));
                if (!options.contains("minecraft:air")) {
                    options.add("minecraft:air");
                }
                r.to = options.get((options.indexOf(r.to) + 1) % options.size());
                swapSel = k;
            });
            swapShown++;
        }
        if (swapRows.size() > per) {
            halfButtons(y + per * SWAP_ENTRY, "Up", b -> swapFirst = Math.max(0, swapFirst - per),
                    "Down", b -> swapFirst = Math.min(swapRows.size() - 1, swapFirst + per));
        }
        y = bottom + 10;
        if (swapSel >= 0 && swapSel < swapRows.size()) {
            SwapRow r = swapRows.get(swapSel);
            swapEdit = new EditBox(font, 4, y, PANEL - 8, ROW - 1, Component.literal("Block"));
            swapEdit.setMaxLength(128);
            swapEdit.setValue(r.to == null ? "" : r.to);
            swapEdit.setResponder(v -> {
                ResourceLocation id = ResourceLocation.tryParse(v.contains(":") ? v : "minecraft:" + v);
                boolean ok = id != null && ForgeRegistries.BLOCKS.containsKey(id);
                swapEdit.setTextColor(ok ? 0xE0E0E0 : 0xFF6060);
                if (ok) {
                    r.to = id.toString();
                }
            });
            addRenderableWidget(swapEdit);
        }
        y += ROW;
        halfButtons(y, "Suggested", b -> swapRows.forEach(r -> {
            List<String> sug = BlockSwap.suggestions(r.id);
            if (!sug.isEmpty()) {
                r.to = sug.get(0);
            }
        }), "Keep all", b -> swapRows.forEach(r -> r.to = null));
        y += ROW;
        halfButtons(y, "Swap", b -> swapSend(BlockSwapMessage.Action.APPLY), "Undo swap", b -> swapSend(BlockSwapMessage.Action.UNDO));
    }

    private static String blockName(String id) {
        return id == null ? "keep" : id.equals("minecraft:air") ? "remove" : id.substring(id.indexOf(':') + 1).replace('_', ' ');
    }

    /** Ask the server to list the box, swap the chosen blocks in it, or undo the last swap. */
    private void swapSend(BlockSwapMessage.Action action) {
        if (swapBox == null && action != BlockSwapMessage.Action.UNDO) {
            flash("Drag a box over the area first");
            return;
        }
        int[] box = swapBox == null ? new int[4] : swapBox;
        List<String> from = new ArrayList<>(), to = new ArrayList<>();
        if (action == BlockSwapMessage.Action.APPLY) {
            for (SwapRow r : swapRows) {
                if (r.to != null) {
                    from.add(r.id);
                    to.add(r.to);
                }
            }
            if (from.isEmpty()) {
                flash("Pick what some blocks become first (click the arrows, or Suggested)");
                return;
            }
        }
        swapNote = action == BlockSwapMessage.Action.SCAN ? "Looking..." : action == BlockSwapMessage.Action.APPLY ? "Swapping..." : "Undoing...";
        PtmUkNetwork.CHANNEL.sendToServer(new BlockSwapMessage(action, box[0], box[1], box[2], box[3], swapGroundOnly, from, to));
    }

    /** What's in the box, from the server: keep the choices already made for blocks still there. */
    public static void swapList(BlockSwapListMessage msg) {
        if (!(Minecraft.getInstance().screen instanceof RoadPlannerScreen s)) {
            return;
        }
        java.util.Map<String, String> chosen = new java.util.HashMap<>();
        s.swapRows.forEach(r -> chosen.put(r.id, r.to));
        s.swapRows.clear();
        for (int i = 0; swapBox != null && i < msg.ids().size(); i++) {
            SwapRow r = new SwapRow(msg.ids().get(i), msg.counts()[i]);
            r.to = msg.note().startsWith("Swapped") ? null : chosen.get(r.id);    // after a swap, start afresh
            s.swapRows.add(r);
        }
        s.swapSel = -1;
        s.swapNote = msg.note().isEmpty() ? s.swapRows.size() + " kinds of block" : msg.note();
        s.rebuildPanel();
    }

    /** Read a map file in the background, then carry on with it on the game thread (see tick). */
    private void readMap(String file, Consumer<OsmMap> then) {
        mapJob = MapFiles.get(file);
        mapThen = then;
    }

    /** A real map's roads into the plan, its middle at the middle of the map view. */
    private void importMap(String file) {
        flash("Reading " + file + "...");
        readMap(file, m -> {
            remember();
            RoadPlan.MapImport mi = new RoadPlan.MapImport();
            mi.file = file;
            mi.lat0 = m.midLat();
            mi.lon0 = m.midLon();
            mi.x = (int) Math.floor(camX);
            mi.z = (int) Math.floor(camZ);
            plan.map = mi;
            layMapRoads(m);
        });
    }

    private void layMapRoads(OsmMap m) {
        osm = m;
        RoadPlan.MapImport mi = plan.map;
        List<OsmMap.Outline> b = m.buildings(mi);
        // all of it: a big map is previewed and built a part at a time, round where you are
        int roads = m.roads(plan, mi, b, -Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
        showOutlines();
        changed();
        int kb = RoadPlans.pack(plan).length / 1024;
        flash(kb > RoadPlans.MAX_PLAN / 1024 ? "That map's too big for one planner (" + kb + " KB of " + RoadPlans.MAX_PLAN / 1024 + ") - export a smaller area"
                : roads + " roads and " + b.size() + " buildings from " + mi.file);
    }

    /** Take out the map's roads that haven't been built (and points left with no roads). */
    private void removeMapRoads() {
        RoadPlan.MapImport mi = plan.map;
        plan.segments.removeIf(sg -> mi.owns(sg.id) && !sg.built);
        plan.nodes.removeIf(n -> mi.owns(n.id) && !n.built && plan.segmentsAt(n.id).isEmpty());
    }

    private void rescale(double scale) {
        RoadPlan.MapImport mi = plan.map;
        double to = Math.max(1, Math.min(3, Math.round(scale * 10) / 10.0));
        readMap(mi.file, m -> {
            remember();
            removeMapRoads();
            mi.scale = to;
            layMapRoads(m);
        });
    }

    private void nudge(int dx, int dz) {
        plan.map.nudgeX += dx;
        plan.map.nudgeZ += dz;
        showOutlines();
    }

    /** The map's buildings, nudged, drawn into a picture laid over the planner's map. */
    private void showOutlines() {
        RoadPlan.MapImport mi = plan.map;
        outlines = mi == null || osm == null ? null : osm.buildings(mi);
        if (outlines == null || map == null) {
            return;
        }
        if (outlineTex == null) {
            outlineTex = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, map.size, map.size, true));
            Minecraft.getInstance().getTextureManager().register(OUTLINES, outlineTex);
        }
        NativeImage img = outlineTex.getPixels();
        if (img == null) {
            return;
        }
        img.fillRect(0, 0, map.size, map.size, 0);
        int colour = abgr(0xFF8C1A, 255);
        for (OsmMap.Outline o : outlines) {
            for (int i = 0; i + 1 < o.x().length; i++) {
                double ax = o.x()[i] + mi.nudgeX - map.originX, az = o.z()[i] + mi.nudgeZ - map.originZ;
                double bx = o.x()[i + 1] + mi.nudgeX - map.originX, bz = o.z()[i + 1] + mi.nudgeZ - map.originZ;
                int steps = (int) Math.ceil(Math.max(Math.abs(bx - ax), Math.abs(bz - az))) + 1;
                for (int k = 0; k <= steps; k++) {
                    int px = (int) Math.floor(ax + (bx - ax) * k / steps), pz = (int) Math.floor(az + (bz - az) * k / steps);
                    if (px >= 0 && pz >= 0 && px < map.size && pz < map.size) {
                        img.setPixelRGBA(px, pz, colour);
                    }
                }
            }
        }
        outlineTex.upload();
    }

    /** The left panel in Sites mode: what a new site will be, or the selected site's options. */
    private void sitePanel(int y) {
        Site st = selSite == null ? null : plan.site(selSite);
        if (st == null) {
            button(y, () -> "New site: " + newSiteType.title, b -> {
                SiteType[] all = SiteType.values();
                newSiteType = all[(newSiteType.ordinal() + 1) % all.length];
                rebuildPanel();
            });
            if (newSiteType == SiteType.TOWN || newSiteType == SiteType.DISTRICT) {
                button(y + ROW, () -> "Shape: " + (drawTown ? "draw round it" : "box"), b -> drawTown = !drawTown);
            }
            if (newSiteType == SiteType.DISTRICT) {
                button(y + 2 * ROW, () -> "Kind: " + newDistrict.title, b -> {
                    RoadPlan.District[] all = RoadPlan.District.values();
                    newDistrict = all[(newDistrict.ordinal() + 1) % all.length];
                });
            }
            return;
        }
        Consumer<Runnable> edit = r -> {
            remember();
            r.run();
            st.built = false;
            changed();
        };
        button(y, () -> "Type: " + st.type.title, b -> edit.accept(() -> {
            SiteType[] all = SiteType.values();
            st.type = all[(st.type.ordinal() + 1) % all.length];
            if (st.type != SiteType.TOWN && st.type != SiteType.GREEN && st.type != SiteType.DISTRICT) {
                st.outline = null;                    // only a town, a district and the green a town leaves keep a drawn shape
            }
            siteDefaults(st);
        }));
        y += ROW;
        if (st.type.ground()) {
            button(y, () -> "Road side: " + sideName(st.front), b -> edit.accept(() -> st.front = st.front >= 3 ? -1 : st.front + 1));
            y += ROW;
        }
        if (st.type == SiteType.TOWN) {
            button(y, () -> "Main road: " + (st.mainRoad ? "yes" : "no"), b -> edit.accept(() -> st.mainRoad = !st.mainRoad));
            y += ROW;
            button(y, () -> "High street: " + (st.highStreet ? "yes" : "no"), b -> edit.accept(() -> st.highStreet = !st.highStreet));
            y += ROW;
            button(y, () -> "Houses: " + TOWN_HOUSES[st.houses % TOWN_HOUSES.length], b -> edit.accept(() -> st.houses = (st.houses + 1) % TOWN_HOUSES.length));
            y += ROW;
            button(y, () -> "Church, pub etc: " + (st.civic ? "yes" : "no"), b -> edit.accept(() -> st.civic = !st.civic));
            y += ROW;
            halfButtons(y, () -> "Streets: " + (st.winding || st.outline != null ? "natural" : "grid"), b -> {
                        if (st.outline != null) {
                            flash("A drawn town's streets follow its shape");
                        } else {
                            edit.accept(() -> st.winding = !st.winding);
                        }
                    },
                    () -> "Kerbs: " + (st.kerbs ? "yes" : "no"), b -> edit.accept(() -> st.kerbs = !st.kerbs));
            y += ROW;
            button(y, () -> "Lay out the town", b -> {
                remember();
                flash(com.ptmuk.road.layout.TownLayout.layOut(plan, st, net.minecraft.util.RandomSource.create()));
                selSite = null;
                changed();
                rebuildPanel();
            });
            y += ROW;
            button(y, () -> "Delete site", b -> deleteSelection());
            return;
        }
        if (st.type == SiteType.DISTRICT) {
            button(y, () -> "Kind: " + st.district.title, b -> edit.accept(() -> {
                RoadPlan.District[] all = RoadPlan.District.values();
                st.district = all[(st.district.ordinal() + 1) % all.length];
            }));
            y += ROW;
            if (st.district == RoadPlan.District.HOUSES) {
                button(y, () -> "Houses: " + TOWN_HOUSES[st.houses % TOWN_HOUSES.length], b -> edit.accept(() -> st.houses = (st.houses + 1) % TOWN_HOUSES.length));
                y += ROW;
            }
            button(y, () -> "Lay out the town", b -> {
                remember();
                flash(com.ptmuk.road.layout.TownLayout.layOutDistricts(plan, st, net.minecraft.util.RandomSource.create()));
                selSite = null;
                changed();
                rebuildPanel();
            });
            y += ROW;
            button(y, () -> "Delete site", b -> deleteSelection());
            return;
        }
        if (st.type == SiteType.KEEP_CLEAR || st.type == SiteType.GREEN) {
            button(y, () -> "Delete site", b -> deleteSelection());
            return;
        }
        if (st.type == SiteType.PARADE || SiteLayout.units(st.type) != SiteLayout.PARADE_UNITS) {
            button(y, () -> st.type == SiteType.PARADE ? "Shuffle shops" : "Shuffle", b -> edit.accept(() -> st.brand = (st.brand + 1) % 16));
            y += ROW;
            button(y, () -> "Height: " + (Double.isNaN(st.y) ? "from ground" : (int) st.y), b -> edit.accept(() -> st.y = Double.NaN));
            y += ROW;
            halfButtons(y, "Raise", b -> nudgeSite(st, 1), "Lower", b -> nudgeSite(st, -1));
            y += ROW;
            button(y, () -> "Delete site", b -> deleteSelection());
            return;
        }
        button(y, () -> "Bays: " + (st.rows == 0 ? "facing the road" : st.rows == 1 ? "along the road" : "most that fit"),
                b -> edit.accept(() -> st.rows = st.rows >= 1 ? -1 : st.rows + 1));
        y += ROW;
        halfButtons(y, () -> "Disabled: " + st.disabled, b -> edit.accept(() -> st.disabled = (st.disabled + 1) % 9),
                () -> "Parent: " + st.parentChild, b -> edit.accept(() -> st.parentChild = (st.parentChild + 1) % 7));
        y += ROW;
        halfButtons(y, () -> "EV bays: " + st.ev, b -> edit.accept(() -> st.ev = (st.ev + 1) % 9),
                () -> "Lights: " + (st.lights ? "yes" : "no"), b -> edit.accept(() -> st.lights = !st.lights));
        y += ROW;
        button(y, () -> "Trees: " + (st.trees == 0 ? "none" : st.trees == 1 ? "at the row ends" : "lots"),
                b -> edit.accept(() -> st.trees = (st.trees + 1) % 3));
        y += ROW;
        if (st.type == SiteType.RETAIL_PARK) {
            halfButtons(y, () -> "Shops: " + (st.units == 0 ? "auto" : st.units), b -> edit.accept(() -> st.units = (st.units + 1) % 9),
                    () -> "Depth: " + st.unitDepth, b -> edit.accept(() -> st.unitDepth = st.unitDepth >= 44 ? 16 : st.unitDepth + 4));
            y += ROW;
            halfButtons(y, () -> "Canopy: " + (st.canopy ? "yes" : "no"), b -> edit.accept(() -> st.canopy = !st.canopy),
                    () -> "Names", b -> edit.accept(() -> st.brand = (st.brand + 1) % SiteLayout.RETAIL_BRANDS.size()));
            y += ROW;
        } else if (st.type == SiteType.DRIVE_THRU) {
            button(y, () -> "Name: " + SiteLayout.DRIVE_THRU_BRANDS.get(Math.floorMod(st.brand, SiteLayout.DRIVE_THRU_BRANDS.size())).name(),
                    b -> edit.accept(() -> st.brand = (st.brand + 1) % SiteLayout.DRIVE_THRU_BRANDS.size()));
            y += ROW;
        }
        button(y, () -> "Height: " + (Double.isNaN(st.y) ? "from ground" : (int) st.y), b -> edit.accept(() -> st.y = Double.NaN));
        y += ROW;
        halfButtons(y, "Raise", b -> nudgeSite(st, 1), "Lower", b -> nudgeSite(st, -1));
        y += ROW;
        button(y, () -> "Delete site", b -> deleteSelection());
    }

    private static final String[] TOWN_HOUSES = {"Victorian terraces", "1930s semis", "Detached", "A mix"};

    private static String sideName(int side) {
        return switch (side) {
            case 0 -> "south";
            case 1 -> "west";
            case 2 -> "north";
            case 3 -> "east";
            default -> "auto";
        };
    }

    /** Sensible bays for each kind of site. */
    private static void siteDefaults(Site st) {
        switch (st.type) {
            case CAR_PARK -> {
                st.disabled = 2;
                st.parentChild = 0;
                st.ev = 2;
            }
            case RETAIL_PARK -> {
                st.disabled = 4;
                st.parentChild = 2;
                st.ev = 2;
            }
            case DRIVE_THRU -> {
                st.disabled = 2;
                st.parentChild = 0;
                st.ev = 0;
            }
        }
    }

    private void nudgeSite(Site st, int dy) {
        remember();
        double base = Double.isNaN(st.y) ? (preview != null ? preview.siteLevel(st.id) : 64) : st.y;
        st.y = base + dy;
        st.built = false;
        changed();
    }

    private SiteLayout siteLayout(int id) {
        if (preview == null) {
            return null;
        }
        for (SiteLayout sl : preview.sites) {
            if (sl.site.id == id) {
                return sl;
            }
        }
        return null;
    }

    private Site siteAt(double sx, double sy) {
        double wx = worldX(sx), wz = worldZ(sy);
        for (int i = plan.sites.size() - 1; i >= 0; i--) {
            if (plan.sites.get(i).inOutline(wx, wz)) {
                return plan.sites.get(i);
            }
        }
        return null;
    }

    /** Which corner of a site is under the mouse (bit 0: east, bit 1: south), or -1. */
    private int cornerAt(Site st, double sx, double sy) {
        if (st.outline != null) {
            return -1;                                // a drawn town is redrawn, not resized
        }
        for (int k = 0; k < 4; k++) {
            double cx = screenX((k & 1) != 0 ? st.x1 + 1 : st.x0), cy = screenY((k & 2) != 0 ? st.z1 + 1 : st.z0);
            if (Math.hypot(cx - sx, cy - sy) < 7) {
                return k;
            }
        }
        return -1;
    }

    private static void copyStyle(RoadStyle from, RoadStyle to) {
        RoadStyle c = from.copy();
        to.name = c.name;
        to.lanes = c.lanes;
        to.laneWidth = c.laneWidth;
        to.oneWay = c.oneWay;
        to.dual = c.dual;
        to.reservation = c.reservation;
        to.centre = c.centre;
        to.laneLinesSolid = c.laneLinesSolid;
        to.edgeLines = c.edgeLines;
        to.doubleYellow = c.doubleYellow;
        to.yellow = c.yellow;
        to.footSurface = c.footSurface;
        to.kerbs = c.kerbs;
        to.pavement = c.pavement;
        to.verge = c.verge;
        to.lights = c.lights;
        to.lightSpacing = c.lightSpacing;
        to.cornerRadius = c.cornerRadius;
        to.surface = c.surface;
        to.zebra = c.zebra;
        to.cycleLane = c.cycleLane;
    }

    /** The way an oval roundabout lies: along the two roads into it that run most nearly straight through. */
    private double mainAxis(Node n) {
        List<Segment> arms = plan.segmentsAt(n.id);
        double best = 2, axis = n.axis;
        for (int i = 0; i < arms.size(); i++) {
            for (int k = i + 1; k < arms.size(); k++) {
                Node a = plan.node(arms.get(i).a == n.id ? arms.get(i).b : arms.get(i).a), b = plan.node(arms.get(k).a == n.id ? arms.get(k).b : arms.get(k).a);
                double ax = a.x - n.x, az = a.z - n.z, bx = b.x - n.x, bz = b.z - n.z;
                double dot = (ax * bx + az * bz) / Math.max(1e-9, Math.hypot(ax, az) * Math.hypot(bx, bz));
                if (dot < best) {
                    best = dot;
                    axis = Math.atan2(az, ax);
                }
            }
        }
        return axis;
    }

    private static String yellowName(RoadStyle.Yellow y) {
        return y == null ? "none" : switch (y) {
            case NONE -> "none";
            case SINGLE -> "single";
            case DOUBLE -> "double";
            case AUTO -> "auto";
            case BAYS -> "bays";
        };
    }

    private static String footName(String id) {
        if (id == null) {
            return "tarmac";
        }
        return id.contains("stone_sidewalk") ? "concrete" : id.contains("used_asphalt") ? "tarmac"
                : id.contains("asphalt") ? "dark tarmac" : id.contains("block_paving") ? "blocks" : "slabs";
    }

    private static String centreName(RoadStyle.Centre c) {
        return switch (c) {
            case NONE -> "none";
            case DASHED -> "dashed";
            case HAZARD -> "hazard";
            case DOUBLE_WHITE -> "double white";
        };
    }

    private interface Label {
        String get();
    }

    private void button(int y, Label label, Button.OnPress press) {
        Button b = Button.builder(Component.literal(label.get()), btn -> {
            press.onPress(btn);
            rebuildPanel();
        }).bounds(4, y, PANEL - 8, ROW - 1).build();
        panelButtons.add(addRenderableWidget(b));
    }

    private void halfButtons(int y, Label l1, Button.OnPress p1, Label l2, Button.OnPress p2) {
        int w = (PANEL - 10) / 2;
        panelButtons.add(addRenderableWidget(Button.builder(Component.literal(l1.get()), btn -> {
            p1.onPress(btn);
            rebuildPanel();
        }).bounds(4, y, w, ROW - 1).build()));
        panelButtons.add(addRenderableWidget(Button.builder(Component.literal(l2.get()), btn -> {
            p2.onPress(btn);
            rebuildPanel();
        }).bounds(6 + w, y, w, ROW - 1).build()));
    }

    private void halfButtons(int y, String l1, Button.OnPress p1, String l2, Button.OnPress p2) {
        halfButtons(y, () -> l1, p1, () -> l2, p2);
    }

    private void nudgeHeight(Node n, int dy) {
        remember();
        double base = Double.isNaN(n.y) ? (preview != null ? preview.heightOf(n.id) : map == null ? 64 : map.groundAt((int) n.x, (int) n.z)) : n.y;
        n.y = base + dy;
        plan.touch(n.id);
        changed();
    }

    /** Pick which two roads carry straight on through a give-way junction. */
    private void cycleMajor(Node n) {
        List<Segment> arms = plan.segmentsAt(n.id);
        List<int[]> pairs = new ArrayList<>();
        for (int a = 0; a < arms.size(); a++) {
            for (int b = a + 1; b < arms.size(); b++) {
                pairs.add(new int[]{arms.get(a).id, arms.get(b).id});
            }
        }
        int cur = -1;
        for (int i = 0; i < pairs.size(); i++) {
            if (pairs.get(i)[0] == n.majorA && pairs.get(i)[1] == n.majorB) {
                cur = i;
            }
        }
        remember();
        if (cur + 1 >= pairs.size()) {
            n.majorA = n.majorB = -1;
        } else {
            n.majorA = pairs.get(cur + 1)[0];
            n.majorB = pairs.get(cur + 1)[1];
        }
        plan.touch(n.id);
        changed();
    }

    // ------------------------------------------------------------------ plan changes

    private void remember() {
        history.add(RoadPlans.pack(plan));                 // packed: a whole city's plan is megabytes of JSON
        if (history.size() > 60) {
            history.remove(0);
        }
    }

    private void undoEdit() {
        if (history.isEmpty()) {
            flash("Nothing to undo");
            return;
        }
        plan = RoadPlans.unpack(history.remove(history.size() - 1));
        chainLast = null;
        selNode = selSeg = null;
        selSite = null;
        showOutlines();
        changed();
    }

    private void changed() {
        lines = Layout.lines(plan);
        previewStale = true;
        rebuildPanel();
    }

    private void deleteSelection() {
        remember();
        if (mode == Mode.SITES && selSite != null) {
            final int id = selSite;
            plan.sites.removeIf(st -> st.id == id);
            selSite = null;
            changed();
            return;
        }
        if (selNode != null) {
            for (Segment s : plan.segmentsAt(selNode)) {
                plan.touch(s.a == selNode ? s.b : s.a);
            }
            plan.removeNode(selNode);
        } else if (selSeg != null) {
            Segment s = plan.segment(selSeg);
            if (s != null) {
                plan.touch(s.a);
                plan.touch(s.b);
            }
            plan.removeSegment(selSeg);
        }
        selNode = selSeg = null;
        chainLast = null;
        changed();
    }

    private void flash(String text) {
        message = text;
        messageTicks = 80;
    }

    private void build() {
        if (preview == null && plan.segments.isEmpty() && plan.sites.isEmpty()) {
            flash("Draw a road or a site first");
            return;
        }
        if (plan.sites.stream().anyMatch(st -> st.type == SiteType.TOWN || st.type == SiteType.DISTRICT)) {
            // towns and districts not laid out yet are laid out now (Undo takes it back)
            remember();
            String said = com.ptmuk.road.layout.TownLayout.layOutAll(plan, net.minecraft.util.RandomSource.create());
            selSite = null;
            changed();
            rebuildPanel();
            if (plan.sites.stream().anyMatch(st -> st.type == SiteType.TOWN || st.type == SiteType.DISTRICT)) {
                flash(said);                                    // one didn't fit: say why and build nothing yet
                return;
            }
        }
        byte[] data = RoadPlans.pack(plan);
        if (data.length > RoadPlans.MAX_PLAN) {
            flash("The plan is too big (" + data.length / 1024 + " KB of " + RoadPlans.MAX_PLAN / 1024 + ") - remove the map or some roads");
            return;
        }
        RoadPlan.MapImport mi = plan.map;
        if (mi != null && mi.buildings) {
            if (outlines == null) {
                flash("The map's still loading - try again in a moment");
                return;
            }
            // the buildings round you go first: the server builds them with the roads
            var player = Minecraft.getInstance().player;
            MapBuildings.Batch batch = MapBuildings.around(outlines, mi, player.getX(), player.getZ(), RoadPlan.PART);
            byte[] packed = batch == null ? null : MapBuildings.pack(batch);
            if (packed != null && packed.length <= RoadPlans.MAX_PLAN) {
                RoadPlannerMessage.send(RoadPlannerMessage.Action.BUILDINGS, packed, hand == InteractionHand.OFF_HAND);
            }
        }
        RoadPlannerMessage.send(RoadPlannerMessage.Action.BUILD, data, hand == InteractionHand.OFF_HAND);
        Minecraft.getInstance().setScreen(null);
    }

    private void send(RoadPlannerMessage.Action action) {
        RoadPlannerMessage.send(action, new byte[0], hand == InteractionHand.OFF_HAND);
    }

    @Override
    public void removed() {
        byte[] data = RoadPlans.pack(plan);
        if (data.length > RoadPlans.MAX_PLAN) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.literal("Road plan too big to save - your changes are lost"), false);
        } else if (stack() != null) {
            RoadPlannerMessage.send(RoadPlannerMessage.Action.SAVE, data, hand == InteractionHand.OFF_HAND);
        }
        if (map != null) {
            map.close();
        }
        Minecraft.getInstance().getTextureManager().release(PREVIEW);
        Minecraft.getInstance().getTextureManager().release(OUTLINES);
        lastZoom = zoom;
        super.removed();
    }

    // ------------------------------------------------------------------ coordinates

    private int mapLeft() {
        return PANEL;
    }

    private double worldX(double sx) {
        return camX + (sx - (mapLeft() + (width - mapLeft()) / 2.0)) / zoom;
    }

    private double worldZ(double sy) {
        return camZ + (sy - (TOP + (height - TOP) / 2.0)) / zoom;
    }

    private double screenX(double wx) {
        return mapLeft() + (width - mapLeft()) / 2.0 + (wx - camX) * zoom;
    }

    private double screenY(double wz) {
        return TOP + (height - TOP) / 2.0 + (wz - camZ) * zoom;
    }

    private boolean overMap(double sx, double sy) {
        return sx >= mapLeft() && sy >= TOP;
    }

    private Node nodeAt(double sx, double sy) {
        Node best = null;
        double bestD = Math.max(6, 2 * zoom);
        for (Node n : plan.nodes) {
            double d = Math.hypot(screenX(n.x + 0.5) - sx, screenY(n.z + 0.5) - sy);
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    /** The road under a point, and where along it (sample index). */
    private int[] segmentAt(double sx, double sy) {
        double wx = worldX(sx), wz = worldZ(sy);
        int best = -1, bestI = -1;
        double bestD = Double.MAX_VALUE;
        for (Segment s : plan.segments) {
            Curve c = lines.curves.get(s.id);
            if (c == null) {
                continue;
            }
            double reach = Math.max(s.style.halfCarriageway(), 4 / zoom);
            for (int i = 0; i < c.n; i += 2) {
                double d = Math.hypot(c.x[i] - wx, c.z[i] - wz);
                if (d < reach && d < bestD) {
                    bestD = d;
                    best = s.id;
                    bestI = i;
                }
            }
        }
        return best < 0 ? null : new int[]{best, bestI};
    }

    private double[] bendHandle(Segment s) {
        Curve c = lines.curves.get(s.id);
        if (c == null) {
            return null;
        }
        int i = c.n / 2;
        return new double[]{screenX(c.x[i]), screenY(c.z[i])};
    }

    // ------------------------------------------------------------------ mouse and keys

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (!overMap(mx, my)) {
            return false;
        }
        pressX = lastMouseX = mx;
        pressY = lastMouseY = my;
        moved = false;
        if (button == 1 || button == 2) {
            panning = true;
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (mode == Mode.DRAW) {
            drawClick(mx, my);
            return true;
        }
        if (mode == Mode.SITES) {
            siteClick(mx, my);
            return true;
        }
        if (mode == Mode.MOVE) {
            if (moveName != null) {
                moveName.setFocused(false);
                setFocused(null);
            }
            int wx = (int) Math.floor(worldX(mx)), wz = (int) Math.floor(worldZ(my));
            if (movePicked != null) {
                movePaste = new int[]{wx, wz};
                rebuildPanel();
            } else {
                moveDragging = true;
                moveBox = new int[]{wx, wz, wx, wz};
            }
            return true;
        }
        if (mode == Mode.SWAP) {
            if (swapEdit != null) {
                swapEdit.setFocused(false);
                setFocused(null);
            }
            swapDragging = true;
            int wx = (int) Math.floor(worldX(mx)), wz = (int) Math.floor(worldZ(my));
            swapBox = new int[]{wx, wz, wx, wz};
            return true;
        }
        if (mode == Mode.MAP) {
            double[] p = {worldX(mx), worldZ(my)};
            if (measureA == null || measureB != null) {
                measureA = p;
                measureB = null;
            } else {
                measureB = p;
            }
            return true;
        }
        // edit: placing a bus stop, a bus stop, the selected road's bend handle, a point, or a road
        if (placingStop) {
            placingStop = false;
            placeStop(worldX(mx), worldZ(my));
            return true;
        }
        RoadPlan.BusStop stopHit = stopAt(mx, my);
        if (stopHit != null) {
            selStop = stopHit.id;
            selNode = selSeg = null;
            rebuildPanel();
            return true;
        }
        selStop = null;
        if (selSeg != null) {
            Segment s = plan.segment(selSeg);
            double[] hnd = s == null ? null : bendHandle(s);
            if (hnd != null && Math.hypot(hnd[0] - mx, hnd[1] - my) < 7) {
                remember();
                draggingBend = true;
                return true;
            }
        }
        Node n = nodeAt(mx, my);
        if (n != null) {
            selNode = n.id;
            selSeg = null;
            remember();
            draggingNode = true;
            rebuildPanel();
            return true;
        }
        int[] hit = segmentAt(mx, my);
        selNode = null;
        selSeg = hit == null ? null : hit[0];
        rebuildPanel();
        return true;
    }

    /** The road a bus stop's on is built again (its stop box, transport point and kerb things). */
    private void touchStop(RoadPlan.BusStop b) {
        Segment s = plan.segment(b.segment);
        if (s != null) {
            s.built = false;
        }
        b.built = false;
        changed();
    }

    /** Where a bus stop's flag sits on the map: beside its road's kerb, so far along it. */
    private double[] stopSpot(RoadPlan.BusStop b) {
        Curve c = lines.curves.get(b.segment);
        Segment s = plan.segment(b.segment);
        if (c == null || s == null) {
            return null;
        }
        int i = c.index(b.at * c.length), side = s.style.oneWay ? 1 : b.side;
        double d = side * (s.style.halfCarriageway() + 1.5);
        return new double[]{c.x[i] + c.leftX(i) * d, c.z[i] + c.leftZ(i) * d};
    }

    private RoadPlan.BusStop stopAt(double mx, double my) {
        for (RoadPlan.BusStop b : plan.busStops) {
            double[] p = stopSpot(b);
            if (p != null && Math.hypot(screenX(p[0]) - mx, screenY(p[1]) - my) < 7) {
                return b;
            }
        }
        return null;
    }

    /** A new bus stop on the road nearest (wx, wz), on the side that's on, named after the road. */
    private void placeStop(double wx, double wz) {
        Segment best = null;
        double bestD = 40, at = 0.5;
        int side = 1;
        for (Segment s : plan.segments) {
            Curve c = lines.curves.get(s.id);
            if (c == null) {
                continue;
            }
            for (int i = 0; i < c.n; i += 2) {
                double dx = wx - c.x[i], dz = wz - c.z[i], d = Math.hypot(dx, dz);
                if (d < bestD) {
                    bestD = d;
                    best = s;
                    at = c.s(i) / c.length;
                    side = dx * c.leftX(i) + dz * c.leftZ(i) >= 0 ? 1 : -1;
                }
            }
        }
        if (best == null) {
            flash("Click beside a road to put a bus stop there");
            return;
        }
        remember();
        Segment on = best;
        long same = plan.busStops.stream().filter(b -> b.segment == on.id).count();
        String street = on.name == null || on.name.isBlank() ? "Bus Stop" : on.name;
        RoadPlan.BusStop b = plan.addBusStop(on.id, at, side, same == 0 ? street : street + " " + (char) ('A' + same));
        touchStop(b);
        selStop = b.id;
        selSeg = null;
        rebuildPanel();
        flash("Bus stop added: name it, then Build");
    }

    /** Bus stops on the map: a red and white marker by the kerb, yellow when selected. */
    private void drawStops(GuiGraphics g) {
        for (RoadPlan.BusStop b : plan.busStops) {
            double[] p = stopSpot(b);
            if (p == null) {
                continue;
            }
            int x = (int) screenX(p[0]), y = (int) screenY(p[1]);
            boolean sel = selStop != null && selStop == b.id;
            g.fill(x - 4, y - 4, x + 5, y + 5, 0xFF000000);
            g.fill(x - 3, y - 3, x + 4, y + 4, sel ? 0xFFFFD24A : 0xFFD8202A);
            g.drawCenteredString(font, "B", x + 1, y - 3, 0xFFFFFFFF);
            if (zoom >= 2 && !b.name.isBlank()) {
                g.drawString(font, b.name, x + 7, y - 4, 0xFFFFE0A0);
            }
        }
    }

    private void siteClick(double mx, double my) {
        Site sel = selSite == null ? null : plan.site(selSite);
        int corner = sel == null ? -1 : cornerAt(sel, mx, my);
        if (corner >= 0) {
            remember();
            siteDrag = 3;
            siteCorner = corner;
            return;
        }
        Site hit = siteAt(mx, my);
        if (hit != null) {
            selSite = hit.id;
            remember();
            siteDrag = 2;
            grabX = worldX(mx);
            grabZ = worldZ(my);
            siteStart = new int[]{hit.x0, hit.z0, hit.x1, hit.z1};
            rebuildPanel();
            return;
        }
        selSite = null;
        if ((newSiteType == SiteType.TOWN || newSiteType == SiteType.DISTRICT) && drawTown) {
            siteDrag = 4;
            lasso.clear();
            lasso.add(new int[]{(int) Math.round(worldX(mx)), (int) Math.round(worldZ(my))});
            rebuildPanel();
            return;
        }
        siteDrag = 1;
        dragX0 = dragX1 = (int) Math.floor(worldX(mx));
        dragZ0 = dragZ1 = (int) Math.floor(worldZ(my));
        rebuildPanel();
    }

    private void drawClick(double mx, double my) {
        if (tracing) {
            PtmUkNetwork.CHANNEL.sendToServer(new TraceRoadMessage((int) Math.floor(worldX(mx)), (int) Math.floor(worldZ(my)), tracePiece ? PIECE : 0));
            flash("Following the road...");
            return;
        }
        remember();
        Node hitNode = nodeAt(mx, my);
        boolean joined = false;
        Node n;
        if (hitNode != null) {
            n = hitNode;
            joined = true;
        } else {
            int[] hit = segmentAt(mx, my);
            if (hit != null) {
                Segment host = plan.segment(hit[0]);
                boolean slip = hasShiftDown() || (drawStyle.oneWay && host != null && host.style.dual);
                if (slip) {
                    // a slip road: it merges into the side of the road without splitting it
                    n = plan.addNode(Math.floor(worldX(mx)), Math.floor(worldZ(my)));
                    if (host != null) {
                        host.built = false;
                    }
                } else {
                    n = split(hit[0], hit[1]);
                }
                joined = true;
            } else {
                n = plan.addNode(Math.floor(worldX(mx)), Math.floor(worldZ(my)));
            }
        }
        if (n == null) {
            return;
        }
        if (chainLast != null && chainLast.id != n.id) {
            boolean exists = false;
            for (Segment s : plan.segmentsAt(n.id)) {
                if (s.a == chainLast.id || s.b == chainLast.id) {
                    exists = true;
                }
            }
            if (!exists) {
                plan.addSegment(chainLast.id, n.id, drawStyle);
                plan.touch(n.id);
                plan.touch(chainLast.id);
            }
            chainLast = joined ? null : n;          // ending on another road finishes this one
            if (joined) {
                flash("Joined - click to start another road");
            }
        } else {
            chainLast = n;
        }
        changed();
    }

    /** An old road followed by the server: points down its middle, joined to points already there at its ends. */
    public static void traced(TraceResultMessage r) {
        if (Minecraft.getInstance().screen instanceof RoadPlannerScreen s) {
            s.addTraced(r);
        }
    }

    private void addTraced(TraceResultMessage r) {
        int n = r.x().length;
        if (n < 2) {
            flash("That isn't an old road - click the middle of its surface");
            return;
        }
        remember();
        boolean[] keep = OsmMap.simplify(r.x(), r.z(), 0, n - 1, 2.0);       // old roads are blocky: don't follow every step
        List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (r.junction()[i]) {
                keep[i] = true;
            } else {
                widths.add(r.width()[i]);
            }
        }
        widths.sort(null);
        int w = widths.isEmpty() ? r.width()[0] : widths.get(widths.size() / 2);
        RoadStyle st = drawStyle.copy();
        fitWidth(st, w, r.pavement());
        Node prev = null;
        int roads = 0, joined = 0;
        for (int i = 0; i < n; i++) {
            if (!keep[i]) {
                continue;
            }
            boolean end = i == 0 || i == n - 1 || r.junction()[i];
            Node node = end ? nodeNear(r.x()[i], r.z()[i], 6) : null;
            if (node != null) {
                joined++;
            } else {
                node = plan.addNode(Math.floor(r.x()[i]), Math.floor(r.z()[i]));
                node.open = i == 0 && r.openStart() || i == n - 1 && r.openEnd();    // only followed this far: build nothing across it
            }
            if (prev != null && prev.id != node.id) {
                plan.addSegment(prev.id, node.id, st);
                roads++;
            }
            plan.touch(node.id);
            prev = node;
        }
        changed();
        flash("Followed " + roads + " road" + (roads == 1 ? "" : "s") + ", " + w + " wide" + (r.pavement() > 0 ? ", pavement " + r.pavement() : "")
                + (joined > 0 ? ", joined to " + joined + " point" + (joined == 1 ? "" : "s") : ""));
    }

    /** Lanes to fill an old road's width (its surface is the carriageway), and its pavement. */
    static void fitWidth(RoadStyle st, int w, int pavement) {
        st.dual = false;
        if (w <= 5) {
            st.oneWay = true;
            st.lanes = 1;
            st.laneWidth = Math.max(3, w);
        } else if (w >= 19 && !st.oneWay) {
            st.lanes = 2;
            st.laneWidth = Math.max(3, Math.min(7, (w - 3) / 4));
        } else {
            st.lanes = 1;
            st.laneWidth = Math.max(3, Math.min(7, st.oneWay ? w : (w - 1) / 2));
        }
        if (pavement > 0) {
            st.pavement = Math.max(2, Math.min(8, pavement));         // the kerb comes out of the old pavement, so it fits where it was
        }
    }

    private Node nodeNear(double x, double z, double within) {
        Node best = null;
        double bd = within;
        for (Node n : plan.nodes) {
            double d = Math.hypot(n.x + 0.5 - x, n.z + 0.5 - z);
            if (d <= bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }

    /** Put a new point on a road where it was clicked, splitting it in two. */
    private Node split(int segId, int sample) {
        Segment s = plan.segment(segId);
        Curve c = lines.curves.get(segId);
        if (s == null || c == null) {
            return null;
        }
        Node n = plan.addNode(Math.floor(c.x[sample]), Math.floor(c.z[sample]));
        Segment second = plan.addSegment(n.id, s.b, s.style);
        s.b = n.id;
        s.bend /= 2;
        second.bend = s.bend;
        s.built = false;
        plan.touch(n.id);
        return n;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (Math.hypot(mx - pressX, my - pressY) > 3) {
            moved = true;
        }
        if (panning) {
            camX -= (mx - lastMouseX) / zoom;
            camZ -= (my - lastMouseY) / zoom;
        } else if (moveDragging && moveBox != null) {
            int lim = com.ptmuk.building.Sections.MAX_SIDE - 1;
            int wx = (int) Math.floor(worldX(mx)), wz = (int) Math.floor(worldZ(my));
            moveBox[2] = Math.max(moveBox[0] - lim, Math.min(moveBox[0] + lim, wx));
            moveBox[3] = Math.max(moveBox[1] - lim, Math.min(moveBox[1] + lim, wz));
        } else if (swapDragging && swapBox != null) {
            int lim = BlockSwap.MAX_SIZE - 1;
            int wx = (int) Math.floor(worldX(mx)), wz = (int) Math.floor(worldZ(my));
            swapBox[2] = Math.max(swapBox[0] - lim, Math.min(swapBox[0] + lim, wx));
            swapBox[3] = Math.max(swapBox[1] - lim, Math.min(swapBox[1] + lim, wz));
        } else if (siteDrag == 1) {
            dragX1 = (int) Math.floor(worldX(mx));
            dragZ1 = (int) Math.floor(worldZ(my));
        } else if (siteDrag == 4) {
            int wx = (int) Math.round(worldX(mx)), wz = (int) Math.round(worldZ(my));
            int[] last = lasso.get(lasso.size() - 1);
            if (Math.hypot(wx - last[0], wz - last[1]) >= Math.max(2, 6 / zoom)) {     // a corner every few pixels
                lasso.add(new int[]{wx, wz});
                if (lasso.size() > RoadPlan.MAX_OUTLINE) {
                    for (int i = lasso.size() - 2; i > 0; i -= 2) {
                        lasso.remove(i);                    // too many corners: drop every other one, keeping both ends
                    }
                }
            }
        } else if (siteDrag >= 2 && selSite != null && plan.site(selSite) != null) {
            Site st = plan.site(selSite);
            if (siteDrag == 2) {
                int ox = (int) Math.round(worldX(mx) - grabX), oz = (int) Math.round(worldZ(my) - grabZ);
                st.shift(siteStart[0] + ox - st.x0, siteStart[1] + oz - st.z0);
            } else {
                int wx = (int) Math.round(worldX(mx)), wz = (int) Math.round(worldZ(my));
                if ((siteCorner & 1) != 0) {
                    st.x1 = Math.max(st.x0 + MIN_SITE - 1, wx - 1);
                } else {
                    st.x0 = Math.min(st.x1 - MIN_SITE + 1, wx);
                }
                if ((siteCorner & 2) != 0) {
                    st.z1 = Math.max(st.z0 + MIN_SITE - 1, wz - 1);
                } else {
                    st.z0 = Math.min(st.z1 - MIN_SITE + 1, wz);
                }
            }
        } else if (draggingNode && selNode != null) {
            Node n = plan.node(selNode);
            if (n != null) {
                n.x = Math.floor(worldX(mx));
                n.z = Math.floor(worldZ(my));
                lines = Layout.lines(plan);
            }
        } else if (draggingBend && selSeg != null) {
            Segment s = plan.segment(selSeg);
            Node a = s == null ? null : plan.node(s.a), b = s == null ? null : plan.node(s.b);
            if (a != null && b != null) {
                double ax = a.x + 0.5, az = a.z + 0.5, bx = b.x + 0.5, bz = b.z + 0.5;
                double len = Math.hypot(bx - ax, bz - az);
                double lx = (bz - az) / len, lz = -(bx - ax) / len;
                double wx = worldX(mx) - (ax + bx) / 2, wz = worldZ(my) - (az + bz) / 2;
                s.bend = Math.round(wx * lx + wz * lz);
                lines = Layout.lines(plan);
            }
        }
        lastMouseX = mx;
        lastMouseY = my;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (panning) {
            panning = false;
            if (!moved && button == 1 && mode == Mode.DRAW) {
                chainLast = null;                      // right-click finishes the road
                flash("Road finished");
            }
            return true;
        }
        if (moveDragging) {
            moveDragging = false;
            rebuildPanel();
            return true;
        }
        if (swapDragging) {
            swapDragging = false;
            swapRows.clear();
            swapFirst = 0;
            swapSend(BlockSwapMessage.Action.SCAN);
            rebuildPanel();
            return true;
        }
        if (siteDrag != 0) {
            if (siteDrag == 4) {
                int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
                for (int[] p : lasso) {
                    x0 = Math.min(x0, p[0]);
                    z0 = Math.min(z0, p[1]);
                    x1 = Math.max(x1, p[0]);
                    z1 = Math.max(z1, p[1]);
                }
                if (lasso.size() >= 3 && x1 - x0 >= MIN_SITE && z1 - z0 >= MIN_SITE) {
                    remember();
                    Site st = plan.addSite(newSiteType, x0, z0, x1 - 1, z1 - 1);   // the corners are block edges
                    st.district = newDistrict;
                    st.outline = new int[lasso.size() * 2];
                    for (int i = 0; i < lasso.size(); i++) {
                        st.outline[2 * i] = lasso.get(i)[0];
                        st.outline[2 * i + 1] = lasso.get(i)[1];
                    }
                    selSite = st.id;
                    changed();
                } else if (moved) {
                    flash("Draw right round where the " + (newSiteType == SiteType.DISTRICT ? "district" : "town") + " goes");
                }
                lasso.clear();
            } else if (siteDrag == 1) {
                int w = Math.abs(dragX1 - dragX0) + 1, d = Math.abs(dragZ1 - dragZ0) + 1;
                if (w >= MIN_SITE && d >= MIN_SITE) {
                    remember();
                    Site st = plan.addSite(newSiteType, dragX0, dragZ0, dragX1, dragZ1);
                    st.district = newDistrict;
                    siteDefaults(st);
                    selSite = st.id;
                    changed();
                } else if (moved) {
                    flash("Make a site at least " + MIN_SITE + " x " + MIN_SITE + " blocks");
                }
            } else if (moved) {
                Site st = selSite == null ? null : plan.site(selSite);
                if (st != null) {
                    st.built = false;
                }
                changed();
            } else if (!history.isEmpty()) {
                history.remove(history.size() - 1);
            }
            siteDrag = 0;
            return true;
        }
        if (draggingNode || draggingBend) {
            if (moved) {
                if (draggingNode && selNode != null) {
                    plan.touch(selNode);
                } else if (selSeg != null) {
                    Segment s = plan.segment(selSeg);
                    if (s != null) {
                        s.built = false;
                        plan.touch(s.a);
                        plan.touch(s.b);
                    }
                }
                changed();
            } else if (!history.isEmpty()) {
                history.remove(history.size() - 1);
            }
            draggingNode = draggingBend = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!overMap(mx, my)) {
            return super.mouseScrolled(mx, my, delta);
        }
        double wx = worldX(mx), wz = worldZ(my);
        zoom = Math.max(0.25, Math.min(12, zoom * (delta > 0 ? 1.25 : 0.8)));
        camX = wx - (mx - (mapLeft() + (width - mapLeft()) / 2.0)) / zoom;
        camZ = wz - (my - (TOP + (height - TOP) / 2.0)) / zoom;
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        mouseX = mx;
        mouseY = my;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (stopName != null && stopName.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                stopName.setFocused(false);
                setFocused(null);
                return true;
            }
            stopName.keyPressed(key, scan, mods);
            return true;
        }
        if (moveName != null && moveName.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                moveName.setFocused(false);
                setFocused(null);
                return true;
            }
            moveName.keyPressed(key, scan, mods);
            return true;                               // typing a name, not moving the map
        }
        if (swapEdit != null && swapEdit.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                swapEdit.setFocused(false);
                setFocused(null);
                rebuildPanel();
                return true;
            }
            swapEdit.keyPressed(key, scan, mods);
            return true;                               // typing a block name, not moving the map
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && chainLast != null) {
            chainLast = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER) {
            chainLast = null;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE)
                && (selNode != null || selSeg != null || (mode == Mode.SITES && selSite != null))) {
            deleteSelection();
            return true;
        }
        if (key == GLFW.GLFW_KEY_Z && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            undoEdit();
            return true;
        }
        double step = 24 / zoom;
        switch (key) {
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> camZ -= step;
            case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> camZ += step;
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> camX -= step;
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> camX += step;
            case GLFW.GLFW_KEY_E -> setMode(mode == Mode.DRAW ? Mode.EDIT : Mode.DRAW);
            case GLFW.GLFW_KEY_Q -> setMode(mode == Mode.SITES ? Mode.DRAW : Mode.SITES);
            case GLFW.GLFW_KEY_M -> setMode(mode == Mode.MAP ? Mode.DRAW : Mode.MAP);
            case GLFW.GLFW_KEY_R -> {
                if (mode != Mode.MOVE || movePicked == null) {
                    return super.keyPressed(key, scan, mods);
                }
                moveTurns = (moveTurns + 1) % 4;
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the preview

    /** How far the map reaches, in chunks each way from where it was opened. */
    private static final int MAP_RADIUS_CHUNKS = 24;
    private int tilesSinceLevel;

    /** A chunk of map from the server (see RoadMapTiles). */
    public static void mapTile(com.ptmuk.network.RoadMapTileMessage msg) {
        if (Minecraft.getInstance().screen instanceof RoadPlannerScreen s && s.map != null
                && s.map.accept(msg.cx(), msg.cz(), msg.colours(), msg.ground())) {
            s.tilesSinceLevel++;
        }
    }

    @Override
    public void tick() {
        if (map != null && !map.done()) {
            map.step(24);
            if (map.done()) {
                previewStale = true;                    // the ground is all known now
            }
        }
        if (map != null) {
            map.flush();
            if (tilesSinceLevel > 0 && pending == null && preview != null && tilesSinceLevel >= 64) {
                previewStale = true;                    // new ground came in under the roads: level them again
            }
        }
        if (messageTicks > 0) {
            messageTicks--;
        }
        if (mapJob != null && mapJob.isDone()) {
            try {
                OsmMap m = mapJob.join();
                osm = m;
                mapThen.accept(m);
            } catch (RuntimeException e) {
                Throwable why = e.getCause() == null ? e : e.getCause();
                flash("Couldn't read the map: " + why.getMessage());
            }
            mapJob = null;
            rebuildPanel();
        }
        if (pending != null && pending.isDone()) {
            try {
                preview = pending.get();
                uploadPreview(preview);
            } catch (Exception e) {
                PtmUk.LOGGER.warn("Road preview failed", e);
            }
            pending = null;
        }
        if (previewStale && pending == null && !draggingNode && !draggingBend && siteDrag == 0 && map != null) {
            previewStale = false;
            tilesSinceLevel = 0;
            // a big plan: just the part round the map, as the server builds it
            RoadPlan copy = plan.tooBig() ? plan.part(map.originX + map.size / 2.0, map.originZ + map.size / 2.0, RoadPlan.PART) : plan.copy();
            var terrain = map.terrain();
            pending = CompletableFuture.supplyAsync(() -> Layout.of(copy, terrain));
        }
    }

    private static int abgr(int rgb, int alpha) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return (alpha << 24) | (b << 16) | (g << 8) | r;
    }

    private static int kindColour(Layout l, int i) {
        return switch (l.kind[i]) {
            case Layout.ROAD -> abgr(l.top[i] % 1 != 0 ? 0x44464B : 0x3A3C40, 255);
            case Layout.PAINT, Layout.DOME -> abgr(0xF0F0F0, 255);
            case Layout.KERB, Layout.ISLAND_KERB -> abgr(0xB8B8B2, 255);
            case Layout.FOOTWAY -> abgr(0xA8A8A6, 255);
            case Layout.TACTILE -> abgr(0xB0453A, 255);
            case Layout.VERGE, Layout.ISLAND, Layout.RESERVATION -> abgr(0x5E9C48, 255);
            case Layout.BARRIER -> abgr(0xD0D0D8, 255);
            case Layout.BLEND -> abgr(0x8A7650, 70);
            case Layout.PLOT -> abgr(0x8E9196, 255);
            default -> 0;
        };
    }

    private void uploadPreview(Layout l) {
        if (previewTex == null || previewW != l.w || previewH != l.h) {
            if (previewTex != null) {
                Minecraft.getInstance().getTextureManager().release(PREVIEW);
            }
            previewTex = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, l.w, l.h, true));
            Minecraft.getInstance().getTextureManager().register(PREVIEW, previewTex);
            previewW = l.w;
            previewH = l.h;
        }
        previewX0 = l.x0;
        previewZ0 = l.z0;
        NativeImage img = previewTex.getPixels();
        if (img == null) {
            return;
        }
        for (int i = 0; i < l.w * l.h; i++) {
            int x = i % l.w, z = i / l.w;
            int c = kindColour(l, i);
            int m = l.mark[i];
            if (m != Layout.M_NONE && l.kind[i] == Layout.ROAD) {
                c = (m & Layout.YELLOW) != 0 ? abgr(0xF2C230, 255) : abgr(0xFFFFFF, 255);
            }
            if (l.parapet.get(i)) {
                c = abgr(0xE8E8F0, 255);                     // a bridge's parapet
            } else if (l.under != null && (l.kind[i] == Layout.NONE || l.kind[i] == Layout.BLEND)) {
                int u = l.under.index(l.cellX(i), l.cellZ(i));
                if (u >= 0 && l.under.kind[u] != Layout.NONE) {
                    c = kindColour(l.under, u);              // the road underneath, seen where nothing's over it
                    if (l.under.mark[u] != Layout.M_NONE && l.under.kind[u] == Layout.ROAD) {
                        c = (l.under.mark[u] & Layout.YELLOW) != 0 ? abgr(0xF2C230, 255) : abgr(0xFFFFFF, 255);
                    }
                }
            }
            if (!l.dirty[i] && l.kind[i] != Layout.NONE && l.kind[i] != Layout.BLEND) {
                // already built: shown a little faded so new work stands out
                c = (c & 0x00FFFFFF) | (200 << 24);
            }
            img.setPixelRGBA(x, z, c);
        }
        for (Layout.Fixture f : l.fixtures) {
            int x = f.x() - l.x0, z = f.z() - l.z0;
            if (x >= 0 && z >= 0 && x < l.w && z < l.h) {
                int c = switch (f.type()) {
                    case TREE -> 0x2E6B2A;
                    case DISABLED_BAY, PARENT_BAY -> 0x4A8CE0;
                    case EV_BAY, EV_CHARGER -> 0x3CC060;
                    case STREET_LIGHT, SIGNAL -> 0xFFE45C;
                    default -> 0xD8D8D8;
                };
                img.setPixelRGBA(x, z, abgr(c, 255));
            }
        }
        previewTex.upload();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xFF101010);
        g.enableScissor(mapLeft(), TOP, width, height);
        PoseStack ps = g.pose();
        if (map != null) {
            ps.pushPose();
            ps.translate(screenX(map.originX), screenY(map.originZ), 0);
            ps.scale((float) zoom, (float) zoom, 1);
            g.blit(PlannerMap.TEXTURE, 0, 0, 0, 0, map.size, map.size, map.size, map.size);
            ps.popPose();
        }
        if (previewTex != null) {
            RenderSystem.enableBlend();
            ps.pushPose();
            ps.translate(screenX(previewX0), screenY(previewZ0), 0);
            ps.scale((float) zoom, (float) zoom, 1);
            g.blit(PREVIEW, 0, 0, 0, 0, previewW, previewH, previewW, previewH);
            ps.popPose();
            RenderSystem.disableBlend();
        }
        if (outlines != null && outlineTex != null && map != null) {
            RenderSystem.enableBlend();
            ps.pushPose();
            ps.translate(screenX(map.originX), screenY(map.originZ), 0);
            ps.scale((float) zoom, (float) zoom, 1);
            g.blit(OUTLINES, 0, 0, 0, 0, map.size, map.size, map.size, map.size);
            ps.popPose();
            RenderSystem.disableBlend();
        }
        drawPlan(g);
        drawMeasure(g);
        drawSwapBox(g);
        drawMoveBox(g);
        drawStops(g);
        drawPlayer(g);
        g.disableScissor();
        // panel and bars
        g.fill(0, 0, PANEL, height, 0xF0202226);
        g.fill(PANEL, 0, width, TOP, 0xF0202226);
        Node n = selNode == null ? null : plan.node(selNode);
        Segment s = selSeg == null ? null : plan.segment(selSeg);
        Site site = selSite == null ? null : plan.site(selSite);
        String heading = mode == Mode.MOVE ? (movePicked != null ? "Paste " + movePicked
                : moveBox == null ? "Copy and paste sections"
                : "Copy " + (Math.abs(moveBox[2] - moveBox[0]) + 1) + " x " + (Math.abs(moveBox[3] - moveBox[1]) + 1))
                : mode == Mode.SWAP ? (swapBox == null ? "Block swap"
                : "Block swap (" + (Math.abs(swapBox[2] - swapBox[0]) + 1) + " x " + (Math.abs(swapBox[3] - swapBox[1]) + 1) + ")")
                : mode == Mode.MAP ? (plan.map == null ? "Real maps (.osm)" : "Map: " + plan.map.file)
                : mode == Mode.SITES ? (site == null ? "Sites" : site.type.title + " (" + site.width() + " x " + site.depth() + ")")
                : n != null && mode == Mode.EDIT ? "Point " + describe(n)
                : s != null ? "Selected road (" + s.style.totalWidth() + " wide)"
                : "New roads (" + drawStyle.totalWidth() + " wide)";
        g.drawString(font, "Road Planner", 6, 6, 0xFFFFD24A);
        g.drawString(font, heading, 6, TOP + 3, 0xFFE0E0E0);
        String help = mode == Mode.MOVE
                ? (movePicked != null ? "Click where its first corner (the red dot) goes, R to turn, then Paste it here. Click it again in the list to stop."
                : "Drag a box to copy (the red dot's corner is where it's pasted from), name it, Copy. Then click a section to paste it.")
                : mode == Mode.SWAP
                ? "Drag a box over an old build. Click an arrow to pick what a block becomes, or type a block's name."
                : mode == Mode.MAP
                ? (plan.map == null ? "Move the map to where its middle goes, then pick a file." : "Click two points to measure.")
                : mode == Mode.SITES
                ? (selSite == null && newSiteType == SiteType.TOWN && drawTown ? "Hold the left button and draw round where the town goes."
                : selSite == null && newSiteType == SiteType.DISTRICT ? "Mark out part of a town and pick what goes there. Districts drawn side by side make one town; Build lays them out."
                : "Drag out a site; drag it to move, a corner to resize.")
                : mode == Mode.DRAW && tracing
                ? (tracePiece ? "Click an old road where a new road will join it: it follows " + PIECE + " blocks each way, ready for the junction."
                : "Click old roads to follow them to their junctions and ends. Click the button again to stop.")
                : mode == Mode.DRAW
                ? (chainLast == null ? "Click to start a road. Right-drag: move map. Scroll: zoom."
                : "Click: next point. End on a road to join it (Shift: slip road). Right-click: finish.")
                : "Drag a point, or a road's middle dot to bend it. Del: delete. Ctrl+Z: undo.";
        g.fill(PANEL, height - 12, width, height, 0xC0000000);
        g.drawString(font, help, PANEL + 4, height - 10, 0xFFB0B0B0);
        if (overMap(mx, my)) {
            int wx = (int) Math.floor(worldX(mx)), wz = (int) Math.floor(worldZ(my));
            String pos = "x " + wx + "  z " + wz + (map != null ? "  ground " + map.groundAt(wx, wz) : "");
            g.drawString(font, pos, width - font.width(pos) - 4, height - 24, 0xFFE0E0E0);
        }
        if (map != null && !map.done()) {
            g.drawString(font, "Mapping the area...", PANEL + 6, TOP + 6, 0xFFFFD24A);
        }
        if (pending != null) {
            g.drawString(font, "Working out the layout...", PANEL + 6, TOP + 16, 0xFFB0D0FF);
        }
        if (messageTicks > 0) {
            g.drawCenteredString(font, message, (PANEL + width) / 2, TOP + 30, 0xFFFFFFFF);
        }
        if (mode == Mode.SITES) {
            sitePanelText(g, site);
        }
        if (mode == Mode.SWAP) {
            swapPanelText(g);
        }
        if (mode == Mode.MAP && plan.map == null && mapFiles != null && mapFiles.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(
                    "Export an area from openstreetmap.org (Export button) and put the .osm file in the game folder's osm_maps folder."),
                    PANEL - 10);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), 6, TOP + 30 + ROW + i * 10, 0xFFB0B0B0);
            }
        }
        if (mapJob != null) {
            g.drawString(font, "Reading the map...", PANEL + 6, TOP + 26, 0xFFFFD24A);
        }
        super.render(g, mx, my, partial);
    }

    private String describe(Node n) {
        int arms = plan.segmentsAt(n.id).size();
        Junction j = preview != null ? preview.junctions.get(n.id) : lines.junctions.get(n.id);
        String what = j == null ? (arms <= 1 ? "road end" : "bend") : j.title.toLowerCase();
        return "(" + what + ", " + arms + " road" + (arms == 1 ? "" : "s") + ")";
    }

    private void line(GuiGraphics g, double x0, double y0, double x1, double y1, float thick, int colour) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 0.5) {
            return;
        }
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x0, y0, 0);
        ps.mulPose(Axis.ZP.rotation((float) Math.atan2(y1 - y0, x1 - x0)));
        int h = Math.max(1, Math.round(thick));
        g.fill(0, -h / 2, (int) Math.ceil(len), -h / 2 + h, colour);
        ps.popPose();
    }

    private void dot(GuiGraphics g, double x, double y, int r, int colour) {
        g.fill((int) x - r, (int) y - r, (int) x + r + 1, (int) y + r + 1, colour);
    }

    private void rect(GuiGraphics g, double x0, double z0, double x1, double z1, int t, int colour) {
        int a = (int) screenX(x0), b = (int) screenY(z0), c = (int) screenX(x1), d = (int) screenY(z1);
        g.fill(a, b, c, b + t, colour);
        g.fill(a, d - t, c, d, colour);
        g.fill(a, b, a + t, d, colour);
        g.fill(c - t, b, c, d, colour);
    }

    /** The site's name in its corner, when selected or when it fits inside (a town's plots would bury the map in labels). */
    private void siteLabel(GuiGraphics g, Site st, boolean sel) {
        String title = st.type == SiteType.DISTRICT ? st.district.title : st.type.title;
        if (sel || zoom >= 1 && font.width(title) + 6 <= screenX(st.x1 + 1) - screenX(st.x0)) {
            g.drawString(font, title, (int) screenX(st.x0) + 3, (int) screenY(st.z0) + 3, sel ? 0xFFFFD24A : 0xFFE0E0E0);
        }
    }

    /** A district's tint on the map: what it is at a glance. */
    private static final int[] DISTRICT_TINT = {0x50E050E0, 0x5050C0E0, 0x50E0C040, 0x504080FF, 0x5040D040, 0x50FF8030, 0x50A0A0A0};

    /** Site outlines, the road side picked out, handles on the selected one, and one being marked out. */
    private void drawSites(GuiGraphics g) {
        for (Site st : plan.sites) {
            boolean sel = mode == Mode.SITES && selSite != null && selSite == st.id;
            SiteLayout sl = siteLayout(st.id);
            if (sl == null || pending != null || siteDrag >= 2) {
                if (st.outline != null) {
                    fillPolygon(g, st.outline, 0x40707478);
                } else {
                    g.fill((int) screenX(st.x0), (int) screenY(st.z0), (int) screenX(st.x1 + 1), (int) screenY(st.z1 + 1), 0x40707478);
                }
            }
            if (st.type == SiteType.KEEP_CLEAR) {
                g.fill((int) screenX(st.x0), (int) screenY(st.z0), (int) screenX(st.x1 + 1), (int) screenY(st.z1 + 1), 0x50E04040);
            }
            if (st.type == SiteType.DISTRICT) {
                int tint = DISTRICT_TINT[st.district.ordinal() % DISTRICT_TINT.length];
                if (st.outline != null) {
                    fillPolygon(g, st.outline, tint);
                } else {
                    g.fill((int) screenX(st.x0), (int) screenY(st.z0), (int) screenX(st.x1 + 1), (int) screenY(st.z1 + 1), tint);
                }
            }
            if (st.outline != null) {
                polygon(g, st.outline, sel ? 2 : 1, sel ? 0xFFFFD24A : 0xA0FFFFFF);
            } else {
                rect(g, st.x0, st.z0, st.x1 + 1, st.z1 + 1, sel ? 2 : 1, sel ? 0xFFFFD24A : st.type == SiteType.KEEP_CLEAR ? 0xC0FF6060 : 0xA0FFFFFF);
            }
            if (!st.type.ground()) {
                siteLabel(g, st, sel);
                continue;
            }
            int front = sl != null ? sl.frame.front() : Math.max(0, st.front);
            double ax = front == 3 ? st.x1 + 1 : st.x0, bx = front == 1 ? st.x0 : st.x1 + 1;
            double az = front == 0 ? st.z1 + 1 : st.z0, bz = front == 2 ? st.z0 : st.z1 + 1;
            g.fill((int) screenX(ax) - 1, (int) screenY(az) - 1, (int) screenX(bx) + 1, (int) screenY(bz) + 1, 0xFF5AA0FF);
            if (sel) {
                for (int k = 0; k < 4; k++) {
                    double cx = screenX((k & 1) != 0 ? st.x1 + 1 : st.x0), cy = screenY((k & 2) != 0 ? st.z1 + 1 : st.z0);
                    dot(g, cx, cy, 4, 0xFF000000);
                    dot(g, cx, cy, 3, 0xFFFFD24A);
                }
            }
            siteLabel(g, st, sel);
        }
        if (siteDrag == 1) {
            int x0 = Math.min(dragX0, dragX1), x1 = Math.max(dragX0, dragX1) + 1, z0 = Math.min(dragZ0, dragZ1), z1 = Math.max(dragZ0, dragZ1) + 1;
            boolean ok = x1 - x0 >= MIN_SITE && z1 - z0 >= MIN_SITE;
            g.fill((int) screenX(x0), (int) screenY(z0), (int) screenX(x1), (int) screenY(z1), ok ? 0x40FFD24A : 0x40FF5050);
            rect(g, x0, z0, x1, z1, 1, ok ? 0xFFFFD24A : 0xFFFF5050);
            g.drawString(font, newSiteType.title + "  " + (x1 - x0) + " x " + (z1 - z0), (int) mouseX + 8, (int) mouseY - 4, 0xFFFFD24A);
        }
        if (siteDrag == 4 && !lasso.isEmpty()) {
            int[] pts = new int[lasso.size() * 2];
            for (int i = 0; i < lasso.size(); i++) {
                pts[2 * i] = lasso.get(i)[0];
                pts[2 * i + 1] = lasso.get(i)[1];
            }
            if (pts.length >= 6) {
                fillPolygon(g, pts, 0x40FFD24A);
            }
            polygon(g, pts, 1, 0xFFFFD24A);
            g.drawString(font, "Draw round the town, then let go", (int) mouseX + 8, (int) mouseY - 4, 0xFFFFD24A);
        }
    }

    /** A drawn outline's edges, back round to the start. */
    private void polygon(GuiGraphics g, int[] pts, float thick, int colour) {
        for (int i = 0, j = pts.length - 2; i < pts.length; j = i, i += 2) {
            line(g, screenX(pts[j]), screenY(pts[j + 1]), screenX(pts[i]), screenY(pts[i + 1]), thick, colour);
        }
    }

    /** A drawn outline filled in, a row of the screen at a time. */
    private void fillPolygon(GuiGraphics g, int[] pts, int colour) {
        double top = Double.MAX_VALUE, bottom = -Double.MAX_VALUE;
        for (int i = 1; i < pts.length; i += 2) {
            top = Math.min(top, screenY(pts[i]));
            bottom = Math.max(bottom, screenY(pts[i]));
        }
        double[] xs = new double[pts.length / 2];
        for (int y = Math.max(TOP, (int) top); y < Math.min(height, Math.ceil(bottom)); y++) {
            double yc = y + 0.5;
            int k = 0;
            for (int i = 0, j = pts.length - 2; i < pts.length; j = i, i += 2) {
                double yi = screenY(pts[i + 1]), yj = screenY(pts[j + 1]);
                if ((yi > yc) != (yj > yc)) {
                    double xi = screenX(pts[i]), xj = screenX(pts[j]);
                    xs[k++] = xi + (yc - yi) * (xj - xi) / (yj - yi);
                }
            }
            java.util.Arrays.sort(xs, 0, k);
            for (int p = 0; p + 1 < k; p += 2) {
                g.fill((int) Math.round(xs[p]), y, (int) Math.round(xs[p + 1]), y + 1, colour);
            }
        }
    }

    /** The swap box on the map. */
    private void drawSwapBox(GuiGraphics g) {
        if (mode != Mode.SWAP || swapBox == null) {
            return;
        }
        int x0 = Math.min(swapBox[0], swapBox[2]), x1 = Math.max(swapBox[0], swapBox[2]) + 1;
        int z0 = Math.min(swapBox[1], swapBox[3]), z1 = Math.max(swapBox[1], swapBox[3]) + 1;
        g.fill((int) screenX(x0), (int) screenY(z0), (int) screenX(x1), (int) screenY(z1), 0x3050E0FF);
        rect(g, x0, z0, x1, z1, 1, 0xFF50E0FF);
        if (swapDragging) {
            g.drawString(font, (x1 - x0) + " x " + (z1 - z0), (int) mouseX + 8, (int) mouseY - 4, 0xFF50E0FF);
        }
    }

    /** Each block's name and count over its button, the note from the server, and the typing box's label. */
    private void swapPanelText(GuiGraphics g) {
        if (swapBox == null) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(
                    "Drag a box over part of an old build to see what it's made of. Open ground is roads, pavements and "
                            + "anything thin on them (carpet markings, post bases); everything takes in the buildings too."), PANEL - 10);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), 6, swapListTop + i * 10, 0xFFB0B0B0);
            }
            return;
        }
        g.drawString(font, swapNote, PANEL + 6, TOP + 6, 0xFF50E0FF);
        for (int k = 0; k < swapShown; k++) {
            SwapRow r = swapRows.get(swapFirst + k);
            String name = font.plainSubstrByWidth(blockName(r.id), PANEL - 50);
            int y = swapListTop + k * SWAP_ENTRY;
            g.drawString(font, name, 6, y, swapFirst + k == swapSel ? 0xFFFFD24A : 0xFFE0E0E0);
            String n = String.valueOf(r.count);
            g.drawString(font, n, PANEL - 6 - font.width(n), y, 0xFF909090);
        }
        if (swapEdit != null) {
            g.drawString(font, "or type a block:", 6, swapEdit.getY() - 10, 0xFFB0B0B0);
        }
    }

    /** The measuring line, in blocks and in real metres at the map's scale. */
    private void drawMeasure(GuiGraphics g) {
        if (mode != Mode.MAP || measureA == null) {
            return;
        }
        double bx = measureB != null ? measureB[0] : worldX(mouseX), bz = measureB != null ? measureB[1] : worldZ(mouseY);
        line(g, screenX(measureA[0]), screenY(measureA[1]), screenX(bx), screenY(bz), 1, 0xFF50E0FF);
        dot(g, screenX(measureA[0]), screenY(measureA[1]), 2, 0xFF50E0FF);
        double blocks = Math.hypot(bx - measureA[0], bz - measureA[1]);
        double scale = plan.map != null ? plan.map.scale : RoadPlan.MapImport.SCALE;
        String text = String.format(java.util.Locale.ROOT, "%.0f blocks = %.1f m", blocks, blocks / scale);
        g.drawString(font, text, (int) screenX(bx) + 6, (int) screenY(bz) - 4, 0xFF50E0FF);
    }

    /** Along the bottom of the map: how many bays the selected site got, and any warning. */
    private void sitePanelText(GuiGraphics g, Site st) {
        SiteLayout sl = st == null ? null : siteLayout(st.id);
        if (st != null && st.type == SiteType.DISTRICT) {
            g.drawString(font, DISTRICT_HELP[st.district.ordinal()], PANEL + 4, height - 24, 0xFFE0E0E0);
            return;
        }
        if (st != null && (st.type == SiteType.KEEP_CLEAR || st.type == SiteType.GREEN)) {
            g.drawString(font, st.type == SiteType.GREEN ? "Grass filling whatever the roads and sites leave, level with them"
                    : "Left as it is: a town lays no buildings here and the ground isn't touched", PANEL + 4, height - 24, 0xFFE0E0E0);
            return;
        }
        if (sl == null) {
            g.drawString(font, "End a road on a site's edge to make a way in.", PANEL + 4, height - 24, 0xFFB0B0B0);
            return;
        }
        int shops = countShops(sl);
        boolean row = st.type == SiteType.PARADE || SiteLayout.units(st.type) != SiteLayout.PARADE_UNITS;
        String what = st.type == SiteType.TOWN ? (st.outline != null ? "A town, laid out to fill the shape" : "A town, laid out to fit")
                : st.type == SiteType.PARADE ? shops + " shop" + (shops == 1 ? "" : "s") + paradeNames(sl)
                : row ? shops + " building" + (shops == 1 ? "" : "s") + paradeNames(sl)
                : sl.bayCount() + " parking bays" + (shops == 0 ? "" : ", " + shops + " building" + (shops == 1 ? "" : "s"));
        g.drawString(font, font.plainSubstrByWidth(what, width - PANEL - 150), PANEL + 4, height - 24, 0xFFE0E0E0);   // clear of the x/z readout
        if (sl.warning != null) {
            g.drawString(font, sl.warning, PANEL + 4, height - 34, 0xFFFF9050);
        }
    }

    private static final String[] DISTRICT_HELP = {
            "High street: shops along all its streets, a church, pub and surgery at its edges",
            "Houses: only houses along its streets (pick which)",
            "Main road: the town's main road runs through here",
            "Shopping: a retail park and its car park, with a road in",
            "Park: grass and trees, no streets or buildings",
            "Motorway junction: a dual carriageway across it with a roundabout the town joins",
            "Bypass: a dual carriageway along it, out of town both ends, roundabouts for roads into town"};

    private static String paradeNames(SiteLayout sl) {
        StringBuilder s = new StringBuilder(":");
        for (SiteLayout.Building b : sl.buildings) {
            com.ptmuk.building.PrefabCatalog.Entry e = com.ptmuk.building.PrefabCatalog.get(SiteLayout.UNITS.get(b.brand()));
            s.append(s.length() > 1 ? ", " : " ").append(e == null ? "?" : e.name().toLowerCase());
        }
        return s.toString();
    }

    private static int countShops(SiteLayout sl) {
        int n = 0;
        for (SiteLayout.Building b : sl.buildings) {
            if (!b.kind().equals("canopy")) {
                n++;
            }
        }
        return n;
    }

    private void drawPlan(GuiGraphics g) {
        drawSites(g);
        boolean showLines = preview == null || pending != null || draggingNode || draggingBend || zoom < 1.5;
        for (Segment s : plan.segments) {
            Curve c = lines.curves.get(s.id);
            if (c == null) {
                continue;
            }
            boolean sel = selSeg != null && selSeg == s.id;
            if (showLines || sel) {
                int colour = sel ? 0xFFFFD24A : 0xA0FFFFFF;
                float w = sel ? 2 : 1;
                if (preview == null || draggingNode || draggingBend) {
                    w = (float) Math.max(1, s.style.halfCarriageway() * 2 * zoom);
                    colour = sel ? 0xC0FFD24A : 0x90505458;
                }
                for (int i = 0; i + 4 < c.n; i += 4) {
                    line(g, screenX(c.x[i]), screenY(c.z[i]), screenX(c.x[i + 4]), screenY(c.z[i + 4]), w, colour);
                }
                line(g, screenX(c.x[c.n - 1 - (c.n - 1) % 4]), screenY(c.z[c.n - 1 - (c.n - 1) % 4]), screenX(c.x[c.n - 1]),
                        screenY(c.z[c.n - 1]), w, colour);
            }
            if (sel && mode == Mode.EDIT) {
                double[] hnd = bendHandle(s);
                if (hnd != null) {
                    dot(g, hnd[0], hnd[1], 3, 0xFF000000);
                    dot(g, hnd[0], hnd[1], 2, 0xFFFFD24A);
                }
            }
        }
        for (Node n : plan.nodes) {
            double x = screenX(n.x + 0.5), y = screenY(n.z + 0.5);
            Junction j = lines.junctions.get(n.id);
            int colour = j == null ? 0xFFFFFFFF : switch (j) {
                case ROUNDABOUT, MINI_ROUNDABOUT -> 0xFF5AA0FF;
                case SIGNALS -> 0xFF50E050;
                case MERGE -> 0xFF50E0E0;
                case STOP -> 0xFFE05050;
                default -> 0xFFFFA040;
            };
            boolean sel = selNode != null && selNode == n.id;
            if (j == null && !sel && zoom < 1.5 && plan.segmentsAt(n.id).size() == 2) {
                continue;                                  // plain bends crowd the map zoomed out (a real map has hundreds)
            }
            dot(g, x, y, sel ? 4 : 3, 0xFF000000);
            dot(g, x, y, sel ? 3 : 2, sel ? 0xFFFFD24A : colour);
            double r = lines.roundaboutRadius(n.id), e = lines.roundaboutStretch(n.id);
            if (r > 0 && (preview == null || showLines)) {
                // round, or an oval: each half of the circle pushed out to its end of the stretch
                double ax = Math.cos(n.axis) * e, az = Math.sin(n.axis) * e;
                for (int k = 0; k < 48; k++) {
                    double a0 = k * Math.PI * 2 / 48, a1 = (k + 1) * Math.PI * 2 / 48;
                    double s0 = Math.signum(Math.cos(a0 - n.axis)), s1 = Math.signum(Math.cos(a1 - n.axis));
                    line(g, x + (Math.cos(a0) * r + ax * s0) * zoom, y + (Math.sin(a0) * r + az * s0) * zoom,
                            x + (Math.cos(a1) * r + ax * s1) * zoom, y + (Math.sin(a1) * r + az * s1) * zoom, 1, 0xC05AA0FF);
                }
            }
        }
        // the road being drawn follows the mouse
        if (mode == Mode.DRAW && chainLast != null && overMap(mouseX, mouseY)) {
            double w = Math.max(1, drawStyle.halfCarriageway() * 2 * zoom);
            line(g, screenX(chainLast.x + 0.5), screenY(chainLast.z + 0.5), mouseX, mouseY, (float) w, 0x80FFD24A);
            line(g, screenX(chainLast.x + 0.5), screenY(chainLast.z + 0.5), mouseX, mouseY, 1, 0xFFFFD24A);
            double len = Math.hypot(worldX(mouseX) - chainLast.x - 0.5, worldZ(mouseY) - chainLast.z - 0.5);
            g.drawString(font, (int) len + " blocks", (int) mouseX + 8, (int) mouseY - 4, 0xFFFFD24A);
        }
    }

    private void drawPlayer(GuiGraphics g) {
        Player p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        double x = screenX(p.getX()), y = screenY(p.getZ());
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        line(g, x, y, x + fx * 8, y + fz * 8, 2, 0xFFFF4040);
        dot(g, x, y, 3, 0xFF000000);
        dot(g, x, y, 2, 0xFFFF4040);
    }
}
