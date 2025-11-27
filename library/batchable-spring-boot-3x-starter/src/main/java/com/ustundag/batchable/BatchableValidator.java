package com.ustundag.batchable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

@Component
public class BatchableValidator implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(BatchableValidator.class);

    private ApplicationContext applicationContext;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Only process the root application context to avoid duplicate processing
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        this.applicationContext = event.getApplicationContext();
        validateAllBatchableMethods();
    }

    private void validateAllBatchableMethods() {
        Map<String, Object> componentBeans = applicationContext.getBeansWithAnnotation(Component.class);
        for (Map.Entry<String, Object> entry : componentBeans.entrySet()) {
            Object bean = entry.getValue();
            String beanName = entry.getKey();
            Class<?> clazz = bean.getClass();

            // Skip Spring internal classes and proxy classes
            if (isFrameworkRelated(beanName)) {
                continue;
            }

            Class<?> actualClass = getActualClass(clazz);

            // Check all methods in the actual class
            Method[] declaredMethods = actualClass.getDeclaredMethods();
            for (Method method : declaredMethods) {
                Batchable batchable = AnnotationUtils.findAnnotation(method, Batchable.class);
                if (batchable != null) {
                    validateBatchable(bean, method, batchable);
                }
            }
        }
    }

    private Class<?> getActualClass(Class<?> clazz) {
        // Unwrap proxy to get the actual class (annotations are on the real class, not the proxy)
        String className = clazz.getName();
        if (className.contains("CGLIB") || className.contains("$$")) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && !superclass.equals(Object.class)) {
                return superclass;
            }
        }
        return clazz;
    }

    private boolean isFrameworkRelated(String beanName) {
        return beanName.contains("$Proxy") ||
                beanName.contains("CGLIB") ||
                beanName.contains("$$") ||
                beanName.startsWith("org.springframework.");
    }

    private void validateBatchable(Object bean, Method annotatedMethod, Batchable batchable) {
        String className = bean.getClass().getName();
        String methodName = annotatedMethod.getName();
        int size = batchable.size();
        int triggerAfterMinutes = batchable.triggerAfterMinutes();

        if (size <= 0 || triggerAfterMinutes <= 0) {
            throw new IllegalStateException(
                    String.format("@Batchable validation failed for method %s.%s: " +
                                    "At least one of 'size' or 'triggerAfterMinutes' must be greater than zero. " +
                                    "Current values: size=%d, triggerAfterMinutes=%d",
                            className, methodName, size, triggerAfterMinutes));
        }

        validateTargetMethod(bean, annotatedMethod, batchable);
    }

    private void validateTargetMethod(Object bean, Method annotatedMethod, Batchable batchable) {
        String className = bean.getClass().getName();
        String targetMethodName = batchable.targetMethod();
        String annotatedMethodName = annotatedMethod.getName();

        try {
            // Use actual class to find private methods (proxy classes don't show private methods)
            Class<?> actualClass = getActualClass(bean.getClass());
            Method targetMethod = findTargetMethod(actualClass, targetMethodName);

            // Check if targetMethod exists
            if (targetMethod == null) {
                throw new IllegalStateException(
                        String.format("@Batchable validation failed for method %s.%s: " +
                                        "Target method '%s' not found in same class or its parent classes",
                                className, annotatedMethodName, targetMethodName));
            }

            // Check if targetMethod is public
            if (java.lang.reflect.Modifier.isPrivate(targetMethod.getModifiers())) {
                throw new IllegalStateException(
                        String.format("@Batchable validation failed for method %s.%s: " +
                                        "Target method '%s' in same class must be public, but it is private.",
                                className, annotatedMethodName, targetMethodName));
            }

            // Check if targetMethod signature accepts List parameter
            Class<?>[] paramTypes = targetMethod.getParameterTypes();
            if (paramTypes.length != 1 || !java.util.List.class.isAssignableFrom(paramTypes[0])) {
                throw new IllegalStateException(
                        String.format("@Batchable validation failed for method %s.%s: " +
                                        "Target method '%s' in same class must accept exactly one parameter of type List",
                                className, annotatedMethodName, targetMethodName));
            }
        } catch (Exception e) {
            log.error("@Batchable validation failed for method {}.{}: {}", className, annotatedMethodName,
                    e.getMessage());
            throw e;
        }
    }

    private Method findTargetMethod(Class<?> clazz, String methodName) {
        // First check public methods (including inherited ones) - these are already public
        Method publicMethod = Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElse(null);

        if (publicMethod != null) {
            return publicMethod;
        }

        // Check declared methods (might be private, protected, or package-private)
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElse(null);
    }
}

