package edu.cmu.minorthird.ui;

import edu.cmu.minorthird.classify.*;
import edu.cmu.minorthird.classify.experiments.*;
import edu.cmu.minorthird.text.*;
import edu.cmu.minorthird.text.learn.*;
import edu.cmu.minorthird.text.learn.experiments.*;
import edu.cmu.minorthird.util.gui.*;
import edu.cmu.minorthird.util.*;

import org.apache.log4j.Logger;
import java.util.*;
import java.io.*;


/**
 * Do a train/test experiment for named-entity extractors.
 *
 * @author William Cohen
 */

public class TestExtractor implements CommandLineUtil.UIMain
{
  private static Logger log = Logger.getLogger(TestExtractor.class);

	// private data needed to train a extractor

	private CommandLineUtil.BaseParams base = new CommandLineUtil.BaseParams();
	private CommandLineUtil.SaveParams save = new CommandLineUtil.SaveParams();
	private CommandLineUtil.ExtractionSignalParams signal = new CommandLineUtil.ExtractionSignalParams();
	private CommandLineUtil.TestExtractorParams test = new CommandLineUtil.TestExtractorParams();
	private TextLabels annLabels = null;

	private CommandLineProcessor getCLP()
	{
		return new JointCommandLineProcessor(new CommandLineProcessor[]{base,save,signal,test});
	}

	//
	// do the experiment
	// 

	public void doMain()
	{
		// check that inputs are valid
		if (base.labels==null) throw new IllegalArgumentException("-labels must be specified");
		if (test.loadFrom==null) throw new IllegalArgumentException("-loadFrom must be specified");

		// echo the input
		if (base.showLabels) {
			Viewer vl = new SmartVanillaViewer();
			vl.setContent(base.labels);
			new ViewerFrame("Textbase",vl);
		}
		
		// load the annotator
		Annotator ann = null;
		try {
			ann = (Annotator)IOUtil.loadSerialized(test.loadFrom);
		} catch (IOException ex) {
			throw new IllegalArgumentException("can't load annotator from "+test.loadFrom+": "+ex);
		}

		if (test.showExtractor) {
			Viewer vx = new SmartVanillaViewer();
			vx.setContent(ann);
			new ViewerFrame("Annotator",vx);
		}

		annLabels = ann.annotatedCopy(base.labels);

		// echo the labels after annotation
		if (base.showResult) {
			Viewer va = new SmartVanillaViewer();
			va.setContent(annLabels);
			new ViewerFrame("Annotated Textbase",va);
		}
		
	}

	public Object getMainResult() { return annLabels; }

	public static void main(String args[])
	{
		try {
			TestExtractor main = new TestExtractor();
			main.getCLP().processArguments(args);
			main.doMain();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("use option -help for help");
		}
	}
}
