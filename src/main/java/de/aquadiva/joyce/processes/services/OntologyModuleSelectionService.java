package de.aquadiva.joyce.processes.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;

import org.apache.tapestry5.ioc.annotations.Symbol;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;

import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;
import com.clarkparsia.pellet.owlapiv3.Reasoner;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import de.aquadiva.joyce.JoyceSymbolConstants;
import de.aquadiva.joyce.base.data.IOntology;
import de.aquadiva.joyce.base.data.IOntologySet;
import de.aquadiva.joyce.base.data.Ontology;
import de.aquadiva.joyce.base.data.OntologyModule;
import de.aquadiva.joyce.base.data.OntologySet;
import de.aquadiva.joyce.base.data.ScoreType;
import de.aquadiva.joyce.base.services.IConstantOntologyScorer;
import de.aquadiva.joyce.base.services.IMetaConceptService;
import de.aquadiva.joyce.base.services.IOWLParsingService;
import de.aquadiva.joyce.base.services.IOntologyDBService;
import de.aquadiva.joyce.base.services.IVariableOntologyScorer;
import de.aquadiva.joyce.core.services.IConceptTaggingService;
import de.aquadiva.joyce.core.services.ClassCoverageScorer.ClassCoverage;
import de.aquadiva.joyce.core.services.ClassOverheadScorer.ClassOverhead;
import de.aquadiva.joyce.core.services.ClassOverlapScorer.ClassOverlap;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

public class OntologyModuleSelectionService implements IOntologyModuleSelectionService {

	private Logger log;
	private IConceptTaggingService taggingService;
	private IOntologyDBService dbService;
	private IVariableOntologyScorer classCoverageScorer;
	private IVariableOntologyScorer classOverheadScorer;
	private IVariableOntologyScorer classOverlapScorer;
	// private final int MAX_ELEMENTS_PER_SET = 10; // the maximal number of
	// ontologies/modules per
	// set
	// that shall be computed
	// private ScoreType[] VARIABLE_SCORE_TYPES_TO_CONSIDER; // the variable
	// // score types
	// // to be
	// // considered
	// // during the
	// // set assembly
	//
	// {
	// VARIABLE_SCORE_TYPES_TO_CONSIDER = new ScoreType[] {
	// ScoreType.TERM_COVERAGE, ScoreType.CLASS_OVERHEAD,
	// ScoreType.CLASS_OVERLAP };
	// }

	private final HashMap<ScoreType, Double> CONSTANT_SCORES_THRESHOLDS;
	private IVariableOntologyScorer variableScoringChain;
	private IConstantOntologyScorer constantScoringChain;
	private ExecutorService executorService;
	private IOWLParsingService owlParsingService;
	private IMetaConceptService metaConceptService;

	{
		// TODO: set proper thresholds
		CONSTANT_SCORES_THRESHOLDS = new HashMap<ScoreType, Double>();
		CONSTANT_SCORES_THRESHOLDS.put(ScoreType.ACTIVE_COMMUNITY, 0.0);
		CONSTANT_SCORES_THRESHOLDS.put(ScoreType.CLASS_DESCRIPTIVITY, 0.0);
		CONSTANT_SCORES_THRESHOLDS.put(ScoreType.POPULARITY, 0.0);
		CONSTANT_SCORES_THRESHOLDS.put(ScoreType.UP_TO_DATE, 0.0);
		CONSTANT_SCORES_THRESHOLDS.put(ScoreType.OBJECT_PROPERTY_RICHNESS, 0.0);
	}

	public OntologyModuleSelectionService(Logger log, IConceptTaggingService taggingService,
			IOntologyDBService dbService, IMetaConceptService metaConceptService,
			@VariableScoringChain IVariableOntologyScorer variableScoringChain,
			@ConstantScoringChain IConstantOntologyScorer constantScoringChain,
			@ClassOverlap IVariableOntologyScorer classOverlapScorer,
			@ClassOverhead IVariableOntologyScorer classOverheadScorer,
			@ClassCoverage IVariableOntologyScorer classCoverageScorer, ExecutorService executorService,
			IOWLParsingService owlParsingService) throws IOException {
		this.log = log;
		this.taggingService = taggingService;
		this.dbService = dbService;
		this.metaConceptService = metaConceptService;
		this.variableScoringChain = variableScoringChain;
		this.constantScoringChain = constantScoringChain;
		this.executorService = executorService;
		this.owlParsingService = owlParsingService;

		this.classCoverageScorer = classCoverageScorer;
		this.classOverheadScorer = classOverheadScorer;
		this.classOverlapScorer = classOverheadScorer;

		// This takes about an hour - not worth it. Why it takes so long, I
		// don't know. (EF, 17.11.2016)
		// log.info("Loading all ontologies from the database");
		// long time = System.currentTimeMillis();
		// // This will take a long time! It is a warmer command: After that,
		// all
		// // ontologies have been loaded. If all required ontology prooperties
		// // have been eager-fetched, no queries to the database will be
		// necessary
		// // any more after that.
		// // Note that this service must be eager loaded itself by the Tapestry
		// // module in order to have all ontologies loaded directly at
		// application
		// // startup.
		// List<Ontology> allOntologies = dbService.getAllOntologies();
		// time = System.currentTimeMillis() - time;
		// log.info("Done loading {} ontologies from the database in {}ms
		// ({}s).", new Object[] {allOntologies.size(), time, time/1000});
	}

