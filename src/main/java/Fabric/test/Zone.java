package Fabric.test;

import net.minecraft.core.BlockPos;
import java.util.*;

public class Zone {

    /** Palette d'overlay partagée serveur/client. L'index 0 (vert) est la couleur historique. */
    public static final int[]    COLORS      = { 0x4CFF66, 0x00E5FF, 0xFFD821, 0xFF8800,
                                                 0xFF4444, 0xFF66CC, 0xAA66FF, 0x4477FF, 0xFFFFFF };
    public static final String[] COLOR_NAMES = { "Vert", "Cyan", "Jaune", "Orange",
                                                 "Rouge", "Rose", "Violet", "Bleu", "Blanc" };

    public String  name;
    public BlockPos min, max;
    public final Set<UUID> members = new LinkedHashSet<>();
    public boolean nightVision = false;
    /** When false the zone is inert: no protection, no flags, no night vision (but not deleted). */
    public boolean enabled = true;
    /** Index dans {@link #COLORS} pour le wireframe et le nom flottant. */
    public int colorIdx = 0;
    /** En cas de chevauchement, la zone de priorité la plus haute décide des flags. */
    public int priority = 0;
    /** Message action-bar à l'entrée dans la zone (vide = aucun). */
    public String greeting = "";
    /** Message action-bar à la sortie de la zone (vide = aucun). */
    public String farewell = "";
    /** Per-flag overrides. Absent flag = its {@link ZoneFlag#defaultAllowed}. */
    public final EnumMap<ZoneFlag, Boolean> flags = new EnumMap<>(ZoneFlag.class);

    public Zone(String name, BlockPos min, BlockPos max) {
        this.name = name;
        this.min  = min;
        this.max  = max;
    }

    /** Whether {@code flag} is currently allowed in this zone (falls back to its default). */
    public boolean flag(ZoneFlag flag) {
        return flags.getOrDefault(flag, flag.defaultAllowed);
    }

    public void setFlag(ZoneFlag flag, boolean allowed) {
        flags.put(flag, allowed);
    }

    public boolean contains(double x, double y, double z) {
        return x >= min.getX() && x <= max.getX() + 1
            && y >= min.getY() && y <= max.getY() + 1
            && z >= min.getZ() && z <= max.getZ() + 1;
    }

    // Empty members = open zone (all authorized)
    public boolean isAuthorized(UUID uuid) {
        return members.isEmpty() || members.contains(uuid);
    }

    public int color() {
        return COLORS[Math.floorMod(colorIdx, COLORS.length)];
    }

    public String colorName() {
        return COLOR_NAMES[Math.floorMod(colorIdx, COLOR_NAMES.length)];
    }

    public BlockPos center() {
        return new BlockPos(
            (min.getX() + max.getX()) / 2,
            (min.getY() + max.getY()) / 2,
            (min.getZ() + max.getZ()) / 2
        );
    }
}
