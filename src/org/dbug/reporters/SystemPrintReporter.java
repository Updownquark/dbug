package org.dbug.reporters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dbug.DBug;
import org.dbug.DBugEvent;
import org.dbug.config.DBugEventReporter;
import org.qommons.collect.ParameterSet.ParameterMap;
import org.qommons.config.QommonsConfig;

public class SystemPrintReporter implements DBugEventReporter {
	private final Pattern PRINT_VAL_REF = Pattern.compile("[$][{](?<name>[a-zA-Z0-9_]+)[}]");

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
	public void eventOccurred(DBugEvent<?> event) {
		StringBuilder str = new StringBuilder();
		indent(str, theIndentAmount.get());
		int printValsIndex = event.getEventConfigValues().keySet().indexOf("printValues");
		if (printValsIndex >= 0) {
			String printStr = (String) event.getEventConfigValues().get(printValsIndex);
			Matcher m = PRINT_VAL_REF.matcher(printStr);
			int c = 0;
			while (c < printStr.length() && m.find(c)) {
				int found = m.start();
				str.append(printStr, c, found);
				printValue(str, event, m.group("name"), false);
				c = m.end();
			}
			str.append(printStr, c, printStr.length());
		} else
			printAllValues(str, event);
		DBug.queueAction(() -> (error ? System.err : System.out).println(str));
	}

	@Override
	public void eventBegun(DBugEvent<?> event) {
		eventOccurred(event);
		theIndentAmount.set(theIndentAmount.get() + 1);
	}

	@Override
	public void eventEnded(DBugEvent<?> event) {
		theIndentAmount.set(theIndentAmount.get() - 1);
	}

	private void indent(StringBuilder str, int amount) {
		for (int i = 0; i < amount; i++)
			str.append(theIndent);
	}

	@Override
	public void close() {}

	private static void printAllValues(StringBuilder str, DBugEvent<?> event) {
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

	private static void printValue(StringBuilder str, DBugEvent<?> event, String value, boolean printLabels) {
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

	private static boolean printStandardValue(StringBuilder str, DBugEvent<?> event, String value, boolean printLabels) {
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
