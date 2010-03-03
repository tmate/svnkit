package org.tmatesoft.refactoring.split.core;

import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.MethodDeclaration;

public class SplitRefactoringModel {
	private Map<IMethod, MethodDeclaration> addMethods;
	private Set<IType> usedTypes;
	private Set<IField> usedFields;
	private Set<IType> nestedTypes;

	public SplitRefactoringModel(Map<IMethod, MethodDeclaration> addMethods, Set<IType> usedTypes,
			Set<IField> usedFields, Set<IType> nestedTypes) {
		this.addMethods = addMethods;
		this.usedTypes = usedTypes;
		this.usedFields = usedFields;
		this.nestedTypes = nestedTypes;
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
}