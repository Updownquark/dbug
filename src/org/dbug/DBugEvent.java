package org.dbug;

import java.time.Instant;

import org.dbug.config.DBugConfig;
import org.dbug.config.DBugConfig.DBugEventConfig;
import org.dbug.config.DBugConfiguredAnchor;
import org.qommons.collect.ParameterSet.ParameterMap;

public interface DBugEvent<T> {
	DBugProcess getProcess();

	DBugConfig<T> getConfig();

	DBugEventConfig<T> getEventConfig();

	long getEventId();

	DBugConfiguredAnchor<T> getAnchor();

	DBugEventType<T> getType();

	ParameterMap<Object> getDynamicValues();

	ParameterMap<Object> getEventValues();

	ParameterMap<Object> getEventConfigValues();

	Instant getStart();

	Instant getEnd();
}
