package com.salesforce.bazel.eclipse.core.model;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.core.runtime.CoreException;

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule;

/**
 * A structure for working with rule attributes.
 * <p>
 * Primarily exists to avoid making <code>com.google.*</code> part of the Bazel model API.
 * </p>
 */
public class BazelRuleAttributes {

    private final Rule rule;
    private final Map<String, Attribute> attributesByAttributeName;

    public BazelRuleAttributes(Rule rule) {
        this.rule = rule;
        // this might fail if there are multiple attributes of the same name
        // TODO: confirm with Bazel what the behavior/expectation should be
        attributesByAttributeName =
                rule.getAttributeList().stream().collect(toMap(Attribute::getName, Function.identity()));
    }

    Rule getRule() {
        return rule;
    }

    public String getString(String name) throws CoreException {
        var attribute = attributesByAttributeName.get(name);
        if (attribute == null) {
            return null;
        }

        return switch (attribute.getType()) {
            case LABEL, STRING -> attribute.getStringValue();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.getType());
        };
    }

    public List<String> getStringList(String name) throws CoreException {
        var attribute = attributesByAttributeName.get(name);
        if (attribute == null) {
            return null;
        }

        return switch (attribute.getType()) {
            case LABEL_LIST, STRING_LIST -> attribute.getStringListValueList();
            default -> throw new IllegalArgumentException("Unexpected value: " + attribute.getType());
        };
    }
}
