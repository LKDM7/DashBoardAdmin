package Fabric.test.client;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "dashboardadmin", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DashClientNetworking {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        reg.playToClient(AdminCommand.OpenAdminGuiPayload.TYPE, AdminCommand.OpenAdminGuiPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() ->
                Minecraft.getInstance().setScreen(new AdminScreen(payload))));

        reg.playToClient(OpenSettingsPayload.TYPE, OpenSettingsPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                Screen cur = Minecraft.getInstance().screen;
                if (cur instanceof SettingsScreen ss) ss.onStatsRefresh(payload);
                else Minecraft.getInstance().setScreen(new SettingsScreen(payload));
            }));

        reg.playToClient(PlayerLogsPayload.TYPE, PlayerLogsPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                Screen cur = Minecraft.getInstance().screen;
                if (cur instanceof AdminScreen as) as.onLogsReceived(payload.playerName(), payload.logsSerialized());
            }));

        reg.playToClient(OpenDealPayload.TYPE, OpenDealPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                Screen cur = Minecraft.getInstance().screen;
                if (cur instanceof DealScreen ds) ds.onUpdate(payload);
                else if (!payload.closed()) Minecraft.getInstance().setScreen(new DealScreen(payload));
            }));

        reg.playToClient(OpenZonePayload.TYPE, OpenZonePayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                Screen cur = Minecraft.getInstance().screen;
                if (cur instanceof AdminScreen as) as.onZoneUpdate(payload);
                else if (cur instanceof ZoneScreen zs) zs.onUpdate(payload);
                else Minecraft.getInstance().setScreen(new ZoneScreen(payload));
            }));

        reg.playToClient(OpenGroupPayload.TYPE, OpenGroupPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                Screen cur = Minecraft.getInstance().screen;
                if (cur instanceof SettingsScreen ss) {
                    ss.onGroupUpdate(payload);
                } else {
                    java.util.List<GroupHud.MemberState> states = new java.util.ArrayList<>();
                    if (!payload.members().isEmpty())
                        for (String entry : payload.members().split("\\|")) {
                            String[] f = entry.split(":");
                            if (f.length == 5) try {
                                states.add(new GroupHud.MemberState(java.util.UUID.fromString(f[0]), f[1], Integer.parseInt(f[2]), 0, 0, 0, 100, "1".equals(f[3]), false));
                            } catch (Exception ignored) {}
                        }
                    GroupHud.updateMembers(states, payload.showNames());
                }
            }));

        reg.playToClient(GroupUpdatePayload.TYPE, GroupUpdatePayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (payload.updates().isEmpty()) { GroupHud.clear(); return; }
                java.util.List<GroupHud.MemberState> states = new java.util.ArrayList<>();
                java.util.UUID myUUID = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
                for (String entry : payload.updates().split("\\|")) {
                    String[] f = entry.split(":");
                    if (f.length < 8) continue;
                    try {
                        java.util.UUID uid = java.util.UUID.fromString(f[0]);
                        if (myUUID != null && uid.equals(myUUID)) continue;
                        states.add(new GroupHud.MemberState(uid, "", Integer.parseInt(f[6]),
                            Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                            Integer.parseInt(f[4]), "1".equals(f[5]), "1".equals(f[7])));
                    } catch (Exception ignored) {}
                }
                java.util.List<GroupHud.MemberState> existing = GroupHud.getMembers();
                java.util.List<GroupHud.MemberState> merged = states.stream().map(s -> {
                    String name = existing.stream().filter(c -> c.uuid().equals(s.uuid()))
                        .findFirst().map(GroupHud.MemberState::name).orElse(s.name());
                    return new GroupHud.MemberState(s.uuid(), name, s.colorIdx(),
                        s.x(), s.y(), s.z(), s.health(), s.afk(), s.vanished());
                }).toList();
                GroupHud.updateMembers(merged, GroupHud.isShowNames());
            }));

        reg.playToClient(NotifPayload.TYPE, NotifPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> NotifHud.push(payload.notifType(), payload.message())));

        reg.playToClient(OpenSanctionsPayload.TYPE, OpenSanctionsPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                Screen cur = Minecraft.getInstance().screen;
                if (cur instanceof AdminScreen as) as.onSanctionsReceived(payload.data());
            }));

        reg.playToClient(OpenReportPayload.TYPE, OpenReportPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(new ReportScreen())));

        reg.playToClient(ReportImagePayload.TYPE, ReportImagePayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                Screen cur = Minecraft.getInstance().screen;
                if (cur instanceof AdminScreen as) as.onReportImageReceived(payload.playerName(), payload.imageData());
            }));
    }
}
