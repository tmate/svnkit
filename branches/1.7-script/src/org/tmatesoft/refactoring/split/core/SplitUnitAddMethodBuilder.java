package org.tmatesoft.refactoring.split.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;

class SplitUnitAddMethodBuilder extends ASTVisitor {

	private final SplitRefactoringModel model;
	private final SplitUnitModel unitModel;
	private final CompilationUnit sourceAst;
	private final IMethod sourceMethod;
	private final Set<IMethod> invokedMethods = new HashSet<IMethod>();

	private final MethodDeclaration sourceMethodNode;
	private final IType sourceMethodDeclaringType;
	private final IMethodBinding sourceMethodBinding;
	private final ITypeBinding sourceMethodDeclaringClass;
	private final ITypeBinding sourceMethodParentClass;

	@Override
	public boolean visit(SimpleName node) {
		determineEntity(node);
		return super.visit(node);
	}

	public boolean visit(final ArrayType node) {
		addUsedType(node.getComponentType().resolveBinding(), node);
		return super.visit(node);
	}

	public SplitUnitAddMethodBuilder(final SplitUnitModel splitModel, final IMethod sourceMethod)
			throws JavaModelException {

		this.unitModel = splitModel;
		this.model = splitModel.getModel();
		this.sourceAst = splitModel.getSourceAst();
		this.sourceMethod = sourceMethod;

		sourceMethodNode = (MethodDeclaration) NodeFinder.perform(sourceAst, sourceMethod.getSourceRange());
		sourceMethodDeclaringType = sourceMethod.getDeclaringType();
		sourceMethodBinding = sourceMethodNode.resolveBinding();
		sourceMethodDeclaringClass = sourceMethodBinding.getDeclaringClass();
		sourceMethodParentClass = sourceMethodDeclaringClass.getDeclaringClass();
	}

	public void addMethodToUnit() throws JavaModelException {

		if (getSourceMethodDeclaringClass().isAnonymous()) {
			// TODO anonymous class
		} else if (getSourceMethodParentClass() != null) {
			addNestedType(getSourceMethodDeclaringType());
		} else {
			unitModel.getAddMethods().put(sourceMethod, getSourceMethodNode());
			final IMethodBinding[] declaredMethods = getSourceMethodDeclaringClass().getDeclaredMethods();
			for (final IMethodBinding methodBinding : declaredMethods) {
				if (methodBinding.isConstructor()) {
					final IMethod constructor = (IMethod) methodBinding.getJavaElement();
					if (constructor != null) {
						final MethodDeclaration constructorNode = (MethodDeclaration) NodeFinder.perform(sourceAst,
								constructor.getSourceRange());
						unitModel.getAddMethods().put(constructor, constructorNode);
						constructorNode.accept(this);
					}
				}
			}
		}

		sourceMethodNode.accept(this);

		for (final IMethod invokedMethod : getInvokedMethods()) {
			if (!unitModel.getAddMethods().containsKey(invokedMethod)) {
				unitModel.addMethodToUnitModel(invokedMethod);
			}
		}

	}

	/**
	 * @return the sourceNode
	 */
	public CompilationUnit getSourceAst() {
		return sourceAst;
	}

	/**
	 * @return the sourceMethod
	 */
	public IMethod getSourceMethod() {
		return sourceMethod;
	}

	/**
	 * @return the splitModel
	 */
	public SplitUnitModel getSplitModel() {
		return unitModel;
	}

	/**
	 * @return the sourceMethodNode
	 */
	public MethodDeclaration getSourceMethodNode() {
		return sourceMethodNode;
	}

	/**
	 * @return the sourceMethodDeclaringType
	 */
	public IType getSourceMethodDeclaringType() {
		return sourceMethodDeclaringType;
	}

	/**
	 * @return the sourceMethodBinding
	 */
	public IMethodBinding getSourceMethodBinding() {
		return sourceMethodBinding;
	}

	/**
	 * @return the sourceMethodDeclaringClass
	 */
	public ITypeBinding getSourceMethodDeclaringClass() {
		return sourceMethodDeclaringClass;
	}

	/**
	 * @return the sourceMethodParentClass
	 */
	public ITypeBinding getSourceMethodParentClass() {
		return sourceMethodParentClass;
	}

	public Set<IMethod> getInvokedMethods() {
		return invokedMethods;
	}

