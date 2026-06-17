package com.lkdm.dashboardadmin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Onglet CHAT & ANNONCES : annonce globale (TOUS / joueur / groupe), MOTD, verrou chat,
 * bypass anti-spam, spy MP, et broadcasts programmés. L'état partagé (motd, chatLocked,
 * mailSpyEnabled, antiSpamBypassEnabled, availableGroups, schedBroadcasts) reste dans
 * {@link AdminScreen} (payload) ; les box de saisie + la cible + l'index de suppression
 * en attente vivent ici.
 */
class ChatTab {

    private final AdminScreen s;

    private EditBox announceBox, broadcastMsgBox, broadcastIntervalBox;
    private String  broadcastTarget = "";   // ""=TOUS, playerName, "GROUP:leaderName"
    private int     deletingBroadcastIdx = -1;

    ChatTab(AdminScreen screen) { this.s = screen; }

    void build() {
        int contentX = s.cx + 10;
        int contentW = s.px + s.pw - s.cx - 20;

        // ── Section ANNONCE ──────────────────────────────────────────────────────
        int aY = s.py + 50;
        announceBox = new EditBox(s.font(), contentX, aY, contentW, 20, Component.empty());
        announceBox.setMaxLength(500);
        s.add(announceBox);

        // Chips: TOUS / joueur / groupe
        int chipY = aY + 26, chipX = contentX, chipMaxX = contentX + contentW;
        String tousLbl = Lang.t("TOUS", "ALL");
        int tousW = s.font().width(tousLbl) + 8;
        boolean tousSel = broadcastTarget.isEmpty();
        s.add(s.btn((tousSel ? "§a" : "§7") + tousLbl,
            b -> { broadcastTarget = ""; s.init(); }).bounds(chipX, chipY, tousW, 14).build());
        chipX += tousW + 3;
        if (Minecraft.getInstance().getConnection() != null) {
            for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
                if (info.getProfile() == null) continue;
                final String pn = info.getProfile().getName();
                int cw = Math.min(s.font().width(pn) + 8, 90);
                if (chipX + cw > chipMaxX - 60) break;
                boolean psel = broadcastTarget.equals(pn);
                s.add(s.btn((psel ? "§e" : "§7") + pn,
                    b -> { broadcastTarget = pn; s.init(); }).bounds(chipX, chipY, cw, 14).build());
                chipX += cw + 2;
            }
        }
        for (String[] grp : s.availableGroups) {
            final String key = "GROUP:" + grp[0];
            String glabel = grp[1];
            int cw = Math.min(s.font().width(glabel) + 8, 100);
            if (chipX + cw > chipMaxX) break;
            boolean gsel = broadcastTarget.equals(key);
            s.add(s.btn((gsel ? "§b" : "§8") + glabel,
                b -> { broadcastTarget = key; s.init(); }).bounds(chipX, chipY, cw, 14).build());
            chipX += cw + 2;
        }

        int btnW = (contentW - 12) / 3;
        s.add(s.btn(Lang.t("§a▶ DIFFUSER", "§a▶ BROADCAST"),
            b -> { if (!announceBox.getValue().isEmpty()) { s.send("ANNOUNCE", broadcastTarget, announceBox.getValue()); announceBox.setValue(""); } })
            .bounds(contentX, aY + 44, btnW, 20).build());
        // Définit le texte saisi comme MOTD (message de connexion) ; champ vide = MOTD supprimé.
        s.add(s.btn(Lang.t("§eDÉFINIR MOTD", "§eSET MOTD"),
            b -> { s.motd = announceBox.getValue().trim(); s.send("SET_MOTD", "", s.motd); announceBox.setValue(""); s.init(); })
            .bounds(contentX + btnW + 6, aY + 44, btnW, 20).build());
        // Suppression du MOTD (visible seulement si un MOTD est défini) — sur la ligne de statut.
        if (!s.motd.isEmpty())
            s.add(s.btn(Lang.t("§c✕ SUPPR. MOTD", "§c✕ CLEAR MOTD"),
                b -> { s.motd = ""; s.send("SET_MOTD", "", ""); s.init(); })
                .bounds(contentX + contentW - 84, aY + 86, 84, 12).build());
        s.add(s.btn("CHAT " + (s.chatLocked ? Lang.t("§c🔒 VERROUILLÉ", "§c🔒 LOCKED") : Lang.t("§a🔓 OUVERT", "§a🔓 OPEN")),
            b -> { s.send("LOCK_CHAT", "", ""); s.chatLocked = !s.chatLocked; s.init(); })
            .bounds(contentX + (btnW + 6) * 2, aY + 44, btnW, 20).build());

        // Ligne dédiée sous la divider : BYPASS anti-spam à gauche, SPY MP à droite.
        s.add(s.btn(Lang.t("BYPASS SPAM (staff) : ", "SPAM BYPASS (staff): ") + (s.antiSpamBypassEnabled ? "§aON" : "§cOFF"),
            b -> { s.send("TOGGLE_ANTISPAM_BYPASS", "", ""); s.antiSpamBypassEnabled = !s.antiSpamBypassEnabled; s.init(); })
            .bounds(contentX, aY + 103, 150, 14).build());
        s.add(s.btn(Lang.t("SPY MP : ", "PM SPY: ") + (s.mailSpyEnabled ? "§aON" : "§cOFF"),
            b -> { s.send("TOGGLE_MAIL_SPY", "", ""); s.mailSpyEnabled = !s.mailSpyEnabled; s.init(); })
            .bounds(contentX + contentW - 90, aY + 103, 90, 14).build());

