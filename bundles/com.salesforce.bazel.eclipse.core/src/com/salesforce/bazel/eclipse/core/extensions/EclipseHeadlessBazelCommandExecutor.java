package com.salesforce.bazel.eclipse.core.extensions;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.PLUGIN_ID;
import static com.salesforce.bazel.eclipse.preferences.BazelCorePreferenceKeys.PREF_KEY_BAZEL_BINARY;
import static com.salesforce.bazel.eclipse.preferences.BazelCorePreferenceKeys.PREF_KEY_USE_SHELL_ENVIRONMENT;
import static java.lang.String.format;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.preferences.BazelCorePreferenceKeys;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.command.BazelBinary;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor;
import com.salesforce.bazel.sdk.command.ProcessStreamsProvider;

/**
 * Headless version initializing the Bazel binary from Eclipse preferences.
 */
public class EclipseHeadlessBazelCommandExecutor extends DefaultBazelCommandExecutor {

    static BazelBinary UNKNOWN_BAZEL_BINARY = new BazelBinary(Path.of("bazel"), new BazelVersion(999, 999, 999));
    static Logger LOG = LoggerFactory.getLogger(EclipseHeadlessBazelCommandExecutor.class);

    private static ProcessStreamsProvider SYSOUT_PROVIDER = new ProcessStreamsProvider() {
        @Override
        public OutputStream getErrorStream() {
            // assuming this is displayed in the Eclipse Console, using System.out avoids the red coloring
            return System.out;
        };
    };

    private final IPreferenceChangeListener preferencesListener = event -> {
        if (PREF_KEY_BAZEL_BINARY.equalsIgnoreCase(event.getKey())) {
            scheduleInitBazelBinaryFromPreferencesJob();
        } else if (PREF_KEY_USE_SHELL_ENVIRONMENT.equalsIgnoreCase(event.getKey())) {
            initializeWrapExecutionIntoShellSettingFromPreferences();
        }
    };

    private final IEclipsePreferences[] preferencesLookup = { getInstanceScopeNode(), getDefaultScopeNode() };

    /**
     * Creates a new command executor initializing the Bazel binary from the preferences and spawning a background job
     * to detect its version.
     */
    public EclipseHeadlessBazelCommandExecutor() {
        initializeBazelBinary();
        initializeWrapExecutionIntoShellSettingFromPreferences();
    }

    IEclipsePreferences getDefaultScopeNode() {
        return DefaultScope.INSTANCE.getNode(PLUGIN_ID);
    }

    IEclipsePreferences getInstanceScopeNode() {
        return InstanceScope.INSTANCE.getNode(PLUGIN_ID);
    }

    @Override
    protected String getToolTagArgument() {
        var toolTagArgument = this.cachedToolTagArgument;
        if (toolTagArgument != null) {
            return toolTagArgument;
        }
        return this.cachedToolTagArgument = format("--tool_tag=eclipse:%s", BazelCorePlugin.getBundleVersion());
    }

    /**
     * Called by {@link EclipseHeadlessBazelCommandExecutor#EclipseHeadlessBazelCommandExecutor() default constructor}
     * to initializing the Bazel binary from the preferences.
     * <p>
     * First the binary is set to {@link #UNKNOWN_BAZEL_BINARY}. Then the preferences are checked. If a setting is
     * available the value will be submitted to {@link #scheduleInitBazelBinaryFromPreferencesJob()} for detecting its
     * version.
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
        var job = scheduleInitBazelBinaryFromPreferencesJob();
        try {
            // wait for completion
            job.join();
        } catch (InterruptedException e) {
            throw new OperationCanceledException("Interrupted waiting for Bazel binary initialization to happen");
        }
    }

    /**
     * Reads the preference value for {@link BazelCorePreferenceKeys#PREF_KEY_USE_SHELL_ENVIRONMENT} from the
     * preferences and applies it to {@link #setWrapExecutionIntoShell(boolean)}
     */
    protected void initializeWrapExecutionIntoShellSettingFromPreferences() {
        var defaultValue = getSystemUtil().isMac(); // mimic super-class default, which is *true* only on Mac
        var value = Platform.getPreferencesService().get(PREF_KEY_USE_SHELL_ENVIRONMENT, null, preferencesLookup);
        setWrapExecutionIntoShell(value == null ? defaultValue : Boolean.valueOf(value).booleanValue());
    }

    @Override
    protected ProcessStreamsProvider newProcessStreamProvider(BazelCommand<?> command, PreparedCommandLine commandLine)
            throws IOException {
        return SYSOUT_PROVIDER;
    }

    /**
     * Called by {@link #initializeBazelBinary()} and by the {@link #preferencesListener} to (re-)initialize the
     * executor configuration from the preferences.
     * <p>
     * Subclasses may override to provide a custom Job. This is not a standard use-case, though. It's intended for tests
     * only.
     * </p>
     *
     * @return an Eclipse {@link Job} for initializing the Bazel version.
     */
    protected Job scheduleInitBazelBinaryFromPreferencesJob() {
        var binary = Path.of(Platform.getPreferencesService().get(PREF_KEY_BAZEL_BINARY, "bazel", preferencesLookup));
        var job = new DetectBazelVersionAndSetBinaryJob(
                binary,
                isWrapExecutionIntoShell(),
                this::setBazelBinary,
                () -> UNKNOWN_BAZEL_BINARY);
        job.schedule();
        return job;
    }

    @Override
    public void setBazelBinary(BazelBinary bazelBinary) {
        LOG.info(
            "Using default Bazel binary '{}' (version '{}')",
            bazelBinary.executable(),
            bazelBinary.bazelVersion());
        super.setBazelBinary(bazelBinary);
    }
}
