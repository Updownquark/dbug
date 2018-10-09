package org.dbug.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.dbug.DBugAnchorTypeBuilder;
import org.dbug.DBugEventType;
import org.dbug.DBugEventTypeBuilder;
import org.dbug.DBugParameterType;
import org.dbug.DBugVariableType;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class DefaultAnchorTypeBuilder<T> implements DBugAnchorTypeBuilder<T> {
	private static final TypeToken<Set<String>> STRING_SET_TYPE = new TypeToken<Set<String>>() {};

	private final Class<?> theType;
	Map<String, DBugParameterType<T, ?>> theValues;
	Map<String, Map<String, TypeToken<?>>> theEvents;

	public DefaultAnchorTypeBuilder(Class<?> type) {
		theType = type;
		theValues = new LinkedHashMap<>();
		theEvents = new LinkedHashMap<>();

		withEvent(DBugEventType.StandardEvents.ANCHOR_ACTIVE.name(),
			eb -> eb.withEventField("active", TypeTokens.get().BOOLEAN).withEventField("field", TypeTokens.get().STRING)
				.withEventField("inactiveFieldValue", TypeTokens.get().of(Object.class)));
		withEvent(DBugEventType.StandardEvents.VALUE_UPDATE.name(),
			eb -> eb.withEventField("field", TypeTokens.get().STRING).withEventField("variables", STRING_SET_TYPE));
	}

	@Override
	public <P> DBugAnchorTypeBuilder<T> withStaticField(String name, TypeToken<P> type, Function<? super T, ? extends P> value) {
		return withField(name, type, value, DBugVariableType.STATIC);
	}

	@Override
	public <P> DBugAnchorTypeBuilder<T> withExternalStaticField(String name, TypeToken<P> type) {
		return withField(name, type, null, DBugVariableType.STATIC);
	}

	@Override
	public <P> DBugAnchorTypeBuilder<T> withDynamicField(String name, TypeToken<P> type, Function<? super T, ? extends P> value) {
		return withField(name, type, value, DBugVariableType.DYNAMIC);
	}

	@Override
	public <P> DBugAnchorTypeBuilder<T> withExternalDynamicField(String name, TypeToken<P> type) {
		return withField(name, type, null, DBugVariableType.DYNAMIC);
	}

	private <P> DBugAnchorTypeBuilder<T> withField(String name, TypeToken<P> type, Function<? super T, ? extends P> value,
		DBugVariableType level) {
		if (name.equals("value"))
			throw new IllegalArgumentException("Field name \"value\" is reserved");
		for (Map.Entry<String, Map<String, TypeToken<?>>> event : theEvents.entrySet()) {
			if (event.getValue().containsKey(name))
				throw new IllegalArgumentException(
					"An anchor field cannot be declared with the same name as an event field: " + event.getKey() + "." + name);
		}
		DBugParameterType<T, ?> previous = theValues.putIfAbsent(name, new DBugParameterType<>(type, value, level));
		if (previous != null) {
			throw new IllegalArgumentException(
				"A field is already named " + theType.getName() + "." + name + ": " + previous.level + " " + previous.type);
		}
		return this;
	}

	@Override
	public DBugAnchorTypeBuilder<T> withEvent(String eventName, Consumer<DBugEventTypeBuilder> eventBuilder) {
		if (theEvents.containsKey(eventName))
			throw new IllegalArgumentException("An event named " + theType + "." + eventName + " has already been declared");
		if (eventBuilder != null) {
			DefaultEventTypeBuilder b = new DefaultEventTypeBuilder(eventName);
			eventBuilder.accept(b);
			theEvents.put(eventName, b.theFields);
		} else
			theEvents.put(eventName, Collections.emptyMap());
		return this;
	}

	private class DefaultEventTypeBuilder implements DBugEventTypeBuilder {
		private final String theEventName;
		final Map<String, TypeToken<?>> theFields;

		DefaultEventTypeBuilder(String eventName) {
			theEventName = eventName;
			theFields = new LinkedHashMap<>();
		}

		@Override
		public <P> DBugEventTypeBuilder withEventField(String name, TypeToken<P> type) {
			if (name.equals("value"))
				throw new IllegalArgumentException("Field name \"value\" is reserved");
			if (theFields.containsKey(name))
				throw new IllegalArgumentException(
					"An event field cannot be declared with the same name as an anchor field: " + theEventName + "." + name);
			TypeToken<?> oldType = theFields.putIfAbsent(name, type);
			if (oldType != null)
				throw new IllegalArgumentException("An event field is already named " + theEventName + "." + name);
			return this;
		}
	}
}
