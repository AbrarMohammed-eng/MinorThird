package edu.cmu.minorthird.text;

import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.cmu.minorthird.Loader;

/** Loads the contents of a TextBase from a file.
 *
 *
 * @author William Cohen
*/

public class TextBaseLoader implements Loader
{
	private static Logger log = Logger.getLogger(TextBaseLoader.class);

	private class StackEntry {
		public int index;
		public String markupTag;
		public StackEntry(int index,String markupTag) {
			this.index=index; this.markupTag=markupTag;
		}
	}
	private class CharSpan {
		public int lo,hi;
		String type;
		public CharSpan(int lo,int hi,String type) {
			this.lo=lo; this.hi=hi; this.type = type;
		}
	}

	private int closurePolicy = TextLabelsLoader.CLOSE_ALL_TYPES;
	/** Set the closure policy.
	 * @param policy one of TextLabelsLoader.CLOSE_ALL_TYPES,
	 * TextLabelsLoader.CLOSE_TYPES_IN_LABELED_DOCS, TextLabelsLoader.DONT_CLOSE_TYPES
	 */
	public void setClosurePolicy(int policy) { this.closurePolicy = policy; }

	// saves labels associated with last set of files loaded
	private MutableTextLabels labels;

	/**
   * Load from either a file (one document per line) or a directory (one document per file)
   * Directory is assumed to be tagged files
   * Single file assumed not to be tagged
   */
	public void loadFile(TextBase base,File file) throws IOException,FileNotFoundException
	{
		if (file.isDirectory())
      loadTaggedFiles(base,file);
		else {
			loadLines(base,file);
			labels = new BasicTextLabels(base);
		}
	}

	/** Get all markup suggested by XML/SGML tags from the files loaded by loadTaggedFiles. */
	public MutableTextLabels getFileMarkup() { return labels; }

	/** Load files from a directory, stripping out any XML/SGML tags. */
	public void loadTaggedFiles(TextBase base,File dir) throws IOException,FileNotFoundException
	{
		labels = new BasicTextLabels(base);
		Pattern markupPattern = Pattern.compile("</?([^ ><]+)( [^<>]+)?>");

		File[] files = dir.listFiles();
		if (files==null) throw new IllegalArgumentException("can't list directory "+dir.getName());

		for (int i=0; i<files.length; i++) {

			// skip CVS directories
			if ("CVS".equals(files[i].getName())) continue;

      loadTaggedFile(files[i], markupPattern, base);

    }
	}

