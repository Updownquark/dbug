package org.dbug.expression;

import org.observe.util.TypeTokens;

public class StringConcatOperation<A> extends BinaryExpression<A, Object, Object, String> {
	public StringConcatOperation(Expression<A, ?> left, Expression<A, ?> right) {
		super(left, right, TypeTokens.get().STRING);
	}

	@Override
	protected String evaluate(Object a, Object b) {
		return new StringBuilder().append(a).append(b).toString();
	}

	@Override
	protected Expression<A, String> copy(Expression<A, ? extends Object> left, Expression<A, ? extends Object> right) {
		return new StringConcatOperation<>(left, right);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof StringConcatOperation))
			return false;
		StringConcatOperation<?> other = (StringConcatOperation<?>) obj;
		return getLeft().equals(other.getLeft()) && getRight().equals(other.getRight());
	}

	@Override
	public String toString() {
		return getLeft() + "+" + getRight();
	}
}
