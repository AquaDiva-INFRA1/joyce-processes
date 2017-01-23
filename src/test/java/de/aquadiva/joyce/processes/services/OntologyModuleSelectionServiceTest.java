package de.aquadiva.joyce.processes.services;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import de.aquadiva.joyce.JoyceSymbolConstants;
import de.aquadiva.joyce.base.data.IOntology;
import de.aquadiva.joyce.base.data.OntologySet;
import de.aquadiva.joyce.processes.services.IOntologyModuleSelectionService;
import de.aquadiva.joyce.processes.services.JoyceProcessesModule;
import de.aquadiva.joyce.processes.services.OntologyModuleSelectionService;

public class OntologyModuleSelectionServiceTest {
	String resultString;

	@Before
	public void setUp() throws Exception {
		System.setProperty(JoyceSymbolConstants.PERSISTENCE_CONTEXT, "de.aquadiva.test.jpa.ontologyselection");
		// Set the test configuration for the gazetteer so we may work with a
		// small test dictionary
		System.setProperty(JoyceSymbolConstants.GAZETTEER_CONFIG, "bioportal.gazetteer.test.processes.properties");
		resultString = "";
		resultString += "null";
		resultString += "[0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 ][12.0 12.0 12.0 12.0 12.0 12.0 12.0 12.0 12.0 12.0 ][24.0 24.0 24.0 24.0 24.0 24.0 24.0 24.0 24.0 24.0 ][36.0 36.0 36.0 36.0 36.0 36.0 36.0 36.0 36.0 36.0 ][48.0 48.0 48.0 48.0 48.0 48.0 48.0 48.0 48.0 48.0 ]";
		resultString += "[0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 1.0 ][12.0 12.0 12.0 12.0 12.0 12.0 12.0 12.0 13.0 13.0 ][24.0 24.0 24.0 24.0 24.0 24.0 24.0 24.0 25.0 25.0 ][36.0 36.0 36.0 36.0 36.0 36.0 36.0 36.0 37.0 37.0 ][48.0 48.0 48.0 48.0 48.0 48.0 48.0 48.0 49.0 49.0 ]";
		resultString += "[0.0 0.0 0.0 0.0 0.0 0.0 1.0 1.0 2.0 2.0 ][12.0 12.0 12.0 12.0 12.0 12.0 13.0 13.0 14.0 14.0 ][24.0 24.0 24.0 24.0 24.0 24.0 25.0 25.0 26.0 26.0 ][36.0 36.0 36.0 36.0 36.0 36.0 37.0 37.0 38.0 38.0 ][48.0 48.0 48.0 48.0 48.0 48.0 49.0 49.0 50.0 50.0 ]";
		resultString += "[0.0 0.0 0.0 0.0 1.0 1.0 2.0 2.0 3.0 3.0 ][12.0 12.0 12.0 12.0 13.0 13.0 14.0 14.0 15.0 15.0 ][24.0 24.0 24.0 24.0 25.0 25.0 26.0 26.0 27.0 27.0 ][36.0 36.0 36.0 36.0 37.0 37.0 38.0 38.0 39.0 39.0 ][48.0 48.0 48.0 48.0 49.0 49.0 50.0 50.0 51.0 51.0 ]";
		resultString += "[0.0 0.0 1.0 1.0 2.0 2.0 3.0 3.0 4.0 4.0 ][12.0 12.0 13.0 13.0 14.0 14.0 15.0 15.0 16.0 16.0 ][24.0 24.0 25.0 25.0 26.0 26.0 27.0 27.0 28.0 28.0 ][36.0 36.0 37.0 37.0 38.0 38.0 39.0 39.0 40.0 40.0 ][48.0 48.0 49.0 49.0 50.0 50.0 51.0 51.0 52.0 52.0 ]";
		resultString += "[0.0 0.0 1.0 1.0 2.0 2.0 3.0 3.0 4.0 5.0 ][12.0 12.0 13.0 13.0 14.0 14.0 15.0 15.0 16.0 17.0 ][24.0 24.0 25.0 25.0 26.0 26.0 27.0 27.0 28.0 29.0 ][36.0 36.0 37.0 37.0 38.0 38.0 39.0 39.0 40.0 41.0 ][48.0 48.0 49.0 49.0 50.0 50.0 51.0 51.0 52.0 53.0 ]";
		resultString += "[0.0 0.0 1.0 1.0 2.0 3.0 4.0 4.0 5.0 6.0 ][12.0 12.0 13.0 13.0 14.0 15.0 16.0 16.0 17.0 18.0 ][24.0 24.0 25.0 25.0 26.0 27.0 28.0 28.0 29.0 30.0 ][36.0 36.0 37.0 37.0 38.0 39.0 40.0 40.0 41.0 42.0 ][48.0 48.0 49.0 49.0 50.0 51.0 52.0 52.0 53.0 54.0 ]";
		resultString += "[0.0 0.0 1.0 2.0 3.0 3.0 4.0 5.0 6.0 7.0 ][12.0 12.0 13.0 14.0 15.0 15.0 16.0 17.0 18.0 19.0 ][24.0 24.0 25.0 26.0 27.0 27.0 28.0 29.0 30.0 31.0 ][36.0 36.0 37.0 38.0 39.0 39.0 40.0 41.0 42.0 43.0 ][48.0 48.0 49.0 50.0 51.0 51.0 52.0 53.0 54.0 55.0 ]";
		resultString += "[0.0 0.0 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 ][12.0 12.0 13.0 14.0 15.0 16.0 17.0 18.0 19.0 20.0 ][24.0 24.0 25.0 26.0 27.0 28.0 29.0 30.0 31.0 32.0 ][36.0 36.0 37.0 38.0 39.0 40.0 41.0 42.0 43.0 44.0 ][48.0 48.0 49.0 50.0 51.0 52.0 53.0 54.0 55.0 56.0 ]";
		resultString += "[0.0 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0 ][12.0 13.0 14.0 15.0 16.0 17.0 18.0 19.0 20.0 21.0 ][24.0 25.0 26.0 27.0 28.0 29.0 30.0 31.0 32.0 33.0 ][36.0 37.0 38.0 39.0 40.0 41.0 42.0 43.0 44.0 45.0 ][48.0 49.0 50.0 51.0 52.0 53.0 54.0 55.0 56.0 57.0 ]";
	}

