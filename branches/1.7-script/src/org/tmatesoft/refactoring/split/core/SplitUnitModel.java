package org.tmatesoft.refactoring.split.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class SplitUnitModel {

	private final SplitRefactoringModel model;

	private final ICompilationUnit sourceUnit;
	private final CompilationUnit sourceAst;
	private final Set<IMethod> sourceMethods;

	private Map<IMethod, MethodDeclaration> addMethods = new HashMap<IMethod, MethodDeclaration>();
	private Set<IType> usedTypes = new HashSet<IType>();
	private Set<IField> usedFields = new HashSet<IField>();
	private Set<IType> nestedTypes = new HashSet<IType>();

	private IType sourceType;
	private String sourceTypeName;

	private boolean isSourceInterface;
	private boolean isSourceAbstractClass;

	private TypeDeclaration sourceTypeNode;
	private ITypeBinding sourceTypeBinding;
	private IMethodBinding[] sourceTypeDeclaredMethods;

	public Type sourceSuperClassNode;
	private TypeMetadata sourceSuperClassMetadata;

	public List<Type> sourceSuperInterfacesNodes;
	private Map<Type, TypeMetadata> sourceSuperInterfacesMetadata = new HashMap<Type, TypeMetadata>();

	public class TypeMetadata {

		private Type typeNode;
		private IType type;
		private ITypeBinding typeBinding;
		private IPackageBinding packageBinding;
		private IPackageFragment packageFragment;
		private ICompilationUnit unit;
		private String name;
		private TypeDeclaration typeDeclaration;
		private IMethodBinding[] declaredMethods;

		public Type getTypeNode() {
			return typeNode;
		}

		public IType getType() {
			return type;
		}

		public ITypeBinding getTypeBinding() {
			return typeBinding;
		}

		public IPackageBinding getPackageBinding() {
			return packageBinding;
		}

		public IPackageFragment getPackageFragment() {
			return packageFragment;
		}

		public ICompilationUnit getUnit() {
			return unit;
		}

		public String getName() {
			return name;
		}

		public TypeDeclaration getTypeDeclaration() {
			return typeDeclaration;
		}

		public IMethodBinding[] getDeclaredMethods() {
			return declaredMethods;
		}

		public TypeMetadata(final Type typeNode) {
			this.typeNode = typeNode;
			if (typeNode != null) {
				typeBinding = typeNode.resolveBinding();
				if (typeBinding != null) {
					type = (IType) typeBinding.getJavaElement();
					if (type != null) {
						packageBinding = typeBinding.getPackage();
						packageFragment = (IPackageFragment) packageBinding.getJavaElement();
						unit = type.getCompilationUnit();
						name = type.getElementName();
						declaredMethods = typeBinding.getDeclaredMethods();
					}
				}
			}

		}

	}

	public SplitUnitModel(final SplitRefactoringModel model, final ICompilationUnit sourceUnit,
			final Set<IMethod> sourceMethods, final CompilationUnit sourceAst) throws JavaModelException {
		this.model = model;
		this.sourceUnit = sourceUnit;
		this.sourceMethods = sourceMethods;
		this.sourceAst = sourceAst;
		resolveMetadata();
	}

	public SplitRefactoringModel getModel() {
		return model;
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

	public IType getSourceType() {
		return sourceType;
	}

	public String getSourceTypeName() {
		return sourceTypeName;
	}

	public boolean isSourceInterface() {
		return isSourceInterface;
	}

	public boolean isSourceAbstractClass() {
		return isSourceAbstractClass;
	}

	public TypeDeclaration getSourceTypeNode() {
		return sourceTypeNode;
	}

	public ITypeBinding getSourceTypeBinding() {
		return sourceTypeBinding;
	}

	public IMethodBinding[] getSourceTypeDeclaredMethods() {
		return sourceTypeDeclaredMethods;
	}

	public Type getSourceSuperClassNode() {
		return sourceSuperClassNode;
	}

	public TypeMetadata getSourceSuperClassMetadata() {
		return sourceSuperClassMetadata;
	}

	public List<Type> getSourceSuperInterfacesNodes() {
		return sourceSuperInterfacesNodes;
	}

	public Map<Type, TypeMetadata> getSourceSuperInterfacesMetadata() {
		return sourceSuperInterfacesMetadata;
	}

	public static SplitUnitModel getUnitModel(final ICompilationUnit sourceUnit, final CompilationUnit sourceAst,
			final SplitRefactoringModel model) throws JavaModelException {
		final Set<IMethod> sourceMethods = model.getUnits().get(sourceUnit);
		final SplitUnitModel unitModel = new SplitUnitModel(model, sourceUnit, sourceMethods, sourceAst);
		return unitModel;
	}

	private void resolveMetadata() throws JavaModelException {

		sourceType = sourceAst.getTypeRoot().findPrimaryType();
		sourceTypeName = sourceType.getElementName();
		isSourceInterface = sourceType.isInterface();
		if (!isSourceInterface) {
			isSourceAbstractClass = Flags.isAbstract(sourceType.getFlags());
		}

		sourceTypeNode = (TypeDeclaration) NodeFinder.perform(sourceAst, sourceType.getSourceRange());
		sourceTypeBinding = sourceTypeNode.resolveBinding();
		sourceTypeDeclaredMethods = sourceTypeBinding.getDeclaredMethods();

		if (!isSourceInterface) {
			sourceSuperClassNode = sourceTypeNode.getSuperclassType();
			if (sourceSuperClassNode != null) {
				sourceSuperClassMetadata = new TypeMetadata(sourceSuperClassNode);
			}
		}

		sourceSuperInterfacesNodes = sourceTypeNode.superInterfaceTypes();
		if (sourceSuperInterfacesNodes != null && !sourceSuperInterfacesNodes.isEmpty()) {
			for (final Type sourceSuperInterfaceType : sourceSuperInterfacesNodes) {
				if (sourceSuperInterfaceType != null) {

					final TypeMetadata sourceSuperInterfaceMetadata = new TypeMetadata(sourceSuperInterfaceType);
					sourceSuperInterfacesMetadata.put(sourceSuperInterfaceType, sourceSuperInterfaceMetadata);

				}
			}
		}

	}

	public void buildModel(final SplitRefactoringModel model) throws JavaModelException {
		addConstructors();
		addSuperMethods();
		for (final IMethod sourceMethod : sourceMethods) {
			try {
				addMethodToUnitModel(sourceMethod);
			} catch (Exception exception) {
				SplitRefactoring.log(exception);
			}
		}
	}

	private void addConstructors() {
		for (final IMethodBinding methodBinding : sourceTypeDeclaredMethods) {
			if (methodBinding.isConstructor()) {
				final IMethod method = (IMethod) methodBinding.getJavaElement();
				if (method != null && method.exists()) {
					sourceMethods.add(method);
				}
			}
		}
	}

	private void addSuperMethods() {

		if (sourceSuperClassMetadata != null) {
			if (sourceSuperClassMetadata.getDeclaredMethods() != null) {
				for (final IMethodBinding superMethodBinding : sourceSuperClassMetadata.getDeclaredMethods()) {
					for (final IMethodBinding methodBinding : sourceTypeDeclaredMethods) {
						if (methodBinding.overrides(superMethodBinding)) {
							final IMethod method = (IMethod) methodBinding.getJavaElement();
							if (method != null && method.exists()) {
								sourceMethods.add(method);
							}
						}
					}
				}
			}
		}

		if (!sourceSuperInterfacesMetadata.isEmpty()) {
			for (final TypeMetadata interfaceMetadata : sourceSuperInterfacesMetadata.values()) {
				if (interfaceMetadata.getDeclaredMethods() != null) {
					for (final IMethodBinding superMethodBinding : interfaceMetadata.getDeclaredMethods()) {
						for (final IMethodBinding methodBinding : sourceTypeDeclaredMethods) {
							if (methodBinding.overrides(superMethodBinding)) {
								final IMethod method = (IMethod) methodBinding.getJavaElement();
								if (method != null && method.exists()) {
									sourceMethods.add(method);
								}
							}
						}
					}
				}
			}
		}

	}

	public void addMethodToUnitModel(final IMethod sourceMethod) throws JavaModelException {
		final SplitUnitAddMethodBuilder builder = new SplitUnitAddMethodBuilder(this, sourceMethod);
		builder.addMethodToUnit();
	}

}