package Fabric.test;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Rôles de modération (Étape 1 — modèle de données + gestion).
 *
 * Un OP crée des rôles nommés (« Helper », « Modo »…), coche les permissions de chaque rôle
 * (onglets du dashboard + actions sensibles) et y assigne des joueurs. Un joueur appartient à
 * au plus un rôle.
 *
 * NB : l'application des permissions (ouverture du dashboard pour les non-OP, filtrage des
 * actions) est l'Étape 2. Ici on ne fait que stocker/gérer. {@link #hasPermission} est déjà
 * fourni pour brancher l'enforcement plus tard.
 */
public class RoleManager {

    /** Permissions « onglet » : donnent accès à un onglet entier du dashboard. */
    public static final String[] TAB_PERMS = {
        "tab.monde", "tab.joueurs", "tab.chat", "tab.features",
        "tab.reports", "tab.logs", "tab.zones", "tab.sanctions", "tab.warps"
    };

    /** Permissions « action sensible » : cochables séparément même si l'onglet est autorisé. */
    public static final String[] ACTION_PERMS = {
        "act.ban", "act.unban", "act.kick", "act.mute",
        "act.gamemode", "act.inv", "act.vanish", "act.restart", "act.manage_roles"
    };

    /** Catalogue complet (onglets puis actions), ordre stable pour la sérialisation/GUI. */
    public static final List<String> ALL_PERMS;
    static {
        List<String> all = new ArrayList<>();
        Collections.addAll(all, TAB_PERMS);
        Collections.addAll(all, ACTION_PERMS);
        ALL_PERMS = Collections.unmodifiableList(all);
    }

    public static final class Role {
        final LinkedHashSet<String> perms   = new LinkedHashSet<>();
        final LinkedHashSet<UUID>   members = new LinkedHashSet<>();
    }

    // nom du rôle (casse conservée pour l'affichage) → Role
    private static final LinkedHashMap<String, Role> roles = new LinkedHashMap<>();

    private static final int MAX_ROLES     = 20;
    private static final int MAX_NAME_LEN  = 24;

    // ─── nom de rôle ──────────────────────────────────────────────────────────────

    /** Nettoie un nom de rôle (alphanumérique + espace _ - ), tronqué, sans séparateurs. */
    public static String sanitizeName(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replaceAll("[\\^|,:]", "").replaceAll("\\s+", " ");
        if (s.length() > MAX_NAME_LEN) s = s.substring(0, MAX_NAME_LEN);
        return s;
    }

    private static String findKey(String name) {
        for (String k : roles.keySet()) if (k.equalsIgnoreCase(name)) return k;
        return null;
    }

    public static boolean isValidPerm(String perm) { return ALL_PERMS.contains(perm); }

    // ─── gestion (appelée par le handler serveur, OP requis en amont) ──────────────

    public static boolean createRole(String rawName) {
        String name = sanitizeName(rawName);
        if (name.isEmpty() || roles.size() >= MAX_ROLES || findKey(name) != null) return false;
        roles.put(name, new Role());
        return true;
    }

    public static boolean deleteRole(String name) {
        String key = findKey(name);
        if (key == null) return false;
        roles.remove(key);
        return true;
    }

