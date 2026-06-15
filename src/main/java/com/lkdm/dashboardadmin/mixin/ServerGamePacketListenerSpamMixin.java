package com.lkdm.dashboardadmin.mixin;

import com.lkdm.dashboardadmin.DashboardAdmin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contournement de l'anti-spam chat vanilla pour les joueurs exemptés (OP + rôles de modération,
 * uniquement si le toggle Features est activé — voir {@link DashboardAdmin#isAntiSpamBypassed}).
 *
 * Vanilla incrémente {@code chatSpamTickCount} de 20 par message et déconnecte au-delà de 200
 * (≈ 10 messages très rapprochés). Plutôt que de toucher à la logique de déconnexion, on remet le
 * compteur à zéro à chaque tick pour les joueurs exemptés : il n'atteint donc jamais le seuil.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerSpamMixin {

    @Shadow public ServerPlayer player;
    @Shadow @Final private AtomicInteger chatSpamTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void dashboardadmin$bypassChatSpam(CallbackInfo ci) {
        if (DashboardAdmin.isAntiSpamBypassed(player)) {
            chatSpamTickCount.set(0);
        }
    }
}
