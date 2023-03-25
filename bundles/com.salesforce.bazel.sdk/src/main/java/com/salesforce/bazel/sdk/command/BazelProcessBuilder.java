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
package com.salesforce.bazel.sdk.command;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.List;
import java.util.Map;

/**
 * A lightweight wrapper around java.lang.ProcessBuilder. To make functional testing possible where ProcessBuilder
 * should be mocked (it is a final class) we created this thin wrapper around it exposing the methods that we use. This
 * class gets mocked during some test executions.
 */
public class BazelProcessBuilder {

    private final ProcessBuilder innerProcessBuilder;

    public BazelProcessBuilder(List<String> args) {
        innerProcessBuilder = new ProcessBuilder(args);
    }

    public BazelProcessBuilder(List<String> args, Map<String, String> bazelEnvironmentVariables) {
        innerProcessBuilder = new ProcessBuilder(args);
        if (bazelEnvironmentVariables != null) {
            var liveEnvironmentVariables = innerProcessBuilder.environment();
            if (liveEnvironmentVariables != null) {
                liveEnvironmentVariables.putAll(bazelEnvironmentVariables);
            }
        }
    }

    /**
     * Returns this process builder's operating system program and arguments. The returned list is <i>not</i> a copy.
     * Subsequent updates to the list will be reflected in the state of this process builder.
     *
     * @return this process builder's program and its arguments
     */
    public List<String> command() {
        return innerProcessBuilder.command();
    }

    /**
     * Returns this process builder's working directory.
     *
     * Subprocesses subsequently started by this object's {@link #start()} method will use this as their working
     * directory. The returned value may be {@code null} -- this means to use the working directory of the current Java
     * process, usually the directory named by the system property {@code user.dir}, as the working directory of the
     * child process.
     *
     * @return this process builder's working directory
     */
    public File directory() {
        return innerProcessBuilder.directory();
    }

    /**
     * Sets this process builder's working directory.
     *
     * Subprocesses subsequently started by this object's {@link #start()} method will use this as their working
     * directory. The argument may be {@code null} -- this means to use the working directory of the current Java
     * process, usually the directory named by the system property {@code user.dir}, as the working directory of the
     * child process.
     *
     * @param directory
     *            the new working directory
     * @return this process builder
     */
    public BazelProcessBuilder directory(File directory) {
        innerProcessBuilder.directory(directory);
        return this;
    }

    /**
     * Return the inner ProcessBuilder. This method is likely not available during test execution.
     */
    public ProcessBuilder getProcessBuilder() {
        return innerProcessBuilder;
    }

    /**
     * Sets this process builder's standard error destination.
     *
     * Subprocesses subsequently started by this object's {@link #start()} method send their standard error to this
     * destination.
     *
     * <p>
     * If the destination is {@link Redirect#PIPE Redirect.PIPE} (the initial value), then the error output of a
     * subprocess can be read using the input stream returned by {@link Process#getErrorStream()}. If the destination is
     * set to any other value, then {@link Process#getErrorStream()} will return a <a href="#redirect-output">null input
     * stream</a>.
     *
     * <p>
     * If the {@link #redirectErrorStream() redirectErrorStream} attribute has been set {@code true}, then the
     * redirection set by this method has no effect.
     *
     * @param destination
     *            the new standard error destination
     * @return this process builder
     * @throws IllegalArgumentException
     *             if the redirect does not correspond to a valid destination of data, that is, has type
     *             {@link Redirect.Type#READ READ}
     * @since 1.7
     */
    public BazelProcessBuilder redirectError(Redirect destination) {
        innerProcessBuilder.redirectError(destination);
        return this;
    }

    /**
     * Sets this process builder's standard output destination.
     *
     * Subprocesses subsequently started by this object's {@link #start()} method send their standard output to this
     * destination.
     *
     * <p>
     * If the destination is {@link Redirect#PIPE Redirect.PIPE} (the initial value), then the standard output of a
     * subprocess can be read using the input stream returned by {@link Process#getInputStream()}. If the destination is
     * set to any other value, then {@link Process#getInputStream()} will return a <a href="#redirect-output">null input
     * stream</a>.
     *
     * @param destination
     *            the new standard output destination
     * @return this process builder
     * @throws IllegalArgumentException
     *             if the redirect does not correspond to a valid destination of data, that is, has type
     *             {@link Redirect.Type#READ READ}
     * @since 1.7
     */
    public BazelProcessBuilder redirectOutput(Redirect destination) {
        innerProcessBuilder.redirectOutput(destination);
        return this;
    }

    /**
     * Starts a new process using the attributes of this process builder.
     *
     * <p>
     * The new process will invoke the command and arguments given by {@link #command()}, in a working directory as
     * given by {@link #directory()}, with a process environment as given by {@link #environment()}.
     *
     * <p>
     * This method checks that the command is a valid operating system command. Which commands are valid is
     * system-dependent, but at the very least the command must be a non-empty list of non-null strings.
     *
     * <p>
     * A minimal set of system dependent environment variables may be required to start a process on some operating
     * systems. As a result, the subprocess may inherit additional environment variable settings beyond those in the
     * process builder's {@link #environment()}.
     *
     * <p>
     * If there is a security manager, its {@link SecurityManager#checkExec checkExec} method is called with the first
     * component of this object's {@code command} array as its argument. This may result in a {@link SecurityException}
     * being thrown.
     *
     * <p>
     * Starting an operating system process is highly system-dependent. Among the many things that can go wrong are:
     * <ul>
     * <li>The operating system program file was not found.
     * <li>Access to the program file was denied.
     * <li>The working directory does not exist.
     * <li>Invalid character in command argument, such as NUL.
     * </ul>
     *
     * <p>
     * In such cases an exception will be thrown. The exact nature of the exception is system-dependent, but it will
     * always be a subclass of {@link IOException}.
     *
     * <p>
     * If the operating system does not support the creation of processes, an {@link UnsupportedOperationException} will
     * be thrown.
     *
     * <p>
     * Subsequent modifications to this process builder will not affect the returned {@link Process}.
     *
     * @return a new {@link Process} object for managing the subprocess
     *
     * @throws NullPointerException
     *             if an element of the command list is null
     *
     * @throws IndexOutOfBoundsException
     *             if the command is an empty list (has size {@code 0})
     *
     * @throws SecurityException
     *             if a security manager exists and
     *             <ul>
     *
     *             <li>its {@link SecurityManager#checkExec checkExec} method doesn't allow creation of the subprocess,
     *             or
     *
     *             <li>the standard input to the subprocess was {@linkplain #redirectInput redirected from a file} and
     *             the security manager's {@link SecurityManager#checkRead(String) checkRead} method denies read access
     *             to the file, or
     *
     *             <li>the standard output or standard error of the subprocess was {@linkplain #redirectOutput
     *             redirected to a file} and the security manager's {@link SecurityManager#checkWrite(String)
     *             checkWrite} method denies write access to the file
     *
     *             </ul>
     *
     * @throws UnsupportedOperationException
     *             If the operating system does not support the creation of processes.
     *
     * @throws IOException
     *             if an I/O error occurs
     *
     * @see Runtime#exec(String[], String[], java.io.File)
     */
    public Process start() throws IOException {
        return innerProcessBuilder.start();
    }

}
