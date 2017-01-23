package de.aquadiva.joyce.processes.application;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.commons.io.IOUtils;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;
import org.junit.Ignore;
import org.junit.Test;

import de.aquadiva.joyce.base.services.IOntologyDBService;
import de.aquadiva.joyce.processes.services.ITermDiversityMeasurementService;
import de.aquadiva.joyce.processes.services.JoyceProcessesModule;

@Ignore
public class TermDiversityScorer {
	@Test
	public void testScorer() throws Exception, IOException {
		String[] terms;
		Set<String> uniTerms=new HashSet<String>();;
		try (InputStream is = new FileInputStream("aquadivaterms-output.txt")) {
			List<String> lines = IOUtils.readLines(is, "UTF-8");
			terms = lines.toArray(new String[lines.size()]);
		}
		
		for(int i=0;i<terms.length;i++)
		{
			String term=terms[i];
			uniTerms.add(term);
		}
		PrintWriter writer = new PrintWriter("the-file-set_domain_5.txt", "UTF-8");
		/*Iterator it = uniTerms.iterator();
		while(it.hasNext()) {
			String termU=(String)it.next();
			String term1=termU.substring(0, termU.indexOf("\t"));
			File dir = new File("aquadiva-text");
			File[] files = dir.listFiles();
			for (File f : files) {
			int count=countWord(term1, f);
			writer.println(count+"\t"+term1+"\t"+f.getName());
		}}
		writer.close();*/
		
		try (InputStream is = new FileInputStream("the-file-set_domain.txt")) {
			List<String> lines = IOUtils.readLines(is, "UTF-8");
			terms = lines.toArray(new String[lines.size()]);
		}
		for(int i=0;i<terms.length-2;i+=2)
		{
			String term=terms[i]; String term2=terms[i+1]; String term3=terms[i+2]; String term4=terms[i+3];
			String id=term.substring(0, term.indexOf("\t")); String id2=term2.substring(0, term2.indexOf("\t"));
			String id3=term3.substring(0, term3.indexOf("\t"));
			String id4=term4.substring(0, term4.indexOf("\t"));
			if(Integer.valueOf(id)>0 && Integer.valueOf(id2)>0 && Integer.valueOf(id3)> 0 && Integer.valueOf(id4)>0)
			{
				writer.println(term); //writer.println(term2);writer.println(term3);writer.println(term4);
			}
				
		}
		Registry registry = null;
		try {
			registry = RegistryBuilder
					.buildAndStartupRegistry(JoyceProcessesModule.class);
			ITermDiversityMeasurementService service = registry
					.getService(ITermDiversityMeasurementService.class);
			System.out.println("Total diversity: "	+ service.getDiversity(terms));
		} finally {
			if (null != registry) {
				// TODO: That's not good, all the services should be registered
				// to the registry's shutdown hub and execute their own
				// shutdowns automatically
				IOntologyDBService dbservice = registry
						.getService(IOntologyDBService.class);
				dbservice.shutdown();
				ExecutorService executorService = registry
						.getService(ExecutorService.class);
				executorService.shutdownNow();
				registry.shutdown();
			}
		}
	}
	
	private int countWord(String word, File file) {
		
		int count = 0;
		FileReader fr=null;
			try {
				fr = new FileReader(file);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			BufferedReader br=new BufferedReader(fr);
			String line;
		    String s;
			   try {
				while ((s=br.readLine())!=null){
				      if(s.contains(word))
				     count++;
				 
				   }
			} catch (IOException e) {
				e.printStackTrace();
			}
					
		return count;
		}
	
	}
