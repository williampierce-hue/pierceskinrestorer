package com.pierce.skinrestorer;

import com.pierce.skinrestorer.command.SkinCommand;
import com.pierce.skinrestorer.config.ModConfig;
import com.pierce.skinrestorer.handler.PlayerEventHandler;
import com.pierce.skinrestorer.network.SkinPacketHandler;
import com.pierce.skinrestorer.skin.SkinStorage;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Pierce Skin Restorer - Server-Side Only
 *
 * Restores player skins on offline mode servers by injecting
 * skin texture data into GameProfiles sent to clients.
 *
 * Compatible with GregTech: New Horizons 2.8.4
 * No client mod required - works with vanilla clients!
 */
@Mod(
    modid = PierceSkinRestorer.MODID,
    name = PierceSkinRestorer.NAME,
    version = PierceSkinRestorer.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:Forge@[10.13.4.1614,)",
    acceptableRemoteVersions = "*"  // Server-side only - client doesn't need this mod
)
public class PierceSkinRestorer {

    public static final String MODID = "pierceskinrestorer";
    public static final String NAME = "Pierce Skin Restorer";
    public static final String VERSION = "1.0.3";

    @Instance(MODID)
    public static PierceSkinRestorer instance;

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    private File dataDir;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Pierce Skin Restorer Pre-Initialization (Server-Side Only)");

        // Initialize config
        ModConfig.init(new File(event.getModConfigurationDirectory(), MODID + ".cfg"));

        // Initialize skin storage
        dataDir = new File(event.getModConfigurationDirectory().getParentFile(), "skinrestorer");
        SkinStorage.init(dataDir);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Pierce Skin Restorer Initialization");

        // Register event handlers
        PlayerEventHandler handler = new PlayerEventHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance().bus().register(handler);

        LOGGER.info("Pierce Skin Restorer loaded successfully - Server-side only, no client mod needed!");
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        LOGGER.info("Registering /skin command");
        event.registerServerCommand(new SkinCommand());

        // Initialize packet handler for skin injection
        SkinPacketHandler.init();
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        LOGGER.info("Server stopping - saving skin data");
        SkinStorage.save();
    }

    public File getDataDir() {
        return dataDir;
    }
}
