package net.luckperms.api;

import net.luckperms.api.cacheddata.CachedPermissionData;

public interface User {
    CachedPermissionData getCachedData();
}
