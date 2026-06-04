package Fabric.test;

import Fabric.test.networking.GroupActionPayload;
import Fabric.test.networking.GroupUpdatePayload;
import Fabric.test.networking.OpenGroupPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.stream.Collectors;

public class GroupManager {

    // leader UUID â†’ ordered set of all member UUIDs (leader is always first)
    private static final Map<UUID, LinkedHashSet<UUID>> groups = new HashMap<>();
    // member UUID â†’ leader UUID (reverse lookup)
    private static final Map<UUID, UUID> membership = new HashMap<>();
    // pending invite: invitee UUID â†’ inviter UUID
    private static final Map<UUID, UUID> pendingInvites = new HashMap<>();
    // per-player settings
    private static final Map<UUID, Integer> colors     = new HashMap<>(); // 0-7
    private static final Map<UUID, Boolean> showNames  = new HashMap<>();
    private static final Map<UUID, Boolean> groupTrust = new HashMap<>();
    // per-group name (keyed by leader UUID)
    private static final Map<UUID, String>  groupNames = new HashMap<>();

    private static int tickCounter = 0;

    private static final net.minecraft.ChatFormatting[] COLOR_FORMAT = {
        net.minecraft.ChatFormatting.GREEN,        // Â§a
        net.minecraft.ChatFormatting.AQUA,         // Â§b
        net.minecraft.ChatFormatting.RED,          // Â§c
        net.minecraft.ChatFormatting.LIGHT_PURPLE, // Â§d
        net.minecraft.ChatFormatting.YELLOW,       // Â§e
        net.minecraft.ChatFormatting.GOLD,         // Â§6
        net.minecraft.ChatFormatting.BLUE,         // Â§9
        net.minecraft.ChatFormatting.DARK_PURPLE,  // Â§5
        net.minecraft.ChatFormatting.WHITE,        // Â§f
        net.minecraft.ChatFormatting.GRAY,         // Â§7
        net.minecraft.ChatFormatting.DARK_GREEN,   // Â§2
        net.minecraft.ChatFormatting.DARK_AQUA,    // Â§3
        net.minecraft.ChatFormatting.DARK_RED,     // Â§4
        net.minecraft.ChatFormatting.DARK_BLUE,    // Â§1
        net.minecraft.ChatFormatting.DARK_GRAY,    // Â§8
        net.minecraft.ChatFormatting.GOLD,         // salmon approx
    };

    // â”€â”€â”€ public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static boolean isInGroup(UUID uuid) { return membership.containsKey(uuid); }
    public static UUID    getLeader(UUID uuid)  { return membership.get(uuid); }
    public static boolean isLeader(UUID uuid)   { UUID l = membership.get(uuid); return uuid.equals(l); }
    public static int     getColor(UUID uuid)   { return colors.getOrDefault(uuid, 0); }
    public static boolean isShowNames(UUID uuid){ return showNames.getOrDefault(uuid, true); }
    public static boolean isGroupTrust(UUID uuid){ return groupTrust.getOrDefault(uuid, false); }

    // â”€â”€â”€ persistence helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
            String leaderName = Test.getPlayerNameCache().getOrDefault(leaderUUID, leaderUUID.toString().substring(0, 8));
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

