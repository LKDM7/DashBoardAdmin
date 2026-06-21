package com.lkdm.dashboardadmin.client;

import com.lkdm.dashboardadmin.networking.GroupActionPayload;
import com.lkdm.dashboardadmin.networking.OpenGroupPayload;
import com.lkdm.dashboardadmin.networking.OpenSettingsPayload;
import net.minecraft.client.gui.components.EditBox;
import com.lkdm.dashboardadmin.networking.PlayerActionPayload;
import com.lkdm.dashboardadmin.networking.UpdateSettingsPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

public class SettingsScreen extends Screen {
    private static final int C_BG     = 0xF01A1A1A;
    private static final int C_SIDE   = 0xFF0C0C0C;
    private static final int C_HBAR   = 0xFF111111;
    private static final int C_ACCENT = 0xFF00E5FF;
    private static final int C_ROW    = 0x22FFFFFF;
    private static final int C_TABSEL = 0x1A00AAFF;
    private static final int C_DIV    = 0x33FFFFFF;
    private static final int C_SEC    = 0xFF888888;

    private static final int SIDE_W = 100;
    private static final int ROW_H  = 38;

    // Layout (computed in init)
    private int px, py, pw, ph, cx, midX, midY;

    // Tab 0 — Paramètres
    private boolean allowPrivateMessages;
    private boolean allowTpaRequests;
    private boolean allowTrades;
    private boolean showChatNotifications;
    private boolean showConnectionAlerts;

    private static String[] labels() {
        return Lang.fr()
            ? new String[]{ "Messages privés", "Demandes de TP", "Échanges joueurs", "Notifications chat", "Alertes connexion" }
            : new String[]{ "Private messages", "TP requests", "Player trades", "Chat notifications", "Connection alerts" };
    }
    private static String[] descs() {
        return Lang.fr()
            ? new String[]{ "Recevoir les messages via /mail", "Recevoir les demandes /tpa", "Recevoir les demandes d'échange", "Notification lors d'un message reçu", "Voir les connexions / déconnexions" }
            : new String[]{ "Receive messages via /mail", "Receive /tpa requests", "Receive trade requests", "Notify when a message is received", "See joins / leaves" };
    }

    // Tab 1 — Commandes
    private final List<String[]> commandList = new ArrayList<>();
    private int cmdScroll = 0;

    // Tab 2 — Homes
    private final Map<String, int[]>  homeMap    = new LinkedHashMap<>();
    private final List<String[]>      warpList   = new ArrayList<>(); // [nom, "x, y, z", dim]
    private final Map<String, String> homeDimMap = new LinkedHashMap<>();

    // Tab 3 — Verrous & Confiance
    private final List<String>   lockList  = new ArrayList<>();
    private final List<String[]> trustList = new ArrayList<>();
    private int verrouScroll = 0;

    // Tab 4 — Groupe
    private final List<String[]> groupMembers  = new ArrayList<>();
    private final List<String>   onlinePlayers = new ArrayList<>();
    private String  pendingInviteFrom = "";
    private int     myColorIdx        = 0;
    private boolean showNames         = true;
    private boolean groupTrustEnabled = false;
    private int     groupScroll       = 0;
    private String  groupName         = "";
    private EditBox groupNameBox;

    // Tab 5 — Stats
    private long statHours, statMinutes, statBlocks;
    private int  statDeaths, statPlayerKills, statMobKills, statXp;

    // Tab 6 — Build (info de la zone où se trouve le joueur)
    private boolean buildHasZone, buildInMode, buildAuthorized, buildZoneActive;
    private String  buildZoneName = "", buildMinStr = "", buildMaxStr = "";
    private final int[] buildDims = {0, 0, 0};
    private final List<String[]> buildFlags = new ArrayList<>(); // [flagName, "1"/"0"]

    // Confirmation suppression home
    private String confirmDeleteHome = null;

    private int currentTab = 0;

    private static String[] tabNames() {
        return Lang.fr()
            ? new String[]{ "PARAMS", "CMDS", "HOMES", "VERROUS", "GROUPE", "STATS", "BUILD" }
            : new String[]{ "SETTINGS", "CMDS", "HOMES", "LOCKS", "GROUP", "STATS", "BUILD" };
    }
    private static String[] tabTitles() {
        return Lang.fr()
            ? new String[]{ "PARAMÈTRES", "COMMANDES", "HOMES", "VERROUS & CONFIANCE", "GROUPE", "STATISTIQUES", "MODE CONSTRUCTION" }
            : new String[]{ "SETTINGS", "COMMANDS", "HOMES", "LOCKS & TRUST", "GROUP", "STATISTICS", "BUILD MODE" };
    }
    private int tabPitch = 22, tabBtnH = 20; // espacement/hauteur des onglets, calculés en init

