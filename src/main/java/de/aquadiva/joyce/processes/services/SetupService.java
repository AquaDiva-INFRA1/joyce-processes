package de.aquadiva.joyce.processes.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
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
import de.aquadiva.joyce.base.services.IOWLParsingService;
import de.aquadiva.joyce.base.services.IOntologyDBService;
import de.aquadiva.joyce.base.services.IOntologyDownloadService;
import de.aquadiva.joyce.base.services.IOntologyFormatConversionService;
import de.aquadiva.joyce.base.services.IOntologyRepositoryStatsPrinterService;
import de.aquadiva.joyce.core.services.IOntologyModularizationService;
import de.aquadiva.joyce.util.OntologyModularizationException;

/**
 * Sets up the environment for ontology module selection.<br/>
 * <p>
 * Input:
 * <ul>
 * <li>A configuration file 'configuration.properties' with keys from
 * {@link JoyceSymbolConstants}.</li>
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
 * Please note that the mapping files/dictionaries are currently created by an
 * external process/database. This project does not contain classes to create
 * these files.
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
 * </ul>
 * </p>
 * 
 * <p>
 * Performs the following tasks:
 * <ol>
 * <li>Downloads original ontology files (mainly in OWL and OBO format) from
 * BioPortal IF the configuration property
 * {@link JoyceSymbolConstants#SETUP_DOWNLOAD_BIOPORTAL} is set to
 * <tt>true</tt>..</li>
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
 * <li>Filters the pre-created ontology class term dictionary at path given with
 * {@link JoyceSymbolConstants#DICT_FULL_PATH} to a smaller dictionary, only
 * including classes actually seen during the setup process, and stores it to
 * the path given through {@link JoyceSymbolConstants#DICT_FILTERED_PATH}.</li>
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
	private boolean doDownload;
	private File mixedClassOntologyMappingFile;
	private String[] requestedAcronyms;
	private IOWLParsingService owlParsingService;
	private File errorFile;
	private String dictFullPath;
	private String dictFilteredPath;
	private boolean doConvert;
	private IMetaConceptService metaConceptService;
	private IOntologyRepositoryStatsPrinterService ontologyRepositoryStatsPrinterService;
	private boolean doImport;
	private ExecutorService executorService;

	public SetupService(Logger log, IOntologyDownloadService downloadService,
			IOntologyFormatConversionService formatConversionService, IOntologyDBService dbService,
			IOntologyModularizationService modularizationService, IOWLParsingService owlParsingService,
			IMetaConceptService metaConceptService, @ConstantScoringChain IConstantOntologyScorer constantScoringChain,
			@Symbol(JoyceSymbolConstants.SETUP_DOWNLOAD_BIOPORTAL) boolean doDownload,
			@Symbol(JoyceSymbolConstants.SETUP_CONVERT_TO_OWL) boolean doConvert,
			@Symbol(JoyceSymbolConstants.SETUP_IMPORT_ONTOLOGIES) boolean doImport,
			@Symbol(JoyceSymbolConstants.SETUP_ERROR_FILE) File errorFile,
			@Symbol(JoyceSymbolConstants.MIXEDCLASS_ONTOLOGY_MAPPING) String classOntologyMappingFile,
			@Symbol(JoyceSymbolConstants.ONTOLOGIES_FOR_DOWNLOAD) String requestedAcronyms,
			@Symbol(JoyceSymbolConstants.DICT_FULL_PATH) String dictFullPath,
			@Symbol(JoyceSymbolConstants.DICT_FILTERED_PATH) String dictFilteredPath,
			IOntologyRepositoryStatsPrinterService ontologyRepositoryStatsPrinterService,
			ExecutorService executorService) {
		this.log = log;
		this.downloadService = downloadService;
		this.formatConversionService = formatConversionService;
		this.dbService = dbService;
		this.modularizationService = modularizationService;
		this.owlParsingService = owlParsingService;
		this.metaConceptService = metaConceptService;
		this.constantScoringChain = constantScoringChain;
		this.doDownload = doDownload;
		this.doConvert = doConvert;
		this.doImport = doImport;
		this.errorFile = errorFile;
		this.dictFullPath = dictFullPath;
		this.dictFilteredPath = dictFilteredPath;
		this.ontologyRepositoryStatsPrinterService = ontologyRepositoryStatsPrinterService;
		this.executorService = executorService;
		this.mixedClassOntologyMappingFile = classOntologyMappingFile.endsWith(".gz")
				? new File(classOntologyMappingFile) : new File(classOntologyMappingFile + ".gz");
		if (!StringUtils.isBlank(requestedAcronyms))
			this.requestedAcronyms = requestedAcronyms.split(",");
		else
			this.requestedAcronyms = new String[0];
	}

	@Override
	public void setupSelectionSystem() throws IOException {
		if (errorFile.exists())
			errorFile.delete();

		if (doDownload) {
			log.info("Downloading ontologies from BioPortal...");
			downloadService.downloadBioPortalOntologiesToConfigDirs(requestedAcronyms);
		} else {
			log.info(
					"Ontology downloading is switched off, system will be set up using existing ontology files on disc.");
		}
		if (doConvert) {
			log.info("Converting downladed ontologies to OWL format where possible.");
			formatConversionService.convertFromDownloadDirToOwlDir();
		}
		List<Ontology> ontologies;
		if (doImport) {
			log.info("Importing downloaded ontologies into the database...");
			ontologies = dbService.importBioPortalOntologiesFromConfigDirs();
		} else {
			ontologies = dbService.getAllOntologies();
		}
		log.debug(
				"Reading the mapping that maps class IRIs to meta class IDs so we can set meta classes to ontologies and modules");
		Multimap<String, String> mixedClassToModuleMapping = HashMultimap.create();

		Map<String, Future<List<OntologyModule>>> ontologyModules = new HashMap<>();
		// unfortunately, it seems the modularization is not thread safe...
		// thus, the above map is not actually used
//		log.info("Modularizing ontologies concurrently");
//		for (Ontology o : ontologies) {
//			ModularizationWorker worker = new ModularizationWorker(o);
//			Future<List<OntologyModule>> modulesFuture = executorService.submit(worker);
//			ontologyModules.put(o.getId(), modulesFuture);
//		}

		SetupStats stats = new SetupStats();
		// For ontology scoring
		for (Ontology o : ontologies) {
			processOntology(ontologies, mixedClassToModuleMapping, stats, o, ontologyModules);
		}
		log.info("{} ontologies were successfully processed.", stats.successcount);
		writeMixedClassToModuleMappingFile(mixedClassToModuleMapping);
		log.info("Filtering full dictionary at {} to smaller dictionary at {}.", dictFullPath, dictFilteredPath);
		filterConceptDictionary(mixedClassToModuleMapping.keySet());
		log.info(getClass().getSimpleName() + " finished processing.");
		ontologyRepositoryStatsPrinterService.printOntologyRepositoryStats(new File("ontologyrepositorystats.csv"));

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
			} catch (Throwable t) {
				o.setHasParsingError(true);
				throw t;
			}
			o.setHasParsingError(false);
			log.debug("Retrieving class IDs of ontology {}.", o.getId());
			Set<String> classIdsForOntology = metaConceptService.getMixedClassIdsForOntology(o);
			o.setClassIds(classIdsForOntology);
			log.debug("Running constant scorers on ontology {}", o.getId());
			constantScoringChain.score(o);
			log.debug("Adding ontology classes for ontology {} to class-ontology mapping", o.getId());
			addToMixedClassModuleMapping(classIdsForOntology, o, mixedClassToModuleMapping);

			log.debug("Modularizing ontology {}", o.getId());
			List<OntologyModule> modules = null;
			try {
				 modules = modularizationService.modularize(o);
			}catch (OntologyModularizationException e) {
				o.setHasModularizationError(true);
				throw e;
			}
//				modules = ontologyModules.get(o.getId()).get(120, TimeUnit.MINUTES);
//			} catch (InterruptedException | ExecutionException e) {
//				o.setHasModularizationError(true);
//				log.error("Exception happened during modularization: " + e.getMessage(), e);
//			} catch (TimeoutException e) {
//				log.debug("Modularization of ontology {} timed out, no modules are created for this ontology.",
//						o.getId());
//				ontologyModules.get(o.getId()).cancel(true);
//				o.setHasModularizationError(true);
//			}
			o.setHasModularizationError(false);
			if (modules == null) {
				modules = Collections.emptyList();
			}
			for (OntologyModule om : modules) {
				log.debug("Retrieving class IDs of module {}", om.getId());
				Set<String> mixedClassIdsForModule = metaConceptService.getMixedClassIdsForOntology(om);
				om.setClassIds(mixedClassIdsForModule);
				log.debug("Adding classes of module {} to class-ontology mapping", om.getId());
				addToMixedClassModuleMapping(mixedClassIdsForModule, om, mixedClassToModuleMapping);
				log.debug("Running constant scorers on module {}", om.getId());
				constantScoringChain.score(om);
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

	private void filterConceptDictionary(Set<String> actualKnownClasses) {
		File dictFilteredFile = new File(dictFilteredPath);
		if (dictFilteredFile.exists())
			dictFilteredFile.delete();
		File dir = dictFilteredFile.getParentFile();
		if (!dir.exists())
			dir.mkdirs();
		try (InputStream is = new GZIPInputStream(new FileInputStream(dictFullPath))) {
			try (OutputStream os = new GZIPOutputStream(new FileOutputStream(dictFilteredPath))) {
				LineIterator lineIterator = IOUtils.lineIterator(is, "UTF-8");
				while (lineIterator.hasNext()) {
					String line = lineIterator.nextLine();
					String classIri = line.split("\\t")[1];
					if (actualKnownClasses.contains(classIri))
						IOUtils.write(line + "\n", os, "UTF-8");
				}
			} catch (IOException e) {
				log.error(
						"Could not write filtered concept recognition dictionary line. Either the full dictionary could be accessed or the writing failed.");
				e.printStackTrace();
			}
		} catch (IOException e) {
			log.error(
					"Could not create the filtered concept recognition dictionary because the full dictionary could not be read from configured path {}: {}",
					dictFullPath, e.getMessage());
			e.printStackTrace();
		}
	}

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