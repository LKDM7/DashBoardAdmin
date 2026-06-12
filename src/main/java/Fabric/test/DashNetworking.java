package Fabric.test;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.*;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.Set;

public class DashNetworking {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        // ── S2C — enregistrés côté serveur sans handler (juste pour l'encodage/envoi)
        //         Les vrais handlers sont dans DashClientNetworking (@EventBusSubscriber Dist.CLIENT)
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            reg.playToClient(AdminCommand.OpenAdminGuiPayload.TYPE, AdminCommand.OpenAdminGuiPayload.CODEC, (p, c) -> {});
            reg.playToClient(OpenSettingsPayload.TYPE,    OpenSettingsPayload.CODEC,    (p, c) -> {});
            reg.playToClient(PlayerLogsPayload.TYPE,      PlayerLogsPayload.CODEC,      (p, c) -> {});
            reg.playToClient(OpenDealPayload.TYPE,        OpenDealPayload.CODEC,        (p, c) -> {});
            reg.playToClient(OpenZonePayload.TYPE,        OpenZonePayload.CODEC,        (p, c) -> {});
            reg.playToClient(OpenGroupPayload.TYPE,       OpenGroupPayload.CODEC,       (p, c) -> {});
            reg.playToClient(GroupUpdatePayload.TYPE,     GroupUpdatePayload.CODEC,     (p, c) -> {});
            reg.playToClient(NotifPayload.TYPE,           NotifPayload.CODEC,           (p, c) -> {});
            reg.playToClient(OpenSanctionsPayload.TYPE,   OpenSanctionsPayload.CODEC,   (p, c) -> {});
            reg.playToClient(OpenReportPayload.TYPE,      OpenReportPayload.CODEC,      (p, c) -> {});
            reg.playToClient(ReportImagePayload.TYPE,     ReportImagePayload.CODEC,     (p, c) -> {});
            reg.playToClient(ZoneSyncPayload.TYPE,        ZoneSyncPayload.CODEC,        (p, c) -> {});
            reg.playToClient(WandSelectionPayload.TYPE,   WandSelectionPayload.CODEC,   (p, c) -> {});
        }

        // ── C2S ───────────────────────────────────────────────────────────────
        reg.playToServer(AdminActionPayload.TYPE, AdminActionPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> handleAdminAction(payload, (ServerPlayer) ctx.player())));

        reg.playToServer(UpdateSettingsPayload.TYPE, UpdateSettingsPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                PlayerSettings s = Test.getPlayerSettings(ctx.player().getUUID());
                s.allowPrivateMessages  = payload.allowPrivateMessages();
                s.allowTpaRequests      = payload.allowTpaRequests();
                s.allowTrades           = payload.allowTrades();
                s.showChatNotifications = payload.showChatNotifications();
                s.showConnectionAlerts  = payload.showConnectionAlerts();
            }));

        reg.playToServer(PlayerActionPayload.TYPE, PlayerActionPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> handlePlayerAction(payload, (ServerPlayer) ctx.player())));

        reg.playToServer(ZoneActionPayload.TYPE, ZoneActionPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (!ctx.player().hasPermissions(2)) return;
                Fabric.test.command.ZoneCommand.handleAction(payload, (ServerPlayer) ctx.player(), ctx.player().getServer());
            }));

        reg.playToServer(GroupActionPayload.TYPE, GroupActionPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> GroupManager.handleAction(payload, (ServerPlayer) ctx.player(), ctx.player().getServer())));

        reg.playToServer(ReportSubmitPayload.TYPE, ReportSubmitPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) ctx.player();
                String name    = player.getName().getString();
                String message = payload.message();
                byte[] img     = payload.imageData();
                boolean hasImg = img != null && img.length > 0;
                Test.pendingReports.put(name, message);
                if (hasImg) Test.reportImages.put(name, img);
                player.sendSystemMessage(Component.literal("§aVotre rapport a été envoyé aux administrateurs."));
                Component notif = Component.literal("§c§l[REPORT] §r§e" + name + " §7» §f" + message + "  ")
                    .append(Component.literal("[ACCEPTER]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/reportaccept " + name))))
                    .append(Component.literal("  "))
                    .append(Component.literal("[REFUSER]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/reportdeny " + name))));
                for (ServerPlayer op : player.getServer().getPlayerList().getPlayers())
                    if (op.hasPermissions(2)) { op.sendSystemMessage(notif); PacketDistributor.sendToPlayer(op, new NotifPayload("REPORT", "§c⚑ Report de §f" + name + (hasImg ? " §8[IMG]" : ""))); }
                DiscordWebhook.sendReport(Test.getWebhookReports(), name, message, img);
            }));

        reg.playToServer(DealActionPayload.TYPE, DealActionPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> handleDealAction(payload, (ServerPlayer) ctx.player())));
    }

    // ─── Admin action handler ─────────────────────────────────────────────────
    private static void handleAdminAction(AdminActionPayload payload, ServerPlayer admin) {
        if (!admin.hasPermissions(2)) return;
        String action = payload.action();
        ServerPlayer target = admin.getServer().getPlayerList().getPlayerByName(payload.target());

        switch (action) {
            case "TOGGLE_PVP"        -> { Test.setPvpEnabled(!Test.isPvpEnabled()); ServerConfig.save(); admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§ePvP : " + (Test.isPvpEnabled() ? "§aactivé" : "§cdésactivé") + "§e."), false); }
            case "SET_DAY"           -> ((ServerLevel) admin.level()).setDayTime(6000);
            case "SET_MORNING"       -> ((ServerLevel) admin.level()).setDayTime(0);
            case "SET_EVENING"       -> ((ServerLevel) admin.level()).setDayTime(12000);
            case "SET_NIGHT"         -> ((ServerLevel) admin.level()).setDayTime(18000);
            case "SET_WEATHER_CLEAR" -> ((ServerLevel) admin.level()).setWeatherParameters(6000, 0, false, false);
            case "SET_WEATHER_RAIN"  -> ((ServerLevel) admin.level()).setWeatherParameters(0, 6000, true, false);
            case "SET_WEATHER_THUNDER" -> ((ServerLevel) admin.level()).setWeatherParameters(0, 6000, true, true);
            case "TOGGLE_WEATHER_CYCLE" -> { Test.setWeatherCycleEnabled(!Test.isWeatherCycleEnabled()); admin.getServer().getCommands().performPrefixedCommand(admin.getServer().createCommandSourceStack(), "gamerule doWeatherCycle " + Test.isWeatherCycleEnabled()); ServerConfig.save(); }
            case "TOGGLE_CROP_TRAMPLE"  -> { Test.setCropTrampleEnabled(!Test.isCropTrampleEnabled()); ServerConfig.save(); }
            case "CLEAR_LAG"   -> { admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§eClear lag dans 30 secondes !"), false); Test.clearLagTicks = 30 * 20; }
            case "SET_SPAWN"   -> { admin.level().getServer().getCommands().performPrefixedCommand(admin.createCommandSourceStack(), "/setworldspawn ~ ~ ~"); admin.sendSystemMessage(Component.literal("§aSpawn défini à votre position.")); }
            case "REMOVE_MOBS" -> { admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§cSuppression des mobs dans 30 secondes !"), false); Test.removeMobsTicks = 30 * 20; }
            case "LOCK_CHAT"   -> { Test.setChatLocked(!Test.isChatLocked()); admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§cLe chat a été " + (Test.isChatLocked() ? "bloqué" : "débloqué") + " !"), false); ServerConfig.save(); }
            case "VANISH" -> {
                net.minecraft.server.MinecraftServer vanishSrv = admin.getServer();
                ServerLevel vanishLevel = (ServerLevel) admin.level();
                net.minecraft.server.level.ChunkMap chunkMap = vanishLevel.getChunkSource().chunkMap;
                if (Test.vanishedPlayers.contains(admin.getUUID())) {
                    Test.vanishedPlayers.remove(admin.getUUID());
                    // Re-add to tab lists first (client needs profile before entity spawn)
                    net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket showInfo =
                        net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
                            .createPlayerInitializing(java.util.List.of(admin));
                    for (ServerPlayer other : vanishSrv.getPlayerList().getPlayers())
                        if (!other.getUUID().equals(admin.getUUID())) other.connection.send(showInfo);
                    // Re-add entity to chunk tracker via reflection (protected access)
                    try {
                        java.lang.reflect.Method addEntity = chunkMap.getClass()
                            .getDeclaredMethod("addEntity", net.minecraft.world.entity.Entity.class);
                        addEntity.setAccessible(true);
                        addEntity.invoke(chunkMap, admin);
                    } catch (ReflectiveOperationException ignored) {}
                    admin.sendSystemMessage(Component.literal("§eVanish : OFF"));
                } else {
                    Test.vanishedPlayers.add(admin.getUUID());
                    // Remove entity from chunk tracker via reflection (broadcasts remove to clients)
                    try {
                        java.lang.reflect.Method removeEntity = chunkMap.getClass()
                            .getDeclaredMethod("removeEntity", net.minecraft.world.entity.Entity.class);
                        removeEntity.setAccessible(true);
                        removeEntity.invoke(chunkMap, admin);
                    } catch (ReflectiveOperationException ignored) {}
                    // Remove from tab lists
                    net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket removeInfo =
                        new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(java.util.List.of(admin.getUUID()));
                    for (ServerPlayer other : vanishSrv.getPlayerList().getPlayers())
                        if (!other.getUUID().equals(admin.getUUID())) other.connection.send(removeInfo);
                    admin.sendSystemMessage(Component.literal("§eVanish : ON"));
                }
            }
            case "MUTE" -> {
                if (target != null) {
                    if (Test.mutedPlayers.contains(target.getUUID())) { Test.mutedPlayers.remove(target.getUUID()); Test.addLog(target.getUUID(), "Unmuted par " + admin.getName().getString()); admin.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " n'est plus muet.")); target.sendSystemMessage(Component.literal("§eVous n'êtes plus muet.")); }
                    else { Test.mutedPlayers.add(target.getUUID()); DiscordWebhook.sendSanction(Test.getWebhookSanctions(), admin.getName().getString(), target.getName().getString(), "MUTE", ""); Test.addLog(target.getUUID(), "Muted par " + admin.getName().getString()); Test.addSanction("MUTE", target.getName().getString(), admin.getName().getString(), ""); admin.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " est maintenant muet.")); target.sendSystemMessage(Component.literal("§cVous avez été rendu muet par un admin.")); }
                }
            }
            case "HEAL"    -> { if (target != null) { target.setHealth(target.getMaxHealth()); target.getFoodData().eat(20, 1.0f); Test.addLog(target.getUUID(), "Heal par " + admin.getName().getString()); admin.sendSystemMessage(Component.literal("§aJoueur soigné.")); } }
            case "ANNOUNCE" -> {
                String tgt = payload.target();
                Component announcement = Component.literal("§6§l[ANNONCE] §r" + payload.value());
                if (tgt.isEmpty()) admin.getServer().getPlayerList().broadcastSystemMessage(announcement, false);
                else if (tgt.startsWith("GROUP:")) {
                    String leaderName = tgt.substring(6);
                    ServerPlayer groupLeader = admin.getServer().getPlayerList().getPlayerByName(leaderName);
                    if (groupLeader != null) GroupManager.getMembers(groupLeader.getUUID()).forEach(uuid -> { ServerPlayer gp = admin.getServer().getPlayerList().getPlayer(uuid); if (gp != null) gp.sendSystemMessage(announcement); });
                } else { ServerPlayer tgtPlayer = admin.getServer().getPlayerList().getPlayerByName(tgt); if (tgtPlayer != null) tgtPlayer.sendSystemMessage(announcement); }
            }
            case "KICK"        -> { if (target != null) { Test.addLog(target.getUUID(), "Kicked par " + admin.getName().getString()); Test.addSanction("KICK", target.getName().getString(), admin.getName().getString(), ""); DiscordWebhook.sendSanction(Test.getWebhookSanctions(), admin.getName().getString(), target.getName().getString(), "KICK", ""); target.connection.disconnect(Component.literal("Expulsé par un admin.")); } }
            case "TELEPORT_TO" -> { if (target != null) { Test.addLog(target.getUUID(), "TP vers par " + admin.getName().getString()); admin.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), admin.getYRot(), admin.getXRot()); } }
            case "OPEN_INV"    -> { if (target != null) admin.openMenu(new SimpleMenuProvider((id, inv, p) -> new EditablePlayerInventoryMenu(id, inv, target.getInventory()), Component.literal("Inv: " + target.getName().getString()))); }
            case "ENDERCHEST"  -> { if (target != null) admin.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, target.getEnderChestInventory(), 3), Component.literal("Ender: " + target.getName().getString()))); }
            case "BRING"       -> { if (target != null) { Test.addLog(target.getUUID(), "Bring par " + admin.getName().getString()); target.teleportTo((ServerLevel) admin.level(), admin.getX(), admin.getY(), admin.getZ(), Set.of(), target.getYRot(), target.getXRot()); } }
            case "FREEZE" -> {
                if (target != null) {
                    boolean frozen = !Test.frozenPlayers.getOrDefault(target.getUUID(), false);
                    Test.frozenPlayers.put(target.getUUID(), frozen);
                    if (frozen) { Test.frozenPositions.put(target.getUUID(), target.position()); Test.addLog(target.getUUID(), "Frozen par " + admin.getName().getString()); target.sendSystemMessage(Component.literal("§bVous avez été gelé par un admin.")); }
                    else { Test.frozenPositions.remove(target.getUUID()); Test.addLog(target.getUUID(), "Unfrozen par " + admin.getName().getString()); target.sendSystemMessage(Component.literal("§aVous n'êtes plus gelé.")); }
                    admin.sendSystemMessage(Component.literal("§bJoueur " + (frozen ? "gelé" : "dégelé") + "."));
                }
            }
            case "ACCEPT_REPORT" -> {
                String rName = payload.target();
                if (!Test.pendingReports.containsKey(rName)) { admin.sendSystemMessage(Component.literal("§cAucun rapport en attente de §e" + rName + "§c.")); return; }
                Test.acceptedReports.put(rName, Test.pendingReports.remove(rName));
                byte[] img = Test.reportImages.remove(rName); if (img != null) Test.acceptedReportImages.put(rName, img);
                ServerPlayer reporter = admin.getServer().getPlayerList().getPlayerByName(rName);
                if (reporter != null) reporter.sendSystemMessage(Component.literal("§aVotre signalement a été pris en charge."));
                admin.sendSystemMessage(Component.literal("§aRapport de §e" + rName + " §apris en charge."));
            }
            case "CLOSE_REPORT" -> {
                String rName = payload.target();
                String rMsg = Test.acceptedReports.remove(rName);
                if (rMsg != null) {
                    if (Test.closedReports.size() >= 15) { String oldest = Test.closedReports.keySet().iterator().next(); Test.closedReports.remove(oldest); Test.closedReportImages.remove(oldest); }
                    Test.closedReports.put(rName, rMsg);
                    byte[] img = Test.acceptedReportImages.remove(rName); if (img != null) Test.closedReportImages.put(rName, img);
                    ServerPlayer reporter = admin.getServer().getPlayerList().getPlayerByName(rName);
                    if (reporter != null) reporter.sendSystemMessage(Component.literal("§aVotre signalement a été résolu."));
                    admin.sendSystemMessage(Component.literal("§aSignalement de §e" + rName + " §aclôturé."));
                } else admin.sendSystemMessage(Component.literal("§cAucun rapport en cours pour §e" + rName + "§c."));
            }
            case "REFUSE_REPORT" -> { String rName = payload.target(); if (Test.pendingReports.remove(rName) != null) { Test.reportImages.remove(rName); admin.sendSystemMessage(Component.literal("§cRapport de §e" + rName + " §crefusé.")); } else admin.sendSystemMessage(Component.literal("§cAucun rapport de §e" + rName + "§c.")); }
            case "FETCH_REPORT_IMAGE" -> {
                String rName = payload.target();
                byte[] img = Test.reportImages.containsKey(rName) ? Test.reportImages.get(rName) : Test.acceptedReportImages.containsKey(rName) ? Test.acceptedReportImages.get(rName) : Test.closedReportImages.get(rName);
                if (img != null) PacketDistributor.sendToPlayer(admin, new ReportImagePayload(rName, img));
            }
            case "SET_MAX_HOMES"  -> { try { Test.setMaxHomes(Integer.parseInt(payload.value())); ServerConfig.save(); admin.sendSystemMessage(Component.literal("§aMax homes fixé à §e" + Test.getMaxHomes() + "§a.")); } catch (NumberFormatException ignored) {} }
            case "SET_WEBHOOKS"   -> { Test.setWebhookReports(payload.target()); Test.setWebhookSanctions(payload.value()); ServerConfig.save(); admin.sendSystemMessage(Component.literal("§aWebhooks Discord mis à jour.")); }
            case "GET_LOGS" -> {
                // Fonctionne aussi pour un joueur hors ligne (résolution via le cache de noms).
                java.util.UUID logsUuid = target != null ? target.getUUID()
                    : Test.getPlayerNameCache().entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(payload.target()))
                        .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
                if (logsUuid != null) {
                    java.util.List<String> logs = Test.getPlayerLogs().getOrDefault(logsUuid, java.util.Collections.emptyList());
                    int from = Math.max(0, logs.size() - 100);
                    String serialized = logs.isEmpty() ? "" : String.join("\n", logs.subList(from, logs.size()));
                    PacketDistributor.sendToPlayer(admin, new PlayerLogsPayload(payload.target(), serialized));
                }
            }
            case "REFRESH_ADMIN" -> AdminCommand.sendAdminGui(admin, admin.getServer());
            case "SET_NOTE" -> {
                java.util.UUID noteUuid = target != null ? target.getUUID()
                    : Test.getPlayerNameCache().entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(payload.target()))
                        .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
                if (noteUuid != null) {
                    Test.setAdminNote(noteUuid, payload.value());
                    ModerationPersistence.save();
                    admin.sendSystemMessage(Component.literal(payload.value().isBlank()
                        ? "§eNote de §f" + payload.target() + " §esupprimée."
                        : "§aNote de §f" + payload.target() + " §aenregistrée."));
                }
            }
            case "GET_CHAT" -> PacketDistributor.sendToPlayer(admin,
                new PlayerLogsPayload("Chat global", Test.getChatHistorySerialized()));
            case "WARP_ADD" -> {
                String wName = payload.value().trim().replaceAll("[^A-Za-z0-9_\\-]", "");
                if (wName.isEmpty()) { admin.sendSystemMessage(Component.literal("§cNom de warp invalide.")); return; }
                if (Test.getWarps().containsKey(wName)) { admin.sendSystemMessage(Component.literal("§cLe warp §e" + wName + " §cexiste déjà.")); return; }
                Test.getWarps().put(wName, admin.blockPosition());
                Test.getWarpsDim().put(wName, admin.level().dimension().location().toString());
                ServerConfig.save();
                admin.sendSystemMessage(Component.literal("§aWarp §e" + wName + " §acréé à votre position."));
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "WARP_DELETE" -> {
                if (Test.getWarps().remove(payload.value()) != null) {
                    Test.getWarpsDim().remove(payload.value());
                    ServerConfig.save();
                    admin.sendSystemMessage(Component.literal("§cWarp §e" + payload.value() + " §csupprimé."));
                }
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "WARP_TP" -> teleportToWarp(admin, payload.value());
            case "SCHEDULE_RESTART" -> {
                try {
                    int mins = Math.max(1, Math.min(120, Integer.parseInt(payload.value())));
                    Test.scheduleRestart(mins);
                    admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "§c⚠ §lRedémarrage du serveur programmé dans §e§l" + mins + " minute" + (mins > 1 ? "s" : "") + "§c§l."), false);
                } catch (NumberFormatException ignored) {}
            }
            case "CANCEL_RESTART" -> {
                if (Test.isRestartScheduled()) {
                    Test.cancelRestart();
                    admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "§a✔ Redémarrage du serveur annulé."), false);
                } else {
                    admin.sendSystemMessage(Component.literal("§eAucun redémarrage programmé."));
                }
            }
            case "TOGGLE_MAIL_SPY" -> {
                Test.setMailSpyEnabled(!Test.isMailSpyEnabled());
                ServerConfig.save();
                admin.sendSystemMessage(Component.literal("§eSpy des MP : "
                    + (Test.isMailSpyEnabled() ? "§aactivé" : "§cdésactivé") + "§e."));
            }
            case "SET_MOTD" -> {
                Test.setMotd(payload.value());
                ServerConfig.save();
                admin.sendSystemMessage(Component.literal(Test.getMotd().isEmpty()
                    ? "§eMOTD supprimé." : "§aMOTD mis à jour : §7" + Test.getMotd()));
            }
            case "BAN" -> {
                if (target != null) {
                    String[] banParts = payload.value().split("\t", 2);
                    int banDays = 0; String reason = "Banni par un admin.";
                    try { banDays = Integer.parseInt(banParts[0]); } catch (NumberFormatException ignored) {}
                    if (banParts.length > 1 && !banParts[1].isEmpty()) reason = banParts[1];
                    java.util.Date expires = banDays > 0 ? new java.util.Date(System.currentTimeMillis() + (long) banDays * 24 * 3600 * 1000L) : null;
                    String sanctionReason = (banDays > 0 ? "(" + banDays + "j) " : "") + reason;
                    Test.addLog(target.getUUID(), "Banned par " + admin.getName().getString() + " (" + sanctionReason + ")");
                    Test.addSanction("BAN", target.getName().getString(), admin.getName().getString(), sanctionReason);
                    DiscordWebhook.sendSanction(Test.getWebhookSanctions(), admin.getName().getString(), target.getName().getString(), "BAN", sanctionReason);
                    admin.getServer().getPlayerList().getBans().add(new net.minecraft.server.players.UserBanListEntry(target.getGameProfile(), null, "admin", expires, reason));
                    target.connection.disconnect(Component.literal(reason));
                }
            }
            case "UNBAN" -> { String name = payload.target(); Test.addSanction("UNBAN", name, admin.getName().getString(), ""); Test.getPlayerNameCache().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(name)).map(java.util.Map.Entry::getKey).findFirst().ifPresent(uuid -> Test.addLog(uuid, "Débanni par " + admin.getName().getString())); admin.getServer().getCommands().performPrefixedCommand(admin.getServer().createCommandSourceStack(), "pardon " + name); admin.sendSystemMessage(Component.literal("§a" + name + " a été débanni.")); }
            case "KEEP_INVENTORY" -> { if (target != null) { PlayerSettings ks = Test.getPlayerSettings(target.getUUID()); ks.keepInventory = !ks.keepInventory; admin.sendSystemMessage(Component.literal("§aKeepInventory " + (ks.keepInventory ? "§aactivé" : "§cdésactivé") + " §apour §e" + target.getName().getString())); target.sendSystemMessage(Component.literal(ks.keepInventory ? "§aVotre inventaire sera conservé à la mort." : "§cVotre inventaire ne sera plus conservé à la mort.")); } }
            case "OPEN_ZONES"    -> Fabric.test.command.ZoneCommand.sendZoneScreen(admin, admin.getServer());
            case "GET_SANCTIONS" -> PacketDistributor.sendToPlayer(admin, new OpenSanctionsPayload(Test.getSanctionsSerialized()));
            case "GAMEMODE" -> { if (target != null) { net.minecraft.world.level.GameType next = switch (target.gameMode.getGameModeForPlayer()) { case SURVIVAL -> net.minecraft.world.level.GameType.CREATIVE; case CREATIVE -> net.minecraft.world.level.GameType.SPECTATOR; default -> net.minecraft.world.level.GameType.SURVIVAL; }; target.setGameMode(next); admin.sendSystemMessage(Component.literal("Mode de jeu changé en: " + next.getName())); } }
            case "SCHEDULE_ADD"  -> { String[] parts = payload.value().split("\t", 2); if (parts.length == 2) { try { int minutes = Integer.parseInt(parts[1].trim()); Test.addScheduledBroadcast(parts[0], minutes * 1200); admin.sendSystemMessage(Component.literal("§aBroadcast programmé ajouté.")); } catch (NumberFormatException ignored) {} } }
            case "SCHEDULE_REMOVE" -> { try { int idx = Integer.parseInt(payload.value()); if (Test.removeScheduledBroadcast(idx)) { ServerConfig.save(); admin.sendSystemMessage(Component.literal("§cBroadcast supprimé.")); } } catch (NumberFormatException ignored) {} }
            case "SET_COOLDOWNS" -> { String[] parts = payload.value().split("\\|"); if (parts.length >= 3) { try { Test.setCooldownHome(Integer.parseInt(parts[0])); Test.setCooldownBack(Integer.parseInt(parts[1])); Test.setCooldownTpa(Integer.parseInt(parts[2])); if (parts.length >= 4) Test.setCooldownWarp(Integer.parseInt(parts[3])); admin.sendSystemMessage(Component.literal("§aCooldowns mis à jour.")); } catch (NumberFormatException ignored) {} } }
            case "TOGGLE_AFK_AUTO"           -> { Test.setAfkAutoEnabled(!Test.isAfkAutoEnabled()); admin.sendSystemMessage(Component.literal("§eAFK Automatique " + (Test.isAfkAutoEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_PROPORTIONAL_SLEEP" -> { Test.setProportionalSleepEnabled(!Test.isProportionalSleepEnabled()); admin.sendSystemMessage(Component.literal("§eSommeil Proportionnel " + (Test.isProportionalSleepEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_TREE_CAPITATOR"     -> { Test.setTreeCapitatorEnabled(!Test.isTreeCapitatorEnabled()); admin.sendSystemMessage(Component.literal("§eBûcheron Intelligent " + (Test.isTreeCapitatorEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_FAST_LEAF_DECAY"    -> { Test.setFastLeafDecayEnabled(!Test.isFastLeafDecayEnabled()); admin.sendSystemMessage(Component.literal("§eFast Leaf Decay " + (Test.isFastLeafDecayEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_DOUBLE_DOOR"        -> { Test.setDoubleDoorEnabled(!Test.isDoubleDoorEnabled()); admin.sendSystemMessage(Component.literal("§eDouble Door " + (Test.isDoubleDoorEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_RIGHT_CLICK_HARVEST"-> { Test.setRightClickHarvestEnabled(!Test.isRightClickHarvestEnabled()); admin.sendSystemMessage(Component.literal("§eRécolte clic droit " + (Test.isRightClickHarvestEnabled() ? "§aactivée" : "§cdésactivée") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_DISPENSER_HARVEST"  -> { Test.setDispenserHarvestEnabled(!Test.isDispenserHarvestEnabled()); admin.sendSystemMessage(Component.literal("§eDistributeur récolte " + (Test.isDispenserHarvestEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "SET_AFK_DELAY" -> { try { int mins = Integer.parseInt(payload.value()); Test.setAfkDelayMinutes(mins); ServerConfig.save(); admin.sendSystemMessage(Component.literal("§eDélai AFK réglé à §f" + mins + "§e min.")); } catch (NumberFormatException ignored) {} }
        }
    }

    /** Téléporte vers un warp (dimension comprise) — utilisé par le GUI admin et le menu joueur. */
    private static void teleportToWarp(ServerPlayer player, String name) {
        net.minecraft.core.BlockPos pos = Test.getWarps().get(name);
        if (pos == null) { player.sendSystemMessage(Component.literal("§cWarp §e" + name + " §cintrouvable.")); return; }
        String dimId = Test.getWarpsDim().getOrDefault(name, "minecraft:overworld");
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey = net.minecraft.resources.ResourceKey.create(
            net.minecraft.world.level.Level.OVERWORLD.registryKey(),
            net.minecraft.resources.ResourceLocation.parse(dimId));
        ServerLevel targetLevel = player.getServer().getLevel(dimKey);
        if (targetLevel == null) targetLevel = (ServerLevel) player.level();
        Test.savePosition(player);
        player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("§aTéléporté au warp §e'" + name + "'§a."));
    }

    // ─── Player action handler ────────────────────────────────────────────────
    private static void handlePlayerAction(PlayerActionPayload payload, ServerPlayer player) {
        switch (payload.action()) {
            case "WARP_TP" -> {
                if (Fabric.test.command.ZoneCommand.isInBuildMode(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("§cImpossible d'utiliser les warps en mode construction."));
                    break;
                }
                if (!Test.checkCooldown(Test.getLastWarpUse(), player.getUUID(), Test.getCooldownWarp(), player, "/warp")) break;
                teleportToWarp(player, payload.value());
            }
            case "HOME_TP" -> {
                if (!Test.checkCooldown(Test.getLastHomeUse(), player.getUUID(), Test.getCooldownHome(), player, "/home")) break;
                net.minecraft.core.BlockPos pos = Test.getPlayerHomes(player.getUUID()).get(payload.value());
                if (pos != null) {
                    Test.getLastPositions().put(player.getUUID(), player.position());
                    String dimId = Test.getPlayerHomesDim(player.getUUID()).getOrDefault(payload.value(), "minecraft:overworld");
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey = net.minecraft.resources.ResourceKey.create(net.minecraft.world.level.Level.OVERWORLD.registryKey(), net.minecraft.resources.ResourceLocation.parse(dimId));
                    ServerLevel targetLevel = player.getServer().getLevel(dimKey);
                    if (targetLevel == null) targetLevel = (ServerLevel) player.level();
                    player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal("§aTéléporté au home '" + payload.value() + "'."));
                }
            }
            case "HOME_DELETE" -> { if (Test.getPlayerHomes(player.getUUID()).remove(payload.value()) != null) { Test.getPlayerHomesDim(player.getUUID()).remove(payload.value()); player.sendSystemMessage(Component.literal("§cHome '" + payload.value() + "' supprimé.")); } }
            case "LOCK_DELETE" -> {
                String[] parts = payload.value().split(",");
                if (parts.length == 3) try {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    if (Test.isLocked(pos) && Test.getOwner(pos).equals(player.getUUID())) { Test.getAllLockedBlocks().remove(pos); player.sendSystemMessage(Component.literal("§aBloc déverrouillé.")); }
                } catch (NumberFormatException ignored) {}
            }
            case "UNTRUST" -> {
                try { java.util.UUID targetUUID = java.util.UUID.fromString(payload.value()); Test.getTrusted(player.getUUID()).remove(targetUUID); String name = Test.getPlayerNameCache().getOrDefault(targetUUID, "joueur"); player.sendSystemMessage(Component.literal("§c" + name + " n'a plus accès à vos blocs verrouillés.")); } catch (IllegalArgumentException ignored) {}
            }
            case "REFRESH_STATS" -> {
                int pt = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
                long ts = pt / 20L; long rh = ts / 3600, rm = (ts % 3600) / 60;
                int rd = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
                int rk = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS));
                int rm2 = Test.getAllHostileMobKills().getOrDefault(player.getUUID(), 0);
                long rb = (player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.WALK_ONE_CM)) + player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.SPRINT_ONE_CM))) / 100L;
                String stats = rh + "|" + rm + "|" + rd + "|" + rk + "|" + rm2 + "|" + rb + "|" + player.totalExperience;
                PlayerSettings rs = Test.getPlayerSettings(player.getUUID());
                PacketDistributor.sendToPlayer(player, new OpenSettingsPayload(rs.allowPrivateMessages, rs.allowTpaRequests, rs.allowTrades, rs.showChatNotifications, rs.showConnectionAlerts, "", "", "", "", stats, Fabric.test.command.ZoneCommand.getBuildInfoFor(player), Test.getWarpsSerialized()));
            }
        }
    }

    // ─── Deal action handler ──────────────────────────────────────────────────
    private static void handleDealAction(DealActionPayload payload, ServerPlayer player) {
        java.util.UUID uid = player.getUUID();
        Test.DealSession session = Test.activeSessions.get(uid);
        if (session == null) return;
        switch (payload.action()) {
            case "ADD_ITEM" -> {
                if (session.isAccepted(uid)) return;
                int invSlot = payload.slot();
                if (invSlot < 0 || invSlot > 35) return;
                ItemStack stack = player.getInventory().getItem(invSlot);
                if (stack.isEmpty()) return;
                NonNullList<ItemStack> offer = session.myOffer(uid);
                int emptyIdx = -1;
                for (int i = 0; i < offer.size(); i++) if (offer.get(i).isEmpty()) { emptyIdx = i; break; }
                if (emptyIdx < 0) { player.sendSystemMessage(Component.literal("§cVotre zone d'échange est pleine.")); return; }
                offer.set(emptyIdx, stack.copy());
                player.getInventory().setItem(invSlot, ItemStack.EMPTY);
                session.resetAccepted();
                Test.broadcastDealUpdate(session, player.getServer());
            }
            case "REMOVE_ITEM" -> {
                if (session.isAccepted(uid)) return;
                int tradeSlot = payload.slot();
                NonNullList<ItemStack> offer = session.myOffer(uid);
                if (tradeSlot < 0 || tradeSlot >= offer.size()) return;
                ItemStack stack = offer.get(tradeSlot);
                if (!stack.isEmpty()) { player.getInventory().add(stack.copy()); offer.set(tradeSlot, ItemStack.EMPTY); session.resetAccepted(); Test.broadcastDealUpdate(session, player.getServer()); }
            }
            case "ACCEPT" -> { session.setAccepted(uid, true); Test.broadcastDealUpdate(session, player.getServer()); if (session.bothAccepted()) Test.completeDeal(session, player.getServer()); }
            case "CANCEL"  -> { String pName2 = Test.getPlayerNameCache().getOrDefault(session.partner(uid), "l'autre joueur"); Test.cancelDeal(session, player.getServer(), "Vous avez annulé l'échange avec §e" + pName2 + "§c. Items restitués.", "§e" + player.getName().getString() + " §ca annulé l'échange. Vos items ont été restitués."); }
        }
    }
}
