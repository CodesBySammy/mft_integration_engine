package com.mft.integration.layout;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Layout definition describing record fields, charset encoding, and record length.
 */
public class LayoutDefinition {

    private final String layoutId;
    private final int recordLength;
    private final Charset charset;
    private final List<FieldDefinition> fields;

    public LayoutDefinition(String layoutId, int recordLength, Charset charset, List<FieldDefinition> fields) {
        this.layoutId = layoutId;
        this.recordLength = recordLength;
        this.charset = charset != null ? charset : StandardCharsets.US_ASCII;
        this.fields = fields != null ? List.copyOf(fields) : List.of();
    }

    public String getLayoutId() {
        return layoutId;
    }

    public int getRecordLength() {
        return recordLength;
    }

    public Charset getCharset() {
        return charset;
    }

    public List<FieldDefinition> getFields() {
        return fields;
    }
}
