package Fabric.test;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class LockPersistence {
    private static final Path PATH = Paths.get("run/data/locks.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();

        JsonObject blocks = new JsonObject();
        for (var e : Test.getAllLockedBlocks().entrySet()) {
            BlockPos pos = e.getKey();
            blocks.addProperty(pos.getX() + "," + pos.getY() + "," + pos.getZ(), e.getValue().toString());
        }
        root.add("lockedBlocks", blocks);

        JsonObject trusted = new JsonObject();
        for (var e : Test.getAllTrustedPlayers().entrySet()) {
            JsonArray arr = new JsonArray();
            for (UUID uuid : e.getValue()) arr.add(uuid.toString());
            trusted.add(e.getKey().toString(), arr);
        }
        root.add("trustedPlayers", trusted);

        JsonObject names = new JsonObject();
        for (var e : Test.getPlayerNameCache().entrySet()) {
            names.addProperty(e.getKey().toString(), e.getValue());
        }
        root.add("playerNameCache", names);

        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(PATH), JsonObject.class);

            if (root.has("lockedBlocks")) {
                Map<BlockPos, UUID> locks = Test.getAllLockedBlocks();
                for (var e : root.getAsJsonObject("lockedBlocks").entrySet()) {
                    String[] parts = e.getKey().split(",");
                    BlockPos pos = new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                    );
                    locks.put(pos, UUID.fromString(e.getValue().getAsString()));
                }
            }

            if (root.has("trustedPlayers")) {
                for (var e : root.getAsJsonObject("trustedPlayers").entrySet()) {
                    UUID owner = UUID.fromString(e.getKey());
                    Set<UUID> set = Test.getTrusted(owner);
                    for (JsonElement el : e.getValue().getAsJsonArray()) {
                        set.add(UUID.fromString(el.getAsString()));
                    }
                }
            }

            if (root.has("playerNameCache")) {
                Map<UUID, String> cache = Test.getPlayerNameCache();
                for (var e : root.getAsJsonObject("playerNameCache").entrySet()) {
                    cache.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}

