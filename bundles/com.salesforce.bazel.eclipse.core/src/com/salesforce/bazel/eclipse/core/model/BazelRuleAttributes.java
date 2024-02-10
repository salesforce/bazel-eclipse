package com.salesforce.bazel.eclipse.core.model;

import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule;

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
    private final Rule rule;

    private final Map<String, Attribute> attributesByAttributeName;

    BazelRuleAttributes(Rule rule) {
        this.rule = rule;
        // multiple attributes with the same name are not expected but can happen (https://github.com/bazelbuild/bazel/issues/20918)
        // we therefore use a merge function which discards all duplicates
        try {
            attributesByAttributeName = rule.getAttributeList()
                    .stream()
                    .collect(toMap(Attribute::getName, Function.identity(), firstOneWinsBazelDuplicateWorkaround));
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                    format(
                        "Error loading attributes of rule '%s(%s)'. There were duplicate attributes. Is this allowed?",
                        rule.getRuleClass(),
                        rule.getName()),
                    e);
        }
    }

    public Boolean getBoolean(String name) {
        var attribute = attributesByAttributeName.get(name);
        if (attribute == null) {
            return null;
        }

        return switch (attribute.getType()) {
            case BOOLEAN -> attribute.getBooleanValue();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.getType());
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

        return rule.getName();
    }

    Rule getRule() {
        return rule;
    }

    public String getRuleClass() {
        return rule.getRuleClass();
    }

    public String getString(String name) {
        var attribute = attributesByAttributeName.get(name);
        if (attribute == null) {
            return null;
        }

        return switch (attribute.getType()) {
            case LABEL, STRING -> attribute.getStringValue();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.getType());
        };
    }

    public List<String> getStringList(String name) {
        var attribute = attributesByAttributeName.get(name);
        if (attribute == null) {
            return null;
        }

        return switch (attribute.getType()) {
            case LABEL_LIST, STRING_LIST -> attribute.getStringListValueList();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.getType());
        };
    }

    @Override
    public String toString() {
        return "BazelRuleAttributes [rule=" + rule + "]";
    }
}
