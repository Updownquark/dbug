package org.dbug.expression;

import java.util.Optional;

import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugConfigEvent;

import com.google.common.reflect.TypeToken;

public interface Expression<A, T> {
	Optional<?> NULL = Optional.of(new Object());

	static <T> Optional<T> NULL() {
		return (Optional<T>) NULL;
	}

	TypeToken<T> getResultType();

	T evaluate(DBugConfigEvent<A> event) throws DBugParseException;

	Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable) throws DBugParseException;
}
