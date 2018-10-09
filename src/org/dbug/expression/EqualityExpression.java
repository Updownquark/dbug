package org.dbug.expression;

import org.observe.util.TypeTokens;

public class EqualityExpression<T> extends BinaryExpression<T, Object, Object, Boolean> {
	private final boolean isEqual;

	public EqualityExpression(Expression<T, ?> left, Expression<T, ?> right, boolean equal) {
		super(left, right, TypeTokens.get().BOOLEAN);
		isEqual = equal;
	}

	@Override
	protected Boolean evaluate(Object a, Object b) {
		return isEqual ? (a == b) : (a != b);
	}

	@Override
	protected Expression<T, Boolean> copy(Expression<T, ? extends Object> left, Expression<T, ? extends Object> right) {
		if (left.equals(right))
			return isEqual ? ConstantExpression.TRUE() : ConstantExpression.FALSE();
		else
			return new EqualityExpression<>(left, right, isEqual);
	}

	@Override
	public int hashCode() {
		return super.hashCode() + (isEqual ? 0 : 1);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof EqualityExpression))
			return false;
		EqualityExpression<?> other = (EqualityExpression<?>) obj;
		if (isEqual != other.isEqual)
			return false;
		if (getLeft().equals(other.getLeft()))
			return getRight().equals(other.getRight());
		else
			return getLeft().equals(other.getRight()) && getRight().equals(other.getLeft());
	}

	@Override
	public String toString() {
		return getLeft() + (isEqual ? "==" : "!=") + getRight();
	}
}
