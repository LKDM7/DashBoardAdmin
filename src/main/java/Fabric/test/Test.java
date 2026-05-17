package Fabric.test;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.AdminActionPayload;
import Fabric.test.networking.DealActionPayload;
import Fabric.test.networking.OpenDealPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.InteractionResult;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.Commands;
import java.util.Set;

public class Test implements ModInitializer {
    private static boolean pvpEnabled = true;
    private static boolean chatLocked = false;
    private static boolean weatherCycleEnabled = true;
    private static boolean cropTrampleEnabled = false;
    private static volatile boolean afkAutoEnabled           = false;
    private static volatile long    afkDelayMs               = 5 * 60 * 1000L; // configurable
    private static volatile boolean proportionalSleepEnabled = false;
    private static volatile boolean treeCapitatorEnabled     = false;
    private static volatile boolean fastLeafDecayEnabled     = false;
    private static volatile boolean doubleDoorEnabled          = false;
    private static volatile boolean rightClickHarvestEnabled  = false;
    private static volatile boolean dispenserHarvestEnabled   = false;

    private static final java.util.Map<java.util.UUID, Long>     lastActivityTime = new java.util.HashMap<>();
    private static final java.util.Set<net.minecraft.core.BlockPos> pendingLeafDecay = new java.util.LinkedHashSet<>();
    private static int leafDecayCounter = 0;
    private static int clearLagTicks = -1;
    private static int removeMobsTicks = -1;
    private static final java.util.Map<java.util.UUID, java.util.Map<String, net.minecraft.core.BlockPos>> playerHomes    = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, java.util.Map<String, String>>                       playerHomesDim = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Boolean> frozenPlayers = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> frozenPositions = new java.util.HashMap<>();
    private static final java.util.Set<java.util.UUID> mutedPlayers = new java.util.HashSet<>();
    private static final java.util.Set<java.util.UUID> vanishedPlayers = new java.util.HashSet<>();
    private static final java.util.Map<java.util.UUID, java.util.List<String>> playerLogs = new java.util.HashMap<>();
    private static final java.util.Map<String, String> pendingReports  = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, String> acceptedReports = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, String> closedReports   = new java.util.LinkedHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer>        hostileMobKills = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, PlayerSettings> playerSettings  = new java.util.HashMap<>();

    private static final java.util.Map<java.util.UUID, Long>    lastSeenTimestamps = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long>    lastHomeUse        = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long>    lastBackUse        = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long>    lastTpaUse         = new java.util.HashMap<>();
    private static volatile int cooldownHome = 30;
    private static volatile int cooldownBack = 10;
    private static volatile int cooldownTpa  = 60;
    private static final java.util.List<String>  scheduledMsgs      = new java.util.ArrayList<>();
    private static final java.util.List<Integer> scheduledIntervals = new java.util.ArrayList<>();
    private static final java.util.List<Integer> scheduledCounters  = new java.util.ArrayList<>();

    private static final java.util.List<String[]> playerCommands = java.util.Arrays.asList(
        new String[]{"/menu",      "Ouvrir le menu paramètres & commandes"},
        new String[]{"/mail",      "Envoyer un message privé à un joueur"},
        new String[]{"/r",         "Répondre au dernier message reçu"},
        new String[]{"/tpa",       "Demander une téléportation vers un joueur"},
        new String[]{"/tpaccept",  "Accepter une demande de téléportation"},
        new String[]{"/tpdeny",    "Refuser une demande de téléportation"},
        new String[]{"/sethome",   "Enregistrer un point de retour (max 3)"},
        new String[]{"/home",      "Se téléporter à un home enregistré"},
        new String[]{"/back",      "Retourner à la position précédente"},
        new String[]{"/lock",      "Verrouiller/déverrouiller un bloc regardé"},
        new String[]{"/trust",     "Donner accès à ses blocs verrouillés"},
        new String[]{"/untrust",   "Retirer l'accès à ses blocs verrouillés"},
        new String[]{"/lockinfo",  "Voir les infos d'un bloc verrouillé"},
        new String[]{"/chest",     "Ouvrir son coffre virtuel personnel"},
        new String[]{"/afk",       "Signaler / annuler son absence"},
        new String[]{"/report",    "Envoyer un signalement aux admins"},
        new String[]{"/stats",     "Voir vos statistiques de jeu"},
        new String[]{"/seen",        "Voir la dernière connexion d'un joueur"},
        new String[]{"/deal",        "Proposer un échange d'items à un joueur"},
        new String[]{"/groupaccept", "Accepter une invitation de groupe"},
        new String[]{"/groupdeny",   "Refuser une invitation de groupe"},
        new String[]{"/build",       "Mode construction dans une zone autorisée"}
    );

    public static java.util.Map<java.util.UUID, PlayerSettings> getAllSettings() { return playerSettings; }
    public static PlayerSettings getPlayerSettings(java.util.UUID uuid) { return playerSettings.computeIfAbsent(uuid, k -> new PlayerSettings()); }

    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> lastPositions = new java.util.HashMap<>();

    private static void savePosition(ServerPlayer player) {
        lastPositions.put(player.getUUID(), player.position());
    }

    public static java.util.Map<java.util.UUID, java.util.Map<String, net.minecraft.core.BlockPos>> getAllHomes() { return playerHomes; }
    public static java.util.Map<java.util.UUID, java.util.Map<String, String>> getAllHomesDim()                   { return playerHomesDim; }
    public static java.util.Map<String, net.minecraft.core.BlockPos> getPlayerHomes(java.util.UUID uuid)          { return playerHomes.computeIfAbsent(uuid, k -> new java.util.HashMap<>()); }
    public static java.util.Map<String, String> getPlayerHomesDim(java.util.UUID uuid)                            { return playerHomesDim.computeIfAbsent(uuid, k -> new java.util.HashMap<>()); }

    public static boolean isPvpEnabled() { return pvpEnabled; }
    public static boolean isChatLocked() { return chatLocked; }
    public static boolean isWeatherCycleEnabled() { return weatherCycleEnabled; }
    public static boolean isCropTrampleEnabled() { return cropTrampleEnabled; }
    public static boolean isAfkAutoEnabled()             { return afkAutoEnabled; }
    public static int     getAfkDelayMinutes()           { return (int)(afkDelayMs / 60000L); }
    public static void    setAfkDelayMinutes(int mins)   { afkDelayMs = Math.max(1, mins) * 60000L; }
    public static boolean isProportionalSleepEnabled()  { return proportionalSleepEnabled; }
    public static boolean isTreeCapitatorEnabled()      { return treeCapitatorEnabled; }
    public static boolean isFastLeafDecayEnabled()      { return fastLeafDecayEnabled; }
    public static boolean isDoubleDoorEnabled()         { return doubleDoorEnabled; }

    public static void setChatLocked(boolean v)              { chatLocked = v; }
    public static void setWeatherCycleEnabled(boolean v)     { weatherCycleEnabled = v; }
    public static void setCropTrampleEnabled(boolean v)      { cropTrampleEnabled = v; }
    public static void setAfkAutoEnabled(boolean v)          { afkAutoEnabled = v; }
    public static void setProportionalSleepEnabled(boolean v){ proportionalSleepEnabled = v; }
    public static void setTreeCapitatorEnabled(boolean v)    { treeCapitatorEnabled = v; }
    public static void setFastLeafDecayEnabled(boolean v)    { fastLeafDecayEnabled = v; }
    public static void setDoubleDoorEnabled(boolean v)       { doubleDoorEnabled = v; }
    public static boolean isRightClickHarvestEnabled()       { return rightClickHarvestEnabled; }
    public static boolean isDispenserHarvestEnabled()        { return dispenserHarvestEnabled; }
    public static void setRightClickHarvestEnabled(boolean v){ rightClickHarvestEnabled = v; }
    public static void setDispenserHarvestEnabled(boolean v) { dispenserHarvestEnabled = v; }

    public static String getFeaturesSerialized() {
        return afkAutoEnabled + "|" + proportionalSleepEnabled + "|" + treeCapitatorEnabled
             + "|" + fastLeafDecayEnabled + "|" + doubleDoorEnabled + "|" + getAfkDelayMinutes()
             + "|" + rightClickHarvestEnabled + "|" + dispenserHarvestEnabled
             + "|" + cropTrampleEnabled;
    }

    private static void scheduleNearbyLeaves(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos pos) {
        for (int dx = -4; dx <= 4; dx++) for (int dy = -1; dy <= 5; dy++) for (int dz = -4; dz <= 4; dz++) {
            net.minecraft.core.BlockPos lp = pos.offset(dx, dy, dz);
            if (world.getBlockState(lp).is(net.minecraft.tags.BlockTags.LEAVES))
                pendingLeafDecay.add(lp.immutable());
        }
    }

