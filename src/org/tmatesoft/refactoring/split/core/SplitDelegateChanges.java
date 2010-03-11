package org.tmatesoft.refactoring.split.core;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.TextEdit;

public class SplitDelegateChanges implements ISplitChanges {

	private String targetPackage1;
	private String targetSuffix1;
	private String targetPackage2;
	private String targetSuffix2;

	public SplitDelegateChanges(final String targetPackage1, final String targetSuffix1, final String targetPackage2,
			final String targetSuffix2) {
		this.targetPackage1 = targetPackage1;
		this.targetSuffix1 = targetSuffix1;
		this.targetPackage2 = targetPackage2;
		this.targetSuffix2 = targetSuffix2;
	}

	@Override
	public boolean doChanges(final SplitRefactoringModel model, final RefactoringStatus status,
			final SubProgressMonitor subMonitor) throws JavaModelException {

		final List<Change> changes = model.getChanges();

		for (final Entry<ICompilationUnit, SplitUnitModel> unitModelEntry : model.getUnitModels().entrySet()) {

			final ICompilationUnit unit = unitModelEntry.getKey();
			final SplitUnitModel unitModel = unitModelEntry.getValue();

			final IType sourceType = unitModel.getSourceType();

			if (sourceType.isInterface()) {
				continue;
			}

			final String source = unit.getSource();
			final Document document = new Document(source);

			final CompilationUnit sourceAst = unitModel.getSourceAst();
			final AST ast = sourceAst.getAST();
			final ASTRewrite rewrite = ASTRewrite.create(ast);

			final ListRewrite importsRewrite = rewrite.getListRewrite(sourceAst, sourceAst.IMPORTS_PROPERTY);

			boolean found = false;
			final List<ImportDeclaration> imports = sourceAst.imports();
			for (final ImportDeclaration importDeclaration : imports) {
				if (importDeclaration.getName().getFullyQualifiedName().equals("org.tmatesoft.svn.core.SVNException")) {
					found = true;
					break;
				}
			}

			if (!found) {
				final ImportDeclaration imp = ast.newImportDeclaration();
				imp.setName(ast.newName("org.tmatesoft.svn.core.SVNException"));
				importsRewrite.insertLast(imp, null);
			}

			final String sourceTypeName = unitModel.getSourceTypeName();

			{
				final ImportDeclaration imp = ast.newImportDeclaration();
				imp.setName(ast.newName(targetPackage1 + "." + sourceTypeName + targetSuffix1));
				importsRewrite.insertLast(imp, null);
			}

			{
				final ImportDeclaration imp = ast.newImportDeclaration();
				imp.setName(ast.newName(targetPackage2 + "." + sourceTypeName + targetSuffix2));
				importsRewrite.insertLast(imp, null);
			}

			{

			}

			final Map<IMethod, MethodDeclaration> methods = unitModel.getAddMethods();
			for (final Entry<IMethod, MethodDeclaration> methodsEntry : methods.entrySet()) {
				final IMethod method = methodsEntry.getKey();
				final MethodDeclaration methodDeclaration = methodsEntry.getValue();
				if (Flags.isPublic(method.getFlags())) {
					doDelegation(unitModel, rewrite, methodDeclaration, status, subMonitor);
				} else {
					rewrite.remove(methodDeclaration, null);
				}
			}

			final TextEdit edit = rewrite.rewriteAST(document, unit.getJavaProject().getOptions(true));
			final TextFileChange textFileChange = new TextFileChange(unit.getElementName(), (IFile) unit.getResource());
			textFileChange.setEdit(edit);
			changes.add(textFileChange);

		}

		return true;
	}

	private void doDelegation(SplitUnitModel unitModel, ASTRewrite rewrite, MethodDeclaration methodDeclaration,
			RefactoringStatus status, SubProgressMonitor subMonitor) {

		if (methodDeclaration.getBody() == null) {
			return;
		}

		final AST ast = methodDeclaration.getAST();

		final Block block = ast.newBlock();

		final TryStatement tryStatement = ast.newTryStatement();
		final List<CatchClause> catchClauses = tryStatement.catchClauses();
		final CatchClause catchClause = ast.newCatchClause();
		final SingleVariableDeclaration exception = ast.newSingleVariableDeclaration();
		exception.setType(ast.newSimpleType(ast.newSimpleName("SVNException")));
		exception.setName(ast.newSimpleName("e"));
		catchClause.setException(exception);
		catchClauses.add(catchClause);
		block.statements().add(tryStatement);

		{
			final Block tryBody = tryStatement.getBody();
			final List<Statement> tryStatements = tryBody.statements();

			final MethodInvocation invoc1 = ast.newMethodInvocation();

			if (Modifier.isStatic(methodDeclaration.getModifiers())) {
				invoc1.setExpression(ast.newSimpleName(unitModel.getSourceTypeName() + targetSuffix1));
			} else {
				final MethodInvocation invoc2 = ast.newMethodInvocation();
				invoc2.setExpression(ast.newSimpleName(unitModel.getSourceTypeName() + targetSuffix1));
				invoc2.setName(ast.newSimpleName("delegate"));
				invoc2.arguments().add(ast.newThisExpression());
				invoc1.setExpression(invoc2);
			}

			invoc1.setName(ast.newSimpleName(methodDeclaration.getName().getIdentifier()));
			final List<Expression> arguments = invoc1.arguments();
			final List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
			for (SingleVariableDeclaration parameter : parameters) {
				arguments.add(ast.newSimpleName(parameter.getName().getIdentifier()));
			}

			tryStatements.add(ast.newExpressionStatement(invoc1));
		}

		{
			final Block catchBody = catchClause.getBody();
			final List<Statement> catchStatements = catchBody.statements();

			final MethodInvocation invoc1 = ast.newMethodInvocation();

			if (Modifier.isStatic(methodDeclaration.getModifiers())) {
				invoc1.setExpression(ast.newSimpleName(unitModel.getSourceTypeName() + targetSuffix2));
			} else {
				final MethodInvocation invoc2 = ast.newMethodInvocation();
				invoc2.setExpression(ast.newSimpleName(unitModel.getSourceTypeName() + targetSuffix2));
				invoc2.setName(ast.newSimpleName("delegate"));
				invoc2.arguments().add(ast.newThisExpression());
				invoc1.setExpression(invoc2);
			}

			invoc1.setName(ast.newSimpleName(methodDeclaration.getName().getIdentifier()));
			final List<Expression> arguments = invoc1.arguments();
			final List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
			for (SingleVariableDeclaration parameter : parameters) {
				arguments.add(ast.newSimpleName(parameter.getName().getIdentifier()));
			}

			catchStatements.add(ast.newExpressionStatement(invoc1));
		}

		rewrite.replace(methodDeclaration.getBody(), block, null);

	}

}
