package org.dbug.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.dbug.DBugAnchor;
import org.dbug.DBugAnchorBuilder;
import org.dbug.DBugAnchorType;
import org.dbug.DBugEventBuilder;
import org.dbug.DBugEventType;
import org.dbug.DBugParameterType;
import org.dbug.DBugVariableType;
import org.dbug.config.DBugConfig;
import org.dbug.config.DBugConfig.DBugConfigVariable;
import org.dbug.config.DBugConfig.DBugEventVariable;
import org.dbug.config.DBugConfigTemplate;
import org.dbug.config.DBugConfigTemplate.DBugEventConfigTemplate;
import org.dbug.expression.DBugParseEnv;
import org.dbug.expression.DBugParseException;
import org.dbug.expression.Expression;
import org.dbug.expression.ExpressionParser;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.collect.ParameterSet;
import org.qommons.collect.ParameterSet.ParameterMap;

import com.google.common.reflect.TypeToken;

public class DefaultDBugAnchorType<A> implements DBugAnchorType<A> {
	private final DefaultDBug theDebug;
	private final Class<A> theType;
	final Class<?> theBuilderClass;
	private final ParameterMap<DBugParameterType<A, ?>> theStaticValues;
	private final ParameterMap<DBugParameterType<A, ?>> theDynamicValues;
	private final ParameterMap<DefaultDBugEventType<A>> theEventTypes;
	final int theActiveEventIndex;
	final int theUpdateEventIndex;

	private final List<DBugConfig<A>> theConfigs;
	private InactiveAnchor<A> theInactive;

	public DefaultDBugAnchorType(DefaultDBug debug, Class<A> type, Class<?> builderClass, Map<String, DBugParameterType<A, ?>> valueTypes,
		Map<String, Map<String, TypeToken<?>>> eventTypes) {
		theDebug = debug;
		theType = type;
		theBuilderClass = builderClass;
		List<String> staticValueNames = new LinkedList<>();
		List<String> dynamicValueNames = new LinkedList<>();
		for (Map.Entry<String, DBugParameterType<A, ?>> valueType : valueTypes.entrySet()) {
			if (valueType.getValue().level == DBugVariableType.STATIC)
				staticValueNames.add(valueType.getKey());
			else
				dynamicValueNames.add(valueType.getKey());
		}
		ParameterMap<DBugParameterType<A, ?>> staticValues = ParameterSet.of(staticValueNames).createMap();
		ParameterMap<DBugParameterType<A, ?>> dynamicValues = ParameterSet.of(dynamicValueNames).createMap();
		for (Map.Entry<String, DBugParameterType<A, ?>> valueType : valueTypes.entrySet()) {
			if (valueType.getValue().level == DBugVariableType.STATIC)
				staticValues.put(valueType.getKey(), valueType.getValue());
			else
				dynamicValues.put(valueType.getKey(), valueType.getValue());
		}
		theStaticValues = staticValues.unmodifiable();
		theDynamicValues = dynamicValues.unmodifiable();

		ParameterMap<DefaultDBugEventType<A>> eventTypeMap = ParameterSet.of(eventTypes.keySet()).createMap();
		for (Map.Entry<String, Map<String, TypeToken<?>>> eventType : eventTypes.entrySet()) {
			eventTypeMap.put(eventType.getKey(), new DefaultDBugEventType<>(this, eventType.getKey(), eventType.getValue()));
		}
		theEventTypes = eventTypeMap.unmodifiable();

		theActiveEventIndex = theEventTypes.keyIndex(DBugEventType.StandardEvents.ANCHOR_ACTIVE.name());
		theUpdateEventIndex = theEventTypes.keyIndex(DBugEventType.StandardEvents.VALUE_UPDATE.name());

		theConfigs = new ArrayList<>();
	}

	@Override
	public Class<A> getType() {
		return theType;
	}

	@Override
	public ParameterMap<DBugParameterType<A, ?>> getStaticFields() {
		return theStaticValues;
	}

	@Override
	public ParameterMap<DBugParameterType<A, ?>> getDynamicFields() {
		return theDynamicValues;
	}

	@Override
	public ParameterMap<DBugEventType<A>> getEventTypes() {
		return (ParameterMap<DBugEventType<A>>) (ParameterMap<? extends DBugEventType<A>>) theEventTypes;
	}

	@Override
	public DBugAnchorBuilder<A> debug(A value) {
		return theDebug.debug(this, value);
	}

	public List<DBugConfig<A>> getConfigs() {
		return Collections.unmodifiableList(theConfigs);
	}

