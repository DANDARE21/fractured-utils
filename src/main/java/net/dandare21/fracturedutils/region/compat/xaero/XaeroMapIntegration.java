package net.dandare21.fracturedutils.region.compat.xaero;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.region.client.ClientRegionData;
import net.dandare21.fracturedutils.region.data.EarClippingTriangulator;
import net.dandare21.fracturedutils.region.data.Polygon2D;
import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.RegionType;
import net.dandare21.fracturedutils.region.data.Vec2d;
import net.dandare21.fracturedutils.region.network.C2SSaveRegionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.dandare21.fracturedutils.client.ClientConfig;
import org.joml.Matrix4f;

import java.util.*;

/**
 * Direct integration with Xaero's World Map (xaero.map.gui.GuiMap).
 * Renders 2D polygon mesh overlays, territory labels, and provides an in-map OP region editor.
 */
@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class XaeroMapIntegration {

    public enum DisplayMode {
        ALL("All"),
        HOVER_ONLY("Hover Only"),
        HIDDEN("Hidden");

        private final String label;

        DisplayMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static DisplayMode fromString(String name) {
            for (DisplayMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) return mode;
            }
            return ALL;
        }
    }

    public enum LayerFilter {
        ALL("All"),
        MAP_ZONES("Map Zones"),
        CITIES("Cities"),
        EVENT_ZONES("Event Zones");

        private final String label;

        LayerFilter(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private static final float[] OPACITY_PRESETS = { 1.0f, 0.75f, 0.5f, 0.25f, 0.10f, 0.0f };
    private static int opacityIndex = 2; // 0.50f
    private static float fillOpacity = 0.5f;
    private static DisplayMode displayMode = DisplayMode.ALL;

    private static boolean editorMode = false;
    private static LayerFilter layerFilter = LayerFilter.ALL;

    // Draft buffer for local editing
    private static final List<Region> draftRegions = new ArrayList<>();
    private static final Deque<List<Region>> undoStack = new ArrayDeque<>();
    private static final Deque<List<Region>> redoStack = new ArrayDeque<>();

    private static Region selectedRegion = null;
    private static Region drawingRegion = null; // New region in progress
    private static List<Vec2d> drawingVertices = new ArrayList<>();

    // Dragging state
    private static boolean isDragging = false;
    private static record DragNode(Region region, int vertexIndex) {}
    private static final List<DragNode> activeDragNodes = new ArrayList<>();

    // Midpoint hover handle
    private static record MidpointHandle(Region region, int edgeIndex, double screenX, double screenY, Vec2d worldPos) {}
    private static MidpointHandle hoveredMidpoint = null;

    private static RegionInspectorPanel inspectorPanel = null;

    private static boolean isXaeroInstalled() {
        return ModList.get().isLoaded("xaeroworldmap");
    }

    private static void ensureDraftSynchronized() {
        if (draftRegions.isEmpty()) {
            copyLiveToDraft();
        }
    }

    public static void copyLiveToDraft() {
        draftRegions.clear();
        for (Region r : ClientRegionData.getInstance().getAllRegions()) {
            draftRegions.add(new Region(
                    r.getId(), r.getName(), r.getType(), r.getParentId(),
                    r.getPolygon() != null ? r.getPolygon().copy() : new Polygon2D(List.of()),
                    r.isLocked(), r.getRequiredFlag(), r.getDeniedMessage(),
                    r.getColor(), r.getSubtitle(), r.getAmbientMusic()
            ));
        }
    }

    public static void onClientRegionsSynced() {
        if (drawingRegion == null && !isDragging) {
            copyLiveToDraft();
        }
    }

    private static void pushUndo() {
        List<Region> snapshot = new ArrayList<>();
        for (Region r : draftRegions) {
            snapshot.add(new Region(
                    r.getId(), r.getName(), r.getType(), r.getParentId(),
                    r.getPolygon() != null ? r.getPolygon().copy() : new Polygon2D(List.of()),
                    r.isLocked(), r.getRequiredFlag(), r.getDeniedMessage(),
                    r.getColor(), r.getSubtitle(), r.getAmbientMusic()
            ));
        }
        undoStack.push(snapshot);
        if (undoStack.size() > 30) undoStack.removeLast();
        redoStack.clear();
    }

    private static void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(new ArrayList<>(draftRegions));
            draftRegions.clear();
            draftRegions.addAll(undoStack.pop());
            Minecraft.getInstance().player.displayClientMessage(Component.literal("§e[Editor] Undo"), true);
        }
    }

    private static void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(new ArrayList<>(draftRegions));
            draftRegions.clear();
            draftRegions.addAll(redoStack.pop());
            Minecraft.getInstance().player.displayClientMessage(Component.literal("§e[Editor] Redo"), true);
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!isXaeroInstalled()) return;
        Screen screen = event.getScreen();
        if (!XaeroReflectionHelper.isXaeroGuiMap(screen)) return;

        Minecraft mc = Minecraft.getInstance();
        inspectorPanel = new RegionInspectorPanel(
                (updatedRegion, origId) -> {
                    // Save callback
                    int existingIdx = -1;
                    for (int i = 0; i < draftRegions.size(); i++) {
                        String curId = draftRegions.get(i).getId();
                        if ((origId != null && !origId.isEmpty() && curId.equals(origId)) || curId.equals(updatedRegion.getId())) {
                            existingIdx = i;
                            break;
                        }
                    }
                    if (existingIdx >= 0) {
                        draftRegions.set(existingIdx, updatedRegion);
                    } else {
                        draftRegions.add(updatedRegion);
                    }

                    // Cascade parent ID update to any child regions
                    if (origId != null && !origId.isEmpty() && !origId.equals(updatedRegion.getId())) {
                        for (Region r : draftRegions) {
                            if (origId.equals(r.getParentId())) {
                                r.setParentId(updatedRegion.getId());
                                ModMessages.sendToServer(new C2SSaveRegionPacket(r, r.getId()));
                            }
                        }
                    }
                    selectedRegion = updatedRegion;
                },
                deletedId -> {
                    draftRegions.removeIf(r -> r.getId().equals(deletedId));
                    if (selectedRegion != null && selectedRegion.getId().equals(deletedId)) {
                        selectedRegion = null;
                    }
                },
                () -> {
                    selectedRegion = null;
                }
        );
        inspectorPanel.init(screen.width, screen.height, mc.font);
        copyLiveToDraft();

        fillOpacity = ClientConfig.getRegionMapOpacity();
        displayMode = DisplayMode.fromString(ClientConfig.getRegionMapDisplayMode());
        for (int i = 0; i < OPACITY_PRESETS.length; i++) {
            if (Math.abs(OPACITY_PRESETS[i] - fillOpacity) < 0.05f) {
                opacityIndex = i;
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!isXaeroInstalled()) return;
        Screen screen = event.getScreen();
        if (!XaeroReflectionHelper.isXaeroGuiMap(screen)) return;

        XaeroReflectionHelper.MapViewport vp = XaeroReflectionHelper.getViewport(screen);
        if (vp == null) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        Font font = mc.font;

        ensureDraftSynchronized();

        // 1. Render Polygons (Fills & Outlines & Labels)
        renderPolygonOverlays(guiGraphics, screen, vp, mouseX, mouseY, font);

        // 2. Render Toolbar (Opacity, Display Mode, and OP Editor if permitted)
        boolean isOp = mc.player != null && mc.player.hasPermissions(2);
        renderToolbar(guiGraphics, font, mouseX, mouseY, isOp);

        if (isOp && editorMode) {
            renderEditorHandles(guiGraphics, screen, vp, mouseX, mouseY, font);
            if (inspectorPanel != null && inspectorPanel.isActive()) {
                inspectorPanel.render(guiGraphics, mouseX, mouseY, event.getPartialTick(), font);
            }
        }
    }

    private static void renderPolygonOverlays(GuiGraphics guiGraphics, Screen screen, XaeroReflectionHelper.MapViewport vp, int mouseX, int mouseY, Font font) {
        List<Region> sourceList = editorMode ? draftRegions : ClientRegionData.getInstance().getAllRegions();
        double cx = vp.cameraX();
        double cz = vp.cameraZ();
        double scale = vp.effectiveScale();
        double halfW = screen.width / 2.0;
        double halfH = screen.height / 2.0;

        double mouseWorldX = cx + (mouseX - halfW) / scale;
        double mouseWorldZ = cz + (mouseY - halfH) / scale;

        // Detect hovered region (prioritizing highest tier and smallest area)
        Region hoveredRegion = null;
        List<Region> hoveredCandidates = new ArrayList<>();
        for (Region r : sourceList) {
            if (!matchesFilter(r)) continue;
            if (r.getPolygon() != null && r.getPolygon().contains(mouseWorldX, mouseWorldZ)) {
                hoveredCandidates.add(r);
            }
        }
        if (!hoveredCandidates.isEmpty()) {
            hoveredCandidates.sort((a, b) -> {
                int tierComp = Integer.compare(b.getType().getTier(), a.getType().getTier());
                if (tierComp != 0) return tierComp;
                double areaA = a.getPolygon() != null ? a.getPolygon().getArea() : 0.0;
                double areaB = b.getPolygon() != null ? b.getPolygon().getArea() : 0.0;
                return Double.compare(areaA, areaB);
            });
            hoveredRegion = hoveredCandidates.get(0);
        }

        // Determine which regions to render based on displayMode
        List<Region> activeList;
        if (displayMode == DisplayMode.HIDDEN) {
            if (editorMode && selectedRegion != null) {
                activeList = List.of(selectedRegion);
            } else {
                return; // Entirely hidden
            }
        } else if (displayMode == DisplayMode.HOVER_ONLY) {
            activeList = new ArrayList<>();
            if (hoveredRegion != null) {
                activeList.add(hoveredRegion);
            }
            if (editorMode && selectedRegion != null && !activeList.contains(selectedRegion)) {
                activeList.add(selectedRegion);
            }
            if (activeList.isEmpty()) {
                return; // Nothing hovered, keep map totally clean
            }
        } else {
            activeList = sourceList;
        }

        Matrix4f matrix = guiGraphics.pose().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // Pass 1: Render Translucent Polygon Fills (Macro-zones first, inner nested zones on top)
        if (fillOpacity > 0.001f) {
            List<Region> sortedForFills = new ArrayList<>(activeList);
            sortedForFills.sort((a, b) -> {
                int tierComp = Integer.compare(a.getType().getTier(), b.getType().getTier());
                if (tierComp != 0) return tierComp;
                double areaA = a.getPolygon() != null ? a.getPolygon().getArea() : 0.0;
                double areaB = b.getPolygon() != null ? b.getPolygon().getArea() : 0.0;
                return Double.compare(areaB, areaA);
            });

            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            for (Region r : sortedForFills) {
                if (!matchesFilter(r)) continue;
                Polygon2D poly = r.getPolygon();
                if (poly == null || poly.vertexCount() < 3) continue;

                List<Vec2d> vertices = poly.getVertices();
                int[] indices = EarClippingTriangulator.triangulate(vertices);

                int baseColor = r.getColor() != 0 ? r.getColor() : (r.getType() != null ? r.getType().getDefaultColor() : 0x00D0FF);
                int red = (baseColor >> 16) & 0xFF;
                int green = (baseColor >> 8) & 0xFF;
                int blue = baseColor & 0xFF;

                float baseAlpha = (r == selectedRegion || r == hoveredRegion) ? 95f : (r.isLocked() ? 60f : 45f);
                int alpha = Math.max(1, (int) (baseAlpha * fillOpacity));

                for (int idx : indices) {
                    Vec2d v = vertices.get(idx);
                    float sx = (float) (halfW + (v.x() - cx) * scale);
                    float sy = (float) (halfH + (v.z() - cz) * scale);
                    buffer.vertex(matrix, sx, sy, 0).color(red, green, blue, alpha).endVertex();
                }
            }
            tesselator.end();
        }

        // Pass 2: Render Crisp Border Outlines
        for (Region r : activeList) {
            if (!matchesFilter(r)) continue;
            Polygon2D poly = r.getPolygon();
            if (poly == null || poly.vertexCount() < 2) continue;

            int borderAlpha;
            if (r == selectedRegion) {
                borderAlpha = Math.max(160, (int) (255 * Math.max(0.4f, fillOpacity)));
            } else if (r == hoveredRegion) {
                borderAlpha = Math.max(180, (int) (255 * Math.max(0.5f, fillOpacity)));
            } else {
                borderAlpha = (int) (238 * fillOpacity);
            }

            if (borderAlpha <= 0) continue;

            int regColor = r.getColor() != 0 ? r.getColor() : (r.getType() != null ? r.getType().getDefaultColor() : 0x00D0FF);
            int strokeColor = (borderAlpha << 24) | (regColor & 0x00FFFFFF);
            if (r == selectedRegion) strokeColor = (borderAlpha << 24) | 0x00FFFFFF;
            else if (r == hoveredRegion) strokeColor = (borderAlpha << 24) | 0x00FFFFAA;

            float strokeWidth = (r == selectedRegion) ? 2.5f : ((r == hoveredRegion) ? 2.0f : 1.5f);

            List<Vec2d> vertices = poly.getVertices();
            int n = vertices.size();
            for (int i = 0; i < n; i++) {
                Vec2d a = vertices.get(i);
                Vec2d b = vertices.get((i + 1) % n);

                float ax = (float) (halfW + (a.x() - cx) * scale);
                float ay = (float) (halfH + (a.z() - cz) * scale);
                float bx = (float) (halfW + (b.x() - cx) * scale);
                float by = (float) (halfH + (b.z() - cz) * scale);

                drawLine(guiGraphics, ax, ay, bx, by, strokeColor, strokeWidth);
            }
        }

        // Pass 3: Render Centered Region Badges
        for (Region r : activeList) {
            if (!matchesFilter(r)) continue;
            Polygon2D poly = r.getPolygon();
            if (poly == null || poly.vertexCount() < 3) continue;

            Vec2d centroid = poly.getCentroid();
            float cxScreen = (float) (halfW + (centroid.x() - cx) * scale);
            float cyScreen = (float) (halfH + (centroid.z() - cz) * scale);

            if (cxScreen >= -50 && cxScreen <= screen.width + 50 && cyScreen >= -50 && cyScreen <= screen.height + 50) {
                String label = r.getName();
                int labelWidth = font.width(label);
                int pillX = (int) (cxScreen - labelWidth / 2.0f);
                int pillY = (int) cyScreen - 6;

                int badgeBg = (r == hoveredRegion || r == selectedRegion) ? 0xDD000000 : 0xAA000000;
                guiGraphics.fill(pillX - 4, pillY - 2, pillX + labelWidth + 4, pillY + 11, badgeBg);
                guiGraphics.fill(pillX - 4, pillY - 2, pillX - 3, pillY + 11, (0xFF000000 | r.getColor()));
                guiGraphics.drawString(font, label, pillX, pillY, 0xFFFFFFFF, true);

                if (r.isLocked()) {
                    guiGraphics.drawCenteredString(font, "§c[LOCKED]", (int) cxScreen, pillY + 12, 0xFFFF3333);
                }
            }
        }

        RenderSystem.disableBlend();
    }

    private static void renderToolbar(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, boolean isOp) {
        int x = 28;
        int y = 5;

        // Row 1: Mode toggles and display settings
        if (isOp) {
            // 1. Editor Mode Button
            String toggleText = editorMode ? "§a[Region Editor: ON]" : "§7[Region Editor: OFF]";
            int toggleW = font.width(toggleText) + 12;
            boolean hoverToggle = mouseX >= x && mouseX <= x + toggleW && mouseY >= y && mouseY <= y + 16;
            guiGraphics.fill(x, y, x + toggleW, y + 16, hoverToggle ? 0xFF2A2A38 : 0xFF181822);
            guiGraphics.fill(x, y, x + toggleW, y + 1, editorMode ? 0xFF00FF55 : 0xFF555566);
            guiGraphics.drawString(font, toggleText, x + 6, y + 4, 0xFFFFFFFF, true);
            x += toggleW + 5;

            // 2. Layer Filter Button (when editorMode is ON)
            if (editorMode) {
                String filterText = "Layer: §e" + layerFilter.getLabel();
                int filterW = font.width(filterText) + 12;
                boolean hoverFilter = mouseX >= x && mouseX <= x + filterW && mouseY >= y && mouseY <= y + 16;
                guiGraphics.fill(x, y, x + filterW, y + 16, hoverFilter ? 0xFF2A2A38 : 0xFF181822);
                guiGraphics.drawString(font, filterText, x + 6, y + 4, 0xFFFFFFFF, true);
                x += filterW + 5;
            }
        }

        // 3. Opacity Button
        String opacityText = fillOpacity <= 0.001f ? "Opacity: §c0%" : ("Opacity: §e" + (int)(fillOpacity * 100) + "%");
        int opacityW = font.width(opacityText) + 12;
        boolean hoverOpacity = mouseX >= x && mouseX <= x + opacityW && mouseY >= y && mouseY <= y + 16;
        guiGraphics.fill(x, y, x + opacityW, y + 16, hoverOpacity ? 0xFF2A2A38 : 0xFF181822);
        guiGraphics.drawString(font, opacityText, x + 6, y + 4, 0xFFFFFFFF, true);
        x += opacityW + 5;

        // 4. Display Mode Button
        String dispText = "Display: §e" + displayMode.getLabel();
        int dispW = font.width(dispText) + 12;
        boolean hoverDisp = mouseX >= x && mouseX <= x + dispW && mouseY >= y && mouseY <= y + 16;
        guiGraphics.fill(x, y, x + dispW, y + 16, hoverDisp ? 0xFF2A2A38 : 0xFF181822);
        guiGraphics.drawString(font, dispText, x + 6, y + 4, 0xFFFFFFFF, true);

        // Row 2: Editor actions (only when OP and editorMode is ON)
        if (isOp && editorMode) {
            int r2X = 28;
            int r2Y = 24;

            // New Region Button
            String newText = drawingRegion != null ? "§e[Drawing New...]" : "[+ New Region]";
            int newW = font.width(newText) + 12;
            boolean hoverNew = mouseX >= r2X && mouseX <= r2X + newW && mouseY >= r2Y && mouseY <= r2Y + 16;
            guiGraphics.fill(r2X, r2Y, r2X + newW, r2Y + 16, hoverNew ? 0xFF0055AA : 0xFF003366);
            guiGraphics.drawString(font, newText, r2X + 6, r2Y + 4, 0xFFFFFFFF, true);
            r2X += newW + 5;

            // Undo Button
            String undoText = "[Undo]";
            int undoW = font.width(undoText) + 10;
            boolean hoverUndo = mouseX >= r2X && mouseX <= r2X + undoW && mouseY >= r2Y && mouseY <= r2Y + 16;
            guiGraphics.fill(r2X, r2Y, r2X + undoW, r2Y + 16, hoverUndo ? 0xFF2A2A38 : 0xFF181822);
            guiGraphics.drawString(font, undoText, r2X + 5, r2Y + 4, undoStack.isEmpty() ? 0xFF666666 : 0xFFFFFFFF, true);
            r2X += undoW + 5;

            // Redo Button
            String redoText = "[Redo]";
            int redoW = font.width(redoText) + 10;
            boolean hoverRedo = mouseX >= r2X && mouseX <= r2X + redoW && mouseY >= r2Y && mouseY <= r2Y + 16;
            guiGraphics.fill(r2X, r2Y, r2X + redoW, r2Y + 16, hoverRedo ? 0xFF2A2A38 : 0xFF181822);
            guiGraphics.drawString(font, redoText, r2X + 5, r2Y + 4, redoStack.isEmpty() ? 0xFF666666 : 0xFFFFFFFF, true);
            r2X += redoW + 5;

            // Save & Sync All Button
            String saveText = "§a[Save & Sync All]";
            int saveW = font.width(saveText) + 12;
            boolean hoverSave = mouseX >= r2X && mouseX <= r2X + saveW && mouseY >= r2Y && mouseY <= r2Y + 16;
            guiGraphics.fill(r2X, r2Y, r2X + saveW, r2Y + 16, hoverSave ? 0xFF008833 : 0xFF005522);
            guiGraphics.drawString(font, saveText, r2X + 6, r2Y + 4, 0xFFFFFFFF, true);
        }
    }

    private static void renderEditorHandles(GuiGraphics guiGraphics, Screen screen, XaeroReflectionHelper.MapViewport vp, int mouseX, int mouseY, Font font) {
        double cx = vp.cameraX();
        double cz = vp.cameraZ();
        double scale = vp.effectiveScale();
        double halfW = screen.width / 2.0;
        double halfH = screen.height / 2.0;

        hoveredMidpoint = null;

        // Draw In-Progress Drawing Region
        if (drawingRegion != null && !drawingVertices.isEmpty()) {
            int n = drawingVertices.size();
            for (int i = 0; i < n - 1; i++) {
                Vec2d a = drawingVertices.get(i);
                Vec2d b = drawingVertices.get(i + 1);
                drawLine(guiGraphics,
                        (float)(halfW + (a.x() - cx) * scale), (float)(halfH + (a.z() - cz) * scale),
                        (float)(halfW + (b.x() - cx) * scale), (float)(halfH + (b.z() - cz) * scale),
                        0xFF55FF55, 2.0f);
            }
            // Line to cursor
            Vec2d last = drawingVertices.get(n - 1);
            drawLine(guiGraphics,
                    (float)(halfW + (last.x() - cx) * scale), (float)(halfH + (last.z() - cz) * scale),
                    mouseX, mouseY,
                    0xAA55FF55, 1.5f);
        }

        // Render Handles for existing draft regions
        for (Region r : draftRegions) {
            if (!matchesFilter(r)) continue;
            Polygon2D poly = r.getPolygon();
            if (poly == null) continue;

            List<Vec2d> vertices = poly.getVertices();
            int n = vertices.size();

            for (int i = 0; i < n; i++) {
                Vec2d v = vertices.get(i);
                float sx = (float) (halfW + (v.x() - cx) * scale);
                float sy = (float) (halfH + (v.z() - cz) * scale);

                double distToMouse = Math.hypot(mouseX - sx, mouseY - sy);
                boolean isHovered = distToMouse <= 7.0;

                // Check if shared vertex
                boolean isShared = countRegionsSharingVertex(v.x(), v.z()) > 1;
                int handleColor = isShared ? 0xFFFFD700 : 0xFF00D0FF;
                if (isHovered) handleColor = 0xFFFFFFFF;

                guiGraphics.fill((int) sx - 3, (int) sy - 3, (int) sx + 4, (int) sy + 4, 0xFF000000);
                guiGraphics.fill((int) sx - 2, (int) sy - 2, (int) sx + 3, (int) sy + 3, handleColor);

                // Midpoint Handle check
                Vec2d next = vertices.get((i + 1) % n);
                double midWorldX = (v.x() + next.x()) / 2.0;
                double midWorldZ = (v.z() + next.z()) / 2.0;
                float midSx = (float) (halfW + (midWorldX - cx) * scale);
                float midSy = (float) (halfH + (midWorldZ - cz) * scale);

                if (Math.hypot(mouseX - midSx, mouseY - midSy) <= 6.0 && hoveredMidpoint == null) {
                    hoveredMidpoint = new MidpointHandle(r, i + 1, midSx, midSy, new Vec2d(midWorldX, midWorldZ));
                    guiGraphics.drawCenteredString(font, "§e+", (int) midSx, (int) midSy - 4, 0xFFFFFFFF);
                }
            }
        }

        // Snap indicator (8 pixels)
        Vec2d snapped = findSnapVertex(mouseX, mouseY, screen, vp, 8.0);
        if (snapped != null) {
            float snapSx = (float) (halfW + (snapped.x() - cx) * scale);
            float snapSy = (float) (halfH + (snapped.z() - cz) * scale);
            guiGraphics.fill((int) snapSx - 5, (int) snapSy - 5, (int) snapSx + 6, (int) snapSy + 6, 0x66FFD700);
            guiGraphics.drawString(font, "§6SNAP", (int) snapSx + 6, (int) snapSy - 4, 0xFFFFD700, true);
        }
    }

    private static int countRegionsSharingVertex(double x, double z) {
        int count = 0;
        for (Region r : draftRegions) {
            if (r.getPolygon() != null && r.getPolygon().snapVertex(x, z, 0.05) != null) {
                count++;
            }
        }
        return count;
    }

    private static Vec2d findSnapVertex(int mouseX, int mouseY, Screen screen, XaeroReflectionHelper.MapViewport vp, double pixelThreshold) {
        double cx = vp.cameraX();
        double cz = vp.cameraZ();
        double scale = vp.effectiveScale();
        double halfW = screen.width / 2.0;
        double halfH = screen.height / 2.0;

        for (Region r : draftRegions) {
            if (r.getPolygon() == null) continue;
            for (Vec2d v : r.getPolygon().getVertices()) {
                double sx = halfW + (v.x() - cx) * scale;
                double sy = halfH + (v.z() - cz) * scale;
                if (Math.hypot(mouseX - sx, mouseY - sy) <= pixelThreshold) {
                    return v;
                }
            }
        }
        return null;
    }

    private static boolean matchesFilter(Region r) {
        if (layerFilter == LayerFilter.ALL) return true;
        if (layerFilter == LayerFilter.MAP_ZONES) return r.getType() == RegionType.MAP_ZONE;
        if (layerFilter == LayerFilter.CITIES) return r.getType() == RegionType.CITY;
        if (layerFilter == LayerFilter.EVENT_ZONES) return r.getType() == RegionType.EVENT_ZONE;
        return true;
    }

    private static void drawLine(GuiGraphics guiGraphics, float x1, float y1, float x2, float y2, int color, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4) return;

        float nx = (-dy / len) * (thickness / 2.0f);
        float ny = (dx / len) * (thickness / 2.0f);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        Matrix4f matrix = guiGraphics.pose().last().pose();

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, x1 - nx, y1 - ny, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2 - nx, y2 - ny, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2 + nx, y2 + ny, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1 + nx, y1 + ny, 0).color(r, g, b, a).endVertex();
        tesselator.end();
    }

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isXaeroInstalled()) return;
        Screen screen = event.getScreen();
        if (!XaeroReflectionHelper.isXaeroGuiMap(screen)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean isOp = mc.player.hasPermissions(2);

        double mx = event.getMouseX();
        double my = event.getMouseY();
        int button = event.getButton();
        Font font = mc.font;

        // 1. Toolbar clicks
        // Row 1 (y = 5..21)
        if (my >= 5 && my <= 21) {
            int x = 28;

            if (isOp) {
                // Toggle Button
                String toggleText = editorMode ? "§a[Region Editor: ON]" : "§7[Region Editor: OFF]";
                int toggleW = font.width(toggleText) + 12;
                if (mx >= x && mx <= x + toggleW) {
                    editorMode = !editorMode;
                    event.setCanceled(true);
                    return;
                }
                x += toggleW + 5;

                if (editorMode) {
                    // Layer Filter Button
                    String filterText = "Layer: §e" + layerFilter.getLabel();
                    int filterW = font.width(filterText) + 12;
                    if (mx >= x && mx <= x + filterW) {
                        int next = (layerFilter.ordinal() + 1) % LayerFilter.values().length;
                        layerFilter = LayerFilter.values()[next];
                        event.setCanceled(true);
                        return;
                    }
                    x += filterW + 5;
                }
            }

            // Opacity Button
            String opacityText = fillOpacity <= 0.001f ? "Opacity: §c0%" : ("Opacity: §e" + (int)(fillOpacity * 100) + "%");
            int opacityW = font.width(opacityText) + 12;
            if (mx >= x && mx <= x + opacityW) {
                opacityIndex = (opacityIndex + 1) % OPACITY_PRESETS.length;
                fillOpacity = OPACITY_PRESETS[opacityIndex];
                ClientConfig.setRegionMapOpacity(fillOpacity);
                event.setCanceled(true);
                return;
            }
            x += opacityW + 5;

            // Display Mode Button
            String dispText = "Display: §e" + displayMode.getLabel();
            int dispW = font.width(dispText) + 12;
            if (mx >= x && mx <= x + dispW) {
                int next = (displayMode.ordinal() + 1) % DisplayMode.values().length;
                displayMode = DisplayMode.values()[next];
                ClientConfig.setRegionMapDisplayMode(displayMode.name());
                event.setCanceled(true);
                return;
            }
        }

        // Row 2 (y = 24..40) when OP and editorMode is ON
        if (isOp && editorMode && my >= 24 && my <= 40) {
            int r2X = 28;

            // New Region Button
            String newText = drawingRegion != null ? "§e[Drawing New...]" : "[+ New Region]";
            int newW = font.width(newText) + 12;
            if (mx >= r2X && mx <= r2X + newW) {
                if (drawingRegion == null) {
                    drawingVertices.clear();
                    drawingRegion = new Region("new_region_" + (draftRegions.size() + 1), "New Region", RegionType.MAP_ZONE, "", new Polygon2D(List.of()), false, "", "", 0x00D0FF, "", "");
                } else {
                    // Finish drawing
                    if (drawingVertices.size() >= 3) {
                        pushUndo();
                        drawingRegion.setPolygon(new Polygon2D(new ArrayList<>(drawingVertices)));
                        draftRegions.add(drawingRegion);
                        ModMessages.sendToServer(new C2SSaveRegionPacket(drawingRegion, ""));
                        selectedRegion = drawingRegion;
                        if (inspectorPanel != null) {
                            inspectorPanel.open(selectedRegion, "", mc.font, screen.width, screen.height);
                        }
                    }
                    drawingRegion = null;
                    drawingVertices.clear();
                }
                event.setCanceled(true);
                return;
            }
            r2X += newW + 5;

            // Undo Button
            String undoText = "[Undo]";
            int undoW = font.width(undoText) + 10;
            if (mx >= r2X && mx <= r2X + undoW) {
                undo();
                event.setCanceled(true);
                return;
            }
            r2X += undoW + 5;

            // Redo Button
            String redoText = "[Redo]";
            int redoW = font.width(redoText) + 10;
            if (mx >= r2X && mx <= r2X + redoW) {
                redo();
                event.setCanceled(true);
                return;
            }
            r2X += redoW + 5;

            // Save & Sync All Button
            String saveText = "§a[Save & Sync All]";
            int saveW = font.width(saveText) + 12;
            if (mx >= r2X && mx <= r2X + saveW) {
                for (Region r : draftRegions) {
                    ModMessages.sendToServer(new C2SSaveRegionPacket(r, r.getId()));
                }
                mc.player.displayClientMessage(Component.literal("§a[Region Editor] All regions synced to server!"), false);
                event.setCanceled(true);
                return;
            }
        }

        if (!isOp || !editorMode) return;

        // 2. Inspector panel input
        if (inspectorPanel != null && inspectorPanel.isActive()) {
            if (inspectorPanel.mouseClicked(mx, my, button)) {
                event.setCanceled(true);
                return;
            }
        }

        XaeroReflectionHelper.MapViewport vp = XaeroReflectionHelper.getViewport(screen);
        if (vp == null) return;

        double cx = vp.cameraX();
        double cz = vp.cameraZ();
        double scale = vp.effectiveScale();
        double halfW = screen.width / 2.0;
        double halfH = screen.height / 2.0;

        double worldX = cx + (mx - halfW) / scale;
        double worldZ = cz + (my - halfH) / scale;

        // Apply snapping
        Vec2d snapped = findSnapVertex((int) mx, (int) my, screen, vp, 8.0);
        if (snapped != null) {
            worldX = snapped.x();
            worldZ = snapped.z();
        }

        // 3. Midpoint Handle Click (Split Edge)
        if (hoveredMidpoint != null && button == 0) {
            pushUndo();
            Region targetRegion = hoveredMidpoint.region();
            List<Vec2d> curVerts = new ArrayList<>(targetRegion.getPolygon().getVertices());
            curVerts.add(hoveredMidpoint.edgeIndex(), hoveredMidpoint.worldPos());
            targetRegion.setPolygon(new Polygon2D(curVerts));

            // Start dragging newly inserted node immediately
            isDragging = true;
            activeDragNodes.clear();
            activeDragNodes.add(new DragNode(targetRegion, hoveredMidpoint.edgeIndex()));

            event.setCanceled(true);
            return;
        }

        // 4. Vertex Handle Click (Drag or Delete)
        for (Region r : draftRegions) {
            if (!matchesFilter(r)) continue;
            Polygon2D poly = r.getPolygon();
            if (poly == null) continue;

            List<Vec2d> vertices = poly.getVertices();
            for (int i = 0; i < vertices.size(); i++) {
                Vec2d v = vertices.get(i);
                double sx = halfW + (v.x() - cx) * scale;
                double sy = halfH + (v.z() - cz) * scale;

                if (Math.hypot(mx - sx, my - sy) <= 7.0) {
                    if (button == 0) { // Left-click: start drag
                        pushUndo();
                        isDragging = true;
                        activeDragNodes.clear();

                        // Topological Shared-Node Dragging: Find all regions sharing this vertex!
                        for (Region candidate : draftRegions) {
                            if (candidate.getPolygon() == null) continue;
                            List<Vec2d> cVerts = candidate.getPolygon().getVertices();
                            for (int ci = 0; ci < cVerts.size(); ci++) {
                                if (cVerts.get(ci).distanceSq(v) <= 0.05 * 0.05) {
                                    activeDragNodes.add(new DragNode(candidate, ci));
                                }
                            }
                        }

                        event.setCanceled(true);
                        return;
                    } else if (button == 1) { // Right-click: delete vertex
                        if (vertices.size() > 3) {
                            pushUndo();
                            List<Vec2d> newVerts = new ArrayList<>(vertices);
                            newVerts.remove(i);
                            r.setPolygon(new Polygon2D(newVerts));
                            ModMessages.sendToServer(new C2SSaveRegionPacket(r, r.getId()));
                        }
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }

        // 5. In Draw Mode: Left-click drops a new vertex
        if (drawingRegion != null && button == 0) {
            drawingVertices.add(new Vec2d(worldX, worldZ));
            event.setCanceled(true);
            return;
        }

        // 6. Click inside polygon to select and open Inspector (innermost / higher-tier first)
        if (button == 0) {
            List<Region> hits = new ArrayList<>();
            for (Region r : draftRegions) {
                if (!matchesFilter(r)) continue;
                if (r.getPolygon() != null && r.getPolygon().contains(worldX, worldZ)) {
                    hits.add(r);
                }
            }

            if (!hits.isEmpty()) {
                // Priority 1: Higher tier first (EVENT_ZONE=3 > CITY=2 > MAP_ZONE=1)
                // Priority 2: Smaller polygon area first
                hits.sort((a, b) -> {
                    int tierComp = Integer.compare(b.getType().getTier(), a.getType().getTier());
                    if (tierComp != 0) return tierComp;
                    double areaA = a.getPolygon() != null ? a.getPolygon().getArea() : 0.0;
                    double areaB = b.getPolygon() != null ? b.getPolygon().getArea() : 0.0;
                    return Double.compare(areaA, areaB);
                });

                Region r = hits.get(0);
                selectedRegion = r;

                // Auto-detect containing MAP_ZONE parent suggestion
                String suggestedParent = "";
                if (r.getType() != RegionType.MAP_ZONE) {
                    for (Region parentCand : draftRegions) {
                        if (parentCand.getType() == RegionType.MAP_ZONE &&
                            parentCand != r &&
                            parentCand.getPolygon() != null &&
                            parentCand.getPolygon().contains(r.getPolygon().getCentroid())) {
                            suggestedParent = parentCand.getId();
                            break;
                        }
                    }
                }

                if (inspectorPanel != null) {
                    inspectorPanel.open(r, suggestedParent, mc.font, screen.width, screen.height);
                }
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!isXaeroInstalled() || !editorMode || !isDragging || activeDragNodes.isEmpty()) return;
        Screen screen = event.getScreen();
        if (!XaeroReflectionHelper.isXaeroGuiMap(screen)) return;

        XaeroReflectionHelper.MapViewport vp = XaeroReflectionHelper.getViewport(screen);
        if (vp == null) return;

        double cx = vp.cameraX();
        double cz = vp.cameraZ();
        double scale = vp.effectiveScale();
        double halfW = screen.width / 2.0;
        double halfH = screen.height / 2.0;

        double worldX = cx + (event.getMouseX() - halfW) / scale;
        double worldZ = cz + (event.getMouseY() - halfH) / scale;

        // Apply snapping
        Vec2d snapped = findSnapVertex((int) event.getMouseX(), (int) event.getMouseY(), screen, vp, 8.0);
        if (snapped != null) {
            worldX = snapped.x();
            worldZ = snapped.z();
        }

        Vec2d newPos = new Vec2d(worldX, worldZ);

        // Update all connected regions simultaneously
        for (DragNode dn : activeDragNodes) {
            Region r = dn.region();
            int idx = dn.vertexIndex();
            List<Vec2d> verts = new ArrayList<>(r.getPolygon().getVertices());
            if (idx >= 0 && idx < verts.size()) {
                verts.set(idx, newPos);
                r.setPolygon(new Polygon2D(verts));
            }
        }

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (isDragging) {
            isDragging = false;
            Set<Region> modified = new HashSet<>();
            for (DragNode dn : activeDragNodes) {
                modified.add(dn.region());
            }
            for (Region r : modified) {
                ModMessages.sendToServer(new C2SSaveRegionPacket(r, r.getId()));
            }
            activeDragNodes.clear();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!isXaeroInstalled() || !editorMode) return;
        Screen screen = event.getScreen();
        if (!XaeroReflectionHelper.isXaeroGuiMap(screen)) return;

        if (inspectorPanel != null && inspectorPanel.isActive()) {
            if (inspectorPanel.charTyped(event.getCodePoint(), event.getModifiers())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!isXaeroInstalled() || !editorMode) return;
        Screen screen = event.getScreen();
        if (!XaeroReflectionHelper.isXaeroGuiMap(screen)) return;

        int keyCode = event.getKeyCode();
        int modifiers = event.getModifiers();

        if (inspectorPanel != null && inspectorPanel.isActive()) {
            if (inspectorPanel.keyPressed(keyCode, event.getScanCode(), modifiers)) {
                event.setCanceled(true);
                return;
            }
            if (inspectorPanel.isAnyBoxFocused() && keyCode != 256) {
                // Consume all typing keys while an edit box is focused so map shortcuts are not triggered
                event.setCanceled(true);
                return;
            }
        }

        boolean isCtrl = (modifiers & 2) != 0; // GLFW_MOD_CONTROL

        if (isCtrl && keyCode == 90) { // Ctrl+Z
            undo();
            event.setCanceled(true);
            return;
        }
        if (isCtrl && keyCode == 89) { // Ctrl+Y
            redo();
            event.setCanceled(true);
            return;
        }

        if (keyCode == 256) { // Escape
            if (drawingRegion != null) {
                drawingRegion = null;
                drawingVertices.clear();
                event.setCanceled(true);
                return;
            }
            if (selectedRegion != null) {
                selectedRegion = null;
                if (inspectorPanel != null) inspectorPanel.close();
                event.setCanceled(true);
                return;
            }
        }
    }
}

