package org.dbug.impl;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbug.config.DBugConfigTemplate;
import org.dbug.config.DBugConfigTemplate.DBugConfigTemplateValue;
import org.dbug.config.DBugConfigTemplate.DBugEventConfigTemplate;
import org.dbug.config.DBugEventReporter;
import org.dbug.expression.DBugAntlrExpression;
import org.dbug.expression.DBugParseException;
import org.dbug.expression.ExpressionParser;
import org.dbug.expression.ExternalExpressionSpec;
import org.qommons.ArrayUtils;
import org.qommons.IterableUtils;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.config.QommonsConfig;

public class DBugConfigSet {
	private final List<EventReporterHolder> theReporters;
	private final Map<String, EventReporterHolder> theReportersByName;
	private final List<DBugConfigTemplate> theTemplates;

	DBugConfigSet() {
		theReporters = new ArrayList<>();
		theReportersByName = new HashMap<>();
		theTemplates = new ArrayList<>();
	}

	public void read(URL configUrl, DefaultDBug dBug) throws IOException, DBugParseException {
		if (configUrl == null) {
			for (DBugConfigTemplate t : theTemplates)
				dBug.removeConfig(t);
			theTemplates.clear();
			for (EventReporterHolder r : theReporters)
				r.reporter.close();
			theReporters.clear();
			theReportersByName.clear();
			return;
		}
		QommonsConfig config = QommonsConfig.fromXml(configUrl);

		// Parse reporters
		List<EventReporterHolder> oldReporters = new ArrayList<>(theReporters.size());
		QommonsConfig[] reporterConfigs = config.subConfig("reporters").subConfigs();
		List<EventReporterHolder> newReporters = new ArrayList<>(reporterConfigs.length);
		ArrayUtils.adjustNoCreate(theReporters.toArray(new EventReporterHolder[theReporters.size()]), reporterConfigs, //
			new ArrayUtils.DifferenceListenerE<EventReporterHolder, QommonsConfig, DBugParseException>() {
				@Override
				public boolean identity(EventReporterHolder o1, QommonsConfig o2) {
					return o1.name.equals(o2.get("name"));
				}

				@Override
				public EventReporterHolder added(QommonsConfig o, int mIdx, int retIdx) throws DBugParseException {
					EventReporterHolder holder = new EventReporterHolder(o);
					newReporters.add(holder);
					return holder;
				}

				@Override
				public EventReporterHolder removed(EventReporterHolder o, int oIdx, int incMod, int retIdx) {
					oldReporters.add(o);
					return null;
				}

				@Override
				public EventReporterHolder set(EventReporterHolder o1, int idx1, int incMod, QommonsConfig o2, int idx2, int retIdx)
					throws DBugParseException {
					if (!o1.config.equals(o2)) {
						oldReporters.add(o1);
						EventReporterHolder holder = new EventReporterHolder(o2);
						newReporters.add(holder);
						return holder;
					}
					return o1;
				}
			});
		// Once we've finished parsing the reporters, configure new reporters and close old ones
		for (EventReporterHolder holder : oldReporters) {
			theReportersByName.remove(holder.name);
			holder.reporter.close();
		}
		theReporters.removeAll(oldReporters);
		for (EventReporterHolder holder : newReporters) {
			theReportersByName.put(holder.name, holder);
			holder.configure();
		}
		if (!newReporters.isEmpty()) {
			theReporters.addAll(newReporters);
		}

		// Parse templates
		List<DBugConfigTemplate> templates = new ArrayList<>(config.subConfigs().length - 1);
		for (QommonsConfig c : config.subConfigs()) {
			if (!c.getName().equals("reporters"))
				templates.add(parseTemplate(c));
		}

		templates.sort(DefaultDBug.CONFIG_TEMPLATE_SORT);
		boolean[] updated = new boolean[1];
		IterableUtils.compare(theTemplates, templates, new IterableUtils.SortedAdjuster<DBugConfigTemplate, DBugConfigTemplate>() {
			@Override
			public int compare(DBugConfigTemplate v1, DBugConfigTemplate v2) {
				return DefaultDBug.CONFIG_TEMPLATE_SORT.compare(v1, v2);
			}

			@Override
			public void added(DBugConfigTemplate newValue, DBugConfigTemplate after, DBugConfigTemplate before) {
				dBug.addConfig(newValue, err -> {
					System.err.println("Error with config " + newValue.getClassName() + ": " + err);
				});
			}

			@Override
			public boolean removed(DBugConfigTemplate oldValue, DBugConfigTemplate after, DBugConfigTemplate before) {
				dBug.removeConfig(oldValue);
				return false;
			}

			@Override
			public void found(DBugConfigTemplate v1, DBugConfigTemplate v2) {
				if (!v1.equals(v2))
					dBug.updateConfig(v1, v2, err -> {
						System.err.println("Error with config " + v1.getClassName() + ": " + err);
					});
			}
		});
		if (updated[0])
			saveConfig(configUrl);
	}

	private void saveConfig(URL config) {
		// TODO
	}

