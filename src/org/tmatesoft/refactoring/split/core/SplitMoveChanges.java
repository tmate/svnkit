package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.tmatesoft.refactoring.split.core.SplitUnitModel.TypeMetadata;

public class SplitMoveChanges extends SplitTargetChanges {

	public SplitMoveChanges(final String targetPackageName, final String targetSuffix) {
		super(targetPackageName, targetSuffix);
	}

	@Override
	protected void doUnitChange(SplitUnitModel unitModel, RefactoringStatus status, SubProgressMonitor monitor)
			throws MalformedTreeException, CoreException, BadLocationException {

		final SplitUnitMoveTypeBuilder builder = new SplitUnitMoveTypeBuilder(unitModel, getTargetSuffix());
		builder.moveTypes();

		applyUnitSplit(unitModel, status, monitor);

		builder.setRestore(true);
		builder.moveTypes();

	}

	public void applyUnitSplit(final SplitUnitModel unitModel, final RefactoringStatus status,
			final IProgressMonitor monitor) throws CoreException, MalformedTreeException, BadLocationException {

		final SplitRefactoringModel model = unitModel.getModel();

		final String typeName = addTargetSuffix(unitModel.getSourceTypeName());
		final String unitName = typeName + ".java";
		final ICompilationUnit targetUnit = getTargetPackage().getCompilationUnit(unitName);
		if (!targetUnit.exists()) {
			buildTargetUnit(model, unitModel, typeName, targetUnit);
		}
	}

	protected void buildTargetUnit(final SplitRefactoringModel model, final SplitUnitModel unitModel,
			final String typeName, final ICompilationUnit unit) throws JavaModelException, BadLocationException {

		final AST ast = AST.newAST(AST.JLS3);
		final CompilationUnit node = ast.newCompilationUnit();

		final PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
		packageDeclaration.setName(ast.newName(getTargetPackage().getElementName()));
		node.setPackage(packageDeclaration);

		final TypeDeclaration type = ast.newTypeDeclaration();
		node.types().add(type);

		type.setInterface(unitModel.isSourceInterface());
		final List modifiers = type.modifiers();
		modifiers.add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		if (unitModel.isSourceAbstractClass()) {
			modifiers.add(ast.newModifier(Modifier.ModifierKeyword.ABSTRACT_KEYWORD));
		}
		type.setName(ast.newSimpleName(typeName));

		if (unitModel.getSourceSuperClassMetadata() != null) {
			final IType sourceSuperClassType = unitModel.getSourceSuperClassMetadata().getType();
			if (sourceSuperClassType != null) {
				final Type sourceSuperClassNode = unitModel.getSourceSuperClassMetadata().getTypeNode();
				final ICompilationUnit sourceSuperClassUnit = unitModel.getSourceSuperClassMetadata().getUnit();
				if (model.getUnits().containsKey(sourceSuperClassUnit)) {
					final String sourceSuperClassName = unitModel.getSourceSuperClassMetadata().getName();
					final String targetSuperclassName = addTargetSuffix(sourceSuperClassName);
					type.setSuperclassType(ast.newSimpleType(ast.newName(targetSuperclassName)));
				} else {
					unitModel.getUsedTypes().add(sourceSuperClassType);
					type.setSuperclassType((Type) ASTNode.copySubtree(ast, sourceSuperClassNode));
				}
			}
		}

		final List<Type> superInterfaceTypes = type.superInterfaceTypes();
		if (!unitModel.getSourceSuperInterfacesMetadata().isEmpty()) {
			for (final TypeMetadata sourceSuperInterface : unitModel.getSourceSuperInterfacesMetadata().values()) {
				final IType sourceSuperInterfaceType = sourceSuperInterface.getType();
				if (sourceSuperInterfaceType != null) {
					final Type sourceSuperInterfaceNode = sourceSuperInterface.getTypeNode();
					final ICompilationUnit sourceSuperInterfaceUnit = sourceSuperInterface.getUnit();
					if (model.getUnits().containsKey(sourceSuperInterfaceUnit)) {
						final String sourceSuperInterfaceName = sourceSuperInterface.getName();
						final String targetSuperInterfaceName = addTargetSuffix(sourceSuperInterfaceName);
						superInterfaceTypes.add(ast.newSimpleType(ast.newName(targetSuperInterfaceName)));
					} else {
						unitModel.getUsedTypes().add(sourceSuperInterfaceType);
						superInterfaceTypes.add((Type) ASTNode.copySubtree(ast, sourceSuperInterfaceNode));
					}
				}
			}
		}

		addImports(unitModel, ast, node);

		final List bodyDeclarations = type.bodyDeclarations();

		for (final IField sourceField : unitModel.getUsedFields()) {
			addField(unitModel, ast, bodyDeclarations, sourceField);
		}

		if (!unitModel.isSourceInterface()) {
			addDelegateMethod(unitModel, ast, bodyDeclarations);
		}

		for (final MethodDeclaration sourceMethodDeclaration : unitModel.getAddMethods().values()) {
			addMethod(unitModel, ast, bodyDeclarations, sourceMethodDeclaration);
		}

		addNestedTypes(unitModel, ast, bodyDeclarations);

		final String source = node.toString();
		final Document document = new Document(source);
		final TextEdit formatEdit = model.getCodeFormatter().format(CodeFormatter.K_COMPILATION_UNIT, source, 0,
				source.length(), 0, model.getJavaProject().findRecommendedLineSeparator());
		formatEdit.apply(document);

		model.getChanges().add(new CreateCompilationUnitChange(unit, document.get(), null));
	}

