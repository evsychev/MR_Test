package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record UserProfile(Map<String, String> fields) {
    private static final Pattern STRING_FIELD = Pattern.compile(
            "\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private static final Pattern ROLES_FIELD = Pattern.compile("\\\"roles\\\"\\s*:\\s*\\[([^]]*)]");
    private static final Pattern JSON_STRING = Pattern.compile("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");

    static UserProfile loadMock() {
        try (InputStream stream = UserProfile.class.getResourceAsStream("/content/mock-user.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing user profile resource: /content/mock-user.json");
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = STRING_FIELD.matcher(json);
            Map<String, String> fields = new LinkedHashMap<>();
            while (matcher.find()) {
                fields.put(matcher.group(1), unescape(matcher.group(2)));
            }
            Matcher rolesMatcher = ROLES_FIELD.matcher(json);
            if (rolesMatcher.find()) {
                Matcher roleMatcher = JSON_STRING.matcher(rolesMatcher.group(1));
                java.util.List<String> roles = new java.util.ArrayList<>();
                while (roleMatcher.find()) {
                    roles.add(unescape(roleMatcher.group(1)));
                }
                fields.put("roles", String.join(", ", roles));
            }
            for (String required : new String[]{"fio", "username", "email", "ost", "post"}) {
                if (!fields.containsKey(required)) {
                    throw new IllegalStateException("User profile is missing required field: " + required);
                }
            }
            return new UserProfile(Map.copyOf(fields));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read mock user profile", exception);
        }
    }

    String value(String key) {
        return fields.getOrDefault(key, "");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }
}
