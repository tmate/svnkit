package org.tmatesoft.refactoring.split2.core;

import java.util.List;
import java.util.regex.Pattern;

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
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
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
		model.setTargetMovePackageName("org.tmatesoft.svn.core.internal.wc.v16");
		model.setTargetMoveSuffix("16");
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

		return true;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			pm.beginTask("Checking preconditions...", 2);
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
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		pm.beginTask("Creating change...", 1);
		final CompositeChange changes = new CompositeChange(TITLE);

		try {
			changes.add(buildChangesMoveClasses());
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

	private Change buildChangesMoveClasses() throws CoreException, MalformedTreeException, BadLocationException {

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
			final String targetTypeName = sourceTypeName + model.getTargetMoveSuffix();

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
}
