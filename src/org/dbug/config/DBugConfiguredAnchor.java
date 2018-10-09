package org.dbug.config;

import org.dbug.DBugAnchor;
import org.qommons.collect.ParameterSet.ParameterMap;

public interface DBugConfiguredAnchor<T> extends DBugAnchor<T> {
	DBugConfig<T> getConfig();

	ParameterMap<Object> getConfigValues();
}
