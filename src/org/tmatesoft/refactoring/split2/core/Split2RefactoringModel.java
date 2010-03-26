package org.tmatesoft.refactoring.split2.core;

import org.eclipse.jface.viewers.IStructuredSelection;

public class Split2RefactoringModel {

	private IStructuredSelection selection;

	public IStructuredSelection getSelection() {
		return selection;
	}

	public void setSelection(IStructuredSelection selection) {
		this.selection = selection;
	}
	
	
}
