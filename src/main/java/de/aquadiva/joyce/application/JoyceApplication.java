package de.aquadiva.joyce.application;

import java.io.IOException;

import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;

import de.aquadiva.joyce.processes.services.ISetupService;
import de.aquadiva.joyce.processes.services.JoyceProcessesModule;

/**
 * A CLI application for setup and local use. Should go into an interface
 * project of its own.
 * 
 * @author faessler
 * 
 */
public class JoyceApplication {

	public static void main(String[] args) throws IOException {
		Registry registry = null;
		try {
			registry = RegistryBuilder
					.buildAndStartupRegistry(JoyceProcessesModule.class);
			ISetupService setupService = registry
					.getService(ISetupService.class);
			setupService.setupSelectionSystem();
		} finally {
			if (null != registry) {
				registry.shutdown();
			}
		}
		System.out.println("Application is finished.");
	}

}
