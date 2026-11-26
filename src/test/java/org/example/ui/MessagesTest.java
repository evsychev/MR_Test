package org.example.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @Test void bothLanguagesProvideEveryMessageDirectly() throws IOException {
        var english = read("/i18n/messages.properties");
        var russian = read("/i18n/messages_ru.properties");
        assertEquals(english.keySet(), russian.keySet());
        for (String key : english.keySet()) {
            assertFalse(english.getString(key).isBlank(), key);
            assertFalse(russian.getString(key).isBlank(), key);
        }
        var active = "en".equalsIgnoreCase(System.getProperty("mr.language")) ? english : russian;
        assertEquals(active.getString("page.rounds"), PanelPage.ROUNDS.title());
        assertTrue(Messages.format("status.selection", "Pump", 1, 4, 8).contains("Pump"));
    }

    private static ResourceBundle read(String path) throws IOException {
        var stream = MessagesTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new PropertyResourceBundle(reader);
        }
    }
}
