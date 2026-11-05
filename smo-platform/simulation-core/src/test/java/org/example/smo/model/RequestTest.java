package org.example.smo.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestTest {
    private Request createRequest() {
        return new Request(
            1L,
            1,
            0.0,
            "Electronics",
            "Moscow",
            "Saint Petersburg"
        );
    }

    @Test
    void shouldCompleteRequest() {
        Request request = createRequest();

        request.markQueued();
        request.markProcessing();
        request.markCompleted();

        assertEquals(RequestStatus.COMPLETED, request.getStatus());
    }

    @Test
    void shouldRejectQueuedRequest() {
        Request request = createRequest();

        request.markQueued();
        request.markRejected();

        assertEquals(RequestStatus.REJECTED, request.getStatus());
    }

    @Test
    void shouldNotProcessCompletedRequest() {
        Request request = createRequest();

        request.markQueued();
        request.markProcessing();
        request.markCompleted();

        assertThrows(IllegalStateException.class, request::markProcessing);
        assertThrows(IllegalStateException.class, request::markRejected);
    }

    @Test
    void shouldNotProcessRejectedRequest() {
        Request request = createRequest();

        request.markQueued();
        request.markRejected();

        assertThrows(IllegalStateException.class, request::markProcessing);
        assertThrows(IllegalStateException.class, request::markCompleted);
    }

    @Test
    void shouldNotCompleteCreatedRequest() {
        Request request = createRequest();

        assertThrows(IllegalStateException.class, request::markCompleted);
    }

    @Test
    void shouldRejectInvalidCreationTime() {
        assertThrows(IllegalArgumentException.class, () -> new Request(1L, 1, -1.0, "Cargo", "A", "B"));
    }
}
