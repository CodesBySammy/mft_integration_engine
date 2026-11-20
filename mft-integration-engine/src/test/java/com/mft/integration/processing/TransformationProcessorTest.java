package com.mft.integration.processing;

import com.mft.integration.checksum.ChecksumService;
import com.mft.integration.exception.ValidationException;
import com.mft.integration.layout.FixedWidthLayoutParser;
import com.mft.integration.layout.LayoutRegistry;
import com.mft.integration.workspace.LocalWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformationProcessorTest {

    private TransformationProcessor processor;
    private LocalWorkspaceService workspaceService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        FixedWidthLayoutParser parser = new FixedWidthLayoutParser();
        LayoutRegistry registry = new LayoutRegistry(parser);
        registry.initialize();

        ChecksumService checksumService = new ChecksumService();
        workspaceService = new LocalWorkspaceService(tempDir.toString());
        workspaceService.initialize();

        processor = new TransformationProcessor(registry, parser, checksumService, workspaceService);
    }

    @Test
    @DisplayName("Transforms valid fixed-width input into Header, Detail, Trailer with companion and manifest")
    void testValidTransformation(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("sample_trades.dat");
        String lines = "TRD000000001USD000000000001500.5020261120\n" +
                       "TRD000000002EUR000000000002800.7520261120\n";
        Files.writeString(input, lines);

        TransformationProcessor.TransformationResult result = processor.transform(input, "R_TRANSFORM", "RUN001");

        assertThat(result.recordCount()).isEqualTo(2);
        assertThat(result.totalAmount()).isEqualTo(new BigDecimal("4301.25"));

        // Verify output payload content
        List<String> outputLines = Files.readAllLines(result.outputFile());
        assertThat(outputLines).hasSize(4); // Header + 2 details + Trailer
        assertThat(outputLines.get(0)).isEqualTo("HDR|20261120|R_TRANSFORM|RUN001");
        assertThat(outputLines.get(1)).isEqualTo("REC000000001|TRD000000001|USD|1500.50|20261120|PROCESSED");
        assertThat(outputLines.get(2)).isEqualTo("REC000000002|TRD000000002|EUR|2800.75|20261120|PROCESSED");
        assertThat(outputLines.get(3)).isEqualTo("TRL|2|4301.25");

        // Verify companion and manifest existence
        assertThat(Files.exists(result.companionFile())).isTrue();
        assertThat(Files.exists(result.manifestFile())).isTrue();
        assertThat(Files.readString(result.manifestFile())).contains("\"recordCount\" : 2");
    }

    @Test
    @DisplayName("Rejects file with invalid ISO currency code")
    void testInvalidCurrencyRejected(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("bad_currency.dat");
        String lines = "TRD000000001XYZ000000000001500.5020261120\n";
        Files.writeString(input, lines);

        assertThatThrownBy(() -> processor.transform(input, "R_TRANSFORM", "RUN002"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid ISO 4217 currency code");
    }
}
