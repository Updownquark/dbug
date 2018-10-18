package org.dbug.expression;

import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugConfigEvent;

import com.google.common.reflect.TypeToken;

public class ConditionalExpression<A, T> implements Expression<A, T> {
	private final TypeToken<T> theType;
	private final Expression<A, Boolean> theCondition;
	private final Expression<A, ? extends T> theAffirmative;
	private final Expression<A, ? extends T> theNegative;

	public ConditionalExpression(TypeToken<T> type, Expression<A, Boolean> condition, Expression<A, ? extends T> affirmative,
		Expression<A, ? extends T> negative) {
		theType = type;
		theCondition = condition;
		theAffirmative = affirmative;
		theNegative = negative;
	}

	@Override
	public TypeToken<T> getResultType() {
		return theType;
	}

	@Override
	public T evaluate(DBugConfigEvent<A> event) throws DBugParseException {
		boolean condition = theCondition.evaluate(event);
		if (condition)
			return theAffirmative.evaluate(event);
		else
			return theNegative.evaluate(event);
	}

	@Override
	public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
		throws DBugParseException {
		Expression<A, ? extends Boolean> condition = theCondition.given(anchor, evalDynamic, cacheable);
		if (cacheable && condition instanceof ConstantExpression) {
			if (((ConstantExpression<A, Boolean>) condition).value)
				return theAffirmative.given(anchor, evalDynamic, cacheable);
			else
				return theNegative.given(anchor, evalDynamic, cacheable);
		}
		Expression<A, ? extends T> affirmative = theAffirmative.given(anchor, evalDynamic, cacheable);
		Expression<A, ? extends T> negative = theNegative.given(anchor, evalDynamic, cacheable);
		if (affirmative == theAffirmative && negative == theNegative)
			return this;
		return new ConditionalExpression<>(theType, theCondition, affirmative, negative);
	}
}
