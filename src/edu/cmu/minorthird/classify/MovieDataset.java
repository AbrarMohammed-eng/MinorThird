package edu.cmu.minorthird.classify;

import edu.cmu.minorthird.classify.*;
import edu.cmu.minorthird.classify.transform.*;
import edu.cmu.minorthird.text.*;
import edu.cmu.minorthird.text.gui.MixupDebugger;
import edu.cmu.minorthird.text.mixup.MixupProgram;
import edu.cmu.minorthird.text.learn.SpanFE;
import edu.cmu.minorthird.text.learn.SpanFeatureExtractor;
import edu.cmu.minorthird.text.learn.FeatureBuffer;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.awt.*;

/**
 * @author Edoardo M. Airoldi
 * Date: Jan 22, 2004
 */

public class MovieDataset {
  static private Logger log = Logger.getLogger(MovieDataset.class);

  // set static variables
  static private boolean MIXUP = true;
  static private String FILTER = "Freq"; // or "T1"

  /**
   * Creates the dataset of movie reviews, with method = "parse".
   */
  public static Dataset MovieReviewsData() {
    return MovieReviewsData("parse");
  }

  /**
   * Creates the dataset of movie reviews.  If method = "parse" uses the specified
   * file.mixup and the specified feature filter;  if method = "file" uses the
   * specified file.id
   */
  public static Dataset MovieReviewsData(String method) {
    Dataset data = new BasicDataset();

    if (method.equals("file"))
    {
      try {
        // load counts from file
        File fileOfCounts = new File("/Users/eairoldi/cmu.research/Text.Learning.Group/UAI.2004/Min3rd-Datasets/movies-cnt-ing.id");
        data = DatasetLoader.loadFile(fileOfCounts);
      } catch (Exception e) {
        log.error(e, e);
        System.exit(1);
      }
      // DEBUG
      BasicFeatureIndex fidx = new BasicFeatureIndex(data);
      System.out.println( "Dataset:\n # examples = "+data.size() );
      System.out.println( " # features = "+fidx.numberOfFeatures() );
    }

    else if (method.equals("parse"))
    {
      try {
        // load the documents and labels
        System.out.println("Load Texts");
        TextBase base = new BasicTextBase();
        TextBaseLoader loader = new TextBaseLoader();
        File dir = new File("/Users/eairoldi/cmu.research/Text.Learning.Group/UAI.2004/Min3rd-Datasets/movie-reviews");
        loader.loadTaggedFiles(base, dir);
        MutableTextLabels labels = new BasicTextLabels(base);
        new TextLabelsLoader().importOps(labels, base, new File("/Users/eairoldi/cmu.research/Text.Learning.Group/UAI.2004/Min3rd-Datasets/movie-labels.env"));
        //TextBaseLabeler.label( labels, new File("my-document-labels.env")); // DEBUG

        // load Mixup file to get candidates
        if (MIXUP)
        {
          System.out.println("Load Mixup file");
          File mixupFile = new File("/Users/eairoldi/cmu.research/minorthird/lib/mixup/sentiments.mixup");
          MixupProgram p = new MixupProgram(mixupFile);
          p.eval(labels,base);
          // DEBUG Mixup
          if (false)
          {
            JFrame frame = new JFrame("TextBaseEditor");
            MixupDebugger debugger = new MixupDebugger(base,null,mixupFile,false,false);
            frame.getContentPane().add( debugger, BorderLayout.CENTER );
            frame.addWindowListener(new WindowAdapter() {
              public void windowClosing(WindowEvent e) { System.exit(0); }
            });
            frame.pack();
            frame.setVisible(true);
          }
        }
        else
        {
          System.out.println("No Mixup file was loaded");
        }

        // set up a simple bag-of-words feature extractor
        SpanFeatureExtractor fe = new SpanFeatureExtractor()
        {
          public Instance extractInstance(TextLabels labels, Span s) {
            FeatureBuffer buf = new FeatureBuffer(labels, s);
            SpanFE.from(s,buf).tokens().eq().lc().punk().emit();
            //SpanFE.from(s,buf).tokens().eq().lc().punk().stopwords("use").emit();
            //SpanFE.from(s,buf).contains("pos").emit();
            return buf.getInstance();
          }
          public Instance extractInstance(Span s) {
            return extractInstance(null,s);
          }
        };

        // Extract features and create a binary dataset for "POS"
        System.out.println("Extract Features");
        for (Span.Looper i = base.documentSpanIterator(); i.hasNext();) {
          Span s = i.nextSpan();
          double label = labels.hasType(s, "POS") ? +1 : -1;
          data.add(new BinaryExample(fe.extractInstance(labels,s), label));
        }
      } catch (Exception e) {
        log.error(e, e);
        System.exit(1);
      }

      // filter features
      if (FILTER.equals("T1"))
      {
        System.out.println("Filter Features");
        T1InstanceTransformLearner filter = new T1InstanceTransformLearner();
        filter.setREF_LENGTH(660.0);
        filter.setPDF("Negative-Binomial");
        T1InstanceTransform t1stat = (T1InstanceTransform)filter.batchTrain( data );
        t1stat.setALPHA(0.05);
        t1stat.setMIN_WORDS(50);
        t1stat.setSAMPLE(2500);
        data = t1stat.transform( data );
      }
      else if (FILTER.equals("Freq"))
      {
        int minFreq =4;  String model = "word"; // or "document"
        System.out.println("Filter Features");
        FrequencyBasedTransformLearner filter = new FrequencyBasedTransformLearner(minFreq,model);
        AbstractInstanceTransform ait = (AbstractInstanceTransform)filter.batchTrain( data );
        data = ait.transform( data );
      }
      else
      {
        System.out.println("No Filter was used");
      }

      // DEBUG
      BasicFeatureIndex fidx = new BasicFeatureIndex(data);
      System.out.println( "Dataset:\n # examples = "+data.size() );
      System.out.println( " # features = "+fidx.numberOfFeatures() );
    }

    return data;
  }
}
