package org.dbug.config;

import org.dbug.DBugAnchor;
import org.qommons.collect.QuickSet.QuickMap;

public interface DBugConfiguredAnchor<T> extends DBugAnchor<T> {
	DBugConfig<T> getConfig();

	QuickMap<String, Object> getConfigValues();
}
