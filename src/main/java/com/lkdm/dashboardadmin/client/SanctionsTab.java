package com.lkdm.dashboardadmin.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Onglet SANCTIONS du dashboard admin : historique des sanctions (BAN/KICK/MUTE/UNBAN) +
 * bouton DÉBAN sur les bans actifs. L'état (sanctionsEntries, bannedPlayers, scroll, et la
 * confirmation de déban gérée par le dialog central) reste partagé dans {@link AdminScreen}.
 */
class SanctionsTab {

    private final AdminScreen s;

    SanctionsTab(AdminScreen screen) { this.s = screen; }

    boolean hasEntries() { return !s.sanctionsEntries.isEmpty(); }

    boolean mouseScrolled(double scrollY) {
        int maxVis = Math.max(1, (s.py + s.ph - 6 - (s.py + 28)) / 18);
        int maxScroll = Math.max(0, s.sanctionsEntries.size() - maxVis);
        s.sanctionsScroll = Math.max(0, Math.min(s.sanctionsScroll - (int) (scrollY * 3), maxScroll));
        s.init();
        return true;
    }

    void build() {
        if (s.sanctionsEntries.isEmpty()) return;
        int panelTop = s.py + 28;
        int panelBot = s.py + s.ph - 6;
        int entryH   = 18;
        int maxVis   = Math.max(1, (panelBot - panelTop) / entryH);

        int y = panelTop;
        for (int i = s.sanctionsScroll; i < s.sanctionsEntries.size() && i < s.sanctionsScroll + maxVis; i++) {
            String[] e = s.sanctionsEntries.get(i);
            if ("BAN".equals(e[1])) {
                final String playerName = e[2];
                boolean isBanned = s.bannedPlayers.stream().anyMatch(b -> b[0].equalsIgnoreCase(playerName));
                if (isBanned) {
                    s.add(s.btn(Lang.t("§aDÉBAN", "§aUNBAN"), b -> {
                        s.confirmUnbanPlayer = playerName;
                        s.init();
                    }).bounds(s.px + s.pw - 62, y + 2, 54, 14).build());
                }
            }
            y += entryH;
        }
    }

    void render(GuiGraphics g) {
        int panelTop = s.py + 28;
        int panelBot = s.py + s.ph - 6;
        int sbX      = s.px + s.pw - 6;
        int entryH   = 18;

        if (s.sanctionsEntries.isEmpty()) {
            g.drawCenteredString(s.font(), Lang.t("§8Aucune sanction enregistrée", "§8No sanctions recorded"), s.midX, s.py + s.ph / 2, 0xFF444444);
            return;
        }

        int maxVis    = Math.max(1, (panelBot - panelTop) / entryH);
        int maxScroll = Math.max(0, s.sanctionsEntries.size() - maxVis);
        if (s.sanctionsScroll > maxScroll) s.sanctionsScroll = maxScroll;
        if (s.sanctionsScroll < 0)         s.sanctionsScroll = 0;

        g.enableScissor(s.cx, panelTop, sbX - 2, panelBot);
        int y = panelTop;
        for (int i = s.sanctionsScroll; i < s.sanctionsEntries.size() && i < s.sanctionsScroll + maxVis; i++) {
            String[] e = s.sanctionsEntries.get(i);
            if (i % 2 == 0) g.fill(s.cx + 4, y, sbX - 4, y + entryH - 2, AdminScreen.C_ROW);
            int typeColor = switch (e[1]) {
                case "BAN"  -> 0xFFFF5555;
                case "KICK" -> 0xFFFFAA00;
                case "MUTE" -> 0xFFFFFF55;
                case "UNBAN"-> 0xFF55FF55;
                default     -> 0xFFAAAAAA;
            };
            g.fill(s.cx + 4, y, s.cx + 5, y + entryH - 2, typeColor);
            String line = "§8" + e[0] + " §r" + e[2] + " §8— " + e[1] + " §8| §7" + e[3]
                + (e[4].equals("—") ? "" : " §8| §7" + s.truncate(e[4], 20));
            g.drawString(s.font(), line, s.cx + 10, y + 4, typeColor, false);
            y += entryH;
        }
        g.disableScissor();

        if (s.sanctionsEntries.size() > maxVis) {
            int sbH    = panelBot - panelTop;
            int thumbH = Math.max(8, sbH * maxVis / s.sanctionsEntries.size());
            int thumbY = maxScroll > 0 ? panelTop + (sbH - thumbH) * s.sanctionsScroll / maxScroll : panelTop;
            g.fill(sbX, panelTop, sbX + 4, panelBot, 0x33FFFFFF);
            g.fill(sbX, thumbY,   sbX + 4, thumbY + thumbH, AdminScreen.C_ACCENT);
        }
    }
}
