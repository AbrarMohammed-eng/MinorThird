/* Copyright 2003, Carnegie Mellon, All Rights Reserved */

package edu.cmu.minorthird.classify;



/** A KWayClassifier composed of a bunch of binary classifiers,
 * each of which separates one class from the others.
 *
 * @author William Cohen
 */

public class OneVsAllClassifier implements Classifier
{
	private String[] classNames;
	private BinaryClassifier[] binaryClassifiers;

	/** Create a OneVsAllClassifier.
	 */
	public OneVsAllClassifier(String[] classNames,BinaryClassifier[] binaryClassifiers) {
		if (classNames.length!=binaryClassifiers.length) {
			throw new IllegalArgumentException("arrays must be parallel");
		}
		this.classNames = classNames;
		this.binaryClassifiers = binaryClassifiers;
	}

	public ClassLabel classification(Instance instance) 
	{
		ClassLabel classLabel = new ClassLabel();
		for (int i=0; i<classNames.length; i++) {
			classLabel.add(classNames[i], binaryClassifiers[i].score(instance));
		}
		return classLabel;
	}

	public String explain(Instance instance) 
	{
		StringBuffer buf = new StringBuffer("");
		for (int i=0; i<binaryClassifiers.length; i++) {
			buf.append("score for "+classNames[i]+": ");
			buf.append( binaryClassifiers[i].explain(instance) );
			buf.append( "\n" );
		}
		buf.append( "classification = "+classification(instance).toString() );
		return buf.toString();
	}

	public String[] getClassNames() { return classNames; }

	public String toString() {
		StringBuffer buf = new StringBuffer("[OneVsAllClassifier:\n");
		for (int i=0; i<classNames.length; i++) {
			buf.append(classNames[i]+": "+binaryClassifiers[i]+"\n");
		}
		buf.append("end OneVsAllClassifier]\n");
		return buf.toString();
	}
}

