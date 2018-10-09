package org.dbug.expression;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;

import org.dbug.DBugEvent;
import org.dbug.config.DBugConfiguredAnchor;

import com.google.common.reflect.TypeToken;

public class MethodExpression<A, S, T> implements Expression<A, T> {
	private final Expression<A, ? extends S> theSource;
	private final Method theMethod;
	private final Expression<A, ?>[] theArgs;
	private final TypeToken<T> theType;

	public MethodExpression(Expression<A, ? extends S> source, Expression<A, ?>[] args, Method method) {
		this(source, method, args, (TypeToken<T>) source.getResultType().resolveType(method.getGenericReturnType()));
	}

	private MethodExpression(Expression<A, ? extends S> source, Method method, Expression<A, ?>[] args, TypeToken<T> type) {
		theSource = source;
		theMethod = method;
		theArgs = args;
		theType = type;
	}

	@Override
	public TypeToken<T> getResultType() {
		return theType;
	}

	@Override
	public T evaluate(DBugEvent<A> event) throws DBugParseException {
		S source = theSource.evaluate(event);
		if (source == null && (theMethod.getModifiers() & Modifier.STATIC) == 0) {
			throw new DBugParseException("Relation is null for method expression");
		}
		Object[] argValues = new Object[theArgs.length];
		for (int a = 0; a < argValues.length; a++) {
			argValues[a] = theArgs[a].evaluate(event);
		}
		try {
			return (T) theMethod.invoke(source, argValues);
		} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
			throw new DBugParseException("Could not evaluate method " + theMethod, e);
		}
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		boolean allConst = true;
		boolean anyDiff = false;
		Expression<A, ?>[] args = new Expression[theArgs.length];
		for (int a = 0; a < args.length; a++) {
			args[a] = theArgs[a].given(anchor, evalDynamic, cacheable);
			allConst &= args[a] instanceof ConstantExpression;
			anyDiff |= args[a] != theArgs[a];
		}
		Expression<A, ? extends S> source = theSource.given(anchor, evalDynamic, cacheable);
		if (cacheable && allConst//
			&& (Modifier.isStatic(theMethod.getModifiers()) || source instanceof ConstantExpression)) {
			Object[] argValues = new Object[args.length];
			for (int a = 0; a < args.length; a++)
				argValues[a] = ((ConstantExpression<A, ?>) args[a]).value;
			try {
				S srcVal = Modifier.isStatic(theMethod.getModifiers()) ? null : ((ConstantExpression<A, S>) source).value;
				return new ConstantExpression<>(theType, //
					(T) theMethod.invoke(srcVal, argValues));
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new DBugParseException("Could not evaluate method " + theMethod, e);
			}
		} else if (args.length == 1 && theMethod.getName().equals("equals") && source.equals(args[0])) {
			return (Expression<A, ? extends T>) ConstantExpression.TRUE();
		} else if (anyDiff)
			return new MethodExpression<>(source, theMethod, args, theType);
		else
			return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(theSource, theMethod, theArgs);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof MethodExpression))
			return false;
		MethodExpression<?, ?, ?> other = (MethodExpression<?, ?, ?>) obj;
		return theMethod.equals(other.theMethod) && Objects.equals(theSource, other.theSource) && Arrays.deepEquals(theArgs, other.theArgs);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder(theSource.toString()).append('.').append(theMethod.getName()).append('(');
		for (int a = 0; a < theArgs.length; a++) {
			if (a > 0)
				str.append(", ");
			str.append(theArgs[a]);
		}
		str.append(')');
		return str.toString();
	}
}
