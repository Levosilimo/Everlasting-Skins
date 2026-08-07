/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import java.util.UUID;

/**
 * Skin storage binding against the 1.7.10 GameProfile surface.
 *
 * <p>1.7.10's {@link EntityPlayer#getGameProfile()} returns a
 * {@link com.mojang.authlib.GameProfile} whose {@code textures} property
 * carries the skin payload exactly like 1.8+ — the difference is only the
 * accessor: 1.7.10 has {@code getGameProfile()} and no
 * {@code getPersistentID()} (that is 1.8+). The UUID is always taken from
 * {@code gameProfile.getId()} at the binding boundary.
 *
 * <p>Skin storage is keyed by UUID ONLY (memory #1123): this provider never
 * accepts an {@link EntityPlayer} — callers extract the UUID first and hand
 * it (plus the profile, for property mutation) in separately. :common sees
 * only UUIDs and {@link CustomSkinProperty} values.
 */
public final class SkinStorageProvider {

    private final SkinStorage storage;

    public SkinStorageProvider(SkinStorage storage) {
        this.storage = storage;
    }

    /** @return the stored skin for the UUID, or null when unset/empty. */
    public CustomSkinProperty getSkin(UUID uuid) {
        CustomSkinProperty skin = storage.getSkin(uuid);
        return (skin == null || skin.isEmpty()) ? null : skin;
    }

    /**
     * Stores the skin and applies it to the profile's {@code textures}
     * property so the client renders it.
     */
    public void applySkin(GameProfile profile, UUID uuid, CustomSkinProperty skin) {
        if (skin == null || skin.isEmpty()) {
            clearSkin(profile, uuid);
            return;
        }
        storage.setSkin(uuid, skin);
        Property original = skin.getOriginalProperty();
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", original);
    }

    /** Removes the stored skin and strips the {@code textures} property. */
    public void clearSkin(GameProfile profile, UUID uuid) {
        storage.removeSkin(uuid);
        profile.getProperties().removeAll("textures");
    }

    /** @return the stored skin's source label (null when unset). */
    public String getSource(UUID uuid) {
        CustomSkinProperty skin = storage.getSkin(uuid);
        return (skin == null || skin.isEmpty()) ? null : skin.getSource();
    }

    public SkinStorage raw() {
        return storage;
    }
}
