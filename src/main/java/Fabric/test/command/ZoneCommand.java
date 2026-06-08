package Fabric.test.command;

import Fabric.test.Zone;
import Fabric.test.networking.OpenZonePayload;
import Fabric.test.networking.ZoneActionPayload;
import com.mojang.brigadier.CommandDispatcher;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ZoneCommand {

    private static final Map<String, Zone>   zones      = new LinkedHashMap<>();
    private static final Map<UUID, BlockPos> wandA      = new HashMap<>();
    private static final Map<UUID, BlockPos> wandB      = new HashMap<>();
    private static final Map<UUID, String>   buildZone  = new HashMap<>();

    private record BuildSavedState(
        NonNullList<ItemStack> items,
        int xpLevel, float xpProgress, int totalXp,
        List<MobEffectInstance> effects,
        Vec3 entryPos
    ) {}
    private static final Map<UUID, BuildSavedState> buildSavedState = new HashMap<>();

    // UUIDs whose build state was reloaded from disk after a reboot — restored on login.
    private static final Set<UUID> pendingRestore = new HashSet<>();
    private static final Path BUILD_STATE_PATH = Paths.get("run/data/build_state.dat");

    public static Map<String, Zone> getZones() { return zones; }

    public static boolean isInBuildMode(UUID uuid) {
        return buildZone.containsKey(uuid);
    }

    public static boolean isActivelyUsedZone(double x, double y, double z) {
        for (Map.Entry<UUID, String> e : buildZone.entrySet()) {
            Zone zone = zones.get(e.getValue());
            if (zone != null && zone.contains(x, y, z)) return true;
        }
        return false;
    }

    private static boolean isWand(ItemStack stack) {
        if (stack.getItem() != Items.BLAZE_ROD) return false;
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name != null && name.getString().contains("Baguette de Zone");
    }

    private static boolean isBlockedEntity(Entity entity) {
        return entity instanceof net.minecraft.world.entity.vehicle.MinecartChest
            || entity instanceof net.minecraft.world.entity.vehicle.MinecartHopper
            || entity instanceof net.minecraft.world.entity.vehicle.ChestBoat
            || entity instanceof net.minecraft.world.entity.animal.horse.Mule
            || entity instanceof net.minecraft.world.entity.animal.horse.Donkey
            || entity instanceof net.minecraft.world.entity.animal.horse.Llama
            || entity instanceof net.minecraft.world.entity.decoration.ItemFrame
            || entity instanceof net.minecraft.world.entity.decoration.ArmorStand;
    }

    // ─── Commands ────────────────────────────────────────────────────────────────

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zone")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("tool").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ItemStack wand = new ItemStack(Items.BLAZE_ROD);
                wand.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§6✦ Baguette de Zone §6✦"));
                player.getInventory().add(wand);
                player.sendSystemMessage(Component.literal(
                    "§a✦ Baguette reçue §7— §fClic gauche §7= Point A§7, §fClic droit §7= Point B"));
                return 1;
            }))
            .then(Commands.literal("admin").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                sendZoneScreen(player, ctx.getSource().getServer());
                return 1;
            }))
            .then(Commands.literal("create")
                .then(Commands.argument("name",
                        com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                        BlockPos a = wandA.get(player.getUUID());
                        BlockPos b = wandB.get(player.getUUID());
                        if (a == null || b == null) {
                            player.sendSystemMessage(Component.literal(
                                "§cDéfinissez d'abord les points A et B avec la baguette."));
                            return 0;
                        }
                        if (zones.containsKey(name)) {
                            player.sendSystemMessage(Component.literal(
                                "§cUne zone §e" + name + " §cexiste déjà."));
                            return 0;
                        }
                        BlockPos min = new BlockPos(
                            Math.min(a.getX(), b.getX()),
                            Math.min(a.getY(), b.getY()),
                            Math.min(a.getZ(), b.getZ()));
                        BlockPos max = new BlockPos(
                            Math.max(a.getX(), b.getX()),
                            Math.max(a.getY(), b.getY()),
                            Math.max(a.getZ(), b.getZ()));
                        zones.put(name, new Zone(name, min, max));
                        wandA.remove(player.getUUID());
                        wandB.remove(player.getUUID());
                        int dx = max.getX()-min.getX()+1, dy = max.getY()-min.getY()+1, dz = max.getZ()-min.getZ()+1;
                        player.sendSystemMessage(Component.literal(
                            "§aZone §e" + name + " §acréée §7(" + dx + "×" + dy + "×" + dz + ")§a !"));
                        return 1;
                    })))
            .then(Commands.literal("delete")
                .then(Commands.argument("name",
                        com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> {
                        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                        deleteZone(name, ctx.getSource().getServer());
                        ctx.getSource().getPlayerOrException().sendSystemMessage(
                            Component.literal("§cZone §e" + name + " §csupprimée."));
                        return 1;
                    })))
            .then(Commands.literal("list").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (zones.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§8Aucune zone définie."));
                } else {
                    player.sendSystemMessage(Component.literal("§6§lZones actives :"));
                    for (Zone z : zones.values()) {
                        int dx = z.max.getX()-z.min.getX()+1;
                        int dy = z.max.getY()-z.min.getY()+1;
                        int dz = z.max.getZ()-z.min.getZ()+1;
                        player.sendSystemMessage(Component.literal(
                            "§7• §e" + z.name + " §8(" + dx + "×" + dy + "×" + dz +
                            ", §7" + z.members.size() + " membres§8)"));
                    }
                }
                return 1;
            }))
        );

        dispatcher.register(Commands.literal("build").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            UUID uuid = player.getUUID();
            if (buildZone.containsKey(uuid)) {
                exitBuildMode(player);
                player.sendSystemMessage(Component.literal("§c✘ Mode construction désactivé."));
                return 1;
            }
            Zone zone = getAuthorizedZoneAt(player);
            if (zone == null) {
                player.sendSystemMessage(Component.literal(
                    "§cVous devez être à l'intérieur d'une zone où vous êtes autorisé pour utiliser §6/build§c."));
                return 0;
            }
            enterBuildMode(player, zone.name);
            player.sendSystemMessage(Component.literal(
                "§a✔ Mode construction activé dans §e" + zone.name + "§a."));
            return 1;
        }));
    }

    // ─── Events (called from Test.onInitialize) ───────────────────────────────

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> loadBuildState(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> saveBuildState(e.getServer()));
    }

    // Restore players whose build state survived a reboot (loaded from disk).
    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        UUID uuid = sp.getUUID();
        if (!pendingRestore.remove(uuid)) return;          // only states reloaded from disk
        if (!buildSavedState.containsKey(uuid)) { buildZone.remove(uuid); return; }
        exitBuildMode(sp);                                 // restores inventory/xp/effects + saves
        sp.sendSystemMessage(Component.literal(
            "§e⚠ Le serveur a redémarré pendant votre mode construction §7— §ainventaire restauré."));
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!sp.hasPermissions(2)) return;
        if (!isWand(sp.getItemInHand(event.getHand()))) return;
        BlockPos pos = event.getPos();
        wandA.put(sp.getUUID(), pos);
        sp.sendSystemMessage(Component.literal("§aPoint A §7→ §f(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"));
        if (wandB.containsKey(sp.getUUID()))
            sendBoxParticles(sp, pos, wandB.get(sp.getUUID()), (ServerLevel) event.getLevel());
        event.setCanceled(true);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (event.getLevel().isClientSide()) return;

        // Wand selection
        if (sp.hasPermissions(2) && isWand(sp.getItemInHand(event.getHand()))) {
            BlockPos pos = event.getHitVec().getBlockPos();
            wandB.put(sp.getUUID(), pos);
            sp.sendSystemMessage(Component.literal("§bPoint B §7→ §f(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"));
            if (wandA.containsKey(sp.getUUID()))
                sendBoxParticles(sp, wandA.get(sp.getUUID()), pos, (ServerLevel) event.getLevel());
            event.setCanceled(true);
            return;
        }

        // Build mode anti-glitch
        if (buildZone.containsKey(sp.getUUID())) {
            BlockPos pos = event.getHitVec().getBlockPos();

            // Tom's Storage : empêche d'ouvrir les interfaces du mod (terminal, connecteur, etc.)
            // tout en laissant poser les blocs (on refuse l'usage du bloc, pas celui de l'item).
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(event.getLevel().getBlockState(pos).getBlock())
                    .getNamespace().equals("toms_storage")) {
                sp.sendSystemMessage(Component.literal(
                    "§cInteraction avec Tom's Storage interdite en mode construction."), true);
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                return;
            }

            if (event.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity
                || event.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) {
                sp.sendSystemMessage(Component.literal("§cAccès aux conteneurs interdit en mode construction."), true);
                event.setCanceled(true);
                return;
            }
            if (sp.getItemInHand(event.getHand()).getItem() instanceof net.minecraft.world.item.SpawnEggItem) {
                sp.sendSystemMessage(Component.literal("§cSpawn eggs interdits en mode construction."), true);
                event.setCanceled(true);
            }
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (buildZone.containsKey(sp.getUUID())) {
            if (isBlockedEntity(event.getTarget())) {
                sp.sendSystemMessage(Component.literal("§cInteraction interdite en mode construction."), true);
                event.setCanceled(true);
            }
        } else {
            if (isActivelyUsedZone(event.getTarget().getX(), event.getTarget().getY(), event.getTarget().getZ())) {
                sp.sendSystemMessage(Component.literal("§cZone protégée — construction en cours."), true);
                event.setCanceled(true);
            }
        }
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        if (buildZone.containsKey(sp.getUUID())) return; // builders are always allowed
        if (sp.hasPermissions(2)) return;                 // admins bypass passive protection
        BlockPos pos = event.getPos();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        for (Zone z : zones.values()) {
            if (z.zoneProtected && z.contains(cx, cy, cz) && !z.isAuthorized(sp.getUUID())) {
                sp.sendSystemMessage(Component.literal("§cZone protégée."), true);
                event.setCanceled(true);
                return;
            }
        }
        if (isActivelyUsedZone(cx, cy, cz)) {
            sp.sendSystemMessage(Component.literal("§cZone protégée — construction en cours."), true);
            event.setCanceled(true);
        }
    }

    private static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (buildZone.containsKey(sp.getUUID())) return;
        if (sp.hasPermissions(2)) return;
        BlockPos pos = event.getPos();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        for (Zone z : zones.values()) {
            if (z.zoneProtected && z.contains(cx, cy, cz) && !z.isAuthorized(sp.getUUID())) {
                sp.sendSystemMessage(Component.literal("§cZone protégée."), true);
                event.setCanceled(true);
                return;
            }
        }
    }

    // ─── Tick (called every server tick from Test) ────────────────────────────

    public static void onTick(MinecraftServer server) {
        long tick = server.getTickCount();

        // Wand selection particles + build zone boundary — every 20 ticks
        if (tick % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.hasPermissions(2)) continue;
                BlockPos a = wandA.get(player.getUUID());
                BlockPos b = wandB.get(player.getUUID());
                if (a != null && b != null)
                    sendBoxParticles(player, a, b, (ServerLevel) player.level());
            }
            // Show zone boundaries to build-mode players (blue flame = active zone limit)
            for (Map.Entry<UUID, String> bEntry : buildZone.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(bEntry.getKey());
                if (player == null) continue;
                Zone z = zones.get(bEntry.getValue());
                if (z == null) continue;
                sendBoxParticles(player, z.min, z.max, (ServerLevel) player.level(), ParticleTypes.SOUL_FIRE_FLAME);
            }
        }

        // Night vision inside nightVision zones — every 40 ticks
        if (tick % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                for (Zone z : zones.values()) {
                    if (z.nightVision && z.contains(player.getX(), player.getY(), player.getZ())) {
                        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 80, 0, false, false));
                        break;
                    }
                }
            }
        }

        // Build-mode boundary enforcement — every 5 ticks
        if (tick % 5 == 0) {
            Iterator<Map.Entry<UUID, String>> it = buildZone.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, String> entry = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                Zone z = zones.get(entry.getValue());
                if (z == null) {
                    it.remove();
                    continue;
                }
                if (!z.contains(player.getX(), player.getY(), player.getZ())) {
                    double bx = Math.max(z.min.getX() + 0.5, Math.min(player.getX(), z.max.getX() + 0.5));
                    double by = Math.max(z.min.getY(),       Math.min(player.getY(), z.max.getY()));
                    double bz = Math.max(z.min.getZ() + 0.5, Math.min(player.getZ(), z.max.getZ() + 0.5));
                    player.connection.teleport(bx, by, bz, player.getYRot(), player.getXRot());
                    player.sendSystemMessage(
                        Component.literal("§c⚠ Impossible de sortir de §e" + z.name + " §cen mode construction."), true);
                }
            }
        }

        // Item drop prevention: return any item entities inside active build zones to the builder
        if (!buildZone.isEmpty()) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (!(e instanceof ItemEntity ie)) continue;
                    for (Map.Entry<UUID, String> bEntry : buildZone.entrySet()) {
                        Zone bz = zones.get(bEntry.getValue());
                        if (bz == null || !bz.contains(ie.getX(), ie.getY(), ie.getZ())) continue;
                        ServerPlayer bp = server.getPlayerList().getPlayer(bEntry.getKey());
                        if (bp != null && bp.level() == level) bp.getInventory().add(ie.getItem().copy());
                        ie.discard();
                        break;
                    }
                }
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private static void sendBoxParticles(ServerPlayer player, BlockPos a, BlockPos b, ServerLevel level) {
        sendBoxParticles(player, a, b, level, ParticleTypes.FLAME);
    }

    private static void sendBoxParticles(ServerPlayer player, BlockPos a, BlockPos b, ServerLevel level, SimpleParticleType particle) {
        int x1 = Math.min(a.getX(), b.getX()), y1 = Math.min(a.getY(), b.getY()), z1 = Math.min(a.getZ(), b.getZ());
        int x2 = Math.max(a.getX(), b.getX())+1, y2 = Math.max(a.getY(), b.getY())+1, z2 = Math.max(a.getZ(), b.getZ())+1;
        int span = Math.max(x2-x1, Math.max(y2-y1, z2-z1));
        double step = Math.max(0.5, span / 24.0);

        for (double x = x1; x <= x2; x += step) {
            level.sendParticles(player, particle, true, (double)x, (double)y1, (double)z1, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, particle, true, (double)x, (double)y2, (double)z1, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, particle, true, (double)x, (double)y1, (double)z2, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, particle, true, (double)x, (double)y2, (double)z2, 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (double y = y1; y <= y2; y += step) {
            level.sendParticles(player, particle, true, (double)x1, (double)y, (double)z1, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, particle, true, (double)x2, (double)y, (double)z1, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, particle, true, (double)x1, (double)y, (double)z2, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, particle, true, (double)x2, (double)y, (double)z2, 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (double z = z1; z <= z2; z += step) {
            level.sendParticles(player, particle, true, (double)x1, (double)y1, (double)z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, particle, true, (double)x2, (double)y1, (double)z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, particle, true, (double)x1, (double)y2, (double)z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(player, particle, true, (double)x2, (double)y2, (double)z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static Zone getAuthorizedZoneAt(ServerPlayer player) {
        for (Zone z : zones.values())
            if (z.contains(player.getX(), player.getY(), player.getZ()) && z.isAuthorized(player.getUUID()))
                return z;
        return null;
    }

    private static void enterBuildMode(ServerPlayer player, String zoneName) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int size = inv.getContainerSize();
        NonNullList<ItemStack> saved = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < size; i++) saved.set(i, inv.getItem(i).copy());

        List<MobEffectInstance> savedEffects = new ArrayList<>();
        for (MobEffectInstance eff : player.getActiveEffects()) {
            savedEffects.add(new MobEffectInstance(eff.getEffect(), eff.getDuration(),
                eff.getAmplifier(), eff.isAmbient(), eff.isVisible()));
        }

        buildSavedState.put(player.getUUID(), new BuildSavedState(
            saved,
            player.experienceLevel,
            player.experienceProgress,
            player.totalExperience,
            savedEffects,
            player.position()
        ));
        buildZone.put(player.getUUID(), zoneName);

        inv.clearContent();
        player.removeAllEffects();
        player.setGameMode(GameType.CREATIVE);
        saveBuildState(player.getServer());
    }

    private static void exitBuildMode(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.containerMenu.setCarried(ItemStack.EMPTY);

        BuildSavedState saved = buildSavedState.remove(player.getUUID());
        buildZone.remove(player.getUUID());

        if (saved != null) {
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            inv.clearContent();
            for (int i = 0; i < saved.items().size() && i < inv.getContainerSize(); i++)
                inv.setItem(i, saved.items().get(i));

            player.experienceLevel    = saved.xpLevel();
            player.experienceProgress = saved.xpProgress();
            player.totalExperience    = saved.totalXp();

            player.removeAllEffects();
            for (MobEffectInstance eff : saved.effects()) player.addEffect(eff);

            player.teleportTo((ServerLevel) player.level(),
                saved.entryPos().x, saved.entryPos().y, saved.entryPos().z,
                Set.of(), player.getYRot(), player.getXRot());
        }
        pendingRestore.remove(player.getUUID());
        saveBuildState(player.getServer());
    }

    public static void deleteZone(String name, MinecraftServer server) {
        zones.remove(name);
        Iterator<Map.Entry<UUID, String>> it = buildZone.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> e = it.next();
            if (!name.equals(e.getValue())) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) {
                player.setGameMode(GameType.SURVIVAL);
                player.containerMenu.setCarried(ItemStack.EMPTY);
                BuildSavedState saved = buildSavedState.remove(player.getUUID());
                if (saved != null) {
                    net.minecraft.world.entity.player.Inventory inv = player.getInventory();
                    inv.clearContent();
                    for (int i = 0; i < saved.items().size() && i < inv.getContainerSize(); i++)
                        inv.setItem(i, saved.items().get(i));
                    player.experienceLevel    = saved.xpLevel();
                    player.experienceProgress = saved.xpProgress();
                    player.totalExperience    = saved.totalXp();
                    player.removeAllEffects();
                    for (MobEffectInstance eff : saved.effects()) player.addEffect(eff);
                    player.teleportTo((ServerLevel) player.level(),
                        saved.entryPos().x, saved.entryPos().y, saved.entryPos().z,
                        Set.of(), player.getYRot(), player.getXRot());
                }
                player.sendSystemMessage(Component.literal(
                    "§c⚠ La zone §e" + name + " §ca été supprimée. Inventaire restauré."));
            }
            buildSavedState.remove(e.getKey());
            pendingRestore.remove(e.getKey());
            it.remove();
        }
        saveBuildState(server);
    }

    // ─── Persistence (reboot/crash-safe build state) ──────────────────────────

    // Serializes the live build state (inventory + xp + effects + entry pos) to disk so a
    // server reboot mid-/build never destroys the player's real inventory. Without this the
    // in-memory maps are lost on shutdown and the builder is left stuck with the empty
    // creative inventory of build mode.
    public static void saveBuildState(MinecraftServer server) {
        if (server == null) return;
        HolderLookup.Provider reg = server.registryAccess();
        CompoundTag root = new CompoundTag();
        ListTag players = new ListTag();
        for (Map.Entry<UUID, String> entry : buildZone.entrySet()) {
            BuildSavedState st = buildSavedState.get(entry.getKey());
            if (st == null) continue;
            CompoundTag p = new CompoundTag();
            p.putUUID("uuid", entry.getKey());
            p.putString("zone", entry.getValue());
            p.putInt("xpLevel", st.xpLevel());
            p.putFloat("xpProgress", st.xpProgress());
            p.putInt("totalXp", st.totalXp());
            p.putDouble("x", st.entryPos().x);
            p.putDouble("y", st.entryPos().y);
            p.putDouble("z", st.entryPos().z);
            p.putInt("size", st.items().size());
            ListTag items = new ListTag();
            for (int i = 0; i < st.items().size(); i++) {
                ItemStack s = st.items().get(i);
                if (s.isEmpty()) continue;
                CompoundTag it = new CompoundTag();
                it.putInt("Slot", i);
                s.save(reg, it);
                items.add(it);
            }
            p.put("items", items);
            ListTag effects = new ListTag();
            for (MobEffectInstance eff : st.effects()) effects.add(eff.save());
            p.put("effects", effects);
            players.add(p);
        }
        root.put("players", players);
        try {
            Files.createDirectories(BUILD_STATE_PATH.getParent());
            NbtIo.writeCompressed(root, BUILD_STATE_PATH);
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public static void loadBuildState(MinecraftServer server) {
        if (server == null || !Files.exists(BUILD_STATE_PATH)) return;
        HolderLookup.Provider reg = server.registryAccess();
        try {
            CompoundTag root = NbtIo.readCompressed(BUILD_STATE_PATH, NbtAccounter.unlimitedHeap());
            ListTag players = root.getList("players", Tag.TAG_COMPOUND);
            for (int i = 0; i < players.size(); i++) {
                CompoundTag p = players.getCompound(i);
                UUID uuid = p.getUUID("uuid");
                int size = p.getInt("size");
                if (size <= 0) size = 41;
                NonNullList<ItemStack> list = NonNullList.withSize(size, ItemStack.EMPTY);
                ListTag items = p.getList("items", Tag.TAG_COMPOUND);
                for (int j = 0; j < items.size(); j++) {
                    CompoundTag it = items.getCompound(j);
                    int slot = it.getInt("Slot");
                    if (slot >= 0 && slot < size) list.set(slot, ItemStack.parseOptional(reg, it));
                }
                List<MobEffectInstance> effects = new ArrayList<>();
                ListTag effTag = p.getList("effects", Tag.TAG_COMPOUND);
                for (int j = 0; j < effTag.size(); j++) {
                    MobEffectInstance eff = MobEffectInstance.load(effTag.getCompound(j));
                    if (eff != null) effects.add(eff);
                }
                Vec3 pos = new Vec3(p.getDouble("x"), p.getDouble("y"), p.getDouble("z"));
                buildSavedState.put(uuid, new BuildSavedState(
                    list, p.getInt("xpLevel"), p.getFloat("xpProgress"), p.getInt("totalXp"), effects, pos));
                buildZone.put(uuid, p.getString("zone"));
                pendingRestore.add(uuid);
            }
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    // ─── GUI ──────────────────────────────────────────────────────────────────────

    public static void sendZoneScreen(ServerPlayer player, MinecraftServer server) {
        StringBuilder sb = new StringBuilder();
        for (Zone z : zones.values()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(z.name).append("|");
            sb.append(z.min.getX()).append(",").append(z.min.getY()).append(",").append(z.min.getZ()).append("|");
            sb.append(z.max.getX()).append(",").append(z.max.getY()).append(",").append(z.max.getZ()).append("|");
            StringJoiner members = new StringJoiner(",");
            for (UUID uuid : z.members) {
                String mName = Fabric.test.Test.getPlayerNameCache()
                    .getOrDefault(uuid, uuid.toString().substring(0, 8));
                members.add(uuid + ":" + mName);
            }
            sb.append(members).append("|");
            sb.append(z.nightVision).append("|").append(z.zoneProtected);
        }
        String online = server.getPlayerList().getPlayers().stream()
            .map(p -> p.getName().getString())
            .collect(Collectors.joining(";"));
        PacketDistributor.sendToPlayer(player, new OpenZonePayload(sb.toString(), online));
    }

    public static void handleAction(ZoneActionPayload payload, ServerPlayer admin, MinecraftServer server) {
        Zone z = zones.get(payload.zoneName());
        switch (payload.action()) {
            case "TP_ZONE" -> {
                if (z != null) {
                    BlockPos c = z.center();
                    admin.teleportTo((ServerLevel) admin.level(),
                        c.getX() + 0.5, c.getY(), c.getZ() + 0.5,
                        Set.of(), admin.getYRot(), admin.getXRot());
                }
            }
            case "DELETE_ZONE" -> {
                deleteZone(payload.zoneName(), server);
                sendZoneScreen(admin, server);
            }
            case "ADD_MEMBER" -> {
                if (z != null) {
                    ServerPlayer target = server.getPlayerList().getPlayerByName(payload.value());
                    if (target != null) {
                        z.members.add(target.getUUID());
                        Fabric.test.Test.getPlayerNameCache().put(target.getUUID(), target.getName().getString());
                        admin.sendSystemMessage(Component.literal(
                            "§a" + target.getName().getString() + " ajouté à §e" + z.name + "§a."));
                    } else {
                        admin.sendSystemMessage(Component.literal(
                            "§cJoueur §e" + payload.value() + " §cinconnu ou hors ligne."));
                    }
                    sendZoneScreen(admin, server);
                }
            }
            case "REMOVE_MEMBER" -> {
                if (z != null) {
                    try { z.members.remove(UUID.fromString(payload.value())); }
                    catch (IllegalArgumentException ignored) {}
                    sendZoneScreen(admin, server);
                }
            }
            case "TOGGLE_NIGHT_VISION" -> {
                if (z != null) {
                    z.nightVision = !z.nightVision;
                    Fabric.test.ZonePersistence.save();
                    sendZoneScreen(admin, server);
                }
            }
            case "TOGGLE_PROTECTED" -> {
                if (z != null) {
                    z.zoneProtected = !z.zoneProtected;
                    Fabric.test.ZonePersistence.save();
                    sendZoneScreen(admin, server);
                }
            }
            case "GIVE_TOOL" -> {
                ItemStack wand = new ItemStack(Items.BLAZE_ROD);
                wand.set(DataComponents.CUSTOM_NAME, Component.literal("§6✦ Baguette de Zone §6✦"));
                admin.getInventory().add(wand);
                admin.sendSystemMessage(Component.literal("§a✦ Baguette de zone ajoutée à votre inventaire."));
            }
            case "UPDATE_COORDS" -> {
                if (z != null) {
                    String[] p = payload.value().split(",");
                    if (p.length == 6) {
                        try {
                            int x1 = Integer.parseInt(p[0].trim()), y1 = Integer.parseInt(p[1].trim()), z1 = Integer.parseInt(p[2].trim());
                            int x2 = Integer.parseInt(p[3].trim()), y2 = Integer.parseInt(p[4].trim()), z2 = Integer.parseInt(p[5].trim());
                            z.min = new BlockPos(Math.min(x1,x2), Math.min(y1,y2), Math.min(z1,z2));
                            z.max = new BlockPos(Math.max(x1,x2), Math.max(y1,y2), Math.max(z1,z2));
                            admin.sendSystemMessage(Component.literal("§aCoordonnées de §e" + z.name + " §amises à jour."));
                        } catch (NumberFormatException ignored) {
                            admin.sendSystemMessage(Component.literal("§cCoordonnées invalides."));
                        }
                    }
                    sendZoneScreen(admin, server);
                }
            }
        }
    }
}
