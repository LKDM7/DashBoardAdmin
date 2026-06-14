package com.lkdm.dashboardadmin;

import com.lkdm.dashboardadmin.networking.GroupActionPayload;
import com.lkdm.dashboardadmin.networking.GroupUpdatePayload;
import com.lkdm.dashboardadmin.networking.OpenGroupPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.stream.Collectors;

public class GroupManager {

    // leader UUID → ordered set of all member UUIDs (leader is always first)
    private static final Map<UUID, LinkedHashSet<UUID>> groups = new HashMap<>();
    // member UUID → leader UUID (reverse lookup)
    private static final Map<UUID, UUID> membership = new HashMap<>();
    // pending invite: invitee UUID → inviter UUID
    private static final Map<UUID, UUID> pendingInvites = new HashMap<>();
    // per-player settings
    private static final Map<UUID, Integer> colors     = new HashMap<>(); // 0-7
    private static final Map<UUID, Boolean> showNames  = new HashMap<>();
    private static final Map<UUID, Boolean> groupTrust = new HashMap<>();
    // per-group name (keyed by leader UUID)
    private static final Map<UUID, String>  groupNames = new HashMap<>();

    private static int tickCounter = 0;

    private static final net.minecraft.ChatFormatting[] COLOR_FORMAT = {
        net.minecraft.ChatFormatting.GREEN,        // §a
        net.minecraft.ChatFormatting.AQUA,         // §b
        net.minecraft.ChatFormatting.RED,          // §c
        net.minecraft.ChatFormatting.LIGHT_PURPLE, // §d
        net.minecraft.ChatFormatting.YELLOW,       // §e
        net.minecraft.ChatFormatting.GOLD,         // §6
        net.minecraft.ChatFormatting.BLUE,         // §9
        net.minecraft.ChatFormatting.DARK_PURPLE,  // §5
        net.minecraft.ChatFormatting.WHITE,        // §f
        net.minecraft.ChatFormatting.GRAY,         // §7
        net.minecraft.ChatFormatting.DARK_GREEN,   // §2
        net.minecraft.ChatFormatting.DARK_AQUA,    // §3
        net.minecraft.ChatFormatting.DARK_RED,     // §4
        net.minecraft.ChatFormatting.DARK_BLUE,    // §1
        net.minecraft.ChatFormatting.DARK_GRAY,    // §8
        net.minecraft.ChatFormatting.GOLD,         // salmon approx
    };

    // ─── public API ────────────────────────────────────────────────────────────

    public static boolean isInGroup(UUID uuid) { return membership.containsKey(uuid); }
    public static UUID    getLeader(UUID uuid)  { return membership.get(uuid); }
    public static boolean isLeader(UUID uuid)   { UUID l = membership.get(uuid); return uuid.equals(l); }
    public static int     getColor(UUID uuid)   { return colors.getOrDefault(uuid, 0); }
    public static boolean isShowNames(UUID uuid){ return showNames.getOrDefault(uuid, true); }
    public static boolean isGroupTrust(UUID uuid){ return groupTrust.getOrDefault(uuid, false); }

    // ─── persistence helpers ─────────────────────────────────────────────────────
    public static Map<UUID, LinkedHashSet<UUID>> getGroupsMap()    { return groups; }
    public static Map<UUID, UUID>                getMembershipMap(){ return membership; }
    public static Map<UUID, Integer>             getColorsMap()    { return colors; }
    public static Map<UUID, Boolean>             getShowNamesMap() { return showNames; }
    public static Map<UUID, Boolean>             getGroupTrustMap(){ return groupTrust; }
    public static Map<UUID, String>              getGroupNamesMap(){ return groupNames; }
    public static void setColorDirect(UUID uuid, int c)        { colors.put(uuid, c); }
    public static void setShowNamesDirect(UUID uuid, boolean v){ showNames.put(uuid, v); }
    public static void setGroupTrustDirect(UUID uuid, boolean v){ groupTrust.put(uuid, v); }
    public static void restoreGroup(UUID leader, LinkedHashSet<UUID> members) {
        groups.put(leader, members);
        for (UUID m : members) membership.put(m, leader);
    }

