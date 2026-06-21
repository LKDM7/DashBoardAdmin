package com.lkdm.dashboardadmin.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Onglet MONDE du dashboard admin : heure, météo (+ cycles), outils serveur (clear lag,
 * remove mobs, spawn, vanish, restart) et carte de santé serveur. L'état (toggles de cycle,
 * flags de dialog remove-mobs/restart, stats serveur) reste partagé dans {@link AdminScreen}.
 */
class MondeTab {

    private final AdminScreen s;

    MondeTab(AdminScreen screen) { this.s = screen; }

    void build() {
        int lx = s.cx + 12;
        int ty = s.py + 56;
        s.add(s.btn(Lang.t("MATIN", "MORNING"),  b -> s.send("SET_MORNING",  "", "")).bounds(lx,       ty, 58, 20).build());
        s.add(s.btn(Lang.t("JOUR", "DAY"),       b -> s.send("SET_DAY",      "", "")).bounds(lx + 62,  ty, 58, 20).build());
        s.add(s.btn(Lang.t("SOIR", "EVENING"),   b -> s.send("SET_EVENING",  "", "")).bounds(lx + 124, ty, 58, 20).build());
        s.add(s.btn(Lang.t("NUIT", "NIGHT"),     b -> s.send("SET_NIGHT",    "", "")).bounds(lx + 186, ty, 58, 20).build());

        int my = s.py + 103;
        s.add(s.btn(Lang.t("SOLEIL", "CLEAR"),   b -> s.send("SET_WEATHER_CLEAR",   "", "")).bounds(lx,       my, 72, 20).build());
        s.add(s.btn(Lang.t("PLUIE", "RAIN"),     b -> s.send("SET_WEATHER_RAIN",    "", "")).bounds(lx + 76,  my, 72, 20).build());
        s.add(s.btn(Lang.t("ORAGE", "THUNDER"),  b -> s.send("SET_WEATHER_THUNDER", "", "")).bounds(lx + 152, my, 72, 20).build());
        s.add(s.btn(Lang.t("CYCLE MÉTÉO: ", "WEATHER: ") + Lang.onOff(s.weatherCycleEnabled),
            b -> { s.send("TOGGLE_WEATHER_CYCLE", "", ""); s.weatherCycleEnabled = !s.weatherCycleEnabled; s.init(); })
            .bounds(lx, my + 26, 118, 20).build());
        // Cycle jour/nuit (gamerule doDaylightCycle) — fige l'heure quand OFF, même logique que le cycle météo.
        s.add(s.btn(Lang.t("CYCLE J/N: ", "DAY/NIGHT: ") + Lang.onOff(s.daylightCycleEnabled),
            b -> { s.send("TOGGLE_DAYLIGHT_CYCLE", "", ""); s.daylightCycleEnabled = !s.daylightCycleEnabled; s.init(); })
            .bounds(lx + 122, my + 26, 118, 20).build());

        int oy = s.py + 168;
        int bw3 = Math.min(92, (s.px + s.pw - 12 - lx - 8) / 3);
        s.add(s.btn("CLEAR LAG",   b -> s.send("CLEAR_LAG", "", "")).bounds(lx,                oy, bw3, 20).build());
        s.add(s.btn("REMOVE MOBS", b -> { s.isRemovingMobs = true; s.init(); }).bounds(lx + bw3 + 4,      oy, bw3, 20).build());
        s.add(s.btn("SET SPAWN",   b -> s.send("SET_SPAWN", "", "")).bounds(lx + (bw3 + 4) * 2, oy, bw3, 20).build());
        if (s.can("act.vanish"))
            s.add(s.btn("VANISH",      b -> s.send("VANISH",    "", "")).bounds(lx,                oy + 26, bw3, 20).build());
        if (s.can("act.restart")) {
            s.add(s.btn("§cRESTART",   b -> { s.isRestarting = true; s.init(); }).bounds(lx + bw3 + 4,      oy + 26, bw3, 20).build());
            s.add(s.btn(Lang.t("§7ANNULER R.", "§7CANCEL R."), b -> s.send("CANCEL_RESTART", "", "")).bounds(lx + (bw3 + 4) * 2, oy + 26, bw3, 20).build());
        }

        // Santé serveur — bouton de rafraîchissement (les stats sont un instantané)
        s.add(s.btn("§b⟳", b -> s.send("REFRESH_ADMIN", "", ""))
            .bounds(s.px + s.pw - 26, oy + 56, 18, 14).build());
    }

