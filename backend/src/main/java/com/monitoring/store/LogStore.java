package com.monitoring.store;

import com.monitoring.model.CallStatus;
import com.monitoring.model.LogEntry;
import com.monitoring.model.PageResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Component
public class LogStore {
    private static final int MAX_ENTRIES = 5000;
    private final ArrayDeque<LogEntry> entries = new ArrayDeque<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void add(LogEntry entry) {
        lock.writeLock().lock();
        try {
            entries.addLast(Objects.requireNonNull(entry));
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<LogEntry> all() {
        lock.readLock().lock();
        try {
            return entries.stream().sorted(Comparator.comparing(LogEntry::timestamp).reversed()).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public PageResponse<LogEntry> findAll(String service, CallStatus status, Instant from, Instant to, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<LogEntry> filtered = all().stream()
                .filter(log -> service == null || service.isBlank() || log.serviceId().equals(service))
                .filter(log -> status == null || log.status() == status)
                .filter(log -> from == null || !log.timestamp().isBefore(from))
                .filter(log -> to == null || !log.timestamp().isAfter(to))
                .collect(Collectors.toList());
        long start = (long) safePage * safeSize;
        List<LogEntry> content = start >= filtered.size() ? List.of()
                : filtered.subList((int) start, Math.min(filtered.size(), (int) start + safeSize));
        int pages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);
        return new PageResponse<>(content, safePage, safeSize, filtered.size(), pages);
    }
}
