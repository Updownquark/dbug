package org.dbug.impl;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.dbug.DBugAnchor;
import org.dbug.DBugAnchorBuilder;
import org.dbug.DBugAnchorType;
import org.dbug.DBugEventBuilder;
import org.dbug.DBugParameterType;
import org.qommons.collect.ParameterSet.ParameterMap;

public class DefaultAnchorBuilder<T> implements DBugAnchorBuilder<T> {
	private final DefaultDBug theDebug;
	private final DefaultDBugAnchorType<T> theType;
	private final T theValue;

	private final ParameterMap<Object> theStaticValues;
	private final ParameterMap<Object> theDynamicValues;
	private Set<String> theExternallySpecifiedParameters;

	public DefaultAnchorBuilder(DefaultDBug debug, DefaultDBugAnchorType<T> type, T value) {
		theDebug = debug;
		theType = type;
		theValue = value;

		theStaticValues = theType.getStaticFields().keySet().createMap();
		for (int i = 0; i < theStaticValues.keySet().size(); i++) {
			Function<? super T, ?> producer = type.getStaticFields().get(i).producer;
			if (producer != null)
				theStaticValues.put(i, type.getStaticFields().get(i).producer.apply(value));
			else {
				if (theExternallySpecifiedParameters == null)
					theExternallySpecifiedParameters = new HashSet<>();
				theExternallySpecifiedParameters.add(theStaticValues.keySet().get(i));
			}
		}
		theDynamicValues = theType.getDynamicFields().keySet().createMap(index -> {
			return type.getDynamicFields().get(index).producer.apply(value);
		});
		for (int i = 0; i < theDynamicValues.keySet().size(); i++) {
			if (theType.getDynamicFields().get(i).producer == null) {
				if (theExternallySpecifiedParameters == null)
					theExternallySpecifiedParameters = new HashSet<>();
				theExternallySpecifiedParameters.add(theDynamicValues.keySet().get(i));
			}
		}
	}

	@Override
	public DBugAnchorBuilder<T> with(String parameter, Object value) {
		int index = theStaticValues.keySet().indexOf(parameter);
		if (index >= 0) {
			DBugParameterType<T, ?> paramType = theType.getStaticFields().get(index);
			if (paramType.producer != null)
				throw new IllegalArgumentException("Parameter " + theType + "." + parameter + " is not externally-specified");
			theStaticValues.put(index, value);
			theExternallySpecifiedParameters.remove(parameter);
		} else {
			index = theDynamicValues.keySet().indexOf(parameter);
			if (index < 0)
				throw new IllegalArgumentException("Unrecognized parameter " + theType + "." + parameter);
			theDynamicValues.put(index, value);
			if (theExternallySpecifiedParameters == null)
				theExternallySpecifiedParameters = new HashSet<>();
			theExternallySpecifiedParameters.remove(parameter);
		}
		return this;
	}

	@Override
	public DBugAnchor<T> build() {
		if (theExternallySpecifiedParameters != null && !theExternallySpecifiedParameters.isEmpty()) {
			throw new IllegalStateException(
				"External parameters " + theExternallySpecifiedParameters + " were not specified for anchor of type " + theType);
		}
		return theDebug.buildAnchor(theType, theValue,
			() -> new DefaultDBugAnchor<>(theDebug, theType, theValue, theStaticValues.unmodifiable(), theDynamicValues));
	}

	@Override
	public DBugAnchor<T> buildIfSatisfied() {
		if (theExternallySpecifiedParameters != null && !theExternallySpecifiedParameters.isEmpty()) {
			DBugAnchor<T> found = theDebug.getPreBuiltAnchor(theType, theValue);
			if (found != null)
				return found;
			else
				return new PlaceHolderAnchor<>(theDebug, theType, theValue);
		} else
			return theDebug.buildAnchor(theType, theValue,
				() -> new DefaultDBugAnchor<>(theDebug, theType, theValue, theStaticValues.unmodifiable(), theDynamicValues));
	}

	private static class PlaceHolderAnchor<T> implements DBugAnchor<T> {
		private final DefaultDBug theDebug;
		private final DBugAnchorType<T> theType;
		private final T theValue;
		private DefaultDBugAnchor<T> anchor;

		PlaceHolderAnchor(DefaultDBug debug, DBugAnchorType<T> type, T value) {
			theDebug = debug;
			theType = type;
			theValue = value;
		}

		private boolean tryRetrieve() {
			if (anchor == null)
				anchor = theDebug.getPreBuiltAnchor(theType, theValue);
			return anchor != null;
		}

		@Override
		public DBugAnchorType<T> getType() {
			return theType;
		}

		@Override
		public T getValue() {
			return theValue;
		}

		@Override
		public boolean isActive() {
			if (!tryRetrieve())
				return false;
			return anchor.isActive();
		}

		@Override
		public ParameterMap<Object> getStaticValues() {
			if (!tryRetrieve())
				return theType.getStaticFields().keySet().createDynamicMap(index -> {
					if (!tryRetrieve())
						return null;
					return anchor.getStaticValues().get(index);
				});
			return anchor.getStaticValues();
		}

		@Override
		public ParameterMap<Object> getDynamicValues() {
			if (!tryRetrieve())
				return theType.getDynamicFields().keySet().createDynamicMap(index -> {
					if (!tryRetrieve())
						return null;
					return anchor.getDynamicValues().get(index);
				});
			return anchor.getDynamicValues();
		}

		@Override
		public <P> P setDynamicValue(String property, P value) {
			if (!tryRetrieve())
				throw new IllegalStateException("This anchor is not available");
			return anchor.setDynamicValue(property, value);
		}

		@Override
		public <P> DBugAnchor<T> modifyDynamicValue(String property, Function<? super P, ? extends P> map) {
			if (!tryRetrieve())
				throw new IllegalStateException("This anchor is not available");
			return anchor.modifyDynamicValue(property, map);
		}

		@Override
		public DBugEventBuilder event(String eventName) {
			if (!tryRetrieve())
				return DoNothingEventBuilder.INSTANCE;
			return anchor.event(eventName);
		}
	}
}
