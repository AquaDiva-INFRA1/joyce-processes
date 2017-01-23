package de.aquadiva.joyce.processes.application;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;


public class SplitTerms {
	
	@Test
	public void testScorer() throws Exception, IOException {
		String[] terms;
		Set<String> uniTerms=new HashSet<String>();
		try (InputStream is = new FileInputStream("src/test/resources/test-terms.txt")) {
			List<String> lines = IOUtils.readLines(is, "UTF-8");
			terms = lines.toArray(new String[lines.size()]);
		}
		
		for(int i=0;i<terms.length;i++)
		{
			String term=terms[i];
			uniTerms.add(term.toLowerCase());
		}
		String[] termN = uniTerms.toArray(new String[0]);
		int size=termN.length;
		int step=30;
		int nof=size/step;
		System.out.println(size+ "\t the term \t"+nof);
	//	Random randomGenerator = new Random();
	//	int randomInt = randomGenerator.nextInt(termN.length);
		for (int j=1;j<=nof;j++)  
		  {  
			 PrintWriter writer = new PrintWriter("selection_"+j+".txt", "UTF-8");  
		     for (int i=(j-1)*step;i<=j*step;i++)  
		   {  
		    String term1=termN[i];   
		    String selTerm= StringUtils.substringBetween(term1, "\t", "\t");
		    writer.println(selTerm);
		}  System.out.println("\t \t the term \t \t \t");
		
	   writer.close();
	}
			
	}

}
