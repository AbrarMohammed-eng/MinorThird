/* Copyright 2003, Carnegie Mellon, All Rights Reserved */

package edu.cmu.minorthird.classify.experiments;

import edu.cmu.minorthird.classify.*;
import edu.cmu.minorthird.classify.sequential.SequenceClassifier;
import edu.cmu.minorthird.classify.sequential.SequenceDataset;
import edu.cmu.minorthird.util.*;
import edu.cmu.minorthird.util.gui.*;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Stores some detailed results of evaluating a classifier on data.
 *
 * @author William Cohen
 */

public class Evaluation implements Visible,Serializable
{
	private static Logger log = Logger.getLogger(Evaluation.class);
	private static final boolean DEBUG = log.getEffectiveLevel().isGreaterOrEqual( Level.DEBUG );

	// serialization stuff
  static private final long serialVersionUID = 1;
	private final int CURRENT_VERSION_NUMBER = 1;

	//
	// private data
	//

	// all entries
	private ArrayList entryList = new ArrayList();
	// cached values
	transient private Matrix cachedPRCMatrix = null;
	transient private Matrix cachedConfusionMatrix = null;
	// dataset schema
	private ExampleSchema schema;
	// properties
	private Properties properties = new Properties();
	private ArrayList propertyKeyList = new ArrayList();
	// are all classes binary?
	private boolean isBinary = true;

	/** Create an evaluation for databases with this schema */

	public Evaluation(ExampleSchema schema)
	{
		this.schema = schema;
		isBinary = schema.equals(ExampleSchema.BINARY_EXAMPLE_SCHEMA);
	}
	

	/** Test the classifier on the examples in the dataset and store the results. */
	public void extend(Classifier c,Dataset d)
	{
		ProgressCounter pc = new ProgressCounter("classifying","examples",d.size());
		for (Example.Looper i=d.iterator(); i.hasNext(); ) {
			Example ex = i.nextExample();
			ClassLabel p = c.classification( ex );
			extend(p,ex);
			pc.progress();
		}
		pc.finished();
	}

	/** Test the SequenceClassifier on the examples in the dataset and store the results. */
	public void extend(SequenceClassifier c,SequenceDataset d)
	{
		for (Iterator i=d.sequenceIterator(); i.hasNext(); ) {
			Example[] seq = (Example[])i.next();
			ClassLabel[] pred = c.classification( seq );
			for (int j=0; j<seq.length; j++) {
				extend( pred[j], seq[j] );
			}
		}
	}

	/** Record the result of predicting the give class label on the given example */
	public void extend(ClassLabel predicted, Example example)
	{
		if (DEBUG) {
			String ok = predicted.isCorrect(example.getLabel()) ? "Y" : "N";
			log.debug("ok: "+ok+"\tpredict: "+predicted+"\ton: "+example);
		}
		entryList.add( new Entry(example.asInstance(), predicted, example.getLabel(), entryList.size()) );
		if (!example.getLabel().isBinary()) isBinary = false;
		if (!predicted.isBinary()) isBinary = false;
		// clear caches
		cachedPRCMatrix = null;
	}

	public void setProperty(String prop,String value)
	{
		if (properties.getProperty(prop)==null) {
			propertyKeyList.add(prop);
		}
		properties.setProperty(prop,value);
	}
	public String getProperty(String prop)
	{
		return properties.getProperty(prop,"=unassigned=");
	}

	//
	// simple statistics
	//

	/** Weighted total errors. */
	public double errors() 
	{
		double errs = 0;
		for (int i=0; i<entryList.size(); i++) {
			Entry e = getEntry(i);
			errs += e.predicted.isCorrect(e.actual) ? 0 : e.w;
		}
		return errs;
	}

    /** Weighted total errors on POSITIVE examples. */
    public double errorsPos()
    {
        double errsPos = 0;
        for (int i=0; i<entryList.size(); i++) {
            Entry e = getEntry(i);
            if ( "POS".equals(e.actual.bestClassName()) ) {
                errsPos += e.predicted.isCorrect(e.actual) ? 0 : e.w;
            }
        }
        return errsPos;
    }

    /** Weighted total errors on NEGATIVE examples. */
    public double errorsNeg()
    {
        double errsNeg = 0;
        for (int i=0; i<entryList.size(); i++) {
            Entry e = getEntry(i);
            if ( "NEG".equals(e.actual.bestClassName()) ) {
                errsNeg += e.predicted.isCorrect(e.actual) ? 0 : e.w;
            }
        }
        return errsNeg;
    }

