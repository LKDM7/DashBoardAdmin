package com.lkdm.dashboardadmin.client;

import com.lkdm.dashboardadmin.Zone;
import com.lkdm.dashboardadmin.ZoneFlag;
import com.lkdm.dashboardadmin.networking.OpenZonePayload;
import com.lkdm.dashboardadmin.networking.ZoneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Onglet GESTION DES ZONES : liste des zones (gauche), détail du zone sélectionnée (droite)
 * en 4 sous-onglets MEMBRES / COORDS / OPTIONS / MSG. L'état zones (issu du payload via
 * {@link #onUpdate}) vit entièrement ici — il n'est utilisé que par cet onglet.
 */
class ZonesTab {

    private record ZoneData(int x1, int y1, int z1, int x2, int y2, int z2,
                             java.util.List<String[]> members, boolean nightVision,
                             java.util.Map<ZoneFlag, Boolean> flags, boolean enabled,
                             int colorIdx, int priority, String greeting, String farewell,
                             java.util.List<String> inside) {
        int color() { return Zone.COLORS[Math.floorMod(colorIdx, Zone.COLORS.length)]; }
    }

    private final AdminScreen s;

    private final java.util.Map<String, ZoneData> zoneMap    = new java.util.LinkedHashMap<>();
    private final java.util.List<String>          zoneOnline = new java.util.ArrayList<>();
    private String  selectedZone   = null;
    private int     zoneListScroll = 0;
    private int     zoneDetailTab  = 0; // 0=MEMBRES 1=COORDS 2=OPTIONS 3=MSG
    private EditBox zMinXBox, zMinYBox, zMinZBox, zMaxXBox, zMaxYBox, zMaxZBox, zPriorityBox;
    private EditBox zGreetingBox, zFarewellBox;

    ZonesTab(AdminScreen screen) { this.s = screen; }

    // ─── réception réseau ──────────────────────────────────────────────────────────
    public void onUpdate(OpenZonePayload payload) {
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
                    java.util.EnumMap<ZoneFlag, Boolean> flags = new java.util.EnumMap<>(ZoneFlag.class);
                    if (f.length > 6 && !f[6].isEmpty())
                        for (String pair : f[6].split(";")) {
                            String[] kv = pair.split(":");
                            if (kv.length == 2) {
                                ZoneFlag fl = ZoneFlag.byName(kv[0]);
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
        if (s.currentTab == 6) s.init();
    }

    /** Molette sur la liste des zones (panneau gauche). Renvoie true si consommé. */
    boolean mouseScrolled(double mouseX, double scrollY) {
        if (mouseX < s.cx || mouseX >= s.cx + AdminScreen.ZONE_LIST_W) return false;
        int maxVis   = Math.max(1, (s.py + s.ph - 28 - (s.py + 30)) / 20);
        int maxScroll = Math.max(0, zoneMap.size() - maxVis);
        zoneListScroll = Math.max(0, Math.min(zoneListScroll - (int) scrollY, maxScroll));
        s.init();
        return true;
    }

    private void sendZone(String action, String zoneName, String value) {
        PacketDistributor.sendToServer(new ZoneActionPayload(action, zoneName, value));
    }

    private EditBox zBox(int x, int y, int w, String value) {
        EditBox b = new EditBox(s.font(), x, y, w, 16, Component.empty());
        b.setMaxLength(8);
        b.setValue(value);
        return b;
    }

    private static void drawFace(GuiGraphics g, String playerName, int x, int y, int size) {
        if (Minecraft.getInstance().getConnection() == null) return;
        PlayerInfo pi = Minecraft.getInstance().getConnection().getPlayerInfo(playerName);
        if (pi != null) PlayerFaceRenderer.draw(g, pi.getSkin(), x, y, size);
    }

    // ─── build ────────────────────────────────────────────────────────────────────
    void build() {
        int znDetX = s.cx + AdminScreen.ZONE_LIST_W + 1;
        int znDetW = (s.px + s.pw) - znDetX;

        // Baguette button at bottom of list panel
        s.add(s.btn(Lang.t("§6✦ Baguette", "§6✦ Wand"), b -> sendZone("GIVE_TOOL", "", ""))
            .bounds(s.cx + 2, s.py + s.ph - 24, AdminScreen.ZONE_LIST_W - 4, 18).build());

        // Zone list entries (sous l'en-tête « ZONES » dessiné à py+28)
        int topY = s.py + 40, botY = s.py + s.ph - 28, entryH = 20;
        int maxVis = Math.max(1, (botY - topY) / entryH);
        zoneListScroll = Math.max(0, Math.min(zoneListScroll, Math.max(0, zoneMap.size() - maxVis)));
        int y = topY, idx = 0;
        for (String name : zoneMap.keySet()) {
            if (idx >= zoneListScroll && idx < zoneListScroll + maxVis) {
                final String n = name;
                final int zColor = zoneMap.get(name).color();
                Component lbl = Component.literal("■ ").withStyle(st -> st.withColor(zColor))
                    .append(Component.literal((name.equals(selectedZone) ? "§f§l" : "§7") + s.truncate(name, 9)));
                s.add(Button.builder(lbl,
                    b -> { selectedZone = n; zoneDetailTab = 0; s.init(); })
                    .bounds(s.cx + 2, y, AdminScreen.ZONE_LIST_W - 4, 16).build());
                y += entryH;
            }
            idx++;
        }

        if (selectedZone == null) return;
        ZoneData z = zoneMap.get(selectedZone);
        if (z == null) return;

        // Sub-tabs
        int tabW = znDetW / 4;
        String[] tabLabels = { Lang.t("MEMBRES", "MEMBERS"), "COORDS", "OPTIONS", "MSG" };
        for (int i = 0; i < tabLabels.length; i++) {
            final int ti = i;
            boolean active = zoneDetailTab == i;
            Component lbl = Component.literal(tabLabels[i]).withStyle(
                active ? st -> st.withColor(0x00E5FF).withBold(true) : st -> st.withColor(0x777777));
            s.add(Button.builder(lbl, b -> { zoneDetailTab = ti; s.init(); })
                .bounds(znDetX + tabW * i, s.py + 50, tabW - 1, 16).build());
        }

        int contentTop = s.py + 70;
        int contentBot = s.py + s.ph - 5;
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
            s.add(s.btn("§c✕", b -> sendZone("REMOVE_MEMBER", selectedZone, uuid))
                .bounds(detX + halfW - 20, entryY + i * 18, 16, 14).build());
        }
        int ri = 0;
        for (String playerName : zoneOnline) {
            if (ri >= maxRows) break;
            boolean isMember = z.members().stream().anyMatch(m -> m[1].equalsIgnoreCase(playerName));
            if (!isMember) {
                final String pn = playerName;
                s.add(s.btn("§a+", b -> sendZone("ADD_MEMBER", selectedZone, pn))
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
            s.add(b);
        s.add(s.btn(Lang.t("§aSAUVEGARDER", "§aSAVE"), b -> {
            try {
                sendZone("UPDATE_COORDS", selectedZone,
                    zMinXBox.getValue() + "," + zMinYBox.getValue() + "," + zMinZBox.getValue() + "," +
                    zMaxXBox.getValue() + "," + zMaxYBox.getValue() + "," + zMaxZBox.getValue());
            } catch (Exception ignored) {}
        }).bounds(detX + detW - 106, top + 80, 98, 18).build());

        // Priorité (zones superposées : la plus haute décide)
        zPriorityBox = zBox(lx, top + 112, 36, String.valueOf(z.priority()));
        zPriorityBox.setMaxLength(3);
        s.add(zPriorityBox);
        s.add(s.btn("§aOK", b -> sendZone("SET_PRIORITY", selectedZone, zPriorityBox.getValue()))
            .bounds(lx + 42, top + 112, 30, 16).build());
    }

    private void buildZoneOptions(ZoneData z, int detX, int detW, int top, int bot) {
        int w = detW - 8, lx = detX + 4;
        int rows = 6 + ZoneFlag.values().length;
        int rowH = Math.max(13, Math.min(20, (bot - top) / rows));
        int bh   = Math.max(12, rowH - 2);
        int y = top;

        s.add(s.btn(Lang.t("État de la zone : ", "Zone state: ")
                + (z.enabled() ? Lang.t("§aACTIVÉE", "§aENABLED") : Lang.t("§cDÉSACTIVÉE", "§cDISABLED")),
            b -> sendZone("TOGGLE_ENABLED", selectedZone, "")).bounds(lx, y, w, bh).build());
        y += rowH;

        s.add(s.btn(Lang.t("Vision nocturne : ", "Night vision: ") + (z.nightVision() ? "§aON" : "§cOFF"),
            b -> sendZone("TOGGLE_NIGHT_VISION", selectedZone, "")).bounds(lx, y, w, bh).build());
        y += rowH;

        int cIdx = Math.floorMod(z.colorIdx(), Zone.COLORS.length);
        Component colorLbl = Component.literal(Lang.t("Couleur : ", "Color: "))
            .append(Component.literal("■ ").withStyle(st -> st.withColor(z.color())))
            .append(Lang.t(Zone.COLOR_NAMES[cIdx], Zone.COLOR_NAMES_EN[cIdx]));
        s.add(Button.builder(colorLbl,
            b -> sendZone("CYCLE_COLOR", selectedZone, "")).bounds(lx, y, w, bh).build());
        y += rowH;

        // Presets : combinaison complète de flags en un clic
        int pw3 = (w - 8) / 3;
        s.add(s.btn("§bSpawn", b -> sendZone("APPLY_PRESET", selectedZone, "SPAWN"))
            .bounds(lx, y, pw3, bh).build());
        s.add(s.btn(Lang.t("§cArène", "§cArena"), b -> sendZone("APPLY_PRESET", selectedZone, "ARENA"))
            .bounds(lx + pw3 + 4, y, pw3, bh).build());
        s.add(s.btn("§6VIP", b -> sendZone("APPLY_PRESET", selectedZone, "VIP"))
            .bounds(lx + (pw3 + 4) * 2, y, pw3, bh).build());
        y += rowH;

        for (ZoneFlag fl : ZoneFlag.values()) {
            boolean allowed = z.flags().getOrDefault(fl, fl.defaultAllowed);
            s.add(s.btn(Lang.t(fl.label, fl.labelEn) + Lang.t(" : ", ": ")
                    + (allowed ? Lang.t("§aautorisé", "§aallowed") : Lang.t("§cbloqué", "§cblocked")),
                b -> sendZone("TOGGLE_FLAG", selectedZone, fl.name())).bounds(lx, y, w, bh).build());
            y += rowH;
        }

        s.add(s.btn(Lang.t("§eTéléporter vers la zone", "§eTeleport to zone"),
            b -> sendZone("TP_ZONE", selectedZone, "")).bounds(lx, y, w, bh).build());
        y += rowH;
        s.add(s.btn(Lang.t("§c§lSUPPRIMER LA ZONE", "§c§lDELETE ZONE"),
            b -> { final String zn = selectedZone; s.askConfirm(
                Lang.t("Supprimer la zone « " + zn + " » ?", "Delete zone \"" + zn + "\"?"),
                () -> { sendZone("DELETE_ZONE", zn, ""); selectedZone = null; }); })
            .bounds(lx, y, w, bh).build());
    }

    private void buildZoneMessages(ZoneData z, int detX, int detW, int top, int bot) {
        int lx = detX + 8, w = detW - 16;

        zGreetingBox = new EditBox(s.font(), lx, top + 14, w, 16, Component.empty());
        zGreetingBox.setMaxLength(100);
        zGreetingBox.setValue(z.greeting());
        s.add(zGreetingBox);

        zFarewellBox = new EditBox(s.font(), lx, top + 52, w, 16, Component.empty());
        zFarewellBox.setMaxLength(100);
        zFarewellBox.setValue(z.farewell());
        s.add(zFarewellBox);

        s.add(s.btn(Lang.t("§aSAUVEGARDER", "§aSAVE"), b ->
            sendZone("SET_MESSAGES", selectedZone, zGreetingBox.getValue() + "\t" + zFarewellBox.getValue()))
            .bounds(detX + detW - 106, top + 78, 98, 18).build());
    }

    // ─── render ───────────────────────────────────────────────────────────────────
    void render(GuiGraphics g) {
        int znDetX = s.cx + AdminScreen.ZONE_LIST_W + 1;
        int znDetW = (s.px + s.pw) - znDetX;

        // Divider between list and detail
        g.fill(s.cx + AdminScreen.ZONE_LIST_W, s.py + 26, s.cx + AdminScreen.ZONE_LIST_W + 1, s.py + s.ph - 5, AdminScreen.C_ACCENT);

        // List header + baguette separator
        g.drawString(s.font(), "ZONES", s.cx + 4, s.py + 28, 0xFF888888);
        g.fill(s.cx + 2, s.py + s.ph - 28, s.cx + AdminScreen.ZONE_LIST_W - 2, s.py + s.ph - 27, AdminScreen.C_DIV);

        if (zoneMap.isEmpty()) {
            g.drawCenteredString(s.font(), Lang.t("§8Aucune zone", "§8No zones"), s.cx + AdminScreen.ZONE_LIST_W / 2, s.py + s.ph / 2 - 14, 0xFF444444);
            g.drawCenteredString(s.font(), "§8/zone create", s.cx + AdminScreen.ZONE_LIST_W / 2, s.py + s.ph / 2 - 2, 0xFF333333);
        }

        // Selected zone highlight in list
        if (selectedZone != null) {
            int topY = s.py + 40, entryH = 20, vi = 0;
            int lIdx = 0;
            for (String name : zoneMap.keySet()) {
                if (lIdx >= zoneListScroll) {
                    if (name.equals(selectedZone)) {
                        g.fill(s.cx + 1, topY + vi * entryH, s.cx + AdminScreen.ZONE_LIST_W - 1, topY + vi * entryH + 18, AdminScreen.C_TABSEL);
                        g.fill(s.cx + 1, topY + vi * entryH, s.cx + 3, topY + vi * entryH + 18, AdminScreen.C_ACCENT);
                    }
                    vi++;
                }
                lIdx++;
            }
        }

        if (selectedZone == null) {
            if (!zoneMap.isEmpty())
                g.drawCenteredString(s.font(), Lang.t("§8Sélectionnez une zone", "§8Select a zone"), znDetX + znDetW / 2, s.py + s.ph / 2, 0xFF444444);
            return;
        }
        ZoneData z = zoneMap.get(selectedZone);
        if (z == null) return;

        // Zone info bar
        g.fill(znDetX, s.py + 26, s.px + s.pw, s.py + 48, 0xFF0A0A0A);
        int sx = z.x2()-z.x1()+1, sy = z.y2()-z.y1()+1, sz = z.z2()-z.z1()+1;
        g.drawString(s.font(), "§e§l" + selectedZone, znDetX + 4, s.py + 28, 0xFFFFFFFF);
        if (!z.inside().isEmpty())
            g.drawString(s.font(), "§a◉ " + z.inside().size() + Lang.t(" présent" + (z.inside().size() > 1 ? "s" : ""), " inside"),
                znDetX + 10 + s.font().width("§e§l" + selectedZone), s.py + 28, 0xFF55FF55);
        g.drawString(s.font(), "§8" + sx + "×" + sy + "×" + sz
            + "  (" + z.x1() + "," + z.y1() + "," + z.z1()
            + ") → (" + z.x2() + "," + z.y2() + "," + z.z2() + ")",
            znDetX + 4, s.py + 38, 0xFF555555);
        g.fill(znDetX, s.py + 47, s.px + s.pw, s.py + 48, AdminScreen.C_DIV);

        // Sub-tab highlight
        int tabW = znDetW / 4;
        int tx = znDetX + tabW * zoneDetailTab;
        g.fill(tx, s.py + 48, tx + tabW - 1, s.py + 66, AdminScreen.C_TABSEL);
        g.fill(tx, s.py + 64, tx + tabW - 1, s.py + 66, AdminScreen.C_ACCENT);
        g.fill(znDetX, s.py + 66, s.px + s.pw, s.py + 67, AdminScreen.C_DIV);

        int contentTop = s.py + 70;
        int contentBot = s.py + s.ph - 5;
        if (zoneDetailTab == 0) renderZoneMembers(g, z, znDetX, znDetW, contentTop, contentBot);
        else if (zoneDetailTab == 1) renderZoneCoords(g, z, znDetX, znDetW, contentTop, contentBot);
        else if (zoneDetailTab == 3) renderZoneMessages(g, z, znDetX, znDetW, contentTop, contentBot);
    }

    private void renderZoneMessages(GuiGraphics g, ZoneData z, int detX, int detW, int top, int bot) {
        int lx = detX + 8;
        g.drawString(s.font(), Lang.t("§7MESSAGE D'ENTRÉE", "§7ENTRY MESSAGE"), lx, top + 4, 0xFF888888);
        g.drawString(s.font(), Lang.t("§7MESSAGE DE SORTIE", "§7EXIT MESSAGE"), lx, top + 42, 0xFF888888);
        g.drawString(s.font(), Lang.t("§8Affiché en action-bar. Vide = aucun message.",
            "§8Shown in the action bar. Empty = no message."), lx, top + 102, 0xFF444444);
    }

    private void renderZoneMembers(GuiGraphics g, ZoneData z, int detX, int detW, int top, int bot) {
        int halfW  = detW / 2;
        int rightX = detX + halfW + 2;
        int entryY = top + 14;
        int maxRows = (bot - entryY) / 18;

        g.fill(detX + halfW, top, detX + halfW + 1, bot, AdminScreen.C_DIV);
        g.fill(detX, top + 12, s.px + s.pw, top + 13, AdminScreen.C_DIV);
        g.drawString(s.font(), Lang.t("MEMBRES", "MEMBERS") + " §7(" + z.members().size() + ")", detX + 4, top + 2, 0xFF888888);
        g.drawString(s.font(), Lang.t("JOUEURS EN LIGNE", "ONLINE PLAYERS"), rightX + 4, top + 2, 0xFF888888);

        if (z.members().isEmpty()) {
            g.drawString(s.font(), Lang.t("§8Ouvert — tous autorisés", "§8Open — everyone allowed"), detX + 6, entryY + 3, 0xFF3A3A3A);
        } else {
            for (int i = 0; i < z.members().size() && i < maxRows; i++) {
                int ry = entryY + i * 18;
                g.fill(detX + 2, ry - 1, detX + halfW - 2, ry + 13, AdminScreen.C_ROW);
                g.drawString(s.font(), "§a● §f" + z.members().get(i)[1], detX + 6, ry + 1, 0xFFFFFFFF);
            }
        }
        if (zoneOnline.isEmpty()) {
            g.drawString(s.font(), Lang.t("§8Aucun joueur en ligne", "§8No players online"), rightX + 4, entryY + 3, 0xFF3A3A3A);
        } else {
            int ri = 0;
            for (String playerName : zoneOnline) {
                if (ri >= maxRows) break;
                boolean isMember = z.members().stream().anyMatch(m -> m[1].equalsIgnoreCase(playerName));
                int ry = entryY + ri * 18;
                g.fill(rightX + 20, ry - 1, s.px + s.pw - 2, ry + 13, AdminScreen.C_ROW);
                drawFace(g, playerName, rightX + 24, ry + 2, 8);
                String insideDot = z.inside().contains(playerName) ? " §a◉" : "";
                g.drawString(s.font(), (isMember ? "§8■ §7" + playerName : "§f" + playerName) + insideDot,
                    rightX + 35, ry + 1, 0xFFFFFFFF);
                ri++;
            }
        }
    }

    private void renderZoneCoords(GuiGraphics g, ZoneData z, int detX, int detW, int top, int bot) {
        int lx = detX + 8, boxW = 40, gap = 4;
        g.drawString(s.font(), Lang.t("§7POINT MIN §8(A)", "§7MIN POINT §8(A)"), lx, top + 4,  0xFF888888);
        g.drawString(s.font(), "§8X", lx,               top + 12, 0xFF555555);
        g.drawString(s.font(), "§8Y", lx + boxW + gap,  top + 12, 0xFF555555);
        g.drawString(s.font(), "§8Z", lx+(boxW+gap)*2,  top + 12, 0xFF555555);
        g.drawString(s.font(), Lang.t("§7POINT MAX §8(B)", "§7MAX POINT §8(B)"), lx, top + 42, 0xFF888888);
        g.drawString(s.font(), "§8X", lx,               top + 50, 0xFF555555);
        g.drawString(s.font(), "§8Y", lx + boxW + gap,  top + 50, 0xFF555555);
        g.drawString(s.font(), "§8Z", lx+(boxW+gap)*2,  top + 50, 0xFF555555);

        g.drawString(s.font(), Lang.t("§7PRIORITÉ §8(la plus haute décide)", "§7PRIORITY §8(highest wins)"), lx, top + 101, 0xFF888888);
    }
}
