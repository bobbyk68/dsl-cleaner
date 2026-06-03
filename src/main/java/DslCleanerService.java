package uk.gov.hmrc.cars.dslcleaner;

import uk.gov.hmrc.cars.dslcleaner.matcher.DslrMatcher;
import uk.gov.hmrc.cars.dslcleaner.model.BrRuleSetResult;
import uk.gov.hmrc.cars.dslcleaner.model.CleanerResult;
import uk.gov.hmrc.cars.dslcleaner.model.DslEntry;
import uk.gov.hmrc.cars.dslcleaner.parser.DslParser;
import uk.gov.hmrc.cars.dslcleaner.report.ReportWriter;
import uk.gov.hmrc.cars.dslcleaner.writer.DslFileWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrates the DSL cleaning process.
 *
 * Responsibilities:
 * - Discovering validationResult-BRXXX.dsl files under the dsl/ directory
 * - Pairing each DSL file with its DSLR counterpart under dslr/BRXXX/
 * - Delegating parsing, matching, reporting and file writing to focused components
 * - Managing the interactive confirmation flow
 */
public class DslCleanerService {

    private static final Pattern DSL_FILE_PATTERN =
            Pattern.compile("validationResult-(BR\\d+)\\.dsl");

    private final DslParser    dslParser;
    private final DslrMatcher  dslrMatcher;
    private final ReportWriter reportWriter;
    private final DslFileWriter dslFileWriter;

    public DslCleanerService(DslParser dslParser,
                             DslrMatcher dslrMatcher,
                             ReportWriter reportWriter,
                             DslFileWriter dslFileWriter) {
        this.dslParser     = dslParser;
        this.dslrMatcher   = dslrMatcher;
        this.reportWriter  = reportWriter;
        this.dslFileWriter = dslFileWriter;
    }

    /**
     * Runs the full analysis and (optionally) writes cleaned files.
     *
     * @param dmsRoot     path to the rules/dms directory
     * @param interactive if true, prompts the user before writing .cleaned files
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

        List<BrRuleSetResult> results = analyse(dslFiles, dslrDir);

        reportWriter.write(dmsRoot, results);

        long totalUnused = results.stream()
                .mapToLong(r -> r.unusedEntries().size())
                .sum();

        if (totalUnused == 0) {
            System.out.println("All DSL entries are in use. No files need updating.");
            return new CleanerResult(results);
        }

        if (interactive && !confirmWithUser()) {
            System.out.println("Aborted. No files were written.");
            return new CleanerResult(results);
        }

        writeCleanedFiles(results);
        return new CleanerResult(results);
    }

    // -------------------------------------------------------------------------
    // Validation
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
    // Discovery
    // -------------------------------------------------------------------------

    private List<Path> discoverDslFiles(Path dslDir) throws IOException {
        return Files.list(dslDir)
                .filter(p -> DSL_FILE_PATTERN.matcher(p.getFileName().toString()).matches())
                .sorted()
                .collect(Collectors.toList());
    }

    private Path findDslrFile(Path dslrDir, String brCode) throws IOException {
        Path brFolder = dslrDir.resolve(brCode);
        if (!Files.isDirectory(brFolder)) return null;
        return Files.list(brFolder)
                .filter(p -> p.toString().endsWith(".dslr"))
                .findFirst()
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // Analysis
    // -------------------------------------------------------------------------

    private List<BrRuleSetResult> analyse(List<Path> dslFiles, Path dslrDir)
            throws IOException {

        List<BrRuleSetResult> results = new ArrayList<>();

        for (Path dslFile : dslFiles) {
            String brCode    = extractBrCode(dslFile.getFileName().toString());
            Path   dslrFile  = findDslrFile(dslrDir, brCode);

            if (dslrFile == null) {
                System.out.printf("[WARN] No DSLR file found for %s (looked in dslr/%s/) — skipping%n",
                        dslFile.getFileName(), brCode);
                results.add(BrRuleSetResult.skipped(brCode, dslFile));
                continue;
            }

            String           dslrContent = Files.readString(dslrFile);
            List<DslEntry>   allEntries  = dslParser.parse(dslFile);
            List<DslEntry>   unused      = allEntries.stream()
                    .filter(e -> !dslrMatcher.isKeyUsed(e.key(), dslrContent))
                    .collect(Collectors.toList());

            results.add(new BrRuleSetResult(brCode, dslFile, dslrFile, allEntries, unused, false));
        }

        return results;
    }

    // -------------------------------------------------------------------------
    // File writing
    // -------------------------------------------------------------------------

    private void writeCleanedFiles(List<BrRuleSetResult> results) throws IOException {
        int filesWritten = 0;

        for (BrRuleSetResult result : results) {
            if (result.skipped() || result.unusedEntries().isEmpty()) continue;

            Path cleanedPath = dslFileWriter.cleanedPathFor(result.dslFile());
            dslFileWriter.writeCleanedFile(result.dslFile(), result.unusedEntries(), cleanedPath);

            System.out.println("Written: " + cleanedPath.getFileName());
            filesWritten++;
        }

        long totalRemoved = results.stream()
                .mapToLong(r -> r.unusedEntries().size())
                .sum();

        System.out.printf("%nDone. %d .cleaned file(s) written, %d entr%s removed.%n",
                filesWritten, totalRemoved, totalRemoved == 1 ? "y" : "ies");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractBrCode(String fileName) {
        Matcher m = DSL_FILE_PATTERN.matcher(fileName);
        if (!m.matches()) throw new IllegalArgumentException("Unexpected filename: " + fileName);
        return m.group(1);
    }

    private boolean confirmWithUser() {
        System.out.print("Proceed with writing .cleaned files? [yes/no]: ");
        String answer = new Scanner(System.in).nextLine().strip().toLowerCase();
        return answer.equals("yes");
    }
}