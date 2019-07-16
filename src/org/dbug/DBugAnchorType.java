package org.dbug;

import org.qommons.collect.QuickSet.QuickMap;

public interface DBugAnchorType<T> {
	String getSchema();
	Class<T> getType();

	QuickMap<String, DBugParameterType<T, ?>> getStaticFields();
	QuickMap<String, DBugParameterType<T, ?>> getDynamicFields();
	QuickMap<String, DBugEventType<T>> getEventTypes();

	DBugAnchorBuilder<T> debug(T value);
}
