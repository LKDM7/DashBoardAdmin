package Fabric.test.client;

import Fabric.test.networking.OpenZonePayload;
import Fabric.test.networking.ZoneActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

public class ZoneScreen extends Screen {

    private static final int C_BG     = 0xF01A1A1A;
    private static final int C_SIDE   = 0xFF0C0C0C;
    private static final int C_HBAR   = 0xFF111111;
    private static final int C_ACCENT = 0xFF00E5FF;
    private static final int C_DIV    = 0x33FFFFFF;
    private static final int C_ROW    = 0x11FFFFFF;
    private static final int C_SEL    = 0x1A00AAFF;
    private static final int LIST_W   = 118;

    private record ZoneData(int x1, int y1, int z1, int x2, int y2, int z2,
                             List<String[]> members, boolean nightVision) {}

    private final Map<String, ZoneData> zones         = new LinkedHashMap<>();
    private final List<String>          onlinePlayers = new ArrayList<>();

    private String  selected   = null;
    private int     listScroll = 0;
    private int     detailTab  = 0; // 0=MEMBRES  1=COORDS  2=OPTIONS

    // Coord edit boxes (created in buildCoords, read in SAUVEGARDER handler)
    private EditBox minXBox, minYBox, minZBox, maxXBox, maxYBox, maxZBox;

    // Layout — computed in init()
    private int px, py, pw, ph;
    private int detX, detW;

    public ZoneScreen(OpenZonePayload payload) {
        super(Component.literal("ZONES"));
        parse(payload);
    }

    public void onUpdate(OpenZonePayload payload) {
        parse(payload);
        init();
    }

    private void parse(OpenZonePayload payload) {
        zones.clear();
        onlinePlayers.clear();
        if (!payload.zonesSerialized().isEmpty()) {
            for (String line : payload.zonesSerialized().split("\n")) {
                String[] f = line.split("\\|", 5);
                if (f.length < 5) continue;
                try {
                    String[] mn = f[1].split(","), mx = f[2].split(",");
                    List<String[]> members = new ArrayList<>();
                    if (!f[3].isEmpty())
                        for (String m : f[3].split(",")) {
                            String[] um = m.split(":", 2);
                            if (um.length == 2) members.add(um); // [uuid, name]
                        }
                    zones.put(f[0], new ZoneData(
                        Integer.parseInt(mn[0]), Integer.parseInt(mn[1]), Integer.parseInt(mn[2]),
                        Integer.parseInt(mx[0]), Integer.parseInt(mx[1]), Integer.parseInt(mx[2]),
                        members, Boolean.parseBoolean(f[4])));
                } catch (Exception ignored) {}
            }
        }
        if (!payload.onlinePlayers().isEmpty())
            Collections.addAll(onlinePlayers, payload.onlinePlayers().split(";"));
        if (selected != null && !zones.containsKey(selected)) selected = null;
    }

    // ─── init ─────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        pw  = Math.min((int)(width  * 0.82), 560);
        ph  = Math.min((int)(height * 0.80), 340);
        px  = (width  - pw) / 2;
        py  = (height - ph) / 2;
        detX = px + LIST_W + 1;
        detW = pw - LIST_W - 1;

        clearWidgets();

        // Close
        addRenderableWidget(btn("§c✕", b -> onClose())
            .bounds(px + pw - 18, py + 3, 14, 14).build());

        // Give-tool button — always at the bottom of the list panel
        addRenderableWidget(btn("§6✦ Baguette", b -> send("GIVE_TOOL", "", ""))
            .bounds(px + 4, py + ph - 24, LIST_W - 8, 18).build());

        buildZoneList();

        if (selected == null) return;
        ZoneData z = zones.get(selected);
        if (z == null) return;

        // Sub-tabs
        int tabW = detW / 3;
        String[] tabLabels = { "MEMBRES", "COORDS", "OPTIONS" };
        for (int i = 0; i < 3; i++) {
            final int ti = i;
            boolean active = detailTab == i;
            Component lbl = Component.literal(tabLabels[i]).withStyle(
                active ? s -> s.withColor(0x00E5FF).withBold(true) : s -> s.withColor(0x777777));
            addRenderableWidget(Button.builder(lbl, b -> { detailTab = ti; init(); })
                .bounds(detX + tabW * i, py + 46, tabW - 1, 18).build());
        }

