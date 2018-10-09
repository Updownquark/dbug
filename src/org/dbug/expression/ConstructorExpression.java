package org.dbug.expression;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import org.dbug.DBugEvent;
import org.dbug.config.DBugConfiguredAnchor;

import com.google.common.reflect.TypeToken;

public class ConstructorExpression<A, T> implements Expression<A, T> {
	private final TypeToken<T> theType;
	private final Constructor<?> theConstructor;
	private final Expression<A, ?>[] theArguments;

	public ConstructorExpression(TypeToken<T> type, Constructor<?> constructor, Expression<A, ?>[] arguments) {
		theType = type;
		theConstructor = constructor;
		theArguments = arguments;
	}

	@Override
	public TypeToken<T> getResultType() {
		return theType;
	}

	@Override
	public T evaluate(DBugEvent<A> event) throws DBugParseException {
		Object[] args = new Object[theArguments.length];
		for (int a = 0; a < args.length; a++)
			args[a] = theArguments[a].evaluate(event);
		try {
			return (T) theConstructor.newInstance(args);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new DBugParseException("Could not invoke constructor " + theConstructor, e);
		}
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		Expression<A, ?>[] args = new Expression[theArguments.length];
		boolean allConst = true, anyDiff = false;
		for (int a = 0; a < args.length; a++) {
			args[a] = theArguments[a].given(anchor, evalDynamic, cacheable);
			allConst = args[a] instanceof ConstantExpression;
			anyDiff |= args[a] != theArguments[a];
		}
		if (cacheable && allConst) {
			Object[] argValues = new Object[theArguments.length];
			for (int a = 0; a < argValues.length; a++)
				argValues[a] = ((ConstantExpression<A, ?>) args[a]).value;
			try {
				return new ConstantExpression<>(theType, //
					(T) theConstructor.newInstance(argValues));
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new DBugParseException("Could not invoke constructor " + theConstructor, e);
			}
		} else if (anyDiff)
			return new ConstructorExpression<>(theType, theConstructor, args);
		else
			return this;
	}

	@Override
	public int hashCode() {
		return theType.hashCode() * 71 + Arrays.hashCode(theArguments);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof ConstructorExpression))
			return false;
		ConstructorExpression<?, ?> other = (ConstructorExpression<?, ?>) obj;
		return theType.equals(other.theType) && Arrays.equals(theArguments, other.theArguments);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("new ").append(theType).append('(');
		for (int i = 0; i < theArguments.length; i++) {
			if (i > 0)
				str.append(", ");
			str.append(theArguments[i]);
		}
		str.append(')');
		return str.toString();
	}
}
