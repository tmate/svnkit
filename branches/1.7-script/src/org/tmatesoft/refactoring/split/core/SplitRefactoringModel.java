package org.tmatesoft.refactoring.split.core;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;

public class SplitRefactoringModel {

	private String sourcePackageName;
	private List<String> typesToHideNames;
	private IStructuredSelection selection;
	private IProject project;
	private IJavaProject javaProject;
	private IPackageFragmentRoot packageRoot;
	private IPackageFragment sourcePackage;
	private List<IType> typesToHide;

	private List<String> blackListTypesNames;
	private List<String> whiteListTypesNames;

	private IJavaSearchScope projectScope;

	private final List<Change> changes = new LinkedList<Change>();
	private final CodeFormatter codeFormatter = ToolFactory.createCodeFormatter(null);

	private final Map<ICompilationUnit, Set<IMethod>> units = new LinkedHashMap<ICompilationUnit, Set<IMethod>>();
	private final Map<ICompilationUnit, SplitUnitModel> unitModels = new LinkedHashMap<ICompilationUnit, SplitUnitModel>();
	
	public SplitRefactoringModel(final String sourcePackageName, final List<String> typesToHideNames,
			final List<String> blackListTypesNames, final List<String> whiteListTypesNames) {
		this.sourcePackageName = sourcePackageName;
		this.typesToHideNames = typesToHideNames;
		this.blackListTypesNames = blackListTypesNames;
		this.whiteListTypesNames = whiteListTypesNames;
	}

	public String getSourcePackageName() {
		return sourcePackageName;
	}

	public void setSourcePackageName(String sourcePackageName) {
		this.sourcePackageName = sourcePackageName;
	}

	public List<String> getTypesToHideNames() {
		return typesToHideNames;
	}

	public void setTypesToHideNames(List<String> typesToHideNames) {
		this.typesToHideNames = typesToHideNames;
	}

	public IStructuredSelection getSelection() {
		return selection;
	}

	public void setSelection(IStructuredSelection selection) {
		this.selection = selection;
	}

	public IProject getProject() {
		return project;
	}

	public void setProject(IProject project) {
		this.project = project;
	}

	public IJavaProject getJavaProject() {
		return javaProject;
	}

	public void setJavaProject(IJavaProject javaProject) {
		this.javaProject = javaProject;
	}

	public IPackageFragmentRoot getPackageRoot() {
		return packageRoot;
	}

	public void setPackageRoot(IPackageFragmentRoot packageRoot) {
		this.packageRoot = packageRoot;
	}

	public IPackageFragment getSourcePackage() {
		return sourcePackage;
	}

	public void setSourcePackage(IPackageFragment sourcePackage) {
		this.sourcePackage = sourcePackage;
	}

	public List<IType> getTypesToHide() {
		return typesToHide;
	}

	public void setTypesToHide(List<IType> typesToHide) {
		this.typesToHide = typesToHide;
	}

	public IJavaSearchScope getProjectScope() {
		return projectScope;
	}

	public void setProjectScope(IJavaSearchScope projectScope) {
		this.projectScope = projectScope;
	}

	public Map<ICompilationUnit, Set<IMethod>> getUnits() {
		return units;
	}

	public List<Change> getChanges() {
		return changes;
	}

	public CodeFormatter getCodeFormatter() {
		return codeFormatter;
	}

	public Map<ICompilationUnit, SplitUnitModel> getUnitModels() {
		return unitModels;
	}

	public List<String> getBlackListTypesNames() {
		return blackListTypesNames;
	}

	public List<String> getWhiteListTypesNames() {
		return whiteListTypesNames;
	}

}