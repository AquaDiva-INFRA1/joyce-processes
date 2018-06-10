package de.aquadiva.joyce.processes.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.slf4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import de.aquadiva.joyce.JoyceSymbolConstants;
import de.aquadiva.joyce.base.data.Ontology;
import de.aquadiva.joyce.base.data.OntologyModule;
import de.aquadiva.joyce.base.services.IConstantOntologyScorer;
import de.aquadiva.joyce.base.services.IMetaConceptService;
import de.aquadiva.joyce.base.services.INeo4jService;
import de.aquadiva.joyce.base.services.IOWLParsingService;
import de.aquadiva.joyce.base.services.IOntologyDBService;
import de.aquadiva.joyce.base.services.IOntologyDownloadService;
import de.aquadiva.joyce.base.services.IOntologyFormatConversionService;
import de.aquadiva.joyce.base.services.IOntologyNameExtractionService;
import de.aquadiva.joyce.base.services.IOntologyRepositoryStatsPrinterService;
import de.aquadiva.joyce.base.util.JoyceException;
import de.aquadiva.joyce.base.util.MetaConceptMapCreationException;
import de.aquadiva.joyce.core.services.IOntologyModularizationService;
import de.aquadiva.joyce.util.OntologyModularizationException;
import de.julielab.java.utilities.FileUtilities;
import de.julielab.jcore.ae.lingpipegazetteer.chunking.ChunkerProviderImplAlt;

/**
 * Sets up the environment for ontology module selection.<br/>
 * <p>
 * Input: >A configuration file 'configuration.properties' with keys from
 * {@link JoyceSymbolConstants}.</li>
 * </p>
 * <p>
 * Output:
 * <ul>
 * <li>An SQL database (possibly just filling an existing database) with all
 * ontologies and their cluster-based modules.</li>
 * <li>A file that maps ontology class IRIs/meta class IDs to the module and
 * ontology IDs, written to the file defined with
 * {@link JoyceSymbolConstants#MIXEDCLASS_ONTOLOGY_MAPPING}.
 * <li>A dictionary mapping class names and synonyms to class IRIs or meta-class
 * IDs for the automatic detection of classes in user input. Written to a file
 * given by {@link JoyceSymbolConstants#DICT_FILTERED_PATH}</li>
 * <li>A term-to-classIRI/metaClassId dictionary mapping all names and synonyms
 * of classes to the respective class IRIs or meta class ID. The location of
 * this dictionary must be configured in the configuration file with key
 * {@link JoyceSymbolConstants#DICT_FULL_PATH}.</li>
 * <li>A meta-concept-mapping-file that maps artificial meta-concept ID to lists
 * of class IRIs that represent equivalent classes. This mapping can have any
 * source, typically the BioPortal mappings are used. The file location is
 * configured with property
 * {@link JoyceSymbolConstants#META_CLASS_TO_IRI_CLASS_MAPPING}</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Performs the following tasks:
 * <ol>
 * <li>Downloads original ontology files (mainly in OWL and OBO format) from
 * BioPortal IF the configuration property
 * {@link JoyceSymbolConstants#SETUP_DOWNLOAD_BIOPORTAL_ONTOLOGIES} is set to
 * <li>Downloads ontology class mappings IF the configuration property
 * {@link JoyceSymbolConstants#SETUP_DOWNLOAD_BIOPORTAL_MAPPINGS} is set to
 * <tt>true</tt>.</li>
 * <li>Converts OBO ontologies to OWL format IF the configuration property
 * {@link JoyceSymbolConstants#SETUP_CONVERT_TO_OWL} is set to
 * <tt>true</tt>.</li>
 * <li>Imports all ontologies in OWL format (also converted ones) into the
 * database.</li>
 * <li>Performs a static cluster-based ontology partitioning on each ontology,
 * creating fixed modules.</li>
 * <li>Stores these modules in the database.</li>
 * <li>Creates a mapping from all module/ontology class IRIs/meta class IDs to
 * the module or ontology ID.Note that the database schema allows for a module
 * to retrieve its source ontology, thus it is always possible filter full
 * ontologies or modules, respectively. The mapping is stored to the file given
 * with configuration property
 * {@link JoyceSymbolConstants#MIXEDCLASS_ONTOLOGY_MAPPING}</li>
 * <li>Extracts all names from the downloaded ontologies</li>
 * <li>Creates an embedded Neo4j graph database from the extracted names and
 * adds the mappings</li>
 * <li>Export a concept name dictionary and the meta class mapping file from the
 * Neo4j database.</li>
 * </ol>
 * </p>
 * 
 * @author faessler
 *
 */
