package edu.cmu.minorthird.text.gui;

import edu.cmu.minorthird.text.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.SimpleAttributeSet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

/** Interactivly edit the subspans associated with a particular
 * document span.
 *
 * @author William Cohen
 */

public class SpanEditor extends ViewerTracker
{

    public static final String EDITOR_PROP = "_edited";

    // internal state
    private String importType, exportType;
    private JLabel ioTypeLabel;
    private TreeSet editedSpans;
	  private int editSpanCursor = -1; // indicates nothing selected
    private boolean readOnly = false;

    // buttons
    JButton readOnlyButton = new JButton(new ReadOnlyButton(readOnly ? "Edit" : "Read"));
    JButton importButton = new JButton(new ImportGuessSpans("Import"));
    JButton exportButton = new JButton(new ExportGuessSpans("Export"));
    JButton addButton = new JButton(new AddSelection("Add"));
    JButton deleteButton = new JButton(new DeleteCursoredSpan("Delete"));
    JButton prevButton = new JButton(new MoveSpanCursor("Prev", -1));
    JButton nextButton = new JButton(new MoveSpanCursor("Next", +1));

    private ArrayList buttonsThatChangeStuff = new ArrayList();

    /**
     * @param viewEnv a superset of editEnv which may include some additional read-only information
     * @param editEnv the environment being modified
     * @param documentList the document Span being edited is associated with
     * the selected entry of the documentList.
     * @param spanPainter used to repaint documentList elements
     * @param statusMsg a JLabel used for status messages.
     */
    public SpanEditor(
            TextEnv viewEnv,
            MutableTextEnv editEnv,
            JList documentList,
            SpanPainter spanPainter,
            StatusMessage statusMsg)
    {
        super(viewEnv, editEnv, documentList, spanPainter, statusMsg);
        init();

    }

    private void init()
    {
        this.importType = this.exportType = null;
        this.ioTypeLabel = new JLabel("Types: [None/None]");

        initLayout();

        loadSpan(nullSpan());
    }

    private void initLayout()
    {
        //
        // layout stuff
        //
        setLayout(new GridBagLayout());
        GridBagConstraints gbc;

        int col = 0;
        gbc = new GridBagConstraints();

        //------------ ioType button ------------------//
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        add(ioTypeLabel, gbc);

        //------------ read only  button ------------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        add(readOnlyButton, gbc);

        //------------ import button ------------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        buttonsThatChangeStuff.add(importButton);
        add(importButton, gbc);

        //------------ export button ------------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        buttonsThatChangeStuff.add(exportButton);
        add(exportButton, gbc);

        //------------ add button -------------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        buttonsThatChangeStuff.add(addButton);
        add(addButton, gbc);

        //-------------- delete button --------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        buttonsThatChangeStuff.add(deleteButton);
        add(deleteButton, gbc);

        //------------ prev button ---------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        add(prevButton, gbc);

        //-------------- next button --------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        add(nextButton, gbc);

        //------------- up button -----------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        add(upButton, gbc);

        //------------- down button ------------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        add(downButton, gbc);

        //-------------- context slider -----------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        add(contextWidthSlider, gbc);

        //----------- save button -------------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;
        gbc.gridx = ++col;
        gbc.gridy = 2;
        add(saveButton, gbc);
        buttonsThatChangeStuff.add(saveButton);
        //System.out.println("create saveButton: saveAsFile=" + saveAsFile + " enabled: " + (saveAsFile != null));
        saveButton.setEnabled(saveAsFile != null);

        //------------- editorHolder ---------------//
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = col;
        add(editorHolder, gbc);
    }

    /** Set mode to read-only or not.  In read-only mode, the
     * document viewed has the same highlighting as in the
     * documentList.  In write mode, the "truth" spans
     * are shown, and the "guess" spans are imported.
     */
    public void setReadOnly(boolean readOnly)
    {
        for (Iterator i = buttonsThatChangeStuff.iterator(); i.hasNext();)
        {
            JButton button = (JButton) i.next();
            button.setEnabled(readOnly ? false : true);
        }
        this.readOnly = readOnly;
    }

    /** Declare which types are being edited. */
    public void setTypesBeingEdited(String inType, String outType)
    {
        this.importType = inType;
        this.exportType = outType;
        ioTypeLabel.setText("Edit: " + importType + "/" + exportType);
    }


