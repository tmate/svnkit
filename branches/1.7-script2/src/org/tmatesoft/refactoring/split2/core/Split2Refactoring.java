package org.tmatesoft.refactoring.split2.core;

import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.corext.refactoring.changes.CopyCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.jdt.internal.corext.refactoring.reorg.INewNameQuery;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

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

		} finally {
			pm.done();
		}

		return status;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		pm.beginTask("Creating change...", 1);

		try {

			final CompositeChange compositeChange = new CompositeChange(TITLE);

			compositeChange.add(buildChangesMoveClasses());

			return compositeChange;

		} finally {
			pm.done();
		}

	}

	private Change buildChangesMoveClasses() {

		final CompositeChange change = new CompositeChange(String.format("Move classes to package '%s'", model
				.getTargetMovePackageName()));

		final IPackageFragment targetPackage = model.getPackageRoot().getPackageFragment(
				model.getTargetMovePackageName());

		if (!targetPackage.exists()) {
			change.add(new CreatePackageChange(targetPackage));
		}

		final List<ICompilationUnit> sourceCompilationUnits = model.getSourceCompilationUnits();
		for (final ICompilationUnit sourceCompilationUnit : sourceCompilationUnits) {
			final String typeQualifiedName = sourceCompilationUnit.findPrimaryType().getTypeQualifiedName();
			final String newName = typeQualifiedName + model.getTargetMoveSuffix();
			final INewNameQuery newNameQuery = new INewNameQuery() {
				@Override
				public String getNewName() throws OperationCanceledException {
					return newName + ".java";
				}
			};
			change.add(new CopyCompilationUnitChange(sourceCompilationUnit, targetPackage, newNameQuery));
		}

		return change;
	}
}
