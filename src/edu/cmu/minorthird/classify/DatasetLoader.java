/* Copyright 2003, Carnegie Mellon, All Rights Reserved */

package edu.cmu.minorthird.classify;

import edu.cmu.minorthird.classify.sequential.SequenceDataset;
import edu.cmu.minorthird.util.ProgressCounter;
import edu.cmu.minorthird.util.StringEncoder;
import edu.cmu.minorthird.util.gui.ViewerFrame;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 * Dataset i/o.
 *
 * For ordinary datasets, format is one example per line, and the
 * format for a line is: 
 * <pre>
 * <code>type subpopid label feature1 feature2 ...</code>
 * </pre>where 
 * <ul>
 * <li>type=b or k (for binary or ordinary examples)
 * <li>subpopid is NUL or a string, naming the subpopulation from which the example was drawn
 * <li>label is +1 or -1 for binary
 * <li>features are a feature name (for binary features) and a featureName=value for numeric
 * features.
 * </ul>
 *
 * For SequenceDatasets, examples from a diffrerent sequence are separated by a single line
 * containing a "*".
 * 
 * @author William Cohen
 */

public class DatasetLoader 
{
	static private Logger log = Logger.getLogger(DatasetLoader.class);

	static private final StringEncoder stringCoder = new StringEncoder('%'," \t");
	static private final StringEncoder featureCoder = new StringEncoder('%',"=. \t");

	static private Map classLabelDict = new HashMap();
	static {
		classLabelDict.put( ExampleSchema.POS_CLASS_NAME, ClassLabel.positiveLabel(+1));
		classLabelDict.put( ExampleSchema.NEG_CLASS_NAME, ClassLabel.negativeLabel(-1));
	}

	/** Save a dataset to a file */
	static public void save(Dataset dataset,File file) throws IOException
	{
		PrintStream out = new PrintStream(new FileOutputStream(file));
		for (Example.Looper i=dataset.iterator(); i.hasNext(); ) {
			out.println( asParsableString(i.nextExample()) );
		}
	}

	/** Load a dataset from a file */
	static public Dataset load(File file) throws IOException,NumberFormatException
	{
		Dataset dataset = new BasicDataset();
		ProgressCounter pc = new ProgressCounter("loading file "+file.getName(), "line");
		LineNumberReader in = new LineNumberReader(new FileReader(file));
		String line;
		while ((line = in.readLine())!=null) {
			dataset.add( parseLine(line,file,in) );
			pc.progress();
		}
		log.info("loaded "+dataset.size()+" examples from "+file.getName());
		in.close();
		pc.finished();
		return dataset;
	}

	/** Save a SequenceDataset to a file */
	static public void saveSequence(SequenceDataset dataset,File file) throws IOException
	{
		PrintStream out = new PrintStream(new FileOutputStream(file));
		for (Iterator i=dataset.sequenceIterator(); i.hasNext(); ) {
			Example[] seq = (Example[]) i.next();
			for (int j=0; j<seq.length; j++) {
				out.println( asParsableString(seq[j]) );
			}
			out.println("*");
		}
		out.close();
	}

	/** Load a SequenceDataset from a file */
	static public SequenceDataset loadSequence(File file) throws IOException,NumberFormatException
	{
		SequenceDataset dataset = new SequenceDataset();
		LineNumberReader in = new LineNumberReader(new FileReader(file));
		String line;
		List list = new ArrayList();
		while ((line = in.readLine())!=null) {
			line = in.readLine();
			if ("*".equals(line)) clearBuffer(list,dataset);
			else list.add( parseLine(line,file,in) );
		}
		if (list.size()>0) clearBuffer(list,dataset);
		log.info("loaded "+dataset.size()+" examples from "+file.getName());
		in.close();
		return dataset;
	}
	static private void clearBuffer(List list,SequenceDataset dataset)
	{
		Example[] seq = (Example[]) list.toArray(new Example[list.size()]);  
		dataset.addSequence( seq );
		list.clear();
	}

