# Batchable Spring Boot

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

## Usage

### 1. Annotate Your Methods

Use the `@Batchable` annotation on methods that should collect parameters for batch processing:

```java
@Component
public class OrderService {

  @Batchable(
          targetMethod = "deleteOrders",
          size = 1000,
          timeout = 1
  )
  public void deleteOrder(Long orderId) {
    // Individual delete is intercepted and batched
  }

  public void deleteOrders(List<Long> orderIds) {
    // Batch delete using IN clause
  }
}
```

### 2. Annotation Parameters

The `@Batchable` annotation supports the following parameters:

- **`targetMethod`** (required): The name of the method that will be called with the batch of parameters. This method must:
  - Be in the same class
  - Be public
  - Accept exactly one parameter of type `List<T>`
  
- **`size`** (optional, default: 10): The number of items to collect before triggering batch processing.

- **`timeout`** (optional, default: 5): The number of minutes to wait before triggering batch processing based on time.

### Important Constraints

1. **Method Signature**: The annotated method must accept exactly one parameter.
2. **Target Method**: The target method must be public and accept a `List<T>` parameter.
3. **Same Class**: The target method must be in the same class as the annotated method.
4. **Validation**: At least one of `size` or `timeout` must be greater than zero.

## How It Works

1. **Interception**: When a method annotated with `@Batchable` is called, the AOP aspect intercepts the call.
2. **Caching**: The method parameter is added to an in-memory cache associated with that method.
3. **Size-Based Triggering**: If the cache reaches the specified `size`, the batch is immediately processed.
4. **Time-Based Triggering**: A scheduled task runs every minute to check for batches that have exceeded the `timeout` threshold.
5. **Batch Processing**: When triggered, all collected parameters are passed as a `List` to the target method.

## Motivation

Batch processing is especially critical for DELETE operations. Processing deletes individually vs. in batches can result in **10-100x performance differences**.

**Why Batch Deletes Are Essential:**

1. Network Round-Trips
2. Execution Plan Overhead
3. Transaction Management
4. Lock/Contention
5. Log Growth

**Performance Comparison:**
- **Row-by-row DELETE**: 5-30 seconds for 10,000 rows
- **Set-based DELETE**: 200-700 milliseconds for 10,000 rows

**Example Implementation:**

```java
@Component
public class OrderService {
    
    @Batchable(
        targetMethod = "deleteOrders",
        size = 1000,
        timeout = 1
    )
    public void deleteOrder(Long orderId) {
        // Individual delete is intercepted and batched
    }
    
    public void deleteOrders(List<Long> orderIds) {
        // Batch delete using IN clause
    }
}
```

**Best Practices:**
- For batches up to ~1,000 IDs: Use `IN` clause (`DELETE FROM Orders WHERE Id IN (...)`)
- For larger batches: Use temporary table + JOIN approach:

**Key Takeaway:** "Set-based operations always beat row-by-row" - this is the fundamental philosophy of all RDBMS engines (SQL Server, PostgreSQL, Oracle, MySQL). Batch processing aligns perfectly with this principle.
