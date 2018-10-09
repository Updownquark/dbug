package org.dbug;

import com.google.common.reflect.TypeToken;

public interface DBugEventTypeBuilder {
	<P> DBugEventTypeBuilder withEventField(String name, TypeToken<P> type);
}
