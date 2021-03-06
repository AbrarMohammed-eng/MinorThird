package LBJ2.nlp;

import LBJ2.parse.LinkedVector;
import LBJ2.parse.Parser;


/**
  * This parser calls another parser that returns arrays of
  * <code>String</code>s, converts the <code>String</code>s to {@link Word}s,
  * and returns {@link LinkedVector}s of {@link Word}s.
  *
  * @author Nick Rizzolo
 **/
public class StringArraysToWords implements Parser
{
  /** A parser that returns arrays of <code>String</code>s. */
  protected Parser parser;


  /**
    * Creates the parser.
    *
    * @param p  A parser that returns arrays of <code>String</code>s.
   **/
  public StringArraysToWords(Parser p) { parser = p; }


  /**
    * Returns the next array of {@link Word}s.
    *
    * @return The next {@link LinkedVector} of {@link Word}s parsed, or
    *         <code>null</code> if there are no more children in the stream.
   **/
  public Object next() {
    return convert((String[]) parser.next());
  }


  /**
    * Given an array of <code>String</code>s, this method creates a new
    * {@link LinkedVector} containing {@link Word}s.
    *
    * @param a  An array of <code>String</code>s.
    * @return A {@link LinkedVector} of {@link Word}s corresponding to the
    *         input <code>String</code>s.
   **/
  public static LinkedVector convert(String[] a) {
    if (a == null) return null;
    if (a.length == 0) return new LinkedVector();

    Word w = new Word(a[0]);
    for (int i = 1; i < a.length; ++i) {
      w.next = new Word(a[i], null, w);
      w = (Word) w.next;
    }

    return new LinkedVector(w);
  }


  /** Sets this parser back to the beginning of the raw data. */
  public void reset() { parser.reset(); }


  /** Frees any resources this parser may be holding. */
  public void close() { parser.close(); }
}

