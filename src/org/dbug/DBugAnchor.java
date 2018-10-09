package org.dbug;

import java.util.function.Function;

import org.qommons.collect.ParameterSet.ParameterMap;

/** This interface allows a programmer to create data that will be used to debug and visualize program flow within a program */
public interface DBugAnchor<T> {
	DBugAnchorType<T> getType();

	T getValue();

	boolean isActive();

	ParameterMap<Object> getStaticValues();

	ParameterMap<Object> getDynamicValues();

	<P> P setDynamicValue(String property, P value);

	<P> DBugAnchor<T> modifyDynamicValue(String property, Function<? super P, ? extends P> map);

	DBugEventBuilder event(String eventName);
}
