package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
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

	private String sourcePackageName = "org.tmatesoft.svn.core.wc";
	private String targetPackageName = "org.tmatesoft.svn.core.internal.wc.v16";
	private String targetSuffix = "16";

	private List<String> typesToHideNames = Arrays.asList(new String[] {
			"org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess",
			"org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea" });

	private IStructuredSelection selection;
	private IProject project;
	private IJavaProject javaProject;
	private IPackageFragmentRoot packageRoot;

	private IPackageFragment sourcePackage;
	private IPackageFragment targetPackage;
	private List<IType> typesToHide;

	private SearchEngine searchEngine = new SearchEngine();
	private IJavaSearchScope projectScope;

	private Map<ICompilationUnit, Set<IMethod>> units = new LinkedHashMap<ICompilationUnit, Set<IMethod>>();

	private List<Change> changes = new LinkedList<Change>();

	private CodeFormatter codeFormatter = ToolFactory.createCodeFormatter(null);

	@Override
	public String getName() {
		return TITLE;
	}

	public void setSelection(IStructuredSelection selection) {
		this.selection = selection;
	}

	/**
	 * @param exception
	 */
	private void log(Exception exception) {
		SplitRefactoringActivator.getDefault().getLog().log(
				new Status(IStatus.ERROR, SplitRefactoringActivator.PLUGIN_ID, 0, exception.getMessage(), exception));
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor progressMonitor) throws CoreException,
			OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			progressMonitor.beginTask("Checking preconditions...", 1);

			if (selection != null) {
				final Object element = selection.getFirstElement();
				if (element != null && element instanceof IProject) {
					project = (IProject) element;
				}
			}

			if (project == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Please select project"));
				return status;
			}

			javaProject = JavaCore.create(project);

			if (javaProject == null || !javaProject.exists()) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Please select Java project"));
				return status;
			}

			if (targetPackageName == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Target package has not been specified."));
				return status;
			}

			projectScope = SearchEngine.createJavaSearchScope(new IJavaElement[] { javaProject },
					IJavaSearchScope.SOURCES);

			if (sourcePackageName == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Source package has not been specified."));
				return status;
			} else {
				sourcePackage = searchPackage(sourcePackageName, projectScope, progressMonitor, status);
			}
			if (typesToHideNames == null || typesToHideNames.isEmpty()) {
				status.merge(RefactoringStatus.createFatalErrorStatus("Types to hide have not been specified."));
				return status;
			} else {
				typesToHide = new ArrayList<IType>();
				for (final String typeName : typesToHideNames) {
					final IType type = searchType(typeName, projectScope, progressMonitor, status);
					if (type != null) {
						typesToHide.add(type);
					}
				}
				if (typesToHide.isEmpty()) {
					status.merge(RefactoringStatus.createFatalErrorStatus("Types to hide have not been found."));
					return status;
				}
			}
		} finally {
			progressMonitor.done();
		}

		return status;
	}

	/**
	 * @param packageName
	 * @param scope
	 * @param progressMonitor
	 * @param status
	 * @throws CoreException
	 */
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

	/**
	 * @param typeName
	 * @param scope
	 * @param progressMonitor
	 * @param status
	 * @throws CoreException
	 */
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

	/**
	 * @param class1
	 * @param pattern
	 * @param scope
	 * @param progressMonitor
	 * @throws CoreException
	 */
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
		searchEngine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope,
				requestor, new SubProgressMonitor(progressMonitor, 1));
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
		searchEngine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope,
				requestor, new SubProgressMonitor(progressMonitor, 1));
		return requestor.found;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor progressMonitor) throws CoreException,
			OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			progressMonitor.beginTask("Checking preconditions...", 1);

			final IJavaElement ancestor = sourcePackage.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (ancestor != null && ancestor instanceof IPackageFragmentRoot) {
				packageRoot = (IPackageFragmentRoot) ancestor;
			}
			if (packageRoot == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus(String.format("Can't find package root for '%s'",
						sourcePackage)));
				return status;
			}

			targetPackage = packageRoot.getPackageFragment(targetPackageName);
			if (!targetPackage.exists()) {
				changes.add(new CreatePackageChange(targetPackage));
			}

			final IJavaSearchScope sourcePackageScope = SearchEngine.createJavaSearchScope(
					new IJavaElement[] { sourcePackage }, IJavaSearchScope.SOURCES);

			for (final IType typeToHide : typesToHide) {
				final SearchPattern referencesPattern = SearchPattern.createPattern(typeToHide,
						IJavaSearchConstants.REFERENCES);
				final List<IMethod> methods = searchManyElements(IMethod.class, referencesPattern, sourcePackageScope,
						progressMonitor);
				if (methods != null && !methods.isEmpty()) {
					for (final IMethod method : methods) {
						final ICompilationUnit unit = method.getCompilationUnit();
						if (!units.containsKey(unit)) {
							units.put(unit, new HashSet<IMethod>());
						}
						final Set<IMethod> set = units.get(unit);
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
						splitCompilationUnit(source, units.get(source), ast, status, subMonitor);
					} catch (Exception exception) {
						log(exception);
					}
				}
			};

			final ASTParser parser = ASTParser.newParser(AST.JLS3);
			parser.setResolveBindings(true);
			parser.setProject(javaProject);
			final Collection<ICompilationUnit> collection = units.keySet();
			final ICompilationUnit[] array = collection.toArray(new ICompilationUnit[collection.size()]);
			parser.createASTs(array, new String[0], requestor, new SubProgressMonitor(progressMonitor, 1));

		} finally {
			progressMonitor.done();
		}

		return status;
	}

	public void splitCompilationUnit(final ICompilationUnit sourceUnit, final Set<IMethod> sourceMethods,
			final CompilationUnit sourceNode, final RefactoringStatus status, final IProgressMonitor monitor)
			throws CoreException, MalformedTreeException, BadLocationException {

		final IType sourceType = sourceNode.getTypeRoot().findPrimaryType();
		final String sourceTypeName = sourceType.getElementName();

		final String typeName = sourceTypeName + targetSuffix;
		final String unitName = typeName + ".java";

		final ICompilationUnit unit = targetPackage.getCompilationUnit(unitName);
		if (!unit.exists()) {

			final AST ast = AST.newAST(AST.JLS3);
			final CompilationUnit node = ast.newCompilationUnit();

			final PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
			packageDeclaration.setName(ast.newName(targetPackage.getElementName()));
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

			final Set<IType> usedTypes = new HashSet<IType>();

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
						if (!sourcePackage.equals(sourceSuperclassPackage) || !units.containsKey(sourceSuperclassUnit)) {
							usedTypes.add(sourceSuperclass);
							type.setSuperclassType((Type) ASTNode.copySubtree(ast, sourceSuperclassType));
						} else {
							final String sourceSuperclassName = sourceSuperclass.getElementName();
							final String targetSuperclassName = sourceSuperclassName + targetSuffix;
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
							if (!sourcePackage.equals(sourceSuperInterfacePackage)
									|| !units.containsKey(sourceSuperInterfaceUnit)) {
								usedTypes.add(sourceSuperInterface);
								superInterfaceTypes.add((Type) ASTNode.copySubtree(ast, sourceSuperInterfaceType));
							} else {
								final String sourceSuperInterfaceName = sourceSuperInterface.getElementName();
								final String targetSuperInterfaceName = sourceSuperInterfaceName + targetSuffix;
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

			final Map<IMethod, MethodDeclaration> addMethods = new HashMap<IMethod, MethodDeclaration>();
			final Set<IField> usedFields = new HashSet<IField>();
			for (final IMethod sourceMethod : sourceMethods) {
				getAddMethodsAndImportPackages(sourceNode, sourceMethod, addMethods, usedTypes, usedFields);
			}

			final List imports = node.imports();
			for (final IType usedType : usedTypes) {
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

			for (final IField sourceField : usedFields) {
				final FieldDeclaration sourceFieldNode = (FieldDeclaration) NodeFinder.perform(sourceNode, sourceField
						.getSourceRange());
				final FieldDeclaration fieldDeclarationCopy = (FieldDeclaration) ASTNode.copySubtree(ast,
						sourceFieldNode);
				bodyDeclarations.add(fieldDeclarationCopy);
			}

			for (final MethodDeclaration sourceMethodDeclaration : addMethods.values()) {

				final MethodDeclaration methodCopy = (MethodDeclaration) ASTNode.copySubtree(ast,
						sourceMethodDeclaration);
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

			final String source = node.toString();
			final Document document = new Document(source);
			final TextEdit formatEdit = codeFormatter.format(CodeFormatter.K_COMPILATION_UNIT, source, 0, source
					.length(), 0, javaProject.findRecommendedLineSeparator());
			formatEdit.apply(document);

			changes.add(new CreateCompilationUnitChange(unit, document.get(), null));

		}
	}

	private void getAddMethodsAndImportPackages(final CompilationUnit sourceNode, final IMethod sourceMethod,
			final Map<IMethod, MethodDeclaration> addMethods, final Set<IType> usedTypes, final Set<IField> usedFields)
			throws JavaModelException {

		if (addMethods.containsKey(sourceMethod))
			return;

		final IType sourceMethodDeclaringType = sourceMethod.getDeclaringType();

		final Set<IMethod> invocedMethods = new HashSet<IMethod>();

		final ASTVisitor visitor = new ASTVisitor() {

			public boolean visit(final ArrayType node) {
				addUsedType(node.getComponentType().resolveBinding());
				return super.visit(node);
			}

			@Override
			public boolean visit(SimpleName node) {
				determineEntity(node);
				return super.visit(node);
			}

			private void determineEntity(SimpleName node) {
				final IBinding binding = node.resolveBinding();
				switch (binding.getKind()) {
				case IBinding.METHOD:
					addInvocedMethod((IMethodBinding) binding);
					break;
				case IBinding.TYPE:
					addUsedType((ITypeBinding) binding);
					break;
				case IBinding.VARIABLE:
					addUsedField((IVariableBinding) binding);
					break;
				}

			}

			private void addUsedField(IVariableBinding binding) {
				if (binding.isField()) {
					final ITypeBinding declaringClass = binding.getDeclaringClass();
					if (declaringClass != null && !declaringClass.isAnonymous()) {
						final IField field = (IField) binding.getJavaElement();
						final IType declaringType = (IType) declaringClass.getJavaElement();
						if (declaringType != null) {
							if (sourceMethodDeclaringType.equals(declaringType)) {
								usedFields.add(field);
							} else {
								usedTypes.add(declaringType);
							}
						}
					}
				}
			}

			private void addInvocedMethod(final IMethodBinding binding) {
				final ITypeBinding declaringClass = binding.getDeclaringClass();
				if (!declaringClass.isAnonymous()) {
					final IMethod method = (IMethod) binding.getJavaElement();
					final IType declaringType = (IType) declaringClass.getJavaElement();
					if (declaringType != null) {
						if (sourceMethodDeclaringType.equals(declaringType)) {
							invocedMethods.add(method);
						} else {
							usedTypes.add(declaringType);
						}
					}
				}
			}

			private void addUsedType(final ITypeBinding binding) {
				if (!binding.isAnonymous()) {
					final IType type = (IType) binding.getJavaElement();
					if (type != null) {
						usedTypes.add(type);
					}
				}
			}

		};

		final MethodDeclaration sourceMethodNode = (MethodDeclaration) NodeFinder.perform(sourceNode, sourceMethod
				.getSourceRange());

		addMethods.put(sourceMethod, sourceMethodNode);

		sourceMethodNode.accept(visitor);

		for (final IMethod invocedMethod : invocedMethods) {
			getAddMethodsAndImportPackages(sourceNode, invocedMethod, addMethods, usedTypes, usedFields);
		}

	}

	@Override
	public Change createChange(IProgressMonitor progressMonitor) throws CoreException, OperationCanceledException {
		try {
			progressMonitor.beginTask("Creating change...", 1);
			final CompositeChange change = new CompositeChange(getName(), changes.toArray(new Change[changes.size()]));
			return change;
		} finally {
			progressMonitor.done();
		}

	}

}