	/** Total weight of all instances. */
	public double numberOfInstances() 
	{
		double n = 0;
		for (int i=0; i<entryList.size(); i++) {
            n += getEntry(i).w;
		}
		return n;
	}

    /** Total weight of all POSITIVE examples. */
    public double numberOfPositiveExamples()
    {
        double n = 0;
        for (int i=0; i<entryList.size(); i++) {
            Entry e = getEntry(i);
            if ( "POS".equals(e.actual.bestClassName()) ) {
			    n += e.w;
            }
        }
        return n;
    }

    /** Total weight of all NEGATIVE examples. */
    public double numberOfNegativeExamples()
    {
        double n = 0;
        for (int i=0; i<entryList.size(); i++) {
            Entry e = getEntry(i);
            if ( "NEG".equals(e.actual.bestClassName()) ) {
			    n += e.w;
            }
        }
        return n;
    }

	/** Error rate. */
	public double errorRate() 
	{
		return errors()/numberOfInstances();
	}

    /** Error rate on Positive examples. */
    public double errorRatePos()
    {
        return errorsPos()/numberOfPositiveExamples();
    }

    /** Error rate on Negative examples. */
    public double errorRateNeg()
    {
        return errorsNeg()/numberOfNegativeExamples();
    }

    /** Balanced Error rate. */
    public double errorRateBalanced()
    {
        return 0.5*(errorsPos()/numberOfPositiveExamples())+0.5*(errorsNeg()/numberOfNegativeExamples());
    }

	/** Non-interpolated average precision. */
	public double averagePrecision()
	{
		if (!isBinary) return -1;

		double total=0, n=0;
		Matrix m = precisionRecallScore();
		double lastRecall = 0;
		for (int i=0; i<m.values.length; i++) {
			if (m.values[i][1] > lastRecall) {
				n++;
				total += m.values[i][0];
			}
			lastRecall = m.values[i][1];
		}
		return total/n;
	}

	/** Max f1 values at any cutoff. */
	public double maxF1()
	{
		if (!isBinary) return -1;

		double maxF1 = 0;
		Matrix m = precisionRecallScore();
		for (int i=0; i<m.values.length; i++) {
			double p = m.values[i][0];
			double r = m.values[i][1];
			if (p>0 || r>0) {
				double f1 = (2*p*r) / (p+r);
				maxF1 = Math.max(maxF1, f1);
			}
		}
		return maxF1;
	}

	/** Average logloss on all examples. */
	public double averageLogLoss()
	{
		double tot = 0;
		for (int i=0; i<entryList.size(); i++) {
			Entry e = getEntry(i);
			double confidence = e.predicted.getWeight( e.actual.bestClassName() );
			double error = e.predicted.isCorrect(e.actual) ? +1 : -1;
			tot += Math.log( 1.0 + Math.exp( confidence * error ) );
		}
		return tot/entryList.size();
	}
		 
	public double precision()
	{
		Matrix cm = confusionMatrix(); 
		int p = classIndexOf(ExampleSchema.POS_CLASS_NAME);
		int n = classIndexOf(ExampleSchema.NEG_CLASS_NAME);
		//cm is actual, predicted
		return cm.values[p][p]/(cm.values[p][p] + cm.values[n][p]);
	}
	public double recall()
	{
		Matrix cm = confusionMatrix(); 
		int p = classIndexOf(ExampleSchema.POS_CLASS_NAME);
		int n = classIndexOf(ExampleSchema.NEG_CLASS_NAME);
		//cm is actual, predicted
		return cm.values[p][p]/(cm.values[p][p] + cm.values[p][n]);
	}
	public double f1()
	{
		double p = precision();
		double r = recall();
		return (2*p*r) / (p+r);
	}
	public double[] summaryStatistics()
	{
		return new double[] { 
			errorRate(),
			errorRateBalanced(),
			errorRatePos(),
			errorRateNeg(),
			averagePrecision(),
			maxF1(),
			averageLogLoss(), 
			recall(),
			precision(),
			f1()
		};
	}
	static public String[] summaryStatisticNames()
	{
		return new String[] { 
			"Error Rate",
            "Balanced Error Rate",
            "- Error Rate on Positive exs.",
            "- Error Rate on Negative exs.",
			"Average Precision",
            "Maximium F1",
			"Average Log Loss",
			"Recall",
			"Precision",
			"F1" };
	}

	//
	// complex statistics, ie ones that are harder to visualize
	//