    public SettingsScreen(OpenSettingsPayload payload) {
        super(Component.literal("MENU"));
        allowPrivateMessages  = payload.allowPrivateMessages();
        allowTpaRequests      = payload.allowTpaRequests();
        allowTrades           = payload.allowTrades();
        showChatNotifications = payload.showChatNotifications();
        showConnectionAlerts  = payload.showConnectionAlerts();

        for (String entry : payload.commands().split("\\|")) {
            if (entry.contains(":")) {
                int c = entry.indexOf(':');
                commandList.add(new String[]{ entry.substring(0, c), entry.substring(c + 1) });
            }
        }
        for (String entry : payload.homes().split("\\|")) {
            if (entry.contains(":")) {
                String[] parts = entry.split(":", 3);
                if (parts.length >= 2) {
                    String name = parts[0];
                    String[] xyz = parts[1].split(",");
                    if (xyz.length == 3) {
                        try {
                            homeMap.put(name, new int[]{
                                Integer.parseInt(xyz[0].trim()),
                                Integer.parseInt(xyz[1].trim()),
                                Integer.parseInt(xyz[2].trim())
                            });
                            if (parts.length >= 3 && !parts[2].isEmpty())
                                homeDimMap.put(name, parts[2]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        if (!payload.warps().isEmpty())
            for (String entry : payload.warps().split(";")) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    String[] cd = parts[1].split(",", 4);
                    if (cd.length == 4) warpList.add(new String[]{
                        parts[0], cd[0] + ", " + cd[1] + ", " + cd[2],
                        cd[3].replace("minecraft:", "") });
                }
            }
        for (String entry : payload.lockedBlocks().split("\\|")) {
            if (!entry.isEmpty()) lockList.add(entry);
        }
        for (String entry : payload.trustedPlayers().split("\\|")) {
            if (entry.contains(":")) {
                int c = entry.indexOf(':');
                trustList.add(new String[]{ entry.substring(0, c), entry.substring(c + 1) });
            }
        }

        // Parse stats: "hours|minutes|deaths|playerKills|mobKills|blocks|xp"
        String[] sf = payload.stats().split("\\|");
        if (sf.length >= 7) {
            try {
                statHours       = Long.parseLong(sf[0]);
                statMinutes     = Long.parseLong(sf[1]);
                statDeaths      = Integer.parseInt(sf[2]);
                statPlayerKills = Integer.parseInt(sf[3]);
                statMobKills    = Integer.parseInt(sf[4]);
                statBlocks      = Long.parseLong(sf[5]);
                statXp          = Integer.parseInt(sf[6]);
            } catch (NumberFormatException ignored) {}
        }
        parseBuildInfo(payload.buildInfo());
    }

    public void onStatsRefresh(OpenSettingsPayload p) {
        String[] sf = p.stats().split("\\|");
        if (sf.length >= 7) {
            try {
                statHours       = Long.parseLong(sf[0]);
                statMinutes     = Long.parseLong(sf[1]);
                statDeaths      = Integer.parseInt(sf[2]);
                statPlayerKills = Integer.parseInt(sf[3]);
                statMobKills    = Integer.parseInt(sf[4]);
                statBlocks      = Long.parseLong(sf[5]);
                statXp          = Integer.parseInt(sf[6]);
            } catch (NumberFormatException ignored) {}
        }
        parseBuildInfo(p.buildInfo());
        if (this.minecraft != null && this.minecraft.screen == this) init();
    }

    private void parseBuildInfo(String s) {
        buildHasZone = false;
        buildFlags.clear();
        if (s == null || s.isEmpty()) return;
        String[] f = s.split("\\|");
        if (f.length < 9) return;
        try {
            buildInMode     = "1".equals(f[0]);
            buildZoneName   = f[1];
            buildDims[0]    = Integer.parseInt(f[2]);
            buildDims[1]    = Integer.parseInt(f[3]);
            buildDims[2]    = Integer.parseInt(f[4]);
            buildMinStr     = f[5];
            buildMaxStr     = f[6];
            buildAuthorized = "1".equals(f[7]);
            buildZoneActive = "1".equals(f[8]);
            if (f.length > 9 && !f[9].isEmpty())
                for (String pair : f[9].split(";")) {
                    String[] kv = pair.split(":");
                    if (kv.length == 2) buildFlags.add(kv);
                }
            buildHasZone = true;
        } catch (Exception ignored) { buildHasZone = false; }
    }

    public void onGroupUpdate(OpenGroupPayload p) {
        groupMembers.clear();
        onlinePlayers.clear();
        pendingInviteFrom = p.pendingInviteFrom();
        myColorIdx        = p.myColorIdx();
        showNames         = p.showNames();
        groupTrustEnabled = p.groupTrust();
        groupName         = p.groupName();
        if (!p.members().isEmpty())
            for (String entry : p.members().split("\\|")) {
                String[] f = entry.split(":");
                if (f.length == 5) groupMembers.add(f);
            }
        if (!p.onlinePlayers().isEmpty())
            for (String n : p.onlinePlayers().split("\\|"))
                if (!n.isEmpty()) onlinePlayers.add(n);
        List<GroupHud.MemberState> hudStates = groupMembers.stream()
            .map(f -> new GroupHud.MemberState(
                tryParseUUID(f[0]), f[1],
                tryInt(f[2]), 0, 0, 0, 100,
                "1".equals(f[3]), false))
            .filter(s -> s.uuid() != null).toList();
        GroupHud.updateMembers(hudStates, showNames);
        if (this.minecraft != null && this.minecraft.screen == this) init();
    }

    private static UUID tryParseUUID(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
    private static int tryInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    // ─── init ────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // Ratio of the screen, but with a minimum size so the fixed-offset content (sidebar tabs,
        // FERMER button) never clips at high GUI scales (x3/x4) where the screen is small.
        pw = Math.max((int)(this.width  * 0.70), Math.min(this.width  - 20, 360));
        ph = Math.max((int)(this.height * 0.70), Math.min(this.height - 20, 235));
        px = (this.width  - pw) / 2;
        py = (this.height - ph) / 2;
        cx = px + SIDE_W;
        midX = cx + (pw - SIDE_W) / 2;
        midY = py + ph / 2;

        clearWidgets();

        // Sidebar tabs — pitch adaptatif pour que les 7 onglets tiennent au-dessus de FERMER
        // quelle que soit l'échelle GUI.
        int navTop    = py + 42;
        int navBottom = py + ph - 28; // au-dessus de FERMER (py+ph-25, haut 20)
        String[] tabNames = tabNames();
        tabPitch = Math.max(16, Math.min(22, (navBottom - navTop) / tabNames.length));
        tabBtnH  = Math.min(20, tabPitch - 2);
        for (int i = 0; i < tabNames.length; i++) {
            final int id = i;
            boolean active = currentTab == i;
            Component lbl = Component.literal(tabNames[i]).withStyle(
                active ? s -> s.withColor(0x00E5FF).withBold(true) : s -> s.withColor(0x777777));
            addRenderableWidget(Button.builder(lbl, b -> {
                currentTab = id;
                confirmDeleteHome = null;
                if (id == 5 || id == 6) sendAction("REFRESH_STATS", ""); // stats + build info live
                init();
            }).bounds(px + 5, navTop + i * tabPitch, SIDE_W - 10, tabBtnH).build());
        }
        addRenderableWidget(Button.builder(Component.literal("FERMER"), b -> onClose())
            .bounds(px + 5, py + ph - 25, SIDE_W - 10, 20).build());

        // Tab content
        switch (currentTab) {
            case 0 -> buildSettings();
            case 2 -> buildHomes();
            case 3 -> buildVerrous();
            case 4 -> buildGroupe();
            case 6 -> buildBuild();
        }
    }

    /** Onglet BUILD : bouton de visualisation des zones (overlay wireframe, préférence client). */
    private void buildBuild() {
        int btnW = Math.min(200, pw - SIDE_W - 28);
        addRenderableWidget(Button.builder(overlayLabel(), b -> {
            ClientZoneCache.overlayEnabled = !ClientZoneCache.overlayEnabled;
            ClientZoneCache.saveConfig();
            b.setMessage(overlayLabel());
        }).bounds(cx + 14, py + ph - 24, btnW, 20).build());
    }

    private static Component overlayLabel() {
        return Component.literal(Lang.t("VISUALISATION ZONES : ", "ZONE OVERLAY: ")
            + Lang.onOff(ClientZoneCache.overlayEnabled));
    }

    private void buildSettings() {
        int btnX  = px + pw - 68;
        int start = py + 34;
        boolean[] vals = { allowPrivateMessages, allowTpaRequests, allowTrades, showChatNotifications, showConnectionAlerts };
        for (int i = 0; i < vals.length; i++) {
            final int idx = i;
            boolean cur = vals[i];
            addRenderableWidget(Button.builder(
                Component.literal(Lang.onOff(cur)),
                b -> toggle(idx)
            ).bounds(btnX, start + ROW_H * i + 10, 58, 18).build());
        }
    }

    /** Y du haut de la section WARPS PUBLICS — formule partagée entre init() et render(). */
    private int warpsSectionTop() {
        return py + 34 + ROW_H * homeMap.size() + (homeMap.isEmpty() ? 22 : 6);
    }

    private void buildHomes() {
        int start = py + 34;
        int i = 0;
        for (String name : new ArrayList<>(homeMap.keySet())) {
            int ry = start + ROW_H * i;
            addRenderableWidget(Button.builder(
                Component.literal(Lang.t("§aTÉLÉPORTER", "§aTELEPORT")),
                b -> { sendAction("HOME_TP", name); onClose(); }
            ).bounds(px + pw - 168, ry + 10, 80, 18).build());

            if (name.equals(confirmDeleteHome)) {
                addRenderableWidget(Button.builder(Component.literal(Lang.t("§cCONFIRMER", "§cCONFIRM")),
                    b -> { homeMap.remove(name); sendAction("HOME_DELETE", name); confirmDeleteHome = null; init(); }
                ).bounds(px + pw - 84, ry + 10, 78, 18).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal(Lang.t("§7SUPPRIMER", "§7DELETE")),
                    b -> { confirmDeleteHome = name; init(); }
                ).bounds(px + pw - 84, ry + 10, 78, 18).build());
            }
            i++;
        }

        // ── WARPS PUBLICS ────────────────────────────────────────────────────
        if (!warpList.isEmpty()) {
            int wy = warpsSectionTop() + 14;
            int maxBottom = py + ph - 8;
            for (String[] w : warpList) {
                if (wy + 16 > maxBottom) break;
                final String wn = w[0];
                addRenderableWidget(Button.builder(Component.literal("§bTP"),
                    b -> { sendAction("WARP_TP", wn); onClose(); })
                    .bounds(px + pw - 44, wy, 38, 14).build());
                wy += 18;
            }
        }
    }

    private void buildVerrous() {
        int contentTop = py + 28;
        int contentBot = py + ph - 8;
        int y = contentTop - verrouScroll;

        y += 20;
        for (int i = 0; i < lockList.size(); i++) {
            final String pos = lockList.get(i);
            int btnY = y + 5;
            if (btnY + 14 > contentTop && btnY < contentBot) {
                addRenderableWidget(Button.builder(
                    Component.literal(Lang.t("§cDÉVERROUILLER", "§cUNLOCK")),
                    b -> { lockList.remove(pos); sendAction("LOCK_DELETE", pos); init(); }
                ).bounds(px + pw - 100, btnY, 94, 14).build());
            }
            y += 24;
        }
        if (lockList.isEmpty()) y += 18;

        y += 12;

        y += 20;
        for (int i = 0; i < trustList.size(); i++) {
            final String[] entry = trustList.get(i);
            int btnY = y + 5;
            if (btnY + 14 > contentTop && btnY < contentBot) {
                addRenderableWidget(Button.builder(
                    Component.literal(Lang.t("§cRETIRER", "§cREMOVE")),
                    b -> { trustList.remove(entry); sendAction("UNTRUST", entry[1]); init(); }
                ).bounds(px + pw - 76, btnY, 70, 14).build());
            }
            y += 24;
        }
    }

    private void buildGroupe() {
        int contentTop = py + 28;
        int contentW   = pw - SIDE_W;
        int botY       = py + ph - 112; // bottom panel starts here (112px)

        if (!pendingInviteFrom.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal(Lang.t("§aACCEPTER", "§aACCEPT")),
                b -> { sendGroup("ACCEPT", ""); pendingInviteFrom = ""; init(); })
                .bounds(midX - 108, contentTop + 2, 104, 16).build());
            addRenderableWidget(Button.builder(Component.literal(Lang.t("§cREFUSER", "§cDENY")),
                b -> { sendGroup("DENY", ""); pendingInviteFrom = ""; init(); })
                .bounds(midX + 4, contentTop + 2, 104, 16).build());
        }

        boolean imLeader = groupMembers.stream().anyMatch(f -> "1".equals(f[4])
            && net.minecraft.client.Minecraft.getInstance().player != null
            && f[0].equals(net.minecraft.client.Minecraft.getInstance().player.getUUID().toString()));

        int y = contentTop + (pendingInviteFrom.isEmpty() ? 2 : 22) - groupScroll;
        for (String[] m : groupMembers) {
            String uuid = m[0];
            boolean isMe = net.minecraft.client.Minecraft.getInstance().player != null
                && uuid.equals(net.minecraft.client.Minecraft.getInstance().player.getUUID().toString());
            if (!isMe && imLeader) {
                int fy = y;
                addRenderableWidget(Button.builder(Component.literal("§c✕"),
                    b -> { sendGroup("KICK", uuid); init(); })
                    .bounds(px + pw - 24, fy + 3, 18, 14).build());
            }
            y += 22;
        }

        // Mirror renderGroupe's vertical cursor exactly so the "+" buttons line up with their
        // player names: members block (header + rows, or the "aucun groupe" line) then the
        // INVITER header (4px gap + 14px header/divider).
        int membersBlockH = groupMembers.isEmpty() ? 22 : 14 + groupMembers.size() * 22;
        int invY = contentTop + (pendingInviteFrom.isEmpty() ? 2 : 22) - groupScroll
            + membersBlockH + 18;
        for (String pName : onlinePlayers) {
            int fy = invY;
            // Name is drawn at fy + 3 (8px tall); centre the 14px button on that line.
            addRenderableWidget(Button.builder(Component.literal("§7+"),
                b -> { sendGroup("INVITE", pName); init(); })
                .bounds(px + pw - 24, fy, 18, 14).build());
            invY += 18;
        }

        // Bottom panel widgets
        groupNameBox = null;
        int nameBoxY = botY + 54;
        if (groupMembers.isEmpty() && pendingInviteFrom.isEmpty()) {
            // CREATE form in bottom panel
            groupNameBox = new EditBox(font, cx + 6, nameBoxY, contentW - 76, 16, Component.empty());
            groupNameBox.setMaxLength(32);
            groupNameBox.setHint(Component.literal(Lang.t("§8Nom du groupe…", "§8Group name…")));
            addRenderableWidget(groupNameBox);
            addRenderableWidget(Button.builder(Component.literal(Lang.t("§aCRÉER", "§aCREATE")),
                b -> { sendGroup("CREATE", groupNameBox.getValue()); })
                .bounds(cx + contentW - 66, nameBoxY, 60, 16).build());
        } else if (imLeader && !groupMembers.isEmpty()) {
            groupNameBox = new EditBox(font, cx + 6, nameBoxY, contentW - 76, 16, Component.empty());
            groupNameBox.setMaxLength(32);
            groupNameBox.setValue(groupName);
            groupNameBox.setHint(Component.literal(Lang.t("§8Nom du groupe…", "§8Group name…")));
            addRenderableWidget(groupNameBox);
            addRenderableWidget(Button.builder(Component.literal("§aOK"),
                b -> { sendGroup("SET_NAME", groupNameBox.getValue()); groupName = groupNameBox.getValue(); })
                .bounds(cx + contentW - 66, nameBoxY, 60, 16).build());
        }

        // Toggles — 2 equal columns
        int hw = (contentW - 16) / 2;
        addRenderableWidget(Button.builder(
            Component.literal(Lang.t("Pseudos : ", "Names: ") + Lang.onOff(showNames)),
            b -> { sendGroup("TOGGLE_SHOW_NAMES", ""); showNames = !showNames; init(); })
            .bounds(cx + 6, botY + 76, hw, 16).build());
        addRenderableWidget(Button.builder(
            Component.literal(Lang.t("Trust : ", "Trust: ") + Lang.onOff(groupTrustEnabled)),
            b -> { sendGroup("TOGGLE_GROUP_TRUST", ""); groupTrustEnabled = !groupTrustEnabled; init(); })
            .bounds(cx + 10 + hw, botY + 76, hw, 16).build());

        if (!groupMembers.isEmpty()) {
            String leaveLbl = imLeader ? Lang.t("§cDISBANDER", "§cDISBAND") : Lang.t("§cQUITTER", "§cLEAVE");
            String leaveAct = imLeader ? "DISBAND" : "LEAVE";
            addRenderableWidget(Button.builder(Component.literal(leaveLbl),
                b -> { sendGroup(leaveAct, ""); init(); })
                .bounds(cx + 6, botY + 96, contentW - 12, 16).build());
        }
    }

    // ─── Actions ─────────────────────────────────────────────────────────────────

    private void toggle(int idx) {
        switch (idx) {
            case 0 -> allowPrivateMessages  = !allowPrivateMessages;
            case 1 -> allowTpaRequests      = !allowTpaRequests;
            case 2 -> allowTrades           = !allowTrades;
            case 3 -> showChatNotifications = !showChatNotifications;
            case 4 -> showConnectionAlerts  = !showConnectionAlerts;
        }
        PacketDistributor.sendToServer(new UpdateSettingsPayload(
            allowPrivateMessages, allowTpaRequests, allowTrades,
            showChatNotifications, showConnectionAlerts
        ));
        this.init();
    }

    private void sendAction(String action, String value) {
        PacketDistributor.sendToServer(new PlayerActionPayload(action, value));
    }

    private void sendGroup(String action, String value) {
        PacketDistributor.sendToServer(new GroupActionPayload(action, value));
    }

    // ─── render ──────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);

        g.fill(cx, py, px + pw, py + ph, C_BG);
        g.fill(px, py, cx,      py + ph, C_SIDE);

        // Sidebar header
        g.fill(px, py, cx, py + 30, 0xFF080808);
        g.drawCenteredString(font,
            Component.literal("MENU").withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            px + SIDE_W / 2, py + 11, 0xFFFFFFFF);
        g.fill(px + 6, py + 29, cx - 6, py + 30, C_ACCENT);

        // Active tab highlight in sidebar
        int tay = py + 42 + currentTab * tabPitch;
        g.fill(px + 2, tay,     px + 4, tay + tabBtnH, C_ACCENT);
        g.fill(px + 4, tay, cx - 2, tay + tabBtnH, C_TABSEL);

        // Sidebar separator
        g.fill(cx - 1, py, cx, py + ph, C_ACCENT);

        // Content header bar
        g.fill(cx, py, px + pw, py + 26, C_HBAR);
        g.drawCenteredString(font,
            Component.literal(tabTitles()[currentTab]).withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            midX, py + 9, 0xFFFFFFFF);
        g.fill(cx, py + 25, px + pw, py + 26, C_DIV);

        switch (currentTab) {
            case 0 -> renderSettings(g);
            case 1 -> renderCommands(g);
            case 2 -> renderHomes(g);
            case 3 -> renderVerrous(g);
            case 4 -> renderGroupe(g);
            case 5 -> renderStats(g);
            case 6 -> renderBuild(g);
        }

        g.drawString(font, "@LKDM", px + pw - font.width("@LKDM") - 4, py + ph - 10, 0x55AAAAAA, false);

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderSettings(GuiGraphics g) {
        int start = py + 34;
        boolean[] vals = { allowPrivateMessages, allowTpaRequests, allowTrades, showChatNotifications, showConnectionAlerts };
        String[] labels = labels(), descs = descs();
        for (int i = 0; i < labels.length; i++) {
            int ry = start + ROW_H * i;
            // Full row: green tint when ON, subtle otherwise
            g.fill(cx + 4, ry + 2, px + pw - 4, ry + ROW_H - 2,
                vals[i] ? 0x5000CC44 : (i % 2 == 0 ? C_ROW : 0));
            // Left accent bar (5px)
            g.fill(cx + 4, ry + 2, cx + 9, ry + ROW_H - 2,
                vals[i] ? 0xFF00CC44 : 0xFF555555);
            g.drawString(font, "§f" + labels[i], cx + 14, ry + 6,  0xFFFFFFFF);
            g.drawString(font, "§7" + descs[i],  cx + 14, ry + 18, 0xFFAAAAAA);
        }
    }

    private void renderCommands(GuiGraphics g) {
        int contentTop = py + 28;
        int contentBot = py + ph - 8;
        int colW       = (pw - SIDE_W) / 2;
        int maxW       = colW - 14;
        int half       = (commandList.size() + 1) / 2;
        int visH       = contentBot - contentTop;
        int maxScroll  = computeMaxCmdScroll(maxW, visH);
        cmdScroll      = Math.max(0, Math.min(cmdScroll, maxScroll));

        g.fill(cx + colW, contentTop, cx + colW + 1, contentBot, C_DIV);
        g.enableScissor(cx, contentTop, px + pw - 5, contentBot);

        int[] colY   = { contentTop - cmdScroll, contentTop - cmdScroll };
        int[] colRow = { 0, 0 };

        for (int i = 0; i < commandList.size(); i++) {
            String[] cmd  = commandList.get(i);
            int col  = i < half ? 0 : 1;
            int ex   = cx + 4 + col * colW;
            int ey   = colY[col];
            var lines = font.split(Component.literal(cmdDesc(cmd)), maxW);
            int entryH = 16 + lines.size() * 10;

            if (colRow[col] % 2 == 0)
                g.fill(ex, ey + 1, ex + colW - 8, ey + entryH - 1, C_ROW);
            g.drawString(font, "§b" + cmd[0], ex + 5, ey + 4, 0xFFFFFFFF);
            int dy = ey + 14;
            for (var line : lines) {
                g.drawString(font, line, ex + 5, dy, 0xFFAAAAAA);
                dy += 10;
            }
            colY[col]   += entryH;
            colRow[col]++;
        }

        g.disableScissor();

        if (maxScroll > 0) {
            int totalH = maxScroll + visH;
            int sbX    = px + pw - 4;
            int thumbH = Math.max(16, visH * visH / totalH);
            int thumbY = contentTop + (int)((long) cmdScroll * (visH - thumbH) / maxScroll);
            g.fill(sbX, contentTop, sbX + 3, contentBot, 0x22FFFFFF);
            g.fill(sbX, thumbY,    sbX + 3, thumbY + thumbH, C_ACCENT);
        }
    }

    private void renderHomes(GuiGraphics g) {
        int start = py + 34;
        if (homeMap.isEmpty()) {
            g.drawString(font, Lang.t("§8Aucun home enregistré — §7utilisez §b/sethome <nom>",
                "§8No homes saved — §7use §b/sethome <name>"), cx + 12, start + 4, 0xFF555555);
        }
        int i = 0;
        for (var e : homeMap.entrySet()) {
            int ry = start + ROW_H * i;
            boolean confirming = e.getKey().equals(confirmDeleteHome);
            String dim = homeDimMap.getOrDefault(e.getKey(), "OW");
            int dimColor = dim.equals("NE") ? 0xFFFF5555 : dim.equals("END") ? 0xFFAA00AA : 0xFF55FF55;
            int accentBar = confirming ? 0xFFFF4444 : dimColor;
            int bg = confirming ? 0x33FF4444 : (i % 2 == 0 ? C_ROW : 0);
            if (bg != 0) g.fill(cx + 4, ry + 2, px + pw - 4, ry + ROW_H - 2, bg);
            g.fill(cx + 4, ry + 2, cx + 5, ry + ROW_H - 2, accentBar);
            int[] pos = e.getValue();
            int dimTagColor = dim.equals("NE") ? 0xFFFF8888 : dim.equals("END") ? 0xFFCC88FF : 0xFF88FF88;
            g.drawString(font, "§f§l" + e.getKey() + (confirming ? Lang.t(" §c— Confirmer la suppression ?", " §c— Confirm deletion?") : ""),
                cx + 12, ry + 7,  0xFFFFFFFF);
            g.drawString(font, "§7" + pos[0] + ", " + pos[1] + ", " + pos[2], cx + 12, ry + 20, 0xFFAAAAAA);
            g.drawString(font, "[" + dim + "]", cx + 12 + font.width("§7" + pos[0] + ", " + pos[1] + ", " + pos[2]) + 4, ry + 20, dimTagColor);
            i++;
        }

        // ── WARPS PUBLICS ────────────────────────────────────────────────────
        int wy = warpsSectionTop();
        g.drawString(font, Lang.t("WARPS PUBLICS", "PUBLIC WARPS") + (warpList.isEmpty() ? "" : " §7(" + warpList.size() + ")"),
            cx + 8, wy + 2, C_SEC);
        g.fill(cx + 8, wy + 11, px + pw - 8, wy + 12, C_DIV);
        wy += 14;
        if (warpList.isEmpty()) {
            g.drawString(font, Lang.t("§8Aucun warp public sur le serveur.", "§8No public warps on the server."), cx + 12, wy + 3, 0xFF555555);
            return;
        }
        int maxBottom = py + ph - 8;
        int shown = 0;
        for (String[] w : warpList) {
            if (wy + 16 > maxBottom) {
                g.drawString(font, "§8+" + (warpList.size() - shown) + "…", cx + 12, wy, 0xFF444444);
                break;
            }
            if (shown % 2 == 0) g.fill(cx + 8, wy - 1, px + pw - 8, wy + 15, C_ROW);
            g.drawString(font, "§b◈ §f" + w[0], cx + 12, wy + 3, 0xFFFFFFFF);
            g.drawString(font, "§8" + w[1] + "  §7[" + w[2] + "]",
                cx + 16 + font.width("§b◈ §f" + w[0]), wy + 3, 0xFF777777);
            wy += 18;
            shown++;
        }
    }

    private void renderVerrous(GuiGraphics g) {
        int contentTop = py + 28;
        int contentBot = py + ph - 8;
        int maxScroll  = Math.max(0, computeVerrouTotalH() - (contentBot - contentTop));
        verrouScroll   = Math.max(0, Math.min(verrouScroll, maxScroll));

        g.enableScissor(cx, contentTop, px + pw, contentBot);

        int y = contentTop - verrouScroll;

        y += 4;
        g.drawString(font, Lang.t("BLOCS VERROUILLÉS", "LOCKED BLOCKS"), cx + 8, y + 4, C_SEC);
        g.fill(cx + 8, y + 14, px + pw - 8, y + 15, C_DIV);
        y += 20;

        if (lockList.isEmpty()) {
            g.drawString(font, Lang.t("§8Aucun bloc verrouillé", "§8No locked blocks"), cx + 12, y + 3, 0xFF555555);
            y += 18;
        } else {
            for (int i = 0; i < lockList.size(); i++) {
                int ry = y;
                if (i % 2 == 0) g.fill(cx + 4, ry, px + pw - 4, ry + 22, C_ROW);
                g.drawString(font, "§7" + lockList.get(i), cx + 10, ry + 6, 0xFFAAAAAA);
                y += 24;
            }
        }

        y += 12;

        g.drawString(font, Lang.t("JOUEURS DE CONFIANCE", "TRUSTED PLAYERS"), cx + 8, y + 4, C_SEC);
        g.fill(cx + 8, y + 14, px + pw - 8, y + 15, C_DIV);
        y += 20;

        if (trustList.isEmpty()) {
            g.drawString(font, Lang.t("§8Aucun joueur de confiance", "§8No trusted players"), cx + 12, y + 3, 0xFF555555);
        } else {
            for (int i = 0; i < trustList.size(); i++) {
                int ry = y;
                if (i % 2 == 0) g.fill(cx + 4, ry, px + pw - 4, ry + 22, C_ROW);
                g.drawString(font, "§f" + trustList.get(i)[0], cx + 10, ry + 6, 0xFFFFFFFF);
                y += 24;
            }
        }

        g.disableScissor();

        if (maxScroll > 0) {
            int visH   = contentBot - contentTop;
            int totalH = maxScroll + visH;
            int sbX    = px + pw - 4;
            int thumbH = Math.max(14, visH * visH / totalH);
            int thumbY = contentTop + (int)((long) verrouScroll * (visH - thumbH) / maxScroll);
            g.fill(sbX, contentTop, sbX + 3, contentBot, 0x22FFFFFF);
            g.fill(sbX, thumbY,    sbX + 3, thumbY + thumbH, C_ACCENT);
        }
    }

    private void renderGroupe(GuiGraphics g) {
        int contentTop = py + 28;
        int botY       = py + ph - 112;
        int contentBot = botY;

        if (!pendingInviteFrom.isEmpty()) {
            g.fill(cx + 4, contentTop, px + pw - 4, contentTop + 20, 0x33FFAA00);
            g.drawCenteredString(font, Lang.t("§eInvitation de §f", "§eInvite from §f") + pendingInviteFrom, midX, contentTop + 5, 0xFFFFFFFF);
        }

        g.enableScissor(cx, contentTop, px + pw, contentBot);

        int y = contentTop + (pendingInviteFrom.isEmpty() ? 2 : 22) - groupScroll;

        if (groupMembers.isEmpty()) {
            g.drawCenteredString(font, Lang.t("§8Vous n'êtes dans aucun groupe.", "§8You are not in a group."), midX, y + 8, 0xFF555555);
            y += 22;
        } else {
            g.drawString(font, Lang.t("MEMBRES", "MEMBERS"), cx + 8, y, C_SEC);
            g.fill(cx + 8, y + 10, px + pw - 8, y + 11, C_DIV);
            y += 14;
            for (String[] m : groupMembers) {
                int ci = tryInt(m[2]);
                boolean isAfk = "1".equals(m[3]);
                boolean isLdr = "1".equals(m[4]);
                String colorCode = GroupHud.COLOR_CODES[Math.max(0, Math.min(ci, GroupHud.COLOR_CODES.length - 1))];
                String nameStr = (isAfk ? "§7" : colorCode) + m[1]
                    + (isLdr ? " §8[Leader]" : "") + (isAfk ? " §8[AFK]" : "");
                if (y + 20 > contentTop && y < contentBot + groupScroll)
                    g.drawString(font, nameStr, cx + 10, y + 5, 0xFFFFFFFF);
                y += 22;
            }
        }

        if (!onlinePlayers.isEmpty()) {
            y += 4;
            g.drawString(font, Lang.t("INVITER", "INVITE"), cx + 8, y, C_SEC);
            g.fill(cx + 8, y + 10, px + pw - 8, y + 11, C_DIV);
            y += 14;
            for (String pName : onlinePlayers) {
                if (y + 16 > contentTop && y < contentBot + groupScroll)
                    g.drawString(font, "§7" + pName, cx + 10, y + 3, 0xFFAAAAAA);
                y += 18;
            }
        }

        g.disableScissor();

        // Bottom panel
        g.fill(cx + 4, contentBot, px + pw - 4, contentBot + 1, C_DIV);
        g.fill(cx, contentBot + 1, px + pw, py + ph, 0x0AFFFFFF);

        // Color swatches (2 rows of 8)
        g.drawString(font, Lang.t("COULEUR", "COLOR"), cx + 8, botY + 4, C_SEC);
        for (int i = 0; i < GroupHud.COLOR_ARGB.length; i++) {
            int row = i / 8, col = i % 8;
            int argb = GroupHud.COLOR_ARGB[i];
            int sx = cx + 8 + col * 18;
            int sy = botY + 14 + row * 18;
            boolean sel = (i == myColorIdx);
            if (sel) g.fill(sx - 1, sy - 1, sx + 17, sy + 17, C_ACCENT);
            g.fill(sx, sy, sx + 16, sy + 16, sel ? argb : (argb & 0x00FFFFFF) | 0x88000000);
        }

        // Group name label/box area — when an edit box is present its own hint ("Nom du
        // groupe…") labels it, so we only draw a label in the read-only (non-leader) case.
        g.fill(cx + 4, botY + 50, px + pw - 4, botY + 51, C_DIV);
        if (!groupMembers.isEmpty() && groupNameBox == null && !groupName.isEmpty()) {
            g.drawString(font, Lang.t("NOM §7", "NAME §7") + groupName, cx + 8, botY + 58, C_SEC);
        }
    }

    private void renderStats(GuiGraphics g) {
        int lx  = cx + 16;
        int rx  = cx + 180;
        int y   = py + 36;
        int gap = 28;

        String[][] rows = {
            { Lang.t("Temps de jeu", "Playtime"),                statHours + "h " + statMinutes + "min" },
            { Lang.t("Morts", "Deaths"),                         String.valueOf(statDeaths) },
            { Lang.t("Kills joueurs", "Player kills"),           String.valueOf(statPlayerKills) },
            { Lang.t("Kills mobs hostiles", "Hostile mob kills"), String.valueOf(statMobKills) },
            { Lang.t("Distance parcourue", "Distance traveled"), statBlocks + Lang.t(" blocs", " blocks") },
            { Lang.t("XP totale", "Total XP"),                   String.valueOf(statXp) },
        };

        for (int i = 0; i < rows.length; i++) {
            int ry = y + i * gap;
            if (i % 2 == 0) g.fill(cx + 8, ry - 3, px + pw - 8, ry + gap - 5, C_ROW);
            g.fill(cx + 8, ry - 3, cx + 11, ry + gap - 5, C_ACCENT);
            g.drawString(font, "§7" + rows[i][0], lx, ry + 4, 0xFFAAAAAA);
            g.drawString(font, "§f" + rows[i][1], rx, ry + 4, 0xFFFFFFFF);
        }
    }

    private void renderBuild(GuiGraphics g) {
        int x = cx + 14, y = py + 38;
        if (!buildHasZone) {
            g.drawCenteredString(font, Lang.t("§8Vous n'êtes dans aucune zone.", "§8You are not in any zone."), midX, py + ph / 2 - 10, 0xFF555555);
            g.drawCenteredString(font, Lang.t("§8Entrez dans une zone ou utilisez §6/build", "§8Enter a zone or use §6/build"), midX, py + ph / 2 + 2, 0xFF444444);
            return;
        }
        g.drawString(font, buildInMode ? Lang.t("§a✔ Mode construction ACTIF", "§a✔ Build mode ACTIVE")
                                       : Lang.t("§7Mode construction : §8inactif", "§7Build mode: §8inactive"), x, y, 0xFFFFFFFF);
        y += 16;
        String[][] rows = {
            { Lang.t("Zone", "Zone"),               "§f" + buildZoneName },
            { Lang.t("État", "State"),              buildZoneActive ? Lang.t("§aactivée", "§aenabled") : Lang.t("§cdésactivée", "§cdisabled") },
            { Lang.t("Taille", "Size"),             "§f" + buildDims[0] + "×" + buildDims[1] + "×" + buildDims[2] },
            { Lang.t("Coin min", "Min corner"),     "§7(" + buildMinStr + ")" },
            { Lang.t("Coin max", "Max corner"),     "§7(" + buildMaxStr + ")" },
            { Lang.t("Votre accès", "Your access"), buildAuthorized ? Lang.t("§aautorisé", "§aallowed") : Lang.t("§cnon autorisé", "§cnot allowed") },
        };
        for (String[] r : rows) {
            g.drawString(font, "§7" + r[0] + " :", x, y, 0xFFAAAAAA);
            g.drawString(font, r[1], x + 80, y, 0xFFFFFFFF);
            y += 13;
        }
        y += 6;
        g.drawString(font, Lang.t("§8RÈGLES DE LA ZONE", "§8ZONE RULES"), x, y, C_SEC);
        g.fill(x, y + 10, px + pw - 12, y + 11, C_DIV);
        y += 16;
        // 2 colonnes pour que la liste (10 flags) tienne dans le panneau à toute échelle GUI
        int perCol = (buildFlags.size() + 1) / 2;
        int colW   = (px + pw - 12 - x) / 2;
        int baseY  = y;
        for (int i = 0; i < buildFlags.size(); i++) {
            String[] fl = buildFlags.get(i);
            com.lkdm.dashboardadmin.ZoneFlag zf = com.lkdm.dashboardadmin.ZoneFlag.byName(fl[0]);
            String label = zf != null ? Lang.t(zf.label, zf.labelEn) : fl[0];
            int fx = x + 2 + (i / perCol) * colW;
            int fy = baseY + (i % perCol) * 12;
            g.drawString(font, "§7• " + label, fx, fy, 0xFFAAAAAA);
            g.drawString(font, "1".equals(fl[1]) ? Lang.t("§aautorisé", "§aallowed") : Lang.t("§cbloqué", "§cblocked"), fx + colW - 52, fy, 0xFFFFFFFF);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    /** Description localisée d'une commande (le serveur n'envoie que le FR ; on traduit par nom). */
    private static String cmdDesc(String[] cmd) {
        if (!Lang.fr()) {
            String en = switch (cmd[0]) {
                case "/menu"       -> "Open the settings & commands menu";
                case "/mail"       -> "Send a private message to a player";
                case "/r"          -> "Reply to the last received message";
                case "/tpa"        -> "Request a teleport to a player";
                case "/tpahere"    -> "Ask a player to teleport to you";
                case "/tpaccept"   -> "Accept a teleport request";
                case "/tpdeny"     -> "Deny a teleport request";
                case "/sethome"    -> "Save a home (Overworld only)";
                case "/home"       -> "Teleport to a saved home";
                case "/back"       -> "Return to your previous position";
                case "/lock"       -> "Lock/unlock the block you're looking at";
                case "/trust"      -> "Grant access to your locked blocks";
                case "/untrust"    -> "Revoke access to your locked blocks";
                case "/lockinfo"   -> "View info about a locked block";
                case "/chest"      -> "Open your personal virtual chest";
                case "/afk"        -> "Toggle your AFK status";
                case "/report"     -> "Send a report to the admins";
                case "/stats"      -> "View your game statistics";
                case "/seen"       -> "See a player's last login";
                case "/deal"       -> "Offer an item trade to a player";
                case "/dealaccept" -> "Accept a trade request";
                case "/dealdeny"   -> "Deny a trade request";
                case "/groupaccept"-> "Accept a group invitation";
                case "/groupdeny"  -> "Deny a group invitation";
                case "/build"      -> "Build mode inside an allowed zone";
                case "/warp"       -> "Teleport to a warp";
                case "/rtp"        -> "Safe random teleport";
                case "/ignore"     -> "Ignore a player's messages";
                case "/unignore"   -> "Stop ignoring a player";
                default -> null;
            };
            if (en != null) return en;
        }
        return cmd[1]; // FR (serveur) ou repli
    }

    private int computeMaxCmdScroll(int maxW, int visH) {
        int half = (commandList.size() + 1) / 2;
        int leftH = 0, rightH = 0;
        for (int i = 0; i < commandList.size(); i++) {
            int h = 16 + font.split(Component.literal(cmdDesc(commandList.get(i))), maxW).size() * 10;
            if (i < half) leftH += h; else rightH += h;
        }
        return Math.max(0, Math.max(leftH, rightH) - visH);
    }

    private int computeVerrouTotalH() {
        return 4
            + 20 + (lockList.isEmpty()  ? 18 : lockList.size()  * 24)
            + 12
            + 20 + (trustList.isEmpty() ? 18 : trustList.size() * 24)
            + 4;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentTab == 4) {
            int botY = py + ph - 112;
            int imx = (int) mouseX, imy = (int) mouseY;
            for (int i = 0; i < GroupHud.COLOR_ARGB.length; i++) {
                int row = i / 8, col = i % 8;
                int sx = cx + 8 + col * 18;
                int sy = botY + 14 + row * 18;
                if (imx >= sx && imx < sx + 16 && imy >= sy && imy < sy + 16) {
                    sendGroup("SET_COLOR", String.valueOf(i));
                    myColorIdx = i;
                    init();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == 1) {
            int colW      = (pw - SIDE_W) / 2;
            int maxW      = colW - 14;
            int visH      = ph - 8 - 28;
            int maxScroll = computeMaxCmdScroll(maxW, visH);
            cmdScroll = Math.max(0, Math.min((int)(cmdScroll - scrollY * 20), maxScroll));
            return true;
        }
        if (currentTab == 3) {
            int visH      = ph - 8 - 28;
            int maxScroll = Math.max(0, computeVerrouTotalH() - visH);
            verrouScroll  = Math.max(0, Math.min((int)(verrouScroll - scrollY * 20), maxScroll));
            return true;
        }
        if (currentTab == 4) {
            int totalH = (pendingInviteFrom.isEmpty() ? 2 : 22)
                + Math.max(22, groupMembers.size() * 22) + 6
                + (onlinePlayers.isEmpty() ? 0 : 14 + onlinePlayers.size() * 18);
            int visH      = ph - 94 - 28;
            int maxScroll = Math.max(0, totalH - visH);
            groupScroll   = Math.max(0, Math.min((int)(groupScroll - scrollY * 20), maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float delta) {}
    @Override
    public boolean isPauseScreen() { return false; }
}

