package com.salesforce.bazel.eclipse.core.model;

import java.util.List;

import com.salesforce.bazel.sdk.command.querylight.BazelRuleAttribute;
import com.salesforce.bazel.sdk.command.querylight.Rule;

/**
 * A structure for working with rule attributes.
 * <p>
 * Primarily exists to avoid making <code>com.google.*</code> part of the Bazel model API.
 * </p>
 */
public class BazelRuleAttributes {

    private final Rule rule;

    BazelRuleAttributes(Rule rule) {
        this.rule = rule;

    }

    public Boolean getBoolean(BazelRuleAttribute name) {
        var attribute = rule.getAttribute(name.key);
        if (attribute == null) {
            return null;
        }

        return switch (attribute.type()) {
            case BOOLEAN -> attribute.booleanValue();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.type());
        };
    }

    /**
     * @return value of the attribute name if present, otherwise {@link Rule#getName()};
     */
    public String getName() {
        var name = getString(BazelRuleAttribute.NAME);
        if (name != null) {
            return name;
        }

        return rule.name();
    }

    Rule getRule() {
        return rule;
    }

    public String getRuleClass() {
        return rule.ruleClass();
    }

    public String getString(BazelRuleAttribute name) {
        var attribute = rule.getAttribute(name.key);
        if (attribute == null) {
            return null;
        }

        return switch (attribute.type()) {
            case LABEL, STRING -> attribute.stringValue();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.type());
        };
    }

    public List<String> getStringList(BazelRuleAttribute name) {
        var attribute = rule.getAttribute(name.key);
        if (attribute == null) {
            return null;
        }

        return switch (attribute.type()) {
            case LABEL_LIST, STRING_LIST -> attribute.stringListValue();
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
        var tags = getStringList(BazelRuleAttribute.TAGS);
        return (tags != null) && tags.contains(tag);
    }

    @Override
    public String toString() {
        return "BazelRuleAttributes [rule=" + rule + "]";
    }
}
