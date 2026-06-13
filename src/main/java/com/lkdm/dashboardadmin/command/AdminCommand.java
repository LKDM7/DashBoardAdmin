package com.lkdm.dashboardadmin.command;

import com.lkdm.dashboardadmin.networking.ModMessages;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class AdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("admin")
            .requires(source -> source.hasPermission(2)
                || (source.getEntity() instanceof ServerPlayer sp
                    && com.lkdm.dashboardadmin.RoleManager.hasAnyRole(sp.getUUID())))
            .executes(context -> {
                sendAdminGui(context.getSource().getPlayerOrException(), context.getSource().getServer());
                return 1;
            }));
    }

    /** Renvoie {@code value} seulement si le joueur a la permission ; sinon une chaîne vide.
     *  Évite de pousser des données sensibles (notes, reports, bans, rôles…) à un porteur de
     *  rôle qui n'a pas l'onglet correspondant — le filtrage client seul est contournable. */
    private static String gated(ServerPlayer p, String perm, String value) {
        return com.lkdm.dashboardadmin.RoleManager.can(p, perm) ? value : "";
    }

    /** Construit et envoie le payload du dashboard admin (utilisé par /admin et REFRESH_ADMIN).
     *  Chaque champ sensible est filtré côté serveur selon les permissions du destinataire. */
    public static void sendAdminGui(ServerPlayer player, MinecraftServer server) {
        boolean canFeatures = com.lkdm.dashboardadmin.RoleManager.can(player, "tab.features");
        PacketDistributor.sendToPlayer(player, new OpenAdminGuiPayload(
            com.lkdm.dashboardadmin.DashboardAdmin.isPvpEnabled(),
            gated(player, "tab.joueurs",     com.lkdm.dashboardadmin.DashboardAdmin.getMutedPlayerNames(server)),
            gated(player, "tab.joueurs",     com.lkdm.dashboardadmin.DashboardAdmin.getFrozenPlayerNames(server)),
            gated(player, "tab.reports",     com.lkdm.dashboardadmin.DashboardAdmin.getReportsSerialized()),
            gated(player, "tab.joueurs",     com.lkdm.dashboardadmin.DashboardAdmin.getKeepInventoryPlayerNames(server)),
            gated(player, "tab.reports",     com.lkdm.dashboardadmin.DashboardAdmin.getAcceptedReportsSerialized()),
            gated(player, "tab.reports",     com.lkdm.dashboardadmin.DashboardAdmin.getClosedReportsSerialized()),
            com.lkdm.dashboardadmin.DashboardAdmin.getScheduledBroadcastsSerialized(),
            com.lkdm.dashboardadmin.DashboardAdmin.getCooldownsSerialized(),
            // Webhooks Discord (secrets) masqués si le joueur n'a pas l'onglet Features.
            com.lkdm.dashboardadmin.DashboardAdmin.getFeaturesSerialized(canFeatures),
            gated(player, "tab.joueurs",     com.lkdm.dashboardadmin.DashboardAdmin.getBannedPlayersSerialized(server)),
            com.lkdm.dashboardadmin.GroupManager.getGroupsSerialized(server),
            com.lkdm.dashboardadmin.DashboardAdmin.getAfkPlayerNames(server),
            com.lkdm.dashboardadmin.DashboardAdmin.getOfflinePlayersSerialized(server),
            com.lkdm.dashboardadmin.DashboardAdmin.getServerStatsSerialized(server),
            com.lkdm.dashboardadmin.WarpManager.getWarpsSerialized(),
            gated(player, "tab.joueurs",     com.lkdm.dashboardadmin.DashboardAdmin.getAdminNotesSerialized()),
            gated(player, "act.manage_roles", com.lkdm.dashboardadmin.RoleManager.getRolesSerialized(server)),
            player.hasPermissions(2) ? "*"
                : String.join(",", com.lkdm.dashboardadmin.RoleManager.getPermsOf(player.getUUID()))
        ));
    }

    public record OpenAdminGuiPayload(
        boolean pvpEnabled,
        String mutedPlayers,
        String frozenPlayers,
        String reports,
        String keepInventoryPlayers,
        String acceptedReports,
        String closedReports,
        String scheduledBroadcasts,
        String cooldowns,
        String features,
        String bannedPlayers,
        String groupsSerialized,
        String afkPlayers,
        String offlinePlayers,
        String serverStats,
        String warps,
        String adminNotes,
        String rolesSerialized,
        String viewerPerms
    ) implements CustomPacketPayload {
        public static final Type<OpenAdminGuiPayload> TYPE = new Type<>(ModMessages.OPEN_ADMIN_GUI);
        public static final StreamCodec<FriendlyByteBuf, OpenAdminGuiPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.pvpEnabled);
                buf.writeUtf(p.mutedPlayers);
                buf.writeUtf(p.frozenPlayers);
                buf.writeUtf(p.reports);
                buf.writeUtf(p.keepInventoryPlayers);
                buf.writeUtf(p.acceptedReports);
                buf.writeUtf(p.closedReports);
                buf.writeUtf(p.scheduledBroadcasts);
                buf.writeUtf(p.cooldowns);
                buf.writeUtf(p.features);
                buf.writeUtf(p.bannedPlayers);
                buf.writeUtf(p.groupsSerialized);
                buf.writeUtf(p.afkPlayers);
                buf.writeUtf(p.offlinePlayers);
                buf.writeUtf(p.serverStats);
                buf.writeUtf(p.warps);
                buf.writeUtf(p.adminNotes);
                buf.writeUtf(p.rolesSerialized);
                buf.writeUtf(p.viewerPerms);
            },
            buf -> new OpenAdminGuiPayload(
                buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf()
            )
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
