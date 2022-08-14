package levosilimo.everlastingskins.skinchanger;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import levosilimo.everlastingskins.EverlastingSkins;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;

public class SkinRestorer {

    private static SkinStorage skinStorage;
    private static SkinIO skinIO;
    public static SkinIO getSkinIO(){return skinIO;}
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }
    public static MinecraftServer server;
    @SubscribeEvent
    public void onInitializeServer(FMLServerStartingEvent event) {
        server = event.getServer();
        skinIO=new SkinIO(event.getServer().func_240776_a_(new FolderName("EverlastingSkins")));
        skinStorage = new SkinStorage(skinIO);
    }
}