	private void determineEntity(Name node) {
		final IBinding binding = node.resolveBinding();
		switch (binding.getKind()) {
		case IBinding.METHOD:
			addInvokedMethod((IMethodBinding) binding, node);
			break;
		case IBinding.TYPE:
			addUsedType((ITypeBinding) binding, node);
			break;
		case IBinding.VARIABLE:
			addUsedField((IVariableBinding) binding, node);
			break;
		}

	}

	private void addUsedField(IVariableBinding binding, final ASTNode node) {
		if (binding.isField()) {
			final ITypeBinding declaringClass = binding.getDeclaringClass();
			if (declaringClass != null) {
				if (!declaringClass.isAnonymous()) {
					final IField field = (IField) binding.getJavaElement();
					final IType declaringType = (IType) declaringClass.getJavaElement();
					if (declaringType != null) {
						if (sourceMethodDeclaringType.equals(declaringType)) {
							final ITypeBinding parentClass = declaringClass.getDeclaringClass();
							if (parentClass == null) {
								unitModel.getUsedFields().add(field);
								final ITypeBinding type = binding.getType();
								if (type != null) {
									if (!type.isArray()) {
										addUsedType(type, node);
									} else {
										addUsedType(type.getComponentType(), node);
									}
								}
							} else {
								addNestedType(declaringType);
							}
						} else {
							addUsedType(declaringClass, node);
						}
					}
				} else {
					// TODO anonymous class
				}
			}
		}
	}

	private void addInvokedMethod(final IMethodBinding binding, final ASTNode node) {
		final ITypeBinding declaringClass = binding.getDeclaringClass();
		if (!declaringClass.isAnonymous()) {
			final IMethod method = (IMethod) binding.getJavaElement();
			final IType declaringType = (IType) declaringClass.getJavaElement();
			if (declaringType != null) {
				if (sourceMethodDeclaringType.equals(declaringType)) {
					final ITypeBinding parentClass = declaringClass.getDeclaringClass();
					if (parentClass == null) {
						invokedMethods.add(method);
					} else {
						addNestedType(declaringType);
					}
				} else {
					final IPackageFragment packageFragment = declaringType.getPackageFragment();
					final ICompilationUnit compilationUnit = declaringType.getCompilationUnit();
					if (model.getSourcePackage().equals(packageFragment)
							&& model.getUnits().containsKey(compilationUnit)) {
						try {
							final SplitUnitModel splitUnitModel = model.getUnitModels().get(compilationUnit);
							splitUnitModel.addMethodToUnitModel(method);
						} catch (Exception e) {
							SplitRefactoring.log(e);
						}
					} else {
						addUsedType(declaringClass, node);
					}
				}
			}
		} else {
			// TODO anonymous class
		}
	}

	private void addUsedType(final ITypeBinding binding, final ASTNode node) {
		if (!binding.isAnonymous()) {
			final IType type = (IType) binding.getJavaElement();
			if (type != null) {
				final ITypeBinding parentClass = binding.getDeclaringClass();
				if (parentClass == null) {
					final ICompilationUnit unit = type.getCompilationUnit();
					if (!model.getUnits().containsKey(unit)) {
						unitModel.getUsedTypes().add(type);
					}
				} else if (sourceMethodDeclaringClass.equals(parentClass)) {
					addNestedType(type);
				}
			}
		} else {
			// TODO anonymous class
		}
	}

	void addNestedType(IType nestedType) {
		if (nestedType != null) {
			final Set<IType> nestedTypes = unitModel.getNestedTypes();
			if (!nestedTypes.contains(nestedType)) {
				nestedTypes.add(nestedType);
				try {

					final TypeDeclaration typeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceAst, nestedType
							.getSourceRange());
					if (!typeDeclaration.isInterface()) {
						final Type superclassType = typeDeclaration.getSuperclassType();
						if (superclassType != null) {
							addUsedType(superclassType.resolveBinding(), typeDeclaration);
						}
					}

					final List<Type> superInterfaceTypes = typeDeclaration.superInterfaceTypes();
					if (superInterfaceTypes != null) {
						for (final Type superInterface : superInterfaceTypes) {
							addUsedType(superInterface.resolveBinding(), typeDeclaration);
						}
					}

					for (final IMethod method : nestedType.getMethods()) {
						unitModel.addMethodToUnitModel(method);
					}

				} catch (JavaModelException e) {
					SplitRefactoring.log(e);
				}
			}
		}
	}

}