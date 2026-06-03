package uk.gov.hmrc.cars.dslcleaner;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DslCleaner.
 *
 * Test data is modelled on the real BR092 and BR150 rule sets
 * (see src/test/resources/rules/dms).
 *
 * Run with: mvn test
 */
class DslCleanerTest {

    private DslCleaner cleaner;

    // Points to src/test/resources/rules/dms
    private Path testDmsRoot;

    @BeforeEach
    void setUp() {
        cleaner = new DslCleaner();
        testDmsRoot = Paths.get("src/test/resources/rules/dms");
    }

    // =========================================================================
    // 1. DSL Parsing tests
    // =========================================================================

    @Nested
    @DisplayName("DSL Entry Parsing")
    class ParsingTests {

        @Test
        @DisplayName("Single-line entry: key and = on same line as [then]")
        void parseSingleLineEntry() throws IOException {
            Path dslFile = testDmsRoot.resolve("dsl/validationResult-BR092.dsl");
            List<DslCleaner.DslEntry> entries = cleaner.parseDslEntries(dslFile);

            assertFalse(entries.isEmpty(), "Should have parsed at least one entry");

            // First entry from the BR092 screenshot
            DslCleaner.DslEntry first = entries.get(0);
            assertEquals(
                "Emit BR092 validation error for consignment shipment consignor physical address street and number",
                first.key(),
                "First key should match BR092 screenshot entry"
            );
            assertTrue(first.value().contains("CONSIGNOR_PHYSICAL_ADDRESS_STREET_NUMBER"),
                "Value should contain the Java insert expression");
        }

