package org.tmatesoft.refactoring.split.core;

import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public abstract class SplitTargetChanges implements ISplitChanges {

	protected final String targetPackageName;
	protected final String targetSuffix;
	protected IPackageFragment targetPackage;

	public SplitTargetChanges(final String targetPackageName, final String targetSuffix) {
		this.targetPackageName = targetPackageName;
		this.targetSuffix = targetSuffix;
	}

	public String getTargetPackageName() {
		return targetPackageName;
	}

	public String getTargetSuffix() {
		return targetSuffix;
	}

	public IPackageFragment getTargetPackage() {
		return targetPackage;
	}

	protected void setTargetPackage(IPackageFragment targetPackage) {
		this.targetPackage = targetPackage;
	}

	public String addTargetSuffix(final String str) {
		return SplitUtils.addSuffix(str, getTargetSuffix());
	}

	@Override
	public boolean doChanges(SplitRefactoringModel model, RefactoringStatus status, SubProgressMonitor subMonitor) {

		setTargetPackage(model.getPackageRoot().getPackageFragment(getTargetPackageName()));
		if (getTargetPackage() == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus("Can't get target package."));
			return false;
		} else if (!getTargetPackage().exists()) {
			model.getChanges().add(new CreatePackageChange(getTargetPackage()));
		}

		return true;
	}

}
