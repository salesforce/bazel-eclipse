/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.google.idea.blaze.base.model.primitives;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;

/**
 * A set of recognized blaze rule names, together with {@link LanguageClass} and {@link RuleType}. Language-specific
 * extensions can provide their own set of rule names, as well as heuristics to recognize rule names at runtime.
 *
 * <p>
 * Each rule name maps to at most one Kind.
 */
public class Kind {

    /**
     * We cache target kinds provided by the extension points above. This state is associated with an application, so
     * for example needs to be reset between unit test runs with manual extension point setup.
     */
    @VisibleForTesting
    public static final class ApplicationState {
        private static ApplicationState instance;

        private static ApplicationState getService() {
            return requireNonNull(instance, "not initialized ... did you call BazelJavaSDKInit?");
        }

        /**
         * Don't call this method directly. It should only be called by {@code com.salesforce.bazel.sdk.init.BazelJavaSDKInit}.
         *
         * @param applicationState
         */
        public static void setInstance(ApplicationState applicationState) {
            instance = applicationState;
        }

        /** An internal map of all known rule types. */
        private final Map<String, Kind> stringToKind = Collections.synchronizedMap(new HashMap<>());

        /** An internal map of all known rule types. */
        private final Multimap<LanguageClass, Kind> perLanguageKinds =
                Multimaps.synchronizedSetMultimap(HashMultimap.create());

        private final ImmutableList<Provider> extensions;

        public ApplicationState(ImmutableList<Provider> extensions) {
            this.extensions = extensions;

            // initialize the global state
            getExtensions().stream().map(Provider::getTargetKinds).flatMap(Collection::stream)
                    .forEach(this::cacheIfNecessary);
        }

        /** Add the Kind instance to the global map, or returns an existing kind with this rule name. */
        private Kind cacheIfNecessary(Kind kind) {
            var existing = stringToKind.putIfAbsent(kind.getKindString(), kind);
            if (existing != null) {
                return existing;
            }
            kind.getLanguageClasses().forEach(languageClass -> perLanguageKinds.put(languageClass, kind));
            return kind;
        }

        private ImmutableList<Provider> getExtensions() {
            return extensions;
        }
    }

    /**
     * Provides a set of recognized blaze rule names. Individual language-specific sub-plugins can use this EP to
     * register rule types relevant to that language.
     *
     * <p>
     * Each rule name must map to at most one Kind, across all such providers.
     */
    public interface Provider {

        List<Provider> extensions = null;

        static Kind create(String ruleName, Collection<LanguageClass> languageClasses, RuleType ruleType) {
            return new Kind(ruleName, ImmutableSet.copyOf(languageClasses), ruleType);
        }

        static Kind create(String ruleName, LanguageClass languageClass, RuleType ruleType) {
            return create(ruleName, ImmutableSet.of(languageClass), ruleType);
        }

        static List<Provider> getExtension() {
            return extensions;
        }

        /**
         * A heuristic to identify additional target kinds at runtime which aren't known up-front. For example, any rule
         * name starting with 'kt_jvm_' might be parsed as a kotlin rule of unknown {@link RuleType}.
         */
        default Function<IntellijIdeInfo.TargetIdeInfo, Kind> getTargetKindHeuristics() {
            return t -> null;
        }

        /** A set of rule names known at compile time. */
        ImmutableSet<Kind> getTargetKinds();
    }

    @Nullable
    public static Kind fromProto(IntellijIdeInfo.TargetIdeInfo proto) {
        var existing = fromRuleName(proto.getKindString());
        if (existing != null) {
            return existing;
        }
        // check provided heuristics
        var derived = ApplicationState.getService().getExtensions().stream()
                .map(p -> p.getTargetKindHeuristics().apply(proto)).filter(Objects::nonNull).findFirst().orElse(null);
        if (derived != null) {
            derived = ApplicationState.getService().cacheIfNecessary(derived);
        }
        return derived;
    }

    @Nullable
    public static Kind fromRuleName(String ruleName) {
        return ApplicationState.getService().stringToKind.get(ruleName);
    }

    public static ImmutableSet<Kind> getKindsForLanguage(LanguageClass language) {
        return ImmutableSet.copyOf(getPerLanguageKinds().get(language));
    }

    /**
     * Returns a per-language map of rule kinds handled by an available {@link Provider}.
     *
     * <p>
     * Don't rely on this map being complete -- some rule names are recognized at runtime using heuristics.
     */
    public static ImmutableMultimap<LanguageClass, Kind> getPerLanguageKinds() {
        var state = ApplicationState.getService();
        synchronized (state.perLanguageKinds) {
            return ImmutableMultimap.copyOf(state.perLanguageKinds);
        }
    }

    /** If rule type isn't recognized, uses a heuristic to guess the rule type. */
    public static RuleType guessRuleType(String ruleName) {
        var kind = fromRuleName(ruleName);
        if (kind != null) {
            return kind.getRuleType();
        }
        if (isTestSuite(ruleName) || ruleName.endsWith("_test")) {
            return RuleType.TEST;
        }
        if (ruleName.endsWith("_binary")) {
            return RuleType.BINARY;
        }
        if (ruleName.endsWith("_library")) {
            return RuleType.LIBRARY;
        }
        return RuleType.UNKNOWN;
    }

    private static boolean isTestSuite(String ruleName) {
        // handle plain test_suite targets and macros producing a test/test_suite
        return "test_suite".equals(ruleName) || ruleName.endsWith("test_suites");
    }

    private final String kindString;

    private final ImmutableSet<LanguageClass> languageClasses;

    private final RuleType ruleType;

    public Kind(String kindString, ImmutableSet<LanguageClass> languageClasses, RuleType ruleType) {
        this.kindString = kindString;
        this.languageClasses = languageClasses;
        this.ruleType = ruleType;
    }

    public String getKindString() {
        return kindString;
    }

    public ImmutableSet<LanguageClass> getLanguageClasses() {
        return languageClasses;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public boolean hasAnyLanguageIn(LanguageClass... languageClass) {
        return Arrays.stream(languageClass).anyMatch(getLanguageClasses()::contains);
    }

    public boolean hasLanguage(LanguageClass languageClass) {
        return getLanguageClasses().contains(languageClass);
    }

    public boolean isOneOf(Kind... kinds) {
        return Arrays.asList(kinds).contains(this);
    }

    public boolean isWebTest() {
        return (getRuleType() == RuleType.TEST) && getKindString().endsWith("web_test");
    }

    @Override
    public final String toString() {
        return getKindString();
    }
}
