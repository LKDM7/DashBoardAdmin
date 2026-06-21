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

        reg.playToServer(AutoEatPayload.TYPE, AutoEatPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> handleAutoEat(payload, (ServerPlayer) ctx.player())));
    }

    // ─── Auto-manger : consommation instantanée côté serveur (sans animation NI son) ──
    private static void handleAutoEat(AutoEatPayload payload, ServerPlayer player) {
        if (player == null) return;
        int slot = payload.slot();
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        if (slot < 0 || slot >= inv.getContainerSize()) return;
        ItemStack stack = inv.getItem(slot);
        if (stack.isEmpty()) return;
        net.minecraft.world.food.FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food == null) return;
        if (!player.canEat(food.canAlwaysEat())) return; // anti-abus : pas si rassasié

        net.minecraft.world.item.Item item = stack.getItem();

        // 1) faim + saturation
        player.getFoodData().eat(food);

        // 2) effets de l'aliment via le composant Consumable (sans son ni animation)
        net.minecraft.world.item.component.Consumable consumable =
            stack.get(net.minecraft.core.component.DataComponents.CONSUMABLE);
        if (consumable != null)
            for (net.minecraft.world.item.consume_effects.ConsumeEffect ce : consumable.onConsumeEffects())
                ce.apply(player.level(), stack, player);
        // 2b) effets stockés (soupe suspecte)
        net.minecraft.world.item.component.SuspiciousStewEffects stew =
            stack.get(net.minecraft.core.component.DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stew != null)
            for (net.minecraft.world.item.component.SuspiciousStewEffects.Entry en : stew.effects())
                player.addEffect(en.createEffectInstance());

        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(item));

        // 3) décrément + contenant éventuel (bol, bouteille) — pas de son, pas d'animation
        if (!player.getAbilities().instabuild) stack.shrink(1);
        ItemStack remainder = containerRemainder(item);
        if (!remainder.isEmpty()) {
            if (stack.isEmpty()) inv.setItem(slot, remainder);
            else if (!inv.add(remainder)) player.drop(remainder, false);
        }
    }

    /** Contenant rendu après consommation (sinon vide). */
    private static ItemStack containerRemainder(net.minecraft.world.item.Item item) {
        if (item == net.minecraft.world.item.Items.MUSHROOM_STEW
            || item == net.minecraft.world.item.Items.RABBIT_STEW
            || item == net.minecraft.world.item.Items.BEETROOT_SOUP
            || item == net.minecraft.world.item.Items.SUSPICIOUS_STEW)
            return new ItemStack(net.minecraft.world.item.Items.BOWL);
        if (item == net.minecraft.world.item.Items.HONEY_BOTTLE)
            return new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE);
        return ItemStack.EMPTY;
    }

    // ─── Admin action handler ─────────────────────────────────────────────────
    private static void handleAdminAction(AdminActionPayload payload, ServerPlayer admin) {
        String action = payload.action();
        if (!RoleManager.can(admin, RoleManager.permForAction(action))) {
            admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cVous n'avez pas la permission pour cette action.", "§cYou don't have permission for this action.")));
            return;
        }
        if (DashboardAdmin.isAuditable(action))
            DashboardAdmin.addAudit(admin.getName().getString(), action, payload.target(), payload.value());
        ServerPlayer target = admin.getServer().getPlayerList().getPlayerByName(payload.target());

        switch (action) {
            case "TOGGLE_PVP"        -> { DashboardAdmin.setPvpEnabled(!DashboardAdmin.isPvpEnabled()); ServerConfig.save(); boolean pvpOn = DashboardAdmin.isPvpEnabled(); SrvLang.each(admin.getServer(), "§ePvP : " + (pvpOn ? "§aactivé" : "§cdésactivé") + "§e.", "§ePvP: " + (pvpOn ? "§aenabled" : "§cdisabled") + "§e."); }
            case "SET_DAY"           -> ((ServerLevel) admin.level()).setDayTime(6000);
            case "SET_MORNING"       -> ((ServerLevel) admin.level()).setDayTime(0);
            case "SET_EVENING"       -> ((ServerLevel) admin.level()).setDayTime(12000);
            case "SET_NIGHT"         -> ((ServerLevel) admin.level()).setDayTime(18000);
            case "SET_WEATHER_CLEAR" -> ((ServerLevel) admin.level()).setWeatherParameters(6000, 0, false, false);
            case "SET_WEATHER_RAIN"  -> ((ServerLevel) admin.level()).setWeatherParameters(0, 6000, true, false);
            case "SET_WEATHER_THUNDER" -> ((ServerLevel) admin.level()).setWeatherParameters(0, 6000, true, true);
            case "TOGGLE_WEATHER_CYCLE" -> { DashboardAdmin.setWeatherCycleEnabled(!DashboardAdmin.isWeatherCycleEnabled()); admin.getServer().getCommands().performPrefixedCommand(admin.getServer().createCommandSourceStack(), "gamerule doWeatherCycle " + DashboardAdmin.isWeatherCycleEnabled()); ServerConfig.save(); }
            case "TOGGLE_DAYLIGHT_CYCLE" -> { DashboardAdmin.setDaylightCycleEnabled(!DashboardAdmin.isDaylightCycleEnabled()); admin.getServer().getCommands().performPrefixedCommand(admin.getServer().createCommandSourceStack(), "gamerule doDaylightCycle " + DashboardAdmin.isDaylightCycleEnabled()); ServerConfig.save(); }
            case "TOGGLE_CROP_TRAMPLE"  -> { DashboardAdmin.setCropTrampleEnabled(!DashboardAdmin.isCropTrampleEnabled()); ServerConfig.save(); }
            case "CLEAR_LAG"   -> { SrvLang.each(admin.getServer(), "§eClear lag dans 30 secondes !", "§eClear lag in 30 seconds!"); DashboardAdmin.clearLagTicks = 30 * 20; }
            case "SET_SPAWN"   -> { admin.level().getServer().getCommands().performPrefixedCommand(admin.createCommandSourceStack(), "/setworldspawn ~ ~ ~"); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aSpawn défini à votre position.", "§aSpawn set to your position."))); }
            case "REMOVE_MOBS" -> { SrvLang.each(admin.getServer(), "§cSuppression des mobs dans 30 secondes !", "§cRemoving mobs in 30 seconds!"); DashboardAdmin.removeMobsTicks = 30 * 20; }
            case "LOCK_CHAT"   -> { DashboardAdmin.setChatLocked(!DashboardAdmin.isChatLocked()); boolean chatLocked = DashboardAdmin.isChatLocked(); SrvLang.each(admin.getServer(), "§cLe chat a été " + (chatLocked ? "bloqué" : "débloqué") + " !", "§cChat has been " + (chatLocked ? "locked" : "unlocked") + "!"); ServerConfig.save(); }
            case "VANISH" -> {
                // L'entité est masquée aux autres joueurs par TrackedEntityVanishMixin (suivi
                // d'entités), SANS toucher au suivi de chunks → l'admin n'est plus gelé et ses
                // chunks chargent normalement. Ici on ne gère que le set + la tab-list.
                net.minecraft.server.MinecraftServer vanishSrv = admin.getServer();
                if (DashboardAdmin.vanishedPlayers.contains(admin.getUUID())) {
                    DashboardAdmin.vanishedPlayers.remove(admin.getUUID());
                    // Re-add to tab lists (le tracker re-spawn l'entité chez les observateurs au tick suivant)
                    net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket showInfo =
                        net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
                            .createPlayerInitializing(java.util.List.of(admin));
                    for (ServerPlayer other : vanishSrv.getPlayerList().getPlayers())
                        if (!other.getUUID().equals(admin.getUUID())) other.connection.send(showInfo);
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eVanish : OFF", "§eVanish: OFF")));
                } else {
                    DashboardAdmin.vanishedPlayers.add(admin.getUUID());
                    // Remove from tab lists (l'entité est masquée par le mixin au tick de suivi suivant)
                    net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket removeInfo =
                        new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(java.util.List.of(admin.getUUID()));
                    for (ServerPlayer other : vanishSrv.getPlayerList().getPlayers())
                        if (!other.getUUID().equals(admin.getUUID())) other.connection.send(removeInfo);
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eVanish : ON", "§eVanish: ON")));
                }
            }
            case "MUTE" -> {
                if (target != null) {
                    if (DashboardAdmin.isMuted(target.getUUID())) {
                        DashboardAdmin.unmute(target.getUUID()); ModerationPersistence.save();
                        DashboardAdmin.addLog(target.getUUID(), "Unmuted par " + admin.getName().getString());
                        admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§e" + target.getName().getString() + " n'est plus muet.", "§e" + target.getName().getString() + " is no longer muted.")));
                        target.sendSystemMessage(Component.literal(SrvLang.t(target, "§eVous n'êtes plus muet.", "§eYou are no longer muted.")));
                    } else {
                        long secs = DashboardAdmin.parseDuration(payload.value()); // value vide = permanent
                        String durLabel = secs <= 0 ? "" : DashboardAdmin.formatDurationShort(secs);
                        String durSuffix = secs <= 0 ? "" : " §7(" + durLabel + ")";
                        DashboardAdmin.muteFor(target.getUUID(), secs); ModerationPersistence.save();
                        DiscordWebhook.sendSanction(DashboardAdmin.getWebhookSanctions(), admin.getName().getString(), target.getName().getString(), "MUTE", secs <= 0 ? "permanent" : durLabel);
                        DashboardAdmin.addLog(target.getUUID(), "Muted " + (secs <= 0 ? "définitivement" : "pour " + durLabel) + " par " + admin.getName().getString());
                        DashboardAdmin.addSanction("MUTE", target.getName().getString(), admin.getName().getString(), durLabel);
                        admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§e" + target.getName().getString() + " est maintenant muet" + durSuffix + "§e.", "§e" + target.getName().getString() + " is now muted" + durSuffix + "§e.")));
                        target.sendSystemMessage(Component.literal(SrvLang.t(target, "§cVous avez été rendu muet par un admin" + durSuffix + "§c.", "§cYou have been muted by an admin" + durSuffix + "§c.")));
                    }
                }
            }
            case "HEAL"    -> { if (target != null) { target.setHealth(target.getMaxHealth()); target.getFoodData().eat(20, 1.0f); DashboardAdmin.addLog(target.getUUID(), "Heal par " + admin.getName().getString()); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aJoueur soigné.", "§aPlayer healed."))); } }
            case "ANNOUNCE" -> {
                String tgt = payload.target();
                String content = payload.value();
                // Le préfixe est résolu par destinataire ; le contenu (saisi par l'admin) reste tel quel.
                java.util.function.Function<ServerPlayer, Component> mkAnnounce = pl ->
                    Component.literal(SrvLang.t(pl, "§6§l[ANNONCE] §r", "§6§l[ANNOUNCEMENT] §r") + content);
                if (tgt.isEmpty()) admin.getServer().getPlayerList().getPlayers().forEach(pl -> pl.sendSystemMessage(mkAnnounce.apply(pl)));
                else if (tgt.startsWith("GROUP:")) {
                    String leaderName = tgt.substring(6);
                    ServerPlayer groupLeader = admin.getServer().getPlayerList().getPlayerByName(leaderName);
                    if (groupLeader != null) GroupManager.getMembers(groupLeader.getUUID()).forEach(uuid -> { ServerPlayer gp = admin.getServer().getPlayerList().getPlayer(uuid); if (gp != null) gp.sendSystemMessage(mkAnnounce.apply(gp)); });
                } else { ServerPlayer tgtPlayer = admin.getServer().getPlayerList().getPlayerByName(tgt); if (tgtPlayer != null) tgtPlayer.sendSystemMessage(mkAnnounce.apply(tgtPlayer)); }
            }
            case "KICK"        -> { if (target != null) { DashboardAdmin.addLog(target.getUUID(), "Kicked par " + admin.getName().getString()); DashboardAdmin.addSanction("KICK", target.getName().getString(), admin.getName().getString(), ""); DiscordWebhook.sendSanction(DashboardAdmin.getWebhookSanctions(), admin.getName().getString(), target.getName().getString(), "KICK", ""); target.connection.disconnect(Component.literal(SrvLang.t(target, "Expulsé par un admin.", "Kicked by an admin."))); } }
            case "TELEPORT_TO" -> { if (target != null) { DashboardAdmin.addLog(target.getUUID(), "TP vers par " + admin.getName().getString()); admin.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), admin.getYRot(), admin.getXRot(), true); } }
            case "OPEN_INV"    -> { if (target != null) admin.openMenu(new SimpleMenuProvider((id, inv, p) -> new EditablePlayerInventoryMenu(id, inv, target.getInventory()), Component.literal("Inv: " + target.getName().getString()))); else OfflinePlayerInventory.openInventory(admin, payload.target()); }
            case "ENDERCHEST"  -> { if (target != null) admin.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, target.getEnderChestInventory(), 3), Component.literal("Ender: " + target.getName().getString()))); else OfflinePlayerInventory.openEnderchest(admin, payload.target()); }
            case "BRING"       -> { if (target != null) { DashboardAdmin.addLog(target.getUUID(), "Bring par " + admin.getName().getString()); target.teleportTo((ServerLevel) admin.level(), admin.getX(), admin.getY(), admin.getZ(), Set.of(), target.getYRot(), target.getXRot(), true); } }
            case "FREEZE" -> {
                if (target != null) {
                    boolean frozen = !DashboardAdmin.frozenPlayers.getOrDefault(target.getUUID(), false);
                    DashboardAdmin.frozenPlayers.put(target.getUUID(), frozen);
                    if (frozen) { DashboardAdmin.frozenPositions.put(target.getUUID(), target.position()); DashboardAdmin.addLog(target.getUUID(), "Frozen par " + admin.getName().getString()); target.sendSystemMessage(Component.literal(SrvLang.t(target, "§bVous avez été gelé par un admin.", "§bYou have been frozen by an admin."))); }
                    else { DashboardAdmin.frozenPositions.remove(target.getUUID()); DashboardAdmin.addLog(target.getUUID(), "Unfrozen par " + admin.getName().getString()); target.sendSystemMessage(Component.literal(SrvLang.t(target, "§aVous n'êtes plus gelé.", "§aYou are no longer frozen."))); }
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§bJoueur " + (frozen ? "gelé" : "dégelé") + ".", "§bPlayer " + (frozen ? "frozen" : "unfrozen") + ".")));
                }
            }
            case "ACCEPT_REPORT"      -> ReportManager.accept(admin, payload.target());
            case "CLOSE_REPORT"       -> ReportManager.close(admin, payload.target());
            case "REFUSE_REPORT"      -> ReportManager.refuse(admin, payload.target());
            case "FETCH_REPORT_IMAGE" -> ReportManager.fetchImage(admin, payload.target());
            case "SET_MAX_HOMES"  -> { try { DashboardAdmin.setMaxHomes(Integer.parseInt(payload.value())); ServerConfig.save(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aMax homes fixé à §e" + DashboardAdmin.getMaxHomes() + "§a.", "§aMax homes set to §e" + DashboardAdmin.getMaxHomes() + "§a."))); } catch (NumberFormatException ignored) {} }
            case "SET_WEBHOOKS"   -> { String[] wh = payload.value().split("\t", 2); DashboardAdmin.setWebhookReports(payload.target()); DashboardAdmin.setWebhookSanctions(wh[0]); DashboardAdmin.setWebhookAudit(wh.length > 1 ? wh[1] : ""); ServerConfig.save(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aWebhooks Discord mis à jour.", "§aDiscord webhooks updated."))); }
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
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aNote ajoutée pour §f" + payload.target() + "§a.", "§aNote added for §f" + payload.target() + "§a.")));
                    AdminCommand.sendAdminGui(admin, admin.getServer());
                } else admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cNote vide ou limite atteinte (15 max).", "§cEmpty note or limit reached (15 max).")));
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
                if (wName.isEmpty()) { admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cNom de warp invalide.", "§cInvalid warp name."))); return; }
                if (WarpManager.getWarps().containsKey(wName)) { admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cLe warp §e" + wName + " §cexiste déjà.", "§cWarp §e" + wName + " §calready exists."))); return; }
                WarpManager.getWarps().put(wName, admin.blockPosition());
                WarpManager.getWarpsDim().put(wName, admin.level().dimension().location().toString());
                ServerConfig.save();
                admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aWarp §e" + wName + " §acréé à votre position.", "§aWarp §e" + wName + " §acreated at your position.")));
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "WARP_DELETE" -> {
                if (WarpManager.getWarps().remove(payload.value()) != null) {
                    WarpManager.getWarpsDim().remove(payload.value());
                    ServerConfig.save();
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cWarp §e" + payload.value() + " §csupprimé.", "§cWarp §e" + payload.value() + " §cdeleted.")));
                }
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "WARP_TP" -> WarpManager.teleportToWarp(admin, payload.value());
            case "SCHEDULE_RESTART" -> {
                try {
                    int mins = Math.max(1, Math.min(120, Integer.parseInt(payload.value())));
                    DashboardAdmin.scheduleRestart(mins);
                    SrvLang.each(admin.getServer(),
                        "§c⚠ §lRedémarrage du serveur programmé dans §e§l" + mins + " minute" + (mins > 1 ? "s" : "") + "§c§l.",
                        "§c⚠ §lServer restart scheduled in §e§l" + mins + " minute" + (mins > 1 ? "s" : "") + "§c§l.");
                } catch (NumberFormatException ignored) {}
            }
            case "CANCEL_RESTART" -> {
                if (DashboardAdmin.isRestartScheduled()) {
                    DashboardAdmin.cancelRestart();
                    SrvLang.each(admin.getServer(), "§a✔ Redémarrage du serveur annulé.", "§a✔ Server restart cancelled.");
                } else {
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eAucun redémarrage programmé.", "§eNo restart scheduled.")));
                }
            }
            case "TOGGLE_MAIL_SPY" -> {
                DashboardAdmin.setMailSpyEnabled(!DashboardAdmin.isMailSpyEnabled());
                ServerConfig.save();
                admin.sendSystemMessage(Component.literal(SrvLang.t(admin,
                    "§eSpy des MP : " + (DashboardAdmin.isMailSpyEnabled() ? "§aactivé" : "§cdésactivé") + "§e.",
                    "§ePM spy: " + (DashboardAdmin.isMailSpyEnabled() ? "§aenabled" : "§cdisabled") + "§e.")));
            }
            case "SET_MOTD" -> {
                DashboardAdmin.setMotd(payload.value());
                ServerConfig.save();
                admin.sendSystemMessage(Component.literal(DashboardAdmin.getMotd().isEmpty()
                    ? SrvLang.t(admin, "§eMOTD supprimé.", "§eMOTD removed.")
                    : SrvLang.t(admin, "§aMOTD mis à jour : §7" + DashboardAdmin.getMotd(), "§aMOTD updated: §7" + DashboardAdmin.getMotd())));
            }
            case "BAN" -> {
                String[] banParts = payload.value().split("\t", 2);
                long banSeconds = 0; String reason = "Banni par un admin."; boolean customReason = false;
                try { banSeconds = Long.parseLong(banParts[0]); } catch (NumberFormatException ignored) {}
                if (banParts.length > 1 && !banParts[1].isEmpty()) { reason = banParts[1]; customReason = true; }
                java.util.Date expires = banSeconds > 0 ? new java.util.Date(System.currentTimeMillis() + banSeconds * 1000L) : null;
                String sanctionReason = (banSeconds > 0 ? "(" + DashboardAdmin.formatDurationShort(banSeconds) + ") " : "") + reason;

                // Cible en ligne → profil direct ; sinon on résout le joueur hors ligne via le cache de noms.
                com.mojang.authlib.GameProfile profile;
                java.util.UUID targetUuid;
                String targetName;
                if (target != null) {
                    profile    = target.getGameProfile();
                    targetUuid = target.getUUID();
                    targetName = target.getName().getString();
                } else {
                    targetUuid = RoleManager.resolveUuid(payload.target(), admin.getServer());
                    if (targetUuid == null) {
                        admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cJoueur introuvable.", "§cPlayer not found.")));
                        return;
                    }
                    targetName = DashboardAdmin.getPlayerNameCache().getOrDefault(targetUuid, payload.target());
                    profile    = new com.mojang.authlib.GameProfile(targetUuid, targetName);
                }

                DashboardAdmin.addLog(targetUuid, "Banned par " + admin.getName().getString() + " (" + sanctionReason + ")");
                DashboardAdmin.addSanction("BAN", targetName, admin.getName().getString(), sanctionReason);
                DiscordWebhook.sendSanction(DashboardAdmin.getWebhookSanctions(), admin.getName().getString(), targetName, "BAN", sanctionReason);
                admin.getServer().getPlayerList().getBans().add(new net.minecraft.server.players.UserBanListEntry(profile, null, "admin", expires, reason));
                if (target != null)
                    target.connection.disconnect(Component.literal(customReason ? reason : SrvLang.t(target, "Banni par un admin.", "Banned by an admin.")));
                admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§a" + targetName + " a été banni.", "§a" + targetName + " has been banned.")));
            }
            case "UNBAN" -> { String name = payload.target(); DashboardAdmin.addSanction("UNBAN", name, admin.getName().getString(), ""); DashboardAdmin.getPlayerNameCache().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(name)).map(java.util.Map.Entry::getKey).findFirst().ifPresent(uuid -> DashboardAdmin.addLog(uuid, "Débanni par " + admin.getName().getString())); admin.getServer().getCommands().performPrefixedCommand(admin.getServer().createCommandSourceStack(), "pardon " + name); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§a" + name + " a été débanni.", "§a" + name + " has been unbanned."))); }
            case "KEEP_INVENTORY" -> { if (target != null) { PlayerSettings ks = DashboardAdmin.getPlayerSettings(target.getUUID()); ks.keepInventory = !ks.keepInventory; admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aKeepInventory " + (ks.keepInventory ? "§aactivé" : "§cdésactivé") + " §apour §e" + target.getName().getString(), "§aKeepInventory " + (ks.keepInventory ? "§aenabled" : "§cdisabled") + " §afor §e" + target.getName().getString()))); target.sendSystemMessage(Component.literal(ks.keepInventory ? SrvLang.t(target, "§aVotre inventaire sera conservé à la mort.", "§aYour inventory will be kept on death.") : SrvLang.t(target, "§cVotre inventaire ne sera plus conservé à la mort.", "§cYour inventory will no longer be kept on death."))); } }
            case "OPEN_ZONES"    -> com.lkdm.dashboardadmin.command.ZoneCommand.sendZoneScreen(admin, admin.getServer());
            case "GET_SANCTIONS" -> PacketDistributor.sendToPlayer(admin, new OpenSanctionsPayload(DashboardAdmin.getSanctionsSerialized()));
            case "GET_AUDIT"     -> PacketDistributor.sendToPlayer(admin, new OpenAuditPayload(DashboardAdmin.getAuditSerialized()));
            case "EXPORT_AUDIT" -> {
                String url = DashboardAdmin.getWebhookAudit();
                if (url == null || url.isBlank()) { admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cAucun webhook Audit configuré (onglet Features).", "§cNo Audit webhook configured (Features tab)."))); return; }
                StringBuilder sb = new StringBuilder();
                for (String[] e : DashboardAdmin.getAuditLog())
                    sb.append(e[0]).append("  ").append(e[1]).append(" -> ").append(e[2])
                      .append("—".equals(e[3]) ? "" : " " + e[3])
                      .append("—".equals(e[4]) ? "" : " (" + e[4] + ")").append('\n');
                DiscordWebhook.sendAuditExport(url, sb.length() == 0 ? "(journal vide)" : sb.toString());
                admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§a✔ Journal d'audit (" + DashboardAdmin.getAuditLog().size() + " entrées) exporté vers le webhook.", "§a✔ Audit log (" + DashboardAdmin.getAuditLog().size() + " entries) exported to the webhook.")));
            }
            case "GAMEMODE" -> { if (target != null) { net.minecraft.world.level.GameType next = switch (target.gameMode.getGameModeForPlayer()) { case SURVIVAL -> net.minecraft.world.level.GameType.CREATIVE; case CREATIVE -> net.minecraft.world.level.GameType.SPECTATOR; default -> net.minecraft.world.level.GameType.SURVIVAL; }; target.setGameMode(next); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "Mode de jeu changé en: ", "Game mode changed to: ") + next.getName())); } }
            case "SCHEDULE_ADD"  -> { String[] parts = payload.value().split("\t", 2); if (parts.length == 2) { try { int minutes = Integer.parseInt(parts[1].trim()); DashboardAdmin.addScheduledBroadcast(parts[0], minutes * 1200); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aBroadcast programmé ajouté.", "§aScheduled broadcast added."))); } catch (NumberFormatException ignored) {} } }
            case "SCHEDULE_REMOVE" -> { try { int idx = Integer.parseInt(payload.value()); if (DashboardAdmin.removeScheduledBroadcast(idx)) { ServerConfig.save(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cBroadcast supprimé.", "§cBroadcast removed."))); } } catch (NumberFormatException ignored) {} }
            case "SET_COOLDOWNS" -> { String[] parts = payload.value().split("\\|"); if (parts.length >= 3) { try { DashboardAdmin.setCooldownHome(Integer.parseInt(parts[0])); DashboardAdmin.setCooldownBack(Integer.parseInt(parts[1])); DashboardAdmin.setCooldownTpa(Integer.parseInt(parts[2])); if (parts.length >= 4) DashboardAdmin.setCooldownWarp(Integer.parseInt(parts[3])); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aCooldowns mis à jour.", "§aCooldowns updated."))); } catch (NumberFormatException ignored) {} } }
            case "TOGGLE_AFK_AUTO"           -> { DashboardAdmin.setAfkAutoEnabled(!DashboardAdmin.isAfkAutoEnabled()); boolean on = DashboardAdmin.isAfkAutoEnabled(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eAFK Automatique " + (on ? "§aactivé" : "§cdésactivé") + "§e.", "§eAuto AFK " + (on ? "§aenabled" : "§cdisabled") + "§e."))); ServerConfig.save(); }
            case "TOGGLE_PROPORTIONAL_SLEEP" -> { DashboardAdmin.setProportionalSleepEnabled(!DashboardAdmin.isProportionalSleepEnabled()); boolean on = DashboardAdmin.isProportionalSleepEnabled(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eSommeil Proportionnel " + (on ? "§aactivé" : "§cdésactivé") + "§e.", "§eProportional Sleep " + (on ? "§aenabled" : "§cdisabled") + "§e."))); ServerConfig.save(); }
            case "TOGGLE_TREE_CAPITATOR"     -> { DashboardAdmin.setTreeCapitatorEnabled(!DashboardAdmin.isTreeCapitatorEnabled()); boolean on = DashboardAdmin.isTreeCapitatorEnabled(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eBûcheron Intelligent " + (on ? "§aactivé" : "§cdésactivé") + "§e.", "§eTree Capitator " + (on ? "§aenabled" : "§cdisabled") + "§e."))); ServerConfig.save(); }
            case "TOGGLE_ANTISPAM_BYPASS"    -> { DashboardAdmin.setAntiSpamBypassEnabled(!DashboardAdmin.isAntiSpamBypassEnabled()); boolean on = DashboardAdmin.isAntiSpamBypassEnabled(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eContournement anti-spam (OP + rôles) " + (on ? "§aactivé" : "§cdésactivé") + "§e.", "§eAnti-spam bypass (OP + roles) " + (on ? "§aenabled" : "§cdisabled") + "§e."))); ServerConfig.save(); }
            case "TOGGLE_FAST_LEAF_DECAY"    -> { DashboardAdmin.setFastLeafDecayEnabled(!DashboardAdmin.isFastLeafDecayEnabled()); boolean on = DashboardAdmin.isFastLeafDecayEnabled(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eFast Leaf Decay " + (on ? "§aactivé" : "§cdésactivé") + "§e.", "§eFast Leaf Decay " + (on ? "§aenabled" : "§cdisabled") + "§e."))); ServerConfig.save(); }
            case "TOGGLE_DOUBLE_DOOR"        -> { DashboardAdmin.setDoubleDoorEnabled(!DashboardAdmin.isDoubleDoorEnabled()); boolean on = DashboardAdmin.isDoubleDoorEnabled(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eDouble Door " + (on ? "§aactivé" : "§cdésactivé") + "§e.", "§eDouble Door " + (on ? "§aenabled" : "§cdisabled") + "§e."))); ServerConfig.save(); }
            case "TOGGLE_RIGHT_CLICK_HARVEST"-> { DashboardAdmin.setRightClickHarvestEnabled(!DashboardAdmin.isRightClickHarvestEnabled()); boolean on = DashboardAdmin.isRightClickHarvestEnabled(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eRécolte clic droit " + (on ? "§aactivée" : "§cdésactivée") + "§e.", "§eRight-click Harvest " + (on ? "§aenabled" : "§cdisabled") + "§e."))); ServerConfig.save(); }
            case "TOGGLE_DISPENSER_HARVEST"  -> { DashboardAdmin.setDispenserHarvestEnabled(!DashboardAdmin.isDispenserHarvestEnabled()); boolean on = DashboardAdmin.isDispenserHarvestEnabled(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eDistributeur récolte " + (on ? "§aactivé" : "§cdésactivé") + "§e.", "§eDispenser Harvest " + (on ? "§aenabled" : "§cdisabled") + "§e."))); ServerConfig.save(); }
            case "SET_AFK_DELAY" -> { try { int mins = Integer.parseInt(payload.value()); DashboardAdmin.setAfkDelayMinutes(mins); ServerConfig.save(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§eDélai AFK réglé à §f" + mins + "§e min.", "§eAFK delay set to §f" + mins + "§e min."))); } catch (NumberFormatException ignored) {} }

            // ─── Rôles de modération (Étape 1 : gestion ; OP requis, déjà garanti ci-dessus) ───
            case "ROLE_CREATE" -> {
                if (RoleManager.createRole(payload.value())) { RolePersistence.save(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aRôle §f" + RoleManager.sanitizeName(payload.value()) + " §acréé.", "§aRole §f" + RoleManager.sanitizeName(payload.value()) + " §acreated."))); }
                else admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cImpossible de créer ce rôle (nom invalide, doublon ou limite atteinte).", "§cCannot create this role (invalid name, duplicate, or limit reached).")));
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_DELETE" -> {
                if (RoleManager.deleteRole(payload.value())) { RolePersistence.save(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cRôle §f" + payload.value() + " §csupprimé.", "§cRole §f" + payload.value() + " §cdeleted."))); }
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_RENAME" -> {
                if (RoleManager.renameRole(payload.target(), payload.value())) { RolePersistence.save(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aRôle renommé en §f" + RoleManager.sanitizeName(payload.value()) + "§a.", "§aRole renamed to §f" + RoleManager.sanitizeName(payload.value()) + "§a."))); }
                else admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cRenommage impossible (nom invalide ou déjà utilisé).", "§cRename failed (invalid name or already in use).")));
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_TOGGLE_PERM" -> {
                if (RoleManager.togglePerm(payload.target(), payload.value())) RolePersistence.save();
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_ASSIGN" -> {
                java.util.UUID u = RoleManager.resolveUuid(payload.value(), admin.getServer());
                if (u != null && RoleManager.assignPlayer(payload.target(), u)) { RolePersistence.save(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§a" + payload.value() + " §aassigné au rôle §f" + payload.target() + "§a.", "§a" + payload.value() + " §aassigned to role §f" + payload.target() + "§a."))); }
                else admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cJoueur introuvable ou rôle invalide.", "§cPlayer not found or invalid role.")));
                AdminCommand.sendAdminGui(admin, admin.getServer());
            }
            case "ROLE_UNASSIGN" -> {
                java.util.UUID u = RoleManager.resolveUuid(payload.value(), admin.getServer());
                if (u != null && RoleManager.unassignPlayer(payload.target(), u)) { RolePersistence.save(); admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§e" + payload.value() + " §eretiré du rôle §f" + payload.target() + "§e.", "§e" + payload.value() + " §eremoved from role §f" + payload.target() + "§e."))); }
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
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cImpossible d'utiliser les warps en mode construction.", "§cCannot use warps in build mode.")));
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
                    player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aTéléporté au home '" + payload.value() + "'.", "§aTeleported to home '" + payload.value() + "'.")));
                }
            }
            case "HOME_DELETE" -> { if (DashboardAdmin.getPlayerHomes(player.getUUID()).remove(payload.value()) != null) { DashboardAdmin.getPlayerHomesDim(player.getUUID()).remove(payload.value()); player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cHome '" + payload.value() + "' supprimé.", "§cHome '" + payload.value() + "' deleted."))); } }
            case "LOCK_DELETE" -> {
                String[] parts = payload.value().split(",");
                if (parts.length == 3) try {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    if (DashboardAdmin.isLocked(pos) && DashboardAdmin.getOwner(pos).equals(player.getUUID())) { DashboardAdmin.getAllLockedBlocks().remove(pos); player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aBloc déverrouillé.", "§aBlock unlocked."))); }
                } catch (NumberFormatException ignored) {}
            }
            case "UNTRUST" -> {
                try { java.util.UUID targetUUID = java.util.UUID.fromString(payload.value()); DashboardAdmin.getTrusted(player.getUUID()).remove(targetUUID); String name = DashboardAdmin.getPlayerNameCache().getOrDefault(targetUUID, SrvLang.t(player, "joueur", "player")); player.sendSystemMessage(Component.literal(SrvLang.t(player, "§c" + name + " n'a plus accès à vos blocs verrouillés.", "§c" + name + " no longer has access to your locked blocks."))); } catch (IllegalArgumentException ignored) {}
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
                if (emptyIdx < 0) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cVotre zone d'échange est pleine.", "§cYour trade area is full."))); return; }
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
            case "CANCEL"  -> { String pName2 = DashboardAdmin.getPlayerNameCache().getOrDefault(session.partner(uid), "?"); String actorName = player.getName().getString(); DealManager.cancelDeal(session, player.getServer(),
                "Vous avez annulé l'échange avec §e" + pName2 + "§c. Items restitués.",
                "You cancelled the trade with §e" + pName2 + "§c. Items returned.",
                "§e" + actorName + " §ca annulé l'échange. Vos items ont été restitués.",
                "§e" + actorName + " §ccancelled the trade. Your items were returned."); }
        }
    }
}
