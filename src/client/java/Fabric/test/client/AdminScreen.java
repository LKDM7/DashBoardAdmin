package Fabric.test.client;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.AdminActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Collection;

public class AdminScreen extends Screen {
    // Palette
    private static final int C_BG      = 0xF01A1A1A;
    private static final int C_SIDE    = 0xFF0C0C0C;
    private static final int C_HBAR    = 0xFF111111;
    private static final int C_ACCENT  = 0xFF00E5FF;
    private static final int C_DIV     = 0x33FFFFFF;
    private static final int C_ROW     = 0x11FFFFFF;
    private static final int C_TABSEL  = 0x1A00AAFF;
    private static final int SIDE_W    = 100;

    // State
    private int     currentTab  = 0;
    private String  selPlayer   = null;
    private String  selGamemode = "???";
    private String  search      = "";
    private java.util.Set<String>         mutedPlayers    = new java.util.HashSet<>();
    private java.util.Set<String>         frozenPlayers   = new java.util.HashSet<>();
    private java.util.Set<String>         keepInvPlayers  = new java.util.HashSet<>();
    private java.util.Map<String, String> reports         = new java.util.LinkedHashMap<>();
    private java.util.Map<String, String> acceptedReports = new java.util.LinkedHashMap<>();
    private java.util.Map<String, String> closedReports   = new java.util.LinkedHashMap<>();
    private EditBox  announceBox, banReasonBox, searchBox;
    private boolean  isBanning = false, isKicking = false, isRemovingMobs = false;
    private boolean  pvpEnabled;
    private boolean  chatLocked          = Fabric.test.Test.isChatLocked();
    private boolean  weatherCycleEnabled = Fabric.test.Test.isWeatherCycleEnabled();

    // Layout (computed in init)
    private int px, py, pw, ph;
    private int cx, midX, midY;

    public AdminScreen(AdminCommand.OpenAdminGuiPayload payload) {
        super(Component.literal("ADMIN DASHBOARD"));
        this.pvpEnabled = payload.pvpEnabled();
        parseList(payload.mutedPlayers(),        mutedPlayers);
        parseList(payload.frozenPlayers(),        frozenPlayers);
        parseList(payload.keepInventoryPlayers(), keepInvPlayers);
        parseMap(payload.reports(),         reports);
        parseMap(payload.acceptedReports(), acceptedReports);
        parseMap(payload.closedReports(),   closedReports);
    }

    private static void parseList(String raw, java.util.Set<String> target) {
        if (!raw.isEmpty()) for (String n : raw.split(";")) target.add(n);
    }
    private static void parseMap(String raw, java.util.Map<String, String> target) {
        if (!raw.isEmpty()) for (String part : raw.split("\\|")) {
            int i = part.indexOf(':');
            if (i > 0) target.put(part.substring(0, i), part.substring(i + 1));
        }
    }

    // ─── init ────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        pw = (int)(this.width  * 0.70);
        ph = (int)(this.height * 0.70);
        px = (this.width  - pw) / 2;
        py = (this.height - ph) / 2;
        cx = px + SIDE_W;
        midX = cx + (pw - SIDE_W) / 2;
        midY = py + ph / 2;

        clearWidgets();

