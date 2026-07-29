package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.JsonUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

public class SkinIO {

    private static final String FILE_EXTENSION = ".json";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String CORRUPT_PREFIX = ".corrupt-";

    private final Path savePath;

    public SkinIO(Path savePath) {
        this.savePath = savePath;
    }

    @Nullable
    public String getSourceFromFileStorage(UUID uuid) {
        String skinJson = readSkinFile(uuid);
        if (skinJson == null) return null;
        try {
            JsonObject obj = new JsonParser().parse(skinJson).getAsJsonObject();
            if (obj.has("source") && !obj.get("source").isJsonNull()) {
                return obj.get("source").getAsString();
            }
            return null;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    @Nullable
    public CustomSkinProperty loadSkin(UUID uuid) {
        String skinJson = readSkinFile(uuid);
        if (skinJson == null) return null;
        try {
            CustomSkinProperty skin = JsonUtils.fromJson(skinJson, CustomSkinProperty.class);
            if (skin == null || skin.getOriginalProperty() == null || skin.getOriginalProperty().getValue() == null || skin.getOriginalProperty().getValue().isEmpty()) {
                return null;
            }
            return skin;
        } catch (JsonParseException e) {
            return null;
        }
    }

    public void saveSkin(UUID uuid, CustomSkinProperty skin) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        Path temp = savePath.resolve(uuid + FILE_EXTENSION + TEMP_SUFFIX);
        String json = JsonUtils.toJson(skin);

        try {
            Files.createDirectories(savePath);
            Files.deleteIfExists(temp);

            Files.write(temp, json.getBytes(StandardCharsets.UTF_8));

            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Nullable
    private String readSkinFile(UUID uuid) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        if (!Files.exists(target)) return null;
        try {
            byte[] bytes = Files.readAllBytes(target);
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (!isValidJson(content)) {
                quarantineFile(uuid);
                return null;
            }
            return content;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isValidJson(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        try {
            JsonElement element = new JsonParser().parse(content);
            return element.isJsonObject() || element.isJsonArray();
        } catch (JsonParseException e) {
            return false;
        }
    }

    private void quarantineFile(UUID uuid) {
        Path target = savePath.resolve(uuid + FILE_EXTENSION);
        if (!Files.exists(target)) return;
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        Path quarantine = savePath.resolve(uuid + FILE_EXTENSION + CORRUPT_PREFIX + timestamp);
        try {
            Files.move(target, quarantine, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }
}
