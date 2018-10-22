package org.dbug.config;

import org.dbug.DBugAnchor;
import org.dbug.DBugEvent;
import org.dbug.config.DBugConfig.DBugEventConfig;
import org.qommons.Transaction;

/** A {@link DBugEventReporter} that does no pre-compiling */
public interface SimpleDBugEventReporter extends DBugEventReporter<Void, Void, Void, Void, Void> {
	@Override
	default Void compileForAnchorConfig(DBugConfig<?> anchor) {
		return null;
	}

	@Override
	default Void compileForEventConfig(Void compiledAnchorType, DBugEventConfig<?> event) {
		return null;
	}

	@Override
	default Void compileForAnchor(DBugAnchor<?> anchor) {
		return null;
	}

	@Override
	default Void compileForConfiguredAnchor(Void compiledAnchor, Void compiledConfig, DBugConfiguredAnchor<?> anchor) {
		return null;
	}

	@Override
	default Void compileForEvent(Void compiledAnchor, Void compiledEventType, DBugEvent<?> event) {
		return null;
	}

	@Override
	default void eventOccurred(DBugConfigEvent<?> event, Void compiledAnchor, Void compiledEvent) {
		eventOccurred(event);
	}

	@Override
	default Transaction eventBegun(DBugConfigEvent<?> event, Void compiledAnchor, Void compiledEvent) {
		return eventBegun(event);
	}

	void eventOccurred(DBugConfigEvent<?> event);

	Transaction eventBegun(DBugConfigEvent<?> event);
}
