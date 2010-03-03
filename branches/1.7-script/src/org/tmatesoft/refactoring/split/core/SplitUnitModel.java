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

	private Map<IMethod, MethodDeclaration> addMethods = new HashMap<IMethod, MethodDeclaration>();
	private Set<IType> usedTypes = new HashSet<IType>();
	private Set<IField> usedFields = new HashSet<IField>();
	private Set<IType> nestedTypes = new HashSet<IType>();

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

	public static SplitUnitModel getModel(final String targetSuffix, final Map<ICompilationUnit, Set<IMethod>> units,
			final CompilationUnit ast, final Set<IMethod> sourceMethods) throws JavaModelException {

		final SplitUnitModel splitModel = new SplitUnitModel();
		for (final IMethod sourceMethod : sourceMethods) {
			SplitUnitModelBuilder.buildSplitRefactoringModel(targetSuffix, units, ast, sourceMethod,
					splitModel);
		}
		return splitModel;
	}

}