/* Copyright 2003, Carnegie Mellon, All Rights Reserved */
package edu.cmu.minorthird.text.learn;

import edu.cmu.minorthird.classify.*;
import edu.cmu.minorthird.text.*;
import edu.cmu.minorthird.text.learn.SpanFeatureExtractor;
import edu.cmu.minorthird.util.StringUtil;
import edu.cmu.minorthird.util.gui.ViewerFrame;

import java.util.Set;

/**
 * Some sample feature extractors.
 *
 * @author William Cohen
 */
public class SampleFE
{
	/** Simple bag of words feature extractor.
	 */
	public static final SpanFeatureExtractor BAG_OF_WORDS = new BagOfWordsFE();

	public static class BagOfWordsFE implements SpanFeatureExtractor
	{
		public Instance extractInstance(TextLabels labels, Span s)	{
			return extractInstance(s);
		}
		public Instance extractInstance(Span s){
			FeatureBuffer buf = new FeatureBuffer(s);
			SpanFE.from(s, buf).tokens().emit();
			return buf.getInstance();
		}
	}

	/** Simple bag of words feature extractor, with all tokens converted to lower case.
	 */
	public static final SpanFeatureExtractor BAG_OF_LC_WORDS = new BagOfLowerCaseWordsFE();

	public static class BagOfLowerCaseWordsFE implements SpanFeatureExtractor
	{
		public Instance extractInstance(TextLabels labels, Span s)	{
			return extractInstance(s);
		}
		public Instance extractInstance(Span s){
			FeatureBuffer buf = new FeatureBuffer(s);
			SpanFE.from(s, buf).tokens().eq().lc().emit();
			return buf.getInstance();
		}
	}

	/** A simple extraction-oriented feature extractor to apply to one-token spans, for extraction tasks. 
	 */
	public static final SpanFeatureExtractor makeExtractionFE(final int featureWindowSize)
	{
		ExtractionFE fe = new ExtractionFE();
		fe.setFeatureWindowSize(featureWindowSize);
		return fe;
	}


	/** A simple extraction-oriented feature extractor to apply to one-token spans, for extraction tasks.
	 */
	public static class ExtractionFE extends SpanFE
	{
		protected int windowSize=5;
		protected String requiredAnnotation = null;
		protected String requiredAnnotationFileToLoad = null;
		protected boolean useCharType=true;
		protected boolean useCompressedCharType=true;
		protected String[] tokenPropertyFeatures=new String[0];

		public ExtractionFE() { this(3); }
		public ExtractionFE(int windowSize) { this.windowSize=windowSize; }

		//
		// getters and setters
		//

		/** Specify an annotator to run before feature generation.
		 */
		public void setRequiredAnnotation(String requiredAnnotation) { this.requiredAnnotation=requiredAnnotation; }
		public String getRequiredAnnotation() { return requiredAnnotation==null ? "" : requiredAnnotation; }


		/** Specify an annotator to run before feature generation
		 * and a mixup file/class that generates it simultaneously.
		*/
		public void setRequiredAnnotation(String requiredAnnotation,String annotationProvider) 
		{ 
			setRequiredAnnotation(requiredAnnotation);
			setAnnotationProvider(annotationProvider);
		}


		/** Specify a mixup file or java class to use to provide the annotation.
		 */
		public void setAnnotationProvider(String classNameOrMixupFileName) {
			this.requiredAnnotationFileToLoad = classNameOrMixupFileName;
		}
		public String getAnnotationProvider() {	
			return requiredAnnotationFileToLoad==null? "" : requiredAnnotationFileToLoad; 
		}

		/** Specify the number of tokens on before and after the span to
		 * emit features for. */
		public void setFeatureWindowSize(int n) { windowSize=n; }
		public int getFeatureWindowSize() { return windowSize; }

		/** If set to true, produce features like
		 * "token.charTypePattern.Aaaa" for the word "Bill" */
		public void setUseCharType(boolean flag) { useCharType=flag; } 
		public boolean getUseCharType() { return useCharType; } 

