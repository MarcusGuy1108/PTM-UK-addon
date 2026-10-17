package com.ptmuk.client.road;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ptmuk.PtmUk;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.RoadPlannerMessage;
import com.ptmuk.road.RoadPlannerItem;
import com.ptmuk.road.RoadPlans;
import com.ptmuk.road.layout.Curve;
import com.ptmuk.road.layout.Layout;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadPlan.Junction;
import com.ptmuk.road.plan.RoadPlan.Node;
import com.ptmuk.road.plan.RoadPlan.Segment;
import com.ptmuk.road.plan.RoadStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The Road Planner: a top-down map of the area round you to draw roads on. Click to lay a road
 * point by point (it curves smoothly through them); end on another road to make a junction.
 * Select a point to drag it or change its junction (give way, stop, mini roundabout,
 * roundabout, traffic lights), select a road to bend it or change its lanes, pavements and
 * markings. The map shows exactly what will be built; Build sends it to the server.
 */
public class RoadPlannerScreen extends Screen {
    private static final ResourceLocation PREVIEW = PtmUk.id("road_planner/preview");
    private static final int PANEL = 136;
    private static final int TOP = 22;
    private static final int ROW = 15;
    private static RoadStyle lastStyle = RoadStyle.mainRoad();
    private static double lastZoom = 2;

    private enum Mode { DRAW, EDIT }

    private final InteractionHand hand;
    private RoadPlan plan;
    private final List<String> history = new ArrayList<>();
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
    private boolean draggingNode, draggingBend, panning, moved;
    private double camX, camZ, zoom;
    private double pressX, pressY, lastMouseX, lastMouseY;
    private double mouseX, mouseY;
    private String message = "";
    private int messageTicks;

