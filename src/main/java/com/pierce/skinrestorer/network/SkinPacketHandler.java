package com.pierce.skinrestorer.network;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.pierce.skinrestorer.PierceSkinRestorer;
import com.pierce.skinrestorer.skin.SkinManager;
import cpw.mods.fml.common.FMLCommonHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S1DPacketEntityEffect;
import net.minecraft.network.play.server.S1FPacketSetExperience;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S39PacketPlayerAbilities;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;

import java.lang.reflect.Field;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Intercepts outgoing packets to inject skin data into GameProfiles.
 * This allows vanilla clients to see custom skins without any client mod.
 *
 * Compatible with Minecraft 1.7.10 / Forge 10.13.4.1614
 */
public class SkinPacketHandler {

    // Reflection fields - initialized once
    private static Field networkManagerField;      // NetHandlerPlayServer.networkManager
    private static Field channelField;             // NetworkManager.channel
    private static Field spawnPlayerProfileField;  // S0CPacketSpawnPlayer.gameProfile

    private static boolean reflectionFailed = false;
    private static boolean initialized = false;

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            // Find NetworkManager field in NetHandlerPlayServer
            // SRG: field_147371_a, MCP: networkManager
            networkManagerField = findField(
                net.minecraft.network.NetHandlerPlayServer.class,
                "field_147371_a", "networkManager"
            );

            // Find channel field in NetworkManager
            // SRG: field_150746_k, MCP: channel
            channelField = findField(
                NetworkManager.class,
                "field_150746_k", "channel"
            );

            // Find GameProfile field in S0CPacketSpawnPlayer
            // In 1.7.10, S0CPacketSpawnPlayer stores player data differently
            // We need to find the field that holds GameProfile or construct one
            // SRG: field_148955_a might not exist - find by type instead
            spawnPlayerProfileField = findFieldByType(
                S0CPacketSpawnPlayer.class,
                GameProfile.class
            );

            if (spawnPlayerProfileField == null) {
                // 1.7.10 might not have a direct GameProfile field
                // We need a different approach - explained below
                PierceSkinRestorer.LOGGER.warn("No GameProfile field found in S0CPacketSpawnPlayer - using alternative approach");
            }

