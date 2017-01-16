package de.aquadiva.ontologyselection.processes.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.slf4j.Logger;

import cern.colt.Arrays;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;

import de.aquadiva.ontologyselection.OSSymbolConstants;
import de.aquadiva.ontologyselection.core.services.IConceptTaggingService;

public class TermDiversityMeasurementService implements
		ITermDiversityMeasurementService {

	private Multimap<String, String> classToModuleMapping;
	private Logger log;

	public TermDiversityMeasurementService(
			Logger log,
			@Symbol(OSSymbolConstants.MIXEDCLASS_ONTOLOGY_MAPPING) File classOntologyMappingFile)
			throws IOException {
		this.log = log;
		classToModuleMapping = readClassToModuleMapping(classOntologyMappingFile);
	}

	@Override
	public int getDiversity(String term) {
		return getModulesForTerm(term).size();
	}

	@Override
	public Set<String> getModulesForTerm(String term) {
		String[] concepts = getConcepts(term);
		if (null != concepts) {
			log.debug("Retrieved {} concept for term {}", concepts.length, term);
			Set<String> modules = new HashSet<>();
			for (int i = 0; i < concepts.length; i++) {
				String conceptId = concepts[i];
				Collection<String> containingModules = classToModuleMapping
						.get(conceptId);
				if (containingModules != null) {
					for (String moduleId : containingModules)
						modules.add(moduleId);
				} else
					log.debug(
							"Concept {} is unknown to the class-module-mapping.",
							conceptId);
			}
			return modules;
		}
		return Collections.emptySet();

	}

	private String[] getConcepts(String term) {
		String[] split = term.split("\\t");
		String[] ids = split[1].split("\\|\\|");
		return ids;
	}

	@Override
	public int getDiversity(String[] terms) {
		Set<String> moduleIds = new HashSet<>();
		for (int i = 0; i < terms.length; i++) {
			String term = terms[i];
			moduleIds.addAll(getModulesForTerm(term));
		}
		return moduleIds.size();
	}

	private Multimap<String, String> readClassToModuleMapping(
			File classOntologyMappingFile) throws IOException {
		Multimap<String, String> map = HashMultimap.create();
		try (GZIPInputStream is = new GZIPInputStream(new FileInputStream(
				classOntologyMappingFile))) {
			LineIterator lineIterator = IOUtils.lineIterator(is, "UTF-8");
			while (lineIterator.hasNext()) {
				String line = lineIterator.nextLine();
				String[] split = line.split("\\t");
				String classId = split[0];
				String[] moduleIds = split[1].split("\\|\\|");
				for (int i = 0; i < moduleIds.length; i++) {
					String moduleId = moduleIds[i];
					map.put(classId, moduleId);
				}
			}
		}
		return map;
	}
}
