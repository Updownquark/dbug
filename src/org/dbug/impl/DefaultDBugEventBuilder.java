package org.dbug.impl;

import java.util.BitSet;
import java.util.function.Supplier;

import org.dbug.DBugEventBuilder;
import org.qommons.Transaction;
import org.qommons.collect.ParameterSet.ParameterMap;

public class DefaultDBugEventBuilder<T> implements DBugEventBuilder {
	private final DefaultDBug theDBug;
	private final DefaultDBugAnchor<T> theAnchor;
	private final DefaultDBugEventType<T> theType;
	private final ParameterMap<Object> theEventProperties;
	private final BitSet theSpecifiedParameters;

	public DefaultDBugEventBuilder(DefaultDBug dBug, DefaultDBugAnchor<T> anchor, DefaultDBugEventType<T> type) {
		theDBug = dBug;
		theAnchor = anchor;
		theType = type;
		theEventProperties = type.getEventFields().keySet().createMap();
		theSpecifiedParameters = new BitSet();
	}

	@Override
	public DBugEventBuilder with(String property, Object value) {
		int index = theEventProperties.keyIndex(property);
		theSpecifiedParameters.set(index);
		theEventProperties.put(index, value);
		return this;
	}

	@Override
	public DBugEventBuilder with(String property, Supplier<?> value) {
		if (value == null)
			return with(property, (Object) value);
		int index = theEventProperties.keyIndex(property);
		theSpecifiedParameters.set(index);
		theEventProperties.put(index, value.get());
		return this;
	}

	private void assertComplete() {
		int unspecified = theSpecifiedParameters.nextClearBit(0);
		if (unspecified < theEventProperties.keySet().size())
			throw new IllegalStateException("Event parameter " + theEventProperties.keySet().get(unspecified) + " has not been specified");
	}

	@Override
	public Transaction begin() {
		assertComplete();
		return theAnchor.beginEvent(//
			new DBugEventTemplate<>(theDBug.getNextEventId(), theAnchor, theType, theEventProperties.unmodifiable(), true));
	}

	@Override
	public void occurred() {
		assertComplete();
		theAnchor.eventOccurred(//
			new DBugEventTemplate<>(theDBug.getNextEventId(), theAnchor, theType, theEventProperties.unmodifiable(), false));
	}
}
