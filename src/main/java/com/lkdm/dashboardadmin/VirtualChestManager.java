package com.lkdm.dashboardadmin;

import com.google.gson.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
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

    // Registres du serveur, nécessaires pour (dé)sérialiser les composants d'items
    // (enchantements, nom personnalisé, durabilité…). Capturés au démarrage du serveur.
    private static HolderLookup.Provider registries;

    public static NonNullList<ItemStack> getChest(UUID uuid) {
        return playerChests.computeIfAbsent(uuid, k -> NonNullList.withSize(9, ItemStack.EMPTY));
    }

    public static void save() {
        if (registries == null) return;
        JsonObject data = new JsonObject();
        for (var entry : playerChests.entrySet()) {
            JsonArray list = new JsonArray();
            NonNullList<ItemStack> items = entry.getValue();
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) continue;
                JsonObject item = new JsonObject();
                item.addProperty("slot", i);
                // NBT complet de l'item : préserve enchantements / nom / composants.
                CompoundTag tag = new CompoundTag();
                stack.save(registries, tag);
                item.addProperty("nbt", tag.toString());
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
                for (int i = 0; i < items.size(); i++) {
                    JsonObject item = items.get(i).getAsJsonObject();
                    if (item.has("nbt")) {
                        // Nouveau format : NBT complet avec slot explicite.
                        int slot = item.has("slot") ? item.get("slot").getAsInt() : i;
                        if (slot < 0 || slot >= 9) continue;
                        try {
                            CompoundTag tag = TagParser.parseTag(item.get("nbt").getAsString());
                            list.set(slot, ItemStack.parseOptional(registries, tag));
                        } catch (Exception ex) { ex.printStackTrace(); }
                    } else if (item.has("id") && i < 9) {
                        // Ancien format (id + count, sans composants) : lu positionnellement.
                        var it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.get("id").getAsString()));
                        int count = item.has("count") ? item.get("count").getAsInt() : 1;
                        if (it != null && count > 0) list.set(i, new ItemStack(it, count));
                    }
                }
                playerChests.put(uuid, list);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> { registries = e.getServer().registryAccess(); load(); });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> save());
    }
}
