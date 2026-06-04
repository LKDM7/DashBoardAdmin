package Fabric.test.command;

import Fabric.test.networking.ModMessages;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class AdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("admin")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                net.minecraft.server.MinecraftServer server = context.getSource().getServer();
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
                    Fabric.test.GroupManager.getGroupsSerialized(server)
                ));
                return 1;
            }));
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
        String groupsSerialized
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
            },
            buf -> new OpenAdminGuiPayload(
                buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()
            )
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
