package org.dbug.config;

import org.dbug.DBugEvent;
import org.dbug.config.DBugConfig.DBugEventConfig;
import org.qommons.collect.QuickSet.QuickMap;

public interface DBugConfigEvent<A> extends DBugEvent<A> {
	DBugConfig<A> getConfig();

	DBugEventConfig<A> getEventConfig();

	@Override
	DBugConfiguredAnchor<A> getAnchor();

	QuickMap<String, Object> getEventConfigValues();
}
