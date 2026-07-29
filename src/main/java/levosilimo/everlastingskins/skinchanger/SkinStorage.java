package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkinStorage {

    private final CustomSkinProperty DEFAULT_SKIN;
    private static final ConcurrentHashMap<UUID, CustomSkinProperty> skinMap = new ConcurrentHashMap<>();
    private final SkinIO skinIO;

    public SkinStorage(SkinIO skinIO) {
        this.skinIO = skinIO;
        this.DEFAULT_SKIN = loadDefaultSkin();
    }

    private static CustomSkinProperty loadDefaultSkin() {
        Properties props = new Properties();
        try (InputStream is = SkinStorage.class.getResourceAsStream("/everlastingskins/default-skin.properties")) {
            if (is == null) {
                throw new RuntimeException("Default skin resource not found: /everlastingskins/default-skin.properties");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default skin resource", e);
        }
        String value = props.getProperty("skin.value");
        String signature = props.getProperty("skin.signature");
        if (value == null || signature == null) {
            throw new RuntimeException("Default skin resource missing required properties");
        }
        return new CustomSkinProperty("textures", value, signature, null);
    }

    // Access via SkinRestorer.getSkinStorage().
    public CustomSkinProperty getSkin(ServerPlayer player) {
        CustomSkinProperty skinProperty = skinMap.get(player.getUUID());
        if (skinProperty != null) return skinProperty;

        skinProperty = skinIO.loadSkin(player.getUUID());
        skinProperty = setSkin(player, skinProperty);
        return skinProperty;
    }
    @Nullable
    public String getSource(UUID uuid) {
        CustomSkinProperty skin = skinMap.get(uuid);
        return skin != null ? skin.getSource() : skinIO.getSourceFromFileStorage(uuid);
    }

    public void saveSkin(ServerPlayer player) {
        CustomSkinProperty skinProperty = skinMap.get(player.getUUID());
        if (skinProperty != null) {
            skinIO.saveSkin(player.getUUID(), skinProperty);
        }
    }

    public CustomSkinProperty setSkin(ServerPlayer player, @Nullable CustomSkinProperty skin) {
        if (skin == null) skin = DEFAULT_SKIN;
        skinMap.put(player.getUUID(), skin);
        return skin;
    }

    public boolean hasDefaultSkin(ServerPlayer player) {
        return DEFAULT_SKIN.equals(getSkin(player));
    }
}
