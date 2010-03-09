package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
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
					insertErrorVersionMismatch(ast, methodCopy, emptyBody);
					insertDefaultReturn(ast, methodCopy, emptyBody);
				}

				statements.clear();
				if (!emptyBody.isEmpty()) {
					statements.addAll(emptyBody);
				}

			}
		}
		return methodCopy;
	}

	private void insertDefaultReturn(final AST ast, final MethodDeclaration methodCopy, final List<Statement> emptyBody) {
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

	private void insertErrorVersionMismatch(final AST ast, final MethodDeclaration methodCopy,
			final List<Statement> emptyBody) {

		boolean found = false;
		final List<Name> thrownExceptions = methodCopy.thrownExceptions();
		for (final Name name : thrownExceptions) {
			if ("org.tmatesoft.svn.core.SVNException".equals(name.getFullyQualifiedName())) {
				found = true;
				break;
			}
		}
		if (!found) {
			thrownExceptions.add(ast.newSimpleName("SVNException"));
		}

		final MethodInvocation invoc1 = ast.newMethodInvocation();
		invoc1.setExpression(ast.newSimpleName("SVNErrorMessage"));
		invoc1.setName(ast.newSimpleName("create"));
		final List<Expression> args1 = invoc1.arguments();
		args1.add(ast.newQualifiedName(ast.newSimpleName("SVNErrorCode"), ast.newSimpleName("VERSION_MISMATCH")));

		final VariableDeclarationFragment varF = ast.newVariableDeclarationFragment();
		varF.setName(ast.newSimpleName("err"));
		varF.setInitializer(invoc1);
		final VariableDeclarationStatement varS = ast.newVariableDeclarationStatement(varF);
		varS.setType(ast.newSimpleType(ast.newSimpleName("SVNErrorMessage")));
		emptyBody.add(varS);

		final MethodInvocation invoc2 = ast.newMethodInvocation();
		invoc2.setExpression(ast.newSimpleName("SVNErrorManager"));
		invoc2.setName(ast.newSimpleName("error"));
		final List<Expression> args2 = invoc2.arguments();
		args2.add(ast.newSimpleName("err"));
		args2.add(ast.newQualifiedName(ast.newSimpleName("SVNLogType"), ast.newSimpleName("CLIENT")));
		emptyBody.add(ast.newExpressionStatement(invoc2));

	}

	@Override
	protected void addImports(final SplitUnitModel unitModel, final AST ast, final CompilationUnit node) {
		super.addImports(unitModel, ast, node);

		boolean foundSVNLogType = false;
		boolean foundSVNException = false;
		boolean foundSVNErrorCode = false;
		boolean foundSVNErrorMessage = false;
		boolean foundSVNErrorManager = false;

		final List<ImportDeclaration> imports = node.imports();
		for (final ImportDeclaration importDeclaration : imports) {

			final String name = importDeclaration.getName().getFullyQualifiedName();

			if (!foundSVNLogType) {
				if ("org.tmatesoft.svn.util.SVNLogType".equals(name)) {
					foundSVNLogType = true;
					continue;
				}
			}

			if (!foundSVNException) {
				if ("org.tmatesoft.svn.core.SVNException".equals(name)) {
					foundSVNException = true;
					continue;
				}
			}

			if (!foundSVNErrorCode) {
				if ("org.tmatesoft.svn.core.SVNErrorCode".equals(name)) {
					foundSVNErrorCode = true;
					continue;
				}
			}

			if (!foundSVNErrorMessage) {
				if ("org.tmatesoft.svn.core.SVNErrorMessage".equals(name)) {
					foundSVNErrorMessage = true;
					continue;
				}
			}

			if (!foundSVNErrorManager) {
				if ("org.tmatesoft.svn.core.internal.wc.SVNErrorManager".equals(name)) {
					foundSVNErrorManager = true;
					continue;
				}
			}

		}

		if (!foundSVNLogType) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.util.SVNLogType"));
		}

		if (!foundSVNException) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.core.SVNException"));
		}

		if (!foundSVNErrorCode) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.core.SVNErrorCode"));
		}

		if (!foundSVNErrorMessage) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.core.SVNErrorMessage"));
		}

		if (!foundSVNErrorManager) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.core.internal.wc.SVNErrorManager"));
		}

	}

	private void addImport(final AST ast, final List<ImportDeclaration> imports, final Name name) {
		final ImportDeclaration importDeclaration = ast.newImportDeclaration();
		importDeclaration.setOnDemand(false);
		importDeclaration.setName(name);
		imports.add(importDeclaration);
	}

}
