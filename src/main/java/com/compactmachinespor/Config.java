package com.compactmachinespor;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder().gameRestart();

    public static final ModConfigSpec.BooleanValue ENABLE_SCAN = BUILDER
            .comment("Scan")
            .translation("config.compactmachinespor.enable_scan")
            .define("Scan", false);

    public static final ModConfigSpec.ConfigValue<String> SCAN_TAG = BUILDER
            .comment("Block Tag to Scan")
            .translation("config.compactmachinespor.scan_tag")
            .define("ScanTag", "c:ores");

    public static final ModConfigSpec.IntValue EVALUATE_SECONDS = BUILDER
            .comment("Evaluate seconds")
            .translation("config.compactmachinespor.evaluate_seconds")
            .defineInRange("EvaluateSeconds", 300, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue UNPACK_PERMISSION_LEVEL = BUILDER
            .comment("Unpack permission level")
            .translation("config.compactmachinespor.unpack_permission_level")
            .defineInRange("PermissionLevel", 2, 0, 4);

    public static final ModConfigSpec.BooleanValue ENABLE_INVENTORY_AUDIT = BUILDER
            .comment("Enable inventory baseline audit (conservation check)")
            .translation("config.compactmachinespor.enable_inventory_audit")
            .define("EnableInventoryAudit", true);

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SUSPICIOUS_MODS = BUILDER
            .comment("Mod IDs whose blocks are untrusted (unscannable storage, e.g. AE2). Evaluation aborts if found.")
            .translation("config.compactmachinespor.suspicious_mods")
            .defineListAllowEmpty("SuspiciousMods", java.util.List.of("ae2", "refinedstorage"), o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SUSPICIOUS_BLOCKS = BUILDER
            .comment("Specific block IDs (namespace:path) that are untrusted.")
            .translation("config.compactmachinespor.suspicious_blocks")
            .defineListAllowEmpty("SuspiciousBlocks", java.util.List.of(), o -> o instanceof String);

    public static final ModConfigSpec.BooleanValue ENABLE_FACTORY_REVERT = BUILDER
            .comment("Allow launcher stick to revert FactoryBlock back to bound machine. Default false to prevent item duplication.")
            .translation("config.compactmachinespor.enable_factory_revert")
            .define("EnableFactoryRevert", false);

    static final ModConfigSpec SPEC = BUILDER.build();
}
