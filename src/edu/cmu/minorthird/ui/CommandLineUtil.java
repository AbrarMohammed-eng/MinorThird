package edu.cmu.minorthird.ui;

import edu.cmu.minorthird.text.*;
import edu.cmu.minorthird.text.learn.*;
import edu.cmu.minorthird.util.*;
import edu.cmu.minorthird.util.gui.*;
import edu.cmu.minorthird.classify.experiments.*;
import edu.cmu.minorthird.classify.*;
import edu.cmu.minorthird.classify.sequential.*;

import org.apache.log4j.Logger;
import java.util.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;


/**
 * Minorthird-specific utilities for command line based interface routines.
 *
 * @author William Cohen
 */

class CommandLineUtil 
{
	private static Logger log = Logger.getLogger(CommandLineUtil.class);

	//
	// misc utilities
	//

	/** Build a sequential classification dataset from the necessary inputs. 
	 */
	static public SequenceDataset 
	toSequenceDataset(TextLabels labels,SpanFeatureExtractor fe,int historySize,String tokenProp)
	{
		NestedTextLabels safeLabels = new NestedTextLabels(labels);
		safeLabels.shadowProperty(tokenProp);

		SequenceDataset seqData = new SequenceDataset();
		seqData.setHistorySize(historySize);
		for (Span.Looper j=labels.getTextBase().documentSpanIterator(); j.hasNext(); ) {
			Span document = j.nextSpan();
			Example[] sequence = new Example[document.size()];
			for (int i=0; i<document.size(); i++) {
				Token tok = document.getToken(i);
				String value = labels.getProperty(tok, tokenProp);
				if (value==null) value = "NONE";
				Span tokenSpan = document.subSpan(i,1);
				Example example = new Example( fe.extractInstance(safeLabels,tokenSpan), new ClassLabel(value) );
				sequence[i] = example;
			}
			seqData.addSequence( sequence );
		}
		return seqData;
	}


	/** Build a classification dataset from the necessary inputs. 
	*/
  static public Dataset toDataset(TextLabels textLabels, SpanFeatureExtractor fe,String spanProp,String spanType)
	{
		return toDataset(textLabels,fe,spanProp,spanType,null);
	}

	/** Build a classification dataset from the necessary inputs. 
	 */
  static public Dataset 
	toDataset(TextLabels textLabels, SpanFeatureExtractor fe,String spanProp,String spanType,String candidateType)
  {
		// use this to print out a summary
		Map countByClass = new HashMap();

		NestedTextLabels safeLabels = new NestedTextLabels(textLabels);
		safeLabels.shadowProperty(spanProp);

		Span.Looper candidateLooper = 
			candidateType!=null ? 
			textLabels.instanceIterator(candidateType) : textLabels.getTextBase().documentSpanIterator();

		// binary dataset - anything labeled as in this type is positive
		if (spanType!=null) {
			Dataset dataset = new BasicDataset();
			for (Span.Looper i=candidateLooper; i.hasNext(); ) {
				Span s = i.nextSpan();
				int classLabel = textLabels.hasType(s,spanType) ? +1 : -1;
				String className = classLabel<0 ? ExampleSchema.NEG_CLASS_NAME : ExampleSchema.POS_CLASS_NAME;
				dataset.add( new BinaryExample( fe.extractInstance(safeLabels,s), classLabel) );
				Integer cnt = (Integer)countByClass.get( className );
				if (cnt==null) countByClass.put( className, new Integer(1) );
				else countByClass.put( className, new Integer(cnt.intValue()+1) );
			}
			System.out.println("Number of examples by class: "+countByClass);
			return dataset;
		}
		// k-class dataset
		if (spanProp!=null) {
			Dataset dataset = new BasicDataset();
			for (Span.Looper i=candidateLooper; i.hasNext(); ) {
				Span s = i.nextSpan();
				String className = textLabels.getProperty(s,spanProp);
				if (className==null) {
					log.warn("no span property "+spanProp+" for document "+s.getDocumentId()+" - will be ignored");
				} else {
					dataset.add( new Example( fe.extractInstance(safeLabels,s), new ClassLabel(className)) );
				}
				Integer cnt = (Integer)countByClass.get( className );
				if (cnt==null) countByClass.put( className, new Integer(1) );
				else countByClass.put( className, new Integer(cnt.intValue()+1) );
			}
			System.out.println("Number of examples by class: "+countByClass);
			return dataset;
		}
		throw new IllegalArgumentException("either spanProp or spanType must be specified");
  }

