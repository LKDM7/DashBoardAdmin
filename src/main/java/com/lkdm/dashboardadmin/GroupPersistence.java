package com.lkdm.dashboardadmin;

import com.google.gson.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GroupPersistence {
    private static final Path PATH = Paths.get("run/data/groups.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();
        JsonArray groupsArr = new JsonArray();

        for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : GroupManager.getGroupsMap().entrySet()) {
            UUID leader = entry.getKey();
            JsonObject obj = new JsonObject();
            obj.addProperty("leader", leader.toString());

            JsonArray members = new JsonArray();
            for (UUID m : entry.getValue()) members.add(m.toString());
            obj.add("members", members);

            obj.addProperty("name", GroupManager.getGroupNamesMap().getOrDefault(leader, ""));

            JsonObject cols = new JsonObject();
            JsonObject trust = new JsonObject();
            JsonObject show = new JsonObject();
            for (UUID m : entry.getValue()) {
                cols.addProperty(m.toString(), GroupManager.getColor(m));
                trust.addProperty(m.toString(), GroupManager.isGroupTrust(m));
                show.addProperty(m.toString(), GroupManager.isShowNames(m));
            }
            obj.add("colors", cols);
            obj.add("groupTrust", trust);
            obj.add("showNames", show);
            groupsArr.add(obj);
        }

        root.add("groups", groupsArr);
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(PATH), JsonObject.class);
            if (!root.has("groups")) return;
            for (JsonElement el : root.getAsJsonArray("groups")) {
                JsonObject obj = el.getAsJsonObject();
                UUID leader = UUID.fromString(obj.get("leader").getAsString());
                LinkedHashSet<UUID> members = new LinkedHashSet<>();
                for (JsonElement m : obj.getAsJsonArray("members"))
                    members.add(UUID.fromString(m.getAsString()));
                GroupManager.restoreGroup(leader, members);
                if (obj.has("name") && !obj.get("name").getAsString().isEmpty())
                    GroupManager.getGroupNamesMap().put(leader, obj.get("name").getAsString());
                if (obj.has("colors"))
                    for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("colors").entrySet())
                        GroupManager.setColorDirect(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                if (obj.has("groupTrust"))
                    for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("groupTrust").entrySet())
                        if (e.getValue().getAsBoolean())
                            GroupManager.setGroupTrustDirect(UUID.fromString(e.getKey()), true);
                if (obj.has("showNames"))
                    for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("showNames").entrySet())
                        GroupManager.setShowNamesDirect(UUID.fromString(e.getKey()), e.getValue().getAsBoolean());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}

