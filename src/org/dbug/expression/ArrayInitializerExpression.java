package org.dbug.expression;

import java.lang.reflect.Array;
import java.util.Arrays;

import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugConfigEvent;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class ArrayInitializerExpression<A, T> implements Expression<A, T> {
	private final Expression<A, ? extends Number>[] theSizes;
	private final TypeToken<T> theArrayType;

	public ArrayInitializerExpression(Expression<A, ? extends Number>[] sizes, TypeToken<T> arrayType) {
		theSizes = sizes;
		theArrayType = arrayType;
	}

	@Override
	public TypeToken<T> getResultType() {
		return theArrayType;
	}

	@Override
	public T evaluate(DBugConfigEvent<A> event) throws DBugParseException {
		int[] sizes = new int[theSizes.length];
		for (int s = 0; s < sizes.length; s++)
			sizes[s] = theSizes[s].evaluate(event).intValue();
		return (T) makeArray(TypeTokens.getRawType(theArrayType).getComponentType(), sizes, 0);
	}

	private static Object makeArray(Class<?> componentType, int[] sizes, int dim) {
		int size = sizes[dim];
		Object array = Array.newInstance(componentType, size);
		if (dim < sizes.length - 1) {
			Class<?> elementClass = componentType.getComponentType();
			for (int i = 0; i < size; i++)
				Array.set(array, i, makeArray(elementClass, sizes, dim + 1));
		}
		return array;
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		Expression<A, ? extends Number>[] sizes = new Expression[theSizes.length];
		boolean allConst = true, anyDiff = false;
		for (int s = 0; s < sizes.length; s++) {
			sizes[s] = theSizes[s].given(anchor, evalDynamic, cacheable);
			allConst &= sizes[s] instanceof ConstantExpression;
			anyDiff |= sizes[s] != theSizes[s];
		}
		if (cacheable && allConst) {
			int[] sizeInts = new int[sizes.length];
			for (int s = 0; s < sizes.length; s++)
				sizeInts[s] = ((ConstantExpression<T, ? extends Number>) sizes[s]).value.intValue();
			return new ConstantExpression<>(theArrayType, (T) makeArray(TypeTokens.getRawType(theArrayType), sizeInts, 0));
		} else if (anyDiff)
			return new ArrayInitializerExpression<>(sizes, theArrayType);
		else
			return this;
	}

	@Override
	public int hashCode() {
		return theArrayType.hashCode() * 71 + Arrays.hashCode(theSizes);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof ArrayInitializerExpression))
			return false;
		ArrayInitializerExpression<?, ?> other = (ArrayInitializerExpression<?, ?>) obj;
		return theArrayType.equals(other.theArrayType) && Arrays.equals(theSizes, other.theSizes);
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder("new ").append(theArrayType);
		for (int i = 0; i < theSizes.length; i++)
			s.append('[').append(theSizes[i]).append(']');
		return s.toString();
	}
}
