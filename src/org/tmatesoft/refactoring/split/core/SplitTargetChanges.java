package org.tmatesoft.refactoring.split.core;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.MalformedTreeException;

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
	public boolean doChanges(final SplitRefactoringModel model, final RefactoringStatus status,
			final SubProgressMonitor monitor) {

		if (doChangeTargetPackage(model, status, monitor)) {
			return doChangeUnits(model, status, monitor);
		}

		return false;

	}

	protected boolean doChangeTargetPackage(SplitRefactoringModel model, RefactoringStatus status,
			SubProgressMonitor monitor) {

		setTargetPackage(model.getPackageRoot().getPackageFragment(getTargetPackageName()));
		if (getTargetPackage() == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus("Can't get target package."));
			return false;
		} else if (!getTargetPackage().exists()) {
			model.getChanges().add(new CreatePackageChange(getTargetPackage()));
		}

		return true;

	}

	protected boolean doChangeUnits(SplitRefactoringModel model, RefactoringStatus status, SubProgressMonitor monitor) {

		for (final Map.Entry<ICompilationUnit, SplitUnitModel> entry : model.getUnitModels().entrySet()) {
			try {
				final SplitUnitModel unitModel = entry.getValue();
				doUnitChange(unitModel, status, monitor);
			} catch (Exception exception) {
				SplitRefactoring.log(exception);
				return false;
			}
		}

		return true;

	}

	abstract protected void doUnitChange(final SplitUnitModel unitModel, RefactoringStatus status,
			SubProgressMonitor monitor) throws MalformedTreeException, CoreException, BadLocationException;

}
