package org.dbug.expression;

import java.util.function.Function;

import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class UnaryMathExpression<A, T> extends UnaryOperation<A, Object, T> {
	private final String theOpName;
	private final Function<Object, T> theOperation;

	public UnaryMathExpression(Expression<A, ?> source, String opName) {
		super(source, typeFor(opName, source.getResultType()));
		theOpName = opName;
		switch (opName) {
		case "+":
			if (!DBugUtils.isMathable(source.getResultType()))
				throw new IllegalArgumentException("Posit cannot be applied to operand type " + source.getResultType());
			if (DBugUtils.CHAR.isAssignableFrom(TypeTokens.get().unwrap(source.getResultType())))
				theOperation = c -> (T) Integer.valueOf(((Character) c).charValue());
			else
				theOperation = v -> (T) v;
			break;
		case "-":
			if (!DBugUtils.isMathable(source.getResultType()))
				throw new IllegalArgumentException("Negate cannot be applied to operand type " + source.getResultType());
			if (DBugUtils.DOUBLE.isAssignableFrom(TypeTokens.get().unwrap(source.getResultType())))
				theOperation = v -> (T) Double.valueOf(-((Double) v).doubleValue());
			else if (DBugUtils.FLOAT.isAssignableFrom(TypeTokens.get().unwrap(source.getResultType())))
				theOperation = v -> (T) Float.valueOf(-((Float) v).floatValue());
			else if (DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(source.getResultType())))
				theOperation = v -> (T) Long.valueOf(-((Long) v).longValue());
			else if (DBugUtils.CHAR.isAssignableFrom(TypeTokens.get().unwrap(source.getResultType())))
				theOperation = c -> (T) Integer.valueOf(-((Character) c).charValue());
			else
				theOperation = v -> (T) Integer.valueOf(-((Number) v).intValue());
			break;
		case "~":
			if (!DBugUtils.isIntMathable(source.getResultType()))
				throw new IllegalArgumentException("Complement cannot be applied to operand type " + source.getResultType());
			if (DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(source.getResultType())))
				theOperation = v -> (T) Long.valueOf(~((Long) v).longValue());
			else if (DBugUtils.CHAR.isAssignableFrom(TypeTokens.get().unwrap(source.getResultType())))
				theOperation = c -> (T) Integer.valueOf(~((Character) c).charValue());
			else
				theOperation = v -> (T) Integer.valueOf(~((Number) v).intValue());
			break;
		default:
			throw new IllegalArgumentException(opName + " is not a recognized unary operation");
		}
	}

	protected UnaryMathExpression(Expression<A, ?> source, String opName, TypeToken<T> type, Function<Object, T> operation) {
		super(source, type);
		theOpName = opName;
		theOperation = operation;
	}

	@Override
	protected T evaluate(Object sourceValue) throws DBugParseException {
		return theOperation.apply(sourceValue);
	}

	@Override
	protected UnaryMathExpression<A, T> copy(Expression<A, ?> sourceCopy) {
		return new UnaryMathExpression<>(sourceCopy, theOpName, getResultType(), theOperation);
	}

	@Override
	public int hashCode() {
		return theOpName.hashCode() * 71 + getSource().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof UnaryMathExpression))
			return false;
		UnaryMathExpression<?, ?> other = (UnaryMathExpression<?, ?>) obj;
		return theOpName.equals(other.theOpName) && getSource().equals(other.getSource());
	}

	@Override
	public String toString() {
		return theOpName + getSource();
	}

	private static <T> TypeToken<T> typeFor(String opName, TypeToken<?> srcType) {
		TypeToken<?> resType;
		switch (opName) {
		case "+":
			if (!DBugUtils.isMathable(srcType))
				throw new IllegalArgumentException("Posit cannot be applied to operand type " + srcType);
			if (DBugUtils.CHAR.isAssignableFrom(TypeTokens.get().unwrap(srcType))) {
				resType = DBugUtils.INT;
			} else {
				resType = srcType;
			}
			break;
		case "-":
			if (!DBugUtils.isMathable(srcType))
				throw new IllegalArgumentException("Negate cannot be applied to operand type " + srcType);
			if (DBugUtils.DOUBLE.isAssignableFrom(TypeTokens.get().unwrap(srcType))) {
				resType = DBugUtils.DOUBLE;
			} else if (DBugUtils.FLOAT.isAssignableFrom(TypeTokens.get().unwrap(srcType))) {
				resType = DBugUtils.FLOAT;
			} else if (DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(srcType))) {
				resType = DBugUtils.LONG;
			} else if (DBugUtils.CHAR.isAssignableFrom(TypeTokens.get().unwrap(srcType))) {
				resType = DBugUtils.INT;
			} else {
				resType = DBugUtils.INT;
			}
			break;
		case "~":
			if (!DBugUtils.isIntMathable(srcType))
				throw new IllegalArgumentException("Complement cannot be applied to operand type " + srcType);
			if (DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(srcType))) {
				resType = DBugUtils.LONG;
			} else if (DBugUtils.CHAR.isAssignableFrom(TypeTokens.get().unwrap(srcType))) {
				resType = DBugUtils.INT;
			} else {
				resType = DBugUtils.INT;
			}
			break;
		default:
			throw new IllegalArgumentException(opName + " is not a recognized unary operation");
		}
		return (TypeToken<T>) resType;
	}
}
