package Fabric.test;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.*;
import java.nio.file.*;
import java.util.Map;

public class ServerConfig {
    private static final Path PATH = Paths.get("run/data/server_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();

        JsonObject cooldowns = new JsonObject();
        cooldowns.addProperty("home", Test.getCooldownHome());
        cooldowns.addProperty("back", Test.getCooldownBack());
        cooldowns.addProperty("tpa",  Test.getCooldownTpa());
        root.add("cooldowns", cooldowns);

        root.addProperty("afkDelayMinutes", Test.getAfkDelayMinutes());
        root.addProperty("maxHomes",        Test.getMaxHomes());
        root.addProperty("webhookReports",   Test.getWebhookReports());
        root.addProperty("webhookSanctions", Test.getWebhookSanctions());

        root.addProperty("pvpEnabled", Test.isPvpEnabled());

        JsonObject features = new JsonObject();
        features.addProperty("weatherCycle",       Test.isWeatherCycleEnabled());
        features.addProperty("cropTrample",        Test.isCropTrampleEnabled());
        features.addProperty("chatLocked",         Test.isChatLocked());
        features.addProperty("afkAuto",            Test.isAfkAutoEnabled());
        features.addProperty("proportionalSleep",  Test.isProportionalSleepEnabled());
        features.addProperty("treeCapitator",      Test.isTreeCapitatorEnabled());
        features.addProperty("fastLeafDecay",      Test.isFastLeafDecayEnabled());
        features.addProperty("doubleDoor",         Test.isDoubleDoorEnabled());
        features.addProperty("rightClickHarvest",  Test.isRightClickHarvestEnabled());
        features.addProperty("dispenserHarvest",   Test.isDispenserHarvestEnabled());
        root.add("features", features);

        root.addProperty("cooldownWarp", Test.getCooldownWarp());

        JsonObject warps = new JsonObject();
        for (Map.Entry<String, BlockPos> e : Test.getWarps().entrySet()) {
            JsonObject w = new JsonObject();
            BlockPos pos = e.getValue();
            w.addProperty("x", pos.getX());
            w.addProperty("y", pos.getY());
            w.addProperty("z", pos.getZ());
            w.addProperty("dim", Test.getWarpsDim().getOrDefault(e.getKey(), "minecraft:overworld"));
            warps.add(e.getKey(), w);
        }
        root.add("warps", warps);

        JsonArray broadcasts = new JsonArray();
        String[] msgs      = Test.getScheduledMsgsArray();
        int[]    intervals = Test.getScheduledIntervalsArray();
        for (int i = 0; i < msgs.length; i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("message",       msgs[i]);
            entry.addProperty("intervalTicks", intervals[i]);
            broadcasts.add(entry);
        }
        root.add("scheduledBroadcasts", broadcasts);

        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(PATH), JsonObject.class);

            if (root.has("cooldowns")) {
                JsonObject c = root.getAsJsonObject("cooldowns");
                if (c.has("home")) Test.setCooldownHome(c.get("home").getAsInt());
                if (c.has("back")) Test.setCooldownBack(c.get("back").getAsInt());
                if (c.has("tpa"))  Test.setCooldownTpa(c.get("tpa").getAsInt());
            }

            if (root.has("pvpEnabled")) Test.setPvpEnabled(root.get("pvpEnabled").getAsBoolean());
            if (root.has("afkDelayMinutes")) Test.setAfkDelayMinutes(root.get("afkDelayMinutes").getAsInt());
            if (root.has("maxHomes"))        Test.setMaxHomes(root.get("maxHomes").getAsInt());
            if (root.has("webhookReports"))   Test.setWebhookReports(root.get("webhookReports").getAsString());
            if (root.has("webhookSanctions")) Test.setWebhookSanctions(root.get("webhookSanctions").getAsString());

            if (root.has("features")) {
                JsonObject f = root.getAsJsonObject("features");
                if (f.has("weatherCycle"))      Test.setWeatherCycleEnabled(f.get("weatherCycle").getAsBoolean());
                if (f.has("cropTrample"))       Test.setCropTrampleEnabled(f.get("cropTrample").getAsBoolean());
                if (f.has("chatLocked"))        Test.setChatLocked(f.get("chatLocked").getAsBoolean());
                if (f.has("afkAuto"))           Test.setAfkAutoEnabled(f.get("afkAuto").getAsBoolean());
                if (f.has("proportionalSleep")) Test.setProportionalSleepEnabled(f.get("proportionalSleep").getAsBoolean());
                if (f.has("treeCapitator"))     Test.setTreeCapitatorEnabled(f.get("treeCapitator").getAsBoolean());
                if (f.has("fastLeafDecay"))     Test.setFastLeafDecayEnabled(f.get("fastLeafDecay").getAsBoolean());
                if (f.has("doubleDoor"))        Test.setDoubleDoorEnabled(f.get("doubleDoor").getAsBoolean());
                if (f.has("rightClickHarvest")) Test.setRightClickHarvestEnabled(f.get("rightClickHarvest").getAsBoolean());
                if (f.has("dispenserHarvest"))  Test.setDispenserHarvestEnabled(f.get("dispenserHarvest").getAsBoolean());
            }

            if (root.has("cooldownWarp")) Test.setCooldownWarp(root.get("cooldownWarp").getAsInt());

            if (root.has("warps")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("warps").entrySet()) {
                    JsonObject w = e.getValue().getAsJsonObject();
                    Test.getWarps().put(e.getKey(), new BlockPos(w.get("x").getAsInt(), w.get("y").getAsInt(), w.get("z").getAsInt()));
                    if (w.has("dim")) Test.getWarpsDim().put(e.getKey(), w.get("dim").getAsString());
                }
            }

            if (root.has("scheduledBroadcasts")) {
                for (JsonElement el : root.getAsJsonArray("scheduledBroadcasts")) {
                    JsonObject entry = el.getAsJsonObject();
                    Test.addScheduledBroadcast(
                        entry.get("message").getAsString(),
                        entry.get("intervalTicks").getAsInt()
                    );
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}

