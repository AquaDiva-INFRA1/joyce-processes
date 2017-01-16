package de.aquadiva.ontologyselection.application;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;

import de.aquadiva.ontologyselection.base.services.IOntologyDBService;
import de.aquadiva.ontologyselection.processes.services.ISetupService;
import de.aquadiva.ontologyselection.processes.services.OSProcessesModule;

/**
 * A CLI application for setup and local use. Should go into an interface
 * project of its own.
 * 
 * @author faessler
 * 
 */
public class ADOSApplication {

	public static void main(String[] args) throws IOException {
		Registry registry = null;
		try {
			registry = RegistryBuilder
					.buildAndStartupRegistry(OSProcessesModule.class);
			ISetupService setupService = registry
					.getService(ISetupService.class);
			setupService.setupSelectionSystem();
		} finally {
			if (null != registry) {
				// TODO: That's not good, all the services should be registered
				// to the registry's shutdown hub and execute their own
				// shutdowns automatically
//				IOntologyDBService dbservice = registry
//						.getService(IOntologyDBService.class);
//				dbservice.shutdown();
//				ExecutorService executorService = registry
//						.getService(ExecutorService.class);
//				List<Runnable> remainingThreads = executorService.shutdownNow();
//				if (remainingThreads.size() != 0)
//					System.out.println("Wait for " + remainingThreads.size() + " to end.");
//				try {
//					executorService.awaitTermination(10, TimeUnit.MINUTES);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
				registry.shutdown();
			}
		}
		System.out.println("Application is finished.");
	}

}
