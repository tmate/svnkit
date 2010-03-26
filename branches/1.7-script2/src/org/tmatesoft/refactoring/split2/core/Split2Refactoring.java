package org.tmatesoft.refactoring.split2.core;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.tmatesoft.refactoring.split2.Split2RefactoringActivator;

public class Split2Refactoring extends Refactoring {

	public static final String TITLE = "Split2 refactoring";

	private Split2RefactoringModel model = new Split2RefactoringModel();

	public Split2Refactoring() {
		super();
	}

	@Override
	public String getName() {
		return TITLE;
	}

	public void setSelection(IStructuredSelection selection) {
		model.setSelection(selection);
	}

	static public void log(Exception exception) {
		Split2RefactoringActivator.log(exception);
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			pm.beginTask("Checking preconditions...", 1);
		} finally {
			pm.done();
		}

		return status;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			pm.beginTask("Checking preconditions...", 1);
		} finally {
			pm.done();
		}

		return status;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException,
			OperationCanceledException {
		final CompositeChange compositeChange = new CompositeChange(TITLE);
		compositeChange.add(new NullChange());
		return compositeChange;
	}

}
