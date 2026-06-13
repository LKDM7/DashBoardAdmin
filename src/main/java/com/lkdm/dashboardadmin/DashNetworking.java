package com.lkdm.dashboardadmin;

import com.lkdm.dashboardadmin.command.AdminCommand;
import com.lkdm.dashboardadmin.networking.*;
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

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

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
            reg.playToClient(OpenAuditPayload.TYPE,       OpenAuditPayload.CODEC,       (p, c) -> {});
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
                PlayerSettings s = DashboardAdmin.getPlayerSettings(ctx.player().getUUID());
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
                if (!RoleManager.can((ServerPlayer) ctx.player(), "tab.zones")) return;
                com.lkdm.dashboardadmin.command.ZoneCommand.handleAction(payload, (ServerPlayer) ctx.player(), ctx.player().getServer());
            }));

        reg.playToServer(GroupActionPayload.TYPE, GroupActionPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> GroupManager.handleAction(payload, (ServerPlayer) ctx.player(), ctx.player().getServer())));

        reg.playToServer(ReportSubmitPayload.TYPE, ReportSubmitPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() ->
                ReportManager.submit((ServerPlayer) ctx.player(), payload.message(), payload.imageData())));

        reg.playToServer(DealActionPayload.TYPE, DealActionPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> handleDealAction(payload, (ServerPlayer) ctx.player())));
    }

    // ─── Admin action handler ─────────────────────────────────────────────────
    private static void handleAdminAction(AdminActionPayload payload, ServerPlayer admin) {
        String action = payload.action();
        if (!RoleManager.can(admin, RoleManager.permForAction(action))) {
            admin.sendSystemMessage(Component.literal("§cVous n'avez pas la permission pour cette action."));
            return;
        }
        if (DashboardAdmin.isAuditable(action))
            DashboardAdmin.addAudit(admin.getName().getString(), action, payload.target(), payload.value());
        ServerPlayer target = admin.getServer().getPlayerList().getPlayerByName(payload.target());

        switch (action) {
            case "TOGGLE_PVP"        -> { DashboardAdmin.setPvpEnabled(!DashboardAdmin.isPvpEnabled()); ServerConfig.save(); admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§ePvP : " + (DashboardAdmin.isPvpEnabled() ? "§aactivé" : "§cdésactivé") + "§e."), false); }
            case "SET_DAY"           -> ((ServerLevel) admin.level()).setDayTime(6000);
            case "SET_MORNING"       -> ((ServerLevel) admin.level()).setDayTime(0);
            case "SET_EVENING"       -> ((ServerLevel) admin.level()).setDayTime(12000);
            case "SET_NIGHT"         -> ((ServerLevel) admin.level()).setDayTime(18000);
            case "SET_WEATHER_CLEAR" -> ((ServerLevel) admin.level()).setWeatherParameters(6000, 0, false, false);
            case "SET_WEATHER_RAIN"  -> ((ServerLevel) admin.level()).setWeatherParameters(0, 6000, true, false);
            case "SET_WEATHER_THUNDER" -> ((ServerLevel) admin.level()).setWeatherParameters(0, 6000, true, true);
            case "TOGGLE_WEATHER_CYCLE" -> { DashboardAdmin.setWeatherCycleEnabled(!DashboardAdmin.isWeatherCycleEnabled()); admin.getServer().getCommands().performPrefixedCommand(admin.getServer().createCommandSourceStack(), "gamerule doWeatherCycle " + DashboardAdmin.isWeatherCycleEnabled()); ServerConfig.save(); }
            case "TOGGLE_CROP_TRAMPLE"  -> { DashboardAdmin.setCropTrampleEnabled(!DashboardAdmin.isCropTrampleEnabled()); ServerConfig.save(); }
            case "CLEAR_LAG"   -> { admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§eClear lag dans 30 secondes !"), false); DashboardAdmin.clearLagTicks = 30 * 20; }
            case "SET_SPAWN"   -> { admin.level().getServer().getCommands().performPrefixedCommand(admin.createCommandSourceStack(), "/setworldspawn ~ ~ ~"); admin.sendSystemMessage(Component.literal("§aSpawn défini à votre position.")); }
            case "REMOVE_MOBS" -> { admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§cSuppression des mobs dans 30 secondes !"), false); DashboardAdmin.removeMobsTicks = 30 * 20; }
            case "LOCK_CHAT"   -> { DashboardAdmin.setChatLocked(!DashboardAdmin.isChatLocked()); admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§cLe chat a été " + (DashboardAdmin.isChatLocked() ? "bloqué" : "débloqué") + " !"), false); ServerConfig.save(); }
            case "VANISH" -> {
                net.minecraft.server.MinecraftServer vanishSrv = admin.getServer();
                ServerLevel vanishLevel = (ServerLevel) admin.level();
                net.minecraft.server.level.ChunkMap chunkMap = vanishLevel.getChunkSource().chunkMap;
                if (DashboardAdmin.vanishedPlayers.contains(admin.getUUID())) {
                    DashboardAdmin.vanishedPlayers.remove(admin.getUUID());
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
                    } catch (ReflectiveOperationException e) {
                        LOGGER.error("[DashBoardAdmin] Vanish OFF: échec ChunkMap.addEntity par réflexion (API NeoForge changée ?)", e);
                        admin.sendSystemMessage(Component.literal("§cErreur interne du vanish (voir logs serveur)."));
                    }
                    admin.sendSystemMessage(Component.literal("§eVanish : OFF"));
                } else {
                    DashboardAdmin.vanishedPlayers.add(admin.getUUID());
                    // Remove entity from chunk tracker via reflection (broadcasts remove to clients)
                    try {
                        java.lang.reflect.Method removeEntity = chunkMap.getClass()
                            .getDeclaredMethod("removeEntity", net.minecraft.world.entity.Entity.class);
                        removeEntity.setAccessible(true);
                        removeEntity.invoke(chunkMap, admin);
                    } catch (ReflectiveOperationException e) {
                        LOGGER.error("[DashBoardAdmin] Vanish ON: échec ChunkMap.removeEntity par réflexion (API NeoForge changée ?)", e);
                        admin.sendSystemMessage(Component.literal("§cErreur interne du vanish (voir logs serveur)."));
                    }
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
                    if (DashboardAdmin.mutedPlayers.contains(target.getUUID())) { DashboardAdmin.mutedPlayers.remove(target.getUUID()); DashboardAdmin.addLog(target.getUUID(), "Unmuted par " + admin.getName().getString()); admin.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " n'est plus muet.")); target.sendSystemMessage(Component.literal("§eVous n'êtes plus muet.")); }
                    else { DashboardAdmin.mutedPlayers.add(target.getUUID()); DiscordWebhook.sendSanction(DashboardAdmin.getWebhookSanctions(), admin.getName().getString(), target.getName().getString(), "MUTE", ""); DashboardAdmin.addLog(target.getUUID(), "Muted par " + admin.getName().getString()); DashboardAdmin.addSanction("MUTE", target.getName().getString(), admin.getName().getString(), ""); admin.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " est maintenant muet.")); target.sendSystemMessage(Component.literal("§cVous avez été rendu muet par un admin.")); }
                }
            }
            case "HEAL"    -> { if (target != null) { target.setHealth(target.getMaxHealth()); target.getFoodData().eat(20, 1.0f); DashboardAdmin.addLog(target.getUUID(), "Heal par " + admin.getName().getString()); admin.sendSystemMessage(Component.literal("§aJoueur soigné.")); } }
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
            case "KICK"        -> { if (target != null) { DashboardAdmin.addLog(target.getUUID(), "Kicked par " + admin.getName().getString()); DashboardAdmin.addSanction("KICK", target.getName().getString(), admin.getName().getString(), ""); DiscordWebhook.sendSanction(DashboardAdmin.getWebhookSanctions(), admin.getName().getString(), target.getName().getString(), "KICK", ""); target.connection.disconnect(Component.literal("Expulsé par un admin.")); } }
            case "TELEPORT_TO" -> { if (target != null) { DashboardAdmin.addLog(target.getUUID(), "TP vers par " + admin.getName().getString()); admin.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), admin.getYRot(), admin.getXRot()); } }
            case "OPEN_INV"    -> { if (target != null) admin.openMenu(new SimpleMenuProvider((id, inv, p) -> new EditablePlayerInventoryMenu(id, inv, target.getInventory()), Component.literal("Inv: " + target.getName().getString()))); }
            case "ENDERCHEST"  -> { if (target != null) admin.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, target.getEnderChestInventory(), 3), Component.literal("Ender: " + target.getName().getString()))); }
            case "BRING"       -> { if (target != null) { DashboardAdmin.addLog(target.getUUID(), "Bring par " + admin.getName().getString()); target.teleportTo((ServerLevel) admin.level(), admin.getX(), admin.getY(), admin.getZ(), Set.of(), target.getYRot(), target.getXRot()); } }
            case "FREEZE" -> {
                if (target != null) {
                    boolean frozen = !DashboardAdmin.frozenPlayers.getOrDefault(target.getUUID(), false);
                    DashboardAdmin.frozenPlayers.put(target.getUUID(), frozen);
                    if (frozen) { DashboardAdmin.frozenPositions.put(target.getUUID(), target.position()); DashboardAdmin.addLog(target.getUUID(), "Frozen par " + admin.getName().getString()); target.sendSystemMessage(Component.literal("§bVous avez été gelé par un admin.")); }
                    else { DashboardAdmin.frozenPositions.remove(target.getUUID()); DashboardAdmin.addLog(target.getUUID(), "Unfrozen par " + admin.getName().getString()); target.sendSystemMessage(Component.literal("§aVous n'êtes plus gelé.")); }
                    admin.sendSystemMessage(Component.literal("§bJoueur " + (frozen ? "gelé" : "dégelé") + "."));
                }
            }
            case "ACCEPT_REPORT"      -> ReportManager.accept(admin, payload.target());
            case "CLOSE_REPORT"       -> ReportManager.close(admin, payload.target());
            case "REFUSE_REPORT"      -> ReportManager.refuse(admin, payload.target());
            case "FETCH_REPORT_IMAGE" -> ReportManager.fetchImage(admin, payload.target());
            case "SET_MAX_HOMES"  -> { try { DashboardAdmin.setMaxHomes(Integer.parseInt(payload.value())); ServerConfig.save(); admin.sendSystemMessage(Component.literal("§aMax homes fixé à §e" + DashboardAdmin.getMaxHomes() + "§a.")); } catch (NumberFormatException ignored) {} }
            case "SET_WEBHOOKS"   -> { DashboardAdmin.setWebhookReports(payload.target()); DashboardAdmin.setWebhookSanctions(payload.value()); ServerConfig.save(); admin.sendSystemMessage(Component.literal("§aWebhooks Discord mis à jour.")); }
            case "GET_LOGS" -> {
                // Fonctionne aussi pour un joueur hors ligne (résolution via le cache de noms).
                java.util.UUID logsUuid = target != null ? target.getUUID()
                    : DashboardAdmin.getPlayerNameCache().entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(payload.target()))
                        .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
                if (logsUuid != null) {
                    java.util.List<String> logs = DashboardAdmin.getPlayerLogs().getOrDefault(logsUuid, java.util.Collections.emptyList());
                    int from = Math.max(0, logs.size() - 100);
                    String serialized = logs.isEmpty() ? "" : String.join("\n", logs.subList(from, logs.size()));
                    PacketDistributor.sendToPlayer(admin, new PlayerLogsPayload(payload.target(), serialized));
                }
            }
            case "REFRESH_ADMIN" -> AdminCommand.sendAdminGui(admin, admin.getServer());
            case "ADD_NOTE" -> {
                java.util.UUID noteUuid = target != null ? target.getUUID()
                    : DashboardAdmin.getPlayerNameCache().entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(payload.target()))
                        .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
                if (noteUuid != null && DashboardAdmin.addAdminNote(noteUuid, payload.value())) {
                    ModerationPersistence.save();
                    admin.sendSystemMessage(Component.literal("§aNote ajoutée pour §f" + payload.target() + "§a."));
                    AdminCommand.sendAdminGui(admin, admin.getServer());
                } else admin.sendSystemMessage(Component.literal("§cNote vide ou limite atteinte (15 max)."));
            }
            case "DEL_NOTE" -> {
                java.util.UUID noteUuid = target != null ? target.getUUID()
                    : DashboardAdmin.getPlayerNameCache().entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(payload.target()))
                        .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
                if (noteUuid != null) {
                    try { DashboardAdmin.removeAdminNote(noteUuid, Integer.parseInt(payload.value())); }
                    catch (NumberFormatException ignored) {}
                    ModerationPersistence.save();
                    AdminCommand.sendAdminGui(admin, admin.getServer());
                }
            }
            case "GET_CHAT" -> PacketDistributor.sendToPlayer(admin,
                new PlayerLogsPayload("Chat global", DashboardAdmin.getChatHistorySerialized()));
            case "WARP_ADD" -> {
                String wName = payload.value().trim().replaceAll("[^A-Za-z0-9_\\-]", "");
                if (wName.isEmpty()) { admin.sendSystemMessage(Component.literal("§cNom de warp invalide.")); return; }
                if (WarpManager.getWarps().containsKey(wName)) { admin.sendSystemMessage(Component.literal("§cLe warp §e" + wName + " §cexiste déjà.")); return; }
                WarpManager.getWarps().put(wName, admin.blockPosition());
                WarpManager.getWarpsDim().put(wName, admin.level().dimension().location().toString());
                ServerConfig.save();
                admin.sendSystemMessage(Component.literal("§aWarp §e" + wName + " §acréé à votre position."));
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "WARP_DELETE" -> {
                if (WarpManager.getWarps().remove(payload.value()) != null) {
                    WarpManager.getWarpsDim().remove(payload.value());
                    ServerConfig.save();
                    admin.sendSystemMessage(Component.literal("§cWarp §e" + payload.value() + " §csupprimé."));
                }
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "WARP_TP" -> WarpManager.teleportToWarp(admin, payload.value());
            case "SCHEDULE_RESTART" -> {
                try {
                    int mins = Math.max(1, Math.min(120, Integer.parseInt(payload.value())));
                    DashboardAdmin.scheduleRestart(mins);
                    admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "§c⚠ §lRedémarrage du serveur programmé dans §e§l" + mins + " minute" + (mins > 1 ? "s" : "") + "§c§l."), false);
                } catch (NumberFormatException ignored) {}
            }
            case "CANCEL_RESTART" -> {
                if (DashboardAdmin.isRestartScheduled()) {
                    DashboardAdmin.cancelRestart();
                    admin.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "§a✔ Redémarrage du serveur annulé."), false);
                } else {
                    admin.sendSystemMessage(Component.literal("§eAucun redémarrage programmé."));
                }
            }
            case "TOGGLE_MAIL_SPY" -> {
                DashboardAdmin.setMailSpyEnabled(!DashboardAdmin.isMailSpyEnabled());
                ServerConfig.save();
                admin.sendSystemMessage(Component.literal("§eSpy des MP : "
                    + (DashboardAdmin.isMailSpyEnabled() ? "§aactivé" : "§cdésactivé") + "§e."));
            }
            case "SET_MOTD" -> {
                DashboardAdmin.setMotd(payload.value());
                ServerConfig.save();
                admin.sendSystemMessage(Component.literal(DashboardAdmin.getMotd().isEmpty()
                    ? "§eMOTD supprimé." : "§aMOTD mis à jour : §7" + DashboardAdmin.getMotd()));
            }
            case "BAN" -> {
                if (target != null) {
                    String[] banParts = payload.value().split("\t", 2);
                    long banSeconds = 0; String reason = "Banni par un admin.";
                    try { banSeconds = Long.parseLong(banParts[0]); } catch (NumberFormatException ignored) {}
                    if (banParts.length > 1 && !banParts[1].isEmpty()) reason = banParts[1];
                    java.util.Date expires = banSeconds > 0 ? new java.util.Date(System.currentTimeMillis() + banSeconds * 1000L) : null;
                    String sanctionReason = (banSeconds > 0 ? "(" + DashboardAdmin.formatDurationShort(banSeconds) + ") " : "") + reason;
                    DashboardAdmin.addLog(target.getUUID(), "Banned par " + admin.getName().getString() + " (" + sanctionReason + ")");
                    DashboardAdmin.addSanction("BAN", target.getName().getString(), admin.getName().getString(), sanctionReason);
                    DiscordWebhook.sendSanction(DashboardAdmin.getWebhookSanctions(), admin.getName().getString(), target.getName().getString(), "BAN", sanctionReason);
                    admin.getServer().getPlayerList().getBans().add(new net.minecraft.server.players.UserBanListEntry(target.getGameProfile(), null, "admin", expires, reason));
                    target.connection.disconnect(Component.literal(reason));
                }
            }
            case "UNBAN" -> { String name = payload.target(); DashboardAdmin.addSanction("UNBAN", name, admin.getName().getString(), ""); DashboardAdmin.getPlayerNameCache().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(name)).map(java.util.Map.Entry::getKey).findFirst().ifPresent(uuid -> DashboardAdmin.addLog(uuid, "Débanni par " + admin.getName().getString())); admin.getServer().getCommands().performPrefixedCommand(admin.getServer().createCommandSourceStack(), "pardon " + name); admin.sendSystemMessage(Component.literal("§a" + name + " a été débanni.")); }
            case "KEEP_INVENTORY" -> { if (target != null) { PlayerSettings ks = DashboardAdmin.getPlayerSettings(target.getUUID()); ks.keepInventory = !ks.keepInventory; admin.sendSystemMessage(Component.literal("§aKeepInventory " + (ks.keepInventory ? "§aactivé" : "§cdésactivé") + " §apour §e" + target.getName().getString())); target.sendSystemMessage(Component.literal(ks.keepInventory ? "§aVotre inventaire sera conservé à la mort." : "§cVotre inventaire ne sera plus conservé à la mort.")); } }
            case "OPEN_ZONES"    -> com.lkdm.dashboardadmin.command.ZoneCommand.sendZoneScreen(admin, admin.getServer());
            case "GET_SANCTIONS" -> PacketDistributor.sendToPlayer(admin, new OpenSanctionsPayload(DashboardAdmin.getSanctionsSerialized()));
            case "GET_AUDIT"     -> PacketDistributor.sendToPlayer(admin, new OpenAuditPayload(DashboardAdmin.getAuditSerialized()));
            case "GAMEMODE" -> { if (target != null) { net.minecraft.world.level.GameType next = switch (target.gameMode.getGameModeForPlayer()) { case SURVIVAL -> net.minecraft.world.level.GameType.CREATIVE; case CREATIVE -> net.minecraft.world.level.GameType.SPECTATOR; default -> net.minecraft.world.level.GameType.SURVIVAL; }; target.setGameMode(next); admin.sendSystemMessage(Component.literal("Mode de jeu changé en: " + next.getName())); } }
            case "SCHEDULE_ADD"  -> { String[] parts = payload.value().split("\t", 2); if (parts.length == 2) { try { int minutes = Integer.parseInt(parts[1].trim()); DashboardAdmin.addScheduledBroadcast(parts[0], minutes * 1200); admin.sendSystemMessage(Component.literal("§aBroadcast programmé ajouté.")); } catch (NumberFormatException ignored) {} } }
            case "SCHEDULE_REMOVE" -> { try { int idx = Integer.parseInt(payload.value()); if (DashboardAdmin.removeScheduledBroadcast(idx)) { ServerConfig.save(); admin.sendSystemMessage(Component.literal("§cBroadcast supprimé.")); } } catch (NumberFormatException ignored) {} }
            case "SET_COOLDOWNS" -> { String[] parts = payload.value().split("\\|"); if (parts.length >= 3) { try { DashboardAdmin.setCooldownHome(Integer.parseInt(parts[0])); DashboardAdmin.setCooldownBack(Integer.parseInt(parts[1])); DashboardAdmin.setCooldownTpa(Integer.parseInt(parts[2])); if (parts.length >= 4) DashboardAdmin.setCooldownWarp(Integer.parseInt(parts[3])); admin.sendSystemMessage(Component.literal("§aCooldowns mis à jour.")); } catch (NumberFormatException ignored) {} } }
            case "TOGGLE_AFK_AUTO"           -> { DashboardAdmin.setAfkAutoEnabled(!DashboardAdmin.isAfkAutoEnabled()); admin.sendSystemMessage(Component.literal("§eAFK Automatique " + (DashboardAdmin.isAfkAutoEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_PROPORTIONAL_SLEEP" -> { DashboardAdmin.setProportionalSleepEnabled(!DashboardAdmin.isProportionalSleepEnabled()); admin.sendSystemMessage(Component.literal("§eSommeil Proportionnel " + (DashboardAdmin.isProportionalSleepEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_TREE_CAPITATOR"     -> { DashboardAdmin.setTreeCapitatorEnabled(!DashboardAdmin.isTreeCapitatorEnabled()); admin.sendSystemMessage(Component.literal("§eBûcheron Intelligent " + (DashboardAdmin.isTreeCapitatorEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_FAST_LEAF_DECAY"    -> { DashboardAdmin.setFastLeafDecayEnabled(!DashboardAdmin.isFastLeafDecayEnabled()); admin.sendSystemMessage(Component.literal("§eFast Leaf Decay " + (DashboardAdmin.isFastLeafDecayEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_DOUBLE_DOOR"        -> { DashboardAdmin.setDoubleDoorEnabled(!DashboardAdmin.isDoubleDoorEnabled()); admin.sendSystemMessage(Component.literal("§eDouble Door " + (DashboardAdmin.isDoubleDoorEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_RIGHT_CLICK_HARVEST"-> { DashboardAdmin.setRightClickHarvestEnabled(!DashboardAdmin.isRightClickHarvestEnabled()); admin.sendSystemMessage(Component.literal("§eRécolte clic droit " + (DashboardAdmin.isRightClickHarvestEnabled() ? "§aactivée" : "§cdésactivée") + "§e.")); ServerConfig.save(); }
            case "TOGGLE_DISPENSER_HARVEST"  -> { DashboardAdmin.setDispenserHarvestEnabled(!DashboardAdmin.isDispenserHarvestEnabled()); admin.sendSystemMessage(Component.literal("§eDistributeur récolte " + (DashboardAdmin.isDispenserHarvestEnabled() ? "§aactivé" : "§cdésactivé") + "§e.")); ServerConfig.save(); }
            case "SET_AFK_DELAY" -> { try { int mins = Integer.parseInt(payload.value()); DashboardAdmin.setAfkDelayMinutes(mins); ServerConfig.save(); admin.sendSystemMessage(Component.literal("§eDélai AFK réglé à §f" + mins + "§e min.")); } catch (NumberFormatException ignored) {} }

            // ─── Rôles de modération (Étape 1 : gestion ; OP requis, déjà garanti ci-dessus) ───
            case "ROLE_CREATE" -> {
                if (RoleManager.createRole(payload.value())) { RolePersistence.save(); admin.sendSystemMessage(Component.literal("§aRôle §f" + RoleManager.sanitizeName(payload.value()) + " §acréé.")); }
                else admin.sendSystemMessage(Component.literal("§cImpossible de créer ce rôle (nom invalide, doublon ou limite atteinte)."));
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_DELETE" -> {
                if (RoleManager.deleteRole(payload.value())) { RolePersistence.save(); admin.sendSystemMessage(Component.literal("§cRôle §f" + payload.value() + " §csupprimé.")); }
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_RENAME" -> {
                if (RoleManager.renameRole(payload.target(), payload.value())) { RolePersistence.save(); admin.sendSystemMessage(Component.literal("§aRôle renommé en §f" + RoleManager.sanitizeName(payload.value()) + "§a.")); }
                else admin.sendSystemMessage(Component.literal("§cRenommage impossible (nom invalide ou déjà utilisé)."));
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_TOGGLE_PERM" -> {
                if (RoleManager.togglePerm(payload.target(), payload.value())) RolePersistence.save();
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_ASSIGN" -> {
                java.util.UUID u = RoleManager.resolveUuid(payload.value(), admin.getServer());
                if (u != null && RoleManager.assignPlayer(payload.target(), u)) { RolePersistence.save(); admin.sendSystemMessage(Component.literal("§a" + payload.value() + " §aassigné au rôle §f" + payload.target() + "§a.")); }
                else admin.sendSystemMessage(Component.literal("§cJoueur introuvable ou rôle invalide."));
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_UNASSIGN" -> {
                java.util.UUID u = RoleManager.resolveUuid(payload.value(), admin.getServer());
                if (u != null && RoleManager.unassignPlayer(payload.target(), u)) { RolePersistence.save(); admin.sendSystemMessage(Component.literal("§e" + payload.value() + " §eretiré du rôle §f" + payload.target() + "§e.")); }
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
        }
    }

    // WarpManager.teleportToWarp(...) déplacé vers WarpManager.

    // ─── Player action handler ────────────────────────────────────────────────
    private static void handlePlayerAction(PlayerActionPayload payload, ServerPlayer player) {
        switch (payload.action()) {
            case "WARP_TP" -> {
                if (com.lkdm.dashboardadmin.command.ZoneCommand.isInBuildMode(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("§cImpossible d'utiliser les warps en mode construction."));
                    break;
                }
                if (!DashboardAdmin.checkCooldown(WarpManager.getLastWarpUse(), player.getUUID(), DashboardAdmin.getCooldownWarp(), player, "/warp")) break;
                WarpManager.teleportToWarp(player, payload.value());
            }
            case "HOME_TP" -> {
                if (!DashboardAdmin.checkCooldown(DashboardAdmin.getLastHomeUse(), player.getUUID(), DashboardAdmin.getCooldownHome(), player, "/home")) break;
                net.minecraft.core.BlockPos pos = DashboardAdmin.getPlayerHomes(player.getUUID()).get(payload.value());
                if (pos != null) {
                    DashboardAdmin.getLastPositions().put(player.getUUID(), player.position());
                    String dimId = DashboardAdmin.getPlayerHomesDim(player.getUUID()).getOrDefault(payload.value(), "minecraft:overworld");
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey = net.minecraft.resources.ResourceKey.create(net.minecraft.world.level.Level.OVERWORLD.registryKey(), net.minecraft.resources.ResourceLocation.parse(dimId));
                    ServerLevel targetLevel = player.getServer().getLevel(dimKey);
                    if (targetLevel == null) targetLevel = (ServerLevel) player.level();
                    player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal("§aTéléporté au home '" + payload.value() + "'."));
                }
            }
            case "HOME_DELETE" -> { if (DashboardAdmin.getPlayerHomes(player.getUUID()).remove(payload.value()) != null) { DashboardAdmin.getPlayerHomesDim(player.getUUID()).remove(payload.value()); player.sendSystemMessage(Component.literal("§cHome '" + payload.value() + "' supprimé.")); } }
            case "LOCK_DELETE" -> {
                String[] parts = payload.value().split(",");
                if (parts.length == 3) try {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    if (DashboardAdmin.isLocked(pos) && DashboardAdmin.getOwner(pos).equals(player.getUUID())) { DashboardAdmin.getAllLockedBlocks().remove(pos); player.sendSystemMessage(Component.literal("§aBloc déverrouillé.")); }
                } catch (NumberFormatException ignored) {}
            }
            case "UNTRUST" -> {
                try { java.util.UUID targetUUID = java.util.UUID.fromString(payload.value()); DashboardAdmin.getTrusted(player.getUUID()).remove(targetUUID); String name = DashboardAdmin.getPlayerNameCache().getOrDefault(targetUUID, "joueur"); player.sendSystemMessage(Component.literal("§c" + name + " n'a plus accès à vos blocs verrouillés.")); } catch (IllegalArgumentException ignored) {}
            }
            case "REFRESH_STATS" -> {
                int pt = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
                long ts = pt / 20L; long rh = ts / 3600, rm = (ts % 3600) / 60;
                int rd = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
                int rk = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS));
                int rm2 = DashboardAdmin.getAllHostileMobKills().getOrDefault(player.getUUID(), 0);
                long rb = (player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.WALK_ONE_CM)) + player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.SPRINT_ONE_CM))) / 100L;
                String stats = rh + "|" + rm + "|" + rd + "|" + rk + "|" + rm2 + "|" + rb + "|" + player.totalExperience;
                PlayerSettings rs = DashboardAdmin.getPlayerSettings(player.getUUID());
                PacketDistributor.sendToPlayer(player, new OpenSettingsPayload(rs.allowPrivateMessages, rs.allowTpaRequests, rs.allowTrades, rs.showChatNotifications, rs.showConnectionAlerts, "", "", "", "", stats, com.lkdm.dashboardadmin.command.ZoneCommand.getBuildInfoFor(player), WarpManager.getWarpsSerialized()));
            }
        }
    }

    // ─── Deal action handler ──────────────────────────────────────────────────
    private static void handleDealAction(DealActionPayload payload, ServerPlayer player) {
        java.util.UUID uid = player.getUUID();
        DealManager.DealSession session = DealManager.activeSessions.get(uid);
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
                DealManager.broadcastDealUpdate(session, player.getServer());
            }
            case "REMOVE_ITEM" -> {
                if (session.isAccepted(uid)) return;
                int tradeSlot = payload.slot();
                NonNullList<ItemStack> offer = session.myOffer(uid);
                if (tradeSlot < 0 || tradeSlot >= offer.size()) return;
                ItemStack stack = offer.get(tradeSlot);
                if (!stack.isEmpty()) { player.getInventory().add(stack.copy()); offer.set(tradeSlot, ItemStack.EMPTY); session.resetAccepted(); DealManager.broadcastDealUpdate(session, player.getServer()); }
            }
            case "ACCEPT" -> { session.setAccepted(uid, true); DealManager.broadcastDealUpdate(session, player.getServer()); if (session.bothAccepted()) DealManager.completeDeal(session, player.getServer()); }
            case "CANCEL"  -> { String pName2 = DashboardAdmin.getPlayerNameCache().getOrDefault(session.partner(uid), "l'autre joueur"); DealManager.cancelDeal(session, player.getServer(), "Vous avez annulé l'échange avec §e" + pName2 + "§c. Items restitués.", "§e" + player.getName().getString() + " §ca annulé l'échange. Vos items ont été restitués."); }
        }
    }
}
