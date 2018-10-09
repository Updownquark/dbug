package org.dbug.expression;

import java.util.Objects;

import org.dbug.DBugAnchorType;
import org.dbug.DBugEvent;
import org.dbug.DBugEventType;
import org.dbug.DBugVariableType;
import org.dbug.config.DBugConfiguredAnchor;

import com.google.common.reflect.TypeToken;

public class AnchorVariableExpression<A, T> implements Expression<A, T> {
	private final DBugAnchorType<A> theAnchorType;
	private final DBugEventType<A> theEventType;
	private final DBugVariableType level;
	private final int theParameterIndex;

	public AnchorVariableExpression(DBugAnchorType<A> anchorType, DBugEventType<A> eventType, DBugVariableType level, int parameterIndex) {
		theAnchorType = anchorType;
		theEventType = eventType;
		this.level = level;
		theParameterIndex = parameterIndex;
	}

	@Override
	public TypeToken<T> getResultType() {
		switch (level) {
		case STATIC:
			return (TypeToken<T>) theAnchorType.getStaticFields().get(theParameterIndex).type;
		case DYNAMIC:
			return (TypeToken<T>) theAnchorType.getDynamicFields().get(theParameterIndex).type;
		case EVENT:
			return (TypeToken<T>) theEventType.getEventFields().get(theParameterIndex);
		}
		throw new IllegalStateException("Unrecognized variable level: " + level);
	}

	@Override
	public T evaluate(DBugEvent<A> event) {
		switch (level) {
		case STATIC:
			return (T) event.getAnchor().getStaticValues().get(theParameterIndex);
		case DYNAMIC:
			return (T) event.getAnchor().getDynamicValues().get(theParameterIndex);
		case EVENT:
			return (T) event.getEventValues().get(theParameterIndex);
		}
		throw new IllegalStateException("Unrecognized variable level: " + level);
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		switch (level) {
		case STATIC:
			return new ConstantExpression<>(getResultType(), (T) anchor.getStaticValues().get(theParameterIndex));
		case DYNAMIC:
			if (evalDynamic)
				return new ConstantExpression<>(getResultType(), (T) anchor.getDynamicValues().get(theParameterIndex));
			else
				return this;
		case EVENT:
			return this;
		}
		throw new IllegalStateException("Unrecognized variable level: " + level);
	}

	@Override
	public int hashCode() {
		return Objects.hash(theAnchorType, theEventType, level, theParameterIndex);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof AnchorVariableExpression))
			return false;
		AnchorVariableExpression<?, ?> other = (AnchorVariableExpression<?, ?>) obj;
		return theAnchorType.equals(other.theAnchorType) && Objects.equals(theEventType, other.theEventType)//
			&& level == other.level && theParameterIndex == other.theParameterIndex;
	}

	@Override
	public String toString() {
		switch (level) {
		case STATIC:
			return theAnchorType + "." + theAnchorType.getStaticFields().keySet().get(theParameterIndex) + "(static)";
		case DYNAMIC:
			return theAnchorType + "." + theAnchorType.getDynamicFields().keySet().get(theParameterIndex) + "(dynamic)";
		case EVENT:
			return theAnchorType + " event " + theEventType.getEventName() + "."
				+ theEventType.getEventFields().keySet().get(theParameterIndex);
		}
		throw new IllegalStateException("Unrecognized variable level: " + level);
	}
}
