/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.eclipse.ui.utils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;

/**
 * Some useful methods copied from org.eclipse.debug.internal.ui.SWTFactory (which isn't API)
 */
public class BazelSWTFactory {

    /**
     * Creates a Composite widget.
     *
     * @param parent
     *            the parent composite to add this composite to
     * @param font
     *            the font to set on the control
     * @param columns
     *            the number of columns within the composite
     * @param hspan
     *            the horizontal span the composite should take up on the parent
     * @param fill
     *            the style for how this composite should fill into its parent
     *
     * @return the new Composite
     */
    public static Composite createComposite(Composite parent, Font font, int columns, int hspan, int fill) {
        var g = new Composite(parent, SWT.NONE);
        g.setLayout(new GridLayout(columns, false));
        g.setFont(font);
        var gd = new GridData(fill);
        gd.horizontalSpan = hspan;
        g.setLayoutData(gd);
        return g;
    }

    /**
     * Creates a Group widget.
     *
     * @param parent
     *            the parent composite to add this group to
     * @param text
     *            the text for the heading of the group
     * @param columns
     *            the number of columns within the group
     * @param hspan
     *            the horizontal span the group should take up on the parent
     * @param fill
     *            the style for how this composite should fill into its parent
     *
     * @return the new Group
     */
    public static Group createGroup(Composite parent, String text, int columns, int hspan, int fill) {
        var g = new Group(parent, SWT.NONE);
        g.setLayout(new GridLayout(columns, false));
        g.setText(text);
        g.setFont(parent.getFont());
        var gd = new GridData(fill);
        gd.horizontalSpan = hspan;
        g.setLayoutData(gd);
        return g;
    }

    /**
     * Creates a new text widget
     *
     * @param parent
     *            the parent composite to add this text widget to
     * @param hspan
     *            the horizontal span to take up on the parent composite
     *
     * @return the new Text widget
     */
    public static Text createSingleText(Composite parent, int hspan) {
        var t = new Text(parent, SWT.SINGLE | SWT.BORDER);
        t.setFont(parent.getFont());
        var gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = hspan;
        t.setLayoutData(gd);
        return t;
    }
}
