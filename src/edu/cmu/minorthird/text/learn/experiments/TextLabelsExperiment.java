package edu.cmu.minorthird.text.learn.experiments;

import edu.cmu.minorthird.util.gui.*;
import edu.cmu.minorthird.classify.*;
import edu.cmu.minorthird.classify.sequential.*;
import edu.cmu.minorthird.classify.experiments.*;
import edu.cmu.minorthird.text.*;
import edu.cmu.minorthird.text.gui.*;
import edu.cmu.minorthird.text.learn.*;
import edu.cmu.minorthird.util.ProgressCounter;

import java.io.File;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;

/** Run an annotation-learning experiment based on pre-labeled text.
 *
 * @author William Cohen
*/

public class TextLabelsExperiment implements Visible
{
	private SampleFE.ExtractionFE fe = new SampleFE.ExtractionFE();
	private int classWindowSize = 3;

	private TextLabels labels;
	private Splitter splitter;
	private AnnotatorLearner learner;
	private String inputLabel;
	private MonotonicTextLabels fullTestLabels;
	private MonotonicTextLabels[] testLabels;
	private String outputLabel;
	private Annotator[] annotators;
  private static Logger log = Logger.getLogger(TextLabelsExperiment.class);

  /**
   * @param labels The labels and base to be annotated in the example
   *               These are the training examples
   * @param splitter splitter for the documents in the labels to create test vs. train
   * @param learnerName AnnotatorLearner algorithm object to use
   * @param inputLabel spanType in the TextLabels to use as training data. (I.e.,
	 *   the spanType to learn.
   * @param outputLabel the spanType that will be assigned to spans predicted
	 * to be of type inputLabel by the learner (I.e., the output type associated
	 * with the learned annotator.)
   */
	public TextLabelsExperiment(
		TextLabels labels,Splitter splitter,String learnerName,String inputLabel,String outputLabel) 
	{
		this.labels = labels;
		this.splitter = splitter;
		this.inputLabel = inputLabel;
		this.outputLabel = outputLabel;
		this.learner = toAnnotatorLearner(learnerName);
		learner.setAnnotationType( outputLabel );
	}

	public TextLabelsExperiment(
		TextLabels labels,Splitter splitter,AnnotatorLearner learner,String inputLabel,String outputLabel)
	{
		this.labels = labels;
		this.splitter = splitter;
		this.inputLabel = inputLabel;
		this.outputLabel = outputLabel;
		this.learner = learner;
		learner.setAnnotationType( outputLabel );
	}

	public SampleFE.ExtractionFE getFE() { 
		return fe; 
	}

	public void doExperiment() 
	{

		splitter.split( labels.getTextBase().documentSpanIterator() );

		annotators = new Annotator[ splitter.getNumPartitions() ];
		Set allTestDocuments = new TreeSet();
		for (int i=0; i<splitter.getNumPartitions(); i++) {
			for (Iterator j=splitter.getTest(i); j.hasNext(); ) 
				allTestDocuments.add( j.next() );
		}
    //Progress counter
    ProgressCounter progressCounter = new ProgressCounter("Text Labels Experiment", splitter.getNumPartitions());

		SubTextBase fullTestBase = new SubTextBase( labels.getTextBase(), allTestDocuments.iterator() );
		fullTestLabels = new NestedTextLabels( new SubTextLabels( fullTestBase, labels ) );
		testLabels = new MonotonicTextLabels[ splitter.getNumPartitions() ];

		for (int i=0; i<splitter.getNumPartitions(); i++) {
			log.info("For partition "+(i+1)+" of "+splitter.getNumPartitions());
			log.info("Creating teacher and train partition...");

			SubTextBase trainBase = new SubTextBase( labels.getTextBase(), splitter.getTrain(i) );
			SubTextLabels trainLabels = new SubTextLabels( trainBase, labels );
			AnnotatorTeacher teacher = new TextLabelsAnnotatorTeacher( trainLabels, inputLabel );

      log.info("Training annotator...");
			annotators[i] = teacher.train( learner );

			/*
			if (learner instanceof SequenceAnnotatorLearner) {
				new ViewerFrame(
					"Dataset",
					((SequenceAnnotatorLearner)learner).getSequenceDataset().toGUI());
			}
			*/

      //log.info("annotators["+i+"]="+annotators[i]);
			log.info("Creating test partition...");
			SubTextBase testBase = new SubTextBase( labels.getTextBase(), splitter.getTest(i) );
			testLabels[i] = new MonotonicSubTextLabels( testBase, fullTestLabels );

      log.info("Labeling test partition...");
			annotators[i].annotate( testLabels[i] );

			log.info("Evaluating test partition...");
			measurePrecisionRecall("Test partition "+(i+1)+":",testLabels[i]);

      //step progress counter
      progressCounter.progress();
    }
		log.info("\nOverall performance:");
		measurePrecisionRecall( "Overall performance", fullTestLabels );

    //end progress counter
    progressCounter.finished();
  }

