package org.example.smo.buffer;

import org.example.smo.model.Request;
import org.example.smo.model.RequestStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class Buffer {
    private final List<Request> slots;

    public Buffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Buffer capacity must be positive");
        }

        slots = new ArrayList<>(Collections.nCopies(capacity, null));
    }

    public PlacementResult place(Request request) {
        Objects.requireNonNull(request, "Request must not be null");

        if (!isWaiting(request)) {
            throw new IllegalArgumentException("Only waiting requests can enter the buffer");
        }

        if (findSlot(request.getId()) >= 0) {
            throw new IllegalArgumentException("Request ID is already in the buffer");
        }

        int freeSlot = firstFreeSlot();

        if (freeSlot >= 0) {
            slots.set(freeSlot, request);
            return new PlacementResult(freeSlot, null);
        }

        int oldestSlot = findOldestWaitingSlot();

        if (oldestSlot < 0) {
            throw new IllegalStateException("No waiting request can be evicted");
        }

        Request evicted = slots.set(oldestSlot, request);

        return new PlacementResult(oldestSlot, evicted);
    }

    public Request take(long requestId) {
        int slot = findSlot(requestId);

        if (slot < 0) {
            return null;
        }

        Request request = slots.get(slot);
        slots.set(slot, null);

        return request;
    }

    public List<Long> getRequestIdsBySource(int sourceId) {
        if (sourceId <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }

        List<Long> ids = new ArrayList<>();

        for (Request request : slots) {
            if (request != null
                && isWaiting(request)
                && request.getSourceId() == sourceId) {
                ids.add(request.getId());
            }
        }

        return List.copyOf(ids);
    }

    public Set<Integer> getSourceIdsWithWaitingRequests() {
        Set<Integer> ids = new TreeSet<>();

        for (Request request : slots) {
            if (request != null && isWaiting(request)) {
                ids.add(request.getSourceId());
            }
        }

        return Collections.unmodifiableSet(ids);
    }

    public List<Request> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public int capacity() {
        return slots.size();
    }

    public int size() {
        int count = 0;

        for (Request request : slots) {
            if (request != null) {
                count++;
            }
        }

        return count;
    }

    public int firstFreeSlot() {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == null) {
                return i;
            }
        }

        return -1;
    }

    private int findSlot(long requestId) {
        for (int i = 0; i < slots.size(); i++) {
            Request request = slots.get(i);

            if (request != null && request.getId() == requestId) {
                return i;
            }
        }

        return -1;
    }

    private int findOldestWaitingSlot() {
        int oldestSlot = -1;

        for (int i = 0; i < slots.size(); i++) {
            Request candidate = slots.get(i);

            if (candidate == null || !isWaiting(candidate)) {
                continue;
            }

            if (oldestSlot < 0 || olderThan(candidate, slots.get(oldestSlot))) {
                oldestSlot = i;
            }
        }

        return oldestSlot;
    }

    private boolean olderThan(Request candidate, Request other) {
        int byTime = Double.compare(
            candidate.getCreatedAt(),
            other.getCreatedAt()
        );

        return byTime < 0 || (byTime == 0 && candidate.getId() < other.getId());
    }

    private boolean isWaiting(Request request) {
        return request.getStatus() == RequestStatus.CREATED
            || request.getStatus() == RequestStatus.QUEUED;
    }
}
