package levosilimo.everlastingskins.permission;

import net.minecraft.entity.player.EntityPlayerMP;

public interface IPermissionService {

    boolean hasPermission(EntityPlayerMP player, String permissionNode);

    String getActiveBackendName();

    int getPriority();
}
