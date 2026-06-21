package com.lkdm.dashboardadmin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Onglet FEATURES du dashboard admin : 8 toggles de gameplay, cooldowns (/home /back /tpa)
 * + délai AFK, max homes, et les 3 webhooks Discord (Reports/Sanctions/Audit). Les valeurs
 * d'état sont issues du payload serveur → elles restent dans {@link AdminScreen} ; seules les
 * EditBox de saisie vivent ici.
 */
class FeaturesTab {

    private final AdminScreen s;

    private EditBox cdHomeBox, cdBackBox, cdTpaBox, afkDelayBox;
    private EditBox maxHomesBox;
    private EditBox webhookReportsBox, webhookSanctionsBox, webhookAuditBox;

    FeaturesTab(AdminScreen screen) { this.s = screen; }

    void build() {
        int contentW = s.pw - AdminScreen.SIDE_W;
        int bx = s.cx + 8;
        int bw = contentW - 16;
        int hw = (bw - 4) / 2; // half-width for 2-column
        int fy = s.py + 34;

        // Row 1
        s.add(s.btn(Lang.t("PIÉTINEMENT: ", "TRAMPLING: ") + Lang.onOff(s.cropTrampleEnabled),
            b -> { s.send("TOGGLE_CROP_TRAMPLE", "", ""); s.cropTrampleEnabled = !s.cropTrampleEnabled; s.init(); })
            .bounds(bx, fy, hw, 20).build());
        s.add(s.btn("AFK AUTO: " + Lang.onOff(s.afkAutoEnabled),
            b -> { s.send("TOGGLE_AFK_AUTO", "", ""); s.afkAutoEnabled = !s.afkAutoEnabled; s.init(); })
            .bounds(bx + hw + 4, fy, hw, 20).build());

        // Row 2
        s.add(s.btn(Lang.t("SOMMEIL PROPORTIONNEL: ", "PROPORTIONAL SLEEP: ") + Lang.onOff(s.proportionalSleepEnabled),
            b -> { s.send("TOGGLE_PROPORTIONAL_SLEEP", "", ""); s.proportionalSleepEnabled = !s.proportionalSleepEnabled; s.init(); })
            .bounds(bx, fy + 24, hw, 20).build());
        s.add(s.btn(Lang.t("BÛCHERON INTELLIGENT: ", "TREE CAPITATOR: ") + Lang.onOff(s.treeCapitatorEnabled),
            b -> { s.send("TOGGLE_TREE_CAPITATOR", "", ""); s.treeCapitatorEnabled = !s.treeCapitatorEnabled; s.init(); })
            .bounds(bx + hw + 4, fy + 24, hw, 20).build());

        // Row 3
        s.add(s.btn("FAST LEAF DECAY: " + Lang.onOff(s.fastLeafDecayEnabled),
            b -> { s.send("TOGGLE_FAST_LEAF_DECAY", "", ""); s.fastLeafDecayEnabled = !s.fastLeafDecayEnabled; s.init(); })
            .bounds(bx, fy + 48, hw, 20).build());
        s.add(s.btn("DOUBLE DOOR: " + Lang.onOff(s.doubleDoorEnabled),
            b -> { s.send("TOGGLE_DOUBLE_DOOR", "", ""); s.doubleDoorEnabled = !s.doubleDoorEnabled; s.init(); })
            .bounds(bx + hw + 4, fy + 48, hw, 20).build());

        // Row 4
        s.add(s.btn(Lang.t("RÉCOLTE CLIC DROIT: ", "RIGHT-CLICK HARVEST: ") + Lang.onOff(s.rightClickHarvestEnabled),
            b -> { s.send("TOGGLE_RIGHT_CLICK_HARVEST", "", ""); s.rightClickHarvestEnabled = !s.rightClickHarvestEnabled; s.init(); })
            .bounds(bx, fy + 72, hw, 20).build());
        s.add(s.btn(Lang.t("DISTRIBUTEUR RÉCOLTE: ", "DISPENSER HARVEST: ") + Lang.onOff(s.dispenserHarvestEnabled),
            b -> { s.send("TOGGLE_DISPENSER_HARVEST", "", ""); s.dispenserHarvestEnabled = !s.dispenserHarvestEnabled; s.init(); })
            .bounds(bx + hw + 4, fy + 72, hw, 20).build());

        // ── Cooldowns + AFK delay ─────────────────────────────────────────────
        int cy = fy + 122;
        int boxW = 40, boxGap = (bw - 4 * boxW) / 3;
        int b0 = bx;
        int b1 = bx + boxW + boxGap;
        int b2 = bx + (boxW + boxGap) * 2;
        int b3 = bx + (boxW + boxGap) * 3;

        cdHomeBox   = new EditBox(s.font(), b0, cy, boxW, 16, Component.empty());
        cdBackBox   = new EditBox(s.font(), b1, cy, boxW, 16, Component.empty());
        cdTpaBox    = new EditBox(s.font(), b2, cy, boxW, 16, Component.empty());
        afkDelayBox = new EditBox(s.font(), b3, cy, boxW, 16, Component.empty());
        cdHomeBox.setMaxLength(5);   cdBackBox.setMaxLength(5);
        cdTpaBox.setMaxLength(5);    afkDelayBox.setMaxLength(4);
        cdHomeBox.setValue(String.valueOf(s.cdHome));
        cdBackBox.setValue(String.valueOf(s.cdBack));
        cdTpaBox.setValue(String.valueOf(s.cdTpa));
        afkDelayBox.setValue(String.valueOf(s.cdAfk));
        s.add(cdHomeBox);
        s.add(cdBackBox);
        s.add(cdTpaBox);
        s.add(afkDelayBox);

        s.add(s.btn(Lang.t("§aSAUVEGARDER", "§aSAVE"), b -> {
            try {
                int h = Integer.parseInt(cdHomeBox.getValue().trim());
                int bk = Integer.parseInt(cdBackBox.getValue().trim());
                int t = Integer.parseInt(cdTpaBox.getValue().trim());
                int a = Integer.parseInt(afkDelayBox.getValue().trim());
                s.cdHome = h; s.cdBack = bk; s.cdTpa = t; s.cdAfk = a;
                s.send("SET_COOLDOWNS", "", h + "|" + bk + "|" + t);
                s.send("SET_AFK_DELAY", "", String.valueOf(a));
            } catch (NumberFormatException ignored) {}
        }).bounds(s.midX - 55, cy + 22, 110, 20).build());

        // ── Max Homes ─────────────────────────────────────────────────────────
        int hmY = cy + 50;
        maxHomesBox = new EditBox(s.font(), bx, hmY, 36, 16, Component.empty());
        maxHomesBox.setMaxLength(2);
        maxHomesBox.setValue(String.valueOf(s.maxHomes));
        s.add(maxHomesBox);
        s.add(s.btn("§aOK", b -> {
            try {
                int m = Integer.parseInt(maxHomesBox.getValue().trim());
                s.maxHomes = Math.max(1, Math.min(10, m));
                maxHomesBox.setValue(String.valueOf(s.maxHomes));
                s.send("SET_MAX_HOMES", "", String.valueOf(s.maxHomes));
            } catch (NumberFormatException ignored) {}
        }).bounds(bx + 42, hmY, 30, 16).build());

        // ── Discord Webhooks (label column on the left, box fills the rest) ────
        int whY   = hmY + 36;
        int wLblW = s.font().width("Sanctions") + 8;
        int wBoxX = bx + wLblW;
        int wBoxW = bw - wLblW;
        webhookReportsBox   = new EditBox(s.font(), wBoxX, whY,      wBoxW, 16, Component.empty());
        webhookSanctionsBox = new EditBox(s.font(), wBoxX, whY + 22, wBoxW, 16, Component.empty());
        webhookAuditBox     = new EditBox(s.font(), wBoxX, whY + 44, wBoxW, 16, Component.empty());
        webhookReportsBox.setMaxLength(300);
        webhookSanctionsBox.setMaxLength(300);
        webhookAuditBox.setMaxLength(300);
        webhookReportsBox.setValue(s.webhookReports);
        webhookSanctionsBox.setValue(s.webhookSanctions);
        webhookAuditBox.setValue(s.webhookAudit);
        s.add(webhookReportsBox);
        s.add(webhookSanctionsBox);
        s.add(webhookAuditBox);
        s.add(s.btn(Lang.t("§aSAUVEGARDER WEBHOOKS", "§aSAVE WEBHOOKS"), b -> {
            s.webhookReports   = webhookReportsBox.getValue().trim();
            s.webhookSanctions = webhookSanctionsBox.getValue().trim();
            s.webhookAudit     = webhookAuditBox.getValue().trim();
            // value = sanctions + TAB + audit (target = reports)
            s.send("SET_WEBHOOKS", s.webhookReports, s.webhookSanctions + "\t" + s.webhookAudit);
        }).bounds(s.midX - 70, whY + 62, 140, 18).build());
    }

