package de.aquadiva.joyce.application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.aquadiva.joyce.base.data.IOntology;
import de.aquadiva.joyce.base.data.InfoType;
import de.aquadiva.joyce.base.data.OntologySet;
import de.aquadiva.joyce.base.data.ScoreType;
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
		log.info("Application finished in {}ms ({}s).", time, time/1000);
	}

}
