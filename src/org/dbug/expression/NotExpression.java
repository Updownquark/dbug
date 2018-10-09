package org.dbug.expression;

import org.observe.util.TypeTokens;

public class NotExpression<A> extends UnaryOperation<A, Boolean, Boolean> {
	public NotExpression(Expression<A, ? extends Boolean> source) {
		super(source, TypeTokens.get().BOOLEAN);
	}

	@Override
	protected Boolean evaluate(Boolean sourceValue) throws DBugParseException {
		return !sourceValue;
	}

	@Override
	protected UnaryOperation<A, Boolean, Boolean> copy(Expression<A, ? extends Boolean> sourceCopy) {
		return new NotExpression<>(sourceCopy);
	}

	@Override
	public int hashCode() {
		return getSource().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof NotExpression))
			return false;
		return getSource().equals(((NotExpression<?>) obj).getSource());
	}

	@Override
	public String toString() {
		return "!" + getSource();
	}
}
