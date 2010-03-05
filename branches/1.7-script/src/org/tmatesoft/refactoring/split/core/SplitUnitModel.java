package org.tmatesoft.refactoring.split.core;

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
	private final Set<IMethod> sourceMethods;
	private final CompilationUnit sourceAst;

	private Map<IMethod, MethodDeclaration> addMethods = new HashMap<IMethod, MethodDeclaration>();
	private Set<IType> usedTypes = new HashSet<IType>();
	private Set<IField> usedFields = new HashSet<IField>();
	private Set<IType> nestedTypes = new HashSet<IType>();

	public SplitUnitModel(final SplitRefactoringModel model, final ICompilationUnit sourceUnit,
			final Set<IMethod> sourceMethods, final CompilationUnit sourceAst) {
		this.model = model;
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
		final SplitUnitModel unitModel = new SplitUnitModel(model, sourceUnit, sourceMethods, sourceAst);
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
			addMethodToUnitModel(sourceMethod);
		}
	}

	public void applyUnitSplit(final SplitRefactoringModel model, final RefactoringStatus status,
			final IProgressMonitor monitor) throws CoreException, MalformedTreeException, BadLocationException {

		final CompilationUnit sourceNode = getSourceAst();
		final Set<IMethod> sourceMethods = model.getUnits().get(sourceUnit);

		final IType sourceType = sourceNode.getTypeRoot().findPrimaryType();
		final String sourceTypeName = sourceType.getElementName();
		final String typeName = model.addTargetSuffix(sourceTypeName);
		final String unitName = typeName + ".java";

		final ICompilationUnit unit = model.getTargetPackage().getCompilationUnit(unitName);
		if (!unit.exists()) {

			final AST ast = AST.newAST(AST.JLS3);
			final CompilationUnit node = ast.newCompilationUnit();

			final PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
			packageDeclaration.setName(ast.newName(model.getTargetPackage().getElementName()));
			node.setPackage(packageDeclaration);

			final TypeDeclaration type = ast.newTypeDeclaration();
			node.types().add(type);

			type.setInterface(sourceType.isInterface());
			final List modifiers = type.modifiers();
			modifiers.add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
			final int sourceFlags = sourceType.getFlags();
			if (Flags.isAbstract(sourceFlags)) {
				modifiers.add(ast.newModifier(Modifier.ModifierKeyword.ABSTRACT_KEYWORD));
			}
			type.setName(ast.newSimpleName(typeName));

			final TypeDeclaration sourceTypeNode = (TypeDeclaration) NodeFinder.perform(sourceNode, sourceType
					.getSourceRange());
			final ITypeBinding sourceTypeBinding = sourceTypeNode.resolveBinding();
			final IMethodBinding[] sourceTypeDeclaredMethods = sourceTypeBinding.getDeclaredMethods();

			if (!type.isInterface()) {
				final Type sourceSuperclassType = sourceTypeNode.getSuperclassType();
				if (sourceSuperclassType != null) {
					final ITypeBinding sourceSuperclassBinding = sourceSuperclassType.resolveBinding();
					final IPackageBinding sourceSuperclassPackageBinding = sourceSuperclassBinding.getPackage();
					final IPackageFragment sourceSuperclassPackage = (IPackageFragment) sourceSuperclassPackageBinding
							.getJavaElement();
					final IType sourceSuperclass = (IType) sourceSuperclassBinding.getJavaElement();
					if (sourceSuperclass != null) {
						final ICompilationUnit sourceSuperclassUnit = sourceSuperclass.getCompilationUnit();
						if (!model.getSourcePackage().equals(sourceSuperclassPackage)
								|| !model.getUnits().containsKey(sourceSuperclassUnit)) {
							getUsedTypes().add(sourceSuperclass);
							type.setSuperclassType((Type) ASTNode.copySubtree(ast, sourceSuperclassType));
						} else {
							final String sourceSuperclassName = sourceSuperclass.getElementName();
							final String targetSuperclassName = model.addTargetSuffix(sourceSuperclassName);
							type.setSuperclassType(ast.newSimpleType(ast.newName(targetSuperclassName)));
						}
					}
				}
			}

			final List<Type> sourceSuperInterfaceTypes = sourceTypeNode.superInterfaceTypes();
			final List<Type> superInterfaceTypes = type.superInterfaceTypes();
			if (sourceSuperInterfaceTypes != null && !sourceSuperInterfaceTypes.isEmpty()) {
				for (final Type sourceSuperInterfaceType : sourceSuperInterfaceTypes) {
					if (sourceSuperInterfaceType != null) {
						final ITypeBinding sourceSuperInterfaceBinding = sourceSuperInterfaceType.resolveBinding();
						final IPackageBinding sourceSuperInterfacePackageBinding = sourceSuperInterfaceBinding
								.getPackage();
						final IPackageFragment sourceSuperInterfacePackage = (IPackageFragment) sourceSuperInterfacePackageBinding
								.getJavaElement();
						final IType sourceSuperInterface = (IType) sourceSuperInterfaceBinding.getJavaElement();
						if (sourceSuperInterface != null) {
							final ICompilationUnit sourceSuperInterfaceUnit = sourceSuperInterface.getCompilationUnit();
							if (!model.getSourcePackage().equals(sourceSuperInterfacePackage)
									|| !model.getUnits().containsKey(sourceSuperInterfaceUnit)) {
								getUsedTypes().add(sourceSuperInterface);
								superInterfaceTypes.add((Type) ASTNode.copySubtree(ast, sourceSuperInterfaceType));
							} else {
								final String sourceSuperInterfaceName = sourceSuperInterface.getElementName();
								final String targetSuperInterfaceName = model.addTargetSuffix(sourceSuperInterfaceName);
								superInterfaceTypes.add(ast.newSimpleType(ast.newName(targetSuperInterfaceName)));
							}

							final IMethodBinding[] sourceSuperInterfaceMethods = sourceSuperInterfaceBinding
									.getDeclaredMethods();
							for (IMethodBinding sourceTypeDeclaredMethod : sourceTypeDeclaredMethods) {
								for (final IMethodBinding sourceSuperInterfaceMethodBinding : sourceSuperInterfaceMethods) {
									if (sourceTypeDeclaredMethod.overrides(sourceSuperInterfaceMethodBinding)) {
										sourceMethods.add((IMethod) sourceTypeDeclaredMethod.getJavaElement());
									}
								}
							}

						}
					}
				}
			}

			final List imports = node.imports();
			for (final IType usedType : getUsedTypes()) {
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
				final FieldDeclaration sourceFieldNode = (FieldDeclaration) NodeFinder.perform(sourceNode, sourceField
						.getSourceRange());
				final FieldDeclaration fieldDeclarationCopy = (FieldDeclaration) ASTNode.copySubtree(ast,
						sourceFieldNode);
				bodyDeclarations.add(fieldDeclarationCopy);
			}

			for (final MethodDeclaration sourceMethodDeclaration : getAddMethods().values()) {

				final MethodDeclaration methodCopy = (MethodDeclaration) ASTNode.copySubtree(ast,
						sourceMethodDeclaration);

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
				final TypeDeclaration sourceNestedTypeNode = (TypeDeclaration) NodeFinder.perform(sourceNode,
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

	public void addMethodToUnitModel(final IMethod sourceMethod) throws JavaModelException {

		final SplitUnitModelBuilder builder = new SplitUnitModelBuilder(model, sourceAst, sourceMethod, this);

		if (builder.getSourceMethodDeclaringClass().isAnonymous()) {
			// TODO anonymous class
		} else if (builder.getSourceMethodParentClass() != null) {
			builder.addNestedType(builder.getSourceMethodDeclaringType());
		} else {
			getAddMethods().put(sourceMethod, builder.getSourceMethodNode());
			final IMethodBinding[] declaredMethods = builder.getSourceMethodDeclaringClass().getDeclaredMethods();
			for (final IMethodBinding methodBinding : declaredMethods) {
				if (methodBinding.isConstructor()) {
					final IMethod constructor = (IMethod) methodBinding.getJavaElement();
					if (constructor != null) {
						final MethodDeclaration constructorNode = (MethodDeclaration) NodeFinder.perform(sourceAst,
								constructor.getSourceRange());
						getAddMethods().put(constructor, constructorNode);
						constructorNode.accept(builder);
					}
				}
			}
		}

		builder.buildUnitModel();

		for (final IMethod invokedMethod : builder.getInvokedMethods()) {
			if (!getAddMethods().containsKey(invokedMethod)) {
				addMethodToUnitModel(invokedMethod);
			}
		}

	}

}