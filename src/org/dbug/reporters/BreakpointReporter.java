package org.dbug.reporters;

import org.dbug.DBugEvent;
import org.dbug.config.DBugEventReporter;
import org.qommons.BreakpointHere;
import org.qommons.Transaction;
import org.qommons.config.QommonsConfig;

public class BreakpointReporter implements DBugEventReporter {
	private static enum Type {
		BEGIN, END, BOTH;
	}

	private Type theType;

	@Override
	public void configure(QommonsConfig config) {
		String type = config.get("type");
		if (type == null)
			theType = Type.BEGIN;
		else if (Type.BOTH.name().equalsIgnoreCase(type))
			theType = Type.BOTH;
		else if (Type.END.name().equalsIgnoreCase(type))
			theType = Type.END;
		else
			theType = Type.BEGIN;
	}

	@Override
	public void eventOccurred(DBugEvent<?> event) {
		BreakpointHere.breakpoint();
	}

	@Override
	public Transaction eventBegun(DBugEvent<?> event) {
		switch (theType) {
		case BEGIN:
			BreakpointHere.breakpoint();
			break;
		case END:
			return () -> BreakpointHere.breakpoint();
		case BOTH:
			BreakpointHere.breakpoint();
			return () -> BreakpointHere.breakpoint();
		}
		throw new IllegalStateException("Unrecognized breakpoint type " + theType);
	}

	@Override
	public void close() {}
}
