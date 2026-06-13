package Fabric.test.command;

import Fabric.test.networking.ModMessages;
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
                    && Fabric.test.RoleManager.hasAnyRole(sp.getUUID())))
            .executes(context -> {
                sendAdminGui(context.getSource().getPlayerOrException(), context.getSource().getServer());
                return 1;
            }));
    }

    /** Construit et envoie le payload du dashboard admin (utilisé par /admin et REFRESH_ADMIN). */
    public static void sendAdminGui(ServerPlayer player, MinecraftServer server) {
        PacketDistributor.sendToPlayer(player, new OpenAdminGuiPayload(
            Fabric.test.Test.isPvpEnabled(),
            Fabric.test.Test.getMutedPlayerNames(server),
            Fabric.test.Test.getFrozenPlayerNames(server),
            Fabric.test.Test.getReportsSerialized(),
            Fabric.test.Test.getKeepInventoryPlayerNames(server),
            Fabric.test.Test.getAcceptedReportsSerialized(),
            Fabric.test.Test.getClosedReportsSerialized(),
            Fabric.test.Test.getScheduledBroadcastsSerialized(),
            Fabric.test.Test.getCooldownsSerialized(),
            Fabric.test.Test.getFeaturesSerialized(),
            Fabric.test.Test.getBannedPlayersSerialized(server),
            Fabric.test.GroupManager.getGroupsSerialized(server),
            Fabric.test.Test.getAfkPlayerNames(server),
            Fabric.test.Test.getOfflinePlayersSerialized(server),
            Fabric.test.Test.getServerStatsSerialized(server),
            Fabric.test.Test.getWarpsSerialized(),
            Fabric.test.Test.getAdminNotesSerialized(),
            Fabric.test.RoleManager.getRolesSerialized(server),
            player.hasPermissions(2) ? "*"
                : String.join(",", Fabric.test.RoleManager.getPermsOf(player.getUUID()))
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
