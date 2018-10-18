package org.dbug.reporters;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dbug.DBug;
import org.dbug.config.DBugConfigEvent;
import org.dbug.config.SimpleDBugEventReporter;
import org.qommons.Transaction;
import org.qommons.collect.ParameterSet.ParameterMap;
import org.qommons.config.QommonsConfig;

public class SystemPrintReporter implements SimpleDBugEventReporter {
	public static final Pattern PRINT_VAL_REF = Pattern.compile("[$][{](?<name>[a-zA-Z0-9_]+)[}]");

	private final ThreadLocal<Integer> theIndentAmount = ThreadLocal.withInitial(() -> 0);

	private boolean error;
	private String theIndent = "\t";

	@Override
	public void configure(QommonsConfig config) {
		error = config.is("error", false);
		if (config.get("indent") != null)
			theIndent = config.get("indent");
	}

	@Override
	public void eventOccurred(DBugConfigEvent<?> event) {
		List<Object> sequence = new LinkedList<>();
		sequence.add(theIndentAmount.get());
		int printValsIndex = event.getEventConfigValues().keySet().indexOf("printValues");
		if (printValsIndex >= 0) {
			String printStr = (String) event.getEventConfigValues().get(printValsIndex);
			PrintedEventWithSpec spec = PrintedEventWithSpec.parse(theIndentAmount.get(), theIndent, printStr, event);
			DBug.queueAction(() -> (error ? System.err : System.out).println(spec));
		} else {
			StringBuilder str = new StringBuilder();
			indent(str, theIndentAmount.get(), theIndent);
			printAllValues(str, event);
			DBug.queueAction(() -> (error ? System.err : System.out).println(str));
		}
	}

	@Override
	public Transaction eventBegun(DBugConfigEvent<?> event) {
		eventOccurred(event);
		theIndentAmount.set(theIndentAmount.get() + 1);
		return () -> theIndentAmount.set(theIndentAmount.get() - 1);
	}

	private static void indent(StringBuilder str, int amount, String indentStr) {
		for (int i = 0; i < amount; i++)
			str.append(indentStr);
	}

	@Override
	public void close() {}

	static class PrintedEventWithSpec {
		private final int theIndentAmount;
		private final String theIndent;
		private final String theText;
		final List<ReferenceValue> theReferences;

		static PrintedEventWithSpec parse(int indentAmount, String indent, String text, DBugConfigEvent<?> event) {
			PrintedEventWithSpec printed = new PrintedEventWithSpec(indentAmount, indent, text);
			Matcher m = PRINT_VAL_REF.matcher(text);
			int c = 0;
			while (c < text.length() && m.find(c)) {
				ReferenceValue ref = ReferenceValue.parse(event, m);
				if (ref != null)
					printed.theReferences.add(ref);
				c = m.end();
			}
			return printed;
		}

		private PrintedEventWithSpec(int indentAmount, String indent, String text) {
			theIndentAmount = indentAmount;
			theIndent = indent;
			theText = text;
			theReferences = new LinkedList<>();
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			indent(str, theIndentAmount, theIndent);
			for (int i = 0; i < theIndentAmount; i++)
				str.append(theIndent);
			Iterator<ReferenceValue> values = theReferences.iterator();
			int lastValueEnd = 0;
			while (values.hasNext()) {
				ReferenceValue value = values.next();
				str.append(theText, lastValueEnd, value.start);
				str.append(value.value);
				lastValueEnd = value.end;
			}
			str.append(theText, lastValueEnd, theText.length());
			return str.toString();
		}
	}

	static class ReferenceValue {
		final Object value;
		final int start;
		final int end;

		ReferenceValue(Object value, int start, int end) {
			this.value = value;
			this.start = start;
			this.end = end;
		}

