package edu.cmu.minorthird.classify.algorithms.linear;

import edu.cmu.minorthird.classify.Classifier;
import edu.cmu.minorthird.classify.Example;
import edu.cmu.minorthird.classify.OnlineBinaryClassifierLearner;

/**
 * Voted perceptron algorithm.  As described in "Large Margin
 * Classification Using the Perceptron Algorithm", Yoav Freund and
 * Robert E. Schapire, Proceedings of the Eleventh Annual Conference
 * on Computational Learning Theory,
 * 1998. 
 *
 * @author William Cohen
 */

/*
voted perceptron: maintain weight vectors S_t and W_t
as follows:

   W_t = d_t x_t + W_{t-1}
   S_t = W_t + S_{t-1}

where d_t = (prediction error on x_t) ? y_t : 0

prediction score of averaged perceptron on x is inner product <S_t,x> 
prediction score of perceptron on x is inner product <W_t,x> 

for kernels, compute for each x after training on x1,..,x_T

for t = 1...T
    KW_t(x) = KW_{t-1}(x) + d_t K(x_t,x)
    KS_t(x) = KS_{t-1}(x) + KW_t(x)
*/

public class VotedPerceptron extends OnlineBinaryClassifierLearner
{
	private Hyperplane s_t,w_t;

	public VotedPerceptron() { reset(); }

	public void reset() 
	{
		s_t = new Hyperplane();
		w_t = new Hyperplane();
	}

	public void addExample(Example example)
	{
		double y_t = example.getLabel().numericScore();
		if (w_t.score(example.asInstance()) * y_t <= 0) {
			w_t.increment( example, y_t );
		}
		s_t.increment( w_t, 1.0 );
	}

	public Classifier getClassifier() 
	{
		return s_t;
	}

	public String toString() { return "[VotedPerceptron]"; }
}
