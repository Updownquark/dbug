package org.dbug.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.dbug.DBugAnchor;
import org.dbug.DBugEventBuilder;
import org.dbug.DBugEventType;
import org.dbug.DBugProcess;
import org.dbug.config.DBugConfig;
import org.dbug.config.DBugConfig.DBugConfigValue;
import org.dbug.config.DBugConfig.DBugEventConfig;
import org.dbug.config.DBugConfig.DBugEventValue;
import org.dbug.config.DBugConfigEvent;
import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugEventReporter;
import org.dbug.expression.ConstantExpression;
import org.dbug.expression.DBugParseException;
import org.dbug.expression.Expression;
import org.qommons.Transaction;
import org.qommons.collect.QuickSet.QuickMap;

public class DefaultDBugAnchor<A> implements DBugAnchor<A> {
	private final DefaultDBug theDBug;
	final DefaultDBugAnchorType<A> theType;
	private final A theValue;
	private final QuickMap<String, Object> theStaticValues;
	private QuickMap<String, Object> theDynamicValues;
	final IdentityHashMap<DBugEventReporter<?, ?, ?, ?, ?>, Object> theCompiledAnchors;

	final List<DBugConfigInstance> theConfigs;
	int isActive;

	DefaultDBugAnchor(DefaultDBug dBug, DefaultDBugAnchorType<A> type, A value, QuickMap<String, Object> staticValues,
		QuickMap<String, Object> dynamicValues) {
		theDBug = dBug;
		theType = type;
		theValue = value;
		theStaticValues = staticValues;
		theDynamicValues = dynamicValues;
		theCompiledAnchors = new IdentityHashMap<>();

		theConfigs = new LinkedList<>();
		long eventId = -1;
		for (DBugConfig<A> cfg : theType.getConfigs()) {
			if (eventId == -1 && cfg.getEvents().get(theType.theActiveEventIndex) != null)
				eventId = theDBug.getNextEventId();
			addConfig(cfg, eventId);
		}
	}

	@Override
	public DefaultDBugAnchorType<A> getType() {
		return theType;
	}

	@Override
	public A getValue() {
		return theValue;
	}

	@Override
	public boolean isActive() {
		return isActive > 0;
	}

	@Override
	public QuickMap<String, Object> getStaticValues() {
		return theStaticValues;
	}

	@Override
	public QuickMap<String, Object> getDynamicValues() {
		return theDynamicValues.unmodifiable();
	}

