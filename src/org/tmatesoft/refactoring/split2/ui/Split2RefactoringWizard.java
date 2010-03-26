package org.tmatesoft.refactoring.split2.ui;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.tmatesoft.refactoring.split2.core.Split2Refactoring;


public class Split2RefactoringWizard extends RefactoringWizard {

	public Split2RefactoringWizard(Split2Refactoring refactoring, String pageTitle) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
		setDefaultPageTitle(pageTitle);
	}

	@Override
	protected void addUserInputPages() {
		addPage(new Split2RefactoringWizardPage("Split2RefactoringWizardPage"));
	}

}
