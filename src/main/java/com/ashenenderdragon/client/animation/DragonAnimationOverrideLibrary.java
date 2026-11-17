package com.ashenenderdragon.client.animation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.ashenenderdragon.AshenEnderDragon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/** Client-local keyframe overrides edited by {@code DragonAnimationDebugScreen}. */
public final class DragonAnimationOverrideLibrary {
    private static final int DATA_VERSION = 2;
    private static final String DEFAULTS_RESOURCE = "/assets/ashenenderdragon/animations/dragon_animation_defaults.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, EnumMap<DragonModelPart, NavigableMap<Integer, DragonPoseTransform>>> ACTIONS = new HashMap<>();
    private static final Map<String, NavigableSet<Integer>> IDLE_POSE_FRAMES = new HashMap<>();

    private DragonAnimationOverrideLibrary() {
    }

    public static DragonPoseTransform sample(String action, DragonModelPart part, float ticks) {
        NavigableMap<Integer, DragonPoseTransform> keyframes = keyframes(action, part, false);
        if (keyframes == null || keyframes.isEmpty()) {
            return DragonPoseTransform.ZERO;
        }

        Map.Entry<Integer, DragonPoseTransform> before = keyframes.floorEntry((int)Math.floor(ticks));
        Map.Entry<Integer, DragonPoseTransform> after = keyframes.ceilingEntry((int)Math.ceil(ticks));
        if (before == null) {
            after = keyframes.firstEntry();
            if (after.getKey() <= 0) {
                return after.getValue();
            }
            float progress = smooth(clamp(ticks / after.getKey()));
            return DragonPoseTransform.lerp(progress, DragonPoseTransform.ZERO, after.getValue());
        }
        if (after == null || before.getKey().equals(after.getKey())) {
            return before.getValue();
        }

        float progress = smooth(clamp((ticks - before.getKey()) / (after.getKey() - before.getKey())));
        return DragonPoseTransform.lerp(progress, before.getValue(), after.getValue());
    }

    public static void setKeyframe(String action, DragonModelPart part, int tick, DragonPoseTransform transform) {
        keyframes(action, part, true).put(Math.max(0, tick), transform);
    }

    public static boolean removeKeyframe(String action, DragonModelPart part, int tick) {
        NavigableMap<Integer, DragonPoseTransform> keyframes = keyframes(action, part, false);
        if (keyframes == null) {
            return false;
        }
        return keyframes.remove(Math.max(0, tick)) != null;
    }

    public static void clearPart(String action, DragonModelPart part) {
        EnumMap<DragonModelPart, NavigableMap<Integer, DragonPoseTransform>> parts = ACTIONS.get(action);
        if (parts != null) {
            parts.remove(part);
            if (parts.isEmpty()) {
                ACTIONS.remove(action);
            }
        }
    }

