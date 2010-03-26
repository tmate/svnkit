package org.tmatesoft.refactoring.split2.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.viewers.IStructuredSelection;

public class Split2RefactoringModel {

	private IStructuredSelection selection;
	private String sourcePackageName;
	private String targetMovePackageName;
	private String targetMoveSuffix;
	private String sourceClassNamePattern;
	private IProject project;
	private IJavaProject javaProject;
	private IPackageFragment sourcePackage;

	private final List<ICompilationUnit> sourceCompilationUnits = new ArrayList<ICompilationUnit>();
	private IPackageFragmentRoot packageRoot;

	public IStructuredSelection getSelection() {
		return selection;
	}

	public void setSelection(IStructuredSelection selection) {
		this.selection = selection;
	}

	public void setSourcePackageName(String sourcePackageName) {
		this.sourcePackageName = sourcePackageName;
	}

	public String getSourcePackageName() {
		return sourcePackageName;
	}

	public void setTargetMovePackageName(String targetMovePackageName) {
		this.targetMovePackageName = targetMovePackageName;
	}

	public String getTargetMovePackageName() {
		return targetMovePackageName;
	}

	public void setTargetMoveSuffix(String targetMoveSuffix) {
		this.targetMoveSuffix = targetMoveSuffix;
	}

	public String getTargetMoveSuffix() {
		return targetMoveSuffix;
	}

	public void setSourceClassNamePattern(String sourceClassNamePattern) {
		this.sourceClassNamePattern = sourceClassNamePattern;
	}

	public String getSourceClassNamePattern() {
		return sourceClassNamePattern;
	}

	public void setProject(IProject project) {
		this.project = project;
	}

	public IProject getProject() {
		return project;
	}

	public void setJavaProject(IJavaProject javaProject) {
		this.javaProject = javaProject;
	}

	public IJavaProject getJavaProject() {
		return javaProject;
	}

	public void setSourcePackage(IPackageFragment sourcePackage) {
		this.sourcePackage = sourcePackage;
	}

	public IPackageFragment getSourcePackage() {
		return sourcePackage;
	}

	public List<ICompilationUnit> getSourceCompilationUnits() {
		return sourceCompilationUnits;
	}

	public void setPackageRoot(IPackageFragmentRoot packageRoot) {
		this.packageRoot = packageRoot;
	}

	public IPackageFragmentRoot getPackageRoot() {
		return packageRoot;
	}

}
