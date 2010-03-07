package org.tmatesoft.refactoring.split.core;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

public class SplitUtils {

	private static final SearchEngine SEARCH_ENGINE = new SearchEngine();

	private static synchronized SearchEngine getSearchEngine() {
		return SEARCH_ENGINE;
	}

	public static synchronized <T extends IJavaElement> T searchOneElement(final Class<T> type,
			final SearchPattern pattern, final IJavaSearchScope scope, final IProgressMonitor progressMonitor)
			throws CoreException {

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
		getSearchEngine().search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
				scope, requestor, new SubProgressMonitor(progressMonitor, 1));
		return requestor.found;
	}

	public static synchronized <T extends IJavaElement> List<T> searchManyElements(final Class<T> type,
			final SearchPattern pattern, final IJavaSearchScope scope, final IProgressMonitor progressMonitor)
			throws CoreException {

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
		getSearchEngine().search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
				scope, requestor, new SubProgressMonitor(progressMonitor, 1));
		return requestor.found;
	}

	/**
	 * @param sourceTypeName
	 * @return
	 */
	public static String addSuffix(final String str, final String suffix) {
		if (!str.endsWith(suffix)) {
			return str + suffix;
		} else {
			return str;
		}
	}

	static final String ORIGINAL_NAME = "org.tmatesoft.refactoring.split.originalName";

}