    void render(GuiGraphics g) {
        int lx = s.cx + 12;
        g.fill(s.cx, s.py + 38, s.px + s.pw, s.py + 55, 0x0AFFFFFF);
        s.lbl(g, Lang.t("TEMPS", "TIME"),  lx, s.py + 43); g.fill(lx, s.py + 53, s.px + s.pw - 12, s.py + 54, AdminScreen.C_DIV);
        g.fill(s.cx, s.py + 85, s.px + s.pw, s.py + 102, 0x0AFFFFFF);
        s.lbl(g, Lang.t("MÉTÉO", "WEATHER"),  lx, s.py + 90); g.fill(lx, s.py + 100, s.px + s.pw - 12, s.py + 101, AdminScreen.C_DIV);
        g.fill(s.cx, s.py + 150, s.px + s.pw, s.py + 167, 0x0AFFFFFF);
        s.lbl(g, Lang.t("OUTILS", "TOOLS"), lx, s.py + 155); g.fill(lx, s.py + 165, s.px + s.pw - 12, s.py + 166, AdminScreen.C_DIV);

        // ── Santé serveur ────────────────────────────────────────────────────
        int oy = s.py + 168;
        int sY = oy + 54;
        g.fill(s.cx, sY, s.px + s.pw, sY + 17, 0x0AFFFFFF);
        s.lbl(g, Lang.t("SANTÉ SERVEUR", "SERVER HEALTH"), lx, sY + 5); g.fill(lx, sY + 15, s.px + s.pw - 12, sY + 16, AdminScreen.C_DIV);
        if (s.serverStats == null || s.serverStats.length < 7) {
            g.drawString(s.font(), Lang.t("§8Indisponible", "§8Unavailable"), lx, sY + 22, 0xFF444444);
            return;
        }
        double tps = 0, mspt = 0;
        long ramU = 0, ramM = 1;
        try {
            tps  = Double.parseDouble(s.serverStats[0]);
            mspt = Double.parseDouble(s.serverStats[1]);
            ramU = Long.parseLong(s.serverStats[2]);
            ramM = Long.parseLong(s.serverStats[3]);
        } catch (NumberFormatException ignored) {}
        String tpsCol = tps >= 18 ? "§a" : tps >= 14 ? "§e" : "§c";
        long upSec = 0;
        try { upSec = Long.parseLong(s.serverStats[6]); } catch (NumberFormatException ignored) {}
        String uptime = upSec >= 3600 ? (upSec / 3600) + "h" + (upSec % 3600) / 60 + "m"
                                       : (upSec / 60) + "m" + (upSec % 60) + "s";

        int line = sY + 22;
        g.drawString(s.font(), "§7TPS" + Lang.t(" : ", ": ") + tpsCol + s.serverStats[0] + " §8(" + s.serverStats[1] + " ms/tick)", lx, line, 0xFFAAAAAA);
        line += 12;
        // RAM : texte + barre de progression
        g.drawString(s.font(), "§7RAM" + Lang.t(" : ", ": ") + "§f" + ramU + " §7/ " + ramM + Lang.t(" Mo", " MB"), lx, line, 0xFFAAAAAA);
        int barX = lx + 130, barW = s.px + s.pw - 40 - barX;
        if (barW > 30) {
            float frac = Math.min(1f, (float) ramU / Math.max(1, ramM));
            int fillCol = frac < 0.7f ? 0xFF00CC44 : frac < 0.9f ? 0xFFFFAA00 : 0xFFFF4444;
            g.fill(barX, line + 1, barX + barW, line + 7, 0x33FFFFFF);
            g.fill(barX, line + 1, barX + (int) (barW * frac), line + 7, fillCol);
        }
        line += 12;
        g.drawString(s.font(), Lang.t("§7Entités : §f", "§7Entities: §f") + s.serverStats[4]
            + Lang.t("   §7Chunks : §f", "   §7Chunks: §f") + s.serverStats[5]
            + Lang.t("   §7Uptime : §f", "   §7Uptime: §f") + uptime, lx, line, 0xFFAAAAAA);
    }
}
