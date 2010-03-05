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

class SplitUnitModelBuilder extends ASTVisitor {

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
	public boolean visit(QualifiedName node) {
		// determineEntity(node);
		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleName node) {
		determineEntity(node);
		return super.visit(node);
	}

	public boolean visit(final ArrayType node) {
		addUsedType(node.getComponentType().resolveBinding(), node);
		return super.visit(node);
	}

	@Override
	public boolean visit(TypeLiteral node) {
		// addUsedType(node.getType().resolveBinding(), node);
		return super.visit(node);
	}

	public SplitUnitModelBuilder(final SplitRefactoringModel model, final CompilationUnit sourceAst,
			final IMethod sourceMethod, final SplitUnitModel splitModel) throws JavaModelException {

		this.model = model;
		this.sourceAst = sourceAst;
		this.sourceMethod = sourceMethod;
		this.unitModel = splitModel;

		sourceMethodNode = (MethodDeclaration) NodeFinder.perform(sourceAst, sourceMethod.getSourceRange());
		sourceMethodDeclaringType = sourceMethod.getDeclaringType();
		sourceMethodBinding = sourceMethodNode.resolveBinding();
		sourceMethodDeclaringClass = sourceMethodBinding.getDeclaringClass();
		sourceMethodParentClass = sourceMethodDeclaringClass.getDeclaringClass();
	}

	public void buildUnitModel() {
		sourceMethodNode.accept(this);
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
							buildSplitUnitModel(method, model, splitUnitModel);
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
					} else {
						moveEntityType(node);
					}
				} else if (sourceMethodDeclaringClass.equals(parentClass)) {
					addNestedType(type);
				}
			}
		} else {
			// TODO anonymous class
		}
	}

	protected void moveEntityType(final ASTNode node) {

		if (node instanceof SimpleName) {
			final SimpleName simpleName = (SimpleName) node;

			final IBinding binding = simpleName.resolveBinding();
			final int kind = binding.getKind();

			switch (kind) {

			case IBinding.TYPE:

				simpleName.setIdentifier(model.addTargetSuffix(simpleName.getIdentifier()));
				break;

			case IBinding.VARIABLE:

				final IVariableBinding var = (IVariableBinding) binding;
				final ITypeBinding varType = var.getType();
				if (varType != null) {
					final IVariableBinding varDeclaration = var.getVariableDeclaration();
					final IJavaElement javaElement = varDeclaration.getJavaElement();
					if (varDeclaration.isField()) {
						final IField field = (IField) javaElement;
						final ICompilationUnit unit = field.getCompilationUnit();
						final SplitUnitModel splitUnitModel = model.getUnitModels().get(unit);
						if (splitUnitModel != null) {
							try {
								final ASTNode nodeFound = NodeFinder.perform(splitUnitModel.getSourceAst(), field
										.getSourceRange());
								if (nodeFound != null) {
									final FieldDeclaration fieldNode = (FieldDeclaration) nodeFound;
									final Type fieldType = fieldNode.getType();
									moveTypeToTarget(fieldType);
								}
							} catch (JavaModelException e) {
								SplitRefactoring.log(e);
							}
						}
					} else {
						// TODO local var
					}
				}

				break;

			case IBinding.METHOD:
				// TODO method
				break;

			}

		} else if (node instanceof ArrayType) {
			final ArrayType arrayType = (ArrayType) node;
			final Type componentType = arrayType.getComponentType();
			if (componentType instanceof SimpleType) {
				if (node instanceof SimpleName) {
					final SimpleName simpleName = (SimpleName) node;
					simpleName.setIdentifier(model.addTargetSuffix(simpleName.getIdentifier()));
				}
			}
		}

	}

	/**
	 * @param type
	 */
	private void moveTypeToTarget(final Type type) {
		if (!type.isArrayType()) {
			addTargetSuffixToType(type);
		} else {
			final ArrayType fieldArrayType = (ArrayType) type;
			final Type componentType = fieldArrayType.getComponentType();
			if (componentType.isSimpleType() && componentType instanceof SimpleType) {
				addTargetSuffixToType(componentType);
			}
		}
	}

	/**
	 * @param type
	 */
	private void addTargetSuffixToType(final Type type) {
		final SimpleType simpleType = (SimpleType) type;
		final Name name = simpleType.getName();
		if (name.isSimpleName() && name instanceof SimpleName) {
			final SimpleName typeSimpleName = (SimpleName) name;
			typeSimpleName.setIdentifier(model.addTargetSuffix(typeSimpleName.getIdentifier()));
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
						buildSplitUnitModel(method, model, unitModel);
					}

				} catch (JavaModelException e) {
					SplitRefactoring.log(e);
				}
			}
		}
	}

	static void buildSplitUnitModel(final IMethod sourceMethod, final SplitRefactoringModel model,
			final SplitUnitModel unitModel) throws JavaModelException {

		final CompilationUnit sourceAst = unitModel.getSourceAst();

		final SplitUnitModelBuilder builder = new SplitUnitModelBuilder(model, sourceAst, sourceMethod, unitModel);

		if (builder.getSourceMethodDeclaringClass().isAnonymous()) {
			// TODO anonymous class
		} else if (builder.getSourceMethodParentClass() != null) {
			builder.addNestedType(builder.getSourceMethodDeclaringType());
		} else {
			unitModel.getAddMethods().put(sourceMethod, builder.getSourceMethodNode());
			final IMethodBinding[] declaredMethods = builder.getSourceMethodDeclaringClass().getDeclaredMethods();
			for (final IMethodBinding methodBinding : declaredMethods) {
				if (methodBinding.isConstructor()) {
					final IMethod constructor = (IMethod) methodBinding.getJavaElement();
					if (constructor != null) {
						final MethodDeclaration constructorNode = (MethodDeclaration) NodeFinder.perform(sourceAst,
								constructor.getSourceRange());
						unitModel.getAddMethods().put(constructor, constructorNode);
						constructorNode.accept(builder);
					}
				}
			}
		}

		builder.buildUnitModel();

		for (final IMethod invokedMethod : builder.getInvokedMethods()) {
			if (!unitModel.getAddMethods().containsKey(invokedMethod)) {
				buildSplitUnitModel(invokedMethod, model, unitModel);
			}
		}

	}

}