	private void addDelegateMethod(SplitUnitModel unitModel, AST ast, List bodyDeclarations) {
		final MethodDeclaration methodDecl = ast.newMethodDeclaration();
		bodyDeclarations.add(methodDecl);
		final List<IExtendedModifier> modifiers = methodDecl.modifiers();
		modifiers.add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		modifiers.add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));

		final String sourceTypeName = unitModel.getSourceTypeName();

		methodDecl.setReturnType2(ast.newSimpleType(ast.newSimpleName(addTargetSuffix(sourceTypeName))));

		methodDecl.setName(ast.newSimpleName("delegate"));
		final SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setType(ast.newSimpleType(ast.newSimpleName(sourceTypeName)));
		param.setName(ast.newSimpleName("dispatcher"));
		methodDecl.parameters().add(param);

		final Block body = ast.newBlock();
		methodDecl.setBody(body);

		final ReturnStatement returnStatement = ast.newReturnStatement();
		body.statements().add(returnStatement);
		// TODO
		returnStatement.setExpression(ast.newNullLiteral());

	}

	protected void addNestedTypes(final SplitUnitModel unitModel, final AST ast, final List bodyDeclarations)
			throws JavaModelException {
		for (final IType sourceNestedType : unitModel.getNestedTypes()) {
			addNestedType(unitModel, ast, bodyDeclarations, sourceNestedType);
		}
	}

	protected void addNestedType(final SplitUnitModel unitModel, final AST ast, final List bodyDeclarations,
			final IType sourceNestedType) throws JavaModelException {
		final TypeDeclaration sourceNestedTypeNode = (TypeDeclaration) NodeFinder.perform(unitModel.getSourceAst(),
				sourceNestedType.getSourceRange());
		final TypeDeclaration sourceNestedTypeCopy = (TypeDeclaration) ASTNode.copySubtree(ast, sourceNestedTypeNode);
		bodyDeclarations.add(sourceNestedTypeCopy);
	}

	protected void addImports(final SplitUnitModel unitModel, final AST ast, final CompilationUnit node) {
		final List<ImportDeclaration> imports = node.imports();
		final List<IType> usedTypesList = new ArrayList<IType>(unitModel.getUsedTypes());
		usedTypesList.add(unitModel.getSourceType());
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
	}

	protected void addField(final SplitUnitModel unitModel, final AST ast, final List bodyDeclarations,
			final IField sourceField) throws JavaModelException {
		final FieldDeclaration sourceFieldNode = (FieldDeclaration) NodeFinder.perform(unitModel.getSourceAst(),
				sourceField.getSourceRange());
		final FieldDeclaration fieldDeclarationCopy = getFieldCopy(ast, sourceFieldNode);
		bodyDeclarations.add(fieldDeclarationCopy);
	}

	protected FieldDeclaration getFieldCopy(final AST ast, final FieldDeclaration sourceFieldNode) {
		final FieldDeclaration fieldDeclarationCopy = (FieldDeclaration) ASTNode.copySubtree(ast, sourceFieldNode);
		return fieldDeclarationCopy;
	}

	protected void addMethod(final SplitUnitModel unitModel, final AST ast, final List bodyDeclarations,
			final MethodDeclaration sourceMethodDeclaration) {
		final MethodDeclaration methodCopy = getMethodCopy(ast, sourceMethodDeclaration);

		if (sourceMethodDeclaration.isConstructor()) {
			methodCopy.setName(ast.newSimpleName(addTargetSuffix(unitModel.getSourceTypeName())));
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

	protected MethodDeclaration getMethodCopy(final AST ast, final MethodDeclaration sourceMethodDeclaration) {
		final MethodDeclaration methodCopy = (MethodDeclaration) ASTNode.copySubtree(ast, sourceMethodDeclaration);
		return methodCopy;
	}

}
