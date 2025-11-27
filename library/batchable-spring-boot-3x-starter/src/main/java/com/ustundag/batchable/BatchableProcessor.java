package com.ustundag.batchable;

import com.ustundag.batchable.BatchableCacheManager.BatchCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

@Component
public class BatchableProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchableProcessor.class);

    public void processBatch(BatchCacheEntry cacheEntry) {
        Object bean = cacheEntry.getBean();
        String batchMethodName = cacheEntry.getBatchMethodName();
        List<Object> parameters = cacheEntry.getParameters();

        try {
            Method batchMethod = findBatchMethod(bean.getClass(), batchMethodName);
            if (batchMethod == null) {
                log.error("Batch method not found: {} in class {}", batchMethodName, bean.getClass().getName());
                return;
            }
            batchMethod.invoke(bean, parameters);
        } catch (Exception e) {
            log.error("Error processing batch for method: {}", batchMethodName, e);
            throw new RuntimeException("Failed to process batch", e);
        }
    }

    private Method findBatchMethod(Class<?> clazz, String methodName) {
        try {
            return Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .filter(method -> {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        return paramTypes.length == 1 && List.class.isAssignableFrom(paramTypes[0]);
                    })
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("Error finding batch method: {}", methodName, e);
            return null;
        }
    }
}

