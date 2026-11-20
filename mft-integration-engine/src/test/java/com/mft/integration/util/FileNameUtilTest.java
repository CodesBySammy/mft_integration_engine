package com.mft.integration.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FileNameUtilTest {

    @ParameterizedTest
    @CsvSource({
            "positions.txt, *.txt, true",
            "positions.dat, *.txt, false",
            "TRADE_20261120.dat, TRADE_*.dat, true",
            "RECON_20261120.dat, TRADE_*.dat, false",
            "report.csv, *, true",
            "anyfile.txt, '', true"
    })
    @DisplayName("Matches filename patterns accurately")
    void testPatternMatching(String filename, String pattern, boolean expected) {
        assertThat(FileNameUtil.matchesPattern(filename, pattern)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Identifies temporary and in-flight files")
    void testTemporaryFileDetection() {
        assertThat(FileNameUtil.isTemporaryFile("file.txt.part")).isTrue();
        assertThat(FileNameUtil.isTemporaryFile("file.txt.tmp")).isTrue();
        assertThat(FileNameUtil.isTemporaryFile(".hidden_in_flight")).isTrue();
        assertThat(FileNameUtil.isTemporaryFile("completed_file.txt")).isFalse();
    }
}
