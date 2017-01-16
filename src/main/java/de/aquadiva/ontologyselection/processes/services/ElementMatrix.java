package de.aquadiva.ontologyselection.processes.services;

import java.util.ArrayList;

import de.aquadiva.ontologyselection.base.data.OntologySet;
import de.aquadiva.ontologyselection.base.data.ScoreType;

/**
 * A class maintaining the ontology sets for each subspace. 
 * 
 * @author friederike
 *
 */
public class ElementMatrix {
	private ArrayList<ArrayList<OntologySet>> entries;
	private ArrayList<double[]> subspaces;
	private int dimensions;
	private ScoreType[] scoreTypes;
	private int numberOfSubspaces;
	
	public ElementMatrix(ArrayList<double[]> subspaces, ScoreType[] scoreTypes) {
		super();
		this.dimensions = subspaces.size();
		this.numberOfSubspaces = (int) Math.pow( (double) dimensions, (double) dimensions );
		this.subspaces = subspaces;
		this.scoreTypes = scoreTypes;
		entries = new ArrayList<ArrayList<OntologySet>>( this.numberOfSubspaces );
		
		// initialize entries
		for(int i=0; i<( this.numberOfSubspaces ); i++) {
			entries.add(new ArrayList<OntologySet>());
		}
		
//		System.out.println("subspaces.size: " + subspaces.size() + ", dimensions: " + this.dimensions + ", numberOfSubspaces: " + this.numberOfSubspaces);
	}
	
	/**
	 * Adds the given ontology set to all subspaces it is contained in.
	 * 
	 * @param s
	 */
	public void addElement(OntologySet s) {
		
		// determine all subspaces (identified by a position) that contain s
		ArrayList<int[]> positions = new ArrayList<int[]>();
		
		// for each dimension
		for( int t=0; t<this.dimensions; t++) {
			ArrayList<int[]> positionsFromThisDimension = new ArrayList<int[]>(); // positions, newly created in this dimension
			
			// determine index
			double score = s.getScore(this.scoreTypes[t]);
			double[] subspace = subspaces.get(t);
			
			// find position of this score w.r.t. the considered dimension 
			for( int j=0; j<this.dimensions; j++ ) {
				if( score < subspace[2*j] ) {
					break;
				}
				if( score <= subspace[2*j+1] ) {
					// dimension 0
					if(t==0) {
						int[] position = new int[this.dimensions]; // create new position
						position[t] = j; // set current dimension
						positionsFromThisDimension.add(position); // add position
					} else {
						// duplicate all existing positions
						ArrayList<int[]> newPositions = new ArrayList<int[]>();
						for( int[] position : positions ) {
							int[] pos = position.clone();
							newPositions.add(pos);
						}
						// add subspace index for that dimension to all newly created positions
						for( int[] pos : newPositions ) {
							pos[t] = j;
						}
						// add them to the list of newly created positions
						positionsFromThisDimension.addAll(newPositions);		
					}					
				}
			}
			
			// if s is contained in more than one range of this dimension, replace the existing positions
			if(positionsFromThisDimension.size()!=0) {
				positions = positionsFromThisDimension;
			}
		
		}
				
		// add s to all subspaces identified by the positions assigned to s
		for(int[] position : positions) {
			this.entries.get(this.getIndex(position)).add(s);			
		}

	}

	/**
	 * Returns the ontology sets that belong to the given subspace.
	 * 
	 * @param subspace
	 * @return
	 */
	public ArrayList<OntologySet> getSubspaceElements( int[] subspace ) {
		if( subspace.length!=this.dimensions ) {
			return null;
		} else {
			return this.entries.get(this.getIndex(subspace));
		}
	}
	
	private int getIndex( int[] position ) {
		int index = 0;
		for(int i=0;i<this.dimensions;i++) {
			index += position[i] * ( this.numberOfSubspaces / (int) (Math.pow((double)this.dimensions, (double) i + 1.0)) );
		}
		return index;
	}


	@Override
	public String toString() {

		String str = "";
		// add general information
		str+= "ElementMatrix [" + this.dimensions + "]\n";
		
		// add subspaces and their elements
		int[] position = new int[this.dimensions];
		for(int i=0; i<this.dimensions-1;i++) {
			position[i] = 0;
		}
		
		return str + printPositions(position, 0);

	}
	
	private String printPositions(int[] position, int dim) {
		String str = "";

		for(int i=0; i<this.dimensions;i++) {
			position[dim] = i;
			if(dim != this.dimensions-1) {
				str += printPositions(position, dim+1);
			} else {
				String posStr = "";
				posStr+="[";
				for(int p : position) {
					posStr+= p + " ";
				}
				posStr+="]: ";
				str+=posStr;
				str+=this.getSubspaceElements(position);
				str+="\n";
			}
		}
		
		return str;
	}
}
