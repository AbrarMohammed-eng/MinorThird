package edu.cmu.minorthird.ui;

import java.io.*;


/**
 * Help for the command-line interface.
 *
 * @author William Cohen
 */

public class Help 
{
	static private String[] msg = {
		"Useful Minorthird commands in edu.cmu.minorthird.ui:",
		"",
		"ApplyAnnotator -labels KEY -loadFrom ANNOTATOR_FILE ...",
		"  apply a learned classifier or extractor to a labeled test base KEY",
		"",
		"TrainClassifier -labels KEY -learner LEARNER_BSH_STRING ... ",
		"  train a classifier from data in the labeled text base KEY",
		"TestClassifier -labels KEY -loadFrom ANNOTATOR_FILE ...",
		"  test a classifier on data in the labeled text base KEY",
		"TrainTestClassifier -labels KEY -splitter SPLITTER ...",
		"  perform a classification-learning experiment with the data in KEY",
		"",
		"TrainExtractor -labels KEY -learner LEARNER_BSH_STRING ... ",
		"  train a extractor from data in the labeled text base KEY",
		"TestExtractor -labels KEY -loadFrom ANNOTATOR_FILE ...",
		"  test a extractor on data in the labeled text base KEY",
		"TrainTestExtractor -labels KEY -splitter SPLITTER ...",
		"  perform a extraction-learning experiment with the data in KEY",
		"",
		"The single option -help gives detailed command-line help for a command."
	};

	public static void main(String args[])
	{
		for (int i=0; i<msg.length; i++) {
			System.out.println(msg[i]);
		}
	}
}
