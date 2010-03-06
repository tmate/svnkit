package org.tmatesoft.refactoring.split.core;

import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public interface ISplitChanges {

	void doChanges(SplitRefactoringModel model, RefactoringStatus status, SubProgressMonitor subMonitor);

}
