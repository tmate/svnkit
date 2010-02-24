package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class SplitRefactoring extends Refactoring {
	
	public static final String TITLE = "Split refactoring";

	private String sourcePackageName = "org.tmatesoft.svn.core.wc";
	private String targetPackageName = "org.tmatesoft.svn.core.internal.wc.v16";
	private List<String> typesToHideNames = Arrays.asList(new String[] {
			"org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess",
			"org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea" });

	private IStructuredSelection selection;
	private IProject project;
	private IJavaProject javaProject;

	private IPackageFragment sourcePackage;
	private IPackageFragment targetPackage;
	private List<IType> typesToHide = new ArrayList<IType>();

	private SearchEngine searchEngine = new SearchEngine();

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor progressMonitor) throws CoreException,
			OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			progressMonitor.beginTask("Checking preconditions...", 2);

			if (selection != null) {
				final Object element = selection.getFirstElement();
				if (element != null && element instanceof IProject) {
					project = (IProject) element;
				}
			}

			if (project == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Please select project"));
				return status;
			}

			javaProject = JavaCore.create(project);

			final IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { javaProject },
					IJavaSearchScope.SOURCES);

			if (sourcePackageName == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Source package has not been specified."));
			} else {
				sourcePackage = searchPackage(sourcePackageName, scope, progressMonitor, status);
			}
			if (targetPackageName == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Target package has not been specified."));
			}
			if (typesToHideNames == null || typesToHideNames.isEmpty()) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Types to hide have not been specified."));
			} else {
				for (final String typeName : typesToHideNames) {
					final IType type = searchType(typeName, scope, progressMonitor, status);
					if (type != null) {
						typesToHide.add(type);
					}
				}
				if (typesToHide == null || typesToHide.isEmpty()) {
					status.merge(RefactoringStatus.createFatalErrorStatus("Types to hide have not been found."));
				}
			}
		} finally {
			progressMonitor.done();
		}

		return status;
	}

	/**
	 * @param packageName
	 * @param scope
	 * @param progressMonitor
	 * @param status
	 * @throws CoreException
	 */
	private IPackageFragment searchPackage(String packageName, final IJavaSearchScope scope,
			IProgressMonitor progressMonitor, final RefactoringStatus status) throws CoreException {
		final SearchPattern pattern = SearchPattern.createPattern(packageName, IJavaSearchConstants.PACKAGE,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
		IPackageFragment found = searchOneElement(IPackageFragment.class, pattern, scope, progressMonitor);
		if (found == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
					"Package '%s' has not been found in selected project", packageName)));
		}
		return found;
	}

	/**
	 * @param typeName
	 * @param scope
	 * @param progressMonitor
	 * @param status
	 * @throws CoreException
	 */
	private IType searchType(String typeName, final IJavaSearchScope scope, IProgressMonitor progressMonitor,
			final RefactoringStatus status) throws CoreException {
		final SearchPattern pattern = SearchPattern.createPattern(typeName, IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
		final IType found = searchOneElement(IType.class, pattern, scope, progressMonitor);
		if (found == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
					"Type '%s' has not been found in selected project", typeName)));
		}
		return found;
	}

	/**
	 * @param class1
	 * @param pattern
	 * @param scope
	 * @param progressMonitor
	 * @throws CoreException
	 */
	private <T extends IJavaElement> T searchOneElement(final Class<T> type, final SearchPattern pattern,
			final IJavaSearchScope scope, final IProgressMonitor progressMonitor) throws CoreException {

		class SearchRequestorImpl extends SearchRequestor {
			T found;

			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				if (match.getAccuracy() == SearchMatch.A_ACCURATE && !match.isInsideDocComment()) {
					final Object element = match.getElement();
					if (element != null && type.isAssignableFrom(element.getClass())) {
						found = type.cast(element);
					}
				}
			}
		}

		final SearchRequestorImpl requestor = new SearchRequestorImpl();
		searchEngine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope,
				requestor, progressMonitor);
		return requestor.found;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		// TODO

		return status;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		// TODO

		return new NullChange();

	}

	@Override
	public String getName() {
		return TITLE;
	}

	public void setSelection(IStructuredSelection selection) {
		this.selection = selection;
	}

}
