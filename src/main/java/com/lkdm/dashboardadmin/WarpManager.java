package com.lkdm.dashboardadmin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Warps publics (création, suppression, téléportation dimension-aware, cooldown d'usage).
 *
 * Extrait de la god-class {@link DashboardAdmin}. L'état des warps vit désormais ici ;
 * la persistance reste gérée par {@link ServerConfig} via {@link #getWarps()} / {@link #getWarpsDim()}.
 */
public final class WarpManager {

    private WarpManager() {}

    private static final Map<String, BlockPos> warps       = new LinkedHashMap<>();
    private static final Map<String, String>   warpsDim    = new LinkedHashMap<>();
    private static final Map<UUID, Long>        lastWarpUse = new java.util.HashMap<>();

    public static Map<String, BlockPos> getWarps()       { return warps; }
    public static Map<String, String>   getWarpsDim()    { return warpsDim; }
    public static Map<UUID, Long>       getLastWarpUse() { return lastWarpUse; }

    /** Warps publics : "nom:x,y,z,dim;..." (le nom d'un warp ne contient ni ':' ni ';'). */
    public static String getWarpsSerialized() {
        StringJoiner sj = new StringJoiner(";");
        for (Map.Entry<String, BlockPos> e : warps.entrySet()) {
            BlockPos p = e.getValue();
            sj.add(e.getKey() + ":" + p.getX() + "," + p.getY() + "," + p.getZ() + ","
                + warpsDim.getOrDefault(e.getKey(), "minecraft:overworld"));
        }
        return sj.toString();
    }

    /** Téléporte vers un warp (dimension comprise) — utilisé par le GUI admin et le menu joueur. */
    public static void teleportToWarp(ServerPlayer player, String name) {
        BlockPos pos = warps.get(name);
        if (pos == null) { player.sendSystemMessage(Component.literal("§cWarp §e" + name + " §cintrouvable.")); return; }
        String dimId = warpsDim.getOrDefault(name, "minecraft:overworld");
        ResourceKey<Level> dimKey = ResourceKey.create(Level.OVERWORLD.registryKey(), ResourceLocation.parse(dimId));
        ServerLevel targetLevel = player.getServer().getLevel(dimKey);
        if (targetLevel == null) targetLevel = (ServerLevel) player.level();
        DashboardAdmin.savePosition(player);
        player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("§aTéléporté au warp §e'" + name + "'§a."));
    }
}
