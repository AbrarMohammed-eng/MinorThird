package edu.cmu.minorthird.classify;

import edu.cmu.minorthird.classify.algorithms.linear.*;

import java.util.*;

/**
 * Multi-class version of a binary classifier.
 *
 * @author William Cohen
 */

public class OneVsAllLearner implements ClassifierLearner
{
    protected ClassifierLearnerFactory learnerFactory;
    protected BatchClassifierLearner learner;
    protected String learnerName;
    protected ArrayList innerLearner = null;
    protected ExampleSchema schema;

    /** Create a new object from a fragment of bean shell code,
     * and make sure it's the correct type.
     */
    static Object newObjectFromBSH(String s,Class expectedType) throws IllegalArgumentException
    {
	try {
	    bsh.Interpreter interp = new bsh.Interpreter();
	    interp.eval("import edu.cmu.minorthird.classify.*;");
	    interp.eval("import edu.cmu.minorthird.classify.experiments.*;");
	    interp.eval("import edu.cmu.minorthird.classify.algorithms.linear.*;");
	    interp.eval("import edu.cmu.minorthird.classify.algorithms.trees.*;");
	    interp.eval("import edu.cmu.minorthird.classify.algorithms.knn.*;");
	    interp.eval("import edu.cmu.minorthird.classify.algorithms.svm.*;");
	    interp.eval("import edu.cmu.minorthird.classify.transform.*;");
	    interp.eval("import edu.cmu.minorthird.classify.sequential.*;");
	    interp.eval("import edu.cmu.minorthird.text.learn.*;");
	    interp.eval("import edu.cmu.minorthird.text.*;");
	    interp.eval("import edu.cmu.minorthird.ui.*;");
	    interp.eval("import edu.cmu.minorthird.util.*;");
	    if (!s.startsWith("new"))	s = "new "+s;
	    Object o = interp.eval(s);
	    if (!expectedType.isInstance(o)) {
		throw new IllegalArgumentException(s+" did not produce "+expectedType);
	    }
	    return o;
	} catch (bsh.EvalError e) {
	    System.out.println("ERROR: " + e.toString());
	    throw new IllegalArgumentException("error parsing '"+s+"':\n"+e);
	}
    }

    public static class IllegalArgumentException extends Exception {
	public IllegalArgumentException(String s) { super(s); }
    }

    public OneVsAllLearner()
    {
	//this(new ClassifierLearnerFactory("new VotedPerceptron()"));
	//try{
	this("new MaxEntLearner()");
    }

    /** 
     * @param learnerFactory a ClassifierLearnerFactory which should produce a BinaryClassifier with each call.
     */
    public OneVsAllLearner(ClassifierLearnerFactory learnerFactory)
    {
	this.learnerFactory = learnerFactory;
    }
    public OneVsAllLearner(String learnerName) {
	this.learnerName = learnerName;
	learnerFactory = new ClassifierLearnerFactory(learnerName);
	try {
	    this.learner = (BatchClassifierLearner)newObjectFromBSH(learnerName,BatchClassifierLearner.class);	
	} catch(Exception e) {
	    e.printStackTrace();
	}
	
    }
    public OneVsAllLearner(BatchClassifierLearner learner) {
	this.learner = learner;
	this.learnerName = learner.toString();
	learnerFactory = new ClassifierLearnerFactory(learnerName);
	
    }
    public void setSchema(ExampleSchema schema)
    {
	this.schema = schema;
	innerLearner = new ArrayList();
	//for (int i=0; i<innerLearner.size(); i++) {
	for(int i=0; i<schema.getNumberOfClasses(); i++) {
	    innerLearner.add(((BatchClassifierLearner)learner).copy());	   
	    ((ClassifierLearner)(innerLearner.get(i))).setSchema( ExampleSchema.BINARY_EXAMPLE_SCHEMA );
	}
    }
    public void reset()
    {
	if (innerLearner!=null) {
	    for (int i=0; i<innerLearner.size(); i++) {
		((ClassifierLearner)(innerLearner.get(i))).reset();
	    }
	}
    }
    public void setInstancePool(Instance.Looper looper) 
    {
	ArrayList list = new ArrayList();
	while (looper.hasNext()) list.add(looper.next());
	for (int i=0; i<innerLearner.size(); i++) {
	    ((ClassifierLearner)(innerLearner.get(i))).setInstancePool( new Instance.Looper(list) );
	}
    }
    public boolean hasNextQuery()
    {
	for (int i=0; i<innerLearner.size(); i++) {
	    if (((ClassifierLearner)(innerLearner.get(i))).hasNextQuery()) return true;
	}
	return false;
    }

    public Instance nextQuery()
    {
	for (int i=0; i<innerLearner.size(); i++) {
	    if (((ClassifierLearner)(innerLearner.get(i))).hasNextQuery()) return ((ClassifierLearner)innerLearner.get(i)).nextQuery();
	}
	return null;
    }

    public void addExample(Example answeredQuery)
    {
	int classIndex = schema.getClassIndex( answeredQuery.getLabel().bestClassName() );
	for (int i=0; i<innerLearner.size(); i++) {
	    ClassLabel label = classIndex==i ? ClassLabel.positiveLabel(1.0) : ClassLabel.negativeLabel(-1.0);
	    ((ClassifierLearner)(innerLearner.get(i))).addExample( new Example( answeredQuery.asInstance(), label ) );
	}
    }

    public void completeTraining()
    {
	for (int i=0; i<innerLearner.size(); i++) {
	    ((ClassifierLearner)(innerLearner.get(i))).completeTraining();
	}
    }

    public Classifier getClassifier()
    {
	Classifier[] classifiers = new Classifier[ innerLearner.size() ];
	for (int i=0; i<innerLearner.size(); i++) {
	    classifiers[i] = ((ClassifierLearner)(innerLearner.get(i))).getClassifier();
	}
	return new OneVsAllClassifier( schema.validClassNames(), classifiers );
    }

}
