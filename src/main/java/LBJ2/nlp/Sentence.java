package LBJ2.nlp;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import LBJ2.parse.LinkedChild;
import LBJ2.parse.LinkedVector;


/**
  * This representation of a sentence simply stores the entire text of the
  * sentence in a string.  This may include any newlines present in the input,
  * depending on the parser (e.g., {@link SentenceSplitter} will leave them
  * in).  However, this class also provides methods to convert that string to
  * other representations.
  *
  * @author Nick Rizzolo
 **/
public class Sentence extends LinkedChild
{
  /**
    * URL prefixes; used by {@link #partOfURL(int)}.  The values in this array
    * need to be sorted by decreasing order of length to make the regular
    * expressions that use them work properly.
   **/
  private static final String[] protocols =
    { "telnet", "https", "file", "http", "nntp", "smtp", };

  /**
    * Domain name suffixes; used by {@link #partOfURL(int)}.  The values in
    * this array need to be sorted by decreasing order of length to make the
    * regular expressions that use them work properly.
   **/
  private static final String[] topLevelDomains =
    {
      "museum", "travel", "aero", "arpa", "coop", "info", "jobs", "name",
      "biz", "com", "edu", "gov", "int", "mil", "net", "org", "pro", "ac",
      "ad", "ae", "af", "ag", "ai", "al", "am", "an", "ao", "aq", "ar", "as",
      "at", "au", "aw", "az", "ba", "bb", "bd", "be", "bf", "bg", "bh", "bi",
      "bj", "bm", "bn", "bo", "br", "bs", "bt", "bv", "bw", "by", "bz", "ca",
      "cc", "cd", "cf", "cg", "ch", "ci", "ck", "cl", "cm", "cn", "co", "cr",
      "cu", "cv", "cx", "cy", "cz", "de", "dj", "dk", "dm", "do", "dz", "ec",
      "ee", "eg", "er", "es", "et", "eu", "fi", "fj", "fk", "fm", "fo", "fr",
      "ga", "gb", "gd", "ge", "gf", "gg", "gh", "gi", "gl", "gm", "gn", "gp",
      "gq", "gr", "gs", "gt", "gu", "gw", "gy", "hk", "hm", "hn", "hr", "ht",
      "hu", "id", "ie", "il", "im", "in", "io", "iq", "ir", "is", "it", "je",
      "jm", "jo", "jp", "ke", "kg", "kh", "ki", "km", "kn", "kr", "kw", "ky",
      "kz", "la", "lb", "lc", "li", "lk", "lr", "ls", "lt", "lu", "lv", "ly",
      "ma", "mc", "md", "mg", "mh", "mk", "ml", "mm", "mn", "mo", "mp", "mq",
      "mr", "ms", "mt", "mu", "mv", "mw", "mx", "my", "mz", "na", "nc", "ne",
      "nf", "ng", "ni", "nl", "no", "np", "nr", "nu", "nz", "om", "pa", "pe",
      "pf", "pg", "ph", "pk", "pl", "pm", "pn", "pr", "ps", "pt", "pw", "py",
      "qa", "re", "ro", "ru", "rw", "sa", "sb", "sc", "sd", "se", "sg", "sh",
      "si", "sj", "sk", "sl", "sm", "sn", "so", "sr", "st", "su", "sv", "sy",
      "sz", "tc", "td", "tf", "tg", "th", "tj", "tk", "tl", "tm", "tn", "to",
      "tp", "tr", "tt", "tv", "tw", "tz", "ua", "ug", "uk", "um", "us", "uy",
      "uz", "va", "vc", "ve", "vg", "vi", "vn", "vu", "wf", "ws", "ye", "yt",
      "yu", "za", "zm", "zw"
    };

  /**
    * Indicates whether the corresponding index in the text has been
    * determined to be part of a URL; used by {@link #partOfURL(int)}.
   **/
  private boolean[] inURL = null;


  /** The actual text of the sentence. */
  public String text = "";


  /**
    * Constructs a sentence from its text.
    *
    * @param t  The text of the sentence.
   **/
  public Sentence(String t) { text = t; }

  /**
    * Constructor that sets the character offsets of this sentence.
    *
    * @param t  The text of the sentence.
    * @param s  The offset at which this child starts.
    * @param e  The offset at which this child ends.
   **/
  public Sentence(String t, int s, int e) {
    super(s, e);
    text = t;
  }


