package com.lkdm.dashboardadmin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Onglet AUDIT du dashboard admin : journal d'actions (lecture seule) avec filtre live
 * (admin / action / cible / détail) et export Discord.
 *
 * Premier onglet extrait de {@link AdminScreen} (refactor de découpe). L'état propre à
 * l'onglet vit ici ; les primitives partagées (layout, font, helpers send/btn/truncate,
 * ajout de widgets) sont fournies par {@link AdminScreen} via des accès package-private.
 */
class AuditTab {

    private final AdminScreen s;

    private List<String[]> entries = new ArrayList<>(); // [ts, admin, action, target, detail]
    private int     scroll = 0;
    private String  filter = "";
    private EditBox searchBox;

    AuditTab(AdminScreen screen) { this.s = screen; }

    boolean hasEntries() { return !entries.isEmpty(); }

    void build() {
        // Bouton d'export du journal vers le webhook Discord (conformité) — en-tête, à droite.
        s.add(s.btn(Lang.t("§eEXPORTER", "§eEXPORT"), b -> s.send("EXPORT_AUDIT", "", ""))
            .bounds(s.px + s.pw - 96, s.py + 5, 88, 18).build());
        // Champ de recherche (filtre live admin / action / cible / détail) — ligne dédiée
        // SOUS l'en-tête, pour ne pas chevaucher le titre centré « ADMIN ACTION LOG ».
        String hint = Lang.t("Filtrer (admin, action, joueur…)", "Filter (admin, action, player…)");
        // Aligné sur la zone de contenu (cx+4 .. px+pw-8), comme les lignes d'audit —
        // ne déborde plus sur la sidebar à gauche ni sur le bord droit.
        searchBox = new EditBox(s.font(), s.cx + 4, s.py + 28, s.px + s.pw - s.cx - 12, 16, Component.literal(hint));
        searchBox.setHint(Component.literal(hint));
        searchBox.setMaxLength(48);
        searchBox.setValue(filter);
        // Pas de init() ici : render lit le filtre à chaque frame → pas de perte de focus.
        searchBox.setResponder(v -> { filter = v; scroll = 0; });
        s.add(searchBox);
    }

    void render(GuiGraphics g) {
        int panelTop = s.py + 48; // sous la ligne de recherche (py+28..44)
        int panelBot = s.py + s.ph - 6;
        int sbX      = s.px + s.pw - 6;
        int entryH   = 18;

        if (entries.isEmpty()) {
            g.drawCenteredString(s.font(), Lang.t("§8Aucune action enregistrée", "§8No actions recorded"), s.midX, s.py + s.ph / 2, 0xFF444444);
            return;
        }
        List<String[]> list = filtered();
        if (list.isEmpty()) {
            g.drawCenteredString(s.font(), Lang.t("§8Aucun résultat pour ce filtre", "§8No result for this filter"), s.midX, s.py + s.ph / 2, 0xFF444444);
            return;
        }

        int maxVis    = Math.max(1, (panelBot - panelTop) / entryH);
        int maxScroll = Math.max(0, list.size() - maxVis);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0)         scroll = 0;

        g.enableScissor(s.cx, panelTop, sbX - 2, panelBot);
        int y = panelTop;
        for (int i = scroll; i < list.size() && i < scroll + maxVis; i++) {
            String[] e = list.get(i); // [ts, admin, action, target, detail]
            if (i % 2 == 0) g.fill(s.cx + 4, y, sbX - 4, y + entryH - 2, AdminScreen.C_ROW);
            g.fill(s.cx + 4, y, s.cx + 5, y + entryH - 2, 0xFF00E5FF);
            String line = "§8" + e[0] + " §b" + e[1] + " §8» §f" + e[2]
                + ("—".equals(e[3]) ? "" : " §7" + e[3])
                + ("—".equals(e[4]) ? "" : " §8(" + s.truncate(e[4], 24) + ")");
            g.drawString(s.font(), line, s.cx + 10, y + 4, 0xFFAAAAAA, false);
            y += entryH;
        }
        g.disableScissor();

        if (list.size() > maxVis) {
            int sbH    = panelBot - panelTop;
            int thumbH = Math.max(8, sbH * maxVis / list.size());
            int thumbY = maxScroll > 0 ? panelTop + (sbH - thumbH) * scroll / maxScroll : panelTop;
            g.fill(sbX, panelTop, sbX + 4, panelBot, 0x33FFFFFF);
            g.fill(sbX, thumbY,   sbX + 4, thumbY + thumbH, AdminScreen.C_ACCENT);
        }
    }

    /** Journal filtré par {@link #filter} (insensible à la casse), sur admin/action/cible/détail. */
    private List<String[]> filtered() {
        if (filter == null || filter.isBlank()) return entries;
        String q = filter.toLowerCase(Locale.ROOT);
        List<String[]> out = new ArrayList<>();
        for (String[] e : entries) // [ts, admin, action, target, detail]
            if (e[1].toLowerCase(Locale.ROOT).contains(q)
                || e[2].toLowerCase(Locale.ROOT).contains(q)
                || e[3].toLowerCase(Locale.ROOT).contains(q)
                || e[4].toLowerCase(Locale.ROOT).contains(q))
                out.add(e);
        return out;
    }

    void onReceived(String data) {
        entries.clear();
        if (!data.isEmpty()) {
            for (String line : data.split("\\|")) {
                String[] parts = line.split("\t", 5);
                if (parts.length == 5) entries.add(parts);
            }
        }
        scroll = 0;
        s.init();
    }

    /** Molette : renvoie true si l'event est consommé. */
    boolean mouseScrolled(double scrollY) {
        if (entries.isEmpty()) return false;
        int maxVis = Math.max(1, (s.py + s.ph - 6 - (s.py + 48)) / 18);
        int maxScroll = Math.max(0, filtered().size() - maxVis);
        scroll = Math.max(0, Math.min(scroll - (int) (scrollY * 3), maxScroll));
        s.init();
        return true;
    }
}