  public void loadTaggedFile(File file, Pattern markupPattern, TextBase base) throws IOException
  {
    if (labels == null)
      labels = new BasicTextLabels(base);

    if (markupPattern == null)
      markupPattern = Pattern.compile("</?([^ ><]+)( [^<>]+)?>");

    // stack of open tags
    ArrayList stack = new ArrayList();
    // list of constructed spans
    List spanList = new ArrayList();
    // file name used as ID
    String id = file.getName();
    // holds a string representation of the file with xml tags removed
    StringBuffer buf = new StringBuffer("");

    LineNumberReader in = new LineNumberReader(new FileReader(file));
    String line;
    while ((line = in.readLine())!=null) {
      int currentChar = 0;
      Matcher matcher = markupPattern.matcher(line);
      while (matcher.find()) {
        String tag = matcher.group(1);
        boolean isOpenTag = !matcher.group().startsWith("</");
        log.debug("matcher.group='"+matcher.group()+"'");
        log.debug("found '"+tag+"' tag ,open="+isOpenTag+", at "+matcher.start()+" in:\n"+line);
        //copy stuff up to tag into buffer
        buf.append( line.substring(currentChar, matcher.start()) );
        currentChar = matcher.end();
        if (isOpenTag) {
          stack.add( new StackEntry(buf.length(), tag) );
        } else {
          // pop the corresponding open off the stack
          StackEntry entry = null;
          for (int j=stack.size()-1; j>=0; j--) {
            entry = (StackEntry)stack.get(j);
            if (tag.equals(entry.markupTag)) {
              stack.remove(j);
              break;
            }
          }
          if (entry==null)
            throw new IllegalStateException(id+"@"+in.getLineNumber()+": close '"+tag+"' tag with no open");
          if (!tag.equals(entry.markupTag))
            throw new IllegalStateException(id+"@"+in.getLineNumber()+": close '"+tag+"' tag paired with open '"
                                            +entry.markupTag+"'");
          log.debug("adding a "+tag+" span from "+entry.index+" to "+buf.length()
                    +": '"+buf.substring(entry.index)+"'");
          spanList.add( new CharSpan(entry.index, buf.length(), tag) );
        }
      }
      // append stuff from end of last tag to end of line into the buffer
      buf.append( line.substring(currentChar, line.length()) );
      buf.append( "\n" );
    }
    // add the document to the textbase
    base.loadDocument(id, buf.toString() );
    // add the markup to the labels
    Set types = new TreeSet();
    for (Iterator j=spanList.iterator(); j.hasNext(); ) {
      CharSpan charSpan = (CharSpan)j.next();
      types.add( charSpan.type );
      Span approxSpan = base.documentSpan(id).charIndexSubSpan(charSpan.lo, charSpan.hi);
      log.debug("approximating "+charSpan.type+" span '"
                +buf.toString().substring(charSpan.lo,charSpan.hi)
                +"' with token span '"+approxSpan);
      labels.addToType( approxSpan, charSpan.type );
    }
    new TextLabelsLoader().closeLabels( labels, closurePolicy );
  }

  //
	// loadLines code
	//

	private boolean firstWordIsDocumentId = false;
	private boolean secondWordIsGroupId = false;

	/** For loadLines, indicates if first word is the Id. */
	public boolean getFirstWordIsDocumentId() { return firstWordIsDocumentId; }

	/** For loadLines, indicates if first word is the Id. */
	public void setFirstWordIsDocumentId(boolean flag) { firstWordIsDocumentId = flag; }

	/** For loadLines, indicates if second word is the group Id. */
	public boolean getSecondWordIsGroupId() { return secondWordIsGroupId; }

	/** For loadLines, indicates if second word is the group Id and also
	 * sets first word to be document id */
	public void setSecondWordIsGroupId(boolean flag) {
		firstWordIsDocumentId = true;
		secondWordIsGroupId = flag;
	}

	/** Load each line of the file as a separate 'document'.
	 * If firstWordIsDocumentId is set to be true, then the first token on
	 * a line is the documentId.
	 */
  public void loadLines(TextBase base, File file) throws IOException, FileNotFoundException
  {
    LineNumberReader in = new LineNumberReader(new FileReader(file));
    String line;
    while ((line = in.readLine()) != null)
    {
      String id = null,groupId = null;
      if (!firstWordIsDocumentId)
      {
        id = file.getName() + "@line:" + in.getLineNumber(); // default
      }
      else
      {
        int spaceIndex = line.indexOf(' ');
        if (spaceIndex < 0)
        {
          id = line;
          line = "";
        }
        else
        {
          id = line.substring(0, spaceIndex);
          if (!secondWordIsGroupId)
          {
            line = line.substring(spaceIndex + 1, line.length());
          }
          else
          {
            int spaceIndex2 = line.indexOf(' ', spaceIndex + 1);
            if (spaceIndex < 0)
            {
              groupId = line.substring(spaceIndex + 1, line.length());
              line = "";
            }
            else
            {
              groupId = line.substring(spaceIndex + 1, spaceIndex2);
              line = line.substring(spaceIndex2 + 1, line.length());
            }
          }
        }
      }
      base.loadDocument(id, line);
      if (groupId != null) {
				base.setDocumentGroupId(id, groupId);
			}
    }
    in.close();
  }

	/** Write the textTokenbase to a file. */
	public void writeSerialized(TextBase base,File file) throws IOException {
		ObjectOutputStream out =
			new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
		out.writeObject(base);
		out.flush();
		out.close();
	}

