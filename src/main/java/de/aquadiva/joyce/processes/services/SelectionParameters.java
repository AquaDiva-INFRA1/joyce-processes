package de.aquadiva.joyce.processes.services;

import de.aquadiva.joyce.base.data.ScoreType;

public class SelectionParameters {

		public enum SelectionType {
			ONTOLOGY, CLUSTER_MODULE, LOCALITY_MODULE
		}

		public int sampleSize;
		// the maximal number of
		// ontologies/modules per
		// set
		// that shall be computed
		public int maxElementsPerSet;
		public Integer[] preferences;
		public SelectionType selectionType;
		public ScoreType[] scoreTypesToConsider;

	}