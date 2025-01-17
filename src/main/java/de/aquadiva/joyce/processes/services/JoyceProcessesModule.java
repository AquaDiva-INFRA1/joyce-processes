package de.aquadiva.joyce.processes.services;

import java.util.List;

import org.apache.tapestry5.ioc.OrderedConfiguration;
import org.apache.tapestry5.ioc.ServiceBinder;
import org.apache.tapestry5.ioc.annotations.Contribute;
import org.apache.tapestry5.ioc.annotations.Marker;
import org.apache.tapestry5.ioc.annotations.SubModule;
import org.apache.tapestry5.ioc.services.ChainBuilder;

import de.aquadiva.joyce.base.services.IConstantOntologyScorer;
import de.aquadiva.joyce.base.services.IVariableOntologyScorer;
import de.aquadiva.joyce.core.services.JoyceCoreModule;
import de.aquadiva.joyce.core.services.ActiveCommunityScorer.ActiveCommunity;
import de.aquadiva.joyce.core.services.ClassCoverageScorer.ClassCoverage;
import de.aquadiva.joyce.core.services.ClassOverheadScorer.ClassOverhead;
import de.aquadiva.joyce.core.services.ClassOverlapScorer.ClassOverlap;
import de.aquadiva.joyce.core.services.DescriptivityScorer.Descriptivity;
import de.aquadiva.joyce.core.services.PopularityScorer.Popularity;
import de.aquadiva.joyce.core.services.RichnessScorer.Richness;
import de.aquadiva.joyce.core.services.UpToDateScorer.UpToDate;
import de.aquadiva.joyce.reasoning.services.JoyceReasoningModule;
import de.aquadiva.joyce.reasoning.services.ReasoningChain;

@SubModule(value = { JoyceCoreModule.class, JoyceReasoningModule.class })
public class JoyceProcessesModule {
	private ChainBuilder chainBuilder;

	public JoyceProcessesModule(ChainBuilder chainBuilder) {
		this.chainBuilder = chainBuilder;
	}
	
	public static void bind(ServiceBinder binder) {
		binder.bind(ISetupService.class, SetupService.class);
		binder.bind(IOntologyModuleSelectionService.class, OntologyModuleSelectionService.class);
		
		binder.bind(ITermDiversityMeasurementService.class, TermDiversityMeasurementService.class);
	}


	@Marker(ConstantScoringChain.class)
	public IConstantOntologyScorer buildConstantScoringChain(List<IConstantOntologyScorer> constantScorers) {
		return chainBuilder.build(IConstantOntologyScorer.class, constantScorers);
	}
	
	@ConstantScoringChain
	@Contribute(IConstantOntologyScorer.class)
	public void contributeConstantScoringChain(OrderedConfiguration<IConstantOntologyScorer> configuration,
			@Descriptivity IConstantOntologyScorer descriptivityScorer,
			@Popularity IConstantOntologyScorer popularityScorer, @UpToDate IConstantOntologyScorer upToDateScorer,
			@ActiveCommunity IConstantOntologyScorer activeCommunityScorer, @Richness IConstantOntologyScorer richnessScorer
			,@ReasoningChain IConstantOntologyScorer reasoningChainScorer
			) {
		configuration.add(Descriptivity.class.getSimpleName(), descriptivityScorer);
		configuration.add(Popularity.class.getSimpleName(), popularityScorer);
		configuration.add(UpToDate.class.getSimpleName(), upToDateScorer);
		configuration.add(ActiveCommunity.class.getSimpleName(), activeCommunityScorer);
		configuration.add(Richness.class.getSimpleName(), richnessScorer);
		configuration.add(ReasoningChain.class.getSimpleName(), reasoningChainScorer);
	}

	@Marker(VariableScoringChain.class)
	public IVariableOntologyScorer buildVariableScoringChain(List<IVariableOntologyScorer> variableScorers) {
		return chainBuilder.build(IVariableOntologyScorer.class, variableScorers);
	}

	public void contributeVariableScoringChain(OrderedConfiguration<IVariableOntologyScorer> configuration,
			@ClassCoverage IVariableOntologyScorer classCoverageScorer,
			@ClassOverhead IVariableOntologyScorer classOverheadScorer,
			@ClassOverlap IVariableOntologyScorer classOverlapScorer) {
		configuration.add(ClassCoverage.class.getSimpleName(), classCoverageScorer);
		configuration.add(ClassOverhead.class.getSimpleName(), classOverheadScorer);
		configuration.add(ClassOverlap.class.getSimpleName(), classOverlapScorer);
	}
}
