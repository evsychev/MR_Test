package org.example.ui;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

final class Messages {
    private static final Locale LANGUAGE = "en".equalsIgnoreCase(System.getProperty("mr.language", "ru"))
            ? Locale.ENGLISH : Locale.forLanguageTag("ru");
    static final ScopedValue<Locale> ACTIVE = ScopedValue.newInstance();

    static Locale locale(String preference) {
        return switch (preference) {
            case "ru", "en" -> Locale.forLanguageTag(preference);
            default -> LANGUAGE;
        };
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("i18n.messages", locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    private Messages() {}

    static String text(String key) {
        return bundle(ACTIVE.orElse(LANGUAGE)).getString(key);
    }

    static String format(String key, Object... arguments) {
        return new MessageFormat(text(key), ACTIVE.orElse(LANGUAGE)).format(arguments);
    }
}
