package de.aquadiva.joyce.processes.services;

import java.util.List;

import de.aquadiva.joyce.base.data.OntologySet;

public interface IOntologyModuleSelectionService {
	/**
	 * Returns scored sets based on the input string and the given preferences.
	 * 
	 * @param text
	 * @return
	 */
	List<OntologySet> selectForText(String text, SelectionParameters params);
}
