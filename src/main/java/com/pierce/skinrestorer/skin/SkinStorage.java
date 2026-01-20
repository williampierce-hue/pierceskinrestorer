package com.pierce.skinrestorer.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pierce.skinrestorer.PierceSkinRestorer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side storage for player skin preferences.
 * Persists to JSON file.
 */
public class SkinStorage {

    private static File dataDir;
    private static File skinsFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Map of player UUID -> SkinData
    private static Map<String, SkinData> skinMap = new ConcurrentHashMap<String, SkinData>();

    public static void init(File dir) {
        dataDir = dir;
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        skinsFile = new File(dataDir, "skins.json");
        load();
    }

    public static void load() {
        if (skinsFile == null || !skinsFile.exists()) {
            skinMap = new ConcurrentHashMap<String, SkinData>();
            return;
        }

        FileReader reader = null;
        try {
            reader = new FileReader(skinsFile);
            Type type = new TypeToken<Map<String, SkinData>>() {}.getType();
            Map<String, SkinData> loaded = GSON.fromJson(reader, type);

            if (loaded != null) {
                skinMap = new ConcurrentHashMap<String, SkinData>(loaded);
                PierceSkinRestorer.LOGGER.info("Loaded " + skinMap.size() + " skin entries");
            } else {
                skinMap = new ConcurrentHashMap<String, SkinData>();
            }
        } catch (Exception e) {
            PierceSkinRestorer.LOGGER.error("Failed to load skins.json", e);
            skinMap = new ConcurrentHashMap<String, SkinData>();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void save() {
        if (skinsFile == null) {
            return;
        }

        FileWriter writer = null;
        try {
            writer = new FileWriter(skinsFile);
            GSON.toJson(skinMap, writer);
        } catch (Exception e) {
            PierceSkinRestorer.LOGGER.error("Failed to save skins.json", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void setSkin(String playerUUID, String playerName, String skinSource, SkinType type) {
        SkinData data = new SkinData();
        data.playerName = playerName;
        data.skinSource = skinSource;
        data.skinType = type;
        data.lastUpdated = System.currentTimeMillis();

        skinMap.put(playerUUID, data);
        save();

        PierceSkinRestorer.LOGGER.info("Saved skin for " + playerName + " (" + playerUUID + "): " + skinSource);
    }

    public static SkinData getSkin(String playerUUID) {
        return skinMap.get(playerUUID);
    }

    public static void removeSkin(String playerUUID) {
        skinMap.remove(playerUUID);
        save();
    }

    public static boolean hasSkin(String playerUUID) {
        return skinMap.containsKey(playerUUID);
    }

    public static Map<String, SkinData> getAllSkins() {
        return new HashMap<String, SkinData>(skinMap);
    }

    public static File getDataDir() {
        return dataDir;
    }

    public static class SkinData {
        public String playerName;
        public String skinSource;  // Username
        public SkinType skinType;
        public long lastUpdated;
    }

    public enum SkinType {
        MOJANG_USERNAME
    }
}
