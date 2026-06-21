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
                    sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§cImpossible d'utiliser §6/tpa §cen mode construction.", "§cYou can't use §6/tpa §cwhile in build mode.")));
                    return 0;
                }
                if (!DashboardAdmin.checkCooldown(lastTpaUse, sender.getUUID(), DashboardAdmin.getCooldownTpa(), sender, "/tpa")) return 0;
                if (!DashboardAdmin.getPlayerSettings(target.getUUID()).allowTpaRequests) {
                    sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§c" + target.getName().getString() + " n'accepte pas les demandes de téléportation.", "§c" + target.getName().getString() + " doesn't accept teleport requests.")));
                    return 0;
                }
                tpaRequests.put(target.getUUID(), sender.getUUID());
                pendingTpaTimestamps.put(target.getUUID(), System.currentTimeMillis());
                Component msg = Component.literal(SrvLang.t(target, sender.getName().getString() + " veut se tp à vous. ", sender.getName().getString() + " wants to teleport to you. "))
                    .append(Component.literal(SrvLang.t(target, "[OUI]", "[YES]")).withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))))
                    .append(Component.literal(" "))
                    .append(Component.literal(SrvLang.t(target, "[NON]", "[NO]")).withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"))));
                target.sendSystemMessage(msg);
                sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§aRequête envoyée à " + target.getName().getString(), "§aRequest sent to " + target.getName().getString())));
                return 1;
            })));
        dispatcher.register(Commands.literal("tpahere")
            .then(Commands.argument("target", EntityArgument.player())
            .executes(context -> {
                ServerPlayer sender = context.getSource().getPlayerOrException();
                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                if (sender == target) return 0;
                if (com.lkdm.dashboardadmin.command.ZoneCommand.isInBuildMode(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§cImpossible d'utiliser §6/tpahere §cen mode construction.", "§cYou can't use §6/tpahere §cwhile in build mode.")));
                    return 0;
                }
                if (!DashboardAdmin.checkCooldown(lastTpaUse, sender.getUUID(), DashboardAdmin.getCooldownTpa(), sender, "/tpahere")) return 0;
                if (!DashboardAdmin.getPlayerSettings(target.getUUID()).allowTpaRequests) {
                    sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§c" + target.getName().getString() + " n'accepte pas les demandes de téléportation.", "§c" + target.getName().getString() + " doesn't accept teleport requests.")));
                    return 0;
                }
                tpaRequests.put(target.getUUID(), sender.getUUID());
                tpaHere.add(target.getUUID());
                pendingTpaTimestamps.put(target.getUUID(), System.currentTimeMillis());
                Component msg = Component.literal(SrvLang.t(target, sender.getName().getString() + " veut que vous vous tp à lui. ", sender.getName().getString() + " wants you to teleport to them. "))
                    .append(Component.literal(SrvLang.t(target, "[OUI]", "[YES]")).withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))))
                    .append(Component.literal(" "))
                    .append(Component.literal(SrvLang.t(target, "[NON]", "[NO]")).withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"))));
                target.sendSystemMessage(msg);
                sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§aRequête envoyée à " + target.getName().getString(), "§aRequest sent to " + target.getName().getString())));
                return 1;
            })));
        dispatcher.register(Commands.literal("tpaccept").executes(context -> {
            ServerPlayer target = context.getSource().getPlayerOrException();
            UUID senderUUID = tpaRequests.remove(target.getUUID());
            pendingTpaTimestamps.remove(target.getUUID());
            boolean here = tpaHere.remove(target.getUUID());
            if (senderUUID == null) { target.sendSystemMessage(Component.literal(SrvLang.t(target, "§cAucune demande en attente.", "§cNo pending request."))); return 0; }
            String senderName = DashboardAdmin.getPlayerNameCache().getOrDefault(senderUUID, "?");
            ServerPlayer sender = context.getSource().getServer().getPlayerList().getPlayer(senderUUID);
            if (sender == null) {
                target.sendSystemMessage(Component.literal(SrvLang.t(target, "§c" + senderName + " n'est plus connecté.", "§c" + senderName + " is no longer online.")));
                return 0;
            }
            if (here) {
                // /tpahere : c'est l'accepteur (target) qui rejoint le demandeur (sender).
                target.teleportTo((ServerLevel) sender.level(), sender.getX(), sender.getY(), sender.getZ(), Set.of(), target.getYRot(), target.getXRot(), true);
            } else {
                sender.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), sender.getYRot(), sender.getXRot(), true);
            }
            target.sendSystemMessage(Component.literal(SrvLang.t(target, "§a✔ Vous avez accepté la demande de §e" + sender.getName().getString() + "§a.", "§a✔ You accepted §e" + sender.getName().getString() + "§a's request.")));
            sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§a✔ §e" + target.getName().getString() + "§a a accepté — téléporté !", "§a✔ §e" + target.getName().getString() + "§a accepted — teleported!")));
            return 1;
        }));
        dispatcher.register(Commands.literal("tpdeny").executes(context -> {
            ServerPlayer target = context.getSource().getPlayerOrException();
            pendingTpaTimestamps.remove(target.getUUID());
            tpaHere.remove(target.getUUID());
            UUID senderUUID = tpaRequests.remove(target.getUUID());
            if (senderUUID == null) { target.sendSystemMessage(Component.literal(SrvLang.t(target, "§cAucune demande en attente.", "§cNo pending request."))); return 0; }
            String senderName = DashboardAdmin.getPlayerNameCache().getOrDefault(senderUUID, "?");
            ServerPlayer sender = context.getSource().getServer().getPlayerList().getPlayer(senderUUID);
            target.sendSystemMessage(Component.literal(SrvLang.t(target, "§c✘ Vous avez refusé la demande de §e" + senderName + "§c.", "§c✘ You denied §e" + senderName + "§c's request.")));
            if (sender != null) sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§c✘ §e" + target.getName().getString() + "§c a refusé votre demande de téléportation.", "§c✘ §e" + target.getName().getString() + "§c denied your teleport request.")));
            return 1;
        }));
    }
}