    // â”€â”€â”€ tick (called from ServerTickEvents) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
                  .append(Test.isAfk(uid) ? '1' : '0').append(':')
                  .append(colors.getOrDefault(uid, 0)).append(':')
                  .append(Test.isVanished(uid) ? '1' : '0');
            }
            if (sb.length() == 0) continue;
            GroupUpdatePayload pkt = new GroupUpdatePayload(sb.toString());
            for (UUID uid : members) {
                ServerPlayer p = server.getPlayerList().getPlayer(uid);
                if (p != null) PacketDistributor.sendToPlayer(p, pkt);
            }
        }
    }

    // â”€â”€â”€ action handler â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void handleAction(GroupActionPayload payload, ServerPlayer actor, MinecraftServer server) {
        UUID actorUUID = actor.getUUID();
        switch (payload.action()) {
            case "CREATE" -> {
                if (isInGroup(actorUUID)) {
                    actor.sendSystemMessage(Component.literal("Â§cVous Ãªtes dÃ©jÃ  dans un groupe."));
                    return;
                }
                String gName = payload.value().trim();
                if (gName.isEmpty()) gName = actor.getName().getString() + "'s Group";
                if (gName.length() > 32) gName = gName.substring(0, 32);
                LinkedHashSet<UUID> grp = new LinkedHashSet<>();
                grp.add(actorUUID);
                groups.put(actorUUID, grp);
                membership.put(actorUUID, actorUUID);
                groupNames.put(actorUUID, gName);
                updatePlayerTeam(actor, server);
                sendGroupScreen(actor, server);
                actor.sendSystemMessage(Component.literal("Â§aGroupe Â§f\"" + gName + "\" Â§acrÃ©Ã© !"));
                GroupPersistence.save();
            }
            case "INVITE" -> {
                if (isInGroup(actorUUID) && !isLeader(actorUUID)) {
                    actor.sendSystemMessage(Component.literal("Â§cSeul le leader peut inviter."));
                    return;
                }
                ServerPlayer target = server.getPlayerList().getPlayerByName(payload.value());
                if (target == null) { actor.sendSystemMessage(Component.literal("Â§cJoueur introuvable.")); return; }
                if (isInGroup(target.getUUID())) { actor.sendSystemMessage(Component.literal("Â§cCe joueur est dÃ©jÃ  dans un groupe.")); return; }
                pendingInvites.put(target.getUUID(), actorUUID);
                // Show clickable invite to target
                Component msg = Component.literal("Â§e" + actor.getName().getString() + " Â§7vous invite dans son groupe. ")
                    .append(Component.literal("[ACCEPTER]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/groupaccept"))))
                    .append(Component.literal(" "))
                    .append(Component.literal("[REFUSER]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/groupdeny"))));
                target.sendSystemMessage(msg);
                PacketDistributor.sendToPlayer(target, new Fabric.test.networking.NotifPayload("GROUP_INVITE", "Â§aâœ¦ Invitation de Â§f" + actor.getName().getString()));
                actor.sendSystemMessage(Component.literal("Â§7Invitation envoyÃ©e Ã  Â§e" + target.getName().getString() + "Â§7."));
            }
            case "ACCEPT" -> {
                UUID inviterUUID = pendingInvites.remove(actorUUID);
                if (inviterUUID == null) { actor.sendSystemMessage(Component.literal("Â§cAucune invitation en attente.")); return; }
                // If inviter is not in a group yet, create one
                if (!isInGroup(inviterUUID)) {
                    LinkedHashSet<UUID> grp = new LinkedHashSet<>();
                    grp.add(inviterUUID);
                    groups.put(inviterUUID, grp);
                    membership.put(inviterUUID, inviterUUID);
                }
                UUID leader = membership.get(inviterUUID);
                LinkedHashSet<UUID> grp = groups.get(leader);
                if (grp == null) { actor.sendSystemMessage(Component.literal("Â§cLe groupe n'existe plus.")); return; }
                grp.add(actorUUID);
                membership.put(actorUUID, leader);
                updatePlayerTeam(actor, server);
                broadcastGroupMessage(server, leader, "Â§a" + actor.getName().getString() + " a rejoint le groupe !");
                broadcastGroupScreen(server, leader);
                GroupPersistence.save();
            }
            case "DENY" -> {
                UUID inv = pendingInvites.remove(actorUUID);
                if (inv != null) {
                    ServerPlayer inviter = server.getPlayerList().getPlayer(inv);
                    if (inviter != null) inviter.sendSystemMessage(Component.literal("Â§c" + actor.getName().getString() + " a refusÃ© l'invitation."));
                }
            }
            case "KICK" -> {
                if (!isLeader(actorUUID)) { actor.sendSystemMessage(Component.literal("Â§cSeul le leader peut expulser.")); return; }
                try {
                    UUID targetUUID = UUID.fromString(payload.value());
                    if (targetUUID.equals(actorUUID)) return;
                    removeFromGroup(targetUUID, server, "Â§cVous avez Ã©tÃ© expulsÃ© du groupe.");
                    broadcastGroupMessage(server, actorUUID, "Â§c" + Test.getPlayerNameCache().getOrDefault(targetUUID, "?") + " a Ã©tÃ© expulsÃ©.");
                    broadcastGroupScreen(server, actorUUID);
                    GroupPersistence.save();
                } catch (IllegalArgumentException ignored) {}
            }
            case "LEAVE" -> {
                if (!isInGroup(actorUUID)) return;
                UUID leader = membership.get(actorUUID);
                String leaveName = actor.getName().getString();
                removeFromGroup(actorUUID, server, "Â§7Vous avez quittÃ© le groupe.");
                if (isInGroup(leader)) {
                    broadcastGroupMessage(server, leader, "Â§7" + leaveName + " a quittÃ© le groupe.");
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

    // â”€â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void onPlayerDisconnect(UUID uuid, MinecraftServer server) {
        pendingInvites.remove(uuid);
        pendingInvites.values().removeIf(v -> v.equals(uuid));
        if (!isInGroup(uuid)) return;
        UUID leader = membership.get(uuid);
        removeFromGroup(uuid, server, null);
        if (isInGroup(leader)) {
            broadcastGroupMessage(server, leader, "Â§7" + Test.getPlayerNameCache().getOrDefault(uuid, "?") + " s'est dÃ©connectÃ©.");
            broadcastGroupScreen(server, leader);
        }
    }

    private static void removeFromGroup(UUID uuid, MinecraftServer server, String msg) {
        UUID leader = membership.remove(uuid);
        if (leader == null) return;
        LinkedHashSet<UUID> grp = groups.get(leader);
        if (grp != null) grp.remove(uuid);

        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null && msg != null) p.sendSystemMessage(Component.literal(msg));
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
                if (nl != null) nl.sendSystemMessage(Component.literal("Â§eVous Ãªtes le nouveau leader du groupe."));
            }
        }

        // Disband if only 1 member left
        if (grp != null && grp.size() == 1) {
            UUID remaining = grp.iterator().next();
            ServerPlayer rp = server.getPlayerList().getPlayer(remaining);
            if (rp != null) rp.sendSystemMessage(Component.literal("Â§7Le groupe a Ã©tÃ© dissous."));
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
            : Test.getPlayerNameCache().getOrDefault(uuid, null);
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
                p.sendSystemMessage(Component.literal("Â§7Le groupe a Ã©tÃ© dissous par le leader."));
                sendGroupScreen(p, server); // Reset HUD
            }
        }
    }

    private static void broadcastGroupMessage(MinecraftServer server, UUID leader, String msg) {
        LinkedHashSet<UUID> members = groups.get(leader);
        if (members == null) return;
        for (UUID m : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) p.sendSystemMessage(Component.literal(msg));
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
                    .filter(m -> server.getPlayerList().getPlayer(m) != null || Test.getPlayerNameCache().containsKey(m))
                    .map(m -> {
                        String name = Test.getPlayerNameCache().getOrDefault(m, m.toString().substring(0, 8));
                        return m + ":" + name + ":" + colors.getOrDefault(m, 0)
                            + ":" + (Test.isAfk(m) ? 1 : 0)
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
        if (inviterUUID != null) pendingFrom = Test.getPlayerNameCache().getOrDefault(inviterUUID, "?");

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
