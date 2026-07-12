package gg.lakehouse.scanner;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ScannerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PRINTOUT_MATERIALS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("scanner");
        REQUIRE_PRINTOUT_MATERIALS = builder
            .comment("If true, createPrintout() consumes one plain paper per page.",
                     "Paper is taken from a folder in the scanner (the document is",
                     "filed into it), or from an exact-count paper stack in the slot.")
            .define("requirePrintoutMaterials", false);
        builder.pop();
        SPEC = builder.build();
    }

    private ScannerConfig() {}
}
