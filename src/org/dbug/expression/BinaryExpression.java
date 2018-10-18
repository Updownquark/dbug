package org.dbug.expression;

import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugConfigEvent;

import com.google.common.reflect.TypeToken;

public abstract class BinaryExpression<T, A, B, X> implements Expression<T, X> {
	private final Expression<T, ? extends A> theLeft;
	private final Expression<T, ? extends B> theRight;
	private final TypeToken<X> theType;

	public BinaryExpression(Expression<T, ? extends A> left, Expression<T, ? extends B> right, TypeToken<X> type) {
		theLeft = left;
		theRight = right;
		theType = type;
	}

	public Expression<T, ? extends A> getLeft() {
		return theLeft;
	}

	public Expression<T, ? extends B> getRight() {
		return theRight;
	}

	@Override
	public TypeToken<X> getResultType() {
		return theType;
	}

	@Override
	public X evaluate(DBugConfigEvent<T> event) throws DBugParseException {
		A leftVal = theLeft.evaluate(event);
		if (!needsRightArg(leftVal))
			return evaluateLeftOnly(leftVal);
		return evaluate(//
			leftVal, theRight.evaluate(event));
	}

	protected abstract X evaluate(A a, B b);

	protected boolean needsRightArg(A a) {
		return true;
	}

	protected X evaluateLeftOnly(A a) {
		throw new IllegalStateException("This must be overridden by the subclass if needsRightArg is overridden");
	}

	@Override
	public Expression<T, ? extends X> given(DBugConfiguredAnchor<T> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		Expression<T, ? extends A> left = theLeft.given(anchor, evalDynamic, cacheable);
		if (left instanceof ConstantExpression) {
			A a = ((ConstantExpression<T, ? extends A>) left).value;
			if (!needsRightArg(a))
				return new ConstantExpression<>(theType, evaluateLeftOnly(a));
		}
		Expression<T, ? extends B> right = theRight.given(anchor, evalDynamic, cacheable);
		if (left == theLeft && right == theRight)
			return this;
		else if (cacheable && left instanceof ConstantExpression && right instanceof ConstantExpression)
			return new ConstantExpression<>(theType,
				evaluate(//
					((ConstantExpression<T, ? extends A>) left).value, //
					((ConstantExpression<T, ? extends B>) right).value));
		else
			return copy(left, right);
	}

	protected abstract Expression<T, X> copy(Expression<T, ? extends A> left, Expression<T, ? extends B> right);

	@Override
	public int hashCode() {
		return (theLeft.hashCode() + theRight.hashCode()) * 7;
	}

	@Override
	public abstract boolean equals(Object obj);

	@Override
	public abstract String toString();
}
