package org.dbug.impl;

import java.util.Map;

import org.dbug.DBugAnchorType;
import org.dbug.DBugEventType;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

public class DefaultDBugEventType<T> implements DBugEventType<T> {
	private final DefaultDBugAnchorType<T> theAnchorType;
	private final String theEventName;
	private final int theEventIndex;
	private final QuickMap<String, TypeToken<?>> theFields;

	public DefaultDBugEventType(DefaultDBugAnchorType<T> anchorType, String eventName, int eventIndex, Map<String, TypeToken<?>> fields) {
		theAnchorType = anchorType;
		theEventName = eventName;
		theEventIndex = eventIndex;
		QuickMap<String, TypeToken<?>> fieldsMap = QuickSet.of(fields.keySet()).createMap();
		for (Map.Entry<String, TypeToken<?>> field : fields.entrySet())
			fieldsMap.put(field.getKey(), field.getValue());
		theFields = fieldsMap.unmodifiable();
	}

	@Override
	public DBugAnchorType<T> getAnchorType() {
		return theAnchorType;
	}

	@Override
	public String getEventName() {
		return theEventName;
	}

	@Override
	public int getEventIndex() {
		return theEventIndex;
	}

	@Override
	public QuickMap<String, TypeToken<?>> getEventFields() {
		return theFields;
	}

	@Override
	public int hashCode() {
		return theAnchorType.hashCode() * 7 + theEventName.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof DefaultDBugEventType))
			return false;
		DefaultDBugEventType<?> other = (DefaultDBugEventType<?>) obj;
		return theAnchorType.equals(other.theAnchorType) && theEventName.equals(other.theEventName);
	}

	@Override
	public String toString() {
		return theAnchorType + "." + theEventName + "(" + theFields + ")";
	}
}
