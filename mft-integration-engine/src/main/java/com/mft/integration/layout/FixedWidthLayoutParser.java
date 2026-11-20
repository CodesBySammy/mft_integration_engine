package com.mft.integration.layout;

import com.mft.integration.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * High-performance byte-boundary fixed-width parser.
 * Safely extracts field slices using exact byte boundaries rather than assuming character offsets.
 */
@Component
public class FixedWidthLayoutParser {

    /**
     * Parses a single fixed-width record line according to the layout definition.
     *
     * @param recordBytes raw bytes of the line (excluding CR/LF)
     * @param layout layout specification
     * @return Map of field name -> trimmed string value
     */
    public Map<String, String> parseRecord(byte[] recordBytes, LayoutDefinition layout) {
        if (recordBytes == null) {
            throw new ValidationException("EMPTY_RECORD", "Record bytes must not be null.");
        }

        if (recordBytes.length < layout.getRecordLength()) {
            throw new ValidationException("RECORD_LENGTH_MISMATCH", String.format(
                    "Record length mismatch for layout [%s]: expected %d bytes, got %d bytes.",
                    layout.getLayoutId(), layout.getRecordLength(), recordBytes.length));
        }

        Charset charset = layout.getCharset();
        Map<String, String> extracted = new LinkedHashMap<>();

        for (FieldDefinition field : layout.getFields()) {
            int startIndex = field.startPos() - 1; // Convert 1-based pos to 0-based index
            int length = field.length();

            if (startIndex + length > recordBytes.length) {
                throw new ValidationException("FIELD_OVERFLOW", String.format(
                        "Field [%s] bounds [%d..%d] exceed record length [%d]",
                        field.name(), field.startPos(), field.endPos(), recordBytes.length));
            }

            byte[] fieldSlice = Arrays.copyOfRange(recordBytes, startIndex, startIndex + length);
            String value = new String(fieldSlice, charset).trim();

            if (field.required() && value.isEmpty()) {
                throw new ValidationException("REQUIRED_FIELD_EMPTY",
                        "Required field [" + field.name() + "] is empty in layout [" + layout.getLayoutId() + "].");
            }

            extracted.put(field.name(), value);
        }

        return extracted;
    }
}
