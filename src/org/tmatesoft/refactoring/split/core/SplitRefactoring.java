package org.tmatesoft.refactoring.split.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.tmatesoft.refactoring.split.SplitRefactoringActivator;

public class SplitRefactoring extends Refactoring {

	public static final String TITLE = "Split refactoring";

	private String sourcePackageName = "org.tmatesoft.svn.core.wc";
	private String targetPackageName = "org.tmatesoft.svn.core.internal.wc.v16";
	private List<String> typesToHideNames = Arrays.asList(new String[] {
			"org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess",
			"org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea" });

	private IStructuredSelection selection;
	private IProject project;
	private IJavaProject javaProject;

	private IPackageFragment sourcePackage;
	private IPackageFragment targetPackage;
	private List<IType> typesToHide;

	private SearchEngine searchEngine = new SearchEngine();
	private IJavaSearchScope projectScope;

	private Map<ICompilationUnit, List<IMethod>> units = new LinkedHashMap<ICompilationUnit, List<IMethod>>();

	private Map<String, Change> changes = new LinkedHashMap<String, Change>();

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

			/*
			 * final IJavaElement packageRoot =
			 * sourcePackage.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT); if
			 * (packageRoot != null && packageRoot instanceof
			 * IPackageFragmentRoot) { final IPackageFragmentRoot root =
			 * (IPackageFragmentRoot) packageRoot; targetPackage =
			 * root.createPackageFragment(targetPackageName, true,
			 * progressMonitor); } if (targetPackage == null) {
			 * status.merge(RefactoringStatus
			 * .createFatalErrorStatus(String.format
			 * ("Can't create package '%s'", targetPackageName))); return
			 * status; }
			 */

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
							units.put(unit, new LinkedList<IMethod>());
						}
						units.get(unit).add(method);
					}
				}
			}

			final ASTRequestor requestor = new ASTRequestor() {
				@Override
				public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
					try {
						rewriteCompilationUnit(this, source, units.get(source), ast, status);
					} catch (CoreException exception) {
						log(exception);
					}
				}
			};

			final ASTParser parser = ASTParser.newParser(AST.JLS3);
			parser.setProject(javaProject);
			parser.setResolveBindings(true);
			final Collection<ICompilationUnit> collection = units.keySet();
			parser.createASTs(collection.toArray(new ICompilationUnit[collection.size()]), new String[0], requestor,
					new SubProgressMonitor(progressMonitor, 1));

		} finally {
			progressMonitor.done();
		}

		return status;
	}

	public void rewriteCompilationUnit(final ASTRequestor requestor, final ICompilationUnit source,
			final List<IMethod> methods, final CompilationUnit node, final RefactoringStatus status)
			throws CoreException {

		final ASTRewrite oldAstRewrite = ASTRewrite.create(node.getAST());

		// final ImportRewrite oldImportRewrite = ImportRewrite.create(node,
		// true);

		final AST newAst = AST.newAST(AST.JLS3);
		final CompilationUnit newUnit = newAst.newCompilationUnit();
		final PackageDeclaration newPackage = newAst.newPackageDeclaration();
		newPackage.setName(newAst.newName(targetPackageName));
		newUnit.setPackage(newPackage);
		final TypeDeclaration newType = newAst.newTypeDeclaration();
		newType.setInterface(false);
		newType.modifiers().add(newAst.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		final String typeName = node.getTypeRoot().findPrimaryType().getElementName();
		newType.setName(newAst.newSimpleName(typeName));
		final ASTRewrite newAstRewrite = ASTRewrite.create(newAst);
		final ListRewrite newTypeBodyRewrite = newAstRewrite.getListRewrite(newType, newType
				.getBodyDeclarationsProperty());
		newUnit.types().add(newType);

		// final ImportRewrite newImportRewrite = ImportRewrite.create(newIUnit,
		// true);

		for (final IMethod method : methods) {
			final ASTNode methodNode = NodeFinder.perform(node, method.getSourceRange());
			if (methodNode instanceof MethodDeclaration) {
				final ASTNode moveMethod = oldAstRewrite.createMoveTarget(methodNode);
				newTypeBodyRewrite.insertLast(moveMethod, null);

				// TODO implement delegation
				oldAstRewrite.remove(methodNode, null);
			}
		}

		rewriteAST(source, oldAstRewrite);

		rewriteAST(targetPackageName + "." + typeName, newAstRewrite);

	}

	private void rewriteAST(String elementName, ASTRewrite astRewrite) {
		try {
			MultiTextEdit edit = new MultiTextEdit();
			TextEdit astEdit = astRewrite.rewriteAST();

			if (!isEmptyEdit(astEdit))
				edit.addChild(astEdit);
			// TextEdit importEdit = importRewrite.rewriteImports(new
			// NullProgressMonitor());
			// if (!isEmptyEdit(importEdit))
			// edit.addChild(importEdit);
			if (isEmptyEdit(edit))
				return;

			Change change = changes.get(elementName);
			if (change == null) {
				change = new DocumentChange(elementName, new Document());
				changes.put(elementName, change);
			}
			if (change instanceof DocumentChange) {
				((DocumentChange) change).addEdit(astEdit);
			}
		} catch (MalformedTreeException exception) {
			log(exception);
		} catch (IllegalArgumentException exception) {
			log(exception);
		} catch (CoreException exception) {
			log(exception);
		}
	}

	private void rewriteAST(ICompilationUnit unit, ASTRewrite astRewrite) {
		try {
			MultiTextEdit edit = new MultiTextEdit();
			TextEdit astEdit = astRewrite.rewriteAST();

			if (!isEmptyEdit(astEdit))
				edit.addChild(astEdit);
			// TextEdit importEdit = importRewrite.rewriteImports(new
			// NullProgressMonitor());
			// if (!isEmptyEdit(importEdit))
			// edit.addChild(importEdit);
			if (isEmptyEdit(edit))
				return;

			final String elementName = unit.getElementName();
			Change change = changes.get(elementName);
			if (change == null) {
				change = new TextFileChange(elementName, (IFile) unit.getResource());
				changes.put(elementName, change);
				((TextFileChange) change).setTextType("java");
				((TextFileChange) change).setEdit(edit);
			}
			if (change instanceof TextFileChange) {
				((TextFileChange) change).getEdit().addChild(edit);
			}
		} catch (MalformedTreeException exception) {
			log(exception);
		} catch (IllegalArgumentException exception) {
			log(exception);
		} catch (CoreException exception) {
			log(exception);
		}
	}

	private boolean isEmptyEdit(TextEdit edit) {
		return edit.getClass() == MultiTextEdit.class && !edit.hasChildren();
	}

	@Override
	public Change createChange(IProgressMonitor progressMonitor) throws CoreException, OperationCanceledException {
		try {
			progressMonitor.beginTask("Creating change...", 1);
			final CompositeChange change = new CompositeChange(getName(), changes.values().toArray(
					new Change[changes.size()]));
			return change;
		} finally {
			progressMonitor.done();
		}

	}

}