public class SetupService implements ISetupService {

	private Logger log;
	private IOntologyDownloadService downloadService;
	private IOntologyModularizationService modularizationService;
	private IOntologyFormatConversionService formatConversionService;
	private IOntologyDBService dbService;
	private IConstantOntologyScorer constantScoringChain;
	private boolean downloadOntologies;
	private File mixedClassOntologyMappingFile;
	private String[] requestedOntologyAcronyms;
	private String[] requestedMappingAcronyms;
	private IOWLParsingService owlParsingService;
	private File errorFile;
	private String dictPath;
	private boolean doConvert;
	private IMetaConceptService metaConceptService;
	private IOntologyRepositoryStatsPrinterService ontologyRepositoryStatsPrinterService;
	private boolean doImport;
	private ExecutorService executorService;
	private boolean downloadMappings;
	private IOntologyNameExtractionService classNameExtractionService;
	private INeo4jService neo4jService;
	/**
	 * We want to get an URL not because we need it in this code to be a URL but
	 * because UIMA requires it later when using the Gazetteer.
	 */
	private String gazetteerConfigUrl;

	public SetupService(Logger log, IOntologyDownloadService downloadService,
			IOntologyFormatConversionService formatConversionService, IOntologyDBService dbService,
			IOntologyModularizationService modularizationService, IOWLParsingService owlParsingService,
			IOntologyNameExtractionService classNameExtractionService, IMetaConceptService metaConceptService,
			INeo4jService neo4jService, @ConstantScoringChain IConstantOntologyScorer constantScoringChain,
			@Symbol(JoyceSymbolConstants.SETUP_DOWNLOAD_BIOPORTAL_ONTOLOGIES) boolean downloadOntologies,
			@Symbol(JoyceSymbolConstants.SETUP_DOWNLOAD_BIOPORTAL_MAPPINGS) boolean downloadMappings,
			@Symbol(JoyceSymbolConstants.SETUP_CONVERT_TO_OWL) boolean doConvert,
			@Symbol(JoyceSymbolConstants.SETUP_IMPORT_ONTOLOGIES) boolean doImport,
			@Symbol(JoyceSymbolConstants.SETUP_ERROR_FILE) File errorFile,
			@Symbol(JoyceSymbolConstants.MIXEDCLASS_ONTOLOGY_MAPPING) String classOntologyMappingFile,
			@Symbol(JoyceSymbolConstants.ONTOLOGIES_FOR_DOWNLOAD) String requestedOntologyAcronyms,
			@Symbol(JoyceSymbolConstants.MAPPINGS_FOR_DOWNLOAD) String requestedMappingAcronyms,
			@Symbol(JoyceSymbolConstants.CONCEPT_TERM_DICTIONARY) String dictPath,
			@Symbol(JoyceSymbolConstants.GAZETTEER_CONFIG) String gazetteerConfigUrl,
			IOntologyRepositoryStatsPrinterService ontologyRepositoryStatsPrinterService,
			ExecutorService executorService) throws JoyceException {
		this.log = log;
		this.downloadService = downloadService;
		this.formatConversionService = formatConversionService;
		this.dbService = dbService;
		this.modularizationService = modularizationService;
		this.owlParsingService = owlParsingService;
		this.classNameExtractionService = classNameExtractionService;
		this.metaConceptService = metaConceptService;
		this.neo4jService = neo4jService;
		this.constantScoringChain = constantScoringChain;
		this.downloadOntologies = downloadOntologies;
		this.downloadMappings = downloadMappings;
		this.doConvert = doConvert;
		this.doImport = doImport;
		this.errorFile = errorFile;
		this.dictPath = dictPath;
		this.gazetteerConfigUrl = gazetteerConfigUrl;
		this.ontologyRepositoryStatsPrinterService = ontologyRepositoryStatsPrinterService;
		this.executorService = executorService;
		this.mixedClassOntologyMappingFile = classOntologyMappingFile.endsWith(".gz")
				? new File(classOntologyMappingFile)
				: new File(classOntologyMappingFile + ".gz");
		if (!StringUtils.isBlank(requestedOntologyAcronyms))
			this.requestedOntologyAcronyms = requestedOntologyAcronyms.split(",");
		else
			this.requestedOntologyAcronyms = new String[0];

		if (!StringUtils.isBlank(requestedMappingAcronyms))
			this.requestedMappingAcronyms = requestedMappingAcronyms.split(",");
		else
			this.requestedMappingAcronyms = new String[0];
	}

