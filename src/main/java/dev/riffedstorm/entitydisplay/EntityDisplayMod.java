package dev.riffedstorm.entitydisplay;

import com.mojang.logging.LogUtils;
import dev.riffedstorm.entitydisplay.network.ModNetworking;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(EntityDisplayMod.MOD_ID)
public final class EntityDisplayMod {
    public static final String MOD_ID = "entitydisplay";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EntityDisplayMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modBus.addListener(ModNetworking::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
