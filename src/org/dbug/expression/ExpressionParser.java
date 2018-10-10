package org.dbug.expression;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.dbug.expression.DBugParser.AdditiveExpressionContext;
import org.dbug.expression.DBugParser.AmbiguousNameContext;
import org.dbug.expression.DBugParser.AndExpressionContext;
import org.dbug.expression.DBugParser.ArgumentListContext;
import org.dbug.expression.DBugParser.ArrayAccessContext;
import org.dbug.expression.DBugParser.ArrayAccess_lf_primaryContext;
import org.dbug.expression.DBugParser.ArrayAccess_lfno_primaryContext;
import org.dbug.expression.DBugParser.ArrayCreationExpressionContext;
import org.dbug.expression.DBugParser.ArrayTypeContext;
import org.dbug.expression.DBugParser.AssignmentContext;
import org.dbug.expression.DBugParser.AssignmentExpressionContext;
import org.dbug.expression.DBugParser.CastExpressionContext;
import org.dbug.expression.DBugParser.ClassInstanceCreationExpressionContext;
import org.dbug.expression.DBugParser.ClassTypeContext;
import org.dbug.expression.DBugParser.ConditionalAndExpressionContext;
import org.dbug.expression.DBugParser.ConditionalExpressionContext;
import org.dbug.expression.DBugParser.ConditionalOrExpressionContext;
import org.dbug.expression.DBugParser.ConstantExpressionContext;
import org.dbug.expression.DBugParser.DimsContext;
import org.dbug.expression.DBugParser.EqualityExpressionContext;
import org.dbug.expression.DBugParser.ExclusiveOrExpressionContext;
import org.dbug.expression.DBugParser.ExpressionContext;
import org.dbug.expression.DBugParser.ExpressionNameContext;
import org.dbug.expression.DBugParser.FieldAccessContext;
import org.dbug.expression.DBugParser.FloatingPointTypeContext;
import org.dbug.expression.DBugParser.InclusiveOrExpressionContext;
import org.dbug.expression.DBugParser.IntegralTypeContext;
import org.dbug.expression.DBugParser.LeftHandSideContext;
import org.dbug.expression.DBugParser.LiteralContext;
import org.dbug.expression.DBugParser.MethodInvocationContext;
import org.dbug.expression.DBugParser.MethodInvocation_lfno_primaryContext;
import org.dbug.expression.DBugParser.MethodNameContext;
import org.dbug.expression.DBugParser.MultiplicativeExpressionContext;
import org.dbug.expression.DBugParser.NumericTypeContext;
import org.dbug.expression.DBugParser.PackageOrTypeNameContext;
import org.dbug.expression.DBugParser.PlaceholderContext;
import org.dbug.expression.DBugParser.PostDecrementExpressionContext;
import org.dbug.expression.DBugParser.PostIncrementExpressionContext;
import org.dbug.expression.DBugParser.PostfixExpressionContext;
import org.dbug.expression.DBugParser.PreDecrementExpressionContext;
import org.dbug.expression.DBugParser.PreIncrementExpressionContext;
import org.dbug.expression.DBugParser.PrimaryContext;
import org.dbug.expression.DBugParser.PrimaryNoNewArray_lf_primaryContext;
import org.dbug.expression.DBugParser.PrimaryNoNewArray_lf_primary_lfno_arrayAccess_lf_primaryContext;
import org.dbug.expression.DBugParser.PrimaryNoNewArray_lfno_arrayAccessContext;
import org.dbug.expression.DBugParser.PrimaryNoNewArray_lfno_primaryContext;
import org.dbug.expression.DBugParser.PrimaryNoNewArray_lfno_primary_lfno_arrayAccess_lfno_primaryContext;
import org.dbug.expression.DBugParser.PrimitiveTypeContext;
import org.dbug.expression.DBugParser.ReferenceTypeContext;
import org.dbug.expression.DBugParser.RelationalExpressionContext;
import org.dbug.expression.DBugParser.ShiftExpressionContext;
import org.dbug.expression.DBugParser.TypeArgumentsContext;
import org.dbug.expression.DBugParser.TypeContext;
import org.dbug.expression.DBugParser.TypeNameContext;
import org.dbug.expression.DBugParser.UnannPrimitiveTypeContext;
import org.dbug.expression.DBugParser.UnaryExpressionContext;
import org.dbug.expression.DBugParser.UnaryExpressionNotPlusMinusContext;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class ExpressionParser {
	private static final Set<String> ASSIGN_BIOPS = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(//
		"=", "+=", "-=", "*=", "/=", "%=", "|=", "&=", "^=", "++", "--")));

	public static DBugAntlrExpression compile(String expression) throws DBugParseException {
		try {
			// lexer splits input into tokens
			ANTLRInputStream input = new ANTLRInputStream(expression);
			TokenStream tokens = new CommonTokenStream(new DBugLexer(input));

			// parser generates abstract syntax tree
			DBugParser parser = new DBugParser(tokens);

			// acquire parse result
			ParseTreeWalker walker = new ParseTreeWalker();
			DBugCompiler compiler = new DBugCompiler(parser);
			walker.walk(compiler, parser.expression());
			return compiler.getExpression();
		} catch (RecognitionException e) {
			throw new DBugParseException("Parsing failed for " + expression, e);
		} catch (IllegalStateException e) {
			throw new DBugParseException("Parsing failed for " + expression, e);
		}
	}

	private static class DBugCompiler extends DBugBaseListener {
		private final DBugParser theParser;
		private final Map<ParserRuleContext, DBugAntlrExpression> theDanglingExpressions;

		public DBugCompiler(DBugParser parser) {
			theParser = parser;
			theDanglingExpressions = new HashMap<>();
		}

		public DBugAntlrExpression getExpression() {
			if (theDanglingExpressions.size() != 1)
				throw new IllegalStateException();
			return theDanglingExpressions.values().iterator().next();
		}

		private void push(DBugAntlrExpression expression) {
			push(expression, expression.getContext());
		}

		private void push(DBugAntlrExpression expression, ParserRuleContext ctx) {
			if (ctx == null)
				throw new NullPointerException();
			if (ctx.exception != null)
				throw ctx.exception;
			theDanglingExpressions.put(ctx, expression);
		}

		private void ascend(ParserRuleContext inner, ParserRuleContext outer) {
			if (outer.exception != null)
				throw outer.exception;
			push(pop(inner), outer);
		}

		private DBugAntlrExpression pop(ParserRuleContext ctx) {
			if (ctx == null)
				throw new NullPointerException();
			DBugAntlrExpression exp = theDanglingExpressions.remove(ctx);
			if (exp == null)
				throw new IllegalStateException(
					"Expression " + ctx.getText() + ", type " + ctx.getClass().getSimpleName() + " was not evaluated. Perhaps exit"
						+ ctx.getClass().getSimpleName().substring(0, ctx.getClass().getSimpleName().length() - "Context".length())
						+ "() is not implemented.");
			return exp;
		}

		@Override
		public void visitErrorNode(ErrorNode arg0) {
			throw new IllegalStateException("Unexpected token ( " + arg0.getSymbol().getText() + ", type "
				+ theParser.getVocabulary().getDisplayName(arg0.getSymbol().getType()) + " ) at position "
				+ arg0.getSymbol().getStartIndex());
		}

		@Override
		public void visitTerminal(TerminalNode node) {
			// Don't think there's anything to do here, but might be useful to have it for debugging sometime
		}

		@Override
		public void exitEveryRule(ParserRuleContext ctx) {
			// Don't think there's anything to do here, but might be useful to have it for debugging sometime
		}

		@Override
		public void exitLiteral(LiteralContext ctx) {
			switch (ctx.start.getType()) {
			case DBugParser.NullLiteral:
				push(new ExpressionTypes.NullLiteral(ctx));
				break;
			case DBugParser.IntegerLiteral:
				push(new ExpressionTypes.IntegerLiteral(ctx));
				break;
			case DBugParser.FloatingPointLiteral:
				push(new ExpressionTypes.FloatLiteral(ctx));
				break;
			case DBugParser.BooleanLiteral:
				push(new ExpressionTypes.BooleanLiteral(ctx));
				break;
			case DBugParser.CharacterLiteral:
				push(new ExpressionTypes.CharLiteral(ctx));
				break;
			case DBugParser.StringLiteral:
				push(new ExpressionTypes.StringLiteral(ctx));
				break;
			default:
				throw new IllegalStateException(
					"Unrecognized literal type: " + theParser.getVocabulary().getDisplayName(ctx.start.getType())
					+ " (" + ctx.start.getType() + ") at position " + ctx.start.getStartIndex());
			}
		}

		@Override
		public void exitExpressionName(ExpressionNameContext ctx) {
			if (ctx.ambiguousName() != null)
				push(new ExpressionTypes.QualifiedName(ctx, (ExpressionTypes.QualifiedName) pop(ctx.ambiguousName()),
					ctx.Identifier().getText()));
			else
				push(new ExpressionTypes.QualifiedName(ctx, null, ctx.Identifier().getText()));
		}

		@Override
		public void exitAmbiguousName(AmbiguousNameContext ctx) {
			if (ctx.ambiguousName() != null)
				push(new ExpressionTypes.QualifiedName(ctx, (ExpressionTypes.QualifiedName) pop(ctx.ambiguousName()),
					ctx.Identifier().getText()));
			else
				push(new ExpressionTypes.QualifiedName(ctx, null, ctx.Identifier().getText()));
		}

		@Override
		public void exitAssignment(AssignmentContext ctx) {
			push(new ExpressionTypes.BinaryOperation(ctx, ctx.assignmentOperator().getText(), pop(ctx.leftHandSide()),
				pop(ctx.expression())));
		}

		@Override
		public void exitAssignmentExpression(AssignmentExpressionContext ctx) {
			if (ctx.conditionalExpression() != null)
				ascend(ctx.conditionalExpression(), ctx);
			else
				ascend(ctx.assignment(), ctx);
		}

		@Override
		public void exitLeftHandSide(LeftHandSideContext ctx) {
			if (ctx.expressionName() != null)
				ascend(ctx.expressionName(), ctx);
			else if (ctx.fieldAccess() != null)
				ascend(ctx.fieldAccess(), ctx);
			else
				ascend(ctx.arrayAccess(), ctx);
		}

		@Override
		public void exitFieldAccess(FieldAccessContext ctx) {
			push(new ExpressionTypes.FieldAccess(ctx, pop(ctx.primary()), ctx.Identifier().getText()));
		}

		@Override
		public void exitArrayAccess(ArrayAccessContext ctx) {
			DBugAntlrExpression result;
			result = new ExpressionTypes.ArrayAccess(ctx,
				pop(ctx.expressionName() != null ? ctx.expressionName() : ctx.primaryNoNewArray_lfno_arrayAccess()),
				pop(ctx.expression(0)));
			for (int i = 0; i < ctx.primaryNoNewArray_lf_arrayAccess().size(); i++)
				result = new ExpressionTypes.ArrayAccess(ctx, result, pop(ctx.expression(i + 1)));
			push(result);
		}

		@Override
		public void exitArrayAccess_lfno_primary(ArrayAccess_lfno_primaryContext ctx) {
			DBugAntlrExpression result;
			result = new ExpressionTypes.ArrayAccess(ctx, pop(
				ctx.expressionName() != null ? ctx.expressionName() : ctx.primaryNoNewArray_lfno_primary_lfno_arrayAccess_lfno_primary()),
				pop(ctx.expression(0)));
			for (int i = 0; i < ctx.primaryNoNewArray_lfno_primary_lf_arrayAccess_lfno_primary().size(); i++)
				result = new ExpressionTypes.ArrayAccess(ctx, result, pop(ctx.expression(i + 1)));
			push(result);
		}

		@Override
		public void exitClassInstanceCreationExpression(ClassInstanceCreationExpressionContext ctx) {
			StringBuilder typeName = new StringBuilder(ctx.Identifier(0).getText());
			for (int i = 1; i < ctx.Identifier().size(); i++)
				typeName.append('.').append(ctx.Identifier(i).getText());
			List<ExpressionTypes.Type> classTypeArgs;
			if (ctx.typeArgumentsOrDiamond() == null)
				classTypeArgs = null;
			else if (ctx.typeArgumentsOrDiamond().typeArguments() == null)
				classTypeArgs = Collections.emptyList();
			else
				classTypeArgs = parseTypeArguments(ctx.typeArgumentsOrDiamond().typeArguments());
			push(new ExpressionTypes.Constructor(ctx, new ExpressionTypes.ClassType(ctx, typeName.toString(), classTypeArgs),
				parseTypeArguments(ctx.typeArguments()), parseArguments(ctx.argumentList())));
		}

		@Override
		public void exitCastExpression(CastExpressionContext ctx) {
			if (ctx.primitiveType() != null)
				push(new ExpressionTypes.Cast(ctx, (ExpressionTypes.PrimitiveType) pop(ctx.primitiveType()), pop(ctx.unaryExpression())));
			else
				push(
					new ExpressionTypes.Cast(ctx, (ExpressionTypes.Type) pop(ctx.referenceType()), pop(ctx.unaryExpressionNotPlusMinus())));
		}

		@Override
		public void exitEqualityExpression(EqualityExpressionContext ctx) {
			if (ctx.equalityExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, getOperator(ctx), pop(ctx.equalityExpression()),
					pop(ctx.relationalExpression())));
			else
				ascend(ctx.relationalExpression(), ctx);
		}

		private String getOperator(EqualityExpressionContext ctx) {
			return ((TerminalNode) ctx.getChild(1)).getText();
		}

		@Override
		public void exitRelationalExpression(RelationalExpressionContext ctx) {
			if (ctx.referenceType() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, "instanceof", pop(ctx.relationalExpression()), pop(ctx.referenceType())));
			else if (ctx.relationalExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, getOperator(ctx), pop(ctx.relationalExpression()),
					pop(ctx.shiftExpression())));
			else
				ascend(ctx.shiftExpression(), ctx);
		}

		private String getOperator(RelationalExpressionContext ctx) {
			return ((TerminalNode) ctx.getChild(1)).getText();
		}

		@Override
		public void exitConditionalOrExpression(ConditionalOrExpressionContext ctx) {
			if (ctx.conditionalOrExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, "||", pop(ctx.conditionalOrExpression()),
					pop(ctx.conditionalAndExpression())));
			else
				ascend(ctx.conditionalAndExpression(), ctx);
		}

		@Override
		public void exitConditionalAndExpression(ConditionalAndExpressionContext ctx) {
			if (ctx.conditionalAndExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, "&&", pop(ctx.conditionalAndExpression()), pop(ctx.inclusiveOrExpression())));
			else
				ascend(ctx.inclusiveOrExpression(), ctx);
		}

		@Override
		public void exitInclusiveOrExpression(InclusiveOrExpressionContext ctx) {
			if (ctx.inclusiveOrExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, "|", pop(ctx.inclusiveOrExpression()), pop(ctx.exclusiveOrExpression())));
			else
				ascend(ctx.exclusiveOrExpression(), ctx);
		}

		@Override
		public void exitExclusiveOrExpression(ExclusiveOrExpressionContext ctx) {
			if (ctx.exclusiveOrExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, "^", pop(ctx.exclusiveOrExpression()), pop(ctx.andExpression())));
			else
				ascend(ctx.andExpression(), ctx);
		}

		@Override
		public void exitAndExpression(AndExpressionContext ctx) {
			if (ctx.andExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, "&", pop(ctx.andExpression()), pop(ctx.equalityExpression())));
			else
				ascend(ctx.equalityExpression(), ctx);
		}

		@Override
		public void exitConditionalExpression(ConditionalExpressionContext ctx) {
			if (ctx.expression() != null)
				push(new ExpressionTypes.Conditional(ctx, pop(ctx.conditionalOrExpression()), pop(ctx.expression()),
					pop(ctx.conditionalExpression())));
			else
				ascend(ctx.conditionalOrExpression(), ctx);
		}

		@Override
		public void exitShiftExpression(ShiftExpressionContext ctx) {
			if (ctx.shiftExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, getOperator(ctx), pop(ctx.shiftExpression()), pop(ctx.additiveExpression())));
			else
				ascend(ctx.additiveExpression(), ctx);
		}

		private String getOperator(ShiftExpressionContext ctx) {
			return ((TerminalNode) ctx.getChild(1)).getText();
		}

		@Override
		public void exitAdditiveExpression(AdditiveExpressionContext ctx) {
			if (ctx.additiveExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, getOperator(ctx), pop(ctx.additiveExpression()),
					pop(ctx.multiplicativeExpression())));
			else
				ascend(ctx.multiplicativeExpression(), ctx);
		}

		private String getOperator(AdditiveExpressionContext ctx) {
			return ((TerminalNode) ctx.getChild(1)).getText();
		}

		@Override
		public void exitMultiplicativeExpression(MultiplicativeExpressionContext ctx) {
			if (ctx.multiplicativeExpression() != null)
				push(new ExpressionTypes.BinaryOperation(ctx, getOperator(ctx), pop(ctx.multiplicativeExpression()),
					pop(ctx.unaryExpression())));
			else
				ascend(ctx.unaryExpression(), ctx);
		}

		private String getOperator(MultiplicativeExpressionContext ctx) {
			return ((TerminalNode) ctx.getChild(1)).getText();
		}

		@Override
		public void exitUnaryExpression(UnaryExpressionContext ctx) {
			if (ctx.preIncrementExpression() != null)
				ascend(ctx.preIncrementExpression(), ctx);
			else if (ctx.preDecrementExpression() != null)
				ascend(ctx.preDecrementExpression(), ctx);
			else if (ctx.unaryExpression() != null)
				push(new ExpressionTypes.UnaryOperation(ctx, getOperator(ctx), true, pop(ctx.unaryExpression())));
			else
				ascend(ctx.unaryExpressionNotPlusMinus(), ctx);
		}

		private String getOperator(UnaryExpressionContext ctx) {
			return ((TerminalNode) ctx.getChild(0)).getText();
		}

		@Override
		public void exitUnaryExpressionNotPlusMinus(UnaryExpressionNotPlusMinusContext ctx) {
			if (ctx.postfixExpression() != null)
				ascend(ctx.postfixExpression(), ctx);
			else if (ctx.castExpression() != null)
				ascend(ctx.castExpression(), ctx);
			else
				push(new ExpressionTypes.UnaryOperation(ctx, getOperator(ctx), true, pop(ctx.unaryExpression())));
		}

		private String getOperator(UnaryExpressionNotPlusMinusContext ctx) {
			return ctx.getChild(0).getText();
		}

		@Override
		public void exitPostfixExpression(PostfixExpressionContext ctx) {
			DBugAntlrExpression operand;
			if (ctx.primary() != null)
				operand = pop(ctx.primary());
			else
				operand = pop(ctx.expressionName());
			DBugAntlrExpression result = operand;
			int inc = 0, dec = 0, u = 0;
			for (int i = 1; i < ctx.getChildCount(); i++) {
				ParseTree child = ctx.getChild(i);
				if (inc < ctx.postIncrementExpression_lf_postfixExpression().size()
					&& child == ctx.postIncrementExpression_lf_postfixExpression(inc)) {
					result = new ExpressionTypes.UnaryOperation(ctx, child.getText(), false, result);
					inc++;
				} else if (dec < ctx.postDecrementExpression_lf_postfixExpression().size()
					&& child == ctx.postDecrementExpression_lf_postfixExpression(dec)) {
					result = new ExpressionTypes.UnaryOperation(ctx, child.getText(), false, result);
					dec++;
				}
			}
			push(result, ctx);
		}

		@Override
		public void exitPreIncrementExpression(PreIncrementExpressionContext ctx) {
			push(new ExpressionTypes.UnaryOperation(ctx, "++", true, pop(ctx.unaryExpression())));
		}

		@Override
		public void exitPostIncrementExpression(PostIncrementExpressionContext ctx) {
			push(new ExpressionTypes.UnaryOperation(ctx, "++", false, pop(ctx.postfixExpression())));
		}

		@Override
		public void exitPreDecrementExpression(PreDecrementExpressionContext ctx) {
			push(new ExpressionTypes.UnaryOperation(ctx, "--", true, pop(ctx.unaryExpression())));
		}

		@Override
		public void exitPostDecrementExpression(PostDecrementExpressionContext ctx) {
			push(new ExpressionTypes.UnaryOperation(ctx, "--", false, pop(ctx.postfixExpression())));
		}

		@Override
		public void exitPrimary(PrimaryContext ctx) {
			DBugAntlrExpression init;
			if (ctx.primaryNoNewArray_lfno_primary() != null)
				init = pop(ctx.primaryNoNewArray_lfno_primary());
			else
				init = pop(ctx.arrayCreationExpression());
			for (PrimaryNoNewArray_lf_primaryContext mod : ctx.primaryNoNewArray_lf_primary()) {
				init = modify(init, mod);
			}
			push(init, ctx);
		}

		private DBugAntlrExpression modify(DBugAntlrExpression init, PrimaryNoNewArray_lf_primaryContext mod) {
			if (mod.fieldAccess_lf_primary() != null)
				return new ExpressionTypes.FieldAccess(mod, init, mod.fieldAccess_lf_primary().Identifier().getText());
			else if (mod.arrayAccess_lf_primary() != null)
				return modify(init, mod.arrayAccess_lf_primary());
			else if (mod.methodInvocation_lf_primary() != null)
				return new ExpressionTypes.MethodInvocation(mod, init, mod.methodInvocation_lf_primary().Identifier().getText(),
					parseTypeArguments(mod.methodInvocation_lf_primary().typeArguments()),
					parseArguments(mod.methodInvocation_lf_primary().argumentList()));
			else
				throw new IllegalStateException("Unrecognized type of " + mod.getClass().getSimpleName() + " modifier");
		}

		private DBugAntlrExpression modify(DBugAntlrExpression init, ArrayAccess_lf_primaryContext mod) {
			init = modify(init, mod.primaryNoNewArray_lf_primary_lfno_arrayAccess_lf_primary());
			init = new ExpressionTypes.ArrayAccess(mod, init, pop(mod.expression(0)));
			for (int i = 1; i < mod.expression().size(); i++)
				init = new ExpressionTypes.ArrayAccess(mod.primaryNoNewArray_lf_primary_lf_arrayAccess_lf_primary(i - 1), init,
					pop(mod.expression(i)));
			return init;
		}

		private DBugAntlrExpression modify(DBugAntlrExpression init, PrimaryNoNewArray_lf_primary_lfno_arrayAccess_lf_primaryContext mod) {
			if (mod.fieldAccess_lf_primary() != null)
				return new ExpressionTypes.FieldAccess(mod, init, mod.fieldAccess_lf_primary().Identifier().getText());
			else if (mod.methodInvocation_lf_primary() != null)
				return new ExpressionTypes.MethodInvocation(mod, init, mod.methodInvocation_lf_primary().Identifier().getText(),
					parseTypeArguments(mod.methodInvocation_lf_primary().typeArguments()),
					parseArguments(mod.methodInvocation_lf_primary().argumentList()));
			else
				throw new IllegalStateException("Unrecognized type of " + mod.getClass().getSimpleName() + " modifier");
		}

		private ExpressionTypes.Type typeFor(DBugAntlrExpression expr) {
			if (expr instanceof ExpressionTypes.Type)
				return (ExpressionTypes.Type) expr;
			else
				return new ExpressionTypes.ClassType(expr.getContext(), ((ExpressionTypes.QualifiedName) expr).print(), null);
		}

		@Override
		public void exitPrimaryNoNewArray_lfno_primary(PrimaryNoNewArray_lfno_primaryContext ctx) {
			if (ctx.literal() != null)
				ascend(ctx.literal(), ctx);
			else if (ctx.placeholder() != null)
				ascend(ctx.placeholder(), ctx);
			else if (ctx.expression() != null)
				push(new ExpressionTypes.Parenthetic(ctx, pop(ctx.expression())));
			else if (ctx.arrayAccess_lfno_primary() != null)
				ascend(ctx.arrayAccess_lfno_primary(), ctx);
			else if (ctx.methodInvocation_lfno_primary() != null)
				ascend(ctx.methodInvocation_lfno_primary(), ctx);
			else {
				// typeName ('[' ']')* '.' 'class'
				// unannPrimitiveType ('[' ']')* '.' 'class'
				// 'void' '.' 'class'
				int dims = 0;
				for (int i = 0; i < ctx.getChildCount(); i++) {
					if (ctx.getChild(i).getText().equals("["))
						dims++;
				}
				if (dims > 0) {
					if (ctx.typeName() != null)
						push(new ExpressionTypes.ArrayType(ctx, typeFor(pop(ctx.typeName())), dims));
					else
						push(new ExpressionTypes.ArrayType(ctx, (ExpressionTypes.Type) pop(ctx.unannPrimitiveType()), dims));
				} else
					push(new ExpressionTypes.PrimitiveType(ctx, void.class));
			}
		}

		@Override
		public void exitPrimaryNoNewArray_lfno_arrayAccess(PrimaryNoNewArray_lfno_arrayAccessContext ctx) {
			if (ctx.literal() != null)
				ascend(ctx.literal(), ctx);
			else if (ctx.placeholder() != null)
				ascend(ctx.placeholder(), ctx);
			else if (ctx.expression() != null)
				push(new ExpressionTypes.Parenthetic(ctx, pop(ctx.expression())));
			else if (ctx.classInstanceCreationExpression() != null)
				ascend(ctx.classInstanceCreationExpression(), ctx);
			else if (ctx.fieldAccess() != null)
				ascend(ctx.fieldAccess(), ctx);
			else if (ctx.methodInvocation() != null)
				ascend(ctx.methodInvocation(), ctx);
			else {
				// typeName ('[' ']')* '.' 'class'
				// 'void' '.' 'class'
				int dims = 0;
				for (int i = 0; i < ctx.getChildCount(); i++) {
					if (ctx.getChild(i).getText().equals("["))
						dims++;
				}
				if (dims > 0)
					push(new ExpressionTypes.ArrayType(ctx, typeFor(pop(ctx.typeName())), dims));
				else
					push(new ExpressionTypes.PrimitiveType(ctx, void.class));
			}
		}

		@Override
		public void exitPrimaryNoNewArray_lfno_primary_lfno_arrayAccess_lfno_primary(
			PrimaryNoNewArray_lfno_primary_lfno_arrayAccess_lfno_primaryContext ctx) {
			if (ctx.literal() != null)
				ascend(ctx.literal(), ctx);
			else if (ctx.placeholder() != null)
				ascend(ctx.placeholder(), ctx);
			else if (ctx.expression() != null)
				push(new ExpressionTypes.Parenthetic(ctx, pop(ctx.expression())));
			else if (ctx.methodInvocation_lfno_primary() != null)
				ascend(ctx.methodInvocation_lfno_primary(), ctx);
			else {
				// typeName ('[' ']')* '.' 'class'
				// unannPrimitiveType ('[' ']')* '.' 'class'
				// 'void' '.' 'class'
				int dims = 0;
				for (int i = 0; i < ctx.getChildCount(); i++) {
					if (ctx.getChild(i).getText().equals("["))
						dims++;
				}
				if (dims > 0) {
					if (ctx.typeName() != null)
						push(new ExpressionTypes.ArrayType(ctx, typeFor(pop(ctx.typeName())), dims));
					else
						push(new ExpressionTypes.ArrayType(ctx, (ExpressionTypes.Type) pop(ctx.unannPrimitiveType()), dims));
				}
				push(new ExpressionTypes.PrimitiveType(ctx, void.class));
			}
		}

		@Override
		public void exitArrayCreationExpression(ArrayCreationExpressionContext ctx) {
			ExpressionTypes.Type type = (ExpressionTypes.Type) (ctx.primitiveType() != null ? pop(ctx.primitiveType())
				: pop(ctx.classType()));
			int dims = dimCount(ctx.dims());
			if (ctx.dimExprs() != null) {
				dims += ctx.dimExprs().dimExpr().size();
				List<DBugAntlrExpression> sizes = ctx.dimExprs().dimExpr().stream().map(dimExpr -> pop(dimExpr.expression()))
					.collect(Collectors.toList());
				push(new ExpressionTypes.ArrayInitializer(ctx, type, dims, sizes, null));
			} else {
				List<DBugAntlrExpression> elements = ctx.arrayInitializer().variableInitializerList().variableInitializer().stream()
					.map(varInit -> pop(varInit.expression())).collect(Collectors.toList());
				push(new ExpressionTypes.ArrayInitializer(ctx, type, dims, null, elements));
			}
		}

		private int dimCount(DimsContext dims) {
			int dim = 0;
			for (int i = 0; i < dims.getChildCount(); i++)
				if (dims.getChild(i).getText().equals("["))
					dim++;
			return dim;
		}

		@Override
		public void exitExpression(ExpressionContext ctx) {
			ascend(ctx.assignmentExpression(), ctx);
		}

		@Override
		public void exitConstantExpression(ConstantExpressionContext ctx) {
			ascend(ctx.expression(), ctx);
		}

		@Override
		public void exitMethodInvocation(MethodInvocationContext ctx) {
			push(methodFor(ctx, c -> c.argumentList(), c -> c.typeArguments(), c -> c.methodName(), c -> c.Identifier(), c -> c.typeName(),
				c -> c.expressionName(), c -> c.primary()));
		}

		@Override
		public void exitMethodInvocation_lfno_primary(MethodInvocation_lfno_primaryContext ctx) {
			push(methodFor(ctx, //
				c -> c.argumentList(), c -> c.typeArguments(), c -> c.methodName(), c -> c.Identifier(), c -> c.typeName(),
				c -> c.expressionName(), null));
		}

		private <C extends ParserRuleContext> ExpressionTypes.MethodInvocation methodFor(C ctx,
			Function<C, ArgumentListContext> argumentList, Function<C, TypeArgumentsContext> typeArguments,
			Function<C, MethodNameContext> methodName, Function<C, TerminalNode> identifier, Function<C, TypeNameContext> typeName,
			Function<C, ExpressionNameContext> expressionName, Function<C, PrimaryContext> primary) {
			ExpressionTypes.MethodInvocation exp;
			List<DBugAntlrExpression> args = parseArguments(argumentList.apply(ctx));
			List<ExpressionTypes.Type> typeArgs = typeArguments == null ? null : parseTypeArguments(typeArguments.apply(ctx));
			if (methodName != null && methodName.apply(ctx) != null) {
				exp = new ExpressionTypes.MethodInvocation(ctx, null, methodName.apply(ctx).getText(), typeArgs, args);
			} else if (typeName != null && typeName.apply(ctx) != null) {
				exp = new ExpressionTypes.MethodInvocation(ctx, pop(typeName.apply(ctx)), identifier.apply(ctx).getText(), typeArgs, args);
			} else if (expressionName != null && expressionName.apply(ctx) != null) {
				exp = new ExpressionTypes.MethodInvocation(ctx, pop(expressionName.apply(ctx)), identifier.apply(ctx).getText(), typeArgs,
					args);
			} else if (primary != null && primary.apply(ctx) != null) {
				exp = new ExpressionTypes.MethodInvocation(ctx, pop(primary.apply(ctx)), identifier.apply(ctx).getText(), typeArgs, args);
			} else {
				exp = new ExpressionTypes.MethodInvocation(ctx, null, identifier.apply(ctx).getText(), typeArgs, args);
			}
			return exp;
		}

		List<DBugAntlrExpression> parseArguments(ArgumentListContext argumentList) {
			return argumentList == null ? Collections.emptyList()
				: argumentList.expression().stream().map(x -> pop(x)).collect(Collectors.toList());
		}

		List<ExpressionTypes.Type> parseTypeArguments(TypeArgumentsContext typeArguments) {
			return typeArguments == null ? null : typeArguments.typeArgumentList().typeArgument().stream()
				.map(a -> (ExpressionTypes.Type) pop(a)).collect(Collectors.toList());
		}

		@Override
		public void exitTypeName(TypeNameContext ctx) {
			if (ctx.packageOrTypeName() != null)
				push(new ExpressionTypes.QualifiedName(ctx, (ExpressionTypes.QualifiedName) pop(ctx.packageOrTypeName()),
					ctx.Identifier().getText()));
			else
				push(new ExpressionTypes.QualifiedName(ctx, null, ctx.Identifier().getText()));
		}

		@Override
		public void exitPackageOrTypeName(PackageOrTypeNameContext ctx) {
			if (ctx.packageOrTypeName() != null)
				push(new ExpressionTypes.QualifiedName(ctx, (ExpressionTypes.QualifiedName) pop(ctx.packageOrTypeName()),
					ctx.Identifier().getText()));
			else
				push(new ExpressionTypes.QualifiedName(ctx, null, ctx.Identifier().getText()));
		}

		@Override
		public void exitType(TypeContext ctx) {
			if (ctx.primitiveType() != null)
				ascend(ctx.primitiveType(), ctx);
			else
				ascend(ctx.referenceType(), ctx);
		}

		@Override
		public void exitReferenceType(ReferenceTypeContext ctx) {
			if (ctx.classType() != null)
				ascend(ctx.classType(), ctx);
			else
				ascend(ctx.arrayType(), ctx);
		}

		@Override
		public void exitClassType(ClassTypeContext ctx) {
			push(new ExpressionTypes.ClassType(ctx, ctx.Identifier().getText(), parseTypeArguments(ctx.typeArguments())));
		}

		@Override
		public void exitIntegralType(IntegralTypeContext ctx) {
			Class<?> type;
			switch (ctx.getText()) {
			case "byte":
				type = Byte.TYPE;
				break;
			case "short":
				type = Short.TYPE;
				break;
			case "int":
				type = Integer.TYPE;
				break;
			case "long":
				type = Long.TYPE;
				break;
			case "char":
				type = Character.TYPE;
				break;
			default:
				throw new IllegalStateException("Unrecognized integral type: " + ctx.getText());
			}
			push(new ExpressionTypes.PrimitiveType(ctx, type));
		}

		@Override
		public void exitFloatingPointType(FloatingPointTypeContext ctx) {
			Class<?> type;
			switch (ctx.getText()) {
			case "float":
				type = Float.TYPE;
				break;
			case "double":
				type = Double.TYPE;
				break;
			default:
				throw new IllegalStateException("Unrecognized floating point type: " + ctx.getText());
			}
			push(new ExpressionTypes.PrimitiveType(ctx, type));
		}

		@Override
		public void exitPrimitiveType(PrimitiveTypeContext ctx) {
			if (ctx.numericType() != null)
				ascend(ctx.numericType(), ctx);
			else
				push(new ExpressionTypes.PrimitiveType(ctx, Boolean.TYPE));
		}

		@Override
		public void exitUnannPrimitiveType(UnannPrimitiveTypeContext ctx) {
			if (ctx.numericType() != null)
				ascend(ctx.numericType(), ctx);
			else
				push(new ExpressionTypes.PrimitiveType(ctx, Boolean.TYPE));
		}

		@Override
		public void exitNumericType(NumericTypeContext ctx) {
			if (ctx.integralType() != null)
				ascend(ctx.integralType(), ctx);
			else if (ctx.floatingPointType() != null)
				ascend(ctx.floatingPointType(), ctx);
			else
				throw new IllegalStateException();
		}

		@Override
		public void exitArrayType(ArrayTypeContext ctx) {
			int dim = dimCount(ctx.dims());
			DBugAntlrExpression type;
			if (ctx.primitiveType() != null)
				type = pop(ctx.primitiveType());
			else if (ctx.classType() != null)
				type = pop(ctx.classType());
			else
				throw new IllegalStateException();
			push(new ExpressionTypes.ArrayType(ctx, (ExpressionTypes.Type) type, dim));
		}

		@Override
		public void exitPlaceholder(PlaceholderContext ctx) {
			push(new ExpressionTypes.Placeholder(ctx, ctx.IntegerLiteral().getText()));
		}
	}

	public static <T, X> Expression<T, ?> parseExpression(DBugAntlrExpression parsedItem, TypeToken<X> type, DBugParseEnv<T> env)
		throws DBugParseException {
		// Sort from easiest to hardest
		// Literals first
		if (parsedItem instanceof ExternalExpressionSpec) {
			return ((ExternalExpressionSpec) parsedItem).getFor(env);
		} else if (parsedItem instanceof ExpressionTypes.NullLiteral) {
			return new ConstantExpression<>(type, null);
		} else if (parsedItem instanceof ExpressionTypes.Literal) {
			ExpressionTypes.Literal<?> literal = (ExpressionTypes.Literal<?>) parsedItem;
			return new ConstantExpression<>((TypeToken<Object>) literal.getType(), literal.getValue());
			// Now easy operations
		} else if (parsedItem instanceof ExpressionTypes.Parenthetic) {
			return evaluateTypeChecked(((ExpressionTypes.Parenthetic) parsedItem).getContents(), type, env);
		} else if (parsedItem instanceof ExpressionTypes.CompoundExpression) {
			throw new DBugParseException("Compound expressions not supported here");
		} else if (parsedItem instanceof ExpressionTypes.Operation) {
			ExpressionTypes.Operation op = (ExpressionTypes.Operation) parsedItem;
			boolean actionOp = ASSIGN_BIOPS.contains(op.getName());
			if (actionOp) {
				throw new DBugParseException("Actions not supported");
			}
			Expression<T, ?> primary = evaluateTypeChecked(op.getPrimaryOperand(), TypeTokens.get().of(Object.class), env);
			if (op instanceof ExpressionTypes.UnaryOperation) {
				ExpressionTypes.UnaryOperation unOp = (ExpressionTypes.UnaryOperation) op;
				return mapUnary(primary, unOp);
			} else {
				ExpressionTypes.BinaryOperation binOp = (ExpressionTypes.BinaryOperation) op;
				if (binOp.getName().equals("instanceof")) {
					// This warrants its own block here instead of doing it in the combineBinary because the right argument is a type
					TypeToken<?> testType = evaluateType((ExpressionTypes.Type) binOp.getRight(), type, env);
					if (testType.getType() instanceof ParameterizedType)
						throw new DBugParseException(
							"instanceof checks cannot be performed against parameterized types: " + binOp.getRight());
					if (!testType.getRawType().isInterface() && !primary.getResultType().getRawType().isInterface()) {
						if (testType.isAssignableFrom(primary.getResultType())) {
							System.err.println("WARNING: " + binOp.getPrimaryOperand() + " is always an instance of " + binOp.getRight()
								+ " (if non-null)");
							return new UnaryFnExpression<>(primary, TypeTokens.get().BOOLEAN, v -> v != null);
						} else if (!primary.getResultType().isAssignableFrom(testType)) {
							System.err.println(binOp.getPrimaryOperand() + " is never an instance of " + binOp.getRight());
							return ConstantExpression.FALSE();
						} else {
							return new UnaryFnExpression<>(primary, TypeTokens.get().BOOLEAN, v -> testType.getRawType().isInstance(v));
						}
					} else {
						return new UnaryFnExpression<>(primary, TypeTokens.get().BOOLEAN, v -> testType.getRawType().isInstance(v));
					}
				} else {
					Expression<T, ?> arg2 = evaluateTypeChecked(binOp.getRight(), TypeTokens.get().of(Object.class), env);
					return combineBinary(primary, arg2, binOp);
				}
			}
		} else if (parsedItem instanceof ExpressionTypes.Conditional) {
			ExpressionTypes.Conditional cond = (ExpressionTypes.Conditional) parsedItem;
			Expression<T, Boolean> condition = (Expression<T, Boolean>) evaluateTypeChecked(cond.getCondition(),
				TypeTokens.get().of(Object.class), env);
			if (!TypeTokens.get().of(Boolean.class).isAssignableFrom(condition.getResultType().wrap()))
				throw new DBugParseException(
					"Condition in " + cond + " evaluates to type " + condition.getResultType() + ", which is not boolean");
			Expression<T, ? extends X> affirm = evaluateTypeChecked(cond.getAffirmative(), type, env);
			Expression<T, ? extends X> neg = evaluateTypeChecked(cond.getNegative(), type, env);
			TypeToken<X> resultType = (TypeToken<X>) DBugUtils.getCommonType(affirm.getResultType(), neg.getResultType());
			return new ConditionalExpression<>(resultType, condition, affirm, neg);
		} else if (parsedItem instanceof ExpressionTypes.Cast) {
			ExpressionTypes.Cast cast = (ExpressionTypes.Cast) parsedItem;
			TypeToken<?> testType = evaluateType(cast.getType(), type, env);
			Expression<T, ?> var = evaluateTypeChecked(cast.getValue(), TypeTokens.get().of(Object.class), env);
			// TODO Can check better by checking for final types
			if (!testType.getRawType().isInterface() && !var.getResultType().getRawType().isInterface()) {
				if (testType.isAssignableFrom(var.getResultType())) {
					System.err.println("WARNING: " + cast.getValue() + " is always an instance of " + cast.getType());
					return new UnaryFnExpression<>(var, (TypeToken<Object>) testType, v -> v);
				} else if (!DBugUtils.isAssignableFrom(var.getResultType(), testType)) {
					env.error(cast.getValue() + " is never an instance of " + cast.getType());
					return new UnaryFnExpression<>(var, (TypeToken<Object>) testType, v -> {
						if (v == null) {
							if (testType.isPrimitive())
								throw new NullPointerException("null cannot be cast to " + testType);
							else
								return null;
						} else
							throw new ClassCastException(v + " is not an instance of " + testType);
					});
				} else {
					return new UnaryFnExpression<>(var, (TypeToken<Object>) testType, v -> {
						if (v == null) {
							if (testType.isPrimitive())
								throw new NullPointerException("null cannot be cast to " + testType);
							else
								return null;
						} else if (testType.getRawType().isInstance(v))
							return v;
						else if (DBugUtils.isAssignableFrom(testType, TypeTokens.get().of(v.getClass())))
							return DBugUtils.convert(testType, v);
						else if (DBugUtils.canConvert(testType, TypeTokens.get().of(v.getClass())))
							return DBugUtils.convert(testType, v);
						else
							throw new ClassCastException(v + " is not an instance of " + testType);
					});
				}
			} else {
				return new UnaryFnExpression<>(var, (TypeToken<Object>) testType, v -> {
					if (v == null) {
						if (testType.isPrimitive())
							throw new NullPointerException("null cannot be cast to " + testType);
						else
							return null;
					} else if (testType.getRawType().isInstance(v))
						return v;
					else if (DBugUtils.isAssignableFrom(testType, TypeTokens.get().of(v.getClass())))
						return DBugUtils.convert(testType, v);
					else if (DBugUtils.canConvert(testType, TypeTokens.get().of(v.getClass())))
						return DBugUtils.convert(testType, v);
					else
						throw new ClassCastException(v + " is not an instance of " + testType);
				});
			}
			// Harder operations
			// Array operations
		} else if (parsedItem instanceof ExpressionTypes.ArrayAccess) {
			ExpressionTypes.ArrayAccess pai = (ExpressionTypes.ArrayAccess) parsedItem;
			Expression<T, ?> array = evaluateTypeChecked(pai.getArray(), TypeTokens.get().of(Object.class), env);
			TypeToken<? extends X> resultType;
			{
				TypeToken<?> testResultType;
				if (array.getResultType().isArray()) {
					testResultType = array.getResultType().getComponentType();
				} else {
					throw new DBugParseException(
						"array value in " + parsedItem + " evaluates to type " + array.getResultType() + ", which is not indexable");
				}
				if (!DBugUtils.isAssignableFrom(type, testResultType))
					throw new DBugParseException("array value in " + parsedItem + " evaluates to type " + array.getResultType()
						+ " which is not indexable to type " + type);
				resultType = (TypeToken<? extends X>) testResultType;
			}
			Expression<T, ?> index = evaluateTypeChecked(pai.getIndex(), TypeTokens.get().of(Object.class), env);
			if (!TypeTokens.get().of(Long.class).isAssignableFrom(array.getResultType().wrap())) {
				throw new DBugParseException(
					"index value in " + parsedItem + " evaluates to type " + index.getResultType() + ", which is not a valid index");
			}
			return new ArrayAccessExpression<>(array, (Expression<T, ? extends Number>) index, resultType);
		} else if (parsedItem instanceof ExpressionTypes.ArrayInitializer) {
			ExpressionTypes.ArrayInitializer arrayInit = (ExpressionTypes.ArrayInitializer) parsedItem;
			TypeToken<?> arrayType = evaluateType(arrayInit.getType(), type, env);
			if (arrayInit.getSizes() != null) {
				Expression<T, ? extends Number>[] sizes = new Expression[arrayInit.getSizes().size()];
				for (int i = 0; i < sizes.length; i++) {
					arrayType = DBugUtils.arrayTypeOf(arrayType);
					Expression<T, ?> size_i = evaluateTypeChecked(arrayInit.getSizes().get(i), TypeTokens.get().of(Object.class), env);
					if (!DBugUtils.isAssignableFrom(TypeTokens.get().of(Integer.TYPE), size_i.getResultType()))
						throw new DBugParseException(
							"Array size " + arrayInit.getSizes().get(i) + " parses to type " + size_i.getResultType()
							+ ", which is not a valid array size type");
					sizes[i] = (Expression<T, ? extends Number>) size_i;
				}

				return new ArrayInitializerExpression<>(sizes, arrayType);
			} else if (arrayInit.getElements() != null) {
				Expression<T, ?>[] elements = new Expression[arrayInit.getElements().size()];
				TypeToken<?> componentType = arrayType.getComponentType();
				for (int i = 0; i < elements.length; i++) {
					Expression<T, ?> element_i = evaluateTypeChecked(arrayInit.getElements().get(i), TypeTokens.get().of(Object.class),
						env);
					if (!DBugUtils.isAssignableFrom(componentType, element_i.getResultType()))
						throw new DBugParseException("Array element " + arrayInit.getElements().get(i) + " parses to type "
							+ element_i.getResultType() + ", which cannot be cast to " + componentType);
					elements[i] = element_i;
				}

				return new ArrayInitializerByValueExpression<>(DBugUtils.arrayTypeOf(arrayType), elements);
			} else
				throw new DBugParseException("Either array sizes or a value list must be specifieded for array initialization");
			// Now pulling stuff from the context
			// Identifier, placeholder, and unit
		} else if (parsedItem instanceof ExpressionTypes.Placeholder) {
			ExpressionTypes.Placeholder placeholder = (ExpressionTypes.Placeholder) parsedItem;
			Expression<T, ?> result = env.parseDependency(TypeTokens.get().of(Object.class), placeholder.print());
			if (result == null)
				throw new DBugParseException("Unrecognized placeholder: " + placeholder);
			return result;
		} else if (parsedItem instanceof ExpressionTypes.UnitValue) {
			throw new DBugParseException("Units not supported");
			// Now harder operations
		} else if (parsedItem instanceof ExpressionTypes.Constructor) {
			ExpressionTypes.Constructor constructor = (ExpressionTypes.Constructor) parsedItem;
			TypeToken<?> typeToCreate = evaluateType(constructor.getType(), type, env);
			Constructor<?> bestConstructor = null;
			InvokableMatch<T> bestMatch = null;
			for (Constructor<?> c : typeToCreate.getRawType().getConstructors()) {
				InvokableMatch<T> match = getMatch(c.getGenericParameterTypes(), typeToCreate.getType(), c.isVarArgs(),
					constructor.getArguments(), type, env);
				if (match != null && match.compareTo(bestMatch) < 0) {
					bestMatch = match;
					bestConstructor = c;
				}
			}
			if (bestConstructor == null)
				throw new DBugParseException("No such constructor found: " + parsedItem);
			if (!bestMatch.matches)
				throw new DBugParseException("Constructor " + bestConstructor + " cannot be applied to " + bestMatch.getArgumentTypes());
			Constructor<?> toInvoke = bestConstructor;
			return new ConstructorExpression<>(bestMatch.returnType, toInvoke, bestMatch.parameters);
		} else if (parsedItem instanceof ExpressionTypes.QualifiedName) {
			ExpressionTypes.QualifiedName qName = (ExpressionTypes.QualifiedName) parsedItem;
			return evaluateMember(new ExpressionTypes.FieldAccess(qName.getContext(), qName.getQualifier(), qName.getName()),
				type, env);
		} else if (parsedItem instanceof ExpressionTypes.MemberAccess) {
			ExpressionTypes.MemberAccess member = (ExpressionTypes.MemberAccess) parsedItem;
			return evaluateMember(member, type, env);
		} else
			throw new DBugParseException("Unrecognized parsed item type: " + parsedItem.getClass());
	}

	private static <T, X> Expression<T, ? extends X> evaluateTypeChecked(DBugAntlrExpression parsedItem, TypeToken<X> type,
		DBugParseEnv<T> env) throws DBugParseException {
		Expression<T, ?> result = parseExpression(parsedItem, type, env);

		if (!DBugUtils.isAssignableFrom(type, result.getResultType()))
			throw new DBugParseException(
				parsedItem + " evaluates to type " + result.getResultType() + ", which is not compatible with expected type " + type);
		return (Expression<T, ? extends X>) result;
	}

	private static TypeToken<?> evaluateType(ExpressionTypes.Type parsedType, TypeToken<?> expected, DBugParseEnv<?> env)
		throws DBugParseException {
		Type reflectType = getReflectType(parsedType, expected, env);

		return TypeToken.of(reflectType);
	}

	private static <T, X> Expression<T, ?> evaluateMember(ExpressionTypes.MemberAccess member, TypeToken<X> type, DBugParseEnv<T> env)
		throws DBugParseException {
		if (member.getMemberContext() == null) {
			if (member instanceof ExpressionTypes.MethodInvocation) {
				// A function
				throw new DBugParseException("Functions not supported");
			} else if (env.hasDependency(member.getName())) {
				// A variable
				return env.parseDependency(TypeTokens.get().of(Object.class), member.getName());
			} else
				return new TypeExpression<>(env.getType(member.getName()));
		} else {
			// May be a method invocation on a value or a static invocation on a type
			String rootCtx = getRootContext(member.getMemberContext());
			Class<?> contextType = null;
			if (rootCtx != null && !env.hasDependency(rootCtx)) {
				// May be a static invocation on a type
				try {
					contextType = env.getType(member.getMemberContext().toString());
				} catch (DBugParseException e) {
					// We'll throw a different exception later if we can't resolve it
				}
			}
			Expression<T, ?> result;
			if (contextType != null) {
				result = evaluateStatic(member, contextType, type, env);
			} else {
				if (mayBeType(member)) {
					try {
						Class<?> evaldType = env.getType(member.toString());
						return new TypeExpression<>(evaldType);
					} catch (DBugParseException e) {
						// Other stuff to try, so ignore this
					}
				}
				// Not a static invocation. Evaluate the context. Let that evaluation throw the exception if needed.
				Expression<T, ?> context = evaluateTypeChecked(member.getMemberContext(), TypeTokens.get().of(Object.class), env);
				result = evaluateMemberAccess(member, context, type, env);
			}
			return result;
		}
	}

	private static boolean mayBeType(ExpressionTypes.MemberAccess member) {
		if (!(member instanceof ExpressionTypes.FieldAccess))
			return false;
		if (member.getMemberContext() instanceof ExpressionTypes.QualifiedName)
			return true;
		else if (member.getMemberContext() instanceof ExpressionTypes.MemberAccess)
			return mayBeType((ExpressionTypes.MemberAccess) member.getMemberContext());
		else
			return false;
	}

	private static Type getReflectType(ExpressionTypes.Type parsedType, TypeToken<?> expected, DBugParseEnv<?> env)
		throws DBugParseException {
		if (parsedType instanceof ExpressionTypes.PrimitiveType)
			return ((ExpressionTypes.PrimitiveType) parsedType).getType();
		else if (parsedType instanceof ExpressionTypes.ClassType) {
			ExpressionTypes.ClassType classType = (ExpressionTypes.ClassType) parsedType;
			if (classType.getTypeArgs() == null)
				return env.getType(classType.getName());
			else
				return parameterizedType(classType.getName(), classType.getTypeArgs(), expected, env);
		} else if (parsedType instanceof ExpressionTypes.ArrayType) {
			ExpressionTypes.ArrayType arrayType = (ExpressionTypes.ArrayType) parsedType;
			TypeToken<?> expectedComponent = expected;
			for (int i = 0; i < arrayType.getDimension() && expectedComponent.isArray(); i++)
				expectedComponent = expectedComponent.getComponentType();
			Type arrayTypeResult = arrayType(getReflectType(arrayType.getComponentType(), expectedComponent, env));
			for (int i = 1; i < arrayType.getDimension(); i++)
				arrayTypeResult = arrayType(arrayTypeResult);
			return arrayTypeResult;
		} else
			throw new DBugParseException("Unrecognized type: " + parsedType.getClass().getSimpleName());
	}

	private static Type parameterizedType(String name, List<ExpressionTypes.Type> parameterTypes, TypeToken<?> expected,
		DBugParseEnv<?> env)
		throws DBugParseException {
		Class<?> raw = env.getType(name);

		Type[] typeArgs = new Type[raw.getTypeParameters().length];
		if (parameterTypes.isEmpty()) {
			// Diamond operator. Figure out the parameter types.
			for (int i = 0; i < typeArgs.length; i++)
				typeArgs[i] = expected.resolveType(raw.getTypeParameters()[i]).getType();
		} else if (parameterTypes.size() != typeArgs.length)
			throw new DBugParseException("Type " + raw.getName() + " cannot be parameterized with " + parameterTypes.size() + " types");
		else {
			for (int i = 0; i < typeArgs.length; i++)
				typeArgs[i] = getReflectType(parameterTypes.get(i), expected.resolveType(raw.getTypeParameters()[i]), env);
		}
		return new ParameterizedType(raw, typeArgs);
	}

	static class ParameterizedType implements java.lang.reflect.ParameterizedType {
		private final Type theRawType;
		private final Type[] theTypeArgs;

		ParameterizedType(Type raw, Type[] args) {
			theRawType = raw;
			theTypeArgs = args;
		}

		@Override
		public Type getRawType() {
			return theRawType;
		}

		@Override
		public Type[] getActualTypeArguments() {
			return theTypeArgs;
		}

		@Override
		public Type getOwnerType() {
			return null;
		}
	}

	private static Type arrayType(Type reflectType) {
		if (reflectType instanceof Class)
			return Array.newInstance((Class<?>) reflectType, 0).getClass();
		class ArrayType implements GenericArrayType {
			@Override
			public Type getGenericComponentType() {
				return reflectType;
			}
		}
		return new ArrayType();
	}

	static class InvokableMatch<T> implements Comparable<InvokableMatch<?>> {
		final Expression<T, ?>[] parameters;
		final TypeToken<?> returnType;
		final double distance;
		final boolean matches;

		InvokableMatch(Expression<T, ?>[] parameters, TypeToken<?> returnType, double distance, boolean matches) {
			this.parameters = parameters;
			this.returnType = returnType;
			this.distance = distance;
			this.matches = matches;
		}

		public List<TypeToken<?>> getArgumentTypes() {
			return Arrays.stream(parameters).map(v -> v.getResultType()).collect(Collectors.toList());
		}

		@Override
		public int compareTo(InvokableMatch<?> o) {
			if (o == null)
				return -1;
			if (matches && !o.matches)
				return -1;
			if (!matches && o.matches)
				return 1;
			double distDiff = distance - o.distance;
			return distDiff < 0 ? -1 : (distDiff > 0 ? 1 : 0);
		}
	}

	private static String getRootContext(DBugAntlrExpression context) {
		if (context instanceof ExpressionTypes.MemberAccess) {
			ExpressionTypes.MemberAccess method = (ExpressionTypes.MemberAccess) context;
			if (method instanceof ExpressionTypes.MethodInvocation)
				return null;
			String ret = getRootContext(method.getMemberContext());
			if (ret == null)
				return null;
			return ret + "." + method.getName();
		} else if (context instanceof ExpressionTypes.QualifiedName) {
			ExpressionTypes.QualifiedName qName = (ExpressionTypes.QualifiedName) context;
			if (qName.getQualifier() == null)
				return qName.getName();
			String ctxStr = getRootContext(qName.getQualifier());
			return ctxStr + "." + qName.getName();
		} else
			return null;
	}

	private static <T> InvokableMatch<T> getMatch(Type[] paramTypes, Type returnType, boolean varArgs, List<DBugAntlrExpression> arguments,
		TypeToken<?> type, DBugParseEnv<T> env) throws DBugParseException {
		TypeToken<?>[] typeTokenParams = new TypeToken[paramTypes.length];
		for (int i = 0; i < paramTypes.length; i++)
			typeTokenParams[i] = type.resolveType(paramTypes[i]);
		return getMatch(typeTokenParams, type.resolveType(returnType), varArgs, arguments, type, env);
	}

	private static <T> InvokableMatch<T> getMatch(TypeToken<?>[] paramTypes, TypeToken<?> returnType, boolean varArgs,
		List<DBugAntlrExpression> arguments, TypeToken<?> type, DBugParseEnv<T> env) throws DBugParseException {
		TypeToken<?>[] argTargetTypes = new TypeToken[arguments.size()];
		if (paramTypes.length == arguments.size()) {
			argTargetTypes = paramTypes;
		} else if (varArgs) {
			if (arguments.size() >= paramTypes.length - 1) {
				for (int i = 0; i < paramTypes.length - 1; i++)
					argTargetTypes[i] = paramTypes[i];
				for (int i = paramTypes.length - 1; i < arguments.size(); i++)
					argTargetTypes[i] = paramTypes[paramTypes.length - 1];
			} else
				return null;
		} else
			return null;

		Expression<T, ?>[] args = new Expression[arguments.size()];
		for (int i = 0; i < args.length; i++)
			args[i] = parseExpression(arguments.get(i), argTargetTypes[i], env);

		Map<TypeToken<?>, TypeToken<?>> typeVariables = new HashMap<>();
		for (int i = 0; i < argTargetTypes.length; i++)
			resolveTypeVariables(argTargetTypes[i], args[i].getResultType(), typeVariables);
		for (int i = 0; i < argTargetTypes.length; i++)
			argTargetTypes[i] = resolveParams(argTargetTypes[i], typeVariables);

		for (int i = 0; i < args.length; i++) {
			if (!DBugUtils.isAssignableFrom(argTargetTypes[i], args[i].getResultType()))
				return new InvokableMatch<>(args, resolveParams(returnType, typeVariables), 0, false);
		}
		double distance = 0;
		for (int i = 0; i < paramTypes.length && i < args.length; i++)
			distance += getDistance(argTargetTypes[i].wrap(), args[i].getResultType().wrap());
		return new InvokableMatch<>(args, resolveParams(returnType, typeVariables), distance, true);
	}

	private static void resolveTypeVariables(TypeToken<?> paramType, TypeToken<?> argType, Map<TypeToken<?>, TypeToken<?>> typeVariables) {
		if (paramType.getType() instanceof TypeVariable) {
			TypeToken<?> type = typeVariables.get(paramType);
			if (type == null)
				type = argType;
			else
				type = DBugUtils.getCommonType(type, argType);
			typeVariables.put(paramType, type.wrap());
		} else if (paramType.getType() instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) paramType.getType();
			for (Type subPT : pt.getActualTypeArguments())
				resolveTypeVariables(TypeToken.of(subPT), argType.resolveType(subPT), typeVariables);
		} else if (paramType.isArray()) {
			TypeToken<?> compType = paramType.getComponentType();
			resolveTypeVariables(compType, argType.resolveType(compType.getType()), typeVariables);
		}
	}

	private static TypeToken<?> resolveParams(TypeToken<?> type, Map<TypeToken<?>, TypeToken<?>> typeVariables) {
		if (type.getType() instanceof TypeVariable)
			return typeVariables.get(type);
		else if (type.getType() instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type.getType();
			boolean changed = false;
			Type[] typeParams = new Type[pt.getActualTypeArguments().length];
			for (int i = 0; i < typeParams.length; i++) {
				TypeToken<?> typeParam = type.resolveType(pt.getActualTypeArguments()[i]);
				TypeToken<?> resolved = resolveParams(typeParam, typeVariables);
				typeParams[i] = resolved.getType();
				changed |= resolved != typeParam;
			}
			if (changed)
				return TypeToken.of(new ParameterizedType(type.getRawType(), typeParams));
			else
				return type;
		} else if (type.isArray()) {
			TypeToken<?> typeParam = type.getComponentType();
			TypeToken<?> resolved = resolveParams(typeParam, typeVariables);
			if (typeParam != resolved)
				return DBugUtils.arrayTypeOf(resolved);
			else
				return type;
		} else
			return type;
	}

	private static double getDistance(TypeToken<?> paramType, TypeToken<?> argType) {
		double distance = 0;
		distance += getRawDistance(paramType.getRawType(), argType.getRawType());
		if (paramType.getType() instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) paramType.getType();
			for (int i = 0; i < pt.getActualTypeArguments().length; i++) {
				double paramDist = getDistance(paramType.resolveType(pt.getActualTypeArguments()[i]),
					argType.resolveType(pt.getActualTypeArguments()[i]));
				distance += paramDist / 10;
			}
		}
		return distance;
	}

	private static int getRawDistance(Class<?> paramType, Class<?> argType) {
		if (paramType.equals(argType))
			return 0;
		else if (paramType.isAssignableFrom(argType)) {
			int dist = 0;
			while (argType.getSuperclass() != null && paramType.isAssignableFrom(argType.getSuperclass())) {
				argType = argType.getSuperclass();
				dist++;
			}
			if (paramType.isInterface())
				dist += getInterfaceDistance(paramType, argType);
			return dist;
		} else if (argType.isAssignableFrom(paramType))
			return getRawDistance(argType, paramType);
		else if (Number.class.isAssignableFrom(paramType) && Number.class.isAssignableFrom(argType)) {
			int paramNumTypeOrdinal = getNumberTypeOrdinal(paramType);
			int argNumTypeOrdinal = getNumberTypeOrdinal(argType);
			if (paramNumTypeOrdinal < 0 || argNumTypeOrdinal < 0 || argNumTypeOrdinal > paramNumTypeOrdinal)
				throw new IllegalStateException("Shouldn't get here: " + paramType.getName() + " and " + argType.getName());
			return paramNumTypeOrdinal - argNumTypeOrdinal;
		} else
			throw new IllegalStateException("Shouldn't get here: " + paramType.getName() + " and " + argType.getName());
	}

	private static int getInterfaceDistance(Class<?> paramType, Class<?> argType) {
		if (paramType.equals(argType))
			return 0;
		for (Class<?> intf : argType.getInterfaces()) {
			if (paramType.isAssignableFrom(intf))
				return getInterfaceDistance(paramType, intf) + 1;
		}
		throw new IllegalStateException("Shouldn't get here");
	}

	private static int getNumberTypeOrdinal(Class<?> numType) {
		if (numType == Byte.class)
			return 0;
		else if (numType == Short.class)
			return 1;
		else if (numType == Integer.class)
			return 2;
		else if (numType == Long.class)
			return 3;
		else if (numType == Float.class)
			return 4;
		else if (numType == Double.class)
			return 5;
		else
			return -1;
	}

	private static <T> Expression<T, ?> evaluateStatic(ExpressionTypes.MemberAccess member, Class<?> targetType, TypeToken<?> type,
		DBugParseEnv<T> env) throws DBugParseException {
		// Evaluate as a static invocation on the type
		if (member instanceof ExpressionTypes.MethodInvocation) {
			Method bestMethod = null;
			InvokableMatch<T> bestMatch = null;
			int publicStatic = Modifier.STATIC | Modifier.PUBLIC;
			for (Method m : targetType.getMethods()) {
				if ((m.getModifiers() & publicStatic) != publicStatic || !m.getName().equals(member.getName()))
					continue;
				InvokableMatch<T> match = getMatch(m.getGenericParameterTypes(), m.getGenericReturnType(), m.isVarArgs(),
					((ExpressionTypes.MethodInvocation) member).getArguments(), type, env);
				if (match != null && match.compareTo(bestMatch) < 0) {
					bestMatch = match;
					bestMethod = m;
				}
			}
			if (bestMethod == null)
				throw new DBugParseException("No such method found: " + targetType.getName() + "." + member.getName());
			if (!bestMatch.matches)
				throw new DBugParseException("Method " + bestMethod + " cannot be applied to " + bestMatch.getArgumentTypes());
			return new MethodExpression<>(new TypeExpression<>(targetType), bestMatch.parameters, bestMethod);
		} else {
			Field fieldRef;
			try {
				fieldRef = targetType.getField(member.getName());
			} catch (NoSuchFieldException e) {
				throw new DBugParseException("No such field " + targetType.getName() + "." + member.getName(), e);
			} catch (SecurityException e) {
				throw new DBugParseException("Could not access field " + targetType.getName() + "." + member.getName(), e);
			}
			if ((fieldRef.getModifiers() & Modifier.STATIC) == 0)
				throw new DBugParseException("Field " + targetType.getName() + "." + fieldRef.getName() + " is not static");
			return new FieldExpression<>(new TypeExpression<>(targetType), fieldRef);
		}
	}

	private static <T> Expression<T, ?> evaluateMemberAccess(ExpressionTypes.MemberAccess member, Expression<T, ?> context,
		TypeToken<?> type, DBugParseEnv<T> env) throws DBugParseException {
		if (member instanceof ExpressionTypes.FieldAccess) {
			Field fieldRef;
			try {
				fieldRef = context.getResultType().getRawType().getField(member.getName());
			} catch (NoSuchFieldException e) {
				throw new DBugParseException("No such field " + member.getMemberContext() + "." + member.getName(), e);
			} catch (SecurityException e) {
				throw new DBugParseException("Could not access field " + member.getMemberContext() + "." + member.getName(), e);
			}
			if ((fieldRef.getModifiers() & Modifier.STATIC) != 0)
				throw new DBugParseException("Field " + context + "." + fieldRef.getName() + " is static");
			return new FieldExpression<>(context, fieldRef);
		} else {
			ExpressionTypes.MethodInvocation method = (ExpressionTypes.MethodInvocation) member;
			Method bestMethod = null;
			InvokableMatch<T> bestMatch = null;
			for (Method m : context.getResultType().getRawType().getMethods()) {
				if (!m.getName().equals(method.getName()))
					continue;
				InvokableMatch<T> match = getMatch(m.getGenericParameterTypes(), m.getGenericReturnType(), m.isVarArgs(),
					method.getArguments(), type, env);
				if (match != null && match.compareTo(bestMatch) < 0) {
					bestMatch = match;
					bestMethod = m;
				}
			}
			if (bestMethod == null)
				throw new DBugParseException("No such method found: " + method);
			if (!bestMatch.matches)
				throw new DBugParseException("Method " + bestMethod + " cannot be applied to " + bestMatch.getArgumentTypes());
			Expression<T, ?>[] composed = new Expression[bestMatch.parameters.length + 1];
			composed[0] = context;
			System.arraycopy(bestMatch.parameters, 0, composed, 1, bestMatch.parameters.length);
			return new MethodExpression<>(context, bestMatch.parameters, bestMethod);
		}
	}

	private static <T> Expression<T, ?> mapUnary(Expression<T, ?> arg1, ExpressionTypes.UnaryOperation op)
		throws DBugParseException {
		try {
			switch (op.getName()) {
			case "+":
			case "-":
			case "~":
				try {
					return new UnaryMathExpression<>(arg1, op.getName());
				} catch (IllegalArgumentException e) {
					throw new DBugParseException(e.getMessage(), e);
				}
			case "!":
				if (!TypeTokens.get().isBoolean(arg1.getResultType()))
					throw new DBugParseException("! cannot be applied to operand type " + arg1.getResultType());
				return new NotExpression<>((Expression<T, Boolean>) arg1);
			default:
				throw new DBugParseException("Unrecognized unary operator: " + op.getName());
			}
		} catch (IllegalArgumentException e) {
			throw new DBugParseException(e.getMessage(), e);
		}
	}

	private static <T> Expression<T, ?> combineBinary(Expression<T, ?> arg1, Expression<T, ?> arg2, ExpressionTypes.BinaryOperation op)
		throws DBugParseException {
		try {
			switch (op.getName()) {
			case "+":
				TypeToken<String> strType = TypeTokens.get().STRING;
				if (strType.isAssignableFrom(arg1.getResultType()) || strType.isAssignableFrom(arg2.getResultType())) {
					return new StringConcatOperation<>(arg1, arg2);
				}
				//$FALL-THROUGH$
			case "==":
				return new EqualityExpression<>(arg1, arg2, true);
			case "!=":
				return new EqualityExpression<>(arg1, arg2, false);
			case "-":
			case "*":
			case "/":
			case "%":
			case "<<":
			case ">>":
			case ">>>":
			case "&":
			case "|":
			case "^":
			case "&&":
			case "||":
			case ">":
			case "<":
			case ">=":
			case "<=":
				try {
					return BinaryMathExpression.binaryOp(arg1, arg2, op.getName());
				} catch (IllegalArgumentException e) {
					throw new DBugParseException(e.getMessage(), e);
				}
			default:
				throw new DBugParseException("Unrecognized binary operator: " + op.getName());
			}
		} catch (IllegalArgumentException e) {
			throw new DBugParseException(op + ": " + e.getMessage(), e);
		}
	}
}
