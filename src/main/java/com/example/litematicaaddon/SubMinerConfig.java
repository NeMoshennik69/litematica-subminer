package com.example.litematicaaddon;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Настройки бота: два угла зоны и слот кирки. Файл: config/litematica-subminer.properties */
public final class SubMinerConfig {

    public static int x1;
    public static int y1;
    public static int z1;
    public static int x2;
    public static int y2;
    public static int z2;
    /** Слот кирки в хотбаре: 1..9, 0 = искать кирку автоматически. */
    public static int pickaxeSlot = 1;
    /** Зона задана (меню хотя бы раз сохранено). */
    public static boolean zoneSet;

    private SubMinerConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("litematica-subminer.properties");
    }

    public static void load() {
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            props.load(reader);
        } catch (IOException e) {
            return;
        }
        x1 = readInt(props, "x1", 0);
        y1 = readInt(props, "y1", 0);
        z1 = readInt(props, "z1", 0);
        x2 = readInt(props, "x2", 0);
        y2 = readInt(props, "y2", 0);
        z2 = readInt(props, "z2", 0);
        pickaxeSlot = Math.max(0, Math.min(9, readInt(props, "pickaxeSlot", 1)));
        zoneSet = Boolean.parseBoolean(props.getProperty("zoneSet", "false"));
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("x1", Integer.toString(x1));
        props.setProperty("y1", Integer.toString(y1));
        props.setProperty("z1", Integer.toString(z1));
        props.setProperty("x2", Integer.toString(x2));
        props.setProperty("y2", Integer.toString(y2));
        props.setProperty("z2", Integer.toString(z2));
        props.setProperty("pickaxeSlot", Integer.toString(pickaxeSlot));
        props.setProperty("zoneSet", Boolean.toString(zoneSet));
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                props.store(writer, "SubMiner settings");
            }
        } catch (IOException ignored) {
            // не критично: настройки просто не сохранятся на диск
        }
    }

    private static int readInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
