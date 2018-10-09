package org.dbug.expression;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.dbug.expression.DBugParser.ConditionalExpressionContext;
import org.dbug.expression.DBugParser.LiteralContext;

import com.google.common.reflect.TypeToken;

class ExpressionTypes {
	private ExpressionTypes() {}

	static class CompoundExpression extends DBugAntlrExpression {
		private final List<DBugAntlrExpression> theChildren;

		public CompoundExpression(ParserRuleContext ctx, List<DBugAntlrExpression> children) {
			super(ctx);
			theChildren = children;
		}

		public List<DBugAntlrExpression> getChildren() {
			return theChildren;
		}
	}

	static class QualifiedName extends DBugAntlrExpression {
		private final ExpressionTypes.QualifiedName theQualifier;
		private final String theName;

		public QualifiedName(ParserRuleContext ctx, QualifiedName qualifier, String name) {
			super(ctx);
			theQualifier = qualifier;
			theName = name;
		}

		public ExpressionTypes.QualifiedName getQualifier() {
			return theQualifier;
		}

		public String getName() {
			return theName;
		}

		@Override
		public String print() {
			if (theQualifier != null)
				return theQualifier + "." + theName;
			else
				return theName;
		}
	}

	static abstract class Type extends DBugAntlrExpression {
		public Type(ParserRuleContext ctx) {
			super(ctx);
		}
	}

	static class PrimitiveType extends Type {
		private final Class<?> thePrimType;

		public PrimitiveType(ParserRuleContext ctx, Class<?> type) {
			super(ctx);
			if (!type.isPrimitive())
				throw new IllegalArgumentException();
			thePrimType = type;
		}

		public Class<?> getType() {
			return thePrimType;
		}

		@Override
		public String print() {
			if (thePrimType == Void.TYPE)
				return "void";
			else if (thePrimType == Boolean.TYPE)
				return "boolean";
			else if (thePrimType == Character.TYPE)
				return "char";
			else if (thePrimType == Byte.TYPE)
				return "byte";
			else if (thePrimType == Short.TYPE)
				return "short";
			else if (thePrimType == Integer.TYPE)
				return "int";
			else if (thePrimType == Long.TYPE)
				return "long";
			else if (thePrimType == Float.TYPE)
				return "float";
			else if (thePrimType == Double.TYPE)
				return "double";
			else
				throw new IllegalStateException("Unrecognized primitive type: " + thePrimType.getName());
		}
	}

	static class ClassType extends ExpressionTypes.Type {
		private final String theName;
		private final List<Type> theTypeArgs;

		public ClassType(ParserRuleContext ctx, String name, List<Type> typeArgs) {
			super(ctx);
			theName = name;
			theTypeArgs = typeArgs;
		}

		public String getName() {
			return theName;
		}

		public List<Type> getTypeArgs() {
			return theTypeArgs;
		}

		@Override
		public String print() {
			if (theTypeArgs == null)
				return theName;
			StringBuilder print = new StringBuilder(theName);
			print.append('<');
			for (int i = 0; i < theTypeArgs.size(); i++) {
				if (i > 0)
					print.append(", ");
				print.append(theTypeArgs.get(i).print());
			}
			print.append('>');
			return print.toString();
		}
	}

	static class ArrayType extends Type {
		private final Type theComponentType;
		private final int theDimension;

		public ArrayType(ParserRuleContext ctx, Type componentType, int dims) {
			super(ctx);
			theComponentType = componentType;
			theDimension = dims;
		}

		public Type getComponentType() {
			return theComponentType;
		}

		public int getDimension() {
			return theDimension;
		}

		@Override
		public String print() {
			StringBuilder print = new StringBuilder(theComponentType.print());
			for (int i = 0; i < theDimension; i++)
				print.append("[]");
			return print.toString();
		}
	}

	static class MemberAccess extends DBugAntlrExpression {
		private final DBugAntlrExpression theMemberContext;
		private final String theName;

