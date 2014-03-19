package com.mg.api;

public class MGAPIException extends Exception {
	private static final long serialVersionUID = 1L;

	public MGAPIException(String message) {
		super(message);
	}

	public MGAPIException(String message, Throwable throwable) {
		super(message, throwable);
	}
}