package com.lkdm.dashboardadmin.command;

import com.lkdm.dashboardadmin.SrvLang;
import com.lkdm.dashboardadmin.Zone;
import com.lkdm.dashboardadmin.ZoneFlag;
import com.lkdm.dashboardadmin.networking.OpenZonePayload;
import com.lkdm.dashboardadmin.networking.WandSelectionPayload;
import com.lkdm.dashboardadmin.networking.ZoneActionPayload;
import com.lkdm.dashboardadmin.networking.ZoneSyncPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
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

    /** Dernière zone connue de chaque joueur, pour les messages d'entrée/sortie. */
    private static final Map<UUID, String> lastZoneOf = new HashMap<>();

    /**
     * Évaluation centrale d'un flag à une position. En cas de chevauchement, la zone de
     * priorité la plus haute décide ; à priorité égale, le blocage l'emporte (comportement
     * historique). Pour un flag d'accès, un joueur autorisé sur la zone décisive est exempté.
     */
    public static boolean isAllowed(ZoneFlag f, double x, double y, double z, UUID who) {
        int best = Integer.MIN_VALUE;
        boolean allowed = true;
        for (Zone zo : zones.values()) {
            if (!zo.enabled || !zo.contains(x, y, z)) continue;
            boolean zoneAllows = zo.flag(f) || (!f.areaRule && who != null && zo.isAuthorized(who));
            if (zo.priority > best) { best = zo.priority; allowed = zoneAllows; }
            else if (zo.priority == best) allowed = allowed && zoneAllows;
        }
        return allowed;
    }

    public static boolean isInBuildMode(UUID uuid) {
        return buildZone.containsKey(uuid);
    }

    /** Commandes vanilla (niveau OP) débloquées pour un joueur non-OP tant qu'il est en /build. */
    private static final Set<String> BUILD_ELEVATED_COMMANDS = Set.of("setblock");

    /**
     * En mode /build, autorise un joueur non-OP à lancer certaines commandes vanilla protégées
     * (setblock) : on ré-analyse la commande avec une source élevée au niveau 2, le temps de
     * cette exécution uniquement. Les OP ne sont pas concernés (déjà autorisés). La cible du
     * setblock est restreinte à la zone de build du joueur (les coords hors zone sont refusées).
     */
    public static void onCommand(CommandEvent event) {
        if (!com.lkdm.dashboardadmin.DashboardAdmin.isSetblockInBuild()) return; // option désactivée dans la config
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        CommandSourceStack source = parse.getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;
        String zoneName = buildZone.get(player.getUUID());
        if (zoneName == null) return; // pas en mode /build

        String input = parse.getReader().getString();
        int sp = input.indexOf(' ');
        String name = (sp < 0 ? input : input.substring(0, sp)).toLowerCase();
        if (!BUILD_ELEVATED_COMMANDS.contains(name)) return;

        // Sans zone valide (supprimée entre-temps), on n'élève rien : setblock reste bloqué.
        Zone zone = zones.get(zoneName);
        if (zone == null) return;

        CommandSourceStack elevated = source.withPermission(2);

        // Restriction zone : la cible doit être DANS la zone de build du joueur.
        if (sp > 0) {
            try {
                StringReader reader = new StringReader(input);
                reader.setCursor(sp + 1); // juste après « setblock »
                Coordinates coords = BlockPosArgument.blockPos().parse(reader);
                BlockPos target = coords.getBlockPos(elevated);
                if (!zone.contains(target.getX(), target.getY(), target.getZ())) {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player,
                        "§cVous ne pouvez modifier que des blocs §edans votre zone §6" + zoneName + "§c.",
                        "§cYou can only edit blocks §ewithin your zone §6" + zoneName + "§c.")));
                    event.setCanceled(true);
                    return;
                }
            } catch (Exception ignored) {
                // Coordonnées illisibles : on laisse la commande s'exécuter (élevée) et échouer
                // avec le message d'usage vanilla habituel.
            }
        }

        event.setParseResults(player.getServer().getCommands().getDispatcher().parse(input, elevated));
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
                player.sendSystemMessage(Component.literal(SrvLang.t(player,
                    "§a✦ Baguette reçue §7— §fClic gauche §7= Point A§7, §fClic droit §7= Point B",
                    "§a✦ Wand received §7— §fLeft click §7= Point A§7, §fRight click §7= Point B")));
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
                            player.sendSystemMessage(Component.literal(SrvLang.t(player,
                                "§cDéfinissez d'abord les points A et B avec la baguette.",
                                "§cSet points A and B with the wand first.")));
                            return 0;
                        }
                        if (zones.containsKey(name)) {
                            player.sendSystemMessage(Component.literal(SrvLang.t(player,
                                "§cUne zone §e" + name + " §cexiste déjà.",
                                "§cA zone §e" + name + " §calready exists.")));
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
                        syncZonesAll(ctx.getSource().getServer());            // la nouvelle zone apparaît chez tous les membres/ops
                        PacketDistributor.sendToPlayer(player, WandSelectionPayload.empty()); // efface la prévisualisation jaune
                        int dx = max.getX()-min.getX()+1, dy = max.getY()-min.getY()+1, dz = max.getZ()-min.getZ()+1;
                        player.sendSystemMessage(Component.literal(SrvLang.t(player,
                            "§aZone §e" + name + " §acréée §7(" + dx + "×" + dy + "×" + dz + ")§a !",
                            "§aZone §e" + name + " §acreated §7(" + dx + "×" + dy + "×" + dz + ")§a!")));
                        return 1;
                    })))
            .then(Commands.literal("delete")
                .then(Commands.argument("name",
                        com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> {
                        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                        deleteZone(name, ctx.getSource().getServer());
                        ctx.getSource().getPlayerOrException().sendSystemMessage(
                            Component.literal(SrvLang.t(ctx.getSource().getPlayerOrException(),
                                "§cZone §e" + name + " §csupprimée.",
                                "§cZone §e" + name + " §cdeleted.")));
                        return 1;
                    })))
            .then(Commands.literal("rename")
                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(zones.keySet(), b))
                .then(Commands.argument("newname", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String oldName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                        String newName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "newname");
                        Zone z = zones.get(oldName);
                        if (z == null) {
                            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cZone §e" + oldName + " §cintrouvable.", "§cZone §e" + oldName + " §cnot found.")));
                            return 0;
                        }
                        if (zones.containsKey(newName)) {
                            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cUne zone §e" + newName + " §cexiste déjà.", "§cA zone §e" + newName + " §calready exists.")));
                            return 0;
                        }
                        zones.remove(oldName);
                        z.name = newName;
                        zones.put(newName, z);
                        // Références par nom à migrer (mode build en cours, dernière zone connue)
                        buildZone.replaceAll((u, n) -> oldName.equals(n) ? newName : n);
                        lastZoneOf.replaceAll((u, n) -> oldName.equals(n) ? newName : n);
                        com.lkdm.dashboardadmin.ZonePersistence.save();
                        syncZonesAll(ctx.getSource().getServer());
                        player.sendSystemMessage(Component.literal(SrvLang.t(player,
                            "§aZone §e" + oldName + " §arenommée en §e" + newName + "§a.",
                            "§aZone §e" + oldName + " §arenamed to §e" + newName + "§a.")));
                        return 1;
                    }))))
            .then(Commands.literal("list").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (zones.isEmpty()) {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§8Aucune zone définie.", "§8No zones defined.")));
                } else {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§6§lZones actives :", "§6§lActive zones:")));
                    for (Zone z : zones.values()) {
                        int dx = z.max.getX()-z.min.getX()+1;
                        int dy = z.max.getY()-z.min.getY()+1;
                        int dz = z.max.getZ()-z.min.getZ()+1;
                        player.sendSystemMessage(Component.literal(SrvLang.t(player,
                            "§7• §e" + z.name + " §8(" + dx + "×" + dy + "×" + dz +
                            ", §7" + z.members.size() + " membres§8)",
                            "§7• §e" + z.name + " §8(" + dx + "×" + dy + "×" + dz +
                            ", §7" + z.members.size() + " members§8)")));
                    }
                }
                return 1;
            }))
            .then(Commands.literal("flag")
                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(zones.keySet(), b))
                .then(Commands.argument("flag", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        Arrays.stream(ZoneFlag.values()).map(Enum::name), b))
                .then(Commands.argument("state", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                        Zone z = zones.get(name);
                        if (z == null) {
                            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cZone §e" + name + " §cintrouvable.", "§cZone §e" + name + " §cnot found.")));
                            return 0;
                        }
                        ZoneFlag f = ZoneFlag.byName(
                            com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "flag").toUpperCase(Locale.ROOT));
                        if (f == null) {
                            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cFlag inconnu. Valeurs : §e", "§cUnknown flag. Values: §e")
                                + Arrays.stream(ZoneFlag.values()).map(Enum::name).collect(Collectors.joining(", "))));
                            return 0;
                        }
                        boolean state = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "state");
                        z.setFlag(f, state);
                        com.lkdm.dashboardadmin.ZonePersistence.save();
                        player.sendSystemMessage(Component.literal(SrvLang.t(player,
                            "§aFlag §e" + f.name() + " §ade la zone §e" + name + " §a→ " + (state ? "§aautorisé" : "§cbloqué") + "§a.",
                            "§aFlag §e" + f.name() + " §aof zone §e" + name + " §a→ " + (state ? "§aallowed" : "§cblocked") + "§a.")));
                        return 1;
                    })))))
        );

        dispatcher.register(Commands.literal("build").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            UUID uuid = player.getUUID();
            if (buildZone.containsKey(uuid)) {
                exitBuildMode(player);
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§c✘ Mode construction désactivé.", "§c✘ Build mode disabled.")));
                return 1;
            }
            Zone zone = getAuthorizedZoneAt(player);
            if (zone == null) {
                player.sendSystemMessage(Component.literal(SrvLang.t(player,
                    "§cVous devez être à l'intérieur d'une zone où vous êtes autorisé pour utiliser §6/build§c.",
                    "§cYou must be inside a zone where you are authorized to use §6/build§c.")));
                return 0;
            }
            enterBuildMode(player, zone.name);
            player.sendSystemMessage(Component.literal(SrvLang.t(player,
                "§a✔ Mode construction activé dans §e" + zone.name + "§a.",
                "§a✔ Build mode enabled in §e" + zone.name + "§a.")));
            return 1;
        }));
    }

    // ─── Events (called from DashboardAdmin.onInitialize) ───────────────────────────────

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onZonePvp);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onZoneMobSpawn);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onZoneExplosion);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onItemToss);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onItemPickup);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onCropTrample);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(ZoneCommand::onCommand);
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> loadBuildState(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> saveBuildState(e.getServer()));
    }

    // Restore players whose build state survived a reboot (loaded from disk).
    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        UUID uuid = sp.getUUID();
        syncZones(sp);                                     // overlay : zones visibles par ce joueur
        if (!pendingRestore.remove(uuid)) return;          // only states reloaded from disk
        if (!buildSavedState.containsKey(uuid)) { buildZone.remove(uuid); return; }
        exitBuildMode(sp);                                 // restores inventory/xp/effects + saves
        sp.sendSystemMessage(Component.literal(SrvLang.t(sp,
            "§e⚠ Le serveur a redémarré pendant votre mode construction §7— §ainventaire restauré.",
            "§e⚠ The server restarted during your build mode §7— §ainventory restored.")));
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!sp.hasPermissions(2)) return;
        if (!isWand(sp.getItemInHand(event.getHand()))) return;
        BlockPos pos = event.getPos();
        wandA.put(sp.getUUID(), pos);
        sp.sendSystemMessage(Component.literal("§aPoint A §7→ §f(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"));
        syncWandSelection(sp);
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
            syncWandSelection(sp);
            if (wandA.containsKey(sp.getUUID()))
                sendBoxParticles(sp, wandA.get(sp.getUUID()), pos, (ServerLevel) event.getLevel());
            event.setCanceled(true);
            return;
        }

        // Zone INTERACT / CONTAINER protection — block right-clicks for non-authorized players
        if (!sp.hasPermissions(2) && !buildZone.containsKey(sp.getUUID())) {
            BlockPos hit = event.getHitVec().getBlockPos();
            double cx = hit.getX() + 0.5, cy = hit.getY() + 0.5, cz = hit.getZ() + 0.5;
            if (!isAllowed(ZoneFlag.INTERACT, cx, cy, cz, sp.getUUID())) {
                sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cInteraction interdite dans cette zone.", "§cInteraction forbidden in this zone.")), true);
                event.setCanceled(true);
                return;
            }
            boolean isContainer = event.getLevel().getBlockEntity(hit) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
            if (isContainer && !isAllowed(ZoneFlag.CONTAINER, cx, cy, cz, sp.getUUID())) {
                sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cConteneurs interdits dans cette zone.", "§cContainers forbidden in this zone.")), true);
                event.setCanceled(true);
                return;
            }
        }

        // Build mode anti-glitch
        if (buildZone.containsKey(sp.getUUID())) {
            BlockPos pos = event.getHitVec().getBlockPos();

            // Tom's Storage : empêche d'ouvrir les interfaces du mod (terminal, connecteur, etc.)
            // tout en laissant poser les blocs (on refuse l'usage du bloc, pas celui de l'item).
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(event.getLevel().getBlockState(pos).getBlock())
                    .getNamespace().equals("toms_storage")) {
                sp.sendSystemMessage(Component.literal(SrvLang.t(sp,
                    "§cInteraction avec Tom's Storage interdite en mode construction.",
                    "§cTom's Storage interaction forbidden in build mode.")), true);
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                return;
            }

            if (event.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity
                || event.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) {
                sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cAccès aux conteneurs interdit en mode construction.", "§cContainer access forbidden in build mode.")), true);
                event.setCanceled(true);
                return;
            }
            if (sp.getItemInHand(event.getHand()).getItem() instanceof net.minecraft.world.item.SpawnEggItem) {
                sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cSpawn eggs interdits en mode construction.", "§cSpawn eggs forbidden in build mode.")), true);
                event.setCanceled(true);
            }
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // Seul le constructeur lui-même est restreint en /build ; les autres joueurs ne sont
        // régis que par les flags de la zone (le /build n'ajoute aucune restriction pour eux).
        if (buildZone.containsKey(sp.getUUID()) && isBlockedEntity(event.getTarget())) {
            sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cInteraction interdite en mode construction.", "§cInteraction forbidden in build mode.")), true);
            event.setCanceled(true);
        }
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        if (buildZone.containsKey(sp.getUUID())) return; // builders are always allowed
        if (sp.hasPermissions(2)) return;                 // admins bypass passive protection
        BlockPos pos = event.getPos();
        if (!isAllowed(ZoneFlag.BUILD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sp.getUUID())) {
            sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cZone protégée.", "§cProtected zone.")), true);
            event.setCanceled(true);
        }
    }

    private static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (buildZone.containsKey(sp.getUUID())) return;
        if (sp.hasPermissions(2)) return;
        BlockPos pos = event.getPos();
        if (!isAllowed(ZoneFlag.BUILD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sp.getUUID())) {
            sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cZone protégée.", "§cProtected zone.")), true);
            event.setCanceled(true);
        }
    }

    // ─── Zone flag enforcement (PvP / mob spawns / explosions / items / cultures) ──

    private static void onZonePvp(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer)) return;
        if (!isAllowed(ZoneFlag.PVP, target.getX(), target.getY(), target.getZ(), null))
            event.setCanceled(true);
    }

    private static void onZoneMobSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster mob)) return;
        if (!isAllowed(ZoneFlag.MOB_SPAWN, mob.getX(), mob.getY(), mob.getZ(), null))
            event.setSpawnCancelled(true);
    }

    private static void onZoneExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        event.getAffectedBlocks().removeIf(pos ->
            !isAllowed(ZoneFlag.EXPLOSIONS, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, null));
    }

    private static void onItemToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        // Mode construction : jeter des items exfiltrerait des objets créatifs dans le monde
        // survie (ils resteraient au sol après la restauration de l'inventaire).
        if (buildZone.containsKey(sp.getUUID())) {
            sp.getInventory().add(event.getEntity().getItem());
            sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cJeter des items est interdit en mode construction.", "§cDropping items is forbidden in build mode.")), true);
            event.setCanceled(true);
            return;
        }
        if (sp.hasPermissions(2)) return;
        if (isAllowed(ZoneFlag.ITEM_DROP, sp.getX(), sp.getY(), sp.getZ(), sp.getUUID())) return;
        // Annuler détruit l'entité item : on restitue le stack au joueur.
        sp.getInventory().add(event.getEntity().getItem());
        sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cJeter des items est interdit dans cette zone.", "§cDropping items is forbidden in this zone.")), true);
        event.setCanceled(true);
    }

    private static void onItemPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        // Mode construction : ramasser détruirait les items à la sortie (inventaire restauré).
        if (buildZone.containsKey(sp.getUUID())) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
            return;
        }
        if (sp.hasPermissions(2)) return;
        var item = event.getItemEntity();
        if (!isAllowed(ZoneFlag.ITEM_PICKUP, item.getX(), item.getY(), item.getZ(), sp.getUUID()))
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    private static void onCropTrample(BlockEvent.FarmlandTrampleEvent event) {
        BlockPos pos = event.getPos();
        if (!isAllowed(ZoneFlag.CROP_TRAMPLE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, null))
            event.setCanceled(true);
    }

    // ─── Tick (called every server tick from DashboardAdmin) ────────────────────────────

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

        // Entry/exit action-bar messages — every 10 ticks
        if (tick % 10 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Zone cur = null;
                for (Zone z : zones.values())
                    if (z.enabled && z.contains(player.getX(), player.getY(), player.getZ())
                        && (cur == null || z.priority > cur.priority)) cur = z;
                String curName = cur == null ? null : cur.name;
                String prev = lastZoneOf.get(player.getUUID());
                if (!Objects.equals(prev, curName)) {
                    Zone prevZone = prev != null ? zones.get(prev) : null;
                    if (prevZone != null && !prevZone.farewell.isEmpty())
                        player.sendSystemMessage(Component.literal("§7" + prevZone.farewell), true);
                    if (cur != null && !cur.greeting.isEmpty())
                        player.sendSystemMessage(Component.literal("§e" + cur.greeting), true);
                    if (curName == null) lastZoneOf.remove(player.getUUID());
                    else lastZoneOf.put(player.getUUID(), curName);
                }
            }
        }

        // ENTRY flag — push non-authorized players out of forbidden zones — every 5 ticks
        if (tick % 5 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.hasPermissions(2) || buildZone.containsKey(player.getUUID())) continue;
                if (player.isSpectator()) continue;
                if (isAllowed(ZoneFlag.ENTRY, player.getX(), player.getY(), player.getZ(), player.getUUID())) continue;
                Zone z = null;
                for (Zone zo : zones.values())
                    if (zo.enabled && !zo.flag(ZoneFlag.ENTRY)
                        && zo.contains(player.getX(), player.getY(), player.getZ())
                        && !zo.isAuthorized(player.getUUID())) { z = zo; break; }
                if (z == null) continue;
                pushOutOfZone(player, z);
            }
        }

        // Night vision inside nightVision zones — every 40 ticks
        if (tick % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                for (Zone z : zones.values()) {
                    if (z.enabled && z.nightVision && z.contains(player.getX(), player.getY(), player.getZ())) {
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
                        Component.literal(SrvLang.t(player,
                            "§c⚠ Impossible de sortir de §e" + z.name + " §cen mode construction.",
                            "§c⚠ Cannot leave §e" + z.name + " §cwhile in build mode.")), true);
                }
            }
        }

    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Téléporte le joueur juste au-delà de la face horizontale (x/z) la plus proche de la zone. */
    private static void pushOutOfZone(ServerPlayer player, Zone z) {
        double west  = player.getX() - (z.min.getX() - 1.0);
        double east  = (z.max.getX() + 2.0) - player.getX();
        double north = player.getZ() - (z.min.getZ() - 1.0);
        double south = (z.max.getZ() + 2.0) - player.getZ();
        double min = Math.min(Math.min(west, east), Math.min(north, south));
        double tx = player.getX(), tz = player.getZ();
        if      (min == west)  tx = z.min.getX() - 1.0;
        else if (min == east)  tx = z.max.getX() + 2.0;
        else if (min == north) tz = z.min.getZ() - 1.0;
        else                   tz = z.max.getZ() + 2.0;
        player.connection.teleport(tx, player.getY(), tz, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal(SrvLang.t(player,
            "§c⚠ L'accès à la zone §e" + z.name + " §cest interdit.",
            "§c⚠ Access to zone §e" + z.name + " §cis forbidden.")), true);
    }

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

    /** Première zone contenant le joueur (peu importe l'autorisation), ou null. */
    private static Zone getZoneAtAny(ServerPlayer player) {
        for (Zone z : zones.values())
            if (z.contains(player.getX(), player.getY(), player.getZ()))
                return z;
        return null;
    }

    /**
     * Info de build pour le menu joueur. Sérialise la zone où est le joueur (celle où il
     * construit s'il est en /build, sinon celle où il se tient physiquement).
     * Format : inBuild|nom|dx|dy|dz|minX,minY,minZ|maxX,maxY,maxZ|autorisé|active|FLAG:0/1;...
     * Chaîne vide si le joueur n'est dans aucune zone.
     */
    public static String getBuildInfoFor(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean inBuild = buildZone.containsKey(uuid);
        Zone z = inBuild ? zones.get(buildZone.get(uuid)) : getZoneAtAny(player);
        if (z == null) return "";
        int dx = z.max.getX() - z.min.getX() + 1;
        int dy = z.max.getY() - z.min.getY() + 1;
        int dz = z.max.getZ() - z.min.getZ() + 1;
        StringJoiner flags = new StringJoiner(";");
        for (ZoneFlag f : ZoneFlag.values()) flags.add(f.name() + ":" + (z.flag(f) ? "1" : "0"));
        return (inBuild ? "1" : "0") + "|" + z.name + "|" + dx + "|" + dy + "|" + dz + "|"
            + z.min.getX() + "," + z.min.getY() + "," + z.min.getZ() + "|"
            + z.max.getX() + "," + z.max.getY() + "," + z.max.getZ() + "|"
            + (z.isAuthorized(uuid) ? "1" : "0") + "|" + (z.enabled ? "1" : "0") + "|" + flags;
    }

    private static Zone getAuthorizedZoneAt(ServerPlayer player) {
        for (Zone z : zones.values())
            if (z.enabled && z.contains(player.getX(), player.getY(), player.getZ()) && z.isAuthorized(player.getUUID()))
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
                player.sendSystemMessage(Component.literal(SrvLang.t(player,
                    "§c⚠ La zone §e" + name + " §ca été supprimée. Inventaire restauré.",
                    "§c⚠ Zone §e" + name + " §cwas deleted. Inventory restored.")));
            }
            buildSavedState.remove(e.getKey());
            pendingRestore.remove(e.getKey());
            it.remove();
        }
        saveBuildState(server);
        syncZonesAll(server);   // la zone disparaît de l'overlay de tous les joueurs
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

    // ─── Overlay sync (canal séparé, volontairement minimal) ──────────────────

    /**
     * Envoie au joueur la liste des zones qu'il a le droit de voir (overlay client).
     * Filtre STRICT : membre explicite uniquement (PAS isAuthorized, qui ouvrirait
     * les zones sans membres à tout le monde). Un op voit tout. Ni membres ni flags
     * ne quittent le serveur — uniquement nom + coordonnées + enabled.
     */
    public static void syncZones(ServerPlayer player) {
        if (player == null) return;
        boolean op = player.hasPermissions(2);
        StringBuilder sb = new StringBuilder();
        for (Zone z : zones.values()) {
            if (!op && !z.members.contains(player.getUUID())) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(z.name).append("|")
              .append(z.min.getX()).append(",").append(z.min.getY()).append(",").append(z.min.getZ()).append("|")
              .append(z.max.getX()).append(",").append(z.max.getY()).append(",").append(z.max.getZ()).append("|")
              .append(z.enabled).append("|")
              .append(z.colorIdx);
        }
        PacketDistributor.sendToPlayer(player, new ZoneSyncPayload(sb.toString()));
    }

    /** Resynchronise tous les joueurs en ligne (après création/suppression/modif). */
    public static void syncZonesAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncZones(p);
    }

    /** Envoie au joueur l'état complet de sa sélection baguette (A/B) pour l'overlay. */
    private static void syncWandSelection(ServerPlayer sp) {
        BlockPos pa = wandA.get(sp.getUUID());
        BlockPos pb = wandB.get(sp.getUUID());
        PacketDistributor.sendToPlayer(sp, new WandSelectionPayload(
            pa != null, pa != null ? pa.getX() : 0, pa != null ? pa.getY() : 0, pa != null ? pa.getZ() : 0,
            pb != null, pb != null ? pb.getX() : 0, pb != null ? pb.getY() : 0, pb != null ? pb.getZ() : 0));
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
                String mName = com.lkdm.dashboardadmin.DashboardAdmin.getPlayerNameCache()
                    .getOrDefault(uuid, uuid.toString().substring(0, 8));
                members.add(uuid + ":" + mName);
            }
            sb.append(members).append("|");
            // Field 4 = nightVision, field 5 = legacy "protected" (= BUILD blocked), field 6 = flags.
            sb.append(z.nightVision).append("|").append(!z.flag(ZoneFlag.BUILD)).append("|");
            StringJoiner flagJoiner = new StringJoiner(";");
            for (ZoneFlag f : ZoneFlag.values()) flagJoiner.add(f.name() + ":" + (z.flag(f) ? "1" : "0"));
            sb.append(flagJoiner);
            // Field 7 = enabled, 8 = couleur, 9 = priorité, 10 = greeting, 11 = farewell,
            // 12 = joueurs en ligne actuellement DANS la zone.
            sb.append("|").append(z.enabled);
            sb.append("|").append(z.colorIdx);
            sb.append("|").append(z.priority);
            sb.append("|").append(sanitizeMsg(z.greeting));
            sb.append("|").append(sanitizeMsg(z.farewell));
            StringJoiner inside = new StringJoiner(",");
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (z.contains(p.getX(), p.getY(), p.getZ())) inside.add(p.getName().getString());
            sb.append("|").append(inside);
        }
        String online = server.getPlayerList().getPlayers().stream()
            .map(p -> p.getName().getString())
            .collect(Collectors.joining(";"));
        PacketDistributor.sendToPlayer(player, new OpenZonePayload(sb.toString(), online));
    }

    /** Applique une combinaison complète de flags (ordre = celui de la déclaration de ZoneFlag). */
    private static void applyPreset(Zone z, boolean build, boolean interact, boolean container,
                                    boolean entry, boolean itemDrop, boolean itemPickup,
                                    boolean pvp, boolean mobSpawn, boolean explosions, boolean cropTrample) {
        z.setFlag(ZoneFlag.BUILD, build);
        z.setFlag(ZoneFlag.INTERACT, interact);
        z.setFlag(ZoneFlag.CONTAINER, container);
        z.setFlag(ZoneFlag.ENTRY, entry);
        z.setFlag(ZoneFlag.ITEM_DROP, itemDrop);
        z.setFlag(ZoneFlag.ITEM_PICKUP, itemPickup);
        z.setFlag(ZoneFlag.PVP, pvp);
        z.setFlag(ZoneFlag.MOB_SPAWN, mobSpawn);
        z.setFlag(ZoneFlag.EXPLOSIONS, explosions);
        z.setFlag(ZoneFlag.CROP_TRAMPLE, cropTrample);
    }

    /** Les messages voyagent dans des formats délimités par | / \n / \t : on neutralise. */
    private static String sanitizeMsg(String s) {
        if (s == null) return "";
        return s.replace("|", " ").replace("\n", " ").replace("\t", " ").trim();
    }

    public static void handleAction(ZoneActionPayload payload, ServerPlayer admin, MinecraftServer server) {
        Zone z = zones.get(payload.zoneName());
        switch (payload.action()) {
            case "CYCLE_COLOR" -> {
                if (z != null) {
                    z.colorIdx = Math.floorMod(z.colorIdx + 1, Zone.COLORS.length);
                    com.lkdm.dashboardadmin.ZonePersistence.save();
                    sendZoneScreen(admin, server);
                    syncZonesAll(server);   // la couleur du wireframe change en direct
                }
            }
            case "SET_PRIORITY" -> {
                if (z != null) {
                    try {
                        z.priority = Math.max(-99, Math.min(99, Integer.parseInt(payload.value().trim())));
                        com.lkdm.dashboardadmin.ZonePersistence.save();
                        admin.sendSystemMessage(Component.literal(SrvLang.t(admin,
                            "§aPriorité de §e" + z.name + " §a→ §e" + z.priority + "§a.",
                            "§aPriority of §e" + z.name + " §a→ §e" + z.priority + "§a.")));
                    } catch (NumberFormatException ignored) {
                        admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cPriorité invalide.", "§cInvalid priority.")));
                    }
                    sendZoneScreen(admin, server);
                }
            }
            case "APPLY_PRESET" -> {
                if (z != null) {
                    switch (payload.value()) {
                        // Spawn protégé : tout bloqué pour les non-membres, monde pacifié.
                        case "SPAWN" -> applyPreset(z, false, false, false, true, true, true,
                                                       false, false, false, false);
                        // Arène PvP : PvP forcé, pas de grief, pas de spawn naturel.
                        case "ARENA" -> applyPreset(z, false, false, false, true, true, true,
                                                       true, false, false, false);
                        // Zone VIP : accès membres uniquement (entrée bloquée), tout protégé.
                        case "VIP"   -> applyPreset(z, false, false, false, false, true, true,
                                                       false, false, false, false);
                        default -> { return; }
                    }
                    com.lkdm.dashboardadmin.ZonePersistence.save();
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin,
                        "§aPreset §e" + payload.value() + " §aappliqué à §e" + z.name + "§a.",
                        "§aPreset §e" + payload.value() + " §aapplied to §e" + z.name + "§a.")));
                    sendZoneScreen(admin, server);
                }
            }
            case "SET_MESSAGES" -> {
                if (z != null) {
                    String[] parts = payload.value().split("\t", -1);
                    z.greeting = sanitizeMsg(parts.length > 0 ? parts[0] : "");
                    z.farewell = sanitizeMsg(parts.length > 1 ? parts[1] : "");
                    com.lkdm.dashboardadmin.ZonePersistence.save();
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aMessages de §e" + z.name + " §amis à jour.", "§aMessages of §e" + z.name + " §aupdated.")));
                    sendZoneScreen(admin, server);
                }
            }
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
                        com.lkdm.dashboardadmin.DashboardAdmin.getPlayerNameCache().put(target.getUUID(), target.getName().getString());
                        admin.sendSystemMessage(Component.literal(SrvLang.t(admin,
                            "§a" + target.getName().getString() + " ajouté à §e" + z.name + "§a.",
                            "§a" + target.getName().getString() + " added to §e" + z.name + "§a.")));
                    } else {
                        admin.sendSystemMessage(Component.literal(SrvLang.t(admin,
                            "§cJoueur §e" + payload.value() + " §cinconnu ou hors ligne.",
                            "§cPlayer §e" + payload.value() + " §cunknown or offline.")));
                    }
                    sendZoneScreen(admin, server);
                    syncZonesAll(server);   // le joueur ajouté doit voir sa zone apparaître sans relog
                }
            }
            case "REMOVE_MEMBER" -> {
                if (z != null) {
                    try { z.members.remove(UUID.fromString(payload.value())); }
                    catch (IllegalArgumentException ignored) {}
                    sendZoneScreen(admin, server);
                    syncZonesAll(server);   // le joueur retiré doit voir sa zone disparaître sans relog
                }
            }
            case "TOGGLE_NIGHT_VISION" -> {
                if (z != null) {
                    z.nightVision = !z.nightVision;
                    com.lkdm.dashboardadmin.ZonePersistence.save();
                    sendZoneScreen(admin, server);
                }
            }
            case "TOGGLE_FLAG" -> {
                if (z != null) {
                    ZoneFlag f = ZoneFlag.byName(payload.value());
                    if (f != null) {
                        z.setFlag(f, !z.flag(f));
                        com.lkdm.dashboardadmin.ZonePersistence.save();
                        sendZoneScreen(admin, server);
                    }
                }
            }
            case "TOGGLE_ENABLED" -> { // active / désactive la zone sans la supprimer
                if (z != null) {
                    z.enabled = !z.enabled;
                    com.lkdm.dashboardadmin.ZonePersistence.save();
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin,
                        "§eZone §6" + z.name + " §e→ " + (z.enabled ? "§aactivée" : "§cdésactivée") + "§e.",
                        "§eZone §6" + z.name + " §e→ " + (z.enabled ? "§aenabled" : "§cdisabled") + "§e.")));
                    sendZoneScreen(admin, server);
                    syncZonesAll(server);   // la boîte passe de vert à gris en direct chez les joueurs
                }
            }
            case "TOGGLE_PROTECTED" -> { // legacy alias → toggles the BUILD flag
                if (z != null) {
                    z.setFlag(ZoneFlag.BUILD, !z.flag(ZoneFlag.BUILD));
                    com.lkdm.dashboardadmin.ZonePersistence.save();
                    sendZoneScreen(admin, server);
                }
            }
            case "GIVE_TOOL" -> {
                ItemStack wand = new ItemStack(Items.BLAZE_ROD);
                wand.set(DataComponents.CUSTOM_NAME, Component.literal("§6✦ Baguette de Zone §6✦"));
                admin.getInventory().add(wand);
                admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§a✦ Baguette de zone ajoutée à votre inventaire.", "§a✦ Zone wand added to your inventory.")));
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
                            admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aCoordonnées de §e" + z.name + " §amises à jour.", "§aCoordinates of §e" + z.name + " §aupdated.")));
                        } catch (NumberFormatException ignored) {
                            admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cCoordonnées invalides.", "§cInvalid coordinates.")));
                        }
                    }
                    sendZoneScreen(admin, server);
                    syncZonesAll(server);   // nouvelles coordonnées reflétées dans l'overlay
                }
            }
        }
    }
}
