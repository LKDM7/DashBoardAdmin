package com.lkdm.dashboardadmin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Demandes de téléportation entre joueurs : /tpa, /tpahere, /tpaccept, /tpdeny + expiration.
 *
 * Extrait de la god-class {@link DashboardAdmin}. Les helpers transverses (cooldowns, settings,
 * cache de noms) restent dans {@link DashboardAdmin} ; le nettoyage à la déconnexion et l'expiration
 * périodique sont dans {@link DashGameEvents} et utilisent les getters publics ci-dessous.
 */
public final class TpaManager {

    private TpaManager() {}

    private static final Map<UUID, UUID> tpaRequests          = new HashMap<>();
    private static final Set<UUID>       tpaHere              = new HashSet<>(); // cibles dont la requête est un /tpahere
    private static final Map<UUID, Long> pendingTpaTimestamps = new HashMap<>();
    private static final Map<UUID, Long> lastTpaUse           = new HashMap<>();

    public static Map<UUID, UUID> getTpaRequests()          { return tpaRequests; }
    public static Set<UUID>       getTpaHere()               { return tpaHere; }
    public static Map<UUID, Long> getPendingTpaTimestamps()  { return pendingTpaTimestamps; }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(Commands.argument("target", EntityArgument.player())
            .executes(context -> {
                ServerPlayer sender = context.getSource().getPlayerOrException();
                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                if (sender == target) return 0;
                if (com.lkdm.dashboardadmin.command.ZoneCommand.isInBuildMode(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/tpa §cen mode construction."));
                    return 0;
                }
                if (!DashboardAdmin.checkCooldown(lastTpaUse, sender.getUUID(), DashboardAdmin.getCooldownTpa(), sender, "/tpa")) return 0;
                if (!DashboardAdmin.getPlayerSettings(target.getUUID()).allowTpaRequests) {
                    sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas les demandes de téléportation."));
                    return 0;
                }
                tpaRequests.put(target.getUUID(), sender.getUUID());
                pendingTpaTimestamps.put(target.getUUID(), System.currentTimeMillis());
                Component msg = Component.literal(sender.getName().getString() + " veut se tp à vous. ")
                    .append(Component.literal("[OUI]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))))
                    .append(Component.literal(" "))
                    .append(Component.literal("[NON]").withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"))));
                target.sendSystemMessage(msg);
                sender.sendSystemMessage(Component.literal("§aRequête envoyée à " + target.getName().getString()));
                return 1;
            })));
        dispatcher.register(Commands.literal("tpahere")
            .then(Commands.argument("target", EntityArgument.player())
            .executes(context -> {
                ServerPlayer sender = context.getSource().getPlayerOrException();
                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                if (sender == target) return 0;
                if (com.lkdm.dashboardadmin.command.ZoneCommand.isInBuildMode(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/tpahere §cen mode construction."));
                    return 0;
                }
                if (!DashboardAdmin.checkCooldown(lastTpaUse, sender.getUUID(), DashboardAdmin.getCooldownTpa(), sender, "/tpahere")) return 0;
                if (!DashboardAdmin.getPlayerSettings(target.getUUID()).allowTpaRequests) {
                    sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas les demandes de téléportation."));
                    return 0;
                }
                tpaRequests.put(target.getUUID(), sender.getUUID());
                tpaHere.add(target.getUUID());
                pendingTpaTimestamps.put(target.getUUID(), System.currentTimeMillis());
                Component msg = Component.literal(sender.getName().getString() + " veut que vous vous tp à lui. ")
                    .append(Component.literal("[OUI]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))))
                    .append(Component.literal(" "))
                    .append(Component.literal("[NON]").withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"))));
                target.sendSystemMessage(msg);
                sender.sendSystemMessage(Component.literal("§aRequête envoyée à " + target.getName().getString()));
                return 1;
            })));
        dispatcher.register(Commands.literal("tpaccept").executes(context -> {
            ServerPlayer target = context.getSource().getPlayerOrException();
            UUID senderUUID = tpaRequests.remove(target.getUUID());
            pendingTpaTimestamps.remove(target.getUUID());
            boolean here = tpaHere.remove(target.getUUID());
            if (senderUUID == null) { target.sendSystemMessage(Component.literal("§cAucune demande en attente.")); return 0; }
            String senderName = DashboardAdmin.getPlayerNameCache().getOrDefault(senderUUID, "?");
            ServerPlayer sender = context.getSource().getServer().getPlayerList().getPlayer(senderUUID);
            if (sender == null) {
                target.sendSystemMessage(Component.literal("§c" + senderName + " n'est plus connecté."));
                return 0;
            }
            if (here) {
                // /tpahere : c'est l'accepteur (target) qui rejoint le demandeur (sender).
                target.teleportTo((ServerLevel) sender.level(), sender.getX(), sender.getY(), sender.getZ(), Set.of(), target.getYRot(), target.getXRot());
            } else {
                sender.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), sender.getYRot(), sender.getXRot());
            }
            target.sendSystemMessage(Component.literal("§a✔ Vous avez accepté la demande de §e" + sender.getName().getString() + "§a."));
            sender.sendSystemMessage(Component.literal("§a✔ §e" + target.getName().getString() + "§a a accepté — téléporté !"));
            return 1;
        }));
        dispatcher.register(Commands.literal("tpdeny").executes(context -> {
            ServerPlayer target = context.getSource().getPlayerOrException();
            pendingTpaTimestamps.remove(target.getUUID());
            tpaHere.remove(target.getUUID());
            UUID senderUUID = tpaRequests.remove(target.getUUID());
            if (senderUUID == null) { target.sendSystemMessage(Component.literal("§cAucune demande en attente.")); return 0; }
            String senderName = DashboardAdmin.getPlayerNameCache().getOrDefault(senderUUID, "?");
            ServerPlayer sender = context.getSource().getServer().getPlayerList().getPlayer(senderUUID);
            target.sendSystemMessage(Component.literal("§c✘ Vous avez refusé la demande de §e" + senderName + "§c."));
            if (sender != null) sender.sendSystemMessage(Component.literal("§c✘ §e" + target.getName().getString() + "§c a refusé votre demande de téléportation."));
            return 1;
        }));
    }
}
