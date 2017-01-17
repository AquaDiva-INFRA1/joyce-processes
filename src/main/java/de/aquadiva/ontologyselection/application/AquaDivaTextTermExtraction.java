package de.aquadiva.ontologyselection.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import de.aquadiva.ontologyselection.JoyceSymbolConstants;
import de.aquadiva.ontologyselection.core.services.IConceptTaggingService;
import de.aquadiva.ontologyselection.processes.services.JoyceProcessesModule;

public class AquaDivaTextTermExtraction {
	public static void main(String[] args) throws Exception, IOException {
		if (args.length != 1) {
			System.err.println("Usage: " + AquaDivaTextTermExtraction.class.getCanonicalName() + " <directory of text files>");
			System.exit(1);
		}
		
		System.setProperty(JoyceSymbolConstants.GAZETTEER_CONFIG, "bioportal.gazetteer.aquadivatext.properties");
		File adTextDir = new File(args[0]);
		File[] adTextFiles = adTextDir.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return !name.equals(".DS_Store");
			}
		});
		Registry r = RegistryBuilder
				.buildAndStartupRegistry(JoyceProcessesModule.class);
		IConceptTaggingService tagger = r
				.getService(IConceptTaggingService.class);
		File outputfile = new File("aquadivaterms-output.txt");
		if (outputfile.exists())
			outputfile.delete();
		Multiset<String> allTerms = HashMultiset.create();
		for (int i = 0; i < adTextFiles.length; i++) {
			File textFile = adTextFiles[i];
			String text;
			try (InputStream is = new FileInputStream(textFile)) {
				text = IOUtils.toString(is, "UTF-8");
			}
			Multiset<String> terms = tagger.findCoveredTermsAndConcepts(text);
			allTerms.addAll(terms);
		}
		FileUtils.writeLines(outputfile, allTerms, true);
	}
}
