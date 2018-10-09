package org.dbug;

import java.util.function.Supplier;

import org.qommons.Transaction;

public interface DBugEventBuilder {
	DBugEventBuilder with(String property, Object value);

	DBugEventBuilder with(String property, Supplier<?> value);

	Transaction begin();

	void occurred();
}
