package com.lkdm.dashboardadmin.mixin;

import com.lkdm.dashboardadmin.DashboardAdmin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanish : masque un joueur "vanished" aux AUTRES joueurs sans casser son propre suivi.
 *
 * L'ancien vanish appelait {@code ChunkMap.removeEntity(player)}, qui pour un ServerPlayer
 * appelle aussi {@code updatePlayerStatus(player, false)} → le joueur n'est plus un chargeur
 * de chunks (gel + chunks qui ne chargent plus). Ici on agit uniquement sur le suivi
 * d'entités côté observateur : pour chaque observateur (≠ le joueur lui-même), si l'entité
 * suivie est vanished, on retire l'observateur du suivi (paquet de despawn) et on annule
 * l'ajout. Le joueur reste pleinement suivi pour SES chunks et SES déplacements.
 *
 * Au désengagement du vanish, {@code updatePlayer} n'est plus annulé → le tracker re-spawn
 * automatiquement l'entité chez les observateurs au tick de suivi suivant.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityVanishMixin {

    @Shadow @Final Entity entity;

    @Shadow public abstract void removePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void dashboardadmin$hideVanished(ServerPlayer player, CallbackInfo ci) {
        if (entity instanceof ServerPlayer tracked
                && tracked != player
                && DashboardAdmin.isVanished(tracked.getUUID())) {
            removePlayer(player); // envoie le despawn si l'observateur le suivait
            ci.cancel();          // n'ajoute pas / ne respawn pas pour cet observateur
        }
    }
}
