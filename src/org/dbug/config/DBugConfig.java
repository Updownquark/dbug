package org.dbug.config;

import java.util.BitSet;
import java.util.List;

import org.dbug.DBugAnchorType;
import org.dbug.DBugEventType;
import org.dbug.config.DBugConfigTemplate.DBugConfigTemplateValue;
import org.dbug.config.DBugConfigTemplate.DBugEventConfigTemplate;
import org.dbug.expression.Expression;
import org.dbug.impl.DefaultDBugEventType;
import org.qommons.collect.ParameterSet.ParameterMap;

public class DBugConfig<A> {
	private final DBugConfigTemplate theTemplate;
	private final DBugAnchorType<A> theAnchorType;
	private final ParameterMap<DBugConfigValue<A, ?>> theValues;
	private final DBugConfigValue<A, Boolean> theCondition;
	private final ParameterMap<List<DBugEventConfig<A>>> theEvents;
	private final Object[] theReporterCompiledAnchors;

	public DBugConfig(DBugConfigTemplate template, DBugAnchorType<A> anchorType, ParameterMap<DBugConfigValue<A, ?>> values,
		DBugConfigValue<A, Boolean> condition, ParameterMap<List<DBugEventConfig<A>>> events) {
		theTemplate = template;
		theAnchorType = anchorType;
		theValues = values;
		theCondition = condition;
		theEvents = events;
		theReporterCompiledAnchors = new Object[template.getReporters().size()];
	}

	public DBugConfigTemplate getTemplate() {
		return theTemplate;
	}

	public DBugAnchorType<A> getAnchorType() {
		return theAnchorType;
	}

	public ParameterMap<DBugConfigValue<A, ?>> getValues() {
		return theValues;
	}

	public DBugConfigValue<A, Boolean> getCondition() {
		return theCondition;
	}

	public ParameterMap<List<DBugEventConfig<A>>> getEvents() {
		return theEvents;
	}

	public List<DBugEventReporter<?, ?, ?, ?, ?>> getReporters() {
		return theTemplate.getReporters();
	}

	public Object getReporterCompiledAnchor(int index) {
		Object anchor = theReporterCompiledAnchors[index];
		if (anchor == null)
			theReporterCompiledAnchors[index] = anchor = theTemplate.getReporters().get(index).compileForAnchorConfig(this);
		return anchor;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof DBugConfig))
			return false;
		DBugConfig<?> other = (DBugConfig<?>) obj;
		if (theAnchorType != other.theAnchorType)
			return false;
		return theValues.equals(other.theValues) && theCondition.equals(other.theCondition) && theEvents.equals(other.theEvents);
	}

	public static class DBugConfigValue<A, T> {
		public final DBugConfigTemplateValue template;
		public final Expression<A, T> expression;
		public final BitSet dynamicDependencies;
		public final BitSet configValueDependencies;

		public DBugConfigValue(DBugConfigTemplateValue template, Expression<A, T> expression, BitSet dynamicDepends,
			BitSet configVarDepends) {
			this.template = template;
			this.expression = expression;
			dynamicDependencies = dynamicDepends;
			configValueDependencies = configVarDepends;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof DBugConfigValue))
				return false;
			DBugConfigValue<?, ?> other = (DBugConfigValue<?, ?>) obj;
			return expression.equals(other.expression);
		}

		@Override
		public String toString() {
			return template == null ? "condition" : template.toString();
		}
	}

	public static class DBugEventConfig<A> {
		public final DBugEventConfigTemplate template;
		public final DefaultDBugEventType<A> eventType;
		public final ParameterMap<DBugEventValue<A, ?>> eventValues;
		public final DBugEventValue<A, Boolean> condition;
		private final DBugConfig<A>[] theConfig;
		private final Object[] theEventReporterCompiledAnchors;
		private final Object[] theReporterCompiledEvents;

		public DBugEventConfig(DBugEventConfigTemplate template, DefaultDBugEventType<A> eventType,
			ParameterMap<DBugEventValue<A, ?>> eventValues, DBugEventValue<A, Boolean> condition, DBugConfig<A>[] config) {
			this.template = template;
			this.eventType = eventType;
			this.eventValues = eventValues;
			this.condition = condition;
			theConfig = config;
			theEventReporterCompiledAnchors = new Object[template.getReporterCount() - template.getTemplate().getReporters().size()];
			theReporterCompiledEvents = new Object[template.getReporterCount()];
		}

		public DBugConfig<A> getConfig() {
			return theConfig[0];
		}

		public Object getReporterCompiledAnchor(int index) {
			Object compiledAnchor;
			int evtRIndex = index - template.getTemplate().getReporters().size();
			if (evtRIndex < 0)
				compiledAnchor = theConfig[0].getReporterCompiledAnchor(index);
			else {
				compiledAnchor = theEventReporterCompiledAnchors[evtRIndex];
				if (compiledAnchor == null)
					theEventReporterCompiledAnchors[evtRIndex] = compiledAnchor = template.getReporter(index)
						.compileForAnchorConfig(theConfig[0]);
			}
			return compiledAnchor;
		}

		public Object getReporterCompiledEvent(int index) {
			Object event = theReporterCompiledEvents[index];
			if (event == null) {
				Object compiledAnchor = getReporterCompiledAnchor(index);
				theReporterCompiledEvents[index] = event = ((DBugEventReporter<Object, ?, ?, ?, ?>) template.getReporter(index))
					.compileForEventConfig(compiledAnchor, this);
			}
			return event;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof DBugEventConfig))
				return false;
			DBugEventConfig<?> other = (DBugEventConfig<?>) obj;
			return eventType == other.eventType && eventValues.equals(other.eventValues) && condition.equals(other.condition);
		}
	}

	public static class DBugEventValue<A, T> {
		public final DBugEventType<A> eventType;
		public final String varName;
		public final int varIndex;
		public final Expression<A, T> expression;
		public final BitSet eventVariableDependencies;

		public DBugEventValue(DBugEventType<A> eventType, String varName, int varIndex, Expression<A, T> expression,
			BitSet eventVariableDependencies) {
			this.eventType = eventType;
			this.varName = varName;
			this.varIndex = varIndex;
			this.expression = expression;
			this.eventVariableDependencies = eventVariableDependencies;
		}

		@Override
		public String toString() {
			return eventType.getEventName() + "." + (varName == null ? "condition" : varName);
		}
	}
}
