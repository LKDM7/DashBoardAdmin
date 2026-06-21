package com.lkdm.dashboardadmin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * Panneau d'auto-manger affiché à GAUCHE de l'inventaire vanilla.
 *
 * <p>Replié par défaut : un petit onglet (toujours visible) permet de le
 * révéler/masquer. Le fond et les slots sont blittés depuis la texture vanilla
 * {@code textures/gui/container/inventory.png} (resource-pack aware).</p>
 *
 * <ul>
 *   <li>Grille 3×3 d'aliments « fantômes » (clic gauche avec un aliment en main
 *       = configurer ; clic droit = vider). Seuls les items consommables sont
 *       acceptés.</li>
 *   <li>Bouton cuisse de poulet = toggle ON/OFF (barré en rouge si OFF).</li>
 *   <li>Bouton seuil de faim (1 case) qui cycle 17 → 10 → 6.</li>
 * </ul>
 */
public final class AutoEatPanel {

    private static final ResourceLocation INV_TEX =
        ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final int TEX = 256;
    private static final int WIN_W = 176, WIN_H = 166;

    private static final int SLOT = 18;
    private static final int B    = 4;   // épaisseur du cadre nine-slice
    private static final int IN   = 5;   // marge intérieure
    private static final int GAPB = 3;   // écart grille ↔ boutons
    private static final int GAP  = 1;   // écart panneau ↔ onglet

    private static final int HW = 10, HH = 22; // onglet afficher/masquer

    private static final int C_BG       = 0xF0101010;
    private static final int C_BG_HOV   = 0xF02A2A2A;
    private static final int C_ACCENT   = 0xFF00E5FF;

