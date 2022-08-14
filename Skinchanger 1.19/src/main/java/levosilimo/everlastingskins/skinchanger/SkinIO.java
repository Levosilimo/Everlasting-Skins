package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.util.FileUtils;
import levosilimo.everlastingskins.util.JsonUtils;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.LiteralContents;

import java.nio.file.Path;
import java.util.UUID;

public class SkinIO {

    private static final String FILE_EXTENSION = ".json";

    private final Path savePath;
    public SkinIO(Path savePath) {
        this.savePath = savePath;
    }
    public int getSource(UUID uuid){
        String msg="";
        if(FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile())!=null){
            Object obj = JsonParser.parseString(FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile()));
            JsonObject jsonObject = (JsonObject)obj;
            String source = jsonObject.get("source").getAsString();
            MutableComponent msgText = MutableComponent.create(new LiteralContents("§6[EverlastingSkins]§f "+source));
            if(source.indexOf('/')>-1)msgText.setStyle(msgText.getStyle().withItalic(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, source)));
            SkinRestorer.server.getPlayerList().getPlayer(uuid).sendSystemMessage(msgText);
        }
        return 1;
    }

    public Property loadSkin(UUID uuid) {
        String file = FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile());
        if(file!=null){
            JsonObject jsonObject = JsonParser.parseString(file).getAsJsonObject();
            jsonObject.remove("source");
            file = jsonObject.toString();
        }
        return JsonUtils.fromJson(file, Property.class);
    }

    public void saveSkin(UUID uuid, Property skin, String source) {
        Object obj = JsonParser.parseString(JsonUtils.toJson(skin));
        JsonObject jsonObject = (JsonObject)obj;
        JsonPrimitive source_json = new JsonPrimitive(source);
        jsonObject.add("source",source_json);
        FileUtils.writeFile(savePath.toFile(), uuid + FILE_EXTENSION, jsonObject.toString());
    }
    public void saveSkin(UUID uuid, Property skin) {
        Object obj = JsonParser.parseString(JsonUtils.toJson(skin));
        JsonObject jsonObject = (JsonObject)obj;
        JsonElement source_json = JsonParser.parseString("——");
        jsonObject.add("source",source_json);
        FileUtils.writeFile(savePath.toFile(), uuid + FILE_EXTENSION, jsonObject.toString());
    }
}
