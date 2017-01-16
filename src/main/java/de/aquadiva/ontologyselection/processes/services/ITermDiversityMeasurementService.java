package de.aquadiva.ontologyselection.processes.services;

import java.util.Set;

/**
 * Measures the diversity of terms. We define a term's diversity as the number
 * of ontology modules it appears in. I.e. no disambiguation is done, we just
 * count all appearances of the term.
 * 
 * @author faessler
 * 
 */
public interface ITermDiversityMeasurementService {
	/**
	 * Measures the diversity of a single term. Please note that the diversity
	 * of a node set is in general <em>not</em> the sum of the individual
	 * diversity scores.
	 * 
	 * @param term
	 * @return
	 */
	int getDiversity(String term);

	/**
	 * Measures the diversity of a whole term set. First, all modules containing
	 * any term in the set is collected, then the number of unique module IDs is
	 * returned.
	 * 
	 * @param terms
	 * @return
	 */
	int getDiversity(String[] terms);

	/**
	 * Returns the set of unique module IDs containing <tt>term</tt> in one of
	 * their classes.
	 * 
	 * @param term
	 * @return
	 */
	Set<String> getModulesForTerm(String term);
}