  /**
    * For debugging purposes, it's useful to insert print statements here.
    *
    * @param l            The list to add to.
    * @param i            The item to add.
    * @param description  A string describing why the addition is happening.
   **/
  private void myAdd(LinkedList l, int i, String description) {
    l.add(new Integer(i));
    //System.out.println("Adding boundary at " + i + ": " + description);
  }


  /**
    * Creates and returns a <code>LinkedVector</code> representation of this
    * sentence in which every <code>LinkedChild</code> is a <code>Word</code>.
    * Offset information is respected and propagated.
    *
    * @see    Word
    * @return A <code>LinkedVector</code> representation of this sentence.
   **/
  public LinkedVector wordSplit() {
    LinkedList boundaries = new LinkedList();

    // Whitespace always signals a word boundary.
    Matcher m = Pattern.compile("\\s+").matcher(text);
    while (m.find()) {
      myAdd(boundaries, m.start() - 1, ")whitespace");
      myAdd(boundaries, m.end(), "(whitespace");
    }

    // The beginning and end of the text are also word boundaries, unless
    // there's whitespace there.
    if (boundaries.size() > 0
        && ((Integer) boundaries.getLast()).intValue() >= text.length())
      boundaries.removeLast();
    else myAdd(boundaries, text.length() - 1, ")$");

    if (boundaries.size() > 1
        && ((Integer) boundaries.getFirst()).intValue() == -1)
      boundaries.removeFirst();
    else myAdd(boundaries, 0, "(^");

    Pattern pNoSpaceOrDigit = Pattern.compile("[^\\s\\d]");
    Pattern pDigit = Pattern.compile("\\d");
    Pattern pDigitCommaNoDigit = Pattern.compile("\\d,\\D");
    Pattern pNoDigitCommaDigit = Pattern.compile("\\D,\\d");

    // Commas are separate words unless they're part of a number.
    for (int i = text.indexOf(','); i != -1; i = text.indexOf(',', i + 1)) {
      if (i > 0 && text.charAt(i - 1) != ','
          && (pNoSpaceOrDigit.matcher(text.substring(i - 1, i)).find()
              || i + 1 == text.length()
                 && pDigit.matcher(text.substring(i - 1, i)).find()
              || i + 1 < text.length()
                 && pDigitCommaNoDigit
                    .matcher(text.substring(i - 1, i + 2)).find())) {
        myAdd(boundaries, i - 1, ")comma1");
        myAdd(boundaries, i, "(comma1");
      }

      if (i + 1 < text.length()
          && (pNoSpaceOrDigit.matcher(text.substring(i + 1, i + 2)).find()
              || i == 0 && pDigit.matcher(text.substring(i + 1, i + 2)).find()
              || i > 0
                 && pNoDigitCommaDigit.matcher(text.substring(i - 1, i + 2))
                                      .find())) {
        myAdd(boundaries, i, ")comma2");
        myAdd(boundaries, i + 1, "(comma2");
      }
    }

    Pattern pApostropheMask = Pattern.compile("[^\\s,']");
    //Pattern pAbbreviation = Pattern.compile("[A-Za-z]'[A-Za-z]");
    //Pattern pPossessive = Pattern.compile("s[^A-Za-z']");
    //Pattern pShortWill = Pattern.compile("ll[^A-Za-z']");

    // Apostrophes are handled by making consecutive occurrences a single
    // separate word and treating all other occurences as abbreviations which
    // should not be separated, with the following exceptions which are
    // considered contractions:
    //    '         Plural possessive (must follow the letter 's')
    //    'd        "I'd", "he'd", "they'd"
    //    'll       "I'll", "he'll", "they'll"
    //    'm        "I'm"
    //    're       "they're"
    //    's        Possessive
    //    've       "I've", "they've"
    //    n't       "can't", "won't", "shouldn't", "aren't"
    for (int i = text.indexOf('\''); i != -1; i = text.indexOf('\'', i + 1)) {
      if (i - 1 > 0 && Character.isLetter(text.charAt(i - 2))
          && text.charAt(i - 1) == 'n' && i + 1 < text.length()
          && text.charAt(i + 1) == 't'
          && (i + 2 == text.length()
              || !Character.isLetter(text.charAt(i + 2))
                 && text.charAt(i + 2) != '\'')) {
        myAdd(boundaries, i - 2, ")n't");
        myAdd(boundaries, i - 1, "(n't");
      }
      else
        if (i > 0
            && (pApostropheMask.matcher(text.substring(i - 1, i)).find()
                   && i + 1 < text.length() && text.charAt(i + 1) == '\''
                || text.charAt(i - 1) == 's'
                   && (i + 1 == text.length()
                       || !Character.isLetter(text.charAt(i + 1))
                          && text.charAt(i + 1) != '\'')
                || Character.isLetter(text.charAt(i - 1))
                   && (i + 1 < text.length()
                          && (i + 2 == text.length()
                              || !Character.isLetter(text.charAt(i + 2))
                                 && text.charAt(i + 2) != '\'')
                          && (text.charAt(i + 1) == 'd'
                              || text.charAt(i + 1) == 'm'
                              || text.charAt(i + 1) == 's')
                       || i + 2 < text.length()
                          && (i + 3 == text.length()
                              || !Character.isLetter(text.charAt(i + 3))
                                 && text.charAt(i + 3) != '\'')
                          && (text.substring(i + 1, i + 3).equals("ll")
                              || text.substring(i + 1, i + 3).equals("re")
                              || text.substring(i + 1, i + 3).equals("ve")))
                || text.charAt(i - 1) == '.' && i - 1 > 0
                   && Character.isLetter(text.charAt(i - 2))
                   && i + 1 < text.length()
                   && (i + 2 == text.length()
                       || !Character.isLetter(text.charAt(i + 2))
                          && text.charAt(i + 2) != '\'')
                   && text.charAt(i + 1) == 's')) {
          myAdd(boundaries, i - 1, ")contraction1");
          myAdd(boundaries, i, "(contraction1");
        }

      if (i + 1 < text.length()
          && pApostropheMask.matcher(text.substring(i + 1, i + 2)).find()
          && (!Character.isLetter(text.charAt(i + 1))
              || i > 0 && text.charAt(i - 1) == '\'')) {
        myAdd(boundaries, i, ")contraction2");
        myAdd(boundaries, i + 1, "(contraction2");
      }
    }

    Pattern pColonMask = Pattern.compile("[^\\s,':]");
    Pattern pColonSeparator = Pattern.compile("\\d:\\d");
    // Colons get separated into their own word unless it looks like they're
    // part of a time (or some other useful structure involving digits) or a
    // URL.
    for (int i = text.indexOf(':'); i != -1; i = text.indexOf(':', i + 1))
      if (!(i >= 2 && i + 2 < text.length()
              && pColonSeparator.matcher(text.substring(i - 2, i + 3)).find()
            || i > 2 && i + 2 < text.length()
               && (text.substring(i - 2, i + 3).equals("tp://")
                   || text.substring(i - 2, i + 3).equals("TP://"))
            || partOfURL(i))) {
        if (i >= 1 && pColonMask.matcher(text.substring(i - 1, i)).find()) {
          myAdd(boundaries, i - 1, ")colon1");
          myAdd(boundaries, i, "(colon1");
        }

        if (i + 1 < text.length()
            && pColonMask.matcher(text.substring(i + 1, i + 2)).find()) {
          myAdd(boundaries, i, ")colon2");
          myAdd(boundaries, i + 1, "(colon2");
        }
      }

    Pattern pSlashMask = Pattern.compile("[^\\s,':/]");
    Pattern pSlashSeparator = Pattern.compile("\\d/\\d");

    // Slashes get separated into their own word unless it looks like they're
    // part of a date (or some other useful structure involving digits) or a
    // URL.
    for (int i = text.indexOf('/'); i != -1; i = text.indexOf('/', i + 1))
      if (!(i >= 2 && i + 2 < text.length()
              && pSlashSeparator.matcher(text.substring(i - 2, i + 3)).find()
            || i > 3 && i + 1 < text.length()
               && (text.substring(i - 3, i + 2).equals("tp://")
                   || text.substring(i - 3, i + 2).equals("TP://"))
            || i > 4
               && (text.substring(i - 4, i + 1).equals("tp://")
                   || text.substring(i - 4, i + 1).equals("TP://"))
            || partOfURL(i))) {
        if (i >= 1 && pSlashMask.matcher(text.substring(i - 1, i)).find()) {
          myAdd(boundaries, i - 1, ")slash1");
          myAdd(boundaries, i, "(slash1");
        }

        if (i + 1 < text.length()
            && pSlashMask.matcher(text.substring(i + 1, i + 2)).find()) {
          myAdd(boundaries, i, ")slash2");
          myAdd(boundaries, i + 1, "(slash2");
        }
      }

    Pattern pDashMask = Pattern.compile("[^\\s,':/-]");
    Pattern pDashSeparator = Pattern.compile("\\w-\\w");
    Pattern pNegative1 = Pattern.compile("-\\.?\\d");
    Pattern pNegative2 = Pattern.compile("\\s-\\.?\\d");

    // Dashes get separated into their own words unless it looks like they're
    // part of some useful structure like a compound word, a number, or a URL.
    for (int i = text.indexOf('-'); i != -1; i = text.indexOf('-', i + 1))
      if (!(i + 1 < text.length() && i >= 1
              && pDashSeparator.matcher(text.substring(i - 1, i + 2)).find()
            || (i + 2 < text.length()
                && (i == 0
                      && pNegative1.matcher(text.substring(i, i + 3)).find()
                    || i > 0
                       && pNegative2.matcher(text.substring(i - 1, i + 3))
                                    .find()))
            || partOfURL(i))) {
        if (i >= 1 && pDashMask.matcher(text.substring(i - 1, i)).find()) {
          myAdd(boundaries, i - 1, ")dash1");
          myAdd(boundaries, i, "(dash1");
        }

        if (i + 1 < text.length()
            && pDashMask.matcher(text.substring(i + 1, i + 2)).find()) {
          myAdd(boundaries, i, ")dash2");
          myAdd(boundaries, i + 1, "(dash2");
        }
      }

    Pattern pDollarMask = Pattern.compile("[^\\s,':/\\$-]");
    Pattern pMoney1 = Pattern.compile("\\$\\.?\\d");
    Pattern pMoney2 = Pattern.compile("(\\s|-)\\$\\.?\\d");

    // Dollar signs get separated into their own words unless it looks like
    // they're in fact delimiting the start of a dollar amount, or are part of
    // a URL.
    for (int i = text.indexOf('$'); i != -1; i = text.indexOf('$', i + 1))
      if (!(i == 0 && i + 2 < text.length()
              && pMoney1.matcher(text.substring(i, i + 3)).find()
            || i > 0 && i + 2 < text.length()
               && pMoney2.matcher(text.substring(i - 1, i + 3)).find()
            || partOfURL(i))) {
        if (i >= 1 && pDollarMask.matcher(text.substring(i - 1, i)).find()) {
          myAdd(boundaries, i - 1, ")dollar1");
          myAdd(boundaries, i, "(dollar1");
        }

        if (i + 1 < text.length()
            && pDollarMask.matcher(text.substring(i + 1, i + 2)).find()) {
          myAdd(boundaries, i, ")dollar2");
          myAdd(boundaries, i + 1, "(dollar2");
        }
      }

    Pattern pBeforeElipsis = Pattern.compile("[^\\s,':/\\$\\.-]\\.\\.\\.");
    Pattern pAfterElipsis = Pattern.compile("\\.\\.\\.[^\\s,':/\\$\\.-]");

    // Three or more consecutive periods form their own word.
    for (int i = text.indexOf('.'); i != -1; i = text.indexOf('.', i + 1)) {
      if (i > 0 && i + 2 < text.length()
          && pBeforeElipsis.matcher(text.substring(i - 1, i + 3)).find()) {
        myAdd(boundaries, i - 1, ")ellipsis1");
        myAdd(boundaries, i, "(ellipsis1");
      }

      if (i >= 2 && i + 1 < text.length()
          && pAfterElipsis.matcher(text.substring(i - 2, i + 2)).find()) {
        myAdd(boundaries, i, ")ellipsis2");
        myAdd(boundaries, i + 1, "(ellipsis2");
      }
    }

    // If the last occurrence of a period in the sentence comes after all
    // occurrences of letters and digits, it is an end of sentence marker
    // which constitutes its own word, unless it appears immediately after two
    // other periods.
    int period = text.lastIndexOf('.');
    if (period != -1) {
      boolean endOfSentence = true;
      for (int i = period + 1; i < text.length() && endOfSentence; ++i)
        endOfSentence = !Character.isLetterOrDigit(text.charAt(i));

      if (endOfSentence) {
        if (period >= 1
            && (text.charAt(period - 1) != '.' || period == 1
                || text.charAt(period - 2) != '.')
            && pDollarMask.matcher(text.substring(period - 1, period)).find())
        {
          myAdd(boundaries, period - 1, ")period1");
          myAdd(boundaries, period, "(period1");
        }

        if (period + 1 < text.length()
            && (period == 0 || text.charAt(period - 1) != '.' || period == 1
                || text.charAt(period - 2) != '.')
            && pDollarMask.matcher(text.substring(period + 1, period + 2))
                          .find()) {
          myAdd(boundaries, period, ")period2");
          myAdd(boundaries, period + 1, "(period2");
        }
      }
      else period = -1;
    }

    // All other punctuation marks constitute their own words, unless they
    // appear immediately after themselves (consecutive identical punctuation
    // marks form a single word) or are part of a URL.
    Pattern pPunctuation = Pattern.compile("[^\\s\\w,'\\.:/\\$-]");
    m = pPunctuation.matcher(text);

    while (m.find())
      if (!partOfURL(m.start())) {
        if (m.start() + 1 < text.length()
            && text.charAt(m.start()) != text.charAt(m.start() + 1)
            && m.start() + 1 != period
            && pPunctuation
               .matcher(text.substring(m.start() + 1, m.start() + 2)).find())
        {
          myAdd(boundaries, m.start(), ")punctuation1");
          myAdd(boundaries, m.start() + 1, "(punctuation1");
        }
      }

    m = Pattern.compile("[^\\s\\w,'\\.:/\\$-]\\w").matcher(text);
    while (m.find())
      if (!partOfURL(m.start())) {
        myAdd(boundaries, m.start(), ")punctuation2");
        myAdd(boundaries, m.start() + 1, "(punctuation2");
      }

    m = Pattern.compile("\\w[^\\s\\w,'\\.:/\\$-]").matcher(text);
    while (m.find())
      if (!partOfURL(m.start())) {
        myAdd(boundaries, m.start(), ")punctuation3");
        myAdd(boundaries, m.start() + 1, "(punctuation3");
      }

    // Now we just have to create the LinkedVector.
    Integer[] temp =
      (Integer[]) boundaries.toArray(new Integer[boundaries.size()]);
    int[] I = new int[temp.length];
    for (int i = 0; i < I.length; ++i) I[i] = temp[i].intValue();
    Arrays.sort(I);

    Word w = new Word(text.substring(I[0], I[1] + 1), I[0] + start,
                      I[1] + start);
    for (int i = 2; i < I.length; i += 2) {
      w.next = new Word(text.substring(I[i], I[i + 1] + 1),
                        w,
                        I[i] + start,
                        I[i + 1] + start);
      w = (Word) w.next;
    }

    inURL = null;
    return new LinkedVector(w);
  }


