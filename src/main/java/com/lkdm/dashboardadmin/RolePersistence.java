package com.lkdm.dashboardadmin;

import com.google.gson.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Persistance JSON des rôles de modération (run/data/roles.json). */
public class RolePersistence {
    private static final Path PATH = Paths.get("run/data/roles.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();
        JsonArray rolesArr = new JsonArray();

        for (Map.Entry<String, RoleManager.Role> entry : RoleManager.getRolesMap().entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", entry.getKey());

            JsonArray perms = new JsonArray();
            for (String p : entry.getValue().perms) perms.add(p);
            obj.add("perms", perms);

            JsonArray members = new JsonArray();
            for (UUID m : entry.getValue().members) members.add(m.toString());
            obj.add("members", members);

            rolesArr.add(obj);
        }

        root.add("roles", rolesArr);
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(PATH), JsonObject.class);
            if (root == null || !root.has("roles")) return;
            for (JsonElement el : root.getAsJsonArray("roles")) {
                JsonObject obj = el.getAsJsonObject();
                String name = obj.get("name").getAsString();

                List<String> perms = new ArrayList<>();
                if (obj.has("perms"))
                    for (JsonElement p : obj.getAsJsonArray("perms")) perms.add(p.getAsString());

                List<UUID> members = new ArrayList<>();
                if (obj.has("members"))
                    for (JsonElement m : obj.getAsJsonArray("members")) {
                        try { members.add(UUID.fromString(m.getAsString())); }
                        catch (IllegalArgumentException ignored) {}
                    }

                RoleManager.restoreRole(name, perms, members);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}
