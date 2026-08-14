/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Equality contract of {@link CustomSkinProperty}: equality covers the
 * textures payload (value + signature) as well as the source, so two skins
 * fetched from the same source with different values are NOT equal. The
 * jqwik properties exercise the equals/hashCode contract over generated
 * skins; the plain tests pin the regression that source-only equality
 * produced (same-source different-value skins compared equal, which also
 * misclassified custom null-source skins as the default skin).
 */
class CustomSkinPropertyEqualsTest {

    @Test
    @DisplayName("same source, different value: NOT equal (regression)")
    void sameSourceDifferentValue_notEqual() {
        CustomSkinProperty a = new CustomSkinProperty("textures", "valueA", "sig", "mojang");
        CustomSkinProperty b = new CustomSkinProperty("textures", "valueB", "sig", "mojang");

        assertNotEquals(a, b);
        assertNotEquals(b, a);
    }

    @Test
    @DisplayName("same source, different signature: NOT equal")
    void sameSourceDifferentSignature_notEqual() {
        CustomSkinProperty a = new CustomSkinProperty("textures", "value", "sigA", "mojang");
        CustomSkinProperty b = new CustomSkinProperty("textures", "value", "sigB", "mojang");

        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("same value + signature + source: equal with equal hash codes")
    void samePayloadAndSource_equal() {
        CustomSkinProperty a = new CustomSkinProperty("textures", "value", "sig", "mojang");
        CustomSkinProperty b = new CustomSkinProperty("textures", "value", "sig", "mojang");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("null-source skins with different values: NOT equal (default-skin misclassification regression)")
    void nullSourceDifferentValues_notEqual() {
        // DEFAULT_SKIN is built with a null source; source-only equality made
        // ANY null-source skin equal to it, so hasDefaultSkin() reported true
        // for custom skins persisted without a source.
        CustomSkinProperty a = new CustomSkinProperty("textures", "default-value", "default-sig", null);
        CustomSkinProperty b = new CustomSkinProperty("textures", "custom-value", "custom-sig", null);

        assertNotEquals(a, b);
    }

    @Property(tries = 100)
    @Label("equals is reflexive and null-safe")
    // The point of this property IS null-safe equals: a.equals(null) must be
    // false, not throw. ErrorProne's EqualsNull (a non-null receiver contract
    // hint) does not apply to a contract test — suppressing it is the intent.
    @SuppressWarnings("EqualsNull")
    void equalsIsReflexive(@ForAll @From("skins") CustomSkinProperty a) {
        assertEquals(a, a);
        assertFalse(a.equals(null));
    }

    @Property(tries = 100)
    @Label("equals is symmetric")
    void equalsIsSymmetric(@ForAll @From("skins") CustomSkinProperty a,
                           @ForAll @From("skins") CustomSkinProperty b) {
        assertEquals(a.equals(b), b.equals(a), "equals must be symmetric");
    }

    @Property(tries = 100)
    @Label("equals is transitive")
    void equalsIsTransitive(@ForAll @From("skins") CustomSkinProperty a,
                            @ForAll @From("skins") CustomSkinProperty b,
                            @ForAll @From("skins") CustomSkinProperty c) {
        if (a.equals(b) && b.equals(c)) {
            assertTrue(a.equals(c), "equals must be transitive");
        }
    }

    @Property(tries = 100)
    @Label("equals is consistent across repeated calls")
    void equalsIsConsistent(@ForAll @From("skins") CustomSkinProperty a,
                            @ForAll @From("skins") CustomSkinProperty b) {
        boolean first = a.equals(b);
        for (int i = 0; i < 3; i++) {
            assertEquals(first, a.equals(b), "equals must be consistent");
        }
    }

    @Property(tries = 100)
    @Label("equal objects have equal hash codes")
    void hashCodeAgreesWithEquals(@ForAll @From("skins") CustomSkinProperty a,
                                  @ForAll @From("skins") CustomSkinProperty b) {
        if (a.equals(b)) {
            assertEquals(a.hashCode(), b.hashCode(), "equal objects must have equal hash codes");
        }
    }

    @Provide
    Arbitrary<CustomSkinProperty> skins() {
        Arbitrary<String> values = Arbitraries.strings().withCharRange('a', 'z')
                .ofMinLength(0).ofMaxLength(6).injectNull(0.2);
        Arbitrary<String> signatures = Arbitraries.strings().withCharRange('A', 'Z')
                .ofMinLength(0).ofMaxLength(6).injectNull(0.2);
        Arbitrary<String> sources = Arbitraries.strings().withCharRange('0', '9')
                .ofMinLength(0).ofMaxLength(4).injectNull(0.2);
        return Combinators.combine(values, signatures, sources)
                .as(CustomSkinProperty::new);
    }
}
