package org.dbug;

import org.qommons.collect.ParameterSet.ParameterMap;

import com.google.common.reflect.TypeToken;

public interface DBugEventType<T> {
	public static enum StandardEvents {
		ANCHOR_ACTIVE, VALUE_UPDATE
	}

	DBugAnchorType<T> getAnchorType();

	String getEventName();

	int getEventIndex();

	ParameterMap<TypeToken<?>> getEventFields();
}
