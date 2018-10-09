package org.dbug.expression;

public class DBugParseException extends Exception {
	public DBugParseException(String message, Throwable cause) {
		super(message, cause);
	}

	public DBugParseException(String message) {
		super(message);
	}
}
