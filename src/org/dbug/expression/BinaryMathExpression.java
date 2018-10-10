package org.dbug.expression;

import static org.dbug.expression.DBugUtils.isIntMathable;
import static org.dbug.expression.DBugUtils.isMathable;

import java.util.function.BiFunction;

import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class BinaryMathExpression<A, T> extends BinaryExpression<A, Object, Object, T> {
	private final String theOpName;
	private final BiFunction<Object, Object, T> theOperation;
	private final boolean isTransitive;

	protected BinaryMathExpression(Expression<A, ?> left, Expression<A, ?> right, TypeToken<T> type, String opName,
		BiFunction<Object, Object, T> operation, boolean transitive) {
		super(left, right, type);
		theOpName = opName;
		theOperation = operation;
		isTransitive = transitive;
	}

	@Override
	protected T evaluate(Object a, Object b) {
		return theOperation.apply(a, b);
	}

	@Override
	protected boolean needsRightArg(Object a) {
		if (theOpName.equals("&&") && !((Boolean) a).booleanValue())
			return false;
		else if (theOpName.equals("||") && ((Boolean) a).booleanValue())
			return false;
		return true;
	}

	@Override
	protected T evaluateLeftOnly(Object a) {
		return (T) a;
	}

	@Override
	protected Expression<A, T> copy(Expression<A, ?> left, Expression<A, ?> right) {
		return new BinaryMathExpression<>(left, right, getResultType(), theOpName, theOperation, isTransitive);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof BinaryMathExpression))
			return false;
		BinaryMathExpression<?, ?> other = (BinaryMathExpression<?, ?>) obj;
		if (!theOpName.equals(other.theOpName))
			return false;
		if (getLeft().equals(other.getLeft()) && getRight().equals(other.getRight()))
			return true;
		else if (isTransitive && getLeft().equals(other.getRight()) && getRight().equals(other.getLeft()))
			return true;
		else
			return false;
	}

	@Override
	public String toString() {
		return getLeft() + theOpName + getRight();
	}

	public static <A> BinaryMathExpression<A, ?> binaryOp(Expression<A, ?> left, Expression<A, ?> right, String opName) {
		TypeToken<?> resultType;
		TypeToken<?> maxType;
		TypeToken<?> leftType = left.getResultType();
		TypeToken<?> rightType = right.getResultType();
		switch (opName) {
		case "+":
		case "-":
		case "*":
		case "/":
		case "%":
			if (!isMathable(leftType) || !isMathable(rightType))
				throw new IllegalArgumentException(opName + " cannot be applied to operand types " + leftType + " and " + rightType);
			if (DBugUtils.DOUBLE.isAssignableFrom(TypeTokens.get().unwrap(leftType))
				|| DBugUtils.DOUBLE.isAssignableFrom(TypeTokens.get().unwrap(rightType)))
				maxType = resultType = DBugUtils.DOUBLE;
			else if (DBugUtils.FLOAT.isAssignableFrom(TypeTokens.get().unwrap(leftType))
				|| DBugUtils.FLOAT.isAssignableFrom(TypeTokens.get().unwrap(rightType)))
				maxType = resultType = DBugUtils.FLOAT;
			else if (DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(leftType))
				|| DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(rightType)))
				maxType = resultType = DBugUtils.LONG;
			else
				maxType = resultType = DBugUtils.INT;
			break;
		case "<<":
		case ">>":
		case ">>>":
		case "&":
		case "|":
		case "^":
			if (!isIntMathable(leftType) || !isIntMathable(rightType))
				throw new IllegalArgumentException(
					"Bit-wise operation " + opName + " cannot be applied to operand types " + leftType + " and " + rightType);
			if (DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(leftType))
				|| DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(rightType)))
				maxType = resultType = DBugUtils.LONG;
			else
				maxType = resultType = DBugUtils.INT;
			break;
		case "&&":
		case "||":
			if (!TypeTokens.get().isBoolean(leftType) || !TypeTokens.get().isBoolean(rightType))
				throw new IllegalArgumentException(opName + " cannot be applied to operand types " + leftType + " and " + rightType);
			maxType = resultType = DBugUtils.BOOLEAN;
			break;
		case ">":
		case "<":
		case ">=":
		case "<=":
			if (!isMathable(leftType) || !isMathable(rightType))
				throw new IllegalArgumentException(opName + " cannot be applied to operand types " + leftType + " and " + rightType);
			resultType = DBugUtils.BOOLEAN;
			if (DBugUtils.DOUBLE.isAssignableFrom(TypeTokens.get().unwrap(leftType))
				|| DBugUtils.DOUBLE.isAssignableFrom(TypeTokens.get().unwrap(rightType)))
				maxType = DBugUtils.DOUBLE;
			else if (DBugUtils.FLOAT.isAssignableFrom(TypeTokens.get().unwrap(leftType))
				|| DBugUtils.FLOAT.isAssignableFrom(TypeTokens.get().unwrap(rightType)))
				maxType = DBugUtils.FLOAT;
			else if (DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(leftType))
				|| DBugUtils.LONG.isAssignableFrom(TypeTokens.get().unwrap(rightType)))
				maxType = DBugUtils.LONG;
			else
				maxType = DBugUtils.INT;
			break;
		default:
			throw new IllegalArgumentException("Unrecognized binary operation: " + opName);
		}

		BiFunction<Object, Object, Object> op;
		boolean transitive;
		switch (opName) {
		case "+":
			transitive = true;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.DOUBLE)
					return number(v1).doubleValue() + number(v2).doubleValue();
				else if (resultType == DBugUtils.FLOAT)
					return number(v1).floatValue() + number(v2).floatValue();
				else if (resultType == DBugUtils.LONG)
					return number(v1).longValue() + number(v2).longValue();
				else
					return number(v1).intValue() + number(v2).intValue();
			};
			break;
		case "-":
			transitive = false;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.DOUBLE)
					return number(v1).doubleValue() - number(v2).doubleValue();
				else if (resultType == DBugUtils.FLOAT)
					return number(v1).floatValue() - number(v2).floatValue();
				else if (resultType == DBugUtils.LONG)
					return number(v1).longValue() - number(v2).longValue();
				else
					return number(v1).intValue() - number(v2).intValue();
			};
			break;
		case "*":
			transitive = true;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.DOUBLE)
					return number(v1).doubleValue() * number(v2).doubleValue();
				else if (resultType == DBugUtils.FLOAT)
					return number(v1).floatValue() * number(v2).floatValue();
				else if (resultType == DBugUtils.LONG)
					return number(v1).longValue() * number(v2).longValue();
				else
					return number(v1).intValue() * number(v2).intValue();
			};
			break;
		case "/":
			transitive = false;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.DOUBLE)
					return number(v1).doubleValue() / number(v2).doubleValue();
				else if (resultType == DBugUtils.FLOAT)
					return number(v1).floatValue() / number(v2).floatValue();
				else if (resultType == DBugUtils.LONG)
					return number(v1).longValue() / number(v2).longValue();
				else
					return number(v1).intValue() / number(v2).intValue();
			};
			break;
		case "%":
			transitive = false;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.DOUBLE)
					return number(v1).doubleValue() % number(v2).doubleValue();
				else if (resultType == DBugUtils.FLOAT)
					return number(v1).floatValue() % number(v2).floatValue();
				else if (resultType == DBugUtils.LONG)
					return number(v1).longValue() % number(v2).longValue();
				else
					return number(v1).intValue() % number(v2).intValue();
			};
			break;
		case "<<":
			transitive = false;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.LONG)
					return number(v1).longValue() << number(v2).longValue();
				else
					return number(v1).intValue() << number(v2).intValue();
			};
			break;
		case ">>":
			transitive = false;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.LONG)
					return number(v1).longValue() >> number(v2).longValue();
				else
					return number(v1).intValue() >> number(v2).intValue();
			};
			break;
		case ">>>":
			transitive = false;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.LONG)
					return number(v1).longValue() >>> number(v2).longValue();
				else
					return number(v1).intValue() >>> number(v2).intValue();
			};
			break;
		case "&":
			transitive = true;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.LONG)
					return number(v1).longValue() & number(v2).longValue();
				else
					return number(v1).intValue() & number(v2).intValue();
			};
			break;
		case "|":
			transitive = true;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.LONG)
					return number(v1).longValue() | number(v2).longValue();
				else
					return number(v1).intValue() | number(v2).intValue();
			};
			break;
		case "^":
			transitive = true;
			op = (v1, v2) -> {
				if (resultType == DBugUtils.LONG)
					return number(v1).longValue() ^ number(v2).longValue();
				else
					return number(v1).intValue() ^ number(v2).intValue();
			};
			break;
		case "&&":
			transitive = true;
			op = (v1, v2) -> ((Boolean) v1).booleanValue() && ((Boolean) v2).booleanValue();
			break;
		case "||":
			transitive = true;
			op = (v1, v2) -> ((Boolean) v1).booleanValue() || ((Boolean) v2).booleanValue();
			break;
		case ">":
			transitive = false;
			op = (v1, v2) -> {
				if (maxType == DBugUtils.DOUBLE)
					return number(v1).doubleValue() > number(v2).doubleValue();
				else if (maxType == DBugUtils.FLOAT)
					return number(v1).floatValue() > number(v2).floatValue();
				else if (maxType == DBugUtils.LONG)
					return number(v1).longValue() > number(v2).longValue();
				else
					return number(v1).intValue() > number(v2).intValue();
			};
			break;
		case "<":
			transitive = false;
			op = (v1, v2) -> {
				if (maxType == DBugUtils.DOUBLE)
					return number(v1).doubleValue() < number(v2).doubleValue();
				else if (maxType == DBugUtils.FLOAT)
					return number(v1).floatValue() < number(v2).floatValue();
				else if (maxType == DBugUtils.LONG)
					return number(v1).longValue() < number(v2).longValue();
				else
					return number(v1).intValue() < number(v2).intValue();
			};
			break;
		case ">=":
			transitive = false;
			op = (v1, v2) -> {
				if (maxType == DBugUtils.DOUBLE)
					return number(v1).doubleValue() >= number(v2).doubleValue();
				else if (maxType == DBugUtils.FLOAT)
					return number(v1).floatValue() >= number(v2).floatValue();
				else if (maxType == DBugUtils.LONG)
					return number(v1).longValue() >= number(v2).longValue();
				else
					return number(v1).intValue() >= number(v2).intValue();
			};
			break;
		case "<=":
			transitive = false;
			op = (v1, v2) -> {
				if (maxType == DBugUtils.DOUBLE)
					return number(v1).doubleValue() <= number(v2).doubleValue();
				else if (maxType == DBugUtils.FLOAT)
					return number(v1).floatValue() <= number(v2).floatValue();
				else if (maxType == DBugUtils.LONG)
					return number(v1).longValue() + number(v2).longValue();
				else
					return number(v1).intValue() + number(v2).intValue();
			};
			break;
		default:
			throw new IllegalArgumentException("Unrecognized binary operation: " + opName);
		}
		return new BinaryMathExpression<A, Object>(left, right, (TypeToken<Object>) resultType, opName, op, transitive);
	}

	private static Number number(Object mathable) {
		if (mathable instanceof Character)
			return Integer.valueOf(((Character) mathable).charValue());
		else
			return (Number) mathable;
	}
}
