package com.avilixradiomod.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;

import java.util.List;

public final class ModConfigs {
    private ModConfigs() {}

    // ---------------- COMMON ----------------
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    // ---------------- CLIENT ----------------
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        var commonPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = commonPair.getRight();
        COMMON = commonPair.getLeft();

        var clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "avilixradiomod-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "avilixradiomod-client.toml");
    }

    // ======= COMMON SETTINGS =======
    public static final class Common {
        public final ModConfigSpec.IntValue scanEveryTicks;
        public final ModConfigSpec.IntValue maxHearDistance;
        public final ModConfigSpec.DoubleValue smoothing;
        public final ModConfigSpec.DoubleValue stopThreshold;
        public final ModConfigSpec.IntValue defaultVolume;

        public final ModConfigSpec.IntValue maxUrlLength;

        Common(ModConfigSpec.Builder b) {
            b.push("audio");
            scanEveryTicks = b.comment("How often (in ticks) to rescan nearby speakers/radios.")
                    .defineInRange("scanEveryTicks", 10, 1, 200);

            maxHearDistance = b.comment("Max distance in blocks where speakers are audible (also affects scan radius).")
                    .defineInRange("maxHearDistance", 30, 4, 128);

            smoothing = b.comment("Volume smoothing factor 0..1. Higher = faster response.")
                    .defineInRange("smoothing", 0.20, 0.01, 1.00);

            stopThreshold = b.comment("If target volume is 0 and smoothed volume drops below this -> stop decoding.")
                    .defineInRange("stopThreshold", 0.50, 0.0, 5.0);

            defaultVolume = b.comment("Default volume for new radios (0..100).")
                    .defineInRange("defaultVolume", 50, 0, 100);
            b.pop();

            b.push("validation");
            maxUrlLength = b.comment("Max URL length allowed in GUI/network.")
                    .defineInRange("maxUrlLength", 8192, 128, 16384);
            b.pop();
        }
    }

    // ======= CLIENT SETTINGS =======
    public static final class Client {
        public final ModConfigSpec.ConfigValue<String> defaultUrl;
        public final ModConfigSpec.IntValue historyLimit;
        public final ModConfigSpec.ConfigValue<List<? extends String>> urlHistory;


        Client(ModConfigSpec.Builder b) {
            b.push("radio");

            defaultUrl = b.comment("Default URL shown in the GUI when empty.")
                    .define("defaultUrl", "http://91.223.90.244:8081/public/avilix");

            historyLimit = b.comment("How many recent URLs to remember.")
                    .defineInRange("historyLimit", 12, 0, 64);

            urlHistory = b.comment("Recent URLs (client-side only).")
                    .defineListAllowEmpty("urlHistory", List.of(), o -> o instanceof String s && !s.isBlank());

            b.pop();
        }
    }
}
