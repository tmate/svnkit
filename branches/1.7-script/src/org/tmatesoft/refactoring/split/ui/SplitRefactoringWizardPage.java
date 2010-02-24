package org.tmatesoft.refactoring.split.ui;

import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

public class SplitRefactoringWizardPage extends UserInputWizardPage {

	public SplitRefactoringWizardPage(String name) {
		super(name);
	}

	public void createControl(Composite parent) {
		
		Composite result= new Composite(parent, SWT.NONE);

		setControl(result);


	}

}
