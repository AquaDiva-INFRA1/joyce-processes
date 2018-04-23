package de.aquadiva.joyce.application;

import static de.aquadiva.joyce.JoyceSymbolConstants.BIOPORTAL_API_KEY;
import static de.aquadiva.joyce.JoyceSymbolConstants.MAPPINGS_DOWNLOAD_DIR;
import static de.aquadiva.joyce.JoyceSymbolConstants.MAPPINGS_FOR_DOWNLOAD;
import static de.aquadiva.joyce.JoyceSymbolConstants.NEO4J_PATH;
import static de.aquadiva.joyce.JoyceSymbolConstants.ONTOLOGIES_DOWNLOAD_DIR;
import static de.aquadiva.joyce.JoyceSymbolConstants.ONTOLOGIES_FOR_DOWNLOAD;
import static de.aquadiva.joyce.JoyceSymbolConstants.ONTOLOGY_CLASSES_NAMES_DIR;
import static de.aquadiva.joyce.JoyceSymbolConstants.ONTOLOGY_INFO_DOWNLOAD_DIR;
import static de.aquadiva.joyce.JoyceSymbolConstants.OWL_DIR;
import static de.aquadiva.joyce.JoyceSymbolConstants.SETUP_CONVERT_TO_OWL;
import static de.aquadiva.joyce.JoyceSymbolConstants.SETUP_DOWNLOAD_BIOPORTAL_MAPPINGS;
import static de.aquadiva.joyce.JoyceSymbolConstants.SETUP_DOWNLOAD_BIOPORTAL_ONTOLOGIES;
import static de.aquadiva.joyce.JoyceSymbolConstants.SETUP_IMPORT_ONTOLOGIES;
import static de.julielab.java.utilities.CLIInteractionUtilities.readLineFromStdIn;
import static de.julielab.java.utilities.CLIInteractionUtilities.readLineFromStdInWithMessage;
import static de.julielab.java.utilities.CLIInteractionUtilities.readYesNoFromStdInWithMessage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aliasi.util.Files;

import de.aquadiva.joyce.JoyceSymbolConstants;
import de.aquadiva.joyce.base.data.IOntology;
import de.aquadiva.joyce.base.data.InfoType;
import de.aquadiva.joyce.base.data.OntologySet;
import de.aquadiva.joyce.base.data.ScoreType;
import de.aquadiva.joyce.base.services.IOntologyRepositoryStatsPrinterService;
import de.aquadiva.joyce.base.util.JoyceException;
import de.aquadiva.joyce.processes.services.IOntologyModuleSelectionService;
import de.aquadiva.joyce.processes.services.ISetupService;
import de.aquadiva.joyce.processes.services.JoyceProcessesModule;
import de.aquadiva.joyce.processes.services.SelectionParameters;
import de.aquadiva.joyce.processes.services.SelectionParameters.SelectionType;

/**
 * A CLI application for setup and local use. Should go into an interface
 * project of its own.
 * 
 * @author faessler
 * 
 */
public class JoyceApplication {

	private static Logger log = LoggerFactory.getLogger(JoyceApplication.class);
	
	static {
		// We need this to have hibernate use slf4j logging.
		System.setProperty("org.jboss.logging.provider", "slf4j");
	}

