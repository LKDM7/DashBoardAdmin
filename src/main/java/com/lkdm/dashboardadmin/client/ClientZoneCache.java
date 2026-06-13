package com.lkdm.dashboardadmin.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Cache CLIENT des zones visibles par ce joueur (reçues via ZoneSyncPayload)
 * et de la sélection baguette en cours (WandSelectionPayload).
 *
 * <p>Contient aussi le toggle de visualisation, persisté localement dans
 * {@code config/dashboardadmin-client.json} (préférence purement client,
 * rien côté serveur).</p>
 */
public final class ClientZoneCache {

    public record ClientZone(String name, int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ, boolean enabled, int colorIdx) {
        public BlockPos center() {
            return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        }
        public int color() {
            return com.lkdm.dashboardadmin.Zone.COLORS[Math.floorMod(colorIdx, com.lkdm.dashboardadmin.Zone.COLORS.length)];
        }
    }

    /** Zones visibles. Remplacée d'un bloc à chaque sync (lecture seule pour le renderer). */
    private static volatile List<ClientZone> zones = List.of();

    /** Toggle du bouton "VISUALISATION ZONES" de l'onglet Build. */
    public static volatile boolean overlayEnabled = false;

    /** Sélection baguette en cours (null = pas de point posé). */
    public static volatile BlockPos wandA = null;
    public static volatile BlockPos wandB = null;

    private ClientZoneCache() {}

    public static List<ClientZone> all() { return zones; }

    /** Parse le format de ZoneSyncPayload : une zone par ligne, champs séparés par '|'. */
    public static void update(String serialized) {
        if (serialized == null || serialized.isEmpty()) { zones = List.of(); return; }
        List<ClientZone> parsed = new ArrayList<>();
        for (String line : serialized.split("\n")) {
            String[] f = line.split("\\|");
            if (f.length < 4) continue;
            try {
                String[] mn = f[1].split(",");
                String[] mx = f[2].split(",");
                int colorIdx = f.length > 4 ? Integer.parseInt(f[4]) : 0;
                parsed.add(new ClientZone(f[0],
                    Integer.parseInt(mn[0]), Integer.parseInt(mn[1]), Integer.parseInt(mn[2]),
                    Integer.parseInt(mx[0]), Integer.parseInt(mx[1]), Integer.parseInt(mx[2]),
                    Boolean.parseBoolean(f[3]), colorIdx));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}
        }
        zones = List.copyOf(parsed);
    }

    public static void updateWand(boolean hasA, int ax, int ay, int az,
                                  boolean hasB, int bx, int by, int bz) {
        wandA = hasA ? new BlockPos(ax, ay, az) : null;
        wandB = hasB ? new BlockPos(bx, by, bz) : null;
    }

    /** Vidé à la déconnexion pour ne pas afficher les zones d'un autre serveur. */
    public static void clear() {
        zones = List.of();
        wandA = null;
        wandB = null;
    }

    // ─── Persistance locale du toggle ──────────────────────────────────────────

    private static final Path CONFIG = Paths.get("config/dashboardadmin-client.json");
    private static final Gson GSON = new Gson();

    public static void loadConfig() {
        if (!Files.exists(CONFIG)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(CONFIG), JsonObject.class);
            if (root != null && root.has("zoneOverlay"))
                overlayEnabled = root.get("zoneOverlay").getAsBoolean();
        } catch (IOException | RuntimeException ignored) {}
    }

    public static void saveConfig() {
        try {
            Files.createDirectories(CONFIG.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("zoneOverlay", overlayEnabled);
            Files.writeString(CONFIG, GSON.toJson(root));
        } catch (IOException ignored) {}
    }
}
