package org.dbug.expression;

import org.dbug.DBugEvent;
import org.dbug.config.DBugConfiguredAnchor;

import com.google.common.reflect.TypeToken;

public abstract class UnaryOperation<A, S, T> implements Expression<A, T> {
	private final Expression<A, ? extends S> theSource;
	private final TypeToken<T> theType;

	public UnaryOperation(Expression<A, ? extends S> source, TypeToken<T> type) {
		theSource = source;
		theType = type;
	}

	protected Expression<A, ? extends S> getSource() {
		return theSource;
	}

	@Override
	public TypeToken<T> getResultType() {
		return theType;
	}

	@Override
	public T evaluate(DBugEvent<A> event) throws DBugParseException {
		S source = theSource.evaluate(event);
		return evaluate(source);
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		Expression<A, ? extends S> source = theSource.given(anchor, evalDynamic, cacheable);
		if (source == theSource)
			return this;
		else if (cacheable && source instanceof ConstantExpression)
			return new ConstantExpression<>(theType, evaluate(((ConstantExpression<A, ? extends S>) source).value));
		else if (cacheable && source instanceof TypeExpression)
			return new ConstantExpression<>(theType, evaluate((S) null));
		else
			return copy(source);
	}

	protected abstract T evaluate(S sourceValue) throws DBugParseException;

	protected abstract UnaryOperation<A, S, T> copy(Expression<A, ? extends S> sourceCopy);

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object obj);

	@Override
	public abstract String toString();
}