	public static void main(String[] args) throws IOException, JoyceException {
		if (args.length == 0) {
			System.err.println("Select from the following modes:");
			System.err.println("-s setup");
			System.err.println("-e select ontology modules");
			System.err.println("-p print JOYCE ontology repository statistics");
			System.exit(1);
		}
		long time = System.currentTimeMillis();
		String mode = args[0];
		Registry registry = null;
		try {
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
				registry = RegistryBuilder.buildAndStartupRegistry(JoyceProcessesModule.class);
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

				if (!selection.isEmpty()) {
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
						for (String classId : firstResult.getCachedInformation(InfoType.COVERING_CLASSES)
								.elementSet()) {
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
				}
				break;
			case "-p":
				registry = RegistryBuilder.buildAndStartupRegistry(JoyceProcessesModule.class);
				IOntologyRepositoryStatsPrinterService printerService = registry
						.getService(IOntologyRepositoryStatsPrinterService.class);
				File file = new File("ontologyrepositorystats.csv");
				log.info(
						"Printing ontology statistics to {}. This may take a long time depending on the repository size.",
						file);
				printerService.printOntologyRepositoryStats(file);
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

		downloadOntologies = readYesNoFromStdInWithMessage("Do you wish to download ontologies from BioPortal?", true);
		if (downloadOntologies) {
			String input = readLineFromStdInWithMessage(
					"Please specify BioPortal ontology acronyms, separated by whitespace, you would like to restrict the download to (leave empty to download all ontologies):");
			if (input.trim().length() > 0)
				ontoDownloadRestriction = input.trim().split("\\s+");
		}
		downloadMappings = readYesNoFromStdInWithMessage("Do you wish to download ontology mappings from BioPortal?",
				true);
		if (downloadMappings) {
			String input = readLineFromStdInWithMessage(
					"Please specify BioPortal ontology acronyms, separated by whitespace, you would like to restrict the download to (leave empty to download mappings for all ontologies):");
			if (input.trim().length() > 0)
				mappingsDownloadRestriction = input.trim().split("\\s+");
		}
		convert = readYesNoFromStdInWithMessage("Do you wish to convert all ontologies into a common OWL format?",
				true);
		importOntologies = readYesNoFromStdInWithMessage("Do you wish to import all ontologies into the database?",
				true);

		Properties config = new Properties();
		config.load(JoyceApplication.class.getResourceAsStream(File.separator + "configuration.properties.template"));
		config.setProperty(BIOPORTAL_API_KEY, apikey);
		config.setProperty(SETUP_DOWNLOAD_BIOPORTAL_ONTOLOGIES, String.valueOf(downloadOntologies));
		config.setProperty(ONTOLOGIES_FOR_DOWNLOAD,
				ontoDownloadRestriction != null ? Stream.of(ontoDownloadRestriction).collect(Collectors.joining(","))
						: "");
		config.setProperty(SETUP_DOWNLOAD_BIOPORTAL_MAPPINGS, String.valueOf(downloadOntologies));
		config.setProperty(MAPPINGS_FOR_DOWNLOAD,
				mappingsDownloadRestriction != null
						? Stream.of(mappingsDownloadRestriction).collect(Collectors.joining(","))
						: "");
		config.setProperty(SETUP_CONVERT_TO_OWL, String.valueOf(convert));
		config.setProperty(SETUP_IMPORT_ONTOLOGIES, String.valueOf(importOntologies));

		String configFilename = readLineFromStdInWithMessage("Where would you like to store the configuration file?",
				"configuration.properties");

		try (Writer w = new BufferedWriter(new FileWriter(configFilename))) {
			config.store(w,
					"This is a JOYCE configuration file.\nExplanations for all configuration symbols can be found in the JavaDocs of\njcore-base:de.aquadiva.joyce.JoyceSymbolConstants");
		}

		// Gets a configuration value as a file instance
		Function<String, File> gf = s -> new File(config.getProperty(s));
		// The following function checks whether a given file name exists
		Function<String, Boolean> f = s -> gf.apply(s).exists();
		Matcher dbFileMatcher = Pattern.compile("file:([^;]+);").matcher("");
		dbFileMatcher.reset(config.getProperty(JoyceSymbolConstants.JPA_JDBC_URL, ""));
		String dbPath = null;
		File dbDirectory = null;
		if (dbFileMatcher.find()) {
			dbPath = dbFileMatcher.group(1);
			int lastPathElementStartIndex = dbPath.lastIndexOf(File.separator);
			dbDirectory = new File(dbPath.substring(0, lastPathElementStartIndex));
		}
		if (f.apply(ONTOLOGIES_DOWNLOAD_DIR) || f.apply(ONTOLOGY_INFO_DOWNLOAD_DIR) || f.apply(MAPPINGS_DOWNLOAD_DIR)
				|| f.apply(ONTOLOGY_CLASSES_NAMES_DIR) || f.apply(NEO4J_PATH) || f.apply(OWL_DIR)
				|| dbDirectory.exists()) {
			Consumer<String> delete = s -> {
				if (f.apply(s))
					Files.removeRecursive(gf.apply(s));
			};
			if (readYesNoFromStdInWithMessage(
					"There is already ontology or mapping data downloaded or derived from downloads. Should existing data be removed? Warning: This will remove all data, including the ontology database.")) {
				delete.accept(ONTOLOGIES_DOWNLOAD_DIR);
				delete.accept(ONTOLOGY_INFO_DOWNLOAD_DIR);
				delete.accept(MAPPINGS_DOWNLOAD_DIR);
				delete.accept(ONTOLOGY_CLASSES_NAMES_DIR);
				delete.accept(NEO4J_PATH);
				delete.accept(OWL_DIR);
				if (dbDirectory.exists()) {
					log.debug("Deleting ontology database directory {}", dbDirectory);
					Files.removeRecursive(dbDirectory);
				}
			}
		}

		return configFilename;
	}

}
