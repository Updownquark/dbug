package org.dbug.config;

import java.util.List;

import org.dbug.expression.DBugAntlrExpression;
import org.qommons.IterableUtils;
import org.qommons.collect.ParameterSet.ParameterMap;

public class DBugConfigTemplate {
	private String theID;
	private final String theClassName;
	private final ParameterMap<DBugConfigTemplateVariable> theVariables;
	private final DBugAntlrExpression theCondition;
	private final ParameterMap<DBugEventConfigTemplate> theEvents;
	private final List<DBugEventReporter> theReporters;

	public DBugConfigTemplate(String id, String className, ParameterMap<DBugConfigTemplateVariable> variables,
		DBugAntlrExpression condition, List<DBugEventReporter> reporters, ParameterMap<DBugEventConfigTemplate> events) {
		theID = id;
		theClassName = className;
		theVariables = variables;
		theCondition = condition;
		theReporters = reporters;
		theEvents = events;
	}

	public String getID() {
		return theID;
	}

	public void setID(String id) {
		theID = id;
	}

	public String getClassName() {
		return theClassName;
	}

	public ParameterMap<DBugConfigTemplateVariable> getVariables() {
		return theVariables;
	}

	public DBugAntlrExpression getCondition() {
		return theCondition;
	}

	public ParameterMap<DBugEventConfigTemplate> getEvents() {
		return theEvents;
	}

	public List<DBugEventReporter> getReporters() {
		return theReporters;
	}

	public static class DBugConfigTemplateVariable {
		public final DBugAntlrExpression expression;
		public final boolean cacheable;

		public DBugConfigTemplateVariable(DBugAntlrExpression expression, boolean cacheable) {
			this.expression = expression;
			this.cacheable = cacheable;
		}
	}

	public static class DBugEventConfigTemplate {
		private List<DBugEventReporter> globalReporters;
		public final String eventName;
		public final ParameterMap<DBugAntlrExpression> eventVariables;
		public final DBugAntlrExpression condition;
		public final List<DBugEventReporter> eventReporters;

		public DBugEventConfigTemplate(List<DBugEventReporter> globalReporters, String eventName,
			ParameterMap<DBugAntlrExpression> eventVariables, DBugAntlrExpression condition, List<DBugEventReporter> eventReporters) {
			this.globalReporters = globalReporters;
			this.eventName = eventName;
			this.eventVariables = eventVariables;
			this.condition = condition;
			this.eventReporters = eventReporters;
		}

		public Iterable<DBugEventReporter> getReporters() {
			return IterableUtils.iterable(globalReporters, eventReporters);
		}
	}
}
