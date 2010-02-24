package org.tmatesoft.refactoring.split.ui;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.tmatesoft.refactoring.split.core.SplitRefactoring;


public class SplitRefactoringWizard extends RefactoringWizard {

	public SplitRefactoringWizard(SplitRefactoring refactoring, String pageTitle) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
		setDefaultPageTitle(pageTitle);
	}

	@Override
	protected void addUserInputPages() {
		addPage(new SplitRefactoringWizardPage("SplitRefactoringWizardPage"));
	}

}
