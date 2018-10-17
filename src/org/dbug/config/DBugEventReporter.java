package org.dbug.config;

import org.dbug.DBugEvent;
import org.qommons.Transaction;
import org.qommons.config.QommonsConfig;

public interface DBugEventReporter {
	void configure(QommonsConfig config);

	void eventOccurred(DBugEvent<?> event);

	Transaction eventBegun(DBugEvent<?> event);

	void close();
}
