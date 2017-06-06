package de.aquadiva.joyce.application;

import static de.aquadiva.joyce.JoyceSymbolConstants.BIOPORTAL_API_KEY;
import static de.aquadiva.joyce.JoyceSymbolConstants.MAPPINGS_FOR_DOWNLOAD;
import static de.aquadiva.joyce.JoyceSymbolConstants.ONTOLOGIES_FOR_DOWNLOAD;
import static de.aquadiva.joyce.JoyceSymbolConstants.SETUP_CONVERT_TO_OWL;
import static de.aquadiva.joyce.JoyceSymbolConstants.SETUP_DOWNLOAD_BIOPORTAL_MAPPINGS;
import static de.aquadiva.joyce.JoyceSymbolConstants.SETUP_DOWNLOAD_BIOPORTAL_ONTOLOGIES;
import static de.aquadiva.joyce.JoyceSymbolConstants.SETUP_IMPORT_ONTOLOGIES;
import static de.julielab.bioportal.util.BioPortalToolUtils.readLineFromStdIn;
import static de.julielab.bioportal.util.BioPortalToolUtils.readLineFromStdInWithMessage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.aquadiva.joyce.JoyceSymbolConstants;
import de.aquadiva.joyce.base.data.IOntology;
import de.aquadiva.joyce.base.data.InfoType;
import de.aquadiva.joyce.base.data.OntologySet;
import de.aquadiva.joyce.base.data.ScoreType;
import de.aquadiva.joyce.processes.services.IOntologyModuleSelectionService;
import de.aquadiva.joyce.processes.services.ISetupService;
import de.aquadiva.joyce.processes.services.JoyceProcessesModule;
import de.aquadiva.joyce.processes.services.SelectionParameters;
import de.aquadiva.joyce.processes.services.SelectionParameters.SelectionType;
import de.julielab.bioportal.util.BioPortalToolUtils;

/**
 * A CLI application for setup and local use. Should go into an interface
 * project of its own.
 * 
 * @author faessler
 * 
 */
public class JoyceApplication {