    public static boolean renameRole(String oldName, String rawNew) {
        String key = findKey(oldName);
        String neu = sanitizeName(rawNew);
        if (key == null || neu.isEmpty()) return false;
        String clash = findKey(neu);
        if (clash != null && !clash.equals(key)) return false; // un autre rôle a déjà ce nom
        // Réinsertion en conservant l'ordre n'est pas garanti par LinkedHashMap.replace ;
        // on reconstruit pour appliquer le nouveau nom.
        LinkedHashMap<String, Role> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, Role> e : roles.entrySet())
            rebuilt.put(e.getKey().equals(key) ? neu : e.getKey(), e.getValue());
        roles.clear();
        roles.putAll(rebuilt);
        return true;
    }

    public static boolean togglePerm(String name, String perm) {
        String key = findKey(name);
        if (key == null || !isValidPerm(perm)) return false;
        Role r = roles.get(key);
        if (!r.perms.remove(perm)) r.perms.add(perm);
        return true;
    }

    /** Assigne un joueur à un rôle. Un joueur n'appartient qu'à un seul rôle à la fois. */
    public static boolean assignPlayer(String name, UUID uuid) {
        String key = findKey(name);
        if (key == null || uuid == null) return false;
        for (Role r : roles.values()) r.members.remove(uuid); // retire des autres rôles
        roles.get(key).members.add(uuid);
        return true;
    }

    public static boolean unassignPlayer(String name, UUID uuid) {
        String key = findKey(name);
        if (key == null || uuid == null) return false;
        return roles.get(key).members.remove(uuid);
    }

    // ─── lecture (Étape 2 : enforcement) ───────────────────────────────────────────

    /** Rôle d'un joueur, ou null s'il n'en a aucun. */
    public static String getRoleOf(UUID uuid) {
        for (Map.Entry<String, Role> e : roles.entrySet())
            if (e.getValue().members.contains(uuid)) return e.getKey();
        return null;
    }

    /** Vrai si le rôle du joueur possède la permission donnée (n'inclut PAS le bypass OP). */
    public static boolean hasPermission(UUID uuid, String perm) {
        for (Role r : roles.values())
            if (r.members.contains(uuid)) return r.perms.contains(perm);
        return false;
    }

    /** Vrai si le joueur appartient à au moins un rôle de modération. */
    public static boolean hasAnyRole(UUID uuid) { return getRoleOf(uuid) != null; }

    /** Permissions du joueur (ensemble du rôle), ou vide. */
    public static Set<String> getPermsOf(UUID uuid) {
        for (Role r : roles.values()) if (r.members.contains(uuid)) return r.perms;
        return Collections.emptySet();
    }

    /**
     * Contrôle d'accès combiné. Les OP ont tout. Sinon, dépend du rôle.
     * perm spéciale "op" = réservé OP ; "open" = tout détenteur d'un rôle.
     */
    public static boolean can(ServerPlayer p, String perm) {
        if (p.hasPermissions(2)) return true;            // bypass OP
        if (perm == null || perm.equals("op")) return false;
        if (perm.equals("open")) return hasAnyRole(p.getUUID());
        return hasPermission(p.getUUID(), perm);
    }

    /** Permission requise par une action du dashboard. "op" = réservé OP ; "open" = tout rôle. */
    public static String permForAction(String action) {
        return switch (action) {
            // ── MONDE
            case "TOGGLE_PVP", "SET_DAY", "SET_MORNING", "SET_EVENING", "SET_NIGHT",
                 "SET_WEATHER_CLEAR", "SET_WEATHER_RAIN", "SET_WEATHER_THUNDER",
                 "TOGGLE_WEATHER_CYCLE", "CLEAR_LAG", "SET_SPAWN", "REMOVE_MOBS" -> "tab.monde";
            case "VANISH" -> "act.vanish";
            case "SCHEDULE_RESTART", "CANCEL_RESTART" -> "act.restart";
            // ── JOUEURS
            case "HEAL", "TELEPORT_TO", "BRING", "FREEZE", "KEEP_INVENTORY", "ADD_NOTE", "DEL_NOTE" -> "tab.joueurs";
            case "MUTE"     -> "act.mute";
            case "KICK"     -> "act.kick";
            case "BAN"      -> "act.ban";
            case "UNBAN"    -> "act.unban";
            case "GAMEMODE" -> "act.gamemode";
            case "OPEN_INV", "ENDERCHEST" -> "act.inv";
            // ── REPORTS
            case "ACCEPT_REPORT", "CLOSE_REPORT", "REFUSE_REPORT", "FETCH_REPORT_IMAGE" -> "tab.reports";
            // ── CHAT
            case "ANNOUNCE", "LOCK_CHAT", "GET_CHAT", "SET_MOTD", "TOGGLE_MAIL_SPY",
                 "SCHEDULE_ADD", "SCHEDULE_REMOVE" -> "tab.chat";
            // ── FEATURES
            case "SET_MAX_HOMES", "SET_WEBHOOKS", "SET_COOLDOWNS", "SET_AFK_DELAY",
                 "TOGGLE_CROP_TRAMPLE", "TOGGLE_AFK_AUTO", "TOGGLE_PROPORTIONAL_SLEEP",
                 "TOGGLE_TREE_CAPITATOR", "TOGGLE_FAST_LEAF_DECAY", "TOGGLE_DOUBLE_DOOR",
                 "TOGGLE_RIGHT_CLICK_HARVEST", "TOGGLE_DISPENSER_HARVEST" -> "tab.features";
            // ── LOGS / SANCTIONS / WARPS / ZONES
            case "GET_LOGS"      -> "tab.logs";
            case "GET_SANCTIONS" -> "tab.sanctions";
            case "WARP_ADD", "WARP_DELETE", "WARP_TP" -> "tab.warps";
            case "OPEN_ZONES"    -> "tab.zones";
            // ── RÔLES
            case "ROLE_CREATE", "ROLE_DELETE", "ROLE_RENAME", "ROLE_TOGGLE_PERM",
                 "ROLE_ASSIGN", "ROLE_UNASSIGN" -> "act.manage_roles";
            // ── Rafraîchissement : tout détenteur d'accès
            case "REFRESH_ADMIN" -> "open";
            default -> "op";
        };
    }

    // ─── persistance ───────────────────────────────────────────────────────────────

    public static Map<String, Role> getRolesMap() { return roles; }

    public static void restoreRole(String name, Collection<String> perms, Collection<UUID> members) {
        String clean = sanitizeName(name);
        if (clean.isEmpty()) return;
        Role r = new Role();
        if (perms != null) for (String p : perms) if (isValidPerm(p)) r.perms.add(p);
        if (members != null) r.members.addAll(members);
        roles.put(clean, r);
    }

    // ─── sérialisation pour le GUI admin ───────────────────────────────────────────
    //   rôles séparés par '|', champs par '^' :  nom ^ permsCSV ^ membresCSV(noms)
    public static String getRolesSerialized(MinecraftServer server) {
        if (roles.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Role> e : roles.entrySet()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(e.getKey()).append('^');
            sb.append(String.join(",", e.getValue().perms)).append('^');
            StringJoiner names = new StringJoiner(",");
            for (UUID m : e.getValue().members) {
                String n = Test.getPlayerNameCache().getOrDefault(m, null);
                if (n == null) {
                    ServerPlayer p = server.getPlayerList().getPlayer(m);
                    n = p != null ? p.getName().getString() : m.toString().substring(0, 8);
                }
                names.add(n);
            }
            sb.append(names);
        }
        return sb.toString();
    }

    /** Résout un nom de joueur en UUID (joueur en ligne, sinon cache de noms). */
    public static UUID resolveUuid(String playerName, MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(playerName);
        if (online != null) return online.getUUID();
        return Test.getPlayerNameCache().entrySet().stream()
            .filter(en -> en.getValue().equalsIgnoreCase(playerName))
            .map(Map.Entry::getKey).findFirst().orElse(null);
    }
}