    void render(GuiGraphics g) {
        int contentW = s.pw - AdminScreen.SIDE_W;
        int bx  = s.cx + 8;
        int bw  = contentW - 16;
        int hw  = (bw - 4) / 2;
        int boxW = 40, boxGap = (bw - 4 * boxW) / 3;
        int fy  = s.py + 34;
        int cy  = fy + 122;

        // Feature state tints (behind buttons)
        boolean[][] states = {
            { s.cropTrampleEnabled,       s.afkAutoEnabled          },
            { s.proportionalSleepEnabled, s.treeCapitatorEnabled    },
            { s.fastLeafDecayEnabled,     s.doubleDoorEnabled       },
            { s.rightClickHarvestEnabled, s.dispenserHarvestEnabled }
        };
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int bxc = bx + col * (hw + 4);
                int byc = fy + row * 24;
                boolean on = states[row][col];
                g.fill(bxc, byc, bxc + hw, byc + 20, on ? 0x5500CC44 : 0x55CC3300);
                g.fill(bxc, byc, bxc + 3, byc + 20, on ? 0xFF00CC44 : 0xFFAA3300);
            }
        }

        // Section divider
        g.fill(bx, fy + 100, bx + bw, fy + 101, AdminScreen.C_DIV);
        s.lbl(g, "COOLDOWNS (s) / AFK (min)", bx, fy + 104);

        // Box labels
        int[] xs = { bx, bx + boxW + boxGap, bx + (boxW + boxGap) * 2, bx + (boxW + boxGap) * 3 };
        String[] fLbls = { "/home(s)", "/back(s)", "/tpa(s)", "afk(min)" };
        for (int i = 0; i < 4; i++)
            g.drawString(s.font(), fLbls[i], xs[i], cy - 9, 0xFF666666);

        // Max Homes section
        int hmY = cy + 50;
        g.fill(bx, hmY - 10, bx + bw, hmY - 9, AdminScreen.C_DIV);
        s.lbl(g, Lang.t("MAX HOMES PAR JOUEUR  (1-10)", "MAX HOMES PER PLAYER  (1-10)"), bx, hmY - 7);

        // Discord Webhooks section — labels vertically centred on their boxes (left column)
        int whY = hmY + 36;
        g.fill(bx, whY - 12, bx + bw, whY - 11, AdminScreen.C_DIV);
        s.lbl(g, "DISCORD WEBHOOKS", bx, whY - 8);
        g.drawString(s.font(), "§8Reports",   bx, whY + 4,  0xFF666666);
        g.drawString(s.font(), "§8Sanctions", bx, whY + 26, 0xFF666666);
        g.drawString(s.font(), "§8Audit",     bx, whY + 48, 0xFF666666);
    }
}
