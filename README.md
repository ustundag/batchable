# Batchable Spring Boot 3.x Starter

An AOP-based batch processing library for Spring Boot 3.x applications that automatically collects method parameters and processes them in batches based on size or time triggers.

## Overview

The `batchable-spring-boot-3x-starter` library provides a simple annotation-based approach to batch processing. Instead of processing individual items immediately, you can annotate methods to collect parameters and process them in batches when either:
- The batch size threshold is reached
- A specified time interval has elapsed

This is particularly useful for scenarios like:
- Batch database operations
- Batch API calls
- Batch file processing
- Any operation that benefits from processing multiple items together

## Requirements

- Java 21 or higher
- Spring Boot 3x or higher
- Spring AOP (automatically included)

## Usage

### 1. Annotate Your Methods

Use the `@Batchable` annotation on methods that should collect parameters for batch processing:

```java
@Component
public class UserService {
    
    @Batchable(
        targetMethod = "addUsers",
        size = 10,
        triggerAfterMinutes = 5
    )
    public void addUser(User user) {
        // This method will be intercepted
        // Parameters are collected and processed in batches
    }
    
    public void addUsers(List<User> users) {
        // This method will be called with a batch of users
        // when either:
        // - 10 users have been collected (size trigger)
        // - 5 minutes have passed since the first user was added (time trigger)
        userRepository.saveAll(users);
    }
}
```

### 3. Annotation Parameters

The `@Batchable` annotation supports the following parameters:

- **`targetMethod`** (required): The name of the method that will be called with the batch of parameters. This method must:
  - Be in the same class
  - Be public
  - Accept exactly one parameter of type `List<T>`
  
- **`size`** (optional, default: 10): The number of items to collect before triggering batch processing.

- **`triggerAfterMinutes`** (optional, default: 5): The number of minutes to wait before triggering batch processing based on time.

### Important Constraints

1. **Method Signature**: The annotated method must accept exactly one parameter.
2. **Target Method**: The target method must be public and accept a `List<T>` parameter.
3. **Same Class**: The target method must be in the same class as the annotated method.
4. **Validation**: At least one of `size` or `triggerAfterMinutes` must be greater than zero.

## How It Works

1. **Interception**: When a method annotated with `@Batchable` is called, the AOP aspect intercepts the call.
2. **Caching**: The method parameter is added to an in-memory cache associated with that method.
3. **Size-Based Triggering**: If the cache reaches the specified `size`, the batch is immediately processed.
4. **Time-Based Triggering**: A scheduled task runs every minute to check for batches that have exceeded the `triggerAfterMinutes` threshold.
5. **Batch Processing**: When triggered, all collected parameters are passed as a `List` to the target method.

## Example Use Cases

### Batch Database Inserts

```java
@Component
public class OrderService {
    
    @Batchable(
        targetMethod = "saveOrdersBatch",
        size = 50,
        triggerAfterMinutes = 2
    )
    public void createOrder(Order order) {
        // Individual order creation is intercepted
    }
    
    public void saveOrdersBatch(List<Order> orders) {
        // Batch insert for better performance
        orderRepository.saveAll(orders);
        log.info("Saved {} orders in batch", orders.size());
    }
}
```

### Batch API Calls

```java
@Component
public class NotificationService {
    
    @Batchable(
        targetMethod = "sendNotificationsBatch",
        size = 20,
        triggerAfterMinutes = 1
    )
    public void sendNotification(Notification notification) {
        // Individual notification is queued
    }
    
    public void sendNotificationsBatch(List<Notification> notifications) {
        // Send all notifications in a single API call
        notificationClient.sendBatch(notifications);
    }
}
```

## Thread Safety

The library uses thread-safe data structures (`ConcurrentHashMap`) and locking mechanisms (`ReentrantLock`) to ensure safe concurrent access to batch caches.

## Validation

The library automatically validates all `@Batchable` annotations at application startup:

- Ensures target methods exist
- Validates method signatures
- Checks that target methods are public
- Verifies that at least one trigger condition is valid

If validation fails, the application will fail to start with a clear error message.

## Limitations

- Methods annotated with `@Batchable` must accept exactly one parameter
- The annotated method returns `null` (batch processing is asynchronous)
- Batch processing is in-memory only (not persistent across restarts)
- Target methods must be in the same class as the annotated method