	private class VariableScoringWorker implements Callable<Ontology> {
		private Ontology o;
		private Multiset<String> concepts;

		public VariableScoringWorker(Ontology o, Multiset<String> concepts) {
			this.o = o;
			this.concepts = concepts;
		}

		@Override
		public Ontology call() throws Exception {
			variableScoringChain.score(o, concepts);
			return o;
		}

	}

	private class LocalityModuleExtractionWorker implements Callable<Ontology> {
		private Ontology o;
		private Set<String> classIris;

		public LocalityModuleExtractionWorker(Ontology o, Set<String> iriClassesInText) {
			this.o = o;
			this.classIris = iriClassesInText;
		}

		@Override
		public Ontology call() throws Exception {
			try {
				// PelletReasonerFactory factory = new PelletReasonerFactory();
				OWLOntologyManager ontologyManager = owlParsingService.getOwlOntologyManager();
				if (!o.isOwlOntologySet())
					owlParsingService.parse(o);
				// first, remove a potentially already existing locality module
				// of this ontology from the OWLOntologyManager
				IRI moduleIri = IRI
						.create(o.getOwlOntology().getOntologyID().getOntologyIRI().toString() + "_localitymodule");
				owlParsingService.removeIntology(moduleIri);
				SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(ontologyManager,
						o.getOwlOntology(), ModuleType.STAR);
				// OWLDataFactory dataFactory =
				// ontologyManager.getOWLDataFactory();
				Set<OWLEntity> entities = new HashSet<>(classIris.size());
				for (OWLClass owlClass : o.getOwlOntology().getClassesInSignature()) {
					if (classIris.contains(owlClass.getIRI().toString())) {
						entities.add(owlClass);
					}
				}
				if (entities.isEmpty())
					log.debug(
							"Found no searched classes in {} but attempted to create module anyway. The classes that were meant to be in the ontology are: {}",
							new Object[] { o.getId(), classIris });
				log.debug(
						"Extracting locality-based module from ontology {}. For {} class IRIs found"
								+ " in user input, {} classes were found in this ontology.",
						new Object[] { o.getId(), classIris.size(), entities.size() });
				// we could also extract module taking sub- or superclasses into
				// account; those would be determined by a reasoner, thus also
				// complex classes would be extracted
				long time = System.currentTimeMillis();
				OWLOntology module = extractor.extractAsOntology(entities, moduleIri);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (GZIPOutputStream os = new GZIPOutputStream(baos)) {
					ontologyManager.saveOntology(module, os);
				}
				OntologyModule ontologyModule = o.createNonStaticModule(o.getId() + "_localitymodule",
						baos.toByteArray());
				ontologyModule.setOwlOntology(module);
				Set<String> classIdsForModule = metaConceptService.getMixedClassIdsForOntology(ontologyModule);
				ontologyModule.setClassIds(classIdsForModule);
				time = System.currentTimeMillis() - time;
				log.debug("Extracting module from ontology {} took {} ms ({} s)",
						new Object[] { o.getId(), time, (time / 1000) });
				return ontologyModule;
			} catch (Exception | Error e) {
				log.error("Exception occurred while trying extract a locality based module from ontology " + o.getId(),
						e);
				throw e;
			}
		}

	}

