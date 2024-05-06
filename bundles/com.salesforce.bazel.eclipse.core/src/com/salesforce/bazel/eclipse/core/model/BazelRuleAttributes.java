package com.salesforce.bazel.eclipse.core.model;

import java.util.List;
import java.util.function.BinaryOperator;

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule;
import com.salesforce.bazel.sdk.model.RuleInternal;

/**
 * A structure for working with rule attributes.
 * <p>
 * Primarily exists to avoid making <code>com.google.*</code> part of the Bazel model API.
 * </p>
 */
public class BazelRuleAttributes {

    /**
     * Workaround for https://github.com/bazelbuild/bazel/issues/20918
     */
    private static BinaryOperator<Attribute> firstOneWinsBazelDuplicateWorkaround = (first, second) -> first;
    private final RuleInternal rule;

    BazelRuleAttributes(RuleInternal rule) {
        this.rule = rule;

    }

    public Boolean getBoolean(String name) {
        var attributes = rule.getAttributes(name);
        if ((attributes == null) || attributes.isEmpty()) {
            return null;
        }

        var attribute = attributes.get(0);

        return switch (attribute.type()) {
            case BOOLEAN -> attribute.attributeBoolean();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.type());
        };
    }

    /**
     * @return value of the attribute name if present, otherwise {@link Rule#getName()};
     */
    public String getName() {
        var name = getString("name");
        if (name != null) {
            return name;
        }

        return rule.name();
    }

    RuleInternal getRule() {
        return rule;
    }

    public String getRuleClass() {
        return rule.ruleClass();
    }

    public String getString(String name) {
        var attributes = rule.getAttributes(name);
        if ((attributes == null) || attributes.isEmpty()) {
            return null;
        }
        var attribute = attributes.get(0);

        return switch (attribute.type()) {
            case LABEL, STRING -> attribute.attribueString();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.type());
        };
    }

    public List<String> getStringList(String name) {
        var attributes = rule.getAttributes(name);
        if ((attributes == null) || attributes.isEmpty()) {
            return null;
        }
        var attribute = attributes.get(0);

        return switch (attribute.type()) {
            case LABEL_LIST, STRING_LIST -> attribute.attributeStringList();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.type());
        };
    }

    /**
     * Returns <code>true</code> if there is an attribute named <code>tags</code> containing the specified tag.
     *
     * @param tag
     *            the tag to check
     * @return <code>true</code> if the attribute <code>tags</code> is present and has the specified tag,
     *         <code>false</code> otherwise
     */
    public boolean hasTag(String tag) {
        var tags = getStringList("tags");
        return (tags != null) && tags.contains(tag);
    }

    @Override
    public String toString() {
        return "BazelRuleAttributes [rule=" + rule + "]";
    }
}
