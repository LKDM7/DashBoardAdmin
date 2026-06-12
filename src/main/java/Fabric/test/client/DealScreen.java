package Fabric.test.client;

import Fabric.test.networking.DealActionPayload;
import Fabric.test.networking.OpenDealPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DealScreen extends Screen {

    private static final int SLOT_SIZE   = 18;
    private static final int COLS        = 9;
    private static final int ROWS        = 3;
    private static final int GRID_W      = COLS * SLOT_SIZE; // 162
    private static final int GRID_H      = ROWS * SLOT_SIZE; // 54

    private static final int C_BG        = 0xF01A1A1A;
    private static final int C_HBAR      = 0xFF111111;
    private static final int C_ACCENT    = 0xFF00E5FF;
    private static final int C_DIV       = 0x33FFFFFF;
    private static final int C_SLOT      = 0xFF2A2A2A;
    private static final int C_SLOT_RO   = 0xFF1E1E1E;
    private static final int C_SLOT_HOV  = 0xFF3C3C3C;
    private static final int C_ACC_OVL   = 0x6600EE55; // green accepted tint

    private String          partnerName  = "";
    private List<ItemStack> myItems      = new ArrayList<>();
    private List<ItemStack> theirItems   = new ArrayList<>();
    private boolean         myAccepted;
    private boolean         theirAccepted;

    // Prevents sending a CANCEL when the server itself closes the screen
    private boolean dealClosed = false;

    // Layout — computed in init()
    private int panelX, panelY, panelW, panelH;
    private int gridX, myGridY, theirGridY, invRowY, hotbarY;

    public DealScreen(OpenDealPayload payload) {
        super(Component.literal("ÉCHANGE"));
        apply(payload);
    }

    public void onUpdate(OpenDealPayload payload) {
        if (Minecraft.getInstance().player == null) return;
        if (payload.closed()) {
            dealClosed = true; // suppress CANCEL in onClose()
            if (!payload.closeReason().isEmpty())
                Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c" + payload.closeReason()), false);
            onClose();
            return;
        }
        apply(payload);
        init();
    }

    private void apply(OpenDealPayload p) {
        partnerName   = p.partnerName();
        myAccepted    = p.myAccepted();
        theirAccepted = p.theirAccepted();
        myItems       = new ArrayList<>(p.myItems());
        theirItems    = new ArrayList<>(p.theirItems());
        while (myItems.size()    < ROWS * COLS) myItems.add(ItemStack.EMPTY);
        while (theirItems.size() < ROWS * COLS) theirItems.add(ItemStack.EMPTY);
    }

    // ─── layout ───────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelW = GRID_W + 16; // 178

        // Header(22) + myLabel(10) + myGrid(54)
        // + div(4) + theirLabel(10) + theirGrid(54)
        // + gap(8) + buttons(20)
        // + gap(8) + invLabel(10) + invGrid(54) + hbGap(4) + hotbar(18) + padding(4)
        panelH = 22 + 10 + GRID_H + 4 + 10 + GRID_H + 8 + 20 + 8 + 10 + GRID_H + 4 + SLOT_SIZE + 4;
        panelX = (width  - panelW) / 2;
        panelY = (height - panelH) / 2;

        gridX      = panelX + 8;
        myGridY    = panelY + 22 + 10;
        theirGridY = myGridY + GRID_H + 4 + 10;
        int btnY   = theirGridY + GRID_H + 8;
        invRowY    = btnY + 20 + 8 + 10;
        hotbarY    = invRowY + GRID_H + 4;

        clearWidgets();

        // Close button (✕)
        addRenderableWidget(Button.builder(Component.literal("§c✕"), b -> onClose())
            .bounds(panelX + panelW - 18, panelY + 3, 14, 14).build());

        // Accept button — disabled (grayed) when already accepted
        Button acceptBtn = Button.builder(
            Component.literal(myAccepted ? Lang.t("§a§l✔ ACCEPTÉ", "§a§l✔ ACCEPTED") : Lang.t("§aAccepter", "§aAccept")),
            b -> send("ACCEPT", -1))
            .bounds(panelX + 4, btnY, panelW / 2 - 6, 20).build();
        acceptBtn.active = !myAccepted;
        addRenderableWidget(acceptBtn);

        // Refuse button
        addRenderableWidget(Button.builder(Component.literal(Lang.t("§cRefuser", "§cDecline")), b -> send("CANCEL", -1))
            .bounds(panelX + panelW / 2 + 2, btnY, panelW / 2 - 6, 20).build());
    }

    @Override
    public void onClose() {
        if (!dealClosed) {
            dealClosed = true;
            send("CANCEL", -1);
        }
        super.onClose();
    }

    // ─── render ───────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);

        // Green border when both accepted
        if (myAccepted && theirAccepted) {
            g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF00CC44);
        }
        // Panel
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_BG);
        g.fill(panelX, panelY, panelX + panelW, panelY + 22, C_HBAR);
        g.fill(panelX, panelY + 21, panelX + panelW, panelY + 22, C_ACCENT);

        if (myAccepted && theirAccepted) {
            g.drawCenteredString(font,
                Component.literal(Lang.t("§a§l✔ ÉCHANGE CONFIRMÉ !", "§a§l✔ TRADE CONFIRMED!")).withStyle(s -> s.withBold(true)),
                panelX + panelW / 2, panelY + 7, 0xFFFFFFFF);
        } else {
            g.drawCenteredString(font,
                Component.literal(Lang.t("⇌ ÉCHANGE §8— §e", "⇌ TRADE §8— §e") + partnerName)
                    .withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
                panelX + panelW / 2, panelY + 7, 0xFFFFFFFF);
        }

        // ── My offer ──
        g.drawString(font, Lang.t("§7MON OFFRE", "§7MY OFFER") + (myAccepted ? " §a✔" : ""), gridX, myGridY - 9, 0xFF666666);
        renderGrid(g, mx, my, gridX, myGridY, myItems, false);
        if (myAccepted) g.fill(gridX, myGridY, gridX + GRID_W, myGridY + GRID_H, C_ACC_OVL);

        // Divider
        g.fill(panelX + 4, myGridY + GRID_H + 2, panelX + panelW - 4, myGridY + GRID_H + 3, C_DIV);

        // ── Their offer ──
        String theirStatus = theirAccepted ? " §a✔" : Lang.t(" §8(en attente…)", " §8(waiting…)");
        g.drawString(font, Lang.t("§7OFFRE DE §f", "§7OFFER FROM §f") + partnerName + theirStatus, gridX, theirGridY - 9, 0xFF666666);
        renderGrid(g, mx, my, gridX, theirGridY, theirItems, true);
        if (theirAccepted) g.fill(gridX, theirGridY, gridX + GRID_W, theirGridY + GRID_H, C_ACC_OVL);

        // ── Buttons divider ──
        int btnY = theirGridY + GRID_H + 8;
        g.fill(panelX + 4, btnY + 22, panelX + panelW - 4, btnY + 23, C_DIV);

        // ── Inventory ──
        g.drawString(font, Lang.t("§7INVENTAIRE", "§7INVENTORY"), gridX, invRowY - 9, 0xFF666666);
        renderInventoryRows(g, mx, my, gridX, invRowY, 9, 35);

        // Hotbar (with subtle separator)
        g.fill(gridX + 2, hotbarY - 2, gridX + GRID_W - 2, hotbarY - 1, 0x22FFFFFF);
        renderInventoryRows(g, mx, my, gridX, hotbarY, 0, 8);

        g.drawString(font, "@LKDM", panelX + panelW - font.width("@LKDM") - 4, panelY + panelH - 10, 0x55AAAAAA, false);

        super.render(g, mx, my, delta);

        // Tooltips — first match only
        if (renderGridTooltips(g, mx, my, gridX, myGridY, myItems))    return;
        if (renderGridTooltips(g, mx, my, gridX, theirGridY, theirItems)) return;
        if (renderInvTooltips(g, mx, my, gridX, invRowY, 9, 35))       return;
        renderInvTooltips(g, mx, my, gridX, hotbarY, 0, 8);
    }

    private void renderGrid(GuiGraphics g, int mx, int my, int gx, int gy,
                             List<ItemStack> items, boolean readOnly) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                int sx  = gx + col * SLOT_SIZE;
                int sy  = gy + row * SLOT_SIZE;
                boolean hover = !readOnly && !myAccepted
                    && mx >= sx && mx < sx + SLOT_SIZE - 1
                    && my >= sy && my < sy + SLOT_SIZE - 1;
                g.fill(sx, sy, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1,
                    readOnly ? C_SLOT_RO : (hover ? C_SLOT_HOV : C_SLOT));
                if (idx < items.size() && !items.get(idx).isEmpty()) {
                    g.renderItem(items.get(idx), sx + 1, sy + 1);
                    g.renderItemDecorations(font, items.get(idx), sx + 1, sy + 1);
                }
            }
        }
    }

    private void renderInventoryRows(GuiGraphics g, int mx, int my,
                                      int gx, int gy, int from, int to) {
        if (Minecraft.getInstance().player == null) return;
        net.minecraft.world.entity.player.Inventory inv = Minecraft.getInstance().player.getInventory();
        int count = to - from + 1;
        for (int i = 0; i < count; i++) {
            int slot = from + i;
            int sx   = gx + (i % COLS) * SLOT_SIZE;
            int sy   = gy + (i / COLS) * SLOT_SIZE;
            boolean hover = !myAccepted
                && mx >= sx && mx < sx + SLOT_SIZE - 1
                && my >= sy && my < sy + SLOT_SIZE - 1;
            g.fill(sx, sy, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, hover ? C_SLOT_HOV : C_SLOT);
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty()) {
                g.renderItem(stack, sx + 1, sy + 1);
                g.renderItemDecorations(font, stack, sx + 1, sy + 1);
            }
        }
    }

    // Returns true if a tooltip was rendered (stops further tooltip checks)
    private boolean renderGridTooltips(GuiGraphics g, int mx, int my,
                                        int gx, int gy, List<ItemStack> items) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                int sx  = gx + col * SLOT_SIZE, sy = gy + row * SLOT_SIZE;
                if (mx >= sx && mx < sx + SLOT_SIZE - 1 && my >= sy && my < sy + SLOT_SIZE - 1
                        && idx < items.size() && !items.get(idx).isEmpty()) {
                    g.renderTooltip(font, items.get(idx), (int)mx, (int)my);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean renderInvTooltips(GuiGraphics g, int mx, int my,
                                       int gx, int gy, int from, int to) {
        if (Minecraft.getInstance().player == null) return false;
        net.minecraft.world.entity.player.Inventory inv = Minecraft.getInstance().player.getInventory();
        int count = to - from + 1;
        for (int i = 0; i < count; i++) {
            int sx = gx + (i % COLS) * SLOT_SIZE, sy = gy + (i / COLS) * SLOT_SIZE;
            if (mx >= sx && mx < sx + SLOT_SIZE - 1 && my >= sy && my < sy + SLOT_SIZE - 1) {
                ItemStack stack = inv.getItem(from + i);
                if (!stack.isEmpty()) { g.renderTooltip(font, stack, (int)mx, (int)my); return true; }
            }
        }
        return false;
    }

    // ─── click handling ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (myAccepted) return false;

        int imx = (int) mouseX, imy = (int) mouseY;

        // Click my trade slot → remove item
        int tradeSlot = slotAt(imx, imy, gridX, myGridY);
        if (tradeSlot >= 0) { send("REMOVE_ITEM", tradeSlot); return true; }

        // Click main inventory (slots 9-35) → add item
        int invSlot = invSlotAt(imx, imy, gridX, invRowY, 9, 35);
        if (invSlot >= 0) { send("ADD_ITEM", invSlot); return true; }

        // Click hotbar (slots 0-8) → add item
        int hbSlot = invSlotAt(imx, imy, gridX, hotbarY, 0, 8);
        if (hbSlot >= 0) { send("ADD_ITEM", hbSlot); return true; }

        return false;
    }

    private int slotAt(int mx, int my, int gx, int gy) {
        if (mx < gx || mx >= gx + GRID_W || my < gy || my >= gy + GRID_H) return -1;
        return ((my - gy) / SLOT_SIZE) * COLS + (mx - gx) / SLOT_SIZE;
    }

    private int invSlotAt(int mx, int my, int gx, int gy, int from, int to) {
        int count = to - from + 1;
        int h     = ((count + COLS - 1) / COLS) * SLOT_SIZE;
        if (mx < gx || mx >= gx + GRID_W || my < gy || my >= gy + h) return -1;
        int i = ((my - gy) / SLOT_SIZE) * COLS + (mx - gx) / SLOT_SIZE;
        return (i < count) ? from + i : -1;
    }

    private void send(String action, int slot) {
        PacketDistributor.sendToServer(new DealActionPayload(action, slot));
    }

    @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float delta) {}
    @Override public boolean isPauseScreen() { return false; }
}

