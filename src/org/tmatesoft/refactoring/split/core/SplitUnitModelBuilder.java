/**
 * 
 */
package org.tmatesoft.refactoring.split.core;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
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

	private Map<ICompilationUnit, Set<IMethod>> units;
	private CompilationUnit sourceNode;
	private IMethod sourceMethod;
	private SplitUnitModel splitModel;
	private Set<IMethod> invokedMethods = new HashSet<IMethod>();

	private MethodDeclaration sourceMethodNode;
	private IType sourceMethodDeclaringType;
	private IMethodBinding sourceMethodBinding;
	private ITypeBinding sourceMethodDeclaringClass;
	private ITypeBinding sourceMethodParentClass;
	private String targetSuffix;

	public SplitUnitModelBuilder(final String targetSuffix, final Map<ICompilationUnit, Set<IMethod>> units,
			final CompilationUnit sourceNode, final IMethod sourceMethod, final SplitUnitModel splitModel)
			throws JavaModelException {

		this.targetSuffix = targetSuffix;
		this.units = units;
		this.sourceNode = sourceNode;
		this.sourceMethod = sourceMethod;
		this.splitModel = splitModel;

		sourceMethodNode = (MethodDeclaration) NodeFinder.perform(sourceNode, sourceMethod.getSourceRange());
		sourceMethodDeclaringType = sourceMethod.getDeclaringType();
		sourceMethodBinding = sourceMethodNode.resolveBinding();
		sourceMethodDeclaringClass = sourceMethodBinding.getDeclaringClass();
		sourceMethodParentClass = sourceMethodDeclaringClass.getDeclaringClass();
	}

	/**
	 * @param sourceTypeName
	 * @return
	 */
	private String addSuffix(final String str) {
		if (!str.endsWith(targetSuffix)) {
			return str + targetSuffix;
		} else {
			return str;
		}
	}

	public void visitSourceMethod() {
		sourceMethodNode.accept(this);
	}

	/**
	 * @return the sourceNode
	 */
	public CompilationUnit getSourceNode() {
		return sourceNode;
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
		return splitModel;
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

	@Override
	public boolean visit(QualifiedName node) {
		determineEntity(node);
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
		addUsedType(node.getType().resolveBinding(), node);
		return super.visit(node);
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
								splitModel.getUsedFields().add(field);
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
					addUsedType(declaringClass, node);
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
					if (!units.containsKey(unit)) {
						splitModel.getUsedTypes().add(type);
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

				simpleName.setIdentifier(addSuffix(simpleName.getIdentifier()));
				break;

			case IBinding.VARIABLE:

				final IVariableBinding var = (IVariableBinding) binding;
				final ITypeBinding varType = var.getType();
				if (varType != null) {
					final IVariableBinding varDeclaration = var.getVariableDeclaration();
					final IJavaElement javaElement = varDeclaration.getJavaElement();
					if (varDeclaration.isField()) {
						final IField field = (IField) javaElement;
						try {
							final ASTNode nodeFound = NodeFinder.perform(sourceNode, field.getSourceRange());
							if (nodeFound != null) {
								if (nodeFound instanceof FieldDeclaration) {
									final FieldDeclaration fieldNode = (FieldDeclaration) nodeFound;
									final Type fieldType = fieldNode.getType();
									moveTypeToTarget(fieldType);
								} else {
									// TODO why?
								}
							}
						} catch (JavaModelException e) {
							SplitRefactoring.log(e);
						}
					} else {
						// TODO local var
					}
				}

				break;

			case IBinding.METHOD:
				break;

			}

		} else if (node instanceof ArrayType) {
			final ArrayType arrayType = (ArrayType) node;
			final Type componentType = arrayType.getComponentType();
			if (componentType instanceof SimpleType) {
				if (node instanceof SimpleName) {
					final SimpleName simpleName = (SimpleName) node;
					simpleName.setIdentifier(addSuffix(simpleName.getIdentifier()));
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
			typeSimpleName.setIdentifier(addSuffix(typeSimpleName.getIdentifier()));
		}
	}

	void addNestedType(IType nestedType) {
		if (nestedType != null) {
			if (!splitModel.getNestedTypes().contains(nestedType)) {
				splitModel.getNestedTypes().add(nestedType);
				try {

					final TypeDeclaration typeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceNode, nestedType
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
						buildSplitRefactoringModel(targetSuffix, units, sourceNode, method, splitModel);
					}
				} catch (JavaModelException e) {
					SplitRefactoring.log(e);
				}
			}
		}
	}

	static void buildSplitRefactoringModel(final String targetSuffix, final Map<ICompilationUnit, Set<IMethod>> units,
			final CompilationUnit sourceNode, final IMethod sourceMethod, final SplitUnitModel splitModel)
			throws JavaModelException {

		if (splitModel.getAddMethods().containsKey(sourceMethod))
			return;

		final SplitUnitModelBuilder visitor = new SplitUnitModelBuilder(targetSuffix, units, sourceNode, sourceMethod,
				splitModel);

		if (visitor.getSourceMethodDeclaringClass().isAnonymous()) {
			// TODO anonymous class
		} else if (visitor.getSourceMethodParentClass() != null) {
			visitor.addNestedType(visitor.getSourceMethodDeclaringType());
		} else {
			splitModel.getAddMethods().put(sourceMethod, visitor.getSourceMethodNode());

			final IMethodBinding[] declaredMethods = visitor.getSourceMethodDeclaringClass().getDeclaredMethods();
			for (final IMethodBinding methodBinding : declaredMethods) {
				if (methodBinding.isConstructor()) {
					final IMethod constructor = (IMethod) methodBinding.getJavaElement();
					if (constructor != null) {
						final MethodDeclaration constructorNode = (MethodDeclaration) NodeFinder.perform(sourceNode,
								constructor.getSourceRange());
						splitModel.getAddMethods().put(constructor, constructorNode);
						constructorNode.accept(visitor);
					}
				}
			}

		}

		visitor.visitSourceMethod();

		for (final IMethod invokedMethod : visitor.getInvokedMethods()) {
			buildSplitRefactoringModel(targetSuffix, units, sourceNode, invokedMethod, splitModel);
		}

	}

}