		public MemberAccess(ParserRuleContext ctx, DBugAntlrExpression memberContext, String name) {
			super(ctx);
			theMemberContext = memberContext;
			theName = name;
		}

		public DBugAntlrExpression getMemberContext() {
			return theMemberContext;
		}

		public String getName() {
			return theName;
		}

		@Override
		public String print() {
			return theMemberContext.print() + "." + theName;
		}
	}

	static class FieldAccess extends MemberAccess {
		public FieldAccess(ParserRuleContext ctx, DBugAntlrExpression methodCtx, String name) {
			super(ctx, methodCtx, name);
		}
	}

	static class MethodInvocation extends MemberAccess {
		private final List<Type> theTypeArguments;
		private final List<DBugAntlrExpression> theArguments;

		public MethodInvocation(ParserRuleContext ctx, DBugAntlrExpression methodCtx, String name, List<Type> typeArgs,
			List<DBugAntlrExpression> args) {
			super(ctx, methodCtx, name);
			theTypeArguments = typeArgs;
			theArguments = args;
		}

		public List<Type> getTypeArguments() {
			return theTypeArguments;
		}

		public List<DBugAntlrExpression> getArguments() {
			return theArguments;
		}

		@Override
		public String print() {
			StringBuilder print = new StringBuilder();
			if (getMemberContext() != null)
				print.append(getMemberContext().print()).append('.');
			print.append(getName());
			print.append('(');
			for (int i = 0; i < theArguments.size(); i++) {
				if (i != 0)
					print.append(", ");
				print.append(theArguments.get(i).print());
			}
			print.append(')');
			return print.toString();
		}
	}

	static class Parenthetic extends DBugAntlrExpression {
		private final DBugAntlrExpression theContents;

		public Parenthetic(ParserRuleContext ctx, DBugAntlrExpression contents) {
			super(ctx);
			theContents = contents;
		}

		public DBugAntlrExpression getContents() {
			return theContents;
		}
	}

	static class Conditional extends DBugAntlrExpression {
		private final DBugAntlrExpression theCondition;
		private final DBugAntlrExpression theAffirmative;
		private final DBugAntlrExpression theNegative;

		public Conditional(ConditionalExpressionContext ctx, DBugAntlrExpression condition, DBugAntlrExpression affirmative,
			DBugAntlrExpression negative) {
			super(ctx);
			theCondition = condition;
			theAffirmative = affirmative;
			theNegative = negative;
		}

		public DBugAntlrExpression getCondition() {
			return theCondition;
		}

		public DBugAntlrExpression getAffirmative() {
			return theAffirmative;
		}

		public DBugAntlrExpression getNegative() {
			return theNegative;
		}

		@Override
		public String print() {
			return "(" + theCondition.print() + ")" + " ? " + "(" + theAffirmative.print() + ")" + " : " + "(" + theNegative.print()
				+ ")";
		}
	}

	static class Operation extends DBugAntlrExpression {
		private final String theName;
		private final DBugAntlrExpression thePrimaryOperand;

		public Operation(ParserRuleContext ctx, String name, DBugAntlrExpression left) {
			super(ctx);
			theName = name;
			thePrimaryOperand = left;
		}

		public String getName() {
			return theName;
		}

		public DBugAntlrExpression getPrimaryOperand() {
			return thePrimaryOperand;
		}
	}

	static class BinaryOperation extends Operation {
		private final DBugAntlrExpression theRight;

		public BinaryOperation(ParserRuleContext ctx, String name, DBugAntlrExpression left, DBugAntlrExpression right) {
			super(ctx, name, left);
			theRight = right;
		}

		public DBugAntlrExpression getRight() {
			return theRight;
		}

		@Override
		public String print() {
			return "(" + getPrimaryOperand().print() + ") " + getName() + " (" + theRight.print() + ")";
		}
	}

	static class UnaryOperation extends Operation {
		private boolean isPreOp;

		public UnaryOperation(ParserRuleContext ctx, String name, boolean preOp, DBugAntlrExpression operand) {
			super(ctx, name, operand);
			isPreOp = preOp;
		}

