package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

public class ForgePermissionService implements IPermissionService {

    private static final String NODE_PREFIX = "everlastingskins.command";
    private static final String SKIN_NODE = NODE_PREFIX + ".skin";
    private static final String SKIN_OTHER_NODE = NODE_PREFIX + ".skin.other";
    private static final String SKIN_URL_NODE = NODE_PREFIX + ".skin.url";
    private static final String SKIN_CLEAR_NODE = NODE_PREFIX + ".skin.clear";
    private static boolean registered = false;

    /** Must run during INIT — Forge's PermissionAPI rejects node registration before FMLInitializationEvent. */
    public static void registerNodes(FMLInitializationEvent event) {
        if (registered) return;
        PermissionAPI.registerNode(SKIN_NODE, DefaultPermissionLevel.ALL, "Change own skin");
        PermissionAPI.registerNode(SKIN_OTHER_NODE, DefaultPermissionLevel.OP, "Change another player's skin");
        PermissionAPI.registerNode(SKIN_URL_NODE, DefaultPermissionLevel.ALL, "Set own skin from URL");
        PermissionAPI.registerNode(SKIN_CLEAR_NODE, DefaultPermissionLevel.ALL, "Clear own skin");
        registered = true;
        PermissionServiceManager.registerService(new ForgePermissionService());
    }

    @Override
    public boolean hasPermission(PermissionContext context, String permissionNode) {
        if (permissionNode.endsWith(".source")) return true;
        return context.isOp();
    }

    @Override
    public String getActiveBackendName() {
        return "Forge PermissionAPI (1.12)";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
