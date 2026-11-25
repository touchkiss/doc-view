package com.liuzhihang.doc.view.contract;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.ui.components.JBPasswordField;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.ui.ShowDocSettingForm;
import com.liuzhihang.doc.view.ui.YApiSettingForm;
import com.liuzhihang.doc.view.ui.YuQueSettingForm;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.lang.reflect.Field;

public final class IntegrationSettingsLayoutTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    public void testMaskedCredentialFieldsAndProjectStorageHintsFitNarrowSettingsContent() {
        EdtTestUtil.runInEdtAndWait(() -> {
            Project project = getProject();
            ShowDocSettingForm showDoc = new ShowDocSettingForm(project);
            YApiSettingForm yapi = new YApiSettingForm(project);
            YuQueSettingForm yuque = new YuQueSettingForm(project);
            try {
                for (int width : new int[]{650, 1000}) {
                    assertPasswordFieldFits(showDoc.getRootPanel(),
                            component(showDoc, "apiKeyTextField", JBPasswordField.class),
                            component(showDoc, "apiKeyLabel", JLabel.class), width, "ShowDoc API key");
                    assertPasswordFieldFits(showDoc.getRootPanel(),
                            component(showDoc, "apiTokenTextField", JBPasswordField.class),
                            component(showDoc, "apiTokenLabel", JLabel.class), width, "ShowDoc API token");
                    assertHintAndGuideFollow(showDoc.getRootPanel(),
                            component(showDoc, "apiTokenTextField", JBPasswordField.class),
                            component(showDoc, "credentialStorageHint", JLabel.class),
                            component(showDoc, "integrationHelpContainer", JPanel.class), width, "ShowDoc");
                    assertPasswordFieldFits(yapi.getRootPanel(),
                            component(yapi, "tokenTextField", JBPasswordField.class),
                            component(yapi, "tokenLabel", JLabel.class), width, "YApi token");
                    assertHintAndGuideFollow(yapi.getRootPanel(),
                            component(yapi, "tokenTextField", JBPasswordField.class),
                            component(yapi, "credentialStorageHint", JLabel.class),
                            component(yapi, "integrationHelpContainer", JPanel.class), width, "YApi");
                    assertPasswordFieldFits(yuque.getRootPanel(),
                            component(yuque, "tokenTextField", JBPasswordField.class),
                            component(yuque, "tokenLabel", JLabel.class), width, "YuQue token");
                    assertHintAndGuideFollow(yuque.getRootPanel(),
                            component(yuque, "tokenTextField", JBPasswordField.class),
                            component(yuque, "credentialStorageHint", JLabel.class),
                            component(yuque, "integrationHelpContainer", JPanel.class), width, "YuQue");
                }
                assertNoClearCheckbox(showDoc.getRootPanel());
                assertNoClearCheckbox(yapi.getRootPanel());
                assertNoClearCheckbox(yuque.getRootPanel());
            } finally {
                showDoc.dispose();
                yapi.dispose();
                yuque.dispose();
            }
        });
    }

    private void assertPasswordFieldFits(JPanel root, JBPasswordField passwordField, JLabel label,
                                         int viewportWidth, String description) {
        root.setSize(viewportWidth, 700);
        layoutRecursively(root);
        assertTrue(description + " password field should retain a usable width at " + viewportWidth,
                passwordField.getWidth() >= 120);
        assertTrue(description + " must remain masked", passwordField.getEchoChar() != 0);
        assertSame(description + " label remains associated with its credential field", passwordField, label.getLabelFor());
        assertEquals(DocViewBundle.message("integration.credentials.project.config"), passwordField.getToolTipText());
    }

    private void assertHintAndGuideFollow(JPanel root, Component finalPassword, JLabel hint,
                                          JPanel guideContainer, int viewportWidth, String provider) {
        Rectangle passwordBounds = SwingUtilities.convertRectangle(finalPassword.getParent(), finalPassword.getBounds(), root);
        Rectangle hintBounds = SwingUtilities.convertRectangle(hint.getParent(), hint.getBounds(), root);
        Rectangle guideBounds = SwingUtilities.convertRectangle(guideContainer.getParent(), guideContainer.getBounds(), root);
        assertEquals(DocViewBundle.message("integration.credentials.project.config"), hint.getText());
        assertTrue(provider + " project-file hint should follow its final credential field",
                hintBounds.y >= passwordBounds.y + passwordBounds.height);
        assertTrue(provider + " project-file hint must fit without clipping",
                hintBounds.width >= hint.getPreferredSize().width
                        && hintBounds.x >= 0 && hintBounds.x + hintBounds.width <= viewportWidth);
        assertTrue(provider + " guide should follow the static storage hint",
                guideBounds.y >= hintBounds.y + hintBounds.height);
    }

    private static void assertNoClearCheckbox(Container container) {
        for (Component component : container.getComponents()) {
            assertFalse("Blank Apply clears credentials without a separate clear checkbox", component instanceof JCheckBox);
            if (component instanceof Container child) {
                assertNoClearCheckbox(child);
            }
        }
    }

    private static <T extends Component> T component(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access form component " + name, exception);
        }
    }

    private static void layoutRecursively(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layoutRecursively(nested);
            }
        }
    }
}