        @Test
        @DisplayName("Two-line entry: key wraps to next line before =")
        void parseTwoLineEntry() throws IOException {
            Path dslFile = testDmsRoot.resolve("dsl/validationResult-BR150.dsl");
            List<DslCleaner.DslEntry> entries = cleaner.parseDslEntries(dslFile);

            // The two-line entry in BR150 has the key split across lines
            DslCleaner.DslEntry twoLine = entries.stream()
                    .filter(e -> e.key().contains("customs office of presentation"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Two-line entry not found"));

            assertEquals(
                "Emit BR150 validation error for declaration header customs office of presentation",
                twoLine.key(),
                "Two-line key should be joined and trimmed correctly"
            );
        }

        @Test
        @DisplayName("BR092 DSL file should contain exactly 11 entries")
        void br092EntryCount() throws IOException {
            Path dslFile = testDmsRoot.resolve("dsl/validationResult-BR092.dsl");
            List<DslCleaner.DslEntry> entries = cleaner.parseDslEntries(dslFile);
            assertEquals(11, entries.size(), "BR092 DSL should have 11 [then] entries");
        }

        @Test
        @DisplayName("BR150 DSL file should contain exactly 4 entries")
        void br150EntryCount() throws IOException {
            Path dslFile = testDmsRoot.resolve("dsl/validationResult-BR150.dsl");
            List<DslCleaner.DslEntry> entries = cleaner.parseDslEntries(dslFile);
            assertEquals(4, entries.size(), "BR150 DSL should have 4 [then] entries");
        }

        @Test
        @DisplayName("All entries should have non-blank keys")
        void allEntriesHaveNonBlankKeys() throws IOException {
            Path dslFile = testDmsRoot.resolve("dsl/validationResult-BR092.dsl");
            List<DslCleaner.DslEntry> entries = cleaner.parseDslEntries(dslFile);
            for (DslCleaner.DslEntry entry : entries) {
                assertFalse(entry.key().isBlank(),
                    "Every parsed entry must have a non-blank key");
            }
        }
    }

    // =========================================================================
    // 2. DSLR matching tests
    // =========================================================================

    @Nested
    @DisplayName("DSLR Key Matching")
    class MatchingTests {

        @Test
        @DisplayName("Key that appears in DSLR should return true")
        void keyFoundInDslr() throws IOException {
            String dslrContent = Files.readString(
                testDmsRoot.resolve("dslr/BR092/BR092_rules.dslr"));

            assertTrue(
                cleaner.isKeyUsedInDslr(
                    "Emit BR092 validation error for consignment shipment consignor physical address street and number",
                    dslrContent),
                "Key present in DSLR should be found"
            );
        }

        @Test
        @DisplayName("Key NOT in DSLR should return false")
        void keyNotFoundInDslr() throws IOException {
            String dslrContent = Files.readString(
                testDmsRoot.resolve("dslr/BR092/BR092_rules.dslr"));

            assertFalse(
                cleaner.isKeyUsedInDslr(
                    "Emit BR092 validation error for consignment shipment consignor physical address region",
                    dslrContent),
                "Key absent from DSLR should not be found"
            );
        }

        @Test
        @DisplayName("Key match should be exact (leading/trailing whitespace ignored)")
        void keyMatchIsTrimmed() throws IOException {
            String dslrContent = Files.readString(
                testDmsRoot.resolve("dslr/BR092/BR092_rules.dslr"));

            // The DSLR has the key indented with spaces — trimming should handle this
            assertTrue(
                cleaner.isKeyUsedInDslr(
                    "Emit BR092 validation error for consignment shipment consignor party name",
                    dslrContent),
                "Key match should ignore leading/trailing whitespace in DSLR lines"
            );
        }

        @Test
        @DisplayName("Partial key match should return false")
        void partialKeyDoesNotMatch() throws IOException {
            String dslrContent = Files.readString(
                testDmsRoot.resolve("dslr/BR092/BR092_rules.dslr"));

            assertFalse(
                cleaner.isKeyUsedInDslr(
                    "Emit BR092 validation error for consignment shipment consignor party",
                    dslrContent),
                "Partial key must not match — whole line must equal the key"
            );
        }
    }

    // =========================================================================
    // 3. Full run tests (non-interactive)
    // =========================================================================

    @Nested
    @DisplayName("Full Analysis Run")
    class FullRunTests {

        @Test
        @DisplayName("BR092: should identify exactly 2 unused entries")
        void br092UnusedEntryCount() throws IOException {
            DslCleaner.CleanerResult result = cleaner.run(testDmsRoot, false);

            DslCleaner.BrRuleSetResult br092 = result.ruleSetResults().stream()
                    .filter(r -> r.brCode().equals("BR092"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("BR092 result not found"));

            assertEquals(2, br092.unusedEntries().size(),
                "BR092 should have exactly 2 unused entries");
        }

        @Test
        @DisplayName("BR092: unused entries should be the region and email entries")
        void br092UnusedEntryKeys() throws IOException {
            DslCleaner.CleanerResult result = cleaner.run(testDmsRoot, false);

            DslCleaner.BrRuleSetResult br092 = result.ruleSetResults().stream()
                    .filter(r -> r.brCode().equals("BR092"))
                    .findFirst()
                    .orElseThrow();

            List<String> unusedKeys = br092.unusedEntries().stream()
                    .map(DslCleaner.DslEntry::key)
                    .toList();

            assertTrue(unusedKeys.contains(
                "Emit BR092 validation error for consignment shipment consignor physical address region"),
                "Region entry should be flagged as unused");

            assertTrue(unusedKeys.contains(
                "Emit BR092 validation error for consignment shipment consignee email address"),
                "Email address entry should be flagged as unused");
        }

        @Test
        @DisplayName("BR092: all 9 other entries should be marked as used")
        void br092UsedEntryCount() throws IOException {
            DslCleaner.CleanerResult result = cleaner.run(testDmsRoot, false);

            DslCleaner.BrRuleSetResult br092 = result.ruleSetResults().stream()
                    .filter(r -> r.brCode().equals("BR092"))
                    .findFirst()
                    .orElseThrow();

            int used = br092.allEntries().size() - br092.unusedEntries().size();
            assertEquals(9, used, "BR092 should have 9 entries in use");
        }

        @Test
        @DisplayName("BR150: should identify exactly 1 unused entry")
        void br150UnusedEntryCount() throws IOException {
            DslCleaner.CleanerResult result = cleaner.run(testDmsRoot, false);

            DslCleaner.BrRuleSetResult br150 = result.ruleSetResults().stream()
                    .filter(r -> r.brCode().equals("BR150"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("BR150 result not found"));

            assertEquals(1, br150.unusedEntries().size(),
                "BR150 should have exactly 1 unused entry");
        }

        @Test
        @DisplayName("BR150: the transport document number entry should be unused")
        void br150UnusedEntryKey() throws IOException {
            DslCleaner.CleanerResult result = cleaner.run(testDmsRoot, false);

            DslCleaner.BrRuleSetResult br150 = result.ruleSetResults().stream()
                    .filter(r -> r.brCode().equals("BR150"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(
                "Emit BR150 validation error for declaration header transport document number",
                br150.unusedEntries().get(0).key(),
                "The transport document number entry should be the one flagged as unused"
            );
        }

        @Test
        @DisplayName("BR150: two-line entry (customs office) should be recognised as USED")
        void br150TwoLineEntryIsUsed() throws IOException {
            DslCleaner.CleanerResult result = cleaner.run(testDmsRoot, false);

            DslCleaner.BrRuleSetResult br150 = result.ruleSetResults().stream()
                    .filter(r -> r.brCode().equals("BR150"))
                    .findFirst()
                    .orElseThrow();

            boolean customsOfficeIsUnused = br150.unusedEntries().stream()
                    .anyMatch(e -> e.key().contains("customs office of presentation"));

            assertFalse(customsOfficeIsUnused,
                "The two-line customs office entry IS in the DSLR and should NOT be flagged as unused");
        }
    }

    // =========================================================================
    // 4. Cleaned file output tests
    // =========================================================================

    @Nested
    @DisplayName("Cleaned File Output")
    class CleanedFileTests {

        @Test
        @DisplayName("Cleaned file should not contain the removed entry keys")
        void cleanedFileDoesNotContainRemovedKeys(@TempDir Path tempDir) throws IOException {
            // Copy the BR092 DSL file to temp dir so we don't modify the test resource
            Path tempDsl = tempDir.resolve("validationResult-BR092.dsl");
            Files.copy(testDmsRoot.resolve("dsl/validationResult-BR092.dsl"), tempDsl);

            // Find unused entries via analysis
            DslCleaner.CleanerResult result = cleaner.run(testDmsRoot, false);
            DslCleaner.BrRuleSetResult br092 = result.ruleSetResults().stream()
                    .filter(r -> r.brCode().equals("BR092"))
                    .findFirst()
                    .orElseThrow();

            Path cleanedPath = tempDir.resolve("validationResult-BR092.dsl.cleaned");
            cleaner.writeCleanedDsl(tempDsl, br092.unusedEntries(), cleanedPath);

            assertTrue(Files.exists(cleanedPath), ".cleaned file should have been created");

            String cleanedContent = Files.readString(cleanedPath);

            assertFalse(cleanedContent.contains("CONSIGNOR_PHYSICAL_ADDRESS_REGION"),
                "Cleaned file must not contain the removed region entry");
            assertFalse(cleanedContent.contains("CONSIGNEE_EMAIL_ADDRESS"),
                "Cleaned file must not contain the removed email entry");
        }

        @Test
        @DisplayName("Cleaned file should still contain the kept entries")
        void cleanedFileRetainsUsedEntries(@TempDir Path tempDir) throws IOException {
            Path tempDsl = tempDir.resolve("validationResult-BR092.dsl");
            Files.copy(testDmsRoot.resolve("dsl/validationResult-BR092.dsl"), tempDsl);

            DslCleaner.CleanerResult result = cleaner.run(testDmsRoot, false);
            DslCleaner.BrRuleSetResult br092 = result.ruleSetResults().stream()
                    .filter(r -> r.brCode().equals("BR092"))
                    .findFirst()
                    .orElseThrow();

            Path cleanedPath = tempDir.resolve("validationResult-BR092.dsl.cleaned");
            cleaner.writeCleanedDsl(tempDsl, br092.unusedEntries(), cleanedPath);

            String cleanedContent = Files.readString(cleanedPath);

            assertTrue(cleanedContent.contains("CONSIGNOR_PHYSICAL_ADDRESS_STREET_NUMBER"),
                "Cleaned file must retain the street number entry which IS used");
            assertTrue(cleanedContent.contains("CONSIGNOR_PARTY_NAME"),
                "Cleaned file must retain the consignor party name entry which IS used");
            assertTrue(cleanedContent.contains("CONSIGNEE_PARTY_NAME"),
                "Cleaned file must retain the consignee party name entry which IS used");
        }

        @Test
        @DisplayName("Original DSL file should be unmodified after producing cleaned file")
        void originalFileIsUnmodified(@TempDir Path tempDir) throws IOException {
            Path originalDsl = testDmsRoot.resolve("dsl/validationResult-BR092.dsl");
            String originalContent = Files.readString(originalDsl);

            Path tempDsl = tempDir.resolve("validationResult-BR092.dsl");
            Files.copy(originalDsl, tempDsl);

            DslCleaner.CleanerResult result = cleaner.run(testDmsRoot, false);
            DslCleaner.BrRuleSetResult br092 = result.ruleSetResults().stream()
                    .filter(r -> r.brCode().equals("BR092"))
                    .findFirst()
                    .orElseThrow();

            Path cleanedPath = tempDir.resolve("validationResult-BR092.dsl.cleaned");
            cleaner.writeCleanedDsl(tempDsl, br092.unusedEntries(), cleanedPath);

            // Original in test/resources must be untouched
            assertEquals(originalContent, Files.readString(originalDsl),
                "The original DSL file must not be modified");
        }
    }

    // =========================================================================
    // 5. Edge case: no matching DSLR file
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("DSL file with no matching DSLR folder should be skipped")
        void missingDslrIsSkipped(@TempDir Path tempDir) throws IOException {
            // Set up a minimal dms root with a DSL file but no dslr counterpart
            Path dslDir  = tempDir.resolve("dsl");
            Path dslrDir = tempDir.resolve("dslr");
            Files.createDirectories(dslDir);
            Files.createDirectories(dslrDir);

            Files.writeString(dslDir.resolve("validationResult-BR999.dsl"),
                "[then] Emit BR999 validation error for something =\n" +
                "    insert(emitter.emit(drools,BR999, of($x, SOMETHING)));\n");

            // No dslr/BR999 folder exists
            DslCleaner.CleanerResult result = cleaner.run(tempDir, false);

            assertEquals(1, result.ruleSetResults().size());
            assertTrue(result.ruleSetResults().get(0).skipped(),
                "Result with no DSLR counterpart should be marked as skipped");
        }

        @Test
        @DisplayName("DSL file where all entries are used should produce no unused entries")
        void allEntriesUsed(@TempDir Path tempDir) throws IOException {
            Path dslDir  = tempDir.resolve("dsl");
            Path dslrDir = tempDir.resolve("dslr/BR998");
            Files.createDirectories(dslDir);
            Files.createDirectories(dslrDir);

            Files.writeString(dslDir.resolve("validationResult-BR998.dsl"),
                "[then] Emit BR998 validation error for some field =\n" +
                "    insert(emitter.emit(drools,BR998, of($x, SOME_FIELD)));\n");

            Files.writeString(dslrDir.resolve("BR998_rules.dslr"),
                "rule \"BR998_001\"\nwhen\n    Some thing exists\nthen\n" +
                "    Emit BR998 validation error for some field\nend\n");

            DslCleaner.CleanerResult result = cleaner.run(tempDir, false);

            DslCleaner.BrRuleSetResult br998 = result.ruleSetResults().get(0);
            assertTrue(br998.unusedEntries().isEmpty(),
                "When all entries are used there should be nothing to remove");
        }
    }
}
