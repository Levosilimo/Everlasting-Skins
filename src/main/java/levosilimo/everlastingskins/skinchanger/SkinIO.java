package levosilimo.everlastingskins.skinchanger;

import com.google.gson.*;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.FileUtils;
import levosilimo.everlastingskins.util.JsonUtils;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.UUID;

public class SkinIO {

    private static final String FILE_EXTENSION = ".json";

    private final Path savePath;
    public SkinIO(Path savePath) {
        this.savePath = savePath;
    }
    @Nullable
    public String getSourceFromFileStorage(UUID uuid){
        String skinJson = FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile());
        if(skinJson!=null){
            JsonObject skinJsonObject = JsonUtils.parseJson(skinJson);
            return skinJsonObject.get("source").getAsString();
        }
        return null;
    }

    public CustomSkinProperty loadSkin(UUID uuid) {
        String skinJson = FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile());
        return JsonUtils.fromJson(skinJson, CustomSkinProperty.class);
    }
    public void saveSkin(UUID uuid, CustomSkinProperty skin) {
        FileUtils.writeFile(savePath.toFile(), uuid + FILE_EXTENSION, JsonUtils.toJson(skin));
    }
}
