package com.lkdm.dashboardadmin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Onglet WARPS du dashboard admin : liste des warps publics (TP / suppression) + création
 * d'un warp à la position de l'admin. La liste {@code warpsList} est remplie depuis le
 * payload serveur → elle reste dans {@link AdminScreen} ; seule la box de saisie vit ici.
 */
class WarpsTab {

    private final AdminScreen s;
    private EditBox nameBox;

    WarpsTab(AdminScreen screen) { this.s = screen; }

    void build() {
        int lx = s.cx + 8;
        int y = s.py + 48;
        int maxBottom = s.py + s.ph - 36;
        for (String[] w : s.warpsList) {
            if (y + 18 > maxBottom) break;
            final String wn = w[0];
            s.add(s.btn("§bTP", b -> { s.send("WARP_TP", "", wn); s.onClose(); })
                .bounds(s.px + s.pw - 66, y, 28, 14).build());
            s.add(s.btn("§c✕", b -> s.askConfirm(
                    Lang.t("Supprimer le warp « " + wn + " » ?", "Delete warp \"" + wn + "\"?"),
                    () -> s.send("WARP_DELETE", "", wn)))
                .bounds(s.px + s.pw - 34, y, 18, 14).build());
            y += 20;
        }
        // Création d'un warp à la position actuelle de l'admin
        nameBox = new EditBox(s.font(), lx, s.py + s.ph - 28, 120, 18, Component.literal("nom du warp"));
        nameBox.setMaxLength(24);
        s.add(nameBox);
        s.add(s.btn(Lang.t("§aCRÉER ICI", "§aCREATE HERE"), b -> {
            String n = nameBox.getValue().trim();
            if (!n.isEmpty()) { s.send("WARP_ADD", "", n); nameBox.setValue(""); }
        }).bounds(lx + 126, s.py + s.ph - 28, 90, 18).build());
    }

    void render(GuiGraphics g) {
        int lx = s.cx + 8;
        s.lbl(g, Lang.t("WARPS PUBLICS", "PUBLIC WARPS") + (s.warpsList.isEmpty() ? "" : " (" + s.warpsList.size() + ")"), lx, s.py + 32);
        g.fill(lx, s.py + 42, s.px + s.pw - 12, s.py + 43, AdminScreen.C_DIV);

        if (s.warpsList.isEmpty()) {
            g.drawCenteredString(s.font(), Lang.t("§8Aucun warp défini", "§8No warps defined"), s.midX, s.py + s.ph / 2 - 6, 0xFF444444);
            g.drawCenteredString(s.font(), Lang.t("§8Créez-en un à votre position ci-dessous", "§8Create one at your position below"), s.midX, s.py + s.ph / 2 + 6, 0xFF333333);
        }

        int y = s.py + 48;
        int maxBottom = s.py + s.ph - 36;
        int shown = 0;
        for (String[] w : s.warpsList) {
            if (y + 18 > maxBottom) {
                g.drawString(s.font(), "§8+" + (s.warpsList.size() - shown) + "…", lx, y, 0xFF444444);
                break;
            }
            if (shown % 2 == 0) g.fill(s.cx + 4, y - 2, s.px + s.pw - 4, y + 14, AdminScreen.C_ROW);
            g.drawString(s.font(), "§b◈ §f" + w[0], lx, y + 2, 0xFFFFFFFF);
            g.drawString(s.font(), "§8" + w[1] + "  §7[" + w[2] + "]",
                lx + 8 + s.font().width("§b◈ §f" + w[0]), y + 2, 0xFF777777);
            y += 20;
            shown++;
        }

        g.fill(lx, s.py + s.ph - 36, s.px + s.pw - 12, s.py + s.ph - 35, AdminScreen.C_DIV);
        g.drawString(s.font(), Lang.t("§8Le warp est créé à votre position actuelle.",
            "§8The warp is created at your current position."), lx + 222, s.py + s.ph - 23, 0xFF444444);
    }
}
