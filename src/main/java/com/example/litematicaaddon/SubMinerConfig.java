package com.example.litematicaaddon;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;

/** Настройки бота. Файл: config/litematica-subminer.properties */
public final class SubMinerConfig {

    public static final int EVERY_MIN = 3;
    public static final int EVERY_MAX = 16;

    public static int x1;
    public static int y1;
    public static int z1;
    public static int x2;
    public static int y2;
    public static int z2;
    /** Слот кирки в хотбаре: 1..9, 0 = искать кирку автоматически. */
    public static int pickaxeSlot = 1;
    /** Зона задана (меню хотя бы раз сохранено с координатами). */
    public static boolean zoneSet;
    /** Слот блока, который бот ставит (факелы и т.п.): 1..9, 0 = выключено. */
    public static int placeSlot;
    /** Через сколько блоков пути ставить блок из placeSlot (3..16). */
    public static int placeEvery = 8;
    /** ID блоков, которые бот не ломает. */
    public static final List<String> ignored = new ArrayList<>(List.of(
            "minecraft:torch", "minecraft:wall_torch",
            "minecraft:soul_torch", "minecraft:soul_wall_torch"));

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
        placeSlot = Math.max(0, Math.min(9, readInt(props, "placeSlot", 0)));
        placeEvery = Math.max(EVERY_MIN, Math.min(EVERY_MAX, readInt(props, "placeEvery", 8)));
        if (props.containsKey("ignored")) {
            List<String> loaded = new ArrayList<>();
            for (String part : props.getProperty("ignored", "").split(",")) {
                loaded.add(part);
            }
            ignored.clear();
            ignored.addAll(cleanedFrom(loaded));
        }
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
        props.setProperty("placeSlot", Integer.toString(placeSlot));
        props.setProperty("placeEvery", Integer.toString(placeEvery));
        props.setProperty("ignored", String.join(",", cleanedFrom(ignored)));
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                props.store(writer, "SubMiner settings");
            }
        } catch (IOException ignoredException) {
            // не критично: настройки просто не сохранятся на диск
        }
    }

    /** "torch" -> "minecraft:torch"; пустое -> null. */
    public static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return null;
        }
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** Нормализует список ID: без пустых и повторов. */
    public static List<String> cleanedFrom(List<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : raw) {
            String id = normalizeId(s);
            if (id != null) {
                out.add(id);
            }
        }
        return new ArrayList<>(out);
    }

    private static int readInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