	/** Summarize an Evaluation object by showing summary statistics.
	 */
	static public void summarizeEvaluation(Evaluation e)
	{
		double[] stats = e.summaryStatistics();
		String[] statNames = e.summaryStatisticNames();
		int maxLen = 0;
		for (int i=0; i<statNames.length; i++) {
			maxLen = Math.max(statNames[i].length(), maxLen); 
		}
		for (int i=0; i<statNames.length; i++) {
			System.out.print(statNames[i]+": ");
			for (int j=0; j<maxLen-statNames[i].length(); j++) System.out.print(" ");
			System.out.println(stats[i]);
		}
	}

	//
	// stuff for command-line parsing
	//

	/** Create a new object from a fragment of bean shell code,
	 * and make sure it's the correct type.
	 */
	static Object newObjectFromBSH(String s,Class expectedType)
	{
		try {
			bsh.Interpreter interp = new bsh.Interpreter();
			interp.eval("import edu.cmu.minorthird.classify.*;");
			interp.eval("import edu.cmu.minorthird.classify.algorithms.linear.*;");
			interp.eval("import edu.cmu.minorthird.classify.algorithms.trees.*;");
			interp.eval("import edu.cmu.minorthird.classify.algorithms.knn.*;");
			interp.eval("import edu.cmu.minorthird.classify.algorithms.svm.*;");
			interp.eval("import edu.cmu.minorthird.classify.transform.*;");
			interp.eval("import edu.cmu.minorthird.classify.sequential.*;");
			interp.eval("import edu.cmu.minorthird.text.learn.*;");
			interp.eval("import edu.cmu.minorthird.text.*;");
			interp.eval("import edu.cmu.minorthird.ui.*;");
			if (!s.startsWith("new"))	s = "new "+s;
			Object o = interp.eval(s);
			if (!expectedType.isInstance(o)) {
				throw new IllegalArgumentException(s+" did not produce "+expectedType);
			}
			return o;
		} catch (bsh.EvalError e) {
			log.error(e.toString());
			throw new IllegalArgumentException("error parsing '"+s+"':\n"+e);
		}
	}

	
	/** Decode splitter names.  Examples of splitter names are: k5, for
	 * 5-fold crossvalidation, s10, for stratified 10-fold
	 * crossvalidation, r70, for random split into 70% training and 30%
	 * test.  The splitter name "-help" will print a help message to
	 * System.out.
	 */
	static Splitter toSplitter(String splitterName)
	{
		if (splitterName.charAt(0)=='k') {
			int folds = StringUtil.atoi(splitterName.substring(1,splitterName.length()));
			return new CrossValSplitter(folds);
		}
		if (splitterName.charAt(0)=='r') {
			double pct = StringUtil.atoi(splitterName.substring(1,splitterName.length())) / 100.0;
			return new RandomSplitter(pct);
		}
		//if (splitterName.charAt(0)=='s') {
		//int folds = StringUtil.atoi(splitterName.substring(1,splitterName.length()));
		//return new StratifiedCrossValSplitter(folds);
		//}
		if ("-help".equals(splitterName)) {
			System.out.println("Valid splitter names:");
			System.out.println(" kN    N-fold cross-validation, e.g. k5");
			System.out.println(" sN    stratified N-fold cross-validation, i.e., the");
			System.out.println("       distribution of pos/neg classes is the same in each fold");
			System.out.println(" rNN   single random train-test split with NN% going to train");
			System.out.println("        e.g, r70 is a 70%-30% split");
			return new RandomSplitter(0.70);
		} 
		throw new IllegalArgumentException("illegal splitterName '"+splitterName+"'");
	}

	//
	// useful sets of parameters that can be read from command line
	// 

