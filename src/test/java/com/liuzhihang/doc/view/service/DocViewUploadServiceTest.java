package com.liuzhihang.doc.view.service;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.config.ShowDocSettings;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import com.intellij.util.xmlb.XmlSerializer;
import com.liuzhihang.doc.view.config.YuQueSettings;
import org.jdom.Element;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.liuzhihang.doc.view.service.impl.YApiServiceImpl;
import com.liuzhihang.doc.view.service.impl.YuQueServiceImpl;
import com.liuzhihang.doc.view.service.impl.ShowDocServiceImpl;
import java.util.List;
import com.liuzhihang.doc.view.integration.impl.YuQueFacadeServiceImpl;
import com.liuzhihang.doc.view.integration.dto.YuQueResponse;
import com.liuzhihang.doc.view.integration.dto.YuQueCreate;
import com.liuzhihang.doc.view.integration.dto.YuQueUpdate;
import java.util.concurrent.atomic.AtomicInteger;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.UploadPlatform;

import java.awt.EventQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DocViewUploadServiceTest extends LightJavaCodeInsightFixtureTestCase {
    private final java.util.List<String> observedNotificationText = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    public void testUploadPlatformRoutingUsesStableIdentifiersInsteadOfLocalizedLabels() {
        assertEquals(List.of(UploadPlatform.values()), DocViewUploadService.UPLOAD_OPTIONS);
        assertEquals("yapi", UploadPlatform.YAPI.getId());
        assertEquals("showdoc", UploadPlatform.SHOW_DOC.getId());
        assertEquals("yuque", UploadPlatform.YUQUE.getId());

        assertSame(ApplicationManager.getApplication().getService(YApiServiceImpl.class),
                DocViewUploadService.getInstance(UploadPlatform.YAPI));
        assertSame(ApplicationManager.getApplication().getService(ShowDocServiceImpl.class),
                DocViewUploadService.getInstance(UploadPlatform.SHOW_DOC));
        assertSame(ApplicationManager.getApplication().getService(YuQueServiceImpl.class),
                DocViewUploadService.getInstance(UploadPlatform.YUQUE));

        assertFalse("Upload dispatch must not depend on a localized display label", java.util.Arrays.stream(
                        DocViewUploadService.class.getDeclaredMethods())
                .anyMatch(method -> "getInstance".equals(method.getName())
                        && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{String.class})));
    }

    public void testSingleUploadChecksAndUploadsInBackground() throws Exception {
        FakeUploadService service = new FakeUploadService(true);
        EdtTestUtil.runInEdtAndWait(() -> service.upload(getProject(), new DocView("fake")));

        assertTrue("settings check should run", service.checked.await(10, TimeUnit.SECONDS));
        assertTrue("upload should run", service.uploaded.await(10, TimeUnit.SECONDS));
        assertTrue(service.checkRanInBackground.get());
        assertTrue(service.uploadRanInBackground.get());
    }

    public void testMissingSettingsStopsBeforeUpload() throws Exception {
        FakeUploadService service = new FakeUploadService(false);
        EdtTestUtil.runInEdtAndWait(() -> service.upload(getProject(), new DocView("fake")));

        assertTrue("settings check should run", service.checked.await(10, TimeUnit.SECONDS));
        assertEquals(1L, service.uploaded.getCount());
        assertTrue(service.checkRanInBackground.get());
    }

    public void testBatchCancellationStopsRemainingItemsWithoutNotification() throws Exception {
        AtomicInteger notifications = trackNotifications();
        AtomicInteger uploads = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        DocViewUploadService service = new DocViewUploadService() {
            @Override public boolean checkSettings(com.intellij.openapi.project.Project project) { return true; }
            @Override public void doUpload(com.intellij.openapi.project.Project project, DocView document) {
                uploads.incrementAndGet();
                ProgressManager.getInstance().getProgressIndicator().cancel();
                first.countDown();
            }
        };
        EdtTestUtil.runInEdtAndWait(() -> service.upload(getProject(), List.of(new DocView("one"), new DocView("two"))));
        assertTrue(first.await(10, TimeUnit.SECONDS));
        PlatformTestUtil.waitForAllBackgroundActivityToCalmDown();
        EdtTestUtil.runInEdtAndWait(PlatformTestUtil::dispatchAllInvocationEventsInIdeEventQueue);
        assertEquals(1, uploads.get());
        assertEquals(0, notifications.get());
    }

    public void testPlatformServicesPropagateCancellationWithoutErrorNotification() throws Exception {
        AtomicInteger notifications = trackNotifications();
        AtomicInteger settingsReads = new AtomicInteger();
        Project delegate = getProject();
        for (DocViewUploadService service : List.of(new YApiServiceImpl(), new ShowDocServiceImpl(), new YuQueServiceImpl())) {
            ProcessCanceledException cancellation = new ProcessCanceledException();
            Project canceledReadProject = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
                    new Class<?>[]{Project.class}, (proxy, method, args) -> {
                        if ("isDisposed".equals(method.getName())) {
                            return false;
                        }
                        if (method.getName().equals("getService") && args != null && args.length == 1
                                && (args[0] == YApiSettings.class || args[0] == ShowDocSettings.class
                                || args[0] == YuQueSettings.class)) {
                            settingsReads.incrementAndGet();
                            // Inject cancellation at the configuration-read boundary, independent of
                            // the IDE test runner's non-cancellable progress/coroutine context.
                            throw cancellation;
                        }
                        try {
                            return method.invoke(delegate, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
            Throwable propagated = ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    service.doUpload(canceledReadProject, new DocView("synthetic"));
                    return null;
                } catch (ProcessCanceledException actual) {
                    return actual;
                }
            }).get(10, TimeUnit.SECONDS);
            assertSame("Each platform must propagate the original cancellation unchanged", cancellation, propagated);
        }
        EdtTestUtil.runInEdtAndWait(PlatformTestUtil::dispatchAllInvocationEventsInIdeEventQueue);
        assertEquals("All three platform entry points must reach the cancellable configuration read", 3, settingsReads.get());
        assertEquals("Cancellation must never be reported as a failed upload", 0, notifications.get());
    }

    public void testYuQueQueryFailureNeverCreatesOrUpdatesDocument() throws Exception {
        AtomicInteger notifications = trackNotifications();
        AtomicInteger queries = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        YuQueSettings settings = YuQueSettings.getInstance(getProject());
        Element originalSettings = XmlSerializer.serialize(settings.getState());
        settings.loadState(XmlSerializer.deserialize(ProjectCredentialPersistenceTest.state(
                "token", "synthetic-sensitive-token", "login", "synthetic-owner", "repos", "synthetic-repo"), YuQueSettings.class));
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), YuQueFacadeServiceImpl.class,
                new YuQueFacadeServiceImpl() {
                    @Override public YuQueResponse getDoc(String url, String token, String namespace, String slug) throws Exception {
                        queries.incrementAndGet();
                        assertEquals("Uploads must read the saved project credential directly", "synthetic-sensitive-token", token);
                        throw new Exception("token=synthetic-sensitive-token at https://private.example.test with private-document-body");
                    }
                    @Override public YuQueResponse create(String url, String token, String namespace, YuQueCreate input) {
                        writes.incrementAndGet(); throw new AssertionError("Query failure must stop creation");
                    }
                    @Override public YuQueResponse update(String url, String token, String namespace, Long id, YuQueUpdate input) {
                        writes.incrementAndGet(); throw new AssertionError("Query failure must stop update");
                    }
                }, getTestRootDisposable());
        try {
            DocView document = new DocView("synthetic"); document.setPath("/test");
            ApplicationManager.getApplication().executeOnPooledThread(() ->
                    new YuQueServiceImpl().doUpload(getProject(), document)).get(10, TimeUnit.SECONDS);
            EdtTestUtil.runInEdtAndWait(PlatformTestUtil::dispatchAllInvocationEventsInIdeEventQueue);
            assertEquals(1, queries.get());
            assertEquals(0, writes.get());
            assertTrue("The failed upload should report a platform error", notifications.get() > 0);
            for (String text : observedNotificationText) {
                assertFalse("Credentials must not leak into upload errors", text.contains("synthetic-sensitive-token"));
                assertFalse("Remote response/address details must stay out of notifications", text.contains("private.example.test"));
                assertFalse("Document content must stay out of notifications", text.contains("private-document-body"));
            }
        } finally {
            settings.loadState(XmlSerializer.deserialize(originalSettings, YuQueSettings.class));
        }
    }

    private AtomicInteger trackNotifications() {
        AtomicInteger count = new AtomicInteger();
        Notifications listener = new Notifications() {
            @Override public void notify(Notification notification) {
                if ("doc-view.NotificationGroup".equals(notification.getGroupId())) {
                    count.incrementAndGet();
                    observedNotificationText.add(notification.getTitle() + " " + notification.getContent());
                }
            }
        };
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, listener);
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, listener);
        return count;
    }

    private static final class FakeUploadService implements DocViewUploadService {
        private final boolean configured;
        private final CountDownLatch checked = new CountDownLatch(1);
        private final CountDownLatch uploaded = new CountDownLatch(1);
        private final AtomicBoolean checkRanInBackground = new AtomicBoolean();
        private final AtomicBoolean uploadRanInBackground = new AtomicBoolean();

        private FakeUploadService(boolean configured) {
            this.configured = configured;
        }

        @Override
        public boolean checkSettings(com.intellij.openapi.project.Project project) {
            checkRanInBackground.set(!EventQueue.isDispatchThread());
            checked.countDown();
            return configured;
        }

        @Override
        public void doUpload(com.intellij.openapi.project.Project project, DocView docView) {
            uploadRanInBackground.set(!EventQueue.isDispatchThread());
            uploaded.countDown();
        }
    }
}
