package net.luckperms.api;

import net.luckperms.api.util.Tristate;

public interface PermissionData {
    Tristate checkPermission(String node);
}
