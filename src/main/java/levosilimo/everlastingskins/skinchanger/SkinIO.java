package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.util.FileUtils;
import levosilimo.everlastingskins.util.JsonUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;

import java.nio.file.Path;
import java.util.UUID;

public class SkinIO {

    private static final String FILE_EXTENSION = ".json";

    private final Path savePath;
    private final JsonParser parser = new JsonParser();
    public SkinIO(Path savePath) {
        this.savePath = savePath;
    }
    public int getSource(UUID uuid){
        if(FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile())!=null){
            Object obj = parser.parse(FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile()));
            JsonObject jsonObject = (JsonObject)obj;
            String source = jsonObject.get("source").getAsString();
            ITextComponent msgText = new TextComponentString("§6[EverlastingSkins]§f "+source);
            if(source.indexOf('/')>-1)msgText.setStyle(msgText.getStyle().setItalic(true).setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, source)));
            EntityPlayerMP player = SkinRestorer.server.getPlayerList().getPlayerByUUID(uuid);
            if(player != null) player.sendMessage(msgText, ChatType.SYSTEM);
        }
        return 1;
    }

    public Property loadSkin(UUID uuid) {
        String file = FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile());
        if(file!=null){
            JsonObject jsonObject = parser.parse(file).getAsJsonObject();
            jsonObject.remove("source");
            file = jsonObject.toString();
        }
        return JsonUtils.fromJson(file, Property.class);
    }

    public void saveSkin(UUID uuid, Property skin, String source) {
        Object obj = parser.parse(JsonUtils.toJson(skin));
        JsonObject jsonObject = (JsonObject)obj;
        JsonPrimitive source_json = new JsonPrimitive(source);
        jsonObject.add("source",source_json);
        FileUtils.writeFile(savePath.toFile(), uuid + FILE_EXTENSION, jsonObject.toString());
    }
    public void saveSkin(UUID uuid, Property skin) {
        Object obj = parser.parse(JsonUtils.toJson(skin));
        JsonObject jsonObject = (JsonObject)obj;
        JsonElement source_json = parser.parse("——");
        jsonObject.add("source",source_json);
        FileUtils.writeFile(savePath.toFile(), uuid + FILE_EXTENSION, jsonObject.toString());
    }
}