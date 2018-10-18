package org.dbug.impl;

import java.time.Instant;

import org.dbug.DBugAnchor;
import org.dbug.DBugEvent;
import org.dbug.DBugEventType;
import org.dbug.DBugProcess;
import org.qommons.collect.ParameterSet.ParameterMap;

public class DBugEventTemplate<A> implements DBugEvent<A> {
	private final DBugProcess theProcess;
	private final long theEventId;
	private final DefaultDBugAnchor<A> theAnchor;
	private final DefaultDBugEventType<A> theType;
	private final ParameterMap<Object> theDynamicValues;
	private final ParameterMap<Object> theEventValues;
	private final Instant theStartTime;
	private Instant theEndTime;

	public DBugEventTemplate(DBugProcess process, long eventId, DefaultDBugAnchor<A> anchor, DefaultDBugEventType<A> type,
		ParameterMap<Object> dynamicValues, ParameterMap<Object> eventValues, boolean transactional) {
		theProcess = process;
		theEventId = eventId;
		theAnchor = anchor;
		theType = type;
		theDynamicValues = dynamicValues;
		theEventValues = eventValues;
		theStartTime = Instant.now();
		if (!transactional)
			theEndTime = theStartTime;
	}

	@Override
	public DBugProcess getProcess() {
		return theProcess;
	}

	@Override
	public long getEventId() {
		return theEventId;
	}

	@Override
	public DBugAnchor<A> getAnchor() {
		return theAnchor;
	}

	@Override
	public DBugEventType<A> getType() {
		return theType;
	}

	@Override
	public ParameterMap<Object> getDynamicValues() {
		return theDynamicValues;
	}

	@Override
	public ParameterMap<Object> getEventValues() {
		return theEventValues;
	}

	@Override
	public Instant getStart() {
		return theStartTime;
	}

	@Override
	public Instant getEnd() {
		return theEndTime;
	}

	void close() {
		theEndTime = Instant.now();
	}
}
