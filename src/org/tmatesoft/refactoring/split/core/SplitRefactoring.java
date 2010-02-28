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
import org.eclipse.jdt.core.ICompilationUnit;
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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
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
			type.setInterface(sourceType.isInterface());
			type.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
			type.setName(ast.newSimpleName(typeName));
			node.types().add(type);

			final Set<IType> usedTypes = new HashSet<IType>();
			final Map<IMethod, MethodDeclaration> addMethods = new HashMap<IMethod, MethodDeclaration>();
			for (final IMethod sourceMethod : sourceMethods) {
				getAddMethodsAndImportPackages(sourceNode, sourceMethod, addMethods, usedTypes);
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
			for (final MethodDeclaration sourceMethodDeclaration : addMethods.values()) {
				bodyDeclarations.add(ASTNode.copySubtree(ast, sourceMethodDeclaration));
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
			final Map<IMethod, MethodDeclaration> addMethods, final Set<IType> usedTypes) throws JavaModelException {

		if (addMethods.containsKey(sourceMethod))
			return;

		final IType sourceMethodDeclaringType = sourceMethod.getDeclaringType();

		final Set<IMethod> invocedMethods = new HashSet<IMethod>();

		final ASTVisitor visitor = new ASTVisitor() {

			public boolean visit(final MethodInvocation node) {
				addInvocedMethod(node);
				return super.visit(node);
			}

			public boolean visit(SimpleType node) {
				addUsedType(node);
				return super.visit(node);
			}

			public boolean visit(final ArrayType node) {
				addUsedType(node.getComponentType());
				return super.visit(node);
			}

			@Override
			public boolean visit(TypeLiteral node) {
				addUsedType(node.getType());
				return super.visit(node);
			}

			@Override
			public boolean visit(SimpleName node) {
				addUsedType(node);
				return super.visit(node);
			}

			private void addInvocedMethod(final MethodInvocation node) {
				final IMethodBinding binding = node.resolveMethodBinding().getMethodDeclaration();
				final IMethod method = (IMethod) binding.getJavaElement();
				final IType declaringType = method.getDeclaringType();
				if (declaringType != null) {
					if (sourceMethodDeclaringType.equals(declaringType)) {
						if (!invocedMethods.contains(method)) {
							invocedMethods.add(method);
						}
					} else {
						if (!usedTypes.contains(declaringType)) {
							usedTypes.add(declaringType);
						}
					}
				}
			}

			private void addUsedType(final IType type) {
				if (type != null) {
					if (!usedTypes.contains(type)) {
						usedTypes.add(type);
					}
				}
			}

			private void addUsedType(final ITypeBinding binding) {
				final IType type = (IType) binding.getJavaElement();
				if (type != null) {
					addUsedType(type);
				}
			}

			private void addUsedType(final Type node) {
				final ITypeBinding binding = node.resolveBinding().getTypeDeclaration();
				if (binding != null) {
					addUsedType(binding);
				}
			}

			private void addUsedType(SimpleName node) {
				final ITypeBinding binding = node.resolveTypeBinding().getTypeDeclaration();
				if (binding != null) {
					addUsedType(binding);
				}
			}

		};

		final MethodDeclaration sourceMethodNode = (MethodDeclaration) NodeFinder.perform(sourceNode, sourceMethod
				.getSourceRange());

		addMethods.put(sourceMethod, sourceMethodNode);

		sourceMethodNode.accept(visitor);

		for (final IMethod invocedMethod : invocedMethods) {
			getAddMethodsAndImportPackages(sourceNode, invocedMethod, addMethods, usedTypes);
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
