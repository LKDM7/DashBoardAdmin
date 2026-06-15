package com.lkdm.dashboardadmin.mixin;

import com.lkdm.dashboardadmin.DashboardAdmin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Contournement de l'anti-spam chat vanilla pour les joueurs exemptés (OP + rôles de modération,
 * uniquement si le toggle est activé — voir {@link DashboardAdmin#isAntiSpamBypassed}).
 *
 * Vanilla appelle {@code detectRateSpam()} après chaque message : on annule l'appel pour les
 * joueurs exemptés → compteur jamais incrémenté, aucun kick possible, quel que soit le débit.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerSpamMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "detectRateSpam", at = @At("HEAD"), cancellable = true)
    private void dashboardadmin$bypassChatSpam(CallbackInfo ci) {
        if (DashboardAdmin.isAntiSpamBypassed(player)) {
            ci.cancel();
        }
    }
}
