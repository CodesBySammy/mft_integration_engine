package com.mft.integration.layout;

/**
 * Definition of an individual field in a fixed-width or delimited record layout.
 */
public record FieldDefinition(
        String name,
        int startPos,   // 1-based start position
        int length,     // character/byte length
        boolean required
) {
    public int endPos() {
        return startPos + length - 1;
    }
}
