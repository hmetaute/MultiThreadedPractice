package com.connextra.pairing.exercise1;


public class ExpensiveResource {
	private int serialNumber;
	
	public ExpensiveResource(int serialNumber) {
		this.serialNumber = serialNumber;
	}
	
	@Override
	public String toString() {
		return "resource number " + serialNumber;
	}
}
