package org.dbug.expression;

import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.dbug.DBugAnchorType;
import org.dbug.DBugEventType;
import org.dbug.DBugVariableType;
import org.dbug.config.DBugConfig;
import org.dbug.config.DBugConfig.DBugConfigVariable;
import org.dbug.config.DBugConfig.DBugEventVariable;
import org.dbug.config.DBugConfigTemplate;
import org.dbug.config.DBugConfigTemplate.DBugConfigTemplateVariable;
import org.dbug.config.DBugConfigTemplate.DBugEventConfigTemplate;
import org.qommons.collect.ParameterSet.ParameterMap;

import com.google.common.reflect.TypeToken;

public class DBugParseEnv<T> {
	private final DBugAnchorType<T> theType;
	private final DBugEventType<T> theEventType;
	private final DBugConfigTemplate theConfig;
	private final DBugEventConfigTemplate theEventConfig;
	private final ParameterMap<DBugConfig.DBugConfigVariable<T, ?>> theAnchorVariables;
	private final ParameterMap<DBugEventVariable<T, ?>> theEventVariables;
	private final BitSet theDynamicDependencies;
	private final BitSet theConfigVariableDependencies;
	private final BitSet theEventVariableDependencies;
	private final Set<String> thePath;
	private final Consumer<String> onError;
	private boolean isCacheable;

	public DBugParseEnv(DBugAnchorType<T> type, DBugEventType<T> eventType, DBugConfigTemplate config, DBugEventConfigTemplate eventConfig,
		ParameterMap<DBugConfigVariable<T, ?>> anchorVariables, ParameterMap<DBugEventVariable<T, ?>> eventVariables, //
		Consumer<String> onError) {
		theType = type;
		theEventType = eventType;
		theConfig = config;
		theEventConfig = eventConfig;
		theAnchorVariables = anchorVariables;
		theEventVariables = eventVariables;
		theDynamicDependencies = new BitSet();
		theConfigVariableDependencies = new BitSet();
		theEventVariableDependencies = eventType == null ? null : new BitSet();
		thePath = new LinkedHashSet<>();
		this.onError = onError;
	}

	public DBugParseEnv<T> reset(boolean cacheable) {
		theDynamicDependencies.clear();
		theConfigVariableDependencies.clear();
		isCacheable = cacheable;
		return this;
	}

	public void error(String msg) {
		onError.accept(msg);
	}

