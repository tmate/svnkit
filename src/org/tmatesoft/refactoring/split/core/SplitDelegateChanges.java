package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.core.util.ASTNodeFinder;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.TextEdit;
import org.tmatesoft.refactoring.split.core.SplitUnitModel.TypeMetadata;

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
			final SubProgressMonitor subMonitor) throws CoreException {

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

			if (model.getWhiteListTypesNames().contains(sourceType.getFullyQualifiedName())) {
				
				addDelegationConstructor(unitModel, rewrite);
				
			} else {

				final ListRewrite importsRewrite = rewrite.getListRewrite(sourceAst, sourceAst.IMPORTS_PROPERTY);

				boolean found = false;
				final List<ImportDeclaration> imports = sourceAst.imports();
				for (final ImportDeclaration importDeclaration : imports) {
					if (importDeclaration.getName().getFullyQualifiedName().equals(
							"org.tmatesoft.svn.core.SVNException")) {
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

				if (!unitModel.isSourceInterface()) {
					addDelegationConstructor(unitModel, rewrite);
				}

				final Set<IType> removeNestedTypes = new HashSet<IType>();

				final Map<IMethod, MethodDeclaration> methods = unitModel.getAddMethods();

				final IJavaSearchScope unitScope = SearchEngine.createJavaSearchScope(new IJavaElement[] { unitModel
						.getSourceUnit() }, IJavaSearchScope.SOURCES);

				final Set<IType> nestedTypes = unitModel.getNestedTypes();
				for (final IType nestedType : nestedTypes) {
					if (Flags.isPrivate(nestedType.getFlags())) {
						final SearchPattern typePattern = SearchPattern.createPattern(nestedType,
								IJavaSearchConstants.REFERENCES);
						final List<IMethod> references = SplitUtils.searchManyElements(IMethod.class, typePattern,
								unitScope, subMonitor);
						boolean delegate = false;
						for (final IMethod reference : references) {
							final MethodDeclaration referenceDecl = methods.get(reference);
							if (referenceDecl == null || !isShouldDelegate(referenceDecl)) {
								delegate = true;
								break;
							}
						}
						if (!delegate) {
							removeNestedTypes.add(nestedType);
							final ASTNode node = NodeFinder.perform(sourceAst, nestedType.getSourceRange());
							if (node != null) {
								rewrite.remove(node, null);
							}
						}
					}
				}

				for (final Entry<IMethod, MethodDeclaration> methodsEntry : methods.entrySet()) {
					final IMethod method = methodsEntry.getKey();
					final MethodDeclaration methodDeclaration = methodsEntry.getValue();
					if (!method.isConstructor()) {
						if (!Flags.isPrivate(method.getFlags())) {
							doDelegation(unitModel, rewrite, methodDeclaration, status, subMonitor);
						} else {
							final SearchPattern methodPattern = SearchPattern.createPattern(method,
									IJavaSearchConstants.REFERENCES);
							final List<IMethod> references = SplitUtils.searchManyElements(IMethod.class,
									methodPattern, unitScope, subMonitor);
							boolean delegate = false;
							for (final IMethod reference : references) {
								final MethodDeclaration referenceDecl = methods.get(reference);
								if (referenceDecl == null || !isShouldDelegate(referenceDecl)) {
									final IType declaringType = reference.getDeclaringType();
									if (declaringType == null || !removeNestedTypes.contains(declaringType)) {
										delegate = true;
										break;
									}
								}
							}
							if (delegate) {
								doDelegation(unitModel, rewrite, methodDeclaration, status, subMonitor);
							} else {
								rewrite.remove(methodDeclaration, null);
							}
						}
					}
				}

				for (final IType nestedType : removeNestedTypes) {
					final ASTNode node = NodeFinder.perform(sourceAst, nestedType.getSourceRange());
					if (node != null) {
						rewrite.remove(node, null);
					}
				}
			}

			final TextEdit edit = rewrite.rewriteAST(document, unit.getJavaProject().getOptions(true));
			final TextFileChange textFileChange = new TextFileChange(unit.getElementName(), (IFile) unit.getResource());
			textFileChange.setEdit(edit);
			changes.add(textFileChange);

		}

		return true;
	}

	private void addDelegationConstructor(final SplitUnitModel unitModel, final ASTRewrite rewrite) {

		final String sourceTypeName = unitModel.getSourceTypeName();

		final TypeDeclaration typeNode = unitModel.getSourceTypeNode();
		final ListRewrite bodyRewrite = rewrite.getListRewrite(typeNode, typeNode.getBodyDeclarationsProperty());

		final CompilationUnit sourceAst = unitModel.getSourceAst();
		final AST ast = sourceAst.getAST();

		final FieldDeclaration[] fields = typeNode.getFields();
		final FieldDeclaration lastField = fields.length > 0 ? fields[fields.length - 1] : null;

		final List<FieldDeclaration> delegateFields = new ArrayList<FieldDeclaration>();

		if (fields.length > 0) {
			for (final FieldDeclaration field : fields) {
				boolean isStatic = false;
				boolean isFinal = false;
				Modifier privateModifier = null;
				final List<IExtendedModifier> modifiers = field.modifiers();
				for (final IExtendedModifier extMod : modifiers) {
					if (extMod.isModifier()) {
						final Modifier mod = (Modifier) extMod;
						if (mod.isPrivate()) {
							privateModifier = mod;
						} else if (mod.isFinal()) {
							isFinal = true;
						} else if (mod.isStatic()) {
							isStatic = true;
						}
					}
				}
				if (privateModifier != null) {
					final ListRewrite modRewrite = rewrite.getListRewrite(field, field.getModifiersProperty());
					modRewrite.replace(privateModifier, ast.newModifier(ModifierKeyword.PROTECTED_KEYWORD), null);
				}
				if (!isFinal && !isStatic) {
					delegateFields.add(field);
				}
			}
		}

		final MethodDeclaration constructorDecl = ast.newMethodDeclaration();
		if (lastField != null) {
			bodyRewrite.insertAfter(constructorDecl, lastField, null);
		} else {
			bodyRewrite.insertFirst(constructorDecl, null);
		}

		constructorDecl.setConstructor(true);
		constructorDecl.setName(ast.newSimpleName(sourceTypeName));
		constructorDecl.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PROTECTED_KEYWORD));

		final SingleVariableDeclaration constructorParam = ast.newSingleVariableDeclaration();
		constructorParam.setType(ast.newSimpleType(ast.newSimpleName(sourceTypeName)));
		constructorParam.setName(ast.newSimpleName("from"));
		constructorDecl.parameters().add(constructorParam);
		final Block constructorBody = ast.newBlock();
		constructorDecl.setBody(constructorBody);

		final TypeMetadata superMeta = unitModel.getSourceSuperClassMetadata();
		if (superMeta != null) {
			final SuperConstructorInvocation superInvoke = ast.newSuperConstructorInvocation();
			superInvoke.arguments().add(ast.newName("from"));
			constructorBody.statements().add(superInvoke);
		}

		if (!delegateFields.isEmpty()) {
			for (final FieldDeclaration field : delegateFields) {
				final List<VariableDeclarationFragment> fragments = field.fragments();
				for (VariableDeclarationFragment var : fragments) {
					final Assignment assign = ast.newAssignment();
					constructorBody.statements().add(ast.newExpressionStatement(assign));
					final FieldAccess thisAccess = ast.newFieldAccess();
					thisAccess.setExpression(ast.newThisExpression());
					thisAccess.setName(ast.newSimpleName(var.getName().getIdentifier()));
					assign.setLeftHandSide(thisAccess);
					final FieldAccess fromAccess = ast.newFieldAccess();
					fromAccess.setExpression(ast.newName("from"));
					fromAccess.setName(ast.newSimpleName(var.getName().getIdentifier()));
					assign.setRightHandSide(fromAccess);
				}
			}
		}

	}

	private boolean isShouldDelegate(MethodDeclaration methodDeclaration) {

		if (methodDeclaration.getBody() == null) {
			return false;
		}

		final List<Name> thrownExceptions = methodDeclaration.thrownExceptions();
		if (thrownExceptions == null || thrownExceptions.isEmpty()) {
			return false;
		}
		boolean foundSVNException = false;
		for (final Name name : thrownExceptions) {
			if ("SVNException".equals(name.getFullyQualifiedName())) {
				foundSVNException = true;
				break;
			}
		}
		if (!foundSVNException) {
			return false;
		}

		return true;

	}

	private void doDelegation(SplitUnitModel unitModel, ASTRewrite rewrite, MethodDeclaration methodDeclaration,
			RefactoringStatus status, SubProgressMonitor subMonitor) {

		if (!isShouldDelegate(methodDeclaration)) {
			return;
		}

		boolean isReturn = true;
		final Type returnType = methodDeclaration.getReturnType2();
		if (returnType.isPrimitiveType()) {
			final PrimitiveType primitiveType = (PrimitiveType) returnType;
			final Code code = primitiveType.getPrimitiveTypeCode();
			if (PrimitiveType.VOID.equals(code)) {
				isReturn = false;
			}
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

			if (isReturn) {
				final ReturnStatement returnStatement = ast.newReturnStatement();
				returnStatement.setExpression(invoc1);
				tryStatements.add(returnStatement);
			} else {
				tryStatements.add(ast.newExpressionStatement(invoc1));
			}
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

			if (isReturn) {
				final ReturnStatement returnStatement = ast.newReturnStatement();
				returnStatement.setExpression(invoc1);
				catchStatements.add(returnStatement);
			} else {
				catchStatements.add(ast.newExpressionStatement(invoc1));
			}
		}

		rewrite.replace(methodDeclaration.getBody(), block, null);

	}

}
