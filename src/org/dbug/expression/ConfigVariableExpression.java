package org.dbug.expression;

import java.util.Objects;

import org.dbug.DBugEvent;
import org.dbug.config.DBugConfiguredAnchor;

import com.google.common.reflect.TypeToken;

public class ConfigVariableExpression<A, T> implements Expression<A, T> {
	private final DBugParseEnv<A> theEnv;
	private final boolean isAnchorConfigVar;
	private final int theParameterIndex;

	public ConfigVariableExpression(DBugParseEnv<A> env, boolean anchorVar, int parameterIndex) {
		theEnv = env;
		isAnchorConfigVar = anchorVar;
		theParameterIndex = parameterIndex;
	}

	private Expression<A, T> getExpression() {
		if (isAnchorConfigVar)
			return (Expression<A, T>) theEnv.getAnchorVariables().get(theParameterIndex).expression;
		else
			return (Expression<A, T>) theEnv.getEventVariables().get(theParameterIndex).expression;
	}

	@Override
	public TypeToken<T> getResultType() {
		return getExpression().getResultType();
	}

	@Override
	public T evaluate(DBugEvent<A> event) throws DBugParseException {
		return getExpression().evaluate(event);
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		if (isAnchorConfigVar)
			return new ConstantExpression<>(getResultType(), (T) anchor.getConfigValues().get(theParameterIndex));
		else
			return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(isAnchorConfigVar, theParameterIndex);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ConfigVariableExpression))
			return false;
		ConfigVariableExpression<?, ?> other = (ConfigVariableExpression<?, ?>) obj;
		return isAnchorConfigVar == other.isAnchorConfigVar && theParameterIndex == other.theParameterIndex;
	}

	@Override
	public String toString() {
		if (isAnchorConfigVar)
			return "anchorVar:" + theEnv.getAnchorVariables().keySet().get(theParameterIndex);
		else
			return "eventVar:" + theEnv.getEventVariables().keySet().get(theParameterIndex);
	}
}
