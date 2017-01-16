package de.aquadiva.ontologyselection.processes.services;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;
import org.apache.tapestry5.ioc.services.SymbolSource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import de.aquadiva.ontologyselection.JoyceSymbolConstants;
import de.aquadiva.ontologyselection.base.data.Ontology;
import de.aquadiva.ontologyselection.base.services.IOntologyDBService;

public class SetupServiceTest {
	private static Registry registry;

	@BeforeClass
	public static void setup() {
		// comment this in if you need to create the test database anew (required for the OntologyModuleSelectionServiceTest)
		// you might have to delete the database directory in src/test/resources/<dbdir> first
//		System.setProperty(OSSymbolConstants.PERSISTENCE_CONTEXT, "de.aquadiva.test.jpa.ontologyselection");
		registry = RegistryBuilder.buildAndStartupRegistry(JoyceProcessesModule.class);
	}

	@AfterClass
	public static void shutdown() {
		registry.shutdown();
	}

	@Test
	public void testSetupSelectionSystem() throws Exception {
		ISetupService setupService = registry.getService(ISetupService.class);
		setupService.setupSelectionSystem();
		
		SymbolSource symbolSource = registry.getService(SymbolSource.class);
		// check that the class-ontology file has been created and looks approximately right
		String classOntologyMappingFile = symbolSource.valueForSymbol(JoyceSymbolConstants.MIXEDCLASS_ONTOLOGY_MAPPING);
		if (!classOntologyMappingFile.endsWith("gz"))
			classOntologyMappingFile += ".gz";
		try (InputStream is = new GZIPInputStream(new FileInputStream(classOntologyMappingFile))) {
			List<String> mappingLines = IOUtils.readLines(is);
			for (String line : mappingLines) {
				assertTrue(line.indexOf('\t') > -1);
			}
		}
		
		IOntologyDBService service = registry.getService(IOntologyDBService.class);
		List<Ontology> allOntologies = service.getAllOntologies();
		System.out.println(allOntologies);
		service.shutdown();
	}
}
