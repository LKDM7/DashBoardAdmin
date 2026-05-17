package Fabric.test.client;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.AdminActionPayload;
import Fabric.test.networking.OpenSettingsPayload;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class TestClient implements ClientModInitializer {
    public static KeyMapping adminKey;
    public static KeyMapping vanishKey;
    public static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        Fabric.test.invsort.client.InvSortClient.init();

        adminKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Dashboard",
                InputConstants.Type.KEYSYM, 
                GLFW.GLFW_KEY_F4, 
                net.minecraft.client.KeyMapping.Category.MISC
        ));
        vanishKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Vanish",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F5,
                net.minecraft.client.KeyMapping.Category.MISC
        ));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                net.minecraft.client.KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (adminKey.consumeClick()) {
                client.player.connection.sendCommand("admin");
            }
            while (vanishKey.consumeClick()) {
                ClientPlayNetworking.send(new AdminActionPayload("VANISH", "", ""));
            }
            while (menuKey.consumeClick()) {
                client.player.connection.sendCommand("menu");
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(AdminCommand.OpenAdminGuiPayload.TYPE, (payload, context) ->
            context.client().execute(() -> Minecraft.getInstance().setScreen(new AdminScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(OpenSettingsPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                net.minecraft.client.gui.screens.Screen current = Minecraft.getInstance().screen;
                if (current instanceof SettingsScreen ss) ss.onStatsRefresh(payload);
                else Minecraft.getInstance().setScreen(new SettingsScreen(payload));
            }));
        ClientPlayNetworking.registerGlobalReceiver(Fabric.test.networking.PlayerLogsPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                net.minecraft.client.gui.screens.Screen current = Minecraft.getInstance().screen;
                if (current instanceof AdminScreen as) as.onLogsReceived(payload.playerName(), payload.logsSerialized());
            }));

        ClientPlayNetworking.registerGlobalReceiver(Fabric.test.networking.OpenDealPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                net.minecraft.client.gui.screens.Screen current = Minecraft.getInstance().screen;
                if (current instanceof DealScreen ds) ds.onUpdate(payload);
                else if (!payload.closed()) Minecraft.getInstance().setScreen(new DealScreen(payload));
            }));

        ClientPlayNetworking.registerGlobalReceiver(Fabric.test.networking.OpenZonePayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                net.minecraft.client.gui.screens.Screen current = Minecraft.getInstance().screen;
                if (current instanceof AdminScreen as) as.onZoneUpdate(payload);
                else if (current instanceof ZoneScreen zs) zs.onUpdate(payload);
                else Minecraft.getInstance().setScreen(new ZoneScreen(payload));
            }));

        // Group: full state refresh (sent on /menu open and after group changes)
        ClientPlayNetworking.registerGlobalReceiver(Fabric.test.networking.OpenGroupPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                net.minecraft.client.gui.screens.Screen current = Minecraft.getInstance().screen;
                if (current instanceof SettingsScreen ss) ss.onGroupUpdate(payload);
                else {
                    // Update HUD even if settings screen is closed
                    List<GroupHud.MemberState> states = new java.util.ArrayList<>();
                    if (!payload.members().isEmpty())
                        for (String entry : payload.members().split("\\|")) {
                            String[] f = entry.split(":");
                            if (f.length == 5) {
                                try {
                                    states.add(new GroupHud.MemberState(
                                        java.util.UUID.fromString(f[0]), f[1],
                                        Integer.parseInt(f[2]), 0, 0, 0, 100,
                                        "1".equals(f[3]), false));
                                } catch (Exception ignored) {}
                            }
                        }
                    GroupHud.updateMembers(states, payload.showNames());
                }
            }));

        // Group: periodic position / health / AFK updates for HUD
        ClientPlayNetworking.registerGlobalReceiver(Fabric.test.networking.GroupUpdatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (payload.updates().isEmpty()) { GroupHud.clear(); return; }
                List<GroupHud.MemberState> states = new java.util.ArrayList<>();
                java.util.UUID myUUID = context.client().player != null
                    ? context.client().player.getUUID() : null;
                for (String entry : payload.updates().split("\\|")) {
                    String[] f = entry.split(":");
                    if (f.length < 8) continue;
                    try {
                        java.util.UUID uid = java.util.UUID.fromString(f[0]);
                        if (myUUID != null && uid.equals(myUUID)) continue; // skip self
                        states.add(new GroupHud.MemberState(
                            uid, "",
                            Integer.parseInt(f[6]),
                            Double.parseDouble(f[1]),
                            Double.parseDouble(f[2]),
                            Double.parseDouble(f[3]),
                            Integer.parseInt(f[4]),
                            "1".equals(f[5]),
                            "1".equals(f[7])
                        ));
                    } catch (Exception ignored) {}
                }
                // Merge names from existing HUD state
                List<GroupHud.MemberState> current = GroupHud.getMembers();
                List<GroupHud.MemberState> merged = states.stream().map(s -> {
                    String name = current.stream().filter(c -> c.uuid().equals(s.uuid()))
                        .findFirst().map(GroupHud.MemberState::name).orElse(s.name());
                    return new GroupHud.MemberState(s.uuid(), name, s.colorIdx(),
                        s.x(), s.y(), s.z(), s.health(), s.afk(), s.vanished());
                }).toList();
                GroupHud.updateMembers(merged, GroupHud.isShowNames());
            }));

        ClientPlayNetworking.registerGlobalReceiver(Fabric.test.networking.NotifPayload.TYPE, (payload, context) ->
            context.client().execute(() -> NotifHud.push(payload.notifType(), payload.message())));

        ClientPlayNetworking.registerGlobalReceiver(Fabric.test.networking.OpenSanctionsPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                net.minecraft.client.gui.screens.Screen current = Minecraft.getInstance().screen;
                if (current instanceof AdminScreen as) as.onSanctionsReceived(payload.data());
            }));

        GroupHud.register();
        NotifHud.register();
    }
}
