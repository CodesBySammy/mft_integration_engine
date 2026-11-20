package com.mft.integration.layout;

import com.mft.integration.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedWidthLayoutParserTest {

    private FixedWidthLayoutParser parser;
    private LayoutDefinition layout;

    @BeforeEach
    void setUp() {
        parser = new FixedWidthLayoutParser();
        List<FieldDefinition> fields = List.of(
                new FieldDefinition("tradeId", 1, 12, true),
                new FieldDefinition("currency", 13, 3, true),
                new FieldDefinition("amount", 16, 18, true),
                new FieldDefinition("businessDate", 34, 8, true)
        );
        layout = new LayoutDefinition("TEST_LAYOUT", 41, StandardCharsets.US_ASCII, fields);
    }

    @Test
    @DisplayName("Parses valid fixed-width 41-byte line into mapped fields")
    void testParseValidLine() {
        String line = "TRD000000001USD000000000001500.5020261120";
        byte[] bytes = line.getBytes(StandardCharsets.US_ASCII);

        Map<String, String> result = parser.parseRecord(bytes, layout);

        assertThat(result.get("tradeId")).isEqualTo("TRD000000001");
        assertThat(result.get("currency")).isEqualTo("USD");
        assertThat(result.get("amount")).isEqualTo("000000000001500.50");
        assertThat(result.get("businessDate")).isEqualTo("20261120");
    }

    @Test
    @DisplayName("Throws ValidationException when line is shorter than layout record length")
    void testShortLineThrows() {
        String shortLine = "TRD000000001USD";
        byte[] bytes = shortLine.getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> parser.parseRecord(bytes, layout))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Record length mismatch");
    }
}
