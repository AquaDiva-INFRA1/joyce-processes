package de.aquadiva.joyce.processes.services;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import de.aquadiva.joyce.base.data.OntologySet;
import de.aquadiva.joyce.base.data.ScoreType;
import de.aquadiva.joyce.processes.services.SkylineSearch;

public class SkylineSearchTest {
	static OntologySet optimalSet1;
	static OntologySet optimalSet2;
	static OntologySet optimalSet3;
	static OntologySet optimalSet4;
	static OntologySet optimalSet5;
	static OntologySet nonOptimalSet1;
	static OntologySet nonOptimalSet2;
	static OntologySet nonOptimalSet3;
	static OntologySet nonOptimalSet4;
	static OntologySet nonOptimalSet5;
	static OntologySet nonOptimalSet6;
	static OntologySet nonOptimalSet7;
	static OntologySet nonOptimalSet8;
	static OntologySet nonOptimalSet9;
	static OntologySet nonOptimalSet10;
	static List<OntologySet> candidates;
	static final ScoreType[] SCORE_TYPES = {ScoreType.TERM_COVERAGE, ScoreType.CLASS_OVERHEAD}; // ScoreTypes to be considered
	
	@Before
	public void setup() {
		
		candidates = new ArrayList<OntologySet>();
		
		// THE OPTIMAL SETS
		
		// [0.1, 0.7]		
		optimalSet1 = new OntologySet();
		optimalSet1.setScore(ScoreType.TERM_COVERAGE, 0.1);
		optimalSet1.setScore(ScoreType.CLASS_OVERHEAD, 0.7);
		candidates.add(optimalSet1);
		
		// [0.3, 0.6]
		optimalSet2 = new OntologySet();
		optimalSet2.setScore(ScoreType.TERM_COVERAGE, 0.3);
		optimalSet2.setScore(ScoreType.CLASS_OVERHEAD, 0.6);
		candidates.add(optimalSet2);
		
		// [0.35, 0.5]
		optimalSet3 = new OntologySet();
		optimalSet3.setScore(ScoreType.TERM_COVERAGE, 0.35);
		optimalSet3.setScore(ScoreType.CLASS_OVERHEAD, 0.5);
		candidates.add(optimalSet3);
		
		// [0.4, 0.2]
		optimalSet4 = new OntologySet();
		optimalSet4.setScore(ScoreType.TERM_COVERAGE, 0.4);
		optimalSet4.setScore(ScoreType.CLASS_OVERHEAD, 0.2);
		candidates.add(optimalSet4);
		
		// [0.4, 0.2] - duplicate of optimalSet4
		optimalSet5 = new OntologySet();
		optimalSet5.setScore(ScoreType.TERM_COVERAGE, 0.4);
		optimalSet5.setScore(ScoreType.CLASS_OVERHEAD, 0.2);
		candidates.add(optimalSet5);

		// THE NON-OPTIMAL SETS
		
		// [0.05, 0.65]
		nonOptimalSet1 = new OntologySet();
		nonOptimalSet1.setScore(ScoreType.TERM_COVERAGE, 0.05);
		nonOptimalSet1.setScore(ScoreType.CLASS_OVERHEAD, 0.65);
		candidates.add(nonOptimalSet1);
		
		// [0.05, 0.55]
		nonOptimalSet2 = new OntologySet();
		nonOptimalSet2.setScore(ScoreType.TERM_COVERAGE, 0.05);
		nonOptimalSet2.setScore(ScoreType.CLASS_OVERHEAD, 0.55);
		candidates.add(nonOptimalSet2);
		
		// [0.05, 0.35]
		nonOptimalSet3 = new OntologySet();
		nonOptimalSet3.setScore(ScoreType.TERM_COVERAGE, 0.05);
		nonOptimalSet3.setScore(ScoreType.CLASS_OVERHEAD, 0.35);
		candidates.add(nonOptimalSet3);
		
		// [0.05, 0.1]
		nonOptimalSet4 = new OntologySet();
		nonOptimalSet4.setScore(ScoreType.TERM_COVERAGE, 0.05);
		nonOptimalSet4.setScore(ScoreType.CLASS_OVERHEAD, 0.1);
		candidates.add(nonOptimalSet4);
		
		// [0.2, 0.55]
		nonOptimalSet5 = new OntologySet();
		nonOptimalSet5.setScore(ScoreType.TERM_COVERAGE, 0.2);
		nonOptimalSet5.setScore(ScoreType.CLASS_OVERHEAD, 0.55);
		candidates.add(nonOptimalSet5);

		// [0.2, 0.35]
		nonOptimalSet6 = new OntologySet();
		nonOptimalSet6.setScore(ScoreType.TERM_COVERAGE, 0.2);
		nonOptimalSet6.setScore(ScoreType.CLASS_OVERHEAD, 0.35);
		candidates.add(nonOptimalSet6);
		
		// [0.2, 0.1]
		nonOptimalSet7 = new OntologySet();
		nonOptimalSet7.setScore(ScoreType.TERM_COVERAGE, 0.2);
		nonOptimalSet7.setScore(ScoreType.CLASS_OVERHEAD, 0.1);
		candidates.add(nonOptimalSet7);

		// [0.325, 0.35]
		nonOptimalSet8 = new OntologySet();
		nonOptimalSet8.setScore(ScoreType.TERM_COVERAGE, 0.325);
		nonOptimalSet8.setScore(ScoreType.CLASS_OVERHEAD, 0.35);
		candidates.add(nonOptimalSet8);

		// [0.325, 0.1]
		nonOptimalSet9 = new OntologySet();
		nonOptimalSet9.setScore(ScoreType.TERM_COVERAGE, 0.325);
		nonOptimalSet9.setScore(ScoreType.CLASS_OVERHEAD, 0.1);
		candidates.add(nonOptimalSet9);

		// [0.375, 0.1]
		nonOptimalSet10 = new OntologySet();
		nonOptimalSet10.setScore(ScoreType.TERM_COVERAGE, 0.375);
		nonOptimalSet10.setScore(ScoreType.CLASS_OVERHEAD, 0.1);
		candidates.add(nonOptimalSet10);
		
	}
	
