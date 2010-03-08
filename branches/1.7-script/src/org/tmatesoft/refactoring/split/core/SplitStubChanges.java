package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;

public class SplitStubChanges extends SplitMoveChanges {

	public SplitStubChanges(String targetPackageName, String targetSuffix) {
		super(targetPackageName, targetSuffix);
	}

	@Override
	protected void addField(SplitUnitModel unitModel, AST ast, List bodyDeclarations, IField sourceField)
			throws JavaModelException {
	}

	@Override
	protected MethodDeclaration getMethodCopy(final AST ast, final MethodDeclaration sourceMethodDeclaration) {
		final MethodDeclaration methodCopy = super.getMethodCopy(ast, sourceMethodDeclaration);
		final Block methodBody = methodCopy.getBody();
		if (methodBody != null) {
			final List<Statement> statements = methodBody.statements();
			if (statements != null) {

				final List<Statement> emptyBody = new ArrayList<Statement>();

				if (methodCopy.isConstructor()) {
					for (final Statement statement : statements) {
						if (statement instanceof SuperConstructorInvocation) {
							emptyBody.add(statement);
						}
					}
				} else {
					final Type returnType = methodCopy.getReturnType2();
					final ReturnStatement newReturnStatement = ast.newReturnStatement();
					if (returnType instanceof PrimitiveType) {
						final PrimitiveType returnPrimitive = (PrimitiveType) returnType;
						final Code code = returnPrimitive.getPrimitiveTypeCode();
						if (code != PrimitiveType.VOID) {
							if (code == PrimitiveType.BYTE || code == PrimitiveType.CHAR || code == PrimitiveType.SHORT
									|| code == PrimitiveType.INT || code == PrimitiveType.LONG) {
								newReturnStatement.setExpression(ast.newNumberLiteral("0"));
							} else if (code == PrimitiveType.FLOAT || code == PrimitiveType.DOUBLE) {
								newReturnStatement.setExpression(ast.newNumberLiteral("0.0"));
							} else if (code == PrimitiveType.BOOLEAN) {
								newReturnStatement.setExpression(ast.newBooleanLiteral(false));
							}
							emptyBody.add(newReturnStatement);
						}
					} else {
						newReturnStatement.setExpression(ast.newNullLiteral());
						emptyBody.add(newReturnStatement);
					}
				}

				statements.clear();
				if (!emptyBody.isEmpty()) {
					statements.addAll(emptyBody);
				}

			}
		}
		return methodCopy;
	}

}
