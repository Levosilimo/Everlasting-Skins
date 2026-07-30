package levosilimo.everlastingskins.integration.placeholderapi;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class PlaceholderApiHookTest {

    private static final java.lang.reflect.Field REGISTERED_FIELD;

    static {
        try {
            REGISTERED_FIELD = PlaceholderApiHook.class.getDeclaredField("registered");
            REGISTERED_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void resetRegisteredState() throws Exception {
        REGISTERED_FIELD.set(null, false);
    }

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

    @Test
    @DisplayName("tryRegister is a no-op when already registered")
    void tryRegister_whenAlreadyRegistered_isNoOp() throws Exception {
        // Set registered = true directly to simulate a prior successful registration
        REGISTERED_FIELD.set(null, true);

        // Second call should be skipped by the early return
        assertDoesNotThrow(PlaceholderApiHook::tryRegister);
        assertTrue(PlaceholderApiHook.isRegistered());
    }

    @Test
    @DisplayName("tryRegister handles PAPI present but incompatible version gracefully")
    void tryRegister_whenPapiVersionLacksMethod_doesNotThrow() {
        // PAPI 2.12.3 is on the test classpath but does not expose
        // the isPlaceholderAPIOnServer() method that the hook looks up
        // via reflection. The catch(NoSuchMethodException) path should
        // handle this without crashing.
        assertDoesNotThrow(PlaceholderApiHook::tryRegister);
        assertFalse(PlaceholderApiHook.isRegistered());
    }
}
