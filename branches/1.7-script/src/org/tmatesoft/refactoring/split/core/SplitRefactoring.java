package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
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
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.tmatesoft.refactoring.split.SplitRefactoringActivator;

public class SplitRefactoring extends Refactoring {

	public static final String TITLE = "Split refactoring";

	private SplitRefactoringModel model = new SplitRefactoringModel("org.tmatesoft.svn.core.wc",
			"org.tmatesoft.svn.core.internal.wc.v16", "16", Arrays.asList(new String[] {
					"org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess",
					"org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea" }));

	@Override
	public String getName() {
		return TITLE;
	}

	public void setSelection(IStructuredSelection selection) {
		this.model.setSelection(selection);
	}

	/**
	 * @param exception
	 */
	static public void log(Exception exception) {
		SplitRefactoringActivator.getDefault().getLog().log(
				new Status(IStatus.ERROR, SplitRefactoringActivator.PLUGIN_ID, 0, exception.getMessage(), exception));
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor progressMonitor) throws CoreException,
			OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			progressMonitor.beginTask("Checking preconditions...", 1);

			if (model.getSelection() != null) {
				final Object element = model.getSelection().getFirstElement();
				if (element != null && element instanceof IProject) {
					model.setProject((IProject) element);
				}
			}

			if (model.getProject() == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Please select project"));
				return status;
			}

			model.setJavaProject(JavaCore.create(model.getProject()));