		public boolean isPreOp() {
			return isPreOp;
		}

		public void setPreOp(boolean isPreOp) {
			this.isPreOp = isPreOp;
		}

		@Override
		public String print() {
			if (isPreOp)
				return getName() + " (" + getPrimaryOperand().print() + ")";
			else
				return "(" + getPrimaryOperand().print() + ") " + getName();
		}
	}

	static class Cast extends DBugAntlrExpression {
		private final Type theType;
		private final DBugAntlrExpression theValue;

		public Cast(ParserRuleContext ctx, Type type, DBugAntlrExpression value) {
			super(ctx);
			theType = type;
			theValue = value;
		}

		public Type getType() {
			return theType;
		}

		public DBugAntlrExpression getValue() {
			return theValue;
		}

		@Override
		public String print() {
			return new StringBuilder().append('(').append(theType.print()).append(") ").append(theValue.print()).toString();
		}
	}

	static class ArrayAccess extends DBugAntlrExpression {
		private final DBugAntlrExpression theArray;
		private final DBugAntlrExpression theIndex;

		public ArrayAccess(ParserRuleContext ctx, DBugAntlrExpression array, DBugAntlrExpression index) {
			super(ctx);
			theArray = array;
			theIndex = index;
		}

		public DBugAntlrExpression getArray() {
			return theArray;
		}

		public DBugAntlrExpression getIndex() {
			return theIndex;
		}

		@Override
		public String print() {
			return new StringBuilder().append('(').append(theArray.print()).append(")[").append(theIndex.print()).append(']').toString();
		}
	}

	static class ArrayInitializer extends DBugAntlrExpression {
		private final ArrayType theType;
		private final List<DBugAntlrExpression> theSizes;
		private final List<DBugAntlrExpression> theElements;

		public ArrayInitializer(ParserRuleContext ctx, Type componentType, int dimension, List<DBugAntlrExpression> sizes,
			List<DBugAntlrExpression> elements) {
			super(ctx);
			theType = new ArrayType(ctx, componentType, dimension);
			theSizes = sizes;
			theElements = elements;
		}

		public ArrayType getType() {
			return theType;
		}

		public List<DBugAntlrExpression> getSizes() {
			return theSizes;
		}

		public List<DBugAntlrExpression> getElements() {
			return theElements;
		}

		@Override
		public String print() {
			StringBuilder print = new StringBuilder().append("new ").append(theType.print());
			if (theSizes != null) {
				for (DBugAntlrExpression size : theSizes)
					print.append('[').append(size.print()).append(']');
			} else {
				print.append('{');
				for (int i = 0; i < theElements.size(); i++) {
					if (i > 0)
						print.append(", ");
					print.append(theElements.get(i).print());
				}
				print.append('}');
			}
			return print.toString();
		}
	}

	static class Constructor extends DBugAntlrExpression {
		private final ClassType theType;
		private final List<Type> theConstructorTypeArguments;
		private final List<DBugAntlrExpression> theArguments;

		public Constructor(ParserRuleContext ctx, ClassType type, List<Type> constructorTypeArgs,
			List<DBugAntlrExpression> args) {
			super(ctx);
			theType = type;
			theConstructorTypeArguments = constructorTypeArgs;
			theArguments = args;
		}

		public ClassType getType() {
			return theType;
		}

		/** @return The type arguments for the constructor itself, not the type to be constructed */
		public List<Type> getConstructorTypeArguments() {
			return theConstructorTypeArguments;
		}

		public List<DBugAntlrExpression> getArguments() {
			return theArguments;
		}

		@Override
		public String print() {
			StringBuilder print = new StringBuilder().append("new ").append(theType.print());
			print.append('(');
			for (int i = 0; i < theArguments.size(); i++) {
				if (i != 0)
					print.append(", ");
				print.append(theArguments.get(i).print());
			}
			print.append(')');
			return print.toString();
		}
	}

	static class UnitValue extends DBugAntlrExpression {
		private final DBugAntlrExpression theValue;
		private final String theUnit;

