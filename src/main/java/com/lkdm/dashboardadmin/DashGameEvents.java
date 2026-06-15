package com.lkdm.dashboardadmin;

import com.lkdm.dashboardadmin.command.AdminCommand;
import com.lkdm.dashboardadmin.networking.*;
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
        com.lkdm.dashboardadmin.command.ZoneCommand.register(dispatcher);
        TpaManager.register(dispatcher);

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
                if (!DashboardAdmin.getPlayerSettings(target.getUUID()).allowPrivateMessages) {
                    sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§c" + target.getName().getString() + " n'accepte pas les messages privés.", "§c" + target.getName().getString() + " doesn't accept private messages.")));
                    return 0;
                }
                if (DashboardAdmin.isIgnoring(target.getUUID(), sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§c" + target.getName().getString() + " n'accepte pas vos messages.", "§c" + target.getName().getString() + " doesn't accept your messages.")));
                    return 0;
                }
                String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "message");
                DashboardAdmin.getLastMsg().put(target.getUUID(), sender.getUUID());
                target.sendSystemMessage(Component.literal("§e" + sender.getName().getString() + " §7: §f" + msg));
                sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§7[moi -> §e", "§7[me -> §e") + target.getName().getString() + "§7] §f" + msg));
                if (DashboardAdmin.getPlayerSettings(target.getUUID()).showChatNotifications)
                    target.sendSystemMessage(Component.literal(SrvLang.t(target, "§e[✉] Nouveau message de §f", "§e[✉] New message from §f") + sender.getName().getString()), true);
                PacketDistributor.sendToPlayer(target, new NotifPayload("MAIL", SrvLang.t(target, "§b✉ Message de §f", "§b✉ Message from §f") + sender.getName().getString()));
                DashboardAdmin.addLog(sender.getUUID(), "MP → " + target.getName().getString() + ": " + msg);
                DashboardAdmin.spyPrivateMessage(ctx.getSource().getServer(), sender, target, msg);
                return 1;
            }))));

        // /r
        dispatcher.register(Commands.literal("r")
            .then(Commands.argument("message", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
            .executes(ctx -> {
                ServerPlayer sender = ctx.getSource().getPlayerOrException();
                if (!DashboardAdmin.getLastMsg().containsKey(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§cAucun message à répondre.", "§cNo message to reply to."))); return 0;
                }
                java.util.UUID targetUUID = DashboardAdmin.getLastMsg().get(sender.getUUID());
                ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayer(targetUUID);
                if (target == null) { sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§cCe joueur n'est plus connecté.", "§cThis player is no longer online."))); return 0; }
                String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "message");
                target.sendSystemMessage(Component.literal("§e" + sender.getName().getString() + " §7: §f" + msg));
                sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§7[moi -> §e", "§7[me -> §e") + target.getName().getString() + "§7] §f" + msg));
                DashboardAdmin.getLastMsg().put(target.getUUID(), sender.getUUID());
                DashboardAdmin.addLog(sender.getUUID(), "MP → " + target.getName().getString() + ": " + msg);
                DashboardAdmin.spyPrivateMessage(ctx.getSource().getServer(), sender, target, msg);
                return 1;
            })));

        // /rtp — téléportation aléatoire sécurisée (Overworld, 500-3000 blocs du spawn)
        dispatcher.register(Commands.literal("rtp").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (com.lkdm.dashboardadmin.command.ZoneCommand.isInBuildMode(player.getUUID())) {
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cImpossible d'utiliser §6/rtp §cen mode construction.", "§cCannot use §6/rtp §cin build mode.")));
                return 0;
            }
            ServerLevel level = (ServerLevel) player.level();
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§c/rtp n'est utilisable que dans l'Overworld.", "§c/rtp can only be used in the Overworld.")));
                return 0;
            }
            if (!DashboardAdmin.checkCooldown(DashboardAdmin.getLastRtpUse(), player.getUUID(), DashboardAdmin.getCooldownRtp(), player, "/rtp")) return 0;

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
                DashboardAdmin.savePosition(player); // /back ramène au point de départ
                player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal(SrvLang.t(player,
                    "§a✔ Téléporté aléatoirement en §e(" + x + ", " + y + ", " + z + ")§a — §7/back §apour revenir.",
                    "§a✔ Randomly teleported to §e(" + x + ", " + y + ", " + z + ")§a — §7/back §ato return.")));
                DashboardAdmin.addLog(player.getUUID(), "RTP → (" + x + ", " + y + ", " + z + ")");
                return 1;
            }
            // Échec : on rend le cooldown au joueur.
            DashboardAdmin.getLastRtpUse().remove(player.getUUID());
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cAucune position sûre trouvée, réessayez.", "§cNo safe position found, try again.")));
            return 0;
        }));

        // /lock
        dispatcher.register(Commands.literal("lock").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0, false);
            if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
                if (DashboardAdmin.isLocked(pos)) {
                    if (DashboardAdmin.getOwner(pos).equals(player.getUUID())) { DashboardAdmin.getAllLockedBlocks().remove(pos); player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aBloc déverrouillé.", "§aBlock unlocked."))); }
                    else player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cCe bloc ne vous appartient pas.", "§cThis block doesn't belong to you.")));
                } else { DashboardAdmin.getAllLockedBlocks().put(pos, player.getUUID()); player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aBloc verrouillé.", "§aBlock locked."))); }
            }
            return 1;
        }));

        // /trust
        dispatcher.register(Commands.literal("trust").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target");
            if (player.getUUID().equals(target.getUUID())) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cVous ne pouvez pas vous faire confiance à vous-même.", "§cYou can't trust yourself."))); return 0; }
            DashboardAdmin.getTrusted(player.getUUID()).add(target.getUUID());
            DashboardAdmin.getPlayerNameCache().put(target.getUUID(), target.getName().getString());
            DashboardAdmin.getPlayerNameCache().put(player.getUUID(), player.getName().getString());
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§a" + target.getName().getString() + " a maintenant accès à vos blocs verrouillés.", "§a" + target.getName().getString() + " now has access to your locked blocks.")));
            target.sendSystemMessage(Component.literal(SrvLang.t(target, "§a" + player.getName().getString() + " vous a accordé l'accès à ses blocs verrouillés.", "§a" + player.getName().getString() + " granted you access to their locked blocks.")));
            return 1;
        })));

        // /untrust
        dispatcher.register(Commands.literal("untrust").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target");
            DashboardAdmin.getTrusted(player.getUUID()).remove(target.getUUID());
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§c" + target.getName().getString() + " n'a plus accès à vos blocs verrouillés.", "§c" + target.getName().getString() + " no longer has access to your locked blocks.")));
            target.sendSystemMessage(Component.literal(SrvLang.t(target, "§c" + player.getName().getString() + " vous a retiré l'accès à ses blocs verrouillés.", "§c" + player.getName().getString() + " revoked your access to their locked blocks.")));
            return 1;
        })));

        // /lockinfo
        dispatcher.register(Commands.literal("lockinfo").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0, false);
            if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cRegardez un bloc.", "§cLook at a block."))); return 0; }
            net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
            if (!DashboardAdmin.isLocked(pos)) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Ce bloc n'est pas verrouillé.", "§7This block isn't locked."))); return 0; }
            java.util.UUID ownerUUID = DashboardAdmin.getOwner(pos);
            String ownerName = resolvePlayerName(ctx.getSource().getServer(), ownerUUID);
            java.util.Set<java.util.UUID> trusted = DashboardAdmin.getTrusted(ownerUUID);
            player.sendSystemMessage(Component.literal("§6§l[LockInfo]"));
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Propriétaire : §e", "§7Owner: §e") + ownerName));
            if (trusted.isEmpty()) player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Accès partagé : §cnul", "§7Shared access: §cnone")));
            else {
                java.util.List<String> names = trusted.stream().map(uuid -> resolvePlayerName(ctx.getSource().getServer(), uuid)).collect(java.util.stream.Collectors.toList());
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Accès partagé : §a", "§7Shared access: §a") + String.join("§7, §a", names)));
            }
            return 1;
        }));

        // /sethome
        dispatcher.register(Commands.literal("sethome").then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string()).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (!player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§c/sethome n'est disponible que dans l'Overworld.", "§c/sethome is only available in the Overworld."))); return 0;
            }
            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
            var homes = DashboardAdmin.getPlayerHomes(player.getUUID());
            if (homes.size() >= DashboardAdmin.getMaxHomes() && !homes.containsKey(name)) {
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cLimite de §e" + DashboardAdmin.getMaxHomes() + " §chomes atteinte.", "§cLimit of §e" + DashboardAdmin.getMaxHomes() + " §chomes reached."))); return 0;
            }
            homes.put(name, player.blockPosition());
            DashboardAdmin.getPlayerHomesDim(player.getUUID()).put(name, player.level().dimension().location().toString());
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aHome §e'" + name + "' §aenregistré ! (§e" + homes.size() + "§a/§e" + DashboardAdmin.getMaxHomes() + "§a)", "§aHome §e'" + name + "' §asaved! (§e" + homes.size() + "§a/§e" + DashboardAdmin.getMaxHomes() + "§a)")));
            return 1;
        })));

        // /back
        dispatcher.register(Commands.literal("back").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (com.lkdm.dashboardadmin.command.ZoneCommand.isInBuildMode(player.getUUID())) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cImpossible d'utiliser §6/back §cen mode construction.", "§cCannot use §6/back §cin build mode."))); return 0; }
            if (!DashboardAdmin.checkCooldown(DashboardAdmin.getLastBackUse(), player.getUUID(), DashboardAdmin.getCooldownBack(), player, "/back")) return 0;
            net.minecraft.world.phys.Vec3 pos = DashboardAdmin.getLastPositions().get(player.getUUID());
            if (pos == null) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cAucune position de retour trouvée.", "§cNo return position found."))); return 0; }
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = DashboardAdmin.getLastPositionDims().get(player.getUUID());
            ServerLevel targetLevel = dim != null ? ctx.getSource().getServer().getLevel(dim) : null;
            if (targetLevel == null) targetLevel = (ServerLevel) player.level();
            DashboardAdmin.savePosition(player);
            player.teleportTo(targetLevel, pos.x, pos.y, pos.z, Set.of(), player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aRetour à la position précédente.", "§aReturned to previous position.")));
            return 1;
        }));

        // /afk
        dispatcher.register(Commands.literal("afk").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            boolean afk = !DashboardAdmin.isAfk(player.getUUID());
            DashboardAdmin.afkPlayers.put(player.getUUID(), afk);
            if (afk) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(SrvLang.t(player, "§eVOUS ÊTES AFK", "§eYOU ARE AFK"))));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 40, 10));
            } else {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(SrvLang.t(player, "§aVOUS N'ÊTES PLUS AFK", "§aYOU ARE NO LONGER AFK"))));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 20, 10));
            }
            String afkName = player.getName().getString();
            SrvLang.each(ctx.getSource().getServer(), "§e" + afkName + (afk ? " est AFK." : " n'est plus AFK."), "§e" + afkName + (afk ? " is now AFK." : " is no longer AFK."));
            return 1;
        }));

        // /home
        dispatcher.register(Commands.literal("home")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                var homes = DashboardAdmin.getPlayerHomes(player.getUUID());
                if (homes.isEmpty()) player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cVous n'avez aucun home enregistré.", "§cYou have no saved homes.")));
                else player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aVos homes : §f", "§aYour homes: §f") + String.join(", ", homes.keySet())));
                return 1;
            })
            .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
            .suggests((ctx, builder) -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return net.minecraft.commands.SharedSuggestionProvider.suggest(DashboardAdmin.getPlayerHomes(player.getUUID()).keySet(), builder);
            })
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                if (com.lkdm.dashboardadmin.command.ZoneCommand.isInBuildMode(player.getUUID())) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cImpossible d'utiliser §6/home §cen mode construction.", "§cCannot use §6/home §cin build mode."))); return 0; }
                if (!DashboardAdmin.checkCooldown(DashboardAdmin.getLastHomeUse(), player.getUUID(), DashboardAdmin.getCooldownHome(), player, "/home")) return 0;
                net.minecraft.core.BlockPos pos = DashboardAdmin.getPlayerHomes(player.getUUID()).get(name);
                if (pos == null) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cHome '" + name + "' introuvable.", "§cHome '" + name + "' not found."))); return 0; }
                DashboardAdmin.savePosition(player);
                player.teleportTo((ServerLevel)player.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aTéléporté au home '" + name + "'.", "§aTeleported to home '" + name + "'.")));
                return 1;
            })));

        // /chest
        dispatcher.register(Commands.literal("chest").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (com.lkdm.dashboardadmin.command.ZoneCommand.isInBuildMode(player.getUUID())) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cImpossible d'utiliser §6/chest §cen mode construction.", "§cCannot use §6/chest §cin build mode."))); return 0; }
            NonNullList<ItemStack> items = VirtualChestManager.getChest(player.getUUID());
            net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(9);
            for (int i = 0; i < 9; i++) container.setItem(i, items.get(i));
            container.addListener(c -> { for (int i = 0; i < 9; i++) items.set(i, c.getItem(i)); });
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new VirtualChestMenu(id, inv, container), Component.literal(SrvLang.t(player, "Coffre Virtuel", "Virtual Chest"))));
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
                ReportManager.submit(player, message, null);
                return 1;
            })));

        dispatcher.register(Commands.literal("reportaccept").requires(s -> s.hasPermission(2))
            .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                ServerPlayer admin = ctx.getSource().getPlayerOrException();
                String pName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player");
                return ReportManager.accept(admin, pName) ? 1 : 0;
            })));

        dispatcher.register(Commands.literal("reportdeny").requires(s -> s.hasPermission(2))
            .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                ServerPlayer admin = ctx.getSource().getPlayerOrException();
                String pName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player");
                return ReportManager.refuse(admin, pName) ? 1 : 0;
            })));

        // /menu
        dispatcher.register(Commands.literal("menu").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PlayerSettings s = DashboardAdmin.getPlayerSettings(player.getUUID());
            String homesSerialized = DashboardAdmin.getPlayerHomes(player.getUUID()).entrySet().stream().map(e -> {
                String dimId = DashboardAdmin.getPlayerHomesDim(player.getUUID()).getOrDefault(e.getKey(), "minecraft:overworld");
                String dimShort = dimId.contains("nether") ? "NE" : dimId.contains("end") ? "END" : "OW";
                return e.getKey() + ":" + e.getValue().getX() + "," + e.getValue().getY() + "," + e.getValue().getZ() + ":" + dimShort;
            }).collect(java.util.stream.Collectors.joining("|"));
            String locksSerialized = DashboardAdmin.getAllLockedBlocks().entrySet().stream().filter(e -> e.getValue().equals(player.getUUID())).map(e -> e.getKey().getX() + "," + e.getKey().getY() + "," + e.getKey().getZ()).collect(java.util.stream.Collectors.joining("|"));
            String trustSerialized = DashboardAdmin.getTrusted(player.getUUID()).stream().map(uuid -> DashboardAdmin.getPlayerNameCache().getOrDefault(uuid, uuid.toString().substring(0, 8) + "…") + ":" + uuid.toString()).collect(java.util.stream.Collectors.joining("|"));
            int playTicks = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
            long totalSec = playTicks / 20L; long ph = totalSec / 3600, pm = (totalSec % 3600) / 60;
            int deaths = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
            int pKills = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS));
            int mKills = DashboardAdmin.getAllHostileMobKills().getOrDefault(player.getUUID(), 0);
            long blocks = (player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.WALK_ONE_CM)) + player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.SPRINT_ONE_CM))) / 100L;
            String statsSerialized = ph + "|" + pm + "|" + deaths + "|" + pKills + "|" + mKills + "|" + blocks + "|" + player.totalExperience;
            PacketDistributor.sendToPlayer(player, new OpenSettingsPayload(s.allowPrivateMessages, s.allowTpaRequests, s.allowTrades, s.showChatNotifications, s.showConnectionAlerts, DashboardAdmin.getCommandsSerialized(), homesSerialized, locksSerialized, trustSerialized, statsSerialized, com.lkdm.dashboardadmin.command.ZoneCommand.getBuildInfoFor(player), WarpManager.getWarpsSerialized()));
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
            int hostile = DashboardAdmin.getAllHostileMobKills().getOrDefault(player.getUUID(), 0);
            long walkCm = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.WALK_ONE_CM));
            long sprintCm = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.SPRINT_ONE_CM));
            long blks = (walkCm + sprintCm) / 100L;
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§6§l━━━━ Vos statistiques ━━━━", "§6§l━━━━ Your statistics ━━━━")));
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Temps de jeu    §f", "§7Playtime        §f") + hours + "h " + minutes + "m"));
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Morts           §c", "§7Deaths          §c") + deaths));
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Kills joueurs   §e", "§7Player kills    §e") + pKills));
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7XP totale       §a", "§7Total XP        §a") + player.totalExperience));
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Mobs hostiles   §d", "§7Hostile mobs    §d") + hostile));
            player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Blocs parcourus §b", "§7Blocks walked   §b") + blks));
            player.sendSystemMessage(Component.literal("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            return 1;
        }));

        // /seen
        dispatcher.register(Commands.literal("seen").then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
            ServerPlayer seeker = ctx.getSource().getPlayerOrException();
            String targetName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player");
            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
            if (online != null) { seeker.sendSystemMessage(Component.literal(SrvLang.t(seeker, "§e" + targetName + " §7est actuellement §aconnecté§7.", "§e" + targetName + " §7is currently §aonline§7."))); return 1; }
            java.util.Optional<java.util.Map.Entry<java.util.UUID, String>> entry = DashboardAdmin.getPlayerNameCache().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(targetName)).findFirst();
            if (entry.isEmpty()) { seeker.sendSystemMessage(Component.literal(SrvLang.t(seeker, "§cJoueur §e" + targetName + " §cinconnu.", "§cPlayer §e" + targetName + " §cunknown."))); return 0; }
            Long ts = DashboardAdmin.getLastSeenTimestamps().get(entry.get().getKey());
            if (ts == null) seeker.sendSystemMessage(Component.literal(SrvLang.t(seeker, "§7Aucune donnée de connexion pour §e" + targetName + "§7.", "§7No connection data for §e" + targetName + "§7.")));
            else seeker.sendSystemMessage(Component.literal(SrvLang.t(seeker, "§e" + targetName + " §7a été vu il y a §f" + DashboardAdmin.formatTimeAgo(ts) + "§7.", "§e" + targetName + " §7was last seen §f" + DashboardAdmin.formatTimeAgo(ts) + " §7ago.")));
            return 1;
        })));

        // /check
        dispatcher.register(Commands.literal("check").requires(s -> s.hasPermission(2)).then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
            ServerPlayer adm = ctx.getSource().getPlayerOrException();
            String targetName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player");
            MinecraftServer srv = ctx.getSource().getServer();
            adm.sendSystemMessage(Component.literal("§6§l━━━ Check : " + targetName + " ━━━"));
            ServerPlayer onlineTarget = srv.getPlayerList().getPlayerByName(targetName);
            adm.sendSystemMessage(Component.literal(SrvLang.t(adm, "§7Connecté : ", "§7Online: ") + (onlineTarget != null ? "§aOUI" : "§cNON")));
            boolean banned = srv.getPlayerList().getBans().getEntries().stream().anyMatch(e -> e.getDisplayName().getString().equalsIgnoreCase(targetName));
            adm.sendSystemMessage(Component.literal(SrvLang.t(adm, "§7Banni : ", "§7Banned: ") + (banned ? "§cOUI" : "§aNON")));
            if (banned) srv.getPlayerList().getBans().getEntries().stream().filter(e -> e.getDisplayName().getString().equalsIgnoreCase(targetName)).findFirst().ifPresent(e -> adm.sendSystemMessage(Component.literal(SrvLang.t(adm, "§7Raison : §f", "§7Reason: §f") + (e.getReason() != null ? e.getReason() : "—"))));
            DashboardAdmin.getPlayerNameCache().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(targetName)).findFirst().ifPresent(cacheEntry -> {
                Long ts = DashboardAdmin.getLastSeenTimestamps().get(cacheEntry.getKey());
                if (onlineTarget != null) adm.sendSystemMessage(Component.literal(SrvLang.t(adm, "§7Dernière vue : §aMaintenant", "§7Last seen: §aNow")));
                else if (ts != null) adm.sendSystemMessage(Component.literal(SrvLang.t(adm, "§7Dernière vue : §fil y a " + DashboardAdmin.formatTimeAgo(ts), "§7Last seen: §f" + DashboardAdmin.formatTimeAgo(ts) + " ago")));
                else adm.sendSystemMessage(Component.literal(SrvLang.t(adm, "§7Dernière vue : §8inconnue", "§7Last seen: §8unknown")));
                int logCount = DashboardAdmin.getPlayerLogs().getOrDefault(cacheEntry.getKey(), java.util.Collections.emptyList()).size();
                adm.sendSystemMessage(Component.literal(SrvLang.t(adm, "§7Logs : §f" + logCount + " entrée" + (logCount > 1 ? "s" : ""), "§7Logs: §f" + logCount + " entr" + (logCount > 1 ? "ies" : "y"))));
            });
            // 5 dernières sanctions du joueur (sanctionsLog = [ts, type, player, admin, reason])
            java.util.List<String[]> sl = DashboardAdmin.getSanctionsLog();
            java.util.List<String[]> recent = new java.util.ArrayList<>();
            for (int i = sl.size() - 1; i >= 0 && recent.size() < 5; i--)
                if (sl.get(i)[2].equalsIgnoreCase(targetName)) recent.add(sl.get(i));
            if (recent.isEmpty()) {
                adm.sendSystemMessage(Component.literal(SrvLang.t(adm, "§7Sanctions : §aaucune", "§7Sanctions: §anone")));
            } else {
                adm.sendSystemMessage(Component.literal(SrvLang.t(adm, "§7Sanctions §8(5 dernières)§7 :", "§7Sanctions §8(last 5)§7:")));
                for (String[] s : recent)
                    adm.sendSystemMessage(Component.literal("§8• §7" + s[0] + " §e" + s[1] + SrvLang.t(adm, " §7par §f", " §7by §f") + s[3]
                        + ("—".equals(s[4]) ? "" : " §8(" + s[4] + ")")));
            }
            adm.sendSystemMessage(Component.literal("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            return 1;
        })));

        // /mute <joueur> [durée]  — durée: 30s/10m/2h/1d ou secondes ; absente = permanent
        dispatcher.register(Commands.literal("mute").requires(s -> s.hasPermission(2))
            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(ctx -> applyMute(ctx, 0))
                .then(Commands.argument("duration", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> applyMute(ctx, DashboardAdmin.parseDuration(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "duration")))))));

        // /unmute <joueur>
        dispatcher.register(Commands.literal("unmute").requires(s -> s.hasPermission(2))
            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer admin = ctx.getSource().getPlayerOrException();
                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                    if (!DashboardAdmin.isMuted(target.getUUID())) { admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§e" + target.getName().getString() + " n'est pas muet.", "§e" + target.getName().getString() + " is not muted."))); return 0; }
                    DashboardAdmin.unmute(target.getUUID()); ModerationPersistence.save();
                    DashboardAdmin.addLog(target.getUUID(), "Unmuted par " + admin.getName().getString());
                    admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§e" + target.getName().getString() + " n'est plus muet.", "§e" + target.getName().getString() + " is no longer muted.")));
                    target.sendSystemMessage(Component.literal(SrvLang.t(target, "§eVous n'êtes plus muet.", "§eYou are no longer muted.")));
                    return 1;
                })));

        // /deal
        dispatcher.register(Commands.literal("deal").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> {
            ServerPlayer sender = ctx.getSource().getPlayerOrException();
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target");
            if (sender == target) { sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§cVous ne pouvez pas échanger avec vous-même.", "§cYou can't trade with yourself."))); return 0; }
            if (DealManager.activeSessions.containsKey(sender.getUUID())) { sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§cVous avez déjà un échange en cours.", "§cYou already have a trade in progress."))); return 0; }
            if (DealManager.activeSessions.containsKey(target.getUUID())) { sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§c" + target.getName().getString() + " a déjà un échange en cours.", "§c" + target.getName().getString() + " already has a trade in progress."))); return 0; }
            if (!DashboardAdmin.getPlayerSettings(target.getUUID()).allowTrades) { sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§c" + target.getName().getString() + " n'accepte pas les échanges.", "§c" + target.getName().getString() + " doesn't accept trades."))); return 0; }
            if (DashboardAdmin.isIgnoring(target.getUUID(), sender.getUUID())) { sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§c" + target.getName().getString() + " n'accepte pas vos demandes.", "§c" + target.getName().getString() + " doesn't accept your requests."))); return 0; }
            DealManager.getPendingDeals().put(target.getUUID(), sender.getUUID());
            DealManager.getPendingDealTimestamps().put(target.getUUID(), System.currentTimeMillis());
            String sName = sender.getName().getString();
            Component msg = Component.literal(SrvLang.t(target, "§e" + sName + " §7souhaite effectuer un échange. ", "§e" + sName + " §7wants to trade with you. "))
                .append(Component.literal("[Y]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/dealaccept"))))
                .append(Component.literal(" "))
                .append(Component.literal("[N]").withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/dealdeny"))));
            target.sendSystemMessage(msg);
            PacketDistributor.sendToPlayer(target, new NotifPayload("DEAL", SrvLang.t(target, "§e⇄ Échange proposé par §f", "§e⇄ Trade proposed by §f") + sName));
            sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§7Demande d'échange envoyée à §e" + target.getName().getString() + "§7.", "§7Trade request sent to §e" + target.getName().getString() + "§7.")));
            return 1;
        })));

        dispatcher.register(Commands.literal("dealaccept").executes(ctx -> {
            ServerPlayer target = ctx.getSource().getPlayerOrException();
            java.util.UUID requesterUUID = DealManager.getPendingDeals().remove(target.getUUID());
            DealManager.getPendingDealTimestamps().remove(target.getUUID());
            if (requesterUUID == null) { target.sendSystemMessage(Component.literal(SrvLang.t(target, "§cAucune demande d'échange en attente.", "§cNo pending trade request."))); return 0; }
            ServerPlayer requester = ctx.getSource().getServer().getPlayerList().getPlayer(requesterUUID);
            if (requester == null) { target.sendSystemMessage(Component.literal(SrvLang.t(target, "§cCe joueur n'est plus connecté.", "§cThis player is no longer online."))); return 0; }
            DealManager.DealSession session = new DealManager.DealSession(requesterUUID, target.getUUID());
            DealManager.activeSessions.put(requesterUUID, session);
            DealManager.activeSessions.put(target.getUUID(), session);
            DashboardAdmin.getPlayerNameCache().put(requester.getUUID(), requester.getName().getString());
            DashboardAdmin.getPlayerNameCache().put(target.getUUID(), target.getName().getString());
            DealManager.broadcastDealUpdate(session, ctx.getSource().getServer());
            return 1;
        }));

        dispatcher.register(Commands.literal("dealdeny").executes(ctx -> {
            ServerPlayer target = ctx.getSource().getPlayerOrException();
            java.util.UUID requesterUUID = DealManager.getPendingDeals().remove(target.getUUID());
            DealManager.getPendingDealTimestamps().remove(target.getUUID());
            if (requesterUUID != null) {
                ServerPlayer requester = ctx.getSource().getServer().getPlayerList().getPlayer(requesterUUID);
                if (requester != null) requester.sendSystemMessage(Component.literal(SrvLang.t(requester, "§c" + target.getName().getString() + " a refusé votre demande d'échange.", "§c" + target.getName().getString() + " declined your trade request.")));
                target.sendSystemMessage(Component.literal(SrvLang.t(target, "§cDemande refusée.", "§cRequest declined.")));
            }
            return 1;
        }));

        // /ignore
        dispatcher.register(Commands.literal("ignore")
            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                if (player == target) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cVous ne pouvez pas vous ignorer vous-même.", "§cYou can't ignore yourself."))); return 0; }
                com.lkdm.dashboardadmin.PlayerSettings s = DashboardAdmin.getPlayerSettings(player.getUUID());
                DashboardAdmin.getPlayerNameCache().put(target.getUUID(), target.getName().getString());
                if (s.ignoredPlayers.add(target.getUUID())) {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Vous ignorez maintenant §e" + target.getName().getString() + "§7.", "§7You are now ignoring §e" + target.getName().getString() + "§7.")));
                } else {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Vous ignorez déjà §e" + target.getName().getString() + "§7.", "§7You are already ignoring §e" + target.getName().getString() + "§7.")));
                }
                return 1;
            })));

        // /unignore
        dispatcher.register(Commands.literal("unignore")
            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                com.lkdm.dashboardadmin.PlayerSettings s = DashboardAdmin.getPlayerSettings(player.getUUID());
                if (s.ignoredPlayers.remove(target.getUUID())) {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Vous n'ignorez plus §e" + target.getName().getString() + "§7.", "§7You are no longer ignoring §e" + target.getName().getString() + "§7.")));
                } else {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§7Vous n'ignoriez pas §e" + target.getName().getString() + "§7.", "§7You weren't ignoring §e" + target.getName().getString() + "§7.")));
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
                WarpManager.getWarps().put(name, player.blockPosition());
                WarpManager.getWarpsDim().put(name, player.level().dimension().location().toString());
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aWarp §e'" + name + "' §acréé à votre position.", "§aWarp §e'" + name + "' §acreated at your position.")));
                com.lkdm.dashboardadmin.ServerConfig.save();
                return 1;
            })));

        // /delwarp <name>
        dispatcher.register(Commands.literal("delwarp")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
            .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(WarpManager.getWarps().keySet(), builder))
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                if (WarpManager.getWarps().remove(name) != null) {
                    WarpManager.getWarpsDim().remove(name);
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cWarp §e'" + name + "' §csupprimé.", "§cWarp §e'" + name + "' §cdeleted.")));
                    com.lkdm.dashboardadmin.ServerConfig.save();
                } else {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cWarp §e'" + name + "' §cintrouvable.", "§cWarp §e'" + name + "' §cnot found.")));
                }
                return 1;
            })));

        // /warp [name]
        dispatcher.register(Commands.literal("warp")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (WarpManager.getWarps().isEmpty()) {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§8Aucun warp disponible.", "§8No warps available.")));
                } else {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§6Warps : §f", "§6Warps: §f") + String.join("§7, §f", WarpManager.getWarps().keySet())));
                }
                return 1;
            })
            .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
            .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(WarpManager.getWarps().keySet(), builder))
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                if (com.lkdm.dashboardadmin.command.ZoneCommand.isInBuildMode(player.getUUID())) {
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cImpossible d'utiliser §6/warp §cen mode construction.", "§cCannot use §6/warp §cin build mode.")));
                    return 0;
                }
                if (!DashboardAdmin.checkCooldown(WarpManager.getLastWarpUse(), player.getUUID(), DashboardAdmin.getCooldownWarp(), player, "/warp")) return 0;
                net.minecraft.core.BlockPos pos = WarpManager.getWarps().get(name);
                if (pos == null) { player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cWarp §e'" + name + "' §cintrouvable.", "§cWarp §e'" + name + "' §cnot found."))); return 0; }
                String dimId = WarpManager.getWarpsDim().getOrDefault(name, "minecraft:overworld");
                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.world.level.Level.OVERWORLD.registryKey(),
                    net.minecraft.resources.ResourceLocation.parse(dimId));
                ServerLevel targetLevel = player.getServer().getLevel(dimKey);
                if (targetLevel == null) targetLevel = (ServerLevel) player.level();
                DashboardAdmin.savePosition(player);
                player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aTéléporté au warp §e'" + name + "'§a.", "§aTeleported to warp §e'" + name + "'§a.")));
                return 1;
            })));
    }

    // ─── Player login / logout ────────────────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        MinecraftServer server = player.getServer();
        // Première connexion : jamais vu déconnecté (persisté) ni en mémoire — à tester AVANT le put.
        boolean firstJoin = !DashboardAdmin.getLastSeenTimestamps().containsKey(player.getUUID())
                         && !DashboardAdmin.getPlayerNameCache().containsKey(player.getUUID());
        DashboardAdmin.getPlayerNameCache().put(player.getUUID(), player.getName().getString());
        DashboardAdmin.getLastActivityTime().put(player.getUUID(), System.currentTimeMillis());
        String joinName = player.getName().getString();
        if (firstJoin) {
            SrvLang.each(server,
                "§6✦ §e" + joinName + " §6rejoint le serveur pour la première fois !",
                "§6✦ §e" + joinName + " §6joins the server for the first time!");
        } else {
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (DashboardAdmin.getPlayerSettings(p.getUUID()).showConnectionAlerts && !p.getUUID().equals(player.getUUID()))
                    p.sendSystemMessage(Component.literal(SrvLang.t(p, "§a[+] §f" + joinName + " §7a rejoint le serveur.", "§a[+] §f" + joinName + " §7joined the server.")));
        }
        if (!DashboardAdmin.getMotd().isEmpty())
            player.sendSystemMessage(Component.literal("§7" + DashboardAdmin.getMotd()));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        MinecraftServer server = player.getServer();
        java.util.UUID uid = player.getUUID();
        DashboardAdmin.getLastSeenTimestamps().put(uid, System.currentTimeMillis());
        DealManager.handleDealDisconnect(player, server);
        DashboardAdmin.getLastMsg().remove(uid);
        DashboardAdmin.getLastMsg().values().removeIf(v -> v.equals(uid));
        GroupManager.onPlayerDisconnect(uid, server);
        DashboardAdmin.afkPlayers.remove(uid);
        DashboardAdmin.lastPos.remove(uid);
        DashboardAdmin.getLastActivityTime().remove(uid);
        TpaManager.getTpaRequests().remove(uid);
        TpaManager.getPendingTpaTimestamps().remove(uid);
        TpaManager.getTpaHere().remove(uid);
        TpaManager.getTpaRequests().entrySet().removeIf(e -> { if (e.getValue().equals(uid)) { TpaManager.getPendingTpaTimestamps().remove(e.getKey()); TpaManager.getTpaHere().remove(e.getKey()); return true; } return false; });
        String leftName = player.getName().getString();
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (DashboardAdmin.getPlayerSettings(p.getUUID()).showConnectionAlerts && !p.getUUID().equals(uid))
                p.sendSystemMessage(Component.literal(SrvLang.t(p, "§c[-] §f" + leftName + " §7a quitté le serveur.", "§c[-] §f" + leftName + " §7left the server.")));
    }

    // ─── Living events ────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Save death position for /back
        if (event.getEntity() instanceof ServerPlayer sp) DashboardAdmin.savePosition(sp);
        // Keep-inventory: save state before death
        if (event.getEntity() instanceof ServerPlayer sp && DashboardAdmin.getPlayerSettings(sp.getUUID()).keepInventory) {
            net.minecraft.world.entity.player.Inventory inv = sp.getInventory();
            int size = inv.getContainerSize();
            NonNullList<ItemStack> copy = NonNullList.withSize(size, ItemStack.EMPTY);
            for (int i = 0; i < size; i++) { copy.set(i, inv.getItem(i).copy()); inv.setItem(i, ItemStack.EMPTY); }
            keepInvSavedStates.put(sp.getUUID(), new SavedState(copy, sp.experienceLevel, sp.experienceProgress, sp.totalExperience, AccessoriesCompat.saveAndClear(sp)));
        }
        // Mob kill tracking (runs before entity is actually removed — fine for counting)
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            DashboardAdmin.getAllHostileMobKills().merge(killer.getUUID(), 1, Integer::sum);
        }
        if (!(event.getEntity() instanceof ServerPlayer) && event.getEntity().hasCustomName()
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            String typeName = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getPath();
            DashboardAdmin.addLog(killer.getUUID(), "Tué [" + typeName + "] \"" + event.getEntity().getCustomName().getString() + "\"");
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
        if (!DashboardAdmin.isPvpEnabled() && event.getSource().getEntity() instanceof ServerPlayer) {
            event.setCanceled(true);
            return;
        }
        // AFK protection from monsters
        if (DashboardAdmin.isAfk(target.getUUID()) && event.getSource().getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof ServerPlayer target) {
            if (!DashboardAdmin.isPvpEnabled()) { event.setCanceled(true); return; }
            if (DashboardAdmin.isAfk(target.getUUID())) event.setCanceled(true);
        }
    }

    // ─── Dimension change (freeze check) ─────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (DashboardAdmin.isFrozen(player.getUUID())) {
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
        if (DashboardAdmin.isMuted(sender.getUUID())) {
            DashboardAdmin.addLog(sender.getUUID(), "Chat bloqué (muet): " + raw);
            long exp = DashboardAdmin.getMuteExpiry(sender.getUUID());
            String dur = exp > 0 ? DashboardAdmin.formatDurationShort((exp - System.currentTimeMillis()) / 1000) : "";
            String remainFr = exp > 0 ? " §7(encore " + dur + ")" : "";
            String remainEn = exp > 0 ? " §7(" + dur + " left)" : "";
            sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§cVous êtes muet, vous ne pouvez pas écrire." + remainFr, "§cYou are muted, you can't chat." + remainEn)));
            event.setCanceled(true);
            return;
        }
        if (raw.startsWith("@g ") && GroupManager.isInGroup(sender.getUUID())) {
            String text = raw.substring(3).trim();
            if (!text.isEmpty()) {
                java.util.UUID leader = GroupManager.getLeader(sender.getUUID());
                String senderName = sender.getName().getString();
                GroupManager.getMembers(leader).forEach(uuid -> {
                    ServerPlayer m = ((ServerLevel) sender.level()).getServer().getPlayerList().getPlayer(uuid);
                    if (m != null) m.sendSystemMessage(Component.literal(SrvLang.t(m, "§a[Groupe] §f", "§a[Group] §f") + senderName + "§7: §f" + text));
                });
                DashboardAdmin.addLog(sender.getUUID(), "[Groupe] " + text);
            }
            event.setCanceled(true);
            return;
        }
        if (DashboardAdmin.isChatLocked() && !sender.hasPermissions(2)) {
            sender.sendSystemMessage(Component.literal(SrvLang.t(sender, "§cLe chat est actuellement bloqué par un admin.", "§cChat is currently locked by an admin.")));
            event.setCanceled(true);
            return;
        }
        DashboardAdmin.addLog(sender.getUUID(), "Chat: " + raw);
        DashboardAdmin.addChatHistory(sender.getName().getString(), raw);
        DashboardAdmin.getLastActivityTime().put(sender.getUUID(), System.currentTimeMillis());

        // Per-player ignore: cancel and re-broadcast only to non-ignoring players
        boolean anyIgnoring = sender.getServer().getPlayerList().getPlayers().stream()
            .anyMatch(p -> DashboardAdmin.isIgnoring(p.getUUID(), sender.getUUID()));
        if (anyIgnoring) {
            event.setCanceled(true);
            Component chatMsg = Component.literal("§f<" + sender.getName().getString() + "§f> " + raw);
            for (ServerPlayer p : sender.getServer().getPlayerList().getPlayers())
                if (!DashboardAdmin.isIgnoring(p.getUUID(), sender.getUUID())) p.sendSystemMessage(chatMsg);
        }
    }

    // ─── Block interaction (lock + right-click harvest + double door) ─────────
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        net.minecraft.core.BlockPos pos = event.getHitVec().getBlockPos();
        net.minecraft.world.entity.player.Player player = event.getEntity();

        // Lock check — les admins (OP niv. 2) accèdent à tous les blocs verrouillés (cohérent avec onLockedBlockBreak).
        if (DashboardAdmin.isLocked(pos) && !player.hasPermissions(2)
                && !DashboardAdmin.getOwner(pos).equals(player.getUUID())
                && !DashboardAdmin.isTrusted(DashboardAdmin.getOwner(pos), player.getUUID())
                && !GroupManager.isTrustedByGroup(DashboardAdmin.getOwner(pos), player.getUUID())) {
            if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cCe bloc est verrouillé.", "§cThis block is locked.")));
            event.setCanceled(true);
            return;
        }
        if (player instanceof ServerPlayer && DashboardAdmin.isLocked(pos)) {
            java.util.UUID ownerUUID = DashboardAdmin.getOwner(pos);
            if (ownerUUID.equals(player.getUUID()) || DashboardAdmin.isTrusted(ownerUUID, player.getUUID())) {
                if (event.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.Container)
                    DashboardAdmin.addLog(player.getUUID(), "Coffre ouvert à (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
            }
        }

        // Right-click harvest
        if (DashboardAdmin.isRightClickHarvestEnabled() && !player.isShiftKeyDown()
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
        if (DashboardAdmin.isDoubleDoorEnabled()) {
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

    // ─── Locked-block break (protection + nettoyage du lock) ──────────────────
    // Sans ça, casser un bloc verrouillé laissait son entrée dans la map des locks :
    // tout bloc reposé à la même position héritait du lock fantôme.
    @SubscribeEvent
    public static void onLockedBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        net.minecraft.core.BlockPos pos = event.getPos();
        if (!DashboardAdmin.isLocked(pos)) return;
        java.util.UUID owner = DashboardAdmin.getOwner(pos);
        net.minecraft.world.entity.player.Player p = event.getPlayer();
        boolean allowed = p != null && (p.getUUID().equals(owner)
            || DashboardAdmin.isTrusted(owner, p.getUUID())
            || GroupManager.isTrustedByGroup(owner, p.getUUID())
            || p.hasPermissions(2));
        if (!allowed) {
            if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(SrvLang.t(sp, "§cCe bloc est verrouillé.", "§cThis block is locked.")));
            event.setCanceled(true);
            return;
        }
        // Cassé par le propriétaire / trusted / OP → on retire le lock pour ne pas l'orpheliner.
        DashboardAdmin.getAllLockedBlocks().remove(pos);
    }

    // Filet de sécurité : un bloc posé à une position verrouillée signifie que l'ancien
    // bloc a disparu (explosion, piston, /setblock…) sans nettoyer le lock. Le lock y est
    // donc obsolète : on le retire (impossible de poser sur un bloc verrouillé encore présent).
    @SubscribeEvent
    public static void onPlaceClearStaleLock(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        DashboardAdmin.getAllLockedBlocks().remove(event.getPos());
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
        if (DashboardAdmin.isTreeCapitatorEnabled()) {
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
                    DashboardAdmin.addLogNeighbors(world, pos, visited, queue);
                    int maxBlocks = Math.min(150, tool.getMaxDamage() - tool.getDamageValue() - 1);
                    int broken = 0;
                    isCapitating = true;
                    try {
                        while (!queue.isEmpty() && broken < maxBlocks && !tool.isEmpty()) {
                            net.minecraft.core.BlockPos cur = queue.poll();
                            ((net.minecraft.world.level.Level) event.getLevel()).destroyBlock(cur, true, sp);
                            tool.hurtAndBreak(1, sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                            broken++;
                            if (DashboardAdmin.isFastLeafDecayEnabled()) DashboardAdmin.scheduleNearbyLeaves(world, cur);
                            DashboardAdmin.addLogNeighbors(world, cur, visited, queue);
                        }
                    } finally { isCapitating = false; }
                    if (DashboardAdmin.isFastLeafDecayEnabled()) DashboardAdmin.scheduleNearbyLeaves(world, pos);
                }
            }
        }
        if (DashboardAdmin.isFastLeafDecayEnabled() && !didCapitate)
            DashboardAdmin.scheduleNearbyLeaves((net.minecraft.world.level.Level) event.getLevel(), event.getPos());
    }

    // ─── Server tick ──────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        com.lkdm.dashboardadmin.command.ZoneCommand.onTick(server);
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
            if (DashboardAdmin.isFrozen(player.getUUID())) {
                net.minecraft.world.phys.Vec3 fp = DashboardAdmin.frozenPositions.get(player.getUUID());
                if (fp != null && player.position().distanceToSqr(fp) > 0.001)
                    player.connection.teleport(fp.x, fp.y, fp.z, player.getYRot(), player.getXRot());
            }
            if (DashboardAdmin.isAfk(player.getUUID())) {
                player.sendSystemMessage(Component.literal(SrvLang.t(player, "§e[AFK] Vous êtes absent.", "§e[AFK] You are away.")), true);
                if (DashboardAdmin.lastPos.containsKey(player.getUUID()) && player.position().distanceToSqr(DashboardAdmin.lastPos.get(player.getUUID())) > 0.01) {
                    DashboardAdmin.afkPlayers.put(player.getUUID(), false);
                    DashboardAdmin.getLastActivityTime().put(player.getUUID(), nowMs);
                    player.sendSystemMessage(Component.literal(SrvLang.t(player, "§eVous n'êtes plus AFK.", "§eYou are no longer AFK.")));
                    String backName = player.getName().getString();
                    SrvLang.each(server, "§e" + backName + " n'est plus AFK.", "§e" + backName + " is no longer AFK.");
                }
            } else if (DashboardAdmin.isAfkAutoEnabled() && !DashboardAdmin.isFrozen(player.getUUID())) {
                net.minecraft.world.phys.Vec3 prevPos = DashboardAdmin.lastPos.get(player.getUUID());
                if (prevPos != null && player.position().distanceToSqr(prevPos) > 0.001) {
                    DashboardAdmin.getLastActivityTime().put(player.getUUID(), nowMs);
                } else {
                    long lastAct = DashboardAdmin.getLastActivityTime().getOrDefault(player.getUUID(), nowMs);
                    if (nowMs - lastAct > (DashboardAdmin.getAfkDelayMinutes() * 60000L)) {
                        DashboardAdmin.afkPlayers.put(player.getUUID(), true);
                        DashboardAdmin.getLastActivityTime().put(player.getUUID(), nowMs);
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(SrvLang.t(player, "§eVOUS ÊTES AFK", "§eYOU ARE AFK"))));
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 40, 10));
                        String autoAfkName = player.getName().getString();
                        SrvLang.each(server, "§e" + autoAfkName + " est AFK (inactivité).", "§e" + autoAfkName + " is AFK (inactivity).");
                    }
                }
            }
            DashboardAdmin.lastPos.put(player.getUUID(), player.position());
        }

        // Proportional sleep
        if (DashboardAdmin.isProportionalSleepEnabled()) {
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
        if (DashboardAdmin.isFastLeafDecayEnabled() && !DashboardAdmin.getPendingLeafDecayMap().isEmpty()) {
            DashboardAdmin.setLeafDecayCounter(DashboardAdmin.getLeafDecayCounter() + 1);
            if (DashboardAdmin.getLeafDecayCounter() % 4 == 0) {
                for (var dimEntry : DashboardAdmin.getPendingLeafDecayMap().entrySet()) {
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
                DashboardAdmin.getPendingLeafDecayMap().values().removeIf(java.util.Set::isEmpty);
            }
        }

        // Restart programmé : annonces dégressives puis sauvegarde complète et arrêt propre.
        if (DashboardAdmin.restartTicks > 0) {
            DashboardAdmin.restartTicks--;
            int t = DashboardAdmin.restartTicks;
            if (t == 0) {
                SrvLang.each(server,
                    "§4§l⚠ Redémarrage du serveur — sauvegarde en cours…",
                    "§4§l⚠ Server restarting — saving…");
                saveAll();
                server.getPlayerList().saveAll();
                server.saveAllChunks(true, true, true);
                server.halt(false);
            } else if (t % 20 == 0) {
                int sec = t / 20;
                String msgFr = switch (sec) {
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
                String msgEn = switch (sec) {
                    case 1800 -> "30 minutes";
                    case 900  -> "15 minutes";
                    case 600  -> "10 minutes";
                    case 300  -> "5 minutes";
                    case 60   -> "1 minute";
                    case 30   -> "30 seconds";
                    case 10   -> "10 seconds";
                    case 5, 4, 3, 2, 1 -> sec + "…";
                    default   -> null;
                };
                if (msgFr != null)
                    SrvLang.each(server, "§c⚠ Redémarrage du serveur dans §e" + msgFr, "§c⚠ Server restarting in §e" + msgEn);
            }
        }

        // ClearLag
        if (DashboardAdmin.clearLagTicks > 0) {
            DashboardAdmin.clearLagTicks--;
            if (DashboardAdmin.clearLagTicks == 0) {
                long count = 0;
                for (ServerLevel level : server.getAllLevels()) for (net.minecraft.world.entity.Entity e : level.getAllEntities()) if (e instanceof net.minecraft.world.entity.item.ItemEntity) { e.discard(); count++; }
                long clearedItems = count;
                SrvLang.each(server, "§eClearLag : §f" + clearedItems + " items supprimés.", "§eClearLag: §f" + clearedItems + " items removed.");
            }
        }
        if (DashboardAdmin.removeMobsTicks > 0) {
            DashboardAdmin.removeMobsTicks--;
            if (DashboardAdmin.removeMobsTicks == 0) {
                long count = 0;
                for (ServerLevel level : server.getAllLevels()) for (net.minecraft.world.entity.Entity e : level.getAllEntities()) if (e instanceof net.minecraft.world.entity.monster.Monster && !e.hasCustomName()) { e.discard(); count++; }
                long removedMobs = count;
                SrvLang.each(server, "§cMobs supprimés : §f" + removedMobs, "§cMobs removed: §f" + removedMobs);
            }
        }

        // TPA and deal request expiry (every 20 ticks = 1 second)
        if (server.getTickCount() % 20 == 0) {
            long nowExpire = System.currentTimeMillis();
            // Expiration des mutes temporaires : retire + notifie le joueur en ligne.
            java.util.Iterator<java.util.Map.Entry<java.util.UUID, Long>> muteIt = DashboardAdmin.getMutedPlayers().entrySet().iterator();
            while (muteIt.hasNext()) {
                java.util.Map.Entry<java.util.UUID, Long> me = muteIt.next();
                if (me.getValue() != 0 && nowExpire >= me.getValue()) {
                    muteIt.remove();
                    ServerPlayer mp = server.getPlayerList().getPlayer(me.getKey());
                    if (mp != null) mp.sendSystemMessage(Component.literal(SrvLang.t(mp, "§aVotre mute a expiré, vous pouvez de nouveau écrire.", "§aYour mute has expired, you can chat again.")));
                }
            }
            java.util.Iterator<java.util.Map.Entry<java.util.UUID, Long>> tpaIt = TpaManager.getPendingTpaTimestamps().entrySet().iterator();
            while (tpaIt.hasNext()) {
                java.util.Map.Entry<java.util.UUID, Long> te = tpaIt.next();
                if (nowExpire - te.getValue() > 60_000L) {
                    java.util.UUID targetUid = te.getKey();
                    java.util.UUID senderUid = TpaManager.getTpaRequests().remove(targetUid);
                    TpaManager.getTpaHere().remove(targetUid);
                    tpaIt.remove();
                    if (senderUid != null) {
                        ServerPlayer tpaSender = server.getPlayerList().getPlayer(senderUid);
                        ServerPlayer tpaTarget = server.getPlayerList().getPlayer(targetUid);
                        if (tpaSender != null) tpaSender.sendSystemMessage(Component.literal(SrvLang.t(tpaSender, "§7Votre demande TPA vers §e" + DashboardAdmin.getPlayerNameCache().getOrDefault(targetUid, "?") + "§7 a expiré.", "§7Your TPA request to §e" + DashboardAdmin.getPlayerNameCache().getOrDefault(targetUid, "?") + "§7 has expired.")));
                        if (tpaTarget != null) tpaTarget.sendSystemMessage(Component.literal(SrvLang.t(tpaTarget, "§7La demande TPA de §e" + DashboardAdmin.getPlayerNameCache().getOrDefault(senderUid, "?") + "§7 a expiré.", "§7The TPA request from §e" + DashboardAdmin.getPlayerNameCache().getOrDefault(senderUid, "?") + "§7 has expired.")));
                    }
                }
            }
            java.util.Iterator<java.util.Map.Entry<java.util.UUID, Long>> dealIt = DealManager.getPendingDealTimestamps().entrySet().iterator();
            while (dealIt.hasNext()) {
                java.util.Map.Entry<java.util.UUID, Long> de = dealIt.next();
                if (nowExpire - de.getValue() > 60_000L) {
                    java.util.UUID targetUid = de.getKey();
                    java.util.UUID senderUid = DealManager.getPendingDeals().remove(targetUid);
                    dealIt.remove();
                    if (senderUid != null) {
                        ServerPlayer dealSender = server.getPlayerList().getPlayer(senderUid);
                        ServerPlayer dealTarget = server.getPlayerList().getPlayer(targetUid);
                        if (dealSender != null) dealSender.sendSystemMessage(Component.literal(SrvLang.t(dealSender, "§7Votre demande d'échange a expiré.", "§7Your trade request has expired.")));
                        if (dealTarget != null) dealTarget.sendSystemMessage(Component.literal(SrvLang.t(dealTarget, "§7La demande d'échange de §e" + DashboardAdmin.getPlayerNameCache().getOrDefault(senderUid, "?") + "§7 a expiré.", "§7The trade request from §e" + DashboardAdmin.getPlayerNameCache().getOrDefault(senderUid, "?") + "§7 has expired.")));
                    }
                }
            }
        }

        // Scheduled broadcasts
        for (int i = 0; i < DashboardAdmin.scheduledCounters.size(); i++) {
            int c = DashboardAdmin.scheduledCounters.get(i) - 1;
            DashboardAdmin.scheduledCounters.set(i, c);
            if (c <= 0) {
                DashboardAdmin.scheduledCounters.set(i, DashboardAdmin.getScheduledIntervalsArray()[i]);
                String schedMsg = DashboardAdmin.getScheduledMsgsArray()[i];
                SrvLang.each(server, "§6§l[Annonce] §r" + schedMsg, "§6§l[Announcement] §r" + schedMsg);
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

    /** Applique un mute (temporaire si {@code seconds}>0, sinon permanent) avec log, sanction, webhook et persistance. */
    private static int applyMute(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, long seconds)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayerOrException();
        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
        DashboardAdmin.muteFor(target.getUUID(), seconds);
        ModerationPersistence.save();
        String durLabel = seconds <= 0 ? "" : DashboardAdmin.formatDurationShort(seconds);
        String suffix   = seconds <= 0 ? "" : " §7(" + durLabel + ")";
        DashboardAdmin.addLog(target.getUUID(), "Muted " + (seconds <= 0 ? "définitivement" : "pour " + durLabel) + " par " + admin.getName().getString());
        DashboardAdmin.addSanction("MUTE", target.getName().getString(), admin.getName().getString(), durLabel);
        DiscordWebhook.sendSanction(DashboardAdmin.getWebhookSanctions(), admin.getName().getString(), target.getName().getString(), "MUTE", seconds <= 0 ? "permanent" : durLabel);
        admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§e" + target.getName().getString() + " est maintenant muet" + suffix + "§e.", "§e" + target.getName().getString() + " is now muted" + suffix + "§e.")));
        target.sendSystemMessage(Component.literal(SrvLang.t(target, "§cVous avez été rendu muet par un admin" + suffix + "§c.", "§cYou have been muted by an admin" + suffix + "§c.")));
        return 1;
    }

    // ─── Utility ──────────────────────────────────────────────────────────────
    private static String resolvePlayerName(MinecraftServer server, java.util.UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        return DashboardAdmin.getPlayerNameCache().getOrDefault(uuid, uuid.toString().substring(0, 8) + "…");
    }
}
