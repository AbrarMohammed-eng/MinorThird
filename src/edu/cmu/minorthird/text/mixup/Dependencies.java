package edu.cmu.minorthird.text.mixup;

import edu.cmu.minorthird.text.MonotonicTextEnv;
import edu.cmu.minorthird.text.Annotator;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Contains static methods to run annotators (either mixup or java code)
 * see runDependency(MonotonicTextEnv, String, String)
 * @author ksteppe
 */
public class Dependencies
{
  private static Map providerMap;
  private static Logger log = Logger.getLogger(Dependencies.class);
  private static String configFile = "annotators.config";

  /**
   * This runs the given file or the default (from <code>configFile</code>) to generate
   * the requested annotations in the given environment.
   *
   * If no file is given and no default is registered then a null pointer will be thrown
   * Exceptions are converted to IllegalStateException objects
   *
   * note here - could catch the annotate exception stuff
   *
   * @param env Text environment to place annotations in - must hold the text base to run against
   * @param reqAnnotation the name of the annotations requested
   * @param file file to run in order to get annotations, if null then the configuration file is
   *        checked for a default
   *
   */
  public static void runDependency(MonotonicTextEnv env, String reqAnnotation, String file)
  {
    log.debug("runDependency : " + reqAnnotation + " : " + file);
    try
    {
      if (file == null)
        file = getDependency(reqAnnotation);

      //error - if (file==null)  throw "can't find annotator for " + reqAnnotation;
      if (file == null)
        throw new Exception("no annotator found for '" + reqAnnotation + "'");

      if (file.endsWith("mixup"))
      {
        InputStream inStream = Dependencies.class.getClassLoader().getResourceAsStream(file);
        byte[] chars = new byte[inStream.available()];
        inStream.read(chars);
        String program = new String(chars);

				log.info("Evaluating mixup program "+file+" to provide "+reqAnnotation);
        MixupProgram subProgram =  new MixupProgram(program);
        subProgram.eval(env, env.getTextBase());
				if (!env.isAnnotatedBy(reqAnnotation)) {
					throw new IllegalArgumentException(
						"file "+file+" did not provide expected annotation type "+reqAnnotation);
				}
      }
      else // if (file.endsWith("java") || file.endsWith(("class")))
      {
        //run the java code
//        String className = file.substring(0, file.lastIndexOf('.'));
        Class clazz = Dependencies.class.getClassLoader().loadClass(file);
        Annotator annotator = (Annotator)clazz.newInstance();
        annotator.annotate(env);
      }
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      IllegalStateException exc = new IllegalStateException("error running mixup: " + file + ": " + e.getMessage());
      exc.setStackTrace(e.getStackTrace());
      throw exc;
    }
    catch (Mixup.ParseException e)
    {
      IllegalStateException exc = new IllegalStateException("error running mixup: " + file + ": " + e.getMessage());
      exc.setStackTrace(e.getStackTrace());
      throw exc;
    }
    catch (Exception e)
    {
      IllegalStateException exc;
      if (file != null)
        exc = new IllegalStateException("error loading " + file + ": " + e.getMessage());
      else
        exc = new IllegalStateException("error loading annotator: " + e.getMessage());
      exc.setStackTrace(e.getStackTrace());
      throw exc;
    }
  }

  /**
   *  Return the file required for this dependency
   * @param reqAnnotation
   * @return name of the default provider (file or java class) for the required
   *        annotation
   */
  protected static String getDependency(String reqAnnotation)
  {

    if (providerMap == null)
    {
      providerMap = new HashMap();
      InputStream inStream =
			  ClassLoader.getSystemResourceAsStream(configFile);
//			  Dependencies.class.getClassLoader().getResourceAsStream(configFile);
      log.debug("in stream = " + inStream);
      if (inStream == null)
      { throw new IllegalStateException("can't find " + configFile + " on classpath"); }
      BufferedReader in = new BufferedReader(new InputStreamReader(inStream));
      try
      {
        while (in.ready())
        {
          String line = in.readLine();
          if (line.startsWith("#"))
            continue;
          StringTokenizer tokenizer = new StringTokenizer(line, " :,");
          String provides = tokenizer.nextToken();
          String service = tokenizer.nextToken();
          providerMap.put(provides, service);
        }
      }
      catch (Exception e)
      {
        log.error(e, e);
      }
    }

    return (String)providerMap.get(reqAnnotation);
  }
}