	@Override
	public synchronized <P> P setDynamicValue(String property, P value) {
		int index = theDynamicValues.keyIndex(property);
		P old = (P) theDynamicValues.put(index, value);
		long eventId = -1;
		QuickMap<String, Object> dvCopy = null;
		IdentityHashMap<DBugEventReporter<?, ?, ?, ?, ?>, Object> compiledEvents = null;
		for (DBugConfigInstance config : theConfigs) {
			// We want to do only enough work here to figure out if the config is now interested in the anchor given the new dynamic value
			boolean preActive = !config.condition.error && config.condition.get();
			BitSet conditionDDs = config.condition.expressionConfig.dynamicDependencies;
			BitSet conditionCVDs = config.condition.expressionConfig.configValueDependencies;
			boolean error = false;
			if (conditionDDs != null && conditionDDs.get(index)) {
				// The condition may have changed as a result of the new dynamic value
				// First re-evaluate all the config variables that the condition uses
				if (conditionCVDs != null) {
					for (int i = conditionCVDs.nextSetBit(0); i >= 0; i = conditionCVDs.nextSetBit(i + 1)) {
						AnchorEvaluatedExpression<?> variable = config.variables.get(i);
						if (variable.expressionConfig.dynamicDependencies != null
							&& variable.expressionConfig.dynamicDependencies.get(index))
							variable.reevaluate();
						error = variable.error;
						if (!preActive && error)
							break;
					}
				}
				// Now re-evaluate the condition itself
				if (!error)
					config.condition.reevaluate();
			}
			boolean postActive = !error && config.condition.get();
			if (preActive || postActive) {
				// The config cares about the anchor
				// re-evaluate the other variables which may have changed
				for (int i = 0; i < config.variables.keySet().size(); i++) {
					AnchorEvaluatedExpression<?> variable = config.variables.get(i);
					if (variable.expressionConfig.dynamicDependencies != null && variable.expressionConfig.dynamicDependencies.get(index)//
						&& (conditionCVDs == null || !conditionCVDs.get(i)))
						variable.reevaluate();
				}
			}
			if (preActive && postActive) {
				List<DBugEventConfigInstance> updateEventConfigs = config.events.get(theType.theUpdateEventIndex);
				if (updateEventConfigs != null) {
					// The config wants to know when any values change
					if (eventId == -1) {
						eventId = theDBug.getNextEventId();
						dvCopy = theDynamicValues.copy().unmodifiable();
					}
					Set<String> varsChanged = null;
					for (int i = 0; i < config.variables.keySet().size(); i++) {
						AnchorEvaluatedExpression<?> variable = config.variables.get(i);
						if (variable.expressionConfig.dynamicDependencies.get(index)) {
							if (varsChanged == null)
								varsChanged = new HashSet<>();
							varsChanged.add(config.variables.keySet().get(i));
						}
					}
					if (varsChanged == null)
						varsChanged = Collections.emptySet();
					DefaultDBugEventType<A> updateEventType = (DefaultDBugEventType<A>) theType.getEventTypes()
						.get(theType.theUpdateEventIndex);
					QuickMap<String, Object> eventValues = updateEventType.getEventFields().keySet().createMap();
					eventValues.put("field", property);
					eventValues.put("variables", varsChanged);
					eventValues = eventValues.unmodifiable();
					for (DBugEventConfigInstance evtConfig : updateEventConfigs) {
						ConfigSpecificEvent cse = new ConfigSpecificEvent(//
							new DBugEventTemplate<>(theDBug.getProcess(), eventId, this, updateEventType, dvCopy, eventValues, false), //
							evtConfig);
						if (cse.active) {
							if (compiledEvents == null)
								compiledEvents = new IdentityHashMap<>();
							cse.occurred(compiledEvents);
						}
					}
				}
			} else if (postActive)
				isActive++;
			else if (preActive)
				isActive--;
			if (preActive != postActive) {
				if (eventId == -1) {
					eventId = theDBug.getNextEventId();
					dvCopy = theDynamicValues.copy().unmodifiable();
				}
				fireActive(config, postActive, property, postActive ? old : value, eventId, dvCopy);
			}
		}
		return old;
	}

	private void fireActive(DBugConfigInstance config, boolean active, String field, Object inactiveValue, long eventId,
		QuickMap<String, Object> dvCopy) {
		List<DBugEventConfigInstance> activeEventConfigs = config.events.get(theType.theActiveEventIndex);
		if (!activeEventConfigs.isEmpty()) {
			IdentityHashMap<DBugEventReporter<?, ?, ?, ?, ?>, Object> compiledEvents = null;
			DefaultDBugEventType<A> activeEventType = (DefaultDBugEventType<A>) theType.getEventTypes().get(theType.theActiveEventIndex);
			// The config wants to know when an anchor becomes active or inactive
			QuickMap<String, Object> eventValues = activeEventType.getEventFields().keySet().createMap();
			eventValues.put("active", active);
			eventValues.put("field", field);
			eventValues.put("inactiveFieldValue", inactiveValue);
			eventValues = eventValues.unmodifiable();
			for (DBugEventConfigInstance evtConfig : activeEventConfigs) {
				ConfigSpecificEvent cse = new ConfigSpecificEvent(//
					new DBugEventTemplate<>(theDBug.getProcess(), eventId, this, activeEventType, dvCopy, eventValues, false), evtConfig);
				if (cse.active) {
					if (compiledEvents == null)
						compiledEvents = new IdentityHashMap<>();
					cse.occurred(new HashMap<>());
				}
			}
		}
	}

	@Override
	public synchronized <P> DBugAnchor<A> modifyDynamicValue(String property, Function<? super P, ? extends P> map) {
		setDynamicValue(property, map.apply((P) theDynamicValues.get(property)));
		return this;
	}

	@Override
	public DBugEventBuilder event(String eventName) {
		if (isActive == 0)
			return DoNothingEventBuilder.INSTANCE;
		return new DefaultDBugEventBuilder<A>(theDBug, this, (DefaultDBugEventType<A>) theType.getEventTypes().get(eventName));
	}

