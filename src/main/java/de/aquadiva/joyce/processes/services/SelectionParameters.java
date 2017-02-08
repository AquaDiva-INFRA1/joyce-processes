package de.aquadiva.joyce.processes.services;

import java.util.Arrays;

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
		/**
		 * coverage, overhead, overlap
		 */
		public Integer[] preferences;
		public SelectionType selectionType;
		public ScoreType[] scoreTypesToConsider;
		@Override
		public String toString() {
			return "SelectionParameters [sampleSize=" + sampleSize + ", maxElementsPerSet=" + maxElementsPerSet
					+ ", preferences=" + Arrays.toString(preferences) + ", selectionType=" + selectionType
					+ ", scoreTypesToConsider=" + Arrays.toString(scoreTypesToConsider) + "]";
		}

	}