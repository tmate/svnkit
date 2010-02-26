package org.tmatesoft.refactoring.split.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.tmatesoft.refactoring.split.core.SplitRefactoring;
import org.tmatesoft.refactoring.split.ui.SplitRefactoringWizard;

public class SplitRefactoringAction implements IObjectActionDelegate {

	private IWorkbenchPart targetPart;
	private IStructuredSelection selection;

	public SplitRefactoringAction() {
	}

	public void run(IAction action) {
		SplitRefactoring refactoring = new SplitRefactoring();
		refactoring.setSelection(selection);
		run(new SplitRefactoringWizard(refactoring, SplitRefactoring.TITLE), targetPart.getSite().getShell(),
				SplitRefactoring.TITLE);
	}

	public void run(RefactoringWizard wizard, Shell parent, String dialogTitle) {
		try {
			RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
			operation.run(parent, dialogTitle);
		} catch (InterruptedException exception) {
			// Do nothing
		}
	}

	public void selectionChanged(IAction action, ISelection selection) {
		if (selection != null && !selection.isEmpty() && selection instanceof IStructuredSelection) {
			this.selection = (IStructuredSelection) selection;
		}
	}

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		this.targetPart = targetPart;
	}
}