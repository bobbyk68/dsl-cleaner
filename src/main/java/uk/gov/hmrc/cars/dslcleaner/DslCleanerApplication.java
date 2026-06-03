package uk.gov.hmrc.cars.dslcleaner;

import uk.gov.hmrc.cars.dslcleaner.matcher.DslrMatcher;
import uk.gov.hmrc.cars.dslcleaner.parser.DslParser;
import uk.gov.hmrc.cars.dslcleaner.report.ReportWriter;
import uk.gov.hmrc.cars.dslcleaner.writer.DslFileWriter;

import java.nio.file.Paths;

/**
 * Entry point for the DSL Cleaner utility.
 *
 * Usage:
 *   java -jar dsl-cleaner.jar <path-to-rules/dms>
 *
 * Example:
 *   java -jar dsl-cleaner.jar ~/projects/centralised-automated-rules-system/src/main/resources/rules/dms
 */
public class DslCleanerApplication {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar dsl-cleaner.jar <path-to-rules/dms>");
            System.exit(1);
        }

        uk.gov.hmrc.cars.dslcleaner.DslCleanerService service = new uk.gov.hmrc.cars.dslcleaner.DslCleanerService(
                new DslParser(),
                new DslrMatcher(),
                new ReportWriter(),
                new DslFileWriter()
        );

        service.run(Paths.get(args[0]).toAbsolutePath().normalize(), true);
    }
}