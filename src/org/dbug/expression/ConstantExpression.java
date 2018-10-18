package org.dbug.expression;

import java.util.Objects;

import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugConfigEvent;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class ConstantExpression<A, T> implements Expression<A, T> {
	private static final ConstantExpression<?, Boolean> TRUE = new ConstantExpression<>(TypeTokens.get().BOOLEAN, true);
	private static final ConstantExpression<?, Boolean> FALSE = new ConstantExpression<>(TypeTokens.get().BOOLEAN, false);

	public static final <A> ConstantExpression<A, Boolean> TRUE() {
		return (ConstantExpression<A, Boolean>) TRUE;
	}

	public static final <A> ConstantExpression<A, Boolean> FALSE() {
		return (ConstantExpression<A, Boolean>) FALSE;
	}

	public final TypeToken<T> type;
	public final T value;

	public ConstantExpression(TypeToken<T> type, T value) {
		this.type = type;
		this.value = value;
	}

	@Override
	public TypeToken<T> getResultType() {
		return type;
	}

	@Override
	public T evaluate(DBugConfigEvent<A> event) {
		return value;
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable) {
		return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ConstantExpression && Objects.equals(value, ((ConstantExpression<?, ?>) obj).value);
	}

	@Override
	public String toString() {
		return String.valueOf(value);
	}
}
