package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — Échoie au joueur sa sélection de baguette en cours (points A / B),
 * pour que l'overlay client dessine la boîte de sélection en temps réel.
 *
 * <p>Envoyé à chaque pose de point, et avec {@code hasA=false, hasB=false}
 * après {@code /zone create} pour effacer la prévisualisation.</p>
 */
public record WandSelectionPayload(
    boolean hasA, int ax, int ay, int az,
    boolean hasB, int bx, int by, int bz
) implements CustomPacketPayload {

    public static final Type<WandSelectionPayload> TYPE = new Type<>(ModMessages.WAND_SELECTION);

    public static final StreamCodec<FriendlyByteBuf, WandSelectionPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBoolean(p.hasA()); buf.writeInt(p.ax()); buf.writeInt(p.ay()); buf.writeInt(p.az());
            buf.writeBoolean(p.hasB()); buf.writeInt(p.bx()); buf.writeInt(p.by()); buf.writeInt(p.bz());
        },
        buf -> new WandSelectionPayload(
            buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
            buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt())
    );

    /** Sélection vide (efface la prévisualisation côté client). */
    public static WandSelectionPayload empty() {
        return new WandSelectionPayload(false, 0, 0, 0, false, 0, 0, 0);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