    private static void addLogNeighbors(net.minecraft.world.level.LevelAccessor world,
                                         net.minecraft.core.BlockPos pos,
                                         java.util.Set<net.minecraft.core.BlockPos> visited,
                                         java.util.Queue<net.minecraft.core.BlockPos> queue) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            net.minecraft.core.BlockPos nb = pos.relative(dir);
            if (visited.add(nb) && world.getBlockState(nb).is(net.minecraft.tags.BlockTags.LOGS)) queue.add(nb);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            net.minecraft.core.BlockPos nb = pos.offset(dx, 0, dz);
            if (visited.add(nb) && world.getBlockState(nb).is(net.minecraft.tags.BlockTags.LOGS)) queue.add(nb);
            nb = pos.offset(dx, 1, dz);
            if (visited.add(nb) && world.getBlockState(nb).is(net.minecraft.tags.BlockTags.LOGS)) queue.add(nb);
        }
    }
    public static boolean isFrozen(java.util.UUID uuid) { return frozenPlayers.getOrDefault(uuid, false); }
    public static boolean isMuted(java.util.UUID uuid) { return mutedPlayers.contains(uuid); }
    public static boolean isVanished(java.util.UUID uuid) { return vanishedPlayers.contains(uuid); }
    public static String getGamemodeName(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer().getName();
    }
    private static final java.time.format.DateTimeFormatter LOG_FMT =
        java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    private static void addLog(java.util.UUID uuid, String entry) {
        String time = java.time.LocalTime.now().format(LOG_FMT);
        playerLogs.computeIfAbsent(uuid, k -> new java.util.ArrayList<>()).add("[" + time + "] " + entry);
    }

    private static final java.util.List<String[]> sanctionsLog = new java.util.ArrayList<>();
    public static java.util.List<String[]> getSanctionsLog() { return sanctionsLog; }

    private static void addSanction(String type, String player, String admin, String reason) {
        String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        if (sanctionsLog.size() >= 200) sanctionsLog.remove(0);
        sanctionsLog.add(new String[]{ ts, type, player, admin, reason.isEmpty() ? "—" : reason });
        SanctionsPersistence.save();
    }

    public static String getSanctionsSerialized() {
        if (sanctionsLog.isEmpty()) return "";
        int start = Math.max(0, sanctionsLog.size() - 50);
        java.util.List<String[]> last = sanctionsLog.subList(start, sanctionsLog.size());
        StringBuilder sb = new StringBuilder();
        for (int i = last.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) sb.append('|');
            String[] e = last.get(i);
            sb.append(e[0]).append('\t').append(e[1]).append('\t')
              .append(e[2]).append('\t').append(e[3]).append('\t').append(e[4]);
        }
        return sb.toString();
    }
    public static String getMutedPlayerNames(net.minecraft.server.MinecraftServer server) {
        return mutedPlayers.stream().map(uuid -> server.getPlayerList().getPlayer(uuid)).filter(p -> p != null).map(p -> p.getName().getString()).collect(java.util.stream.Collectors.joining(";"));
    }
    public static String getFrozenPlayerNames(net.minecraft.server.MinecraftServer server) {
        return frozenPlayers.entrySet().stream().filter(java.util.Map.Entry::getValue).map(e -> server.getPlayerList().getPlayer(e.getKey())).filter(p -> p != null).map(p -> p.getName().getString()).collect(java.util.stream.Collectors.joining(";"));
    }
    private static String serializeReports(java.util.Map<String, String> map) {
        return map.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue().replace("|", " ")).collect(java.util.stream.Collectors.joining("|"));
    }
    public static String getReportsSerialized()         { return serializeReports(pendingReports);  }
    public static String getAcceptedReportsSerialized() { return serializeReports(acceptedReports); }
    public static String getClosedReportsSerialized()   { return serializeReports(closedReports);   }
    public static String getCommandsSerialized() {
        return playerCommands.stream().map(e -> e[0] + ":" + e[1]).collect(java.util.stream.Collectors.joining("|"));
    }
    public static String getKeepInventoryPlayerNames(net.minecraft.server.MinecraftServer server) {
        return playerSettings.entrySet().stream()
            .filter(e -> e.getValue().keepInventory)
            .map(e -> server.getPlayerList().getPlayer(e.getKey()))
            .filter(p -> p != null)
            .map(p -> p.getName().getString())
            .collect(java.util.stream.Collectors.joining(";"));
    }

    private static final java.util.Map<java.util.UUID, java.util.UUID> tpaRequests = new java.util.HashMap<>();
    private record SavedState(NonNullList<ItemStack> items, int xpLevel, float xpProgress, int totalXp) {}
    private static final java.util.Map<java.util.UUID, SavedState> savedStates = new java.util.HashMap<>();

    private static class DealSession {
        final java.util.UUID player1, player2;
        final NonNullList<ItemStack> offer1 = NonNullList.withSize(27, ItemStack.EMPTY);
        final NonNullList<ItemStack> offer2 = NonNullList.withSize(27, ItemStack.EMPTY);
        boolean accepted1 = false, accepted2 = false;

        DealSession(java.util.UUID p1, java.util.UUID p2) { player1 = p1; player2 = p2; }

        boolean isPlayer1(java.util.UUID u) { return player1.equals(u); }
        NonNullList<ItemStack> myOffer(java.util.UUID u)    { return isPlayer1(u) ? offer1 : offer2; }
        NonNullList<ItemStack> theirOffer(java.util.UUID u) { return isPlayer1(u) ? offer2 : offer1; }
        boolean isAccepted(java.util.UUID u)          { return isPlayer1(u) ? accepted1 : accepted2; }
        void setAccepted(java.util.UUID u, boolean v) { if (isPlayer1(u)) accepted1 = v; else accepted2 = v; }
        // Any change to the offer resets BOTH confirmations to avoid blind trades
        void resetAccepted()                          { accepted1 = false; accepted2 = false; }
        java.util.UUID partner(java.util.UUID u)      { return isPlayer1(u) ? player2 : player1; }
        boolean bothAccepted()                        { return accepted1 && accepted2; }
    }
    private static final java.util.Map<java.util.UUID, java.util.UUID>  pendingDeals  = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, DealSession>     activeSessions = new java.util.HashMap<>();

    private static void sendDealPayload(ServerPlayer player, DealSession session) {
        String partnerName = playerNameCache.getOrDefault(session.partner(player.getUUID()), "???");
        boolean myAcc    = session.isAccepted(player.getUUID());
        boolean theirAcc = session.isAccepted(session.partner(player.getUUID()));
        java.util.List<ItemStack> myItems    = new java.util.ArrayList<>(session.myOffer(player.getUUID()));
        java.util.List<ItemStack> theirItems = new java.util.ArrayList<>(session.theirOffer(player.getUUID()));
        ServerPlayNetworking.send(player, new OpenDealPayload(partnerName, false, "", myAcc, theirAcc, myItems, theirItems));
    }

    private static void broadcastDealUpdate(DealSession session, net.minecraft.server.MinecraftServer server) {
        ServerPlayer p1 = server.getPlayerList().getPlayer(session.player1);
        ServerPlayer p2 = server.getPlayerList().getPlayer(session.player2);
        if (p1 != null) sendDealPayload(p1, session);
        if (p2 != null) sendDealPayload(p2, session);
    }

    private static void cancelDeal(DealSession session, net.minecraft.server.MinecraftServer server,
                                    String reasonP1, String reasonP2) {
        activeSessions.remove(session.player1);
        activeSessions.remove(session.player2);
        for (boolean isP1 : new boolean[]{true, false}) {
            java.util.UUID uid = isP1 ? session.player1 : session.player2;
            ServerPlayer p = server.getPlayerList().getPlayer(uid);
            NonNullList<ItemStack> offer = isP1 ? session.offer1 : session.offer2;
            // Return offered items to their owner
            for (ItemStack stack : offer)
                if (!stack.isEmpty() && p != null) p.getInventory().add(stack.copy());
            if (p != null) ServerPlayNetworking.send(p, new OpenDealPayload(
                "", true, isP1 ? reasonP1 : reasonP2, false, false, java.util.List.of(), java.util.List.of()));
        }
    }

    // Cleanup when a player disconnects mid-trade
    private static void handleDealDisconnect(net.minecraft.server.network.ServerGamePacketListenerImpl handler,
                                              net.minecraft.server.MinecraftServer server) {
        java.util.UUID uid = handler.player.getUUID();
        pendingDeals.remove(uid);
        // Also remove any pending deal where this player is the requester
        pendingDeals.values().removeIf(reqUUID -> reqUUID.equals(uid));

        DealSession session = activeSessions.remove(uid);
        if (session == null) return;
        activeSessions.remove(session.partner(uid));

        // Return disconnecting player's items to their own inventory (saved on disconnect)
        NonNullList<ItemStack> myOffer = session.myOffer(uid);
        for (ItemStack stack : myOffer)
            if (!stack.isEmpty()) handler.player.getInventory().add(stack.copy());

        // Notify and refund the online partner
        java.util.UUID partnerUUID = session.partner(uid);
        ServerPlayer partner = server.getPlayerList().getPlayer(partnerUUID);
        if (partner != null) {
            NonNullList<ItemStack> partnerOffer = session.myOffer(partnerUUID);
            for (ItemStack stack : partnerOffer)
                if (!stack.isEmpty()) partner.getInventory().add(stack.copy());
            ServerPlayNetworking.send(partner, new OpenDealPayload("", true,
                handler.player.getName().getString() + " s'est déconnecté. Échange annulé, items restitués.",
                false, false, java.util.List.of(), java.util.List.of()));
        }
    }

    private static void completeDeal(DealSession session, net.minecraft.server.MinecraftServer server) {
        activeSessions.remove(session.player1);
        activeSessions.remove(session.player2);
        ServerPlayer p1 = server.getPlayerList().getPlayer(session.player1);
        ServerPlayer p2 = server.getPlayerList().getPlayer(session.player2);
        // p1 gets offer2, p2 gets offer1
        for (ItemStack stack : session.offer2) if (!stack.isEmpty() && p1 != null) p1.getInventory().add(stack.copy());
        for (ItemStack stack : session.offer1) if (!stack.isEmpty() && p2 != null) p2.getInventory().add(stack.copy());
        if (p1 != null) {
            ServerPlayNetworking.send(p1, new OpenDealPayload("", true, "", false, false, java.util.List.of(), java.util.List.of()));
            p1.sendSystemMessage(Component.literal("§aÉchange complété !"));
        }
        if (p2 != null) {
            ServerPlayNetworking.send(p2, new OpenDealPayload("", true, "", false, false, java.util.List.of(), java.util.List.of()));
            p2.sendSystemMessage(Component.literal("§aÉchange complété !"));
        }
    }

    public static void registerTpaCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
            .executes(context -> {
                ServerPlayer sender = context.getSource().getPlayerOrException();
                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                if (sender == target) return 0;
                if (Fabric.test.command.ZoneCommand.isInBuildMode(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/tpa §cen mode construction."));
                    return 0;
                }
                if (!checkCooldown(lastTpaUse, sender.getUUID(), cooldownTpa, sender, "/tpa")) return 0;
                if (!getPlayerSettings(target.getUUID()).allowTpaRequests) {
                    sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas les demandes de téléportation."));
                    return 0;
                }
                tpaRequests.put(target.getUUID(), sender.getUUID());

                Component msg = Component.literal(sender.getName().getString() + " veut se tp à vous. ")
                    .append(Component.literal("[OUI]").withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN).withClickEvent(new net.minecraft.network .chat.ClickEvent.RunCommand("/tpaccept"))))
                    .append(Component.literal(" "))
                    .append(Component.literal("[NON]").withStyle(style -> style.withColor(net.minecraft.ChatFormatting.RED).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/tpdeny"))));
                
                target.sendSystemMessage(msg);
                sender.sendSystemMessage(Component.literal("§aRequête envoyée à " + target.getName().getString()));
                return 1;
            })));

        dispatcher.register(Commands.literal("tpaccept").executes(context -> {
            ServerPlayer target = context.getSource().getPlayerOrException();
            java.util.UUID senderUUID = tpaRequests.remove(target.getUUID());
            if (senderUUID == null) { target.sendSystemMessage(Component.literal("§cAucune demande.")); return 0; }
            ServerPlayer sender = context.getSource().getServer().getPlayerList().getPlayer(senderUUID);
            if (sender != null) {
                sender.teleportTo((ServerLevel)target.level(), target.getX(), target.getY(), target.getZ(), java.util.Set.of(), sender.getYRot(), sender.getXRot(), true);
                sender.sendSystemMessage(Component.literal("§aTéléporté !"));
            }
            return 1;
        }));

        dispatcher.register(Commands.literal("tpdeny").executes(context -> {
            ServerPlayer target = context.getSource().getPlayerOrException();
            if (tpaRequests.remove(target.getUUID()) != null) target.sendSystemMessage(Component.literal("§cRequête refusée."));
            return 1;
        }));
    }

    private static final java.util.Map<java.util.UUID, java.util.UUID> lastMsg = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Boolean> afkPlayers = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> lastPos = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.core.BlockPos, java.util.UUID> lockedBlocks = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> trustedPlayers = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, String> playerNameCache = new java.util.HashMap<>();

    public static boolean isLocked(net.minecraft.core.BlockPos pos) { return lockedBlocks.containsKey(pos); }
    public static java.util.UUID getOwner(net.minecraft.core.BlockPos pos) { return lockedBlocks.get(pos); }
    public static boolean isTrusted(java.util.UUID owner, java.util.UUID player) { return trustedPlayers.getOrDefault(owner, java.util.Collections.emptySet()).contains(player); }
    public static java.util.Set<java.util.UUID> getTrusted(java.util.UUID owner) { return trustedPlayers.computeIfAbsent(owner, k -> new java.util.HashSet<>()); }
    public static java.util.Map<net.minecraft.core.BlockPos, java.util.UUID> getAllLockedBlocks() { return lockedBlocks; }
    public static java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> getAllTrustedPlayers() { return trustedPlayers; }
    public static java.util.Map<java.util.UUID, String> getPlayerNameCache() { return playerNameCache; }
    public static java.util.Map<java.util.UUID, Integer> getAllHostileMobKills() { return hostileMobKills; }
    private static String resolvePlayerName(net.minecraft.server.MinecraftServer server, java.util.UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        return playerNameCache.getOrDefault(uuid, uuid.toString().substring(0, 8) + "…");
    }

    public static boolean isAfk(java.util.UUID uuid) { return afkPlayers.getOrDefault(uuid, false); }

    public static java.util.Map<java.util.UUID, Long> getLastSeenTimestamps() { return lastSeenTimestamps; }
    public static int  getCooldownHome() { return cooldownHome; }
    public static int  getCooldownBack() { return cooldownBack; }
    public static int  getCooldownTpa()  { return cooldownTpa; }
    public static void setCooldownHome(int v) { cooldownHome = v; }
    public static void setCooldownBack(int v) { cooldownBack = v; }
    public static void setCooldownTpa(int v)  { cooldownTpa  = v; }
    public static String[] getScheduledMsgsArray()      { return scheduledMsgs.toArray(new String[0]); }
    public static int[]    getScheduledIntervalsArray()  { return scheduledIntervals.stream().mapToInt(i -> i).toArray(); }
    public static void addScheduledBroadcast(String msg, int intervalTicks) {
        scheduledMsgs.add(msg);
        scheduledIntervals.add(intervalTicks);
        scheduledCounters.add(intervalTicks);
    }
    public static String getScheduledBroadcastsSerialized() {
        if (scheduledMsgs.isEmpty()) return "";
        java.util.StringJoiner sj = new java.util.StringJoiner("|");
        for (int i = 0; i < scheduledMsgs.size(); i++)
            sj.add(scheduledMsgs.get(i) + "\t" + (scheduledIntervals.get(i) / 1200));
        return sj.toString();
    }
    public static String getCooldownsSerialized() {
        return cooldownHome + "|" + cooldownBack + "|" + cooldownTpa;
    }

    public static String getBannedPlayersSerialized(net.minecraft.server.MinecraftServer server) {
        java.util.StringJoiner sj = new java.util.StringJoiner("|");
        for (net.minecraft.server.players.UserBanListEntry entry : server.getPlayerList().getBans().getEntries()) {
            String name = entry.getUser().name();
            String reason = entry.getReason() != null
                ? entry.getReason().replace("|", " ").replace(":", " ") : "";
            sj.add(name + ":" + reason);
        }
        return sj.toString();
    }

    private static boolean checkCooldown(java.util.Map<java.util.UUID, Long> map, java.util.UUID uuid,
                                         int seconds, ServerPlayer player, String label) {
        long now = System.currentTimeMillis();
        Long last = map.get(uuid);
        if (last != null) {
            long remaining = seconds - (now - last) / 1000;
            if (remaining > 0) {
                player.sendSystemMessage(Component.literal(
                    "§cAttendez encore §e" + remaining + "s §cavant de réutiliser §6" + label + "§c."));
                return false;
            }
        }
        map.put(uuid, now);
        return true;
    }

    private static String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long sec  = diff / 1000;
        if (sec < 60)  return sec + " seconde"  + (sec  > 1 ? "s" : "");
        long min  = sec / 60;
        if (min < 60)  return min + " minute"   + (min  > 1 ? "s" : "");
        long hr   = min / 60;  long m = min % 60;
        if (hr  < 24)  return hr  + "h" + (m  > 0 ? m  + "m" : "");
        long day  = hr  / 24;  long h = hr  % 24;
        return day + " jour" + (day > 1 ? "s" : "") + (h > 0 ? " " + h + "h" : "");
    }

    // ... (à ajouter après tpaRequests)

    // ─── Dispenser harvest helper ──────────────────────────────────────────────

    private static void dispenserHarvestBlock(net.minecraft.server.level.ServerLevel level,
                                               net.minecraft.core.BlockPos pos,
                                               net.minecraft.world.item.ItemStack hoe,
                                               int fortuneLevel) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();

        java.util.List<net.minecraft.world.item.ItemStack> drops = null;
        net.minecraft.world.level.block.state.BlockState resetState = null;

        if (block instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state)) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = crop.getStateForAge(0);

        } else if (block instanceof net.minecraft.world.level.block.NetherWartBlock
                && state.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) == 3) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = state.setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 0);

        } else if (block instanceof net.minecraft.world.level.block.CocoaBlock
                && state.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) == 2) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = state.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 0);

        } else if (state.is(net.minecraft.world.level.block.Blocks.SUGAR_CANE)
                && level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.SUGAR_CANE)) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        } else if (state.is(net.minecraft.world.level.block.Blocks.CACTUS)
                && level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.CACTUS)) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        if (drops == null) return;

        // Apply Fortune bonus manually so all crop types benefit
        if (fortuneLevel > 0) {
            int mult = 1 + level.random.nextInt(fortuneLevel + 1);
            for (net.minecraft.world.item.ItemStack drop : drops) drop.setCount(drop.getCount() * mult);
        }

        level.setBlock(pos, resetState, 3);
        drops.forEach(d -> net.minecraft.world.level.block.Block.popResource(level, pos, d));
    }

    private static void registerDispenserHoeBehavior() {
        net.minecraft.core.dispenser.DispenseItemBehavior behavior = (source, stack) -> {
            if (!dispenserHarvestEnabled) return stack;

            net.minecraft.server.level.ServerLevel level = source.level();
            net.minecraft.core.BlockPos pos   = source.pos();
            net.minecraft.core.Direction facing = source.state()
                .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);

            // Efficiency → range, Fortune → drop multiplier
            // unwrapKey() + path string is the only fully reliable match in 1.21.x
            int effLevel    = 0;
            int fortuneLevel = 0;
            for (var entry : stack.getEnchantments().entrySet()) {
                var optKey = entry.getKey().unwrapKey();
                if (optKey.isEmpty()) continue;
                String path = optKey.get().location().getPath();
                int lvl = entry.getIntValue();
                if ("efficiency".equals(path)) effLevel    = lvl;
                else if ("fortune".equals(path)) fortuneLevel = lvl;
            }

            int range  = 5 + effLevel * 4; // 5,9,13,17,21,25
            int radius = (range - 1) / 2;  // 2,4,6,8,10,12

            // Always scan a horizontal X-Z plane: correct for downward dispensers and
            // for horizontal dispensers placed at crop height or one above.
            net.minecraft.core.BlockPos center = pos.relative(facing);
            boolean isVertical = (facing.getAxis() == net.minecraft.core.Direction.Axis.Y);

            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    // center.offset(a, 0, b) → horizontal X-Z at center's Y
                    dispenserHarvestBlock(level, center.offset(a, 0, b), stack, fortuneLevel);
                    // For horizontal dispensers also check one Y below (dispenser placed 1 above crops)
                    if (!isVertical) {
                        dispenserHarvestBlock(level, center.offset(a, -1, b), stack, fortuneLevel);
                    }
                }
            }
            return stack; // hoe is not consumed
        };

        for (net.minecraft.world.item.Item hoe : new net.minecraft.world.item.Item[]{
            net.minecraft.world.item.Items.WOODEN_HOE,
            net.minecraft.world.item.Items.STONE_HOE,
            net.minecraft.world.item.Items.IRON_HOE,
            net.minecraft.world.item.Items.GOLDEN_HOE,
            net.minecraft.world.item.Items.DIAMOND_HOE,
            net.minecraft.world.item.Items.NETHERITE_HOE
        }) {
            net.minecraft.world.level.block.DispenserBlock.registerBehavior(hoe, behavior);
        }
    }

    @Override
    public void onInitialize() {
        Fabric.test.invsort.networking.InvSortNetworking.register();
        HomePersistence.register();
        registerDispenserHoeBehavior();
        VirtualChestManager.register();
        SettingsPersistence.register();
        LockPersistence.register();
        MobKillsPersistence.register();
        SeenPersistence.register();
        ServerConfig.register();
        ZonePersistence.register();
        SanctionsPersistence.register();
        GroupPersistence.register();

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer sp && getPlayerSettings(sp.getUUID()).keepInventory) {
                net.minecraft.world.entity.player.Inventory inv = sp.getInventory();
                int size = inv.getContainerSize();
                NonNullList<ItemStack> copy = NonNullList.withSize(size, ItemStack.EMPTY);
                for (int i = 0; i < size; i++) copy.set(i, inv.getItem(i).copy());
                savedStates.put(sp.getUUID(), new SavedState(copy, sp.experienceLevel, sp.experienceProgress, sp.totalExperience));
            }
            return true;
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                SavedState saved = savedStates.remove(oldPlayer.getUUID());
                if (saved != null) {
                    net.minecraft.world.entity.player.Inventory inv = newPlayer.getInventory();
                    for (int i = 0; i < saved.items().size() && i < inv.getContainerSize(); i++)
                        inv.setItem(i, saved.items().get(i));
                    newPlayer.experienceLevel    = saved.xpLevel();
                    newPlayer.experienceProgress = saved.xpProgress();
                    newPlayer.totalExperience    = saved.totalXp();
                }
            }
        });

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer sp
                    && isAfk(sp.getUUID())
                    && source.getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
                return false;
            }
            return true;
        });

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof net.minecraft.world.entity.monster.Monster
                    && source.getEntity() instanceof ServerPlayer killer) {
                hostileMobKills.merge(killer.getUUID(), 1, Integer::sum);
            }
            // Log kills of named mobs (any type with a custom nametag)
            if (!(entity instanceof ServerPlayer) && entity.hasCustomName()
                    && source.getEntity() instanceof ServerPlayer killer) {
                String typeName = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(entity.getType()).getPath();
                addLog(killer.getUUID(), "Tué [" + typeName + "] \"" + entity.getCustomName().getString() + "\"");
            }
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            playerNameCache.put(handler.player.getUUID(), handler.player.getName().getString());
            lastActivityTime.put(handler.player.getUUID(), System.currentTimeMillis());
            String joinName = handler.player.getName().getString();
            Component joinMsg = Component.literal("§a[+] §f" + joinName + " §7a rejoint le serveur.");
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (getPlayerSettings(p.getUUID()).showConnectionAlerts && !p.getUUID().equals(handler.player.getUUID()))
                    p.sendSystemMessage(joinMsg);
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            java.util.UUID uid = handler.player.getUUID();
            lastSeenTimestamps.put(uid, System.currentTimeMillis());
            handleDealDisconnect(handler, server);
            lastMsg.remove(uid);
            lastMsg.values().removeIf(v -> v.equals(uid));
            GroupManager.onPlayerDisconnect(uid, server);
            afkPlayers.remove(uid);
            lastPos.remove(uid);
            lastActivityTime.remove(uid);
            tpaRequests.remove(uid);
            tpaRequests.values().removeIf(v -> v.equals(uid));
            String leftName = handler.player.getName().getString();
            Component leftMsg = Component.literal("§c[-] §f" + leftName + " §7a quitté le serveur.");
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (getPlayerSettings(p.getUUID()).showConnectionAlerts && !p.getUUID().equals(handler.player.getUUID()))
                    p.sendSystemMessage(leftMsg);
            }
        });
        PayloadTypeRegistry.playS2C().register(AdminCommand.OpenAdminGuiPayload.TYPE, AdminCommand.OpenAdminGuiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AdminActionPayload.TYPE, AdminActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Fabric.test.networking.OpenSettingsPayload.TYPE, Fabric.test.networking.OpenSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Fabric.test.networking.UpdateSettingsPayload.TYPE, Fabric.test.networking.UpdateSettingsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Fabric.test.networking.PlayerLogsPayload.TYPE, Fabric.test.networking.PlayerLogsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Fabric.test.networking.PlayerActionPayload.TYPE, Fabric.test.networking.PlayerActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Fabric.test.networking.OpenZonePayload.TYPE, Fabric.test.networking.OpenZonePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Fabric.test.networking.ZoneActionPayload.TYPE, Fabric.test.networking.ZoneActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenDealPayload.TYPE, OpenDealPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DealActionPayload.TYPE, DealActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Fabric.test.networking.OpenGroupPayload.TYPE, Fabric.test.networking.OpenGroupPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Fabric.test.networking.GroupActionPayload.TYPE, Fabric.test.networking.GroupActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Fabric.test.networking.GroupUpdatePayload.TYPE, Fabric.test.networking.GroupUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Fabric.test.networking.NotifPayload.TYPE, Fabric.test.networking.NotifPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Fabric.test.networking.OpenSanctionsPayload.TYPE, Fabric.test.networking.OpenSanctionsPayload.CODEC);
        Fabric.test.command.ZoneCommand.registerEvents();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AdminCommand.register(dispatcher);
            Fabric.test.command.ZoneCommand.register(dispatcher);
            registerTpaCommands(dispatcher);

            dispatcher.register(Commands.literal("groupaccept").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                GroupManager.handleAction(new Fabric.test.networking.GroupActionPayload("ACCEPT", ""), player, context.getSource().getServer());
                return 1;
            }));
            dispatcher.register(Commands.literal("groupdeny").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                GroupManager.handleAction(new Fabric.test.networking.GroupActionPayload("DENY", ""), player, context.getSource().getServer());
                return 1;
            }));
            
            dispatcher.register(Commands.literal("mail")
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                .then(Commands.argument("message", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(context -> {
                    ServerPlayer sender = context.getSource().getPlayerOrException();
                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                    if (!getPlayerSettings(target.getUUID()).allowPrivateMessages) {
                        sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas les messages privés."));
                        return 0;
                    }
                    String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "message");
                    lastMsg.put(target.getUUID(), sender.getUUID());
                    target.sendSystemMessage(Component.literal("§e" + sender.getName().getString() + " §7: §f" + msg));
                    sender.sendSystemMessage(Component.literal("§7[moi -> §e" + target.getName().getString() + "§7] §f" + msg));
                    if (getPlayerSettings(target.getUUID()).showChatNotifications)
                        target.sendSystemMessage(Component.literal("§e[✉] Nouveau message de §f" + sender.getName().getString()), true);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target, new Fabric.test.networking.NotifPayload("MAIL", "§b✉ Message de §f" + sender.getName().getString()));
                    return 1;
                }))));

            dispatcher.register(Commands.literal("r")
                .then(Commands.argument("message", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(context -> {
                    ServerPlayer sender = context.getSource().getPlayerOrException();
                    if (!lastMsg.containsKey(sender.getUUID())) {
                        sender.sendSystemMessage(Component.literal("§cAucun message à répondre."));
                        return 0;
                    }
                    java.util.UUID targetUUID = lastMsg.get(sender.getUUID());
                    ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayer(targetUUID);
                    if (target == null) {
                        sender.sendSystemMessage(Component.literal("§cCe joueur n'est plus connecté."));
                        return 0;
                    }
                    
                    String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "message");
                    target.sendSystemMessage(Component.literal("§e" + sender.getName().getString() + " §7: §f" + msg));
                    sender.sendSystemMessage(Component.literal("§7[moi -> §e" + target.getName().getString() + "§7] §f" + msg));
                    lastMsg.put(target.getUUID(), sender.getUUID());
                    return 1;
                })));
            dispatcher.register(Commands.literal("lock")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0, false);
                    if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                        net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
                        if (isLocked(pos)) {
                            if (getOwner(pos).equals(player.getUUID())) {
                                lockedBlocks.remove(pos);
                                player.sendSystemMessage(Component.literal("§aBloc déverrouillé."));
                            } else {
                                player.sendSystemMessage(Component.literal("§cCe bloc ne vous appartient pas."));
                            }
                        } else {
                            lockedBlocks.put(pos, player.getUUID());
                            player.sendSystemMessage(Component.literal("§aBloc verrouillé."));
                        }
                    }
                    return 1;
                }));
            dispatcher.register(Commands.literal("trust")
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                    if (player.getUUID().equals(target.getUUID())) {
                        player.sendSystemMessage(Component.literal("§cVous ne pouvez pas vous faire confiance à vous-même."));
                        return 0;
                    }
                    getTrusted(player.getUUID()).add(target.getUUID());
                    playerNameCache.put(target.getUUID(), target.getName().getString());
                    playerNameCache.put(player.getUUID(), player.getName().getString());
                    player.sendSystemMessage(Component.literal("§a" + target.getName().getString() + " a maintenant accès à vos blocs verrouillés."));
                    target.sendSystemMessage(Component.literal("§a" + player.getName().getString() + " vous a accordé l'accès à ses blocs verrouillés."));
                    return 1;
                })));
            dispatcher.register(Commands.literal("untrust")
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                    getTrusted(player.getUUID()).remove(target.getUUID());
                    player.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'a plus accès à vos blocs verrouillés."));
                    target.sendSystemMessage(Component.literal("§c" + player.getName().getString() + " vous a retiré l'accès à ses blocs verrouillés."));
                    return 1;
                })));
            dispatcher.register(Commands.literal("lockinfo")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0, false);
                    if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) {
                        player.sendSystemMessage(Component.literal("§cRegardez un bloc."));
                        return 0;
                    }
                    net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
                    if (!isLocked(pos)) {
                        player.sendSystemMessage(Component.literal("§7Ce bloc n'est pas verrouillé."));
                        return 0;
                    }
                    java.util.UUID ownerUUID = getOwner(pos);
                    String ownerName = resolvePlayerName(context.getSource().getServer(), ownerUUID);
                    java.util.Set<java.util.UUID> trusted = getTrusted(ownerUUID);
                    player.sendSystemMessage(Component.literal("§6§l[LockInfo]"));
                    player.sendSystemMessage(Component.literal("§7Propriétaire : §e" + ownerName));
                    if (trusted.isEmpty()) {
                        player.sendSystemMessage(Component.literal("§7Accès partagé : §cnul"));
                    } else {
                        java.util.List<String> names = trusted.stream()
                            .map(uuid -> resolvePlayerName(context.getSource().getServer(), uuid))
                            .collect(java.util.stream.Collectors.toList());
                        player.sendSystemMessage(Component.literal("§7Accès partagé : §a" + String.join("§7, §a", names)));
                    }
                    return 1;
                }));
            dispatcher.register(Commands.literal("sethome")
                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name");
                    var homes = getPlayerHomes(player.getUUID());
                    if (homes.size() >= 3 && !homes.containsKey(name)) {
                        player.sendSystemMessage(Component.literal("§cLimite de 3 homes atteinte."));
                        return 0;
                    }
                    homes.put(name, player.blockPosition());
                    getPlayerHomesDim(player.getUUID()).put(name, player.level().dimension().location().toString());
                    player.sendSystemMessage(Component.literal("§aHome '" + name + "' enregistré !"));
                    return 1;
                })));

            dispatcher.register(Commands.literal("back")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (Fabric.test.command.ZoneCommand.isInBuildMode(player.getUUID())) {
                        player.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/back §cen mode construction."));
                        return 0;
                    }
                    if (!checkCooldown(lastBackUse, player.getUUID(), cooldownBack, player, "/back")) return 0;
                    if (!lastPositions.containsKey(player.getUUID())) {
                        player.sendSystemMessage(Component.literal("§cAucune position de retour trouvée."));
                        return 0;
                    }
                    net.minecraft.world.phys.Vec3 pos = lastPositions.get(player.getUUID());
                    savePosition(player);
                    player.teleportTo((ServerLevel)player.level(), pos.x, pos.y, pos.z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                    player.sendSystemMessage(Component.literal("§aRetour à la position précédente."));
                    return 1;
                }));
            dispatcher.register(Commands.literal("afk").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                boolean afk = !isAfk(player.getUUID());
                afkPlayers.put(player.getUUID(), afk);
                
                if (afk) {
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§eVOUS ÊTES AFK")));
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 40, 10));
                } else {
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§aVOUS N'ÊTES PLUS AFK")));
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 20, 10));
                }
                
                context.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + (afk ? " est AFK." : " n'est plus AFK.")), false);
                return 1;
            }));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity instanceof ServerPlayer target && isAfk(target.getUUID())) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
            dispatcher.register(Commands.literal("home")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    var homes = getPlayerHomes(player.getUUID());
                    if (homes.isEmpty()) {
                        player.sendSystemMessage(Component.literal("§cVous n'avez aucun home enregistré."));
                    } else {
                        String list = String.join(", ", homes.keySet());
                        player.sendSystemMessage(Component.literal("§aVos homes : §f" + list));
                    }
                    return 1;
                })
                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                .suggests((context, builder) -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    return net.minecraft.commands.SharedSuggestionProvider.suggest(getPlayerHomes(player.getUUID()).keySet(), builder);
                })
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name");
                    if (Fabric.test.command.ZoneCommand.isInBuildMode(player.getUUID())) {
                        player.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/home §cen mode construction."));
                        return 0;
                    }
                    if (!checkCooldown(lastHomeUse, player.getUUID(), cooldownHome, player, "/home")) return 0;
                    var homes = getPlayerHomes(player.getUUID());
                    if (!homes.containsKey(name)) {
                        player.sendSystemMessage(Component.literal("§cHome '" + name + "' introuvable."));
                        return 0;
                    }
                    savePosition(player);
                    net.minecraft.core.BlockPos pos = homes.get(name);
                    player.teleportTo((ServerLevel)player.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                    player.sendSystemMessage(Component.literal("§aTéléporté au home '" + name + "'."));
                    return 1;
                })));
            dispatcher.register(Commands.literal("chest")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (Fabric.test.command.ZoneCommand.isInBuildMode(player.getUUID())) {
                        player.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/chest §cen mode construction."));
                        return 0;
                    }
                    NonNullList<ItemStack> items = VirtualChestManager.getChest(player.getUUID());
                    SimpleContainer container = new SimpleContainer(9);
                    for (int i = 0; i < 9; i++) container.setItem(i, items.get(i));
                    container.addListener(c -> { for (int i = 0; i < 9; i++) items.set(i, c.getItem(i)); });

                    player.openMenu(new SimpleMenuProvider((id, inv, p) -> new VirtualChestMenu(id, inv, container), Component.literal("Coffre Virtuel")));
                    return 1;
                }));
            dispatcher.register(Commands.literal("report")
                .then(Commands.argument("message", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String message = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "message");
                    String name = player.getName().getString();
                    pendingReports.put(name, message);
                    player.sendSystemMessage(Component.literal("§aVotre rapport a été envoyé aux administrateurs."));
                    Component notif = Component.literal("§c§l[REPORT] §r§e" + name + " §7» §f" + message + "  ")
                        .append(Component.literal("[ACCEPTER]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/reportaccept " + name))))
                        .append(Component.literal("  "))
                        .append(Component.literal("[REFUSER]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/reportdeny " + name))));
                    for (ServerPlayer op : context.getSource().getServer().getPlayerList().getPlayers()) {
                        if (op.hasPermissions(2)) {
                            op.sendSystemMessage(notif);
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(op, new Fabric.test.networking.NotifPayload("REPORT", "§c⚑ Report de §f" + name));
                        }
                    }
                    return 1;
                })));
            dispatcher.register(Commands.literal("reportaccept")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(context -> {
                    ServerPlayer admin = context.getSource().getPlayerOrException();
                    String playerName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
                    if (!pendingReports.containsKey(playerName)) { admin.sendSystemMessage(Component.literal("§cAucun rapport de §e" + playerName + "§c.")); return 0; }
                    pendingReports.remove(playerName);
                    ServerPlayer reporter = context.getSource().getServer().getPlayerList().getPlayerByName(playerName);
                    if (reporter != null) reporter.sendSystemMessage(Component.literal("§aVotre rapport a été pris en compte. Un administrateur arrive au plus vite !"));
                    admin.sendSystemMessage(Component.literal("§aRapport de §e" + playerName + " §aaccepté."));
                    return 1;
                })));
            dispatcher.register(Commands.literal("reportdeny")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(context -> {
                    ServerPlayer admin = context.getSource().getPlayerOrException();
                    String playerName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
                    if (pendingReports.remove(playerName) == null) { admin.sendSystemMessage(Component.literal("§cAucun rapport de §e" + playerName + "§c.")); return 0; }
                    admin.sendSystemMessage(Component.literal("§cRapport de §e" + playerName + " §crefusé."));
                    return 1;
                })));
            dispatcher.register(Commands.literal("menu")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PlayerSettings s = getPlayerSettings(player.getUUID());
                    String homesSerialized = getPlayerHomes(player.getUUID()).entrySet().stream()
                        .map(e -> {
                            String dimId = getPlayerHomesDim(player.getUUID()).getOrDefault(e.getKey(), "minecraft:overworld");
                            String dimShort = dimId.contains("nether") ? "NE" : dimId.contains("end") ? "END" : "OW";
                            return e.getKey() + ":" + e.getValue().getX() + "," + e.getValue().getY() + "," + e.getValue().getZ() + ":" + dimShort;
                        })
                        .collect(java.util.stream.Collectors.joining("|"));
                    String locksSerialized = lockedBlocks.entrySet().stream()
                        .filter(e -> e.getValue().equals(player.getUUID()))
                        .map(e -> e.getKey().getX() + "," + e.getKey().getY() + "," + e.getKey().getZ())
                        .collect(java.util.stream.Collectors.joining("|"));
                    String trustSerialized = trustedPlayers.getOrDefault(player.getUUID(), java.util.Collections.emptySet()).stream()
                        .map(uuid -> playerNameCache.getOrDefault(uuid, uuid.toString().substring(0, 8) + "…") + ":" + uuid.toString())
                        .collect(java.util.stream.Collectors.joining("|"));
                    int playTicks = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
                    long totalSec = playTicks / 20L;
                    long ph = totalSec / 3600, pm = (totalSec % 3600) / 60;
                    int deaths = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
                    int pKills = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS));
                    int mKills = hostileMobKills.getOrDefault(player.getUUID(), 0);
                    long blocks = (player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.WALK_ONE_CM))
                                 + player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.SPRINT_ONE_CM))) / 100L;
                    int xp = player.totalExperience;
                    String statsSerialized = ph + "|" + pm + "|" + deaths + "|" + pKills + "|" + mKills + "|" + blocks + "|" + xp;
                    ServerPlayNetworking.send(player, new Fabric.test.networking.OpenSettingsPayload(
                        s.allowPrivateMessages, s.allowTpaRequests, s.allowTrades,
                        s.showChatNotifications, s.showConnectionAlerts,
                        getCommandsSerialized(), homesSerialized, locksSerialized, trustSerialized,
                        statsSerialized
                    ));
                    ServerPlayNetworking.send(player, GroupManager.buildGroupPayload(player, context.getSource().getServer()));
                    return 1;
                }));

            dispatcher.register(Commands.literal("stats")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();

                    int playTicks   = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
                    long totalSec   = playTicks / 20L;
                    long hours      = totalSec / 3600;
                    long minutes    = (totalSec % 3600) / 60;

                    int deaths      = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
                    int playerKills = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS));
                    int hostile     = hostileMobKills.getOrDefault(player.getUUID(), 0);

                    long walkCm     = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.WALK_ONE_CM));
                    long sprintCm   = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.SPRINT_ONE_CM));
                    long blocks     = (walkCm + sprintCm) / 100L;

                    int xp          = player.totalExperience;

                    player.sendSystemMessage(Component.literal("§6§l━━━━ Vos statistiques ━━━━"));
                    player.sendSystemMessage(Component.literal("§7Temps de jeu    §f" + hours + "h " + minutes + "m"));
                    player.sendSystemMessage(Component.literal("§7Morts           §c" + deaths));
                    player.sendSystemMessage(Component.literal("§7Kills joueurs   §e" + playerKills));
                    player.sendSystemMessage(Component.literal("§7XP totale       §a" + xp));
                    player.sendSystemMessage(Component.literal("§7Mobs hostiles   §d" + hostile));
                    player.sendSystemMessage(Component.literal("§7Blocs parcourus §b" + blocks));
                    player.sendSystemMessage(Component.literal("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    return 1;
                }));

            dispatcher.register(Commands.literal("seen")
                .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(context -> {
                    ServerPlayer seeker = context.getSource().getPlayerOrException();
                    String targetName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
                    ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);
                    if (online != null) {
                        seeker.sendSystemMessage(Component.literal("§e" + targetName + " §7est actuellement §aconnecté§7."));
                        return 1;
                    }
                    java.util.Optional<java.util.Map.Entry<java.util.UUID, String>> entry = playerNameCache.entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(targetName))
                        .findFirst();
                    if (entry.isEmpty()) {
                        seeker.sendSystemMessage(Component.literal("§cJoueur §e" + targetName + " §cinconnu."));
                        return 0;
                    }
                    java.util.UUID uuid = entry.get().getKey();
                    Long ts = lastSeenTimestamps.get(uuid);
                    if (ts == null) {
                        seeker.sendSystemMessage(Component.literal("§7Aucune donnée de connexion pour §e" + targetName + "§7."));
                    } else {
                        seeker.sendSystemMessage(Component.literal("§e" + targetName + " §7a été vu il y a §f" + formatTimeAgo(ts) + "§7."));
                    }
                    return 1;
                })));

            dispatcher.register(Commands.literal("check")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(context -> {
                    ServerPlayer adm = context.getSource().getPlayerOrException();
                    String targetName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
                    net.minecraft.server.MinecraftServer srv = context.getSource().getServer();

                    adm.sendSystemMessage(Component.literal("§6§l━━━ Check : " + targetName + " ━━━"));

                    ServerPlayer onlineTarget = srv.getPlayerList().getPlayerByName(targetName);
                    adm.sendSystemMessage(Component.literal("§7Connecté : " + (onlineTarget != null ? "§aOUI" : "§cNON")));

                    boolean banned = srv.getPlayerList().getBans().getEntries().stream()
                        .anyMatch(e -> e.getUser().name().equalsIgnoreCase(targetName));
                    adm.sendSystemMessage(Component.literal("§7Banni : " + (banned ? "§cOUI" : "§aNON")));
                    if (banned) {
                        srv.getPlayerList().getBans().getEntries().stream()
                            .filter(e -> e.getUser().name().equalsIgnoreCase(targetName))
                            .findFirst()
                            .ifPresent(e -> adm.sendSystemMessage(
                                Component.literal("§7Raison : §f" + (e.getReason() != null ? e.getReason() : "—"))));
                    }

                    java.util.Optional<java.util.Map.Entry<java.util.UUID, String>> cacheEntry =
                        playerNameCache.entrySet().stream()
                            .filter(e -> e.getValue().equalsIgnoreCase(targetName))
                            .findFirst();
                    if (cacheEntry.isPresent()) {
                        java.util.UUID uid = cacheEntry.get().getKey();
                        Long ts = lastSeenTimestamps.get(uid);
                        if (onlineTarget != null) {
                            adm.sendSystemMessage(Component.literal("§7Dernière vue : §aMaintenant"));
                        } else if (ts != null) {
                            adm.sendSystemMessage(Component.literal("§7Dernière vue : §fil y a " + formatTimeAgo(ts)));
                        } else {
                            adm.sendSystemMessage(Component.literal("§7Dernière vue : §8inconnue"));
                        }
                        int logCount = playerLogs.getOrDefault(uid, java.util.Collections.emptyList()).size();
                        adm.sendSystemMessage(Component.literal("§7Logs : §f" + logCount + " entrée" + (logCount > 1 ? "s" : "")));
                    } else {
                        adm.sendSystemMessage(Component.literal("§8Aucune donnée locale pour ce joueur."));
                    }
                    adm.sendSystemMessage(Component.literal("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    return 1;
                })));

            // /deal
            dispatcher.register(Commands.literal("deal")
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(context -> {
                    ServerPlayer sender = context.getSource().getPlayerOrException();
                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                    if (sender == target) {
                        sender.sendSystemMessage(Component.literal("§cVous ne pouvez pas échanger avec vous-même."));
                        return 0;
                    }
                    if (activeSessions.containsKey(sender.getUUID())) {
                        sender.sendSystemMessage(Component.literal("§cVous avez déjà un échange en cours."));
                        return 0;
                    }
                    if (activeSessions.containsKey(target.getUUID())) {
                        sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " a déjà un échange en cours."));
                        return 0;
                    }
                    if (!getPlayerSettings(target.getUUID()).allowTrades) {
                        sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas les échanges."));
                        return 0;
                    }
                    pendingDeals.put(target.getUUID(), sender.getUUID());
                    String sName = sender.getName().getString();
                    Component msg = Component.literal("§e" + sName + " §7souhaite effectuer un échange. ")
                        .append(Component.literal("[Y]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/dealaccept"))))
                        .append(Component.literal(" "))
                        .append(Component.literal("[N]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/dealdeny"))));
                    target.sendSystemMessage(msg);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target, new Fabric.test.networking.NotifPayload("DEAL", "§e⇄ Échange proposé par §f" + sName));
                    sender.sendSystemMessage(Component.literal("§7Demande d'échange envoyée à §e" + target.getName().getString() + "§7."));
                    return 1;
                })));

            // /dealaccept
            dispatcher.register(Commands.literal("dealaccept").executes(context -> {
                ServerPlayer target = context.getSource().getPlayerOrException();
                java.util.UUID requesterUUID = pendingDeals.remove(target.getUUID());
                if (requesterUUID == null) {
                    target.sendSystemMessage(Component.literal("§cAucune demande d'échange en attente."));
                    return 0;
                }
                ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayer(requesterUUID);
                if (requester == null) {
                    target.sendSystemMessage(Component.literal("§cCe joueur n'est plus connecté."));
                    return 0;
                }
                DealSession session = new DealSession(requesterUUID, target.getUUID());
                activeSessions.put(requesterUUID, session);
                activeSessions.put(target.getUUID(), session);
                playerNameCache.put(requester.getUUID(), requester.getName().getString());
                playerNameCache.put(target.getUUID(), target.getName().getString());
                broadcastDealUpdate(session, context.getSource().getServer());
                return 1;
            }));

            // /dealdeny
            dispatcher.register(Commands.literal("dealdeny").executes(context -> {
                ServerPlayer target = context.getSource().getPlayerOrException();
                java.util.UUID requesterUUID = pendingDeals.remove(target.getUUID());
                if (requesterUUID != null) {
                    ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayer(requesterUUID);
                    if (requester != null)
                        requester.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " a refusé votre demande d'échange."));
                    target.sendSystemMessage(Component.literal("§cDemande refusée."));
                }
                return 1;
            }));
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (isFrozen(player.getUUID())) {
                player.teleportTo(origin, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYRot(), player.getXRot(), true);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Fabric.test.command.ZoneCommand.onTick(server);
            GroupManager.onTick(server);

            long nowMs = System.currentTimeMillis();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isFrozen(player.getUUID())) {
                    net.minecraft.world.phys.Vec3 frozenPos = frozenPositions.get(player.getUUID());
                    if (frozenPos != null && player.position().distanceToSqr(frozenPos) > 0.001) {
                        player.connection.teleport(frozenPos.x, frozenPos.y, frozenPos.z, player.getYRot(), player.getXRot());
                    }
                }
                if (isAfk(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("§e[AFK] Vous êtes absent."), true);
                    if (lastPos.containsKey(player.getUUID()) && player.position().distanceToSqr(lastPos.get(player.getUUID())) > 0.01) {
                        afkPlayers.put(player.getUUID(), false);
                        lastActivityTime.put(player.getUUID(), nowMs);
                        player.sendSystemMessage(Component.literal("§eVous n'êtes plus AFK."));
                        server.getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + " n'est plus AFK."), false);
                    }
                } else if (afkAutoEnabled && !isFrozen(player.getUUID())) {
                    net.minecraft.world.phys.Vec3 prevPos = lastPos.get(player.getUUID());
                    if (prevPos != null && player.position().distanceToSqr(prevPos) > 0.001) {
                        lastActivityTime.put(player.getUUID(), nowMs);
                    } else {
                        long lastAct = lastActivityTime.getOrDefault(player.getUUID(), nowMs);
                        if (nowMs - lastAct > afkDelayMs) {
                            afkPlayers.put(player.getUUID(), true);
                            lastActivityTime.put(player.getUUID(), nowMs);
                            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§eVOUS ÊTES AFK")));
                            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 40, 10));
                            server.getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + " est AFK (inactivité)."), false);
                        }
                    }
                }
                lastPos.put(player.getUUID(), player.position());
            }

            // Sommeil proportionnel : 30 % de joueurs couchés → accélération de la nuit
            if (proportionalSleepEnabled) {
                for (net.minecraft.server.level.ServerLevel lvl : server.getAllLevels()) {
                    if (!lvl.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) continue;
                    java.util.List<ServerPlayer> inLvl = lvl.players();
                    if (inLvl.isEmpty()) continue;
                    long sleeping = inLvl.stream().filter(net.minecraft.world.entity.player.Player::isSleeping).count();
                    if ((double) sleeping / inLvl.size() >= 0.30) {
                        long dayTime = lvl.getDayTime() % 24000;
                        if (dayTime >= 12542 || dayTime < 500) {
                            lvl.setDayTime(lvl.getDayTime() + 40);
                        }
                    }
                }
            }

            // Fast Leaf Decay : traitement par lots
            if (fastLeafDecayEnabled && !pendingLeafDecay.isEmpty()) {
                leafDecayCounter++;
                if (leafDecayCounter % 4 == 0) {
                    java.util.Iterator<net.minecraft.core.BlockPos> it = pendingLeafDecay.iterator();
                    int processed = 0;
                    while (it.hasNext() && processed < 20) {
                        net.minecraft.core.BlockPos leafPos = it.next();
                        it.remove();
                        for (net.minecraft.server.level.ServerLevel lvl : server.getAllLevels()) {
                            net.minecraft.world.level.block.state.BlockState ls = lvl.getBlockState(leafPos);
                            if (ls.is(net.minecraft.tags.BlockTags.LEAVES)
                                    && ls.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT)
                                    && !ls.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT)) {
                                lvl.destroyBlock(leafPos, true);
                                break;
                            }
                        }
                        processed++;
                    }
                }
            }

            if (clearLagTicks > 0) {
                clearLagTicks--;
                if (clearLagTicks == 0) {
                    long count = 0;
                    for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                            if (e instanceof net.minecraft.world.entity.item.ItemEntity) {
                                e.discard();
                                count++;
                            }
                        }
                    }
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§eClearLag: §f" + count + " items supprimés."), false);
                }
            }
            if (removeMobsTicks > 0) {
                removeMobsTicks--;
                if (removeMobsTicks == 0) {
                    long count = 0;
                    for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                            if (e instanceof net.minecraft.world.entity.monster.Monster && !e.hasCustomName()) {
                                e.discard();
                                count++;
                            }
                        }
                    }
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§cMobs supprimés: §f" + count), false);
                }
            }

            for (int i = 0; i < scheduledCounters.size(); i++) {
                int c = scheduledCounters.get(i) - 1;
                scheduledCounters.set(i, c);
                if (c <= 0) {
                    scheduledCounters.set(i, scheduledIntervals.get(i));
                    server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§6§l[Annonce] §r" + scheduledMsgs.get(i)), false);
                }
            }
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (isMuted(sender.getUUID())) {
                addLog(sender.getUUID(), "Chat bloqué (muet): " + message.signedBody().content());
                sender.sendSystemMessage(Component.literal("§cVous êtes muet, vous ne pouvez pas écrire."));
                return false;
            }
            String raw = message.signedBody().content();
            if (raw.startsWith("@g ") && GroupManager.isInGroup(sender.getUUID())) {
                String text = raw.substring(3).trim();
                if (!text.isEmpty()) {
                    java.util.UUID leader = GroupManager.getLeader(sender.getUUID());
                    String colored = "§a[Groupe] §f" + sender.getName().getString() + "§7: §f" + text;
                    GroupManager.getMembers(leader).forEach(uuid -> {
                        ServerPlayer m = ((ServerLevel)sender.level()).getServer().getPlayerList().getPlayer(uuid);
                        if (m != null) m.sendSystemMessage(Component.literal(colored));
                    });
                    addLog(sender.getUUID(), "[Groupe] " + text);
                }
                return false;
            }
            if (chatLocked && !sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("§cLe chat est actuellement bloqué par un admin."));
                return false;
            }
            addLog(sender.getUUID(), "Chat: " + raw);
            lastActivityTime.put(sender.getUUID(), System.currentTimeMillis());
            return true;
        });

        // Empêcher le piétinement des cultures et gérer le verrouillage
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            net.minecraft.core.BlockPos pos = hitResult.getBlockPos();
            if (isLocked(pos) && !getOwner(pos).equals(player.getUUID())
                    && !isTrusted(getOwner(pos), player.getUUID())
                    && !GroupManager.isTrustedByGroup(getOwner(pos), player.getUUID())) {
                if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal("§cCe bloc est verrouillé."));
                return InteractionResult.FAIL;
            }
            if (player instanceof ServerPlayer && isLocked(pos)) {
                java.util.UUID ownerUUID = getOwner(pos);
                if (ownerUUID.equals(player.getUUID()) || isTrusted(ownerUUID, player.getUUID())) {
                    if (world.getBlockEntity(pos) instanceof net.minecraft.world.Container) {
                        addLog(player.getUUID(), "Coffre ouvert à (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
                    }
                }
            }
            // Right-click harvest
            if (rightClickHarvestEnabled && !player.isShiftKeyDown()
                    && hand == net.minecraft.world.InteractionHand.MAIN_HAND && !world.isClientSide()) {
                net.minecraft.world.level.block.state.BlockState bState = world.getBlockState(pos);
                net.minecraft.world.level.block.Block bBlock = bState.getBlock();
                net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
                if (!held.is(net.minecraft.world.item.Items.BONE_MEAL)) {
                    net.minecraft.server.level.ServerLevel sLevel = (net.minecraft.server.level.ServerLevel) world;
                    if (bBlock instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(bState)) {
                        java.util.List<net.minecraft.world.item.ItemStack> drops =
                            net.minecraft.world.level.block.Block.getDrops(bState, sLevel, pos, null, player, held);
                        world.setBlock(pos, crop.getStateForAge(0), 3);
                        drops.forEach(d -> net.minecraft.world.level.block.Block.popResource(world, pos, d));
                        return InteractionResult.SUCCESS;
                    } else if (bBlock instanceof net.minecraft.world.level.block.NetherWartBlock
                            && bState.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) == 3) {
                        java.util.List<net.minecraft.world.item.ItemStack> drops =
                            net.minecraft.world.level.block.Block.getDrops(bState, sLevel, pos, null, player, held);
                        world.setBlock(pos, bState.setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 0), 3);
                        drops.forEach(d -> net.minecraft.world.level.block.Block.popResource(world, pos, d));
                        return InteractionResult.SUCCESS;
                    } else if (bBlock instanceof net.minecraft.world.level.block.CocoaBlock
                            && bState.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) == 2) {
                        java.util.List<net.minecraft.world.item.ItemStack> drops =
                            net.minecraft.world.level.block.Block.getDrops(bState, sLevel, pos, null, player, held);
                        world.setBlock(pos, bState.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 0), 3);
                        drops.forEach(d -> net.minecraft.world.level.block.Block.popResource(world, pos, d));
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            if (doubleDoorEnabled && !world.isClientSide()) {
                net.minecraft.world.level.block.state.BlockState dState = world.getBlockState(pos);
                if (dState.getBlock() instanceof net.minecraft.world.level.block.DoorBlock dBlock
                        && dState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                        && dState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                    net.minecraft.core.Direction facing = dState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                    boolean isOpen = dState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
                    for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
                        net.minecraft.core.BlockPos partnerPos = pos.relative(side);
                        net.minecraft.world.level.block.state.BlockState pState = world.getBlockState(partnerPos);
                        if (pState.getBlock() == dState.getBlock()
                                && pState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                                && pState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                                && pState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN) == isOpen) {
                            dBlock.setOpen(player, world, pState, partnerPos, !isOpen);
                            break;
                        }
                    }
                }
            }
            return InteractionResult.PASS;
        });

        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) return;
            if (!state.is(net.minecraft.tags.BlockTags.LOGS)) return;
            if (!(player instanceof ServerPlayer sp)) return;

            boolean didCapitate = false;

            if (treeCapitatorEnabled) {
                ItemStack tool = sp.getMainHandItem();
                if (tool.getItem() instanceof net.minecraft.world.item.AxeItem) {
                    boolean hasLeaves = false;
                    net.minecraft.core.BlockPos.MutableBlockPos mut = new net.minecraft.core.BlockPos.MutableBlockPos();
                    outer:
                    for (int dx = -3; dx <= 3; dx++) for (int dy = 0; dy <= 8; dy++) for (int dz = -3; dz <= 3; dz++) {
                        mut.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                        if (world.getBlockState(mut).is(net.minecraft.tags.BlockTags.LEAVES)) { hasLeaves = true; break outer; }
                    }
                    if (hasLeaves) {
                        didCapitate = true;
                        java.util.Set<net.minecraft.core.BlockPos> visited = new java.util.HashSet<>();
                        java.util.Queue<net.minecraft.core.BlockPos> queue = new java.util.LinkedList<>();
                        visited.add(pos);
                        addLogNeighbors(world, pos, visited, queue);
                        int maxBlocks = Math.min(150, tool.getMaxDamage() - tool.getDamageValue() - 1);
                        int broken = 0;
                        while (!queue.isEmpty() && broken < maxBlocks && !tool.isEmpty()) {
                            net.minecraft.core.BlockPos cur = queue.poll();
                            world.destroyBlock(cur, true, sp);
                            tool.hurtAndBreak(1, sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                            broken++;
                            if (fastLeafDecayEnabled) scheduleNearbyLeaves(world, cur);
                            addLogNeighbors(world, cur, visited, queue);
                        }
                        if (fastLeafDecayEnabled) scheduleNearbyLeaves(world, pos);
                    }
                }
            }

            if (fastLeafDecayEnabled && !didCapitate) {
                scheduleNearbyLeaves(world, pos);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(AdminActionPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer admin = context.player();
                if (!admin.hasPermissions(2)) return;

                String action = payload.action();
                ServerPlayer target = context.server().getPlayerList().getPlayerByName(payload.target());

                switch (action) {
                    case "SET_DAY" -> ((ServerLevel)admin.level()).setDayTime(6000);
                    case "SET_MORNING" -> ((ServerLevel)admin.level()).setDayTime(0);
                    case "SET_EVENING" -> ((ServerLevel)admin.level()).setDayTime(12000);
                    case "SET_NIGHT" -> ((ServerLevel)admin.level()).setDayTime(18000);
                    case "SET_WEATHER_CLEAR" -> ((ServerLevel)admin.level()).setWeatherParameters(6000, 0, false, false);
                    case "SET_WEATHER_RAIN" -> ((ServerLevel)admin.level()).setWeatherParameters(0, 6000, true, false);
                    case "SET_WEATHER_THUNDER" -> ((ServerLevel)admin.level()).setWeatherParameters(0, 6000, true, true);
                    case "TOGGLE_WEATHER_CYCLE" -> {
                        weatherCycleEnabled = !weatherCycleEnabled;
                        context.server().getCommands().performPrefixedCommand(context.server().createCommandSourceStack(), "gamerule doWeatherCycle " + weatherCycleEnabled);
                        ServerConfig.save();
                    }
                    case "TOGGLE_CROP_TRAMPLE" -> { cropTrampleEnabled = !cropTrampleEnabled; ServerConfig.save(); }
                    case "CLEAR_LAG" -> {
                        context.server().getPlayerList().broadcastSystemMessage(Component.literal("§eClear lag dans 30 secondes !"), false);
                        clearLagTicks = 30 * 20;
                    }
                    case "SET_SPAWN" -> {
                        admin.level().getServer().getCommands().performPrefixedCommand(admin.createCommandSourceStack(), "/setworldspawn ~ ~ ~");
                        admin.sendSystemMessage(Component.literal("§aSpawn défini à votre position."));
                    }

                    case "REMOVE_MOBS" -> {
                        context.server().getPlayerList().broadcastSystemMessage(Component.literal("§cSuppression des mobs dans 30 secondes !"), false);
                        removeMobsTicks = 30 * 20;
                    }
                    case "MUTE" -> {
                        if (target != null) {
                            if (mutedPlayers.contains(target.getUUID())) {
                                mutedPlayers.remove(target.getUUID());
                                addLog(target.getUUID(), "Unmuted par " + admin.getName().getString());
                                admin.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " n'est plus muet."));
                                target.sendSystemMessage(Component.literal("§eVous n'êtes plus muet."));
                            } else {
                                mutedPlayers.add(target.getUUID());
                                addLog(target.getUUID(), "Muted par " + admin.getName().getString());
                                addSanction("MUTE", target.getName().getString(), admin.getName().getString(), "");
                                admin.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " est maintenant muet."));
                                target.sendSystemMessage(Component.literal("§cVous avez été rendu muet par un admin."));
                            }
                        }
                    }
                    case "HEAL" -> { if (target != null) { target.setHealth(target.getMaxHealth()); target.getFoodData().eat(20, 1.0f); addLog(target.getUUID(), "Heal par " + admin.getName().getString()); admin.sendSystemMessage(Component.literal("§aJoueur soigné.")); } }
                    case "LOCK_CHAT" -> {
                        chatLocked = !chatLocked;
                        context.server().getPlayerList().broadcastSystemMessage(Component.literal("§cLe chat a été " + (chatLocked ? "bloqué" : "débloqué") + " !"), false);
                        ServerConfig.save();
                    }
                    case "VANISH" -> {
                        if (vanishedPlayers.contains(admin.getUUID())) {
                            vanishedPlayers.remove(admin.getUUID());
                            admin.sendSystemMessage(Component.literal("§eVanish: OFF"));
                            admin.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                        } else {
                            vanishedPlayers.add(admin.getUUID());
                            admin.sendSystemMessage(Component.literal("§eVanish: ON"));
                            admin.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        }
                    }
                    case "ANNOUNCE" -> {
                        String tgt = payload.target();
                        Component announcement = Component.literal("§6§l[ANNONCE] §r" + payload.value());
                        if (tgt.isEmpty()) {
                            context.server().getPlayerList().broadcastSystemMessage(announcement, false);
                        } else if (tgt.startsWith("GROUP:")) {
                            String leaderName = tgt.substring(6);
                            ServerPlayer groupLeader = context.server().getPlayerList().getPlayerByName(leaderName);
                            if (groupLeader != null) {
                                for (java.util.UUID uid : GroupManager.getMembers(groupLeader.getUUID())) {
                                    ServerPlayer gp = context.server().getPlayerList().getPlayer(uid);
                                    if (gp != null) gp.sendSystemMessage(announcement);
                                }
                            }
                        } else {
                            ServerPlayer tgtPlayer = context.server().getPlayerList().getPlayerByName(tgt);
                            if (tgtPlayer != null) tgtPlayer.sendSystemMessage(announcement);
                        }
                    }
                    case "KICK" -> { if (target != null) { addLog(target.getUUID(), "Kicked par " + admin.getName().getString()); addSanction("KICK", target.getName().getString(), admin.getName().getString(), ""); target.connection.disconnect(Component.literal("Expulsé par un admin.")); } }
                    case "TELEPORT_TO" -> { if (target != null) { addLog(target.getUUID(), "TP vers par " + admin.getName().getString()); admin.teleportTo((ServerLevel)target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), admin.getYRot(), admin.getXRot(), true); } }
                    case "OPEN_INV" -> {
                        if (target != null) {
                            admin.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new EditablePlayerInventoryMenu(id, inv, target.getInventory()), Component.literal("Inv: " + target.getName().getString())));
                        }
                    }
                    case "ENDERCHEST" -> {
                        if (target != null) {
                            admin.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, target.getEnderChestInventory(), 3), Component.literal("Ender: " + target.getName().getString())));
                        }
                    }
                    case "BRING" -> { if (target != null) { addLog(target.getUUID(), "Bring par " + admin.getName().getString()); target.teleportTo((ServerLevel)admin.level(), admin.getX(), admin.getY(), admin.getZ(), Set.of(), target.getYRot(), target.getXRot(), true); } }
                    case "FREEZE" -> {
                        if (target != null) {
                            boolean frozen = !frozenPlayers.getOrDefault(target.getUUID(), false);
                            frozenPlayers.put(target.getUUID(), frozen);
                            if (frozen) {
                                frozenPositions.put(target.getUUID(), target.position());
                                addLog(target.getUUID(), "Frozen par " + admin.getName().getString());
                                target.sendSystemMessage(Component.literal("§bVous avez été gelé par un admin."));
                            } else {
                                frozenPositions.remove(target.getUUID());
                                addLog(target.getUUID(), "Unfrozen par " + admin.getName().getString());
                                target.sendSystemMessage(Component.literal("§aVous n'êtes plus gelé."));
                            }
                            admin.sendSystemMessage(Component.literal("§bJoueur " + (frozen ? "gelé" : "dégelé") + "."));
                        }
                    }
                    case "ACCEPT_REPORT" -> {
                        String rName = payload.target();
                        if (!pendingReports.containsKey(rName)) { admin.sendSystemMessage(Component.literal("§cAucun rapport en attente de §e" + rName + "§c.")); }
                        else {
                            acceptedReports.put(rName, pendingReports.remove(rName));
                            ServerPlayer reporter = context.server().getPlayerList().getPlayerByName(rName);
                            if (reporter != null) reporter.sendSystemMessage(Component.literal("§aVotre signalement a été pris en charge. Un administrateur arrive au plus vite !"));
                            admin.sendSystemMessage(Component.literal("§aRapport de §e" + rName + " §apris en charge."));
                        }
                    }
                    case "CLOSE_REPORT" -> {
                        String rName = payload.target();
                        String rMsg = acceptedReports.remove(rName);
                        if (rMsg != null) {
                            if (closedReports.size() >= 15) closedReports.remove(closedReports.keySet().iterator().next());
                            closedReports.put(rName, rMsg);
                            ServerPlayer reporter = context.server().getPlayerList().getPlayerByName(rName);
                            if (reporter != null) reporter.sendSystemMessage(Component.literal("§aVotre signalement a été résolu. Merci de nous avoir contacté !"));
                            admin.sendSystemMessage(Component.literal("§aSignalement de §e" + rName + " §aclôturé."));
                        } else {
                            admin.sendSystemMessage(Component.literal("§cAucun rapport en cours pour §e" + rName + "§c."));
                        }
                    }
                    case "REFUSE_REPORT" -> {
                        String rName = payload.target();
                        if (pendingReports.remove(rName) != null) admin.sendSystemMessage(Component.literal("§cRapport de §e" + rName + " §crefusé."));
                        else admin.sendSystemMessage(Component.literal("§cAucun rapport de §e" + rName + "§c."));
                    }
                    case "GET_LOGS" -> {
                        if (target != null) {
                            java.util.List<String> logs = playerLogs.getOrDefault(target.getUUID(), java.util.Collections.emptyList());
                            int from = Math.max(0, logs.size() - 100);
                            String serialized = logs.isEmpty() ? "" : String.join("\n", logs.subList(from, logs.size()));
                            ServerPlayNetworking.send(admin, new Fabric.test.networking.PlayerLogsPayload(target.getName().getString(), serialized));
                        }
                    }
                    case "BAN" -> {
                        if (target != null) {
                            String[] banParts = payload.value().split("\t", 2);
                            int banDays = 0;
                            String reason = "Banni par un admin.";
                            try { banDays = Integer.parseInt(banParts[0]); } catch (NumberFormatException ignored) {}
                            if (banParts.length > 1 && !banParts[1].isEmpty()) reason = banParts[1];
                            java.util.Date expires = banDays > 0
                                ? new java.util.Date(System.currentTimeMillis() + (long) banDays * 24 * 3600 * 1000L)
                                : null;
                            String sanctionReason = (banDays > 0 ? "(" + banDays + "j) " : "") + reason;
                            addLog(target.getUUID(), "Banned par " + admin.getName().getString() + " (" + sanctionReason + ")");
                            addSanction("BAN", target.getName().getString(), admin.getName().getString(), sanctionReason);
                            context.server().getPlayerList().getBans().add(new net.minecraft.server.players.UserBanListEntry(new net.minecraft.server.players.NameAndId(target.getGameProfile()), null, "admin", expires, reason));
                            target.connection.disconnect(Component.literal(reason));
                        }
                    }
                    case "UNBAN" -> {
                        String name = payload.target();
                        addSanction("UNBAN", name, admin.getName().getString(), "");
                        getPlayerNameCache().entrySet().stream()
                            .filter(e -> e.getValue().equalsIgnoreCase(name))
                            .map(java.util.Map.Entry::getKey).findFirst()
                            .ifPresent(uuid -> addLog(uuid, "Débanni par " + admin.getName().getString()));
                        context.server().getCommands().performPrefixedCommand(
                            context.server().createCommandSourceStack(), "pardon " + name);
                        admin.sendSystemMessage(Component.literal("§a" + name + " a été débanni."));
                    }
                    case "KEEP_INVENTORY" -> {
                        if (target != null) {
                            PlayerSettings ks = getPlayerSettings(target.getUUID());
                            ks.keepInventory = !ks.keepInventory;
                            admin.sendSystemMessage(Component.literal("§aKeepInventory " + (ks.keepInventory ? "§aactivé" : "§cdésactivé") + " §apour §e" + target.getName().getString()));
                            target.sendSystemMessage(Component.literal(ks.keepInventory ? "§aVotre inventaire sera conservé à la mort." : "§cVotre inventaire ne sera plus conservé à la mort."));
                        }
                    }
                    case "OPEN_ZONES" -> Fabric.test.command.ZoneCommand.sendZoneScreen(admin, context.server());
                    case "GET_SANCTIONS" -> ServerPlayNetworking.send(admin, new Fabric.test.networking.OpenSanctionsPayload(getSanctionsSerialized()));
                    case "GAMEMODE" -> {
                        if (target != null) {
                            net.minecraft.world.level.GameType next = switch (target.gameMode.getGameModeForPlayer()) {
                                case SURVIVAL -> net.minecraft.world.level.GameType.CREATIVE;
                                case CREATIVE -> net.minecraft.world.level.GameType.SPECTATOR;
                                default -> net.minecraft.world.level.GameType.SURVIVAL;
                            };
                            target.setGameMode(next);
                            admin.sendSystemMessage(Component.literal("Mode de jeu changé en: " + next.getName()));
                        }
                    }
                    case "SCHEDULE_ADD" -> {
                        String[] parts = payload.value().split("\t", 2);
                        if (parts.length == 2) {
                            try {
                                int minutes = Integer.parseInt(parts[1].trim());
                                addScheduledBroadcast(parts[0], minutes * 1200);
                                admin.sendSystemMessage(Component.literal("§aBroadcast programmé ajouté."));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    case "SCHEDULE_REMOVE" -> {
                        try {
                            int idx = Integer.parseInt(payload.value());
                            if (idx >= 0 && idx < scheduledMsgs.size()) {
                                scheduledMsgs.remove(idx);
                                scheduledIntervals.remove(idx);
                                scheduledCounters.remove(idx);
                                ServerConfig.save();
                                admin.sendSystemMessage(Component.literal("§cBroadcast supprimé."));
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    case "SET_COOLDOWNS" -> {
                        String[] parts = payload.value().split("\\|");
                        if (parts.length == 3) {
                            try {
                                setCooldownHome(Integer.parseInt(parts[0]));
                                setCooldownBack(Integer.parseInt(parts[1]));
                                setCooldownTpa(Integer.parseInt(parts[2]));
                                admin.sendSystemMessage(Component.literal("§aCooldowns mis à jour."));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    case "TOGGLE_AFK_AUTO" -> {
                        afkAutoEnabled = !afkAutoEnabled;
                        admin.sendSystemMessage(Component.literal("§eAFK Automatique " + (afkAutoEnabled ? "§aactivé" : "§cdésactivé") + "§e."));
                        ServerConfig.save();
                    }
                    case "TOGGLE_PROPORTIONAL_SLEEP" -> {
                        proportionalSleepEnabled = !proportionalSleepEnabled;
                        admin.sendSystemMessage(Component.literal("§eSommeil Proportionnel " + (proportionalSleepEnabled ? "§aactivé" : "§cdésactivé") + "§e."));
                        ServerConfig.save();
                    }
                    case "TOGGLE_TREE_CAPITATOR" -> {
                        treeCapitatorEnabled = !treeCapitatorEnabled;
                        admin.sendSystemMessage(Component.literal("§eBûcheron Intelligent " + (treeCapitatorEnabled ? "§aactivé" : "§cdésactivé") + "§e."));
                        ServerConfig.save();
                    }
                    case "TOGGLE_FAST_LEAF_DECAY" -> {
                        fastLeafDecayEnabled = !fastLeafDecayEnabled;
                        admin.sendSystemMessage(Component.literal("§eFast Leaf Decay " + (fastLeafDecayEnabled ? "§aactivé" : "§cdésactivé") + "§e."));
                        ServerConfig.save();
                    }
                    case "TOGGLE_DOUBLE_DOOR" -> {
                        doubleDoorEnabled = !doubleDoorEnabled;
                        admin.sendSystemMessage(Component.literal("§eDouble Door " + (doubleDoorEnabled ? "§aactivé" : "§cdésactivé") + "§e."));
                        ServerConfig.save();
                    }
                    case "TOGGLE_RIGHT_CLICK_HARVEST" -> {
                        rightClickHarvestEnabled = !rightClickHarvestEnabled;
                        admin.sendSystemMessage(Component.literal("§eRécolte clic droit " + (rightClickHarvestEnabled ? "§aactivée" : "§cdésactivée") + "§e."));
                        ServerConfig.save();
                    }
                    case "TOGGLE_DISPENSER_HARVEST" -> {
                        dispenserHarvestEnabled = !dispenserHarvestEnabled;
                        admin.sendSystemMessage(Component.literal("§eDistributeur récolte " + (dispenserHarvestEnabled ? "§aactivé" : "§cdésactivé") + "§e."));
                        ServerConfig.save();
                    }
                    case "SET_AFK_DELAY" -> {
                        try {
                            int mins = Integer.parseInt(payload.value());
                            setAfkDelayMinutes(mins);
                            ServerConfig.save();
                            admin.sendSystemMessage(Component.literal("§eDelai AFK réglé à §f" + mins + "§e min."));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }));

        ServerPlayNetworking.registerGlobalReceiver(Fabric.test.networking.UpdateSettingsPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                PlayerSettings s = getPlayerSettings(context.player().getUUID());
                s.allowPrivateMessages  = payload.allowPrivateMessages();
                s.allowTpaRequests      = payload.allowTpaRequests();
                s.allowTrades           = payload.allowTrades();
                s.showChatNotifications = payload.showChatNotifications();
                s.showConnectionAlerts  = payload.showConnectionAlerts();
            }));

        ServerPlayNetworking.registerGlobalReceiver(Fabric.test.networking.PlayerActionPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                switch (payload.action()) {
                    case "HOME_TP" -> {
                        if (!checkCooldown(lastHomeUse, player.getUUID(), cooldownHome, player, "/home")) break;
                        net.minecraft.core.BlockPos pos = getPlayerHomes(player.getUUID()).get(payload.value());
                        if (pos != null) {
                            lastPositions.put(player.getUUID(), player.position());
                            String dimId = getPlayerHomesDim(player.getUUID()).getOrDefault(payload.value(), "minecraft:overworld");
                            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey =
                                net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.world.level.Level.OVERWORLD.registryKey(),
                                    net.minecraft.resources.ResourceLocation.parse(dimId));
                            ServerLevel targetLevel = context.server().getLevel(dimKey);
                            if (targetLevel == null) targetLevel = (ServerLevel) player.level();
                            player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                            player.sendSystemMessage(Component.literal("§aTéléporté au home '" + payload.value() + "'."));
                        }
                    }
                    case "HOME_DELETE" -> {
                        if (getPlayerHomes(player.getUUID()).remove(payload.value()) != null) {
                            getPlayerHomesDim(player.getUUID()).remove(payload.value());
                            player.sendSystemMessage(Component.literal("§cHome '" + payload.value() + "' supprimé."));
                        }
                    }
                    case "LOCK_DELETE" -> {
                        String[] parts = payload.value().split(",");
                        if (parts.length == 3) {
                            try {
                                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                                if (isLocked(pos) && getOwner(pos).equals(player.getUUID())) {
                                    lockedBlocks.remove(pos);
                                    player.sendSystemMessage(Component.literal("§aBloc déverrouillé."));
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    case "UNTRUST" -> {
                        try {
                            java.util.UUID targetUUID = java.util.UUID.fromString(payload.value());
                            getTrusted(player.getUUID()).remove(targetUUID);
                            String name = playerNameCache.getOrDefault(targetUUID, "joueur");
                            player.sendSystemMessage(Component.literal("§c" + name + " n'a plus accès à vos blocs verrouillés."));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    case "REFRESH_STATS" -> {
                        int pt = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
                        long ts = pt / 20L;
                        long rh = ts / 3600, rm = (ts % 3600) / 60;
                        int rd = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
                        int rk = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS));
                        int rm2 = hostileMobKills.getOrDefault(player.getUUID(), 0);
                        long rb = (player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.WALK_ONE_CM))
                                 + player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.SPRINT_ONE_CM))) / 100L;
                        int rx = player.totalExperience;
                        String stats = rh + "|" + rm + "|" + rd + "|" + rk + "|" + rm2 + "|" + rb + "|" + rx;
                        PlayerSettings rs = getPlayerSettings(player.getUUID());
                        ServerPlayNetworking.send(player, new Fabric.test.networking.OpenSettingsPayload(
                            rs.allowPrivateMessages, rs.allowTpaRequests, rs.allowTrades,
                            rs.showChatNotifications, rs.showConnectionAlerts,
                            "", "", "", "", stats
                        ));
                    }
                }
            }));

        ServerPlayNetworking.registerGlobalReceiver(Fabric.test.networking.ZoneActionPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                if (!context.player().hasPermissions(2)) return;
                Fabric.test.command.ZoneCommand.handleAction(payload, context.player(), context.server());
            }));

        ServerPlayNetworking.registerGlobalReceiver(Fabric.test.networking.GroupActionPayload.TYPE, (payload, context) ->
            context.server().execute(() ->
                GroupManager.handleAction(payload, context.player(), context.server())));

        ServerPlayNetworking.registerGlobalReceiver(DealActionPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                java.util.UUID uid  = player.getUUID();
                DealSession session = activeSessions.get(uid);
                if (session == null) return;

                switch (payload.action()) {
                    case "ADD_ITEM" -> {
                        if (session.isAccepted(uid)) return; // locked
                        int invSlot = payload.slot();
                        if (invSlot < 0 || invSlot > 35) return;
                        ItemStack stack = player.getInventory().getItem(invSlot);
                        if (stack.isEmpty()) return;
                        NonNullList<ItemStack> offer = session.myOffer(uid);
                        int emptyIdx = -1;
                        for (int i = 0; i < offer.size(); i++) { if (offer.get(i).isEmpty()) { emptyIdx = i; break; } }
                        if (emptyIdx < 0) { player.sendSystemMessage(Component.literal("§cVotre zone d'échange est pleine.")); return; }
                        offer.set(emptyIdx, stack.copy());
                        player.getInventory().setItem(invSlot, ItemStack.EMPTY);
                        // Any change resets BOTH players' confirmation to prevent blind trades
                        session.resetAccepted();
                        broadcastDealUpdate(session, context.server());
                    }
                    case "REMOVE_ITEM" -> {
                        if (session.isAccepted(uid)) return;
                        int tradeSlot = payload.slot();
                        NonNullList<ItemStack> offer = session.myOffer(uid);
                        if (tradeSlot < 0 || tradeSlot >= offer.size()) return;
                        ItemStack stack = offer.get(tradeSlot);
                        if (!stack.isEmpty()) {
                            player.getInventory().add(stack.copy());
                            offer.set(tradeSlot, ItemStack.EMPTY);
                            // Any change resets BOTH players' confirmation
                            session.resetAccepted();
                            broadcastDealUpdate(session, context.server());
                        }
                    }
                    case "ACCEPT" -> {
                        session.setAccepted(uid, true);
                        broadcastDealUpdate(session, context.server());
                        if (session.bothAccepted()) completeDeal(session, context.server());
                    }
                    case "CANCEL" -> {
                        String pName  = player.getName().getString();
                        String pName2 = playerNameCache.getOrDefault(session.partner(uid), "l'autre joueur");
                        // Notify only the two players, not the whole server
                        cancelDeal(session, context.server(),
                            "Vous avez annulé l'échange avec §e" + pName2 + "§c. Items restitués.",
                            "§e" + pName + " §ca annulé l'échange. Vos items ont été restitués.");
                    }
                }
            }));
    }
}