	public Transaction beginEvent(DBugEventTemplate<A> event) {
		List<ConfigSpecificEvent> configEvents = createConfigEvents(event);
		IdentityHashMap<DBugEventReporter<?, ?, ?, ?, ?>, Object> compiledEvents = new IdentityHashMap<>();
		for (ConfigSpecificEvent configEvent : configEvents) {
			if (configEvent != null)
				configEvent.begun(compiledEvents);
		}
		return () -> {
			for (ConfigSpecificEvent configEvent : configEvents) {
				if (configEvent != null)
					configEvent.ended();
			}
		};
	}

	public void eventOccurred(DBugEventTemplate<A> event) {
		List<ConfigSpecificEvent> configEvents = createConfigEvents(event);
		IdentityHashMap<DBugEventReporter<?, ?, ?, ?, ?>, Object> compiledEvents = null;
		for (ConfigSpecificEvent configEvent : configEvents) {
			if (configEvent != null) {
				if (compiledEvents == null)
					compiledEvents = new IdentityHashMap<>();
				configEvent.occurred(compiledEvents);
			}
		}
	}

	private synchronized List<ConfigSpecificEvent> createConfigEvents(DBugEventTemplate<A> event) {
		if (isActive == 0)
			return Collections.emptyList();
		List<ConfigSpecificEvent> configEvents = null;
		for (int i = 0; i < theConfigs.size(); i++) {
			List<DBugEventConfigInstance> evtConfigs = theConfigs.get(i).events.get(event.getType().getEventIndex());
			for (DBugEventConfigInstance evtConfig : evtConfigs) {
				ConfigSpecificEvent cse = new ConfigSpecificEvent(event, evtConfig);
				if (cse.active) {
					if (configEvents == null)
						configEvents = new ArrayList<>(theConfigs.size());
					configEvents.add(cse);
				}
			}
		}
		return configEvents;
	}

	public synchronized void addConfig(DBugConfig<A> config, long eventId) {
		for (DBugConfigInstance cfg : theConfigs)
			if (cfg.config == config)
				return;
		DBugConfigInstance configInst = new DBugConfigInstance(config);
		boolean active;
		try {
			active = configInst.condition.get();
		} catch (RuntimeException e) {
			active = false;
		}
		if (!active && configInst.condition.expressionConfig.dynamicDependencies == null) {
			// If the condition is static and false, don't even bother adding it since it can never be called
		} else {
			theConfigs.add(configInst);
			if (active) {
				isActive++;
				fireActive(configInst, true, null, null, eventId, theDynamicValues.copy());
			}
		}
	}

	public synchronized void removeConfig(DBugConfig<A> config) {
		Iterator<DBugConfigInstance> iter = theConfigs.iterator();
		while (iter.hasNext()) {
			DBugConfigInstance configInst = iter.next();
			if (configInst.config == config) {
				iter.remove();
				configInst.remove();
				break;
			}
		}
	}

	public synchronized void updateConfig(DBugConfig<A> oldConfig, DBugConfig<A> newConfig) {
		ListIterator<DBugConfigInstance> iter = theConfigs.listIterator();
		while (iter.hasNext()) {
			DBugConfigInstance configInst = iter.next();
			if (configInst.config == oldConfig) {
				if (newConfig == null) {
					iter.remove();
					configInst.remove();
				} else {
					iter.set(configInst.replaceWith(newConfig));
				}
				break;
			}
		}
	}

