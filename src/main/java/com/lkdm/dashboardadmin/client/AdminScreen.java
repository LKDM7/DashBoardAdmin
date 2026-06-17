package com.lkdm.dashboardadmin.client;

import com.lkdm.dashboardadmin.command.AdminCommand;
import com.lkdm.dashboardadmin.networking.AdminActionPayload;
import com.lkdm.dashboardadmin.networking.OpenZonePayload;
import com.lkdm.dashboardadmin.networking.ZoneActionPayload;
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
    static final int C_ACCENT  = 0xFF00E5FF;
    static final int C_DIV     = 0x33FFFFFF;
    static final int C_ROW     = 0x11FFFFFF;
    static final int C_TABSEL  = 0x1A00AAFF;
    static final int SIDE_W      = 100;
    static final int ZONE_LIST_W = 100;

    // State
    private static int lastTab = 0; // mémorise l'onglet ouvert entre deux ouvertures de la GUI (Échap → réouverture)
    int     currentTab  = 0;        // partagé : onglets changent currentTab
    String  selPlayer   = null;   // joueur sélectionné — partagé Joueurs/Logs
    String  selGamemode = "???";
    String  search      = "";
    int     sortMode    = 0;     // 0 = récent (défaut), 1 = A→Z, 2 = nb sanctions
    boolean sanctionsRequested = false; // évite de re-demander les sanctions en boucle pour le tri
    final java.util.Set<String>         mutedPlayers    = new java.util.HashSet<>();
    final java.util.Set<String>         frozenPlayers   = new java.util.HashSet<>();
    final java.util.Set<String>         keepInvPlayers  = new java.util.HashSet<>();
    final java.util.Set<String>         afkPlayers      = new java.util.HashSet<>();
    final java.util.List<String[]> offlinePlayers = new java.util.ArrayList<>(); // [nom, lastSeenMs]
    String[] serverStats = null; // tps|mspt|ramU|ramM|entités|chunks|uptimeSec
    boolean  selOffline  = false;
    final java.util.List<String[]> warpsList = new java.util.ArrayList<>(); // [nom, "x, y, z", dim] — rempli par le payload
    private final WarpsTab warpsTab = new WarpsTab(this); // onglet WARPS extrait
    final java.util.Map<String, java.util.List<String>> adminNotes = new java.util.HashMap<>(); // nomLower → notes (overlay central + JoueursTab)
    boolean showNotesList = false;  // overlay notes central
    int     notesScroll   = 0;
    private String  hoverNoteFull = null; // note complète survolée dans l'overlay (tooltip)
    final java.util.Map<String, String> reports         = new java.util.LinkedHashMap<>();
    final java.util.Map<String, String> acceptedReports = new java.util.LinkedHashMap<>();
    final java.util.Map<String, String> closedReports   = new java.util.LinkedHashMap<>();
    String   hoverReportMsg = null; // message complet du report survolé (tooltip, rendu central)
    private EditBox  banReasonBox;
    boolean  isBanning = false, isKicking = false, isRemovingMobs = false, isRestarting = false;
    private long     banDurationSecs   = 0;   // 0=permanent, sinon durée en secondes
    private int      restartMinutes    = 5;
    private EditBox  banDayBox, banHourBox, banMinBox, banSecBox;
    String   confirmUnbanPlayer = null; // non-null = confirmation déban SANCTIONS (dialog central)
    private String   confirmLabel = null;  // texte du dialog de confirmation générique (null = fermé)
    private Runnable confirmRun   = null;  // action exécutée si l'admin confirme (delete zone/rôle/warp…)
    final java.util.List<String[]> availableGroups = new java.util.ArrayList<>(); // [leaderName, groupName, count] — payload
    final LogsTab logsTab = new LogsTab(this); // onglet LOGS extrait (LOGS button depuis Joueurs)
    final java.util.List<String[]> schedBroadcasts  = new java.util.ArrayList<>(); // payload + onglet Chat
    final java.util.List<String[]> bannedPlayers    = new java.util.ArrayList<>(); // [name, reason, expiresMs]
    final java.util.List<String[]> sanctionsEntries = new java.util.ArrayList<>(); // [ts, type, player, admin, reason]
    private final AuditTab auditTab = new AuditTab(this); // onglet AUDIT extrait (état + build/render)
    int  sanctionsScroll      = 0;
    private final SanctionsTab sanctionsTab = new SanctionsTab(this); // onglet SANCTIONS extrait
    private final MondeTab mondeTab = new MondeTab(this); // onglet MONDE extrait
    private final FeaturesTab featuresTab = new FeaturesTab(this); // onglet FEATURES extrait
    private final ReportsTab reportsTab = new ReportsTab(this); // onglet REPORTS extrait
    private final ChatTab chatTab = new ChatTab(this); // onglet CHAT extrait
    private final RolesTab rolesTab = new RolesTab(this); // onglet RÔLES extrait
    private final JoueursTab joueursTab = new JoueursTab(this); // onglet JOUEURS extrait
    private final ZonesTab zonesTab = new ZonesTab(this); // onglet ZONES extrait
    int  cdHome = 30, cdBack = 10, cdTpa = 60, cdAfk = 5;     // partagé : Features (édition) + cooldowns serveur
    int     maxHomes         = 3;
    String  webhookReports   = "";
    String  webhookSanctions = "";
    String  webhookAudit     = "";
    String  motd             = "";
    boolean mailSpyEnabled   = false;
    private boolean  pvpEnabled;
    boolean  chatLocked          = com.lkdm.dashboardadmin.DashboardAdmin.isChatLocked();
    boolean  weatherCycleEnabled = com.lkdm.dashboardadmin.DashboardAdmin.isWeatherCycleEnabled();
    boolean  daylightCycleEnabled = com.lkdm.dashboardadmin.DashboardAdmin.isDaylightCycleEnabled();
    boolean  afkAutoEnabled           = false;
    boolean  proportionalSleepEnabled = false;
    boolean  treeCapitatorEnabled     = false;
    boolean  antiSpamBypassEnabled    = false;  // onglet Chat
    boolean  fastLeafDecayEnabled     = false;
    boolean  doubleDoorEnabled          = false;
    boolean  cropTrampleEnabled         = false;
    boolean  rightClickHarvestEnabled  = false;
    boolean  dispenserHarvestEnabled   = false;

    // Report image overlay
    final java.util.Map<String, byte[]> reportImageCache  = new java.util.HashMap<>();
    private String  reportImagePlayer  = null;
    private net.minecraft.client.renderer.texture.DynamicTexture reportOverlayTex    = null;
    private net.minecraft.resources.ResourceLocation              reportOverlayTexLoc = null;

    // Onglet ZONES : tout l'état + build/render vit dans ZonesTab (champ déclaré plus bas).

    // Rôles de modération (onglet RÔLES)
    record RoleEntry(String name, java.util.Set<String> perms, java.util.List<String> members) {}
    final java.util.List<RoleEntry> roles = new java.util.ArrayList<>(); // payload ; UI dans RolesTab

    // Permissions du joueur qui regarde le dashboard (Étape 2 — filtrage GUI)
    private boolean viewerAllPerms = true;  // true = OP (voit tout)
    private final java.util.Set<String> viewerPerms = new java.util.HashSet<>();

    /** Le joueur courant peut-il accéder à cette permission (OP = tout) ? */
    boolean can(String perm) { return viewerAllPerms || viewerPerms.contains(perm); }

    // ── Accès partagé pour les classes d'onglet (même package) ──────────────────
    net.minecraft.client.gui.Font font() { return this.font; }
    <T extends net.minecraft.client.gui.components.events.GuiEventListener
             & net.minecraft.client.gui.components.Renderable
             & net.minecraft.client.gui.narration.NarratableEntry> T add(T w) {
        return addRenderableWidget(w);
    }

    private String tabPerm(int id) {
        return switch (id) {
            case 0 -> "tab.monde";  case 1 -> "tab.joueurs"; case 2 -> "tab.chat";
            case 3 -> "tab.features"; case 4 -> "tab.reports"; case 5 -> "tab.logs";
            case 6 -> "tab.zones";  case 7 -> "tab.sanctions"; case 8 -> "tab.warps";
            case 9 -> "act.manage_roles"; case 10 -> "tab.audit"; default -> "op";
        };
    }

    private String tabLabel(int id) {
        return switch (id) {
            case 0 -> Lang.t("MONDE", "WORLD");   case 1 -> Lang.t("JOUEURS", "PLAYERS");
            case 2 -> "CHAT";                      case 3 -> "FEATURES";
            case 5 -> "LOGS";                      case 8 -> "WARPS";
            case 10 -> "AUDIT";
            default -> "?";
        };
    }

    // Layout (computed in init) — package-private : lu par les classes d'onglet.
    int px, py, pw, ph;
    int cx, midX, midY;
    private final int[] tabYMap = new int[11]; // Y position of each tab button, for highlight
    // Sidebar nav layout (computed in init from ph, mirrored by render for labels/dividers)
    private int navTabH = 20;
    private int navServeurLabelY, navJoueursLabelY, navChatLabelY;
    private int navDiv1Y, navDiv2Y;

    public AdminScreen(AdminCommand.OpenAdminGuiPayload payload) {
        super(Component.literal("ADMIN DASHBOARD"));
        applyPayload(payload);
        currentTab = lastTab; // réouvre sur le dernier onglet consulté (repli si non autorisé géré dans init)
    }

    @Override
    public void onClose() {
        lastTab = currentTab; // retient l'onglet courant pour la prochaine ouverture
        super.onClose();
    }

    /** Rafraîchissement en place (action REFRESH_ADMIN) : recharge les données, garde l'onglet. */
    public void onAdminRefresh(AdminCommand.OpenAdminGuiPayload payload) {
        mutedPlayers.clear(); frozenPlayers.clear(); keepInvPlayers.clear(); afkPlayers.clear();
        reports.clear(); acceptedReports.clear(); closedReports.clear();
        schedBroadcasts.clear(); bannedPlayers.clear(); availableGroups.clear(); offlinePlayers.clear();
        warpsList.clear(); adminNotes.clear(); roles.clear(); viewerPerms.clear();
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
                if (ci > 0) {
                    java.util.List<String> ns = new java.util.ArrayList<>();
                    java.util.Collections.addAll(ns, line.substring(ci + 1).split(""));
                    adminNotes.put(line.substring(0, ci).toLowerCase(), ns);
                }
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
            // Format : nom:raison:expirationMs (raison et nom sans ':' ; expiration absente sur ancien format)
            String[] f = entry.split(":");
            if (f.length >= 1 && !f[0].isEmpty())
                bannedPlayers.add(new String[]{ f[0], f.length > 1 ? f[1] : "", f.length > 2 ? f[2] : "0" });
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
                if (feats.length >= 16) webhookAudit     = feats[15];
                if (feats.length >= 13) motd             = feats[12];
                if (feats.length >= 14) mailSpyEnabled   = Boolean.parseBoolean(feats[13]);
                if (feats.length >= 15) antiSpamBypassEnabled = Boolean.parseBoolean(feats[14]);
            }
        }
        String grpRaw = payload.groupsSerialized();
        if (!grpRaw.isEmpty()) for (String entry : grpRaw.split("\\|")) {
            String[] parts = entry.split(":", 3);
            if (parts.length == 3) availableGroups.add(parts);
        }
        String rolesRaw = payload.rolesSerialized();
        if (!rolesRaw.isEmpty()) for (String entry : rolesRaw.split("\\|")) {
            String[] f = entry.split("\\^", -1);
            if (f.length == 0 || f[0].isEmpty()) continue;
            java.util.Set<String> perms = new java.util.HashSet<>();
            if (f.length >= 2 && !f[1].isEmpty()) java.util.Collections.addAll(perms, f[1].split(","));
            java.util.List<String> members = new java.util.ArrayList<>();
            if (f.length >= 3 && !f[2].isEmpty()) java.util.Collections.addAll(members, f[2].split(","));
            roles.add(new RoleEntry(f[0], perms, members));
        }
        String vp = payload.viewerPerms();
        viewerAllPerms = "*".equals(vp);
        if (!viewerAllPerms && !vp.isEmpty()) java.util.Collections.addAll(viewerPerms, vp.split(","));
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
            addRenderableWidget(btn(Lang.t("§cFermer", "§cClose"), b -> {
                reportImagePlayer = null;
                init();
            }).bounds(width / 2 - 40, closeBtnY, 80, 20).build());
            return;
        }

        // ── Liste des notes admin (overlay) ──────────────────────────────────────
        if (showNotesList) {
            // Boutons × de suppression, alignés sur les lignes de renderNotesOverlay (1 ligne = 1 note).
            int ow = Math.min(380, width - 40), oh = ph - 16;
            int ox = (width - ow) / 2, ot = py + 8;
            int listTop = ot + 22, listBot = ot + oh - 30;
            int entryH = 14;
            int maxVis = Math.max(1, (listBot - listTop) / entryH);
            java.util.List<String[]> rows = notesRows();
            int maxScroll = Math.max(0, rows.size() - maxVis);
            notesScroll = Math.max(0, Math.min(notesScroll, maxScroll));
            int ny = listTop;
            for (int i = notesScroll; i < rows.size() && i < notesScroll + maxVis; i++) {
                final String[] row = rows.get(i);
                addRenderableWidget(btn("§c×", b -> {
                    send("DEL_NOTE", row[0], row[1]);
                    java.util.List<String> ns = adminNotes.get(row[0]);
                    if (ns != null) {
                        int idx = Integer.parseInt(row[1]);
                        if (idx >= 0 && idx < ns.size()) ns.remove(idx);
                        if (ns.isEmpty()) adminNotes.remove(row[0]);
                    }
                    init();
                }).bounds(ox + ow - 24, ny + 1, 16, entryH - 3).build());
                ny += entryH;
            }
            int closeBtnY = py + ph - 28;
            addRenderableWidget(btn(Lang.t("§cFermer", "§cClose"), b -> { showNotesList = false; init(); })
                .bounds(width / 2 - 40, closeBtnY, 80, 20).build());
            return;
        }

        // ── Confirmation dialogs ─────────────────────────────────────────────────
        if (confirmUnbanPlayer != null) {
            int dw = 240, dh = 80, dx = (width - dw) / 2, dy = (height - dh) / 2;
            final String pname = confirmUnbanPlayer;
            addRenderableWidget(btn(Lang.t("§aCONFIRMER DÉBAN", "§aCONFIRM UNBAN"), b -> {
                send("UNBAN", pname, "");
                bannedPlayers.removeIf(e -> e[0].equalsIgnoreCase(pname));
                sanctionsEntries.removeIf(e -> "BAN".equals(e[1]) && e[2].equalsIgnoreCase(pname));
                confirmUnbanPlayer = null;
                init();
            }).bounds(dx + 10, dy + 48, 105, 20).build());
            addRenderableWidget(btn(Lang.t("ANNULER", "CANCEL"), b -> { confirmUnbanPlayer = null; init(); })
                .bounds(dx + 125, dy + 48, 105, 20).build());
            return;
        }
        if (confirmRun != null) {
            int dw = 240, dh = 80, dx = (width - dw) / 2, dy = (height - dh) / 2;
            addRenderableWidget(btn(Lang.t("§cCONFIRMER", "§cCONFIRM"), b -> {
                Runnable r = confirmRun; confirmRun = null; confirmLabel = null;
                r.run(); init();
            }).bounds(dx + 10, dy + 48, 105, 20).build());
            addRenderableWidget(btn(Lang.t("ANNULER", "CANCEL"), b -> { confirmRun = null; confirmLabel = null; init(); })
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
            String[] durLabels = Lang.fr() ? new String[]{"1j", "3j", "7j", "∞"} : new String[]{"1d", "3d", "7d", "∞"};
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

            addRenderableWidget(btn(Lang.t("§aVALIDER", "§aCONFIRM"), b -> {
                long custom = banBoxVal(banDayBox) * 86400L + banBoxVal(banHourBox) * 3600L
                            + banBoxVal(banMinBox) * 60L + banBoxVal(banSecBox);
                long secs = custom > 0 ? custom : banDurationSecs;
                send("BAN", selPlayer, secs + "\t" + banReasonBox.getValue());
                isBanning = false; banDurationSecs = 0; init();
            }).bounds(dx + 10, dy + 144, 105, 20).build());
            addRenderableWidget(btn(Lang.t("§cANNULER", "§cCANCEL"), b -> {
                isBanning = false; banDurationSecs = 0; init();
            }).bounds(dx + 125, dy + 144, 105, 20).build());
            return;
        }
        if (isKicking) {
            int dw = 240, dh = 80, dx = (width - dw) / 2, dy = (height - dh) / 2;
            addRenderableWidget(btn(Lang.t("§cCONFIRMER KICK", "§cCONFIRM KICK"), b -> { send("KICK", selPlayer, ""); isKicking = false; init(); }).bounds(dx + 10,  dy + 48, 105, 20).build());
            addRenderableWidget(btn(Lang.t("ANNULER", "CANCEL"), b -> { isKicking = false; init(); }).bounds(dx + 125, dy + 48, 105, 20).build());
            return;
        }
        if (isRemovingMobs) {
            int dw = 240, dh = 80, dx = (width - dw) / 2, dy = (height - dh) / 2;
            addRenderableWidget(btn(Lang.t("§cSUPPRIMER MOBS", "§cREMOVE MOBS"), b -> { send("REMOVE_MOBS", "", ""); isRemovingMobs = false; init(); }).bounds(dx + 10,  dy + 48, 105, 20).build());
            addRenderableWidget(btn(Lang.t("ANNULER", "CANCEL"), b -> { isRemovingMobs = false; init(); }).bounds(dx + 125, dy + 48, 105, 20).build());
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
            addRenderableWidget(btn(Lang.t("§cPROGRAMMER", "§cSCHEDULE"), b -> {
                send("SCHEDULE_RESTART", "", String.valueOf(restartMinutes));
                isRestarting = false; init();
            }).bounds(dx + 10, dy + 74, 105, 20).build());
            addRenderableWidget(btn(Lang.t("ANNULER", "CANCEL"), b -> { isRestarting = false; init(); })
                .bounds(dx + 125, dy + 74, 105, 20).build());
            return;
        }

        // ── Sidebar tabs (adaptive vertical layout: compresses at high GUI scale so the
        //    lower tabs never collide with the bottom-anchored FERMER button) ───────────
        int unresolved = reports.size() + acceptedReports.size();
        int fermerH   = 20;

        // Repli onglet : si le joueur (rôle) n'a pas l'onglet courant, basculer sur le 1er autorisé.
        if (!can(tabPerm(currentTab))) {
            for (int id : new int[]{0, 3, 6, 8, 1, 5, 7, 9, 10, 2, 4}) if (can(tabPerm(id))) { currentTab = id; break; }
        }

        if (viewerAllPerms) {
            int navTop    = py + 36;
            int navBottom = py + ph - 8 - fermerH;   // tabs must end above FERMER
            int gap       = 6;
            int labelH    = 11;
            int avail     = navBottom - navTop;
            int fixedOverhead = 3 * labelH + 2 * gap; // 3 section labels + 2 inter-group gaps
            int tabSlot   = Math.max(15, Math.min(22, (avail - fixedOverhead) / 11));
            navTabH       = Math.min(20, tabSlot - 2);

            int y = navTop;
            // ─ SERVEUR group ─
            navServeurLabelY = y; y += labelH;
            tabYMap[0] = y; tab(Lang.t("MONDE", "WORLD"), 0, y); y += tabSlot;
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
            tabYMap[1] = y; tab(Lang.t("JOUEURS", "PLAYERS"), 1, y); y += tabSlot;
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
            tabYMap[9] = y; tab(Lang.t("RÔLES", "ROLES"), 9, y); y += tabSlot;
            tabYMap[10] = y;
            boolean auditActive = currentTab == 10;
            addRenderableWidget(Button.builder(
                Component.literal("AUDIT").withStyle(auditActive
                    ? s -> s.withColor(0x00E5FF).withBold(true)
                    : s -> s.withColor(0x777777)),
                b -> { send("GET_AUDIT", "", ""); currentTab = 10; init(); })
                .bounds(px + 5, y, SIDE_W - 10, navTabH).build());
            y += tabSlot;
            navDiv2Y = y; y += gap;

            // ─ CHAT group ─
            navChatLabelY = y; y += labelH;
            tabYMap[2] = y; tab("CHAT",     2, y); y += tabSlot;
            tabYMap[4] = y;
            tab("REPORTS" + (unresolved == 0 ? "" : " §c(" + unresolved + ")"), 4, y);
        } else {
            buildModoNav(unresolved, fermerH);
        }

        addRenderableWidget(btn(Lang.t("FERMER", "CLOSE"), b -> onClose()).bounds(px + 5, py + ph - 6 - fermerH, SIDE_W - 10, fermerH).build());

        // ── Tab content (uniquement si le joueur a la permission de l'onglet) ──────
        if (can(tabPerm(currentTab))) switch (currentTab) {
            case 0 -> mondeTab.build();
            case 1 -> joueursTab.build();
            case 2 -> chatTab.build();
            case 3 -> featuresTab.build();
            case 4 -> reportsTab.build();
            case 5 -> logsTab.build();
            case 6 -> zonesTab.build();
            case 7 -> sanctionsTab.build();
            case 8 -> warpsTab.build();
            case 9 -> rolesTab.build();
            case 10 -> auditTab.build();
        }
    }

    private void tab(String label, int id, int y) {
        boolean active = currentTab == id;
        Component txt = Component.literal(label).withStyle(
            active ? s -> s.withColor(0x00E5FF).withBold(true) : s -> s.withColor(0x777777));
        addRenderableWidget(Button.builder(txt, b -> { currentTab = id; init(); })
            .bounds(px + 5, y, SIDE_W - 10, navTabH).build());
    }

    /** Sidebar simplifiée pour un modérateur (non-OP) : liste plate des onglets autorisés. */
    private void buildModoNav(int unresolved, int fermerH) {
        int navTop = py + 36;
        int navBottom = py + ph - 8 - fermerH;
        int[] order = {0, 3, 6, 8, 1, 5, 7, 9, 10, 2, 4};
        java.util.List<Integer> vis = new java.util.ArrayList<>();
        for (int id : order) if (can(tabPerm(id))) vis.add(id);
        int slot = Math.max(15, Math.min(24, (navBottom - navTop) / Math.max(1, vis.size())));
        navTabH = Math.min(20, slot - 2);
        // Pas d'en-têtes de groupe en mode modo : on renvoie les labels/dividers hors écran.
        navServeurLabelY = navJoueursLabelY = navChatLabelY = -100;
        navDiv1Y = navDiv2Y = -100;

        int y = navTop;
        for (int id : vis) { tabYMap[id] = y; addModoTab(id, unresolved, y); y += slot; }
    }

    private void addModoTab(int id, int unresolved, int y) {
        switch (id) {
            case 6 -> { // ZONES (action spéciale)
                boolean a = currentTab == 6;
                addRenderableWidget(Button.builder(Component.literal("ZONES").withStyle(
                        a ? s -> s.withColor(0x00E5FF).withBold(true) : s -> s.withColor(0x777777)),
                    b -> { send("OPEN_ZONES", "", ""); currentTab = 6; init(); })
                    .bounds(px + 5, y, SIDE_W - 10, navTabH).build());
            }
            case 7 -> { // SANCTIONS (action spéciale)
                boolean a = currentTab == 7;
                addRenderableWidget(Button.builder(Component.literal("SANCTIONS").withStyle(
                        a ? s -> s.withColor(0x00E5FF).withBold(true) : s -> s.withColor(0x777777)),
                    b -> { send("GET_SANCTIONS", "", ""); currentTab = 7; init(); })
                    .bounds(px + 5, y, SIDE_W - 10, navTabH).build());
            }
            case 4 -> tab("REPORTS" + (unresolved == 0 ? "" : " §c(" + unresolved + ")"), 4, y);
            case 9 -> tab(Lang.t("RÔLES", "ROLES"), 9, y);
            case 10 -> { // AUDIT (action spéciale : charge le journal)
                boolean a = currentTab == 10;
                addRenderableWidget(Button.builder(Component.literal("AUDIT").withStyle(
                        a ? s -> s.withColor(0x00E5FF).withBold(true) : s -> s.withColor(0x777777)),
                    b -> { send("GET_AUDIT", "", ""); currentTab = 10; init(); })
                    .bounds(px + 5, y, SIDE_W - 10, navTabH).build());
            }
            default -> tab(tabLabel(id), id, y);
        }
    }

    Button.Builder btn(String label, Button.OnPress handler) {
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


    // ─── JOUEURS ─────────────────────────────────────────────────────────────────



    // ─── FEATURES ────────────────────────────────────────────────────────────────


    // ─── REPORTS ─────────────────────────────────────────────────────────────────


    void send(String action, String target, String value) {
        PacketDistributor.sendToServer(new AdminActionPayload(action, target, value));
    }

    /** Ouvre un dialog de confirmation générique avant une action destructive (delete zone/rôle/warp…). */
    void askConfirm(String label, Runnable onConfirm) {
        confirmLabel = label;
        confirmRun   = onConfirm;
        init();
    }

    public void onReportImageReceived(String playerName, byte[] data) {
        reportImageCache.put(playerName, data);
        showReportOverlay(playerName, data);
        init();
    }

    void showReportOverlay(String playerName, byte[] data) {
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

        // L'overlay NOTES remplace tout l'écran : dessiner le contenu en dessous ferait
        // remonter ses textes au-dessus du panneau (les fills sont rendus avant les
        // textes dans le batch GUI, quel que soit l'ordre d'appel).
        if (showNotesList) {
            renderNotesOverlay(g, mx, my);
            super.render(g, mx, my, delta);
            // Tooltip : note complète si elle est tronquée et survolée.
            if (hoverNoteFull != null)
                g.renderTooltip(font, font.split(Component.literal(hoverNoteFull), 240), mx, my);
            return;
        }

        g.fill(cx, py, px + pw, py + ph, C_BG);
        g.fill(px, py, cx,      py + ph, C_SIDE);

        // Sidebar header
        g.fill(px, py, cx, py + 30, 0xFF080808);
        g.drawCenteredString(font,
            Component.literal("DASHBOARD").withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            px + SIDE_W / 2, py + 11, 0xFFFFFFFF);
        g.fill(px + 6, py + 29, cx - 6, py + 30, C_ACCENT);

        // Sidebar section labels (positions computed in init, adaptive to ph)
        g.drawString(font, Lang.t("SERVEUR", "SERVER"),  px + 7, navServeurLabelY, 0xFF666666);
        g.drawString(font, Lang.t("JOUEURS", "PLAYERS"), px + 7, navJoueursLabelY, 0xFF666666);
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
        String[] titles = Lang.fr()
            ? new String[]{ "GESTION DU MONDE", "JOUEURS EN LIGNE", "CHAT & ANNONCES", "FONCTIONNALITÉS", "REPORTS", "LOGS JOUEURS", "GESTION DES ZONES", "HISTORIQUE SANCTIONS", "WARPS", "RÔLES DE MODÉRATION", "JOURNAL D'ACTIONS ADMIN" }
            : new String[]{ "WORLD MANAGEMENT", "ONLINE PLAYERS", "CHAT & ANNOUNCEMENTS", "FEATURES", "REPORTS", "PLAYER LOGS", "ZONE MANAGEMENT", "SANCTIONS HISTORY", "WARPS", "MODERATION ROLES", "ADMIN ACTION LOG" };
        g.fill(cx, py, px + pw, py + 26, C_HBAR);
        g.drawCenteredString(font,
            Component.literal(titles[currentTab]).withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            midX, py + 9, 0xFFFFFFFF);
        g.fill(cx, py + 25, px + pw, py + 26, C_DIV);

        // Texte du contenu masqué quand un dialogue/overlay est ouvert : le batching GUI
        // rend les textes après les fills, ils traverseraient sinon le panneau du dialogue.
        boolean dialogOpen = confirmUnbanPlayer != null || isBanning || isKicking
            || isRemovingMobs || isRestarting || confirmRun != null
            || (reportImagePlayer != null && reportOverlayTexLoc != null);
        if (!dialogOpen && can(tabPerm(currentTab))) {
            switch (currentTab) {
                case 0 -> mondeTab.render(g);
                case 1 -> joueursTab.render(g);
                case 2 -> chatTab.render(g);
                case 3 -> featuresTab.render(g);
                case 4 -> reportsTab.render(g, mx, my);
                case 5 -> logsTab.render(g);
                case 6 -> zonesTab.render(g);
                case 7 -> sanctionsTab.render(g);
                case 8 -> warpsTab.render(g);
                case 9 -> rolesTab.render(g, mx, my);
                case 10 -> auditTab.render(g);
            }
        }

        // Dialog overlays
        if (confirmUnbanPlayer != null || isBanning || isKicking || isRemovingMobs || isRestarting) {
            int dh = isBanning ? 174 : isRestarting ? 104 : 80;
            int dw = 240, dx = (width - dw) / 2, dy = (height - dh) / 2;
            g.fill(0, 0, this.width, this.height, 0x88000000);
            g.fill(dx, dy, dx + dw, dy + dh, 0xFF1A1A1A);
            g.fill(dx, dy, dx + dw, dy + 2, C_ACCENT);
            String title = isBanning     ? "BAN" + Lang.t(" : ", ": ") + selPlayer
                : isKicking              ? "KICK" + Lang.t(" : ", ": ") + selPlayer + Lang.t(" ?", "?")
                : isRemovingMobs         ? Lang.t("Supprimer les mobs ?", "Remove mobs?")
                : isRestarting           ? Lang.t("Redémarrer le serveur ?", "Restart the server?")
                : Lang.t("Déban : ", "Unban: ") + confirmUnbanPlayer + Lang.t(" ?", "?");
            g.drawCenteredString(font,
                Component.literal("§c⚠ §f" + title).withStyle(s -> s.withBold(true)),
                dx + dw / 2, dy + 8, 0xFFFFFFFF);
            if (isBanning) {
                g.drawString(font, Lang.t("§8Raison :", "§8Reason:"), dx + 10, dy + 24, 0xFF555555);
                g.drawString(font, Lang.t("§8Durée :", "§8Duration:"),  dx + 10, dy + 60, 0xFF555555);
                g.drawString(font, Lang.t("§8Durée perso §7(prioritaire si renseignée) §8:",
                    "§8Custom duration §7(overrides presets) §8:"), dx + 10, dy + 94, 0xFF555555);
                g.drawString(font, Lang.t("§7j", "§7d"), dx + 46,  dy + 109, 0xFF888888);
                g.drawString(font, "§7h", dx + 102, dy + 109, 0xFF888888);
                g.drawString(font, "§7m", dx + 158, dy + 109, 0xFF888888);
                g.drawString(font, "§7s", dx + 214, dy + 109, 0xFF888888);
            }
            if (isRestarting) {
                g.drawString(font, Lang.t("§8Délai avant arrêt (annonces auto) :",
                    "§8Delay before shutdown (auto announcements):"), dx + 10, dy + 26, 0xFF555555);
            }
        }

        // Dialog de confirmation générique (delete zone/rôle/warp…)
        if (confirmRun != null) {
            int dw = 240, dh = 80, dx = (width - dw) / 2, dy = (height - dh) / 2;
            g.fill(0, 0, this.width, this.height, 0x88000000);
            g.fill(dx, dy, dx + dw, dy + dh, 0xFF1A1A1A);
            g.fill(dx, dy, dx + dw, dy + 2, C_ACCENT);
            g.drawCenteredString(font,
                Component.literal("§c⚠ " + Lang.t("CONFIRMER", "CONFIRM")).withStyle(s -> s.withBold(true)),
                dx + dw / 2, dy + 8, 0xFFFFFFFF);
            int ly = dy + 24;
            for (var l : font.split(Component.literal("§f" + (confirmLabel == null ? "" : confirmLabel)), dw - 20)) {
                g.drawCenteredString(font, l, dx + dw / 2, ly, 0xFFFFFFFF);
                ly += 11;
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
                Component.literal(Lang.t("§bCapture : §f", "§bScreenshot: §f") + reportImagePlayer),
                ix + imgW / 2, iy - 15, 0xFFFFFFFF);
            g.blit(reportOverlayTexLoc,
                ix, iy, 0f, 0f, imgW, imgH, imgW, imgH);
        }

        g.drawString(font, "@LKDM", px + pw - font.width("@LKDM") - 4, py + ph - 10, 0x55AAAAAA, false);

        // Tooltip : message complet d'un report survolé (uniquement si aucun overlay ouvert)
        if (hoverReportMsg != null && reportImagePlayer == null
                && !isBanning && !isKicking && !isRemovingMobs && confirmUnbanPlayer == null) {
            g.renderTooltip(font, font.split(Component.literal("§f" + hoverReportMsg), 220), mx, my);
        }

        super.render(g, mx, my, delta);
    }



    /** Aplatit les notes en lignes [nomLower, index, texte] triées par joueur — partagé init/render. */
    private java.util.List<String[]> notesRows() {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>(adminNotes.keySet());
        java.util.Collections.sort(names);
        for (String name : names) {
            java.util.List<String> ns = adminNotes.get(name);
            for (int i = 0; i < ns.size(); i++) rows.add(new String[]{ name, String.valueOf(i), ns.get(i) });
        }
        return rows;
    }

    private void renderNotesOverlay(GuiGraphics g, int mx, int my) {
        hoverNoteFull = null;
        int ow = Math.min(380, width - 40), oh = ph - 16;
        int ox = (width - ow) / 2, ot = py + 8;
        g.fill(ox, ot, ox + ow, ot + oh, 0xFF1A1A1A);
        g.fill(ox, ot, ox + ow, ot + 2, C_ACCENT);
        g.drawCenteredString(font,
            Component.literal(Lang.t("NOTES ADMIN", "ADMIN NOTES")).withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            ox + ow / 2, ot + 8, 0xFFFFFFFF);

        int listTop = ot + 22, listBot = ot + oh - 30;
        if (adminNotes.isEmpty()) {
            g.drawCenteredString(font, Lang.t("§8Aucune note enregistrée", "§8No notes recorded"), ox + ow / 2, (listTop + listBot) / 2 - 4, 0xFF444444);
            g.drawCenteredString(font, Lang.t("§8Sélectionnez un joueur puis remplissez le champ Note.",
                "§8Select a player and fill in the Note field."), ox + ow / 2, (listTop + listBot) / 2 + 8, 0xFF333333);
            return;
        }

        java.util.List<String[]> rows = notesRows();  // [nomLower, index, texte]
        int entryH = 14;
        int maxVis = Math.max(1, (listBot - listTop) / entryH);
        int maxScroll = Math.max(0, rows.size() - maxVis);
        notesScroll = Math.max(0, Math.min(notesScroll, maxScroll));

        g.enableScissor(ox + 2, listTop, ox + ow - 8, listBot);
        int y = listTop;
        for (int i = notesScroll; i < rows.size() && i < notesScroll + maxVis; i++) {
            String[] row = rows.get(i);
            if (i % 2 == 0) g.fill(ox + 4, y, ox + ow - 10, y + entryH - 2, C_ROW);
            g.fill(ox + 4, y, ox + 5, y + entryH - 2, 0xFFFFAA00);
            String prefix = "§e" + row[0] + " §8» ";
            int avail = ow - 32 - font.width(prefix);
            String note = row[2];
            boolean trimmed = false;
            while (font.width("§7" + note) > avail && note.length() > 1) { note = note.substring(0, note.length() - 1); trimmed = true; }
            g.drawString(font, prefix + "§7" + note + (trimmed ? "…" : ""), ox + 10, y + 3, 0xFFFFFFFF);
            // Survol d'une note tronquée → mémorise le texte complet pour le tooltip.
            if (trimmed && mx >= ox + 4 && mx <= ox + ow - 26 && my >= y && my < y + entryH - 2)
                hoverNoteFull = "§e" + row[0] + " §8» §f" + row[2];
            y += entryH;
        }
        g.disableScissor();

        if (rows.size() > maxVis) {
            int sbX = ox + ow - 7, sbH = listBot - listTop;
            int thumbH = Math.max(8, sbH * maxVis / rows.size());
            int thumbY = maxScroll > 0 ? listTop + (sbH - thumbH) * notesScroll / maxScroll : listTop;
            g.fill(sbX, listTop, sbX + 3, listBot, 0x33FFFFFF);
            g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, C_ACCENT);
        }
    }

    /** Fiche Activité du joueur sélectionné : dernière connexion, sanctions, reports, note admin. */




    // ─── LOGS ────────────────────────────────────────────────────────────────────



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

    // ─── AUDIT (journal d'actions admin) ──────────────────────────────────────────

    public void onAuditReceived(String data) { auditTab.onReceived(data); }

    public void onLogsReceived(String playerName, String logsSerialized) { logsTab.onReceived(playerName, logsSerialized); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showNotesList) {
            notesScroll = Math.max(0, notesScroll - (int) scrollY);
            return true;
        }
        if (currentTab == 5 && logsTab.hasEntries()) {
            return logsTab.mouseScrolled(scrollY);
        }
        if (currentTab == 7 && sanctionsTab.hasEntries()) {
            return sanctionsTab.mouseScrolled(scrollY);
        }
        if (currentTab == 10 && auditTab.hasEntries()) {
            return auditTab.mouseScrolled(scrollY);
        }
        if (currentTab == 6 && zonesTab.mouseScrolled(mouseX, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ─── RÔLES DE MODÉRATION ─────────────────────────────────────────────────────


    String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    void lbl(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x, y, 0xFF888888);
    }

    public void onZoneUpdate(OpenZonePayload payload) { zonesTab.onUpdate(payload); }

    @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float delta) {}
    @Override public boolean isPauseScreen() { return false; }
}

