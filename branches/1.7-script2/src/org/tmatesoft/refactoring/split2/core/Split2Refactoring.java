package org.tmatesoft.refactoring.split2.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class Split2Refactoring extends Refactoring {

	public static final String TITLE = "Split2 refactoring";

	private Split2RefactoringModel model = new Split2RefactoringModel();

	public Split2Refactoring() {
		super();
		model = new Split2RefactoringModel();
		initModel(model);
	}

	private static void initModel(Split2RefactoringModel model) {
		model.setSourcePackageName("org.tmatesoft.svn.core.wc");
		model.setSourceClassNamePattern("SVN[\\w]*Client");
		model.getTargetNamesMap().put("SVNBasicClient", "SVNBasicDelegate");
		model.setTargetMovePackageName("org.tmatesoft.svn.core.internal.wc16");
		model.setTargetMoveSuffix("16");
		model.setTargetStubPackageName("org.tmatesoft.svn.core.internal.wc17");
		model.setTargetStubSuffix("17");
		model.getSourceMoveClassesNames().add("org.tmatesoft.svn.core.internal.wc.SVNCopyDriver");
		model.getSourceMoveClassesNames().add("org.tmatesoft.svn.core.internal.wc.SVNMergeDriver");
		model.getSourceMoveClassesNames().add("org.tmatesoft.svn.core.wc.admin.SVNAdminClient");
	}

	@Override
	public String getName() {
		return TITLE;
	}

	public void setSelection(IStructuredSelection selection) {
		model.setSelection(selection);
	}

	static public void log(Exception exception) {
		Split2RefactoringUtils.log(exception);
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException,
			OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			pm.beginTask("Checking preconditions...", 1);

			if (!searchProject(status)) {
				return status;
			}
			pm.worked(1);

		} finally {
			pm.done();
		}

		return status;
	}

	private boolean searchProject(final RefactoringStatus status) {

		if (model.getSelection() != null) {
			final Object element = model.getSelection().getFirstElement();
			if (element != null && element instanceof IProject) {
				model.setProject((IProject) element);
			}
		}

		if (model.getProject() == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus("Please select project"));
			return false;
		}

		model.setJavaProject(JavaCore.create(model.getProject()));

		if (model.getJavaProject() == null || !model.getJavaProject().exists()) {
			status.merge(RefactoringStatus.createFatalErrorStatus("Please select Java project"));
			return false;
		}

		return true;

	}

	private boolean searchSourceClasses(final RefactoringStatus status, IProgressMonitor pm) throws CoreException {

		final IJavaSearchScope scope = Split2RefactoringUtils.createSearchScope(new IJavaElement[] { model
				.getJavaProject() });

		final SearchPattern pattern = SearchPattern.createPattern(model.getSourcePackageName(),
				IJavaSearchConstants.PACKAGE, IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);

		final IPackageFragment sourcePackage = Split2RefactoringUtils.searchOneElement(IPackageFragment.class, pattern,
				scope, pm);

		if (sourcePackage == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
					"Package '%s' has not been found in selected project", model.getSourcePackageName())));
			return false;
		}

		model.setSourcePackage(sourcePackage);
		model.setPackageRoot((IPackageFragmentRoot) sourcePackage.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT));

		final Pattern classNamePattern = Pattern.compile(model.getSourceClassNamePattern());
		final ICompilationUnit[] compilationUnits = sourcePackage.getCompilationUnits();
		for (final ICompilationUnit compilationUnit : compilationUnits) {
			final String typeQualifiedName = compilationUnit.findPrimaryType().getTypeQualifiedName();
			if (classNamePattern.matcher(typeQualifiedName).matches()) {
				model.getSourceCompilationUnits().add(compilationUnit);
			}
		}
		pm.worked(3);

		if (model.getSourceCompilationUnits().size() == 0) {
			status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
					"Not found classes with pattern '%s' in package '%s'", model.getSourceClassNamePattern(), model
							.getSourcePackageName())));
			return false;
		}

		for (final String sourceMoveClassName : model.getSourceMoveClassesNames()) {

			final SearchPattern sourceMoveClassPattern = SearchPattern.createPattern(sourceMoveClassName,
					IJavaSearchConstants.CLASS, IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);

			final IType sourceMoveClass = Split2RefactoringUtils.searchOneElement(IType.class, sourceMoveClassPattern,
					scope, pm);

			if (sourceMoveClass != null && sourceMoveClass.exists()) {
				model.getSourceMoveClassesUnits().add(sourceMoveClass.getCompilationUnit());
			}

		}

		return true;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			pm.beginTask("Checking preconditions...", 3);
			if (!searchSourceClasses(status, pm)) {
				return status;
			}

			parseSourceClasses(pm);

		} finally {
			pm.done();
		}

		return status;
	}

	private void parseSourceClasses(IProgressMonitor pm) {

		final ASTRequestor requestor = new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				try {
					model.getParsedUnits().put(source, ast);
				} catch (Exception exception) {
					log(exception);
				}
			}
		};

		ASTParser parser = ASTParser.newParser(AST.JLS3);
		final ICompilationUnit[] units = model.getSourceCompilationUnits().toArray(
				new ICompilationUnit[model.getSourceCompilationUnits().size()]);
		parser.createASTs(units, new String[0], requestor, new SubProgressMonitor(pm, 1));

		final ICompilationUnit[] moveUnits = model.getSourceMoveClassesUnits().toArray(
				new ICompilationUnit[model.getSourceMoveClassesUnits().size()]);
		parser.createASTs(moveUnits, new String[0], requestor, new SubProgressMonitor(pm, 1));

	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		pm.beginTask("Creating change...", 1);
		final CompositeChange changes = new CompositeChange(TITLE);

		try {
			changes.add(buildChangesTargetMoveClasses(pm));
			changes.add(buildChangesTargetStubClasses(pm));
			changes.add(buildChangesSourceMoveClasses(pm));
			changes.add(buildChangesSourceStubClasses(pm));
			return changes;
		} catch (MalformedTreeException e) {
			log(e);
			return changes;
		} catch (BadLocationException e) {
			log(e);
			return changes;
		} finally {
			pm.done();
		}

	}

	// Move classes

	private Change buildChangesTargetMoveClasses(IProgressMonitor pm) throws CoreException, MalformedTreeException,
			BadLocationException {

		final CompositeChange changes = new CompositeChange(String.format("Move classes to package '%s'", model
				.getTargetMovePackageName()));

		final IPackageFragment targetPackage = model.getPackageRoot().getPackageFragment(
				model.getTargetMovePackageName());

		if (!targetPackage.exists()) {
			changes.add(new CreatePackageChange(targetPackage));
		}

		final List<ICompilationUnit> sourceCompilationUnits = model.getSourceCompilationUnits();
		for (final ICompilationUnit sourceUnit : sourceCompilationUnits) {

			final IType sourcePrimaryType = sourceUnit.findPrimaryType();
			final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();
			final String targetTypeName = model.getTargetNamesMap().containsKey(sourceTypeName) ? model
					.getTargetNamesMap().get(sourceTypeName) : sourceTypeName + model.getTargetMoveSuffix();

			final ICompilationUnit targetUnit = targetPackage.getCompilationUnit(targetTypeName + ".java");

			final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);

			final AST targetAST = AST.newAST(AST.JLS3);
			final CompilationUnit targetUnitNode = targetAST.newCompilationUnit();

			final PackageDeclaration packageDeclaration = targetAST.newPackageDeclaration();
			packageDeclaration.setName(targetAST.newName(model.getTargetMovePackageName()));
			targetUnitNode.setPackage(packageDeclaration);

			final List<ImportDeclaration> targetImports = targetUnitNode.imports();

			final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
			for (final ImportDeclaration sourceImportDeclaration : sourceImports) {
				ImportDeclaration targetImportDeclaration = (ImportDeclaration) ASTNode.copySubtree(targetAST,
						sourceImportDeclaration);
				targetImports.add(targetImportDeclaration);
			}

			final ImportDeclaration targetImportDeclaration = targetAST.newImportDeclaration();
			targetImportDeclaration.setOnDemand(true);
			targetImportDeclaration.setName(targetAST.newName(model.getSourcePackageName()));
			targetImports.add(targetImportDeclaration);

			final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
					sourcePrimaryType.getSourceRange());
			final TypeDeclaration targetTypeDeclaration = (TypeDeclaration) ASTNode.copySubtree(targetAST,
					sourceTypeDeclaration);
			targetUnitNode.types().add(targetTypeDeclaration);

			final Type targetSuperclassType = targetTypeDeclaration.getSuperclassType();
			if (targetSuperclassType != null) {
				if (targetSuperclassType.isSimpleType()) {
					final SimpleType targetSuperclassSimpleType = (SimpleType) targetSuperclassType;
					final Name targetSuperclassName = targetSuperclassSimpleType.getName();
					if (targetSuperclassName.isSimpleName()) {
						final SimpleName targetSuperclassSimpleName = (SimpleName) targetSuperclassName;
						final String targetSuperclassIdentifier = targetSuperclassSimpleName.getIdentifier();
						if (model.getTargetNamesMap().containsKey(targetSuperclassIdentifier)) {
							targetSuperclassSimpleName.setIdentifier(model.getTargetNamesMap().get(
									targetSuperclassIdentifier));
						}
					}
				}
			}

			targetUnitNode.accept(new ASTVisitor() {
				@Override
				public boolean visit(SimpleName node) {
					if (sourceTypeName.equals(node.getIdentifier())) {
						node.setIdentifier(targetTypeName);
					}
					return super.visit(node);
				}
			});

			final String targetUnitText = targetUnitNode.toString();
			final Document targetUnitDocument = new Document(targetUnitText);
			final TextEdit formatEdit = model.getCodeFormatter().format(CodeFormatter.K_COMPILATION_UNIT,
					targetUnitText, 0, targetUnitText.length(), 0,
					model.getJavaProject().findRecommendedLineSeparator());
			formatEdit.apply(targetUnitDocument);

			changes.add(new CreateCompilationUnitChange(targetUnit, targetUnitDocument.get(), null));

		}

		return changes;
	}

	// Stub classes

	private Change buildChangesTargetStubClasses(IProgressMonitor pm) throws JavaModelException,
			MalformedTreeException, BadLocationException {

		final CompositeChange changes = new CompositeChange(String.format("Create stub classes in package '%s'", model
				.getTargetStubPackageName()));

		final IPackageFragment targetPackage = model.getPackageRoot().getPackageFragment(
				model.getTargetStubPackageName());

		if (!targetPackage.exists()) {
			changes.add(new CreatePackageChange(targetPackage));
		}

		final List<ICompilationUnit> sourceCompilationUnits = model.getSourceCompilationUnits();
		for (final ICompilationUnit sourceUnit : sourceCompilationUnits) {

			final IType sourcePrimaryType = sourceUnit.findPrimaryType();
			final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();

			if (model.getTargetNamesMap().containsKey(sourceTypeName)) {
				continue;
			}

			final String targetTypeName = sourceTypeName + model.getTargetStubSuffix();

			final ICompilationUnit targetUnit = targetPackage.getCompilationUnit(targetTypeName + ".java");

			final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);

			final AST targetAST = AST.newAST(AST.JLS3);
			final CompilationUnit targetUnitNode = targetAST.newCompilationUnit();

			final PackageDeclaration packageDeclaration = targetAST.newPackageDeclaration();
			packageDeclaration.setName(targetAST.newName(model.getTargetStubPackageName()));
			targetUnitNode.setPackage(packageDeclaration);

			final List<ImportDeclaration> targetImports = targetUnitNode.imports();

			final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
			for (final ImportDeclaration sourceImportDeclaration : sourceImports) {
				ImportDeclaration targetImportDeclaration = (ImportDeclaration) ASTNode.copySubtree(targetAST,
						sourceImportDeclaration);
				targetImports.add(targetImportDeclaration);
			}

			{
				final ImportDeclaration targetImportDeclaration = targetAST.newImportDeclaration();
				targetImportDeclaration.setOnDemand(true);
				targetImportDeclaration.setName(targetAST.newName(model.getSourcePackageName()));
				targetImports.add(targetImportDeclaration);
			}

			{
				final ImportDeclaration targetImportDeclaration = targetAST.newImportDeclaration();
				targetImportDeclaration.setOnDemand(true);
				targetImportDeclaration.setName(targetAST.newName(model.getTargetMovePackageName()));
				targetImports.add(targetImportDeclaration);
			}

			addErrorsImports(targetAST, targetUnitNode);

			final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
					sourcePrimaryType.getSourceRange());
			final TypeDeclaration targetTypeDeclaration = (TypeDeclaration) ASTNode.copySubtree(targetAST,
					sourceTypeDeclaration);
			targetUnitNode.types().add(targetTypeDeclaration);

			final Type targetSuperclassType = targetTypeDeclaration.getSuperclassType();
			if (targetSuperclassType != null) {
				if (targetSuperclassType.isSimpleType()) {
					final SimpleType targetSuperclassSimpleType = (SimpleType) targetSuperclassType;
					final Name targetSuperclassName = targetSuperclassSimpleType.getName();
					if (targetSuperclassName.isSimpleName()) {
						final SimpleName targetSuperclassSimpleName = (SimpleName) targetSuperclassName;
						final String targetSuperclassIdentifier = targetSuperclassSimpleName.getIdentifier();
						if (model.getTargetNamesMap().containsKey(targetSuperclassIdentifier)) {
							targetSuperclassSimpleName.setIdentifier(model.getTargetNamesMap().get(
									targetSuperclassIdentifier));
						}
					}
				}
			}

			final MethodDeclaration[] targetMethods = targetTypeDeclaration.getMethods();
			for (final MethodDeclaration targetMethodDeclaration : targetMethods) {

				if (targetMethodDeclaration.isConstructor()) {
					continue;
				}

				boolean isPublic = false;
				final List<IExtendedModifier> modifiers = targetMethodDeclaration.modifiers();
				for (final IExtendedModifier extendedModifier : modifiers) {
					if (extendedModifier.isModifier()) {
						final Modifier modifier = (Modifier) extendedModifier;
						if (modifier.isPublic()) {
							isPublic = true;
							break;
						}
					}
				}
				if (!isPublic) {
					targetMethodDeclaration.delete();
				} else {
					final SimpleName targetMethodName = targetMethodDeclaration.getName();
					final String targetMethodIdentifier = targetMethodName.getIdentifier();
					if (targetMethodIdentifier.startsWith("do") || targetMethodIdentifier.startsWith("undo")) {
						final List<Statement> statements = targetMethodDeclaration.getBody().statements();
						if (statements != null) {
							final List<Statement> emptyBody = new ArrayList<Statement>();
							insertErrorVersionMismatch(targetAST, targetMethodDeclaration, emptyBody);
							insertDefaultReturn(targetAST, targetMethodDeclaration, emptyBody);
							statements.clear();
							if (!emptyBody.isEmpty()) {
								statements.addAll(emptyBody);
							}
						}
					}
				}
			}

			final TypeDeclaration[] types = targetTypeDeclaration.getTypes();
			for (final TypeDeclaration typeDeclaration : types) {
				boolean isPublic = false;
				final List<IExtendedModifier> modifiers = typeDeclaration.modifiers();
				for (final IExtendedModifier extendedModifier : modifiers) {
					if (extendedModifier.isModifier()) {
						final Modifier modifier = (Modifier) extendedModifier;
						if (modifier.isPublic()) {
							isPublic = true;
							break;
						}
					}
				}
				if (!isPublic) {
					typeDeclaration.delete();
				}
			}

			targetUnitNode.accept(new ASTVisitor() {
				@Override
				public boolean visit(SimpleName node) {
					if (sourceTypeName.equals(node.getIdentifier())) {
						node.setIdentifier(targetTypeName);
					}
					return super.visit(node);
				}
			});

			final String targetUnitText = targetUnitNode.toString();
			final Document targetUnitDocument = new Document(targetUnitText);
			final TextEdit formatEdit = model.getCodeFormatter().format(CodeFormatter.K_COMPILATION_UNIT,
					targetUnitText, 0, targetUnitText.length(), 0,
					model.getJavaProject().findRecommendedLineSeparator());
			formatEdit.apply(targetUnitDocument);

			changes.add(new CreateCompilationUnitChange(targetUnit, targetUnitDocument.get(), null));

		}

		return changes;
	}

	private void addErrorsImports(final AST ast, final CompilationUnit node) {

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

	private void insertErrorVersionMismatch(final AST ast, final MethodDeclaration methodCopy,
			final List<Statement> emptyBody) {

		boolean found = false;
		final List<Name> thrownExceptions = methodCopy.thrownExceptions();
		for (final Name name : thrownExceptions) {
			if (name.getFullyQualifiedName().endsWith("SVNException")) {
				found = true;
				break;
			}
		}
		if (!found) {
			return;
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

	// Dispatcher classes

	private Change buildChangesSourceMoveClasses(IProgressMonitor pm) throws JavaModelException,
			MalformedTreeException, BadLocationException {

		final CompositeChange changes = new CompositeChange(
				"Modify dependent classes to inherits from moved base delegate");

		final List<ICompilationUnit> sourceMoveUnits = model.getSourceMoveClassesUnits();
		for (final ICompilationUnit sourceUnit : sourceMoveUnits) {

			final IType sourcePrimaryType = sourceUnit.findPrimaryType();
			final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();

			final String sourceUnitText = sourceUnit.getSource();
			final Document sourceUnitDocument = new Document(sourceUnitText);

			final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);
			final AST sourceAst = sourceParsedUnit.getAST();

			final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
					sourcePrimaryType.getSourceRange());

			final Type sourceSuperclassType = sourceTypeDeclaration.getSuperclassType();
			if (sourceSuperclassType != null) {
				if (sourceSuperclassType.isSimpleType()) {
					final SimpleType sourceSuperclassSimpleType = (SimpleType) sourceSuperclassType;
					final Name sourceSuperclassName = sourceSuperclassSimpleType.getName();
					if (sourceSuperclassName.isSimpleName()) {
						final SimpleName sourceSuperclassSimpleName = (SimpleName) sourceSuperclassName;
						final String sourceSuperclassIdentifier = sourceSuperclassSimpleName.getIdentifier();
						if (model.getTargetNamesMap().containsKey(sourceSuperclassIdentifier)) {

							sourceParsedUnit.recordModifications();

							final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
							{
								final ImportDeclaration importDeclaration = sourceAst.newImportDeclaration();
								importDeclaration.setOnDemand(true);
								importDeclaration.setName(sourceAst.newName(model.getTargetMovePackageName()));
								sourceImports.add(importDeclaration);
							}

							sourceSuperclassSimpleName.setIdentifier(model.getTargetNamesMap().get(
									sourceSuperclassIdentifier));

							final TextEdit sourceUnitEdits = sourceParsedUnit.rewrite(sourceUnitDocument, model
									.getJavaProject().getOptions(true));
							final TextFileChange sourceTextFileChange = new TextFileChange(sourceUnit.getElementName(),
									(IFile) sourceUnit.getResource());
							sourceTextFileChange.setEdit(sourceUnitEdits);
							changes.add(sourceTextFileChange);
						}
					}
				}
			}

		}

		return changes;
	}

	private Change buildChangesSourceStubClasses(IProgressMonitor pm) throws JavaModelException,
			MalformedTreeException, BadLocationException {

		final CompositeChange changes = new CompositeChange(String.format(
				"Modify classes in package '%s' to work as dispatchers", model.getSourcePackageName()));

		final List<ICompilationUnit> sourceCompilationUnits = model.getSourceCompilationUnits();
		for (final ICompilationUnit sourceUnit : sourceCompilationUnits) {

			final IType sourcePrimaryType = sourceUnit.findPrimaryType();
			final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();

			final String sourceUnitText = sourceUnit.getSource();
			final Document sourceUnitDocument = new Document(sourceUnitText);

			final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);
			final AST sourceAst = sourceParsedUnit.getAST();

			sourceParsedUnit.recordModifications();

			final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
			{
				final ImportDeclaration importDeclaration = sourceAst.newImportDeclaration();
				importDeclaration.setOnDemand(true);
				importDeclaration.setName(sourceAst.newName(model.getTargetMovePackageName()));
				sourceImports.add(importDeclaration);
			}

			{
				final ImportDeclaration importDeclaration = sourceAst.newImportDeclaration();
				importDeclaration.setOnDemand(true);
				importDeclaration.setName(sourceAst.newName(model.getTargetStubPackageName()));
				sourceImports.add(importDeclaration);
			}

			final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
					sourcePrimaryType.getSourceRange());

			final MethodDeclaration[] sourceMethods = sourceTypeDeclaration.getMethods();
			for (final MethodDeclaration sourceMethodDeclaration : sourceMethods) {

				if (sourceMethodDeclaration.isConstructor()) {
					continue;
				}

				boolean isPublic = false;
				final List<IExtendedModifier> modifiers = sourceMethodDeclaration.modifiers();
				for (final IExtendedModifier extendedModifier : modifiers) {
					if (extendedModifier.isModifier()) {
						final Modifier modifier = (Modifier) extendedModifier;
						if (modifier.isPublic()) {
							isPublic = true;
							break;
						}
					}
				}
				if (!isPublic) {
					sourceMethodDeclaration.delete();
				} else {
					final SimpleName sourceMethodName = sourceMethodDeclaration.getName();
					final String sourceMethodIdentifier = sourceMethodName.getIdentifier();
					if (sourceMethodIdentifier.startsWith("do") || sourceMethodIdentifier.startsWith("undo")) {
						final List<Statement> statements = sourceMethodDeclaration.getBody().statements();
						if (statements != null) {
							final List<Statement> emptyBody = new ArrayList<Statement>();
							insertDefaultReturn(sourceAst, sourceMethodDeclaration, emptyBody);
							statements.clear();
							if (!emptyBody.isEmpty()) {
								statements.addAll(emptyBody);
							}
						}
					}
				}
			}

			final TypeDeclaration[] types = sourceTypeDeclaration.getTypes();
			for (final TypeDeclaration typeDeclaration : types) {
				boolean isPublic = false;
				final List<IExtendedModifier> modifiers = typeDeclaration.modifiers();
				for (final IExtendedModifier extendedModifier : modifiers) {
					if (extendedModifier.isModifier()) {
						final Modifier modifier = (Modifier) extendedModifier;
						if (modifier.isPublic()) {
							isPublic = true;
							break;
						}
					}
				}
				if (!isPublic) {
					typeDeclaration.delete();
				}
			}

			final TextEdit sourceUnitEdits = sourceParsedUnit.rewrite(sourceUnitDocument, model.getJavaProject()
					.getOptions(true));
			final TextFileChange sourceTextFileChange = new TextFileChange(sourceUnit.getElementName(),
					(IFile) sourceUnit.getResource());
			sourceTextFileChange.setEdit(sourceUnitEdits);
			changes.add(sourceTextFileChange);

		}

		return changes;
	}

}
