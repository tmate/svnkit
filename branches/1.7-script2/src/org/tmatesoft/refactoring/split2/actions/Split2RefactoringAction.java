package org.tmatesoft.refactoring.split2.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.tmatesoft.refactoring.split2.core.Split2Refactoring;
import org.tmatesoft.refactoring.split2.core.Split2RefactoringUtils;
import org.tmatesoft.refactoring.split2.ui.Split2RefactoringWizard;

public class Split2RefactoringAction implements IObjectActionDelegate {

	private IWorkbenchPart targetPart;
	private IStructuredSelection selection;

	public Split2RefactoringAction() {
	}

	public void run(IAction action) {
		Split2Refactoring refactoring = new Split2Refactoring();
		refactoring.setSelection(selection);
		run(new Split2RefactoringWizard(refactoring, Split2Refactoring.TITLE), targetPart.getSite().getShell(),
				Split2Refactoring.TITLE);
	}

	public void run(RefactoringWizard wizard, Shell parent, String dialogTitle) {
		try {
			RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
			operation.run(parent, dialogTitle);
		} catch (InterruptedException exception) {
			Split2RefactoringUtils.log(exception);
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
