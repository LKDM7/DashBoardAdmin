package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — Synchronise vers le client la liste des zones que CE joueur a le droit de voir
 * (toutes pour un op, uniquement celles où il est membre sinon).
 *
 * <p>Format sérialisé, une zone par ligne ({@code \n}) :</p>
 * <pre>name|minX,minY,minZ|maxX,maxY,maxZ|enabled</pre>
 *
 * <p>Volontairement minimal : ni membres ni flags ne quittent le serveur.</p>
 */
public record ZoneSyncPayload(String zonesSerialized) implements CustomPacketPayload {

    public static final Type<ZoneSyncPayload> TYPE = new Type<>(ModMessages.ZONE_SYNC);

    public static final StreamCodec<FriendlyByteBuf, ZoneSyncPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeUtf(p.zonesSerialized()),
        buf -> new ZoneSyncPayload(buf.readUtf())
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
