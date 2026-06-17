package com.lkdm.dashboardadmin.client;

import com.lkdm.dashboardadmin.RoleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Onglet RÔLES DE MODÉRATION : liste des rôles (gauche), détail du rôle sélectionné (droite)
 * avec renommage, grille de permissions (onglets/actions), membres assignés et ajout de
 * joueurs en ligne. La liste {@code roles} est issue du payload → elle reste dans
 * {@link AdminScreen} ; la sélection et les brouillons de saisie vivent ici.
 */
class RolesTab {

    private final AdminScreen s;

    private String  selRole        = null; // nom du rôle sélectionné
    private String  createRoleDraft = "";  // texte en cours dans la box de création
    private EditBox roleNameBox;
    private EditBox roleRenameBox;
    private String  roleRenameDraft = null; // brouillon de renommage
    private String  roleRenameFor   = null; // rôle pour lequel le brouillon est valide

    RolesTab(AdminScreen screen) { this.s = screen; }

    private AdminScreen.RoleEntry selectedRole() {
        if (selRole == null) return null;
        for (AdminScreen.RoleEntry r : s.roles) if (r.name().equalsIgnoreCase(selRole)) return r;
        return null;
    }

    void build() {
        int listX = s.cx + 5, listW = 104;
        int listTop = s.py + 44, rowH = 15;
        int createBoxY = s.py + s.ph - 38;

        // Liste des rôles (clic = sélection)
        int maxRows = Math.max(1, (createBoxY - 6 - listTop) / rowH);
        for (int i = 0; i < s.roles.size() && i < maxRows; i++) {
            final AdminScreen.RoleEntry r = s.roles.get(i);
            boolean sel = r.name().equalsIgnoreCase(selRole);
            s.add(s.btn((sel ? "§b» " : "§f") + s.truncate(r.name(), 13),
                b -> { selRole = r.name(); s.init(); })
                .bounds(listX, listTop + i * rowH, listW, rowH - 2).build());
        }

        // Création d'un rôle (bas de la colonne gauche)
        roleNameBox = new EditBox(s.font(), listX, createBoxY, listW, 14,
            Component.literal(Lang.t("Nom du rôle", "Role name")));
        roleNameBox.setMaxLength(24);
        roleNameBox.setValue(createRoleDraft);
        roleNameBox.setResponder(v -> createRoleDraft = v);
        s.add(roleNameBox);
        s.add(s.btn(Lang.t("§a+ CRÉER", "§a+ CREATE"), b -> {
            String n = roleNameBox.getValue().trim();
            if (!n.isEmpty()) { createRoleDraft = ""; s.send("ROLE_CREATE", "", n); }
        }).bounds(listX, s.py + s.ph - 22, listW, 16).build());

        // Panneau de droite : détails du rôle sélectionné
        final AdminScreen.RoleEntry sel = selectedRole();
        if (sel == null) return;

        int rx = listX + listW + 10;
        int rightR = s.px + s.pw - 6;
        int colGap = 8;
        int colW = (rightR - rx - colGap) / 2;
        int permTop = s.py + 58, pRowH = 13;

        // Renommer le rôle (haut du panneau droit)
        if (!sel.name().equals(roleRenameFor)) { roleRenameDraft = sel.name(); roleRenameFor = sel.name(); }
        roleRenameBox = new EditBox(s.font(), rx, s.py + 31, rightR - rx - 62, 14,
            Component.literal(Lang.t("Renommer", "Rename")));
        roleRenameBox.setMaxLength(24);
        roleRenameBox.setValue(roleRenameDraft);
        roleRenameBox.setResponder(v -> roleRenameDraft = v);
        s.add(roleRenameBox);
        s.add(s.btn(Lang.t("§eRENOMMER", "§eRENAME"), b -> {
            String nn = roleRenameBox.getValue().trim();
            if (!nn.isEmpty() && !nn.equals(sel.name())) {
                String old = sel.name(); selRole = nn; roleRenameFor = null;
                s.send("ROLE_RENAME", old, nn);
            }
        }).bounds(rightR - 58, s.py + 31, 58, 14).build());

        // Colonne A — onglets autorisés
        for (int i = 0; i < RoleManager.TAB_PERMS.length; i++) {
            final String key = RoleManager.TAB_PERMS[i];
            boolean on = sel.perms().contains(key);
            s.add(s.btn((on ? "§a✔ " : "§8✘ ") + permLabel(key),
                b -> s.send("ROLE_TOGGLE_PERM", sel.name(), key))
                .bounds(rx, permTop + i * pRowH, colW, pRowH - 2).build());
        }
        // Colonne B — actions sensibles
        for (int i = 0; i < RoleManager.ACTION_PERMS.length; i++) {
            final String key = RoleManager.ACTION_PERMS[i];
            boolean on = sel.perms().contains(key);
            s.add(s.btn((on ? "§a✔ " : "§8✘ ") + permLabel(key),
                b -> s.send("ROLE_TOGGLE_PERM", sel.name(), key))
                .bounds(rx + colW + colGap, permTop + i * pRowH, colW, pRowH - 2).build());
        }

        // Supprimer le rôle (sous la colonne des actions)
        s.add(s.btn(Lang.t("§cSUPPRIMER", "§cDELETE"),
            b -> { final String n = sel.name(); s.askConfirm(
                Lang.t("Supprimer le rôle « " + n + " » ?", "Delete role \"" + n + "\"?"),
                () -> { selRole = null; s.send("ROLE_DELETE", "", n); }); })
            .bounds(rx + colW + colGap,
                permTop + RoleManager.ACTION_PERMS.length * pRowH + 6, colW, 14).build());

        // Membres assignés (chips avec retrait)
        int memTop = permTop + RoleManager.TAB_PERMS.length * pRowH + 10;
        int by = s.py + s.ph - 8;
        int chipY = memTop + 12, chipX = rx;
        for (String m : sel.members()) {
            int w = s.font().width(m) + 18;
            if (chipX + w > rightR) { chipX = rx; chipY += 16; }
            if (chipY > by - 14) break;
            final String mm = m;
            s.add(s.btn("§f" + m + " §c×", b -> s.send("ROLE_UNASSIGN", sel.name(), mm))
                .bounds(chipX, chipY, w, 14).build());
            chipX += w + 4;
        }

        // Joueurs en ligne à ajouter
        int addY = chipY + 22, addX = rx;
        if (Minecraft.getInstance().getConnection() != null) {
            for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
                if (info.getProfile() == null) continue;
                final String name = info.getProfile().getName();
                if (sel.members().stream().anyMatch(x -> x.equalsIgnoreCase(name))) continue;
                int w = s.font().width(name) + 16;
                if (addX + w > rightR) { addX = rx; addY += 16; }
                if (addY > by - 14) break;
                s.add(s.btn("§a+ §f" + name, b -> s.send("ROLE_ASSIGN", sel.name(), name))
                    .bounds(addX, addY, w, 14).build());
                addX += w + 4;
            }
        }
    }

    void render(GuiGraphics g, int mx, int my) {
        int listX = s.cx + 5, listW = 104;
        int rx = listX + listW + 10;
        int rightR = s.px + s.pw - 6;

        // Séparateur vertical liste / détails
        g.fill(rx - 7, s.py + 28, rx - 6, s.py + s.ph - 6, AdminScreen.C_DIV);

        // En-tête colonne gauche
        s.lbl(g, Lang.t("RÔLES", "ROLES") + " §7(" + s.roles.size() + ")", listX + 2, s.py + 33);
        if (s.roles.isEmpty())
            g.drawString(s.font(), Lang.t("§8Aucun rôle.", "§8No roles."), listX + 2, s.py + 48, 0xFF555555);

        AdminScreen.RoleEntry sel = selectedRole();
        if (sel == null) {
            g.drawCenteredString(s.font(),
                Component.literal(Lang.t("§8← Sélectionnez ou créez un rôle",
                    "§8← Select or create a role")),
                (rx + rightR) / 2, s.py + s.ph / 2, 0xFF555555);
            return;
        }

        int colGap = 8;
        int colW = (rightR - rx - colGap) / 2;
        int permTop = s.py + 58, pRowH = 13;

        s.lbl(g, Lang.t("ONGLETS AUTORISÉS", "ALLOWED TABS"), rx, permTop - 11);
        s.lbl(g, Lang.t("ACTIONS SENSIBLES", "SENSITIVE ACTIONS"), rx + colW + colGap, permTop - 11);

        // Section membres
        int memTop = permTop + RoleManager.TAB_PERMS.length * pRowH + 10;
        g.fill(rx, memTop - 2, rightR, memTop - 1, AdminScreen.C_DIV);
        s.lbl(g, Lang.t("MEMBRES", "MEMBERS") + " §7(" + sel.members().size() + ")", rx, memTop);
        if (sel.members().isEmpty())
            g.drawString(s.font(), Lang.t("§8aucun membre", "§8no members"), rx + 96, memTop, 0xFF555555);

        // Position de la ligne « AJOUTER » (réplique exacte de build)
        int by = s.py + s.ph - 8;
        int chipY = memTop + 12, chipX = rx;
        for (String m : sel.members()) {
            int w = s.font().width(m) + 18;
            if (chipX + w > rightR) { chipX = rx; chipY += 16; }
            if (chipY > by - 14) break;
            chipX += w + 4;
        }
        g.drawString(s.font(), Lang.t("§8AJOUTER (en ligne) :", "§8ADD (online):"),
            rx, chipY + 12, 0xFF555555);
    }

    private static String permLabel(String key) {
        return switch (key) {
            case "tab.monde"     -> Lang.t("Monde", "World");
            case "tab.joueurs"   -> Lang.t("Joueurs", "Players");
            case "tab.chat"      -> "Chat";
            case "tab.features"  -> "Features";
            case "tab.reports"   -> "Reports";
            case "tab.logs"      -> "Logs";
            case "tab.zones"     -> "Zones";
            case "tab.sanctions" -> "Sanctions";
            case "tab.warps"     -> "Warps";
            case "tab.audit"     -> "Audit";
            case "act.ban"       -> "Ban";
            case "act.unban"     -> Lang.t("Déban", "Unban");
            case "act.kick"      -> "Kick";
            case "act.mute"      -> "Mute";
            case "act.gamemode"  -> "Gamemode";
            case "act.inv"       -> Lang.t("Inventaire", "Inventory");
            case "act.vanish"    -> "Vanish";
            case "act.restart"   -> "Restart";
            case "act.manage_roles" -> Lang.t("Gérer rôles", "Manage roles");
            default -> key;
        };
    }
}