        // ── Confirmation dialogs ─────────────────────────────────────────────────
        if (isBanning) {
            int dw = 240, dh = 110, dx = (width - dw) / 2, dy = (height - dh) / 2;
            banReasonBox = new EditBox(font, dx + 10, dy + 35, 220, 20, Component.literal("Raison du ban"));
            banReasonBox.setFocused(true);
            addRenderableWidget(banReasonBox);
            addRenderableWidget(btn("§aVALIDER",  b -> { send("BAN", selPlayer, banReasonBox.getValue()); isBanning = false; init(); }).bounds(dx + 10,  dy + 80, 105, 20).build());
            addRenderableWidget(btn("§cANNULER",  b -> { isBanning = false; init(); }).bounds(dx + 125, dy + 80, 105, 20).build());
            return;
        }
        if (isKicking) {
            int dw = 240, dh = 80, dx = (width - dw) / 2, dy = (height - dh) / 2;
            addRenderableWidget(btn("§cCONFIRMER KICK", b -> { send("KICK", selPlayer, ""); isKicking = false; init(); }).bounds(dx + 10,  dy + 48, 105, 20).build());
            addRenderableWidget(btn("ANNULER",           b -> { isKicking = false; init(); }).bounds(dx + 125, dy + 48, 105, 20).build());
            return;
        }
        if (isRemovingMobs) {
            int dw = 240, dh = 80, dx = (width - dw) / 2, dy = (height - dh) / 2;
            addRenderableWidget(btn("§cSUPPRIMER MOBS", b -> { send("REMOVE_MOBS", "", ""); isRemovingMobs = false; init(); }).bounds(dx + 10,  dy + 48, 105, 20).build());
            addRenderableWidget(btn("ANNULER",           b -> { isRemovingMobs = false; init(); }).bounds(dx + 125, dy + 48, 105, 20).build());
            return;
        }

        // ── Sidebar tabs ─────────────────────────────────────────────────────────
        int unresolved = reports.size() + acceptedReports.size();
        tab("MONDE",    0, py + 42);
        tab("JOUEURS",  1, py + 66);
        tab("CHAT",     2, py + 90);
        tab("FEATURES", 3, py + 114);
        tab("REPORTS" + (unresolved == 0 ? "" : " §c(" + unresolved + ")"), 4, py + 138);
        addRenderableWidget(btn("FERMER", b -> onClose()).bounds(px + 5, py + ph - 25, SIDE_W - 10, 20).build());

