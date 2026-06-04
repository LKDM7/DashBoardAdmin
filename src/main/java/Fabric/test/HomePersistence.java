package Fabric.test;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class HomePersistence {
    private static final Path PATH = Paths.get("run/data/homes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();
        for (var entry : Test.getAllHomes().entrySet()) {
            JsonObject playerObj = new JsonObject();
            Map<String, String> dims = Test.getPlayerHomesDim(entry.getKey());
            for (var home : entry.getValue().entrySet()) {
                BlockPos pos = home.getValue();
                JsonObject homeObj = new JsonObject();
                homeObj.addProperty("x", pos.getX());
                homeObj.addProperty("y", pos.getY());
                homeObj.addProperty("z", pos.getZ());
                homeObj.addProperty("dim", dims.getOrDefault(home.getKey(), "minecraft:overworld"));
                playerObj.add(home.getKey(), homeObj);
            }
            root.add(entry.getKey().toString(), playerObj);
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
                Map<String, BlockPos> homes = Test.getPlayerHomes(uuid);
                Map<String, String>  dims  = Test.getPlayerHomesDim(uuid);
                for (var home : entry.getValue().getAsJsonObject().entrySet()) {
                    JsonElement val = home.getValue();
                    if (val.isJsonArray()) {
                        // Backward compat: old [x, y, z] format
                        JsonArray pos = val.getAsJsonArray();
                        homes.put(home.getKey(), new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()));
                    } else if (val.isJsonObject()) {
                        JsonObject obj = val.getAsJsonObject();
                        homes.put(home.getKey(), new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt()));
                        if (obj.has("dim")) dims.put(home.getKey(), obj.get("dim").getAsString());
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}

