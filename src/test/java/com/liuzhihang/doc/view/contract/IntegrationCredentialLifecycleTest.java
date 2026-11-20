package com.liuzhihang.doc.view.contract;

import com.intellij.openapi.options.Configurable;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.util.xmlb.XmlSerializer;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.ShowDocSettings;
import com.liuzhihang.doc.view.config.ShowDocSettingsConfigurable;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.config.YApiSettingsConfigurable;
import com.liuzhihang.doc.view.config.YuQueSettings;
import com.liuzhihang.doc.view.config.YuQueSettingsConfigurable;
import com.liuzhihang.doc.view.ui.ShowDocSettingForm;
import com.liuzhihang.doc.view.ui.YApiSettingForm;
import com.liuzhihang.doc.view.ui.YuQueSettingForm;
import org.jdom.Element;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.List;

public final class IntegrationCredentialLifecycleTest extends LightJavaCodeInsightFixtureTestCase {

    public void testSerializedSettingsKeepOrdinaryAndCredentialFields() {
        ShowDocSettings showDoc = XmlSerializer.deserialize(state("url", "https://showdoc.example.test",
                "apiKey", "synthetic-showdoc-key", "apiToken", "synthetic-showdoc-token"), ShowDocSettings.class);
        Element showDocXml = XmlSerializer.serialize(showDoc.getState());
        assertChildPresent(showDocXml, "url");
        assertChildValue(showDocXml, "apiKey", "synthetic-showdoc-key");
        assertChildValue(showDocXml, "apiToken", "synthetic-showdoc-token");

        YApiSettings yapi = XmlSerializer.deserialize(state("url", "https://yapi.example.test",
                "projectId", "42", "token", "synthetic-yapi-token"), YApiSettings.class);
        Element yapiXml = XmlSerializer.serialize(yapi.getState());
        assertChildPresent(yapiXml, "url");
        assertChildPresent(yapiXml, "projectId");
        assertChildValue(yapiXml, "token", "synthetic-yapi-token");

        YuQueSettings yuque = XmlSerializer.deserialize(state("login", "owner", "repos", "docs",
                "token", "synthetic-yuque-token"), YuQueSettings.class);
        Element yuqueXml = XmlSerializer.serialize(yuque.getState());
        assertChildPresent(yuqueXml, "url");
        assertChildPresent(yuqueXml, "apiUrl");
        assertChildPresent(yuqueXml, "login");
        assertChildPresent(yuqueXml, "repos");
        assertChildValue(yuqueXml, "token", "synthetic-yuque-token");
    }

    public void testCredentialFormsHaveNoSecretStoreStatusOrClearControls() {
        for (Class<?> type : List.of(ShowDocSettingForm.class, YApiSettingForm.class, YuQueSettingForm.class)) {
            for (Field field : type.getDeclaredFields()) {
                assertFalse("Direct project settings must not expose a PasswordSafe status control",
                        field.getName().equals("credentialStatusLabel"));
                assertFalse("Blank Apply directly clears a saved project value",
                        field.getName().startsWith("clear") && field.getName().endsWith("CheckBox"));
            }
        }
    }

    public void testAllIntegrationSecretInputsArePasswordFields() throws Exception {
        assertPasswordField(ShowDocSettingForm.class, "apiKeyTextField");
        assertPasswordField(ShowDocSettingForm.class, "apiTokenTextField");
        assertPasswordField(YApiSettingForm.class, "tokenTextField");
        assertPasswordField(YuQueSettingForm.class, "tokenTextField");
    }