        // ── Tab content ──────────────────────────────────────────────────────────
        switch (currentTab) {
            case 0 -> buildMonde();
            case 1 -> buildJoueurs();
            case 2 -> buildChat();
            case 3 -> buildFeatures();
            case 4 -> buildReports();
        }
    }

    private void tab(String label, int id, int y) {
        boolean active = currentTab == id;
        Component txt = Component.literal(label).withStyle(
            active ? s -> s.withColor(0x00E5FF).withBold(true) : s -> s.withColor(0x777777));
        addRenderableWidget(Button.builder(txt, b -> { currentTab = id; selPlayer = null; init(); })
            .bounds(px + 5, y, SIDE_W - 10, 20).build());
    }

    private Button.Builder btn(String label, Button.OnPress handler) {
        return Button.builder(Component.literal(label), handler);
    }

    // ─── MONDE ───────────────────────────────────────────────────────────────────

    private void buildMonde() {
        int lx = cx + 12;
        int ty = py + 56;
        addRenderableWidget(btn("MATIN",  b -> send("SET_MORNING",         "", "")).bounds(lx,       ty, 58, 20).build());
        addRenderableWidget(btn("JOUR",   b -> send("SET_DAY",             "", "")).bounds(lx + 62,  ty, 58, 20).build());
        addRenderableWidget(btn("SOIR",   b -> send("SET_EVENING",         "", "")).bounds(lx + 124, ty, 58, 20).build());
        addRenderableWidget(btn("NUIT",   b -> send("SET_NIGHT",           "", "")).bounds(lx + 186, ty, 58, 20).build());

        int my = py + 103;
        addRenderableWidget(btn("SOLEIL", b -> send("SET_WEATHER_CLEAR",   "", "")).bounds(lx,       my, 72, 20).build());
        addRenderableWidget(btn("PLUIE",  b -> send("SET_WEATHER_RAIN",    "", "")).bounds(lx + 76,  my, 72, 20).build());
        addRenderableWidget(btn("ORAGE",  b -> send("SET_WEATHER_THUNDER", "", "")).bounds(lx + 152, my, 72, 20).build());
        addRenderableWidget(btn("CYCLE MÉTÉO: " + (weatherCycleEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_WEATHER_CYCLE", "", ""); weatherCycleEnabled = !weatherCycleEnabled; init(); })
            .bounds(lx, my + 26, 162, 20).build());

        int oy = py + 168;
        addRenderableWidget(btn("CLEAR LAG",   b -> send("CLEAR_LAG", "", "")).bounds(lx,       oy, 92, 20).build());
        addRenderableWidget(btn("REMOVE MOBS", b -> { isRemovingMobs = true; init(); }).bounds(lx + 96,  oy, 92, 20).build());
        addRenderableWidget(btn("SET SPAWN",   b -> send("SET_SPAWN", "", "")).bounds(lx,      oy + 26, 92, 20).build());
        addRenderableWidget(btn("VANISH",      b -> send("VANISH",    "", "")).bounds(lx + 96, oy + 26, 92, 20).build());
    }

    // ─── JOUEURS ─────────────────────────────────────────────────────────────────

    private void buildJoueurs() {
        if (Minecraft.getInstance().getConnection() == null) return;
        Collection<PlayerInfo> players = Minecraft.getInstance().getConnection().getOnlinePlayers();

        searchBox = new EditBox(font, cx + 5, py + 29, 88, 14, Component.literal("Rechercher..."));
        searchBox.setResponder(s -> { if (!s.equals(search)) { search = s; init(); } });
        searchBox.setValue(search);
        addRenderableWidget(searchBox);

        int yOff = py + 48;
        for (PlayerInfo info : players) {
            String name = info.getProfile().name();
            if (!search.isEmpty() && !name.toLowerCase().contains(search.toLowerCase())) continue;
            boolean sel = name.equals(selPlayer);
            String dot = mutedPlayers.contains(name)   ? "§c■ " :
                         frozenPlayers.contains(name)  ? "§b■ " :
                         keepInvPlayers.contains(name) ? "§a■ " : "§8· ";
            Component lbl = Component.literal(dot + (sel ? "§f§l" : "§7") + name);
            addRenderableWidget(Button.builder(lbl, b -> {
                selPlayer   = name;
                selGamemode = info.getGameMode().getName().toUpperCase();
                init();
            }).bounds(cx + 4, yOff, 90, 14).build());
            yOff += 16;
        }

        if (selPlayer == null) return;

        int divX = cx + 98;
        int areaW = px + pw - divX - 6;
        int aMid  = divX + 2 + areaW / 2;
        int lCol  = aMid - 108;
        int rCol  = aMid + 8;
        int bw    = 100;
        int aY    = py + 68;

        addRenderableWidget(btn("INVENTAIRE", b -> send("OPEN_INV",     selPlayer, "")).bounds(lCol, aY,       bw, 20).build());
        addRenderableWidget(btn("ENDERCHEST", b -> send("ENDERCHEST",   selPlayer, "")).bounds(lCol, aY + 24,  bw, 20).build());
        addRenderableWidget(btn("BRING",      b -> send("BRING",        selPlayer, "")).bounds(lCol, aY + 48,  bw, 20).build());
        addRenderableWidget(btn("TP VERS",    b -> send("TELEPORT_TO",  selPlayer, "")).bounds(lCol, aY + 72,  bw, 20).build());
        addRenderableWidget(btn("§aHEAL",     b -> send("HEAL",         selPlayer, "")).bounds(lCol, aY + 96,  bw, 20).build());
        addRenderableWidget(btn("§eLOG",      b -> { send("GET_LOGS",   selPlayer, ""); onClose(); }).bounds(lCol, aY + 120, bw, 20).build());

        boolean frozen  = frozenPlayers.contains(selPlayer);
        boolean muted   = mutedPlayers.contains(selPlayer);
        boolean keepInv = keepInvPlayers.contains(selPlayer);

        addRenderableWidget(btn(frozen  ? "§bFREEZE"    : "§7FREEZE",   b -> { send("FREEZE",         selPlayer, ""); if (frozen)  frozenPlayers.remove(selPlayer);  else frozenPlayers.add(selPlayer);  init(); }).bounds(rCol, aY,       bw, 20).build());
        addRenderableWidget(btn(selGamemode, b -> { send("GAMEMODE", selPlayer, ""); selGamemode = switch(selGamemode) { case "SURVIVAL" -> "CREATIVE"; case "CREATIVE" -> "SPECTATOR"; default -> "SURVIVAL"; }; init(); }).bounds(rCol, aY + 24, bw, 20).build());
        addRenderableWidget(btn("§cKICK",                               b -> { isKicking = true; init(); }).bounds(rCol, aY + 48,  bw, 20).build());
        addRenderableWidget(btn("§4BAN",                                b -> { isBanning = true; init(); }).bounds(rCol, aY + 72,  bw, 20).build());
        addRenderableWidget(btn(muted   ? "§cMUTE"     : "§7MUTE",     b -> { send("MUTE",           selPlayer, ""); if (muted)   mutedPlayers.remove(selPlayer);   else mutedPlayers.add(selPlayer);   init(); }).bounds(rCol, aY + 96,  bw, 20).build());
        addRenderableWidget(btn(keepInv ? "§aKEEP INV" : "§7KEEP INV", b -> { send("KEEP_INVENTORY", selPlayer, ""); if (keepInv) keepInvPlayers.remove(selPlayer); else keepInvPlayers.add(selPlayer); init(); }).bounds(rCol, aY + 120, bw, 20).build());
    }

    // ─── CHAT ────────────────────────────────────────────────────────────────────

    private void buildChat() {
        announceBox = new EditBox(font, midX - 105, midY - 15, 210, 20, Component.empty());
        addRenderableWidget(announceBox);
        addRenderableWidget(btn("DIFFUSER", b -> { send("ANNOUNCE", "", announceBox.getValue()); announceBox.setValue(""); }).bounds(midX - 50, midY + 12, 100, 20).build());
        addRenderableWidget(btn("LOCK CHAT: " + (chatLocked ? "§cON" : "§aOFF"),
            b -> { send("LOCK_CHAT", "", ""); chatLocked = !chatLocked; init(); })
            .bounds(midX - 75, midY + 38, 150, 20).build());
    }

    // ─── FEATURES ────────────────────────────────────────────────────────────────

    private void buildFeatures() {
        addRenderableWidget(btn("PIÉTINEMENT CULTURES: " + (Fabric.test.Test.isCropTrampleEnabled() ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_CROP_TRAMPLE", "", ""); init(); })
            .bounds(midX - 110, midY - 10, 220, 20).build());
    }

    // ─── REPORTS ─────────────────────────────────────────────────────────────────

    private void buildReports() {
        int bottomY = py + ph - 32;   // where the CLÔTURÉS strip starts
        int colDiv  = cx + (pw - SIDE_W) / 2;  // vertical divider = midX
        int rx1 = colDiv - 3;          // right edge of left panel
        int lx2 = colDiv + 3;          // left edge of right panel
        int rx2 = px + pw - 4;

        // Left panel — EN ATTENTE
        int y = py + 48;
        for (java.util.Map.Entry<String, String> e : new java.util.LinkedHashMap<>(reports).entrySet()) {
            String pn = e.getKey();
            addRenderableWidget(btn("§aACCEPT", b -> {
                String msg = reports.remove(pn);
                if (msg != null) acceptedReports.put(pn, msg);
                send("ACCEPT_REPORT", pn, "");
                init();
            }).bounds(rx1 - 112, y + 8, 50, 14).build());
            addRenderableWidget(btn("§cREFUSER", b -> {
                reports.remove(pn);
                send("REFUSE_REPORT", pn, "");
                init();
            }).bounds(rx1 - 58, y + 8, 50, 14).build());
            y += 44;
        }

        // Right panel — EN COURS
        y = py + 48;
        for (java.util.Map.Entry<String, String> e : new java.util.LinkedHashMap<>(acceptedReports).entrySet()) {
            String pn = e.getKey();
            addRenderableWidget(btn("§eCLÔTURER", b -> {
                String msg = acceptedReports.remove(pn);
                if (msg != null) {
                    if (closedReports.size() >= 15) closedReports.remove(closedReports.keySet().iterator().next());
                    closedReports.put(pn, msg);
                }
                send("CLOSE_REPORT", pn, "");
                init();
            }).bounds(rx2 - 62, y + 8, 58, 14).build());
            y += 44;
        }
    }

    private void send(String action, String target, String value) {
        ClientPlayNetworking.send(new AdminActionPayload(action, target, value));
    }

    // ─── render ──────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderTransparentBackground(g);

        g.fill(cx, py, px + pw, py + ph, C_BG);
        g.fill(px, py, cx,      py + ph, C_SIDE);

        // Sidebar header
        g.fill(px, py, cx, py + 30, 0xFF080808);
        g.drawCenteredString(font,
            Component.literal("DASHBOARD").withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            px + SIDE_W / 2, py + 11, 0xFFFFFFFF);
        g.fill(px + 6, py + 29, cx - 6, py + 30, C_ACCENT);

        // Active tab highlight
        int[] tabYs = { py + 42, py + 66, py + 90, py + 114, py + 138 };
        int tay = tabYs[currentTab];
        g.fill(px + 2, tay,     px + 4, tay + 20, C_ACCENT);
        g.fill(px + 4, tay, cx - 2, tay + 20, C_TABSEL);

        // Sidebar separator
        g.fill(cx - 1, py, cx, py + ph, C_ACCENT);

        // Content header
        String[] titles = { "GESTION DU MONDE", "JOUEURS EN LIGNE", "CHAT & ANNONCES", "FONCTIONNALITÉS", "REPORTS" };
        g.fill(cx, py, px + pw, py + 26, C_HBAR);
        g.drawCenteredString(font,
            Component.literal(titles[currentTab]).withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            midX, py + 9, 0xFFFFFFFF);
        g.fill(cx, py + 25, px + pw, py + 26, C_DIV);

        switch (currentTab) {
            case 0 -> renderMonde(g);
            case 1 -> renderJoueurs(g);
            case 2 -> renderChat(g);
            case 4 -> renderReports(g);
        }

        super.render(g, mx, my, delta);
    }

    private void renderMonde(GuiGraphics g) {
        int lx = cx + 12;
        lbl(g, "TEMPS",  lx, py + 42); g.fill(lx, py + 53, lx + 250, py + 54, C_DIV);
        lbl(g, "MÉTÉO",  lx, py + 89); g.fill(lx, py + 100, lx + 250, py + 101, C_DIV);
        lbl(g, "OUTILS", lx, py + 154); g.fill(lx, py + 165, lx + 250, py + 166, C_DIV);
    }

    private void renderJoueurs(GuiGraphics g) {
        int divX = cx + 98;
        g.fill(divX, py + 26, divX + 1, py + ph - 5, C_DIV);

        if (selPlayer == null) {
            g.drawCenteredString(font, "§8", cx + 49, py + ph / 2, 0xFF555555);
            return;
        }

        g.fill(divX + 1, py + 26, px + pw, py + 46, 0xFF0A0A0A);
        g.drawString(font, "§e§l" + selPlayer, divX + 8, py + 31, 0xFFFFFFFF);
        int gmX = divX + 10 + font.width("§e§l" + selPlayer);
        g.drawString(font, " §8[" + selGamemode + "]", gmX, py + 31, 0xFFFFFFFF);
        g.fill(divX + 1, py + 45, px + pw, py + 46, C_DIV);

        int areaW = px + pw - (divX + 2) - 6;
        int aMid  = divX + 2 + areaW / 2;
        lbl(g, "ACTIONS",    aMid - 108, py + 53);
        lbl(g, "MODÉRATION", aMid + 8,   py + 53);
    }

    private void renderChat(GuiGraphics g) {
        g.drawCenteredString(font, "§8Rédigez votre annonce :", midX, midY - 33, 0xFF666666);
        if (announceBox != null && !announceBox.getValue().isEmpty()) {
            g.fill(cx + 8, midY + 58, px + pw - 8, midY + 94, 0x22FFFFFF);
            g.fill(cx + 8, midY + 58, cx + 9,      midY + 94, C_ACCENT);
            g.drawString(font, "§8Aperçu", cx + 14, midY + 62, 0xFF666666);
            g.drawString(font, Component.literal("§6§l[ANNONCE] §r" + announceBox.getValue()), cx + 14, midY + 75, 0xFFFFFFFF);
        }
    }

    private void renderReports(GuiGraphics g) {
        int bottomY = py + ph - 32;
        int colDiv  = midX;
        int lx1 = cx + 2,    rx1 = colDiv - 3;
        int lx2 = colDiv + 3, rx2 = px + pw - 2;

        // Vertical column divider
        g.fill(colDiv - 1, py + 26, colDiv, bottomY, C_DIV);
        // Bottom CLÔTURÉS strip separator
        g.fill(cx + 2, bottomY, px + pw - 2, bottomY + 1, C_DIV);
        g.fill(cx + 2, bottomY + 1, px + pw - 2, py + ph - 3, 0x0DFFFFFF);

        // Column headers
        lbl(g, "EN ATTENTE" + (reports.isEmpty() ? "" : " (" + reports.size() + ")"),         lx1 + 5, py + 30);
        lbl(g, "EN COURS"   + (acceptedReports.isEmpty() ? "" : " (" + acceptedReports.size() + ")"), lx2 + 5, py + 30);

        // Left — pending
        if (reports.isEmpty()) {
            g.drawCenteredString(font, "§8Aucun", (lx1 + rx1) / 2, (py + 26 + bottomY) / 2, 0xFF444444);
        } else {
            int y = py + 48;
            for (java.util.Map.Entry<String, String> e : reports.entrySet()) {
                g.fill(lx1 + 3, y - 2, rx1 - 2, y + 37, C_ROW);
                g.fill(lx1 + 3, y - 2, lx1 + 4, y + 37, 0xFFFF4444);
                String msg = truncate(e.getValue(), 22);
                g.drawString(font, "§e" + e.getKey(), lx1 + 8, y + 3,  0xFFFFFFFF);
                g.drawString(font, "§7» " + msg,      lx1 + 8, y + 15, 0xFFAAAAAA);
                y += 44;
            }
        }

        // Right — in-progress
        if (acceptedReports.isEmpty()) {
            g.drawCenteredString(font, "§8Aucun", (lx2 + rx2) / 2, (py + 26 + bottomY) / 2, 0xFF444444);
        } else {
            int y = py + 48;
            for (java.util.Map.Entry<String, String> e : acceptedReports.entrySet()) {
                g.fill(lx2 + 3, y - 2, rx2 - 2, y + 37, C_ROW);
                g.fill(lx2 + 3, y - 2, lx2 + 4, y + 37, 0xFFFFAA00);
                String msg = truncate(e.getValue(), 22);
                g.drawString(font, "§e" + e.getKey(), lx2 + 8, y + 3,  0xFFFFFFFF);
                g.drawString(font, "§7» " + msg,      lx2 + 8, y + 15, 0xFFAAAAAA);
                y += 44;
            }
        }

        // Bottom — closed history
        lbl(g, "CLÔTURÉS", cx + 6, bottomY + 8);
        if (closedReports.isEmpty()) {
            g.drawString(font, "§8aucun", cx + 65, bottomY + 8, 0xFF444444);
        } else {
            int x = cx + 65;
            for (String name : closedReports.keySet()) {
                String tag = "§a■ §7" + name;
                g.drawString(font, tag, x, bottomY + 8, 0xFFFFFFFF);
                x += font.width(tag) + 8;
                if (x > px + pw - 20) break;
            }
        }
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private void lbl(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x, y, 0xFF555555);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