	@Override
	public void setupSelectionSystem() throws IOException, JoyceException {
		if (errorFile.exists())
			errorFile.delete();

		prepareForModularization();
		modularize();
	}

	@Override
	public void setupSelectionSystem(boolean premodularization) throws IOException, JoyceException {
		if (errorFile.exists())
			errorFile.delete();

		if (premodularization) {
			prepareForModularization();
		} else {
			modularize();
		}
	}

	private void prepareForModularization() throws MetaConceptMapCreationException {
		if (downloadOntologies) {
			log.info("Downloading ontologies from BioPortal...");
			downloadService.downloadBioPortalOntologiesToConfigDirs(requestedOntologyAcronyms);
		} else {
			log.info("Ontology download is switched off, system will be set up using existing ontology files on disc.");
		}
		if (downloadMappings) {
			log.info("Downloading mappings from BioPortal...");
			downloadService.downloadBioPortalMappingsToConfigDirs(requestedMappingAcronyms);
		} else {
			log.info("Mapping download is switched off, system will be set up using existing mapping files on disc.");
		}
		if (doConvert) {
			log.info("Converting downladed ontologies to OWL format where possible.");
			formatConversionService.convertFromDownloadDirToOwlDir();
		}
		log.info("Extracting ontology class names...");
		classNameExtractionService.extractNames();
		List<Ontology> ontologies;
		if (doImport) {
			log.info("Importing downloaded ontologies into the database...");
			ontologies = dbService.importBioPortalOntologiesFromConfigDirs();
		} else {
			ontologies = dbService.getAllOntologies();
		}
		log.debug("There are {} ontologies in the database that will now be prepared for selection requirements.",
				ontologies.size());

		neo4jService.insertClasses();
		neo4jService.insertMappings();
		neo4jService.createMetaClassesInDatabase();
		neo4jService.exportMetaClassToIriMappingFile();
		neo4jService.exportLingpipeDictionary();
	}
	
	private void modularize() throws IOException {
		List<Ontology> ontologies;
		ontologies = dbService.getAllOntologies();
		metaConceptService.loadMetaClassIriMaps();

		// unfortunately, it seems the modularization is not thread safe...
		// thus, the above map is not actually used
		// log.info("Modularizing ontologies concurrently");
		// for (Ontology o : ontologies) {
		// ModularizationWorker worker = new ModularizationWorker(o);
		// Future<List<OntologyModule>> modulesFuture =
		// executorService.submit(worker);
		// ontologyModules.put(o.getId(), modulesFuture);
		// }

		Multimap<String, String> mixedClassToModuleMapping = HashMultimap.create();
		Map<String, Future<List<OntologyModule>>> ontologyModules = new HashMap<>();
		SetupStats stats = new SetupStats();
		// For ontology scoring
		for (Ontology o : ontologies) {
			processOntology(ontologies, mixedClassToModuleMapping, stats, o, ontologyModules);
		}
		log.info("{} ontologies were successfully processed.", stats.successcount);
		writeMixedClassToModuleMappingFile(mixedClassToModuleMapping);
		log.info(
				"Writing concept gazetteer configuration file, used to recognize concept classes in the input text for ontology module selection, to \"{}\"",
				gazetteerConfigUrl);
		writeConceptGazetteerConfigurationFile(dictPath);
		log.info(getClass().getSimpleName() + " finished processing.");
		ontologyRepositoryStatsPrinterService.printOntologyRepositoryStats(new File("ontologyrepositorystats.csv"));
	}

