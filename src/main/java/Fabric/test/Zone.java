package Fabric.test;

import net.minecraft.core.BlockPos;
import java.util.*;

public class Zone {
    public String  name;
    public BlockPos min, max;
    public final Set<UUID> members = new LinkedHashSet<>();
    public boolean nightVision   = false;
    public boolean zoneProtected = false;

    public Zone(String name, BlockPos min, BlockPos max) {
        this.name = name;
        this.min  = min;
        this.max  = max;
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

    public BlockPos center() {
        return new BlockPos(
            (min.getX() + max.getX()) / 2,
            (min.getY() + max.getY()) / 2,
            (min.getZ() + max.getZ()) / 2
        );
    }
}