	public Viewer toGUI()
	{
		ParallelViewer v = new ParallelViewer();
		for (int i=0; i<annotators.length; i++) {
			final int index = i;
			v.addSubView( "Annotator "+(i+1), new TransformedViewer(new SmartVanillaViewer()) {
					public Object transform(Object o) {
						return annotators[index];
					}
				});
			v.addSubView( "Test set "+(i+1), new TransformedViewer(new SmartVanillaViewer()) {
					public Object transform(Object o) {
						return testLabels[index];
					}
				});
		}
		v.setContent( this );
		return v;
	}

	private void measurePrecisionRecall(String tag,TextLabels labels)
	{
		SpanDifference sd =
			new SpanDifference( 
				labels.instanceIterator(outputLabel),
				labels.instanceIterator(inputLabel),
				labels.closureIterator(inputLabel) );
		System.out.println(tag);
		double tokenF = 2*sd.tokenPrecision()*sd.tokenRecall()/(sd.tokenPrecision()+sd.tokenRecall());
		System.out.println("TokenPrecision: "+sd.tokenPrecision()+" TokenRecall: "+sd.tokenRecall()+" F: "+tokenF);
		double spanF = 2*sd.spanPrecision()*sd.spanRecall()/(sd.spanPrecision()+sd.spanRecall());
		System.out.println("SpanPrecision:  "+sd.spanPrecision() +" SpanRecall:  "+sd.spanRecall()+" F: "+spanF);
	}

	public AnnotatorLearner toAnnotatorLearner(String s)
	{
		try {
			OnlineBinaryClassifierLearner learner = (OnlineBinaryClassifierLearner)Expt.toLearner(s);
			BatchSequenceClassifierLearner seqLearner = 
				new GenericCollinsLearner(learner,classWindowSize);
			return new SequenceAnnotatorLearner(seqLearner, fe, classWindowSize);
		} catch (IllegalArgumentException ex) {
			/* that's ok, maybe it's something else */ ;
		}
		try {
			BatchSequenceClassifierLearner seqLearner = 
				(BatchSequenceClassifierLearner)SequenceAnnotatorExpt.toSeqLearner(s);
			return new SequenceAnnotatorLearner(seqLearner, fe, classWindowSize);
		} catch (IllegalArgumentException ex) {
			/* that's ok, maybe it's something else */ ;
		}
		try {
			bsh.Interpreter interp = new bsh.Interpreter();
			interp.eval("import edu.cmu.minorthird.text.*;");
			interp.eval("import edu.cmu.minorthird.text.learn.*;");
			interp.eval("import edu.cmu.minorthird.classify.*;");
			interp.eval("import edu.cmu.minorthird.classify.experiments.*;");
			interp.eval("import edu.cmu.minorthird.classify.algorithms.linear.*;");
			interp.eval("import edu.cmu.minorthird.classify.algorithms.trees.*;");
			interp.eval("import edu.cmu.minorthird.classify.algorithms.knn.*;");
			interp.eval("import edu.cmu.minorthird.classify.algorithms.svm.*;");
			interp.eval("import edu.cmu.minorthird.classify.sequential.*;");
			return (AnnotatorLearner)interp.eval(s);
		} catch (bsh.EvalError e) {
			throw new IllegalArgumentException("error parsing learnerName '"+s+"':\n"+e);
		}
	}

