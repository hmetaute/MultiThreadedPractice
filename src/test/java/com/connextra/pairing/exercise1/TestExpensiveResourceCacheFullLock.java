package com.connextra.pairing.exercise1;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class TestExpensiveResourceCacheFullLock {
    private SlowExpensiveResourceFactory slowExpensiveResourceFactory = new SlowExpensiveResourceFactory();

    @Test
    public void testCacheReturnsASprocket() {
        ExpensiveResourceCache cache = new ExpensiveResourceCache(slowExpensiveResourceFactory);
        ExpensiveResource expensiveResource = cache.getWithFullLock("key");
        assertNotNull(expensiveResource);
    }

    @Test
    public void testCacheReturnsSameObjectForSameKey() {
        ExpensiveResourceCache cache = new ExpensiveResourceCache(slowExpensiveResourceFactory);

        ExpensiveResource expensiveResource1 = cache.getWithFullLock("key");
        ExpensiveResource expensiveResource2 = cache.getWithFullLock("key");

        assertEquals("Cache should return the same object for the same key", expensiveResource1, expensiveResource2);
        assertEquals("Factory's create method should be called once only", 1, slowExpensiveResourceFactory.getMaxSerialNumber());
    }

    @Test
    public void testCacheReturnsDifferentObjectsForDifferentKeys() {
        ExpensiveResourceCache cache = new ExpensiveResourceCache(slowExpensiveResourceFactory);
        ExpensiveResource expensiveResource1 = cache.getWithFullLock("key1");
        ExpensiveResource expensiveResource2 = cache.getWithFullLock("key2");
        assertNotEquals("Different keys should yield different values", expensiveResource1, expensiveResource2);
    }

    @Test
    public void testCacheTimeout() throws InterruptedException {
        //1. You give the entire cache a time to live or max age
        long maxCachedItemAgeMs = 50;
        ExpensiveResourceCache cache = new ExpensiveResourceCache(slowExpensiveResourceFactory);
        cache.setMaxAgeMs(maxCachedItemAgeMs);

        //1. Get the item before it times out
        ExpensiveResource beforeTimeoutExpensiveResource = cache.getWithFullLock("key");

        //Wait so that the item times out
        Thread.sleep(maxCachedItemAgeMs + 10);


        //2. Get the item after the time out passes
        ExpensiveResource itemRefreshedAfterTimeout = cache.getWithFullLock("key");

        assertNotEquals("Cached item should expire after maxAge", beforeTimeoutExpensiveResource,
                itemRefreshedAfterTimeout);
    }

    @Test
    public void testThreadSafe() throws ExecutionException, InterruptedException {
        //Set up the shared resource
        ExpensiveResourceCache cache = new ExpensiveResourceCache(slowExpensiveResourceFactory);

        //Set up the threads
        ExecutorService service = Executors.newFixedThreadPool(2);
        CompletableFuture<ExpensiveResource> firstThreadResult =
                CompletableFuture.supplyAsync(() -> cache.getWithFullLock("key1"), service);
        CompletableFuture<ExpensiveResource> secondThreadResult =
                CompletableFuture.supplyAsync(() -> cache.getWithFullLock("key1"), service);

        firstThreadResult.thenAcceptBoth(secondThreadResult, (firstResult, secondResult) ->
                assertEquals("The cache should return the same object for the two different threads",
                        firstResult, secondResult));


		/*  Or wait for both to be ready blocking
		assertEquals("The cache should return the same object for the two different threads",
				firstThreadResult.get(),
				secondThreadResult.get());
		*/
    }
}
