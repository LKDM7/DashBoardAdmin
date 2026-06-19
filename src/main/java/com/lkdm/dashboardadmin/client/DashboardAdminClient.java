package com.lkdm.dashboardadmin.client;

import com.lkdm.dashboardadmin.networking.AdminActionPayload;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "dashboardadmin", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DashboardAdminClient {

    public static KeyMapping adminKey;
    public static KeyMapping vanishKey;
    public static KeyMapping menuKey;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        adminKey  = new KeyMapping("Dashboard", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F4, KeyMapping.CATEGORY_MISC);
        vanishKey = new KeyMapping("Vanish",    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F5, KeyMapping.CATEGORY_MISC);
        menuKey   = new KeyMapping("Menu",      InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, KeyMapping.CATEGORY_MISC);
        event.register(adminKey);
        event.register(vanishKey);
        event.register(menuKey);
    }

    // Called from DashboardAdmin constructor (client-side init)
    public static void initClient(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(ClientTickHandler.class);
        GroupHud.register();
        NotifHud.register();
        ZoneOverlayRenderer.register();
        ClientZoneCache.loadConfig();
        AutoEatConfig.load();
        AutoEatPanel.register();
        AutoEatHandler.register();
    }

    // ─── Tick handler (inner static class on GAME bus) ────────────────────────
    @EventBusSubscriber(modid = "dashboardadmin", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class ClientTickHandler {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client.player == null) return;

            while (adminKey != null && adminKey.consumeClick()) {
                client.player.connection.sendCommand("admin");
            }
            while (vanishKey != null && vanishKey.consumeClick()) {
                PacketDistributor.sendToServer(new AdminActionPayload("VANISH", "", ""));
            }
            while (menuKey != null && menuKey.consumeClick()) {
                client.player.connection.sendCommand("menu");
            }
        }
    }
}
