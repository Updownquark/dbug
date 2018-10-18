package org.dbug.expression;

import java.lang.reflect.Array;
import java.util.Arrays;

import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugConfigEvent;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class ArrayInitializerByValueExpression<A, T> implements Expression<A, T> {
	private final TypeToken<T> theType;
	private final Expression<A, ?>[] theElements;

	public ArrayInitializerByValueExpression(TypeToken<T> type, Expression<A, ?>[] elements) {
		theType = type;
		theElements = elements;
	}

	@Override
	public TypeToken<T> getResultType() {
		return theType;
	}

	@Override
	public T evaluate(DBugConfigEvent<A> event) throws DBugParseException {
		Object array = Array.newInstance(TypeTokens.getRawType(theType).getComponentType(), theElements.length);
		for (int i = 0; i < theElements.length; i++)
			Array.set(array, i, theElements[i].evaluate(event));
		return (T) array;
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		Expression<A, ?>[] elements = new Expression[theElements.length];
		boolean allConst = true, anyDiff = false;
		for (int i = 0; i < theElements.length; i++) {
			elements[i] = theElements[i].given(anchor, evalDynamic, cacheable);
			allConst &= elements[i] instanceof ConstantExpression;
			anyDiff |= elements[i] != theElements[i];
		}
		if (cacheable && allConst) {
			Object array = Array.newInstance(TypeTokens.getRawType(theType).getComponentType(), theElements.length);
			for (int i = 0; i < elements.length; i++)
				Array.set(array, i, ((ConstantExpression<A, ?>) elements[i]).value);
			return new ConstantExpression<>(theType, (T) array);
		} else if (anyDiff)
			return new ArrayInitializerByValueExpression<>(theType, elements);
		else
			return this;
	}

	@Override
	public int hashCode() {
		return theType.hashCode() * 71 + Arrays.hashCode(theElements);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof ArrayInitializerByValueExpression))
			return false;
		ArrayInitializerByValueExpression<?, ?> other = (ArrayInitializerByValueExpression<?, ?>) obj;
		return theType.equals(other.theType) && Arrays.equals(theElements, other.theElements);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("new ").append(theType).append('{');
		for (int i = 0; i < theElements.length; i++) {
			if (i > 0)
				str.append(", ");
			str.append(theElements[i]);
		}
		str.append('}');
		return str.toString();
	}
}
