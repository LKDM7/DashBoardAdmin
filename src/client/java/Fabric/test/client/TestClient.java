package Fabric.test.client;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.AdminActionPayload;
import Fabric.test.networking.OpenSettingsPayload;
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

    @Override
    public void onInitializeClient() {
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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (adminKey.consumeClick()) {
                client.player.connection.sendCommand("admin");
            }
            while (vanishKey.consumeClick()) {
                ClientPlayNetworking.send(new AdminActionPayload("VANISH", "", ""));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(AdminCommand.OpenAdminGuiPayload.TYPE, (payload, context) ->
            context.client().execute(() -> Minecraft.getInstance().setScreen(new AdminScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(OpenSettingsPayload.TYPE, (payload, context) ->
            context.client().execute(() -> Minecraft.getInstance().setScreen(new SettingsScreen(payload))));
    }
}
