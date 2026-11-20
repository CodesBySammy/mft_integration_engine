package com.mft.integration.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mft.integration.checksum.ChecksumAlgorithm;
import com.mft.integration.checksum.ChecksumService;
import com.mft.integration.exception.ValidationException;
import com.mft.integration.layout.FixedWidthLayoutParser;
import com.mft.integration.layout.LayoutDefinition;
import com.mft.integration.layout.LayoutRegistry;
import com.mft.integration.workspace.LocalWorkspaceService;
import com.mft.integration.workspace.WorkspaceDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * Enterprise transformation processor:
 * 1. Parses fixed-width records.
 * 2. Validates domain rules (ISO-4217, numeric amount, yyyyMMdd date).
 * 3. Formats delimited output with Header, Detail, and Trailer records.
 * 4. Emits companion SHA-256 and JSON manifest.
 */
@Service
public class TransformationProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransformationProcessor.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LayoutRegistry layoutRegistry;
    private final FixedWidthLayoutParser layoutParser;
    private final ChecksumService checksumService;
    private final LocalWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public TransformationProcessor(LayoutRegistry layoutRegistry,
                                   FixedWidthLayoutParser layoutParser,
                                   ChecksumService checksumService,
                                   LocalWorkspaceService workspaceService) {
        this.layoutRegistry = layoutRegistry;
        this.layoutParser = layoutParser;
        this.checksumService = checksumService;
        this.workspaceService = workspaceService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public TransformationResult transform(Path inputFile, String routeId, String runId) {
        log.info("Transforming file [{}] for route [{}] run [{}]", inputFile.getFileName(), routeId, runId);

        LayoutDefinition layout = layoutRegistry.getLayout(LayoutRegistry.POSITION_LAYOUT_ID);
        if (layout == null) {
            throw new ValidationException("LAYOUT_NOT_FOUND", "Position fixed-width layout not found in registry.");
        }

        List<PositionRecord> validRecords = new ArrayList<>();
        List<RecordValidationError> errors = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        LocalDate businessDate = null;

        int lineNum = 0;
        try (BufferedReader reader = Files.newBufferedReader(inputFile, layout.getCharset())) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                byte[] lineBytes = line.getBytes(layout.getCharset());
                try {
                    Map<String, String> fields = layoutParser.parseRecord(lineBytes, layout);
                    PositionRecord record = validateAndMap(fields, lineNum);
                    validRecords.add(record);
                    totalAmount = totalAmount.add(record.amount());
                    if (businessDate == null) {
                        businessDate = record.businessDate();
                    }
                } catch (ValidationException ve) {
                    errors.add(new RecordValidationError(lineNum, "record", ve.getErrorCode(), ve.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new ValidationException("FILE_READ_ERROR", "Failed to read input file: " + inputFile, e);
        }

        if (!errors.isEmpty()) {
            log.error("File [{}] failed validation with {} error(s): {}", inputFile.getFileName(), errors.size(), errors);
            throw new ValidationException("RECORD_VALIDATION_FAILED",
                    String.format("File [%s] rejected: %d record validation error(s). First error: %s",
                            inputFile.getFileName(), errors.size(), errors.get(0)));
        }

        if (businessDate == null) {
            businessDate = LocalDate.now();
        }

        // Generate output filename: SL_RECON_<yyyyMMdd>_<runId>.txt
        String dateStr = businessDate.format(DATE_FORMAT);
        String outputFilename = String.format("SL_RECON_%s_%s.txt", dateStr, runId);
        Path outputFile = workspaceService.resolvePath(WorkspaceDirectory.OUTPUT, outputFilename);

        // Generate Delimited Output (Header, Details, Trailer)
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            // Header: HDR|<businessDate>|<routeId>|<runId>
            writer.write(String.format("HDR|%s|%s|%s%n", dateStr, routeId, runId));

            // Details: REC_ID|TRADE_ID|CURRENCY|AMOUNT|BUSINESS_DATE|STATUS
            int recordSeq = 1;
            for (PositionRecord rec : validRecords) {
                String recId = String.format("REC%09d", recordSeq++);
                String line = String.format("%s|%s|%s|%s|%s|PROCESSED%n",
                        recId,
                        rec.tradeId(),
                        rec.currency(),
                        rec.amount().setScale(2, RoundingMode.HALF_UP),
                        rec.businessDate().format(DATE_FORMAT));
                writer.write(line);
            }

            // Trailer: TRL|<recordCount>|<totalAmount>
            writer.write(String.format("TRL|%d|%s%n",
                    validRecords.size(),
                    totalAmount.setScale(2, RoundingMode.HALF_UP)));
        } catch (IOException e) {
            throw new ValidationException("OUTPUT_WRITE_ERROR", "Failed to write transformed output file: " + outputFile, e);
        }

        // Calculate Checksum & Generate Companion
        String outputChecksum = checksumService.calculateChecksum(outputFile, ChecksumAlgorithm.SHA_256);
        Path companionFile = checksumService.writeCompanionChecksumFile(outputFile, outputChecksum, ChecksumAlgorithm.SHA_256);

        // Generate JSON Manifest
        Path manifestFile = generateManifest(inputFile, outputFile, outputChecksum, routeId, runId, validRecords.size());

        log.info("Transformation successful: [{}] -> [{}] ({} records, total: {})",
                inputFile.getFileName(), outputFilename, validRecords.size(), totalAmount);

        return new TransformationResult(outputFile, outputChecksum, companionFile, manifestFile, validRecords.size(), totalAmount);
    }

    private PositionRecord validateAndMap(Map<String, String> fields, int lineNum) {
        String tradeId = fields.get("tradeId");
        if (tradeId == null || tradeId.isBlank()) {
            throw new ValidationException("MISSING_TRADE_ID", "tradeId is required on line " + lineNum);
        }

        String currencyStr = fields.get("currency");
        if (currencyStr == null || currencyStr.isBlank()) {
            throw new ValidationException("MISSING_CURRENCY", "currency is required on line " + lineNum);
        }
        try {
            Currency.getInstance(currencyStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("INVALID_CURRENCY", "Invalid ISO 4217 currency code [" + currencyStr + "] on line " + lineNum);
        }

        String amountStr = fields.get("amount");
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr.trim());
        } catch (Exception e) {
            throw new ValidationException("INVALID_AMOUNT", "Non-numeric amount [" + amountStr + "] on line " + lineNum);
        }

        String dateStr = fields.get("businessDate");
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new ValidationException("INVALID_DATE", "Invalid businessDate [" + dateStr + "] on line " + lineNum + ", expected yyyyMMdd");
        }

        return new PositionRecord(tradeId.trim(), currencyStr.trim().toUpperCase(), amount, date);
    }

    private Path generateManifest(Path inputFile, Path outputFile, String outputChecksum,
                                 String routeId, String runId, int recordCount) {
        String manifestFilename = outputFile.getFileName().toString() + ".manifest.json";
        Path manifestPath = outputFile.resolveSibling(manifestFilename);

        String sourceChecksum = checksumService.calculateChecksum(inputFile, ChecksumAlgorithm.SHA_256);

        ManifestPayload manifest = new ManifestPayload(
                "MFT-RECON-INT",
                "1.0",
                routeId,
                runId,
                inputFile.getFileName().toString(),
                sourceChecksum,
                outputFile.getFileName().toString(),
                outputChecksum,
                recordCount,
                Instant.now()
        );

        try {
            objectMapper.writeValue(manifestPath.toFile(), manifest);
            log.info("Generated JSON transfer manifest: [{}]", manifestPath);
            return manifestPath;
        } catch (IOException e) {
            throw new ValidationException("MANIFEST_ERROR", "Failed to serialize transfer manifest: " + manifestPath, e);
        }
    }

    public record TransformationResult(
            Path outputFile,
            String outputChecksum,
            Path companionFile,
            Path manifestFile,
            long recordCount,
            BigDecimal totalAmount
    ) {}
}