    protected void loadSpanHook()
    {
        if (readOnly && !DUMMY_ID.equals(documentSpan.getDocumentId()))
        {
            importDocumentListMarkup(documentSpan.getDocumentId());
        }
        Keymap keymap = editorPane.getKeymap(JTextComponent.DEFAULT_KEYMAP);
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("control I"), importButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("control E"), exportButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("control S"), exportButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("control A"), addButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("DELETE"), deleteButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("control D"), deleteButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("LEFT"), prevButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("control B"), prevButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("RIGHT"), nextButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("control F"), nextButton.getAction());
        keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("TAB"), nextButton.getAction());
        editedSpans = new TreeSet();
    }

    /** Toggles readOnly status */
    private class ReadOnlyButton extends AbstractAction
    {
        public ReadOnlyButton(String msg)
        {
            super(msg);
        }

        public void actionPerformed(ActionEvent event)
        {
            setReadOnly(!readOnly);
            if (documentSpan != null) loadSpan(documentSpan);
            readOnlyButton.setText(readOnly ? "Edit" : "Read");
        }
    }

    /** Imports the spans associated with the documentSpan into
     * the set currently being edited */
    private class ImportGuessSpans extends AbstractAction
    {
        public ImportGuessSpans(String msg)
        {
            super(msg);
        }

        public void actionPerformed(ActionEvent event)
        {
            if (importType == null)
            {
                statusMsg.display("what type?");
                return;
            }
            editedDoc.resetHighlights();
            editedSpans = new TreeSet();
            //System.out.println("viewenv: "+viewEnv);
            for (Span.Looper i = viewEnv.instanceIterator(importType, documentSpan.getDocumentId()); i.hasNext();)
            {
                Span guessSpan = i.nextSpan();
                editedDoc.highlight(guessSpan, HiliteColors.yellow);
                editedSpans.add(guessSpan);
            }
            editSpanCursor = -1;
            statusMsg.display("imported " + editedSpans.size() + " " + importType + " spans");
        }
    }


    /** Exports the spans associated with the documentSpan into
     * the set currently being edited */
    private class ExportGuessSpans extends AbstractAction
    {
        public ExportGuessSpans(String msg)
        {
            super(msg);
        }

        public void actionPerformed(ActionEvent event)
        {
            if (exportType == null)
            {
                statusMsg.display("what type?");
                return;
            }
            Span.Looper newSpans = new BasicSpanLooper(editedSpans.iterator());
            //System.out.println("exportType: "+exportType+" documentSpan "+documentSpan+" newSpans "+newSpans);
            //System.out.println("editedSpans: "+editedSpans);
						//System.out.println("current export type spans:");
						//for (Span.Looper ii=editEnv.instanceIterator(exportType,documentSpan.getDocumentId()); ii.hasNext(); )
						//System.out.println(" - "+ii.nextSpan());
            editEnv.defineTypeInside(exportType, documentSpan, newSpans);
						//System.out.println("new spans type: "+exportType);
						//for (Span.Looper ii=editEnv.instanceIterator(exportType,documentSpan.getDocumentId()); ii.hasNext(); )
						//System.out.println(" - "+ii.nextSpan());
            //editEnv.setProperty(documentSpan.documentSpan(), EDITOR_PROP, "t");
            //System.out.println("SE: will paint "+documentSpan+" id: "+documentSpan.getDocumentId());
            spanPainter.paintDocument(documentSpan.getDocumentId());
            editSpanCursor = -1;
            statusMsg.display("exported " + editedSpans.size() + " " + exportType + " spans");
        }
    }

    /** Add span associated with selected text. */
    private class AddSelection extends AbstractAction
    {
        public AddSelection(String msg)
        {
            super(msg);
        }

        public void actionPerformed(ActionEvent event)
        {
            int lo = editorPane.getSelectionStart();
            int hi = editorPane.getSelectionEnd();
            Span span = documentSpan.charIndexSubSpan(lo, hi);
            // figure out if we need to move the selected span
            int correction = 0;
            if (editSpanCursor>=0 && (span.compareTo(getEditSpan(editSpanCursor)) < 0))
            {
                correction = 1;
            }
            editedDoc.highlight(span, HiliteColors.yellow);
            editedSpans.add(span);
            editSpanCursor += correction;
            statusMsg.display("adding " + span);
        }
    }

    /** Delete the span under the cursor. */
    private class DeleteCursoredSpan extends AbstractAction
    {
        public DeleteCursoredSpan(String msg)
        {
            super(msg);
        }

        public void actionPerformed(ActionEvent event)
        {
            Span span = null;
            if (editSpanCursor >= 0)
            {
                span = getEditSpan(editSpanCursor);
            }
            else
            {
                int lo = editorPane.getSelectionStart();
                int hi = editorPane.getSelectionEnd();
                span = documentSpan.charIndexSubSpan(lo, hi);
            }
            editedDoc.highlight(span, SimpleAttributeSet.EMPTY);
            editedSpans.remove(span);
            if (editSpanCursor >= editedSpans.size())
            {
                editSpanCursor = -1;
            }
            else if (editSpanCursor >= 0)
            {
                // highlight next span after the deleted one
                editedDoc.highlight(getEditSpan(editSpanCursor), HiliteColors.cursorColor);
            }
        }
    }

    /** Move through list of spans */
    private class MoveSpanCursor extends AbstractAction
    {
        private int delta;

        public MoveSpanCursor(String msg, int delta)
        {
            super(msg);
            this.delta = delta;
        }

        public void actionPerformed(ActionEvent event)
        {
            if (editedSpans == null || editedSpans.isEmpty()) return;
            if (editSpanCursor >= 0)
            {
                editedDoc.highlight(getEditSpan(editSpanCursor), HiliteColors.yellow);
                editSpanCursor = editSpanCursor + delta;
								// wrap around
                if (editSpanCursor < 0) editSpanCursor += editedSpans.size();
                else if (editSpanCursor >= editedSpans.size()) editSpanCursor -= editedSpans.size();
            }
            else
            {
							// move to first legit span
							editSpanCursor = 0; 
            }
            editedDoc.highlight(getEditSpan(editSpanCursor), HiliteColors.cursorColor);
            statusMsg.display("to span#" + editSpanCursor + ": " + getEditSpan(editSpanCursor));
        }
    }

    private Span getEditSpan(int k)
    {
        for (Iterator i = editedSpans.iterator(); i.hasNext();)
        {
            Span s = (Span) i.next();
            if (k-- == 0) return s;
        }
        throw new IllegalStateException("bad editedSpan index " + k);
    }
}

