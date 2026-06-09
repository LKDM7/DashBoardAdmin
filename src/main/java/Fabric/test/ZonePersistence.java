package Fabric.test;

import Fabric.test.command.ZoneCommand;
import com.google.gson.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ZonePersistence {
    private static final Path PATH = Paths.get("run/data/zones.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Zone> e : ZoneCommand.getZones().entrySet()) {
            Zone z = e.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("minX", z.min.getX());
            obj.addProperty("minY", z.min.getY());
            obj.addProperty("minZ", z.min.getZ());
            obj.addProperty("maxX", z.max.getX());
            obj.addProperty("maxY", z.max.getY());
            obj.addProperty("maxZ", z.max.getZ());
            obj.addProperty("nightVision", z.nightVision);
            JsonObject flags = new JsonObject();
            for (ZoneFlag f : ZoneFlag.values()) flags.addProperty(f.name(), z.flag(f));
            obj.add("flags", flags);
            JsonArray members = new JsonArray();
            for (UUID uuid : z.members) members.add(uuid.toString());
            obj.add("members", members);
            root.add(e.getKey(), obj);
        }
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(PATH), JsonObject.class);
            Map<String, Zone> zones = ZoneCommand.getZones();
            zones.clear();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject obj = e.getValue().getAsJsonObject();
                BlockPos min = new BlockPos(
                    obj.get("minX").getAsInt(), obj.get("minY").getAsInt(), obj.get("minZ").getAsInt());
                BlockPos max = new BlockPos(
                    obj.get("maxX").getAsInt(), obj.get("maxY").getAsInt(), obj.get("maxZ").getAsInt());
                Zone z = new Zone(e.getKey(), min, max);
                z.nightVision = obj.has("nightVision") && obj.get("nightVision").getAsBoolean();
                if (obj.has("flags")) {
                    JsonObject flags = obj.getAsJsonObject("flags");
                    for (ZoneFlag f : ZoneFlag.values())
                        if (flags.has(f.name())) z.setFlag(f, flags.get(f.name()).getAsBoolean());
                } else if (obj.has("zoneProtected") && obj.get("zoneProtected").getAsBoolean()) {
                    // Migration: old single "protected" boolean → BUILD access blocked.
                    z.setFlag(ZoneFlag.BUILD, false);
                }
                if (obj.has("members"))
                    for (JsonElement m : obj.getAsJsonArray("members"))
                        z.members.add(UUID.fromString(m.getAsString()));
                zones.put(e.getKey(), z);
            }
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}

