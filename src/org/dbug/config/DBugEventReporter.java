package org.dbug.config;

import org.dbug.DBugEvent;
import org.qommons.config.QommonsConfig;

public interface DBugEventReporter {
	void configure(QommonsConfig config);

	void eventOccurred(DBugEvent<?> event);

	void eventBegun(DBugEvent<?> event);

	void eventEnded(DBugEvent<?> event);

	void close();
}
