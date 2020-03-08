package com.salesforce.bazel.eclipse.projectview;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension3;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.salesforce.bazel.eclipse.BazelPluginActivator;

/**
 * File system path based Content Assistant (auto-completion). Adapted from
 * org.eclipse.ui.texteditor.HippieProposalProcessor.
 *
 * @author stoens
 * @since March 2020
 */
public final class BazelPackageContentAssistProcessor implements IContentAssistProcessor {

    private static final ICompletionProposal[] NO_PROPOSALS= new ICompletionProposal[0];
    private static final IContextInformation[] NO_CONTEXTS= new IContextInformation[0];
    
    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
        try {
            String prefix = getPrefix(viewer, offset);
            String matchSuffix = null;
            String suggestionPrefix = "";

            File f = new File(BazelPluginActivator.getBazelWorkspaceRootDirectory(), prefix);
            if (f.isDirectory()) {
                if (!prefix.isEmpty() && !prefix.endsWith(File.separator)) {
                    suggestionPrefix = File.separator;
                }                
            } else {
                int i = prefix.lastIndexOf(File.separator);
                if (i == -1) {
                    f = BazelPluginActivator.getBazelWorkspaceRootDirectory();
                } else {
                    f = new File(BazelPluginActivator.getBazelWorkspaceRootDirectory(), prefix.substring(0, i));
                }
                matchSuffix = prefix.substring(i+1);    
            }
            
            if (f.isDirectory()) {
                List<String> directories = Arrays.asList(f.list(new FilenameFilter() {                    
                    @Override
                    public boolean accept(File dir, String name) {
                        File f = new File(dir, name);
                        return !name.startsWith(".") && f.isDirectory() && !Files.isSymbolicLink(f.toPath());
                    }
                }));
                if (matchSuffix != null) {
                    List<String> updatedDirectories = new ArrayList<>();
                    for (String d : directories) {
                        if (d.startsWith(matchSuffix)) {
                            updatedDirectories.add(d.substring(matchSuffix.length()));
                        }
                    }
                    directories = updatedDirectories;
                }
                Collections.sort(directories);
                List<ICompletionProposal> proposals = new ArrayList<>(directories.size());
                for (String dir : directories) {
                    proposals.add(new Proposal(suggestionPrefix + dir, prefix, offset));
                }
                return proposals.toArray(new ICompletionProposal[proposals.size()]);
            }
        } catch (BadLocationException x) {

        }
        return NO_PROPOSALS;
    }

    private String getPrefix(ITextViewer viewer, int offset) throws BadLocationException {
        IDocument doc = viewer.getDocument();
        if (doc == null || offset > doc.getLength())
            return null;

        int length= 0;
        while (--offset >= 0 && doc.getChar(offset) != '\n') {
            length++;
        }

        return doc.get(offset + 1, length).trim();
    }

    @Override
    public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
        return NO_CONTEXTS;
    }

    @Override
    public char[] getCompletionProposalAutoActivationCharacters() {
        return null;
    }

    @Override
    public char[] getContextInformationAutoActivationCharacters() {
        return null;
    }

    @Override
    public IContextInformationValidator getContextInformationValidator() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    private static final class Proposal implements ICompletionProposal, ICompletionProposalExtension, ICompletionProposalExtension2, ICompletionProposalExtension3 {
        private final String fString;
        private final String fPrefix;
        private final int fOffset;

        Proposal(String string, String prefix, int offset) {
            fString= string;
            fPrefix= prefix;
            fOffset= offset;
        }

        @Override
        public void apply(IDocument document) {
            apply(null, '\0', 0, fOffset);
        }

        @Override
        public Point getSelection(IDocument document) {
            return new Point(fOffset + fString.length(), 0);
        }

        @Override
        public String getAdditionalProposalInfo() {
            return null;
        }

        @Override
        public String getDisplayString() {
            return fPrefix + fString;
        }

        @Override
        public Image getImage() {
            return null;
        }

        @Override
        public IContextInformation getContextInformation() {
            return null;
        }

        @Override
        public void apply(IDocument document, char trigger, int offset) {
            try {
                String replacement= fString.substring(offset - fOffset);
                document.replace(offset, 0, replacement);
            } catch (BadLocationException x) {
            }
        }

        @Override
        public boolean isValidFor(IDocument document, int offset) {
            return validate(document, offset, null);
        }

        @Override
        public char[] getTriggerCharacters() {
            return null;
        }

        @Override
        public int getContextInformationPosition() {
            return 0;
        }

        @Override
        public void apply(ITextViewer viewer, char trigger, int stateMask, int offset) {
            apply(viewer.getDocument(), trigger, offset);
        }

        @Override
        public void selected(ITextViewer viewer, boolean smartToggle) {
        }

        @Override
        public void unselected(ITextViewer viewer) {
        }

        @Override
        public boolean validate(IDocument document, int offset, DocumentEvent event) {
            try {
                int prefixStart= fOffset - fPrefix.length();
                return offset >= fOffset && offset < fOffset + fString.length() && document.get(prefixStart, offset - (prefixStart)).equals((fPrefix + fString).substring(0, offset - prefixStart));
            } catch (BadLocationException x) {
                return false;
            }
        }

        @Override
        public IInformationControlCreator getInformationControlCreator() {
            return null;
        }

        @Override
        public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
            return fPrefix + fString;
        }

        @Override
        public int getPrefixCompletionStart(IDocument document, int completionOffset) {
            return fOffset - fPrefix.length();
        }
    }
}
