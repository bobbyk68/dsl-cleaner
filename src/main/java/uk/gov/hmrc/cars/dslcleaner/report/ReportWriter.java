package uk.gov.hmrc.cars.dslcleaner.report;

import uk.gov.hmrc.cars.dslcleaner.model.BrRuleSetResult;
import uk.gov.hmrc.cars.dslcleaner.model.DslEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes the analysis summary to the console and to a report file.
 *
 * The report file is written to {@code <dmsRoot>/dsl-cleaner-report.txt}
 * after every run, regardless of whether any unused entries were found.
 */
public class ReportWriter {

    private static final String DIVIDER = "=".repeat(70);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Prints a per-rule-set summary to the console and writes the report file.
     *
     * @param dmsRoot path to the rules/dms root directory
     * @param results one result per BR rule set analysed
     * @return path to the written report file
     */
    public Path write(Path dmsRoot, List<BrRuleSetResult> results) throws IOException {
        printConsole(results);

        String reportContent = buildReportContent(dmsRoot, results);
        Path reportPath = dmsRoot.resolve("dsl-cleaner-report.txt");
        Files.writeString(reportPath, reportContent);

        System.out.println("Report written to: " + reportPath);
        return reportPath;
    }

    // -------------------------------------------------------------------------
    // Console output
    // -------------------------------------------------------------------------

    private void printConsole(List<BrRuleSetResult> results) {
        System.out.println();
        System.out.println(DIVIDER);
        System.out.println(" DSL Cleaner — Analysis Report");
        System.out.println(DIVIDER);

        for (BrRuleSetResult result : results) {
            System.out.println();
            if (result.skipped()) {
                System.out.printf("[%s] SKIPPED — no matching DSLR file found%n", result.brCode());
                continue;
            }

            int total  = result.allEntries().size();
            int unused = result.unusedEntries().size();

            System.out.printf("[%s] %s%n", result.brCode(), result.dslFile().getFileName());
            System.out.printf("  Total entries : %d%n", total);
            System.out.printf("  In use        : %d%n", total - unused);
            System.out.printf("  Unused        : %d%n", unused);

            if (!result.unusedEntries().isEmpty()) {
                System.out.println("  Entries to remove:");
                for (DslEntry e : result.unusedEntries()) {
                    System.out.printf("    - [line %d] %s%n", e.startLine() + 1, e.key());
                }
            }
        }

        long grandTotal  = results.stream().filter(r -> !r.skipped())
                .mapToLong(r -> r.allEntries().size()).sum();
        long grandUnused = results.stream()
                .mapToLong(r -> r.unusedEntries().size()).sum();

        System.out.println();
        System.out.println(DIVIDER);
        System.out.printf("  Grand total entries : %d%n", grandTotal);
        System.out.printf("  Total unused        : %d%n", grandUnused);
        System.out.println(DIVIDER);
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Report file content
    // -------------------------------------------------------------------------

    private String buildReportContent(Path dmsRoot, List<BrRuleSetResult> results) {
        StringBuilder sb = new StringBuilder();

        sb.append("DSL Cleaner Report\n");
        sb.append("Generated : ").append(LocalDateTime.now().format(DATE_FORMAT)).append("\n");
        sb.append("Root      : ").append(dmsRoot).append("\n");
        sb.append(DIVIDER).append("\n");

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
                    sb.append("    - [line ").append(e.startLine() + 1).append("] ")
                            .append(e.key()).append("\n");
                }
                sb.append("  Cleaned file: ")
                        .append(buildCleanedFileName(result.dslFile())).append("\n");
            } else {
                sb.append("  Status: all entries in use — no changes needed\n");
            }
        }

        long grandUnused = results.stream()
                .mapToLong(r -> r.unusedEntries().size()).sum();

        sb.append("\n").append(DIVIDER).append("\n");
        sb.append("Total unused entries removed: ").append(grandUnused).append("\n");

        return sb.toString();
    }

    private String buildCleanedFileName(Path dslFile) {
        return dslFile.getFileName().toString() + ".cleaned";
    }
}