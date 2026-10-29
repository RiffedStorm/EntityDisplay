package dev.riffedstorm.entitydisplay;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue PERMISSION_LEVEL = BUILDER
            .comment("Permission level needed to spawn/edit/delete display entities (2 = operator).")
            .defineInRange("permissionLevel", 2, 0, 4);

    public static final ModConfigSpec.BooleanValue ALLOW_CREATIVE = BUILDER
            .comment("Let any player in creative mode edit displays, regardless of permission level.")
            .define("allowCreativePlayers", true);

    public static final ModConfigSpec.DoubleValue MAX_DISTANCE = BUILDER
            .comment("Maximum distance (blocks) between a player and the display they edit.")
            .defineInRange("maxEditDistance", 64.0, 4.0, 512.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {}
}
