/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest.config;

import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.duracloud.common.aop.RetryAdvice;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;

/**
 * Sends an email to the admins if multiple retries fail.
 *
 * @author Daniel Bernstein
 * Date: Jan 13, 2016
 */
@Aspect
class BridgeRetryAdvice extends RetryAdvice {

    private NotificationManager notificationManager;
    private Map<Object, Date> lastNotificationSentMap = new HashMap<>();
    private long minWaitBetweenNotificationsInSeconds = 3600;

    public BridgeRetryAdvice(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
        setMaxRetries(3);
        setWaitTime(3000);
    }

    @Pointcut("(execution(public * org.duracloud.client.ContentStore.*(..)) and "
              + "  !execution(public * org.duracloud.client.ContentStore.addContent(..))) "
              + " or execution(public * org.duracloud.client.util.StoreClientUtil.*(..))")
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        try {
            return super.invoke(invocation);
        } catch (Throwable t) {
            try {
                Date lastNotifcationSent = this.lastNotificationSentMap.get(invocation.getThis());
                Date nextNotification = new Date();
                if (lastNotifcationSent != null) {
                    nextNotification = new Date(lastNotifcationSent.getTime() +
                                                (minWaitBetweenNotificationsInSeconds * 1000));
                }

                if (nextNotification.getTime() <= System.currentTimeMillis()) {
                    InetAddress lh = InetAddress.getLocalHost();
                    String subject = "The bridge (" + lh.getHostName() + "/" + lh.getHostAddress() + ") " +
                                     "failed to access duracloud instance with store client: ";
                    String message = t.getMessage() + "-->" + invocation.getThis();
                    this.notificationManager.sendAdminNotification(NotificationType.EMAIL, subject, message);
                    this.lastNotificationSentMap.put(invocation.getThis(), new Date());
                }
            } catch (Exception ex) {
                ApplicationConfig.log.error("failed to send notification: " + ex.getMessage()
                                            + "->" + t.getMessage(), ex);
            }
            throw t;
        }
    }
}
