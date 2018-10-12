package org.dbug.config;

import java.util.BitSet;
import java.util.List;

import org.dbug.DBugAnchorType;
import org.dbug.DBugEventType;
import org.dbug.config.DBugConfigTemplate.DBugConfigTemplateVariable;
import org.dbug.config.DBugConfigTemplate.DBugEventConfigTemplate;
import org.dbug.expression.Expression;
import org.dbug.impl.DefaultDBugEventType;
import org.qommons.collect.ParameterSet.ParameterMap;

public class DBugConfig<A> {
	private final DBugConfigTemplate theTemplate;
	private final DBugAnchorType<A> theAnchorType;
	private final ParameterMap<DBugConfigVariable<A, ?>> theVariables;
	private final DBugConfigVariable<A, Boolean> theCondition;
	private final ParameterMap<DBugEventConfig<A>> theEvents;

	public DBugConfig(DBugConfigTemplate template, DBugAnchorType<A> anchorType, ParameterMap<DBugConfigVariable<A, ?>> variables,
		DBugConfigVariable<A, Boolean> condition, ParameterMap<DBugEventConfig<A>> events) {
		theTemplate = template;
		theAnchorType = anchorType;
		theVariables = variables;
		theCondition = condition;
		theEvents = events;
	}

	public DBugConfigTemplate getTemplate() {
		return theTemplate;
	}

	public DBugAnchorType<A> getAnchorType() {
		return theAnchorType;
	}

	public ParameterMap<DBugConfigVariable<A, ?>> getVariables() {
		return theVariables;
	}

	public DBugConfigVariable<A, Boolean> getCondition() {
		return theCondition;
	}

	public ParameterMap<DBugEventConfig<A>> getEvents() {
		return theEvents;
	}

	public List<DBugEventReporter> getReporters() {
		return theTemplate.getReporters();
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
		return theVariables.equals(other.theVariables) && theCondition.equals(other.theCondition) && theEvents.equals(other.theEvents);
	}

	public static class DBugConfigVariable<A, T> {
		public final DBugConfigTemplateVariable template;
		public final Expression<A, T> expression;
		public final BitSet dynamicDependencies;
		public final BitSet configVariableDependencies;

		public DBugConfigVariable(DBugConfigTemplateVariable template, Expression<A, T> expression, BitSet dynamicDepends,
			BitSet configVarDepends) {
			this.template = template;
			this.expression = expression;
			dynamicDependencies = dynamicDepends;
			configVariableDependencies = configVarDepends;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof DBugConfigVariable))
				return false;
			DBugConfigVariable<?, ?> other = (DBugConfigVariable<?, ?>) obj;
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
		public final ParameterMap<DBugEventVariable<A, ?>> eventVariables;
		public final DBugEventVariable<A, Boolean> condition;

		public DBugEventConfig(DBugEventConfigTemplate template, DefaultDBugEventType<A> eventType,
			ParameterMap<DBugEventVariable<A, ?>> eventVariables, DBugEventVariable<A, Boolean> condition) {
			this.template = template;
			this.eventType = eventType;
			this.eventVariables = eventVariables;
			this.condition = condition;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof DBugEventConfig))
				return false;
			DBugEventConfig<?> other = (DBugEventConfig<?>) obj;
			return eventType == other.eventType && eventVariables.equals(other.eventVariables) && condition.equals(other.condition);
		}
	}

	public static class DBugEventVariable<A, T> {
		public final DBugEventType<A> eventType;
		public final String varName;
		public final int varIndex;
		public final Expression<A, T> expression;
		public final BitSet eventVariableDependencies;

		public DBugEventVariable(DBugEventType<A> eventType, String varName, int varIndex, Expression<A, T> expression,
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