		static ReferenceValue parse(DBugConfigEvent<?> event, Matcher m) {
			String varName = m.group("name");
			switch (varName) {
			case "value":
				return new ReferenceValue(event.getAnchor().getValue(), m.start(), m.end());
			case "event":
				return new ReferenceValue(event.getType().getEventName(), m.start(), m.end());
			case "class":
				return new ReferenceValue(event.getAnchor().getType().getType().getSimpleName(), m.start(), m.end());
			case "time":
				return new ReferenceValue(event.getStart(), m.start(), m.end());
			}
			ParameterMap<Object> values = event.getEventConfigValues();
			int index = values.keySet().indexOf(varName);
			if (index < 0) {
				values = event.getEventValues();
				index = values.keySet().indexOf(varName);
			}
			if (index < 0) {
				values = event.getAnchor().getConfigValues();
				index = values.keySet().indexOf(varName);
			}
			if (index < 0) {
				values = event.getAnchor().getDynamicValues();
				index = values.keySet().indexOf(varName);
			}
			if (index < 0) {
				values = event.getAnchor().getStaticValues();
				index = values.keySet().indexOf(varName);
			}
			if (index >= 0)
				return new ReferenceValue(values.get(index), m.start(), m.end());
			return null;
		}
	}

	private static void printAllValues(StringBuilder str, DBugConfigEvent<?> event) {
		printStandardValue(str, event, "class", true);
		str.append(' ');
		printStandardValue(str, event, "time", true);
		str.append(' ');
		printStandardValue(str, event, "event", true);
		str.append(' ');
		for (int i = 0; i < event.getAnchor().getStaticValues().keySet().size(); i++) {
			str.append(' ');
			printValue(str, event.getAnchor().getStaticValues(), i, true);
		}
		for (int i = 0; i < event.getAnchor().getDynamicValues().keySet().size(); i++) {
			str.append(' ');
			printValue(str, event.getAnchor().getDynamicValues(), i, true);
		}
		for (int i = 0; i < event.getAnchor().getConfigValues().keySet().size(); i++) {
			str.append(' ');
			printValue(str, event.getAnchor().getConfigValues(), i, true);
		}
		for (int i = 0; i < event.getEventValues().keySet().size(); i++) {
			str.append(' ');
			printValue(str, event.getEventValues(), i, true);
		}
		for (int i = 0; i < event.getEventConfigValues().keySet().size(); i++) {
			if (event.getEventConfigValues().keySet().get(i).equals("printLabels"))
				continue;
			str.append(' ');
			printValue(str, event.getEventConfigValues(), i, true);
		}
	}

	private static void printValue(StringBuilder str, DBugConfigEvent<?> event, String value, boolean printLabels) {
		if (printStandardValue(str, event, value, printLabels))
			return;
		ParameterMap<Object> values = event.getEventConfigValues();
		int index = values.keySet().indexOf(value);
		if (index < 0) {
			values = event.getEventValues();
			index = values.keySet().indexOf(value);
		}
		if (index < 0) {
			values = event.getAnchor().getConfigValues();
			index = values.keySet().indexOf(value);
		}
		if (index < 0) {
			values = event.getAnchor().getDynamicValues();
			index = values.keySet().indexOf(value);
		}
		if (index < 0) {
			values = event.getAnchor().getStaticValues();
			index = values.keySet().indexOf(value);
		}
		if (index >= 0)
			printValue(str, values, index, printLabels);
		else
			str.append("${").append(value).append('}');
	}

	private static boolean printStandardValue(StringBuilder str, DBugConfigEvent<?> event, String value, boolean printLabels) {
		switch (value) {
		case "event":
			if (printLabels)
				str.append(value).append('=');
			str.append(event.getType().getEventName());
			break;
		case "class":
			if (printLabels)
				str.append(value).append('=');
			str.append(event.getAnchor().getType().getType().getSimpleName());
			break;
		case "time":
			if (printLabels)
				str.append(value).append('=');
			str.append(event.getStart());
			break;
		default:
			return false;
		}
		return true;
	}

	private static void printValue(StringBuilder str, ParameterMap<Object> values, int index, boolean printLabels) {
		if (printLabels)
			str.append(values.keySet().get(index)).append('=');
		str.append(values.get(index));
	}
}
