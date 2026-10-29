package dev.riffedstorm.entitydisplay.network;

import dev.riffedstorm.entitydisplay.server.DisplayEditService;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // Optional: players without the mod can still join and see displays; only builders need it.
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(SpawnDisplayPayload.TYPE, SpawnDisplayPayload.STREAM_CODEC, DisplayEditService::handleSpawn);
        registrar.playToServer(UpdateDisplayPayload.TYPE, UpdateDisplayPayload.STREAM_CODEC, DisplayEditService::handleUpdate);
        registrar.playToServer(DeleteDisplayPayload.TYPE, DeleteDisplayPayload.STREAM_CODEC, DisplayEditService::handleDelete);
        // The lambda body only touches client classes when it runs, which never happens on a dedicated server.
        registrar.playToClient(SelectDisplayPayload.TYPE, SelectDisplayPayload.STREAM_CODEC,
                (payload, context) -> dev.riffedstorm.entitydisplay.client.DisplayTool.selectWhenLoaded(payload.entityId()));
    }
}
