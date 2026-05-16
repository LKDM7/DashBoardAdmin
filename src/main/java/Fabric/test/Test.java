package Fabric.test;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.AdminActionPayload;
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
import net.minecraft.network.chat.Component;
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
    private static int clearLagTicks = -1;
    private static int removeMobsTicks = -1;
    private static final java.util.Map<java.util.UUID, java.util.Map<String, net.minecraft.core.BlockPos>> playerHomes = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Boolean> frozenPlayers = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> frozenPositions = new java.util.HashMap<>();
    private static final java.util.Set<java.util.UUID> mutedPlayers = new java.util.HashSet<>();
    private static final java.util.Set<java.util.UUID> vanishedPlayers = new java.util.HashSet<>();
    private static final java.util.Map<java.util.UUID, java.util.List<String>> playerLogs = new java.util.HashMap<>();
    private static final java.util.Map<String, String> pendingReports  = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, String> acceptedReports = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, String> closedReports   = new java.util.LinkedHashMap<>();
    private static final java.util.Map<java.util.UUID, PlayerSettings> playerSettings = new java.util.HashMap<>();

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
        new String[]{"/report",    "Envoyer un signalement aux admins"}
    );

    public static java.util.Map<java.util.UUID, PlayerSettings> getAllSettings() { return playerSettings; }
    public static PlayerSettings getPlayerSettings(java.util.UUID uuid) { return playerSettings.computeIfAbsent(uuid, k -> new PlayerSettings()); }

    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> lastPositions = new java.util.HashMap<>();

    private static void savePosition(ServerPlayer player) {
        lastPositions.put(player.getUUID(), player.position());
    }

    public static java.util.Map<java.util.UUID, java.util.Map<String, net.minecraft.core.BlockPos>> getAllHomes() {
        return playerHomes;
    }

    public static java.util.Map<String, net.minecraft.core.BlockPos> getPlayerHomes(java.util.UUID uuid) {
        return playerHomes.computeIfAbsent(uuid, k -> new java.util.HashMap<>());
    }

    public static boolean isPvpEnabled() { return pvpEnabled; }
    public static boolean isChatLocked() { return chatLocked; }
    public static boolean isWeatherCycleEnabled() { return weatherCycleEnabled; }
    public static boolean isCropTrampleEnabled() { return cropTrampleEnabled; }
    public static boolean isFrozen(java.util.UUID uuid) { return frozenPlayers.getOrDefault(uuid, false); }
    public static boolean isMuted(java.util.UUID uuid) { return mutedPlayers.contains(uuid); }
    public static boolean isVanished(java.util.UUID uuid) { return vanishedPlayers.contains(uuid); }
    public static String getGamemodeName(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer().getName();
    }
    private static void addLog(java.util.UUID uuid, String entry) {
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        playerLogs.computeIfAbsent(uuid, k -> new java.util.ArrayList<>()).add("[" + time + "] " + entry);
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

    public static void registerTpaCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
            .executes(context -> {
                ServerPlayer sender = context.getSource().getPlayerOrException();
                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                if (sender == target) return 0;
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
    private static String resolvePlayerName(net.minecraft.server.MinecraftServer server, java.util.UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        return playerNameCache.getOrDefault(uuid, uuid.toString().substring(0, 8) + "…");
    }

    public static boolean isAfk(java.util.UUID uuid) { return afkPlayers.getOrDefault(uuid, false); }

    // ... (à ajouter après tpaRequests)

    @Override
    public void onInitialize() {
        HomePersistence.register();
        VirtualChestManager.register();
        SettingsPersistence.register();

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

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            playerNameCache.put(handler.player.getUUID(), handler.player.getName().getString());
            String joinName = handler.player.getName().getString();
            Component joinMsg = Component.literal("§a[+] §f" + joinName + " §7a rejoint le serveur.");
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (getPlayerSettings(p.getUUID()).showConnectionAlerts && !p.getUUID().equals(handler.player.getUUID()))
                    p.sendSystemMessage(joinMsg);
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AdminCommand.register(dispatcher);
            registerTpaCommands(dispatcher);
            
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
                    return 0; // Retourner 0 ici aussi
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
                    player.sendSystemMessage(Component.literal("§aHome '" + name + "' enregistré !"));
                    return 1;
                })));

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
                    player.sendSystemMessage(Component.literal("§aHome '" + name + "' enregistré !"));
                    return 1;
                })));
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
                    var homes = getPlayerHomes(player.getUUID());
                    if (!homes.containsKey(name)) {
                        player.sendSystemMessage(Component.literal("§cHome '" + name + "' introuvable."));
                        return 0;
                    }
                    net.minecraft.core.BlockPos pos = homes.get(name);
                    player.teleportTo((ServerLevel)player.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                    player.sendSystemMessage(Component.literal("§aTéléporté au home '" + name + "'."));
                    return 1;
                })));
            dispatcher.register(Commands.literal("back")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
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
                        if (op.hasPermissions(2)) op.sendSystemMessage(notif);
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
                    ServerPlayNetworking.send(player, new Fabric.test.networking.OpenSettingsPayload(
                        s.allowPrivateMessages, s.allowTpaRequests, s.allowTrades,
                        s.showChatNotifications, s.showConnectionAlerts,
                        getCommandsSerialized()
                    ));
                    return 1;
                }));
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (isFrozen(player.getUUID())) {
                player.teleportTo(origin, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYRot(), player.getXRot(), true);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isFrozen(player.getUUID())) {
                    net.minecraft.world.phys.Vec3 frozenPos = frozenPositions.get(player.getUUID());
                    if (frozenPos != null && player.position().distanceToSqr(frozenPos) > 0.001) {
                        player.connection.teleport(frozenPos.x, frozenPos.y, frozenPos.z, player.getYRot(), player.getXRot());
                    }
                }
                if (isAfk(player.getUUID())) {
                    // Message persistant dans l'ActionBar
                    player.sendSystemMessage(Component.literal("§e[AFK] Vous êtes absent."), true);
                    
                    if (lastPos.containsKey(player.getUUID()) && player.position().distanceToSqr(lastPos.get(player.getUUID())) > 0.01) {
                        afkPlayers.put(player.getUUID(), false);
                        player.sendSystemMessage(Component.literal("§eVous n'êtes plus AFK."));
                        server.getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + " n'est plus AFK."), false);
                    }
                }
                lastPos.put(player.getUUID(), player.position());
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
                            if (e instanceof net.minecraft.world.entity.monster.Monster) {
                                e.discard();
                                count++;
                            }
                        }
                    }
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§cMobs supprimés: §f" + count), false);
                }
            }
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (isMuted(sender.getUUID())) {
                addLog(sender.getUUID(), "Chat bloqué (muet): " + message.signedBody().content());
                sender.sendSystemMessage(Component.literal("§cVous êtes muet, vous ne pouvez pas écrire."));
                return false;
            }
            if (chatLocked && !sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("§cLe chat est actuellement bloqué par un admin."));
                return false;
            }
            addLog(sender.getUUID(), "Chat: " + message.signedBody().content());
            return true;
        });

        // Empêcher le piétinement des cultures et gérer le verrouillage
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            net.minecraft.core.BlockPos pos = hitResult.getBlockPos();
            if (isLocked(pos) && !getOwner(pos).equals(player.getUUID()) && !isTrusted(getOwner(pos), player.getUUID())) {
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
            if (!cropTrampleEnabled && world.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.FarmBlock) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
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
                    }
                    case "TOGGLE_CROP_TRAMPLE" -> cropTrampleEnabled = !cropTrampleEnabled;
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
                                admin.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " est maintenant muet."));
                                target.sendSystemMessage(Component.literal("§cVous avez été rendu muet par un admin."));
                            }
                        }
                    }
                    case "HEAL" -> { if (target != null) { target.setHealth(target.getMaxHealth()); target.getFoodData().eat(20, 1.0f); addLog(target.getUUID(), "Heal par " + admin.getName().getString()); admin.sendSystemMessage(Component.literal("§aJoueur soigné.")); } }
                    case "LOCK_CHAT" -> {
                        chatLocked = !chatLocked;
                        context.server().getPlayerList().broadcastSystemMessage(Component.literal("§cLe chat a été " + (chatLocked ? "bloqué" : "débloqué") + " !"), false);
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
                    case "ANNOUNCE" -> context.server().getPlayerList().broadcastSystemMessage(Component.literal("§6§l[ANNONCE] §r" + payload.value()), false);
                    case "KICK" -> { if (target != null) { addLog(target.getUUID(), "Kicked par " + admin.getName().getString()); target.connection.disconnect(Component.literal("Expulsé par un admin.")); } }
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
                            if (logs.isEmpty()) {
                                admin.sendSystemMessage(Component.literal("§7Aucun log pour §e" + target.getName().getString() + "§7."));
                            } else {
                                admin.sendSystemMessage(Component.literal("§6§l[LOGS: " + target.getName().getString() + "]"));
                                for (String entry : logs) admin.sendSystemMessage(Component.literal("§7" + entry));
                            }
                        }
                    }
                    case "BAN" -> {
                        if (target != null) {
                            String reason = payload.value().isEmpty() ? "Banni par un admin." : payload.value();
                            addLog(target.getUUID(), "Banned par " + admin.getName().getString() + " (" + reason + ")");
                            context.server().getPlayerList().getBans().add(new net.minecraft.server.players.UserBanListEntry(new net.minecraft.server.players.NameAndId(target.getGameProfile()), null, "admin", null, reason));
                            target.connection.disconnect(Component.literal(reason));
                        }
                    }
                    case "KEEP_INVENTORY" -> {
                        if (target != null) {
                            PlayerSettings ks = getPlayerSettings(target.getUUID());
                            ks.keepInventory = !ks.keepInventory;
                            admin.sendSystemMessage(Component.literal("§aKeepInventory " + (ks.keepInventory ? "§aactivé" : "§cdésactivé") + " §apour §e" + target.getName().getString()));
                            target.sendSystemMessage(Component.literal(ks.keepInventory ? "§aVotre inventaire sera conservé à la mort." : "§cVotre inventaire ne sera plus conservé à la mort."));
                        }
                    }
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
    }
}
