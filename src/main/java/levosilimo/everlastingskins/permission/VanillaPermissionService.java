package levosilimo.everlastingskins.permission;

import net.minecraft.server.level.ServerPlayer;

public class VanillaPermissionService implements IPermissionService {

    private static final int REQUIRED_OP_LEVEL = 2;

    @Override
    public boolean hasPermission(ServerPlayer player, String permissionNode) {
        return player.hasPermissions(REQUIRED_OP_LEVEL);
    }

    @Override
    public String getActiveBackendName() {
        return "Vanilla (op level " + REQUIRED_OP_LEVEL + ")";
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
