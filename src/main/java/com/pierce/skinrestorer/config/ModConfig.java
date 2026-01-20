package com.pierce.skinrestorer.config;

import com.pierce.skinrestorer.PierceSkinRestorer;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * Mod configuration handler.
 */
public class ModConfig {

    private static Configuration config;

    // Config values
    public static int fetchTimeoutSeconds = 10;
    public static boolean requirePermission = false;
    public static boolean logDebug = false;

    public static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    private static void loadConfig() {
        try {
            config.load();

            fetchTimeoutSeconds = config.getInt(
                "fetchTimeoutSeconds",
                Configuration.CATEGORY_GENERAL,
                10,
                5, 60,
                "Timeout for fetching skins from Mojang API in seconds"
            );

            requirePermission = config.getBoolean(
                "requirePermission",
                Configuration.CATEGORY_GENERAL,
                false,
                "Require OP permission to use /skin command"
            );

            logDebug = config.getBoolean(
                "logDebug",
                Configuration.CATEGORY_GENERAL,
                false,
                "Enable debug logging"
            );

        } catch (Exception e) {
            PierceSkinRestorer.LOGGER.error("Error loading config", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static void save() {
        if (config != null && config.hasChanged()) {
            config.save();
        }
    }

    public static Configuration getConfig() {
        return config;
    }
}
