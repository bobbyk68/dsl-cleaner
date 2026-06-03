package uk.gov.hmrc.cars.dslcleaner.writer;

import uk.gov.hmrc.cars.dslcleaner.model.DslEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes cleaned copies of DSL files with unused entries removed.
 *
 * The original DSL file is never modified. The cleaned version is written
 * alongside the original as {@code validationResult-BRXXX.dsl.cleaned}.
 *
 * The blank line immediately following a removed entry block is also removed
 * to keep the file tidy.
 */
public class DslFileWriter {

    /**
     * Writes a cleaned copy of the given DSL file to the specified output path,
     * omitting all entries in the {@code toRemove} list.
     *
     * @param dslFile    the original DSL file to read from
     * @param toRemove   the list of unused entries to exclude
     * @param outputPath the path to write the cleaned file to
     */
    public void writeCleanedFile(Path dslFile, List<DslEntry> toRemove, Path outputPath)
            throws IOException {

        List<String> lines = Files.readAllLines(dslFile);
        Set<Integer> linesToRemove = buildLinesToRemove(lines, toRemove);

        List<String> retained = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!linesToRemove.contains(i)) {
                retained.add(lines.get(i));
            }
        }

        Files.writeString(outputPath,
                String.join(System.lineSeparator(), retained) + System.lineSeparator());
    }

    /**
     * Builds the set of 0-based line indices to remove, including any
     * trailing blank line after each removed entry block.
     */
    private Set<Integer> buildLinesToRemove(List<String> lines, List<DslEntry> toRemove) {
        Set<Integer> linesToRemove = new HashSet<>();

        for (DslEntry entry : toRemove) {
            for (int i = entry.startLine(); i <= entry.endLine(); i++) {
                linesToRemove.add(i);
            }
            int trailingBlank = entry.endLine() + 1;
            if (trailingBlank < lines.size() && lines.get(trailingBlank).isBlank()) {
                linesToRemove.add(trailingBlank);
            }
        }

        return linesToRemove;
    }

    /**
     * Returns the path for the cleaned version of the given DSL file,
     * written alongside the original with a .cleaned extension.
     */
    public Path cleanedPathFor(Path dslFile) {
        return dslFile.resolveSibling(dslFile.getFileName().toString() + ".cleaned");
    }
}