	/** Basic parameters used by almost everything. */
	public static class BaseParams extends BasicCommandLineProcessor {
		public TextLabels labels=null;
		private String repositoryKey="";
		public boolean showLabels=false, showResult=false;
		public void labels(String repositoryKey) { 
			this.repositoryKey = repositoryKey;
			this.labels = (TextLabels)FancyLoader.loadTextLabels(repositoryKey); 
		}
		public void showLabels() { this.showLabels=true; }
		public void showResult() { this.showResult=true; }
		public void usage() {
			System.out.println("basic parameters:");
			System.out.println(" -labels REPOSITORY_KEY   load text from REPOSITORY_KEY ");
			System.out.println(" [-showLabels]            interactively view textBase loaded by -labels");
			System.out.println(" [-showResult]            interactively view final result of this operation");
			System.out.println();
		}
		// for GUI
		//public String getLabels() { return repositoryKey; }
		//public void setLabels(String key) { labels(key); }
		public String getLabelsFilename() { return repositoryKey; }
		public void setLabelsFilename(String name) { 
			if (name.endsWith(".labels")) labels(name.substring(0,name.length()-".labels".length()));
			else labels(name);
		}
		public String getRepositoryKey() { return repositoryKey; }
		public void setRepositoryKey(String key) { labels(key); }
		public Object[] getAllowedRepositoryKeyValues() { return FancyLoader.getPossibleTextLabelKeys(); }
		public boolean getShowLabels() { return showLabels; }
		public void setShowLabels(boolean flag ) { showLabels=flag; }
		public boolean getShowResult() { return showResult; }
		public void setShowResult(boolean flag ) { showResult=flag; }
	}
	
	/** Parameters used by all 'train' routines. */
	public static class SaveParams extends BasicCommandLineProcessor {
		public File saveAs=null;
		private String saveAsName=null;
		public void saveAs(String fileName) { this.saveAs = new File(fileName); this.saveAsName=fileName; }
		public void usage() {
			System.out.println("save parameters:");
			System.out.println(" [-saveAs FILE]           save final result of this operation in FILE");
			System.out.println();
		}
		// for gui
		public String getSaveAs() { return saveAsName==null ? "n/a" : saveAsName; }
		public void setSaveAs(String s) { saveAs( "n/a".equals(s) ? null : s ); }
	}

	/** Parameters encoding the 'training signal' for classification learning. */
	public static class ClassificationSignalParams extends BasicCommandLineProcessor {
		private BaseParams base=new BaseParams();
		/** Not recommended, but required for bean-shell like visualization */
		public ClassificationSignalParams() {;}
		public ClassificationSignalParams(BaseParams base) {this.base=base;}
		public String spanProp=null;
		public String spanType=null;
		public String candidateType=null;
		public void candidateType(String s) { this.candidateType=s; }
		public void spanProp(String s) { this.spanProp=s; }
		public void spanType(String s) { this.spanType=s; }
		// useful abstractions
		public String getOutputType(String output) { return spanType==null ? null : output;	}
		public String getOutputProp(String output) { return spanProp==null ? null : output; }
		public void usage() {
			System.out.println("classification 'signal' parameters:");
			System.out.println(" -spanType TYPE           create binary dataset, where candidates that");
			System.out.println("                          are marked with spanType TYPE are positive");
			System.out.println(" -spanProp PROP           create multi-class dataset, where candidates");
			System.out.println("                          are given a class determine by the spanProp PROP");
			System.out.println("                          - exactly one of spanType, spanProp should be specified");
			System.out.println(" [-candidateType TYPE]    classify all spans of the given TYPE");
			System.out.println("                          - default is to classify all document spans");
			System.out.println();
		}
		// for gui
		public String getCandidateType() { return safeGet(candidateType,"top"); }
		public void setCandidateType(String s) { candidateType = safePut(s,"top"); }
		public Object[] getAllowedCandidateTypeValues() { 
			return base.labels==null ? new String[]{} : base.labels.getTypes().toArray();
		}
		public String getSpanProp() { return safeGet(spanProp,"n/a"); }
		public void setSpanProp(String s) { spanProp = safePut(s,"n/a"); }
		public Object[] getAllowedSpanPropValues() {
			return base.labels==null ? new String[]{} : base.labels.getSpanProperties().toArray();
		}
		public String getSpanType() { return safeGet(spanType,"n/a"); }
		public void setSpanType(String s) { spanType = safePut(s,"n/a"); }
		public Object[] getAllowedSpanTypeValues() {
			return base.labels==null ? new String[]{} : base.labels.getTypes().toArray();
		}
		// subroutines for gui
		private String safeGet(String s,String def) { return s==null?def:s; }
		private String safePut(String s,String def) { return def.equals(s)?null:s; }
	}

