package org.dbug.config;

import java.util.List;

import org.dbug.expression.DBugAntlrExpression;
import org.qommons.collect.QuickSet.QuickMap;

public class DBugConfigTemplate {
	private final String theID;
	private final String theSchema;
	private final String theClassName;
	private final QuickMap<String, DBugConfigTemplateValue> theValues;
	private final DBugAntlrExpression theCondition;
	private final QuickMap<String, DBugEventConfigTemplate> theEvents;
	private final List<DBugEventReporter<?, ?, ?, ?, ?>> theReporters;

	public DBugConfigTemplate(String id, String schema, String className, QuickMap<String, DBugConfigTemplateValue> values,
		DBugAntlrExpression condition, List<DBugEventReporter<?, ?, ?, ?, ?>> reporters, QuickMap<String, DBugEventConfigTemplate> events) {
		theID = id;
		theSchema = schema;
		theClassName = className;
		theValues = values;
		theCondition = condition;
		theReporters = reporters;
		theEvents = events;
	}

	public String getID() {
		return theID;
	}

	public String getSchema() {
		return theSchema;
	}

	public String getClassName() {
		return theClassName;
	}

	public QuickMap<String, DBugConfigTemplateValue> getValues() {
		return theValues;
	}

	public DBugAntlrExpression getCondition() {
		return theCondition;
	}

	public QuickMap<String, DBugEventConfigTemplate> getEvents() {
		return theEvents;
	}

	public List<DBugEventReporter<?, ?, ?, ?, ?>> getReporters() {
		return theReporters;
	}

	public static class DBugConfigTemplateValue {
		public final String varName;
		public final DBugAntlrExpression expression;
		public final boolean cacheable;

		public DBugConfigTemplateValue(String varName, DBugAntlrExpression expression, boolean cacheable) {
			this.varName = varName;
			this.expression = expression;
			this.cacheable = cacheable;
		}

		@Override
		public String toString() {
			return varName;
		}
	}

	public static class DBugEventConfigTemplate {
		private List<DBugEventReporter<?, ?, ?, ?, ?>> globalReporters;
		public final String eventName;
		public final QuickMap<String, DBugAntlrExpression> eventVariables;
		public final DBugAntlrExpression condition;
		public final List<DBugEventReporter<?, ?, ?, ?, ?>> eventReporters;
		private final DBugConfigTemplate[] template;

		public DBugEventConfigTemplate(List<DBugEventReporter<?, ?, ?, ?, ?>> globalReporters, String eventName,
			QuickMap<String, DBugAntlrExpression> eventVariables, DBugAntlrExpression condition,
			List<DBugEventReporter<?, ?, ?, ?, ?>> eventReporters, DBugConfigTemplate[] template) {
			this.globalReporters = globalReporters;
			this.eventName = eventName;
			this.eventVariables = eventVariables;
			this.condition = condition;
			this.eventReporters = eventReporters;
			this.template = template;
		}

		public DBugConfigTemplate getTemplate() {
			return template[0];
		}

		public int getReporterCount() {
			return globalReporters.size() + eventReporters.size();
		}

		public DBugEventReporter<?, ?, ?, ?, ?> getReporter(int index) {
			int erIndex = index - globalReporters.size();
			if (erIndex < 0)
				return globalReporters.get(index);
			else
				return eventReporters.get(erIndex);
		}
	}
}
