package org.tmatesoft.refactoring.split2.ui;

import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

public class Split2RefactoringWizardPage extends UserInputWizardPage {

	public Split2RefactoringWizardPage(String name) {
		super(name);
	}

	public void createControl(Composite parent) {
		
		Composite result= new Composite(parent, SWT.NONE);

		setControl(result);


	}

}