	private static Logger log = LoggerFactory.getLogger(JoyceApplication.class);

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			System.err.println("Select from the following modes:");
			System.err.println("-s setup");
			System.err.println("-e select ontology modules");
			System.exit(1);
		}
		long time = System.currentTimeMillis();
		String mode = args[0];
		Registry registry = null;
		try {
			registry = RegistryBuilder.buildAndStartupRegistry(JoyceProcessesModule.class);
			switch (mode) {
			case "-s":
				String configFilePath = System.getProperty(JoyceSymbolConstants.JOYCE_CONFIG_FILE);
				if (null == configFilePath && args.length < 2) {
					System.out.println("No configuration file was specified. Please select an option:");
					System.out.println("1. Create a configuration file");
					System.out.println("2. Use default configuration file (./configuration.properties)");
					System.out.println("3. Exit");
					String choice = readLineFromStdIn();
					if (choice.equals("3") || choice.equals("3."))
						System.exit(0);
					else if (choice.equals("2") || choice.equals("2."))
						configFilePath = "configuration.properties";
					else
						configFilePath = doInteractiveConfiguration();
				} else if (null == configFilePath) {
					configFilePath = args[1];
				}
				System.setProperty(JoyceSymbolConstants.JOYCE_CONFIG_FILE, configFilePath);
				registry.shutdown();
				registry = RegistryBuilder.buildAndStartupRegistry(JoyceProcessesModule.class);
				ISetupService setupService = registry.getService(ISetupService.class);
				setupService.setupSelectionSystem();
				break;
			case "-e":
				if (args.length < 2) {
					System.err.println("Specify the following parameters for modules -e:");
					System.err.println("text input file");
					break;
				}
				File textInput = new File(args[1]);
				IOntologyModuleSelectionService selectionService = registry
						.getService(IOntologyModuleSelectionService.class);
				SelectionParameters p = new SelectionParameters();
				p.preferences = new Integer[] { 2, 0, 1 };
				p.maxElementsPerSet = 15;
				p.sampleSize = 100;
				p.scoreTypesToConsider = new ScoreType[] { ScoreType.TERM_COVERAGE, ScoreType.CLASS_OVERHEAD,
						ScoreType.CLASS_OVERLAP };
				p.selectionType = SelectionType.CLUSTER_MODULE;
				log.debug("Configuration: {}", p);

				List<OntologySet> selection = selectionService
						.selectForText(FileUtils.readFileToString(textInput, "UTF-8"), p);
				File resultDir = new File("selectionresult");
				if (!resultDir.exists())
					resultDir.mkdir();
				log.info("The result ontologies and result statistics are written to {}", resultDir);

				OntologySet firstResult = selection.get(0);

				try (OutputStream os = new FileOutputStream(
						new File(resultDir.getAbsolutePath() + File.separator + "resultstats.txt"))) {
					IOUtils.write(firstResult.getScores().toString() + "\n", os);
				}
				try (OutputStream os = new FileOutputStream(
						new File(resultDir.getAbsolutePath() + File.separator + "missingclasses.txt"))) {
					for (String classId : firstResult.getCachedInformation(InfoType.MISSING_CLASSES)) {
						IOUtils.write(classId + "\n", os);
					}
				}
				try (OutputStream os = new FileOutputStream(
						new File(resultDir.getAbsolutePath() + File.separator + "coveredclasses.txt"))) {
					for (String classId : firstResult.getCachedInformation(InfoType.COVERING_CLASSES).elementSet()) {
						IOUtils.write(classId + "\n", os);
					}
				}

				for (IOntology o : firstResult.getOntologies()) {
					File ontologyFile = o.getFile();
					File destFile = new File(resultDir.getAbsolutePath() + File.separator + ontologyFile.getName());
					if (ontologyFile.exists()) {
						FileUtils.copyFile(ontologyFile, destFile);
					} else {
						byte[] ontologyData = o.getOntologyData();
						try (OutputStream os = new FileOutputStream(destFile)) {
							IOUtils.write(ontologyData, os);
						}
					}
				}

				break;
			}
		} finally {
			if (null != registry) {
				registry.shutdown();
			}
		}
		time = System.currentTimeMillis() - time;
		log.info("Application finished in {}ms ({}s).", time, time / 1000);
	}

	private static String doInteractiveConfiguration() throws IOException {
		String apikey = readLineFromStdInWithMessage("Please specify your BioPortal API key:");
		boolean downloadOntologies;
		boolean downloadMappings;
		String[] ontoDownloadRestriction = null;
		String[] mappingsDownloadRestriction = null;
		boolean convert;
		boolean importOntologies;

		downloadOntologies = BioPortalToolUtils
				.readYesNoFromStdInWithMessage("Do you wish to download ontologies from BioPortal?", true);
		if (downloadOntologies) {
			String input = readLineFromStdInWithMessage(
					"Please specify BioPortal ontology acronyms, separated by whitespace, you would like to restrict the download to (leave empty to download all ontologies):");
			if (input.trim().length() > 0)
				ontoDownloadRestriction = input.trim().split("\\s+");
		}
		downloadMappings = BioPortalToolUtils
				.readYesNoFromStdInWithMessage("Do you wish to download ontology mappings from BioPortal?", true);
		if (downloadMappings) {
			String input = readLineFromStdInWithMessage(
					"Please specify BioPortal ontology acronyms, separated by whitespace, you would like to restrict the download to (leave empty to download mappings for all ontologies):");
			if (input.trim().length() > 0)
				mappingsDownloadRestriction = input.trim().split("\\s+");
		}
		convert = BioPortalToolUtils
				.readYesNoFromStdInWithMessage("Do you wish to convert all ontologies into a common OWL format?", true);
		importOntologies = BioPortalToolUtils
				.readYesNoFromStdInWithMessage("Do you wish to import all ontologies into the database?", true);

		Properties config = new Properties();
		config.load(JoyceApplication.class.getResourceAsStream("/configuration.properties.template"));
		config.setProperty(BIOPORTAL_API_KEY, apikey);
		config.setProperty(SETUP_DOWNLOAD_BIOPORTAL_ONTOLOGIES, String.valueOf(downloadOntologies));
		config.setProperty(ONTOLOGIES_FOR_DOWNLOAD,
				Stream.of(ontoDownloadRestriction).collect(Collectors.joining(",")));
		config.setProperty(SETUP_DOWNLOAD_BIOPORTAL_MAPPINGS, String.valueOf(downloadOntologies));
		config.setProperty(MAPPINGS_FOR_DOWNLOAD,
				Stream.of(mappingsDownloadRestriction).collect(Collectors.joining(",")));
		config.setProperty(SETUP_CONVERT_TO_OWL, String.valueOf(convert));
		config.setProperty(SETUP_IMPORT_ONTOLOGIES, String.valueOf(importOntologies));

		String configFilename = BioPortalToolUtils.readLineFromStdInWithMessage(
				"Where would you like to store the configuration file?", "configuration.properties");

		try (Writer w = new BufferedWriter(new FileWriter(configFilename))) {
			config.store(w,
					"This is a JOYCE configuration file.\nExplanations for all configuration symbols can be found in the JavaDocs of\njcore-base:de.aquadiva.joyce.JoyceSymbolConstants");
		}

		return configFilename;
	}

}
