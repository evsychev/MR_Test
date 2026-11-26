package org.example.profile;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UserProfileTest {
    @Test void identityComesOnlyFromTheTopLevel() throws Exception {
        var data = """
                {"id":"account","fio":"Name","username":"domain\\\\user","post":"USER",
                 "email":"email","ost":"Org","roles":["A","B"],"nested":{"id":"other"}}
                """;
        var profile = UserProfile.read(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
        assertEquals("account", profile.id());
        assertEquals("domain\\user", profile.username());
        assertEquals(List.of("A", "B"), profile.roles());
    }
}