	@After
	public void tearDown() throws Exception {
	}

	// @Test
	// public void testGetSubspacesWithValidInput() {
	//
	// String resultAsString = "";
	//
	// // test for different numbers of candidate elements: 0, 1, ..., 10
	// for( int k=0; k<11; k++) {
	//
	// ScoreType[] scoreTypes = new ScoreType[5]; // 5 score types (= 5
	// dimensions)
	// ArrayList<double[]> scoresByType = new ArrayList<double[]>(); // the
	// scores by type
	// ArrayList<OntologySet> candidates = new ArrayList<OntologySet>(); // the
	// candidate elements
	//
	// // generate scores
	// // iterate over score types
	// for( int j=0; j<5; j++ ) {
	// double[] scoresOfTypeJ= new double[k];
	// for( int i=0; i<k; i++ ) {
	// scoresOfTypeJ[i] = 12.0 * (double) j + (double) i;
	// }
	// scoresByType.add(j, scoresOfTypeJ);
	// }
	//
	// // generate candidate elements
	// for( int i=0; i<k; i++ ) {
	// candidates.add( new OntologySet() );
	// }
	//
	// // get subspaces
	// ArrayList<double[]> subspaces =
	// OntologyModuleSelectionService.getSubspaceBorders( candidates,
	// scoreTypes, scoresByType );
	//
	// // add result to result string
	// if(subspaces == null) {
	// resultAsString+="null";
	// } else {
	// resultAsString+=toString(subspaces);
	// }
	// }
	//
	// // test validity of the result
	// assertTrue(resultAsString.equals(resultString));
	//
	// }
	//
	// @Test
	// public void testGetSubspacesWithInvalidInput() {
	//
	// // NULL input
	// assertNull(OntologyModuleSelectionService.getSubspaceBorders( null, new
	// ScoreType[5], new ArrayList<double[]>() ));
	// assertNull(OntologyModuleSelectionService.getSubspaceBorders( new
	// ArrayList<OntologySet>(), null, new ArrayList<double[]>() ));
	// assertNull(OntologyModuleSelectionService.getSubspaceBorders( new
	// ArrayList<OntologySet>(), new ScoreType[5], null ));
	//
	// // the number of types in scoresByType does not correspond to the number
	// of score types
	// assertNull(OntologyModuleSelectionService.getSubspaceBorders( new
	// ArrayList<OntologySet>(), new ScoreType[5], new ArrayList<double[]>() ));
	//
	// }
	//
	// @Test
	// public void testExpandPreferencesWithValidInput() {
	//
	// ArrayList<int[]> expandedPreferences1 =
	// OntologyModuleSelectionService.expandPreferences(new int[] {1,1,1});
	// ArrayList<int[]> expectedPreferences1 = new ArrayList<int[]>();
	// expectedPreferences1.add(new int[] {0,0,0});
	// expectedPreferences1.add(new int[] {1,1,1});
	// expectedPreferences1.add(new int[] {2,2,2});
	// assertTrue(Arrays.deepEquals(expandedPreferences1.toArray(),
	// expectedPreferences1.toArray()));
	//
	// ArrayList<int[]> expandedPreferences2 =
	// OntologyModuleSelectionService.expandPreferences(new int[] {0,1,2});
	// ArrayList<int[]> expectedPreferences2 = new ArrayList<int[]>();
	// expectedPreferences2.add(new int[] {0,1,2});
	// assertTrue(Arrays.deepEquals(expandedPreferences2.toArray(),
	// expectedPreferences2.toArray()));
	//
	// ArrayList<int[]> expandedPreferences3 =
	// OntologyModuleSelectionService.expandPreferences(new int[] {0,0,1});
	// ArrayList<int[]> expectedPreferences3 = new ArrayList<int[]>();
	// expectedPreferences3.add(new int[] {0,0,1});
	// expectedPreferences3.add(new int[] {0,0,2});
	// expectedPreferences3.add(new int[] {1,1,2});
	// assertTrue(Arrays.deepEquals(expandedPreferences3.toArray(),
	// expectedPreferences3.toArray()));
	//
	// }
	//
	// @Test
	// public void testExpandPreferencesWithInvalidInput() {
	//
	// assertTrue(OntologyModuleSelectionService.expandPreferences(null).size()==0);
	//
	// }

