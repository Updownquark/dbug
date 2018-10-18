package org.dbug;

import java.time.Instant;

import org.qommons.collect.ParameterSet.ParameterMap;

public interface DBugEvent<A> {
	DBugProcess getProcess();

	long getEventId();

	DBugEventType<A> getType();

	DBugAnchor<A> getAnchor();

	ParameterMap<Object> getDynamicValues();
	ParameterMap<Object> getEventValues();

	Instant getStart();
	Instant getEnd();
}
