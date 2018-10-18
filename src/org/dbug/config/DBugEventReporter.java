package org.dbug.config;

import org.dbug.config.DBugConfig.DBugEventConfig;
import org.qommons.Transaction;
import org.qommons.config.QommonsConfig;

public interface DBugEventReporter<AT, ET, A, E> {
	void configure(QommonsConfig config);

	AT compileForAnchorConfig(DBugConfig<?> anchor);
	ET compileForEventType(AT compiledAnchorType, DBugEventConfig<?> event);
	A compileForConfiguredAnchor(AT compiledAnchorType, DBugConfiguredAnchor<?> anchor);
	E compileForEvent(A compiledAnchor, ET compiledEventType, long eventId);

	void eventOccurred(DBugConfigEvent<?> event, A compiledAnchor, E compiledEvent);
	Transaction eventBegun(DBugConfigEvent<?> event, A compiledAnchor, E compiledEvent);

	void close();
}
