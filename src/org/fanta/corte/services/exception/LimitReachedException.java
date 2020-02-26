package org.fanta.corte.services.exception;

public class LimitReachedException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public LimitReachedException(String message) {
		super(message);
	}
}
