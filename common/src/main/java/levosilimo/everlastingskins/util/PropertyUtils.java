/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import com.google.gson.Gson;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.responses.profile.DecodedTextureProperty;
import levosilimo.everlastingskins.skinchanger.responses.profile.MojangProfileTextureMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for retrieving information from profile properties related to skins.
 */
public class PropertyUtils {
    private static final Gson GSON = new Gson();

    /**
     * Returns a <a href="https://textures.minecraft.net/id">Texture Url</a> based on skin
     * This is useful for skull plugins like Dynmap or DiscordSRV
     * for example <a href="https://mc-heads.net/avatar/cb50beab76e56472637c304a54b330780e278decb017707bf7604e484e4d6c9f/100.png">https://mc-heads.net/avatar/%texture_id%/%size%.png</a>
     *
     * @param property Profile property
     * @return full textures.minecraft.net url
     */
    public static String getSkinTextureUrl(CustomSkinProperty property) {
        return getSkinProfileData(property).textures().SKIN().url();
    }

    public static SkinVariant getSkinVariant(CustomSkinProperty property) {
        MojangProfileTextureMeta meta = getSkinProfileData(property).textures().SKIN().metadata();
        if (meta == null) {
            return SkinVariant.CLASSIC;
        }

        return meta.model().equalsIgnoreCase("slim") ? SkinVariant.SLIM : SkinVariant.CLASSIC;
    }

    /**
     * Only returns the id at the end of the url.
     * Example:
     * <a href="https://textures.minecraft.net/texture/cb50beab76e56472637c304a54b330780e278decb017707bf7604e484e4d6c9f">
     * https://textures.minecraft.net/texture/cb50beab76e56472637c304a54b330780e278decb017707bf7604e484e4d6c9f
     * </a>
     * Would return: cb50beab76e56472637c304a54b330780e278decb017707bf7604e484e4d6c9f
     *
     * @param property Profile property
     * @return textures.minecraft.net id
     * @see #getSkinTextureUrl(CustomSkinProperty)
     */
    public static String getSkinTextureUrlStripped(CustomSkinProperty property) {
        return getSkinProfileData(property).textures().SKIN().getStrippedUrl();
    }

    /**
     * Returns only the texture ID (hex hash) at the end of the texture URL.
     * Useful for skull plugins like Dynmap or DiscordSRV, for example:
     * mc-heads.net/avatar/%texture_id%/%size%.png
     *
     * @param property Profile property
     * @return textures.minecraft.net id
     * @see #getSkinTextureUrl(CustomSkinProperty)
     */
    public static String getTextureId(CustomSkinProperty property) {
        return getSkinTextureUrlStripped(property);
    }

    /**
     * Returns the decoded profile data from the profile property.
     * This is useful for getting the skin data from the property and other information like cape.
     * The user stored in this property may not be the same as the player who has the skin.
     * APIs like MineSkin use multiple shared accounts to generate these properties.
     * Or it could be the property of another player that the player set their skin to.
     *
     * @param property Profile property
     * @return Decoded profile data as java object
     */
    public static DecodedTextureProperty getSkinProfileData(CustomSkinProperty property) {
        String decodedString = new String(Base64.getDecoder().decode(property.getOriginalProperty().getValue()), StandardCharsets.UTF_8);

        return GSON.fromJson(decodedString, DecodedTextureProperty.class);
    }

    private PropertyUtils() {
    }
}