    public static String getGroupsSerialized(MinecraftServer server) {
        if (groups.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<UUID, LinkedHashSet<UUID>> e : groups.entrySet()) {
            UUID leaderUUID = e.getKey();
            String leaderName = DashboardAdmin.getPlayerNameCache().getOrDefault(leaderUUID, leaderUUID.toString().substring(0, 8));
            String gName = groupNames.getOrDefault(leaderUUID, "Groupe de " + leaderName);
            if (sb.length() > 0) sb.append('|');
            sb.append(leaderName).append(':').append(gName.replace('|', ' ').replace(':', ' ')).append(':').append(e.getValue().size());
        }
        return sb.toString();
    }

    public static Set<UUID> getMembers(UUID uuid) {
        UUID leader = membership.get(uuid);
        if (leader == null) return Collections.emptySet();
        LinkedHashSet<UUID> m = groups.get(leader);
        return m != null ? m : Collections.emptySet();
    }

    /** True if player2 is in the same group as player1 AND player1 has groupTrust enabled. */
    public static boolean isTrustedByGroup(UUID owner, UUID visitor) {
        if (!isGroupTrust(owner)) return false;
        if (!isInGroup(owner) || !isInGroup(visitor)) return false;
        return getLeader(owner).equals(getLeader(visitor));
    }

    // ─── tick (called from ServerTickEvents) ─────────────────────────────────────

