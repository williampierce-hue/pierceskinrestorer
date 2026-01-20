package com.pierce.skinrestorer.handler;

import com.pierce.skinrestorer.PierceSkinRestorer;
import com.pierce.skinrestorer.skin.SkinManager;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Handles player join/leave events for skin management.
 */
public class PlayerEventHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            final EntityPlayerMP player = (EntityPlayerMP) event.player;

            // Delay skin loading slightly to ensure player is fully connected
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000); // Wait 1 second for connection to stabilize
                        SkinManager.onPlayerJoin(player);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            }, "SkinJoin-" + player.getCommandSenderName()).start();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            SkinManager.onPlayerLeave(player);
            PierceSkinRestorer.LOGGER.debug("Player logged out: " + player.getCommandSenderName());
        }
    }
}
