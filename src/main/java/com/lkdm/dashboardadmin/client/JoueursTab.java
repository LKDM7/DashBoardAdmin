package com.lkdm.dashboardadmin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Onglet JOUEURS EN LIGNE : recherche/tri, liste en ligne + bannis + hors ligne, panneau
 * d'actions (online/offline) et fiche Activité. L'overlay NOTES et le dialog ban/kick sont
 * centraux ({@link AdminScreen}) ; tout l'état (payload-derived, sélection, dialog) reste
 * partagé dans AdminScreen — cet onglet ne porte que la logique d'affichage + ses box.
 */
class JoueursTab {

    private final AdminScreen s;
    private EditBox searchBox, noteBox;

    JoueursTab(AdminScreen screen) { this.s = screen; }

    // ─── helpers de layout / filtrage (Joueurs) ───────────────────────────────────

    /** Y du haut de la section HORS LIGNE — formule partagée entre build() et render(). */
    private int offlineSectionTop() {
        return s.py + 48 + filteredPlayers().size() * 16 + 8 + filteredBanned().size() * 18
            + (filteredBanned().isEmpty() ? 0 : 6);
    }

    /** Réplique client de DashboardAdmin.formatTimeAgo (la fiche Activité reçoit des timestamps bruts). */
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

    /** Durée restante avant une échéance future, format compact ("2j 3h", "45 min", "12s"). */
    private static String timeUntil(long futureMs) {
        long sec = (futureMs - System.currentTimeMillis()) / 1000;
        if (sec <= 0) return "0s";
        if (sec < 60)   return sec + "s";
        long min = sec / 60;
        if (min < 60)   return min + " min";
        long hr = min / 60;
        if (hr < 24)    return hr + "h" + (min % 60 > 0 ? min % 60 : "");
        long day = hr / 24;
        return day + "j" + (hr % 24 > 0 ? " " + hr % 24 + "h" : "");
    }

    /** Nombre de sanctions enregistrées pour un joueur (alimente le tri + la fiche Activité). */
    private long sanctionCountOf(String name) {
        return s.sanctionsEntries.stream().filter(e -> e[2].equalsIgnoreCase(name)).count();
    }

