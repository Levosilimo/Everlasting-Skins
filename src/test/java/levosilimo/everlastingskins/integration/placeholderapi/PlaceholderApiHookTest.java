package levosilimo.everlastingskins.integration.placeholderapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceholderApiHookTest {
    @Test
    @DisplayName("tryRegister when PAPI is absent does not throw")
    void tryRegister_whenNotPresent_doesNotThrow() {
        PlaceholderApiHook.tryRegister();
        assertFalse(PlaceholderApiHook.isRegistered());
    }
    @Test
    @DisplayName("isRegistered returns false initially")
    void isRegistered_returnsFalseInitially() {
        assertFalse(PlaceholderApiHook.isRegistered());
    }
}
