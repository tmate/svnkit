package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

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
		moveTypes();
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

	private void moveTypes() throws JavaModelException {
		final SplitUnitMoveTypeBuilder builder = new SplitUnitMoveTypeBuilder(this);
		builder.moveTypes();
	}

	public void addMethodToUnitModel(final IMethod sourceMethod) throws JavaModelException {
		final SplitUnitAddMethodBuilder builder = new SplitUnitAddMethodBuilder(this, sourceMethod);
		builder.addMethodToUnit();
	}

	public void applyUnitSplit(final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException,
			MalformedTreeException, BadLocationException {
		final String typeName = model.addTargetSuffix(sourceTypeName);
		final String unitName = typeName + ".java";
		final ICompilationUnit targetUnit = model.getTargetPackage().getCompilationUnit(unitName);
		if (!targetUnit.exists()) {
			buildTargetUnit(typeName, targetUnit);
		}
	}

	private void buildTargetUnit(final String typeName, final ICompilationUnit unit) throws JavaModelException,
			BadLocationException {

		final AST ast = AST.newAST(AST.JLS3);
		final CompilationUnit node = ast.newCompilationUnit();

		final PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
		packageDeclaration.setName(ast.newName(model.getTargetPackage().getElementName()));
		node.setPackage(packageDeclaration);

		final TypeDeclaration type = ast.newTypeDeclaration();
		node.types().add(type);

		type.setInterface(isSourceInterface);
		final List modifiers = type.modifiers();
		modifiers.add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		if (isSourceAbstractClass) {
			modifiers.add(ast.newModifier(Modifier.ModifierKeyword.ABSTRACT_KEYWORD));
		}
		type.setName(ast.newSimpleName(typeName));

		if (sourceSuperClassMetadata != null) {
			final IType sourceSuperClassType = sourceSuperClassMetadata.getType();
			if (sourceSuperClassType != null) {
				final Type sourceSuperClassNode = sourceSuperClassMetadata.getTypeNode();
				final ICompilationUnit sourceSuperClassUnit = sourceSuperClassMetadata.getUnit();
				if (model.getUnits().containsKey(sourceSuperClassUnit)) {
					final String sourceSuperClassName = sourceSuperClassMetadata.getName();
					final String targetSuperclassName = model.addTargetSuffix(sourceSuperClassName);
					type.setSuperclassType(ast.newSimpleType(ast.newName(targetSuperclassName)));
				} else {
					getUsedTypes().add(sourceSuperClassType);
					type.setSuperclassType((Type) ASTNode.copySubtree(ast, sourceSuperClassNode));
				}
			}
		}

		final List<Type> superInterfaceTypes = type.superInterfaceTypes();
		if (!sourceSuperInterfacesMetadata.isEmpty()) {
			for (final TypeMetadata sourceSuperInterface : sourceSuperInterfacesMetadata.values()) {
				final IType sourceSuperInterfaceType = sourceSuperInterface.getType();
				if (sourceSuperInterfaceType != null) {
					final Type sourceSuperInterfaceNode = sourceSuperInterface.getTypeNode();
					final ICompilationUnit sourceSuperInterfaceUnit = sourceSuperInterface.getUnit();
					if (model.getUnits().containsKey(sourceSuperInterfaceUnit)) {
						final String sourceSuperInterfaceName = sourceSuperInterface.getName();
						final String targetSuperInterfaceName = model.addTargetSuffix(sourceSuperInterfaceName);
						superInterfaceTypes.add(ast.newSimpleType(ast.newName(targetSuperInterfaceName)));
					} else {
						getUsedTypes().add(sourceSuperInterfaceType);
						superInterfaceTypes.add((Type) ASTNode.copySubtree(ast, sourceSuperInterfaceNode));
					}
				}
			}
		}

		final List imports = node.imports();
		final List<IType> usedTypesList = new ArrayList<IType>(getUsedTypes());
		Collections.sort(usedTypesList, new Comparator<IType>() {
			@Override
			public int compare(IType t1, IType t2) {
				final IPackageFragment p1 = t1.getPackageFragment();
				final IPackageFragment p2 = t2.getPackageFragment();
				final int p = p1.getElementName().compareTo(p2.getElementName());
				if (p != 0) {
					return p;
				}
				return t1.getElementName().compareTo(t2.getElementName());
			}
		});
		for (final IType usedType : usedTypesList) {
			final IPackageFragment usedPackage = usedType.getPackageFragment();
			if (!"java.lang".equals(usedPackage.getElementName())) {
				final ImportDeclaration importDeclaration = ast.newImportDeclaration();
				importDeclaration.setOnDemand(false);
				importDeclaration.setName(ast.newQualifiedName(ast.newName(usedPackage.getElementName()), ast
						.newSimpleName(usedType.getElementName())));
				imports.add(importDeclaration);
			}
		}

		final List bodyDeclarations = type.bodyDeclarations();

		for (final IField sourceField : getUsedFields()) {
			final FieldDeclaration sourceFieldNode = (FieldDeclaration) NodeFinder.perform(sourceAst, sourceField
					.getSourceRange());
			final FieldDeclaration fieldDeclarationCopy = (FieldDeclaration) ASTNode.copySubtree(ast, sourceFieldNode);
			bodyDeclarations.add(fieldDeclarationCopy);
		}

		for (final MethodDeclaration sourceMethodDeclaration : getAddMethods().values()) {

			final MethodDeclaration methodCopy = (MethodDeclaration) ASTNode.copySubtree(ast, sourceMethodDeclaration);

			if (sourceMethodDeclaration.isConstructor()) {
				methodCopy.setName(ast.newSimpleName(model.addTargetSuffix(sourceTypeName)));
			}

			final IMethodBinding sourceMethodBinding = sourceMethodDeclaration.resolveBinding();
			final String from = sourceMethodBinding.getDeclaringClass().getQualifiedName();

			Javadoc javadoc = methodCopy.getJavadoc();
			if (javadoc == null) {
				javadoc = ast.newJavadoc();
				methodCopy.setJavadoc(javadoc);
			}
			final TagElement tag = ast.newTagElement();
			tag.setTagName("@from ");
			final TextElement text = ast.newTextElement();
			text.setText(from);
			tag.fragments().add(text);
			javadoc.tags().add(tag);

			bodyDeclarations.add(methodCopy);
		}

		for (final IType sourceNestedType : getNestedTypes()) {
			final TypeDeclaration sourceNestedTypeNode = (TypeDeclaration) NodeFinder.perform(sourceAst,
					sourceNestedType.getSourceRange());
			final TypeDeclaration sourceNestedTypeCopy = (TypeDeclaration) ASTNode.copySubtree(ast,
					sourceNestedTypeNode);
			bodyDeclarations.add(sourceNestedTypeCopy);
		}

		final String source = node.toString();
		final Document document = new Document(source);
		final TextEdit formatEdit = model.getCodeFormatter().format(CodeFormatter.K_COMPILATION_UNIT, source, 0,
				source.length(), 0, model.getJavaProject().findRecommendedLineSeparator());
		formatEdit.apply(document);

		model.getChanges().add(new CreateCompilationUnitChange(unit, document.get(), null));
	}

}