	private DBugConfigTemplate parseTemplate(QommonsConfig config) throws DBugParseException {
		String id = config.get("id");
		if (id == null)
			throw new DBugParseException("Config declared with no id");
		String schema = config.get("schema");
		String className = config.get("class");
		if (schema == null || className == null)
			throw new DBugParseException("Config declared with no schema or class name");
		Map<String, DBugConfigTemplateValue> variables = new LinkedHashMap<>();
		for (QommonsConfig varConfig : config.subConfigs("variable")) {
			DBugConfigTemplateValue var = parseVariable(className, varConfig);
			if (variables.put(varConfig.get("name"), var) != null)
				throw new DBugParseException("Duplicate variables " + varConfig.get("name"));
		}
		QuickMap<String, DBugConfigTemplateValue> varMap = QuickSet.of(variables.keySet()).createMap();
		for (int i = 0; i < varMap.keySet().size(); i++)
			varMap.put(i, variables.get(varMap.keySet().get(i)));
		variables = null;

		DBugAntlrExpression condition;
		try {
			condition = parseExpression(config.subConfig("condition"));
		} catch (DBugParseException e) {
			throw new DBugParseException("Error parsing condition for " + className, e);
		}

		List<DBugEventReporter<?, ?, ?, ?, ?>> globalReporters = new ArrayList<>(config.subConfigs("reporter").length);
		for (QommonsConfig repConfig : config.subConfigs("reporter")) {
			String name = repConfig.get("name");
			EventReporterHolder holder = theReportersByName.get(name);
			if (holder == null)
				throw new DBugParseException("No such reporter named " + name + " declared");
			globalReporters.add(holder.reporter);
		}

		DBugConfigTemplate[] template = new DBugConfigTemplate[1];
		Map<String, DBugEventConfigTemplate> events = new LinkedHashMap<>();
		for (QommonsConfig evtConfig : config.subConfigs("event")) {
			DBugEventConfigTemplate evt = parseEvent(globalReporters, className, evtConfig, template);
			if (events.put(evtConfig.get("name"), evt) != null)
				throw new DBugParseException("Duplicate events " + evtConfig.get("name"));
		}
		QuickMap<String, DBugEventConfigTemplate> evtMap = QuickSet.of(events.keySet()).createMap();
		for (int i = 0; i < evtMap.keySet().size(); i++)
			evtMap.put(i, events.get(evtMap.keySet().get(i)));
		events = null;

		return template[0] = new DBugConfigTemplate(id, schema, className, varMap.unmodifiable(), condition,
			Collections.unmodifiableList(globalReporters),
			evtMap.unmodifiable());
	}

	private static DBugConfigTemplateValue parseVariable(String configName, QommonsConfig varConfig) throws DBugParseException {
		DBugAntlrExpression expression;
		try {
			expression = parseExpression(varConfig);
		} catch (DBugParseException e) {
			throw new DBugParseException("Error parsing variable " + configName + "." + varConfig.get("name"), e);
		}
		return new DBugConfigTemplateValue(varConfig.get("name"), expression, varConfig.is("cache", true));
	}

	private static DBugAntlrExpression parseExpression(QommonsConfig subConfig) throws DBugParseException {
		String ref = subConfig.get("ref");
		if (ref != null)
			return new ExternalExpressionSpec(null, ref);
		else
			return ExpressionParser.compile(subConfig.getValue());
	}

	private DBugEventConfigTemplate parseEvent(List<DBugEventReporter<?, ?, ?, ?, ?>> globalReporters, String configName,
		QommonsConfig evtConfig,
		DBugConfigTemplate[] template)
		throws DBugParseException {
		String name = evtConfig.get("name");
		Map<String, DBugAntlrExpression> variables = new LinkedHashMap<>();
		for (QommonsConfig varConfig : evtConfig.subConfigs("variable")) {
			DBugAntlrExpression expression;
			try {
				expression = parseExpression(varConfig);
			} catch (DBugParseException e) {
				throw new DBugParseException("Error parsing event variable " + configName + "." + name + "." + varConfig.get("name"), e);
			}
			if (variables.put(varConfig.get("name"), expression) != null)
				throw new DBugParseException("Duplicate event variables " + varConfig.get("name"));
		}
		QuickMap<String, DBugAntlrExpression> varMap = QuickSet.of(variables.keySet()).createMap();
		for (int i = 0; i < varMap.keySet().size(); i++)
			varMap.put(i, variables.get(varMap.keySet().get(i)));
		variables = null;
		DBugAntlrExpression condition;
		try {
			condition = evtConfig.subConfig("condition") == null ? null : parseExpression(evtConfig.subConfig("condition"));
		} catch (DBugParseException e) {
			throw new DBugParseException("Error parsing condition for event " + configName + "." + name, e);
		}

		List<DBugEventReporter<?, ?, ?, ?, ?>> evtReporters = new ArrayList<>(evtConfig.subConfigs("reporter").length);
		for (QommonsConfig repConfig : evtConfig.subConfigs("reporter")) {
			String reporterName = repConfig.get("name");
			EventReporterHolder holder = theReportersByName.get(reporterName);
			if (holder == null)
				throw new DBugParseException("No such reporter named " + reporterName + " declared");
			evtReporters.add(holder.reporter);
		}

		return new DBugEventConfigTemplate(globalReporters, name, varMap.unmodifiable(), condition,
			Collections.unmodifiableList(evtReporters), template);
	}

	private static class EventReporterHolder {
		final String name;
		final QommonsConfig config;
		final DBugEventReporter<?, ?, ?, ?, ?> reporter;

		EventReporterHolder(QommonsConfig config) throws DBugParseException {
			this.name = config.get("name");
			this.config = config;
			try {
				reporter = Class.forName(config.get("class")).asSubclass(DBugEventReporter.class).newInstance();
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				throw new DBugParseException("Could not instantiate reporter class " + config.get("class"), e);
			}
		}

		void configure() {
			reporter.configure(config);
		}
	}
}