    private AutoEatPanel() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AutoEatPanel::onRender);
        NeoForge.EVENT_BUS.addListener(AutoEatPanel::onClick);
    }

    // Géométrie : onglet toujours présent ; panneau seulement si visible.
    private record Geo(int hx, int hy, int hw, int hh,
                       int x, int y, int w, int h,
                       int gridX, int gridY,
                       int chickX, int chickY,
                       int thrX, int thrY) {}

    private static Geo geo(AbstractContainerScreen<?> sc) {
        int w    = IN * 2 + 3 * SLOT;        // 64
        int btnY = IN + 3 * SLOT + GAPB;
        int h    = btnY + SLOT + IN;

        int hx = sc.getGuiLeft() - HW - 2;
        int hy = sc.getGuiTop() + 4;

        int px = Math.max(2, hx - w - GAP);
        int py = sc.getGuiTop();

        return new Geo(hx, hy, HW, HH,
            px, py, w, h,
            px + IN, py + IN,
            px + IN, py + btnY,
            px + IN + 2 * SLOT, py + btnY);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ─── helpers texture (resource-pack aware) ──────────────────────────────────────

    private static void panelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.blit(INV_TEX, x + B, y + B, w - 2 * B, h - 2 * B, 88f, 6f, 1, 1, TEX, TEX);
        g.blit(INV_TEX, x + B,     y,         w - 2 * B, B,         B,         0f,          WIN_W - 2 * B, B,             TEX, TEX);
        g.blit(INV_TEX, x + B,     y + h - B, w - 2 * B, B,         B,         WIN_H - B,   WIN_W - 2 * B, B,             TEX, TEX);
        g.blit(INV_TEX, x,         y + B,     B,         h - 2 * B, 0f,        B,           B,             WIN_H - 2 * B, TEX, TEX);
        g.blit(INV_TEX, x + w - B, y + B,     B,         h - 2 * B, WIN_W - B, B,           B,             WIN_H - 2 * B, TEX, TEX);
        g.blit(INV_TEX, x,         y,         B, B, 0f,        0f,        B, B, TEX, TEX);
        g.blit(INV_TEX, x + w - B, y,         B, B, WIN_W - B, 0f,        B, B, TEX, TEX);
        g.blit(INV_TEX, x,         y + h - B, B, B, 0f,        WIN_H - B, B, B, TEX, TEX);
        g.blit(INV_TEX, x + w - B, y + h - B, B, B, WIN_W - B, WIN_H - B, B, B, TEX, TEX);
    }

    private static void slotTile(GuiGraphics g, int x, int y) {
        g.blit(INV_TEX, x, y, SLOT, SLOT, 7f, 83f, SLOT, SLOT, TEX, TEX);
    }

    // ─── rendu ────────────────────────────────────────────────────────────────────

    private static void onRender(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof InventoryScreen sc)) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        GuiGraphics g = e.getGuiGraphics();
        Geo o = geo(sc);
        int mx = e.getMouseX(), my = e.getMouseY();

        boolean visible = AutoEatConfig.panelVisible;

        // Onglet afficher/masquer (toujours dessiné)
        boolean hHov = in(mx, my, o.hx(), o.hy(), o.hw(), o.hh());
        g.fill(o.hx(), o.hy(), o.hx() + o.hw(), o.hy() + o.hh(), hHov ? C_BG_HOV : C_BG);
        g.fill(o.hx() + o.hw() - 1, o.hy(), o.hx() + o.hw(), o.hy() + o.hh(), C_ACCENT);
        g.drawCenteredString(font, visible ? "§b▶" : "§b◀", o.hx() + o.hw() / 2, o.hy() + o.hh() / 2 - 4, 0xFFFFFFFF);

        if (!visible) {
            if (hHov) g.renderComponentTooltip(font, List.of(
                Component.literal(Lang.t("§bAuto-manger", "§bAuto-eat")),
                Component.literal(Lang.t("§7Clic pour afficher", "§7Click to show"))), mx, my);
            return;
        }

        panelBg(g, o.x(), o.y(), o.w(), o.h());

        // Grille 3×3
        ItemStack hoverStack = null;
        boolean hoverEmpty = false;
        for (int i = 0; i < AutoEatConfig.SLOTS; i++) {
            int sx = o.gridX() + (i % 3) * SLOT;
            int sy = o.gridY() + (i / 3) * SLOT;
            slotTile(g, sx, sy);
            boolean hov = in(mx, my, sx + 1, sy + 1, SLOT - 2, SLOT - 2);
            Item it = AutoEatConfig.food(i);
            if (it != null) {
                ItemStack st = new ItemStack(it);
                g.renderItem(st, sx + 1, sy + 1);
                if (hov) hoverStack = st;
            } else if (hov) {
                hoverEmpty = true;
            }
            if (hov) g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x80FFFFFF);
        }

        // Bouton cuisse de poulet (toggle)
        int cx = o.chickX(), cy = o.chickY();
        slotTile(g, cx, cy);
        boolean chHov = in(mx, my, cx + 1, cy + 1, SLOT - 2, SLOT - 2);
        g.renderItem(new ItemStack(Items.COOKED_CHICKEN), cx + 1, cy + 1);
        if (AutoEatConfig.enabled) {
            g.fill(cx + 1, cy, cx + SLOT - 1, cy + 1, 0xFF00CC44);
        } else {
            g.fill(cx + 1, cy + 1, cx + SLOT - 1, cy + SLOT - 1, 0x66FF0000);
            g.drawCenteredString(font, "§c§l✕", cx + SLOT / 2, cy + 5, 0xFFFF5555);
        }
        if (chHov) g.fill(cx + 1, cy + 1, cx + SLOT - 1, cy + SLOT - 1, 0x80FFFFFF);

        // Bouton seuil (1 case)
        int tx = o.thrX(), ty = o.thrY();
        slotTile(g, tx, ty);
        boolean tHov = in(mx, my, tx + 1, ty + 1, SLOT - 2, SLOT - 2);
        String tl = String.valueOf(AutoEatConfig.threshold());
        g.drawString(font, tl, tx + 1 + (SLOT - 2 - font.width(tl)) / 2, ty + (SLOT - 8) / 2, 0xFFFFE066, false);
        if (tHov) g.fill(tx + 1, ty + 1, tx + SLOT - 1, ty + SLOT - 1, 0x80FFFFFF);

        // Tooltips
        if (hoverStack != null) {
            g.renderTooltip(font, hoverStack, mx, my);
        } else if (hoverEmpty) {
            g.renderComponentTooltip(font, List.of(
                Component.literal(Lang.t("§eSlot d'aliment", "§eFood slot")),
                Component.literal(Lang.t("§7Clic gauche avec un aliment en main", "§7Left-click while holding a food")),
                Component.literal(Lang.t("§7pour le définir", "§7to set it")),
                Component.literal(Lang.t("§7Clic droit : vider", "§7Right-click: clear"))), mx, my);
        } else if (chHov) {
            g.renderComponentTooltip(font, List.of(
                Component.literal(Lang.t("§bAuto-manger : ", "§bAuto-eat: ") + Lang.onOff(AutoEatConfig.enabled)),
                Component.literal(Lang.t("§7Clic pour basculer", "§7Click to toggle"))), mx, my);
        } else if (tHov) {
            g.renderComponentTooltip(font, List.of(
                Component.literal(Lang.t("§eSeuil de faim : §f", "§eHunger threshold: §f") + AutoEatConfig.threshold() + "§7/20"),
                Component.literal(Lang.t("§7Mange quand la faim descend à ce niveau", "§7Eats when hunger drops to this level")),
                Component.literal(Lang.t("§7Clic pour changer (17 / 10 / 6)", "§7Click to change (17 / 10 / 6)"))), mx, my);
        } else if (hHov) {
            g.renderComponentTooltip(font, List.of(
                Component.literal(Lang.t("§bAuto-manger", "§bAuto-eat")),
                Component.literal(Lang.t("§7Clic pour masquer", "§7Click to hide"))), mx, my);
        }
    }

    // ─── clics ────────────────────────────────────────────────────────────────────

    private static void onClick(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!(e.getScreen() instanceof InventoryScreen sc)) return;
        Geo o = geo(sc);
        double mx = e.getMouseX(), my = e.getMouseY();
        int btn = e.getButton();

        // Onglet afficher/masquer
        if (in(mx, my, o.hx(), o.hy(), o.hw(), o.hh())) {
            AutoEatConfig.togglePanel();
            e.setCanceled(true);
            return;
        }

        if (!AutoEatConfig.panelVisible) return;

        // Grille 3×3
        for (int i = 0; i < AutoEatConfig.SLOTS; i++) {
            int sx = o.gridX() + (i % 3) * SLOT;
            int sy = o.gridY() + (i / 3) * SLOT;
            if (in(mx, my, sx, sy, SLOT, SLOT)) {
                if (btn == 1) {
                    AutoEatConfig.clearFood(i);
                } else {
                    ItemStack carried = sc.getMenu().getCarried();
                    if (AutoEatConfig.isFood(carried)) AutoEatConfig.setFood(i, carried.getItem());
                }
                e.setCanceled(true);
                return;
            }
        }

        // Bouton cuisse (toggle)
        if (in(mx, my, o.chickX(), o.chickY(), SLOT, SLOT)) {
            AutoEatConfig.toggle();
            e.setCanceled(true);
            return;
        }

        // Bouton seuil
        if (in(mx, my, o.thrX(), o.thrY(), SLOT, SLOT)) {
            AutoEatConfig.cycleThreshold();
            e.setCanceled(true);
            return;
        }

        // Tout clic restant dans le panneau est absorbé
        if (in(mx, my, o.x(), o.y(), o.w(), o.h())) e.setCanceled(true);
    }
}
