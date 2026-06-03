package uk.gov.hmrc.cars.dslcleaner;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * DslCleaner - Removes unused [then] entries from validationResult-BRXXX.dsl files.
 *
 * A DSL entry is considered unused if its key (the text between [then] and =)
 * does not appear as a line in the corresponding dslr/BRXXX/BRXXX_rules.dslr file.
 *
 * Usage:
 *   mvn package
 *   java -jar target/dsl-cleaner-1.0-SNAPSHOT.jar <path-to-rules/dms>
 *
 * The tool runs in dry-run mode first, prints a summary per rule set,
 * writes a report file, then prompts for confirmation before writing
 * .cleaned versions of any modified DSL files.
 */
public class DslCleaner {

    private static final Pattern DSL_FILE_PATTERN =
            Pattern.compile("validationResult-(BR\\d+)\\.dsl");

    private static final DateTimeFormatter REPORT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar dsl-cleaner.jar <path-to-rules/dms>");
            System.exit(1);
        }

        Path dmsRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        new DslCleaner().run(dmsRoot, true);
    }

    /**
     * Main entry point — separated from main() so tests can call it directly.
     *
     * @param dmsRoot       path to the rules/dms directory
     * @param interactive   if true, prompts user for confirmation before writing files
     */
    public CleanerResult run(Path dmsRoot, boolean interactive) throws IOException {
        Path dslDir  = dmsRoot.resolve("dsl");
        Path dslrDir = dmsRoot.resolve("dslr");

        validateDirectories(dslDir, dslrDir);

        List<Path> dslFiles = discoverDslFiles(dslDir);
        if (dslFiles.isEmpty()) {
            System.out.println("No validationResult-BRXXX.dsl files found. Nothing to do.");
            return new CleanerResult(Collections.emptyList());
        }

        List<BrRuleSetResult> results = analyseRuleSets(dslFiles, dslrDir);

        printSummary(results);

        String reportContent = buildReport(dmsRoot, results);
        Path reportPath = dmsRoot.resolve("dsl-cleaner-report.txt");
        Files.writeString(reportPath, reportContent);
        System.out.println("Report written to: " + reportPath);

        long totalUnused = results.stream()
                .mapToLong(r -> r.unusedEntries().size())
                .sum();

        if (totalUnused == 0) {
            System.out.println("\nAll DSL entries are in use. No files need updating.");
            return new CleanerResult(results);
        }

        if (interactive) {
            System.out.print("\nProceed with writing .cleaned files? [yes/no]: ");
            Scanner scanner = new Scanner(System.in);
            String answer = scanner.nextLine().strip().toLowerCase();
            if (!answer.equals("yes")) {
                System.out.println("Aborted. No files were written.");
                return new CleanerResult(results);
            }
        }

        writeCleanedFiles(results);
        return new CleanerResult(results);
    }

    // -------------------------------------------------------------------------
    // Directory validation
    // -------------------------------------------------------------------------

    private void validateDirectories(Path dslDir, Path dslrDir) {
        if (!Files.isDirectory(dslDir)) {
            throw new IllegalArgumentException("dsl directory not found: " + dslDir);
        }
        if (!Files.isDirectory(dslrDir)) {
            throw new IllegalArgumentException("dslr directory not found: " + dslrDir);
        }
    }

    // -------------------------------------------------------------------------
    // File discovery
    // -------------------------------------------------------------------------

    private List<Path> discoverDslFiles(Path dslDir) throws IOException {
        return Files.list(dslDir)
                .filter(p -> DSL_FILE_PATTERN.matcher(p.getFileName().toString()).matches())
                .sorted()
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Analysis — pair each DSL file with its DSLR and find unused entries
    // -------------------------------------------------------------------------

    private List<BrRuleSetResult> analyseRuleSets(List<Path> dslFiles, Path dslrDir)
            throws IOException {

        List<BrRuleSetResult> results = new ArrayList<>();

        for (Path dslFile : dslFiles) {
            String brCode = extractBrCode(dslFile.getFileName().toString());
            Path dslrFile = findDslrFile(dslrDir, brCode);

            if (dslrFile == null) {
                System.out.printf("[WARN] No DSLR file found for %s (looked in dslr/%s/) — skipping%n",
                        dslFile.getFileName(), brCode);
                results.add(BrRuleSetResult.skipped(brCode, dslFile));
                continue;
            }

            String dslrContent       = Files.readString(dslrFile);
            List<DslEntry> allEntries = parseDslEntries(dslFile);
            List<DslEntry> unused    = allEntries.stream()
                    .filter(e -> !isKeyUsedInDslr(e.key(), dslrContent))
                    .collect(Collectors.toList());

            results.add(new BrRuleSetResult(brCode, dslFile, dslrFile, allEntries, unused, false));
        }

        return results;
    }

    // -------------------------------------------------------------------------
    // DSL parsing
    // -------------------------------------------------------------------------

    /**
     * Parses all [then] entries from a DSL file.
     *
     * Each entry starts with a line containing [then] and ends at the first
     * line containing a semicolon. The key is the text between [then] and
     * the first = sign, which may be on the same line or the next line.
     */
    public List<DslEntry> parseDslEntries(Path dslFile) throws IOException {
        List<String> lines = Files.readAllLines(dslFile);
        return parseDslLines(lines);
    }

    /**
     * Core parsing logic operating on raw lines — exposed for testing.
     */
    public List<DslEntry> parseDslLines(List<String> lines) {
        List<DslEntry> entries = new ArrayList<>();
        int i = 0;

        while (i < lines.size()) {
            String trimmed = lines.get(i).stripLeading();

            if (trimmed.startsWith("[then]")) {
                int startLine = i;
                // Text immediately after [then]
                String afterThen = trimmed.substring(6).stripLeading();
                int equalsPos    = afterThen.indexOf('=');

                String key;
                StringBuilder valueBuilder = new StringBuilder();

                if (equalsPos >= 0) {
                    // Key and = are on the same line as [then]
                    key = afterThen.substring(0, equalsPos).strip();
                    valueBuilder.append(afterThen.substring(equalsPos + 1).stripLeading());
                } else {
                    // Key continues on this line; = is on a subsequent line
                    key = afterThen.strip();

                    i++;
                    while (i < lines.size()) {
                        String nextLine = lines.get(i);
                        int eqPos       = nextLine.indexOf('=');

                        if (eqPos >= 0) {
                            // Any text before = on this line is a key continuation
                            String beforeEq = nextLine.substring(0, eqPos).strip();
                            if (!beforeEq.isEmpty()) {
                                key = key + " " + beforeEq;
                            }
                            valueBuilder.append(nextLine.substring(eqPos + 1).stripLeading());
                            break;
                        }
                        i++;
                    }
                }

                // Consume value lines until we see a semicolon
                while (!valueBuilder.toString().contains(";") && i + 1 < lines.size()) {
                    i++;
                    valueBuilder.append("\n").append(lines.get(i));
                }

                entries.add(new DslEntry(key.strip(), valueBuilder.toString().strip(), startLine, i));
            }
            i++;
        }

        return entries;
    }

    // -------------------------------------------------------------------------
    // DSLR matching
    // -------------------------------------------------------------------------

    /**
     * Returns true if the DSL key appears as a trimmed line anywhere in the DSLR content.
     */
    public boolean isKeyUsedInDslr(String key, String dslrContent) {
        for (String line : dslrContent.split("\\r?\\n")) {
            if (line.strip().equals(key)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Output — write .cleaned files
    // -------------------------------------------------------------------------

    private void writeCleanedFiles(List<BrRuleSetResult> results) throws IOException {
        int filesWritten = 0;

        for (BrRuleSetResult result : results) {
            if (result.skipped() || result.unusedEntries().isEmpty()) continue;

            Path cleanedPath = buildCleanedPath(result.dslFile());
            writeCleanedDsl(result.dslFile(), result.unusedEntries(), cleanedPath);

            System.out.println("Written: " + cleanedPath.getFileName());
            filesWritten++;
        }

        long totalRemoved = results.stream().mapToLong(r -> r.unusedEntries().size()).sum();
        System.out.printf("%nDone. %d .cleaned file(s) written, %d entr%s removed.%n",
                filesWritten, totalRemoved, totalRemoved == 1 ? "y" : "ies");
    }

    /**
     * Writes a cleaned copy of the DSL file with unused entries removed.
     * Also removes the blank line that immediately follows a removed block.
     */
    public void writeCleanedDsl(Path dslFile, List<DslEntry> toRemove, Path outputPath)
            throws IOException {

        List<String> lines = Files.readAllLines(dslFile);

        Set<Integer> linesToRemove = new HashSet<>();
        for (DslEntry entry : toRemove) {
            for (int i = entry.startLine(); i <= entry.endLine(); i++) {
                linesToRemove.add(i);
            }
            // Remove trailing blank line after the entry if present
            int nextLine = entry.endLine() + 1;
            if (nextLine < lines.size() && lines.get(nextLine).isBlank()) {
                linesToRemove.add(nextLine);
            }
        }

        List<String> retained = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!linesToRemove.contains(i)) {
                retained.add(lines.get(i));
            }
        }

        Files.writeString(outputPath,
                String.join(System.lineSeparator(), retained) + System.lineSeparator());
    }

    // -------------------------------------------------------------------------
    // Console summary
    // -------------------------------------------------------------------------

    private void printSummary(List<BrRuleSetResult> results) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(" DSL Cleaner — Analysis Report");
        System.out.println("=".repeat(70));

        for (BrRuleSetResult result : results) {
            if (result.skipped()) {
                System.out.printf("%n[%s] SKIPPED — no matching DSLR file found%n", result.brCode());
                continue;
            }

            int total  = result.allEntries().size();
            int unused = result.unusedEntries().size();
            int used   = total - unused;

            System.out.printf("%n[%s] %s%n", result.brCode(), result.dslFile().getFileName());
            System.out.printf("  Total entries : %d%n", total);
            System.out.printf("  In use        : %d%n", used);
            System.out.printf("  Unused        : %d%n", unused);

            if (!result.unusedEntries().isEmpty()) {
                System.out.println("  Entries to remove:");
                for (DslEntry e : result.unusedEntries()) {
                    System.out.println("    - " + e.key());
                }
            }
        }

        long grandTotal  = results.stream().filter(r -> !r.skipped())
                .mapToLong(r -> r.allEntries().size()).sum();
        long grandUnused = results.stream().mapToLong(r -> r.unusedEntries().size()).sum();

        System.out.println();
        System.out.println("=".repeat(70));
        System.out.printf("  Grand total entries : %d%n", grandTotal);
        System.out.printf("  Total unused        : %d%n", grandUnused);
        System.out.println("=".repeat(70));
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Report file
    // -------------------------------------------------------------------------

    private String buildReport(Path dmsRoot, List<BrRuleSetResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("DSL Cleaner Report\n");
        sb.append("Generated : ").append(LocalDateTime.now().format(REPORT_DATE_FORMAT)).append("\n");
        sb.append("Root      : ").append(dmsRoot).append("\n");
        sb.append("=".repeat(70)).append("\n");

        for (BrRuleSetResult result : results) {
            sb.append("\n");
            if (result.skipped()) {
                sb.append("[").append(result.brCode()).append("] SKIPPED — no matching DSLR file\n");
                continue;
            }

            int total  = result.allEntries().size();
            int unused = result.unusedEntries().size();

            sb.append("[").append(result.brCode()).append("] ")
              .append(result.dslFile().getFileName()).append("\n");
            sb.append("  DSL file  : ").append(result.dslFile()).append("\n");
            sb.append("  DSLR file : ").append(result.dslrFile()).append("\n");
            sb.append("  Total     : ").append(total).append("\n");
            sb.append("  In use    : ").append(total - unused).append("\n");
            sb.append("  Unused    : ").append(unused).append("\n");

            if (!result.unusedEntries().isEmpty()) {
                sb.append("  Entries removed:\n");
                for (DslEntry e : result.unusedEntries()) {
                    sb.append("    - ").append(e.key()).append("\n");
                }
                sb.append("  Cleaned file: ")
                  .append(buildCleanedPath(result.dslFile()).getFileName()).append("\n");
            } else {
                sb.append("  Status: all entries in use — no changes needed\n");
            }
        }

        sb.append("\n").append("=".repeat(70)).append("\n");
        long grandUnused = results.stream().mapToLong(r -> r.unusedEntries().size()).sum();
        sb.append("Total unused entries removed: ").append(grandUnused).append("\n");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractBrCode(String fileName) {
        Matcher m = DSL_FILE_PATTERN.matcher(fileName);
        if (!m.matches()) throw new IllegalArgumentException("Unexpected filename: " + fileName);
        return m.group(1);
    }

    private Path findDslrFile(Path dslrDir, String brCode) throws IOException {
        Path brFolder = dslrDir.resolve(brCode);
        if (!Files.isDirectory(brFolder)) return null;
        return Files.list(brFolder)
                .filter(p -> p.toString().endsWith(".dslr"))
                .findFirst()
                .orElse(null);
    }

    public Path buildCleanedPath(Path dslFile) {
        return dslFile.resolveSibling(dslFile.getFileName().toString() + ".cleaned");
    }

    // -------------------------------------------------------------------------
    // Result records
    // -------------------------------------------------------------------------

    public record DslEntry(String key, String value, int startLine, int endLine) {}

    public record BrRuleSetResult(
            String brCode,
            Path dslFile,
            Path dslrFile,
            List<DslEntry> allEntries,
            List<DslEntry> unusedEntries,
            boolean skipped) {

        static BrRuleSetResult skipped(String brCode, Path dslFile) {
            return new BrRuleSetResult(brCode, dslFile, null,
                    Collections.emptyList(), Collections.emptyList(), true);
        }
    }

    public record CleanerResult(List<BrRuleSetResult> ruleSetResults) {}
}