	/** Read a serialized BasicTextBase from a file. */
	public TextBase readSerialized(File file) throws IOException {
		try {
			ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
			TextBase b = (TextBase)in.readObject();
			in.close();
			return b;
		}
		catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("can't read BasicTextBase from "+file+": "+e);
		}
	}

	/**
	 * Takes a base directory.  Each file is a different doc to load.
	 * @param base TextBase to load into
	 * @param directory File representation of directory
	 */
	public void loadDir(TextBase base, File directory)
	{
		if (directory.isDirectory())
		{
			String categoryLabel = directory.getName();
			log.debug("found directory for type: " + categoryLabel);
			//load everything in the directory
			try
			{
				File[] files = directory.listFiles();
				for (int j = 0; j < files.length; j++)
				{
					// skip CVS directories
					if ("CVS".equals(files[j].getName())) continue;
					File file = files[j];
					this.loadFileWithID(base, file, file.getName());
				}
			}
			catch (IOException ioe)
			{ log.error(ioe, ioe); }
		}
		else
			log.error("loadDir found a file instead of directory label: "
								+ directory.getPath() + File.pathSeparator + directory.getName());
	}

	/**
	 * Takes a base directory.  Each subdirectory is a label for the category
	 * of the files in that directory.  Each file is a different doc
	 * @param base TextBase to load into
	 * @param dir File representation of dir to use as the base
	 */
	public void loadLabeledDir(TextBase base, File dir)
	{
		labels = new BasicTextLabels(base);
		//cycle through the directories
		//these should all be directories
		File[] dirs = dir.listFiles();
		for (int i = 0; i < dirs.length; i++)
		{
			File directory = dirs[i];
			if (directory.isDirectory())
			{
				String categoryLabel = directory.getName();
				log.debug("found directory for type: " + categoryLabel);
				//load everything in the directory
				try
				{
					File[] files = directory.listFiles();
					for (int j = 0; j < files.length; j++)
					{
						File file = files[j];
						this.loadFileWithID(base, file, file.getName());
						//label the new span
						labels.addToType(base.documentSpan(file.getName()), categoryLabel);
					}
				}
				catch (IOException ioe)
				{ log.error(ioe, ioe); }
			}
			else
				log.error("loadLabeledDir found a file instead of directory label: "
									+ directory.getPath() + File.pathSeparator + directory.getName());
		}
	}


	/**
	 * the given file is treated as a single document
	 * @param base TextBase to load into
	 * @param file File to load from
   * @param id ID to be given to the document
	 */
	public void loadFileWithID(TextBase base, File file, String id) throws IOException
	{
		log.debug("loadFileWithID: " + file);
		if (!file.isFile())
			throw new IllegalArgumentException("loadFileWithID must be given a file, not a directory");
		BufferedReader in = new BufferedReader(new FileReader(file));
		String allLines = new String();
		while (in.ready())
		{
			allLines += in.readLine() + "\n";
		}
		
		base.loadDocument(id, allLines);
		in.close();
	}

	// test routine
	static public void main(String[] args) {
		if (args.length<2)
			throw new IllegalArgumentException("usage: TextBaseLoader [file.txt|dir] output.seqbase");
		try {
			TextBase b = new BasicTextBase();
			TextBaseLoader loader = new TextBaseLoader();
			File f = new File(args[0]);
			if (f.isDirectory()) {
				loader.loadTaggedFiles(b, f);
			} else {
				loader.loadLines(b, f);
			}
			loader.writeSerialized(b, new File(args[1]));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

  public MutableTextLabels getLabels()
  {
    return labels;
  }

  /**
   * Uses load file and the TextBase instead the labels property
   * @param f
   */
  public void load(File f) throws IOException
  {
    if (labels == null)
      labels = new BasicTextLabels();
    if (labels.getTextBase() == null)
      labels.setTextBase(new BasicTextBase());

    loadFile(labels.getTextBase(), f);
  }
}