    private final List<Button> panelButtons = new ArrayList<>();

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
            int radius = Math.min(512, Math.max(96, mc.options.renderDistance().get() * 16 + 16));
            map = new PlannerMap(mc.level, (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getZ()), radius,
                    (int) Math.floor(mc.player.getY()));
        }
        lines = Layout.lines(plan);
        // top bar
        int x = PANEL + 4;
        drawButton = addRenderableWidget(Button.builder(Component.literal("Draw"), b -> setMode(Mode.DRAW)).bounds(x, 3, 36, 16).build());
        editButton = addRenderableWidget(Button.builder(Component.literal("Edit"), b -> setMode(Mode.EDIT)).bounds(x + 38, 3, 36, 16).build());
        int right = width - 3;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(right - 36, 3, 36, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Build"), b -> build()).bounds(right - 76, 3, 38, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Undo build"), b -> {
            send(RoadPlannerMessage.Action.UNDO);
            // the roads are gone again, so the plan can be changed and built afresh
            plan.nodes.forEach(n -> n.built = false);
            plan.segments.forEach(sg -> sg.built = false);
            changed();
            flash("Last build undone - the plan is still here to change and build again");
        }).bounds(right - 136, 3, 58, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            remember();
            plan = new RoadPlan();
            chainLast = null;
            selNode = selSeg = null;
            changed();
        }).bounds(right - 172, 3, 34, 16).build());
        rebuildPanel();
    }

    private void setMode(Mode m) {
        mode = m;
        chainLast = null;
        rebuildPanel();
    }

    private Button drawButton, editButton;

    /** The left panel: the selected point's junction, or the selected (or next) road's style. */
    private void rebuildPanel() {
        for (Button b : panelButtons) {
            removeWidget(b);
        }
        panelButtons.clear();
        if (drawButton != null) {
            drawButton.active = mode != Mode.DRAW;
            editButton.active = mode != Mode.EDIT;
        }
        int y = TOP + 14;
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
        button(y, () -> "Grass verge: " + (st.verge == 0 ? "none" : st.verge), b -> edit.accept(() -> st.verge = st.verge >= 4 ? 0 : st.verge + 1));
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
        if (seg != null) {
            halfButtons(y, () -> "Straighten", b -> edit.accept(() -> seg.bend = 0), () -> "Reverse", b -> edit.accept(() -> {
                int t = seg.a;
                seg.a = seg.b;
                seg.b = t;
                seg.bend = -seg.bend;
            }));
            y += ROW;
            button(y, () -> "Delete road", b -> deleteSelection());
            y += ROW;
            button(y, () -> "Use for new roads", b -> {
                drawStyle = st.copy();
                lastStyle = drawStyle.copy();
                flash("New roads will be " + st.name);
            });
        }
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
    }

    private static String yellowName(RoadStyle.Yellow y) {
        return y == null ? "none" : switch (y) {
            case NONE -> "none";
            case SINGLE -> "single";
            case DOUBLE -> "double";
            case AUTO -> "auto";
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
        history.add(plan.toJson());
        if (history.size() > 60) {
            history.remove(0);
        }
    }

    private void undoEdit() {
        if (history.isEmpty()) {
            flash("Nothing to undo");
            return;
        }
        plan = RoadPlan.fromJson(history.remove(history.size() - 1));
        chainLast = null;
        selNode = selSeg = null;
        changed();
    }

    private void changed() {
        lines = Layout.lines(plan);
        previewStale = true;
        rebuildPanel();
    }

    private void deleteSelection() {
        remember();
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
        if (preview == null && plan.segments.isEmpty()) {
            flash("Draw a road first");
            return;
        }
        byte[] data = RoadPlans.pack(plan);
        if (data.length > RoadPlans.MAX_BYTES) {
            flash("The plan is too big to send in one go - clear the built roads and carry on with a new plan");
            return;
        }
        PtmUkNetwork.CHANNEL.sendToServer(new RoadPlannerMessage(RoadPlannerMessage.Action.BUILD, data, hand == InteractionHand.OFF_HAND));
        Minecraft.getInstance().setScreen(null);
    }

    private void send(RoadPlannerMessage.Action action) {
        PtmUkNetwork.CHANNEL.sendToServer(new RoadPlannerMessage(action, new byte[0], hand == InteractionHand.OFF_HAND));
    }

    @Override
    public void removed() {
        byte[] data = RoadPlans.pack(plan);
        if (data.length <= RoadPlans.MAX_BYTES && stack() != null) {
            PtmUkNetwork.CHANNEL.sendToServer(new RoadPlannerMessage(RoadPlannerMessage.Action.SAVE, data, hand == InteractionHand.OFF_HAND));
        }
        if (map != null) {
            map.close();
        }
        Minecraft.getInstance().getTextureManager().release(PREVIEW);
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
        // edit: the selected road's bend handle, a point, or a road
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

    private void drawClick(double mx, double my) {
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
        if (key == GLFW.GLFW_KEY_ESCAPE && chainLast != null) {
            chainLast = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER) {
            chainLast = null;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) && (selNode != null || selSeg != null)) {
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
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the preview

    @Override
    public void tick() {
        if (map != null && !map.done()) {
            map.step(24);
            if (map.done()) {
                previewStale = true;                    // the ground is all known now
            }
        }
        if (messageTicks > 0) {
            messageTicks--;
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
        if (previewStale && pending == null && !draggingNode && !draggingBend && map != null) {
            previewStale = false;
            RoadPlan copy = plan.copy();
            var terrain = map.terrain();
            pending = CompletableFuture.supplyAsync(() -> Layout.of(copy, terrain));
        }
    }

    private static int abgr(int rgb, int alpha) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return (alpha << 24) | (b << 16) | (g << 8) | r;
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
            int c = switch (l.kind[i]) {
                case Layout.ROAD -> abgr(l.top[i] % 1 != 0 ? 0x44464B : 0x3A3C40, 255);
                case Layout.PAINT, Layout.DOME -> abgr(0xF0F0F0, 255);
                case Layout.KERB, Layout.ISLAND_KERB -> abgr(0xB8B8B2, 255);
                case Layout.FOOTWAY -> abgr(0xA8A8A6, 255);
                case Layout.TACTILE -> abgr(0xB0453A, 255);
                case Layout.VERGE, Layout.ISLAND, Layout.RESERVATION -> abgr(0x5E9C48, 255);
                case Layout.BARRIER -> abgr(0xD0D0D8, 255);
                case Layout.BLEND -> abgr(0x8A7650, 70);
                default -> 0;
            };
            int m = l.mark[i];
            if (m != Layout.M_NONE && l.kind[i] == Layout.ROAD) {
                c = (m & Layout.YELLOW) != 0 ? abgr(0xF2C230, 255) : abgr(0xFFFFFF, 255);
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
                img.setPixelRGBA(x, z, abgr(0xFFE45C, 255));
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
        drawPlan(g);
        drawPlayer(g);
        g.disableScissor();
        // panel and bars
        g.fill(0, 0, PANEL, height, 0xF0202226);
        g.fill(PANEL, 0, width, TOP, 0xF0202226);
        Node n = selNode == null ? null : plan.node(selNode);
        Segment s = selSeg == null ? null : plan.segment(selSeg);
        String heading = n != null && mode == Mode.EDIT ? "Point " + describe(n)
                : s != null ? "Selected road (" + s.style.totalWidth() + " wide)"
                : "New roads (" + drawStyle.totalWidth() + " wide)";
        g.drawString(font, "Road Planner", 6, 6, 0xFFFFD24A);
        g.drawString(font, heading, 6, TOP + 3, 0xFFE0E0E0);
        String help = mode == Mode.DRAW
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

    private void drawPlan(GuiGraphics g) {
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
            dot(g, x, y, sel ? 4 : 3, 0xFF000000);
            dot(g, x, y, sel ? 3 : 2, sel ? 0xFFFFD24A : colour);
            double r = lines.roundaboutRadius(n.id);
            if (r > 0 && (preview == null || showLines)) {
                for (int k = 0; k < 48; k++) {
                    double a0 = k * Math.PI * 2 / 48, a1 = (k + 1) * Math.PI * 2 / 48;
                    line(g, x + Math.cos(a0) * r * zoom, y + Math.sin(a0) * r * zoom, x + Math.cos(a1) * r * zoom,
                            y + Math.sin(a1) * r * zoom, 1, 0xC05AA0FF);
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
