/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package net.luckperms.api;

import net.luckperms.api.util.Tristate;

public interface PermissionData {
    Tristate checkPermission(String node);
}
