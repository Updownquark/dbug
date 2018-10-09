package org.dbug.expression;

import java.util.LinkedHashSet;

import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public class DBugUtils {
	/**
	 * Checks whether a variable of type <code>left</code> may be assigned a value of type <code>right</code>. This is more lenient than
	 * {@link TypeToken#isAssignableFrom(TypeToken)} because this method allows for auto (un)boxing and conversion between compatible
	 * primitive types (e.g. float f=0).
	 *
	 * @param left The type of the variable to assign
	 * @param right The type of the value being assigned
	 * @return Whether the assignment is allowable
	 */
	public static boolean isAssignableFrom(TypeToken<?> left, TypeToken<?> right) {
		// Check auto boxing/unboxing first
		if (left.isAssignableFrom(right) || left.isAssignableFrom(right.wrap()) || left.wrap().isAssignableFrom(right))
			return true;
		// Handle primitive conversions
		Class<?> primTypeLeft = left.unwrap().getRawType();
		if (!primTypeLeft.isPrimitive() || !TypeTokens.get().of(Number.class).isAssignableFrom(right.wrap()))
			return false;
		Class<?> primTypeRight = right.unwrap().getRawType();
		if (primTypeLeft == primTypeRight)
			return true;
		if (primTypeLeft == Double.TYPE)
			return primTypeRight == Number.class || primTypeRight == Float.TYPE || primTypeRight == Long.TYPE
				|| primTypeRight == Integer.TYPE || primTypeRight == Short.TYPE || primTypeRight == Byte.TYPE;
		else if (primTypeLeft == Float.TYPE)
			return primTypeRight == Long.TYPE || primTypeRight == Integer.TYPE || primTypeRight == Short.TYPE || primTypeRight == Byte.TYPE;
		else if (primTypeLeft == Long.TYPE)
			return primTypeRight == Integer.TYPE || primTypeRight == Short.TYPE || primTypeRight == Byte.TYPE;
		else if (primTypeLeft == Integer.TYPE)
			return primTypeRight == Short.TYPE || primTypeRight == Byte.TYPE;
		else if (primTypeLeft == Short.TYPE)
			return primTypeRight == Byte.TYPE;
		return false;
	}

	/**
	 * @param left The type of the variable to assign
	 * @param right Type type of the value being assigned
	 * @return Whether the assignment is allowable with an explicit cast
	 */
	public static boolean canConvert(TypeToken<?> left, TypeToken<?> right) {
		TypeToken<Number> numType = TypeTokens.get().of(Number.class);
		return left.isAssignableFrom(right) || (numType.isAssignableFrom(left.wrap()) && numType.isAssignableFrom(right.wrap()));
	}

	/**
	 * @param <T> The compile-time type to make an array type of
	 * @param type The type to make an array type of
	 * @return An array type whose component type is <code>type</code>
	 */
	public static <T> TypeToken<T[]> arrayTypeOf(TypeToken<T> type) {
		return new TypeToken<T[]>() {}.where(new TypeParameter<T>() {}, type);
	}

	/**
	 * For types that pass {@link #isAssignableFrom(TypeToken, TypeToken)}, this method converts the given value to the correct run-time
	 * type
	 *
	 * @param <T> The compile-time type to convert to
	 * @param type The type to convert to
	 * @param value The value to convert
	 * @return The converted value
	 */
	public static <T> T convert(TypeToken<T> type, Object value) {
		if (type.isPrimitive() && value == null)
			throw new NullPointerException("null cannot be assigned to " + type);
		if (value == null || type.getRawType().isInstance(value))
			return (T) value;
		Class<?> primType = type.unwrap().getRawType();
		if (!primType.isPrimitive() || !(value instanceof Number))
			throw new IllegalArgumentException(value.getClass() + " cannot be converted to " + type);
		if (primType == Double.TYPE)
			return (T) Double.valueOf(((Number) value).doubleValue());
		else if (primType == Float.TYPE)
			return (T) Float.valueOf(((Number) value).floatValue());
		else if (primType == Long.TYPE)
			return (T) Long.valueOf(((Number) value).longValue());
		else if (primType == Integer.TYPE)
			return (T) Integer.valueOf(((Number) value).intValue());
		else if (primType == Short.TYPE)
			return (T) Short.valueOf(((Number) value).shortValue());
		else if (primType == Byte.TYPE)
			return (T) Byte.valueOf(((Number) value).byteValue());
		else
			throw new IllegalArgumentException(value.getClass() + " cannot be converted to " + type);
	}

	/**
	 * @param type1 The first type
	 * @param type2 The second type
	 * @return The most specific type that is assignable from both argument types
	 */
	public static TypeToken<?> getCommonType(TypeToken<?> type1, TypeToken<?> type2) {
		if (type1.equals(type2) || DBugUtils.isAssignableFrom(type1, type2))
			return type1;
		if (DBugUtils.isAssignableFrom(type2, type1))
			return type2;
		// TODO could be smarter about this, looking at interfaces, etc.
		LinkedHashSet<TypeToken<?>> type1Path = new LinkedHashSet<>();
		TypeToken<?> copy = type1;
		while (copy != null) {
			type1Path.add(copy);
			copy = copy.getSupertype((Class<Object>) copy.getRawType().getSuperclass());
		}
		copy = type2;
		while (copy != null) {
			if (type1Path.contains(copy))
				return copy;
			copy = copy.getSupertype((Class<Object>) copy.getRawType().getSuperclass());
		}
		return TypeTokens.get().of(Object.class);
	}

	/** Primitive double type */
	public static final TypeToken<Double> DOUBLE = TypeTokens.get().of(Double.TYPE);
	/** Primitive float type */
	public static final TypeToken<Float> FLOAT = TypeTokens.get().of(Float.TYPE);
	/** Primitive long type */
	public static final TypeToken<Long> LONG = TypeTokens.get().of(Long.TYPE);
	/** Primitive int type */
	public static final TypeToken<Integer> INT = TypeTokens.get().of(Integer.TYPE);
	/** Primitive short type */
	public static final TypeToken<Short> SHORT = TypeTokens.get().of(Short.TYPE);
	/** Primitive byte type */
	public static final TypeToken<Byte> BYTE = TypeTokens.get().of(Byte.TYPE);
	/** Primitive char type */
	public static final TypeToken<Character> CHAR = TypeTokens.get().of(Character.TYPE);
	/** Primitive boolean type */
	public static final TypeToken<Boolean> BOOLEAN = TypeTokens.get().of(Boolean.TYPE);

	/**
	 * @param type The type to test
	 * @return Whether math operations like add and multiply can be applied to the given type
	 */
	public static boolean isMathable(TypeToken<?> type) {
		TypeToken<?> prim = TypeTokens.get().unwrap(type);
		return prim.equals(DOUBLE) || prim.equals(FLOAT) || prim.equals(LONG) || prim.equals(INT) || prim.equals(SHORT) || prim.equals(BYTE)
			|| prim.equals(CHAR);
	}

	/**
	 * @param type The type to test
	 * @return Whether integer-specific math operations like bitwise shifts can be applied to the given type
	 */
	public static boolean isIntMathable(TypeToken<?> type) {
		TypeToken<?> prim = TypeTokens.get().unwrap(type);
		return prim.equals(LONG) || prim.equals(INT) || prim.equals(SHORT) || prim.equals(BYTE) || prim.equals(CHAR);
	}
}
