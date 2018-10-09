package org.dbug;

import java.time.Instant;

public interface DBugProcess {
	Instant getStartTime();

	String getProcessId();
}
