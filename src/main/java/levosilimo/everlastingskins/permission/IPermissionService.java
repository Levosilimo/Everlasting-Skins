package levosilimo.everlastingskins.permission;

import net.minecraft.server.level.ServerPlayer;

public interface IPermissionService {

    boolean hasPermission(ServerPlayer player, String permissionNode);

    String getActiveBackendName();

    int getPriority();
}
