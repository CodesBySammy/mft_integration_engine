package com.mft.integration.layout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DelimitedLayoutParserTest {

    private DelimitedLayoutParser parser;

    @BeforeEach
    void setUp() {
        parser = new DelimitedLayoutParser();
    }

    @Test
    @DisplayName("Parses pipe-delimited records cleanly")
    void testParsePipeDelimited() {
        String csv = "HDR|20261120|001\nREC001|USD|1500.50\nTRL|1|1500.50";
        List<List<String>> rows = parser.parseDelimited(csv, '|', false);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsExactly("HDR", "20261120", "001");
        assertThat(rows.get(1)).containsExactly("REC001", "USD", "1500.50");
        assertThat(rows.get(2)).containsExactly("TRL", "1", "1500.50");
    }
}
