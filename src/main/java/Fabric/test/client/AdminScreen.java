package Fabric.test.client;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.AdminActionPayload;
import Fabric.test.networking.OpenZonePayload;
import Fabric.test.networking.ZoneActionPayload;
import net.neoforged.neoforge.network.PacketDistributor;
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
    private static final int SIDE_W      = 100;
    private static final int ZONE_LIST_W = 100;

    // State
    private int     currentTab  = 0;
    private String  selPlayer   = null;
    private String  selGamemode = "???";
    private String  search      = "";
    private java.util.Set<String>         mutedPlayers    = new java.util.HashSet<>();
    private java.util.Set<String>         frozenPlayers   = new java.util.HashSet<>();
    private java.util.Set<String>         keepInvPlayers  = new java.util.HashSet<>();
    private java.util.Set<String>         afkPlayers      = new java.util.HashSet<>();
    private final java.util.List<String[]> offlinePlayers = new java.util.ArrayList<>(); // [nom, lastSeenMs]
    private String[] serverStats = null; // tps|mspt|ramU|ramM|entités|chunks|uptimeSec
    private boolean  selOffline  = false;
    private final java.util.List<String[]> warpsList = new java.util.ArrayList<>(); // [nom, "x, y, z", dim]
    private EditBox warpNameBox;
    private final java.util.Map<String, String> adminNotes = new java.util.HashMap<>(); // nomLower → note
    private EditBox noteBox;
    private boolean showNotesList = false;
    private int     notesScroll   = 0;
    private java.util.Map<String, String> reports         = new java.util.LinkedHashMap<>();
    private java.util.Map<String, String> acceptedReports = new java.util.LinkedHashMap<>();
    private java.util.Map<String, String> closedReports   = new java.util.LinkedHashMap<>();
    private EditBox  announceBox, banReasonBox, searchBox;
    private boolean  isBanning = false, isKicking = false, isRemovingMobs = false, isRestarting = false;
    private long     banDurationSecs   = 0;   // 0=permanent, sinon durée en secondes
    private int      restartMinutes    = 5;
    private EditBox  banDayBox, banHourBox, banMinBox, banSecBox;
    private String   confirmUnbanPlayer = null; // non-null = confirmation déban SANCTIONS
    private String   broadcastTarget   = "";  // ""=TOUS, playerName, "GROUP:leaderName"
    private java.util.List<String[]> availableGroups = new java.util.ArrayList<>(); // [leaderName, groupName, count]
    private String                 logsPlayer  = null;
    private java.util.List<String> logsEntries = new java.util.ArrayList<>();
    private int                    logsScroll  = 0;
    private java.util.List<String[]> schedBroadcasts  = new java.util.ArrayList<>();
    private java.util.List<String[]> bannedPlayers    = new java.util.ArrayList<>(); // [name, reason]
    private java.util.List<String[]> sanctionsEntries = new java.util.ArrayList<>(); // [ts, type, player, admin, reason]
    private int  deletingBroadcastIdx = -1;
    private int  sanctionsScroll      = 0;
    private int  cdHome = 30, cdBack = 10, cdTpa = 60, cdAfk = 5;
    private EditBox broadcastMsgBox, broadcastIntervalBox, cdHomeBox, cdBackBox, cdTpaBox, afkDelayBox;
    private int     maxHomes         = 3;
    private String  webhookReports   = "";
    private String  webhookSanctions = "";
    private String  motd             = "";
    private boolean mailSpyEnabled   = false;
    private EditBox maxHomesBox;
    private EditBox webhookReportsBox;
    private EditBox webhookSanctionsBox;
    private boolean  pvpEnabled;
    private boolean  chatLocked          = Fabric.test.Test.isChatLocked();
    private boolean  weatherCycleEnabled = Fabric.test.Test.isWeatherCycleEnabled();
    private boolean  afkAutoEnabled           = false;
    private boolean  proportionalSleepEnabled = false;
    private boolean  treeCapitatorEnabled     = false;
    private boolean  fastLeafDecayEnabled     = false;
    private boolean  doubleDoorEnabled          = false;
    private boolean  cropTrampleEnabled         = false;
    private boolean  rightClickHarvestEnabled  = false;
    private boolean  dispenserHarvestEnabled   = false;

    // Report image overlay
    private final java.util.Map<String, byte[]> reportImageCache  = new java.util.HashMap<>();
    private String  reportImagePlayer  = null;
    private net.minecraft.client.renderer.texture.DynamicTexture reportOverlayTex    = null;
    private net.minecraft.resources.ResourceLocation              reportOverlayTexLoc = null;

    // Zones tab state
    private record ZoneData(int x1, int y1, int z1, int x2, int y2, int z2,
                             java.util.List<String[]> members, boolean nightVision,
                             java.util.Map<Fabric.test.ZoneFlag, Boolean> flags, boolean enabled,
                             int colorIdx, int priority, String greeting, String farewell,
                             java.util.List<String> inside) {
        int color() { return Fabric.test.Zone.COLORS[Math.floorMod(colorIdx, Fabric.test.Zone.COLORS.length)]; }
    }
    private final java.util.Map<String, ZoneData> zoneMap      = new java.util.LinkedHashMap<>();
    private final java.util.List<String>          zoneOnline   = new java.util.ArrayList<>();
    private String  selectedZone   = null;
    private int     zoneListScroll = 0;
    private int     zoneDetailTab  = 0; // 0=MEMBRES 1=COORDS 2=OPTIONS 3=MSG
    private EditBox zMinXBox, zMinYBox, zMinZBox, zMaxXBox, zMaxYBox, zMaxZBox, zPriorityBox;
    private EditBox zGreetingBox, zFarewellBox;

    // Layout (computed in init)
    private int px, py, pw, ph;
    private int cx, midX, midY;
    private final int[] tabYMap = new int[9]; // Y position of each tab button, for highlight
    // Sidebar nav layout (computed in init from ph, mirrored by render for labels/dividers)
    private int navTabH = 20;
    private int navServeurLabelY, navJoueursLabelY, navChatLabelY;
    private int navDiv1Y, navDiv2Y;

    public AdminScreen(AdminCommand.OpenAdminGuiPayload payload) {
        super(Component.literal("ADMIN DASHBOARD"));
        applyPayload(payload);
    }

    /** Rafraîchissement en place (action REFRESH_ADMIN) : recharge les données, garde l'onglet. */
    public void onAdminRefresh(AdminCommand.OpenAdminGuiPayload payload) {
        mutedPlayers.clear(); frozenPlayers.clear(); keepInvPlayers.clear(); afkPlayers.clear();
        reports.clear(); acceptedReports.clear(); closedReports.clear();
        schedBroadcasts.clear(); bannedPlayers.clear(); availableGroups.clear(); offlinePlayers.clear();
        warpsList.clear(); adminNotes.clear();
        applyPayload(payload);
        init();
    }

    private void applyPayload(AdminCommand.OpenAdminGuiPayload payload) {
        this.pvpEnabled = payload.pvpEnabled();
        parseList(payload.afkPlayers(),          afkPlayers);
        if (!payload.offlinePlayers().isEmpty())
            for (String entry : payload.offlinePlayers().split(";")) {
                int ci = entry.lastIndexOf(':');
                if (ci > 0) offlinePlayers.add(new String[]{ entry.substring(0, ci), entry.substring(ci + 1) });
            }
        serverStats = payload.serverStats().isEmpty() ? null : payload.serverStats().split("\\|");
        if (!payload.adminNotes().isEmpty())
            for (String line : payload.adminNotes().split("\n")) {
                int ci = line.indexOf(':');
                if (ci > 0) adminNotes.put(line.substring(0, ci).toLowerCase(), line.substring(ci + 1));
            }
        if (!payload.warps().isEmpty())
            for (String entry : payload.warps().split(";")) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    String[] cd = parts[1].split(",", 4);
                    if (cd.length == 4) warpsList.add(new String[]{
                        parts[0], cd[0] + ", " + cd[1] + ", " + cd[2],
                        cd[3].replace("minecraft:", "") });
                }
            }
        parseList(payload.mutedPlayers(),        mutedPlayers);
        parseList(payload.frozenPlayers(),        frozenPlayers);
        parseList(payload.keepInventoryPlayers(), keepInvPlayers);
        parseMap(payload.reports(),         reports);
        parseMap(payload.acceptedReports(), acceptedReports);
        parseMap(payload.closedReports(),   closedReports);
        String sbRaw = payload.scheduledBroadcasts();
        if (!sbRaw.isEmpty()) for (String entry : sbRaw.split("\\|")) {
            String[] parts = entry.split("\t", 2);
            if (parts.length == 2) schedBroadcasts.add(new String[]{parts[0], parts[1]});
        }
        String cdRaw = payload.cooldowns();
        if (!cdRaw.isEmpty()) {
            String[] parts = cdRaw.split("\\|");
            if (parts.length == 3) try {
                cdHome = Integer.parseInt(parts[0]);
                cdBack = Integer.parseInt(parts[1]);
                cdTpa  = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {}
        }
        String banRaw = payload.bannedPlayers();
        if (!banRaw.isEmpty()) for (String entry : banRaw.split("\\|")) {
            int ci = entry.indexOf(':');
            if (ci > 0) bannedPlayers.add(new String[]{ entry.substring(0, ci), entry.substring(ci + 1) });
        }
        String featRaw = payload.features();
        if (!featRaw.isEmpty()) {
            String[] feats = featRaw.split("\\|", -1);
            if (feats.length >= 5) {
                afkAutoEnabled           = Boolean.parseBoolean(feats[0]);
                proportionalSleepEnabled = Boolean.parseBoolean(feats[1]);
                treeCapitatorEnabled     = Boolean.parseBoolean(feats[2]);
                fastLeafDecayEnabled     = Boolean.parseBoolean(feats[3]);
                doubleDoorEnabled        = Boolean.parseBoolean(feats[4]);
                if (feats.length >= 6)  try { cdAfk     = Integer.parseInt(feats[5]); } catch (NumberFormatException ignored) {}
                if (feats.length >= 7)  rightClickHarvestEnabled = Boolean.parseBoolean(feats[6]);
                if (feats.length >= 8)  dispenserHarvestEnabled  = Boolean.parseBoolean(feats[7]);
                if (feats.length >= 9)  cropTrampleEnabled       = Boolean.parseBoolean(feats[8]);
                if (feats.length >= 10) try { maxHomes  = Integer.parseInt(feats[9]); } catch (NumberFormatException ignored) {}
                if (feats.length >= 11) webhookReports   = feats[10];
                if (feats.length >= 12) webhookSanctions = feats[11];
                if (feats.length >= 13) motd             = feats[12];
                if (feats.length >= 14) mailSpyEnabled   = Boolean.parseBoolean(feats[13]);
            }
        }
        String grpRaw = payload.groupsSerialized();
        if (!grpRaw.isEmpty()) for (String entry : grpRaw.split("\\|")) {
            String[] parts = entry.split(":", 3);
            if (parts.length == 3) availableGroups.add(parts);
        }
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
        // Ratio of the screen, with a minimum size so the tab/content layout stays clean at
        // high GUI scales (x3/x4) where the available screen is small.
        pw = Math.max((int)(this.width  * 0.70), Math.min(this.width  - 20, 400));
        // Height floor raised to ~310 so the densest tab (FEATURES) and the full sidebar fit
        // at high GUI scales; clamped to height-16 so the panel never leaves the screen.
        ph = Math.max((int)(this.height * 0.72), Math.min(this.height - 16, 310));
        px = (this.width  - pw) / 2;
        py = (this.height - ph) / 2;
        cx = px + SIDE_W;
        midX = cx + (pw - SIDE_W) / 2;
        midY = py + ph / 2;

        clearWidgets();

        // ── Report image overlay ─────────────────────────────────────────────────
        if (reportImagePlayer != null && reportOverlayTexLoc != null) {
            int closeBtnY = Math.min(height / 2 + 110, height - 30);
            addRenderableWidget(btn("§cFermer", b -> {
                reportImagePlayer = null;
                init();
            }).bounds(width / 2 - 40, closeBtnY, 80, 20).build());
            return;
        }

        // ── Liste des notes admin (overlay) ──────────────────────────────────────
        if (showNotesList) {
            int closeBtnY = py + ph - 28;
            addRenderableWidget(btn("§cFermer", b -> { showNotesList = false; init(); })
                .bounds(width / 2 - 40, closeBtnY, 80, 20).build());
            return;
        }

        // ── Confirmation dialogs ─────────────────────────────────────────────────
        if (confirmUnbanPlayer != null) {
            int dw = 240, dh = 80, dx = (width - dw) / 2, dy = (height - dh) / 2;
            final String pname = confirmUnbanPlayer;
            addRenderableWidget(btn("§aCONFIRMER DÉBAN", b -> {
                send("UNBAN", pname, "");
                bannedPlayers.removeIf(e -> e[0].equalsIgnoreCase(pname));
                sanctionsEntries.removeIf(e -> "BAN".equals(e[1]) && e[2].equalsIgnoreCase(pname));
                confirmUnbanPlayer = null;
                init();
            }).bounds(dx + 10, dy + 48, 105, 20).build());
            addRenderableWidget(btn("ANNULER", b -> { confirmUnbanPlayer = null; init(); })
                .bounds(dx + 125, dy + 48, 105, 20).build());
            return;
        }
        if (isBanning) {
            int dw = 240, dh = 174, dx = (width - dw) / 2, dy = (height - dh) / 2;
            // init() est rappelé à chaque clic de chip : on préserve ce qui a déjà été tapé.
            String prevReason = banReasonBox != null ? banReasonBox.getValue() : "";
            String prevD = banDayBox  != null ? banDayBox.getValue()  : "";
            String prevH = banHourBox != null ? banHourBox.getValue() : "";
            String prevM = banMinBox  != null ? banMinBox.getValue()  : "";
            String prevS = banSecBox  != null ? banSecBox.getValue()  : "";

            banReasonBox = new EditBox(font, dx + 10, dy + 34, 220, 20, Component.literal("Raison du ban"));
            banReasonBox.setValue(prevReason);
            banReasonBox.setFocused(true);
            addRenderableWidget(banReasonBox);

            long[] durations = {86400L, 259200L, 604800L, 0L};
            String[] durLabels = {"1j", "3j", "7j", "∞"};
            for (int i = 0; i < 4; i++) {
                final long dur = durations[i];
                boolean sel = banDurationSecs == dur;
                addRenderableWidget(btn((sel ? "§a" : "§7") + durLabels[i],
                    b -> { banDurationSecs = dur; init(); })
                    .bounds(dx + 10 + i * 56, dy + 70, 50, 16).build());
            }

            // Durée personnalisée : jours / heures / minutes / secondes (prend le dessus si renseignée)
            banDayBox  = banUnitBox(dx + 10,  dy + 106, prevD);
            banHourBox = banUnitBox(dx + 66,  dy + 106, prevH);
            banMinBox  = banUnitBox(dx + 122, dy + 106, prevM);
            banSecBox  = banUnitBox(dx + 178, dy + 106, prevS);

            addRenderableWidget(btn("§aVALIDER", b -> {
                long custom = banBoxVal(banDayBox) * 86400L + banBoxVal(banHourBox) * 3600L
                            + banBoxVal(banMinBox) * 60L + banBoxVal(banSecBox);
                long secs = custom > 0 ? custom : banDurationSecs;
                send("BAN", selPlayer, secs + "\t" + banReasonBox.getValue());
                isBanning = false; banDurationSecs = 0; init();
            }).bounds(dx + 10, dy + 144, 105, 20).build());
            addRenderableWidget(btn("§cANNULER", b -> {
                isBanning = false; banDurationSecs = 0; init();
            }).bounds(dx + 125, dy + 144, 105, 20).build());
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
        if (isRestarting) {
            int dw = 240, dh = 104, dx = (width - dw) / 2, dy = (height - dh) / 2;
            int[] durations = {1, 5, 15, 30};
            for (int i = 0; i < durations.length; i++) {
                final int dur = durations[i];
                boolean sel = restartMinutes == dur;
                addRenderableWidget(btn((sel ? "§a" : "§7") + dur + "m",
                    b -> { restartMinutes = dur; init(); })
                    .bounds(dx + 10 + i * 56, dy + 38, 50, 16).build());
            }
            addRenderableWidget(btn("§cPROGRAMMER", b -> {
                send("SCHEDULE_RESTART", "", String.valueOf(restartMinutes));
                isRestarting = false; init();
            }).bounds(dx + 10, dy + 74, 105, 20).build());
            addRenderableWidget(btn("ANNULER", b -> { isRestarting = false; init(); })
                .bounds(dx + 125, dy + 74, 105, 20).build());
            return;
        }

        // ── Sidebar tabs (adaptive vertical layout: compresses at high GUI scale so the
        //    lower tabs never collide with the bottom-anchored FERMER button) ───────────
        int unresolved = reports.size() + acceptedReports.size();
        int fermerH   = 20;
        int navTop    = py + 36;
        int navBottom = py + ph - 8 - fermerH;   // tabs must end above FERMER
        int gap       = 6;
        int labelH    = 11;
        int avail     = navBottom - navTop;
        int fixedOverhead = 3 * labelH + 2 * gap; // 3 section labels + 2 inter-group gaps
        int tabSlot   = Math.max(15, Math.min(22, (avail - fixedOverhead) / 9));
        navTabH       = Math.min(20, tabSlot - 2);

        int y = navTop;
        // ─ SERVEUR group ─
        navServeurLabelY = y; y += labelH;
        tabYMap[0] = y; tab("MONDE",    0, y); y += tabSlot;
        tabYMap[3] = y; tab("FEATURES", 3, y); y += tabSlot;
        tabYMap[6] = y;
        boolean zonesActive = currentTab == 6;
        addRenderableWidget(Button.builder(
            Component.literal("ZONES").withStyle(zonesActive
                ? s -> s.withColor(0x00E5FF).withBold(true)
                : s -> s.withColor(0x777777)),
            b -> { send("OPEN_ZONES", "", ""); currentTab = 6; init(); })
            .bounds(px + 5, y, SIDE_W - 10, navTabH).build());
        y += tabSlot;
        tabYMap[8] = y; tab("WARPS", 8, y); y += tabSlot;
        navDiv1Y = y; y += gap;

        // ─ JOUEURS group ─
        navJoueursLabelY = y; y += labelH;
        tabYMap[1] = y; tab("JOUEURS",  1, y); y += tabSlot;
        tabYMap[5] = y; tab("LOGS",     5, y); y += tabSlot;
        tabYMap[7] = y;
        boolean sancActive = currentTab == 7;
        addRenderableWidget(Button.builder(
            Component.literal("SANCTIONS").withStyle(sancActive
                ? s -> s.withColor(0x00E5FF).withBold(true)
                : s -> s.withColor(0x777777)),
            b -> { send("GET_SANCTIONS", "", ""); currentTab = 7; init(); })
            .bounds(px + 5, y, SIDE_W - 10, navTabH).build());
        y += tabSlot;
        navDiv2Y = y; y += gap;

        // ─ CHAT group ─
        navChatLabelY = y; y += labelH;
        tabYMap[2] = y; tab("CHAT",     2, y); y += tabSlot;
        tabYMap[4] = y;
        tab("REPORTS" + (unresolved == 0 ? "" : " §c(" + unresolved + ")"), 4, y);

        addRenderableWidget(btn("FERMER", b -> onClose()).bounds(px + 5, py + ph - 6 - fermerH, SIDE_W - 10, fermerH).build());

        // ── Tab content ──────────────────────────────────────────────────────────
        switch (currentTab) {
            case 0 -> buildMonde();
            case 1 -> buildJoueurs();
            case 2 -> buildChat();
            case 3 -> buildFeatures();
            case 4 -> buildReports();
            case 5 -> buildLogs();
            case 6 -> buildZones();
            case 7 -> buildSanctions();
            case 8 -> buildWarps();
        }
    }

    private void tab(String label, int id, int y) {
        boolean active = currentTab == id;
        Component txt = Component.literal(label).withStyle(
            active ? s -> s.withColor(0x00E5FF).withBold(true) : s -> s.withColor(0x777777));
        addRenderableWidget(Button.builder(txt, b -> { currentTab = id; init(); })
            .bounds(px + 5, y, SIDE_W - 10, navTabH).build());
    }

    private Button.Builder btn(String label, Button.OnPress handler) {
        return Button.builder(Component.literal(label), handler);
    }

    /** Petite EditBox numérique pour la durée perso du ban (j/h/m/s). */
    private EditBox banUnitBox(int x, int y, String value) {
        EditBox b = new EditBox(font, x, y, 34, 14, Component.empty());
        b.setMaxLength(4);
        b.setValue(value);
        b.setFilter(s -> s.matches("\\d{0,4}"));
        addRenderableWidget(b);
        return b;
    }

    private static long banBoxVal(EditBox b) {
        try { return Long.parseLong(b.getValue().trim()); } catch (Exception e) { return 0; }
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
        int bw3 = Math.min(92, (px + pw - 12 - lx - 8) / 3);
        addRenderableWidget(btn("CLEAR LAG",   b -> send("CLEAR_LAG", "", "")).bounds(lx,                oy, bw3, 20).build());
        addRenderableWidget(btn("REMOVE MOBS", b -> { isRemovingMobs = true; init(); }).bounds(lx + bw3 + 4,      oy, bw3, 20).build());
        addRenderableWidget(btn("SET SPAWN",   b -> send("SET_SPAWN", "", "")).bounds(lx + (bw3 + 4) * 2, oy, bw3, 20).build());
        addRenderableWidget(btn("VANISH",      b -> send("VANISH",    "", "")).bounds(lx,                oy + 26, bw3, 20).build());
        addRenderableWidget(btn("§cRESTART",   b -> { isRestarting = true; init(); }).bounds(lx + bw3 + 4,      oy + 26, bw3, 20).build());
        addRenderableWidget(btn("§7ANNULER R.", b -> send("CANCEL_RESTART", "", "")).bounds(lx + (bw3 + 4) * 2, oy + 26, bw3, 20).build());

        // Santé serveur — bouton de rafraîchissement (les stats sont un instantané)
        addRenderableWidget(btn("§b⟳", b -> send("REFRESH_ADMIN", "", ""))
            .bounds(px + pw - 26, oy + 56, 18, 14).build());
    }

    // ─── JOUEURS ─────────────────────────────────────────────────────────────────

    /** Y du haut de la section HORS LIGNE — formule partagée entre init() et render(). */
    private int offlineSectionTop() {
        return py + 48 + filteredPlayers().size() * 16 + 8 + bannedPlayers.size() * 18
            + (bannedPlayers.isEmpty() ? 0 : 6);
    }

    /** Réplique client de Test.formatTimeAgo (la fiche Activité reçoit des timestamps bruts). */
    private static String timeAgo(long timestamp) {
        long sec = (System.currentTimeMillis() - timestamp) / 1000;
        if (sec < 60)   return sec + "s";
        long min = sec / 60;
        if (min < 60)   return min + " min";
        long hr = min / 60;
        if (hr < 24)    return hr + "h" + (min % 60 > 0 ? min % 60 : "");
        long day = hr / 24;
        return day + "j" + (hr % 24 > 0 ? " " + hr % 24 + "h" : "");
    }

    /** Joueurs en ligne filtrés par la recherche — même liste pour init() et render() (têtes). */
    private java.util.List<PlayerInfo> filteredPlayers() {
        if (Minecraft.getInstance().getConnection() == null) return java.util.List.of();
        java.util.List<PlayerInfo> out = new java.util.ArrayList<>();
        for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
            if (info.getProfile() == null) continue;
            if (!search.isEmpty() && !info.getProfile().getName().toLowerCase().contains(search.toLowerCase())) continue;
            out.add(info);
        }
        return out;
    }

    private void buildJoueurs() {
        if (Minecraft.getInstance().getConnection() == null) return;

        searchBox = new EditBox(font, cx + 5, py + 29, 88, 14, Component.literal("Rechercher..."));
        searchBox.setResponder(s -> { if (!s.equals(search)) { search = s; init(); } });
        searchBox.setValue(search);
        addRenderableWidget(searchBox);

        // Consultation de toutes les notes admin (coin droit de la barre d'en-tête du détail)
        addRenderableWidget(btn("§eNOTES" + (adminNotes.isEmpty() ? "" : " (" + adminNotes.size() + ")"),
            b -> { showNotesList = true; notesScroll = 0; init(); })
            .bounds(px + pw - 64, py + 29, 58, 14).build());

        int yOff = py + 48;
        for (PlayerInfo info : filteredPlayers()) {
            String name = info.getProfile().getName();
            boolean sel = name.equals(selPlayer);
            String dot = mutedPlayers.contains(name)   ? "§c■" :
                         frozenPlayers.contains(name)  ? "§b■" :
                         afkPlayers.contains(name)     ? "§e■" :
                         keepInvPlayers.contains(name) ? "§a■" : "";
            Component lbl = Component.literal((sel ? "§f§l" : "§7") + name + (dot.isEmpty() ? "" : " " + dot));
            addRenderableWidget(Button.builder(lbl, b -> {
                selPlayer   = name;
                selOffline  = false;
                selGamemode = info.getGameMode().getName().toUpperCase();
                send("GET_SANCTIONS", "", ""); // alimente la fiche Activité
                init();
            }).bounds(cx + 16, yOff, 78, 14).build());
            yOff += 16;
        }

        // Section BANNIS
        yOff += 8;
        for (String[] ban : bannedPlayers) {
            final String banName = ban[0];
            addRenderableWidget(btn("§aDÉBAN", b -> {
                send("UNBAN", banName, "");
                bannedPlayers.removeIf(e -> e[0].equalsIgnoreCase(banName));
                init();
            }).bounds(cx + 4, yOff + 2, 88, 12).build());
            yOff += 18;
        }

        // Section HORS LIGNE (consultation logs / sanctions / dernière connexion)
        if (!offlinePlayers.isEmpty()) {
            int offY = offlineSectionTop() + 12;
            int maxBottom = py + ph - 8;
            for (String[] off : offlinePlayers) {
                if (offY + 13 > maxBottom) break;
                final String name = off[0];
                boolean sel = name.equals(selPlayer);
                addRenderableWidget(Button.builder(
                    Component.literal((sel ? "§f§l" : "§8") + truncate(name, 11)), b -> {
                        selPlayer   = name;
                        selOffline  = true;
                        selGamemode = "HORS LIGNE";
                        send("GET_SANCTIONS", "", "");
                        init();
                    }).bounds(cx + 4, offY, 90, 12).build());
                offY += 14;
            }
        }

        if (selPlayer == null) return;

        // Note admin (fiche Activité) — éditable pour joueurs en ligne ET hors ligne
        {
            int divXn = cx + 98;
            int noteY = py + ph - 16;
            int okW = 26;
            noteBox = new EditBox(font, divXn + 42, noteY, px + pw - (divXn + 42) - okW - 10, 12, Component.literal("note admin"));
            noteBox.setMaxLength(120);
            noteBox.setValue(adminNotes.getOrDefault(selPlayer.toLowerCase(), ""));
            addRenderableWidget(noteBox);
            addRenderableWidget(btn("§aOK", b -> {
                String note = noteBox.getValue().trim();
                if (note.isEmpty()) adminNotes.remove(selPlayer.toLowerCase());
                else adminNotes.put(selPlayer.toLowerCase(), note);
                send("SET_NOTE", selPlayer, note);
            }).bounds(px + pw - okW - 6, noteY - 1, okW, 14).build());
        }

        if (selOffline) {
            // Joueur hors ligne : seules les consultations ont un sens.
            int divX2 = cx + 98;
            int areaW2 = px + pw - divX2 - 6;
            int bw2 = Math.max(60, Math.min(120, areaW2 - 16));
            addRenderableWidget(btn("§eLOGS", b -> {
                logsPlayer = null; logsEntries = new java.util.ArrayList<>(); logsScroll = 0;
                send("GET_LOGS", selPlayer, ""); currentTab = 5; init();
            }).bounds(divX2 + 2 + (areaW2 - bw2) / 2, py + 68, bw2, 20).build());
            return;
        }

        int divX = cx + 98;
        int areaW = px + pw - divX - 6;
        // Two button columns derived from the available width so they never overflow the panel
        // at high GUI scales (capped at 100px wide, centred in the area).
        int gap   = 8;
        int bw    = Math.max(60, Math.min(100, (areaW - gap) / 2));
        int totalW = bw * 2 + gap;
        int lCol  = divX + 2 + (areaW - totalW) / 2;
        int rCol  = lCol + bw + gap;
        int aY    = py + 68;

        addRenderableWidget(btn("INVENTAIRE", b -> send("OPEN_INV",     selPlayer, "")).bounds(lCol, aY,       bw, 20).build());
        addRenderableWidget(btn("ENDERCHEST", b -> send("ENDERCHEST",   selPlayer, "")).bounds(lCol, aY + 24,  bw, 20).build());
        addRenderableWidget(btn("BRING",      b -> send("BRING",        selPlayer, "")).bounds(lCol, aY + 48,  bw, 20).build());
        addRenderableWidget(btn("TP VERS",    b -> send("TELEPORT_TO",  selPlayer, "")).bounds(lCol, aY + 72,  bw, 20).build());
        addRenderableWidget(btn("§aHEAL",     b -> send("HEAL",         selPlayer, "")).bounds(lCol, aY + 96,  bw, 20).build());
        addRenderableWidget(btn("§eLOGS",     b -> { logsPlayer = null; logsEntries = new java.util.ArrayList<>(); logsScroll = 0; send("GET_LOGS", selPlayer, ""); currentTab = 5; init(); }).bounds(lCol, aY + 120, bw, 20).build());

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
        int contentX = cx + 10;
        int contentW = px + pw - cx - 20;

        // ── Section ANNONCE ──────────────────────────────────────────────────────
        int aY = py + 50;
        announceBox = new EditBox(font, contentX, aY, contentW, 20, Component.empty());
        announceBox.setMaxLength(500);
        addRenderableWidget(announceBox);

        // Chips: TOUS / joueur / groupe
        int chipY = aY + 26, chipX = contentX, chipMaxX = contentX + contentW;
        int tousW = font.width("TOUS") + 8;
        boolean tousSel = broadcastTarget.isEmpty();
        addRenderableWidget(btn((tousSel ? "§a" : "§7") + "TOUS",
            b -> { broadcastTarget = ""; init(); }).bounds(chipX, chipY, tousW, 14).build());
        chipX += tousW + 3;
        if (Minecraft.getInstance().getConnection() != null) {
            for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
                if (info.getProfile() == null) continue;
                final String pn = info.getProfile().getName();
                int cw = Math.min(font.width(pn) + 8, 90);
                if (chipX + cw > chipMaxX - 60) break;
                boolean psel = broadcastTarget.equals(pn);
                addRenderableWidget(btn((psel ? "§e" : "§7") + pn,
                    b -> { broadcastTarget = pn; init(); }).bounds(chipX, chipY, cw, 14).build());
                chipX += cw + 2;
            }
        }
        for (String[] grp : availableGroups) {
            final String key = "GROUP:" + grp[0];
            String glabel = grp[1];
            int cw = Math.min(font.width(glabel) + 8, 100);
            if (chipX + cw > chipMaxX) break;
            boolean gsel = broadcastTarget.equals(key);
            addRenderableWidget(btn((gsel ? "§b" : "§8") + glabel,
                b -> { broadcastTarget = key; init(); }).bounds(chipX, chipY, cw, 14).build());
            chipX += cw + 2;
        }

        int btnW = (contentW - 12) / 3;
        addRenderableWidget(btn("§a▶ DIFFUSER",
            b -> { if (!announceBox.getValue().isEmpty()) { send("ANNOUNCE", broadcastTarget, announceBox.getValue()); announceBox.setValue(""); } })
            .bounds(contentX, aY + 44, btnW, 20).build());
        // Définit le texte saisi comme MOTD (message de connexion) ; champ vide = MOTD supprimé.
        addRenderableWidget(btn("§eDÉFINIR MOTD",
            b -> { motd = announceBox.getValue().trim(); send("SET_MOTD", "", motd); announceBox.setValue(""); init(); })
            .bounds(contentX + btnW + 6, aY + 44, btnW, 20).build());
        addRenderableWidget(btn("CHAT " + (chatLocked ? "§c🔒 VERROUILLÉ" : "§a🔓 OUVERT"),
            b -> { send("LOCK_CHAT", "", ""); chatLocked = !chatLocked; init(); })
            .bounds(contentX + (btnW + 6) * 2, aY + 44, btnW, 20).build());

        // Spy des messages privés (les MP sont relayés aux admins en ligne).
        // Posé sur la ligne du label BROADCASTS (divY+2 côté droit, libre) — voir renderChat.
        addRenderableWidget(btn("SPY MP : " + (mailSpyEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_MAIL_SPY", "", ""); mailSpyEnabled = !mailSpyEnabled; init(); })
            .bounds(contentX + contentW - 90, aY + 101, 90, 14).build());

        // ── Section BROADCASTS ───────────────────────────────────────────────────
        int bY = py + 168;
        broadcastMsgBox = new EditBox(font, contentX, bY, contentW - 78, 18, Component.literal("Message du broadcast..."));
        broadcastIntervalBox = new EditBox(font, contentX + contentW - 72, bY, 34, 18, Component.literal("min"));
        broadcastIntervalBox.setMaxLength(4);
        addRenderableWidget(broadcastMsgBox);
        addRenderableWidget(broadcastIntervalBox);
        addRenderableWidget(btn("§aAJOUTER", b -> {
            String msg    = broadcastMsgBox.getValue().trim();
            String minStr = broadcastIntervalBox.getValue().trim();
            if (!msg.isEmpty() && !minStr.isEmpty()) {
                try {
                    int min = Integer.parseInt(minStr);
                    if (min > 0) {
                        schedBroadcasts.add(new String[]{msg, String.valueOf(min)});
                        send("SCHEDULE_ADD", "", msg + "\t" + min);
                        broadcastMsgBox.setValue("");
                        broadcastIntervalBox.setValue("");
                        init();
                    }
                } catch (NumberFormatException ignored) {}
            }
        }).bounds(contentX + contentW - 34, bY - 1, 34, 20).build());

        int listY = bY + 26;
        for (int i = 0; i < schedBroadcasts.size(); i++) {
            final int idx = i;
            if (deletingBroadcastIdx == i) {
                addRenderableWidget(btn("§a✔ OUI",
                    b -> { schedBroadcasts.remove(idx); send("SCHEDULE_REMOVE", "", String.valueOf(idx)); deletingBroadcastIdx = -1; init(); })
                    .bounds(px + pw - 82, listY + i * 20 + 3, 36, 14).build());
                addRenderableWidget(btn("§c✖ NON", b -> { deletingBroadcastIdx = -1; init(); })
                    .bounds(px + pw - 42, listY + i * 20 + 3, 36, 14).build());
            } else {
                addRenderableWidget(btn("§c✕", b -> { deletingBroadcastIdx = idx; init(); })
                    .bounds(px + pw - 26, listY + i * 20 + 3, 18, 14).build());
            }
        }
    }

    // ─── FEATURES ────────────────────────────────────────────────────────────────

    private void buildFeatures() {
        int contentW = pw - SIDE_W;
        int bx = cx + 8;
        int bw = contentW - 16;
        int hw = (bw - 4) / 2; // half-width for 2-column
        int fy = py + 34;

        // Row 1
        addRenderableWidget(btn("PIÉTINEMENT: " + (cropTrampleEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_CROP_TRAMPLE", "", ""); cropTrampleEnabled = !cropTrampleEnabled; init(); })
            .bounds(bx, fy, hw, 20).build());
        addRenderableWidget(btn("AFK AUTO: " + (afkAutoEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_AFK_AUTO", "", ""); afkAutoEnabled = !afkAutoEnabled; init(); })
            .bounds(bx + hw + 4, fy, hw, 20).build());

        // Row 2
        addRenderableWidget(btn("SOMMEIL PROPORTIONNEL: " + (proportionalSleepEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_PROPORTIONAL_SLEEP", "", ""); proportionalSleepEnabled = !proportionalSleepEnabled; init(); })
            .bounds(bx, fy + 24, hw, 20).build());
        addRenderableWidget(btn("BÛCHERON INTELLIGENT: " + (treeCapitatorEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_TREE_CAPITATOR", "", ""); treeCapitatorEnabled = !treeCapitatorEnabled; init(); })
            .bounds(bx + hw + 4, fy + 24, hw, 20).build());

        // Row 3
        addRenderableWidget(btn("FAST LEAF DECAY: " + (fastLeafDecayEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_FAST_LEAF_DECAY", "", ""); fastLeafDecayEnabled = !fastLeafDecayEnabled; init(); })
            .bounds(bx, fy + 48, hw, 20).build());
        addRenderableWidget(btn("DOUBLE DOOR: " + (doubleDoorEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_DOUBLE_DOOR", "", ""); doubleDoorEnabled = !doubleDoorEnabled; init(); })
            .bounds(bx + hw + 4, fy + 48, hw, 20).build());

        // Row 4
        addRenderableWidget(btn("RÉCOLTE CLIC DROIT: " + (rightClickHarvestEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_RIGHT_CLICK_HARVEST", "", ""); rightClickHarvestEnabled = !rightClickHarvestEnabled; init(); })
            .bounds(bx, fy + 72, hw, 20).build());
        addRenderableWidget(btn("DISTRIBUTEUR RÉCOLTE: " + (dispenserHarvestEnabled ? "§aON" : "§cOFF"),
            b -> { send("TOGGLE_DISPENSER_HARVEST", "", ""); dispenserHarvestEnabled = !dispenserHarvestEnabled; init(); })
            .bounds(bx + hw + 4, fy + 72, hw, 20).build());

        // ── Cooldowns + AFK delay ─────────────────────────────────────────────
        // 4 boxes equally spaced in one row
        int cy = fy + 122;
        int boxW = 40, boxGap = (bw - 4 * boxW) / 3;
        int b0 = bx;
        int b1 = bx + boxW + boxGap;
        int b2 = bx + (boxW + boxGap) * 2;
        int b3 = bx + (boxW + boxGap) * 3;

        cdHomeBox   = new EditBox(font, b0, cy, boxW, 16, Component.empty());
        cdBackBox   = new EditBox(font, b1, cy, boxW, 16, Component.empty());
        cdTpaBox    = new EditBox(font, b2, cy, boxW, 16, Component.empty());
        afkDelayBox = new EditBox(font, b3, cy, boxW, 16, Component.empty());
        cdHomeBox.setMaxLength(5);   cdBackBox.setMaxLength(5);
        cdTpaBox.setMaxLength(5);    afkDelayBox.setMaxLength(4);
        cdHomeBox.setValue(String.valueOf(cdHome));
        cdBackBox.setValue(String.valueOf(cdBack));
        cdTpaBox.setValue(String.valueOf(cdTpa));
        afkDelayBox.setValue(String.valueOf(cdAfk));
        addRenderableWidget(cdHomeBox);
        addRenderableWidget(cdBackBox);
        addRenderableWidget(cdTpaBox);
        addRenderableWidget(afkDelayBox);

        addRenderableWidget(btn("§aSAUVEGARDER", b -> {
            try {
                int h = Integer.parseInt(cdHomeBox.getValue().trim());
                int bk = Integer.parseInt(cdBackBox.getValue().trim());
                int t = Integer.parseInt(cdTpaBox.getValue().trim());
                int a = Integer.parseInt(afkDelayBox.getValue().trim());
                cdHome = h; cdBack = bk; cdTpa = t; cdAfk = a;
                send("SET_COOLDOWNS", "", h + "|" + bk + "|" + t);
                send("SET_AFK_DELAY", "", String.valueOf(a));
            } catch (NumberFormatException ignored) {}
        }).bounds(midX - 55, cy + 22, 110, 20).build());

        // ── Max Homes ─────────────────────────────────────────────────────────
        int hmY = cy + 50;
        maxHomesBox = new EditBox(font, bx, hmY, 36, 16, Component.empty());
        maxHomesBox.setMaxLength(2);
        maxHomesBox.setValue(String.valueOf(maxHomes));
        addRenderableWidget(maxHomesBox);
        addRenderableWidget(btn("§aOK", b -> {
            try {
                int m = Integer.parseInt(maxHomesBox.getValue().trim());
                maxHomes = Math.max(1, Math.min(10, m));
                maxHomesBox.setValue(String.valueOf(maxHomes));
                send("SET_MAX_HOMES", "", String.valueOf(maxHomes));
            } catch (NumberFormatException ignored) {}
        }).bounds(bx + 42, hmY, 30, 16).build());

        // ── Discord Webhooks (label column on the left, box fills the rest) ────
        int whY   = hmY + 36;
        int wLblW = font.width("Sanctions") + 8;
        int wBoxX = bx + wLblW;
        int wBoxW = bw - wLblW;
        webhookReportsBox   = new EditBox(font, wBoxX, whY,      wBoxW, 16, Component.empty());
        webhookSanctionsBox = new EditBox(font, wBoxX, whY + 22, wBoxW, 16, Component.empty());
        webhookReportsBox.setMaxLength(300);
        webhookSanctionsBox.setMaxLength(300);
        webhookReportsBox.setValue(webhookReports);
        webhookSanctionsBox.setValue(webhookSanctions);
        addRenderableWidget(webhookReportsBox);
        addRenderableWidget(webhookSanctionsBox);
        addRenderableWidget(btn("§aSAUVEGARDER WEBHOOKS", b -> {
            webhookReports   = webhookReportsBox.getValue().trim();
            webhookSanctions = webhookSanctionsBox.getValue().trim();
            send("SET_WEBHOOKS", webhookReports, webhookSanctions);
        }).bounds(midX - 70, whY + 40, 140, 18).build());
    }

    // ─── REPORTS ─────────────────────────────────────────────────────────────────

    private void buildReports() {
        int bottomY = py + ph - 32;
        int colDiv  = cx + (pw - SIDE_W) / 2;
        int lx1 = cx + 2;
        int rx1 = colDiv - 3;
        int lx2 = colDiv + 3;
        int rx2 = px + pw - 4;

        // Left panel — EN ATTENTE
        int y = py + 48;
        for (java.util.Map.Entry<String, String> e : new java.util.LinkedHashMap<>(reports).entrySet()) {
            String pn  = e.getKey();
            String msg = e.getValue();
            addRenderableWidget(btn("§aACCEPT", b -> {
                String m = reports.remove(pn);
                if (m != null) acceptedReports.put(pn, m);
                send("ACCEPT_REPORT", pn, "");
                init();
            }).bounds(rx1 - 112, y + 8, 50, 14).build());
            addRenderableWidget(btn("§cREFUSER", b -> {
                reports.remove(pn);
                send("REFUSE_REPORT", pn, "");
                init();
            }).bounds(rx1 - 58, y + 8, 50, 14).build());
            if (msg.length() > 0 && msg.charAt(0) == '') {
                addRenderableWidget(btn("§b[IMG]", b -> {
                    if (reportImageCache.containsKey(pn)) showReportOverlay(pn, reportImageCache.get(pn));
                    else send("FETCH_REPORT_IMAGE", pn, "");
                    init();
                }).bounds(lx1 + 4, y + 25, 32, 11).build());
            }
            y += 44;
        }

        // Right panel — EN COURS
        y = py + 48;
        for (java.util.Map.Entry<String, String> e : new java.util.LinkedHashMap<>(acceptedReports).entrySet()) {
            String pn  = e.getKey();
            String msg = e.getValue();
            addRenderableWidget(btn("§eCLÔTURER", b -> {
                String m = acceptedReports.remove(pn);
                if (m != null) {
                    if (closedReports.size() >= 15) closedReports.remove(closedReports.keySet().iterator().next());
                    closedReports.put(pn, m);
                }
                send("CLOSE_REPORT", pn, "");
                init();
            }).bounds(rx2 - 62, y + 8, 58, 14).build());
            if (msg.length() > 0 && msg.charAt(0) == '') {
                addRenderableWidget(btn("§b[IMG]", b -> {
                    if (reportImageCache.containsKey(pn)) showReportOverlay(pn, reportImageCache.get(pn));
                    else send("FETCH_REPORT_IMAGE", pn, "");
                    init();
                }).bounds(lx2 + 4, y + 25, 32, 11).build());
            }
            y += 44;
        }
    }

    private void send(String action, String target, String value) {
        PacketDistributor.sendToServer(new AdminActionPayload(action, target, value));
    }

    public void onReportImageReceived(String playerName, byte[] data) {
        reportImageCache.put(playerName, data);
        showReportOverlay(playerName, data);
        init();
    }

    private void showReportOverlay(String playerName, byte[] data) {
        if (reportOverlayTexLoc != null) {
            Minecraft.getInstance().getTextureManager().release(reportOverlayTexLoc);
            if (reportOverlayTex != null) reportOverlayTex.close();
        }
        try {
            com.mojang.blaze3d.platform.NativeImage img =
                com.mojang.blaze3d.platform.NativeImage.read(data);
            reportOverlayTex    = new net.minecraft.client.renderer.texture.DynamicTexture(img);
            reportOverlayTexLoc = net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath("dashboardadmin", "admin_report_img");
            Minecraft.getInstance().getTextureManager().register(reportOverlayTexLoc, reportOverlayTex);
            reportImagePlayer = playerName;
        } catch (Exception ignored) {
            reportOverlayTex    = null;
            reportOverlayTexLoc = null;
        }
    }

    // ─── render ──────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);

        g.fill(cx, py, px + pw, py + ph, C_BG);
        g.fill(px, py, cx,      py + ph, C_SIDE);

        // Sidebar header
        g.fill(px, py, cx, py + 30, 0xFF080808);
        g.drawCenteredString(font,
            Component.literal("DASHBOARD").withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            px + SIDE_W / 2, py + 11, 0xFFFFFFFF);
        g.fill(px + 6, py + 29, cx - 6, py + 30, C_ACCENT);

        // Sidebar section labels (positions computed in init, adaptive to ph)
        g.drawString(font, "SERVEUR",  px + 7, navServeurLabelY, 0xFF666666);
        g.drawString(font, "JOUEURS",  px + 7, navJoueursLabelY, 0xFF666666);
        g.drawString(font, "CHAT",     px + 7, navChatLabelY,    0xFF666666);
        // Group dividers
        g.fill(px + 5, navDiv1Y, cx - 5, navDiv1Y + 1, 0x33FFFFFF);
        g.fill(px + 5, navDiv2Y, cx - 5, navDiv2Y + 1, 0x33FFFFFF);

        // Active tab highlight (position from tabYMap)
        int tay = tabYMap[currentTab];
        g.fill(px + 2, tay,     px + 4, tay + navTabH, C_ACCENT);
        g.fill(px + 4, tay, cx - 2, tay + navTabH, C_TABSEL);

        // Sidebar separator
        g.fill(cx - 1, py, cx, py + ph, C_ACCENT);

        // Content header
        String[] titles = { "GESTION DU MONDE", "JOUEURS EN LIGNE", "CHAT & ANNONCES", "FONCTIONNALITÉS", "REPORTS", "LOGS JOUEURS", "GESTION DES ZONES", "HISTORIQUE SANCTIONS", "WARPS" };
        g.fill(cx, py, px + pw, py + 26, C_HBAR);
        g.drawCenteredString(font,
            Component.literal(titles[currentTab]).withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            midX, py + 9, 0xFFFFFFFF);
        g.fill(cx, py + 25, px + pw, py + 26, C_DIV);

        switch (currentTab) {
            case 0 -> renderMonde(g);
            case 1 -> renderJoueurs(g);
            case 2 -> renderChat(g);
            case 3 -> renderFeatures(g);
            case 4 -> renderReports(g);
            case 5 -> renderLogs(g);
            case 6 -> renderZones(g);
            case 7 -> renderSanctions(g);
            case 8 -> renderWarps(g);
        }

        // Dialog overlays
        if (confirmUnbanPlayer != null || isBanning || isKicking || isRemovingMobs || isRestarting) {
            int dh = isBanning ? 174 : isRestarting ? 104 : 80;
            int dw = 240, dx = (width - dw) / 2, dy = (height - dh) / 2;
            g.fill(0, 0, this.width, this.height, 0x88000000);
            g.fill(dx, dy, dx + dw, dy + dh, 0xFF1A1A1A);
            g.fill(dx, dy, dx + dw, dy + 2, C_ACCENT);
            String title = isBanning     ? "BAN : " + selPlayer
                : isKicking              ? "KICK : " + selPlayer + " ?"
                : isRemovingMobs         ? "Supprimer les mobs ?"
                : isRestarting           ? "Redémarrer le serveur ?"
                : "Déban : " + confirmUnbanPlayer + " ?";
            g.drawCenteredString(font,
                Component.literal("§c⚠ §f" + title).withStyle(s -> s.withBold(true)),
                dx + dw / 2, dy + 8, 0xFFFFFFFF);
            if (isBanning) {
                g.drawString(font, "§8Raison :", dx + 10, dy + 24, 0xFF555555);
                g.drawString(font, "§8Durée :",  dx + 10, dy + 60, 0xFF555555);
                g.drawString(font, "§8Durée perso §7(prioritaire si renseignée) §8:", dx + 10, dy + 94, 0xFF555555);
                g.drawString(font, "§7j", dx + 46,  dy + 109, 0xFF888888);
                g.drawString(font, "§7h", dx + 102, dy + 109, 0xFF888888);
                g.drawString(font, "§7m", dx + 158, dy + 109, 0xFF888888);
                g.drawString(font, "§7s", dx + 214, dy + 109, 0xFF888888);
            }
            if (isRestarting) {
                g.drawString(font, "§8Délai avant arrêt (annonces auto) :", dx + 10, dy + 26, 0xFF555555);
            }
        }

        // Notes admin overlay
        if (showNotesList) {
            int ow = Math.min(380, width - 40), oh = ph - 16;
            int ox = (width - ow) / 2, ot = py + 8;
            g.fill(0, 0, this.width, this.height, 0xAA000000);
            g.fill(ox, ot, ox + ow, ot + oh, 0xFF1A1A1A);
            g.fill(ox, ot, ox + ow, ot + 2, C_ACCENT);
            g.drawCenteredString(font,
                Component.literal("NOTES ADMIN").withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
                ox + ow / 2, ot + 8, 0xFFFFFFFF);

            int listTop = ot + 22, listBot = ot + oh - 26;
            if (adminNotes.isEmpty()) {
                g.drawCenteredString(font, "§8Aucune note enregistrée", ox + ow / 2, (listTop + listBot) / 2 - 4, 0xFF444444);
                g.drawCenteredString(font, "§8Sélectionnez un joueur puis remplissez le champ Note.", ox + ow / 2, (listTop + listBot) / 2 + 8, 0xFF333333);
            } else {
                java.util.List<String> names = new java.util.ArrayList<>(adminNotes.keySet());
                java.util.Collections.sort(names);
                int entryH = 24;
                int maxVis = Math.max(1, (listBot - listTop) / entryH);
                int maxScroll = Math.max(0, names.size() - maxVis);
                notesScroll = Math.max(0, Math.min(notesScroll, maxScroll));

                g.enableScissor(ox + 2, listTop, ox + ow - 8, listBot);
                int y = listTop;
                for (int i = notesScroll; i < names.size() && i < notesScroll + maxVis; i++) {
                    String name = names.get(i);
                    if (i % 2 == 0) g.fill(ox + 4, y, ox + ow - 10, y + entryH - 2, C_ROW);
                    g.fill(ox + 4, y, ox + 5, y + entryH - 2, 0xFFFFAA00);
                    g.drawString(font, "§e" + name, ox + 10, y + 2, 0xFFFFFFFF);
                    String note = adminNotes.get(name);
                    while (font.width("§7" + note) > ow - 24 && note.length() > 1)
                        note = note.substring(0, note.length() - 1);
                    g.drawString(font, "§7" + note + (note.equals(adminNotes.get(name)) ? "" : "…"), ox + 10, y + 12, 0xFFAAAAAA);
                    y += entryH;
                }
                g.disableScissor();

                if (names.size() > maxVis) {
                    int sbX = ox + ow - 7, sbH = listBot - listTop;
                    int thumbH = Math.max(8, sbH * maxVis / names.size());
                    int thumbY = maxScroll > 0 ? listTop + (sbH - thumbH) * notesScroll / maxScroll : listTop;
                    g.fill(sbX, listTop, sbX + 3, listBot, 0x33FFFFFF);
                    g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, C_ACCENT);
                }
            }
        }

        // Report image overlay
        if (reportImagePlayer != null && reportOverlayTexLoc != null) {
            int imgW = Math.min(480, width - 40);
            int imgH = imgW * 9 / 16;
            int ix   = (width - imgW) / 2;
            int iy   = (height - imgH) / 2 - 12;
            g.fill(0, 0, this.width, this.height, 0xAA000000);
            g.fill(ix - 4, iy - 22, ix + imgW + 4, iy + imgH + 26, 0xFF1A1A1A);
            g.fill(ix - 4, iy - 22, ix + imgW + 4, iy - 20, 0xFF00E5FF);
            g.drawCenteredString(font,
                Component.literal("§bCapture : §f" + reportImagePlayer),
                ix + imgW / 2, iy - 15, 0xFFFFFFFF);
            g.blit(reportOverlayTexLoc,
                ix, iy, 0f, 0f, imgW, imgH, imgW, imgH);
        }

        g.drawString(font, "@LKDM", px + pw - font.width("@LKDM") - 4, py + ph - 10, 0x55AAAAAA, false);

        super.render(g, mx, my, delta);
    }

    private void renderMonde(GuiGraphics g) {
        int lx = cx + 12;
        g.fill(cx, py + 38, px + pw, py + 55, 0x0AFFFFFF);
        lbl(g, "TEMPS",  lx, py + 43); g.fill(lx, py + 53, px + pw - 12, py + 54, C_DIV);
        g.fill(cx, py + 85, px + pw, py + 102, 0x0AFFFFFF);
        lbl(g, "MÉTÉO",  lx, py + 90); g.fill(lx, py + 100, px + pw - 12, py + 101, C_DIV);
        g.fill(cx, py + 150, px + pw, py + 167, 0x0AFFFFFF);
        lbl(g, "OUTILS", lx, py + 155); g.fill(lx, py + 165, px + pw - 12, py + 166, C_DIV);

        // ── Santé serveur ────────────────────────────────────────────────────
        int oy = py + 168;
        int sY = oy + 54;
        g.fill(cx, sY, px + pw, sY + 17, 0x0AFFFFFF);
        lbl(g, "SANTÉ SERVEUR", lx, sY + 5); g.fill(lx, sY + 15, px + pw - 12, sY + 16, C_DIV);
        if (serverStats == null || serverStats.length < 7) {
            g.drawString(font, "§8Indisponible", lx, sY + 22, 0xFF444444);
            return;
        }
        double tps = 0, mspt = 0;
        long ramU = 0, ramM = 1;
        try {
            tps  = Double.parseDouble(serverStats[0]);
            mspt = Double.parseDouble(serverStats[1]);
            ramU = Long.parseLong(serverStats[2]);
            ramM = Long.parseLong(serverStats[3]);
        } catch (NumberFormatException ignored) {}
        String tpsCol = tps >= 18 ? "§a" : tps >= 14 ? "§e" : "§c";
        long upSec = 0;
        try { upSec = Long.parseLong(serverStats[6]); } catch (NumberFormatException ignored) {}
        String uptime = upSec >= 3600 ? (upSec / 3600) + "h" + (upSec % 3600) / 60 + "m"
                                       : (upSec / 60) + "m" + (upSec % 60) + "s";

        int line = sY + 22;
        g.drawString(font, "§7TPS : " + tpsCol + serverStats[0] + " §8(" + serverStats[1] + " ms/tick)", lx, line, 0xFFAAAAAA);
        line += 12;
        // RAM : texte + barre de progression
        g.drawString(font, "§7RAM : §f" + ramU + " §7/ " + ramM + " Mo", lx, line, 0xFFAAAAAA);
        int barX = lx + 130, barW = px + pw - 40 - barX;
        if (barW > 30) {
            float frac = Math.min(1f, (float) ramU / Math.max(1, ramM));
            int fillCol = frac < 0.7f ? 0xFF00CC44 : frac < 0.9f ? 0xFFFFAA00 : 0xFFFF4444;
            g.fill(barX, line + 1, barX + barW, line + 7, 0x33FFFFFF);
            g.fill(barX, line + 1, barX + (int)(barW * frac), line + 7, fillCol);
        }
        line += 12;
        g.drawString(font, "§7Entités : §f" + serverStats[4] + "   §7Chunks : §f" + serverStats[5]
            + "   §7Uptime : §f" + uptime, lx, line, 0xFFAAAAAA);
    }

    private void renderJoueurs(GuiGraphics g) {
        int divX = cx + 98;
        g.fill(divX, py + 26, divX + 1, py + ph - 5, C_DIV);

        // Têtes de skin à gauche de chaque entrée de la liste
        java.util.List<PlayerInfo> shown = filteredPlayers();
        {
            int yOff = py + 48;
            for (PlayerInfo info : shown) {
                net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, info.getSkin(), cx + 5, yOff + 3, 8);
                yOff += 16;
            }
        }

        // Section BANNIS dans le panneau gauche
        if (!bannedPlayers.isEmpty()) {
            int searchCount = shown.size();
            int banY = py + 48 + searchCount * 16 + 8;
            g.drawString(font, "BANNIS", cx + 6, banY - 2, 0xFF888888);
            g.fill(cx + 4, banY + 6, cx + 94, banY + 7, C_DIV);
            for (int i = 0; i < bannedPlayers.size(); i++) {
                String bname  = bannedPlayers.get(i)[0];
                String reason = bannedPlayers.get(i)[1];
                int by = banY + 10 + i * 18;
                g.fill(cx + 4, by, cx + 94, by + 14, 0x22FF4444);
                g.drawString(font, "§c" + truncate(bname, 9), cx + 6, by + 2, 0xFFFF6666);
                if (!reason.isEmpty())
                    g.drawString(font, "§8" + truncate(reason, 9), cx + 6, by + 10, 0xFF555555);
            }
        }

        // Section HORS LIGNE (en-tête + dernière connexion en sous-texte des boutons)
        if (!offlinePlayers.isEmpty()) {
            int offTop = offlineSectionTop();
            g.drawString(font, "HORS LIGNE", cx + 6, offTop, 0xFF888888);
            g.fill(cx + 4, offTop + 8, cx + 94, offTop + 9, C_DIV);
        }

        if (selPlayer == null) {
            g.drawCenteredString(font, "§8← Sélectionnez", cx + 49, py + ph / 2, 0xFF555555);
            return;
        }

        g.fill(divX + 1, py + 26, px + pw, py + 46, 0xFF0A0A0A);
        PlayerInfo selInfo = shown.stream()
            .filter(i -> i.getProfile().getName().equals(selPlayer)).findFirst().orElse(null);
        int nameX = divX + 8;
        if (selInfo != null) {
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, selInfo.getSkin(), divX + 6, py + 30, 12);
            nameX = divX + 22;
        }
        g.drawString(font, "§e§l" + selPlayer, nameX, py + 31, 0xFFFFFFFF);
        int gmX = nameX + 2 + font.width("§e§l" + selPlayer);
        g.drawString(font, " §8[" + selGamemode + "]", gmX, py + 31, 0xFFFFFFFF);
        g.fill(divX + 1, py + 45, px + pw, py + 46, C_DIV);

        if (!selOffline) {
            // Mirror the adaptive column layout from buildPlayerActions so the labels stay above their columns.
            int areaW  = px + pw - divX - 6;
            int gap    = 8;
            int bw     = Math.max(60, Math.min(100, (areaW - gap) / 2));
            int totalW = bw * 2 + gap;
            int lCol   = divX + 2 + (areaW - totalW) / 2;
            int rCol   = lCol + bw + gap;
            lbl(g, "ACTIONS",    lCol, py + 53);
            lbl(g, "MODÉRATION", rCol, py + 53);
        }

        renderActivityCard(g, divX);
    }

    /** Fiche Activité du joueur sélectionné : dernière connexion, sanctions, reports, note admin. */
    private void renderActivityCard(GuiGraphics g, int divX) {
        int top = py + ph - 58;
        g.fill(divX + 1, top - 2, px + pw, top - 1, C_DIV);
        g.fill(divX + 1, top - 1, px + pw, py + ph - 3, 0x0DFFFFFF);
        lbl(g, "ACTIVITÉ", divX + 8, top + 2);

        String seen = "§aen ligne";
        if (selOffline) {
            seen = "§7?";
            for (String[] off : offlinePlayers)
                if (off[0].equals(selPlayer)) {
                    try { seen = "§7il y a " + timeAgo(Long.parseLong(off[1])); } catch (NumberFormatException ignored) {}
                    break;
                }
        }

        long sanctionCount = sanctionsEntries.stream().filter(e -> e[2].equalsIgnoreCase(selPlayer)).count();
        String lastSanction = sanctionsEntries.stream().filter(e -> e[2].equalsIgnoreCase(selPlayer))
            .findFirst().map(e -> e[1] + " " + e[0]).orElse(null); // entries arrivent du plus récent au plus ancien
        long reportCount = java.util.stream.Stream.of(reports, acceptedReports, closedReports)
            .flatMap(m -> m.keySet().stream()).filter(n -> n.equalsIgnoreCase(selPlayer)).count();

        g.drawString(font, "§7Vu : " + seen, divX + 8, top + 13, 0xFFAAAAAA);
        String sancTxt = sanctionsEntries.isEmpty() && sanctionCount == 0
            ? "§7Sanctions : §8—"
            : "§7Sanctions : " + (sanctionCount == 0 ? "§a0" : "§c" + sanctionCount
                + (lastSanction != null ? " §8(dernier : " + lastSanction + ")" : ""));
        g.drawString(font, sancTxt, divX + 8, top + 24, 0xFFAAAAAA);
        g.drawString(font, "§7Reports déposés : " + (reportCount == 0 ? "§80" : "§e" + reportCount),
            divX + 8, top + 35, 0xFFAAAAAA);
        g.drawString(font, "§7Note :", divX + 8, py + ph - 14, 0xFFAAAAAA);
    }

    private void renderChat(GuiGraphics g) {
        int contentX = cx + 10;
        int contentW = px + pw - cx - 20;
        int aY       = py + 50;

        // ── Section ANNONCE ──────────────────────────────────────────────────────
        lbl(g, "ANNONCE GLOBALE", contentX, aY - 12);

        // Preview box under the buttons
        String preview = announceBox != null ? announceBox.getValue() : "";
        int previewY = aY + 70;
        g.fill(contentX, previewY, contentX + contentW, previewY + 14, 0x22FFFFFF);
        g.fill(contentX, previewY, contentX + 2, previewY + 14, C_ACCENT);
        g.drawString(font,
            preview.isEmpty() ? "§8Aperçu — tapez votre annonce…" : "§6§l[ANNONCE] §r§f" + preview,
            contentX + 6, previewY + 3, 0xFFFFFFFF);
        g.drawString(font, "§8MOTD connexion : " + (motd.isEmpty() ? "§8aucun" : "§7" + truncate(motd, 40)),
            contentX, previewY + 17, 0xFF555555);

        // ── Divider ──────────────────────────────────────────────────────────────
        int divY = previewY + 29;
        g.fill(contentX, divY, contentX + contentW, divY + 1, C_DIV);

        // ── Section BROADCASTS ───────────────────────────────────────────────────
        int bY    = py + 168;
        int listY = bY + 26;
        lbl(g, "BROADCASTS PROGRAMMÉS", contentX, divY + 6);
        g.drawString(font, "§8min", contentX + contentW - 68, bY + 3, 0xFF444444);

        if (schedBroadcasts.isEmpty()) {
            g.drawString(font, "§8Aucun broadcast programmé", contentX + 2, listY + 2, 0xFF444444);
        } else {
            for (int i = 0; i < schedBroadcasts.size(); i++) {
                String[] entry = schedBroadcasts.get(i);
                boolean confirming = deletingBroadcastIdx == i;
                g.fill(cx + 8, listY + i * 20 - 2, px + pw - 8, listY + i * 20 + 12,
                       confirming ? 0x33FF4444 : 0x11FFFFFF);
                if (confirming) {
                    g.drawString(font, "§cSupprimer ? §7" + truncate(entry[0], 20) + " §8(" + entry[1] + "min)",
                                 cx + 12, listY + i * 20, 0xFFFFFFFF);
                } else {
                    g.drawString(font, "§6[Annonce] §f" + truncate(entry[0], 30) + " §8— §e" + entry[1] + "min",
                                 cx + 12, listY + i * 20, 0xFFFFFFFF);
                }
            }
        }
    }

    private void renderFeatures(GuiGraphics g) {
        int contentW = pw - SIDE_W;
        int bx  = cx + 8;
        int bw  = contentW - 16;
        int hw  = (bw - 4) / 2;
        int boxW = 40, boxGap = (bw - 4 * boxW) / 3;
        int fy  = py + 34;
        int cy  = fy + 122;

        // Feature state tints (behind buttons)
        boolean[][] states = {
            { cropTrampleEnabled,       afkAutoEnabled          },
            { proportionalSleepEnabled, treeCapitatorEnabled    },
            { fastLeafDecayEnabled,     doubleDoorEnabled       },
            { rightClickHarvestEnabled, dispenserHarvestEnabled }
        };
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int bxc = bx + col * (hw + 4);
                int byc = fy + row * 24;
                boolean on = states[row][col];
                g.fill(bxc, byc, bxc + hw, byc + 20, on ? 0x5500CC44 : 0x55CC3300);
                g.fill(bxc, byc, bxc + 3, byc + 20, on ? 0xFF00CC44 : 0xFFAA3300);
            }
        }

        // Section divider
        g.fill(bx, fy + 100, bx + bw, fy + 101, C_DIV);
        lbl(g, "COOLDOWNS (s) / AFK (min)", bx, fy + 104);

        // Box labels
        int[] xs = { bx, bx + boxW + boxGap, bx + (boxW + boxGap) * 2, bx + (boxW + boxGap) * 3 };
        String[] fLbls = { "/home(s)", "/back(s)", "/tpa(s)", "afk(min)" };
        for (int i = 0; i < 4; i++)
            g.drawString(font, fLbls[i], xs[i], cy - 9, 0xFF666666);

        // Max Homes section
        int hmY = cy + 50;
        g.fill(bx, hmY - 10, bx + bw, hmY - 9, C_DIV);
        lbl(g, "MAX HOMES PAR JOUEUR  (1-10)", bx, hmY - 7);

        // Discord Webhooks section — labels vertically centred on their boxes (left column)
        int whY = hmY + 36;
        g.fill(bx, whY - 12, bx + bw, whY - 11, C_DIV);
        lbl(g, "DISCORD WEBHOOKS", bx, whY - 8);
        g.drawString(font, "§8Reports",   bx, whY + 4,  0xFF666666);
        g.drawString(font, "§8Sanctions", bx, whY + 26, 0xFF666666);
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
                String rawMsg = e.getValue();
                boolean hasImg = rawMsg.length() > 0 && rawMsg.charAt(0) == '';
                String msg = truncate(hasImg ? rawMsg.substring(1) : rawMsg, 22);
                g.drawString(font, "§e" + e.getKey(), lx1 + 8, y + 3,  0xFFFFFFFF);
                g.drawString(font, "§7» " + msg,      lx1 + 8, y + 15, 0xFFAAAAAA);
                if (hasImg) g.drawString(font, "§b[capture jointe]", lx1 + 8, y + 27, 0xFF4499FF);
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
                String rawMsg = e.getValue();
                boolean hasImg = rawMsg.length() > 0 && rawMsg.charAt(0) == '';
                String msg = truncate(hasImg ? rawMsg.substring(1) : rawMsg, 22);
                g.drawString(font, "§e" + e.getKey(), lx2 + 8, y + 3,  0xFFFFFFFF);
                g.drawString(font, "§7» " + msg,      lx2 + 8, y + 15, 0xFFAAAAAA);
                if (hasImg) g.drawString(font, "§b📷", lx2 + 8, y + 27, 0xFFFFFFFF);
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

    // ─── LOGS ────────────────────────────────────────────────────────────────────

    private void buildLogs() {
        if (Minecraft.getInstance().getConnection() == null) return;
        java.util.Collection<net.minecraft.client.multiplayer.PlayerInfo> players =
            Minecraft.getInstance().getConnection().getOnlinePlayers();

        // Historique du chat public du serveur (réutilise le visualiseur de logs)
        addRenderableWidget(btn("Chat global".equals(selPlayer) ? "§b§lCHAT GLOBAL" : "§bCHAT GLOBAL", b -> {
            selPlayer   = "Chat global";
            logsPlayer  = null;
            logsEntries = new java.util.ArrayList<>();
            logsScroll  = 0;
            send("GET_CHAT", "", "");
            init();
        }).bounds(cx + 4, py + 28, 90, 14).build());

        int yOff = py + 48;
        for (net.minecraft.client.multiplayer.PlayerInfo info : players) {
            if (info.getProfile() == null) continue;
            String name = info.getProfile().getName();
            boolean sel = name.equals(selPlayer);
            Component lbl = Component.literal((sel ? "§f§l" : "§7") + name);
            addRenderableWidget(Button.builder(lbl, b -> {
                selPlayer   = name;
                logsPlayer  = null;
                logsEntries = new java.util.ArrayList<>();
                logsScroll  = 0;
                send("GET_LOGS", name, "");
                init();
            }).bounds(cx + 16, yOff, 78, 14).build());
            yOff += 16;
        }
    }

    private void renderLogs(GuiGraphics g) {
        int divX = cx + 98;
        g.fill(divX, py + 26, divX + 1, py + ph - 5, C_DIV);

        // Têtes de skin dans la liste de gauche
        if (Minecraft.getInstance().getConnection() != null) {
            int yOff = py + 48;
            for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
                if (info.getProfile() == null) continue;
                net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, info.getSkin(), cx + 5, yOff + 3, 8);
                yOff += 16;
            }
        }

        if (selPlayer == null) {
            g.drawCenteredString(font, "§8← Sélectionnez un joueur", (divX + px + pw) / 2, py + ph / 2, 0xFF555555);
            return;
        }

        // Header
        g.fill(divX + 1, py + 26, px + pw, py + 46, 0xFF0A0A0A);
        g.drawString(font, "§e§l" + selPlayer + " §8— Logs", divX + 8, py + 31, 0xFFFFFFFF);
        g.fill(divX + 1, py + 45, px + pw, py + 46, C_DIV);

        if (logsPlayer == null) {
            g.drawCenteredString(font, "§8Chargement des logs...", (divX + px + pw) / 2, py + ph / 2, 0xFF666666);
            return;
        }

        if (logsEntries.isEmpty()) {
            g.drawCenteredString(font, "§8Aucun log", (divX + px + pw) / 2, py + ph / 2, 0xFF444444);
            return;
        }

        int entryH    = 11;
        int panelTop  = py + 48;
        int panelBot  = py + ph - 6;
        int visH      = panelBot - panelTop;
        int maxVis    = Math.max(1, visH / entryH);
        int maxScroll = Math.max(0, logsEntries.size() - maxVis);
        if (logsScroll > maxScroll) logsScroll = maxScroll;
        if (logsScroll < 0)         logsScroll = 0;

        int sbX = px + pw - 6;

        g.enableScissor(divX + 2, panelTop, sbX - 2, panelBot);
        int y = panelTop;
        for (int i = logsScroll; i < logsEntries.size() && i < logsScroll + maxVis; i++) {
            if (i % 2 == 0) g.fill(divX + 2, y - 1, sbX - 2, y + entryH, 0x0AFFFFFF);
            g.drawString(font, "§7" + logsEntries.get(i), divX + 6, y + 1, 0xFFAAAAAA);
            y += entryH;
        }
        g.disableScissor();

        // Scrollbar
        if (logsEntries.size() > maxVis) {
            int sbH    = panelBot - panelTop;
            int thumbH = Math.max(8, sbH * maxVis / logsEntries.size());
            int thumbY = maxScroll > 0 ? panelTop + (sbH - thumbH) * logsScroll / maxScroll : panelTop;
            g.fill(sbX, panelTop, sbX + 4, panelBot, 0x33FFFFFF);
            g.fill(sbX, thumbY,   sbX + 4, thumbY + thumbH, C_ACCENT);
        }
    }

    private void buildSanctions() {
        if (sanctionsEntries.isEmpty()) return;
        int panelTop = py + 28;
        int panelBot = py + ph - 6;
        int entryH   = 18;
        int maxVis   = Math.max(1, (panelBot - panelTop) / entryH);

        int y = panelTop;
        for (int i = sanctionsScroll; i < sanctionsEntries.size() && i < sanctionsScroll + maxVis; i++) {
            String[] e = sanctionsEntries.get(i);
            if ("BAN".equals(e[1])) {
                final String playerName = e[2];
                boolean isBanned = bannedPlayers.stream().anyMatch(b -> b[0].equalsIgnoreCase(playerName));
                if (isBanned) {
                    addRenderableWidget(btn("§aDÉBAN", b -> {
                        confirmUnbanPlayer = playerName;
                        init();
                    }).bounds(px + pw - 62, y + 2, 54, 14).build());
                }
            }
            y += entryH;
        }
    }

    private void renderSanctions(GuiGraphics g) {
        int panelTop = py + 28;
        int panelBot = py + ph - 6;
        int sbX      = px + pw - 6;
        int entryH   = 18;

        if (sanctionsEntries.isEmpty()) {
            g.drawCenteredString(font, "§8Aucune sanction enregistrée", midX, py + ph / 2, 0xFF444444);
            return;
        }

        int maxVis    = Math.max(1, (panelBot - panelTop) / entryH);
        int maxScroll = Math.max(0, sanctionsEntries.size() - maxVis);
        if (sanctionsScroll > maxScroll) sanctionsScroll = maxScroll;
        if (sanctionsScroll < 0)         sanctionsScroll = 0;

        g.enableScissor(cx, panelTop, sbX - 2, panelBot);
        int y = panelTop;
        for (int i = sanctionsScroll; i < sanctionsEntries.size() && i < sanctionsScroll + maxVis; i++) {
            String[] e = sanctionsEntries.get(i);
            if (i % 2 == 0) g.fill(cx + 4, y, sbX - 4, y + entryH - 2, C_ROW);
            int typeColor = switch (e[1]) {
                case "BAN"  -> 0xFFFF5555;
                case "KICK" -> 0xFFFFAA00;
                case "MUTE" -> 0xFFFFFF55;
                case "UNBAN"-> 0xFF55FF55;
                default     -> 0xFFAAAAAA;
            };
            g.fill(cx + 4, y, cx + 5, y + entryH - 2, typeColor);
            String line = "§8" + e[0] + " §r" + e[2] + " §8— " + e[1] + " §8| §7" + e[3]
                + (e[4].equals("—") ? "" : " §8| §7" + truncate(e[4], 20));
            g.drawString(font, line, cx + 10, y + 4, typeColor, false);
            y += entryH;
        }
        g.disableScissor();

        if (sanctionsEntries.size() > maxVis) {
            int sbH    = panelBot - panelTop;
            int thumbH = Math.max(8, sbH * maxVis / sanctionsEntries.size());
            int thumbY = maxScroll > 0 ? panelTop + (sbH - thumbH) * sanctionsScroll / maxScroll : panelTop;
            g.fill(sbX, panelTop, sbX + 4, panelBot, 0x33FFFFFF);
            g.fill(sbX, thumbY,   sbX + 4, thumbY + thumbH, C_ACCENT);
        }
    }

    public void onSanctionsReceived(String data) {
        sanctionsEntries.clear();
        if (!data.isEmpty()) {
            for (String line : data.split("\\|")) {
                String[] parts = line.split("\t", 5);
                if (parts.length == 5) sanctionsEntries.add(parts);
            }
        }
        sanctionsScroll = 0;
        init();
    }

    public void onLogsReceived(String playerName, String logsSerialized) {
        this.logsPlayer  = playerName;
        this.logsEntries = logsSerialized.isEmpty()
            ? new java.util.ArrayList<>()
            : new java.util.ArrayList<>(java.util.Arrays.asList(logsSerialized.split("\n")));
        this.logsScroll  = 0;
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showNotesList) {
            notesScroll = Math.max(0, notesScroll - (int) scrollY);
            return true;
        }
        if (currentTab == 5 && !logsEntries.isEmpty()) {
            logsScroll -= (int)(scrollY * 3);
            if (logsScroll < 0) logsScroll = 0;
            return true;
        }
        if (currentTab == 7 && !sanctionsEntries.isEmpty()) {
            int maxVis = Math.max(1, (py + ph - 6 - (py + 28)) / 18);
            int maxScroll = Math.max(0, sanctionsEntries.size() - maxVis);
            sanctionsScroll = Math.max(0, Math.min(sanctionsScroll - (int)(scrollY * 3), maxScroll));
            init();
            return true;
        }
        if (currentTab == 6 && mouseX >= cx && mouseX < cx + ZONE_LIST_W) {
            int maxVis   = Math.max(1, (py + ph - 28 - (py + 30)) / 20);
            int maxScroll = Math.max(0, zoneMap.size() - maxVis);
            zoneListScroll = Math.max(0, Math.min(zoneListScroll - (int) scrollY, maxScroll));
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private void lbl(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x, y, 0xFF888888);
    }

    /** Tête de skin d'un joueur en ligne (rien si hors ligne / inconnu). */
    private static void drawFace(GuiGraphics g, String playerName, int x, int y, int size) {
        if (Minecraft.getInstance().getConnection() == null) return;
        PlayerInfo pi = Minecraft.getInstance().getConnection().getPlayerInfo(playerName);
        if (pi != null) net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, pi.getSkin(), x, y, size);
    }

    // ─── WARPS ───────────────────────────────────────────────────────────────────

    private void buildWarps() {
        int lx = cx + 8;
        int y = py + 48;
        int maxBottom = py + ph - 36;
        for (String[] w : warpsList) {
            if (y + 18 > maxBottom) break;
            final String wn = w[0];
            addRenderableWidget(btn("§bTP", b -> { send("WARP_TP", "", wn); onClose(); })
                .bounds(px + pw - 66, y, 28, 14).build());
            addRenderableWidget(btn("§c✕", b -> send("WARP_DELETE", "", wn))
                .bounds(px + pw - 34, y, 18, 14).build());
            y += 20;
        }
        // Création d'un warp à la position actuelle de l'admin
        warpNameBox = new EditBox(font, lx, py + ph - 28, 120, 18, Component.literal("nom du warp"));
        warpNameBox.setMaxLength(24);
        addRenderableWidget(warpNameBox);
        addRenderableWidget(btn("§aCRÉER ICI", b -> {
            String n = warpNameBox.getValue().trim();
            if (!n.isEmpty()) { send("WARP_ADD", "", n); warpNameBox.setValue(""); }
        }).bounds(lx + 126, py + ph - 28, 90, 18).build());
    }

    private void renderWarps(GuiGraphics g) {
        int lx = cx + 8;
        lbl(g, "WARPS PUBLICS" + (warpsList.isEmpty() ? "" : " (" + warpsList.size() + ")"), lx, py + 32);
        g.fill(lx, py + 42, px + pw - 12, py + 43, C_DIV);

        if (warpsList.isEmpty()) {
            g.drawCenteredString(font, "§8Aucun warp défini", midX, py + ph / 2 - 6, 0xFF444444);
            g.drawCenteredString(font, "§8Créez-en un à votre position ci-dessous", midX, py + ph / 2 + 6, 0xFF333333);
        }

        int y = py + 48;
        int maxBottom = py + ph - 36;
        int shown = 0;
        for (String[] w : warpsList) {
            if (y + 18 > maxBottom) {
                g.drawString(font, "§8+" + (warpsList.size() - shown) + "…", lx, y, 0xFF444444);
                break;
            }
            if (shown % 2 == 0) g.fill(cx + 4, y - 2, px + pw - 4, y + 14, C_ROW);
            g.drawString(font, "§b◈ §f" + w[0], lx, y + 2, 0xFFFFFFFF);
            g.drawString(font, "§8" + w[1] + "  §7[" + w[2] + "]",
                lx + 8 + font.width("§b◈ §f" + w[0]), y + 2, 0xFF777777);
            y += 20;
            shown++;
        }

        g.fill(lx, py + ph - 36, px + pw - 12, py + ph - 35, C_DIV);
        g.drawString(font, "§8Le warp est créé à votre position actuelle.", lx + 222, py + ph - 23, 0xFF444444);
    }

    // ─── ZONES ───────────────────────────────────────────────────────────────────

    public void onZoneUpdate(OpenZonePayload payload) {
        zoneMap.clear();
        zoneOnline.clear();
        if (!payload.zonesSerialized().isEmpty()) {
            for (String line : payload.zonesSerialized().split("\n")) {
                String[] f = line.split("\\|", -1);
                if (f.length < 5) continue;
                try {
                    String[] mn = f[1].split(","), mx = f[2].split(",");
                    java.util.List<String[]> members = new java.util.ArrayList<>();
                    if (!f[3].isEmpty())
                        for (String m : f[3].split(",")) {
                            String[] um = m.split(":", 2);
                            if (um.length == 2) members.add(um);
                        }
                    java.util.EnumMap<Fabric.test.ZoneFlag, Boolean> flags = new java.util.EnumMap<>(Fabric.test.ZoneFlag.class);
                    if (f.length > 6 && !f[6].isEmpty())
                        for (String pair : f[6].split(";")) {
                            String[] kv = pair.split(":");
                            if (kv.length == 2) {
                                Fabric.test.ZoneFlag fl = Fabric.test.ZoneFlag.byName(kv[0]);
                                if (fl != null) flags.put(fl, kv[1].equals("1"));
                            }
                        }
                    boolean enabled = f.length <= 7 || !"false".equals(f[7]); // défaut : activée
                    int colorIdx    = f.length > 8 ? Integer.parseInt(f[8]) : 0;
                    int priority    = f.length > 9 ? Integer.parseInt(f[9]) : 0;
                    String greeting = f.length > 10 ? f[10] : "";
                    String farewell = f.length > 11 ? f[11] : "";
                    java.util.List<String> inside = new java.util.ArrayList<>();
                    if (f.length > 12 && !f[12].isEmpty()) java.util.Collections.addAll(inside, f[12].split(","));
                    zoneMap.put(f[0], new ZoneData(
                        Integer.parseInt(mn[0]), Integer.parseInt(mn[1]), Integer.parseInt(mn[2]),
                        Integer.parseInt(mx[0]), Integer.parseInt(mx[1]), Integer.parseInt(mx[2]),
                        members, Boolean.parseBoolean(f[4]), flags, enabled,
                        colorIdx, priority, greeting, farewell, inside));
                } catch (Exception ignored) {}
            }
        }
        if (!payload.onlinePlayers().isEmpty())
            for (String p : payload.onlinePlayers().split(";")) zoneOnline.add(p);
        if (selectedZone != null && !zoneMap.containsKey(selectedZone)) selectedZone = null;
        if (currentTab == 6) init();
    }

    private void sendZone(String action, String zoneName, String value) {
        PacketDistributor.sendToServer(new ZoneActionPayload(action, zoneName, value));
    }

    private void buildZones() {
        int znDetX = cx + ZONE_LIST_W + 1;
        int znDetW = (px + pw) - znDetX;

        // Baguette button at bottom of list panel
        addRenderableWidget(btn("§6✦ Baguette", b -> sendZone("GIVE_TOOL", "", ""))
            .bounds(cx + 2, py + ph - 24, ZONE_LIST_W - 4, 18).build());

        // Zone list entries
        int topY = py + 30, botY = py + ph - 28, entryH = 20;
        int maxVis = Math.max(1, (botY - topY) / entryH);
        zoneListScroll = Math.max(0, Math.min(zoneListScroll, Math.max(0, zoneMap.size() - maxVis)));
        int y = topY, idx = 0;
        for (String name : zoneMap.keySet()) {
            if (idx >= zoneListScroll && idx < zoneListScroll + maxVis) {
                final String n = name;
                final int zColor = zoneMap.get(name).color();
                Component lbl = Component.literal("■ ").withStyle(s -> s.withColor(zColor))
                    .append(Component.literal((name.equals(selectedZone) ? "§f§l" : "§7") + truncate(name, 9)));
                addRenderableWidget(Button.builder(lbl,
                    b -> { selectedZone = n; zoneDetailTab = 0; init(); })
                    .bounds(cx + 2, y, ZONE_LIST_W - 4, 16).build());
                y += entryH;
            }
            idx++;
        }

        if (selectedZone == null) return;
        ZoneData z = zoneMap.get(selectedZone);
        if (z == null) return;

        // Sub-tabs (sous l'en-tête de 22px : nom + dimensions sans chevauchement)
        int tabW = znDetW / 4;
        String[] tabLabels = { "MEMBRES", "COORDS", "OPTIONS", "MSG" };
        for (int i = 0; i < tabLabels.length; i++) {
            final int ti = i;
            boolean active = zoneDetailTab == i;
            Component lbl = Component.literal(tabLabels[i]).withStyle(
                active ? s -> s.withColor(0x00E5FF).withBold(true) : s -> s.withColor(0x777777));
            addRenderableWidget(Button.builder(lbl, b -> { zoneDetailTab = ti; init(); })
                .bounds(znDetX + tabW * i, py + 50, tabW - 1, 16).build());
        }

        int contentTop = py + 70;
        int contentBot = py + ph - 5;
        switch (zoneDetailTab) {
            case 0 -> buildZoneMembers(z, znDetX, znDetW, contentTop, contentBot);
            case 1 -> buildZoneCoords(z, znDetX, znDetW, contentTop, contentBot);
            case 2 -> buildZoneOptions(z, znDetX, znDetW, contentTop, contentBot);
            case 3 -> buildZoneMessages(z, znDetX, znDetW, contentTop, contentBot);
        }
    }

    private void buildZoneMembers(ZoneData z, int detX, int detW, int top, int bot) {
        int halfW  = detW / 2;
        int rightX = detX + halfW + 2;
        int entryY = top + 14;
        int maxRows = (bot - entryY) / 18;

        for (int i = 0; i < z.members().size() && i < maxRows; i++) {
            final String uuid = z.members().get(i)[0];
            addRenderableWidget(btn("§c✕", b -> sendZone("REMOVE_MEMBER", selectedZone, uuid))
                .bounds(detX + halfW - 20, entryY + i * 18, 16, 14).build());
        }
        int ri = 0;
        for (String playerName : zoneOnline) {
            if (ri >= maxRows) break;
            boolean isMember = z.members().stream().anyMatch(m -> m[1].equalsIgnoreCase(playerName));
            if (!isMember) {
                final String pn = playerName;
                addRenderableWidget(btn("§a+", b -> sendZone("ADD_MEMBER", selectedZone, pn))
                    .bounds(rightX, entryY + ri * 18, 16, 14).build());
            }
            ri++;
        }
    }

    private void buildZoneCoords(ZoneData z, int detX, int detW, int top, int bot) {
        int lx = detX + 8, boxW = 40, gap = 4;
        zMinXBox = zBox(lx,                top + 22, boxW, String.valueOf(z.x1()));
        zMinYBox = zBox(lx + boxW + gap,   top + 22, boxW, String.valueOf(z.y1()));
        zMinZBox = zBox(lx+(boxW+gap)*2,   top + 22, boxW, String.valueOf(z.z1()));
        zMaxXBox = zBox(lx,                top + 58, boxW, String.valueOf(z.x2()));
        zMaxYBox = zBox(lx + boxW + gap,   top + 58, boxW, String.valueOf(z.y2()));
        zMaxZBox = zBox(lx+(boxW+gap)*2,   top + 58, boxW, String.valueOf(z.z2()));
        for (EditBox b : new EditBox[]{zMinXBox, zMinYBox, zMinZBox, zMaxXBox, zMaxYBox, zMaxZBox})
            addRenderableWidget(b);
        addRenderableWidget(btn("§aSAUVEGARDER", b -> {
            try {
                sendZone("UPDATE_COORDS", selectedZone,
                    zMinXBox.getValue() + "," + zMinYBox.getValue() + "," + zMinZBox.getValue() + "," +
                    zMaxXBox.getValue() + "," + zMaxYBox.getValue() + "," + zMaxZBox.getValue());
            } catch (Exception ignored) {}
        }).bounds(detX + detW - 106, top + 80, 98, 18).build());

        // Priorité (zones superposées : la plus haute décide)
        zPriorityBox = zBox(lx, top + 112, 36, String.valueOf(z.priority()));
        zPriorityBox.setMaxLength(3);
        addRenderableWidget(zPriorityBox);
        addRenderableWidget(btn("§aOK", b -> sendZone("SET_PRIORITY", selectedZone, zPriorityBox.getValue()))
            .bounds(lx + 42, top + 112, 30, 16).build());
    }

    private EditBox zBox(int x, int y, int w, String value) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.empty());
        b.setMaxLength(8);
        b.setValue(value);
        return b;
    }

    private void buildZoneOptions(ZoneData z, int detX, int detW, int top, int bot) {
        int w = detW - 8, lx = detX + 4;
        // Row height derived from available height so the list fits at any GUI scale.
        int rows = 6 + Fabric.test.ZoneFlag.values().length;
        int rowH = Math.max(13, Math.min(20, (bot - top) / rows));
        int bh   = Math.max(12, rowH - 2);
        int y = top;

        addRenderableWidget(btn("État de la zone : " + (z.enabled() ? "§aACTIVÉE" : "§cDÉSACTIVÉE"),
            b -> sendZone("TOGGLE_ENABLED", selectedZone, "")).bounds(lx, y, w, bh).build());
        y += rowH;

        addRenderableWidget(btn("Vision nocturne : " + (z.nightVision() ? "§aON" : "§cOFF"),
            b -> sendZone("TOGGLE_NIGHT_VISION", selectedZone, "")).bounds(lx, y, w, bh).build());
        y += rowH;

        int cIdx = Math.floorMod(z.colorIdx(), Fabric.test.Zone.COLORS.length);
        Component colorLbl = Component.literal("Couleur : ")
            .append(Component.literal("■ ").withStyle(s -> s.withColor(z.color())))
            .append(Fabric.test.Zone.COLOR_NAMES[cIdx]);
        addRenderableWidget(Button.builder(colorLbl,
            b -> sendZone("CYCLE_COLOR", selectedZone, "")).bounds(lx, y, w, bh).build());
        y += rowH;

        // Presets : combinaison complète de flags en un clic
        int pw3 = (w - 8) / 3;
        addRenderableWidget(btn("§bSpawn", b -> sendZone("APPLY_PRESET", selectedZone, "SPAWN"))
            .bounds(lx, y, pw3, bh).build());
        addRenderableWidget(btn("§cArène", b -> sendZone("APPLY_PRESET", selectedZone, "ARENA"))
            .bounds(lx + pw3 + 4, y, pw3, bh).build());
        addRenderableWidget(btn("§6VIP", b -> sendZone("APPLY_PRESET", selectedZone, "VIP"))
            .bounds(lx + (pw3 + 4) * 2, y, pw3, bh).build());
        y += rowH;

        for (Fabric.test.ZoneFlag fl : Fabric.test.ZoneFlag.values()) {
            boolean allowed = z.flags().getOrDefault(fl, fl.defaultAllowed);
            addRenderableWidget(btn(fl.label + " : " + (allowed ? "§aautorisé" : "§cbloqué"),
                b -> sendZone("TOGGLE_FLAG", selectedZone, fl.name())).bounds(lx, y, w, bh).build());
            y += rowH;
        }

        addRenderableWidget(btn("§eTéléporter vers la zone",
            b -> sendZone("TP_ZONE", selectedZone, "")).bounds(lx, y, w, bh).build());
        y += rowH;
        addRenderableWidget(btn("§c§lSUPPRIMER LA ZONE",
            b -> { sendZone("DELETE_ZONE", selectedZone, ""); selectedZone = null; init(); })
            .bounds(lx, y, w, bh).build());
    }

    private void buildZoneMessages(ZoneData z, int detX, int detW, int top, int bot) {
        int lx = detX + 8, w = detW - 16;

        zGreetingBox = new EditBox(font, lx, top + 14, w, 16, Component.empty());
        zGreetingBox.setMaxLength(100);
        zGreetingBox.setValue(z.greeting());
        addRenderableWidget(zGreetingBox);

        zFarewellBox = new EditBox(font, lx, top + 52, w, 16, Component.empty());
        zFarewellBox.setMaxLength(100);
        zFarewellBox.setValue(z.farewell());
        addRenderableWidget(zFarewellBox);

        addRenderableWidget(btn("§aSAUVEGARDER", b ->
            sendZone("SET_MESSAGES", selectedZone, zGreetingBox.getValue() + "\t" + zFarewellBox.getValue()))
            .bounds(detX + detW - 106, top + 78, 98, 18).build());
    }

    private void renderZones(GuiGraphics g) {
        int znDetX = cx + ZONE_LIST_W + 1;
        int znDetW = (px + pw) - znDetX;

        // Divider between list and detail
        g.fill(cx + ZONE_LIST_W, py + 26, cx + ZONE_LIST_W + 1, py + ph - 5, C_ACCENT);

        // List header + baguette separator
        g.drawString(font, "ZONES", cx + 4, py + 28, 0xFF888888);
        g.fill(cx + 2, py + ph - 28, cx + ZONE_LIST_W - 2, py + ph - 27, C_DIV);

        if (zoneMap.isEmpty()) {
            g.drawCenteredString(font, "§8Aucune zone", cx + ZONE_LIST_W / 2, py + ph / 2 - 14, 0xFF444444);
            g.drawCenteredString(font, "§8/zone create", cx + ZONE_LIST_W / 2, py + ph / 2 - 2, 0xFF333333);
        }

        // Selected zone highlight in list
        if (selectedZone != null) {
            int topY = py + 30, entryH = 20, vi = 0;
            int lIdx = 0;
            for (String name : zoneMap.keySet()) {
                if (lIdx >= zoneListScroll) {
                    if (name.equals(selectedZone)) {
                        g.fill(cx + 1, topY + vi * entryH, cx + ZONE_LIST_W - 1, topY + vi * entryH + 18, C_TABSEL);
                        g.fill(cx + 1, topY + vi * entryH, cx + 3, topY + vi * entryH + 18, C_ACCENT);
                    }
                    vi++;
                }
                lIdx++;
            }
        }

        if (selectedZone == null) {
            if (!zoneMap.isEmpty())
                g.drawCenteredString(font, "§8Sélectionnez une zone", znDetX + znDetW / 2, py + ph / 2, 0xFF444444);
            return;
        }
        ZoneData z = zoneMap.get(selectedZone);
        if (z == null) return;

        // Zone info bar (22px : la ligne des dimensions ne touche plus les sous-onglets)
        g.fill(znDetX, py + 26, px + pw, py + 48, 0xFF0A0A0A);
        int sx = z.x2()-z.x1()+1, sy = z.y2()-z.y1()+1, sz = z.z2()-z.z1()+1;
        g.drawString(font, "§e§l" + selectedZone, znDetX + 4, py + 28, 0xFFFFFFFF);
        if (!z.inside().isEmpty())
            g.drawString(font, "§a◉ " + z.inside().size() + " présent" + (z.inside().size() > 1 ? "s" : ""),
                znDetX + 10 + font.width("§e§l" + selectedZone), py + 28, 0xFF55FF55);
        g.drawString(font, "§8" + sx + "×" + sy + "×" + sz
            + "  (" + z.x1() + "," + z.y1() + "," + z.z1()
            + ") → (" + z.x2() + "," + z.y2() + "," + z.z2() + ")",
            znDetX + 4, py + 38, 0xFF555555);
        g.fill(znDetX, py + 47, px + pw, py + 48, C_DIV);

        // Sub-tab highlight
        int tabW = znDetW / 4;
        int tx = znDetX + tabW * zoneDetailTab;
        g.fill(tx, py + 48, tx + tabW - 1, py + 66, C_TABSEL);
        g.fill(tx, py + 64, tx + tabW - 1, py + 66, C_ACCENT);
        g.fill(znDetX, py + 66, px + pw, py + 67, C_DIV);

        int contentTop = py + 70;
        int contentBot = py + ph - 5;
        if (zoneDetailTab == 0) renderZoneMembers(g, z, znDetX, znDetW, contentTop, contentBot);
        else if (zoneDetailTab == 1) renderZoneCoords(g, z, znDetX, znDetW, contentTop, contentBot);
        else if (zoneDetailTab == 3) renderZoneMessages(g, z, znDetX, znDetW, contentTop, contentBot);
    }

    private void renderZoneMessages(GuiGraphics g, ZoneData z, int detX, int detW, int top, int bot) {
        int lx = detX + 8;
        g.drawString(font, "§7MESSAGE D'ENTRÉE", lx, top + 4, 0xFF888888);
        g.drawString(font, "§7MESSAGE DE SORTIE", lx, top + 42, 0xFF888888);
        g.drawString(font, "§8Affiché en action-bar. Vide = aucun message.", lx, top + 102, 0xFF444444);
    }

    private void renderZoneMembers(GuiGraphics g, ZoneData z, int detX, int detW, int top, int bot) {
        int halfW  = detW / 2;
        int rightX = detX + halfW + 2;
        int entryY = top + 14;
        int maxRows = (bot - entryY) / 18;

        g.fill(detX + halfW, top, detX + halfW + 1, bot, C_DIV);
        g.fill(detX, top + 12, px + pw, top + 13, C_DIV);
        g.drawString(font, "MEMBRES §7(" + z.members().size() + ")", detX + 4, top + 2, 0xFF888888);
        g.drawString(font, "JOUEURS EN LIGNE", rightX + 4, top + 2, 0xFF888888);

        if (z.members().isEmpty()) {
            g.drawString(font, "§8Ouvert — tous autorisés", detX + 6, entryY + 3, 0xFF3A3A3A);
        } else {
            for (int i = 0; i < z.members().size() && i < maxRows; i++) {
                int ry = entryY + i * 18;
                g.fill(detX + 2, ry - 1, detX + halfW - 2, ry + 13, C_ROW);
                g.drawString(font, "§a● §f" + z.members().get(i)[1], detX + 6, ry + 1, 0xFFFFFFFF);
            }
        }
        if (zoneOnline.isEmpty()) {
            g.drawString(font, "§8Aucun joueur en ligne", rightX + 4, entryY + 3, 0xFF3A3A3A);
        } else {
            int ri = 0;
            for (String playerName : zoneOnline) {
                if (ri >= maxRows) break;
                boolean isMember = z.members().stream().anyMatch(m -> m[1].equalsIgnoreCase(playerName));
                int ry = entryY + ri * 18;
                g.fill(rightX + 20, ry - 1, px + pw - 2, ry + 13, C_ROW);
                drawFace(g, playerName, rightX + 24, ry + 2, 8);
                String insideDot = z.inside().contains(playerName) ? " §a◉" : "";
                g.drawString(font, (isMember ? "§8■ §7" + playerName : "§f" + playerName) + insideDot,
                    rightX + 35, ry + 1, 0xFFFFFFFF);
                ri++;
            }
        }
    }

    private void renderZoneCoords(GuiGraphics g, ZoneData z, int detX, int detW, int top, int bot) {
        int lx = detX + 8, boxW = 40, gap = 4;
        g.drawString(font, "§7POINT MIN §8(A)", lx, top + 4,  0xFF888888);
        g.drawString(font, "§8X", lx,               top + 12, 0xFF555555);
        g.drawString(font, "§8Y", lx + boxW + gap,  top + 12, 0xFF555555);
        g.drawString(font, "§8Z", lx+(boxW+gap)*2,  top + 12, 0xFF555555);
        g.drawString(font, "§7POINT MAX §8(B)", lx, top + 42, 0xFF888888);
        g.drawString(font, "§8X", lx,               top + 50, 0xFF555555);
        g.drawString(font, "§8Y", lx + boxW + gap,  top + 50, 0xFF555555);
        g.drawString(font, "§8Z", lx+(boxW+gap)*2,  top + 50, 0xFF555555);

        g.drawString(font, "§7PRIORITÉ §8(la plus haute décide)", lx, top + 101, 0xFF888888);
    }

    @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float delta) {}
    @Override public boolean isPauseScreen() { return false; }
}

