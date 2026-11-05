package com.liuzhihang.doc.view.notification;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.LightPlatformTestCase;
import java.util.concurrent.atomic.AtomicReference;

public final class YApiUploadNotificationTest extends LightPlatformTestCase {
    public void testFallbackLinkIsAnExplicitWarningRatherThanAnInterfaceSuccess() {
        Notification notification = notifyUpload("http://yapi.example/project/299/interface/api/cat_1376");
        assertEquals(NotificationType.WARNING, notification.getType());
        assertTrue(notification.getContent(), notification.getContent().contains("未能定位接口"));
        assertEquals("打开分类", notification.getActions().getFirst().getTemplatePresentation().getText());
    }

    public void testDetailLinkRemainsNormalSuccess() {
        Notification notification = notifyUpload("http://yapi.example/project/299/interface/api/4396");
        assertEquals(NotificationType.INFORMATION, notification.getType());
        assertFalse(notification.getContent().contains("未能定位接口"));
    }

    private Notification notifyUpload(String url) {
        AtomicReference<Notification> emitted = new AtomicReference<>();
        Notifications listener = new Notifications() {
            @Override public void notify(Notification notification) {
                if ("doc-view.NotificationGroup".equals(notification.getGroupId())) emitted.set(notification);
            }
        };
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
                .subscribe(Notifications.TOPIC, listener);
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, listener);
        DocViewNotification.uploadSuccess(getProject(), "YApi", url);
        assertNotNull(emitted.get());
        emitted.get().expire();
        return emitted.get();
    }
}
