/* Copyright 2003, Carnegie Mellon, All Rights Reserved */

package edu.cmu.minorthird.text.learn;

import edu.cmu.minorthird.classify.*;
import edu.cmu.minorthird.classify.experiments.CrossValSplitter;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;

/**
 *
 * @author William Cohen
 */

public class CVSplitterTest extends TestCase
{
	private static Logger log = Logger.getLogger(CVSplitterTest.class);

	public CVSplitterTest(String name) { super(name); }
	public CVSplitterTest() { super("CVSplitterTest"); }
  public static Test suite() { return new TestSuite(CVSplitterTest.class); }
	
	public void testCV()
	{
		doTest(10,1);
		doTest(3,5);
	}

	public void doTest(int numSites,int numPagesPerSite) 
	{
		log.debug("[SpanDataXVTest sites: "+numSites+" pages/site: "+numPagesPerSite+"]");
		Dataset data = new BasicDataset();
		for (int site=1; site<=numSites; site++) {
			String subpop = "www.site"+site+".com";
			for (int page=1; page<=numPagesPerSite; page++) {
				MutableInstance inst = new MutableInstance("page"+page+".html", subpop);
				inst.addBinary( new Feature("site"+site+".page"+page) );
				data.add(new BinaryExample(inst, +1));
				log.debug("instance: "+inst);
			}
		}
		int totalSize = data.size();
		Dataset.Split split = data.split(new CrossValSplitter(3));
		assertEquals( 3, split.getNumPartitions() );
		Dataset[] train = new Dataset[3];
		Dataset[] test = new Dataset[3];
		int totalTest = 0;
		for (int i=0; i<3; i++) {
			log.debug("partition "+(i+1)+":");
			train[i] = split.getTrain(i);
			test[i] = split.getTest(i);
			for (Example.Looper j=test[i].iterator(); j.hasNext(); ) {
				Example e = j.nextExample();
				log.debug("  test:  "+e);
				assertTrue( !contains(train[i],e) );
			}
			log.debug("  -----\n  "+test[i].size()+" total");
			for (Example.Looper j=train[i].iterator(); j.hasNext(); ) {
				Example e = j.nextExample();
				log.debug("  train:  "+e);
				assertTrue( !contains(test[i],e) );
			}
			log.debug("  -----\n  "+train[i].size()+" total");
			assertEquals( totalSize, train[i].size() + test[i].size() );
			totalTest += test[i].size();
		}
		assertEquals( totalSize, totalTest );
	}
	private boolean contains(Dataset data,Example e)
	{
		for (Example.Looper j=data.iterator(); j.hasNext(); ) {
			Example e1 = j.nextExample();
			if (e1.asInstance()==e) return true;
		}
		return false;
	}
}

