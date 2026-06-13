package Fabric.test;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;

public class DashGameEvents {

    // ─── Commands ─────────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        AdminCommand.register(dispatcher);
        Fabric.test.command.ZoneCommand.register(dispatcher);
        Test.registerTpaCommands(dispatcher);

        dispatcher.register(Commands.literal("groupaccept").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            GroupManager.handleAction(new GroupActionPayload("ACCEPT", ""), player, ctx.getSource().getServer());
            return 1;
        }));
        dispatcher.register(Commands.literal("groupdeny").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            GroupManager.handleAction(new GroupActionPayload("DENY", ""), player, ctx.getSource().getServer());
            return 1;
        }));

        // /mail
        dispatcher.register(Commands.literal("mail")
            .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
            .then(Commands.argument("message", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
            .executes(ctx -> {
                ServerPlayer sender = ctx.getSource().getPlayerOrException();
                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target");
                if (!Test.getPlayerSettings(target.getUUID()).allowPrivateMessages) {
                    sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas les messages privés."));
                    return 0;
                }
                if (Test.isIgnoring(target.getUUID(), sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas vos messages."));
                    return 0;
                }
                String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "message");
                Test.getLastMsg().put(target.getUUID(), sender.getUUID());
                target.sendSystemMessage(Component.literal("§e" + sender.getName().getString() + " §7: §f" + msg));
                sender.sendSystemMessage(Component.literal("§7[moi -> §e" + target.getName().getString() + "§7] §f" + msg));
                if (Test.getPlayerSettings(target.getUUID()).showChatNotifications)
                    target.sendSystemMessage(Component.literal("§e[✉] Nouveau message de §f" + sender.getName().getString()), true);
                PacketDistributor.sendToPlayer(target, new NotifPayload("MAIL", "§b✉ Message de §f" + sender.getName().getString()));
                Test.addLog(sender.getUUID(), "MP → " + target.getName().getString() + ": " + msg);
                Test.spyPrivateMessage(ctx.getSource().getServer(), sender, target, msg);
                return 1;
            }))));

        // /r
        dispatcher.register(Commands.literal("r")
            .then(Commands.argument("message", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
            .executes(ctx -> {
                ServerPlayer sender = ctx.getSource().getPlayerOrException();
                if (!Test.getLastMsg().containsKey(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal("§cAucun message à répondre.")); return 0;
                }
                java.util.UUID targetUUID = Test.getLastMsg().get(sender.getUUID());
                ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayer(targetUUID);
                if (target == null) { sender.sendSystemMessage(Component.literal("§cCe joueur n'est plus connecté.")); return 0; }
                String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "message");
                target.sendSystemMessage(Component.literal("§e" + sender.getName().getString() + " §7: §f" + msg));
                sender.sendSystemMessage(Component.literal("§7[moi -> §e" + target.getName().getString() + "§7] §f" + msg));
                Test.getLastMsg().put(target.getUUID(), sender.getUUID());
                Test.addLog(sender.getUUID(), "MP → " + target.getName().getString() + ": " + msg);
                Test.spyPrivateMessage(ctx.getSource().getServer(), sender, target, msg);
                return 1;
            })));

        // /rtp — téléportation aléatoire sécurisée (Overworld, 500-3000 blocs du spawn)
        dispatcher.register(Commands.literal("rtp").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (Fabric.test.command.ZoneCommand.isInBuildMode(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/rtp §cen mode construction."));
                return 0;
            }
            ServerLevel level = (ServerLevel) player.level();
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                player.sendSystemMessage(Component.literal("§c/rtp n'est utilisable que dans l'Overworld."));
                return 0;
            }
            if (!Test.checkCooldown(Test.getLastRtpUse(), player.getUUID(), Test.getCooldownRtp(), player, "/rtp")) return 0;

            net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
            java.util.Random rng = new java.util.Random();
            for (int attempt = 0; attempt < 15; attempt++) {
                double angle = rng.nextDouble() * Math.PI * 2;
                double dist  = 500 + rng.nextDouble() * 2500;
                int x = spawn.getX() + (int) (Math.cos(angle) * dist);
                int z = spawn.getZ() + (int) (Math.sin(angle) * dist);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y <= level.getMinBuildHeight() + 1) continue;
                net.minecraft.core.BlockPos groundPos = new net.minecraft.core.BlockPos(x, y - 1, z);
                net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(groundPos);
                if (!ground.getFluidState().isEmpty()) continue; // eau / lave
                if (ground.isAir()
                    || ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    || ground.is(net.minecraft.world.level.block.Blocks.CACTUS)
                    || ground.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                    || ground.is(net.minecraft.world.level.block.Blocks.FIRE)) continue;
                Test.savePosition(player); // /back ramène au point de départ
                player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal(
                    "§a✔ Téléporté aléatoirement en §e(" + x + ", " + y + ", " + z + ")§a — §7/back §apour revenir."));
                Test.addLog(player.getUUID(), "RTP → (" + x + ", " + y + ", " + z + ")");
                return 1;
            }
            // Échec : on rend le cooldown au joueur.
            Test.getLastRtpUse().remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§cAucune position sûre trouvée, réessayez."));
            return 0;
        }));

        // /lock
        dispatcher.register(Commands.literal("lock").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0, false);
            if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
                if (Test.isLocked(pos)) {
                    if (Test.getOwner(pos).equals(player.getUUID())) { Test.getAllLockedBlocks().remove(pos); player.sendSystemMessage(Component.literal("§aBloc déverrouillé.")); }
                    else player.sendSystemMessage(Component.literal("§cCe bloc ne vous appartient pas."));
                } else { Test.getAllLockedBlocks().put(pos, player.getUUID()); player.sendSystemMessage(Component.literal("§aBloc verrouillé.")); }
            }
            return 1;
        }));

        // /trust
        dispatcher.register(Commands.literal("trust").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target");
            if (player.getUUID().equals(target.getUUID())) { player.sendSystemMessage(Component.literal("§cVous ne pouvez pas vous faire confiance à vous-même.")); return 0; }
            Test.getTrusted(player.getUUID()).add(target.getUUID());
            Test.getPlayerNameCache().put(target.getUUID(), target.getName().getString());
            Test.getPlayerNameCache().put(player.getUUID(), player.getName().getString());
            player.sendSystemMessage(Component.literal("§a" + target.getName().getString() + " a maintenant accès à vos blocs verrouillés."));
            target.sendSystemMessage(Component.literal("§a" + player.getName().getString() + " vous a accordé l'accès à ses blocs verrouillés."));
            return 1;
        })));

        // /untrust
        dispatcher.register(Commands.literal("untrust").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target");
            Test.getTrusted(player.getUUID()).remove(target.getUUID());
            player.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'a plus accès à vos blocs verrouillés."));
            target.sendSystemMessage(Component.literal("§c" + player.getName().getString() + " vous a retiré l'accès à ses blocs verrouillés."));
            return 1;
        })));

        // /lockinfo
        dispatcher.register(Commands.literal("lockinfo").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0, false);
            if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) { player.sendSystemMessage(Component.literal("§cRegardez un bloc.")); return 0; }
            net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
            if (!Test.isLocked(pos)) { player.sendSystemMessage(Component.literal("§7Ce bloc n'est pas verrouillé.")); return 0; }
            java.util.UUID ownerUUID = Test.getOwner(pos);
            String ownerName = resolvePlayerName(ctx.getSource().getServer(), ownerUUID);
            java.util.Set<java.util.UUID> trusted = Test.getTrusted(ownerUUID);
            player.sendSystemMessage(Component.literal("§6§l[LockInfo]"));
            player.sendSystemMessage(Component.literal("§7Propriétaire : §e" + ownerName));
            if (trusted.isEmpty()) player.sendSystemMessage(Component.literal("§7Accès partagé : §cnul"));
            else {
                java.util.List<String> names = trusted.stream().map(uuid -> resolvePlayerName(ctx.getSource().getServer(), uuid)).collect(java.util.stream.Collectors.toList());
                player.sendSystemMessage(Component.literal("§7Accès partagé : §a" + String.join("§7, §a", names)));
            }
            return 1;
        }));

        // /sethome
        dispatcher.register(Commands.literal("sethome").then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string()).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (!player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                player.sendSystemMessage(Component.literal("§c/sethome n'est disponible que dans l'Overworld.")); return 0;
            }
            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
            var homes = Test.getPlayerHomes(player.getUUID());
            if (homes.size() >= Test.getMaxHomes() && !homes.containsKey(name)) {
                player.sendSystemMessage(Component.literal("§cLimite de §e" + Test.getMaxHomes() + " §chomes atteinte.")); return 0;
            }
            homes.put(name, player.blockPosition());
            Test.getPlayerHomesDim(player.getUUID()).put(name, player.level().dimension().location().toString());
            player.sendSystemMessage(Component.literal("§aHome §e'" + name + "' §aenregistré ! (§e" + homes.size() + "§a/§e" + Test.getMaxHomes() + "§a)"));
            return 1;
        })));

        // /back
        dispatcher.register(Commands.literal("back").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (Fabric.test.command.ZoneCommand.isInBuildMode(player.getUUID())) { player.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/back §cen mode construction.")); return 0; }
            if (!Test.checkCooldown(Test.getLastBackUse(), player.getUUID(), Test.getCooldownBack(), player, "/back")) return 0;
            net.minecraft.world.phys.Vec3 pos = Test.getLastPositions().get(player.getUUID());
            if (pos == null) { player.sendSystemMessage(Component.literal("§cAucune position de retour trouvée.")); return 0; }
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = Test.getLastPositionDims().get(player.getUUID());
            ServerLevel targetLevel = dim != null ? ctx.getSource().getServer().getLevel(dim) : null;
            if (targetLevel == null) targetLevel = (ServerLevel) player.level();
            Test.savePosition(player);
            player.teleportTo(targetLevel, pos.x, pos.y, pos.z, Set.of(), player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("§aRetour à la position précédente."));
            return 1;
        }));

        // /afk
        dispatcher.register(Commands.literal("afk").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            boolean afk = !Test.isAfk(player.getUUID());
            Test.afkPlayers.put(player.getUUID(), afk);
            if (afk) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§eVOUS ÊTES AFK")));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 40, 10));
            } else {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§aVOUS N'ÊTES PLUS AFK")));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 20, 10));
            }
            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + (afk ? " est AFK." : " n'est plus AFK.")), false);
            return 1;
        }));

        // /home
        dispatcher.register(Commands.literal("home")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                var homes = Test.getPlayerHomes(player.getUUID());
                if (homes.isEmpty()) player.sendSystemMessage(Component.literal("§cVous n'avez aucun home enregistré."));
                else player.sendSystemMessage(Component.literal("§aVos homes : §f" + String.join(", ", homes.keySet())));
                return 1;
            })
            .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
            .suggests((ctx, builder) -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return net.minecraft.commands.SharedSuggestionProvider.suggest(Test.getPlayerHomes(player.getUUID()).keySet(), builder);
            })
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                if (Fabric.test.command.ZoneCommand.isInBuildMode(player.getUUID())) { player.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/home §cen mode construction.")); return 0; }
                if (!Test.checkCooldown(Test.getLastHomeUse(), player.getUUID(), Test.getCooldownHome(), player, "/home")) return 0;
                net.minecraft.core.BlockPos pos = Test.getPlayerHomes(player.getUUID()).get(name);
                if (pos == null) { player.sendSystemMessage(Component.literal("§cHome '" + name + "' introuvable.")); return 0; }
                Test.savePosition(player);
                player.teleportTo((ServerLevel)player.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal("§aTéléporté au home '" + name + "'."));
                return 1;
            })));

        // /chest
        dispatcher.register(Commands.literal("chest").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (Fabric.test.command.ZoneCommand.isInBuildMode(player.getUUID())) { player.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/chest §cen mode construction.")); return 0; }
            NonNullList<ItemStack> items = VirtualChestManager.getChest(player.getUUID());
            net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(9);
            for (int i = 0; i < 9; i++) container.setItem(i, items.get(i));
            container.addListener(c -> { for (int i = 0; i < 9; i++) items.set(i, c.getItem(i)); });
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new VirtualChestMenu(id, inv, container), Component.literal("Coffre Virtuel")));
            return 1;
        }));

        // /report
        dispatcher.register(Commands.literal("report")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                PacketDistributor.sendToPlayer(player, new OpenReportPayload());
                return 1;
            })
            .then(Commands.argument("message", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String message = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "message");
                String name = player.getName().getString();
                Test.pendingReports.put(name, message);
                player.sendSystemMessage(Component.literal("§aVotre rapport a été envoyé aux administrateurs."));
                Component notif = Component.literal("§c§l[REPORT] §r§e" + name + " §7» §f" + message + "  ")
                    .append(Component.literal("[ACCEPTER]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/reportaccept " + name))))
                    .append(Component.literal("  "))
                    .append(Component.literal("[REFUSER]").withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/reportdeny " + name))));
                for (ServerPlayer op : ctx.getSource().getServer().getPlayerList().getPlayers())
                    if (op.hasPermissions(2)) { op.sendSystemMessage(notif); PacketDistributor.sendToPlayer(op, new NotifPayload("REPORT", "§c⚑ Report de §f" + name)); }
                Fabric.test.DiscordWebhook.sendReport(Test.getWebhookReports(), name, message, null);
                return 1;
            })));

        dispatcher.register(Commands.literal("reportaccept").requires(s -> s.hasPermission(2))
            .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                ServerPlayer admin = ctx.getSource().getPlayerOrException();
                String pName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player");
                if (!Test.pendingReports.containsKey(pName)) { admin.sendSystemMessage(Component.literal("§cAucun rapport de §e" + pName + "§c.")); return 0; }
                Test.acceptedReports.put(pName, Test.pendingReports.remove(pName));
                byte[] img = Test.reportImages.remove(pName); if (img != null) Test.acceptedReportImages.put(pName, img);
                ServerPlayer reporter = ctx.getSource().getServer().getPlayerList().getPlayerByName(pName);
                if (reporter != null) reporter.sendSystemMessage(Component.literal("§aVotre signalement a été pris en charge par un administrateur."));
                admin.sendSystemMessage(Component.literal("§aRapport de §e" + pName + " §apris en charge."));
                return 1;
            })));

        dispatcher.register(Commands.literal("reportdeny").requires(s -> s.hasPermission(2))
            .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                ServerPlayer admin = ctx.getSource().getPlayerOrException();
                String pName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player");
                if (Test.pendingReports.remove(pName) == null) { admin.sendSystemMessage(Component.literal("§cAucun rapport de §e" + pName + "§c.")); return 0; }
                admin.sendSystemMessage(Component.literal("§cRapport de §e" + pName + " §crefusé."));
                return 1;
            })));

        // /menu
        dispatcher.register(Commands.literal("menu").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PlayerSettings s = Test.getPlayerSettings(player.getUUID());
            String homesSerialized = Test.getPlayerHomes(player.getUUID()).entrySet().stream().map(e -> {
                String dimId = Test.getPlayerHomesDim(player.getUUID()).getOrDefault(e.getKey(), "minecraft:overworld");
                String dimShort = dimId.contains("nether") ? "NE" : dimId.contains("end") ? "END" : "OW";
                return e.getKey() + ":" + e.getValue().getX() + "," + e.getValue().getY() + "," + e.getValue().getZ() + ":" + dimShort;
            }).collect(java.util.stream.Collectors.joining("|"));
            String locksSerialized = Test.getAllLockedBlocks().entrySet().stream().filter(e -> e.getValue().equals(player.getUUID())).map(e -> e.getKey().getX() + "," + e.getKey().getY() + "," + e.getKey().getZ()).collect(java.util.stream.Collectors.joining("|"));
            String trustSerialized = Test.getTrusted(player.getUUID()).stream().map(uuid -> Test.getPlayerNameCache().getOrDefault(uuid, uuid.toString().substring(0, 8) + "…") + ":" + uuid.toString()).collect(java.util.stream.Collectors.joining("|"));
            int playTicks = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
            long totalSec = playTicks / 20L; long ph = totalSec / 3600, pm = (totalSec % 3600) / 60;
            int deaths = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
            int pKills = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS));
            int mKills = Test.getAllHostileMobKills().getOrDefault(player.getUUID(), 0);
            long blocks = (player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.WALK_ONE_CM)) + player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.SPRINT_ONE_CM))) / 100L;
            String statsSerialized = ph + "|" + pm + "|" + deaths + "|" + pKills + "|" + mKills + "|" + blocks + "|" + player.totalExperience;
            PacketDistributor.sendToPlayer(player, new OpenSettingsPayload(s.allowPrivateMessages, s.allowTpaRequests, s.allowTrades, s.showChatNotifications, s.showConnectionAlerts, Test.getCommandsSerialized(), homesSerialized, locksSerialized, trustSerialized, statsSerialized, Fabric.test.command.ZoneCommand.getBuildInfoFor(player), Test.getWarpsSerialized()));
            PacketDistributor.sendToPlayer(player, GroupManager.buildGroupPayload(player, ctx.getSource().getServer()));
            return 1;
        }));

        // /stats
        dispatcher.register(Commands.literal("stats").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            int playTicks = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
            long ts = playTicks / 20L; long hours = ts / 3600, minutes = (ts % 3600) / 60;
            int deaths = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
            int pKills = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS));
            int hostile = Test.getAllHostileMobKills().getOrDefault(player.getUUID(), 0);
            long walkCm = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.WALK_ONE_CM));
            long sprintCm = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.SPRINT_ONE_CM));
            long blks = (walkCm + sprintCm) / 100L;
            player.sendSystemMessage(Component.literal("§6§l━━━━ Vos statistiques ━━━━"));
            player.sendSystemMessage(Component.literal("§7Temps de jeu    §f" + hours + "h " + minutes + "m"));
            player.sendSystemMessage(Component.literal("§7Morts           §c" + deaths));
            player.sendSystemMessage(Component.literal("§7Kills joueurs   §e" + pKills));
            player.sendSystemMessage(Component.literal("§7XP totale       §a" + player.totalExperience));
            player.sendSystemMessage(Component.literal("§7Mobs hostiles   §d" + hostile));
            player.sendSystemMessage(Component.literal("§7Blocs parcourus §b" + blks));
            player.sendSystemMessage(Component.literal("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            return 1;
        }));

        // /seen
        dispatcher.register(Commands.literal("seen").then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
            ServerPlayer seeker = ctx.getSource().getPlayerOrException();
            String targetName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player");
            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
            if (online != null) { seeker.sendSystemMessage(Component.literal("§e" + targetName + " §7est actuellement §aconnecté§7.")); return 1; }
            java.util.Optional<java.util.Map.Entry<java.util.UUID, String>> entry = Test.getPlayerNameCache().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(targetName)).findFirst();
            if (entry.isEmpty()) { seeker.sendSystemMessage(Component.literal("§cJoueur §e" + targetName + " §cinconnu.")); return 0; }
            Long ts = Test.getLastSeenTimestamps().get(entry.get().getKey());
            if (ts == null) seeker.sendSystemMessage(Component.literal("§7Aucune donnée de connexion pour §e" + targetName + "§7."));
            else seeker.sendSystemMessage(Component.literal("§e" + targetName + " §7a été vu il y a §f" + Test.formatTimeAgo(ts) + "§7."));
            return 1;
        })));

        // /check
        dispatcher.register(Commands.literal("check").requires(s -> s.hasPermission(2)).then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
            ServerPlayer adm = ctx.getSource().getPlayerOrException();
            String targetName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player");
            MinecraftServer srv = ctx.getSource().getServer();
            adm.sendSystemMessage(Component.literal("§6§l━━━ Check : " + targetName + " ━━━"));
            ServerPlayer onlineTarget = srv.getPlayerList().getPlayerByName(targetName);
            adm.sendSystemMessage(Component.literal("§7Connecté : " + (onlineTarget != null ? "§aOUI" : "§cNON")));
            boolean banned = srv.getPlayerList().getBans().getEntries().stream().anyMatch(e -> e.getDisplayName().getString().equalsIgnoreCase(targetName));
            adm.sendSystemMessage(Component.literal("§7Banni : " + (banned ? "§cOUI" : "§aNON")));
            if (banned) srv.getPlayerList().getBans().getEntries().stream().filter(e -> e.getDisplayName().getString().equalsIgnoreCase(targetName)).findFirst().ifPresent(e -> adm.sendSystemMessage(Component.literal("§7Raison : §f" + (e.getReason() != null ? e.getReason() : "—"))));
            Test.getPlayerNameCache().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(targetName)).findFirst().ifPresent(cacheEntry -> {
                Long ts = Test.getLastSeenTimestamps().get(cacheEntry.getKey());
                if (onlineTarget != null) adm.sendSystemMessage(Component.literal("§7Dernière vue : §aMaintenant"));
                else if (ts != null) adm.sendSystemMessage(Component.literal("§7Dernière vue : §fil y a " + Test.formatTimeAgo(ts)));
                else adm.sendSystemMessage(Component.literal("§7Dernière vue : §8inconnue"));
                int logCount = Test.getPlayerLogs().getOrDefault(cacheEntry.getKey(), java.util.Collections.emptyList()).size();
                adm.sendSystemMessage(Component.literal("§7Logs : §f" + logCount + " entrée" + (logCount > 1 ? "s" : "")));
            });
            adm.sendSystemMessage(Component.literal("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            return 1;
        })));

        // /deal
        dispatcher.register(Commands.literal("deal").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> {
            ServerPlayer sender = ctx.getSource().getPlayerOrException();
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target");
            if (sender == target) { sender.sendSystemMessage(Component.literal("§cVous ne pouvez pas échanger avec vous-même.")); return 0; }
            if (Test.activeSessions.containsKey(sender.getUUID())) { sender.sendSystemMessage(Component.literal("§cVous avez déjà un échange en cours.")); return 0; }
            if (Test.activeSessions.containsKey(target.getUUID())) { sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " a déjà un échange en cours.")); return 0; }
            if (!Test.getPlayerSettings(target.getUUID()).allowTrades) { sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas les échanges.")); return 0; }
            if (Test.isIgnoring(target.getUUID(), sender.getUUID())) { sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas vos demandes.")); return 0; }
            Test.getPendingDeals().put(target.getUUID(), sender.getUUID());
            Test.getPendingDealTimestamps().put(target.getUUID(), System.currentTimeMillis());
            String sName = sender.getName().getString();
            Component msg = Component.literal("§e" + sName + " §7souhaite effectuer un échange. ")
                .append(Component.literal("[Y]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/dealaccept"))))
                .append(Component.literal(" "))
                .append(Component.literal("[N]").withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/dealdeny"))));
            target.sendSystemMessage(msg);
            PacketDistributor.sendToPlayer(target, new NotifPayload("DEAL", "§e⇄ Échange proposé par §f" + sName));
            sender.sendSystemMessage(Component.literal("§7Demande d'échange envoyée à §e" + target.getName().getString() + "§7."));
            return 1;
        })));

        dispatcher.register(Commands.literal("dealaccept").executes(ctx -> {
            ServerPlayer target = ctx.getSource().getPlayerOrException();
            java.util.UUID requesterUUID = Test.getPendingDeals().remove(target.getUUID());
            Test.getPendingDealTimestamps().remove(target.getUUID());
            if (requesterUUID == null) { target.sendSystemMessage(Component.literal("§cAucune demande d'échange en attente.")); return 0; }
            ServerPlayer requester = ctx.getSource().getServer().getPlayerList().getPlayer(requesterUUID);
            if (requester == null) { target.sendSystemMessage(Component.literal("§cCe joueur n'est plus connecté.")); return 0; }
            Test.DealSession session = new Test.DealSession(requesterUUID, target.getUUID());
            Test.activeSessions.put(requesterUUID, session);
            Test.activeSessions.put(target.getUUID(), session);
            Test.getPlayerNameCache().put(requester.getUUID(), requester.getName().getString());
            Test.getPlayerNameCache().put(target.getUUID(), target.getName().getString());
            Test.broadcastDealUpdate(session, ctx.getSource().getServer());
            return 1;
        }));

        dispatcher.register(Commands.literal("dealdeny").executes(ctx -> {
            ServerPlayer target = ctx.getSource().getPlayerOrException();
            java.util.UUID requesterUUID = Test.getPendingDeals().remove(target.getUUID());
            Test.getPendingDealTimestamps().remove(target.getUUID());
            if (requesterUUID != null) {
                ServerPlayer requester = ctx.getSource().getServer().getPlayerList().getPlayer(requesterUUID);
                if (requester != null) requester.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " a refusé votre demande d'échange."));
                target.sendSystemMessage(Component.literal("§cDemande refusée."));
            }
            return 1;
        }));

        // /ignore
        dispatcher.register(Commands.literal("ignore")
            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                if (player == target) { player.sendSystemMessage(Component.literal("§cVous ne pouvez pas vous ignorer vous-même.")); return 0; }
                Fabric.test.PlayerSettings s = Test.getPlayerSettings(player.getUUID());
                Test.getPlayerNameCache().put(target.getUUID(), target.getName().getString());
                if (s.ignoredPlayers.add(target.getUUID())) {
                    player.sendSystemMessage(Component.literal("§7Vous ignorez maintenant §e" + target.getName().getString() + "§7."));
                } else {
                    player.sendSystemMessage(Component.literal("§7Vous ignorez déjà §e" + target.getName().getString() + "§7."));
                }
                return 1;
            })));

        // /unignore
        dispatcher.register(Commands.literal("unignore")
            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                Fabric.test.PlayerSettings s = Test.getPlayerSettings(player.getUUID());
                if (s.ignoredPlayers.remove(target.getUUID())) {
                    player.sendSystemMessage(Component.literal("§7Vous n'ignorez plus §e" + target.getName().getString() + "§7."));
                } else {
                    player.sendSystemMessage(Component.literal("§7Vous n'ignoriez pas §e" + target.getName().getString() + "§7."));
                }
                return 1;
            })));

        // /setwarp <name>
        dispatcher.register(Commands.literal("setwarp")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                Test.getWarps().put(name, player.blockPosition());
                Test.getWarpsDim().put(name, player.level().dimension().location().toString());
                player.sendSystemMessage(Component.literal("§aWarp §e'" + name + "' §acréé à votre position."));
                Fabric.test.ServerConfig.save();
                return 1;
            })));

        // /delwarp <name>
        dispatcher.register(Commands.literal("delwarp")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
            .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Test.getWarps().keySet(), builder))
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                if (Test.getWarps().remove(name) != null) {
                    Test.getWarpsDim().remove(name);
                    player.sendSystemMessage(Component.literal("§cWarp §e'" + name + "' §csupprimé."));
                    Fabric.test.ServerConfig.save();
                } else {
                    player.sendSystemMessage(Component.literal("§cWarp §e'" + name + "' §cintrouvable."));
                }
                return 1;
            })));

        // /warp [name]
        dispatcher.register(Commands.literal("warp")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (Test.getWarps().isEmpty()) {
                    player.sendSystemMessage(Component.literal("§8Aucun warp disponible."));
                } else {
                    player.sendSystemMessage(Component.literal("§6Warps : §f" + String.join("§7, §f", Test.getWarps().keySet())));
                }
                return 1;
            })
            .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
            .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Test.getWarps().keySet(), builder))
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                if (Fabric.test.command.ZoneCommand.isInBuildMode(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/warp §cen mode construction."));
                    return 0;
                }
                if (!Test.checkCooldown(Test.getLastWarpUse(), player.getUUID(), Test.getCooldownWarp(), player, "/warp")) return 0;
                net.minecraft.core.BlockPos pos = Test.getWarps().get(name);
                if (pos == null) { player.sendSystemMessage(Component.literal("§cWarp §e'" + name + "' §cintrouvable.")); return 0; }
                String dimId = Test.getWarpsDim().getOrDefault(name, "minecraft:overworld");
                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.world.level.Level.OVERWORLD.registryKey(),
                    net.minecraft.resources.ResourceLocation.parse(dimId));
                ServerLevel targetLevel = player.getServer().getLevel(dimKey);
                if (targetLevel == null) targetLevel = (ServerLevel) player.level();
                Test.savePosition(player);
                player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal("§aTéléporté au warp §e'" + name + "'§a."));
                return 1;
            })));
    }

    // ─── Player login / logout ────────────────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        MinecraftServer server = player.getServer();
        // Première connexion : jamais vu déconnecté (persisté) ni en mémoire — à tester AVANT le put.
        boolean firstJoin = !Test.getLastSeenTimestamps().containsKey(player.getUUID())
                         && !Test.getPlayerNameCache().containsKey(player.getUUID());
        Test.getPlayerNameCache().put(player.getUUID(), player.getName().getString());
        Test.getLastActivityTime().put(player.getUUID(), System.currentTimeMillis());
        if (firstJoin) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§6✦ §e" + player.getName().getString() + " §6rejoint le serveur pour la première fois !"), false);
        } else {
            Component joinMsg = Component.literal("§a[+] §f" + player.getName().getString() + " §7a rejoint le serveur.");
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (Test.getPlayerSettings(p.getUUID()).showConnectionAlerts && !p.getUUID().equals(player.getUUID()))
                    p.sendSystemMessage(joinMsg);
        }
        if (!Test.getMotd().isEmpty())
            player.sendSystemMessage(Component.literal("§7" + Test.getMotd()));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        MinecraftServer server = player.getServer();
        java.util.UUID uid = player.getUUID();
        Test.getLastSeenTimestamps().put(uid, System.currentTimeMillis());
        Test.handleDealDisconnect(player, server);
        Test.getLastMsg().remove(uid);
        Test.getLastMsg().values().removeIf(v -> v.equals(uid));
        GroupManager.onPlayerDisconnect(uid, server);
        Test.afkPlayers.remove(uid);
        Test.lastPos.remove(uid);
        Test.getLastActivityTime().remove(uid);
        Test.getTpaRequests().remove(uid);
        Test.getPendingTpaTimestamps().remove(uid);
        Test.getTpaHere().remove(uid);
        Test.getTpaRequests().entrySet().removeIf(e -> { if (e.getValue().equals(uid)) { Test.getPendingTpaTimestamps().remove(e.getKey()); Test.getTpaHere().remove(e.getKey()); return true; } return false; });
        Component leftMsg = Component.literal("§c[-] §f" + player.getName().getString() + " §7a quitté le serveur.");
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (Test.getPlayerSettings(p.getUUID()).showConnectionAlerts && !p.getUUID().equals(uid))
                p.sendSystemMessage(leftMsg);
    }

    // ─── Living events ────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Save death position for /back
        if (event.getEntity() instanceof ServerPlayer sp) Test.savePosition(sp);
        // Keep-inventory: save state before death
        if (event.getEntity() instanceof ServerPlayer sp && Test.getPlayerSettings(sp.getUUID()).keepInventory) {
            net.minecraft.world.entity.player.Inventory inv = sp.getInventory();
            int size = inv.getContainerSize();
            NonNullList<ItemStack> copy = NonNullList.withSize(size, ItemStack.EMPTY);
            for (int i = 0; i < size; i++) { copy.set(i, inv.getItem(i).copy()); inv.setItem(i, ItemStack.EMPTY); }
            keepInvSavedStates.put(sp.getUUID(), new SavedState(copy, sp.experienceLevel, sp.experienceProgress, sp.totalExperience, AccessoriesCompat.saveAndClear(sp)));
        }
        // Mob kill tracking (runs before entity is actually removed — fine for counting)
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            Test.getAllHostileMobKills().merge(killer.getUUID(), 1, Integer::sum);
        }
        if (!(event.getEntity() instanceof ServerPlayer) && event.getEntity().hasCustomName()
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            String typeName = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getPath();
            Test.addLog(killer.getUUID(), "Tué [" + typeName + "] \"" + event.getEntity().getCustomName().getString() + "\"");
        }
    }

    // Saved state for keep-inventory (package-private, accessed by onPlayerClone)
    static final java.util.Map<java.util.UUID, SavedState> keepInvSavedStates = new java.util.HashMap<>();
    // Accessories restore is deferred one tick so the capability is fully attached on the new player
    static final java.util.Map<java.util.UUID, java.util.Map<String, NonNullList<ItemStack>>> pendingAccessoriesRestore = new java.util.HashMap<>();

    record SavedState(NonNullList<ItemStack> items, int xpLevel, float xpProgress, int totalXp,
                      java.util.Map<String, NonNullList<ItemStack>> accessories) {}

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        ServerPlayer oldPlayer = (ServerPlayer) event.getOriginal();
        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
        SavedState saved = keepInvSavedStates.remove(oldPlayer.getUUID());
        if (saved == null) return;
        net.minecraft.world.entity.player.Inventory inv = newPlayer.getInventory();
        for (int i = 0; i < saved.items().size() && i < inv.getContainerSize(); i++) inv.setItem(i, saved.items().get(i));
        newPlayer.experienceLevel    = saved.xpLevel();
        newPlayer.experienceProgress = saved.xpProgress();
        newPlayer.totalExperience    = saved.totalXp();
        if (!saved.accessories().isEmpty())
            pendingAccessoriesRestore.put(newPlayer.getUUID(), saved.accessories());
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target)) return;
        // PvP protection: cancel any player-sourced damage when PvP is disabled
        if (!Test.isPvpEnabled() && event.getSource().getEntity() instanceof ServerPlayer) {
            event.setCanceled(true);
            return;
        }
        // AFK protection from monsters
        if (Test.isAfk(target.getUUID()) && event.getSource().getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof ServerPlayer target) {
            if (!Test.isPvpEnabled()) { event.setCanceled(true); return; }
            if (Test.isAfk(target.getUUID())) event.setCanceled(true);
        }
    }

    // ─── Dimension change (freeze check) ─────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (Test.isFrozen(player.getUUID())) {
            // Teleport back to origin — destination level is now player's level
            ServerLevel origin = player.getServer().getLevel(event.getFrom());
            if (origin != null)
                player.teleportTo(origin, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYRot(), player.getXRot());
        }
    }

    // ─── Chat ─────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        String raw = event.getRawText();
        if (Test.isMuted(sender.getUUID())) {
            Test.addLog(sender.getUUID(), "Chat bloqué (muet): " + raw);
            sender.sendSystemMessage(Component.literal("§cVous êtes muet, vous ne pouvez pas écrire."));
            event.setCanceled(true);
            return;
        }
        if (raw.startsWith("@g ") && GroupManager.isInGroup(sender.getUUID())) {
            String text = raw.substring(3).trim();
            if (!text.isEmpty()) {
                java.util.UUID leader = GroupManager.getLeader(sender.getUUID());
                String colored = "§a[Groupe] §f" + sender.getName().getString() + "§7: §f" + text;
                GroupManager.getMembers(leader).forEach(uuid -> {
                    ServerPlayer m = ((ServerLevel) sender.level()).getServer().getPlayerList().getPlayer(uuid);
                    if (m != null) m.sendSystemMessage(Component.literal(colored));
                });
                Test.addLog(sender.getUUID(), "[Groupe] " + text);
            }
            event.setCanceled(true);
            return;
        }
        if (Test.isChatLocked() && !sender.hasPermissions(2)) {
            sender.sendSystemMessage(Component.literal("§cLe chat est actuellement bloqué par un admin."));
            event.setCanceled(true);
            return;
        }
        Test.addLog(sender.getUUID(), "Chat: " + raw);
        Test.addChatHistory(sender.getName().getString(), raw);
        Test.getLastActivityTime().put(sender.getUUID(), System.currentTimeMillis());

        // Per-player ignore: cancel and re-broadcast only to non-ignoring players
        boolean anyIgnoring = sender.getServer().getPlayerList().getPlayers().stream()
            .anyMatch(p -> Test.isIgnoring(p.getUUID(), sender.getUUID()));
        if (anyIgnoring) {
            event.setCanceled(true);
            Component chatMsg = Component.literal("§f<" + sender.getName().getString() + "§f> " + raw);
            for (ServerPlayer p : sender.getServer().getPlayerList().getPlayers())
                if (!Test.isIgnoring(p.getUUID(), sender.getUUID())) p.sendSystemMessage(chatMsg);
        }
    }

    // ─── Block interaction (lock + right-click harvest + double door) ─────────
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        net.minecraft.core.BlockPos pos = event.getHitVec().getBlockPos();
        net.minecraft.world.entity.player.Player player = event.getEntity();

        // Lock check
        if (Test.isLocked(pos) && !Test.getOwner(pos).equals(player.getUUID())
                && !Test.isTrusted(Test.getOwner(pos), player.getUUID())
                && !GroupManager.isTrustedByGroup(Test.getOwner(pos), player.getUUID())) {
            if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal("§cCe bloc est verrouillé."));
            event.setCanceled(true);
            return;
        }
        if (player instanceof ServerPlayer && Test.isLocked(pos)) {
            java.util.UUID ownerUUID = Test.getOwner(pos);
            if (ownerUUID.equals(player.getUUID()) || Test.isTrusted(ownerUUID, player.getUUID())) {
                if (event.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.Container)
                    Test.addLog(player.getUUID(), "Coffre ouvert à (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
            }
        }

        // Right-click harvest
        if (Test.isRightClickHarvestEnabled() && !player.isShiftKeyDown()
                && event.getHand() == InteractionHand.MAIN_HAND) {
            net.minecraft.world.level.block.state.BlockState bState = event.getLevel().getBlockState(pos);
            net.minecraft.world.level.block.Block bBlock = bState.getBlock();
            ItemStack held = player.getItemInHand(event.getHand());
            if (!held.is(net.minecraft.world.item.Items.BONE_MEAL)) {
                net.minecraft.server.level.ServerLevel sLevel = (net.minecraft.server.level.ServerLevel) event.getLevel();
                boolean harvested = false;
                if (bBlock instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(bState)) {
                    java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(bState, sLevel, pos, null, player, held);
                    event.getLevel().setBlock(pos, crop.getStateForAge(0), 3);
                    drops.forEach(d -> net.minecraft.world.level.block.Block.popResource(event.getLevel(), pos, d));
                    harvested = true;
                } else if (bBlock instanceof net.minecraft.world.level.block.NetherWartBlock && bState.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) == 3) {
                    java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(bState, sLevel, pos, null, player, held);
                    event.getLevel().setBlock(pos, bState.setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 0), 3);
                    drops.forEach(d -> net.minecraft.world.level.block.Block.popResource(event.getLevel(), pos, d));
                    harvested = true;
                } else if (bBlock instanceof net.minecraft.world.level.block.CocoaBlock && bState.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) == 2) {
                    java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(bState, sLevel, pos, null, player, held);
                    event.getLevel().setBlock(pos, bState.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 0), 3);
                    drops.forEach(d -> net.minecraft.world.level.block.Block.popResource(event.getLevel(), pos, d));
                    harvested = true;
                }
                if (harvested) { event.setCanceled(true); return; }
            }
        }

        // Double door
        if (Test.isDoubleDoorEnabled()) {
            net.minecraft.world.level.block.state.BlockState dState = event.getLevel().getBlockState(pos);
            if (dState.getBlock() instanceof net.minecraft.world.level.block.DoorBlock dBlock
                    && dState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && dState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                net.minecraft.core.Direction facing = dState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                boolean isOpen = dState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
                for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
                    net.minecraft.core.BlockPos partnerPos = pos.relative(side);
                    net.minecraft.world.level.block.state.BlockState pState = event.getLevel().getBlockState(partnerPos);
                    if (pState.getBlock() == dState.getBlock()
                            && pState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                            && pState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                            && pState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN) == isOpen) {
                        dBlock.setOpen(player, event.getLevel(), pState, partnerPos, !isOpen);
                        break;
                    }
                }
            }
        }
    }

    // ─── Block break (tree capitator + fast leaf decay) ───────────────────────
    private static volatile boolean isCapitating = false;

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        if (!event.getState().is(net.minecraft.tags.BlockTags.LOGS)) return;
        if (isCapitating) return;

        boolean didCapitate = false;
        if (Test.isTreeCapitatorEnabled()) {
            ItemStack tool = sp.getMainHandItem();
            if (tool.getItem() instanceof net.minecraft.world.item.AxeItem) {
                net.minecraft.core.BlockPos pos = event.getPos();
                net.minecraft.world.level.Level world = (net.minecraft.world.level.Level) event.getLevel();
                boolean hasLeaves = false;
                net.minecraft.core.BlockPos.MutableBlockPos mut = new net.minecraft.core.BlockPos.MutableBlockPos();
                outer:
                for (int dx = -3; dx <= 3; dx++) for (int dy = 0; dy <= 8; dy++) for (int dz = -3; dz <= 3; dz++) {
                    mut.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (world.getBlockState(mut).is(net.minecraft.tags.BlockTags.LEAVES)) { hasLeaves = true; break outer; }
                }
                if (hasLeaves) {
                    didCapitate = true;
                    java.util.Set<net.minecraft.core.BlockPos> visited = new java.util.HashSet<>();
                    java.util.Queue<net.minecraft.core.BlockPos> queue = new java.util.LinkedList<>();
                    visited.add(pos);
                    Test.addLogNeighbors(world, pos, visited, queue);
                    int maxBlocks = Math.min(150, tool.getMaxDamage() - tool.getDamageValue() - 1);
                    int broken = 0;
                    isCapitating = true;
                    try {
                        while (!queue.isEmpty() && broken < maxBlocks && !tool.isEmpty()) {
                            net.minecraft.core.BlockPos cur = queue.poll();
                            ((net.minecraft.world.level.Level) event.getLevel()).destroyBlock(cur, true, sp);
                            tool.hurtAndBreak(1, sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                            broken++;
                            if (Test.isFastLeafDecayEnabled()) Test.scheduleNearbyLeaves(world, cur);
                            Test.addLogNeighbors(world, cur, visited, queue);
                        }
                    } finally { isCapitating = false; }
                    if (Test.isFastLeafDecayEnabled()) Test.scheduleNearbyLeaves(world, pos);
                }
            }
        }
        if (Test.isFastLeafDecayEnabled() && !didCapitate)
            Test.scheduleNearbyLeaves((net.minecraft.world.level.Level) event.getLevel(), event.getPos());
    }

    // ─── Server tick ──────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Fabric.test.command.ZoneCommand.onTick(server);
        GroupManager.onTick(server);

        // Deferred accessories restore (one tick after respawn so the capability is ready)
        if (!pendingAccessoriesRestore.isEmpty()) {
            java.util.Iterator<java.util.Map.Entry<java.util.UUID, java.util.Map<String, NonNullList<ItemStack>>>> accIt = pendingAccessoriesRestore.entrySet().iterator();
            while (accIt.hasNext()) {
                java.util.Map.Entry<java.util.UUID, java.util.Map<String, NonNullList<ItemStack>>> entry = accIt.next();
                ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
                if (p != null) { AccessoriesCompat.restore(p, entry.getValue()); accIt.remove(); }
            }
        }

        long nowMs = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Test.isFrozen(player.getUUID())) {
                net.minecraft.world.phys.Vec3 fp = Test.frozenPositions.get(player.getUUID());
                if (fp != null && player.position().distanceToSqr(fp) > 0.001)
                    player.connection.teleport(fp.x, fp.y, fp.z, player.getYRot(), player.getXRot());
            }
            if (Test.isAfk(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§e[AFK] Vous êtes absent."), true);
                if (Test.lastPos.containsKey(player.getUUID()) && player.position().distanceToSqr(Test.lastPos.get(player.getUUID())) > 0.01) {
                    Test.afkPlayers.put(player.getUUID(), false);
                    Test.getLastActivityTime().put(player.getUUID(), nowMs);
                    player.sendSystemMessage(Component.literal("§eVous n'êtes plus AFK."));
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + " n'est plus AFK."), false);
                }
            } else if (Test.isAfkAutoEnabled() && !Test.isFrozen(player.getUUID())) {
                net.minecraft.world.phys.Vec3 prevPos = Test.lastPos.get(player.getUUID());
                if (prevPos != null && player.position().distanceToSqr(prevPos) > 0.001) {
                    Test.getLastActivityTime().put(player.getUUID(), nowMs);
                } else {
                    long lastAct = Test.getLastActivityTime().getOrDefault(player.getUUID(), nowMs);
                    if (nowMs - lastAct > (Test.getAfkDelayMinutes() * 60000L)) {
                        Test.afkPlayers.put(player.getUUID(), true);
                        Test.getLastActivityTime().put(player.getUUID(), nowMs);
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§eVOUS ÊTES AFK")));
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 40, 10));
                        server.getPlayerList().broadcastSystemMessage(Component.literal("§e" + player.getName().getString() + " est AFK (inactivité)."), false);
                    }
                }
            }
            Test.lastPos.put(player.getUUID(), player.position());
        }

        // Proportional sleep
        if (Test.isProportionalSleepEnabled()) {
            for (ServerLevel lvl : server.getAllLevels()) {
                if (!lvl.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) continue;
                java.util.List<ServerPlayer> inLvl = lvl.players();
                if (inLvl.isEmpty()) continue;
                long sleeping = inLvl.stream().filter(net.minecraft.world.entity.player.Player::isSleeping).count();
                if ((double) sleeping / inLvl.size() >= 0.30) {
                    long dayTime = lvl.getDayTime() % 24000;
                    if (dayTime >= 12542 || dayTime < 500) lvl.setDayTime(lvl.getDayTime() + 40);
                }
            }
        }

        // Fast leaf decay
        if (Test.isFastLeafDecayEnabled() && !Test.getPendingLeafDecayMap().isEmpty()) {
            Test.setLeafDecayCounter(Test.getLeafDecayCounter() + 1);
            if (Test.getLeafDecayCounter() % 4 == 0) {
                for (var dimEntry : Test.getPendingLeafDecayMap().entrySet()) {
                    ServerLevel targetLevel = server.getLevel(dimEntry.getKey());
                    if (targetLevel == null) { dimEntry.getValue().clear(); continue; }
                    java.util.Iterator<net.minecraft.core.BlockPos> it = dimEntry.getValue().iterator();
                    int processed = 0;
                    while (it.hasNext() && processed < 20) {
                        net.minecraft.core.BlockPos leafPos = it.next();
                        it.remove();
                        net.minecraft.world.level.block.state.BlockState ls = targetLevel.getBlockState(leafPos);
                        if (ls.is(net.minecraft.tags.BlockTags.LEAVES)
                                && ls.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT)
                                && !ls.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT)) {
                            targetLevel.destroyBlock(leafPos, true);
                        }
                        processed++;
                    }
                }
                Test.getPendingLeafDecayMap().values().removeIf(java.util.Set::isEmpty);
            }
        }

        // Restart programmé : annonces dégressives puis sauvegarde complète et arrêt propre.
        if (Test.restartTicks > 0) {
            Test.restartTicks--;
            int t = Test.restartTicks;
            if (t == 0) {
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§4§l⚠ Redémarrage du serveur — sauvegarde en cours…"), false);
                saveAll();
                server.getPlayerList().saveAll();
                server.saveAllChunks(true, true, true);
                server.halt(false);
            } else if (t % 20 == 0) {
                int sec = t / 20;
                String msg = switch (sec) {
                    case 1800 -> "30 minutes";
                    case 900  -> "15 minutes";
                    case 600  -> "10 minutes";
                    case 300  -> "5 minutes";
                    case 60   -> "1 minute";
                    case 30   -> "30 secondes";
                    case 10   -> "10 secondes";
                    case 5, 4, 3, 2, 1 -> sec + "…";
                    default   -> null;
                };
                if (msg != null)
                    server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§c⚠ Redémarrage du serveur dans §e" + msg), false);
            }
        }

        // ClearLag
        if (Test.clearLagTicks > 0) {
            Test.clearLagTicks--;
            if (Test.clearLagTicks == 0) {
                long count = 0;
                for (ServerLevel level : server.getAllLevels()) for (net.minecraft.world.entity.Entity e : level.getAllEntities()) if (e instanceof net.minecraft.world.entity.item.ItemEntity) { e.discard(); count++; }
                server.getPlayerList().broadcastSystemMessage(Component.literal("§eClearLag : §f" + count + " items supprimés."), false);
            }
        }
        if (Test.removeMobsTicks > 0) {
            Test.removeMobsTicks--;
            if (Test.removeMobsTicks == 0) {
                long count = 0;
                for (ServerLevel level : server.getAllLevels()) for (net.minecraft.world.entity.Entity e : level.getAllEntities()) if (e instanceof net.minecraft.world.entity.monster.Monster && !e.hasCustomName()) { e.discard(); count++; }
                server.getPlayerList().broadcastSystemMessage(Component.literal("§cMobs supprimés : §f" + count), false);
            }
        }

        // TPA and deal request expiry (every 20 ticks = 1 second)
        if (server.getTickCount() % 20 == 0) {
            long nowExpire = System.currentTimeMillis();
            java.util.Iterator<java.util.Map.Entry<java.util.UUID, Long>> tpaIt = Test.getPendingTpaTimestamps().entrySet().iterator();
            while (tpaIt.hasNext()) {
                java.util.Map.Entry<java.util.UUID, Long> te = tpaIt.next();
                if (nowExpire - te.getValue() > 60_000L) {
                    java.util.UUID targetUid = te.getKey();
                    java.util.UUID senderUid = Test.getTpaRequests().remove(targetUid);
                    Test.getTpaHere().remove(targetUid);
                    tpaIt.remove();
                    if (senderUid != null) {
                        ServerPlayer tpaSender = server.getPlayerList().getPlayer(senderUid);
                        ServerPlayer tpaTarget = server.getPlayerList().getPlayer(targetUid);
                        if (tpaSender != null) tpaSender.sendSystemMessage(Component.literal("§7Votre demande TPA vers §e" + Test.getPlayerNameCache().getOrDefault(targetUid, "?") + "§7 a expiré."));
                        if (tpaTarget != null) tpaTarget.sendSystemMessage(Component.literal("§7La demande TPA de §e" + Test.getPlayerNameCache().getOrDefault(senderUid, "?") + "§7 a expiré."));
                    }
                }
            }
            java.util.Iterator<java.util.Map.Entry<java.util.UUID, Long>> dealIt = Test.getPendingDealTimestamps().entrySet().iterator();
            while (dealIt.hasNext()) {
                java.util.Map.Entry<java.util.UUID, Long> de = dealIt.next();
                if (nowExpire - de.getValue() > 60_000L) {
                    java.util.UUID targetUid = de.getKey();
                    java.util.UUID senderUid = Test.getPendingDeals().remove(targetUid);
                    dealIt.remove();
                    if (senderUid != null) {
                        ServerPlayer dealSender = server.getPlayerList().getPlayer(senderUid);
                        ServerPlayer dealTarget = server.getPlayerList().getPlayer(targetUid);
                        if (dealSender != null) dealSender.sendSystemMessage(Component.literal("§7Votre demande d'échange a expiré."));
                        if (dealTarget != null) dealTarget.sendSystemMessage(Component.literal("§7La demande d'échange de §e" + Test.getPlayerNameCache().getOrDefault(senderUid, "?") + "§7 a expiré."));
                    }
                }
            }
        }

        // Scheduled broadcasts
        for (int i = 0; i < Test.scheduledCounters.size(); i++) {
            int c = Test.scheduledCounters.get(i) - 1;
            Test.scheduledCounters.set(i, c);
            if (c <= 0) {
                Test.scheduledCounters.set(i, Test.getScheduledIntervalsArray()[i]);
                server.getPlayerList().broadcastSystemMessage(Component.literal("§6§l[Annonce] §r" + Test.getScheduledMsgsArray()[i]), false);
            }
        }

        // Periodic autosave (every 5 minutes) — protects all data against crashes / hard kills,
        // since the regular persistence only fires on a clean ServerStoppingEvent.
        if (server.getTickCount() > 0 && server.getTickCount() % 6000 == 0) {
            saveAll();
        }
    }

    /** Persists every data store. Each save is isolated so one failure cannot abort the others. */
    static void saveAll() {
        runSave(HomePersistence::save,        "homes");
        runSave(VirtualChestManager::save,    "virtual chests");
        runSave(SettingsPersistence::save,    "player settings");
        runSave(LockPersistence::save,        "locks");
        runSave(MobKillsPersistence::save,    "mob kills");
        runSave(SeenPersistence::save,        "seen timestamps");
        runSave(ServerConfig::save,           "server config");
        runSave(ZonePersistence::save,        "zones");
        runSave(SanctionsPersistence::save,   "sanctions");
        runSave(GroupPersistence::save,       "groups");
        runSave(ModerationPersistence::save,  "moderation (logs/chat/notes)");
    }

    private static void runSave(Runnable save, String label) {
        try {
            save.run();
        } catch (Exception e) {
            System.err.println("[DashBoardAdmin] Autosave failed for " + label + ": " + e.getMessage());
        }
    }

    // ─── Utility ──────────────────────────────────────────────────────────────
    private static String resolvePlayerName(MinecraftServer server, java.util.UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        return Test.getPlayerNameCache().getOrDefault(uuid, uuid.toString().substring(0, 8) + "…");
    }
}