    public void testConfigurableUiResourcesCanBeDisposedAndRecreated() throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> {
            List<Configurable> configurables = List.of(
                    new ShowDocSettingsConfigurable(getProject()),
                    new YApiSettingsConfigurable(getProject()),
                    new YuQueSettingsConfigurable(getProject()));
            for (Configurable configurable : configurables) {
                assertNotNull(configurable.createComponent());
                configurable.disposeUIResources();
                configurable.disposeUIResources();
                assertNotNull(configurable.createComponent());
                configurable.disposeUIResources();
            }
        });
    }

    public void testResetShowsSavedCredentialsAndDisposeDiscardsOnlyDrafts() {
        ShowDocSettings showSettings = ShowDocSettings.getInstance(getProject());
        YApiSettings yapiSettings = YApiSettings.getInstance(getProject());
        YuQueSettings yuqueSettings = YuQueSettings.getInstance(getProject());
        Element originalShow = XmlSerializer.serialize(showSettings.getState());
        Element originalYapi = XmlSerializer.serialize(yapiSettings.getState());
        Element originalYuque = XmlSerializer.serialize(yuqueSettings.getState());
        showSettings.loadState(XmlSerializer.deserialize(state("apiKey", "synthetic-saved-key",
                "apiToken", "synthetic-saved-show-token"), ShowDocSettings.class));
        yapiSettings.loadState(XmlSerializer.deserialize(state("token", "synthetic-saved-yapi"), YApiSettings.class));
        yuqueSettings.loadState(XmlSerializer.deserialize(state("token", "synthetic-saved-yuque"), YuQueSettings.class));
        try {
            EdtTestUtil.runInEdtAndWait(() -> {
                ShowDocSettingForm showDoc = new ShowDocSettingForm(getProject());
                YApiSettingForm yapi = new YApiSettingForm(getProject());
                YuQueSettingForm yuque = new YuQueSettingForm(getProject());
                try {
                    passwordField(showDoc, "apiKeyTextField").setText("synthetic-draft-key");
                    showDoc.reset();
                    assertPasswordValue("synthetic-saved-key", passwordField(showDoc, "apiKeyTextField"));
                    passwordField(showDoc, "apiTokenTextField").setText("synthetic-show-draft");
                    passwordField(yapi, "tokenTextField").setText("synthetic-yapi-draft");
                    yapi.reset();
                    assertPasswordValue("synthetic-saved-yapi", passwordField(yapi, "tokenTextField"));
                    passwordField(yapi, "tokenTextField").setText("synthetic-yapi-draft");
                    passwordField(yuque, "tokenTextField").setText("synthetic-yuque-draft");
                    yuque.reset();
                    assertPasswordValue("synthetic-saved-yuque", passwordField(yuque, "tokenTextField"));
                    passwordField(yuque, "tokenTextField").setText("synthetic-yuque-draft");
                } finally {
                    showDoc.dispose();
                    yapi.dispose();
                    yuque.dispose();
                }
                assertPasswordEmpty(passwordField(showDoc, "apiKeyTextField"));
                assertPasswordEmpty(passwordField(showDoc, "apiTokenTextField"));
                assertPasswordEmpty(passwordField(yapi, "tokenTextField"));
                assertPasswordEmpty(passwordField(yuque, "tokenTextField"));
                assertChildValue(XmlSerializer.serialize(showSettings.getState()), "apiKey", "synthetic-saved-key");
                assertChildValue(XmlSerializer.serialize(showSettings.getState()), "apiToken", "synthetic-saved-show-token");
                assertChildValue(XmlSerializer.serialize(yapiSettings.getState()), "token", "synthetic-saved-yapi");
                assertChildValue(XmlSerializer.serialize(yuqueSettings.getState()), "token", "synthetic-saved-yuque");
            });
        } finally {
            showSettings.loadState(XmlSerializer.deserialize(originalShow, ShowDocSettings.class));
            yapiSettings.loadState(XmlSerializer.deserialize(originalYapi, YApiSettings.class));
            yuqueSettings.loadState(XmlSerializer.deserialize(originalYuque, YuQueSettings.class));
        }
    }

    public void testIntegrationPagesUseGuideIconsAndClarifyYuQueFields() throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> {
            ShowDocSettingForm showDoc = new ShowDocSettingForm(getProject());
            YApiSettingForm yapi = new YApiSettingForm(getProject());
            YuQueSettingForm yuque = new YuQueSettingForm(getProject());
            try {
                for (Object form : List.of(showDoc, yapi, yuque)) {
                    assertEquals(DocViewBundle.message("integration.credentials.project.config"),
                            field(form, "credentialStorageHint", JLabel.class).getText());
                    JPanel helpContainer = field(form, "integrationHelpContainer", JPanel.class);
                    assertEquals("Each integration form should show one compact help icon",
                            1, helpContainer.getComponentCount());
                    Component guide = helpContainer.getComponent(0);
                    assertTrue("Integration guide should use IDEA's native help icon",
                            guide instanceof ContextHelpLabel);
                    assertEquals(DocViewBundle.message("integration.help.open"),
                            guide.getAccessibleContext().getAccessibleName());
                    assertEquals(DocViewBundle.message("integration.help.description"),
                            guide.getAccessibleContext().getAccessibleDescription());
                }

                assertTrue(containsLabel(yuque.getRootPanel(), DocViewBundle.message("yuque.url")));
                assertTrue(containsLabel(yuque.getRootPanel(), DocViewBundle.message("yuque.api.url")));
                assertTrue(containsLabel(yuque.getRootPanel(), DocViewBundle.message("yuque.username")));
                assertTrue(containsLabel(yuque.getRootPanel(), DocViewBundle.message("yuque.repos")));
                assertTrue(field(yuque, "apiUrlTextField", com.intellij.ui.components.JBTextField.class)
                        .getToolTipText().contains("example.test/api/v2"));
            } finally {
                showDoc.dispose();
                yapi.dispose();
                yuque.dispose();
            }
        });
    }

    private static void assertPasswordField(Class<?> formClass, String fieldName) throws Exception {
        Field field = formClass.getDeclaredField(fieldName);
        assertTrue(fieldName + " should use a password control",
                JBPasswordField.class.isAssignableFrom(field.getType()));
    }

    private static JBPasswordField passwordField(Object form, String fieldName) {
        try {
            Field field = form.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (JBPasswordField) field.get(form);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access password field " + fieldName, exception);
        }
    }

    private static <T> T field(Object instance, String fieldName, Class<T> fieldType) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return fieldType.cast(field.get(instance));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access form field " + fieldName, exception);
        }
    }

    private static boolean containsLabel(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) {
                return true;
            }
            if (child instanceof Container container && containsLabel(container, text)) {
                return true;
            }
        }
        return false;
    }

    private void assertPasswordValue(String expected, JBPasswordField field) {
        char[] value = field.getPassword();
        try {
            assertEquals(expected, new String(value));
        } finally {
            java.util.Arrays.fill(value, '\0');
        }
    }

    private static Element state(String... fields) {
        Element state = new Element("component");
        for (int i = 0; i < fields.length; i += 2) {
            state.addContent(new Element("option").setAttribute("name", fields[i]).setAttribute("value", fields[i + 1]));
        }
        return state;
    }

    private void assertChildValue(Element state, String name, String value) {
        Element option = state.getChildren("option").stream()
                .filter(child -> name.equals(child.getAttributeValue("name"))).findFirst().orElse(null);
        assertNotNull("Expected saved project field " + name, option);
        assertEquals(value, option.getAttributeValue("value"));
    }

    private void assertPasswordEmpty(JBPasswordField field) {
        char[] value = field.getPassword();
        try {
            assertEquals(0, value.length);
        } finally {
            java.util.Arrays.fill(value, '\0');
        }
    }

    private void assertChildPresent(Element element, String name) {
        assertTrue("Expected serialized field " + name, hasChild(element, name));
    }

    private static boolean hasChild(Element element, String name) {
        return element.getChildren().stream().anyMatch(child -> name.equals(child.getName())
                || name.equals(child.getAttributeValue("name")));
    }
}