	private static class Matrix {
		public double[][] values;
		public Matrix(double[][] values) { this.values=values; }
		public String toString()
		{
			StringBuffer buf = new StringBuffer("");
			for (int i=0; i<values.length; i++) {
				buf.append(StringUtil.toString(values[i])+"\n");
			}
			return buf.toString();
		}
	}

	/** Return a confusion matrix.
	 */
	public Matrix confusionMatrix()
	{
		if (cachedConfusionMatrix!=null) return cachedConfusionMatrix;

		String[] classes = getClasses();
		// count up the errors
		double[][] confused = new double[classes.length][classes.length];
		for (int i=0; i<entryList.size(); i++) {
			Entry e = getEntry(i);
			confused[classIndexOf(e.actual)][classIndexOf(e.predicted)]++;
		}
		cachedConfusionMatrix = new Matrix(confused);
		return cachedConfusionMatrix;
	}

	public String[] getClasses()
	{
		return schema.validClassNames();
	}

	/** Return array of precision,recall,logitScore.
	 */
	public Matrix precisionRecallScore()
	{
		if (cachedPRCMatrix!=null) return cachedPRCMatrix;

		if (!isBinary) 
			throw new IllegalArgumentException("can't compute precisionRecallScore for non-binary data");
		byBinaryScore();
		int allActualPos = 0;
		int lastIndexOfActualPos = 0;
		ProgressCounter pc = new ProgressCounter("counting positive examples","examples",entryList.size());
		for (int i=0; i<entryList.size(); i++) {
			if (getEntry(i).actual.isPositive()) {
				allActualPos++;
				lastIndexOfActualPos = i;
			}
			pc.progress();
		}
		pc.finished();
		double truePosSoFar = 0;
		double falsePosSoFar = 0;
		double precision=1, recall=1, score=0;
		ProgressCounter pc2 = new ProgressCounter("computing statistics","examples",lastIndexOfActualPos);
		double[][] result = new double[lastIndexOfActualPos+1][3];
		for (int i=0; i<=lastIndexOfActualPos; i++) {
			Entry e = getEntry(i);
			score = e.predicted.posWeight();
			if (e.actual.isPositive()) truePosSoFar++;
			else falsePosSoFar++;
			if (truePosSoFar+falsePosSoFar>0) precision = truePosSoFar/(truePosSoFar + falsePosSoFar);
			if (allActualPos>0) recall = truePosSoFar/allActualPos;
			result[i][0] = precision;
			result[i][1] = recall;
			result[i][2] = score;
			pc2.progress();
		}
		pc2.finished();
		cachedPRCMatrix = new Matrix(result);
		return cachedPRCMatrix;
	}

	/** Return eleven-point interpolated precision.
	 * Precisely, result is an array p[] of doubles
	 * such that p[i] is the maximal precision value
	 * for any point with recall>=i/10.
	 *
	 */
	public double[] elevenPointPrecision()
	{
		Matrix m = precisionRecallScore();
		//System.out.println("prs = "+m);
		double[] p = new double[11];
		p[0] = 1.0;
		for (int i=0; i<m.values.length; i++) {
			double r = m.values[i][1];
			//System.out.println("row "+i+", recall "+r+": "+StringUtil.toString(m.values[i]));
			for (int j=1; j<=10; j++) {
				if (r >= j/10.0) {
					p[j] = Math.max(p[j],m.values[i][0]);
					//System.out.println("update p["+j+"] => "+p[j]);
				}
			}
		}
		return p;
	}


	//
	// views of data
	//

	/** Detailed view. */
	public String toString()
	{
		StringBuffer buf = new StringBuffer(""); 
		for (int i=0; i<entryList.size(); i++) {
			buf.append( getEntry(i) + "\n" );
		}
		return buf.toString();
	}

