package com.liuzhihang.doc.view.config;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.ui.GenerationRulesForm;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

public final class NoDocViewTagSettingsTest extends LightJavaCodeInsightFixtureTestCase {
    public void testStandardValidationSourcesAreAvailableByDefault() {
        var names = new Settings().getRequiredFieldAnnotation();
        for (String prefix : List.of("jakarta.validation.constraints.", "javax.validation.constraints.")) {
            for (String name : List.of("NotNull", "NotEmpty", "NotBlank")) assertTrue(prefix + name, names.contains(prefix + name));
        }
    }

    public void testKnownBrokenLegacyValidationSourcesMigrateWithoutLosingCustomSources() {
        Settings previous = new Settings();
        previous.setRequiredFieldAnnotation(new java.util.HashSet<>(java.util.Set.of(
                "[jakarta.validation.constraints.NotNull, javax.validation.constraints.NotNull]", "acme.Required")));
        Settings loaded = new Settings();
        loaded.loadState(previous);
        assertEquals(java.util.Set.of("jakarta.validation.constraints.NotNull", "javax.validation.constraints.NotNull", "acme.Required"),
                loaded.getRequiredFieldAnnotation());
    }

    public void testExplicitlyClearedStandardSourcesStayCleared() {
        Settings previous = new Settings();
        previous.setRequiredFieldAnnotation(new java.util.HashSet<>());
        Settings loaded = new Settings();
        loaded.loadState(previous);
        assertTrue(loaded.getRequiredFieldAnnotation().isEmpty());
    }

    public void testPluginTagSourcesAreNoLongerPersistedSettings() {
        for (String name : List.of("titleTag", "titleUseCommentTag", "nameTag", "nameUseCommentTag",
                "required", "requiredUseCommentTag")) {
            assertFalse("Removed plugin metadata must not remain configurable: " + name,
                    java.util.Arrays.stream(Settings.class.getDeclaredFields()).anyMatch(f -> name.equals(f.getName())));
        }
    }

    public void testRulePageContainsOnlyStandardSources() {
        GenerationRulesForm form = new GenerationRulesForm(getProject());
        List<JCheckBox> options = new ArrayList<>();
        collect(form.getRootPanel(), options);
        assertEquals(10, options.size());
        for (JCheckBox option : options) {
            assertFalse(option.getText(), option.getText().contains("@DocView."));
            assertFalse("Legacy tag help must be removed", tooltipContainsTag(option));
        }
    }

    private static boolean tooltipContainsTag(JComponent component) {
        String tooltip = component.getToolTipText();
        return tooltip != null && tooltip.contains("@DocView.");
    }

    private static void collect(Component component, List<JCheckBox> options) {
        if (component instanceof JCheckBox box) options.add(box);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, options);
        }
    }
}
