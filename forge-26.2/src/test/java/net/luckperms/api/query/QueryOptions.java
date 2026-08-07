/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package net.luckperms.api.query;

public interface QueryOptions {
    static QueryOptions defaultContextualOptions() {
        return new DefaultQueryOptions();
    }
}
