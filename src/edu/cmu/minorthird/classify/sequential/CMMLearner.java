package edu.cmu.minorthird.classify.sequential;

import edu.cmu.minorthird.classify.*;

/**
 * Train a CMM (in batch mode).
 *
 * @author William Cohen
 */

public class CMMLearner implements BatchSequenceClassifierLearner
{
	private ClassifierLearner baseLearner;
	private int historySize;

	public int getHistorySize() { return historySize; }

	public CMMLearner(ClassifierLearner baseLearner,int historySize)
	{
		this.baseLearner = baseLearner;
		this.historySize = historySize;
	}

	public void setSchema(ExampleSchema schema) {;}

	public SequenceClassifier batchTrain(SequenceDataset dataset)
	{
		ExampleSchema schema = dataset.getSchema();
		baseLearner.reset();
		baseLearner.setSchema( schema );
		for (Example.Looper i=dataset.iterator(); i.hasNext(); ) {
			Example e = i.nextExample();
			baseLearner.addExample( e );
		}
		Classifier classifier = baseLearner.getClassifier();
		return new CMM(classifier,historySize,schema);
	}
}

