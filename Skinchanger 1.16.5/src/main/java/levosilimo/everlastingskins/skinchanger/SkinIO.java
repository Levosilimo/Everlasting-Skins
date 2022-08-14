package levosilimo.everlastingskins.skinchanger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.util.FileUtils;
import levosilimo.everlastingskins.util.JsonUtils;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
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
        String msg="";
        if(FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile())!=null){
            Object obj = parser.parse(FileUtils.readFile(savePath.resolve(uuid + FILE_EXTENSION).toFile()));
            JsonObject jsonObject = (JsonObject)obj;
            String source = jsonObject.get("source").getAsString();
            StringTextComponent msgText = new StringTextComponent("§6[EverlastingSkins]§f "+source);
            if(source.indexOf('/')>-1)msgText.setStyle(msgText.getStyle().setItalic(true).setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, source)));
            SkinRestorer.server.getPlayerList().getPlayerByUUID(uuid).sendMessage(msgText, MathHelper.getRandomUUID());
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
        /*byte[] decodedBytes = Base64.getDecoder().decode(skin.getValue());
        String decodedValueSTR = new String(decodedBytes);
        JsonObject decodedValueJSON = (JsonObject) parser.parse(decodedValueSTR);
        JsonObject TexturesGlobalJson = decodedValueJSON.getAsJsonObject("textures");
        decodedValueJSON.remove("textures");
        if(TexturesGlobalJson.size()>1) TexturesGlobalJson.remove("CAPE");
        JsonObject CAPE = new JsonObject();
        CAPE.add("url",new JsonPrimitive("http://176.113.169.30/cape" + MathHelper.nextInt(new Random(),1,28)));
        TexturesGlobalJson.add("CAPE",CAPE);
        decodedValueJSON.add("textures",TexturesGlobalJson);
        decodedValueJSON.addProperty("signatureRequired",false);
        String toEncode = decodedValueJSON.toString();
        EverlastingSkins.logger.warn(toEncode);
        Property skina = new Property("textures",Base64.getEncoder().encodeToString(toEncode.getBytes()), skin.getSignature());*/

        Object obj = parser.parse(JsonUtils.toJson(skin));
        JsonObject jsonObject = (JsonObject)obj;
        JsonPrimitive source_json = new JsonPrimitive(source);
        jsonObject.add("source",source_json);
        FileUtils.writeFile(savePath.toFile(), uuid + FILE_EXTENSION, jsonObject.toString());
        //FileUtils.writeFile(savePath.toFile(), uuid + FILE_EXTENSION, JsonUtils.toJson(skin).replace("\"\n}",("\", \"source\": \""+source+"\"}")));
    }
    public void saveSkin(UUID uuid, Property skin) {
        Object obj = parser.parse(JsonUtils.toJson(skin));
        JsonObject jsonObject = (JsonObject)obj;
        JsonElement source_json = parser.parse("——");
        jsonObject.add("source",source_json);
        FileUtils.writeFile(savePath.toFile(), uuid + FILE_EXTENSION, jsonObject.toString());
        //FileUtils.writeFile(savePath.toFile(), uuid + FILE_EXTENSION, JsonUtils.toJson(skin).replace("\"\n}",("\", \"source\": \""+SkinRestorer.server.getPlayerList().getPlayerByUUID(uuid).getGameProfile().getName()+"\"}")));
    }
}
