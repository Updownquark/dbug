package org.dbug.reporters;

import org.dbug.DBugEvent;
import org.dbug.config.DBugEventReporter;
import org.qommons.BreakpointHere;
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
	public void eventBegun(DBugEvent<?> event) {
		switch (theType) {
		case BEGIN:
		case BOTH:
			BreakpointHere.breakpoint();
			break;
		default:
		}
	}

	@Override
	public void eventEnded(DBugEvent<?> event) {
		switch (theType) {
		case END:
		case BOTH:
			BreakpointHere.breakpoint();
			break;
		default:
		}
	}

	@Override
	public void close() {}
}