	static private String asParsableString(Example x) 
	{	
		StringBuffer buf = new StringBuffer("");
		buf.append((x instanceof BinaryExample) ? "b" : "k" );
		buf.append(' ');
		buf.append(stringCoder.encode(x.getSubpopulationId()!=null? x.getSubpopulationId() : "NUL"));
		buf.append(' ');
		buf.append(stringCoder.encode(x.getLabel().bestClassName()));
		buf.append(' ');
		for (Feature.Looper i=x.binaryFeatureIterator(); i.hasNext(); ) {
			Feature f = i.nextFeature();
			buf.append(' ');
			for (int j=0; j<f.size(); j++) {
				if (j>0) buf.append('.');
				buf.append(featureCoder.encode(f.getPart(j)));
			}
		}
		for (Feature.Looper i=x.numericFeatureIterator(); i.hasNext(); ) {
			Feature f = i.nextFeature();
			buf.append(' ');
			for (int j=0; j<f.size(); j++) {
				if (j>0) buf.append('.');
				buf.append(featureCoder.encode(f.getPart(j)));
			}
			buf.append("="+x.getWeight(f));
		}
		return buf.toString();
	}

	static private Example parseLine(String line, File file,LineNumberReader in)
	{
		String[] arr = line.split("\\s+");
		for (int i=0; i<3; i++) arr[i] = stringCoder.decode(arr[i]);
		if (arr.length<3) 
			throw new IllegalArgumentException("too few values at line#"+in.getLineNumber()+" of "+file.getName());
		String subpopulationId = arr[1];
		String source = file.getName()+":"+in.getLineNumber();
		if ("NUL".equals(arr[1])) subpopulationId = null;
		MutableInstance instance = new MutableInstance(source,subpopulationId);
		for (int i=3; i<arr.length; i++) {
			int eqPos = arr[i].indexOf("=");
			if (eqPos>=0) {
				try {
					String feature = arr[i].substring(0,eqPos);
					String value = arr[i].substring(eqPos+1);
					String[] featureParts = feature.split("\\.");
					for (int j=0; j<featureParts.length; j++) 
						featureParts[j] = featureCoder.decode(featureParts[j]);
					double weight = Double.parseDouble(value); 
					instance.addNumeric( Feature.Factory.getFeature(feature), weight); //new Feature(featureParts), weight);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("bad feature# "+i+" line#"+in.getLineNumber()+" of "+file.getName());
				}
			} else {
				instance.addBinary( Feature.Factory.getFeature(arr[i].split("\\."))); // Feature(arr[i].split("\\.")) );
			}
		}
		ClassLabel label = (ClassLabel) classLabelDict.get(arr[2]);
		if (label==null) {
			if ("b".equals(arr[0])) {
				throw new IllegalArgumentException(
					"should be POS/NEG but label is '"+arr[2]+"' at line#"+in.getLineNumber()+" of "+file.getName());
			}
			classLabelDict.put( arr[2], (label = new ClassLabel(arr[2])) );
		}
		if ("b".equals(arr[0])) {
			return new BinaryExample(instance,label.numericScore());
		} else {
			return new Example(instance,label);
		}
	}


  public static Dataset loadSVMStyle(File file) throws IOException
  {
    Dataset dataset = new BasicDataset();
    BufferedReader in = new BufferedReader(new FileReader(file));
    while (in.ready())
    {
      String line = in.readLine();
      StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");

      //label - yes this is necessary:
      //the original string representation and the reconstituted versions are different.
      //ex: (string)+1 => (double)1.0 => (string)1.0
      MutableInstance instance = new MutableInstance();
      String label = st.nextToken();
      double labelDouble = Double.parseDouble(label);
      label = "" + labelDouble;

      //num features
//      int numFeatures = st.countTokens()/2;
      while (st.hasMoreTokens())
      {
        //add features to instance
        // note for svm these should be numeric
        String featureName = st.nextToken();
        String featureValue = st.nextToken();
        instance.addNumeric(new Feature(featureName), Double.parseDouble(featureValue));
      }

      //build Example
      Example example = new Example(instance, ClassLabel.binaryLabel(labelDouble));

      dataset.add(example);
    }

    return dataset;
  }

	static public void main(String[] args)
	{
		try {
			boolean sequential = "-sequential".equals(args[0]);
			String dbName =  sequential ? args[1] : args[0];
			DatasetLoader loader = new DatasetLoader();
			Dataset d = sequential ? loader.loadSequence(new File(dbName)) : loader.load(new File(dbName));
			ViewerFrame f = new ViewerFrame("Data from "+dbName, d.toGUI());
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("usage: file");
		}
	}
}
