package org.dbug;

import java.util.function.Consumer;

public interface DBugImplementation {
	<T> DBugAnchorType<T> declare(String schema, Class<T> type, Consumer<DBugAnchorTypeBuilder<T>> builder);

	void queueAction(Runnable action);
}