	public TextLabels getTestLabels() { return fullTestLabels; }

	public static void main(String[] args) 
	{
		Splitter splitter=new RandomSplitter(0.7);
		String outputLabel="_prediction"; 
		String learnerName="new CollinsPerceptronLearner()";
		TextLabels labels=null;
		String inputLabel=null, saveFileName=null, show=null, annotationNeeded=null;
		try {
			int pos = 0;
			while (pos<args.length) {
				String opt = args[pos++];
				if (opt.startsWith("-lab")) {
					labels = FancyLoader.loadTextLabels(args[pos++]);
				} else if (opt.startsWith("-lea")) {
					learnerName = args[pos++];
				} else if (opt.startsWith("-split")) {
					splitter = Expt.toSplitter(args[pos++]);
				} else if (opt.startsWith("-in")) {
					inputLabel = args[pos++];
				} else if (opt.startsWith("-out")) {
					outputLabel = args[pos++];
				} else if (opt.startsWith("-save")) {
					saveFileName = args[pos++];
				} else if (opt.startsWith("-show")) {
					show = args[pos++];
				} else if (opt.startsWith("-mix")) {
					annotationNeeded = args[pos++];
				} else {
					usage();
				}
			}
			if (labels==null || learnerName==null || splitter==null|| inputLabel==null || outputLabel==null) {
				usage();
			}
      TextLabelsExperiment expt = new TextLabelsExperiment(labels,splitter,learnerName,inputLabel,outputLabel);
			if (annotationNeeded!=null) {
				expt.getFE().setRequiredAnnotation(annotationNeeded);
				expt.getFE().setAnnotationProvider(annotationNeeded+".mixup");
				expt.getFE().setTokenPropertyFeatures("*"); // use all defined properties
				labels.require(annotationNeeded,annotationNeeded+".mixup");
			}
			expt.doExperiment();
			if (saveFileName!=null) {
				new TextLabelsLoader().saveTypesAsOps( expt.getTestLabels(), new File(saveFileName) );
			}
			if (show!=null) {
				TextBaseViewer.view( expt.getTestLabels() );
				if (show.startsWith("all")) {
					new ViewerFrame("Experiment", expt.toGUI());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			usage();
		}
	}
	private static void usage() {
		String[] usageLines = new String[] {
			"usage: options are:",
			"       -label labelsKey   dataset to load",
			"       -input inputLabel  label that defines the extraction target",
			"       -learn learner     Java code to construct the learner, which could be an ",
			"                          an AnnotatorLearner, a BatchSequenceClassifierLearner, or an OnlineClassifierLearner",
			"                          - a BatchSequenceClassifierLearner is used to defined a SequenceAnnotatorLearner",
			"                          and an OnlineClassifierLearner is used to define a GenericCollinsLearner", 
			"                          optional, default \"new CollinsPerceptronLearner()\"",
			"       -out outputLabel   label assigned to predictions",
			"                          optional, default _prediction", 
			"       -split splitter    splitter to use, in format used by minorthird.classify.experiments.Expt.toSplitter()",
			"                          optional, default r70",
			"       -save fileName     file to save extended TextLabels in (train data + predictions)",
			"                          optional",
			"       -show xxxx         how much detail on experiment to show - xxx=all shows the most",
			"                          optional", 
			"       -mix yyyy          augment feature extracture to first execute 'require yyyy,yyyy.mixup'",
			"                          optional",
		};
		for (int i=0; i<usageLines.length; i++) System.out.println(usageLines[i]);
		System.exit(-1);
	}
}

