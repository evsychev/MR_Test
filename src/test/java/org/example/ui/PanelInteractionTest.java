package org.example.ui;

import org.example.tree.TreeRows;

import org.example.profile.UserProfile;
import org.example.state.ProfileStateStore;
import org.example.state.UserAppState;
import org.example.tree.TechnicalObjectTreeDataSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.openxr.XrView;
import org.lwjgl.system.MemoryStack;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PanelInteractionTest {
    @TempDir Path directory;
    private final PanelUiLayout layout = new PanelUiLayout(1024, 1536);
    private final AnchoredWindow window = new AnchoredWindow();
    private final Quaternionf aim = new Quaternionf();

    private void sample(TechnicalObjectTreeUI ui, int x, int y, boolean held) {
        var origin = new Vector3f(window.anchorPosition()).add(
                (float) x * window.widthMeters() / layout.logicalWidth,
                -(float) y * window.heightMeters() / layout.pixelHeight, 1f);
        ui.updateInteraction(false, origin, aim, false, held, origin, aim, true);
        ui.markWindowStateChanged(window.minimized());
        ui.pollTextureUpdate();
    }

    private void click(TechnicalObjectTreeUI ui, int x, int y) {
        sample(ui, x, y, true);
        sample(ui, x, y, false);
    }

    @Test void windowAndTreeRespondToPointerRays() {
        var path = directory.resolve("state.db");
        var profile = UserProfile.loadMock();
        var root = TechnicalObjectTreeDataSource.loadTree().roots().getFirst();
        try (var store = ProfileStateStore.open(profile.id(), path);
             var ui = new TechnicalObjectTreeUI(1024, 1536, window, profile, store);
             var stack = MemoryStack.stackPush()) {
            var views = XrView.calloc(2, stack);
            views.forEach(view -> view.pose().orientation().w(1));
            window.updateFromViews(views);
            assertEquals(1024 * 1536 * 4, ui.pollTextureUpdate().remaining());
            assertNull(ui.pollTextureUpdate());
            click(ui, 60, layout.treeViewport.y + 30);
            assertTrue(store.state().expandedNodeIds().contains(root.id));
            int childY = layout.treeViewport.y + PanelUiLayout.TREE_ROW_HEIGHT + 30;
            click(ui, 305, childY);
            assertEquals(root.children.getFirst().id, store.state().selectedNodeId());
            click(ui, 305, childY);
            click(ui, layout.minimizeButton.x + 40, 50);
            assertFalse(window.minimized(), "Details must capture the click");
            click(ui, layout.logicalWidth - 76, 132);
            click(ui, layout.minimizeButton.x + 40, 50);
            assertTrue(window.minimized());
            click(ui, layout.logicalWidth / 2, layout.pixelHeight / 2);
            assertFalse(window.minimized());
            click(ui, layout.closeButton.x + 40, 50);
            assertFalse(window.closed());
            click(ui, layout.logicalWidth - 88, 178);
            click(ui, layout.closeButton.x + 40, 50);
            assertTrue(window.closed());
        }
    }

    @Test void resettingFromProfileRestoresOnlyTheActiveUsersSettings() {
        var profile = UserProfile.loadMock();
        var path = directory.resolve("state.db");
        var root = TechnicalObjectTreeDataSource.loadTree().roots().getFirst();
        var changed = new UserAppState(1.1f, 1.8f, .1f, .2f, .3f, true,
                root.children.getFirst().id, Set.of(root.id), 2);
        try (var other = ProfileStateStore.open("other-user", path);
             var store = ProfileStateStore.open(profile.id(), path)) {
            other.save(changed);
            store.save(changed);
            try (var ui = new TechnicalObjectTreeUI(1024, 1536, window, profile, store);
                 var stack = MemoryStack.stackPush()) {
                var views = XrView.calloc(2, stack);
                views.forEach(view -> view.pose().orientation().w(1));
                window.updateFromViews(views);
                click(ui, layout.logicalWidth / 2, layout.pixelHeight / 2);
                click(ui, layout.profileReset.x + 10, layout.profileReset.y + 10);
                assertEquals(UserAppState.DEFAULT_WIDTH_METERS, window.widthMeters());
                assertEquals(UserAppState.DEFAULT_HEIGHT_METERS, window.heightMeters());
                assertFalse(window.minimized());
            }
        }
        try (var other = ProfileStateStore.open("other-user", path);
             var store = ProfileStateStore.open(profile.id(), path)) {
            assertEquals(changed, other.state());
            assertEquals(new UserAppState(UserAppState.DEFAULT_WIDTH_METERS,
                    UserAppState.DEFAULT_HEIGHT_METERS, 0, 0, 0, false, root.id, Set.of(), 0),
                    store.state());
        }
    }

    @Test void pageArrowsAndSwipesPreserveTheTree() {
        var profile = UserProfile.loadMock();
        var root = TechnicalObjectTreeDataSource.loadTree().roots().getFirst();
        try (var store = ProfileStateStore.open(profile.id(), directory.resolve("state.db"));
             var ui = new TechnicalObjectTreeUI(1024, 1536, window, profile, store);
             var stack = MemoryStack.stackPush()) {
            var views = XrView.calloc(2, stack);
            views.forEach(view -> view.pose().orientation().w(1));
            window.updateFromViews(views);
            ui.pollTextureUpdate();
            int rowY = layout.treeViewport.y + 30;
            click(ui, 60, rowY);
            click(ui, 305, rowY + PanelUiLayout.TREE_ROW_HEIGHT);
            var treeState = store.state();
            assertTrue(treeState.expandedNodeIds().contains(root.id));
            assertEquals(root.children.getFirst().id, treeState.selectedNodeId());

            click(ui, layout.previousPage.x + 32, layout.previousPage.y + 32);
            assertEquals(PanelPage.TECHNICAL_OBJECTS, ui.currentPage());
            sample(ui, 305, rowY, true);
            sample(ui, 145, rowY + 5, true);
            assertEquals(PanelPage.TECHNICAL_OBJECTS, ui.currentPage());
            sample(ui, 145, rowY + 5, false);
            assertEquals(PanelPage.ROUNDS, ui.currentPage());
            click(ui, layout.nextPage.x + 32, layout.nextPage.y + 32);
            click(ui, 60, rowY);
            assertEquals(PanelPage.ROUNDS, ui.currentPage());
            assertEquals(treeState, store.state());

            sample(ui, 175, rowY, true);
            sample(ui, 335, rowY, false);
            assertEquals(PanelPage.TECHNICAL_OBJECTS, ui.currentPage());
            click(ui, layout.nextPage.x + 32, layout.nextPage.y + 32);
            assertEquals(PanelPage.ROUNDS, ui.currentPage());
            click(ui, layout.previousPage.x + 32, layout.previousPage.y + 32);
            assertEquals(PanelPage.TECHNICAL_OBJECTS, ui.currentPage());
            assertEquals(treeState, store.state());
        }
    }

    @Test void draggingAndResizingSaveTheReleasedGeometry() {
        var profile = UserProfile.loadMock();
        var path = directory.resolve("state.db");
        try (var store = ProfileStateStore.open(profile.id(), path);
             var ui = new TechnicalObjectTreeUI(1024, 1536, window, profile, store);
             var stack = MemoryStack.stackPush()) {
            var views = XrView.calloc(2, stack);
            views.forEach(view -> view.pose().orientation().w(1));
            window.updateFromViews(views);
            ui.pollTextureUpdate();
            sample(ui, layout.logicalWidth / 2, 160, true);
            sample(ui, layout.logicalWidth / 2 + 40, 180, true);
            sample(ui, layout.logicalWidth / 2 + 40, 180, false);
            assertTrue(store.state().offsetX() > 0);
            assertTrue(store.state().offsetY() < 0);
            float width = window.widthMeters();
            sample(ui, layout.logicalWidth - 5, layout.pixelHeight - 5, true);
            sample(ui, layout.logicalWidth + 40, layout.pixelHeight + 50, true);
            sample(ui, layout.logicalWidth + 40, layout.pixelHeight + 50, false);
            assertTrue(store.state().widthMeters() > width);
        }
        try (var store = ProfileStateStore.open(profile.id(), path)) {
            assertEquals(window.widthMeters(), store.state().widthMeters());
        }
    }

    @Test void keyboardCanSwitchLanguagesAndEnterDigits() {
        var keyboard = new SearchKeyboard();
        var image = new java.awt.image.BufferedImage(1024, 1536, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            keyboard.show();
            keyboard.draw(graphics, layout.logicalWidth, layout.pixelHeight, "");
            int panelTop = layout.pixelHeight - 390;
            keyboard.activate(60, panelTop + 290);
            keyboard.draw(graphics, layout.logicalWidth, layout.pixelHeight, "");
            keyboard.activate(65, panelTop + 120);
            keyboard.activate(65, panelTop + 65);
            assertEquals("\u04391", keyboard.text());
        } finally {
            graphics.dispose();
        }
    }

    @Test void panelProducesPremultipliedRgbaForVulkan() {
        try (var texture = new PanelTexture(layout.pixelWidth, layout.pixelHeight)) {
            var pixels = texture.paint(layout, g -> {
                g.setColor(new java.awt.Color(255, 128, 0, 128));
                g.fillRect(30, 30, 100, 100);
            });
            int pixel = (60 * layout.pixelWidth + 60) * 4;
            assertEquals(128, Byte.toUnsignedInt(pixels.get(pixel)));
            assertEquals(64, Byte.toUnsignedInt(pixels.get(pixel + 1)));
            assertEquals(0, pixels.get(pixel + 2));
            assertEquals(128, Byte.toUnsignedInt(pixels.get(pixel + 3)));
            assertEquals(0, pixels.getInt(0));
        }
    }

    @Test void searchKeepsAncestorsAndPreservesExpansion() {
        var tree = TechnicalObjectTreeDataSource.loadTree();
        var root = tree.roots().getFirst();
        var child = root.children.getFirst();
        var model = new TreeRows(tree.roots(), Set.of());
        var initial = model.visible("");
        assertEquals(tree.roots().size(), initial.size());
        assertSame(initial, model.visible(""));
        var filtered = model.visible(child.id);
        assertTrue(filtered.stream().anyMatch(row -> row.node().id.equals(root.id)));
        assertTrue(filtered.stream().anyMatch(row -> row.node().id.equals(child.id)));
        assertEquals(initial, model.visible(""));
    }

    @Test void scrollbarMapsEndpointsInBothDirections() {
        var scroll = layout.scrollbar(1000);
        assertEquals(0, scroll.offsetAt(scroll.thumbTop(0) - 5));
        assertEquals(scroll.maxOffset(), scroll.offsetAt(scroll.thumbTop(scroll.maxOffset()) + 5));
        assertEquals(0, layout.scrollbar(1).maxOffset());
    }
}
