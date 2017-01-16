package de.aquadiva.ontologyselection.processes.services;

import java.io.IOException;

public interface ISetupService {
	/**
	 * Downloads ontologies from BioPortal, does the modularization, constant
	 * scoring, concept dictionary adaption with respect to the modularization
	 * and ontology database population.
	 * 
	 * @throws IOException
	 *             If the writing of the class Id to module Id mapping file was
	 *             not successful.
	 */
	void setupSelectionSystem() throws IOException;
}
