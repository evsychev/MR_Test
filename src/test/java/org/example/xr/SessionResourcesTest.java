package org.example.xr;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionResourcesTest {
    @Test void releasesInReverseOrderEvenAfterFailure() {
        var resources = new SessionResources();
        var released = new ArrayList<Integer>();
        resources.defer(() -> released.add(1));
        resources.defer(() -> { throw new IllegalStateException("release failure"); });
        resources.defer(() -> released.add(3));
        assertThrows(IllegalStateException.class, resources::close);
        assertEquals(List.of(3, 1), released);
        resources.close();
        assertEquals(2, released.size());
    }
}
