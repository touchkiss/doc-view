package com.star.easydoc.glossary;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ProjectGlossaryTest {
    @Test
    public void acceptsOnlyTermsObservedInProjectAndNormalizesKeys() {
        String response = """
                ```json
                [{"term":"TREC","meaning":"交易对账"},{"term":"abc","meaning":"未知"},
                 {"term":"trec","meaning":"重复"},{"term":"OMS","meaning":"  "}]
                ```
                """;
        assertEquals(Map.of("trec", "交易对账"), ProjectGlossary.parse(response, Set.of("trec", "oms")));
        assertThrows(IllegalArgumentException.class, () -> ProjectGlossary.parse("error", Set.of()));
    }

    @Test
    public void includesOnlyIdentifierEvidenceInPrompt() {
        ProjectGlossary.Evidence evidence = new ProjectGlossary.Evidence();
        evidence.add("TrecSettlementHandler");
        evidence.add("findByQuoteId");
        evidence.add("TRECMAX123");
        assertTrue(evidence.terms().containsAll(Set.of("trec", "quote", "id")));
        assertTrue(evidence.prompt().contains("TrecSettlementHandler"));
    }
}
