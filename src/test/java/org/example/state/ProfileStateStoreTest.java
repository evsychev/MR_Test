package org.example.state;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProfileStateStoreTest {
    @TempDir Path directory;

    @Test void roundTripAndIsolation() {
        var path = directory.resolve("settings.db");
        var saved = new UserAppState(1.1f, 1.5f, -.2f, .4f, 0, true, "machine", Set.of("plant"), 7);
        try (var first = ProfileStateStore.open("first", path)) {
            first.save(saved);
        }
        try (var other = ProfileStateStore.open("second", path)) {
            assertEquals(UserAppState.defaults(), other.state());
            other.save(new UserAppState(1, 1, 0, 0, 0, false, "other", Set.of(), 1));
        }
        try (var first = ProfileStateStore.open("first", path)) {
            assertEquals(saved, first.state());
        }
    }

    @Test void missingIdentityDoesNotCreateDatabase() {
        Path path = directory.resolve("settings.db");
        try (var store = ProfileStateStore.open(" ", path)) {
            assertEquals(UserAppState.defaults(), store.state());
        }
        assertFalse(Files.exists(path));
    }
}
