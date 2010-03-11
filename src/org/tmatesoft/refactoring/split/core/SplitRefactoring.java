package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.tmatesoft.refactoring.split.SplitRefactoringActivator;

public class SplitRefactoring extends Refactoring {

	public static final String TITLE = "Split refactoring";

	private SplitRefactoringModel model = new SplitRefactoringModel("org.tmatesoft.svn.core.wc", Arrays
			.asList(new String[] { "org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess",
					"org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea" }));

	private List<ISplitChanges> splitChanges = new ArrayList<ISplitChanges>(Arrays.asList(new ISplitChanges[] {
			new SplitMoveChanges("org.tmatesoft.svn.core.internal.wc.v16", "16"),
			new SplitStubChanges("org.tmatesoft.svn.core.internal.wc.v17", "17"),
			new SplitDelegateChanges("org.tmatesoft.svn.core.internal.wc.v17", "17",
					"org.tmatesoft.svn.core.internal.wc.v16", "16") }));

	@Override
	public String getName() {
		return TITLE;
	}

	public void setSelection(IStructuredSelection selection) {
		this.model.setSelection(selection);
	}

	/**
	 * @param exception
	 */
	static public void log(Exception exception) {
		SplitRefactoringActivator.getDefault().getLog().log(
				new Status(IStatus.ERROR, SplitRefactoringActivator.PLUGIN_ID, 0, exception.getMessage(), exception));
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor progressMonitor) throws CoreException,
			OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			progressMonitor.beginTask("Checking preconditions...", 1);

			if (model.getSelection() != null) {
				final Object element = model.getSelection().getFirstElement();
				if (element != null && element instanceof IProject) {
					model.setProject((IProject) element);
				}
			}

			if (model.getProject() == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Please select project"));
				return status;
			}

			model.setJavaProject(JavaCore.create(model.getProject()));

			if (model.getJavaProject() == null || !model.getJavaProject().exists()) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Please select Java project"));
				return status;
			}

			model.setProjectScope(SearchEngine.createJavaSearchScope(new IJavaElement[] { model.getJavaProject() },
					IJavaSearchScope.SOURCES));

			if (model.getSourcePackageName() == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Source package has not been specified."));
				return status;
			} else {
				model.setSourcePackage(searchPackage(model.getSourcePackageName(), model.getProjectScope(),
						progressMonitor, status));
			}
			if (model.getTypesToHideNames() == null || model.getTypesToHideNames().isEmpty()) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Types to hide have not been specified."));
				return status;
			} else {
				model.setTypesToHide(new ArrayList<IType>());
				for (final String typeName : model.getTypesToHideNames()) {
					final IType type = searchType(typeName, model.getProjectScope(), progressMonitor, status);
					if (type != null) {
						model.getTypesToHide().add(type);
					}
				}
				if (model.getTypesToHide().isEmpty()) {
					status.merge(RefactoringStatus.createFatalErrorStatus("Types to hide have not been found."));
					return status;
				}
			}
		} finally {
			progressMonitor.done();
		}

		return status;
	}

	private IPackageFragment searchPackage(String packageName, final IJavaSearchScope scope,
			IProgressMonitor progressMonitor, final RefactoringStatus status) throws CoreException {
		final SearchPattern pattern = SearchPattern.createPattern(packageName, IJavaSearchConstants.PACKAGE,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
		IPackageFragment found = SplitUtils.searchOneElement(IPackageFragment.class, pattern, scope, progressMonitor);
		if (found == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
					"Package '%s' has not been found in selected project", packageName)));
		}
		return found;
	}

	private IType searchType(String typeName, final IJavaSearchScope scope, IProgressMonitor progressMonitor,
			final RefactoringStatus status) throws CoreException {
		final SearchPattern pattern = SearchPattern.createPattern(typeName, IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
		final IType found = SplitUtils.searchOneElement(IType.class, pattern, scope, progressMonitor);
		if (found == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
					"Type '%s' has not been found in selected project", typeName)));
		}
		return found;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor progressMonitor) throws CoreException,
			OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			progressMonitor.beginTask("Checking preconditions...", 1);

			final IJavaElement ancestor = model.getSourcePackage().getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (ancestor != null && ancestor instanceof IPackageFragmentRoot) {
				model.setPackageRoot((IPackageFragmentRoot) ancestor);
			}
			if (model.getPackageRoot() == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus(String.format("Can't find package root for '%s'",
						model.getSourcePackage())));
				return status;
			}

			final IJavaSearchScope sourcePackageScope = SearchEngine.createJavaSearchScope(new IJavaElement[] { model
					.getSourcePackage() }, IJavaSearchScope.SOURCES);

			for (final IType typeToHide : model.getTypesToHide()) {
				final SearchPattern referencesPattern = SearchPattern.createPattern(typeToHide,
						IJavaSearchConstants.REFERENCES);
				final List<IMethod> methods = SplitUtils.searchManyElements(IMethod.class, referencesPattern,
						sourcePackageScope, progressMonitor);
				if (methods != null && !methods.isEmpty()) {
					for (final IMethod method : methods) {
						final ICompilationUnit unit = method.getCompilationUnit();
						if (!model.getUnits().containsKey(unit)) {
							model.getUnits().put(unit, new HashSet<IMethod>());
						}
						final Set<IMethod> set = model.getUnits().get(unit);
						if (!set.contains(method)) {
							set.add(method);
						}
					}
				}
			}

			final SubProgressMonitor subMonitor = new SubProgressMonitor(progressMonitor, 1);
			final ASTRequestor requestor = new ASTRequestor() {
				@Override
				public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
					try {
						final SplitUnitModel unitModel = SplitUnitModel.getUnitModel(source, ast, model);
						model.getUnitModels().put(source, unitModel);
					} catch (Exception exception) {
						log(exception);
					}
				}
			};

			final ASTParser parser = ASTParser.newParser(AST.JLS3);
			parser.setResolveBindings(true);
			parser.setProject(model.getJavaProject());
			final Collection<ICompilationUnit> collection = model.getUnits().keySet();
			final ICompilationUnit[] array = collection.toArray(new ICompilationUnit[collection.size()]);
			parser.createASTs(array, new String[0], requestor, new SubProgressMonitor(progressMonitor, 1));

			for (final SplitUnitModel unitModel : model.getUnitModels().values()) {
				try {
					unitModel.buildModel(model);
				} catch (Exception exception) {
					log(exception);
				}
			}

			for (final ISplitChanges splitChange : splitChanges) {
				if (!splitChange.doChanges(model, status, subMonitor)) {
					return status;
				}
			}

		} finally {
			progressMonitor.done();
		}

		return status;
	}

	@Override
	public Change createChange(IProgressMonitor progressMonitor) throws CoreException, OperationCanceledException {
		try {
			progressMonitor.beginTask("Creating change...", 1);
			final CompositeChange change = new CompositeChange(getName(), model.getChanges().toArray(
					new Change[model.getChanges().size()]));
			return change;
		} finally {
			progressMonitor.done();
		}

	}

}
