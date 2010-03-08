package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;

public class SplitStubChanges extends SplitMoveChanges {

	public SplitStubChanges(String targetPackageName, String targetSuffix) {
		super(targetPackageName, targetSuffix);
	}

	@Override
	protected MethodDeclaration getMethodCopy(AST ast, MethodDeclaration sourceMethodDeclaration) {
		final MethodDeclaration methodCopy = super.getMethodCopy(ast, sourceMethodDeclaration);
		final Block methodBody = methodCopy.getBody();
		if (methodBody != null) {
			final List<Statement> statements = methodBody.statements();
			if (statements != null) {
				final List<Statement> emptyBody = new ArrayList<Statement>();
				for (final Statement statement : statements) {
					if (statement instanceof SuperConstructorInvocation) {
						emptyBody.add(statement);
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