		/** If set to true, produce features like
		 * "token.charTypePattern.Aa+" for the word "Bill". */
		public void setUseCompressedCharType(boolean flag) { useCompressedCharType=flag; } 
		public boolean getUseCompressedCharType() { return useCompressedCharType; } 

		public String getTokenPropertyFeatures() { return StringUtil.toString(tokenPropertyFeatures); }
		/** Specify the token properties from the TextLabels environment
		 * that will be used as features. A value of '*' means to use all
		 * defined token properties. */
		public void setTokenPropertyFeatures(String commaSeparatedTokenPropertyList) {
			if ("*".equals(commaSeparatedTokenPropertyList)) {
				//System.out.println("setting properties to null");
				tokenPropertyFeatures = null; 
			} else {
				tokenPropertyFeatures = commaSeparatedTokenPropertyList.split(",\\s*");
			}
		}
		public void setTokenPropertyFeatures(Set propertySet) {
			tokenPropertyFeatures = (String[])propertySet.toArray(new String[propertySet.size()]);
		}

		public void extractFeatures(Span s)
		{
			extractFeatures(new EmptyLabels(), s);
		}
		public void extractFeatures(TextLabels labels, Span s)
		{
			if (requiredAnnotation!=null) {
				//System.out.println("require "+requiredAnnotation+", tokenPropertyFeatures="+tokenPropertyFeatures);
				labels.require(requiredAnnotation,requiredAnnotationFileToLoad);
			}
			if (tokenPropertyFeatures==null) {
				System.out.println("setTokenPropertyFeatures to the set "+labels.getTokenProperties());
				setTokenPropertyFeatures( labels.getTokenProperties() );
			}

			// tokens in span
			from(s).tokens().eq().lc().emit();
			// simplified capitalization pattern
			if (useCompressedCharType) {
				from(s).tokens().eq().charTypePattern().emit();
			}
			// exact capitalization pattern
			if (useCharType) {
				from(s).tokens().eq().charTypes().emit();
			}
			// token properties
			for (int j=0; j<tokenPropertyFeatures.length; j++) {
				from(s).tokens().prop(tokenPropertyFeatures[j]).emit();
			}
			// window
			for (int i=0; i<windowSize; i++) {
				from(s).left().token(-i-1).eq().lc().emit();
				from(s).right().token(i).eq().lc().emit();
				for (int j=0; j<tokenPropertyFeatures.length; j++) {
					//System.out.println("Property: "+tokenPropertyFeatures[j]);
					from(s).left().token(-i-1).prop(tokenPropertyFeatures[j]).emit();
					from(s).right().token(i).prop(tokenPropertyFeatures[j]).emit();
				}
				if (useCompressedCharType) {
					from(s).left().token(-i-1).eq().charTypePattern().emit();
					from(s).right().token(i).eq().charTypePattern().emit();
				}
				if (useCharType) {
					from(s).left().token(-i-1).eq().charTypes().emit();
					from(s).right().token(i).eq().charTypes().emit();
				}
			}
		}
	}

	/** Test case to try out the feature extractors
	 */
	public static void main(String[] args)
	{
		try {
			SpanFeatureExtractor fe = BAG_OF_LC_WORDS;
			TextBase base = new BasicTextBase();
			for (int i=0; i<SampleDatasets.posTrain.length; i++) {
				base.loadDocument("pos"+i, SampleDatasets.posTrain[i]);
			}
			for (int i=0; i<SampleDatasets.negTrain.length; i++) {
				base.loadDocument("neg"+i, SampleDatasets.negTrain[i]);
			}
			Dataset dataset = new BasicDataset();
			for (Span.Looper i=base.documentSpanIterator(); i.hasNext(); ) {
				Span s = i.nextSpan();
				String id = s.getDocumentId();
				ClassLabel label = ClassLabel.binaryLabel( id.startsWith("pos") ? +1 : -1 );
				dataset.add(new BinaryExample(fe.extractInstance(s), label));
			}
			ViewerFrame f = new ViewerFrame("Toy data", dataset.toGUI());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
