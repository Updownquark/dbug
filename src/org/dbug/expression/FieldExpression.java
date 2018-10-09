package org.dbug.expression;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.google.common.reflect.TypeToken;

public class FieldExpression<A, S, T> extends UnaryOperation<A, S, T> {
	private final Field theField;

	public FieldExpression(Expression<A, ? extends S> source, Field field) {
		this(source, field, (TypeToken<T>) source.getResultType().resolveType(field.getGenericType()));
	}

	private FieldExpression(Expression<A, ? extends S> source, Field field, TypeToken<T> type) {
		super(source, type);
		theField = field;
	}

	@Override
	protected T evaluate(S sourceValue) throws DBugParseException {
		if (sourceValue == null && (theField.getModifiers() & Modifier.STATIC) == 0) {
			throw new DBugParseException("Relation is null for field expression");
		}
		try {
			return (T) theField.get(sourceValue);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new DBugParseException("Failed to evalute field " + theField.getName(), e);
		}
	}

	@Override
	protected UnaryOperation<A, S, T> copy(Expression<A, ? extends S> sourceCopy) {
		return new FieldExpression<>(sourceCopy, theField, getResultType());
	}

	@Override
	public int hashCode() {
		return getSource().hashCode() * 17 + theField.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof FieldExpression))
			return false;
		FieldExpression<?, ?, ?> other = (FieldExpression<?, ?, ?>) obj;
		return theField.equals(other.theField) && getSource().equals(other.getSource());
	}

	@Override
	public String toString() {
		return getSource() + "." + theField.getName();
	}
}
