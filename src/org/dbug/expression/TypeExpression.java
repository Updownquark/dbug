package org.dbug.expression;

import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugConfigEvent;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class TypeExpression<A, T> implements Expression<A, T> {
	private final TypeToken<T> theResultType;

	public TypeExpression(Class<T> type) {
		theResultType = TypeTokens.get().of(type);
	}

	@Override
	public TypeToken<T> getResultType() {
		return theResultType;
	}

	@Override
	public T evaluate(DBugConfigEvent<A> event) throws DBugParseException {
		throw new DBugParseException("Type expressions (" + this + ") do not have a value");
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		return this;
	}

	@Override
	public int hashCode() {
		return theResultType.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof TypeExpression && theResultType.equals(((TypeExpression<?, ?>) obj).getResultType());
	}

	@Override
	public String toString() {
		return theResultType.toString();
	}
}
