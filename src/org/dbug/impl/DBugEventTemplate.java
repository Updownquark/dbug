package org.dbug.impl;

import java.time.Instant;

import org.dbug.DBugAnchor;
import org.dbug.DBugEventType;
import org.qommons.collect.ParameterSet.ParameterMap;

public class DBugEventTemplate<T> {
	private final long theEventId;
	private final DefaultDBugAnchor<T> theAnchor;
	private final DefaultDBugEventType<T> theType;
	private final ParameterMap<Object> theEventValues;
	private final Instant theStartTime;
	private Instant theEndTime;

	public DBugEventTemplate(long eventId, DefaultDBugAnchor<T> anchor, DefaultDBugEventType<T> type, ParameterMap<Object> eventValues,
		boolean transactional) {
		theEventId = eventId;
		theAnchor = anchor;
		theType = type;
		theEventValues = eventValues;
		theStartTime = Instant.now();
		if (!transactional)
			theEndTime = theStartTime;
	}

	public long getEventId() {
		return theEventId;
	}

	public DBugAnchor<T> getAnchor() {
		return theAnchor;
	}

	public DBugEventType<T> getType() {
		return theType;
	}

	public ParameterMap<Object> getEventValues() {
		return theEventValues;
	}

	public Object getValue(String fieldName) {
		throw new IllegalStateException("This should not be called");
	}

	public Instant getStart() {
		return theStartTime;
	}

	public Instant getEnd() {
		return theEndTime;
	}

	void close() {
		theEndTime = Instant.now();
	}
}
