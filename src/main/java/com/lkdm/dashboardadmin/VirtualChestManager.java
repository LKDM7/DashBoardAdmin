package com.lkdm.dashboardadmin;

import com.google.gson.*;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class VirtualChestManager {
    private static final Path PATH = Paths.get("run/data/virtual_chests.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, NonNullList<ItemStack>> playerChests = new HashMap<>();

    public static NonNullList<ItemStack> getChest(UUID uuid) {
        return playerChests.computeIfAbsent(uuid, k -> NonNullList.withSize(9, ItemStack.EMPTY));
    }

    public static void save() {
        JsonObject data = new JsonObject();
        for (var entry : playerChests.entrySet()) {
            JsonArray list = new JsonArray();
            for (ItemStack stack : entry.getValue()) {
                JsonObject item = new JsonObject();
                item.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                item.addProperty("count", stack.getCount());
                list.add(item);
            }
            data.add(entry.getKey().toString(), list);
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
                NonNullList<ItemStack> list = NonNullList.withSize(9, ItemStack.EMPTY);
                JsonArray items = entry.getValue().getAsJsonArray();
                for (int i = 0; i < items.size() && i < 9; i++) {
                    JsonObject item = items.get(i).getAsJsonObject();
                    var it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.get("id").getAsString()));
                    if (it != null) list.set(i, new ItemStack(it, item.get("count").getAsInt()));
                }
                playerChests.put(uuid, list);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> load());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}