	/** Parameters for training a classifier. */
	public static class TrainClassifierParams extends BasicCommandLineProcessor {
		public boolean showData=false;
		public ClassifierLearner learner = new Recommended.NaiveBayes();
		public SpanFeatureExtractor fe = new Recommended.DocumentFE();
		public String output="_prediction";
		public void showData() { this.showData=true; }
		public void learner(String s) { this.learner = (ClassifierLearner)newObjectFromBSH(s,ClassifierLearner.class); }
		public void output(String s) { this.output=s; }
		public CommandLineProcessor fe(String s) { 
			this.fe = (SpanFeatureExtractor)newObjectFromBSH(s,SpanFeatureExtractor.class); 
			if (this.fe instanceof CommandLineProcessor.Configurable) {
				return ((CommandLineProcessor.Configurable)this.fe).getCLP();
			} else {
				return null;
			}
		}
		public void usage() {
			System.out.println("classification training parameters:");
			System.out.println(" [-learner BSH]           Bean-shell code to create a ClassifierLearner");
			System.out.println("                          - default is \"new Recommended.NaiveBayes()\"");
			System.out.println(" [-showData]              interactively view the constructed training dataset");
			System.out.println(" [-fe FE]                 Bean-shell code to create a SpanFeatureExtractor");
			System.out.println("                          - default is \"new Recommended.DocumentFE()\"");
			System.out.println("                          - if FE implements CommandLineProcessor.Configurable then" );
			System.out.println("                            immediately following command-line arguments are passed to it");
			System.out.println(" [-output STRING]         the type or property that is produced by the learned");
			System.out.println("                            ClassifierAnnotator - default is \"_prediction\"");
			System.out.println();
		}
		// for gui
		public boolean getShowData() { return showData; }
		public void setShowData(boolean flag) { this.showData=flag; }	
		public ClassifierLearner getLearner() { return learner; }
		public void setLearner(ClassifierLearner learner) { this.learner=learner; }
		public String getOutput() { return output; } 
		public void setOutput(String s) { output(s); }
		public SpanFeatureExtractor getFeatureExtractor() { return fe; }
		public void setFeatureExtractor(SpanFeatureExtractor fe) { this.fe=fe; }
	}

	/** Parameters for testing a stored classifier. */
	public static class TestClassifierParams extends LoadAnnotatorParams {
		public boolean showClassifier=false;
		public boolean showData=false;
		public boolean showTestDetails=false;
		public void showClassifier() { this.showClassifier=true; }
		public void showData() { this.showData=true; }
		public void showTestDetails() { this.showTestDetails=true; }
		public void usage() {
			System.out.println("classifier testing parameters:");
			System.out.println(" -loadFrom FILE           file containing serialized ClassifierAnnotator");
			System.out.println("                          - as learned by TrainClassifier.");
			System.out.println(" [-showData]              interactively view the test dataset");
			System.out.println(" [-showTestDetails]       visualize test examples along with evaluation");
			System.out.println(" [-showClassifier]        interactively view the classifier");
			System.out.println();
		}
		// for gui
		public boolean getShowClassifier() { return showClassifier; }
		public void setShowClassifier(boolean flag) { this.showClassifier=flag; }
		public boolean getShowData() { return showData; }
		public void setShowData(boolean flag) { this.showData=flag; }
		public boolean getShowTestDetails() { return showTestDetails; }
		public void setShowTestDetails(boolean flag) { this.showTestDetails=flag; }
	}

