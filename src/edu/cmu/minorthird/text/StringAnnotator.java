package edu.cmu.minorthird.text;

import edu.cmu.minorthird.text.MonotonicTextEnv;
import edu.cmu.minorthird.text.AbstractAnnotator;
import edu.cmu.minorthird.text.CharAnnotation;
import org.apache.log4j.Logger;

/**
 * This class...
 * @author ksteppe
 */
public abstract class StringAnnotator extends AbstractAnnotator
{
  private static Logger log = Logger.getLogger(StringAnnotator.class);

  protected void doAnnotate(MonotonicTextEnv env)
	{
		//add the annotations into env
		edu.cmu.minorthird.text.TextBase textBase = env.getTextBase();
		for (edu.cmu.minorthird.text.Span.Looper it = textBase.documentSpanIterator(); it.hasNext();)
		{
			edu.cmu.minorthird.text.Span span = it.nextSpan();
			String spanString = span.asString();

      CharAnnotation[] annotations = annotateString(spanString);

      if (annotations != null)
      {
        for (int i = 0; i < annotations.length; i++)
        {
          CharAnnotation ann = annotations[i];
          int lo = ann.getOffset();
          edu.cmu.minorthird.text.Span newSpan = span.charIndexSubSpan(lo, lo + ann.getLength());
          env.addToType(newSpan, ann.getType());
        }
      }
      log.warn("no annotations returned for span");
		}

	}

  protected String[] closedTypes()
  {
    return null;
  }

  protected abstract CharAnnotation[] annotateString(String spanString);

}
