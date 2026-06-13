package com.lkdm.dashboardadmin;

import com.google.gson.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persistance des données de modération : logs par joueur, historique du chat
 * public et notes admin. Sans ce fichier, tout cet historique était perdu à
 * chaque redémarrage du serveur (la section HORS LIGNE affichait des logs vides).
 */
public class ModerationPersistence {
    private static final Path PATH = Paths.get("run/data/moderation.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();

        JsonObject logs = new JsonObject();
        for (Map.Entry<UUID, List<String>> e : DashboardAdmin.getPlayerLogs().entrySet()) {
            JsonArray arr = new JsonArray();
            for (String line : e.getValue()) arr.add(line);
            logs.add(e.getKey().toString(), arr);
        }
        root.add("playerLogs", logs);

        JsonArray chat = new JsonArray();
        synchronized (DashboardAdmin.getChatHistoryRaw()) {
            for (String line : DashboardAdmin.getChatHistoryRaw()) chat.add(line);
        }
        root.add("chatHistory", chat);

        JsonObject notes = new JsonObject();
        for (Map.Entry<UUID, List<String>> e : DashboardAdmin.getAdminNotes().entrySet()) {
            JsonArray arr = new JsonArray();
            for (String n : e.getValue()) arr.add(n);
            notes.add(e.getKey().toString(), arr);
        }
        root.add("adminNotes", notes);

        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(PATH), JsonObject.class);

            if (root.has("playerLogs")) {
                Map<UUID, List<String>> logs = DashboardAdmin.getPlayerLogs();
                logs.clear();
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("playerLogs").entrySet()) {
                    List<String> list = new ArrayList<>();
                    for (JsonElement el : e.getValue().getAsJsonArray()) list.add(el.getAsString());
                    try { logs.put(UUID.fromString(e.getKey()), list); }
                    catch (IllegalArgumentException ignored) {}
                }
            }

            if (root.has("chatHistory")) {
                List<String> chat = DashboardAdmin.getChatHistoryRaw();
                synchronized (chat) {
                    chat.clear();
                    for (JsonElement el : root.getAsJsonArray("chatHistory")) chat.add(el.getAsString());
                }
            }

            if (root.has("adminNotes")) {
                Map<UUID, List<String>> notes = DashboardAdmin.getAdminNotes();
                notes.clear();
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("adminNotes").entrySet()) {
                    try {
                        UUID id = UUID.fromString(e.getKey());
                        List<String> list = new ArrayList<>();
                        if (e.getValue().isJsonArray())
                            for (JsonElement el : e.getValue().getAsJsonArray()) list.add(el.getAsString());
                        else
                            list.add(e.getValue().getAsString()); // rétro-compat ancien format (1 note)
                        if (!list.isEmpty()) notes.put(id, list);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}
