package org.dbug;

import org.qommons.collect.ParameterSet.ParameterMap;

public interface DBugAnchorType<T> {
	String getSchema();
	Class<T> getType();

	ParameterMap<DBugParameterType<T, ?>> getStaticFields();
	ParameterMap<DBugParameterType<T, ?>> getDynamicFields();
	ParameterMap<DBugEventType<T>> getEventTypes();

	DBugAnchorBuilder<T> debug(T value);
}