	/** Parameters for testing a stored classifier. */
	public static class TestExtractorParams extends LoadAnnotatorParams {
		public boolean showExtractor=false;
		public void showExtractor() { this.showExtractor=true; }
		public void usage() {
			System.out.println("extractor testing parameters:");
			System.out.println(" -loadFrom FILE           file holding serialized Annotator, learned by TrainExtractor.");
			System.out.println(" [-showExtractor]         interactively view the loaded extractor");
			System.out.println();
		}
		// for gui
		public boolean getShowExtractor() { return showExtractor; }
		public void setShowExtractor(boolean flag) { this.showExtractor=flag; }
	}

	/** Parameters for testing a stored classifier. */
	public static class LoadAnnotatorParams extends BasicCommandLineProcessor {
		public File loadFrom;
		private String loadFromName;
		public void loadFrom(String s) {this.loadFrom = new File(s); this.loadFromName=s; }
		public void usage() {
			System.out.println("annotation loading parameters:");
			System.out.println(" -loadFrom FILE           file containing serialized Annotator");
			System.out.println();
		}
		// for gui
		public String getLoadFrom() { return loadFromName; }
		public void setLoadFrom(String s) { loadFrom(s); }
	}

	/** Parameters for doing train/test evaluation of a classifier. */
	public static class SplitterParams extends BasicCommandLineProcessor {
		public Splitter splitter=new RandomSplitter(0.70); 
		public TextLabels labels=null;
		public boolean showTestDetails=false;
		public void splitter(String s) { this.splitter = toSplitter(s); }
		public void showTestDetails() { this.showTestDetails = true; }
		public void test(String s) { this.labels = FancyLoader.loadTextLabels(s); }
		public void usage() {
			System.out.println("train/test experimentation parameters:");
			System.out.println(" -splitter SPLITTER       specify splitter, e.g. -k5, -s10, -r70");
			System.out.println(" [-showTestDetails]       visualize test examples along with evaluation");
			System.out.println(" -test REPOSITORY_KEY     specify source for test data");
			System.out.println("                         - at most one of -splitter, -test should be specified");
			System.out.println("                           default splitter is r70");
			System.out.println();
		}
		public Splitter getSplitter() { return splitter; }
		public void setSplitter(Splitter splitter) { this.splitter=splitter; }
		public boolean getShowTestDetails() { return showTestDetails; }
		public void setShowTestDetails(boolean flag) { this.showTestDetails=flag; }
	}

	/** Parameters encoding the 'training signal' for extraction learning. */
	public static class ExtractionSignalParams extends BasicCommandLineProcessor {
		private BaseParams base=new BaseParams();
		/** Not recommended, but required for bean-shell like visualization */
		public ExtractionSignalParams() {;}
		public ExtractionSignalParams(BaseParams base) {this.base=base;}
		public String spanType=null;
		public void spanType(String s) { this.spanType=s; }
		public void usage() {
			System.out.println("extraction 'signal' parameters:");
			System.out.println(" -spanType TYPE           create a binary dataset, where subsequences that");
			System.out.println("                          are marked with spanType TYPE are positive");
		}
		// for gui
		public String getSpanType() { return spanType==null?"n/a": spanType; }
		public void setSpanType(String t) { this.spanType = "n/a".equals(t)?null:t; } 
		public Object[] getAllowedSpanTypeValues() { 
			return base.labels==null ? new String[]{} : base.labels.getTypes().toArray();
		}
	}

	/** Parameters encoding the 'training signal' for learning a token-tagger. */
	public static class TaggerSignalParams extends BasicCommandLineProcessor {
		private BaseParams base=new BaseParams();
		/** Not recommended, but required for bean-shell like visualization */
		public TaggerSignalParams() {;}
		public TaggerSignalParams(BaseParams base) {this.base=base;}
		public String tokenProp=null;
		public void tokenProp(String s) { this.tokenProp=s; }
		public void usage() {
			System.out.println("tagger 'signal' parameters:");
			System.out.println(" -tokenProp TYPE          create a sequential dataset, where tokens are");
			System.out.println("                          given the class associated with this token property");
		}
		// for gui
		public String getTokenProp() { return tokenProp==null?"n/a": tokenProp; }
		public void setTokenProp(String t) { this.tokenProp = "n/a".equals(t)?null:t; } 
		public Object[] getAllowedTokenPropValues() { 
			return base.labels==null ? new String[]{} : base.labels.getTokenProperties().toArray();
		}
	}

