package com.liuzhihang.doc.view.service;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.xmlb.XmlSerializer;
import com.liuzhihang.doc.view.config.ShowDocSettings;
import com.liuzhihang.doc.view.ui.settings.ShowDocSettingsConfigurable;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.ui.settings.YApiSettingsConfigurable;
import com.liuzhihang.doc.view.config.YuQueSettings;
import com.liuzhihang.doc.view.ui.settings.YuQueSettingsConfigurable;
import com.liuzhihang.doc.view.ui.ShowDocSettingForm;
import com.liuzhihang.doc.view.ui.YApiSettingForm;
import com.liuzhihang.doc.view.ui.YuQueSettingForm;
import org.jdom.Element;

import java.lang.reflect.Field;

/** Project XML credentials follow normal settings Apply/Reset/Cancel behavior, without a secret-store fixture. */
public final class IntegrationCredentialApplyTest extends LightJavaCodeInsightFixtureTestCase {
    public void testYapiInvalidApplyIsAtomicThenRetryResetCancelAndBlankApplyWork() {
        YApiSettings settings = YApiSettings.getInstance(getProject());
        Element original = XmlSerializer.serialize(settings.getState());
        settings.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                "url", "https://saved.example.test", "projectId", "7", "token", "synthetic-saved-token"), YApiSettings.class));
        try {
            EdtTestUtil.runInEdtAndWait(() -> {
                YApiSettingsConfigurable configurable = new YApiSettingsConfigurable(getProject());
                configurable.createComponent();
                configurable.reset();
                YApiSettingForm form = field(configurable, "yApiSettingForm", YApiSettingForm.class);
                try {
                    assertPasswordValue("synthetic-saved-token", passwordField(form, "tokenTextField"));
                    assertFalse(configurable.isModified());
                    textField(form, "urlTextField").setText(" https://draft.example.test/// ");
                    textField(form, "projectIdTextField").setText("not-a-number");
                    passwordField(form, "tokenTextField").setText("synthetic-new-token");
                    try {
                        configurable.apply();
                        fail("An invalid project ID must reject the entire draft");
                    } catch (ConfigurationException expected) {
                        assertFalse(expected.getMessage().contains("synthetic-new-token"));
                    }
                    assertEquals("https://saved.example.test", settings.getUrl());
                    assertEquals(Long.valueOf(7), settings.getProjectId());
                    assertSecret(settings, "token", "synthetic-saved-token");
                    assertPasswordValue("synthetic-new-token", passwordField(form, "tokenTextField"));
                    assertTrue(configurable.isModified());
                    textField(form, "projectIdTextField").setText("42");
                    apply(configurable);
                    assertEquals("https://draft.example.test", settings.getUrl());
                    assertEquals(Long.valueOf(42), settings.getProjectId());
                    assertSecret(settings, "token", "synthetic-new-token");
                    assertFalse(configurable.isModified());
                    passwordField(form, "tokenTextField").setText("synthetic-reset-draft");
                    configurable.reset();
                    assertPasswordValue("synthetic-new-token", passwordField(form, "tokenTextField"));
                    assertFalse(configurable.isModified());
                    passwordField(form, "tokenTextField").setText("synthetic-cancel-draft");
                } finally {
                    configurable.disposeUIResources();
                }
                assertSecret(settings, "token", "synthetic-new-token");
                YApiSettingsConfigurable reopened = new YApiSettingsConfigurable(getProject());
                reopened.createComponent();
                reopened.reset();
                try {
                    YApiSettingForm saved = field(reopened, "yApiSettingForm", YApiSettingForm.class);
                    assertPasswordValue("synthetic-new-token", passwordField(saved, "tokenTextField"));
                    passwordField(saved, "tokenTextField").setText("");
                    assertTrue(reopened.isModified());
                    apply(reopened);
                    assertSecretCleared(settings, "token");
                    assertFalse(reopened.isModified());
                } finally {
                    reopened.disposeUIResources();
                }
            });
        } finally {
            settings.loadState(XmlSerializer.deserialize(original, YApiSettings.class));
        }
    }

    public void testShowdocIndependentKeyAndTokenEditsPersistOnlyAfterApply() {
        ShowDocSettings settings = ShowDocSettings.getInstance(getProject());
        Element original = XmlSerializer.serialize(settings.getState());
        settings.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                "url", "https://showdoc.example.test", "apiKey", "synthetic-saved-key", "apiToken", "synthetic-saved-token"), ShowDocSettings.class));
        try {
            EdtTestUtil.runInEdtAndWait(() -> {
                ShowDocSettingsConfigurable configurable = new ShowDocSettingsConfigurable(getProject());
                configurable.createComponent();
                configurable.reset();
                ShowDocSettingForm form = field(configurable, "showDocSettingForm", ShowDocSettingForm.class);
                try {
                    assertPasswordValue("synthetic-saved-key", passwordField(form, "apiKeyTextField"));
                    assertPasswordValue("synthetic-saved-token", passwordField(form, "apiTokenTextField"));
                    assertFalse(configurable.isModified());
                    passwordField(form, "apiKeyTextField").setText("synthetic-replaced-key");
                    apply(configurable);
                    assertSecret(settings, "apiKey", "synthetic-replaced-key");
                    assertSecret(settings, "apiToken", "synthetic-saved-token");
                    assertFalse(configurable.isModified());
                    passwordField(form, "apiTokenTextField").setText("synthetic-reset-draft");
                    configurable.reset();
                    assertPasswordValue("synthetic-saved-token", passwordField(form, "apiTokenTextField"));
                    passwordField(form, "apiKeyTextField").setText("");
                    apply(configurable);
                    assertSecretCleared(settings, "apiKey");
                    assertSecret(settings, "apiToken", "synthetic-saved-token");
                    passwordField(form, "apiTokenTextField").setText("synthetic-cancel-draft");
                } finally {
                    configurable.disposeUIResources();
                }
                assertSecretCleared(settings, "apiKey");
                assertSecret(settings, "apiToken", "synthetic-saved-token");
            });
        } finally {
            settings.loadState(XmlSerializer.deserialize(original, ShowDocSettings.class));
        }
    }

    public void testYuqueResetLoadsSavedTokenAndBlankApplyClearsIt() {
        YuQueSettings settings = YuQueSettings.getInstance(getProject());
        Element original = XmlSerializer.serialize(settings.getState());
        settings.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                "login", "owner", "repos", "docs", "token", "synthetic-saved-yuque"), YuQueSettings.class));
        try {
            EdtTestUtil.runInEdtAndWait(() -> {
                YuQueSettingsConfigurable configurable = new YuQueSettingsConfigurable(getProject());
                configurable.createComponent();
                configurable.reset();
                YuQueSettingForm form = field(configurable, "yuQueSettingForm", YuQueSettingForm.class);
                try {
                    assertPasswordValue("synthetic-saved-yuque", passwordField(form, "tokenTextField"));
                    passwordField(form, "tokenTextField").setText("synthetic-reset-draft");
                    configurable.reset();
                    assertPasswordValue("synthetic-saved-yuque", passwordField(form, "tokenTextField"));
                    assertFalse(configurable.isModified());
                    passwordField(form, "tokenTextField").setText("synthetic-replaced-yuque");
                    apply(configurable);
                    assertSecret(settings, "token", "synthetic-replaced-yuque");
                    assertFalse(configurable.isModified());
                    passwordField(form, "tokenTextField").setText("");
                    apply(configurable);
                    assertSecretCleared(settings, "token");
                    assertFalse(configurable.isModified());
                    passwordField(form, "tokenTextField").setText("synthetic-cancel-draft");
                } finally {
                    configurable.disposeUIResources();
                }
                assertSecretCleared(settings, "token");
            });
        } finally {
            settings.loadState(XmlSerializer.deserialize(original, YuQueSettings.class));
        }
    }

    private static void apply(com.intellij.openapi.options.Configurable configurable) {
        try {
            configurable.apply();
        } catch (ConfigurationException exception) {
            throw new AssertionError("Valid project settings must apply directly", exception);
        }
    }

    private static void assertSecret(Object settings, String name, String expected) {
        ProjectCredentialPersistenceTest.assertOption(XmlSerializer.serialize(settings), name, expected);
    }

    private static void assertSecretCleared(Object settings, String name) {
        Element option = XmlSerializer.serialize(settings).getChildren("option").stream()
                .filter(child -> name.equals(child.getAttributeValue("name"))).findFirst().orElse(null);
        assertTrue("Expected cleared project credential " + name,
                option == null || option.getAttributeValue("value", "").isEmpty());
    }

    private static <T> T field(Object instance, String name, Class<T> type) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(instance));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access " + name, exception);
        }
    }

    private static JBTextField textField(Object form, String name) {
        return field(form, name, JBTextField.class);
    }

    private static JBPasswordField passwordField(Object form, String name) {
        return field(form, name, JBPasswordField.class);
    }

    private static void assertPasswordValue(String expected, JBPasswordField field) {
        char[] actual = field.getPassword();
        try {
            assertEquals(expected, new String(actual));
        } finally {
            java.util.Arrays.fill(actual, '\0');
        }
    }
}