        // ── Section BROADCASTS ───────────────────────────────────────────────────
        int bY = s.py + 186;
        broadcastMsgBox = new EditBox(s.font(), contentX, bY, contentW - 78, 18, Component.literal("Message du broadcast..."));
        broadcastIntervalBox = new EditBox(s.font(), contentX + contentW - 72, bY, 34, 18, Component.literal("min"));
        broadcastIntervalBox.setMaxLength(4);
        s.add(broadcastMsgBox);
        s.add(broadcastIntervalBox);
        s.add(s.btn(Lang.t("§aAJOUTER", "§aADD"), b -> {
            String msg    = broadcastMsgBox.getValue().trim();
            String minStr = broadcastIntervalBox.getValue().trim();
            if (!msg.isEmpty() && !minStr.isEmpty()) {
                try {
                    int min = Integer.parseInt(minStr);
                    if (min > 0) {
                        s.schedBroadcasts.add(new String[]{msg, String.valueOf(min)});
                        s.send("SCHEDULE_ADD", "", msg + "\t" + min);
                        broadcastMsgBox.setValue("");
                        broadcastIntervalBox.setValue("");
                        s.init();
                    }
                } catch (NumberFormatException ignored) {}
            }
        }).bounds(contentX + contentW - 34, bY - 1, 34, 20).build());

        int listY = bY + 26;
        for (int i = 0; i < s.schedBroadcasts.size(); i++) {
            final int idx = i;
            if (deletingBroadcastIdx == i) {
                s.add(s.btn(Lang.t("§a✔ OUI", "§a✔ YES"),
                    b -> { s.schedBroadcasts.remove(idx); s.send("SCHEDULE_REMOVE", "", String.valueOf(idx)); deletingBroadcastIdx = -1; s.init(); })
                    .bounds(s.px + s.pw - 82, listY + i * 20 + 3, 36, 14).build());
                s.add(s.btn(Lang.t("§c✖ NON", "§c✖ NO"), b -> { deletingBroadcastIdx = -1; s.init(); })
                    .bounds(s.px + s.pw - 42, listY + i * 20 + 3, 36, 14).build());
            } else {
                s.add(s.btn("§c✕", b -> { deletingBroadcastIdx = idx; s.init(); })
                    .bounds(s.px + s.pw - 26, listY + i * 20 + 3, 18, 14).build());
            }
        }
    }

    void render(GuiGraphics g) {
        int contentX = s.cx + 10;
        int contentW = s.px + s.pw - s.cx - 20;
        int aY       = s.py + 50;

        // ── Section ANNONCE ──────────────────────────────────────────────────────
        s.lbl(g, Lang.t("ANNONCE GLOBALE", "GLOBAL ANNOUNCEMENT"), contentX, aY - 12);

        // Preview box under the buttons
        String preview = announceBox != null ? announceBox.getValue() : "";
        int previewY = aY + 70;
        g.fill(contentX, previewY, contentX + contentW, previewY + 14, 0x22FFFFFF);
        g.fill(contentX, previewY, contentX + 2, previewY + 14, AdminScreen.C_ACCENT);
        g.drawString(s.font(),
            preview.isEmpty() ? Lang.t("§8Aperçu — tapez votre annonce…", "§8Preview — type your announcement…")
                              : "§6§l[ANNONCE] §r§f" + preview,
            contentX + 6, previewY + 3, 0xFFFFFFFF);
        g.drawString(s.font(), Lang.t("§8MOTD connexion : ", "§8Login MOTD: ")
            + (s.motd.isEmpty() ? Lang.t("§8aucun", "§8none") : "§7" + s.truncate(s.motd, 40)),
            contentX, previewY + 17, 0xFF555555);

        // ── Divider ──────────────────────────────────────────────────────────────
        int divY = previewY + 29;
        g.fill(contentX, divY, contentX + contentW, divY + 1, AdminScreen.C_DIV);

        // ── Section BROADCASTS ───────────────────────────────────────────────────
        int bY    = s.py + 186;
        int listY = bY + 26;
        s.lbl(g, Lang.t("BROADCASTS PROGRAMMÉS", "SCHEDULED BROADCASTS"), contentX, divY + 24);
        g.drawString(s.font(), "§8min", contentX + contentW - 68, bY + 3, 0xFF444444);

        if (s.schedBroadcasts.isEmpty()) {
            g.drawString(s.font(), Lang.t("§8Aucun broadcast programmé", "§8No scheduled broadcasts"), contentX + 2, listY + 2, 0xFF444444);
        } else {
            for (int i = 0; i < s.schedBroadcasts.size(); i++) {
                String[] entry = s.schedBroadcasts.get(i);
                boolean confirming = deletingBroadcastIdx == i;
                g.fill(s.cx + 8, listY + i * 20 - 2, s.px + s.pw - 8, listY + i * 20 + 12,
                       confirming ? 0x33FF4444 : 0x11FFFFFF);
                if (confirming) {
                    g.drawString(s.font(), Lang.t("§cSupprimer ? §7", "§cDelete? §7") + s.truncate(entry[0], 20) + " §8(" + entry[1] + "min)",
                                 s.cx + 12, listY + i * 20, 0xFFFFFFFF);
                } else {
                    g.drawString(s.font(), "§6[Annonce] §f" + s.truncate(entry[0], 30) + " §8— §e" + entry[1] + "min",
                                 s.cx + 12, listY + i * 20, 0xFFFFFFFF);
                }
            }
        }
    }
}