	/** Parameters for training an extractor. */
	public static class TrainExtractorParams extends BasicCommandLineProcessor {
		public AnnotatorLearner learner = new Recommended.VPHMMLearner();
		private String learnerName;
		public SpanFeatureExtractor fe = null;
		public String output="_prediction";
		public void learner(String s) { 
			this.learnerName = s;
			this.learner = (AnnotatorLearner)newObjectFromBSH(s,AnnotatorLearner.class); 
		}
		public void output(String s) { this.output=s; }
		public CommandLineProcessor fe(String s) { 
			this.fe = (SpanFeatureExtractor)newObjectFromBSH(s,SpanFeatureExtractor.class); 
			if (this.fe instanceof CommandLineProcessor.Configurable) {
				return ((CommandLineProcessor.Configurable)this.fe).getCLP();
			} else {
				return null;
			}
		}
		public void usage() {
			System.out.println("extraction training parameters:");
			System.out.println(" [-learner BSH]           Bean-shell code to create an AnnotatorLearner ");
			//System.out.println("                          - default is \"new Recommended.NaiveBayes()\"");
			System.out.println(" [-fe FE]                 Bean-shell code to create a SpanFeatureExtractor");
			System.out.println("                          - default is \"new Recommended.TokenFE()\"");
			System.out.println("                          - if FE implements CommandLineProcessor.Configurable then" );
			System.out.println("                            immediately following arguments are passed to it");
			System.out.println(" [-output STRING]         the type or property that is produced by the learned");
			System.out.println("                            Annotator - default is \"_prediction\"");
			System.out.println();
		}
		// for gui
		public AnnotatorLearner getLearner() { return learner; }
		public void setLearner(AnnotatorLearner learner) { this.learner=learner; }
		public String getOutput() { return output; }
		public void setOutput(String s) { this.output=s; }
	}

	/** Parameters for training an extractor. */
	public static class TrainTaggerParams extends BasicCommandLineProcessor {
		public SequenceClassifierLearner learner = new Recommended.VPTagLearner();
		public SpanFeatureExtractor fe = new Recommended.TokenFE();
		public String output="_prediction";
		public boolean showData=false;
		public void showData() { this.showData=true; }
		public void learner(String s) { 
			this.learner = (SequenceClassifierLearner)newObjectFromBSH(s,SequenceClassifierLearner.class); 
		}
		public void output(String s) { this.output=s; }
		public CommandLineProcessor fe(String s) { 
			this.fe = (SpanFeatureExtractor)newObjectFromBSH(s,SpanFeatureExtractor.class); 
			if (this.fe instanceof CommandLineProcessor.Configurable) {
				return ((CommandLineProcessor.Configurable)this.fe).getCLP();
			} else {
				return null;
			}
		}
		public void usage() {
			System.out.println("tagger training parameters:");
			System.out.println(" [-learner BSH]           Bean-shell code to create an SequenceClassifierLearner ");
			System.out.println(" [-showData]              interactively view the constructed training dataset");
			System.out.println(" [-fe FE]                 Bean-shell code to create a SpanFeatureExtractor");
			System.out.println("                          - default is \"new Recommended.TokenFE()\"");
			System.out.println("                          - if FE implements CommandLineProcessor.Configurable then" );
			System.out.println("                            immed. following command-line arguments are passed to it");
			System.out.println(" [-output STRING]         the type or property that is produced by the learned");
			System.out.println("                            Annotator - default is \"_prediction\"");
			System.out.println();
		}
		// for gui
		public SequenceClassifierLearner getLearner() { return learner; }
		public void setLearner(SequenceClassifierLearner learner) { this.learner=learner; }
		public String getOutput() { return output; }
		public void setOutput(String s) { this.output=s; }
		public SpanFeatureExtractor getFeatureExtractor() { return fe; }
		public void setFeatureExtractor(SpanFeatureExtractor fe) { this.fe=fe; }
		public boolean getShowData() { return showData; }
		public void setShowData(boolean flag) { this.showData=flag; }	
	}

}
