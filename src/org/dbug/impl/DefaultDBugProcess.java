package org.dbug.impl;

import java.lang.management.ManagementFactory;
import java.time.Instant;

import org.dbug.DBugProcess;

public class DefaultDBugProcess implements DBugProcess {
	private final Instant theStartTime = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
	private final String theProcessId = ManagementFactory.getRuntimeMXBean().getName();

	@Override
	public Instant getStartTime() {
		return theStartTime;
	}

	@Override
	public String getProcessId() {
		return theProcessId;
	}
}
