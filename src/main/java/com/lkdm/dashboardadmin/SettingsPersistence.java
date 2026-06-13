package com.lkdm.dashboardadmin;

import com.google.gson.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SettingsPersistence {
    private static final Path PATH = Paths.get("run/data/player_settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();
        for (var entry : DashboardAdmin.getAllSettings().entrySet()) {
            PlayerSettings s = entry.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("allowPrivateMessages",  s.allowPrivateMessages);
            obj.addProperty("allowTpaRequests",       s.allowTpaRequests);
            obj.addProperty("allowTrades",            s.allowTrades);
            obj.addProperty("showChatNotifications",  s.showChatNotifications);
            obj.addProperty("showConnectionAlerts",   s.showConnectionAlerts);
            obj.addProperty("keepInventory",          s.keepInventory);
            if (!s.ignoredPlayers.isEmpty()) {
                JsonArray ignored = new JsonArray();
                for (UUID u : s.ignoredPlayers) ignored.add(u.toString());
                obj.add("ignoredPlayers", ignored);
            }
            root.add(entry.getKey().toString(), obj);
        }
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonObject json = GSON.fromJson(Files.readString(PATH), JsonObject.class);
            for (var entry : json.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                PlayerSettings s = DashboardAdmin.getPlayerSettings(uuid);
                JsonObject m = entry.getValue().getAsJsonObject();
                if (m.has("allowPrivateMessages"))  s.allowPrivateMessages  = m.get("allowPrivateMessages").getAsBoolean();
                if (m.has("allowTpaRequests"))       s.allowTpaRequests       = m.get("allowTpaRequests").getAsBoolean();
                if (m.has("allowTrades"))            s.allowTrades            = m.get("allowTrades").getAsBoolean();
                if (m.has("showChatNotifications"))  s.showChatNotifications  = m.get("showChatNotifications").getAsBoolean();
                if (m.has("showConnectionAlerts"))   s.showConnectionAlerts   = m.get("showConnectionAlerts").getAsBoolean();
                if (m.has("keepInventory"))          s.keepInventory          = m.get("keepInventory").getAsBoolean();
                if (m.has("ignoredPlayers"))
                    for (JsonElement el : m.getAsJsonArray("ignoredPlayers"))
                        s.ignoredPlayers.add(UUID.fromString(el.getAsString()));
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}

