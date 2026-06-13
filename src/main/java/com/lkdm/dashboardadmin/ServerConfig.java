package com.lkdm.dashboardadmin;

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
        cooldowns.addProperty("home", DashboardAdmin.getCooldownHome());
        cooldowns.addProperty("back", DashboardAdmin.getCooldownBack());
        cooldowns.addProperty("tpa",  DashboardAdmin.getCooldownTpa());
        root.add("cooldowns", cooldowns);

        root.addProperty("afkDelayMinutes", DashboardAdmin.getAfkDelayMinutes());
        root.addProperty("maxHomes",        DashboardAdmin.getMaxHomes());
        root.addProperty("webhookReports",   DashboardAdmin.getWebhookReports());
        root.addProperty("webhookSanctions", DashboardAdmin.getWebhookSanctions());
        root.addProperty("motd",             DashboardAdmin.getMotd());

        root.addProperty("pvpEnabled", DashboardAdmin.isPvpEnabled());
        root.addProperty("setblockInBuild", DashboardAdmin.isSetblockInBuild());

        JsonObject features = new JsonObject();
        features.addProperty("weatherCycle",       DashboardAdmin.isWeatherCycleEnabled());
        features.addProperty("cropTrample",        DashboardAdmin.isCropTrampleEnabled());
        features.addProperty("chatLocked",         DashboardAdmin.isChatLocked());
        features.addProperty("afkAuto",            DashboardAdmin.isAfkAutoEnabled());
        features.addProperty("proportionalSleep",  DashboardAdmin.isProportionalSleepEnabled());
        features.addProperty("treeCapitator",      DashboardAdmin.isTreeCapitatorEnabled());
        features.addProperty("fastLeafDecay",      DashboardAdmin.isFastLeafDecayEnabled());
        features.addProperty("doubleDoor",         DashboardAdmin.isDoubleDoorEnabled());
        features.addProperty("rightClickHarvest",  DashboardAdmin.isRightClickHarvestEnabled());
        features.addProperty("dispenserHarvest",   DashboardAdmin.isDispenserHarvestEnabled());
        features.addProperty("mailSpy",            DashboardAdmin.isMailSpyEnabled());
        root.add("features", features);

        root.addProperty("cooldownWarp", DashboardAdmin.getCooldownWarp());
        root.addProperty("cooldownRtp",  DashboardAdmin.getCooldownRtp());

        JsonObject warps = new JsonObject();
        for (Map.Entry<String, BlockPos> e : WarpManager.getWarps().entrySet()) {
            JsonObject w = new JsonObject();
            BlockPos pos = e.getValue();
            w.addProperty("x", pos.getX());
            w.addProperty("y", pos.getY());
            w.addProperty("z", pos.getZ());
            w.addProperty("dim", WarpManager.getWarpsDim().getOrDefault(e.getKey(), "minecraft:overworld"));
            warps.add(e.getKey(), w);
        }
        root.add("warps", warps);

        JsonArray broadcasts = new JsonArray();
        String[] msgs      = DashboardAdmin.getScheduledMsgsArray();
        int[]    intervals = DashboardAdmin.getScheduledIntervalsArray();
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
                if (c.has("home")) DashboardAdmin.setCooldownHome(c.get("home").getAsInt());
                if (c.has("back")) DashboardAdmin.setCooldownBack(c.get("back").getAsInt());
                if (c.has("tpa"))  DashboardAdmin.setCooldownTpa(c.get("tpa").getAsInt());
            }

            if (root.has("pvpEnabled")) DashboardAdmin.setPvpEnabled(root.get("pvpEnabled").getAsBoolean());
            if (root.has("setblockInBuild")) DashboardAdmin.setSetblockInBuild(root.get("setblockInBuild").getAsBoolean());
            if (root.has("afkDelayMinutes")) DashboardAdmin.setAfkDelayMinutes(root.get("afkDelayMinutes").getAsInt());
            if (root.has("maxHomes"))        DashboardAdmin.setMaxHomes(root.get("maxHomes").getAsInt());
            if (root.has("webhookReports"))   DashboardAdmin.setWebhookReports(root.get("webhookReports").getAsString());
            if (root.has("webhookSanctions")) DashboardAdmin.setWebhookSanctions(root.get("webhookSanctions").getAsString());
            if (root.has("motd"))             DashboardAdmin.setMotd(root.get("motd").getAsString());

            if (root.has("features")) {
                JsonObject f = root.getAsJsonObject("features");
                if (f.has("weatherCycle"))      DashboardAdmin.setWeatherCycleEnabled(f.get("weatherCycle").getAsBoolean());
                if (f.has("cropTrample"))       DashboardAdmin.setCropTrampleEnabled(f.get("cropTrample").getAsBoolean());
                if (f.has("chatLocked"))        DashboardAdmin.setChatLocked(f.get("chatLocked").getAsBoolean());
                if (f.has("afkAuto"))           DashboardAdmin.setAfkAutoEnabled(f.get("afkAuto").getAsBoolean());
                if (f.has("proportionalSleep")) DashboardAdmin.setProportionalSleepEnabled(f.get("proportionalSleep").getAsBoolean());
                if (f.has("treeCapitator"))     DashboardAdmin.setTreeCapitatorEnabled(f.get("treeCapitator").getAsBoolean());
                if (f.has("fastLeafDecay"))     DashboardAdmin.setFastLeafDecayEnabled(f.get("fastLeafDecay").getAsBoolean());
                if (f.has("doubleDoor"))        DashboardAdmin.setDoubleDoorEnabled(f.get("doubleDoor").getAsBoolean());
                if (f.has("rightClickHarvest")) DashboardAdmin.setRightClickHarvestEnabled(f.get("rightClickHarvest").getAsBoolean());
                if (f.has("dispenserHarvest"))  DashboardAdmin.setDispenserHarvestEnabled(f.get("dispenserHarvest").getAsBoolean());
                if (f.has("mailSpy"))           DashboardAdmin.setMailSpyEnabled(f.get("mailSpy").getAsBoolean());
            }

            if (root.has("cooldownWarp")) DashboardAdmin.setCooldownWarp(root.get("cooldownWarp").getAsInt());
            if (root.has("cooldownRtp"))  DashboardAdmin.setCooldownRtp(root.get("cooldownRtp").getAsInt());

            if (root.has("warps")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("warps").entrySet()) {
                    JsonObject w = e.getValue().getAsJsonObject();
                    WarpManager.getWarps().put(e.getKey(), new BlockPos(w.get("x").getAsInt(), w.get("y").getAsInt(), w.get("z").getAsInt()));
                    if (w.has("dim")) WarpManager.getWarpsDim().put(e.getKey(), w.get("dim").getAsString());
                }
            }

            if (root.has("scheduledBroadcasts")) {
                for (JsonElement el : root.getAsJsonArray("scheduledBroadcasts")) {
                    JsonObject entry = el.getAsJsonObject();
                    DashboardAdmin.addScheduledBroadcast(
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

