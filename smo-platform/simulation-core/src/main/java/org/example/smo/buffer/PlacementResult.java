package org.example.smo.buffer;

import org.example.smo.model.Request;

public record PlacementResult(int slotIndex, Request evictedRequest) {

    public PlacementResult {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("Slot index cannot be negative");
        }
    }

    public Long evictedRequestId() {
        return evictedRequest == null ? null : evictedRequest.getId();
    }
}
