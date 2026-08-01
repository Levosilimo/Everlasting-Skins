package levosilimo.everlastingskins.harness;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builds a real EntityPlayerMP (public constructor) with a mocked connection,
 * so commands run through the real CommandHandler and packets are captured on
 * player.connection.sendPacket.
 */
public final class TestPlayerFactory {

    private TestPlayerFactory() {
    }

    public static EntityPlayerMP create(MinecraftServer server, WorldServer world, String name) {
        UUID uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, name);
        PlayerInteractionManager interactionManager = mock(PlayerInteractionManager.class);
        when(interactionManager.getGameType()).thenReturn(GameType.SURVIVAL);
        EntityPlayerMP player = new EntityPlayerMP(server, world, profile, interactionManager);
        player.connection = mock(NetHandlerPlayServer.class);
        return player;
    }
}