		public UnitValue(ParserRuleContext ctx, DBugAntlrExpression value, String unit) {
			super(ctx);
			theValue = value;
			theUnit = unit;
		}

		public DBugAntlrExpression getValue() {
			return theValue;
		}

		public String getUnit() {
			return theUnit;
		}

		@Override
		public String print() {
			return new StringBuilder().append('(').append(theValue.print()).append(") ").append(theUnit).toString();
		}
	}

	static class Placeholder extends DBugAntlrExpression {
		private final String theName;

		public Placeholder(ParserRuleContext ctx, String name) {
			super(ctx);
			theName = name;
		}

		public String getName() {
			return theName;
		}

		@Override
		public String print() {
			return toString();
		}
	}

	static abstract class Literal<T> extends DBugAntlrExpression {
		private final TypeToken<? extends T> theType;
		private final T theValue;

		public Literal(LiteralContext ctx) {
			super(ctx);
			theType = getType(ctx);
			theValue = parseValue(ctx);
		}

		public TypeToken<? extends T> getType() {
			return theType;
		}

		public T getValue() {
			return theValue;
		}

		protected abstract TypeToken<? extends T> getType(LiteralContext ctx);

		protected abstract T parseValue(ParserRuleContext ctx);

		@Override
		public String print() {
			return super.print() + ": " + theValue + " (" + theType + ")";
		}
	}

	static class NullLiteral extends Literal<Void> {
		public NullLiteral(LiteralContext ctx) {
			super(ctx);
		}

		@Override
		protected TypeToken<? extends Void> getType(LiteralContext ctx) {
			return TypeToken.of(Void.class);
		}

		@Override
		protected Void parseValue(ParserRuleContext ctx) {
			return null;
		}
	}

	static abstract class NumberLiteral extends Literal<Number> {
		public NumberLiteral(LiteralContext ctx) {
			super(ctx);
		}
	}

	static class IntegerLiteral extends NumberLiteral {
		public IntegerLiteral(LiteralContext ctx) {
			super(ctx);
		}

		@Override
		protected TypeToken<? extends Number> getType(LiteralContext ctx) {
			String text = ctx.getText();
			if (text.endsWith("l") || text.endsWith("L"))
				return TypeToken.of(Long.TYPE);
			else
				return TypeToken.of(Integer.TYPE);
		}

		@Override
		protected Number parseValue(ParserRuleContext ctx) {
			String text = ctx.getText();
			boolean isLong = getType().getRawType() == Long.TYPE;
			boolean isHex = text.startsWith("0x") || text.startsWith("0X");
			boolean isBin = !isHex && (text.startsWith("0b") || text.startsWith("0B"));
			boolean isOct = !isHex && !isBin && text.length() > 1 && text.startsWith("0");

			StringBuilder content = new StringBuilder(text);
			if (isLong)
				content.deleteCharAt(content.length() - 1);
			if (isHex || isBin)
				content.delete(0, 2);
			else if (isOct)
				content.deleteCharAt(0);

			for (int i = content.length() - 1; i >= 0; i--) {
				if (content.charAt(i) == '_') {
					content.deleteCharAt(i);
					i--;
				}
			}
			int radix;
			if (isHex)
				radix = 16;
			else if (isOct)
				radix = 8;
			else if (isBin)
				radix = 2;
			else
				radix = 10;

			if (isLong)
				return Long.valueOf(content.toString(), radix);
			else
				return Integer.valueOf(content.toString(), radix);
		}
	}

	static class FloatLiteral extends NumberLiteral {
		public FloatLiteral(LiteralContext ctx) {
			super(ctx);
		}

		@Override
		protected TypeToken<? extends Number> getType(LiteralContext ctx) {
			String text = ctx.getText();
			if (text.endsWith("f") || text.endsWith("F"))
				return TypeToken.of(Float.TYPE);
			else
				return TypeToken.of(Double.TYPE);
		}

