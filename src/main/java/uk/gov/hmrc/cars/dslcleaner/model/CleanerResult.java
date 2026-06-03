package uk.gov.hmrc.cars.dslcleaner.model;

import java.util.List;

/**
 * The top-level result of a full DSL cleaner run across all BR rule sets.
 *
 * @param ruleSetResults one result per validationResult-BRXXX.dsl file discovered
 */
public record CleanerResult(List<BrRuleSetResult> ruleSetResults) {}