  /**
    * Does a simple check to determine if the symbol at the specified index in
    * the specified string is likely to be part of a URL.  If the specified
    * text contains any of the following strings before the specified symbol,
    * and there is no whitespace in between the two, the specified symbol is
    * deemed likely to be part of a URL.
    *
    * @param index  The index of the symbol in question.
    * @return <code>true</code> if and only if the specified symbol appears to
    *         be part of a URL.
   **/
  private boolean partOfURL(int index) {
    if (inURL != null) return inURL[index];
    inURL = new boolean[text.length()];

    StringBuffer pattern = new StringBuffer();
    pattern.append("(?i)(");
    pattern.append(protocols[0]);
    for (int i = 1; i < protocols.length; ++i) {
      pattern.append("|");
      pattern.append(protocols[i]);
    }

    pattern.append(")://\\S+|[a-zA-Z0-9][a-zA-Z0-9-]*\\.(");
    pattern.append(topLevelDomains[0]);
    for (int i = 0; i < topLevelDomains.length; ++i) {
      pattern.append("|");
      pattern.append(topLevelDomains[i]);
    }
    pattern.append(")(/\\S+)?");

    Matcher m = Pattern.compile(pattern.toString()).matcher(text);
    while (m.find())
      for (int i = m.start(); i < m.end(); ++i) inURL[i] = true;
    return inURL[index];
  }


  /**
    * The string representation of a <code>Sentence</code> is just its text.
    *
    * @return The text of this sentence.
   **/
  public String toString() { return text; }
}