		@Override
		protected Number parseValue(ParserRuleContext ctx) {
			String text = ctx.getText();
			boolean isFloat = getType().getRawType() == Float.TYPE;
			if (isFloat)
				return Float.parseFloat(text);
			else
				return Double.parseDouble(text);
		}
	}

	static class BooleanLiteral extends ExpressionTypes.Literal<Boolean> {
		public BooleanLiteral(LiteralContext ctx) {
			super(ctx);
		}

		@Override
		protected TypeToken<Boolean> getType(LiteralContext ctx) {
			return TypeToken.of(Boolean.TYPE);
		}

		@Override
		protected Boolean parseValue(ParserRuleContext ctx) {
			return Boolean.valueOf(ctx.getText());
		}
	}

	static class CharLiteral extends ExpressionTypes.Literal<Character> {
		public CharLiteral(LiteralContext ctx) {
			super(ctx);
		}

		@Override
		protected TypeToken<Character> getType(LiteralContext ctx) {
			return TypeToken.of(Character.TYPE);
		}

		@Override
		protected Character parseValue(ParserRuleContext ctx) {
			String text = ctx.getText();
			return unescapeJavaString(text.substring(1, text.length() - 1)).charAt(0); // Take off the quotes and unescape
		}
	}

	static class StringLiteral extends ExpressionTypes.Literal<String> {
		protected StringLiteral(LiteralContext ctx) {
			super(ctx);
		}

		@Override
		protected TypeToken<String> getType(LiteralContext ctx) {
			return TypeToken.of(String.class);
		}

		@Override
		protected String parseValue(ParserRuleContext ctx) {
			String text = ctx.getText();
			return unescapeJavaString(text.substring(1, text.length() - 1)); // Take off the quotes and unescape
		}
	}

	/**
	 * <p>
	 * Copied from <a href="https://gist.github.com/uklimaschewski/6741769">https://gist.github.com/uklimaschewski/6741769</a>
	 * </p>
	 *
	 * Unescapes a string that contains standard Java escape sequences.
	 * <ul>
	 * <li><strong>&#92;b &#92;f &#92;n &#92;r &#92;t &#92;" &#92;'</strong> : BS, FF, NL, CR, TAB, double and single quote.</li>
	 * <li><strong>&#92;X &#92;XX &#92;XXX</strong> : Octal character specification (0 - 377, 0x00 - 0xFF).</li>
	 * <li><strong>&#92;uXXXX</strong> : Hexadecimal based Unicode character.</li>
	 * </ul>
	 *
	 * @param st A string optionally containing standard java escape sequences.
	 * @return The translated string.
	 */
	public static String unescapeJavaString(String st) {
		StringBuilder sb = new StringBuilder(st.length());

		for (int i = 0; i < st.length(); i++) {
			char ch = st.charAt(i);
			if (ch == '\\') {
				char nextChar = (i == st.length() - 1) ? '\\' : st.charAt(i + 1);
				// Octal escape?
				if (nextChar >= '0' && nextChar <= '7') {
					String code = "" + nextChar;
					i++;
					if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
						code += st.charAt(i + 1);
						i++;
						if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
							code += st.charAt(i + 1);
							i++;
						}
					}
					sb.append((char) Integer.parseInt(code, 8));
					continue;
				}
				switch (nextChar) {
				case '\\':
					ch = '\\';
					break;
				case 'b':
					ch = '\b';
					break;
				case 'f':
					ch = '\f';
					break;
				case 'n':
					ch = '\n';
					break;
				case 'r':
					ch = '\r';
					break;
				case 't':
					ch = '\t';
					break;
				case '\"':
					ch = '\"';
					break;
				case '\'':
					ch = '\'';
					break;
				// Hex Unicode: u????
				case 'u':
					if (i >= st.length() - 5) {
						ch = 'u';
						break;
					}
					int code = Integer.parseInt("" + st.charAt(i + 2) + st.charAt(i + 3) + st.charAt(i + 4) + st.charAt(i + 5), 16);
					sb.append(Character.toChars(code));
					i += 5;
					continue;
				}
				i++;
			}
			sb.append(ch);
		}
		return sb.toString();
	}
}
