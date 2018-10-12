package org.dbug.impl;

import java.util.function.Function;

import org.dbug.DBugAnchor;
import org.dbug.DBugAnchorType;
import org.dbug.DBugEventBuilder;
import org.qommons.collect.ParameterSet.ParameterMap;

class PlaceHolderAnchor<T> implements DBugAnchor<T> {
	private final DefaultDBug theDebug;
	private final DefaultDBugAnchorType<T> theType;
	private final T theValue;
	private DBugAnchor<T> anchor;

	PlaceHolderAnchor(DefaultDBug debug, DefaultDBugAnchorType<T> type, T value) {
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