    public static List<Integer> keyframeTicks(String action, DragonModelPart part) {
        NavigableMap<Integer, DragonPoseTransform> keyframes = keyframes(action, part, false);
        return keyframes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(keyframes.keySet()));
    }

    public static boolean isIdlePoseFrame(String action, float ticks) {
        NavigableSet<Integer> frames = IDLE_POSE_FRAMES.get(action);
        return frames != null && frames.contains((int)Math.floor(Math.max(0.0F, ticks)));
    }

    public static void markIdlePoseFrame(String action, int tick) {
        int safeTick = Math.max(0, tick);
        IDLE_POSE_FRAMES.computeIfAbsent(action, ignored -> new TreeSet<>()).add(safeTick);
        for (DragonModelPart part : DragonModelPart.values()) {
            setKeyframe(action, part, safeTick, DragonPoseTransform.ZERO);
        }
    }

    public static boolean unmarkIdlePoseFrame(String action, int tick) {
        NavigableSet<Integer> frames = IDLE_POSE_FRAMES.get(action);
        if (frames == null) {
            return false;
        }
        boolean removed = frames.remove(Math.max(0, tick));
        if (frames.isEmpty()) {
            IDLE_POSE_FRAMES.remove(action);
        }
        return removed;
    }

    public static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve(AshenEnderDragon.MODID).resolve("dragon_animation_overrides.json");
    }

    public static boolean save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", DATA_VERSION);
        JsonObject actions = new JsonObject();
        for (Map.Entry<String, EnumMap<DragonModelPart, NavigableMap<Integer, DragonPoseTransform>>> actionEntry : ACTIONS.entrySet()) {
            JsonObject parts = new JsonObject();
            for (Map.Entry<DragonModelPart, NavigableMap<Integer, DragonPoseTransform>> partEntry : actionEntry.getValue().entrySet()) {
                JsonArray frames = new JsonArray();
                for (Map.Entry<Integer, DragonPoseTransform> frameEntry : partEntry.getValue().entrySet()) {
                    DragonPoseTransform transform = frameEntry.getValue();
                    JsonObject frame = new JsonObject();
                    frame.addProperty("tick", frameEntry.getKey());
                    frame.addProperty("x", transform.x());
                    frame.addProperty("y", transform.y());
                    frame.addProperty("z", transform.z());
                    frame.addProperty("xRot", transform.xRot());
                    frame.addProperty("yRot", transform.yRot());
                    frame.addProperty("zRot", transform.zRot());
                    frames.add(frame);
                }
                parts.add(partEntry.getKey().name(), frames);
            }
            actions.add(actionEntry.getKey(), parts);
        }
        root.add("actions", actions);
        JsonObject idlePoseFrames = new JsonObject();
        for (Map.Entry<String, NavigableSet<Integer>> entry : IDLE_POSE_FRAMES.entrySet()) {
            JsonArray frames = new JsonArray();
            for (int tick : entry.getValue()) {
                frames.add(tick);
            }
            idlePoseFrames.add(entry.getKey(), frames);
        }
        root.add("idlePoseFrames", idlePoseFrames);

        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            AshenEnderDragon.LOGGER.error("Failed to save dragon animation overrides to {}", path, exception);
            return false;
        }
    }

    public static boolean load() {
        Path path = filePath();
        if (!Files.exists(path)) {
            try (InputStream stream = DragonAnimationOverrideLibrary.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
                if (stream == null) {
                    AshenEnderDragon.LOGGER.error("Missing bundled dragon animation defaults at {}", DEFAULTS_RESOURCE);
                    return false;
                }
                JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                replaceActions(readActions(root));
                replaceIdlePoseFrames(readIdlePoseFrames(root));
                return true;
            } catch (Exception exception) {
                AshenEnderDragon.LOGGER.error("Failed to load bundled dragon animation defaults from {}", DEFAULTS_RESOURCE, exception);
                return false;
            }
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            replaceActions(readActions(root));
            replaceIdlePoseFrames(readIdlePoseFrames(root));
            return true;
        } catch (Exception exception) {
            AshenEnderDragon.LOGGER.error("Failed to load dragon animation overrides from {}", path, exception);
            return false;
        }
    }

    private static Map<String, EnumMap<DragonModelPart, NavigableMap<Integer, DragonPoseTransform>>> readActions(JsonObject root) {
        Map<String, EnumMap<DragonModelPart, NavigableMap<Integer, DragonPoseTransform>>> loaded = new HashMap<>();
        JsonObject actions = root.has("actions") ? root.getAsJsonObject("actions") : new JsonObject();
        for (Map.Entry<String, JsonElement> actionEntry : actions.entrySet()) {
            EnumMap<DragonModelPart, NavigableMap<Integer, DragonPoseTransform>> parts = new EnumMap<>(DragonModelPart.class);
            for (Map.Entry<String, JsonElement> partEntry : actionEntry.getValue().getAsJsonObject().entrySet()) {
                DragonModelPart part = DragonModelPart.valueOf(partEntry.getKey());
                NavigableMap<Integer, DragonPoseTransform> frames = new TreeMap<>();
                for (JsonElement element : partEntry.getValue().getAsJsonArray()) {
                    JsonObject frame = element.getAsJsonObject();
                    frames.put(Math.max(0, frame.get("tick").getAsInt()), new DragonPoseTransform(
                            number(frame, "x"),
                            number(frame, "y"),
                            number(frame, "z"),
                            number(frame, "xRot"),
                            number(frame, "yRot"),
                            number(frame, "zRot")
                    ));
                }
                if (!frames.isEmpty()) {
                    parts.put(part, frames);
                }
            }
            if (!parts.isEmpty()) {
                loaded.put(actionEntry.getKey(), parts);
            }
        }
        return loaded;
    }

    private static void replaceActions(Map<String, EnumMap<DragonModelPart, NavigableMap<Integer, DragonPoseTransform>>> loaded) {
        ACTIONS.clear();
        ACTIONS.putAll(loaded);
    }

    private static Map<String, NavigableSet<Integer>> readIdlePoseFrames(JsonObject root) {
        Map<String, NavigableSet<Integer>> loaded = new HashMap<>();
        JsonObject actions = root.has("idlePoseFrames") ? root.getAsJsonObject("idlePoseFrames") : new JsonObject();
        for (Map.Entry<String, JsonElement> actionEntry : actions.entrySet()) {
            NavigableSet<Integer> frames = new TreeSet<>();
            for (JsonElement element : actionEntry.getValue().getAsJsonArray()) {
                frames.add(Math.max(0, element.getAsInt()));
            }
            if (!frames.isEmpty()) {
                loaded.put(actionEntry.getKey(), frames);
            }
        }
        return loaded;
    }

    private static void replaceIdlePoseFrames(Map<String, NavigableSet<Integer>> loaded) {
        IDLE_POSE_FRAMES.clear();
        IDLE_POSE_FRAMES.putAll(loaded);
    }

    private static float number(JsonObject object, String name) {
        return object.has(name) ? object.get(name).getAsFloat() : 0.0F;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static NavigableMap<Integer, DragonPoseTransform> keyframes(String action, DragonModelPart part, boolean create) {
        EnumMap<DragonModelPart, NavigableMap<Integer, DragonPoseTransform>> parts = create
                ? ACTIONS.computeIfAbsent(action, ignored -> new EnumMap<>(DragonModelPart.class))
                : ACTIONS.get(action);
        if (parts == null) {
            return null;
        }
        return create ? parts.computeIfAbsent(part, ignored -> new TreeMap<>()) : parts.get(part);
    }
}
