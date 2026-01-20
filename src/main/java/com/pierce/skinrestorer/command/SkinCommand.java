package com.pierce.skinrestorer.command;

import com.pierce.skinrestorer.PierceSkinRestorer;
import com.pierce.skinrestorer.config.ModConfig;
import com.pierce.skinrestorer.skin.SkinManager;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * /skin command for setting player skins.
 * Server-side only - works with vanilla clients.
 */
public class SkinCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "skin";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/skin <set|clear|reload> [username] OR /skin <player> set <username>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // Allow all players to use basic commands
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // Allow any player, or console for admin commands
        if (sender instanceof EntityPlayerMP) {
            // If requirePermission is enabled, check for OP
            if (ModConfig.requirePermission) {
                return sender.canCommandSenderUseCommand(2, this.getCommandName());
            }
            return true; // All players can use basic skin commands
        }
        // Console can use admin commands
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        String subCommand = args[0].toLowerCase();

        // Console can only use admin commands
        if (!(sender instanceof EntityPlayerMP)) {
            if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
                handleAdminSetFromConsole(sender, args);
            } else {
                throw new WrongUsageException("Console usage: /skin <player> set <username>");
            }
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (subCommand.equals("set")) {
            handleSet(player, args);
        } else if (subCommand.equals("clear")) {
            handleClear(player);
        } else if (subCommand.equals("reload")) {
            handleReload(player);
        } else {
            // Check if it's an admin command: /skin <player> set <username>
            if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
                handleAdminSet(player, args);
            } else {
                throw new WrongUsageException(getCommandUsage(sender));
            }
        }
    }

    private void handleSet(final EntityPlayerMP player, String[] args) {
        if (args.length < 2) {
            sendError(player, "Usage: /skin set <username>");
            return;
        }

        final String targetUsername = args[1];
        sendMessage(player, "Fetching skin for " + targetUsername + "...");

        // Run async to not block server
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean success = SkinManager.setSkinByUsername(player, targetUsername);
                    if (success) {
                        sendSuccess(player, "Skin set to " + targetUsername);
                        sendMessage(player, "Other players will see your new skin when you respawn or rejoin.");
                    } else {
                        sendError(player, "Failed to fetch skin for " + targetUsername);
                        sendMessage(player, "Make sure the username exists and has a valid Mojang account.");
                    }
                } catch (Exception e) {
                    PierceSkinRestorer.LOGGER.error("Error setting skin", e);
                    sendError(player, "Error: " + e.getMessage());
                }
            }
        }, "SkinFetcher-" + player.getCommandSenderName()).start();
    }

    private void handleClear(EntityPlayerMP player) {
        SkinManager.clearSkin(player);
        sendSuccess(player, "Skin cleared");
        sendMessage(player, "You will appear with the default skin to other players.");
    }

    private void handleReload(final EntityPlayerMP player) {
        sendMessage(player, "Reloading skin...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean success = SkinManager.reloadSkin(player);
                    if (success) {
                        sendSuccess(player, "Skin reloaded");
                    } else {
                        sendMessage(player, "No saved skin found. Use /skin set <username> first.");
                    }
                } catch (Exception e) {
                    PierceSkinRestorer.LOGGER.error("Error reloading skin", e);
                    sendError(player, "Error: " + e.getMessage());
                }
            }
        }, "SkinReload-" + player.getCommandSenderName()).start();
    }

    private void handleAdminSet(final EntityPlayerMP sender, String[] args) {
        // /skin <player> set <username>
        // Requires OP level 2
        if (!sender.canCommandSenderUseCommand(2, "skin.admin")) {
            sendError(sender, "You don't have permission to change other players' skins");
            return;
        }

        String targetPlayerName = args[0];
        final String targetSkinUsername = args[2];

        final EntityPlayerMP targetPlayer = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(targetPlayerName); // getPlayerByUsername

        if (targetPlayer == null) {
            sendError(sender, "Player " + targetPlayerName + " not found");
            return;
        }

        sendMessage(sender, "Setting " + targetPlayerName + "'s skin to " + targetSkinUsername + "...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean success = SkinManager.setSkinByUsername(targetPlayer, targetSkinUsername);
                    if (success) {
                        sendSuccess(sender, "Set " + targetPlayer.getCommandSenderName() + "'s skin to " + targetSkinUsername);
                        sendSuccess(targetPlayer, "Your skin was set to " + targetSkinUsername + " by " + sender.getCommandSenderName());
                    } else {
                        sendError(sender, "Failed to fetch skin for " + targetSkinUsername);
                    }
                } catch (Exception e) {
                    PierceSkinRestorer.LOGGER.error("Error setting admin skin", e);
                    sendError(sender, "Error: " + e.getMessage());
                }
            }
        }, "SkinFetcher-Admin").start();
    }

    private void handleAdminSetFromConsole(final ICommandSender sender, String[] args) {
        // /skin <player> set <username> - from console
        String targetPlayerName = args[0];
        final String targetSkinUsername = args[2];

        final EntityPlayerMP targetPlayer = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(targetPlayerName); // getPlayerByUsername

        if (targetPlayer == null) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Player " + targetPlayerName + " not found"));
            return;
        }

        sender.addChatMessage(new ChatComponentText("Setting " + targetPlayerName + "'s skin to " + targetSkinUsername + "..."));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean success = SkinManager.setSkinByUsername(targetPlayer, targetSkinUsername);
                    if (success) {
                        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Set " + targetPlayer.getCommandSenderName() + "'s skin to " + targetSkinUsername));
                        sendSuccess(targetPlayer, "Your skin was set to " + targetSkinUsername + " by console");
                    } else {
                        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Failed to fetch skin for " + targetSkinUsername));
                    }
                } catch (Exception e) {
                    PierceSkinRestorer.LOGGER.error("Error setting skin from console", e);
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Error: " + e.getMessage()));
                }
            }
        }, "SkinFetcher-Console").start();
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        List<String> options = new ArrayList<String>();

        if (args.length == 1) {
            options.add("set");
            options.add("clear");
            options.add("reload");

            // Add online player names for admin command
            List<?> playerList = MinecraftServer.getServer().getConfigurationManager().playerEntityList;
            for (Object obj : playerList) {
                EntityPlayerMP p = (EntityPlayerMP) obj;
                options.add(p.getCommandSenderName());
            }
        } else if (args.length == 2) {
            // If first arg is a player name, suggest "set"
            EntityPlayerMP target = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152612_a(args[0]);
            if (target != null) {
                options.add("set");
            }
        }

        return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
    }

    private void sendMessage(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "[Skin] " + EnumChatFormatting.WHITE + message));
    }

    private void sendSuccess(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "[Skin] " + EnumChatFormatting.GREEN + message));
    }

    private void sendError(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "[Skin] " + EnumChatFormatting.RED + message));
    }
}