    public static void onTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 4 != 0) return;

        for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : groups.entrySet()) {
            Set<UUID> members = entry.getValue();
            if (members.size() < 2) continue;

            StringBuilder sb = new StringBuilder();
            for (UUID uid : members) {
                ServerPlayer p = server.getPlayerList().getPlayer(uid);
                if (p == null) continue;
                if (sb.length() > 0) sb.append('|');
                sb.append(uid).append(':')
                  .append((int) p.getX()).append(':')
                  .append((int) p.getY()).append(':')
                  .append((int) p.getZ()).append(':')
                  .append((int)(p.getHealth() * 5)).append(':') // 0-100
                  .append(DashboardAdmin.isAfk(uid) ? '1' : '0').append(':')
                  .append(colors.getOrDefault(uid, 0)).append(':')
                  .append(DashboardAdmin.isVanished(uid) ? '1' : '0');
            }
            if (sb.length() == 0) continue;
            GroupUpdatePayload pkt = new GroupUpdatePayload(sb.toString());
            for (UUID uid : members) {
                ServerPlayer p = server.getPlayerList().getPlayer(uid);
                if (p != null) PacketDistributor.sendToPlayer(p, pkt);
            }
        }
    }

    // ─── action handler ──────────────────────────────────────────────────────────

    public static void handleAction(GroupActionPayload payload, ServerPlayer actor, MinecraftServer server) {
        UUID actorUUID = actor.getUUID();
        switch (payload.action()) {
            case "CREATE" -> {
                if (isInGroup(actorUUID)) {
                    actor.sendSystemMessage(Component.literal(SrvLang.t(actor, "§cVous êtes déjà dans un groupe.", "§cYou are already in a group.")));
                    return;
                }
                String gName = payload.value().trim();
                if (gName.isEmpty()) gName = "Groupe de " + actor.getName().getString();
                if (gName.length() > 32) gName = gName.substring(0, 32);
                LinkedHashSet<UUID> grp = new LinkedHashSet<>();
                grp.add(actorUUID);
                groups.put(actorUUID, grp);
                membership.put(actorUUID, actorUUID);
                groupNames.put(actorUUID, gName);
                updatePlayerTeam(actor, server);
                sendGroupScreen(actor, server);
                actor.sendSystemMessage(Component.literal(SrvLang.t(actor, "§aGroupe §f\"" + gName + "\" §acréé !", "§aGroup §f\"" + gName + "\" §acreated!")));
                GroupPersistence.save();
            }
            case "INVITE" -> {
                if (isInGroup(actorUUID) && !isLeader(actorUUID)) {
                    actor.sendSystemMessage(Component.literal(SrvLang.t(actor, "§cSeul le leader peut inviter.", "§cOnly the leader can invite.")));
                    return;
                }
                ServerPlayer target = server.getPlayerList().getPlayerByName(payload.value());
                if (target == null) { actor.sendSystemMessage(Component.literal(SrvLang.t(actor, "§cJoueur introuvable.", "§cPlayer not found."))); return; }
                if (isInGroup(target.getUUID())) { actor.sendSystemMessage(Component.literal(SrvLang.t(actor, "§cCe joueur est déjà dans un groupe.", "§cThis player is already in a group."))); return; }
                pendingInvites.put(target.getUUID(), actorUUID);
                // Show clickable invite to target
                Component msg = Component.literal(SrvLang.t(target, "§e" + actor.getName().getString() + " §7vous invite dans son groupe. ", "§e" + actor.getName().getString() + " §7invites you to their group. "))
                    .append(Component.literal(SrvLang.t(target, "[ACCEPTER]", "[ACCEPT]")).withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/groupaccept"))))
                    .append(Component.literal(" "))
                    .append(Component.literal(SrvLang.t(target, "[REFUSER]", "[DENY]")).withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/groupdeny"))));
                target.sendSystemMessage(msg);
                PacketDistributor.sendToPlayer(target, new com.lkdm.dashboardadmin.networking.NotifPayload("GROUP_INVITE", SrvLang.t(target, "§a✦ Invitation de §f" + actor.getName().getString(), "§a✦ Invitation from §f" + actor.getName().getString())));
                actor.sendSystemMessage(Component.literal(SrvLang.t(actor, "§7Invitation envoyée à §e" + target.getName().getString() + "§7.", "§7Invite sent to §e" + target.getName().getString() + "§7.")));
            }
            case "ACCEPT" -> {
                UUID inviterUUID = pendingInvites.remove(actorUUID);
                if (inviterUUID == null) { actor.sendSystemMessage(Component.literal(SrvLang.t(actor, "§cAucune invitation en attente.", "§cNo pending invitation."))); return; }
                // If inviter is not in a group yet, create one
                if (!isInGroup(inviterUUID)) {
                    LinkedHashSet<UUID> grp = new LinkedHashSet<>();
                    grp.add(inviterUUID);
                    groups.put(inviterUUID, grp);
                    membership.put(inviterUUID, inviterUUID);
                }
                UUID leader = membership.get(inviterUUID);
                LinkedHashSet<UUID> grp = groups.get(leader);
                if (grp == null) { actor.sendSystemMessage(Component.literal(SrvLang.t(actor, "§cLe groupe n'existe plus.", "§cThe group no longer exists."))); return; }
                grp.add(actorUUID);
                membership.put(actorUUID, leader);
                updatePlayerTeam(actor, server);
                broadcastGroupMessage(server, leader, "§a" + actor.getName().getString() + " a rejoint le groupe !", "§a" + actor.getName().getString() + " joined the group!");
                broadcastGroupScreen(server, leader);
                GroupPersistence.save();
            }
            case "DENY" -> {
                UUID inv = pendingInvites.remove(actorUUID);
                if (inv != null) {
                    ServerPlayer inviter = server.getPlayerList().getPlayer(inv);
                    if (inviter != null) inviter.sendSystemMessage(Component.literal(SrvLang.t(inviter, "§c" + actor.getName().getString() + " a refusé l'invitation.", "§c" + actor.getName().getString() + " declined the invitation.")));
                }
            }
            case "KICK" -> {
                if (!isLeader(actorUUID)) { actor.sendSystemMessage(Component.literal(SrvLang.t(actor, "§cSeul le leader peut expulser.", "§cOnly the leader can kick."))); return; }
                try {
                    UUID targetUUID = UUID.fromString(payload.value());
                    if (targetUUID.equals(actorUUID)) return;
                    removeFromGroup(targetUUID, server, "§cVous avez été expulsé du groupe.", "§cYou have been kicked from the group.");
                    broadcastGroupMessage(server, actorUUID, "§c" + DashboardAdmin.getPlayerNameCache().getOrDefault(targetUUID, "?") + " a été expulsé.", "§c" + DashboardAdmin.getPlayerNameCache().getOrDefault(targetUUID, "?") + " was kicked.");
                    broadcastGroupScreen(server, actorUUID);
                    GroupPersistence.save();
                } catch (IllegalArgumentException ignored) {}
            }
            case "LEAVE" -> {
                if (!isInGroup(actorUUID)) return;
                UUID leader = membership.get(actorUUID);
                String leaveName = actor.getName().getString();
                removeFromGroup(actorUUID, server, "§7Vous avez quitté le groupe.", "§7You left the group.");
                if (isInGroup(leader)) {
                    broadcastGroupMessage(server, leader, "§7" + leaveName + " a quitté le groupe.", "§7" + leaveName + " left the group.");
                    broadcastGroupScreen(server, leader);
                }
                GroupPersistence.save();
            }
            case "DISBAND" -> {
                if (!isLeader(actorUUID)) return;
                disbandGroup(actorUUID, server);
                GroupPersistence.save();
            }
            case "SET_COLOR" -> {
                try {
                    int c = Integer.parseInt(payload.value());
                    if (c >= 0 && c < COLOR_FORMAT.length) {
                        colors.put(actorUUID, c);
                        updatePlayerTeam(actor, server);
                        if (isInGroup(actorUUID)) broadcastGroupScreen(server, membership.get(actorUUID));
                        else sendGroupScreen(actor, server);
                    }
                } catch (NumberFormatException ignored) {}
            }
            case "TOGGLE_SHOW_NAMES" -> {
                showNames.put(actorUUID, !isShowNames(actorUUID));
                sendGroupScreen(actor, server);
            }
            case "TOGGLE_GROUP_TRUST" -> {
                groupTrust.put(actorUUID, !isGroupTrust(actorUUID));
                sendGroupScreen(actor, server);
            }
            case "SET_NAME" -> {
                if (!isLeader(actorUUID)) return;
                String name = payload.value().trim();
                if (name.length() > 32) name = name.substring(0, 32);
                groupNames.put(actorUUID, name);
                broadcastGroupScreen(server, actorUUID);
                GroupPersistence.save();
            }
        }
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    public static void onPlayerDisconnect(UUID uuid, MinecraftServer server) {
        pendingInvites.remove(uuid);
        pendingInvites.values().removeIf(v -> v.equals(uuid));
        if (!isInGroup(uuid)) return;
        UUID leader = membership.get(uuid);
        removeFromGroup(uuid, server, null, null);
        if (isInGroup(leader)) {
            broadcastGroupMessage(server, leader, "§7" + DashboardAdmin.getPlayerNameCache().getOrDefault(uuid, "?") + " s'est déconnecté.", "§7" + DashboardAdmin.getPlayerNameCache().getOrDefault(uuid, "?") + " disconnected.");
            broadcastGroupScreen(server, leader);
        }
    }

    private static void removeFromGroup(UUID uuid, MinecraftServer server, String fr, String en) {
        UUID leader = membership.remove(uuid);
        if (leader == null) return;
        LinkedHashSet<UUID> grp = groups.get(leader);
        if (grp != null) grp.remove(uuid);

        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null && fr != null) p.sendSystemMessage(Component.literal(SrvLang.t(p, fr, en)));
        clearPlayerTeam(uuid, server);
        if (p != null) sendGroupScreen(p, server); // Reset HUD: player no longer in membership

        // If the removed player was the leader, promote next or disband
        if (uuid.equals(leader)) {
            if (grp == null || grp.isEmpty()) {
                groups.remove(leader);
            } else {
                UUID newLeader = grp.iterator().next();
                // Re-key the group under the new leader
                groups.remove(leader);
                groups.put(newLeader, grp);
                for (UUID m : grp) membership.put(m, newLeader);
                ServerPlayer nl = server.getPlayerList().getPlayer(newLeader);
                if (nl != null) nl.sendSystemMessage(Component.literal(SrvLang.t(nl, "§eVous êtes le nouveau leader du groupe.", "§eYou are the new group leader.")));
            }
        }

        // Disband if only 1 member left
        if (grp != null && grp.size() == 1) {
            UUID remaining = grp.iterator().next();
            ServerPlayer rp = server.getPlayerList().getPlayer(remaining);
            if (rp != null) rp.sendSystemMessage(Component.literal(SrvLang.t(rp, "§7Le groupe a été dissous.", "§7The group has been disbanded.")));
            UUID remainingLeader = membership.remove(remaining);
            if (remainingLeader != null) groups.remove(remainingLeader);
            clearPlayerTeam(remaining, server);
            if (rp != null) sendGroupScreen(rp, server); // Reset HUD for last remaining member
        }
    }

    private static void updatePlayerTeam(ServerPlayer player, MinecraftServer server) {
        net.minecraft.world.scores.Scoreboard sb = server.getScoreboard();
        String name = player.getScoreboardName();
        net.minecraft.world.scores.PlayerTeam current = sb.getPlayersTeam(name);
        if (current != null && current.getName().startsWith("grp_col_"))
            sb.removePlayerFromTeam(name, current);
        if (isInGroup(player.getUUID())) {
            int ci = Math.max(0, Math.min(colors.getOrDefault(player.getUUID(), 0), COLOR_FORMAT.length - 1));
            String teamName = "grp_col_" + ci;
            net.minecraft.world.scores.PlayerTeam team = sb.getPlayerTeam(teamName);
            if (team == null) {
                team = sb.addPlayerTeam(teamName);
                team.setColor(COLOR_FORMAT[ci]);
            }
            sb.addPlayerToTeam(name, team);
        }
    }

    private static void clearPlayerTeam(UUID uuid, MinecraftServer server) {
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        String name = p != null ? p.getScoreboardName()
            : DashboardAdmin.getPlayerNameCache().getOrDefault(uuid, null);
        if (name == null) return;
        net.minecraft.world.scores.Scoreboard sb = server.getScoreboard();
        net.minecraft.world.scores.PlayerTeam current = sb.getPlayersTeam(name);
        if (current != null && current.getName().startsWith("grp_col_"))
            sb.removePlayerFromTeam(name, current);
    }

    private static void disbandGroup(UUID leader, MinecraftServer server) {
        LinkedHashSet<UUID> members = groups.remove(leader);
        if (members == null) return;
        groupNames.remove(leader);
        for (UUID m : members) {
            membership.remove(m);
            colors.remove(m);
            showNames.remove(m);
            groupTrust.remove(m);
            pendingInvites.values().removeIf(v -> v.equals(m));
            pendingInvites.remove(m);
            clearPlayerTeam(m, server);
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                p.sendSystemMessage(Component.literal(SrvLang.t(p, "§7Le groupe a été dissous par le leader.", "§7The group was disbanded by the leader.")));
                sendGroupScreen(p, server); // Reset HUD
            }
        }
    }

    private static void broadcastGroupMessage(MinecraftServer server, UUID leader, String fr, String en) {
        LinkedHashSet<UUID> members = groups.get(leader);
        if (members == null) return;
        for (UUID m : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) p.sendSystemMessage(Component.literal(SrvLang.t(p, fr, en)));
        }
    }

    public static void broadcastGroupScreen(MinecraftServer server, UUID leader) {
        LinkedHashSet<UUID> members = groups.get(leader);
        if (members == null) return;
        for (UUID m : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) PacketDistributor.sendToPlayer(p, buildGroupPayload(p, server));
        }
    }

    public static void sendGroupScreen(ServerPlayer player, MinecraftServer server) {
        PacketDistributor.sendToPlayer(player, buildGroupPayload(player, server));
    }

    public static OpenGroupPayload buildGroupPayload(ServerPlayer player, MinecraftServer server) {
        UUID uid = player.getUUID();
        // Build members string
        String membersStr = "";
        if (isInGroup(uid)) {
            UUID leader = membership.get(uid);
            LinkedHashSet<UUID> members = groups.get(leader);
            if (members != null) {
                membersStr = members.stream()
                    .filter(m -> server.getPlayerList().getPlayer(m) != null || DashboardAdmin.getPlayerNameCache().containsKey(m))
                    .map(m -> {
                        String name = DashboardAdmin.getPlayerNameCache().getOrDefault(m, m.toString().substring(0, 8));
                        return m + ":" + name + ":" + colors.getOrDefault(m, 0)
                            + ":" + (DashboardAdmin.isAfk(m) ? 1 : 0)
                            + ":" + (m.equals(leader) ? 1 : 0);
                    })
                    .collect(Collectors.joining("|"));
            }
        }
        // Online players for invite list
        String online = server.getPlayerList().getPlayers().stream()
            .filter(p -> !p.getUUID().equals(uid) && !isInGroup(p.getUUID()))
            .map(p -> p.getName().getString())
            .collect(Collectors.joining("|"));
        // Pending invite
        UUID inviterUUID = pendingInvites.get(uid);
        String pendingFrom = "";
        if (inviterUUID != null) pendingFrom = DashboardAdmin.getPlayerNameCache().getOrDefault(inviterUUID, "?");

        UUID groupLeader = isInGroup(uid) ? membership.get(uid) : null;
        String gName = groupLeader != null ? groupNames.getOrDefault(groupLeader, "") : "";
        return new OpenGroupPayload(
            membersStr, online, pendingFrom,
            colors.getOrDefault(uid, 0),
            isShowNames(uid), isGroupTrust(uid),
            gName
        );
    }
}
