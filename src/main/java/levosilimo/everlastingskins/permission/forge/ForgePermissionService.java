package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

public class ForgePermissionService implements IPermissionService {

    public static final PermissionNode<Boolean> SKIN_NODE =
        new PermissionNode<>("everlastingskins", "command.skin",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> true);

    public static final PermissionNode<Boolean> SKIN_OTHER_NODE =
        new PermissionNode<>("everlastingskins", "command.skin.other",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(2));

    public static final PermissionNode<Boolean> SKIN_URL_NODE =
        new PermissionNode<>("everlastingskins", "command.skin.url",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> true);

    public static final PermissionNode<Boolean> SKIN_CLEAR_NODE =
        new PermissionNode<>("everlastingskins", "command.skin.clear",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> true);

    public static void registerNodes() {
        PermissionServiceManager.registerService(new ForgePermissionService());
    }

    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(SKIN_NODE, SKIN_OTHER_NODE, SKIN_URL_NODE, SKIN_CLEAR_NODE);
    }

    @Override
    public boolean hasPermission(ServerPlayer player, String permissionNode) {
        PermissionNode<Boolean> node;
        if (permissionNode.endsWith(".skin.other")) {
            node = SKIN_OTHER_NODE;
        } else if (permissionNode.endsWith(".skin.url")) {
            node = SKIN_URL_NODE;
        } else if (permissionNode.endsWith(".skin.clear")) {
            node = SKIN_CLEAR_NODE;
        } else if (permissionNode.endsWith(".skin.source")) {
            return true;
        } else {
            node = SKIN_NODE;
        }
        try {
            return PermissionAPI.getPermission(player, node);
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public String getActiveBackendName() {
        return "Forge PermissionAPI (1.21)";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
