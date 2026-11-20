package com.mft.integration.layout;

import com.mft.integration.exception.FileProcessingException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Delimited parser backed by Apache Commons CSV supporting custom delimiters, quotes, and headers.
 */
@Component
public class DelimitedLayoutParser {

    public List<List<String>> parseDelimited(String content, char delimiter, boolean hasHeader) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        try (Reader reader = new StringReader(content);
             CSVParser parser = format.parse(reader)) {

            List<List<String>> records = new ArrayList<>();
            for (CSVRecord record : parser) {
                List<String> values = new ArrayList<>();
                record.forEach(values::add);
                records.add(values);
            }
            return records;
        } catch (IOException e) {
            throw new FileProcessingException("DELIMITED_PARSE_ERROR", "Failed to parse delimited content", e);
        }
    }
}