	public <X> Expression<T, X> parseDependency(TypeToken<X> type, String dependency) throws DBugParseException {
		if (dependency.equals("value")) {
			if (!type.isAssignableFrom(theType.getType()))
				throw new DBugParseException("Cannot convert from " + theType.getType().getName() + " to " + type);
			return (Expression<T, X>) new AnchorValueType<>(theType);
		}
		if (!thePath.add(dependency))
			throw new DBugParseException("Circular dependency detected: " + printPath(thePath, dependency));
		try {
			int index;
			index = theType.getStaticFields().keySet().indexOf(dependency);
			if (index >= 0) {
				return new AnchorVariableExpression<>(theType, null, DBugVariableType.STATIC, index);
			}

			index = theType.getDynamicFields().keySet().indexOf(dependency);
			if (index >= 0) {
				theDynamicDependencies.set(index);
				return new AnchorVariableExpression<>(theType, null, DBugVariableType.DYNAMIC, index);
			}

			index = theAnchorVariables.keySet().indexOf(dependency);
			if (index >= 0) {
				theConfigVariableDependencies.set(index);
				DBugConfigVariable<T, ?> variable = theAnchorVariables.get(index);
				if (variable != null) {
					if (!type.isAssignableFrom(variable.expression.getResultType()))
						throw new DBugParseException("Cannot convert from " + variable.expression.getResultType() + " to " + type);
					return (Expression<T, X>) variable.expression;
				}
				DBugConfigTemplateVariable varConfig = theConfig.getVariables().get(index);
				if (isCacheable && !varConfig.cacheable)
					throw new DBugParseException("A cacheable variable cannot depend on an uncacheable one: " + dependency);
				boolean oldCacheable = isCacheable;
				isCacheable = varConfig.cacheable;
				Expression<T, X> ex;
				try {
					ex = (Expression<T, X>) ExpressionParser.parseExpression(varConfig.expression, type, this);
				} finally {
					isCacheable = oldCacheable;
				}

				theAnchorVariables.put(index,
					new DBugConfigVariable<>(varConfig, ex, getDynamicDependencies(), getConfigVariableDependencies()));
				return ex;
			}

			index = theEventType == null ? -1 : theEventType.getEventFields().keySet().indexOf(dependency);
			if (index >= 0) {
				return new AnchorVariableExpression<>(theType, theEventType, DBugVariableType.EVENT, index);
			}

			index = theEventVariables == null ? -1 : theEventVariables.keySet().indexOf(dependency);
			if (index >= 0) {
				theEventVariableDependencies.set(index);
				Expression<T, X> ex = (Expression<T, X>) theEventVariables.get(index);
				if (ex != null) {
					if (!type.isAssignableFrom(ex.getResultType()))
						throw new DBugParseException("Cannot convert from " + ex.getResultType() + " to " + type);
					return ex;
				}
				DBugAntlrExpression varConfig = theEventConfig.eventVariables.get(index);

				boolean oldCacheable = isCacheable;
				isCacheable = false;
				try {
					ex = (Expression<T, X>) ExpressionParser.parseExpression(varConfig, type, this);
				} finally {
					isCacheable = oldCacheable;
				}
				theEventVariables.put(index, new DBugEventVariable<>(ex, getEventVariableDependencies()));
				return ex;
			}
			throw new DBugParseException("Unrecognized variable: " + dependency);
		} finally {
			thePath.remove(dependency);
		}
	}

	public BitSet getDynamicDependencies() {
		return theDynamicDependencies.isEmpty() ? null : (BitSet) theDynamicDependencies.clone();
	}

	public BitSet getConfigVariableDependencies() {
		return theConfigVariableDependencies.isEmpty() ? null : (BitSet) theConfigVariableDependencies.clone();
	}

	public BitSet getEventVariableDependencies() {
		return theEventVariableDependencies.isEmpty() ? null : (BitSet) theEventVariableDependencies.clone();
	}

	public boolean hasDependency(String dependency) {
		return theType.getStaticFields().keySet().contains(dependency)//
			|| theType.getDynamicFields().keySet().contains(dependency)//
			|| theAnchorVariables.keySet().contains(dependency)//
			|| (theEventVariables != null && theEventVariables.keySet().contains(dependency));
	}

	public Class<?> getType(String className) throws DBugParseException {
		ClassLoader loader = theType.getType().getClassLoader();
		int dotIdx = className.lastIndexOf('.');
		if (dotIdx < 0) {
			Class<?> clazz;
			try {
				clazz = loader.loadClass(className);
			} catch (ClassNotFoundException e) {
				try {
					clazz = loader.loadClass("java.lang." + className);
				} catch (ClassNotFoundException e2) {
					throw new DBugParseException("Could not load type " + className);
				}
			}
			if (clazz != null)
				return clazz;
		}
		try {
			return loader.loadClass(className);
		} catch (ClassNotFoundException e) {
			// Do nothing. We'll throw our own exception if we can't find the class.
		}
		StringBuilder qClassName = new StringBuilder(className);
		while (dotIdx >= 0) {
			qClassName.setCharAt(dotIdx, '$');
			try {
				return loader.loadClass(qClassName.toString());
			} catch (ClassNotFoundException e) {}
			dotIdx = qClassName.lastIndexOf(".");
		}
		throw new DBugParseException("Could not load class " + className);
	}

	private static String printPath(Set<String> path, String dependency) {
		StringBuilder s = new StringBuilder();
		boolean found = false;
		for (String p : path) {
			found = dependency.equals(p);
			if (found)
				s.append(p).append("->");
		}
		s.append(dependency);
		return s.toString();
	}
}
