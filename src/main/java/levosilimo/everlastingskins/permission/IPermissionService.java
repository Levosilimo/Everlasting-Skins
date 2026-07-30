package levosilimo.everlastingskins.permission;

public interface IPermissionService {

    boolean hasPermission(PermissionContext context, String permissionNode);

    String getActiveBackendName();

    int getPriority();
}