	static public class PropertyViewer extends ComponentViewer
	{
		public JComponent componentFor(Object o) 
		{
			final Evaluation e = (Evaluation)o;
			final JPanel panel = new JPanel(); 
			final JTextField propField = new JTextField(10);
			final JTextField valField = new JTextField(10);
			final JTable table = makePropertyTable(e);
			final JScrollPane tableScroller = new JScrollPane(table);
			final JButton addButton = 
				new JButton(new AbstractAction("Insert Property") {
						public void actionPerformed(ActionEvent event) {
							e.setProperty(propField.getText(), valField.getText());
							tableScroller.getViewport().setView(makePropertyTable(e));
							tableScroller.revalidate();
							panel.revalidate();
						}
					});
			panel.setLayout(new GridBagLayout());
			GridBagConstraints gbc = fillerGBC(); 
			//gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.gridwidth=3;
			panel.add(tableScroller,gbc);
			panel.add(addButton,myGBC(0));
			panel.add(propField,myGBC(1));
			panel.add(valField,myGBC(2));
			return panel;
		}
		private GridBagConstraints myGBC(int col) {
			GridBagConstraints gbc = fillerGBC();
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.gridx = col;
			gbc.gridy = 1;
			return gbc;
		}
		private JTable makePropertyTable(final Evaluation e) 
		{
			Object[][] table = new Object[e.propertyKeyList.size()][2];
			for (int i=0; i<e.propertyKeyList.size(); i++) {
				table[i][0] = e.propertyKeyList.get(i);
				table[i][1] = e.properties.get(e.propertyKeyList.get(i));
			}
			String[] colNames = new String[] { "Property", "Property's Value" };
			return new JTable(table,colNames);
		}
	}

	static public class SummaryViewer extends ComponentViewer {
		public JComponent componentFor(Object o) {
			Evaluation e = (Evaluation)o;
			double[] ss = e.summaryStatistics();
			String[] ssn = e.summaryStatisticNames();
			Object[][] oss = new Object[ss.length][2];
			for (int i=0; i<ss.length; i++) {
				oss[i][0] = ssn[i];
				oss[i][1] = new Double(ss[i]);
			}
			return new JScrollPane(new JTable(oss,new String[] { "Statistic", "Value" }));
		}
	}

	static public class ElevenPointPrecisionViewer extends ComponentViewer {
		public JComponent componentFor(Object o) {
			Evaluation e = (Evaluation)o;
			double[] p = e.elevenPointPrecision();
			LineCharter lc = new LineCharter();
			lc.startCurve("Interpolated Precision");
			for (int i=0; i<p.length; i++) {
				lc.addPoint( i/10.0, p[i] );
			}
			return lc.getPanel("11-Pt Interpolated Precision vs. Recall", "Recall", "Precision"); 
		}
	}

	static public class ConfusionMatrixViewer extends ComponentViewer
	{
		public JComponent componentFor(Object o) {
			Evaluation e = (Evaluation)o;
			JPanel panel = new JPanel();
			Matrix m = e.confusionMatrix();
			String[] classes = e.getClasses();
			panel.setLayout(new GridBagLayout());
			//add( new JLabel("Actual class"), cmGBC(0,0) );
			GridBagConstraints gbc = cmGBC(0,1);
			gbc.gridwidth = classes.length;
			panel.add( new JLabel("Predicted Class"), gbc );
			for (int i=0; i<classes.length; i++) {
				panel.add( new JLabel(classes[i]), cmGBC(1,i+1) );
			}
			for (int i=0; i<classes.length; i++) {
				panel.add( new JLabel(classes[i]), cmGBC(i+2,0) );
				for (int j=0; j<classes.length; j++) {
					panel.add( new JLabel(Double.toString(m.values[i][j])), cmGBC( i+2,j+1 ) );
				}
			}
			return panel;
		}
		private GridBagConstraints cmGBC(int i,int j)
		{
			GridBagConstraints gbc = new GridBagConstraints();
			//gbc.fill = GridBagConstraints.BOTH;
			gbc.weightx = gbc.weighty = 0;
			gbc.gridy = i;
			gbc.gridx = j;
			gbc.ipadx = gbc.ipady = 20;
			return gbc;
		}
	}


	public Viewer toGUI()
	{
		ParallelViewer main = new ParallelViewer();

		main.addSubView("Summary",new SummaryViewer());
		main.addSubView("Properties",new PropertyViewer());
		/*
		Viewer prViewer = new ComponentViewer() {
				public JComponent componentFor(Object o) {
					Evaluation e = (Evaluation)o;
					Matrix m = e.precisionRecallScore();
					LineCharter lc = new LineCharter();
					lc.startCurve("Raw Precision");
					for (int i=0; i<m.values.length; i++) {
						lc.addPoint(m.values[i][1], m.values[i][0]);
					}
					return lc.getPanel("Precision vs Recall", "Recall", "Precision");
				}
			};
		main.addSubView("Raw Precision/Recall",prViewer);
		*/
		if (isBinary) {
			main.addSubView("11Pt Precision/Recall",new ElevenPointPrecisionViewer());
		}
		main.addSubView("Confusion Matrix",new ConfusionMatrixViewer());
		main.addSubView("Debug", new VanillaViewer());
		main.setContent(this);

		return main;
	}


