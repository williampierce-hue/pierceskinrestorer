# Pierce Skin Restorer

A **server-side only** Minecraft 1.7.10 Forge mod that restores player skins on offline mode servers.

**No client mod required!** Works with vanilla clients and is compatible with GregTech: New Horizons (GTNH) 2.8.4.

## Download

**[Download Latest Release](https://github.com/williampierce-hue/pierceskinrestorer/releases/latest)**

## Features

- **Server-side only** - Players don't need to install anything
- **Works with vanilla clients** - No client mod required
- **Instant skin updates** - See your new skin immediately, no rejoin required
- **Command-based**: `/skin set <username>` to use any Minecraft account's skin
- **Persistent storage** - Skin preferences survive server restarts
- **GTNH compatible** - Tested with GregTech: New Horizons 2.8.4

## How It Works

When a player sets their skin, the server:
1. Fetches the skin texture data from Mojang's API
2. Stores the preference in a JSON file
3. Injects the skin data into player packets sent to other clients
4. Vanilla clients automatically display the correct skin

## Commands

| Command | Description |
|---------|-------------|
| `/skin set <username>` | Set your skin to any Minecraft account's skin |
| `/skin clear` | Remove your custom skin |
| `/skin reload` | Refresh your skin from Mojang |
| `/skin <player> set <username>` | (Admin) Set another player's skin |

### Examples

```
/skin set Notch          - Use Notch's skin
/skin set jeb_           - Use Jeb's skin
/skin clear              - Reset to default
```

## Installation

### Requirements
- Minecraft 1.7.10
- Minecraft Forge 10.13.4.1614 or compatible
- Java 8+ (Java 17+ recommended for GTNH 2.8.4)

### Server Installation
1. Download the latest `PierceSkinRestorer-x.x.x.jar` from [Releases](https://github.com/williampierce-hue/pierceskinrestorer/releases/latest)
2. Place in your server's `mods/` folder
3. Restart the server
4. Done! Players can now use `/skin` command

**Players do NOT need to install anything** - their vanilla clients will see skins automatically.

## Building from Source

```bash
# Clone the repository
git clone https://github.com/williampierce-hue/pierceskinrestorer.git
cd pierceskinrestorer

# Set up ForgeGradle (first time only)
./gradlew setupDecompWorkspace

# Build the mod
./gradlew build
```

The JAR will be at `build/libs/PierceSkinRestorer-x.x.x.jar`

## Configuration

Config file: `config/pierceskinrestorer.cfg`

```properties
# Timeout for fetching skins from Mojang API (seconds)
I:fetchTimeoutSeconds=10

# Require OP permission to use /skin command
B:requirePermission=false

# Enable debug logging
B:logDebug=false
```

## Data Storage

Skin preferences are stored in `skinrestorer/skins.json`:

```json
{
  "player-uuid-here": {
    "playerName": "Steve",
    "skinSource": "Notch",
    "skinType": "MOJANG_USERNAME",
    "lastUpdated": 1705766400000
  }
}
```

## GTNH Compatibility

This mod is designed to be fully compatible with GTNH 2.8.4:

- **No core mod** - Uses standard Forge events only
- **No client mod** - Server-side packet manipulation
- **No conflicts** - Doesn't touch rendering code
- **Lightweight** - Minimal memory footprint

## Limitations

- **Mojang usernames only** - The username must exist on Mojang's servers
- **No custom URL skins** - Vanilla clients only download from Mojang CDN

## Troubleshooting

### "Failed to fetch skin" error
- The username doesn't exist or has no skin set
- Mojang API may be temporarily unavailable
- Check server logs for details

### Skin not showing for other players
- Wait for the player to respawn or rejoin
- The server may still be fetching the skin data
- Try `/skin reload`

### API Rate Limits
Mojang's API has rate limits (~200 requests/minute). If you have many players setting skins simultaneously, some requests may fail temporarily.

## Technical Details

The mod works by:
1. Intercepting `S0CPacketSpawnPlayer` packets before they're sent
2. Modifying the `GameProfile` to include skin texture properties
3. The vanilla client receives the modified profile and downloads the skin

This approach requires no client-side code because the vanilla Minecraft client already knows how to:
- Read texture properties from GameProfiles
- Download skin textures from Mojang's CDN
- Apply skins to player models

## License

MIT License - Feel free to use, modify, and distribute.

## Credits

- Mojang for the skin/profile APIs
- GTNH team for the amazing modpack
