package edu.cmu.minorthird.text;import edu.cmu.minorthird.text.gui.TextBaseViewer;import edu.cmu.minorthird.text.learn.AnnotatorLearner;import java.io.File;import java.io.IOException;import java.io.InputStream;import java.util.Properties;import org.apache.log4j.*;/** Loads data files in a configurable way. * * To use this, put data.properties on the classpath, * and use it to define wcohen.scriptdir and wcohen.datadir. * Then FancyLoader.loadFoo("bar") will look in scriptdir * for a script named bar, execute it with dataDir bound to * the data directory, and return the result as an object of type 'Foo'. * * @author William Cohen*/public class FancyLoader{	private static Logger log = Logger.getLogger(FancyLoader.class);	/** Property defining location of raw data */	public static final String DATADIR_PROP = "edu.cmu.minorthird.dataDir";	/** Property defining location of labels added to data */	public static final String LABELDIR_PROP = "edu.cmu.minorthird.labelDir";	/** Property defining location of scripts for loading data */	public static final String SCRIPTDIR_PROP = "edu.cmu.minorthird.scriptDir";	private static Properties props = new Properties();	static {		try {			InputStream in = FancyLoader.class.getClassLoader().getResourceAsStream("data.properties");			if (in != null) {				props.load(in);				log.info("loaded properties from stream "+in);			} else {				log.error("can't find data.properties");				throw new IllegalStateException("can't get resource data.properties");			}		} catch (IOException e) {			throw new IllegalStateException("error getting wcohen.properties: "+e);		}	};	public static TextBase loadTextBase(String script) {		Object o = loadObject(script);		if (o instanceof TextBase) return (TextBase)o;		else if (o instanceof TextEnv) return ((TextEnv)o).getTextBase();		else throw new IllegalStateException("script "+script+" doesn't load a text base");	}	public static TextEnv loadTextEnv(String script) {		return (TextEnv)loadObject(script);	}	public static AnnotatorLearner loadAnnotatorLearner(String script) {		return (AnnotatorLearner)loadObject(script);	}	public static Object loadObject(String script)	{		String dataDir = getProperty(DATADIR_PROP);		String labelDir = getProperty(LABELDIR_PROP);		String scriptDir = getProperty(SCRIPTDIR_PROP);		log.info("dataDir: "+dataDir+" labelDir: "+labelDir+" scriptDir: "+scriptDir);		try {			bsh.Interpreter interpreter = new bsh.Interpreter();			interpreter.eval("File dataDir = new File(\""+dataDir+"\");");			interpreter.eval("File labelDir = new File(\""+labelDir+"\");");			File f =  new File(new File(scriptDir),script);			if (!f.exists()) throw new IllegalArgumentException("can't find file "+f.getAbsolutePath());			log.info("loading object defined by "+f.getAbsolutePath());			return interpreter.source(f.getAbsolutePath());			} catch (Exception e) {				e.printStackTrace();				return null;			}		}			private static String getProperty(String prop) {		String v = System.getProperty(prop);		return v!=null ? v : props.getProperty(prop);	}	static public void main(String[] args) {		Object o = FancyLoader.loadObject(args[0]);		System.out.println("loaded "+o);		if (o instanceof TextEnv) {			TextBaseViewer.view((TextEnv) o );		}	}}