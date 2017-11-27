# JOYCE Processes

## Overview

This is the main application project that relies on *joyce-base* and *joyce-core* to create the JOYCE ontology (module) repository and to select modules from this repository given a text with ontology class mentions in it. The main class is `de.aquadiva.joyce.application.JoyceApplication` and offers functionality for the setup of the repository, selection of modules given a text and printing the ontology repository statics to file.

## Usage

Upon doing a `mvn clean package` of this project (if the other JOYCE projects where not installed via `mvn clean install` you might want to do so first by executing `mvn clean install` on the root joyce project), an *uber JAR* will be created in the `target/` directory containing everything to run JOYCE. Start it with
`java -jar target/joyce-processes-<version>.jar`. A menu will come up showing the options.

## Main services

### de.aquadiva.joyce.processes.services.ISetupService

Invoked with the -s option from the JOYCE menu. Downloads ontologies and mappings, imports them into the SQL database, does ontology modularization, extracts ontology class names and exports mapping files and the class name dictionary from the Neo4j database. WARNING: Although multi-threaded, the mapping download from BioPortal will be very long, probably several days. Much worse is however the running time of the cluster-based modularization. More below in *caveats*.

### de.aquadiva.joyce.processes.services.IOntologyModuleSelectionService

The is the actual selection service and thus the whole goal of JOYCE. It takes a text string in which it looks for mentions of ontology class names. Then, it tries to find sets of best fitting modules covering the found classes, trying to minimize unrequired class overhead. 

## Caveats

### Running time
The setup may run extremely long. This is in part due to the download of mappings which takes much longer than the download of classes. Also, to extract ontology names, all ontologies must be parsed which also will take quite a while. The worst time factor, however, is the modularization. It was tried to create modules of the Gene Ontology, which has 50k classes. After 7 days without result, the process was stopped. Since other ontologies have even more classes, this is quite an issue.
To restrict the time given to the modularization algorithm for each ontology, a timeout mechanism has been employed. Find it in the `de.aquadiva.joyce.core.services.OntologyModularizationService` service implementation class. Look for the line with content *HERE: Modularization timeout*. The current timeout is 120 days which you might want to reduce significantly. Of course, for ontologies caught by the timeout, no modules will be created.

### Configuration of the single application JAR

The single executable JAR (can be run with java -jar) is built with the [Maven Shade](https://maven.apache.org/plugins/maven-shade-plugin/). This plugin not only allows to put all dependencies into one single JAR but it also takes care of merging configuration files contained in different dependencies with the same name. For details, see the respective section in the pom.xml file.
