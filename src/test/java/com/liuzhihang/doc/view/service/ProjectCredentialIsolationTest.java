package com.liuzhihang.doc.view.service;

import com.intellij.mock.MockProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import com.liuzhihang.doc.view.config.ShowDocSettings;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.config.YuQueSettings;
import org.jdom.Element;

/** Project containers must return independent settings, without consulting any external secret store. */
public final class ProjectCredentialIsolationTest extends LightJavaCodeInsightFixtureTestCase {
    public void testSeparateProjectServicesRetainIndependentXmlCredentials() {
        YApiSettings firstYapi = YApiSettings.getInstance(getProject());
        ShowDocSettings firstShow = ShowDocSettings.getInstance(getProject());
        YuQueSettings firstYuque = YuQueSettings.getInstance(getProject());
        Element originalYapi = XmlSerializer.serialize(firstYapi.getState());
        Element originalShow = XmlSerializer.serialize(firstShow.getState());
        Element originalYuque = XmlSerializer.serialize(firstYuque.getState());
        MockProject other = new MockProject(null, getTestRootDisposable());
        other.registerService(YApiSettings.class, new YApiSettings());
        other.registerService(ShowDocSettings.class, new ShowDocSettings());
        other.registerService(YuQueSettings.class, new YuQueSettings());
        try {
            YApiSettings secondYapi = YApiSettings.getInstance(other);
            ShowDocSettings secondShow = ShowDocSettings.getInstance(other);
            YuQueSettings secondYuque = YuQueSettings.getInstance(other);
            assertNotSame(firstYapi, secondYapi);
            assertNotSame(firstShow, secondShow);
            assertNotSame(firstYuque, secondYuque);
            firstYapi.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                    "token", "synthetic-first-yapi"), YApiSettings.class));
            secondYapi.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                    "token", "synthetic-second-yapi"), YApiSettings.class));
            firstShow.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                    "apiKey", "synthetic-first-key", "apiToken", "synthetic-first-show-token"), ShowDocSettings.class));
            secondShow.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                    "apiKey", "synthetic-second-key", "apiToken", "synthetic-second-show-token"), ShowDocSettings.class));
            firstYuque.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                    "token", "synthetic-first-yuque"), YuQueSettings.class));
            secondYuque.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                    "token", "synthetic-second-yuque"), YuQueSettings.class));
            firstYapi.loadState(new YApiSettings());
            firstShow.loadState(new ShowDocSettings());
            firstYuque.loadState(new YuQueSettings());
            ProjectCredentialPersistenceTest.assertOption(XmlSerializer.serialize(secondYapi.getState()), "token", "synthetic-second-yapi");
            ProjectCredentialPersistenceTest.assertOption(XmlSerializer.serialize(secondShow.getState()), "apiKey", "synthetic-second-key");
            ProjectCredentialPersistenceTest.assertOption(XmlSerializer.serialize(secondShow.getState()), "apiToken", "synthetic-second-show-token");
            ProjectCredentialPersistenceTest.assertOption(XmlSerializer.serialize(secondYuque.getState()), "token", "synthetic-second-yuque");
        } finally {
            firstYapi.loadState(XmlSerializer.deserialize(originalYapi, YApiSettings.class));
            firstShow.loadState(XmlSerializer.deserialize(originalShow, ShowDocSettings.class));
            firstYuque.loadState(XmlSerializer.deserialize(originalYuque, YuQueSettings.class));
        }
    }
}
