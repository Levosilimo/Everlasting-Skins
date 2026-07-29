package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence atomicity and corrupt-record handling for {@link SkinIO}.
 * Uses a temporary directory for each test method.
 */
class SkinIOTest {

    @TempDir
    Path tempDir;

    private SkinIO skinIO;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        skinIO = new SkinIO(tempDir);
        uuid = UUID.randomUUID();
    }

    @Nested
    class AtomicWrite {

        @Test
        @DisplayName("saveSkin creates a valid JSON file at the target path")
        void saveCreatesFile() {
            CustomSkinProperty skin = new CustomSkinProperty("value1", "sig1", "source1");
            skinIO.saveSkin(uuid, skin);

            Path target = tempDir.resolve(uuid + ".json");
            assertTrue(Files.exists(target), "Target file must exist after save");
            assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
                @Override
                public void execute() throws Throwable {
                    new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
                }
            });
        }

        @Test
        @DisplayName("saveSkin does not leave a .tmp file behind")
        void saveCleansTempFile() throws IOException {
            CustomSkinProperty skin = new CustomSkinProperty("value", "sig", "src");
            skinIO.saveSkin(uuid, skin);

            Path temp = tempDir.resolve(uuid + ".json.tmp");
            assertFalse(Files.exists(temp), "Temp file must be removed after atomic rename");
        }

        @Test
        @DisplayName("Simulated crash mid-write: prior state readable")
        void crashBeforeRenameReturnsPriorState() throws IOException {
            CustomSkinProperty initialSkin = new CustomSkinProperty("initialValue", "initialSig", "initial");
            skinIO.saveSkin(uuid, initialSkin);

            CustomSkinProperty loaded = skinIO.loadSkin(uuid);
            assertNotNull(loaded);
            assertEquals("initialValue", loaded.getOriginalProperty().getValue());

            Path temp = tempDir.resolve(uuid + ".json.tmp");
            String crashedJson = "{\"value\":\"crashValue\",\"signature\":\"crashSig\",\"name\":\"textures\",\"source\":\"crash\"}";
            Files.write(temp, crashedJson.getBytes(StandardCharsets.UTF_8));

            CustomSkinProperty afterCrash = skinIO.loadSkin(uuid);
            assertNotNull(afterCrash);
            assertEquals("initialValue", afterCrash.getOriginalProperty().getValue(),
                    "Must return prior state when crash prevented atomic rename");
        }
    }

    @Nested
    @DisplayName("Corrupt record handling")
    class CorruptRecord {

        @Test
        @DisplayName("Malformed JSON in storage -> returns null, file quarantined")
        void malformedJson() throws IOException {
            writeRawSkinFile("this is not json at all");

            CustomSkinProperty loaded = skinIO.loadSkin(uuid);
            assertNull(loaded);

            Path target = tempDir.resolve(uuid + ".json");
            assertFalse(Files.exists(target), "Original file should be quarantined");
            assertCorruptFileExists();
        }

        @Test
        @DisplayName("Partial JSON (truncated) -> returns null, file quarantined")
        void partialJson() throws IOException {
            writeRawSkinFile("{\"value\":\"abc\",\"signature\"");

            CustomSkinProperty loaded = skinIO.loadSkin(uuid);
            assertNull(loaded);
            assertCorruptFileExists();
        }

        @Test
        @DisplayName("Empty file -> returns null, file quarantined")
        void emptyFile() throws IOException {
            writeRawSkinFile("");

            CustomSkinProperty loaded = skinIO.loadSkin(uuid);
            assertNull(loaded);
            assertCorruptFileExists();
        }

        @Test
        @DisplayName("Missing fields (no value) -> JsonUtils returns null, loadSkin returns null")
        void missingFields() throws IOException {
            writeRawSkinFile("{\"name\":\"textures\",\"signature\":\"sig\"}");

            CustomSkinProperty loaded = skinIO.loadSkin(uuid);
            assertNull(loaded);
        }

        @Test
        @DisplayName("getSourceFromFileStorage with corrupt JSON -> returns null")
        void sourceFromCorruptFile() throws IOException {
            writeRawSkinFile("garbage");

            String source = skinIO.getSourceFromFileStorage(uuid);
            assertNull(source);
        }

        @Test
        @DisplayName("Non-existent file -> returns null (not quarantined, not created)")
        void nonExistentFile() {
            assertNull(skinIO.loadSkin(uuid));
            Path target = tempDir.resolve(uuid + ".json");
            assertFalse(Files.exists(target));
        }
    }

    @Nested
    @DisplayName("Load/save round-trip")
    class RoundTrip {

        @Test
        @DisplayName("save then load returns equivalent skin")
        void saveThenLoad() {
            CustomSkinProperty original = new CustomSkinProperty("val", "sig", "test-source");
            skinIO.saveSkin(uuid, original);

            CustomSkinProperty loaded = skinIO.loadSkin(uuid);
            assertNotNull(loaded);
            assertEquals("val", loaded.getOriginalProperty().getValue());
            assertEquals("sig", loaded.getOriginalProperty().getSignature());
            assertEquals("test-source", loaded.getSource());
        }

        @Test
        @DisplayName("getSourceFromFileStorage returns source")
        void getSource() {
            CustomSkinProperty skin = new CustomSkinProperty("v", "s", "my-source");
            skinIO.saveSkin(uuid, skin);

            String source = skinIO.getSourceFromFileStorage(uuid);
            assertEquals("my-source", source);
        }

        @Test
        @DisplayName("Multiple saves overwrite")
        void overwrite() {
            CustomSkinProperty first = new CustomSkinProperty("val1", "sig1", "src1");
            skinIO.saveSkin(uuid, first);

            CustomSkinProperty second = new CustomSkinProperty("val2", "sig2", "src2");
            skinIO.saveSkin(uuid, second);

            CustomSkinProperty loaded = skinIO.loadSkin(uuid);
            assertNotNull(loaded);
            assertEquals("val2", loaded.getOriginalProperty().getValue());
        }
    }

    @Nested
    @DisplayName("deleteSkin")
    class DeleteSkin {

        @Test
        @DisplayName("deleteSkin removes the file when it exists")
        void deleteExistingFile() throws IOException {
            CustomSkinProperty skin = new CustomSkinProperty("v", "s", "src");
            skinIO.saveSkin(uuid, skin);
            Path target = tempDir.resolve(uuid + ".json");
            assertTrue(Files.exists(target));

            skinIO.deleteSkin(uuid);

            assertFalse(Files.exists(target));
        }

        @Test
        @DisplayName("deleteSkin does not throw when file does not exist")
        void deleteNonExistentFile() {
            assertDoesNotThrow(() -> skinIO.deleteSkin(uuid));
        }
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    private void writeRawSkinFile(String content) throws IOException {
        Path target = tempDir.resolve(uuid + ".json");
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    private void assertCorruptFileExists() {
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            boolean found = files.anyMatch(p -> p.getFileName().toString().startsWith(uuid + ".json.corrupt-"));
            assertTrue(found, "Expected a .corrupt-* quarantine file");
        } catch (IOException e) {
            fail("Failed to list temp dir for corrupt file check", e);
        }
    }
}
