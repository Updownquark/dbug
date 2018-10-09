package org.dbug.expression;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ParserRuleContext;
import org.dbug.DBugEvent;
import org.dbug.config.DBugConfiguredAnchor;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class ExternalExpressionSpec extends DBugAntlrExpression {
	private static final Pattern REF_PATTERN = Pattern
		.compile("(?<className>[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)*)\\.(?<methodName>[a-zA-Z0-9_]+)([(])([a-zA-Z0-9_]+,?\\s*)*[)]");

	private final String theClassName;
	private final String theMethodName;
	private final List<String> theArgNames;

	public ExternalExpressionSpec(ParserRuleContext ctx, String ref) throws DBugParseException {
		super(ctx);
		Matcher m = REF_PATTERN.matcher(ref);
		if (!m.matches())
			throw new DBugParseException("Could not parse ref " + ref);
		theClassName = m.group("className");
		theMethodName = m.group("methodName");
		LinkedList<String> argNames = new LinkedList<>();
		for (int i = m.groupCount();; i--) {
			String g = m.group(i);
			if (g.equals("("))
				break;
			argNames.addFirst(g);
		}
		theArgNames = argNames.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(argNames);
	}

	public String getClassName() {
		return theClassName;
	}

	public String getMethodName() {
		return theMethodName;
	}

	public List<String> getArgNames() {
		return theArgNames;
	}

	public <A> Expression<A, ?> getFor(DBugParseEnv<A> env) throws DBugParseException {
		Class<?> clazz = env.getType(theClassName);
		List<Expression<A, ?>> args = new ArrayList<>(theArgNames.size());
		Class<?>[] argTypes = new Class[theArgNames.size()];
		int i = 0;
		for (String arg : theArgNames) {
			args.add(env.parseDependency(TypeTokens.get().OBJECT, arg));
			argTypes[i++] = TypeTokens.getRawType(args.get(args.size() - 1).getResultType());
		}
		Method method;
		try {
			method = clazz.getDeclaredMethod(theMethodName, argTypes);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new DBugParseException("Could not find method " + theClassName + "." + theMethodName + theArgNames, e);
		}
		return new ExternalExpression<>(method, args);
	}

	private static class ExternalExpression<A, T> implements Expression<A, T> {
		private final Method theMethod;
		private final TypeToken<T> theType;
		private final List<Expression<A, ?>> theArgs;

		ExternalExpression(Method method, List<Expression<A, ?>> args) {
			this(method, args, (TypeToken<T>) TypeToken.of(method.getGenericReturnType()));
		}

		ExternalExpression(Method method, List<Expression<A, ?>> args, TypeToken<T> type) {
			theMethod = method;
			theArgs = args;
			theType = type;
		}

		@Override
		public TypeToken<T> getResultType() {
			return theType;
		}

		@Override
		public T evaluate(DBugEvent<A> event) throws DBugParseException {
			Object[] args = new Object[theArgs.size()];
			for (int i = 0; i < args.length; i++)
				args[i] = theArgs.get(i).evaluate(event);
			try {
				return (T) theMethod.invoke(null, args);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new DBugParseException("Could not invoke method " + theMethod, e);
			}
		}

		@Override
		public Expression<A, ? extends T> given(DBugConfiguredAnchor<A> anchor, boolean evalDynamic, boolean cacheable)
			throws DBugParseException {
			List<Expression<A, ?>> args = new ArrayList<>(theArgs.size());
			boolean allConst = true;
			boolean anyDiff = false;
			for (int i = 0; i < theArgs.size(); i++) {
				Expression<A, ?> arg = theArgs.get(i).given(anchor, evalDynamic, cacheable);
				allConst &= arg instanceof ConstantExpression;
				anyDiff |= arg != theArgs.get(i);
				args.add(arg);
			}
			if (allConst && cacheable) {
				Object[] argValues = new Object[args.size()];
				for (int i = 0; i < args.size(); i++)
					argValues[i] = ((ConstantExpression<A, ?>) args.get(i)).value;
				try {
					return new ConstantExpression<>(theType, (T) theMethod.invoke(null, argValues));
				} catch (IllegalAccessException | IllegalArgumentException e) {
					throw new DBugParseException("Could not invoke method " + theMethod, e);
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof RuntimeException)
						throw (RuntimeException) e.getTargetException();
					else
						throw new IllegalStateException("Could not invoke method " + theMethod, e.getTargetException());
				}
			} else if (!anyDiff)
				return this;
			else
				return new ExternalExpression<>(theMethod, args, theType);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder().append(theMethod.getDeclaringClass().getName()).append('.').append(theMethod.getName());
			str.append('(');
			boolean first = true;
			for (Expression<A, ?> arg : theArgs) {
				if (!first)
					str.append(", ");
				first = false;
				str.append(arg);
			}
			str.append(')');
			return str.toString();
		}
	}
}
