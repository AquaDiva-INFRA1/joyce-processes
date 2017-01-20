package de.aquadiva.joyce.processes.services;

import ifis.skysim2.algorithms.SkylineAlgorithm;
import ifis.skysim2.algorithms.SkylineAlgorithmBBS;
import ifis.skysim2.data.sources.PointSource;
import ifis.skysim2.data.sources.PointSourceRAM;
import ifis.skysim2.simulator.DataSource;
import ifis.skysim2.simulator.SimulatorConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.aquadiva.joyce.base.data.IOntologySet;
import de.aquadiva.joyce.base.data.OntologySet;
import de.aquadiva.joyce.base.data.ScoreType;

/**
 * Class providing access to the skyline algorithms implemented by Christoph Lofi (lofi@ifis.cs.tu-bs.de).
 * 
 * @author lofi, friederike
 *
 */
public class SkylineSearch {
    /**
     * Creates a simple config setting.
     *
     * @return
     */
    private static SimulatorConfiguration getSimpleConfig() {
        SimulatorConfiguration config = new SimulatorConfiguration();
        config.setDataSource(DataSource.MEMORY); // set type of data source
        config.setSkylineAlgorithm(new SkylineAlgorithmBBS()); // set skyline algorithm
        // config.setSkylineAlgorithm(new SkylineAlgorithmParallelBNLLinkedListLazySync_SubspaceAware());
        // config.setSkylineAlgorithm(new SkylineAlgorithmKdTrie(false));
        // config.setSkylineAlgorithm(new SkylineAlgorithmBNL());
        config.getSkylineAlgorithm().setExperimentConfig(config);
        // its indeed a multi-cpu algorithm....
//        config.setNumberOfCPUs(4);
//        config.setDistributedNumBlocks(100);
//        config.setStaticArrayWindowSize(200000);
//        config.setDeleteDuringCleaning(true);
        return config;
    }

	
    /**
     * Runs a skyline algorithm on a set of candidate ontology sets based on the given score types.
     * 
     * @param candidates the candidate ontology sets
     * @param scoreTypes the score types to consider
     * @return the skyline ontology sets, i.e. the pareto-optimal sets
     */
    public static List<OntologySet> getSkyline(List<OntologySet> candidates, ScoreType[] scoreTypes) {
    	
    	if ( candidates!=null && scoreTypes !=null ) {
    		
    		if ( candidates.size()==0 || scoreTypes.length==0 ) return new ArrayList<OntologySet>();
    		
        	/**
        	 *  properly transform the input into a format that is understood by the skyline algorithm
        	 *  (candidate.getScore(ScoreType1),candidate.getScore(ScoreType2), ...)
        	 */
        	List<float[]> dataList = new ArrayList<float[]>(); // list of transformed candidates
        	HashMap<String, ArrayList<OntologySet>> dataArrayToInputMap = new HashMap<String, ArrayList<OntologySet>>(); // needed for translation of the output to sets of ontologies
        	
        	// transform candidates and add them to the list
        	for( int candidateId=0; candidateId<candidates.size(); candidateId++ ) {
        		
        		OntologySet c = candidates.get(candidateId); // get candidate
        		
        		if( c==null ) { return null; }
        		
        		// candidate does not have one or more of the considered scores assigned
        		boolean ok = true;
        		
        		if( c.getScores()==null || c.getScores().size()==0 ) {
        			ok = false;
        		} else {
        			for ( ScoreType t : scoreTypes ) {
        				if( !c.getScores().containsKey(t) ) {
        					ok = false;
        					break;
        				}
            		}
        		}
        		
        		if (!ok ) { return new ArrayList<OntologySet>(); }
        		
        		float[] dataItem = new float[scoreTypes.length]; // initialize data array
        		
        		// add scores
        		for( int j=0; j<scoreTypes.length;j++ ) {
        			dataItem[j] = c.getScore(scoreTypes[j]).floatValue();
        		}
        		
        		// add a mapping from the given data array to the candidate ontology set
        		ArrayList<OntologySet> candidatesWithGivenArray;
        		if( !dataArrayToInputMap.containsKey( getStringRepresentation(dataItem) ) ) {
        			candidatesWithGivenArray = new ArrayList<OntologySet>();
        		} else {
        			candidatesWithGivenArray = dataArrayToInputMap.get( getStringRepresentation(dataItem) );
        		}
        		candidatesWithGivenArray.add(c);
        		dataArrayToInputMap.put( getStringRepresentation(dataItem) , candidatesWithGivenArray);
        		
        		// add data item to the list
        		dataList.add(dataItem);

        	}
        	
        	// create a PointSource out of the list
        	PointSource data = new PointSourceRAM(dataList);
        	
        	/**
        	 *  determine the skyline
        	 */
        	SimulatorConfiguration config = getSimpleConfig();
        	config.setD(scoreTypes.length);
            SkylineAlgorithm alg = config.getSkylineAlgorithm();
            alg.setExperimentConfig(config);
                            
            // run skyline algorithm
            List<float[]> skyline = alg.compute(data);
            
        	
        	/**
        	 *  transform result into a list of IOntologySets
        	 */
        	ArrayList<OntologySet> transformedSkyline = new ArrayList<OntologySet>();
        	for ( float[] el : skyline ) {
        		
        		ArrayList<OntologySet> correspondingCandidates = dataArrayToInputMap.get( getStringRepresentation(el) );
        		
        		for( OntologySet candidate : correspondingCandidates ) {
        			if ( !transformedSkyline.contains(candidate) ) {
                		transformedSkyline.add( candidate );
        			}
        		}
        		
        	}
        	
            return transformedSkyline;

    	}
    	
    	return null;
    	
    }
    
    /**
     * Computes a string out of a float array based on the string representations of its values. This was need, since although two float-
     * arrays might contain the same printed values, the internal representation of these can be different. Thus our mapping from float-
     * arrays back to ontology sets does not work.
     * 
     * @param array
     * @return
     */
    private static String getStringRepresentation(float[] array) {
    	String str = "";
    	for( float f : array ) {
    		str+= f;
    	}
    	return str;
    }
}
