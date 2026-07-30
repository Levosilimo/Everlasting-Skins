package levosilimo.everlastingskins.permission;

import net.minecraft.entity.player.EntityPlayerMP;

public class VanillaPermissionService implements IPermissionService {

    private static final int REQUIRED_OP_LEVEL = 2;

    @Override
    public boolean hasPermission(EntityPlayerMP player, String permissionNode) {
        return player.canUseCommand(REQUIRED_OP_LEVEL, "everlastingskins");
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