	@Test
	public void testSelectForText() throws Exception {

		Registry registry = RegistryBuilder.buildAndStartupRegistry(JoyceProcessesModule.class);

		IOntologyModuleSelectionService selectionService = registry.getService(IOntologyModuleSelectionService.class);
		// String inputTerms = "Backpain, White blood cell, Carcinoma, Cavity of
		// stomach, Ductal Carcinoma in Situ, Adjuvant chemotherapy, Axillary
		// lymph node staging, Mastectomy, tamoxifen, serotonin reuptake
		// inhibitors, Invasive Breast Cancer, hormone receptor positive breast
		// cancer, ovarian ablation, premenopausal women, surgical management,
		// biopsy of breast tumor, Fine needle aspiration, entinel lymph node,
		// breast preservation, adjuvant radiation therapy, prechemotherapy,
		// Inflammatory Breast Cancer, ovarian failure, Bone scan, lumpectomy,
		// brain metastases, pericardial effusion, aromatase inhibitor,
		// postmenopausal, Palliative care, Guidelines, Stage IV breast cancer
		// disease, Trastuzumab, Breast MRI examination";
//		String inputTerms = "plant anatomical entity, plant structure, plant cell, adverse event, entity, project, bilateral shaking finding, reporting process";
		String inputTerms ="magma";
		List<OntologySet> recommendations = selectionService.selectForText(inputTerms, new SelectionParameters());

		System.out.println("testSelectForText()");
		System.out.println(recommendations.size());
		System.out.println(recommendations);
		for (IOntology o : recommendations.get(0).getOntologies()) {
			OWLOntologyManager oom = o.getOwlOntology().getOWLOntologyManager();
			try (OutputStream os = new FileOutputStream(o.getId() + ".owl")) {
				oom.saveOntology(o.getOwlOntology(), os);
			}
		}

		registry.shutdown();

	}

	/**
	 * 
	 * @param subspaces
	 * @return a string representation of subspaces
	 */
	private String toString(ArrayList<double[]> subspaces) {

		String str = "";

		for (double[] space : subspaces) {
			str += "[";
			for (double j : space) {
				str += j + " ";
			}
			str += "]";
		}

		return str;
	}

}