	@Override
	public int hashCode() {
		return theType.hashCode() * 3 + theBuilderClass.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof DefaultDBugAnchorType))
			return false;
		DefaultDBugAnchorType<?> other = (DefaultDBugAnchorType<?>) obj;
		return theType.equals(other.theType) && theBuilderClass.equals(other.theBuilderClass);
	}

	@Override
	public String toString() {
		return theType.getName();
	}

	public DBugConfig<A> addConfig(DBugConfigTemplate config, Consumer<String> onError) {
		DBugConfig<A> parsed = parseConfig(config, onError);
		if (parsed != null)
			theConfigs.add(parsed);
		return parsed;
	}

	public DBugConfig<A> removeConfig(DBugConfigTemplate config) {
		for (int i = 0; i < theConfigs.size(); i++) {
			DBugConfig<A> cfg = theConfigs.get(i);
			if (cfg.getTemplate().equals(config)) {
				theConfigs.remove(i);
				return cfg;
			}
		}
		return null;
	}

	public BiTuple<DBugConfig<A>, DBugConfig<A>> updateConfig(DBugConfigTemplate oldConfig, DBugConfigTemplate newConfig,
		Consumer<String> onError) {
		int found = -1;
		for (int i = 0; i < theConfigs.size(); i++) {
			DBugConfig<A> cfg = theConfigs.get(i);
			if (cfg.getTemplate().equals(oldConfig)) {
				found = i;
				break;
			}
		}
		if (found < 0)
			return null;
		DBugConfig<A> oldCfg = theConfigs.get(found);
		DBugConfig<A> newCfg = parseConfig(newConfig, onError);
		if (newCfg == null) {
			theConfigs.remove(found);
			return new BiTuple<>(oldCfg, null);
		} else if (oldCfg.equals(newCfg))
			return null;
		else {
			theConfigs.set(found, newCfg);
			return new BiTuple<>(oldCfg, newCfg);
		}
	}

	DBugAnchor<A> inactive() {
		if (theInactive == null)
			theInactive = new InactiveAnchor<>(this);
		return theInactive;
	}

	private DBugConfig<A> parseConfig(DBugConfigTemplate config, Consumer<String> onError) {
		// Parse the anchor variables first
		ParameterMap<DBugConfig.DBugConfigVariable<A, ?>> variables = parseVariables(config, onError);
		// Parse the events
		ParameterMap<DBugConfig.DBugEventConfig<A>> events = parseEvents(config, variables, onError);
		// Parse the condition last
		boolean[] valid = new boolean[] { true };
		DBugParseEnv<A> env = new DBugParseEnv<>(this, null, config, null, variables, null, err -> {
			onError.accept("Error in condition for anchor " + theType.getName() + ": " + err);
			valid[0] = false;
		});
		Expression<A, ?> condition;
		try {
			condition = ExpressionParser.parseExpression(config.getCondition(), TypeTokens.get().BOOLEAN, env);
		} catch (DBugParseException e) {
			onError.accept("Could not parse condition for anchor " + theType.getName() + ": " + e);
			e.printStackTrace();
			return null;
		}
		if (valid[0] && !TypeTokens.get().isBoolean(condition.getResultType())) {
			valid[0] = false;
			onError.accept("Condition for anchor " + theType.getName() + " does not resolve to a boolean: " + condition + " ("
				+ condition.getResultType() + ")");
		}
		if (valid[0])
			return new DBugConfig<>(config, this, variables, new DBugConfigVariable<>(null, (Expression<A, Boolean>) condition,
				env.getDynamicDependencies(), env.getConfigVariableDependencies()), events);
		else
			return null;
	}

	private ParameterMap<DBugConfig.DBugConfigVariable<A, ?>> parseVariables(DBugConfigTemplate config, Consumer<String> onError) {
		ParameterMap<DBugConfigTemplate.DBugConfigTemplateVariable> varTemplates = config.getVariables();
		ParameterMap<DBugConfig.DBugConfigVariable<A, ?>> variables = varTemplates.keySet().createMap();
		String[] varName = new String[1];
		DBugParseEnv<A> env = new DBugParseEnv<>(this, null, config, null, variables, null, err -> {
			onError.accept("Error in variable " + theType.getName() + "." + varName[0] + ": " + err);
		});
		for (int i = 0; i < varTemplates.keySet().size(); i++) {
			varName[0] = varTemplates.keySet().get(i);
			if (theStaticValues.keySet().contains(varName[0]) || theDynamicValues.keySet().contains(varName[0])) {
				onError.accept("Variable " + theType.getName() + "." + varName[0] + " would hide an anchor field");
				continue;
			}
			boolean found = false;
			for (DefaultDBugEventType<A> eventType : theEventTypes.allValues()) {
				if (eventType.getEventFields().keySet().contains(varName[0])) {
					onError.accept("Variable " + theType.getName() + "." + varName[0] + " would hide an event field for event "
						+ eventType.getEventName());
					found = true;
					break;
				}
			}
			if (found)
				continue;
			try {
				// the env will itself populate the variables map
				env.reset(varTemplates.get(i).cacheable).parseDependency(TypeTokens.get().of(Object.class), varName[0]);
			} catch (DBugParseException e) {
				onError.accept("Error in variable " + theType.getName() + "." + varName[0] + ": " + e);
				e.printStackTrace();
			}
		}
		return variables;
	}

	private ParameterMap<DBugConfig.DBugEventConfig<A>> parseEvents(DBugConfigTemplate config,
		ParameterMap<DBugConfig.DBugConfigVariable<A, ?>> variables, Consumer<String> onError) {
		ParameterMap<DBugConfigTemplate.DBugEventConfigTemplate> eventTemplates = config.getEvents();
		ParameterMap<DBugConfig.DBugEventConfig<A>> events = theEventTypes.keySet().createMap();
		for (int i = 0; i < eventTemplates.keySet().size(); i++) {
			DBugEventConfigTemplate event = eventTemplates.get(i);
			int ei = theEventTypes.keySet().indexOf(event.eventName);
			if (ei < 0) {
				onError.accept("Anchor " + theType.getName() + " does not define event " + event.eventName);
				continue;
			}
			DefaultDBugEventType<A> eventType = theEventTypes.get(ei);
			// Event variables
			ParameterMap<DBugEventVariable<A, ?>> eventVars = event.eventVariables.keySet().createMap();
			String[] varName = new String[1];
			DBugParseEnv<A> env = new DBugParseEnv<>(this, eventType, config, event, variables, eventVars, err -> {
				String v = varName[0] == null//
					? "condition for event " + theType.getName() + "." + event.eventName//
					: "event variable " + theType.getName() + "." + event.eventName + "." + varName[0];
				onError.accept("Error in " + v + ": " + err);
			});
			for (int j = 0; j < eventVars.keySet().size(); j++) {
				varName[0] = eventVars.keySet().get(j);
				if (theStaticValues.keySet().contains(varName[0]) || theDynamicValues.keySet().contains(varName[0])) {
					onError.accept(
						"Event variable " + theType.getName() + "." + event.eventName + "." + varName[0] + " would hide an anchor field");
					continue;
				} else if (eventType.getEventFields().keySet().contains(varName[0])) {
					onError.accept(
						"Event variable " + theType.getName() + "." + event.eventName + "." + varName[0] + " would hide an event field");
					continue;
				} else if (variables.keySet().contains(varName[0])) {
					onError.accept("Event variable " + theType.getName() + "." + event.eventName + "." + varName[0]
						+ " would hide an anchor variable");
					continue;
				}
				try {
					// the env will itself populate the eventVars map
					env.reset(false).parseDependency(TypeTokens.get().of(Object.class), varName[0]);
				} catch (DBugParseException e) {
					onError.accept("Error in event variable " + theType.getName() + "." + event.eventName + "." + varName[0] + ": " + e);
					e.printStackTrace();
				}
			}
			varName[0] = null;
			// Event condition
			boolean[] valid = new boolean[] { true };
			Expression<A, ?> condition;
			if (event.condition != null) {
				try {
					condition = ExpressionParser.parseExpression(event.condition, TypeTokens.get().BOOLEAN, env);
				} catch (DBugParseException e) {
					onError.accept("Error in condition for event " + theType.getName() + "." + event.eventName + ": " + e);
					e.printStackTrace();
					return null;
				}
				if (valid[0] && !TypeTokens.get().isBoolean(condition.getResultType())) {
					valid[0] = false;
					onError.accept("Condition for event " + theType.getName() + "." + event.eventName + " does not resolve to a boolean: "
						+ condition + " (" + condition.getResultType() + ")");
				}
			} else
				condition = null;
			if (valid[0])
				events.put(ei, new DBugConfig.DBugEventConfig<>(event, eventType, eventVars, condition == null ? null
					: new DBugEventVariable<>(eventType, null, -1, (Expression<A, Boolean>) condition,
						env.getEventVariableDependencies())));
		}
		return events;
	}

	private static class InactiveAnchor<A> implements DBugAnchor<A> {
		private final DefaultDBugAnchorType<A> theType;

		InactiveAnchor(DefaultDBugAnchorType<A> type) {
			theType = type;
		}

		@Override
		public DBugAnchorType<A> getType() {
			return theType;
		}

		@Override
		public A getValue() {
			return null;
		}

		@Override
		public boolean isActive() {
			return false;
		}

		@Override
		public ParameterMap<Object> getStaticValues() {
			return ParameterSet.EMPTY.createMap();
		}

		@Override
		public ParameterMap<Object> getDynamicValues() {
			return ParameterSet.EMPTY.createMap();
		}

		@Override
		public <P> P setDynamicValue(String property, P value) {
			return null;
		}

		@Override
		public <P> DBugAnchor<A> modifyDynamicValue(String property, Function<? super P, ? extends P> map) {
			return null;
		}

		@Override
		public DBugEventBuilder event(String eventName) {
			return DoNothingEventBuilder.INSTANCE;
		}
	}
}
