package org.example.profile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record UserProfile(String id, String name, String username, String position,
                          String email, String organization, List<String> roles) {
    public static UserProfile loadMock() {
        try (var input = UserProfile.class.getResourceAsStream("/content/mock-user.json")) {
            if (input == null) throw new IllegalStateException("Missing mock user profile");
            return read(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read user profile", exception);
        }
    }

    static UserProfile read(InputStream input) throws IOException {
        JsonNode root = new ObjectMapper().readTree(input);
        if (root == null || !root.isObject()) throw new IOException("Expected a user profile object");
        return new UserProfile(root.path("id").asText(""), required(root, "fio"),
                required(root, "username"), required(root, "post"), required(root, "email"),
                required(root, "ost"), StreamSupport.stream(root.path("roles").spliterator(), false)
                        .map(JsonNode::asText).toList());
    }

    private static String required(JsonNode root, String field) throws IOException {
        if (!root.path(field).isTextual()) throw new IOException("Missing profile field: " + field);
        return root.get(field).textValue();
    }
}
