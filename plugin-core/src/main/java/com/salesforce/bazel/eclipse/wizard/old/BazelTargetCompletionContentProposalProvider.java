// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.salesforce.bazel.eclipse.wizard.old;

import java.io.IOException;
import java.util.List;

import org.eclipse.jface.fieldassist.ContentProposal;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;

/**
 * A {@link IContentProposalProvider} to provide completion for Bazel. Use the
 * {@link #setBazelInstance(BazelWorkspaceCommandRunner)} method to provide with the {@link BazelWorkspaceCommandRunner}
 * interface to Bazel.
 * 
 * REFERENCE ONLY! This is the old mechanism from the original Bazel plugin. It used the New... extension point instead
 * of the Import... extension point.
 * 
 */
public class BazelTargetCompletionContentProposalProvider implements IContentProposalProvider {

    private BazelWorkspaceCommandRunner bazel = null;

    @Override
    public IContentProposal[] getProposals(String contents, int position) {
        if (bazel == null) {
            return null;
        }
        try {
            List<String> completions = bazel.getMatchingTargets(contents.substring(0, position), null);
            if (completions != null) {
                IContentProposal[] result = new IContentProposal[completions.size()];
                int i = 0;
                for (String s : completions) {
                    result[i] = new ContentProposal(s);
                    i++;
                }
                return result;
            }
        } catch (IOException e) {
            BazelPluginActivator.error("Failed to run Bazel to get completion information", e);
        } catch (InterruptedException e) {
            BazelPluginActivator.error("Bazel was interrupted", e);
        } catch (BazelCommandLineToolConfigurationException e) {
            BazelPluginActivator.error("Bazel not found: " + e.getMessage());
        }
        return null;
    }

    /**
     * Set the {@link BazelWorkspaceCommandRunner} to use to query for completion targets.
     */
    public void setBazelInstance(BazelWorkspaceCommandRunner bazel) {
        this.bazel = bazel;
    }

}