    /** Joueurs en ligne filtrés par la recherche puis triés — même liste pour build() et render(). */
    private java.util.List<PlayerInfo> filteredPlayers() {
        if (Minecraft.getInstance().getConnection() == null) return java.util.List.of();
        java.util.List<PlayerInfo> out = new java.util.ArrayList<>();
        for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
            if (info.getProfile() == null) continue;
            if (!s.search.isEmpty() && !info.getProfile().getName().toLowerCase().contains(s.search.toLowerCase())) continue;
            out.add(info);
        }
        if (s.sortMode == 1)
            out.sort(java.util.Comparator.comparing(i -> i.getProfile().getName().toLowerCase()));
        else if (s.sortMode == 2)
            out.sort(java.util.Comparator.comparingLong((PlayerInfo i) -> sanctionCountOf(i.getProfile().getName())).reversed());
        return out;
    }

    /** Joueurs hors ligne filtrés par la recherche puis triés ([nom, lastSeenMs]). */
    private java.util.List<String[]> filteredOffline() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String[] off : s.offlinePlayers) {
            if (!s.search.isEmpty() && !off[0].toLowerCase().contains(s.search.toLowerCase())) continue;
            out.add(off);
        }
        if (s.sortMode == 1)
            out.sort(java.util.Comparator.comparing(o -> o[0].toLowerCase()));
        else if (s.sortMode == 2)
            out.sort(java.util.Comparator.comparingLong((String[] o) -> sanctionCountOf(o[0])).reversed());
        return out;
    }

    /** Joueurs bannis filtrés par la recherche ([nom, raison, expirationMs]). */
    private java.util.List<String[]> filteredBanned() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String[] ban : s.bannedPlayers) {
            if (!s.search.isEmpty() && !ban[0].toLowerCase().contains(s.search.toLowerCase())) continue;
            out.add(ban);
        }
        if (s.sortMode == 1) out.sort(java.util.Comparator.comparing(o -> o[0].toLowerCase()));
        return out;
    }

    // ─── build ────────────────────────────────────────────────────────────────────

    void build() {
        if (Minecraft.getInstance().getConnection() == null) return;

        searchBox = new EditBox(s.font(), s.cx + 5, s.py + 29, 88, 14, Component.literal("Rechercher..."));
        searchBox.setResponder(v -> { if (!v.equals(s.search)) { s.search = v; s.init(); } });
        searchBox.setValue(s.search);
        s.add(searchBox);

        // Le tri par nb de sanctions a besoin de la liste complète : on la demande une fois.
        if (s.sortMode == 2 && !s.sanctionsRequested && s.sanctionsEntries.isEmpty()) {
            s.sanctionsRequested = true;
            s.send("GET_SANCTIONS", "", "");
        }

        // Bouton de tri (cycle : Récent → A-Z → Sanctions).
        String sortLbl = switch (s.sortMode) {
            case 1  -> "§e⇅ A-Z";
            case 2  -> Lang.t("§e⇅ Sanc.", "§e⇅ Sanc.");
            default -> Lang.t("§e⇅ Récent", "§e⇅ Recent");
        };
        s.add(s.btn(sortLbl, b -> { s.sortMode = (s.sortMode + 1) % 3; s.init(); })
            .bounds(s.px + s.pw - 130, s.py + 29, 62, 14).build());

        // Consultation de toutes les notes admin (coin droit de la barre d'en-tête du détail)
        int totalNotes = s.adminNotes.values().stream().mapToInt(java.util.List::size).sum();
        s.add(s.btn("§eNOTES" + (totalNotes == 0 ? "" : " (" + totalNotes + ")"),
            b -> { s.showNotesList = true; s.notesScroll = 0; s.init(); })
            .bounds(s.px + s.pw - 64, s.py + 29, 58, 14).build());

        int yOff = s.py + 48;
        for (PlayerInfo info : filteredPlayers()) {
            String name = info.getProfile().getName();
            boolean sel = name.equals(s.selPlayer);
            String dot = s.mutedPlayers.contains(name)   ? "§c■" :
                         s.frozenPlayers.contains(name)  ? "§b■" :
                         s.afkPlayers.contains(name)     ? "§e■" :
                         s.keepInvPlayers.contains(name) ? "§a■" : "";
            Component lbl = Component.literal((sel ? "§f§l" : "§7") + name + (dot.isEmpty() ? "" : " " + dot));
            s.add(Button.builder(lbl, b -> {
                s.selPlayer   = name;
                s.selOffline  = false;
                s.selGamemode = info.getGameMode().getName().toUpperCase();
                s.send("GET_SANCTIONS", "", ""); // alimente la fiche Activité
                s.init();
            }).bounds(s.cx + 16, yOff, 78, 14).build());
            yOff += 16;
        }

        // Section BANNIS — un bouton plein par banni (nom + durée), aligné sur le rendu de la
        // ligne (banY + 10 + i*18). Le clic passe par la confirmation de déban (dialog central).
        if (s.can("act.unban")) {
            int banBtnY = s.py + 48 + filteredPlayers().size() * 16 + 8 + 10;
            for (String[] ban : filteredBanned()) {
                final String banName = ban[0];
                long exp = 0L; try { exp = Long.parseLong(ban[2]); } catch (NumberFormatException ignored) {}
                String sub = exp > 0 ? " §8" + timeUntil(exp) : " §8∞";
                s.add(s.btn("§c" + s.truncate(banName, 9) + sub, b -> { s.confirmUnbanPlayer = banName; s.init(); })
                    .bounds(s.cx + 4, banBtnY, 90, 16).build());
                banBtnY += 18;
            }
        }

        // Section HORS LIGNE (consultation logs / sanctions / dernière connexion)
        if (!s.offlinePlayers.isEmpty()) {
            int offY = offlineSectionTop() + 12;
            int maxBottom = s.py + s.ph - 8;
            for (String[] off : filteredOffline()) {
                if (offY + 13 > maxBottom) break;
                final String name = off[0];
                boolean sel = name.equals(s.selPlayer);
                s.add(Button.builder(
                    Component.literal((sel ? "§f§l" : "§8") + s.truncate(name, 11)), b -> {
                        s.selPlayer   = name;
                        s.selOffline  = true;
                        s.selGamemode = Lang.t("HORS LIGNE", "OFFLINE");
                        s.send("GET_SANCTIONS", "", "");
                        s.init();
                    }).bounds(s.cx + 4, offY, 90, 12).build());
                offY += 14;
            }
        }

        if (s.selPlayer == null) return;

        // Note admin (fiche Activité) — éditable pour joueurs en ligne ET hors ligne
        {
            int divXn = s.cx + 98;
            int noteY = s.py + s.ph - 16;
            int okW = 26;
            int nCount = s.adminNotes.getOrDefault(s.selPlayer.toLowerCase(), java.util.List.of()).size();
            noteBox = new EditBox(s.font(), divXn + 42, noteY, s.px + s.pw - (divXn + 42) - okW - 10, 12,
                Component.literal(Lang.t("ajouter une note", "add a note")));
            noteBox.setMaxLength(120);
            noteBox.setHint(Component.literal(
                Lang.t("ajouter une note", "add a note") + (nCount > 0 ? " (" + nCount + ")" : "")));
            s.add(noteBox);
            s.add(s.btn("§a+", b -> {
                String note = noteBox.getValue().trim();
                if (!note.isEmpty()) {
                    s.adminNotes.computeIfAbsent(s.selPlayer.toLowerCase(), k -> new java.util.ArrayList<>()).add(note);
                    s.send("ADD_NOTE", s.selPlayer, note);
                    noteBox.setValue("");
                }
            }).bounds(s.px + s.pw - okW - 6, noteY - 1, okW, 14).build());
        }

        if (s.selOffline) {
            // Joueur hors ligne : consultations + ban (le serveur résout la cible via le cache de noms).
            int divX2 = s.cx + 98;
            int areaW2 = s.px + s.pw - divX2 - 6;
            int bw2 = Math.max(60, Math.min(120, areaW2 - 16));
            int bx2 = divX2 + 2 + (areaW2 - bw2) / 2;
            int oy2 = s.py + 68;
            if (s.can("act.inv")) {
                s.add(s.btn(Lang.t("INVENTAIRE", "INVENTORY"), b -> s.send("OPEN_INV",   s.selPlayer, "")).bounds(bx2, oy2, bw2, 20).build());
                oy2 += 24;
                s.add(s.btn("ENDERCHEST", b -> s.send("ENDERCHEST", s.selPlayer, "")).bounds(bx2, oy2, bw2, 20).build());
                oy2 += 24;
            }
            s.add(s.btn("§eLOGS", b -> {
                s.logsTab.reset();
                s.send("GET_LOGS", s.selPlayer, ""); s.currentTab = 5; s.init();
            }).bounds(bx2, oy2, bw2, 20).build());
            oy2 += 24;
            boolean offBanned = s.bannedPlayers.stream().anyMatch(e -> e[0].equalsIgnoreCase(s.selPlayer));
            if (offBanned) {
                if (s.can("act.unban")) {
                    final String unbanName = s.selPlayer;
                    s.add(s.btn(Lang.t("§aDÉBAN", "§aUNBAN"), b -> {
                        s.send("UNBAN", unbanName, "");
                        s.bannedPlayers.removeIf(e -> e[0].equalsIgnoreCase(unbanName));
                        s.init();
                    }).bounds(bx2, oy2, bw2, 20).build());
                }
            } else if (s.can("act.ban")) {
                s.add(s.btn("§4BAN", b -> { s.isBanning = true; s.init(); }).bounds(bx2, oy2, bw2, 20).build());
            }
            return;
        }

        int divX = s.cx + 98;
        int areaW = s.px + s.pw - divX - 6;
        int gap   = 8;
        int bw    = Math.max(60, Math.min(100, (areaW - gap) / 2));
        int totalW = bw * 2 + gap;
        int lCol  = divX + 2 + (areaW - totalW) / 2;
        int rCol  = lCol + bw + gap;
        int aY    = s.py + 68;

        if (s.can("act.inv")) {
            s.add(s.btn(Lang.t("INVENTAIRE", "INVENTORY"), b -> s.send("OPEN_INV",     s.selPlayer, "")).bounds(lCol, aY,       bw, 20).build());
            s.add(s.btn("ENDERCHEST", b -> s.send("ENDERCHEST",   s.selPlayer, "")).bounds(lCol, aY + 24,  bw, 20).build());
        }
        s.add(s.btn("BRING",      b -> s.send("BRING",        s.selPlayer, "")).bounds(lCol, aY + 48,  bw, 20).build());
        s.add(s.btn(Lang.t("TP VERS", "TP TO"), b -> s.send("TELEPORT_TO",  s.selPlayer, "")).bounds(lCol, aY + 72,  bw, 20).build());
        s.add(s.btn("§aHEAL",     b -> s.send("HEAL",         s.selPlayer, "")).bounds(lCol, aY + 96,  bw, 20).build());
        s.add(s.btn("§eLOGS",     b -> { s.logsTab.reset(); s.send("GET_LOGS", s.selPlayer, ""); s.currentTab = 5; s.init(); }).bounds(lCol, aY + 120, bw, 20).build());

        boolean frozen  = s.frozenPlayers.contains(s.selPlayer);
        boolean muted   = s.mutedPlayers.contains(s.selPlayer);
        boolean keepInv = s.keepInvPlayers.contains(s.selPlayer);

        s.add(s.btn(frozen  ? "§bFREEZE"    : "§7FREEZE",   b -> { s.send("FREEZE",         s.selPlayer, ""); if (frozen)  s.frozenPlayers.remove(s.selPlayer);  else s.frozenPlayers.add(s.selPlayer);  s.init(); }).bounds(rCol, aY,       bw, 20).build());
        if (s.can("act.gamemode"))
            s.add(s.btn(s.selGamemode, b -> { s.send("GAMEMODE", s.selPlayer, ""); s.selGamemode = switch(s.selGamemode) { case "SURVIVAL" -> "CREATIVE"; case "CREATIVE" -> "SPECTATOR"; default -> "SURVIVAL"; }; s.init(); }).bounds(rCol, aY + 24, bw, 20).build());
        if (s.can("act.kick"))
            s.add(s.btn("§cKICK",                               b -> { s.isKicking = true; s.init(); }).bounds(rCol, aY + 48,  bw, 20).build());
        if (s.can("act.ban"))
            s.add(s.btn("§4BAN",                                b -> { s.isBanning = true; s.init(); }).bounds(rCol, aY + 72,  bw, 20).build());
        if (s.can("act.mute"))
            s.add(s.btn(muted   ? "§cMUTE"     : "§7MUTE",     b -> { s.send("MUTE",           s.selPlayer, ""); if (muted)   s.mutedPlayers.remove(s.selPlayer);   else s.mutedPlayers.add(s.selPlayer);   s.init(); }).bounds(rCol, aY + 96,  bw, 20).build());
        s.add(s.btn(keepInv ? "§aKEEP INV" : "§7KEEP INV", b -> { s.send("KEEP_INVENTORY", s.selPlayer, ""); if (keepInv) s.keepInvPlayers.remove(s.selPlayer); else s.keepInvPlayers.add(s.selPlayer); s.init(); }).bounds(rCol, aY + 120, bw, 20).build());
    }

    // ─── render ───────────────────────────────────────────────────────────────────

    void render(GuiGraphics g) {
        int divX = s.cx + 98;
        g.fill(divX, s.py + 26, divX + 1, s.py + s.ph - 5, AdminScreen.C_DIV);

        // Têtes de skin à gauche de chaque entrée de la liste
        java.util.List<PlayerInfo> shown = filteredPlayers();
        {
            int yOff = s.py + 48;
            for (PlayerInfo info : shown) {
                PlayerFaceRenderer.draw(g, info.getSkin(), s.cx + 5, yOff + 3, 8);
                yOff += 16;
            }
        }

        // Section BANNIS dans le panneau gauche
        java.util.List<String[]> bannedShown = filteredBanned();
        if (!bannedShown.isEmpty()) {
            int searchCount = shown.size();
            int banY = s.py + 48 + searchCount * 16 + 8;
            g.drawString(s.font(), Lang.t("BANNIS", "BANNED"), s.cx + 6, banY - 2, 0xFF888888);
            g.fill(s.cx + 4, banY + 6, s.cx + 94, banY + 7, AdminScreen.C_DIV);
            // Avec permission de déban : les boutons (build) affichent nom + durée.
            // Sans permission : liste rouge en lecture seule (nom + compte à rebours).
            if (!s.can("act.unban")) {
                for (int i = 0; i < bannedShown.size(); i++) {
                    String bname = bannedShown.get(i)[0];
                    long expires = 0L; try { expires = Long.parseLong(bannedShown.get(i)[2]); } catch (NumberFormatException ignored) {}
                    int by = banY + 10 + i * 18;
                    g.fill(s.cx + 4, by, s.cx + 94, by + 14, 0x22FF4444);
                    g.drawString(s.font(), "§c" + s.truncate(bname, 9), s.cx + 6, by + 2, 0xFFFF6666);
                    String sub = expires > 0 ? "§e⌛ " + timeUntil(expires) : "§8∞";
                    g.drawString(s.font(), sub, s.cx + 6, by + 10, 0xFFAAAA55);
                }
            }
        }

        // Section HORS LIGNE (en-tête + dernière connexion en sous-texte des boutons)
        if (!filteredOffline().isEmpty()) {
            int offTop = offlineSectionTop();
            g.drawString(s.font(), Lang.t("HORS LIGNE", "OFFLINE"), s.cx + 6, offTop, 0xFF888888);
            g.fill(s.cx + 4, offTop + 8, s.cx + 94, offTop + 9, AdminScreen.C_DIV);
        }

        if (s.selPlayer == null) {
            g.drawCenteredString(s.font(), Lang.t("§8← Sélectionnez", "§8← Select"), s.cx + 49, s.py + s.ph / 2, 0xFF555555);
            return;
        }

        g.fill(divX + 1, s.py + 26, s.px + s.pw, s.py + 46, 0xFF0A0A0A);
        PlayerInfo selInfo = shown.stream()
            .filter(i -> i.getProfile().getName().equals(s.selPlayer)).findFirst().orElse(null);
        int nameX = divX + 8;
        if (selInfo != null) {
            PlayerFaceRenderer.draw(g, selInfo.getSkin(), divX + 6, s.py + 30, 12);
            nameX = divX + 22;
        }
        g.drawString(s.font(), "§e§l" + s.selPlayer, nameX, s.py + 31, 0xFFFFFFFF);
        int gmX = nameX + 2 + s.font().width("§e§l" + s.selPlayer);
        g.drawString(s.font(), " §8[" + s.selGamemode + "]", gmX, s.py + 31, 0xFFFFFFFF);
        g.fill(divX + 1, s.py + 45, s.px + s.pw, s.py + 46, AdminScreen.C_DIV);

        if (!s.selOffline) {
            int areaW  = s.px + s.pw - divX - 6;
            int gap    = 8;
            int bw     = Math.max(60, Math.min(100, (areaW - gap) / 2));
            int totalW = bw * 2 + gap;
            int lCol   = divX + 2 + (areaW - totalW) / 2;
            int rCol   = lCol + bw + gap;
            s.lbl(g, "ACTIONS",    lCol, s.py + 53);
            s.lbl(g, Lang.t("MODÉRATION", "MODERATION"), rCol, s.py + 53);
        }

        renderActivityCard(g, divX);
    }

    /** Fiche Activité du joueur sélectionné : dernière connexion, sanctions, reports, note admin. */
    private void renderActivityCard(GuiGraphics g, int divX) {
        int top = s.py + s.ph - 58;
        g.fill(divX + 1, top - 2, s.px + s.pw, top - 1, AdminScreen.C_DIV);
        g.fill(divX + 1, top - 1, s.px + s.pw, s.py + s.ph - 3, 0x0DFFFFFF);
        s.lbl(g, Lang.t("ACTIVITÉ", "ACTIVITY"), divX + 8, top + 2);

        String seen = Lang.t("§aen ligne", "§aonline");
        if (s.selOffline) {
            seen = "§7?";
            for (String[] off : s.offlinePlayers)
                if (off[0].equals(s.selPlayer)) {
                    try {
                        String ago = timeAgo(Long.parseLong(off[1]));
                        seen = "§7" + Lang.t("il y a " + ago, ago + " ago");
                    } catch (NumberFormatException ignored) {}
                    break;
                }
        }

        long sanctionCount = s.sanctionsEntries.stream().filter(e -> e[2].equalsIgnoreCase(s.selPlayer)).count();
        String lastSanction = s.sanctionsEntries.stream().filter(e -> e[2].equalsIgnoreCase(s.selPlayer))
            .findFirst().map(e -> e[1] + " " + e[0]).orElse(null);
        long reportCount = java.util.stream.Stream.of(s.reports, s.acceptedReports, s.closedReports)
            .flatMap(m -> m.keySet().stream()).filter(n -> n.equalsIgnoreCase(s.selPlayer)).count();

        g.drawString(s.font(), Lang.t("§7Vu : ", "§7Seen: ") + seen, divX + 8, top + 13, 0xFFAAAAAA);
        String sancLbl = Lang.t("§7Sanctions : ", "§7Sanctions: ");
        String sancTxt = s.sanctionsEntries.isEmpty() && sanctionCount == 0
            ? sancLbl + "§8—"
            : sancLbl + (sanctionCount == 0 ? "§a0" : "§c" + sanctionCount
                + (lastSanction != null ? " §8(" + Lang.t("dernier : ", "last: ") + lastSanction + ")" : ""));
        g.drawString(s.font(), sancTxt, divX + 8, top + 24, 0xFFAAAAAA);
        g.drawString(s.font(), Lang.t("§7Reports déposés : ", "§7Reports filed: ") + (reportCount == 0 ? "§80" : "§e" + reportCount),
            divX + 8, top + 35, 0xFFAAAAAA);
        g.drawString(s.font(), "§7" + Lang.t("Notes", "Notes"), divX + 8, s.py + s.ph - 14, 0xFFAAAAAA);
    }
}
