package com.liuzhihang.doc.view.httpclient.idea;

import org.junit.Test;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** The merge must produce one Doc View plugin; native HTTP APIs are registered only with HTTP Client present. */
public class DocViewHttpClientRegistrationTest {
    private Document descriptor(String name) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(Path.of("src/main/resources/META-INF", name).toFile());
    }
    @Test public void preservesDocViewIdentityAndMakesHttpClientOptional() throws Exception {
        Document main = descriptor("plugin.xml");
        assertEquals("com.liuzhihang.doc-view", main.getElementsByTagName("id").item(0).getTextContent());
        NodeList dependencies = main.getElementsByTagName("depends");
        Element http = null;
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element item = (Element) dependencies.item(i);
            assertNotEquals("Do not depend on the separate REST Client X plugin", "com.touchkiss.restclientx", item.getTextContent().trim());
            if (item.getTextContent().trim().equals("com.jetbrains.restClient")) http = item;
        }
        assertNotNull("Native HTTP Client must be an optional dependency", http);
        assertEquals("true", http.getAttribute("optional"));
        assertEquals("doc-view-http-client.xml", http.getAttribute("config-file"));
    }
    @Test public void registersAllHttpIntegrationsOnlyInAdditionalDescriptor() throws Exception {
        Document main = descriptor("plugin.xml");
        assertEquals(0, main.getElementsByTagName("multiHostInjector").getLength());
        Document http = descriptor("doc-view-http-client.xml");
        assertEquals("An additional descriptor must not declare a second plugin ID", 0, http.getElementsByTagName("id").getLength());
        assertEquals("com.liuzhihang.doc.view.httpclient.idea.Json5BodyInjector",
            ((Element) http.getElementsByTagName("multiHostInjector").item(0)).getAttribute("implementation"));
        assertEquals("com.liuzhihang.doc.view.httpclient.idea.RuntimeInstaller",
            ((Element) http.getElementsByTagName("applicationService").item(0)).getAttribute("serviceImplementation"));
        assertEquals("com.liuzhihang.doc.view.httpclient.idea.ProjectStartup",
            ((Element) http.getElementsByTagName("postStartupActivity").item(0)).getAttribute("implementation"));
        Element action = (Element) http.getElementsByTagName("action").item(0);
        assertEquals("HTTPClientConvertToCurlAndCopy", action.getAttribute("id"));
        assertEquals("true", action.getAttribute("overrides"));
    }
}
