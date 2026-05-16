package Fabric.test;

import com.google.gson.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

public class SettingsPersistence {
    private static final Path PATH = Paths.get("run/data/player_settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        java.util.Map<String, java.util.Map<String, Boolean>> data = new java.util.HashMap<>();
        for (var entry : Test.getAllSettings().entrySet()) {
            PlayerSettings s = entry.getValue();
            java.util.Map<String, Boolean> map = new java.util.HashMap<>();
            map.put("allowPrivateMessages",  s.allowPrivateMessages);
            map.put("allowTpaRequests",       s.allowTpaRequests);
            map.put("allowTrades",            s.allowTrades);
            map.put("showChatNotifications",  s.showChatNotifications);
            map.put("showConnectionAlerts",   s.showConnectionAlerts);
            map.put("keepInventory",          s.keepInventory);
            data.put(entry.getKey().toString(), map);
        }
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(data));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonObject json = GSON.fromJson(Files.readString(PATH), JsonObject.class);
            for (var entry : json.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                PlayerSettings s = Test.getPlayerSettings(uuid);
                JsonObject m = entry.getValue().getAsJsonObject();
                if (m.has("allowPrivateMessages"))  s.allowPrivateMessages  = m.get("allowPrivateMessages").getAsBoolean();
                if (m.has("allowTpaRequests"))       s.allowTpaRequests       = m.get("allowTpaRequests").getAsBoolean();
                if (m.has("allowTrades"))            s.allowTrades            = m.get("allowTrades").getAsBoolean();
                if (m.has("showChatNotifications"))  s.showChatNotifications  = m.get("showChatNotifications").getAsBoolean();
                if (m.has("showConnectionAlerts"))   s.showConnectionAlerts   = m.get("showConnectionAlerts").getAsBoolean();
                if (m.has("keepInventory"))          s.keepInventory          = m.get("keepInventory").getAsBoolean();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
    }
}
