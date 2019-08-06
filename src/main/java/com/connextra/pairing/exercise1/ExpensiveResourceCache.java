package com.connextra.pairing.exercise1;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Class that acts as the cache for an expensive and slow to build resource
 * it follows three main synchronization techniques: a lock, a readWrite lock and a ConcurrentHashmap
 * in order to implement access control to the shared mutable state
 * (in this case the expensive resource and the relationship to how old it is)
 */
public class ExpensiveResourceCache {

	private final HashMap<String, ExpensiveResource> cache;
	private final HashMap<String, Long> cachedItemsTimestamp;
	private long maxCachedItemAge;
	private final ConcurrentHashMap<String, ExpensiveResourceWithTimeStamp> cacheWithThreadSafety;
	private ReentrantLock lock = new ReentrantLock();
	private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private final Lock readLock = readWriteLock.readLock();
	private final Lock writeLock = readWriteLock.writeLock();

	/**
	 * Creates slowly the expensive resource instances
	 */
	private final SlowExpensiveResourceFactory expensiveResourceFactory;

	public ExpensiveResourceCache(SlowExpensiveResourceFactory expensiveResourceFactory) {
		this.expensiveResourceFactory = expensiveResourceFactory;
		this.cache = new HashMap<>();
		this.cachedItemsTimestamp = new HashMap<>();
		this.maxCachedItemAge = Long.MAX_VALUE;
		this.cacheWithThreadSafety = new ConcurrentHashMap<>();
	}

	public void setMaxAgeMs(long maxAgeMs) {
		this.maxCachedItemAge = maxAgeMs;
	}

	/**
	 * Implements the retrieval of an Expensive Resource instance blocking the reader threads
	 * using a full block for the reader and writer threads when each other pass through the
	 * critical parts of the code in order to maintain the data integrity constraints
	 * Implements the rule that the cached item expires after maxCacheItemAge ms and gets refreshed
	 * @param key
	 * @return
	 */
	public ExpensiveResource getWithFullLock(String key) {
		ExpensiveResource result = null;
		long currentTimeMs = new Date().getTime();
		try {
			lock.lock();
			if (cache.containsKey(key) && currentTimeMs - cachedItemsTimestamp.get(key) < maxCachedItemAge) {
				result = cache.get(key);
			}
		} finally {
			lock.unlock();
		}

		if (result == null){
			try {
				lock.lock();
				ExpensiveResource expensiveResourceToCache = this.expensiveResourceFactory.createSprocket();
				cache.put(key, expensiveResourceToCache);
				cachedItemsTimestamp.put(key, new Date().getTime());
				result = expensiveResourceToCache;
			} finally {
				lock.unlock();
			}
		}
		return result;
	}

	/**
	 * Utility method to update atomically with the writeLock both maps which compose the
	 * shared mutable state
	 * @param key
	 * @return
	 */
	private ExpensiveResource updateSprocketCachesAndReturnNewValue(String key) {
		try {
			writeLock.lock();
			ExpensiveResource expensiveResourceToCache = this.expensiveResourceFactory.createSprocket();
			cache.put(key, expensiveResourceToCache);
			cachedItemsTimestamp.put(key, new Date().getTime());
			return expensiveResourceToCache;
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Implements the retrieval of an Expensive Resource based on a reentrant read write block.
	 * Several Reader threads can get a readLock lock as long as the writer lock doesn't get a writeLock
	 * This implementation performs better if the reads-to-write ratio is big.
	 * Otherwise the full lock implementation performs better
	 * @param key
	 * @return
	 */
	public ExpensiveResource getWithReadWriteLock(String key) {
		ExpensiveResource result = null;
		try {
			readLock.lock();
			if (cache.containsKey(key)) {
				result = cache.get(key);
			}
		} finally {
			readLock.unlock();
		}
		if (result == null) {
			result = updateSprocketCachesAndReturnNewValue(key);
		} else {
			long cachedItemTTLTime;
			try {
				readLock.lock();
				cachedItemTTLTime = cachedItemsTimestamp.get(key);
			} finally {
				readLock.unlock();
			}
			if (new Date().getTime() - cachedItemTTLTime > maxCachedItemAge) {
				result = updateSprocketCachesAndReturnNewValue(key);
			}
		}
		return result;
	}

	/**
	 * Implements the same logic using a ConcurrentHashMap as the underlying mechanism for concurrency
	 * and atomicity
	 * @param key
	 * @return
	 */
	public ExpensiveResource getWithConcurrentHashMap(String key) {
		ExpensiveResource result = null;
		result = cacheWithThreadSafety.compute(key, (passedKey, value) -> {
			if (value == null) {
				return new ExpensiveResourceWithTimeStamp(expensiveResourceFactory.createSprocket());
			} else {
				long currentTimeMs = new Date().getTime();
				long timeDifference = currentTimeMs - value.getDate().getTime();
				if (timeDifference > maxCachedItemAge) {
					return new ExpensiveResourceWithTimeStamp(expensiveResourceFactory.createSprocket());
				}
				return value;
			}
		}).getExpensiveResource();
		return result;
	}


	/**
	 * Inner class that represents both the ExpensiveResource and the moment in which it was created
	 */
	private class ExpensiveResourceWithTimeStamp {
		private ExpensiveResource expensiveResource;
		private Date date;

		public ExpensiveResourceWithTimeStamp(ExpensiveResource expensiveResource) {
			this.expensiveResource = expensiveResource;
			this.date = new Date();
		}

		public ExpensiveResource getExpensiveResource() {
			return expensiveResource;
		}

		public Date getDate() {
			return date;
		}
	}


}