	@Test
	public void testGetSkylineWithValidInputs() {
		
		//test correctness of the result for a valid input
		List<OntologySet> result = SkylineSearch.getSkyline(candidates, SCORE_TYPES);
		assertTrue( result.contains(optimalSet1) && result.contains(optimalSet2) && result.contains(optimalSet3) && result.contains(optimalSet4) && result.contains(optimalSet5) );

	}
	
	@Test
	public void testGetSkylineWithInalidInputs() {
		
		/**
		 * test correctness of the result for invalid inputs
		 */
		
		// some input is NULL -> return NULL
		assertNull( SkylineSearch.getSkyline(null, SCORE_TYPES) );
		assertNull( SkylineSearch.getSkyline(candidates, null) );
		assertNull( SkylineSearch.getSkyline(null, null) );
		
		// no score types given -> return empty set
		assertTrue( SkylineSearch.getSkyline(candidates, new ScoreType[0]).size()==0 );
		
		// no candidates given -> return empty set
		assertTrue( SkylineSearch.getSkyline(new ArrayList<OntologySet>(), SCORE_TYPES).size()==0 );
		
		// NULL candidate -> return NULL
		ArrayList<OntologySet> candidatesWithEmptyCandidate = new ArrayList<OntologySet>();
		candidatesWithEmptyCandidate.add(null);
		assertNull( SkylineSearch.getSkyline(candidatesWithEmptyCandidate, SCORE_TYPES) );

		// candidate with no scores -> return empty set
		ArrayList<OntologySet> candidatesWithDisjointScoresCandidate = new ArrayList<OntologySet>();
		candidatesWithDisjointScoresCandidate.add(new OntologySet());
		assertTrue( SkylineSearch.getSkyline(candidatesWithDisjointScoresCandidate, SCORE_TYPES).size()==0 );
		
		// candidate having not been scored with respect to all input score types -> return empty set
		ArrayList<OntologySet> candidatesWithDisjointScoresCandidate2 = new ArrayList<OntologySet>();
		OntologySet setWithWrongScoreType = new OntologySet();
		setWithWrongScoreType.setScore(ScoreType.POPULARITY, 0.0);
		setWithWrongScoreType.setScore(ScoreType.TERM_COVERAGE, 0.0);
		candidatesWithDisjointScoresCandidate2.add(setWithWrongScoreType);
		assertTrue( SkylineSearch.getSkyline(candidatesWithDisjointScoresCandidate2, SCORE_TYPES).size()==0 );
		
	}
	
}
