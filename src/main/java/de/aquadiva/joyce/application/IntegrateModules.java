package de.aquadiva.joyce.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;
import org.apache.tapestry5.ioc.services.SymbolSource;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import de.aquadiva.joyce.JoyceSymbolConstants;
import de.aquadiva.joyce.base.data.IOntology;
import de.aquadiva.joyce.base.data.Ontology;
import de.aquadiva.joyce.base.data.OntologyModule;
import de.aquadiva.joyce.base.services.IOWLParsingService;
import de.aquadiva.joyce.base.services.IOntologyDBService;
import de.aquadiva.joyce.processes.services.JoyceProcessesModule;

public class IntegrateModules {

	private static final Logger log = LoggerFactory
			.getLogger(IntegrateModules.class);

	private static File classOntologyMappingFile;
	private static String dictFilteredPath;
	private static String dictFullPath;

	public static void main(String[] args) throws Exception, IOException {
		if (args.length != 1) {
			System.out.println("Usage: "
					+ IntegrateModules.class.getSimpleName()
					+ " <directory with modules>");
			System.exit(1);
		}

		File moduleDir = new File(args[0]);

		Registry registry = null;
		try {
			List<Ontology> ontologies;
			Map<String, Ontology> id2onto;
			List<String> ontologyIds;
			IOWLParsingService parsingService;

			registry = RegistryBuilder
					.buildAndStartupRegistry(JoyceProcessesModule.class);
			parsingService = registry.getService(IOWLParsingService.class);
			SymbolSource symbolSource = registry.getService(SymbolSource.class);
			dictFilteredPath = symbolSource.valueForSymbol(JoyceSymbolConstants.DICT_FILTERED_PATH);
			dictFullPath = symbolSource.valueForSymbol(JoyceSymbolConstants.DICT_FULL_PATH);
			log.debug("Reading the mapping that maps class IRIs to meta class IDs so we can set meta classes to ontologies and modules");
			Map<String, String> metaConceptMapping = readMetaConceptMapping(new File(
					symbolSource
							.valueForSymbol(JoyceSymbolConstants.META_CLASS_TO_IRI_CLASS_MAPPING)));
			classOntologyMappingFile = new File(
					symbolSource
							.valueForSymbol(JoyceSymbolConstants.MIXEDCLASS_ONTOLOGY_MAPPING));

			log.info("Getting all ontologies from database in order to match their IDs to the module file names.");
			IOntologyDBService dbService = registry
					.getService(IOntologyDBService.class);
			ontologies = dbService.getAllOntologies();
			ontologyIds = new ArrayList<>(ontologies.size());
			for (Ontology o : ontologies)
				ontologyIds.add(o.getId().toLowerCase());
			id2onto = new HashMap<>();
			log.info("Assigning source ontologies to modules in directory {}",
					moduleDir);
			for (Ontology o : ontologies)
				id2onto.put(o.getId().toLowerCase(), o);

			File[] moduleFiles = moduleDir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".owl");
				}
			});

			dbService.beginTransaction();
			List<OntologyModule> modules = new ArrayList<>();
			for (int i = 0; i < moduleFiles.length; i++) {
				File moduleFile = moduleFiles[i];
				String filename = moduleFile.getName();
				Set<String> ontoIdsForModule = new HashSet<>();
				for (String ontoId : ontologyIds) {
					if (filename.toLowerCase().contains(ontoId))
						ontoIdsForModule.add(ontoId);
				}

				int maxIdlength = 0;
				Iterator<String> iterator = ontoIdsForModule.iterator();
				while (iterator.hasNext()) {
					String ontoIdForModule = (String) iterator.next();
					if (ontoIdForModule.length() > maxIdlength)
						maxIdlength = ontoIdForModule.length();
				}
				iterator = ontoIdsForModule.iterator();
				while (iterator.hasNext()) {
					String ontoIdForModule = (String) iterator.next();
					if (ontoIdForModule.length() < maxIdlength)
						iterator.remove();
				}
				if (ontoIdsForModule.size() > 1)
					throw new IllegalStateException(
							"Ontology module "
									+ filename
									+ " has multiple possible source ontologies by their ID: "
									+ ontoIdsForModule);
				if (ontoIdsForModule.size() == 0)
					throw new IllegalStateException(
							"For ontology module "
									+ filename
									+ " no source ontology could be found in the ontology database.");

				Ontology sourceOnto = id2onto.get(ontoIdsForModule.iterator()
						.next());
				byte[] moduleData = null;
				try (InputStream is = new FileInputStream(moduleFile)) {
					moduleData = IOUtils.toByteArray(is);
				}
				OntologyModule m = sourceOnto
						.createStaticModule(filename, moduleData);
				if (m == null) {
					log.debug(
							"Module {} of ontology {} is already present and is omitted from this process.",
							filename, sourceOnto.getId());
					continue;
				}
				modules.add(m);
				log.debug("Retrieving class IDs of module {}", m.getId());
				OWLOntology owlOntology = parsingService.parse(m);
				m.setOwlOntology(owlOntology);
				Set<String> classIdsForModule = getClassIdsForOntology(m,
						metaConceptMapping);
				m.setClassIds(classIdsForModule);
				sourceOnto.setHasModularizationError(false);
			}
			dbService.storeOntologies(modules, false);
			log.info("Committing new modules and source ontology changes to the database.");
			dbService.commit();

			log.info("Retrieving all modules and all ontologies with modularization errors from database to create the class to ontology mapping file.");
			List<OntologyModule> allModules = dbService.getAllOntologyModules();
			List<Ontology> allOntologiesWithModularizationError = dbService
					.getAllOntologiesWithModularizationError();
			List<IOntology> ontologiesForClassOntologyMapping = new ArrayList<>(
					allModules.size()
							+ allOntologiesWithModularizationError.size());
			log.debug("Got {} ontologies with modularization error:",
					allOntologiesWithModularizationError.size());
			for (Ontology o : allOntologiesWithModularizationError)
				log.debug(o.getId());
			ontologiesForClassOntologyMapping.addAll(allModules);
			ontologiesForClassOntologyMapping
					.addAll(allOntologiesWithModularizationError);

			Multimap<String, String> classToModuleMapping = HashMultimap
					.create();

			log.info("Creating class to module mapping completely fresh from database information...");
			for (IOntology o : ontologiesForClassOntologyMapping) {
				for (String classId : o.getClassIds())
					classToModuleMapping.put(classId, o.getId());
			}

			log.info("Writing class to ontology mapping to "
					+ classOntologyMappingFile);
			writeClassToModuleMappingFile(classToModuleMapping);
			log.info(
					"Filtering full dictionary at {} to smaller dictionary at {}.",
					dictFullPath, dictFilteredPath);
			filterConceptDictionary(classToModuleMapping.keySet());

		} finally {
			if (null != registry) {
				// TODO: That's not good, all the services should be registered
				// to the registry's shutdown hub and execute their own
				// shutdowns automatically
				IOntologyDBService dbservice = registry
						.getService(IOntologyDBService.class);
				dbservice.shutdown();
				ExecutorService executorService = registry
						.getService(ExecutorService.class);
				List<Runnable> remainingThreads = executorService.shutdownNow();
				if (remainingThreads.size() != 0)
					System.out.println("Wait for " + remainingThreads.size()
							+ " to end.");
				try {
					executorService.awaitTermination(10, TimeUnit.MINUTES);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				registry.shutdown();
			}
		}
		System.out.println("Integration is finished.");
	}

	// TODO Duplicate to SetupService
	private static Set<String> getClassIdsForOntology(Ontology o,
			Map<String, String> metaConceptMapping) {
		OWLOntology owl = o.getOwlOntology();
		Set<String> classesInModule = new HashSet<>();
		for (OWLClass c : owl.getClassesInSignature()) {
			// we are only interested in asserted classes for the time being
			if (c.isAnonymous())
				continue;
			String iri = c.getIRI().toString();
			String metaConceptId = metaConceptMapping.get(iri);
			if (null != metaConceptId)
				classesInModule.add(metaConceptId);
			else
				classesInModule.add(iri);
		}
		return classesInModule;
	}

	/**
	 * Returns a mapping FROM class IRIs TO meta class IDs. Classes not
	 * contained in the mapping just do not have other, equivalent classes.
	 * 
	 * @param metaConceptMappingFile
	 * @return
	 */
	// TODO Duplicate to SetupService
	private static Map<String, String> readMetaConceptMapping(
			File metaConceptMappingFile) {
		Map<String, String> mapping = new HashMap<>();
		if (!metaConceptMappingFile.exists()) {
			log.warn(
					"Meta concept mapping file could not be found at configured path {}. Meta concepts in the dictionary will be removed.",
					metaConceptMappingFile);
			return mapping;
		}
		try (InputStream is = new GZIPInputStream(new FileInputStream(
				metaConceptMappingFile))) {
			LineIterator lineIterator = IOUtils.lineIterator(is, "UTF-8");
			while (lineIterator.hasNext()) {
				// format is:
				// metaConceptId<tab>classIri1||classIri2||classIri3||...
				String line = lineIterator.nextLine();
				// comment?
				if (line.startsWith("#"))
					continue;
				String[] tabSplit = line.split("\\t");
				String metaId = tabSplit[0];
				String[] classIris = tabSplit[1].split("\\|\\|");
				for (int i = 0; i < classIris.length; i++) {
					String classIri = classIris[i];
					mapping.put(classIri, metaId);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return mapping;
	}

	// TODO Duplicate to SetupService
	private static void writeClassToModuleMappingFile(
			Multimap<String, String> classToModuleMapping) throws IOException {
		File dir = classOntologyMappingFile.getParentFile();
		if (!dir.exists())
			dir.mkdirs();
		try (GZIPOutputStream os = new GZIPOutputStream(new FileOutputStream(
				classOntologyMappingFile))) {
			for (String classId : classToModuleMapping.keySet()) {
				Collection<String> moduleIds = classToModuleMapping
						.get(classId);
				IOUtils.write(
						classId + "\t" + StringUtils.join(moduleIds, "||")
								+ "\n", os, "UTF-8");
			}
		}
	}

	// TODO Duplicate to SetupService
	private static void filterConceptDictionary(Set<String> keySet) {
		File dictFilteredFile = new File(dictFilteredPath);
		if (dictFilteredFile.exists())
			dictFilteredFile.delete();
		File dir = dictFilteredFile.getParentFile();
		if (!dir.exists())
			dir.mkdirs();
		try (InputStream is = new GZIPInputStream(new FileInputStream(
				dictFullPath))) {
			try (OutputStream os = new GZIPOutputStream(new FileOutputStream(
					dictFilteredPath))) {
				LineIterator lineIterator = IOUtils.lineIterator(is, "UTF-8");
				while (lineIterator.hasNext()) {
					String line = lineIterator.nextLine();
					String classIri = line.split("\\t")[1];
					if (keySet.contains(classIri))
						IOUtils.write(line + "\n", os, "UTF-8");
				}
			} catch (IOException e) {
				log.error("Could not write filtered concept recognition dictionary line. Either the full dictionary could be accessed or the writing failed.");
				e.printStackTrace();
			}
		} catch (IOException e) {
			log.error(
					"Could not create the filtered concept recognition dictionary because the full dictionary could not be read from configured path {}: {}",
					dictFullPath, e.getMessage());
			e.printStackTrace();
		}
	}
	
}
