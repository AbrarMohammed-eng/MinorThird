package edu.cmu.minorthird.text;

import java.util.Set;
import java.util.TreeSet;

/** An empty text environment. 
 *
 * @author William Cohen
*/

public class EmptyEnv implements TextEnv
{
	public static final TreeSet EMPTY_SET = new TreeSet();

	public EmptyEnv() {;}

	public boolean isAnnotatedBy(String s) { return false; }
	public void setAnnotatedBy(String s) { ; }
	public TextBase getTextBase() { throw new UnsupportedOperationException("no text base"); }
	public boolean inDict(Token token,String dict) { return false; }
	public String getProperty(Token token,String prop) { return null; }
	public Set getTokenProperties() { return EMPTY_SET; }
	public String getProperty(Span span,String prop) { return null; }
	public Set getSpanProperties() { return EMPTY_SET; }
	public boolean hasType(Span span,String type) { return false; }
	public Span.Looper instanceIterator(String type) { return nullLooper(); }
	public Span.Looper instanceIterator(String type,String documentId) { return nullLooper(); }
	public Set getTypes() { return EMPTY_SET; }
	public boolean isType(String type) { return false; }
	public Span.Looper closureIterator(String type) { return nullLooper(); }
	public Span.Looper closureIterator(String type, String documentId) { return nullLooper(); }
	public String showTokenProp(TextBase base, String prop) { return ""; }
	public Details getDetails(Span span,String type) { return null; }

	private Span.Looper nullLooper() { return new BasicSpanLooper( EMPTY_SET ); }
}
