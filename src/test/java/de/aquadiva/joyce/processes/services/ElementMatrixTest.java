package de.aquadiva.joyce.processes.services;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;

import de.aquadiva.joyce.base.data.OntologySet;
import de.aquadiva.joyce.base.data.ScoreType;
import de.aquadiva.joyce.processes.services.ElementMatrix;

public class ElementMatrixTest {
	
	@Test
	public void testToString() {
//		ArrayList<double[]> subspaces = new ArrayList<double[]>();
//		subspaces.add(new double[3]);
//		ElementMatrix m = new ElementMatrix(subspaces, null);
//		System.out.println(m.toString());
//		fail("Not yet implemented");
	}

	@Test
	public void testAddElement() {
		
		// create proper subspaces for 3 dimensions
		ArrayList<double[]> subspaces = new ArrayList<double[]>();
		subspaces.add(new double[] {0.0, 2.0, 2.0, 4.0, 4.0, 6.0});
		subspaces.add(new double[] {0.0, 2.0, 2.0, 4.0, 4.0, 6.0});
		subspaces.add(new double[] {0.0, 2.0, 2.0, 4.0, 4.0, 6.0});
		
		// create list of 3 score types
		ScoreType[] scoreTypes = {ScoreType.ACTIVE_COMMUNITY, ScoreType.CLASS_OVERHEAD, ScoreType.CLASS_OVERLAP};
		
		ElementMatrix m = new ElementMatrix(subspaces, scoreTypes);
		OntologySet s = new OntologySet();
		s.setScore(ScoreType.ACTIVE_COMMUNITY, 1.0);
		s.setScore(ScoreType.CLASS_OVERHEAD, 3.0);
		s.setScore(ScoreType.CLASS_OVERLAP, 5.0);
		m.addElement(s);
		System.out.println(m.toString());
		
		ElementMatrix m2 = new ElementMatrix(subspaces, scoreTypes);
		OntologySet s2 = new OntologySet();
		s2.setScore(ScoreType.ACTIVE_COMMUNITY, 2.0);
		s2.setScore(ScoreType.CLASS_OVERHEAD, 3.0);
		s2.setScore(ScoreType.CLASS_OVERLAP, 5.0);
		m2.addElement(s2);
		System.out.println(m2.toString());
		
		ElementMatrix m3 = new ElementMatrix(subspaces, scoreTypes);
		OntologySet s3 = new OntologySet();
		s3.setScore(ScoreType.ACTIVE_COMMUNITY, 2.0);
		s3.setScore(ScoreType.CLASS_OVERHEAD, 4.0);
		s3.setScore(ScoreType.CLASS_OVERLAP, 6.0);
		m3.addElement(s3);
		System.out.println(m3.toString());
		
//		fail("Not yet implemented");
	}

	@Test
	public void testGetSubspaceElements() {
		fail("Not yet implemented");
	}

}
