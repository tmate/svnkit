package org.tmatesoft.refactoring.split.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

public class SplitUnitModel {

	private final ICompilationUnit sourceUnit;
	private final Set<IMethod> sourceMethods;
	private final CompilationUnit sourceAst;

	private Map<IMethod, MethodDeclaration> addMethods = new HashMap<IMethod, MethodDeclaration>();
	private Set<IType> usedTypes = new HashSet<IType>();
	private Set<IField> usedFields = new HashSet<IField>();
	private Set<IType> nestedTypes = new HashSet<IType>();

	public SplitUnitModel(final ICompilationUnit sourceUnit, final Set<IMethod> sourceMethods,
			final CompilationUnit sourceAst) {
		this.sourceUnit = sourceUnit;
		this.sourceMethods = sourceMethods;
		this.sourceAst = sourceAst;
	}

	public ICompilationUnit getSourceUnit() {
		return sourceUnit;
	}

	public Set<IMethod> getSourceMethods() {
		return sourceMethods;
	}

	public CompilationUnit getSourceAst() {
		return sourceAst;
	}

	public Map<IMethod, MethodDeclaration> getAddMethods() {
		return addMethods;
	}

	public Set<IType> getUsedTypes() {
		return usedTypes;
	}

	public Set<IField> getUsedFields() {
		return usedFields;
	}

	public Set<IType> getNestedTypes() {
		return nestedTypes;
	}

	public static SplitUnitModel getUnitModel(final ICompilationUnit sourceUnit, final CompilationUnit sourceAst,
			final SplitRefactoringModel model) throws JavaModelException {
		final Set<IMethod> sourceMethods = model.getUnits().get(sourceUnit);
		final SplitUnitModel unitModel = new SplitUnitModel(sourceUnit, sourceMethods, sourceAst);
		return unitModel;
	}

	/**
	 * @param model
	 * @param sourceMethods
	 * @param unitModel
	 * @throws JavaModelException
	 */
	public void buildModel(final SplitRefactoringModel model) throws JavaModelException {
		for (final IMethod sourceMethod : sourceMethods) {
			SplitUnitModelBuilder.buildSplitUnitModel(sourceMethod, model, this);
		}
	}

}