package Fabric.test;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class HomePersistence {
    private static final Path PATH = Paths.get("run/data/homes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        Map<String, Map<String, List<Integer>>> data = new HashMap<>();
        for (var entry : Test.getAllHomes().entrySet()) {
            Map<String, List<Integer>> playerHomes = new HashMap<>();
            for (var home : entry.getValue().entrySet()) {
                BlockPos pos = home.getValue();
                playerHomes.put(home.getKey(), Arrays.asList(pos.getX(), pos.getY(), pos.getZ()));
            }
            data.put(entry.getKey().toString(), playerHomes);
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
                Map<String, BlockPos> playerHomes = Test.getPlayerHomes(uuid);
                for (var home : entry.getValue().getAsJsonObject().entrySet()) {
                    JsonArray pos = home.getValue().getAsJsonArray();
                    playerHomes.put(home.getKey(), new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()));
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
    }
}
