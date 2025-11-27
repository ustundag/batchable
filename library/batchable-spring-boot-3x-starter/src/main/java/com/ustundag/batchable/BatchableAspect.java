package com.ustundag.batchable;

import com.ustundag.batchable.BatchableCacheManager.BatchCacheEntry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Aspect
@Component
public class BatchableAspect {

    private static final Logger log = LoggerFactory.getLogger(BatchableAspect.class);
    private final BatchableProcessor batchableProcessor;
    private final BatchableCacheManager batchableCacheManager;

    public BatchableAspect(BatchableProcessor batchableProcessor, BatchableCacheManager batchableCacheManager) {
        this.batchableProcessor = batchableProcessor;
        this.batchableCacheManager = batchableCacheManager;
    }

    @Around("@annotation(batchable)")
    public Object intercept(ProceedingJoinPoint joinPoint, Batchable batchable) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length != 1) {
            log.error("@Batchable annotation can only be used on methods with single parameter. Method: {}",
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        Object parameter = args[0];
        String methodKey = getMethodName(joinPoint);
        batchableCacheManager.add(methodKey, parameter, joinPoint.getTarget(), batchable);

        BatchCacheEntry cacheEntry = batchableCacheManager.peek(methodKey);
        if (cacheEntry != null && cacheEntry.shouldTriggerBySize()) {
            try {
                BatchCacheEntry removedEntry = batchableCacheManager.getAndClear(methodKey);
                batchableProcessor.processBatch(removedEntry);
            } catch (Exception e) {
                log.error("Error triggering batch method for: {}", methodKey, e);
            }

        }
        return null;
    }

    // Check every minute
    @Scheduled(fixedRate = 60000)
    public void processExpiredBatches() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, BatchCacheEntry> entriesMap = batchableCacheManager.getAllEntriesMap();

        for (Map.Entry<String, BatchCacheEntry> entry : entriesMap.entrySet()) {
            String methodKey = entry.getKey();
            BatchCacheEntry cacheEntry = entry.getValue();
            if (cacheEntry != null && cacheEntry.shouldTriggerByCron(now)) {
                try {
                    BatchCacheEntry removedEntry = batchableCacheManager.getAndClear(methodKey);
                    batchableProcessor.processBatch(removedEntry);
                } catch (Exception e) {
                    log.error("Error processing expired batch entry for method: {}", methodKey, e);
                }
            }
        }
    }

    private String getMethodName(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        return className + "." + methodName;
    }
}

