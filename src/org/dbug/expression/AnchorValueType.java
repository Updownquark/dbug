package org.dbug.expression;

import org.dbug.DBugAnchorType;
import org.dbug.DBugEvent;
import org.dbug.config.DBugConfiguredAnchor;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class AnchorValueType<A> implements Expression<A, A> {
	private final DBugAnchorType<A> theType;

	public AnchorValueType(DBugAnchorType<A> type) {
		theType = type;
	}

	@Override
	public TypeToken<A> getResultType() {
		return TypeTokens.get().of(theType.getType());
	}

	@Override
	public A evaluate(DBugEvent<A> event) throws DBugParseException {
		return event.getAnchor().getValue();
	}

	@Override
	public Expression<A, A> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		return new ConstantExpression<>(getResultType(), anchor.getValue());
	}

	@Override
	public int hashCode() {
		return 0;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof AnchorValueType;
	}

	@Override
	public String toString() {
		return "value";
	}
}
