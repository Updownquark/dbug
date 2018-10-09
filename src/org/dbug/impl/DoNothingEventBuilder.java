package org.dbug.impl;

import java.util.function.Supplier;

import org.dbug.DBugEventBuilder;
import org.qommons.Transaction;

class DoNothingEventBuilder implements DBugEventBuilder {
	static DoNothingEventBuilder INSTANCE = new DoNothingEventBuilder();

	private DoNothingEventBuilder() {}

	@Override
	public DBugEventBuilder with(String property, Object value) {
		return this;
	}

	@Override
	public DBugEventBuilder with(String property, Supplier<?> value) {
		return this;
	}

	@Override
	public Transaction begin() {
		return Transaction.NONE;
	}

	@Override
	public void occurred() {}
}