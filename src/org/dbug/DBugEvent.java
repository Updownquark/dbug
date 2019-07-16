package org.dbug;

import java.time.Instant;

import org.qommons.collect.QuickSet.QuickMap;

public interface DBugEvent<A> {
	DBugProcess getProcess();

	long getEventId();

	DBugEventType<A> getType();

	DBugAnchor<A> getAnchor();

	QuickMap<String, Object> getDynamicValues();
	QuickMap<String, Object> getEventValues();

	Instant getStart();
	Instant getEnd();
}