	private void writeConceptGazetteerConfigurationFile(String dictPath) throws IOException {
		Properties p = new Properties();
		p.setProperty(ChunkerProviderImplAlt.PARAM_DICTIONARY_FILE, dictPath);
		p.setProperty(ChunkerProviderImplAlt.PARAM_STOPWORD_FILE, "/general_english_words");
		p.setProperty(ChunkerProviderImplAlt.PARAM_CASE_SENSITIVE, "false");
		p.setProperty(ChunkerProviderImplAlt.PARAM_MAKE_VARIANTS, "false");
		p.setProperty(ChunkerProviderImplAlt.PARAM_NORMALIZE_TEXT, "true");
		p.setProperty(ChunkerProviderImplAlt.PARAM_TRANSLITERATE_TEXT, "true");
		p.setProperty(ChunkerProviderImplAlt.PARAM_USE_APPROXIMATE_MATCHING, "true");
		try (Writer w = FileUtilities.getWriterToFile(new File(gazetteerConfigUrl.replace("file:", "")))) {
			p.store(w, "This configuration file was automatically created by " + getClass().getName() + " on "
					+ new Date());
		}
	}

	private class ModularizationWorker implements Callable<List<OntologyModule>> {
		private Ontology o;

		public ModularizationWorker(Ontology o) {
			this.o = o;
		}

		@Override
		public List<OntologyModule> call() throws Exception {
			return modularizationService.modularize(o);
		}

	}

