package uk.gov.hmrc.cars.dslcleaner.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * The result of analysing a single BR rule set — one DSL file paired with its DSLR file.
 *
 * @param brCode        the BR identifier e.g. BR092
 * @param dslFile       path to the validationResult-BRXXX.dsl file
 * @param dslrFile      path to the paired BRXXX_rules.dslr file (null if skipped)
 * @param allEntries    all [then] entries parsed from the DSL file
 * @param unusedEntries entries whose key was not found in the DSLR file
 * @param skipped       true if no matching DSLR file was found for this BR code
 */
public record BrRuleSetResult(
        String brCode,
        Path dslFile,
        Path dslrFile,
        List<DslEntry> allEntries,
        List<DslEntry> unusedEntries,
        boolean skipped) {

    public static BrRuleSetResult skipped(String brCode, Path dslFile) {
        return new BrRuleSetResult(
                brCode, dslFile, null,
                Collections.emptyList(), Collections.emptyList(), true);
    }
}