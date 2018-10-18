package org.dbug.config;

import org.dbug.DBugEvent;
import org.dbug.config.DBugConfig.DBugEventConfig;
import org.qommons.collect.ParameterSet.ParameterMap;

public interface DBugConfigEvent<A> extends DBugEvent<A> {
	DBugConfig<A> getConfig();

	DBugEventConfig<A> getEventConfig();

	@Override
	DBugConfiguredAnchor<A> getAnchor();

	ParameterMap<Object> getEventConfigValues();
}
