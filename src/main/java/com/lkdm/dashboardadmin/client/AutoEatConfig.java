package com.lkdm.dashboardadmin.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Config CLIENT de l'auto-manger.
 *
 * <p>Contient 9 aliments « fantômes » (références au type d'item uniquement,
 * pas de vrais stacks) dont l'ordre des slots définit la priorité, un toggle
 * ON/OFF et un seuil de faim de déclenchement (17, 10 ou 6 sur 20).</p>
 *
 * <p>Persisté localement dans {@code config/dashboardadmin-autoeat.json}
 * (préférence purement client, rien côté serveur) — même esprit que
 * {@link ClientZoneCache}.</p>
 */
public final class AutoEatConfig {

    public static final int   SLOTS      = 9;
    public static final int[] THRESHOLDS = { 17, 10, 6 };

    /** Aliments configurés (null = slot vide). Index 0..8, priorité décroissante. */
    private static final Item[] foods = new Item[SLOTS];

    public static volatile boolean enabled = false;

    /** Panneau replié par défaut : il faut cliquer l'onglet pour le révéler. */
    public static volatile boolean panelVisible = false;

    /** Index courant dans {@link #THRESHOLDS}. */
    private static int thresholdIdx = 0;

    private AutoEatConfig() {}

    // ─── accès ──────────────────────────────────────────────────────────────────

    public static Item food(int i) { return (i >= 0 && i < SLOTS) ? foods[i] : null; }

    public static void setFood(int i, Item item) {
        if (i < 0 || i >= SLOTS) return;
        foods[i] = (item == null || item == Items.AIR) ? null : item;
        save();
    }

    public static void clearFood(int i) { setFood(i, null); }

    public static boolean hasAnyFood() {
        for (Item it : foods) if (it != null) return true;
        return false;
    }

    public static int threshold() { return THRESHOLDS[thresholdIdx]; }

    public static void cycleThreshold() {
        thresholdIdx = (thresholdIdx + 1) % THRESHOLDS.length;
        save();
    }

    public static void toggle() { enabled = !enabled; save(); }

    public static void togglePanel() { panelVisible = !panelVisible; save(); }

    /** true si le stack porte des propriétés de nourriture (consommable). */
    public static boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    // ─── persistance ──────────────────────────────────────────────────────────────

    private static final Path CONFIG = Paths.get("config/dashboardadmin-autoeat.json");
    private static final Gson GSON   = new Gson();

    public static void load() {
        if (!Files.exists(CONFIG)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(CONFIG), JsonObject.class);
            if (root == null) return;
            if (root.has("enabled")) enabled = root.get("enabled").getAsBoolean();
            if (root.has("panelVisible")) panelVisible = root.get("panelVisible").getAsBoolean();
            if (root.has("threshold")) {
                int t = root.get("threshold").getAsInt();
                for (int i = 0; i < THRESHOLDS.length; i++) if (THRESHOLDS[i] == t) thresholdIdx = i;
            }
            if (root.has("foods")) {
                JsonArray arr = root.getAsJsonArray("foods");
                for (int i = 0; i < SLOTS && i < arr.size(); i++) {
                    String id = arr.get(i).isJsonNull() ? "" : arr.get(i).getAsString();
                    foods[i] = id.isEmpty() ? null : resolve(id);
                }
            }
        } catch (IOException | RuntimeException ignored) {}
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            root.addProperty("panelVisible", panelVisible);
            root.addProperty("threshold", threshold());
            JsonArray arr = new JsonArray();
            for (Item it : foods) arr.add(it == null ? "" : key(it));
            root.add("foods", arr);
            Files.writeString(CONFIG, GSON.toJson(root));
        } catch (IOException ignored) {}
    }

    private static Item resolve(String id) {
        try {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            return it == Items.AIR ? null : it;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String key(Item it) {
        return BuiltInRegistries.ITEM.getKey(it).toString();
    }
}
