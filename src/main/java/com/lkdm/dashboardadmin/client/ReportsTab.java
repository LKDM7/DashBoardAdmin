package com.lkdm.dashboardadmin.client;

import net.minecraft.client.gui.GuiGraphics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Onglet REPORTS du dashboard admin : EN ATTENTE / EN COURS / CLÔTURÉS, avec accept/refus/
 * clôture et bouton [IMG] (capture jointe). Les maps de reports et l'overlay image sont un
 * état partagé (compteurs nav/activité, overlay rendu dans {@link AdminScreen#render}, callback
 * réseau) → ils restent dans {@link AdminScreen} ; cet onglet ne porte que build + render.
 */
class ReportsTab {

    /** Marqueur (préfixe) signalant qu'un message de report a une capture jointe (U+0002). */
    private static final char IMG_MARK = '';

    private final AdminScreen s;

    ReportsTab(AdminScreen screen) { this.s = screen; }

    void build() {
        int colDiv  = s.cx + (s.pw - AdminScreen.SIDE_W) / 2;
        int lx1 = s.cx + 2;
        int rx1 = colDiv - 3;
        int lx2 = colDiv + 3;
        int rx2 = s.px + s.pw - 4;

        // Left panel — EN ATTENTE
        int y = s.py + 48;
        for (Map.Entry<String, String> e : new LinkedHashMap<>(s.reports).entrySet()) {
            String pn  = e.getKey();
            String msg = e.getValue();
            s.add(s.btn("§aACCEPT", b -> {
                String m = s.reports.remove(pn);
                if (m != null) s.acceptedReports.put(pn, m);
                s.send("ACCEPT_REPORT", pn, "");
                s.init();
            }).bounds(rx1 - 112, y + 8, 50, 14).build());
            s.add(s.btn(Lang.t("§cREFUSER", "§cDENY"), b -> {
                s.reports.remove(pn);
                s.send("REFUSE_REPORT", pn, "");
                s.init();
            }).bounds(rx1 - 58, y + 8, 50, 14).build());
            if (msg.length() > 0 && msg.charAt(0) == IMG_MARK) {
                s.add(s.btn("§b[IMG]", b -> {
                    if (s.reportImageCache.containsKey(pn)) s.showReportOverlay(pn, s.reportImageCache.get(pn));
                    else s.send("FETCH_REPORT_IMAGE", pn, "");
                    s.init();
                }).bounds(lx1 + 4, y + 25, 32, 11).build());
            }
            y += 44;
        }

        // Right panel — EN COURS
        y = s.py + 48;
        for (Map.Entry<String, String> e : new LinkedHashMap<>(s.acceptedReports).entrySet()) {
            String pn  = e.getKey();
            String msg = e.getValue();
            s.add(s.btn(Lang.t("§eCLÔTURER", "§eCLOSE"), b -> {
                String m = s.acceptedReports.remove(pn);
                if (m != null) {
                    if (s.closedReports.size() >= 15) s.closedReports.remove(s.closedReports.keySet().iterator().next());
                    s.closedReports.put(pn, m);
                }
                s.send("CLOSE_REPORT", pn, "");
                s.init();
            }).bounds(rx2 - 62, y + 8, 58, 14).build());
            if (msg.length() > 0 && msg.charAt(0) == IMG_MARK) {
                s.add(s.btn("§b[IMG]", b -> {
                    if (s.reportImageCache.containsKey(pn)) s.showReportOverlay(pn, s.reportImageCache.get(pn));
                    else s.send("FETCH_REPORT_IMAGE", pn, "");
                    s.init();
                }).bounds(lx2 + 4, y + 25, 32, 11).build());
            }
            y += 44;
        }
    }

    void render(GuiGraphics g, int mx, int my) {
        s.hoverReportMsg = null;
        int bottomY = s.py + s.ph - 32;
        int colDiv  = s.midX;
        int lx1 = s.cx + 2,    rx1 = colDiv - 3;
        int lx2 = colDiv + 3,  rx2 = s.px + s.pw - 2;

        // Vertical column divider
        g.fill(colDiv - 1, s.py + 26, colDiv, bottomY, AdminScreen.C_DIV);
        // Bottom CLÔTURÉS strip separator
        g.fill(s.cx + 2, bottomY, s.px + s.pw - 2, bottomY + 1, AdminScreen.C_DIV);
        g.fill(s.cx + 2, bottomY + 1, s.px + s.pw - 2, s.py + s.ph - 3, 0x0DFFFFFF);

        // Column headers
        s.lbl(g, Lang.t("EN ATTENTE", "PENDING") + (s.reports.isEmpty() ? "" : " (" + s.reports.size() + ")"),         lx1 + 5, s.py + 30);
        s.lbl(g, Lang.t("EN COURS", "IN PROGRESS") + (s.acceptedReports.isEmpty() ? "" : " (" + s.acceptedReports.size() + ")"), lx2 + 5, s.py + 30);

        // Left — pending
        if (s.reports.isEmpty()) {
            g.drawCenteredString(s.font(), Lang.t("§8Aucun", "§8None"), (lx1 + rx1) / 2, (s.py + 26 + bottomY) / 2, 0xFF444444);
        } else {
            int y = s.py + 48;
            for (Map.Entry<String, String> e : s.reports.entrySet()) {
                g.fill(lx1 + 3, y - 2, rx1 - 2, y + 37, AdminScreen.C_ROW);
                g.fill(lx1 + 3, y - 2, lx1 + 4, y + 37, 0xFFFF4444);
                String rawMsg = e.getValue();
                boolean hasImg = rawMsg.length() > 0 && rawMsg.charAt(0) == IMG_MARK;
                String fullMsg = hasImg ? rawMsg.substring(1) : rawMsg;
                String msg = s.truncate(fullMsg, 22);
                g.drawString(s.font(), "§e" + e.getKey(), lx1 + 8, y + 3,  0xFFFFFFFF);
                g.drawString(s.font(), "§7» " + msg,      lx1 + 8, y + 15, 0xFFAAAAAA);
                if (hasImg) g.drawString(s.font(), Lang.t("§b[capture jointe]", "§b[screenshot attached]"), lx1 + 8, y + 27, 0xFF4499FF);
                if (fullMsg.length() > 22 && mx >= lx1 + 3 && mx <= rx1 - 2 && my >= y - 2 && my <= y + 37)
                    s.hoverReportMsg = fullMsg;
                y += 44;
            }
        }

        // Right — in-progress
        if (s.acceptedReports.isEmpty()) {
            g.drawCenteredString(s.font(), Lang.t("§8Aucun", "§8None"), (lx2 + rx2) / 2, (s.py + 26 + bottomY) / 2, 0xFF444444);
        } else {
            int y = s.py + 48;
            for (Map.Entry<String, String> e : s.acceptedReports.entrySet()) {
                g.fill(lx2 + 3, y - 2, rx2 - 2, y + 37, AdminScreen.C_ROW);
                g.fill(lx2 + 3, y - 2, lx2 + 4, y + 37, 0xFFFFAA00);
                String rawMsg = e.getValue();
                boolean hasImg = rawMsg.length() > 0 && rawMsg.charAt(0) == IMG_MARK;
                String fullMsg = hasImg ? rawMsg.substring(1) : rawMsg;
                String msg = s.truncate(fullMsg, 22);
                g.drawString(s.font(), "§e" + e.getKey(), lx2 + 8, y + 3,  0xFFFFFFFF);
                g.drawString(s.font(), "§7» " + msg,      lx2 + 8, y + 15, 0xFFAAAAAA);
                if (hasImg) g.drawString(s.font(), "§b📷", lx2 + 8, y + 27, 0xFFFFFFFF);
                if (fullMsg.length() > 22 && mx >= lx2 + 3 && mx <= rx2 - 2 && my >= y - 2 && my <= y + 37)
                    s.hoverReportMsg = fullMsg;
                y += 44;
            }
        }

        // Bottom — closed history
        s.lbl(g, Lang.t("CLÔTURÉS", "CLOSED"), s.cx + 6, bottomY + 8);
        if (s.closedReports.isEmpty()) {
            g.drawString(s.font(), Lang.t("§8aucun", "§8none"), s.cx + 65, bottomY + 8, 0xFF444444);
        } else {
            int x = s.cx + 65;
            for (String name : s.closedReports.keySet()) {
                String tag = "§a■ §7" + name;
                g.drawString(s.font(), tag, x, bottomY + 8, 0xFFFFFFFF);
                x += s.font().width(tag) + 8;
                if (x > s.px + s.pw - 20) break;
            }
        }
    }
}
