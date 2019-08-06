package com.connextra.pairing.exercise1;

import java.util.concurrent.atomic.AtomicInteger;

public class SlowExpensiveResourceFactory {
	private final AtomicInteger maxSerialNumber = new AtomicInteger();

	public ExpensiveResource createSprocket() {
		// clang, click, whistle, pop and other expensive onomatopoeic operations
		int serialNumber = maxSerialNumber.incrementAndGet();
		return new ExpensiveResource(serialNumber);
	}
	
	public int getMaxSerialNumber() {
		return maxSerialNumber.get();
	}
}