	/**
	 * Scores all ontologies and returns a score-ranked list of suggested
	 * ontology sets. Caution: All returned ontologies are copies of the
	 * ontologies residing in the database. This is necessary in a multi-user
	 * environment as depending on the input, the scores will change and thus
	 * multiple instances of the ontologies are required.
	 */
	@Override
	public List<OntologySet> selectForText(String text, SelectionParameters params) {
		log.info("Starting module selection process.");
		// owlParsingService.reset();

		// find concepts for the input terms
		log.debug("Finding concepts in input text {}", text);
		Multiset<String> mixedClassesInText = taggingService.findConcepts(text);
		Set<String> iriClassesInText = metaConceptService.convertMixedClassesToIriClasses(mixedClassesInText);

		// the next three lines were used for evaluation when the concept list
		// was fixed so we didn't have to load the concept dictionary on each
		// evaluation run.
		// Multiset<String> concepts = HashMultiset.create();
		// String ourconcepts = "atid6833, atid15455,
		// http://purl.obolibrary.org/obo/WSIO_compression_004, atid254780,
		// atid212432, atid4050,
		// http://phenomebrowser.net/ontologies/mesh/mesh.owl#D002641,
		// atid29911, atid124130, atid7009, atid219971,
		// http://phenomebrowser.net/ontologies/mesh/mesh.owl#G05.360.340.037,
		// atid59295, atid1079, atid1956, atid18608, atid158,
		// http://phenomebrowser.net/ontologies/mesh/mesh.owl#Z01.107.567.875.510.350.200,
		// atid343339, atid20904, atid200172,
		// http://doe-generated-ontology.com/OntoAD#C0080103,
		// http://doe-generated-ontology.com/OntoAD#C0027361, atid112,
		// http://purl.obolibrary.org/obo/ENVO_00005803, atid261, atid59438,
		// http://purl.obolibrary.org/obo/TGMA_0001246, atid7075, atid235407,
		// http://purl.obolibrary.org/obo/NCBIGene_40633, atid192148,
		// http://phenomebrowser.net/ontologies/mesh/mesh.owl#Z01.433.305,
		// atid996 x 2, atid229581, atid205028, atid29665, atid192562, atid7061,
		// atid528, atid292432, atid192040,
		// http://doe-generated-ontology.com/OntoAD#C1524026 x 2,
		// http://phenomebrowser.net/ontologies/mesh/mesh.owl#D059646,
		// http://purl.obolibrary.org/obo/NCBIGene_11067, atid6367, atid183535,
		// http://purl.obolibrary.org/obo/ENVO_0010080, atid190349, atid5537,
		// http://phenomebrowser.net/ontologies/mesh/mesh.owl#Z01.107.567.875.350.350.200,
		// http://who.int/icf#e2253, atid41238,
		// http://purl.obolibrary.org/obo/ENVO_01000703, atid256088,
		// http://doe-generated-ontology.com/OntoAD#C0232117,
		// http://purl.obolibrary.org/obo/NCBIGene_32506, atid14195, atid13960,
		// atid13132, atid12153, atid923,
		// http://doe-generated-ontology.com/OntoAD#C0699900,
		// http://who.int/icf#d4104, atid197151, atid2128,
		// http://purl.obolibrary.org/obo/CHMO_0000152, atid20071, atid41121,
		// http://purl.obolibrary.org/obo/UBERON_0004529,
		// http://purl.obolibrary.org/obo/TGMA_0000677,
		// http://purl.obolibrary.org/obo/TGMA_0000678";
		// concepts.addAll(Arrays.asList(ourconcepts.split(", ")));

		log.info("Extracted concept IDs from input text: {}", mixedClassesInText);
		// get the ids of all modules that contain at least one input concept
		Multiset<String> moduleIds = metaConceptService.getOntologiesForMixedClasses(mixedClassesInText);

		log.debug("Module IDs containing extracted concepts: {}", moduleIds);
		// score these ontologies w.r.t. the defined scorers
		// and create an ontology set, which contains that ontology/module
		// and create a list of existing ontologies
		ArrayList<OntologySet> initialCandidateSet = new ArrayList<OntologySet>();
		ArrayList<Ontology> ontologies = new ArrayList<>();

		// Batch-preloading for performance improvement
		String[] array = moduleIds.elementSet().toArray(new String[moduleIds.elementSet().size()]);
		log.info("Getting all module IDs from database: {}", Arrays.toString(array));
		List<Ontology> allOntologies = dbService.getOntologiesByIds(array);
		Set<Ontology> ontologiesToScore = new HashSet<>();
		List<Future<Ontology>> localityModules = new ArrayList<>();
		for (Ontology o : allOntologies) {
			if (params.selectionType == SelectionParameters.SelectionType.ONTOLOGY)
				ontologiesToScore.add(getSourceOntology(o));
			if (params.selectionType == SelectionParameters.SelectionType.CLUSTER_MODULE && o instanceof OntologyModule)
				ontologiesToScore.add(o);
			if (params.selectionType == SelectionParameters.SelectionType.LOCALITY_MODULE
					&& !(o instanceof OntologyModule)) {
				localityModules.add(executorService.submit(new LocalityModuleExtractionWorker(o, iriClassesInText)));
			}
		}
		for (int i = 0; i < localityModules.size(); ++i) {
			Future<Ontology> localityModuleFuture = localityModules.get(i);
			try {
				ontologiesToScore.add(localityModuleFuture.get());
				log.info("Finished {} of {} locality module extractions", i, localityModules.size());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		log.info("Final ontology or module IDs: {}", ontologiesToScore);

		if (ontologiesToScore.isEmpty()) {
			log.info("No ontologies or modules for scoring could be determined.");
			return Collections.emptyList();
		}

		log.info("Scoring modules...");
		Set<Future<Ontology>> scoredOntologies = new HashSet<>(moduleIds.size());
		long copyTime = 0;
		for (Ontology om : ontologiesToScore) {

			// WARNING: Here we create a copy of the ontology. As of this
			// point we must not request the ontologies from the DB service as
			// it would return the original database-grounded ontology.
			long time = System.currentTimeMillis();
			Ontology o = om.copy();
			time = System.currentTimeMillis() - time;
			copyTime += time;

			VariableScoringWorker worker = new VariableScoringWorker(o, mixedClassesInText);
			Future<Ontology> future = executorService.submit(worker);
			scoredOntologies.add(future);
		}
		log.info("Copying of {} ontologies took {}ms", scoredOntologies.size(), copyTime);

		log.info("Preprocessing modules ... ");
		double bestCoverageSoFar = 0.0;
		for (Future<Ontology> ontologyFuture : scoredOntologies) {
			Ontology o = null;
			try {
				o = ontologyFuture.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}

			// filter out certain ontologies/modules that do not exceed the
			// given
			// thresholds for the constant scores
			boolean filterOut = false;
			for (ScoreType t : CONSTANT_SCORES_THRESHOLDS.keySet()) {
				if (o.getScore(t) < CONSTANT_SCORES_THRESHOLDS.get(t)) {
					filterOut = true;
					log.debug("Module did not pass filter " + o);
					break;
				}
			}

			if (!filterOut) {
				log.debug("Module passed filter " + o);

				// add the ontology/module to the set of ontologies
				ontologies.add(o);
				log.info("The scores of " + o.getId() + ": " + o.getScores());

				// create an ontology set, which contains that ontology/module
				OntologySet s = new OntologySet();
				HashSet<IOntology> containedOntologies = new HashSet<IOntology>();
				containedOntologies.add(o);
				s.setOntologies(containedOntologies);
				variableScoringChain.score(s, mixedClassesInText);
				// constantScoringChain.score(s); //UNCOMMENT, IF constant
				// scores shall be considered again
				initialCandidateSet.add(s);
				bestCoverageSoFar = Math.max(bestCoverageSoFar, s.getScore(ScoreType.TERM_COVERAGE));
				log.info("best coverage so far " + bestCoverageSoFar);
				log.debug("Created an ontology set and scored it " + o);
			}

		}

		// find the best modules
		List<OntologySet> result = determineOptimalSets(initialCandidateSet, ontologies, params,
				expandPreferences(params.preferences), 1, mixedClassesInText, bestCoverageSoFar);

		// find most important criterion
		ScoreType mostImpCriterion = null;
		int maximalPreference = 0;
		for (int i = 0; i < params.scoreTypesToConsider.length; i++) {
			if (maximalPreference <= params.preferences[i]) {
				mostImpCriterion = params.scoreTypesToConsider[i];
				maximalPreference = params.preferences[i];
			}
		}

		// sort results by most important criterion
		TreeMap<Double, ArrayList<OntologySet>> sortedResults = new TreeMap<Double, ArrayList<OntologySet>>();
		for (OntologySet s : result) {
			Double score = s.getScore(mostImpCriterion);
			if (!sortedResults.containsKey(score)) {
				ArrayList<OntologySet> resultsWithGivenScore = new ArrayList<OntologySet>();
				resultsWithGivenScore.add(s);
				sortedResults.put(score, resultsWithGivenScore);
			} else {
				sortedResults.get(score).add(s);
			}
		}

		ArrayList<OntologySet> sorted = new ArrayList<OntologySet>();
		for (Double score : sortedResults.descendingKeySet()) {
			sorted.addAll(sortedResults.get(score));
		}

		return sorted;

	}

	private Ontology getSourceOntology(Ontology om) {
		if (om instanceof OntologyModule)
			return ((OntologyModule) om).getSourceOntology();
		return om;
	}

	/**
	 * Recursively determines a set of pareto-optimal combinations of
	 * ontologies/modules with respect to the given score types and preferences.
	 * 
	 * @param candidates
	 * @param ontologies
	 * @param scoreTypes
	 * @param expandedPreferences
	 * @param currentElementsPerSet
	 * @param concepts
	 * @return
	 */
	private List<OntologySet> determineOptimalSets(List<OntologySet> candidates, List<Ontology> ontologies,
			SelectionParameters params, ArrayList<int[]> expandedPreferences, int currentElementsPerSet,
			Multiset<String> concepts, double bestCoverageSoFar) {

		log.info("\n\nDetermine optimal sets ...");
		log.info("Size candidate set: {}", candidates.size());

		double bestCoverage = bestCoverageSoFar;

		// handle invalid inputs
		if (candidates == null || ontologies == null || params.scoreTypesToConsider == null
				|| expandedPreferences == null || params.maxElementsPerSet == 0) {
			log.debug("return, since MAX_ELEMENTS_PER_SET==0");
			return new ArrayList<OntologySet>();
		}

		log.debug("candidates: " + candidates);
		log.debug("modules: " + ontologies);
		log.debug("scoreTypes:");
		log.debug("preferences: " + expandedPreferences);
		log.debug("currentElementsPerSet" + currentElementsPerSet);
		log.debug("concepts: " + concepts);
		log.debug("bestCoverageSoFar: " + bestCoverageSoFar);

		/**
		 * determine the pareto-optimal candidates sets out of the input
		 * candidate sets
		 */
		log.info("Before Skyline");
		List<OntologySet> optimalCandidates = SkylineSearch.getSkyline(candidates, params.scoreTypesToConsider);
		log.info("optimal candidates after taking the skyline: " + optimalCandidates);

		log.info("Before all-ontologies-Skyline");
		List<OntologySet> allOntologySets = new ArrayList<>();
		for (Ontology o : ontologies) {
			OntologySet ontologySet = new OntologySet();
			ontologySet.addOntology(o);
			allOntologySets.add(ontologySet);
		}
		List<OntologySet> skylineOntologySets = SkylineSearch.getSkyline(allOntologySets, params.scoreTypesToConsider);
		log.info("optimal candidates after taking the skyline: " + skylineOntologySets);
		Set<Ontology> allOntologySet = new HashSet<>(ontologies);
		Set<Ontology> skylineOntologies = new HashSet<>();
		for (OntologySet os : skylineOntologySets)
			skylineOntologies.add((Ontology) os.getOntologies().iterator().next());
		SetView<Ontology> nonSkylineOntologies = Sets.difference(allOntologySet, skylineOntologies);
		ArrayList<OntologySet> nonSkylineOntologiesSets = new ArrayList<>();
		for (Ontology o : nonSkylineOntologies) {
			OntologySet ontologySet = new OntologySet();
			ontologySet.addOntology(o);
			nonSkylineOntologiesSets.add(ontologySet);
		}
		List<Ontology> finalOntologies = null;
		// take random sample according to the user's preferences
		{
			// get the scores of the candidates separated by type
			ArrayList<double[]> scoresByType = new ArrayList<double[]>();
			for (ScoreType type : params.scoreTypesToConsider) {
				scoresByType.add(new double[nonSkylineOntologiesSets.size()]);
			}

			for (int j = 0; j < nonSkylineOntologiesSets.size(); j++) {
				for (int i = 0; i < params.scoreTypesToConsider.length; i++) {
					scoresByType.get(i)[j] = nonSkylineOntologiesSets.get(j).getScore(params.scoreTypesToConsider[i]);
				}
			}

			// for each type, divide the scores into subspaces
			ArrayList<double[]> subspaceBorders = getSubspaceBorders((List) nonSkylineOntologiesSets,
					params.scoreTypesToConsider, scoresByType);

			// get subspace elements
			ElementMatrix subspaces = new ElementMatrix(subspaceBorders, params.scoreTypesToConsider);
			for (OntologySet s : nonSkylineOntologiesSets) {
				subspaces.addElement(s);
			}

			HashSet<OntologySet> selectedCandidates = new HashSet<OntologySet>();
			for (int[] pref : expandedPreferences) {
				selectedCandidates.addAll(subspaces.getSubspaceElements(pref));
			}

			List<OntologySet> selectedCandidatesList = new ArrayList<>(selectedCandidates);
			Collections.shuffle(selectedCandidatesList, new Random(System.currentTimeMillis()));
			// int sampleSize = 50;
			finalOntologies = new ArrayList<>(params.sampleSize);
			for (int i = 0; i < selectedCandidatesList.size() && i < params.sampleSize; ++i) {
				finalOntologies.add((Ontology) selectedCandidatesList.get(i).getOntologies().iterator().next());
			}

		}

		/**
		 * select the subset(s) of the optimal candidates to go on with
		 * depending on the given preferences
		 */
		// get the scores of the candidates separated by type
		log.info("get the scores of the candidates separated by type");
		ArrayList<double[]> scoresByType = new ArrayList<double[]>(); // the
																		// scores
																		// of
																		// all
																		// candidates
																		// separated
																		// by
																		// score
																		// type
		for (ScoreType type : params.scoreTypesToConsider) {
			scoresByType.add(new double[optimalCandidates.size()]);
		}

		for (int j = 0; j < optimalCandidates.size(); j++) {
			for (int i = 0; i < params.scoreTypesToConsider.length; i++) {
				scoresByType.get(i)[j] = optimalCandidates.get(j).getScore(params.scoreTypesToConsider[i]);
			}
		}
		log.info("get the scores of the candidates separated by type finished");

		// for each type, divide the scores into subspaces
		log.info("for each type, divide the scores into subspaces");
		ArrayList<double[]> subspaceBorders = getSubspaceBorders(optimalCandidates, params.scoreTypesToConsider,
				scoresByType);
		log.info("for each type, divide the scores into subspaces finished");

		// get subspace elements
		log.info("get subspaces elements");
		ElementMatrix subspaces = new ElementMatrix(subspaceBorders, params.scoreTypesToConsider);
		for (OntologySet s : optimalCandidates) {
			subspaces.addElement(s);
		}
		log.debug("subspaces: " + subspaces);

		HashSet<OntologySet> selectedCandidates = new HashSet<OntologySet>();
		for (int[] pref : expandedPreferences) {
			selectedCandidates.addAll(subspaces.getSubspaceElements(pref));
		}
		log.debug("selectedCandidates based on preferences: " + selectedCandidates);
		log.info("get subspaces elements finished");

		// if the preferences are too restrictive, take all candidates
		if (selectedCandidates.size() == 0) {
			log.info("preferences too restrictive");
			selectedCandidates = new HashSet<OntologySet>();
			selectedCandidates.addAll(optimalCandidates);
		}
		log.debug("selectedCandidates finally: " + selectedCandidates);

		/**
		 * TODO: take random samples, if necessary
		 */

		/**
		 * for each candidate determine the best combination with one additional
		 * ontology and add it to the set of candidates Note: we do not check
		 * whether a certain ontology is already contained in a given set, since
		 * adding such an ontology would result in a worse or equally high score
		 * and thus this combination will not be considered.
		 */
		log.info("determine new combinations");
		boolean addedNewCandidates = false;
		ArrayList<OntologySet> newCandidates = new ArrayList<OntologySet>();
		newCandidates.addAll(selectedCandidates);
		log.debug("the candidates considered for this round " + newCandidates);
		for (IOntologySet s : selectedCandidates) {
			log.info("determining new combinations for set s");

			// if we already encountered a set with coverage 1.0, we stop
			if (s.getScore(ScoreType.TERM_COVERAGE) == 1.0) {
				log.debug("We stop right now, since we already encountered a set with coverage 1.0!");
				return newCandidates;
			}

			log.debug("Determining new combinations for set s");
			// we just want to generate larger sets
			if (s.getOntologies().size() == currentElementsPerSet) {
				log.info("found set from last iteration");
				ArrayList<OntologySet> improvedCombinations = new ArrayList<OntologySet>();
				// for(IOntology o : ontologies) {
				for (IOntology o : finalOntologies) {
					boolean scoreImproved = false;
					for (ScoreType t : params.scoreTypesToConsider) {
						// get scorer and calculate new score
						double newScore = getVariableScorer(t).getScoreAdded(s, o, concepts);
						// check whether the score is improved by adding the
						// ontology
						if (s.getScore(t) < newScore) {
							scoreImproved = true;
							break;
						}
					}
					// if there is an improvement, save the new combination
					if (scoreImproved) {
						// copy ontology set
						OntologySet sNew = (OntologySet) s.copy();

						// add new ontology
						sNew.addOntology(o);

						// score
						// TODO: for now, the score is completely calculated
						// from scratch
						this.variableScoringChain.score(sNew, concepts);
						// this.constantScoringChain.score(sNew); //UNCOMMENT,
						// IF constant scores shall be considered again

						log.debug("\tFound new combination " + sNew);

						// add to the set of improved combinations, if the
						// combination has not been encountered yet and the
						// coverage has been improved
						log.debug("newCandidates: " + newCandidates);
						log.debug("!newCandidates.contains(sNew): " + !newCandidates.contains(sNew));
						log.debug("improvedCombinations: " + improvedCombinations);
						log.debug("!improvedCombinations.contains(sNew): " + !newCandidates.contains(sNew));
						if (!newCandidates.contains(sNew) && !improvedCombinations.contains(sNew)
								&& sNew.getScore(ScoreType.TERM_COVERAGE) > bestCoverage) {
							log.debug("\tNew combination not yet encountered");
							improvedCombinations.add(sNew);
						}
					} else {
						log.info("Score not improved ...");
					}
				}
				log.info("improved combinations determined: " + improvedCombinations);

				newCandidates.addAll(improvedCombinations);
				log.debug("all candidates: " + newCandidates);

				// determine new best coverage
				for (OntologySet c : improvedCombinations) {
					bestCoverage = Math.max(bestCoverage, c.getScore(ScoreType.TERM_COVERAGE));
				}

				if (improvedCombinations.size() != 0) {
					log.info("added a new candiate");
					addedNewCandidates = true;
				}
			}
		}

		/**
		 * terminate or make a recursive call
		 */
		// if the maximal number of ontologies/modules per set is reached or no
		// new candidates have been encountered return the optimal sets
		if (params.maxElementsPerSet <= (currentElementsPerSet + 1) || !addedNewCandidates) {
			List<OntologySet> result = SkylineSearch.getSkyline(newCandidates, params.scoreTypesToConsider);
			log.info("Returning result " + result);
			log.info("NUMBER OF ITERATIONS: " + currentElementsPerSet);
			return result;
		}

		// recursively call this method for the new candidate set
		log.info("Recursive call with candidates " + newCandidates);
		return determineOptimalSets(newCandidates, ontologies, params, expandedPreferences, currentElementsPerSet + 1,
				concepts, bestCoverage);

	}

	/**
	 * For each type, divides the scores of the candidates into subspaces.
	 * Returns an array for each dimension containing the borders of the
	 * subspaces w.r.t. this dimension. E.g. for 3 dimensions, we get { [d00
	 * left, d00 right, d01 -left, d01 - right, d02 - left, d02 - right], [d10
	 * left, d10 right, d11 -left, d11 - right, d12 - left, d12 - right], [d20
	 * left, d20 right, d21 -left, d21 - right, d22 - left, d22 - right] }.
	 * 
	 * @param candidates
	 * @param scoreTypes
	 * @param scoresByType
	 * @return the borders of the subspaces for each dimension
	 */
	private static ArrayList<double[]> getSubspaceBorders(List<OntologySet> candidates, ScoreType[] scoreTypes,
			ArrayList<double[]> scoresByType) {

		if (candidates != null && scoreTypes != null && scoresByType != null) {

			if (candidates.size() == 0)
				return null;

			if (scoresByType.size() != scoreTypes.length)
				return null;

			int[] subspaceIndices = new int[2 * scoreTypes.length];

			for (int i = 0; i < scoreTypes.length; i++) {
				if (i == scoreTypes.length - 1) {
					subspaceIndices[2 * i + 1] = Math.max(candidates.size() - 1, 0);
				} else {
					subspaceIndices[2 * i + 1] = (int) Math.max(
							Math.floor((double) (candidates.size() * (i + 1)) / (double) scoreTypes.length) - 1.0, 0.0);
					// subspaceIndices[2*i+1] = Math.max( Math.floorDiv(
					// candidates.size() * (i + 1) , scoreTypes.length ) - 1,
					// 0);
				}

				if (i == 0) {
					subspaceIndices[2 * i] = 0;
				} else {
					subspaceIndices[2 * i] = Math.min(
							Math.max(Math.min(subspaceIndices[2 * i - 1] + 1, candidates.size() - 1), 0),
							subspaceIndices[2 * i + 1]);
				}
			}

			ArrayList<double[]> subspacesByType = new ArrayList<double[]>(); // the
																				// subspaces
																				// of
																				// the
																				// candidate
																				// scores
																				// separated
																				// by
																				// score
																				// type
			for (int i = 0; i < scoresByType.size(); i++) {
				double[] scores = scoresByType.get(i);
				Arrays.sort(scores);
				double[] subspaces = new double[2 * scoreTypes.length];
				for (int j = 0; j < subspaceIndices.length; j++) {
					subspaces[j] = scores[subspaceIndices[j]];
				}
				subspacesByType.add(i, subspaces);
			}

			return subspacesByType;
		} else {
			return null;
		}
	}

	/**
	 * Given a preference representation, this method determines all equivalent
	 * preference representations. Example:
	 * 
	 * The preference representation [0,0,1] indicates that criterion 0 is
	 * equally important to criterion 1. Both are less important than criterion
	 * 2. So, position i in the array indicates the importance rank of criterion
	 * i.
	 * 
	 * For the input [0,0,1], the method would return {[0,0,1], [0,0,2],
	 * [1,1,2]}, which are all possible representations that express the same
	 * preference.
	 * 
	 * @param preferences
	 * @return
	 */
	private static ArrayList<int[]> expandPreferences(Integer[] preferences) {

		if (preferences != null) {

			// create a mapping indicating for each rank, the id of the criteria
			// it has been assigned to
			TreeMap<Integer, ArrayList<Integer>> idsSortedByRank = new TreeMap<Integer, ArrayList<Integer>>();
			for (int i = 0; i < preferences.length; i++) {
				ArrayList<Integer> ids;
				int rank = preferences[i];
				if (!idsSortedByRank.containsKey(rank)) {
					ids = new ArrayList<Integer>();
				} else {
					ids = idsSortedByRank.get(rank);
				}
				ids.add(i);
				idsSortedByRank.put(rank, ids);
			}

			// the number of different ranks within the input preference
			// representation
			int numberOfDifferentRanks = idsSortedByRank.size();

			// the number of different possible rank values for the criterion
			// with the minimal rank
			int rankRangeForMinimalRank = preferences.length - numberOfDifferentRanks + 1;

			// recursively determine possible rank values for each criterion
			ArrayList<int[]> expandedPreferences = new ArrayList<int[]>();
			expandedPreferences.add(new int[preferences.length]);
			return expand(expandedPreferences, idsSortedByRank, idsSortedByRank.firstEntry().getKey(), 0,
					rankRangeForMinimalRank);

		} else {
			return new ArrayList<int[]>();
		}

	}

	/**
	 * Recursively determines all possible ranks for each criterion.
	 * 
	 * @param preferences
	 *            the set of preferences for the criteria that have been already
	 *            ranked
	 * @param idsSortedByRank
	 *            a mapping indicating for each rank, the id of the criteria it
	 *            has been assigned to
	 * @param rank
	 *            the rank that is considered in this call of the method
	 * @param minimalRank
	 *            the minimal possible rank value for that rank
	 * @param rankRange
	 *            the number of different possible rank values for the given
	 *            rank
	 * @return
	 */
	private static ArrayList<int[]> expand(ArrayList<int[]> preferences,
			TreeMap<Integer, ArrayList<Integer>> idsSortedByRank, int rank, int minimalRank, int rankRange) {

		ArrayList<int[]> newPreferences = new ArrayList<int[]>();

		for (int i = minimalRank; i < (minimalRank + rankRange); i++) {
			for (int[] pref : preferences) {
				int[] newPref = (int[]) pref.clone();
				for (Integer id : idsSortedByRank.get(rank)) {
					newPref[id] = i;
				}
				ArrayList<int[]> preferencePrototype = new ArrayList<int[]>();
				preferencePrototype.add(newPref);
				if (idsSortedByRank.ceilingEntry(rank + 1) != null) {
					ArrayList<int[]> expandedPreferences = expand(preferencePrototype, idsSortedByRank,
							idsSortedByRank.ceilingEntry(rank + 1).getKey(), i + 1, minimalRank + rankRange - i);
					newPreferences.addAll(expandedPreferences);
				} else {
					newPreferences.addAll(preferencePrototype);
				}
			}
		}

		return newPreferences;
	}

	private IVariableOntologyScorer getVariableScorer(ScoreType scoreType) {

		if (scoreType.equals(ScoreType.TERM_COVERAGE)) {
			return this.classCoverageScorer;
		} else if (scoreType.equals(ScoreType.CLASS_OVERHEAD)) {
			return this.classOverheadScorer;
		} else if (scoreType.equals(ScoreType.CLASS_OVERLAP)) {
			return this.classOverlapScorer;
		}

		return null;

	}

	private String printList(ArrayList<double[]> list) {
		String str = "[";
		for (double[] el : list) {
			str += "[";
			for (double d : el) {
				str += d + " ";
			}
			str += "] ";
		}
		return str;
	}

	public static class SelectionParameters {

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
}
