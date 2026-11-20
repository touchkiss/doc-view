package com.liuzhihang.doc.view.service;

import com.intellij.util.xmlb.XmlSerializer;
import com.liuzhihang.doc.view.config.ShowDocSettings;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.config.YuQueSettings;
import junit.framework.TestCase;
import org.jdom.Element;

/** Uses synthetic XML only: no PasswordSafe and no user project files are accessed. */
public final class ProjectCredentialPersistenceTest extends TestCase {
    public void testYapiLegacyTokenLoadsAndSurvivesStateAndXmlRoundTrip() {
        Element oldXml = state("url", "https://yapi.example.test", "projectId", "42",
                "token", "synthetic-yapi-<&\"+ token");
        YApiSettings loaded = XmlSerializer.deserialize(oldXml, YApiSettings.class);
        YApiSettings settings = new YApiSettings();
        settings.loadState(loaded);
        assertOption(XmlSerializer.serialize(settings.getState()), "token", "synthetic-yapi-<&\"+ token");
        YApiSettings reopened = XmlSerializer.deserialize(XmlSerializer.serialize(settings.getState()), YApiSettings.class);
        assertOption(XmlSerializer.serialize(reopened.getState()), "token", "synthetic-yapi-<&\"+ token");
        assertEquals("https://yapi.example.test", reopened.getUrl());
        assertEquals(Long.valueOf(42), reopened.getProjectId());
    }

    public void testShowdocLegacyApiKeyAndApiTokenSurviveXmlRoundTrip() {
        ShowDocSettings loaded = XmlSerializer.deserialize(state("url", "https://showdoc.example.test",
                "apiKey", "synthetic-key", "apiToken", "synthetic-token"), ShowDocSettings.class);
        ShowDocSettings settings = new ShowDocSettings();
        settings.loadState(loaded);
        Element serialized = XmlSerializer.serialize(settings.getState());
        assertOption(serialized, "apiKey", "synthetic-key");
        assertOption(serialized, "apiToken", "synthetic-token");
        ShowDocSettings reopened = XmlSerializer.deserialize(serialized, ShowDocSettings.class);
        assertOption(XmlSerializer.serialize(reopened.getState()), "apiKey", "synthetic-key");
        assertOption(XmlSerializer.serialize(reopened.getState()), "apiToken", "synthetic-token");
        assertEquals("https://showdoc.example.test", reopened.getUrl());
    }

    public void testYuqueLegacyTokenAndOrdinaryFieldsSurviveXmlRoundTrip() {
        YuQueSettings loaded = XmlSerializer.deserialize(state("url", "https://yuque.example.test",
                "apiUrl", "https://yuque.example.test/api/v2", "login", "owner", "repos", "docs",
                "token", "synthetic-yuque-token"), YuQueSettings.class);
        YuQueSettings settings = new YuQueSettings();
        settings.loadState(loaded);
        YuQueSettings reopened = XmlSerializer.deserialize(XmlSerializer.serialize(settings.getState()), YuQueSettings.class);
        assertOption(XmlSerializer.serialize(reopened.getState()), "token", "synthetic-yuque-token");
        assertEquals("owner/docs", reopened.getNamespace());
        assertEquals("https://yuque.example.test/api/v2", reopened.getApiUrl());
    }

    public void testProjectStateInstancesDoNotShareCredentialsOrLoadMutations() {
        YApiSettings projectA = XmlSerializer.deserialize(state("token", "synthetic-project-a"), YApiSettings.class);
        YApiSettings projectB = XmlSerializer.deserialize(state("token", "synthetic-project-b"), YApiSettings.class);
        projectA.loadState(XmlSerializer.deserialize(state("token", "synthetic-replaced-a"), YApiSettings.class));
        assertOption(XmlSerializer.serialize(projectA.getState()), "token", "synthetic-replaced-a");
        assertOption(XmlSerializer.serialize(projectB.getState()), "token", "synthetic-project-b");
        ShowDocSettings showA = XmlSerializer.deserialize(state("apiKey", "synthetic-show-a-key",
                "apiToken", "synthetic-show-a-token"), ShowDocSettings.class);
        ShowDocSettings showB = XmlSerializer.deserialize(state("apiKey", "synthetic-show-b-key",
                "apiToken", "synthetic-show-b-token"), ShowDocSettings.class);
        showA.loadState(XmlSerializer.deserialize(state("apiKey", "", "apiToken", ""), ShowDocSettings.class));
        assertOption(XmlSerializer.serialize(showA.getState()), "apiToken", "");
        assertOption(XmlSerializer.serialize(showB.getState()), "apiKey", "synthetic-show-b-key");
        assertOption(XmlSerializer.serialize(showB.getState()), "apiToken", "synthetic-show-b-token");
    }

    public void testConfigurationToStringDoesNotExposePersistedSecrets() {
        YApiSettings yapi = XmlSerializer.deserialize(state("token", "synthetic-yapi-private"), YApiSettings.class);
        ShowDocSettings showdoc = XmlSerializer.deserialize(state("apiKey", "synthetic-show-private-key",
                "apiToken", "synthetic-show-private-token"), ShowDocSettings.class);
        YuQueSettings yuque = XmlSerializer.deserialize(state("token", "synthetic-yuque-private"), YuQueSettings.class);
        assertFalse(yapi.toString().contains("synthetic-yapi-private"));
        assertFalse(showdoc.toString().contains("synthetic-show-private-key"));
        assertFalse(showdoc.toString().contains("synthetic-show-private-token"));
        assertFalse(yuque.toString().contains("synthetic-yuque-private"));
    }

    static Element state(String... fields) {
        Element state = new Element("component");
        for (int i = 0; i < fields.length; i += 2) {
            state.addContent(new Element("option").setAttribute("name", fields[i]).setAttribute("value", fields[i + 1]));
        }
        return state;
    }

    static void assertOption(Element state, String name, String expected) {
        Element option = state.getChildren("option").stream()
                .filter(child -> name.equals(child.getAttributeValue("name"))).findFirst().orElse(null);
        assertNotNull("Missing project XML field " + name, option);
        assertEquals(name, expected, option.getAttributeValue("value"));
    }
}
