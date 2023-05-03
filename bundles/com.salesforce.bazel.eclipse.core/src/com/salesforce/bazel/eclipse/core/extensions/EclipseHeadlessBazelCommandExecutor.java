package com.salesforce.bazel.eclipse.core.extensions;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.PLUGIN_ID;
import static java.lang.String.format;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.readString;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.command.BazelBinary;
import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor;

/**
 * Headless version initializing the Bazel binary from Eclipse preferences.
 */
public class EclipseHeadlessBazelCommandExecutor extends DefaultBazelCommandExecutor {

    /**
     * Calls <code>bazel --version</code> on a provided binary to identify the version use.
     * <p>
     * Calls {@link EclipseHeadlessBazelCommandExecutor#setBazelBinary(BazelBinary)} when done.
     * </p>
     */
    protected final class DetectBazelVersionAndSetBinaryJob extends Job {
        private final Path binary;

        private DetectBazelVersionAndSetBinaryJob(Path binary) {
            super(format("Detecting Bazel version...", binary));
            this.binary = binary;
            setSystem(true);
            setPriority(SHORT);
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            try {
                List<String> commandLine = List.of(binary.toString(), "--version");
                if (isWrapExecutionIntoShell()) {
                    commandLine = wrapExecutionIntoShell(commandLine);
                }
                var pb = new ProcessBuilder(commandLine);
                var stdoutFile = File.createTempFile("bazel_version_", ".txt");
                pb.redirectOutput(stdoutFile);
                var stderrFile = File.createTempFile("bazel_version_", ".err.txt");
                pb.redirectError(stderrFile);
                var result = pb.start().waitFor();
                if (result != 0) {
                    var out = readString(stderrFile.toPath(), Charset.defaultCharset());
                    LOG.debug("Error executing '{}'. Process exited with code {}: {}",
                        commandLine.stream().collect(joining(" ")), result, out);
                    setBazelBinary(UNKNOWN_BAZEL_BINARY);
                    return Status.error(format("Unable to detect Bazel version of '%s'! %n%s", binary, out));
                }
                var lines = readAllLines(stdoutFile.toPath(), Charset.defaultCharset());
                for (String potentialVersion : lines) {
                    if (potentialVersion.startsWith(BAZEL_VERSION_PREFIX)) {
                        setBazelBinary(new BazelBinary(binary,
                                BazelVersion.parseVersion(potentialVersion.substring(BAZEL_VERSION_PREFIX.length()))));
                        return Status.OK_STATUS;
                    }
                }
                setBazelBinary(UNKNOWN_BAZEL_BINARY);
                return Status.OK_STATUS;
            } catch (IOException e) {
                setBazelBinary(UNKNOWN_BAZEL_BINARY);
                return Status.error(format("Unable to detect Bazel version of '%s'!", binary), e);
            } catch (InterruptedException e) {
                LOG.warn("Interrupted waiting for bazel --version to respond for binary '{}'", binary, e);
                setBazelBinary(UNKNOWN_BAZEL_BINARY);
                return Status.CANCEL_STATUS;
            }
        }
    }

    static final String BAZEL_VERSION_PREFIX = "bazel ";
    static BazelBinary UNKNOWN_BAZEL_BINARY = new BazelBinary(Path.of("bazel"), new BazelVersion(999, 999, 999));
    static Logger LOG = LoggerFactory.getLogger(EclipseHeadlessBazelCommandExecutor.class);

    public static final String PREF_KEY_BAZEL_BINARY = "bazelBinary";

    private final IPreferenceChangeListener preferencesListener = event -> {
        if (PREF_KEY_BAZEL_BINARY.equalsIgnoreCase(event.getKey())) {
            newInitJobFromPreferences();
        }
    };

    private final IEclipsePreferences[] preferencesLookup = { getInstanceScopeNode(), getDefaultScopeNode() };

    /**
     * Creates a new command executor initializing the Bazel binary from the preferences and spawning a background job
     * to detect its version.
     */
    public EclipseHeadlessBazelCommandExecutor() {
        initializeBazelBinary();
    }

    IEclipsePreferences getDefaultScopeNode() {
        return DefaultScope.INSTANCE.getNode(PLUGIN_ID);
    }

    IEclipsePreferences getInstanceScopeNode() {
        return InstanceScope.INSTANCE.getNode(PLUGIN_ID);
    }

    @Override
    protected OutputStream getProcessErrorStream() {
        // assuming this is displayed in the Eclipse Console, using System.out avoids the red coloring
        return System.out;
    }

    /**
     * Called by {@link EclipseHeadlessBazelCommandExecutor#EclipseHeadlessBazelCommandExecutor() default constructor}
     * to initializing the Bazel binary from the preferences.
     * <p>
     * First the binary is set to {@link #UNKNOWN_BAZEL_BINARY}. Then the preferences are checked. If a setting is
     * available the value will be submitted to {@link #newInitJobFromPreferences()} for detecting its version.
     * </p>
     * <p>
     * Additionally, a preference listener will be installed which will ensure the version is detected again when the
     * setting changes.
     * </p>
     * <p>
     * Subclasses may override to provide a custom initialization logic. This is not a standard use-case, though. It's
     * intended for tests only.
     * </p>
     */
    protected void initializeBazelBinary() {
        // start with a simple default
        setBazelBinary(UNKNOWN_BAZEL_BINARY);
        // add listener to instance scope
        // note: we never unregister because we do expect BazelCorePlugin lifetime to match that of the IDE process
        preferencesLookup[0].addPreferenceChangeListener(preferencesListener);
        // schedule initial initialization sync
        var job = newInitJobFromPreferences();
        job.schedule();
        try {
            job.join();
        } catch (InterruptedException e) {
            throw new OperationCanceledException("Interrupted waiting for Bazel binary initialization to happen");
        }
    }

    /**
     * Called by {@link #initializeBazelBinary()} and by the {@link #preferencesListener}. *
     * <p>
     * Subclasses may override to provide a custom Job. This is not a standard use-case, though. It's intended for tests
     * only.
     * </p>
     *
     * @return an Eclipse {@link Job} for initializing the Bazel version.
     */
    protected Job newInitJobFromPreferences() {
        var binary = Path.of(Platform.getPreferencesService().get(PREF_KEY_BAZEL_BINARY, "bazel", preferencesLookup));
        return new DetectBazelVersionAndSetBinaryJob(binary);
    }

    @Override
    public void setBazelBinary(BazelBinary bazelBinary) {
        LOG.info("Using default Bazel binary '{}' (version '{}')", bazelBinary.executable(),
            bazelBinary.bazelVersion());
        super.setBazelBinary(bazelBinary);
    }
}