	//
	// one entry in the evaluation
	//
	private static class Entry implements Serializable 
	{
		private static final long serialVersionUID = -4069980043842319179L;
		transient public Instance instance = null;
		public int index;
		public ClassLabel predicted,actual;
		public double w;
		public int h;
		public Entry(Instance i,ClassLabel p,ClassLabel a,int k) 
		{ 
			instance=i; predicted=p; actual=a;	index=k;
			h=instance.hashCode(); 
			w=instance.getWeight();
		}
		public String toString() 
		{
			double w = predicted.bestWeight();
			return predicted+"\t"+actual+"\t"+instance; 
		}
	}

	//
	// i/o
	//
	public void save(File file) throws IOException
	{
		PrintStream out = new PrintStream(new GZIPOutputStream(new FileOutputStream(file)));
		out.println(StringUtil.toString( schema.validClassNames() ));
		for (Iterator i=propertyKeyList.iterator(); i.hasNext(); ) {
			String prop = (String)i.next();
			String value = properties.getProperty(prop);
			out.println(prop+"="+value);
		}
		byOriginalPosition();
		for (Iterator i=entryList.iterator(); i.hasNext(); ) {
			Entry e = (Entry)i.next();
			out.println(
				e.predicted.bestClassName() +" "+ 
				e.predicted.bestWeight() +" "+  
				e.actual.bestClassName() +" "+
				e.w);
		}
		out.close();
	}
	static public Evaluation load(File file) throws IOException
	{
		// first try loading a serialized version
		try {
			return (Evaluation)IOUtil.loadSerialized(file);
		} catch (Exception ex) {
			;
		}
		LineNumberReader in = 
			new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
		String line = in.readLine();
		if (line==null) throw new IllegalArgumentException("no class list on line 1 of file "+file.getName());
		String[] classes = line.substring(1,line.length()-1).split(",");
		ExampleSchema schema = new ExampleSchema(classes);
		Evaluation result = new Evaluation(schema);
		while ((line = in.readLine())!=null) {
			if (line.indexOf('=')>=0) {
				// property
				String[] propValue = line.split("=");
				if (propValue.length!=2) {
					throw new IllegalArgumentException(
						file.getName()+" line "+in.getLineNumber()+": illegal format");					
				}
				result.setProperty(propValue[0],propValue[1]);
			} else {
				String[] words = line.split(" ");
				if (words.length!=4) 
					throw new IllegalArgumentException(
						file.getName()+" line "+in.getLineNumber()+": illegal format");
				ClassLabel predicted = new ClassLabel(words[0],StringUtil.atof(words[1]));
				ClassLabel actual = new ClassLabel(words[2]);
				double instanceWeight = StringUtil.atof(words[3]);
				MutableInstance instance = new MutableInstance("dummy");
				instance.setWeight( instanceWeight );
				Example example = new Example(instance, actual );
				result.extend( predicted, example );
			}
		}
		in.close();
		return result;
	}


	//
	// convenience methods
	//
	private Entry getEntry(int i) 
	{ 
		return (Entry)entryList.get(i); 
	}
	private int classIndexOf(ClassLabel classLabel)
	{
		return classIndexOf(classLabel.bestClassName());
	}
	private int classIndexOf(String classLabelName)
	{
		int result = schema.getClassIndex(classLabelName);
		if (result>=0) return result;
		throw new IllegalArgumentException("no class "+classLabelName+" in "+schema);
	}

	private void byBinaryScore()
	{
		Collections.sort(
			entryList, 
			new Comparator() {
				public int compare(Object a, Object b) {
					return MathUtil.sign( ((Entry)b).predicted.posWeight() - ((Entry)a).predicted.posWeight() );
				}
			});
	}
	
	private void byOriginalPosition()
	{
		Collections.sort(
			entryList, 
			new Comparator() {
				public int compare(Object a, Object b) {
					return ((Entry)a).index - ((Entry)b).index;
				}
			});
	}

	//
	// test routine
	//
	static public void main(String[] args)
	{
		try {
			Evaluation v = Evaluation.load(new File(args[0]));
			if (args.length>1) v.save(new File(args[1]));
			ViewerFrame f = new ViewerFrame("From file "+args[0], v.toGUI());
		} catch (Exception e) {
			System.out.println("usage: Evaluation [serializedFile|evaluationFile] [evaluationFile]");
			e.printStackTrace();
		}
	}
}
