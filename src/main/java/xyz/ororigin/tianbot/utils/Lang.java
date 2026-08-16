package xyz.ororigin.tianbot.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class Lang {

    public static final String FILE_NAME = "messages.yml";
    private static YamlConfiguration defaults;
    private static YamlConfiguration messages;

    private Lang() {
    }
    public static synchronized void init(JavaPlugin plugin) {
        plugin.saveResource(FILE_NAME, false);
        defaults = loadFromJar(plugin);
        messages = loadFromDisk(new File(plugin.getDataFolder(), FILE_NAME));
    }

    public static synchronized void reload(JavaPlugin plugin) {
        defaults = loadFromJar(plugin);
        messages = loadFromDisk(new File(plugin.getDataFolder(), FILE_NAME));
    }

    public static String get(String key) {
        String template = resolve(key);
        return template != null ? template : key;
    }

    public static String t(String key, Object... keyValues) {
        String template = resolve(key);
        if (template == null) {
            return key;
        }
        if (keyValues == null || keyValues.length == 0) {
            return template;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String name = String.valueOf(keyValues[i]);
            String value = String.valueOf(keyValues[i + 1]);
            template = template.replace("{" + name + "}", value);
        }
        return template;
    }

    private static String resolve(String key) {
        if (messages != null) {
            String value = messages.getString(key);
            if (value != null) {
                return value;
            }
        }
        if (defaults != null) {
            String value = defaults.getString(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static YamlConfiguration loadFromJar(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource(FILE_NAME)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static YamlConfiguration loadFromDisk(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
