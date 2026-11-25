package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.httpClient.http.request.notification.HttpClientNotificationPanelV2Kt;
import com.intellij.openapi.ide.CopyPasteManager;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.awt.datatransfer.DataFlavor;

/** Runs the actual native toolbar actions against a loopback server, without a replacement HTTP engine. */
public class NativeActionsEndToEndTest extends BasePlatformTestCase {
    public void testRunAllNativeScriptsAndExportedCurlReachServer() throws Exception {
        List<String> paths = new CopyOnWriteArrayList<>();
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            paths.add(exchange.getRequestURI().getPath()); bodies.add(body);
            byte[] response = "{\"id\":123}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            RuntimeInstaller.install();
            // Avoid an unrelated IDEA/macOS locale pattern bug in the headless test process.
            var dateFormats = com.intellij.util.text.DateTimeFormatManager.getInstance();
            boolean overrideDates = dateFormats.isOverrideSystemDateFormat();
            com.intellij.openapi.util.Disposer.register(getTestRootDisposable(), () -> {
                dateFormats.setOverrideSystemDateFormat(overrideDates); dateFormats.resetFormats();
            });
            dateFormats.setOverrideSystemDateFormat(true); dateFormats.resetFormats();
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port;
            String file = """
                ### First JSON5
                POST %s/one
                Content-Type: application/json

                { // JSON5 comment\n                name:'O\\'Brien', count:0xff, tags:[1,],}
                > {%%
                  client.global.set("restClientXNextId", response.body.id);
                %%}

                ### Second with pre-request script
                < {%%
                  request.variables.set("localName", "Alice");
                %%}
                POST %s/two
                Content-Type: application/json

                {id:{{restClientXNextId}}, name:'{{localName}}',}

                ### Plain text
                POST %s/plain
                Content-Type: text/plain

                keep // plain text
                """.formatted(url, url, url);
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("rest-client-x-e2e").toRealPath();
            com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), dir.toString());
            com.intellij.openapi.util.Disposer.register(getTestRootDisposable(), () -> com.intellij.openapi.util.io.FileUtil.delete(dir.toFile()));
            java.nio.file.Path path = java.nio.file.Files.writeString(dir.resolve("run-all.http"), file);
            var virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
            assertNotNull(virtualFile);
            myFixture.configureFromExistingVirtualFile(virtualFile);
            myFixture.getEditor().getCaretModel().moveToOffset(file.indexOf("name:"));
            var copy = ActionManager.getInstance().getAction("HTTPClientConvertToCurlAndCopy");
            copy.actionPerformed(event(copy));
            String curl = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
            assertNotNull(curl);
            assertTrue(curl, curl.contains("\"count\":255"));
            Process process = new ProcessBuilder("/bin/sh", "-c", curl).redirectErrorStream(true).start();
            assertTrue("cURL timed out", process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8), 0, process.exitValue());
            assertEquals(1, bodies.size());
            String curlBody = bodies.getFirst();
            assertEquals("{\"name\":\"O'Brien\",\"count\":255,\"tags\":[1]}", curlBody);

            var runAll = ActionManager.getInstance().getAction("HTTPClient.RunAll");
            runAll.actionPerformed(event(runAll));
            PlatformTestUtil.waitWithEventsDispatching(() -> "Run All paths=" + paths + "\n" +
                java.util.Arrays.stream(com.intellij.openapi.editor.EditorFactory.getInstance().getAllEditors())
                    .map(e -> e.getDocument().getText()).collect(java.util.stream.Collectors.joining("\n---console---\n")),
                () -> bodies.size() == 4, 25);
            assertEquals(List.of("/one", "/one", "/two", "/plain"), paths);
            assertEquals(curlBody, bodies.get(1));
            assertEquals("{\"id\":123,\"name\":\"Alice\"}", bodies.get(2).trim());
            assertTrue(bodies.get(3), bodies.get(3).contains("keep // plain text"));
            waitForNativeProcesses();
            var block = com.intellij.psi.util.PsiTreeUtil.findChildOfType(myFixture.getFile(), com.intellij.httpClient.http.request.psi.HttpRequestBlock.class);
            var single = new com.intellij.httpClient.http.request.run.RunHttpExecutionAction.RunExecutionWithoutEnvAction(
                new com.intellij.httpClient.http.request.run.config.HttpSingleExecutionConfig(block), null);
            single.actionPerformed(event(single));
            PlatformTestUtil.waitWithEventsDispatching("Native single request must reach server", () -> bodies.size() == 5, 20);
            assertEquals(curlBody, bodies.get(4));
            waitForNativeProcesses();
            assertEquals(file, myFixture.getFile().getText());
        } finally {
            var contents = com.intellij.execution.ExecutionManager.getInstance(getProject()).getContentManager();
            for (var descriptor : allDescriptors()) {
                var handler = descriptor.getProcessHandler();
                if (handler != null && !handler.isProcessTerminated()) {
                    handler.destroyProcess();
                }
                var console = descriptor.getExecutionConsole();
                if (console != null && !com.intellij.openapi.util.Disposer.isDisposed(console)) {
                    com.intellij.openapi.util.Disposer.dispose(console);
                }
                contents.removeRunContent(com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance(), descriptor);
                if (!com.intellij.openapi.util.Disposer.isDisposed(descriptor)) {
                    com.intellij.openapi.util.Disposer.dispose(descriptor);
                }
            }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            // Some native request-preview views outlive their RunContent descriptor in light fixtures.
            // Dispose the owners through the disposer, rather than releasing their editors independently.
            List<com.intellij.openapi.Disposable> nativeConsoles = new ArrayList<>();
            com.intellij.openapi.util.Disposer.getTree().assertNoReferenceKeptInTree(object -> {
                if (object instanceof com.intellij.httpClient.http.request.run.console.HttpResponseConsole console) {
                    nativeConsoles.add(console.getConsole());
                }
                if (object instanceof com.intellij.openapi.Disposable disposable) {
                    String chain = com.intellij.openapi.util.Disposer.getTree().printParentChainToRoot(disposable);
                    if (object instanceof com.intellij.httpClient.http.request.run.console.HttpSingleRequestConsoleRequestTabHolder
                        || object instanceof com.intellij.httpClient.http.request.run.console.single.HttpSingleRequestConsoleAuthLogTabHolder
                        || (chain != null && chain.contains("com.intellij.httpClient.http.request.run.console.single.HttpResponseConsoleView"))) {
                        nativeConsoles.add(disposable);
                    }
                }
                return false;
            });
            for (var console : nativeConsoles) {
                if (!com.intellij.openapi.util.Disposer.isDisposed(console)) {
                    com.intellij.openapi.util.Disposer.dispose(console);
                }
            }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            server.stop(0); serverThreads.shutdownNow();
        }
    }
    private Set<com.intellij.execution.ui.RunContentDescriptor> allDescriptors() {
        var manager = com.intellij.execution.ExecutionManager.getInstance(getProject());
        Set<com.intellij.execution.ui.RunContentDescriptor> result = new LinkedHashSet<>(manager.getContentManager().getAllDescriptors());
        result.addAll(manager.getRunningDescriptors(settings -> true));
        for (var service : com.intellij.platform.execution.dashboard.RunDashboardManagerImpl.getInstance(getProject()).getRunConfigurations()) {
            if (service.getDescriptor() != null) {
                result.add(service.getDescriptor());
            }
        }
        return result;
    }
    private void waitForNativeProcesses() {
        PlatformTestUtil.waitWithEventsDispatching("Native execution should finish", () ->
            allDescriptors().stream()
                .allMatch(d -> d.getProcessHandler() == null || d.getProcessHandler().isProcessTerminated()), 20);
    }
    private AnActionEvent event(AnAction action) {
        var context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, getProject())
            .add(CommonDataKeys.PSI_FILE, myFixture.getFile())
            .add(CommonDataKeys.VIRTUAL_FILE, myFixture.getFile().getVirtualFile())
            .add(CommonDataKeys.EDITOR, myFixture.getEditor())
            .add(HttpClientNotificationPanelV2Kt.HTTP_CLIENT_IS_TOOLBAR_V2, true).build();
        return AnActionEvent.createFromAnAction(action, null, ActionPlaces.EDITOR_TOOLBAR, context);
    }
}