	private void processOntology(List<Ontology> ontologies, Multimap<String, String> mixedClassToModuleMapping,
			SetupStats stats, Ontology o, Map<String, Future<List<OntologyModule>>> ontologyModules) {
		dbService.beginTransaction();
		log.debug("Processing {}. ontology of {} (ID: {})",
				new Object[] { ++stats.progress, ontologies.size(), o.getId() });
		try {
			log.debug("Parsing ontology {}", o.getId());
			try {
				owlParsingService.parse(o);
				owlParsingService.clearOntologies();
			} catch (IOException e) {
				o.setHasParsingError(true);
				throw e;
			}
			o.setHasParsingError(false);
			log.debug("Retrieving class IDs of ontology {}.", o.getId());
			Set<String> classIdsForOntology = metaConceptService.getMixedClassIdsForOntology(o);
			o.setClassIds(classIdsForOntology);
			log.debug("Running constant scorers on ontology {}", o.getId());
			constantScoringChain.score(o);
			log.debug("Adding ontology classes for ontology {} to class-ontology mapping", o.getId());
			addToMixedClassModuleMapping(classIdsForOntology, o, mixedClassToModuleMapping);

			List<OntologyModule> modules;
			if (o.getModules() == null || o.getModules().isEmpty()) {
				log.debug("Modularizing ontology {}", o.getId());
				try {
					modules = modularizationService.modularize(o);
				} catch (OntologyModularizationException e) {
					o.setHasModularizationError(true);
					throw e;
				}
				// modules = ontologyModules.get(o.getId()).get(120,
				// TimeUnit.MINUTES);
				// } catch (InterruptedException | ExecutionException e) {
				// o.setHasModularizationError(true);
				// log.error("Exception happened during modularization: " +
				// e.getMessage(), e);
				// } catch (TimeoutException e) {
				// log.debug("Modularization of ontology {} timed out, no modules
				// are created for this ontology.",
				// o.getId());
				// ontologyModules.get(o.getId()).cancel(true);
				// o.setHasModularizationError(true);
				// }
				o.setHasModularizationError(false);
				if (modules == null) {
					modules = Collections.emptyList();
				}
			}
			modules = o.getModules();
			if (modules != null) {
				for (OntologyModule om : modules) {
					log.debug("Retrieving class IDs of module {}", om.getId());
					if (om.getOwlOntology() == null)
						owlParsingService.parse(om);
					Set<String> mixedClassIdsForModule = metaConceptService.getMixedClassIdsForOntology(om);
					om.setClassIds(mixedClassIdsForModule);
					log.debug("Adding classes of module {} to class-ontology mapping", om.getId());
					addToMixedClassModuleMapping(mixedClassIdsForModule, om, mixedClassToModuleMapping);
					log.debug("Running constant scorers on module {}", om.getId());
					constantScoringChain.score(om);
				}
			}
			dbService.storeOntologies(modules, false);
			stats.successcount++;
			log.debug("Writing ontology scores back to database");
			// for ontology scoring
			dbService.commit();
		} catch (Exception | Error e) {
			// Note that we even catch "Error" (the Throwable that should
			// not be catched) because it may be thrown by the OWLApi (OBO
			// parser) when trying to parse OBO (which actually happened
			// because it was not OWL and OBO was guessed; then the error
			// was thrown because the characters of the ontology were
			// complete rubbish)
			log.error("{} occurred while processing ontology {}: ",
					new Object[] { e.getClass().getSimpleName(), o.getId(), e });
			log.error("Stack trace: ", e);
			try {
				FileUtils.write(errorFile, e.getClass().getSimpleName() + " occurred while processing ontology "
						+ o.getId() + ": " + e.getMessage() + "\n", "UTF-8", true);
				FileUtils.write(errorFile, "Full stack trace: " + Arrays.toString(e.getStackTrace()) + "\n", "UTF-8",
						true);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	// private void filterConceptDictionary(Set<String> actualKnownClasses) {
	// File dictFilteredFile = new File(dictFilteredPath);
	// if (dictFilteredFile.exists())
	// dictFilteredFile.delete();
	// File dir = dictFilteredFile.getParentFile();
	// if (!dir.exists())
	// dir.mkdirs();
	// try (InputStream is = new GZIPInputStream(new FileInputStream(dictFullPath)))
	// {
	// try (OutputStream os = new GZIPOutputStream(new
	// FileOutputStream(dictFilteredPath))) {
	// LineIterator lineIterator = IOUtils.lineIterator(is, "UTF-8");
	// while (lineIterator.hasNext()) {
	// String line = lineIterator.nextLine();
	// String classIri = line.split("\\t")[1];
	// if (actualKnownClasses.contains(classIri))
	// IOUtils.write(line + "\n", os, "UTF-8");
	// }
	// } catch (IOException e) {
	// log.error(
	// "Could not write filtered concept recognition dictionary line. Either the
	// full dictionary could be accessed or the writing failed.");
	// e.printStackTrace();
	// }
	// } catch (IOException e) {
	// log.error(
	// "Could not create the filtered concept recognition dictionary because the
	// full dictionary could not be read from configured path {}: {}",
	// dictFullPath, e.getMessage());
	// e.printStackTrace();
	// }
	// }

	private void writeMixedClassToModuleMappingFile(Multimap<String, String> classToModuleMapping) throws IOException {
		File dir = mixedClassOntologyMappingFile.getParentFile();
		if (!dir.exists())
			dir.mkdirs();
		try (GZIPOutputStream os = new GZIPOutputStream(new FileOutputStream(mixedClassOntologyMappingFile))) {
			for (String classId : classToModuleMapping.keySet()) {
				Collection<String> moduleIds = classToModuleMapping.get(classId);
				IOUtils.write(classId + "\t" + StringUtils.join(moduleIds, "||") + "\n", os, "UTF-8");
			}
		}
	}

	private void addToMixedClassModuleMapping(Set<String> classIdsForModule, Ontology o,
			Multimap<String, String> classToModuleMapping) {
		for (String classId : classIdsForModule)
			classToModuleMapping.put(classId, o.getId());
	}

	private class SetupStats {
		int progress = 0;
		int successcount = 0;
	}

}
