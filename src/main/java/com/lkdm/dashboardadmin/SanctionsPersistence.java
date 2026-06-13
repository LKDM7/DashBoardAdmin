package com.lkdm.dashboardadmin;

import com.google.gson.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.*;
import java.nio.file.*;

public class SanctionsPersistence {
    private static final Path PATH = Paths.get("run/data/sanctions.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            JsonArray arr = new JsonArray();
            for (String[] e : DashboardAdmin.getSanctionsLog()) {
                JsonArray row = new JsonArray();
                for (String s : e) row.add(s);
                arr.add(row);
            }
            Files.writeString(PATH, GSON.toJson(arr));
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonArray arr = GSON.fromJson(Files.readString(PATH), JsonArray.class);
            if (arr == null) return;
            DashboardAdmin.getSanctionsLog().clear();
            for (JsonElement el : arr) {
                JsonArray row = el.getAsJsonArray();
                if (row.size() >= 5) {
                    DashboardAdmin.getSanctionsLog().add(new String[]{
                        row.get(0).getAsString(), row.get(1).getAsString(),
                        row.get(2).getAsString(), row.get(3).getAsString(),
                        row.get(4).getAsString()
                    });
                }
            }
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}