        int contentTop = py + 68;
        int contentBot = py + ph - 6;

        switch (detailTab) {
            case 0 -> buildMembers(z, contentTop, contentBot);
            case 1 -> buildCoords(z, contentTop, contentBot);
            case 2 -> buildOptions(z, contentTop, contentBot);
        }
    }

    private void buildZoneList() {
        int topY   = py + 30;
        int botY   = py + ph - 30;
        int entryH = 22;
        int maxVis = Math.max(1, (botY - topY) / entryH);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, zones.size() - maxVis)));

        int y = topY, idx = 0;
        for (String name : zones.keySet()) {
            if (idx >= listScroll && idx < listScroll + maxVis) {
                final String n = name;
                addRenderableWidget(Button.builder(
                    Component.literal((name.equals(selected) ? "§f§l" : "§7") + truncate(name, 11)),
                    b -> { selected = n; detailTab = 0; init(); })
                    .bounds(px + 4, y, LIST_W - 8, 18).build());
                y += entryH;
            }
            idx++;
        }
    }

    // ── MEMBRES ──────────────────────────────────────────────────────────────────

    private void buildMembers(ZoneData z, int top, int bot) {
        int halfW  = detW / 2;
        int rightX = detX + halfW + 2;
        int maxRows = (bot - top) / 18;

        // Left: ✕ buttons for current members
        for (int i = 0; i < z.members().size() && i < maxRows; i++) {
            final String uuid = z.members().get(i)[0];
            addRenderableWidget(btn("§c✕", b -> send("REMOVE_MEMBER", selected, uuid))
                .bounds(detX + halfW - 20, top + i * 18, 16, 14).build());
        }

        // Right: + buttons for online players who are NOT already members
        int ri = 0;
        for (String playerName : onlinePlayers) {
            if (ri >= maxRows) break;
            boolean isMember = z.members().stream().anyMatch(m -> m[1].equalsIgnoreCase(playerName));
            if (!isMember) {
                final String pn = playerName;
                addRenderableWidget(btn("§a+", b -> send("ADD_MEMBER", selected, pn))
                    .bounds(rightX, top + ri * 18, 16, 14).build());
            }
            ri++;
        }
    }

    // ── COORDS ───────────────────────────────────────────────────────────────────

    private void buildCoords(ZoneData z, int top, int bot) {
        int lx   = detX + 8;
        int boxW = 50, gap = 6;

        minXBox = box(lx,               top + 22, boxW, String.valueOf(z.x1()));
        minYBox = box(lx + boxW + gap,  top + 22, boxW, String.valueOf(z.y1()));
        minZBox = box(lx+(boxW+gap)*2,  top + 22, boxW, String.valueOf(z.z1()));
        maxXBox = box(lx,               top + 60, boxW, String.valueOf(z.x2()));
        maxYBox = box(lx + boxW + gap,  top + 60, boxW, String.valueOf(z.y2()));
        maxZBox = box(lx+(boxW+gap)*2,  top + 60, boxW, String.valueOf(z.z2()));

        for (EditBox b : new EditBox[]{minXBox,minYBox,minZBox,maxXBox,maxYBox,maxZBox})
            addRenderableWidget(b);

        addRenderableWidget(btn("§aSAUVEGARDER", b -> {
            try {
                send("UPDATE_COORDS", selected,
                    minXBox.getValue() + "," + minYBox.getValue() + "," + minZBox.getValue() + "," +
                    maxXBox.getValue() + "," + maxYBox.getValue() + "," + maxZBox.getValue());
            } catch (Exception ignored) {}
        }).bounds(detX + detW - 112, top + 84, 104, 18).build());
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.empty());
        b.setMaxLength(8);
        b.setValue(value);
        return b;
    }

    // ── OPTIONS ──────────────────────────────────────────────────────────────────

    private void buildOptions(ZoneData z, int top, int bot) {
        int lx = detX + 4, w = detW - 8;
        addRenderableWidget(btn("Vision nocturne : " + (z.nightVision() ? "§aON" : "§cOFF"),
            b -> send("TOGGLE_NIGHT_VISION", selected, "")).bounds(lx, top,      w, 18).build());
        addRenderableWidget(btn("§eTéléporter vers la zone",
            b -> { send("TP_ZONE", selected, ""); onClose(); }).bounds(lx, top + 24, w, 18).build());
        addRenderableWidget(btn("§c§lSUPPRIMER LA ZONE",
            b -> { send("DELETE_ZONE", selected, ""); selected = null; init(); }).bounds(lx, top + 48, w, 18).build());
    }

    // ─── render ───────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderTransparentBackground(g);

        // Panels
        g.fill(px,         py, px + pw,       py + ph, C_BG);
        g.fill(px,         py, px + LIST_W,   py + ph, C_SIDE);
        g.fill(px + LIST_W, py, px + LIST_W + 1, py + ph, C_ACCENT);

        // Header
        g.fill(px, py, px + pw, py + 22, C_HBAR);
        g.drawCenteredString(font,
            Component.literal("ZONES").withStyle(s -> s.withColor(0x00E5FF).withBold(true)),
            px + pw / 2, py + 7, 0xFFFFFFFF);
        g.fill(px, py + 21, px + pw, py + 22, C_ACCENT);

        g.drawString(font, "ZONES", px + 5, py + 24, 0xFF888888);
        g.fill(px + 4, py + ph - 28, px + LIST_W - 4, py + ph - 27, C_DIV);

        if (zones.isEmpty()) {
            g.drawCenteredString(font, "§8Aucune zone", px + LIST_W / 2, py + ph / 2 - 8, 0xFF444444);
            g.drawCenteredString(font, "§8/zone create", px + LIST_W / 2, py + ph / 2 + 4, 0xFF333333);
        }

        // Selected highlight in list
        if (selected != null) {
            int topY = py + 30, entryH = 22, idx = 0, vi = 0;
            for (String name : zones.keySet()) {
                if (idx >= listScroll) {
                    if (name.equals(selected)) {
                        g.fill(px + 2, topY + vi * entryH, px + LIST_W - 2, topY + vi * entryH + 20, C_SEL);
                        g.fill(px + 2, topY + vi * entryH, px + 4, topY + vi * entryH + 20, C_ACCENT);
                    }
                    vi++;
                }
                idx++;
            }
        }

        // Scrollbar for zone list
        {
            int topY = py + 30, botY = py + ph - 30, entryH = 22;
            int maxVis = Math.max(1, (botY - topY) / entryH);
            if (zones.size() > maxVis) {
                int sbX = px + LIST_W - 5;
                int sbH = botY - topY;
                int thumbH = Math.max(8, sbH * maxVis / zones.size());
                int maxScroll = Math.max(0, zones.size() - maxVis);
                int thumbY = maxScroll > 0 ? topY + (sbH - thumbH) * listScroll / maxScroll : topY;
                g.fill(sbX, topY, sbX + 3, botY, 0x22FFFFFF);
                g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, C_ACCENT);
            }
        }

        if (selected != null) {
            ZoneData z = zones.get(selected);
            if (z != null) renderDetail(g, z);
        } else if (!zones.isEmpty()) {
            g.drawCenteredString(font, "§8Sélectionnez une zone", detX + detW / 2, py + ph / 2, 0xFF444444);
        }

        g.drawString(font, "@LKDM", px + pw - font.width("@LKDM") - 4, py + ph - 10, 0x55AAAAAA, false);

        super.render(g, mx, my, delta);
    }

    private void renderDetail(GuiGraphics g, ZoneData z) {
        // Zone name + coords bar
        g.fill(detX, py + 22, px + pw, py + 44, 0xFF0A0A0A);
        int sx = z.x2()-z.x1()+1, sy = z.y2()-z.y1()+1, sz = z.z2()-z.z1()+1;
        g.drawString(font, "§e§l" + selected, detX + 6, py + 26, 0xFFFFFFFF);
        g.drawString(font, "§8" + sx + "×" + sy + "×" + sz
            + "  (" + z.x1() + "," + z.y1() + "," + z.z1()
            + ") → (" + z.x2() + "," + z.y2() + "," + z.z2() + ")",
            detX + 6, py + 36, 0xFF555555);
        g.fill(detX, py + 43, px + pw, py + 44, C_DIV);

        // Sub-tab highlight
        int tabW = detW / 3;
        if (detailTab >= 0 && detailTab < 3) {
            int tx = detX + tabW * detailTab;
            g.fill(tx, py + 46, tx + tabW - 1, py + 64, C_SEL);
            g.fill(tx, py + 62, tx + tabW - 1, py + 64, C_ACCENT);
        }
        g.fill(detX, py + 63, px + pw, py + 64, C_DIV);

        int contentTop = py + 68;
        int contentBot = py + ph - 6;

        switch (detailTab) {
            case 0 -> renderMembers(g, z, contentTop, contentBot);
            case 1 -> renderCoords(g, z, contentTop, contentBot);
            case 2 -> {} // buttons rendered via super.render()
        }
    }

    private void renderMembers(GuiGraphics g, ZoneData z, int top, int bot) {
        int halfW  = detW / 2;
        int rightX = detX + halfW + 2;
        int maxRows = (bot - top) / 18;

        // Column divider
        g.fill(detX + halfW, top - 14, detX + halfW + 1, bot, C_DIV);

        // Headers
        g.fill(detX, top - 14, px + pw, top - 13, C_DIV);
        g.drawString(font, "MEMBRES §7(" + z.members().size() + ")", detX + 4, top - 11, 0xFF888888);
        g.drawString(font, "JOUEURS EN LIGNE", rightX + 4, top - 11, 0xFF888888);

        // Left: current members
        if (z.members().isEmpty()) {
            g.drawString(font, "§8Ouvert — tous autorisés", detX + 6, top + 3, 0xFF3A3A3A);
        } else {
            for (int i = 0; i < z.members().size() && i < maxRows; i++) {
                int ry = top + i * 18;
                g.fill(detX + 2, ry - 1, detX + halfW - 2, ry + 13, C_ROW);
                g.drawString(font, "§a● §f" + z.members().get(i)[1], detX + 6, ry + 1, 0xFFFFFFFF);
            }
            if (z.members().size() > maxRows)
                g.drawString(font, "§8+" + (z.members().size() - maxRows) + "…", detX + 6, bot - 10, 0xFF444444);
        }

        // Right: online players (non-members in full colour, members dimmed, no button)
        if (onlinePlayers.isEmpty()) {
            g.drawString(font, "§8Aucun joueur en ligne", rightX + 4, top + 3, 0xFF3A3A3A);
        } else {
            int ri = 0;
            for (String playerName : onlinePlayers) {
                if (ri >= maxRows) break;
                boolean isMember = z.members().stream().anyMatch(m -> m[1].equalsIgnoreCase(playerName));
                int ry = top + ri * 18;
                g.fill(rightX + 20, ry - 1, px + pw - 2, ry + 13, C_ROW);
                if (isMember) {
                    g.drawString(font, "§8■ §7" + playerName, rightX + 24, ry + 1, 0xFF555555);
                } else {
                    g.drawString(font, "§f" + playerName, rightX + 24, ry + 1, 0xFFFFFFFF);
                }
                ri++;
            }
        }
    }

    private void renderCoords(GuiGraphics g, ZoneData z, int top, int bot) {
        int lx   = detX + 8;
        int boxW = 50, gap = 6;

        // Labels
        g.drawString(font, "§7POINT MIN §8(A)", lx, top + 4,  0xFF888888);
        g.drawString(font, "§8X", lx,               top + 12, 0xFF555555);
        g.drawString(font, "§8Y", lx + boxW + gap,  top + 12, 0xFF555555);
        g.drawString(font, "§8Z", lx+(boxW+gap)*2,  top + 12, 0xFF555555);

        g.drawString(font, "§7POINT MAX §8(B)", lx, top + 42, 0xFF888888);
        g.drawString(font, "§8X", lx,               top + 50, 0xFF555555);
        g.drawString(font, "§8Y", lx + boxW + gap,  top + 50, 0xFF555555);
        g.drawString(font, "§8Z", lx+(boxW+gap)*2,  top + 50, 0xFF555555);
    }

    // ─── misc ─────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < detX) {
            int maxScroll = Math.max(0, zones.size() - (ph - 60) / 22);
            listScroll = Math.max(0, Math.min(listScroll - (int) sy, maxScroll));
            init();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private Button.Builder btn(String label, Button.OnPress handler) {
        return Button.builder(Component.literal(label), handler);
    }

    private void send(String action, String zoneName, String value) {
        ClientPlayNetworking.send(new ZoneActionPayload(action, zoneName, value));
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    @Override public boolean isPauseScreen() { return false; }
}
