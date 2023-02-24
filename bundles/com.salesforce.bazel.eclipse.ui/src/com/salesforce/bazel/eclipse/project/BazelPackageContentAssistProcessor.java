/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.eclipse.project;

import java.io.File;
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

import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;

/**
 * File system path based Content Assistant (auto-completion). Adapted from
 * org.eclipse.ui.texteditor.HippieProposalProcessor.
 */
public final class BazelPackageContentAssistProcessor implements IContentAssistProcessor {

    private static final class Proposal implements ICompletionProposal, ICompletionProposalExtension,
            ICompletionProposalExtension2, ICompletionProposalExtension3 {
        private final String fString;
        private final String fPrefix;
        private final int fOffset;

        Proposal(String string, String prefix, int offset) {
            fString = string;
            fPrefix = prefix;
            fOffset = offset;
        }

        @Override
        public void apply(IDocument document) {
            apply(null, '\0', 0, fOffset);
        }

        @Override
        public void apply(IDocument document, char trigger, int offset) {
            try {
                var replacement = fString.substring(offset - fOffset);
                document.replace(offset, 0, replacement);
            } catch (BadLocationException x) {}
        }

        @Override
        public void apply(ITextViewer viewer, char trigger, int stateMask, int offset) {
            apply(viewer.getDocument(), trigger, offset);
        }

        @Override
        public String getAdditionalProposalInfo() {
            return null;
        }

        @Override
        public IContextInformation getContextInformation() {
            return null;
        }

        @Override
        public int getContextInformationPosition() {
            return 0;
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
        public IInformationControlCreator getInformationControlCreator() {
            return null;
        }

        @Override
        public int getPrefixCompletionStart(IDocument document, int completionOffset) {
            return fOffset - fPrefix.length();
        }

        @Override
        public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
            return fPrefix + fString;
        }

        @Override
        public Point getSelection(IDocument document) {
            return new Point(fOffset + fString.length(), 0);
        }

        @Override
        public char[] getTriggerCharacters() {
            return null;
        }

        @Override
        public boolean isValidFor(IDocument document, int offset) {
            return validate(document, offset, null);
        }

        @Override
        public void selected(ITextViewer viewer, boolean smartToggle) {}

        @Override
        public void unselected(ITextViewer viewer) {}

        @Override
        public boolean validate(IDocument document, int offset, DocumentEvent event) {
            try {
                var prefixStart = fOffset - fPrefix.length();
                return (offset >= fOffset) && (offset < (fOffset + fString.length()))
                        && document.get(prefixStart, offset - (prefixStart))
                                .equals((fPrefix + fString).substring(0, offset - prefixStart));
            } catch (BadLocationException x) {
                return false;
            }
        }
    }

    private static final ICompletionProposal[] NO_PROPOSALS = {};

    private static final IContextInformation[] NO_CONTEXTS = {};

    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
        try {
            var prefix = getPrefix(viewer, offset);
            String matchSuffix = null;
            var suggestionPrefix = "";

            var f = new File(EclipseBazelWorkspaceContext.getInstance().getBazelWorkspaceRootDirectory(), prefix);
            if (f.isDirectory()) {
                if (!prefix.isEmpty() && !prefix.endsWith(File.separator)) {
                    suggestionPrefix = File.separator;
                }
            } else {
                var i = prefix.lastIndexOf(File.separator);
                if (i == -1) {
                    f = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspaceRootDirectory();
                } else {
                    f = new File(EclipseBazelWorkspaceContext.getInstance().getBazelWorkspaceRootDirectory(),
                            prefix.substring(0, i));
                }
                matchSuffix = prefix.substring(i + 1);
            }

            if (f.isDirectory()) {
                List<String> directories = Arrays.asList(f.list((dir, name) -> {
                    var f1 = new File(dir, name);
                    return !name.startsWith(".") && f1.isDirectory() && !Files.isSymbolicLink(f1.toPath());
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

    private String getPrefix(ITextViewer viewer, int offset) throws BadLocationException {
        var doc = viewer.getDocument();
        if ((doc == null) || (offset > doc.getLength())) {
            return null;
        }

        var length = 0;
        while ((--offset >= 0) && (doc.getChar(offset) != '\n')) {
            length++;
        }

        return doc.get(offset + 1, length).trim();
    }
}
