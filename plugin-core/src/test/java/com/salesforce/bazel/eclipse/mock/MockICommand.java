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
 */
package com.salesforce.bazel.eclipse.mock;

import java.util.Map;

import org.eclipse.core.resources.ICommand;

public class MockICommand implements ICommand {
    private static final String UOE_MSG = "MockICommand is pay as you go, you have hit a method that is not implemented."; 
    private String builderName;
    
    @Override
    public String getBuilderName() {
        return builderName;
    }

    @Override
    public void setBuilderName(String builderName) {
        this.builderName = builderName;
    }


    
    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.

    @Override
    public Map<String, String> getArguments() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isBuilding(int kind) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isConfigurable() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setArguments(Map<String, String> args) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setBuilding(int kind, boolean value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
