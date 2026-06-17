package com.lkdm.dashboardadmin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Onglet LOGS du dashboard admin : liste des joueurs en ligne (+ chat global) à gauche,
 * journal du joueur sélectionné à droite. Le joueur sélectionné ({@code selPlayer}) est
 * un état partagé avec l'onglet Joueurs → il reste dans {@link AdminScreen}. L'état propre
 * au journal (joueur chargé, lignes, scroll) vit ici.
 */
class LogsTab {

    private final AdminScreen s;

    private String       player  = null; // joueur dont les logs sont chargés
    private List<String> entries = new ArrayList<>();
    private int          scroll  = 0;
    private String       filter  = "";   // filtre live sur les lignes du journal
    private EditBox      searchBox;

    LogsTab(AdminScreen screen) { this.s = screen; }

    boolean hasEntries() { return !entries.isEmpty(); }

    /** Réinitialise l'affichage du journal (avant une nouvelle requête GET_LOGS / GET_CHAT). */
    void reset() { player = null; entries = new ArrayList<>(); scroll = 0; filter = ""; }

    /** Lignes filtrées par {@link #filter} (insensible à la casse). */
    private List<String> filtered() {
        if (filter == null || filter.isBlank()) return entries;
        String q = filter.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String e : entries) if (e.toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        return out;
    }

    void build() {
        if (Minecraft.getInstance().getConnection() == null) return;
        java.util.Collection<PlayerInfo> players = Minecraft.getInstance().getConnection().getOnlinePlayers();

        // Historique du chat public du serveur (réutilise le visualiseur de logs)
        String chatLbl = Lang.t("CHAT GLOBAL", "GLOBAL CHAT");
        s.add(s.btn(("Chat global".equals(s.selPlayer) ? "§b§l" : "§b") + chatLbl, b -> {
            s.selPlayer = "Chat global";
            reset();
            s.send("GET_CHAT", "", "");
            s.init();
        }).bounds(s.cx + 4, s.py + 28, 90, 14).build());

        int yOff = s.py + 48;
        for (PlayerInfo info : players) {
            if (info.getProfile() == null) continue;
            String name = info.getProfile().getName();
            boolean sel = name.equals(s.selPlayer);
            Component lbl = Component.literal((sel ? "§f§l" : "§7") + name);
            s.add(Button.builder(lbl, b -> {
                s.selPlayer = name;
                reset();
                s.send("GET_LOGS", name, "");
                s.init();
            }).bounds(s.cx + 16, yOff, 78, 14).build());
            yOff += 16;
        }

        // Champ de filtre des lignes (panneau droit) — seulement quand un journal est chargé.
        if (player != null && !entries.isEmpty()) {
            int divX = s.cx + 98;
            String hint = Lang.t("Filtrer les lignes…", "Filter lines…");
            searchBox = new EditBox(s.font(), divX + 8, s.py + 48, s.px + s.pw - (divX + 8) - 8, 14, Component.literal(hint));
            searchBox.setHint(Component.literal(hint));
            searchBox.setMaxLength(48);
            searchBox.setValue(filter);
            searchBox.setResponder(v -> { filter = v; scroll = 0; });
            s.add(searchBox);
        }
    }

    void render(GuiGraphics g) {
        int divX = s.cx + 98;
        g.fill(divX, s.py + 26, divX + 1, s.py + s.ph - 5, AdminScreen.C_DIV);

        // Têtes de skin dans la liste de gauche
        if (Minecraft.getInstance().getConnection() != null) {
            int yOff = s.py + 48;
            for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
                if (info.getProfile() == null) continue;
                PlayerFaceRenderer.draw(g, info.getSkin(), s.cx + 5, yOff + 3, 8);
                yOff += 16;
            }
        }

        if (s.selPlayer == null) {
            g.drawCenteredString(s.font(), Lang.t("§8← Sélectionnez un joueur", "§8← Select a player"), (divX + s.px + s.pw) / 2, s.py + s.ph / 2, 0xFF555555);
            return;
        }

        // Header
        g.fill(divX + 1, s.py + 26, s.px + s.pw, s.py + 46, 0xFF0A0A0A);
        g.drawString(s.font(), "§e§l" + s.selPlayer + " §8— Logs", divX + 8, s.py + 31, 0xFFFFFFFF);
        g.fill(divX + 1, s.py + 45, s.px + s.pw, s.py + 46, AdminScreen.C_DIV);

        if (player == null) {
            g.drawCenteredString(s.font(), Lang.t("§8Chargement des logs...", "§8Loading logs..."), (divX + s.px + s.pw) / 2, s.py + s.ph / 2, 0xFF666666);
            return;
        }

        if (entries.isEmpty()) {
            g.drawCenteredString(s.font(), Lang.t("§8Aucun log", "§8No logs"), (divX + s.px + s.pw) / 2, s.py + s.ph / 2, 0xFF444444);
            return;
        }

        List<String> list = filtered();
        int entryH    = 11;
        int panelTop  = s.py + 66;   // sous la ligne de filtre (py+48..62)
        int panelBot  = s.py + s.ph - 6;
        if (list.isEmpty()) {
            g.drawCenteredString(s.font(), Lang.t("§8Aucun résultat", "§8No result"), (divX + s.px + s.pw) / 2, s.py + s.ph / 2, 0xFF444444);
            return;
        }
        int visH      = panelBot - panelTop;
        int maxVis    = Math.max(1, visH / entryH);
        int maxScroll = Math.max(0, list.size() - maxVis);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0)         scroll = 0;

        int sbX = s.px + s.pw - 6;

        g.enableScissor(divX + 2, panelTop, sbX - 2, panelBot);
        int y = panelTop;
        for (int i = scroll; i < list.size() && i < scroll + maxVis; i++) {
            if (i % 2 == 0) g.fill(divX + 2, y - 1, sbX - 2, y + entryH, 0x0AFFFFFF);
            g.drawString(s.font(), "§7" + list.get(i), divX + 6, y + 1, 0xFFAAAAAA);
            y += entryH;
        }
        g.disableScissor();

        // Scrollbar
        if (list.size() > maxVis) {
            int sbH    = panelBot - panelTop;
            int thumbH = Math.max(8, sbH * maxVis / list.size());
            int thumbY = maxScroll > 0 ? panelTop + (sbH - thumbH) * scroll / maxScroll : panelTop;
            g.fill(sbX, panelTop, sbX + 4, panelBot, 0x33FFFFFF);
            g.fill(sbX, thumbY,   sbX + 4, thumbY + thumbH, AdminScreen.C_ACCENT);
        }
    }

    void onReceived(String playerName, String logsSerialized) {
        this.player  = playerName;
        this.entries = logsSerialized.isEmpty()
            ? new ArrayList<>()
            : new ArrayList<>(java.util.Arrays.asList(logsSerialized.split("\n")));
        this.scroll  = 0;
        s.init();
    }

    boolean mouseScrolled(double scrollY) {
        if (entries.isEmpty()) return false;
        scroll -= (int) (scrollY * 3);
        if (scroll < 0) scroll = 0;
        return true;
    }
}
