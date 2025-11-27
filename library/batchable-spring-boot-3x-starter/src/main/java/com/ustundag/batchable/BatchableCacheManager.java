package com.ustundag.batchable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class BatchableCacheManager {

    private static final Logger log = LoggerFactory.getLogger(BatchableCacheManager.class);

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, BatchCacheEntry> cache = new ConcurrentHashMap<>();

    public void add(String methodKey, Object parameter, Object target, Batchable batchable) {
        lock.lock();
        try {
            int batchSize = batchable.size();
            int cronMinutes = batchable.triggerAfterMinutes();
            String batchMethodName = batchable.targetMethod();

            BatchCacheEntry cacheEntry = cache.computeIfAbsent(methodKey, k ->
                    new BatchCacheEntry(batchSize, cronMinutes, methodKey, target, batchMethodName));

            cacheEntry.setLastAccess(LocalDateTime.now());

            if (!cacheEntry.getParameters().contains(parameter)) {
                cacheEntry.getParameters().add(parameter);
            }
        } finally {
            lock.unlock();
        }
    }

    public BatchCacheEntry getAndClear(String methodKey) {
        lock.lock();
        try {
            BatchCacheEntry entry = cache.remove(methodKey);
            if (entry != null && !entry.getParameters().isEmpty()) {
                return entry;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public BatchCacheEntry peek(String methodKey) {
        lock.lock();
        try {
            return cache.get(methodKey);
        } finally {
            lock.unlock();
        }
    }

    public Map<String, BatchCacheEntry> getAllEntriesMap() {
        lock.lock();
        try {
            return new HashMap<>(cache);
        } finally {
            lock.unlock();
        }
    }

    public static class BatchCacheEntry {

        private final int batchSize;
        private final int cronMinutes;
        private final Object bean;
        private final String methodKey;
        private final String batchMethodName;
        private final List<Object> parameters;
        private final LocalDateTime createdAt;
        private LocalDateTime lastAccess;

        public BatchCacheEntry(int batchSize, int cronMinutes, String batchMethodName, Object bean,
                String methodKey) {
            LocalDateTime now = LocalDateTime.now();
            this.parameters = new ArrayList<>();
            this.batchSize = batchSize;
            this.cronMinutes = cronMinutes;
            this.batchMethodName = batchMethodName;
            this.bean = bean;
            this.methodKey = methodKey;
            this.createdAt = now;
            this.lastAccess = now;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public int getCronMinutes() {
            return cronMinutes;
        }

        public Object getBean() {
            return bean;
        }

        public String getMethodKey() {
            return methodKey;
        }

        public String getBatchMethodName() {
            return batchMethodName;
        }

        public List<Object> getParameters() {
            return parameters;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getLastAccess() {
            return lastAccess;
        }

        public void setLastAccess(LocalDateTime lastAccess) {
            this.lastAccess = lastAccess;
        }

        public boolean shouldTriggerBySize() {
            return parameters.size() >= batchSize;
        }

        public boolean shouldTriggerByCron(LocalDateTime now) {
            boolean expired = createdAt.plusMinutes(cronMinutes).isBefore(now) ||
                    createdAt.plusMinutes(cronMinutes).isEqual(now);

            return expired && !parameters.isEmpty();
        }
    }
}

