package com.lkdm.dashboardadmin;

import com.google.gson.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MobKillsPersistence {
    private static final Path PATH = Paths.get("run/data/mob_kills.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject data = new JsonObject();
        for (var e : DashboardAdmin.getAllHostileMobKills().entrySet()) {
            data.addProperty(e.getKey().toString(), e.getValue());
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
            Map<UUID, Integer> kills = DashboardAdmin.getAllHostileMobKills();
            for (var e : json.entrySet()) {
                kills.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}