	@Override
	public int hashCode() {
		return theType.hashCode() * 7 + theValue.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof DefaultDBugAnchor))
			return false;
		DefaultDBugAnchor<?> other = (DefaultDBugAnchor<?>) obj;
		return theType.equals(other.theType) && theValue.equals(other.theValue);
	}

	@Override
	public String toString() {
		return theStaticValues.toString();
	}

	<A2> A2 getReporterCompiledAnchor(DBugEventReporter<?, ?, A2, ?, ?> reporter) {
		synchronized (theCompiledAnchors) {
			return (A2) theCompiledAnchors.computeIfAbsent(reporter, r -> r.compileForAnchor(this));
		}
	}

	abstract class AbstractConfiguredRepresenation implements DBugConfiguredAnchor<A> {
		@Override
		public DefaultDBugAnchorType<A> getType() {
			return DefaultDBugAnchor.this.getType();
		}

		@Override
		public A getValue() {
			return DefaultDBugAnchor.this.getValue();
		}

		@Override
		public QuickMap<String, Object> getStaticValues() {
			return DefaultDBugAnchor.this.getStaticValues();
		}

		@Override
		public QuickMap<String, Object> getDynamicValues() {
			return DefaultDBugAnchor.this.getDynamicValues();
		}

		@Override
		public <P> P setDynamicValue(String property, P value) {
			throw new IllegalStateException("Dynamic values may not be modified through a configured view");
		}

		@Override
		public <P> DBugAnchor<A> modifyDynamicValue(String property, Function<? super P, ? extends P> map) {
			throw new IllegalStateException("Dynamic values may not be modified through a configured view");
		}

		@Override
		public DBugEventBuilder event(String eventName) {
			throw new IllegalStateException("Events may not be fired through a configured view");
		}
	}

	private class DBugConfigInstance extends AbstractConfiguredRepresenation {
		final DBugConfig<A> config;
		final QuickMap<String, AnchorEvaluatedExpression<?>> variables;
		final QuickMap<String, List<DBugEventConfigInstance>> events;
		final AnchorEvaluatedExpression<Boolean> condition;
		final Object[] theReporterCompiledConfiguredAnchors;

		DBugConfigInstance(DBugConfig<A> config) {
			this.config = config;
			variables = config.getValues().keySet().createMap();
			for (int i = 0; i < variables.keySet().size(); i++)
				if (config.getValues().get(i) != null) {
					if (config.getValues().get(i).template.cacheable)
						variables.put(i, new CachedAnchorEvaluatedExpression<>(this, config.getValues().get(i)));
					else
						variables.put(i, new UncachedAnchorEvaluatedExpression<>(this, config.getValues().get(i)));
				}
			for (int i = 0; i < variables.keySet().size(); i++) {
				variables.get(i).init();
			}
			for (int i = 0; i < variables.keySet().size(); i++) {
				if (variables.get(i).expressionConfig.template.cacheable)
					variables.get(i).reevaluate();
			}
			events = config.getEvents().keySet().createMap();
			for (int i = 0; i < config.getEvents().keySet().size(); i++) {
				if (config.getEvents().get(i).isEmpty())
					events.put(i, Collections.emptyList());
				else
					events.put(i, config.getEvents().get(i).stream().map(event -> new DBugEventConfigInstance(this, event))
						.collect(Collectors.toList()));
			}
			condition = new CachedAnchorEvaluatedExpression<>(this, config.getCondition());
			condition.init();
			condition.reevaluate();
			if (!condition.error && condition.get())
				isActive++;
			theReporterCompiledConfiguredAnchors = new Object[config.getReporters().size()];
		}

		Object getReporterCompiledConfiguredAnchor(int index) {
			Object compiledConfiguredAnchor = theReporterCompiledConfiguredAnchors[index];
			if (compiledConfiguredAnchor == null) {
				DBugEventReporter<Object, ?, Object, ?, ?> reporter = (DBugEventReporter<Object, ?, Object, ?, ?>) config.getReporters()
					.get(index);
				Object compiledAnchor = getReporterCompiledAnchor(reporter);
				theReporterCompiledConfiguredAnchors[index] = compiledConfiguredAnchor = reporter.compileForConfiguredAnchor(compiledAnchor,
					config.getReporterCompiledAnchor(index), this);
			}
			return compiledConfiguredAnchor;
		}

		void remove() {
			if (!condition.error && condition.get())
				isActive--;
		}

		DBugConfigInstance replaceWith(DBugConfig<A> config) {
			// TODO
		}

		@Override
		public DBugConfig<A> getConfig() {
			return config;
		}

		@Override
		public boolean isActive() {
			return true; // If we get here, the anchor is active
		}

		@Override
		public QuickMap<String, Object> getConfigValues() {
			return variables.keySet().createDynamicMap(index -> {
				return variables.get(index).get();
			});
		}
	}

	private abstract class AnchorEvaluatedExpression<X> {
		final DBugConfigInstance configuredAnchor;
		final DBugConfigValue<A, X> expressionConfig;
		Expression<A, ? extends X> staticallyEvaluated;
		boolean initialized;
		boolean error;

		AnchorEvaluatedExpression(DBugConfigInstance configAnchor, DBugConfigValue<A, X> config) {
			configuredAnchor = configAnchor;
			this.expressionConfig = config;
		}

		void init() {
			initialized = true;
			Expression<A, ? extends X> evald;
			try {
				evald = expressionConfig.expression.given(configuredAnchor, false,
					expressionConfig.template == null || expressionConfig.template.cacheable);
			} catch (DBugParseException e) {
				error = true;
				// TODO At some point it would be better if we could report the error to the configs
				evald = null;
				e.printStackTrace();
			} catch (RuntimeException e) {
				error = true;
				evald = null;
				System.err.println("Could not evaluate " + (expressionConfig == null ? "condition" : expressionConfig.toString())
					+ " for config on " + theType);
				e.printStackTrace(); // Assume this is from the implementation
			}
			staticallyEvaluated = evald;
		}

		X evaluate() {
			if (staticallyEvaluated == null) {
				if (!initialized)
					init();
				if (staticallyEvaluated == null)
					return null;
			}
			try {
				error = false;
				return ((ConstantExpression<A, ? extends X>) staticallyEvaluated.given(configuredAnchor, true, true)).value;
			} catch (DBugParseException | RuntimeException e) {
				error = true;
				// TODO At some point it would be better if we could report the error to the configs
				System.err.println("Could not evaluate " + (expressionConfig == null ? "condition" : expressionConfig.toString())
					+ " for config on " + theType);
				e.printStackTrace();
				return null;
			}
		}

		abstract X get();

		abstract void reevaluate();
	}

	private class CachedAnchorEvaluatedExpression<X> extends AnchorEvaluatedExpression<X> {
		X dynamicallyEvaluated;

		CachedAnchorEvaluatedExpression(DBugConfigInstance configAnchor, DBugConfigValue<A, X> config) {
			super(configAnchor, config);
		}

		@Override
		void reevaluate() {
			dynamicallyEvaluated = evaluate();
		}

		@Override
		X get() {
			if (!initialized) {
				init();
				reevaluate();
			}
			return dynamicallyEvaluated;
		}
	}

	private class UncachedAnchorEvaluatedExpression<X> extends AnchorEvaluatedExpression<X> {
		UncachedAnchorEvaluatedExpression(DBugConfigInstance configAnchor, DBugConfigValue<A, X> config) {
			super(configAnchor, config);
		}

		@Override
		X get() {
			return evaluate();
		}

		@Override
		void reevaluate() {}
	}

	private class DBugEventConfigInstance {
		final DBugConfigInstance config;
		final DBugEventConfig<A> eventConfig;
		final QuickMap<String, EventEvaluableExpression<?>> eventVariables;
		final EventEvaluableExpression<?> condition;
		final Object[] theEventReporterCompiledConfiguredAnchors;

		public DBugEventConfigInstance(DBugConfigInstance config, DBugEventConfig<A> eventConfig) {
			this.config = config;
			this.eventConfig = eventConfig;
			eventVariables = eventConfig.eventValues.keySet().createMap();
			for (int i = 0; i < eventVariables.keySet().size(); i++) {
				if (eventConfig.eventValues.get(i) != null)
					eventVariables.put(i, new EventEvaluableExpression<>(this, eventConfig.eventValues.get(i)));
			}
			condition = eventConfig.condition == null ? null : new EventEvaluableExpression<>(this, eventConfig.condition);
			theEventReporterCompiledConfiguredAnchors = new Object[eventConfig.template.getReporterCount()
				- config.getConfig().getReporters().size()];
		}

		Object getReporterCompiledConfiguredAnchor(int index) {
			int evtRIndex = index - config.getConfig().getReporters().size();
			Object compiledConfiguredAnchor;
			if (evtRIndex < 0)
				compiledConfiguredAnchor = config.getReporterCompiledConfiguredAnchor(index);
			else {
				compiledConfiguredAnchor = theEventReporterCompiledConfiguredAnchors[evtRIndex];
				if (compiledConfiguredAnchor == null){
					DBugEventReporter<Object, ?, Object, ?, ?> reporter;
					reporter = (DBugEventReporter<Object, ?, Object, ?, ?>) eventConfig.template.getReporter(index);
					Object compiledAnchor=getReporterCompiledAnchor(reporter);
					theEventReporterCompiledConfiguredAnchors[evtRIndex] = compiledConfiguredAnchor = reporter
						.compileForConfiguredAnchor(compiledAnchor, eventConfig.getReporterCompiledAnchor(index), config);
				}
			}
			return compiledConfiguredAnchor;
		}
	}

	private class EventEvaluableExpression<X> {
		final DBugEventConfigInstance eventConfig;
		final DBugEventValue<A, X> config;
		final Expression<A, ? extends X> staticallyEvaluated;
		boolean error;

		EventEvaluableExpression(DBugEventConfigInstance eventConfig, DBugEventValue<A, X> config) {
			this.eventConfig = eventConfig;
			this.config = config;
			Expression<A, ? extends X> evald;
			try {
				evald = config.expression.given(eventConfig.config, false, false);
			} catch (DBugParseException e) {
				// TODO At some point it would be better if we could report the error to the configs
				evald = null;
				e.printStackTrace();
				error = true;
			}
			staticallyEvaluated = evald;
		}
	}

	private class ConfigSpecificEvent implements DBugConfigEvent<A> {
		private final DBugEventTemplate<A> theEvent;
		private final DBugEventConfigInstance theConfig;
		private final EventConfiguredRepresentation theEventAnchor;
		private final QuickMap<String, Object> theEventConfigValues;
		final boolean active;
		private List<Transaction> theReporterTransactions;

		ConfigSpecificEvent(DBugEventTemplate<A> event, DBugEventConfigInstance config) {
			theEvent = event;
			theConfig = config;
			theEventAnchor = new EventConfiguredRepresentation(config.config);
			QuickMap<String, Object> configValues = config.eventConfig.eventValues.keySet().createMap();
			theEventConfigValues = configValues.unmodifiable();
			// Evaluate event variables that the condition depends on
			if (config.condition != null) {
				boolean error = config.condition.error;
				if (config.condition.config.eventVariableDependencies != null) {
					for (int i = config.condition.config.eventVariableDependencies.nextSetBit(0); !error
						&& i >= 0; i = config.condition.config.eventVariableDependencies.nextSetBit(i + 1)) {
						Expression<A, ?> configVar = config.eventVariables.get(i).staticallyEvaluated;
						if (configVar == null) {
							error = true;
							continue;
						}
						try {
							configValues.put(i, configVar.evaluate(this));
						} catch (DBugParseException e) {
							error = true;
							// TODO At some point it would be better if we could report the error to the configs
							System.err.println("Could not evaluate event variable " + theType + "." + theEvent.getType().getEventName()
								+ "." + theEvent.getType().getEventFields().keySet().get(i));
							e.printStackTrace();
						}
					}
				}
				if (error)
					active = false;
				else {
					boolean conditionActive;
					try {
						conditionActive = Boolean.TRUE.equals(config.condition.staticallyEvaluated.evaluate(this));
					} catch (DBugParseException e) {
						conditionActive = false;
						error = true;
						// TODO At some point it would be better if we could report the error to the configs
						System.err.println("Could not evaluate condition for event " + theType + "." + theEvent.getType().getEventName());
						e.printStackTrace();
					}
					active = conditionActive;
				}
			} else
				active = true; // If no condition, then the event is always active
			if (active) {
				for (int i = 0; i < configValues.keySet().size(); i++) {
					if (config.condition != null && config.condition.config.eventVariableDependencies != null
						&& config.condition.config.eventVariableDependencies.get(i))
						continue; // Already evaluated
					Expression<A, ?> configVar = config.eventVariables.get(i).staticallyEvaluated;
					if (configVar != null) {
						try {
							configValues.put(i, configVar.evaluate(this));
						} catch (DBugParseException e) {
							// TODO At some point it would be better if we could report the error to the configs
							System.err.println("Could not evaluate event variable " + theType + "." + theEvent.getType().getEventName()
								+ "." + theEvent.getType().getEventFields().keySet().get(i));
							e.printStackTrace();
						}
					}
				}
			}
		}

		@Override
		public DBugProcess getProcess() {
			return theDBug.getProcess();
		}

		@Override
		public DBugConfig<A> getConfig() {
			return theConfig.config.config;
		}

		@Override
		public DBugEventConfig<A> getEventConfig() {
			return theConfig.eventConfig;
		}

		@Override
		public long getEventId() {
			return theEvent.getEventId();
		}

		@Override
		public DBugConfiguredAnchor<A> getAnchor() {
			return theEventAnchor;
		}

		@Override
		public DBugEventType<A> getType() {
			return theConfig.eventConfig.eventType;
		}

		@Override
		public QuickMap<String, Object> getDynamicValues() {
			return theEvent.getDynamicValues();
		}

		@Override
		public QuickMap<String, Object> getEventConfigValues() {
			return theEventConfigValues;
		}

		@Override
		public QuickMap<String, Object> getEventValues() {
			return theEvent.getEventValues();
		}

		@Override
		public Instant getStart() {
			return theEvent.getStart();
		}

		@Override
		public Instant getEnd() {
			return theEvent.getEnd();
		}

		void occurred(Map<DBugEventReporter<?, ?, ?, ?, ?>, Object> compiledEvents) {
			int rc = theConfig.eventConfig.template.getReporterCount();
			for (int i = 0; i < rc; i++) {
				DBugEventReporter<Object, Object, Object, Object, Object> reporter;
				reporter = (DBugEventReporter<Object, Object, Object, Object, Object>) theConfig.eventConfig.template.getReporter(i);
				Object compiledAnchor = theConfig.getReporterCompiledConfiguredAnchor(i);
				Object compiledEventType = theConfig.eventConfig.getReporterCompiledEvent(i);
				Object compiledEvent = compiledEvents.computeIfAbsent(reporter,
					r -> reporter.compileForEvent(compiledAnchor, compiledEventType, theEvent));
				try {
					reporter.eventOccurred(this, compiledAnchor, compiledEvent);
				} catch (RuntimeException e) {
					System.err.println("Exception occurred notifying reporter " + reporter + " of event " + this);
					e.printStackTrace();
				}
			}
		}

		void begun(Map<DBugEventReporter<?, ?, ?, ?, ?>, Object> compiledEvents) {
			int rc = theConfig.eventConfig.template.getReporterCount();
			theReporterTransactions = new ArrayList<>(rc);
			for (int i = 0; i < rc; i++) {
				DBugEventReporter<Object, Object, Object, Object, Object> reporter;
				reporter = (DBugEventReporter<Object, Object, Object, Object, Object>) theConfig.eventConfig.template.getReporter(i);
				Object compiledAnchor = theConfig.getReporterCompiledConfiguredAnchor(i);
				Object compiledEventType = theConfig.eventConfig.getReporterCompiledEvent(i);
				Object compiledEvent = compiledEvents.computeIfAbsent(reporter,
					r -> reporter.compileForEvent(compiledAnchor, compiledEventType, theEvent));
				try {
					theReporterTransactions.add(//
						reporter.eventBegun(this, compiledAnchor, compiledEvent));
				} catch (RuntimeException e) {
					System.err.println("Exception occurred notifying reporter " + reporter + " of event " + this);
					e.printStackTrace();
				}
			}
		}

		void ended() {
			theEvent.close();
			for (int i = 0; i < theReporterTransactions.size(); i++) {
				try {
					theReporterTransactions.get(i).close();
				} catch (RuntimeException e) {
					System.err.println("Exception occurred notifying reporter of end of event " + this);
					e.printStackTrace();
				}
			}
		}
	}

	class EventConfiguredRepresentation extends AbstractConfiguredRepresenation {
		private final DBugConfig<A> theConfig;
		private final QuickMap<String, Object> theAnchorConfigValues;

		EventConfiguredRepresentation(DBugConfigInstance config) {
			theConfig = config.config;
			theAnchorConfigValues = config.getConfigValues().copy();
		}

		@Override
		public boolean isActive() {
			return true; // If we get here, it's because the anchor is active
		}

		@Override
		public DBugConfig<A> getConfig() {
			return theConfig;
		}

		@Override
		public QuickMap<String, Object> getConfigValues() {
			return theAnchorConfigValues;
		}
	}
}