			if (model.getJavaProject() == null || !model.getJavaProject().exists()) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Please select Java project"));
				return status;
			}

			if (model.getTargetPackageName() == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Target package has not been specified."));
				return status;
			}

			model.setProjectScope(SearchEngine.createJavaSearchScope(new IJavaElement[] { model.getJavaProject() },
					IJavaSearchScope.SOURCES));

			if (model.getSourcePackageName() == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Source package has not been specified."));
				return status;
			} else {
				model.setSourcePackage(searchPackage(model.getSourcePackageName(), model.getProjectScope(),
						progressMonitor, status));
			}
			if (model.getTypesToHideNames() == null || model.getTypesToHideNames().isEmpty()) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Types to hide have not been specified."));
				return status;
			} else {
				model.setTypesToHide(new ArrayList<IType>());
				for (final String typeName : model.getTypesToHideNames()) {
					final IType type = searchType(typeName, model.getProjectScope(), progressMonitor, status);
					if (type != null) {
						model.getTypesToHide().add(type);
					}
				}
				if (model.getTypesToHide().isEmpty()) {
					status.merge(RefactoringStatus.createFatalErrorStatus("Types to hide have not been found."));
					return status;
				}
			}
		} finally {
			progressMonitor.done();
		}

		return status;
	}

	private IPackageFragment searchPackage(String packageName, final IJavaSearchScope scope,
			IProgressMonitor progressMonitor, final RefactoringStatus status) throws CoreException {
		final SearchPattern pattern = SearchPattern.createPattern(packageName, IJavaSearchConstants.PACKAGE,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
		IPackageFragment found = searchOneElement(IPackageFragment.class, pattern, scope, progressMonitor);
		if (found == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
					"Package '%s' has not been found in selected project", packageName)));
		}
		return found;
	}

	private IType searchType(String typeName, final IJavaSearchScope scope, IProgressMonitor progressMonitor,
			final RefactoringStatus status) throws CoreException {
		final SearchPattern pattern = SearchPattern.createPattern(typeName, IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
		final IType found = searchOneElement(IType.class, pattern, scope, progressMonitor);
		if (found == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
					"Type '%s' has not been found in selected project", typeName)));
		}
		return found;
	}

	private <T extends IJavaElement> T searchOneElement(final Class<T> type, final SearchPattern pattern,
			final IJavaSearchScope scope, final IProgressMonitor progressMonitor) throws CoreException {

		class SearchRequestorImpl extends SearchRequestor {
			T found;

			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				if (match.getAccuracy() == SearchMatch.A_ACCURATE && !match.isInsideDocComment()) {
					final Object element = match.getElement();
					if (element != null && type.isAssignableFrom(element.getClass())) {
						found = type.cast(element);
					}
				}
			}
		}

		final SearchRequestorImpl requestor = new SearchRequestorImpl();
		model.getSearchEngine().search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
				scope, requestor, new SubProgressMonitor(progressMonitor, 1));
		return requestor.found;
	}

	private <T extends IJavaElement> List<T> searchManyElements(final Class<T> type, final SearchPattern pattern,
			final IJavaSearchScope scope, final IProgressMonitor progressMonitor) throws CoreException {

		class SearchRequestorImpl extends SearchRequestor {
			List<T> found = new LinkedList<T>();

			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				if (match.getAccuracy() == SearchMatch.A_ACCURATE && !match.isInsideDocComment()) {
					final Object element = match.getElement();
					if (element != null && type.isAssignableFrom(element.getClass())) {
						found.add(type.cast(element));
					}
				}
			}
		}

		final SearchRequestorImpl requestor = new SearchRequestorImpl();
		model.getSearchEngine().search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
				scope, requestor, new SubProgressMonitor(progressMonitor, 1));
		return requestor.found;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor progressMonitor) throws CoreException,
			OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			progressMonitor.beginTask("Checking preconditions...", 1);

			final IJavaElement ancestor = model.getSourcePackage().getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (ancestor != null && ancestor instanceof IPackageFragmentRoot) {
				model.setPackageRoot((IPackageFragmentRoot) ancestor);
			}
			if (model.getPackageRoot() == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus(String.format("Can't find package root for '%s'",
						model.getSourcePackage())));
				return status;
			}

			model.setTargetPackage(model.getPackageRoot().getPackageFragment(model.getTargetPackageName()));
			if (!model.getTargetPackage().exists()) {
				model.getChanges().add(new CreatePackageChange(model.getTargetPackage()));
			}

			final IJavaSearchScope sourcePackageScope = SearchEngine.createJavaSearchScope(new IJavaElement[] { model
					.getSourcePackage() }, IJavaSearchScope.SOURCES);

			for (final IType typeToHide : model.getTypesToHide()) {
				final SearchPattern referencesPattern = SearchPattern.createPattern(typeToHide,
						IJavaSearchConstants.REFERENCES);
				final List<IMethod> methods = searchManyElements(IMethod.class, referencesPattern, sourcePackageScope,
						progressMonitor);
				if (methods != null && !methods.isEmpty()) {
					for (final IMethod method : methods) {
						final ICompilationUnit unit = method.getCompilationUnit();
						if (!model.getUnits().containsKey(unit)) {
							model.getUnits().put(unit, new HashSet<IMethod>());
						}
						final Set<IMethod> set = model.getUnits().get(unit);
						if (!set.contains(method)) {
							set.add(method);
						}
					}
				}
			}

			final SubProgressMonitor subMonitor = new SubProgressMonitor(progressMonitor, 1);
			final ASTRequestor requestor = new ASTRequestor() {
				@Override
				public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
					try {
						final SplitUnitModel unitModel = SplitUnitModel.getUnitModel(source, ast, model);
						model.getUnitModels().put(source, unitModel);
					} catch (Exception exception) {
						log(exception);
					}
				}
			};

			final ASTParser parser = ASTParser.newParser(AST.JLS3);
			parser.setResolveBindings(true);
			parser.setProject(model.getJavaProject());
			final Collection<ICompilationUnit> collection = model.getUnits().keySet();
			final ICompilationUnit[] array = collection.toArray(new ICompilationUnit[collection.size()]);
			parser.createASTs(array, new String[0], requestor, new SubProgressMonitor(progressMonitor, 1));

			for (final SplitUnitModel unitModel : model.getUnitModels().values()) {
				try {
					unitModel.buildModel(model);
				} catch (Exception exception) {
					log(exception);
				}
			}

			for (final Map.Entry<ICompilationUnit, SplitUnitModel> entry : model.getUnitModels().entrySet()) {
				try {
					applyUnitSplit(entry.getKey(), entry.getValue(), status, subMonitor);
				} catch (Exception exception) {
					log(exception);
				}
			}

		} finally {
			progressMonitor.done();
		}

		return status;
	}

	private void applyUnitSplit(final ICompilationUnit sourceUnit, final SplitUnitModel unitModel,
			final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException,
			MalformedTreeException, BadLocationException {

		final CompilationUnit sourceNode = unitModel.getSourceAst();
		final Set<IMethod> sourceMethods = model.getUnits().get(sourceUnit);

		final IType sourceType = sourceNode.getTypeRoot().findPrimaryType();
		final String sourceTypeName = sourceType.getElementName();
		final String typeName = addSuffix(sourceTypeName);
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
							unitModel.getUsedTypes().add(sourceSuperclass);
							type.setSuperclassType((Type) ASTNode.copySubtree(ast, sourceSuperclassType));
						} else {
							final String sourceSuperclassName = sourceSuperclass.getElementName();
							final String targetSuperclassName = addSuffix(sourceSuperclassName);
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
								unitModel.getUsedTypes().add(sourceSuperInterface);
								superInterfaceTypes.add((Type) ASTNode.copySubtree(ast, sourceSuperInterfaceType));
							} else {
								final String sourceSuperInterfaceName = sourceSuperInterface.getElementName();
								final String targetSuperInterfaceName = addSuffix(sourceSuperInterfaceName);
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
			for (final IType usedType : unitModel.getUsedTypes()) {
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

			for (final IField sourceField : unitModel.getUsedFields()) {
				final FieldDeclaration sourceFieldNode = (FieldDeclaration) NodeFinder.perform(sourceNode, sourceField
						.getSourceRange());
				final FieldDeclaration fieldDeclarationCopy = (FieldDeclaration) ASTNode.copySubtree(ast,
						sourceFieldNode);
				bodyDeclarations.add(fieldDeclarationCopy);
			}

			for (final MethodDeclaration sourceMethodDeclaration : unitModel.getAddMethods().values()) {

				final MethodDeclaration methodCopy = (MethodDeclaration) ASTNode.copySubtree(ast,
						sourceMethodDeclaration);

				if (sourceMethodDeclaration.isConstructor()) {
					methodCopy.setName(ast.newSimpleName(addSuffix(sourceTypeName)));
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

			for (final IType sourceNestedType : unitModel.getNestedTypes()) {
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

	/**
	 * @param sourceTypeName
	 * @return
	 */
	private String addSuffix(final String str) {
		if (!str.endsWith(model.getTargetSuffix())) {
			return str + model.getTargetSuffix();
		} else {
			return str;
		}
	}

	@Override
	public Change createChange(IProgressMonitor progressMonitor) throws CoreException, OperationCanceledException {
		try {
			progressMonitor.beginTask("Creating change...", 1);
			final CompositeChange change = new CompositeChange(getName(), model.getChanges().toArray(
					new Change[model.getChanges().size()]));
			return change;
		} finally {
			progressMonitor.done();
		}

	}

}
