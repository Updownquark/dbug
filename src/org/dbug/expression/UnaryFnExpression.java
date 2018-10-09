package org.dbug.expression;

import java.util.function.Function;

import com.google.common.reflect.TypeToken;

public class UnaryFnExpression<A, S, T> extends UnaryOperation<A, S, T> {
	private final Function<? super S, ? extends T> theMap;

	public UnaryFnExpression(Expression<A, ? extends S> source, TypeToken<T> type, Function<? super S, ? extends T> map) {
		super(source, type);
		theMap = map;
	}

	@Override
	protected T evaluate(S sourceValue) {
		return theMap.apply(sourceValue);
	}

	@Override
	protected UnaryOperation<A, S, T> copy(Expression<A, ? extends S> sourceCopy) {
		return new UnaryFnExpression<>(sourceCopy, getResultType(), theMap);
	}

	@Override
	public int hashCode() {
		return getSource().hashCode() * 17 + theMap.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof UnaryFnExpression))
			return false;
		UnaryFnExpression<A, ?, ?> other = (UnaryFnExpression<A, ?, ?>) obj;
		return theMap.equals(other.theMap) && getSource().equals(other.getSource());
	}

	@Override
	public String toString() {
		return theMap + "(" + getSource() + ")";
	}
}
