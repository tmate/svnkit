package org.tmatesoft.refactoring.split.core;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.TextEdit;

public class SplitDelegateChanges implements ISplitChanges {

	@Override
	public boolean doChanges(final SplitRefactoringModel model, final RefactoringStatus status,
			final SubProgressMonitor subMonitor) throws JavaModelException {

		final List<Change> changes = model.getChanges();

		for (final Entry<ICompilationUnit, SplitUnitModel> unitModelEntry : model.getUnitModels().entrySet()) {

			final ICompilationUnit unit = unitModelEntry.getKey();
			final SplitUnitModel unitModel = unitModelEntry.getValue();

			final String source = unit.getSource();
			final Document document = new Document(source);

			final CompilationUnit sourceAst = unitModel.getSourceAst();
			final ASTRewrite rewrite = ASTRewrite.create(sourceAst.getAST());

			final Map<IMethod, MethodDeclaration> methods = unitModel.getAddMethods();
			for (final Entry<IMethod, MethodDeclaration> methodsEntry : methods.entrySet()) {
				final IMethod method = methodsEntry.getKey();
				final MethodDeclaration methodDeclaration = methodsEntry.getValue();
				if (Flags.isPublic(methodDeclaration.getFlags())) {
					doDelegation(unitModel, rewrite, methodDeclaration, status, subMonitor);
				} else {
					rewrite.remove(methodDeclaration, null);
				}
			}

			final TextEdit edit = rewrite.rewriteAST(document, unit.getJavaProject().getOptions(true));
			final TextFileChange textFileChange = new TextFileChange(unit.getElementName(), (IFile) unit.getResource());
			textFileChange.setEdit(edit);
			changes.add(textFileChange);

		}

		return true;
	}

	private void doDelegation(SplitUnitModel unitModel, ASTRewrite rewrite, MethodDeclaration methodDeclaration,
			RefactoringStatus status, SubProgressMonitor subMonitor) {

	}

}
