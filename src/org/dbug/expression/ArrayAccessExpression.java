package org.dbug.expression;

import java.lang.reflect.Array;

import com.google.common.reflect.TypeToken;

public class ArrayAccessExpression<T, X> extends BinaryExpression<T, Object, Number, X> {
	public ArrayAccessExpression(Expression<T, ? extends Object> left, Expression<T, ? extends Number> right, TypeToken<X> type) {
		super(left, right, type);
	}

	@Override
	protected X evaluate(Object a, Number b) {
		if(a instanceof Object [])
			return (X) ((Object[]) a)[b.intValue()];
		else
			return (X) Array.get(a, b.intValue());
	}

	@Override
	protected Expression<T, X> copy(Expression<T, ? extends Object> left, Expression<T, ? extends Number> right) {
		return new ArrayAccessExpression<>(left, right, getResultType());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof ArrayAccessExpression))
			return false;
		ArrayAccessExpression<?, ?> other = (ArrayAccessExpression<?, ?>) obj;
		return getLeft().equals(other.getLeft()) && getRight().equals(other.getRight());
	}

	@Override
	public String toString() {
		return getLeft() + "[" + getRight() + "]";
	}
}