            PierceSkinRestorer.LOGGER.info("Packet handler initialized successfully");

        } catch (Exception e) {
            PierceSkinRestorer.LOGGER.error("Failed to initialize packet reflection", e);
            reflectionFailed = true;
        }
    }

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                PierceSkinRestorer.LOGGER.debug("Found field " + name + " in " + clazz.getSimpleName());
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new RuntimeException("Could not find field in " + clazz.getName() + " (tried: " + String.join(", ", names) + ")");
    }

    private static Field findFieldByType(Class<?> clazz, Class<?> fieldType) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType() == fieldType) {
                field.setAccessible(true);
                PierceSkinRestorer.LOGGER.debug("Found " + fieldType.getSimpleName() + " field by type: " + field.getName());
                return field;
            }
        }
        return null;
    }

    /**
     * Get the Netty channel from a player's network handler.
     */
    private static Channel getChannel(EntityPlayerMP player) {
        try {
            NetworkManager networkManager = (NetworkManager) networkManagerField.get(player.playerNetServerHandler);
            return (Channel) channelField.get(networkManager);
        } catch (Exception e) {
            PierceSkinRestorer.LOGGER.error("Failed to get channel for " + player.getCommandSenderName(), e);
            return null;
        }
    }

    /**
     * Inject our channel handler into a player's network channel.
     * Called when a player joins the server.
     */
    public static void injectPlayer(EntityPlayerMP player) {
        if (reflectionFailed) {
            return;
        }

        if (player == null || player.playerNetServerHandler == null) {
            return; // Player not fully connected or already disconnected
        }

        try {
            Channel channel = getChannel(player);
            if (channel == null) {
                return;
            }

            // Remove old handler if exists
            try {
                if (channel.pipeline().get("pierceskin_handler") != null) {
                    channel.pipeline().remove("pierceskin_handler");
                }
            } catch (NoSuchElementException ignored) {
            }

            // Add our handler before the packet_handler
            channel.pipeline().addBefore("packet_handler", "pierceskin_handler",
                new SkinChannelHandler(player));

            PierceSkinRestorer.LOGGER.debug("Injected skin handler for " + player.getCommandSenderName());

        } catch (Exception e) {
            PierceSkinRestorer.LOGGER.error("Failed to inject channel handler for " + player.getCommandSenderName(), e);
        }
    }

    /**
     * Remove our channel handler when a player disconnects.
     */
    public static void removePlayer(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) {
            return; // Player not fully connected
        }
        try {
            Channel channel = getChannel(player);
            if (channel != null && channel.pipeline().get("pierceskin_handler") != null) {
                channel.pipeline().remove("pierceskin_handler");
            }
        } catch (Exception e) {
            // Ignore - player already disconnected
        }
    }

    /**
     * Channel handler that intercepts outgoing packets.
     */
    private static class SkinChannelHandler extends ChannelDuplexHandler {

        private final EntityPlayerMP viewer;

        public SkinChannelHandler(EntityPlayerMP viewer) {
            this.viewer = viewer;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Packet) {
                msg = processPacket((Packet) msg);
            }
            super.write(ctx, msg, promise);
        }

        private Packet processPacket(Packet packet) {
            try {
                if (packet instanceof S0CPacketSpawnPlayer) {
                    return processSpawnPacket((S0CPacketSpawnPlayer) packet);
                }
            } catch (Exception e) {
                PierceSkinRestorer.LOGGER.debug("Error processing packet: " + e.getMessage());
            }
            return packet;
        }

        private S0CPacketSpawnPlayer processSpawnPacket(S0CPacketSpawnPlayer packet) {
            if (spawnPlayerProfileField == null) {
                return packet;
            }

            try {
                GameProfile originalProfile = (GameProfile) spawnPlayerProfileField.get(packet);
                if (originalProfile == null) {
                    return packet;
                }

                // Get modified profile with skin data
                GameProfile modifiedProfile = SkinManager.getModifiedProfile(originalProfile);

                if (modifiedProfile != null && modifiedProfile != originalProfile) {
                    spawnPlayerProfileField.set(packet, modifiedProfile);
                    PierceSkinRestorer.LOGGER.debug("Injected skin into spawn packet for " + originalProfile.getName());
                }

            } catch (Exception e) {
                PierceSkinRestorer.LOGGER.debug("Error processing spawn packet: " + e.getMessage());
            }
            return packet;
        }
    }

    /**
     * Force refresh a player's skin for all online players.
     * This respawns the player entity for other clients.
     */
    public static void refreshPlayerSkin(final EntityPlayerMP targetPlayer) {
        if (targetPlayer == null || targetPlayer.playerNetServerHandler == null) {
            return; // Player disconnected
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }

        // First, refresh the player's own view of their skin
        refreshSelfSkin(targetPlayer);

        // Get all online players - copy to avoid ConcurrentModificationException
        List<?> playerList;
        synchronized (server.getConfigurationManager().playerEntityList) {
            playerList = new java.util.ArrayList<Object>(server.getConfigurationManager().playerEntityList);
        }

        for (Object obj : playerList) {
            EntityPlayerMP viewer = (EntityPlayerMP) obj;

            if (viewer == targetPlayer) {
                continue; // Already handled above
            }

            // Check if the viewer can see the target (same world, within distance)
            if (viewer.worldObj == targetPlayer.worldObj) {
                double distance = viewer.getDistanceToEntity(targetPlayer);
                if (distance < 256) { // Within render distance
                    // Remove and re-add the player entity for this viewer
                    // This triggers a new spawn packet which our handler will intercept
                    viewer.playerNetServerHandler.sendPacket(
                        new net.minecraft.network.play.server.S13PacketDestroyEntities(targetPlayer.getEntityId())
                    );

                    // Send spawn packet - our handler will inject the skin
                    viewer.playerNetServerHandler.sendPacket(
                        new S0CPacketSpawnPlayer(targetPlayer)
                    );
                }
            }
        }

        PierceSkinRestorer.LOGGER.info("Refreshed skin display for " + targetPlayer.getCommandSenderName());
    }

    /**
     * Refresh a player's own view of their skin by simulating a respawn.
     * This sends packets to make the client reload its own skin.
     */
    private static void refreshSelfSkin(final EntityPlayerMP player) {
        try {
            // Store current state
            final double x = player.posX;
            final double y = player.posY;
            final double z = player.posZ;
            final float yaw = player.rotationYaw;
            final float pitch = player.rotationPitch;
            final int dimension = player.dimension;

            // Get game type
            WorldSettings.GameType gameType = player.theItemInWorldManager.getGameType();

            // Send respawn packet - this forces client to reload player entity including skin
            // We send a respawn to a different dimension first, then back to current dimension
            int fakeDimension = (dimension == 0) ? -1 : 0;

            player.playerNetServerHandler.sendPacket(new S07PacketRespawn(
                fakeDimension,
                player.worldObj.difficultySetting,
                player.worldObj.getWorldInfo().getTerrainType(),
                gameType
            ));

            // Now respawn back to the real dimension
            player.playerNetServerHandler.sendPacket(new S07PacketRespawn(
                dimension,
                player.worldObj.difficultySetting,
                player.worldObj.getWorldInfo().getTerrainType(),
                gameType
            ));

            // Restore position
            player.playerNetServerHandler.sendPacket(new S08PacketPlayerPosLook(
                x, y, z, yaw, pitch, false
            ));

            // Restore player abilities (flying, creative mode, etc.)
            player.playerNetServerHandler.sendPacket(new S39PacketPlayerAbilities(player.capabilities));

            // Restore health and food
            player.playerNetServerHandler.sendPacket(new S06PacketUpdateHealth(
                player.getHealth(),
                player.getFoodStats().getFoodLevel(),
                player.getFoodStats().getSaturationLevel()
            ));

            // Restore experience
            player.playerNetServerHandler.sendPacket(new S1FPacketSetExperience(
                player.experience,
                player.experienceTotal,
                player.experienceLevel
            ));

            // Restore active potion effects
            for (Object effectObj : player.getActivePotionEffects()) {
                PotionEffect effect = (PotionEffect) effectObj;
                player.playerNetServerHandler.sendPacket(new S1DPacketEntityEffect(player.getEntityId(), effect));
            }

            // Update the player's loaded chunks (the respawn clears them)
            player.mcServer.getConfigurationManager().updateTimeAndWeatherForPlayer(player, player.worldObj);
            player.mcServer.getConfigurationManager().syncPlayerInventory(player);

            PierceSkinRestorer.LOGGER.debug("Refreshed self skin view for " + player.getCommandSenderName());

        } catch (Exception e) {
            PierceSkinRestorer.LOGGER.error("Error refreshing self skin for " + player.getCommandSenderName(), e);
        }
    }
}
