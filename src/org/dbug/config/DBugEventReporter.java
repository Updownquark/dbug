package org.dbug.config;

import org.dbug.DBugAnchor;
import org.dbug.DBugEvent;
import org.dbug.config.DBugConfig.DBugEventConfig;
import org.qommons.Transaction;
import org.qommons.config.QommonsConfig;

public interface DBugEventReporter<C, ET, A, CA, E> {
	void configure(QommonsConfig config);

	C compileForAnchorConfig(DBugConfig<?> anchor);
	ET compileForEventConfig(C compiledConfig, DBugEventConfig<?> event);
	A compileForAnchor(DBugAnchor<?> anchor);
	CA compileForConfiguredAnchor(A compiledAnchor, C compiledConfig, DBugConfiguredAnchor<?> anchor);
	E compileForEvent(CA compiledAnchor, ET compiledEventType, DBugEvent<?> event);

	void eventOccurred(DBugConfigEvent<?> event, CA compiledAnchor, E compiledEvent);
	Transaction eventBegun(DBugConfigEvent<?> event, CA compiledAnchor, E compiledEvent);

	void close();
}
