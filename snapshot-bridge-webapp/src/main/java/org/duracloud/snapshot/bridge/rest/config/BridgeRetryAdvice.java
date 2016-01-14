/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest.config;

import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.duracloud.common.aop.RetryAdvice;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;

/**
 * Sends an email to the admins if multiple retries fail.
 * @author Daniel Bernstein
 *         Date: Jan 13, 2016
 */
@Aspect
class BridgeRetryAdvice extends RetryAdvice {
    
    private NotificationManager notificationManager;
    
    public BridgeRetryAdvice(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
        setMaxRetries(3);
    }
    
    @Pointcut("execution(* org.duracloud.storeclient.ContentStore.*(..))")
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        try { 
            return super.invoke(invocation);
        }catch(Throwable t){
            try {
                this.notificationManager.sendAdminNotification(NotificationType.EMAIL,
                                                               "Failed to access duracloud instance with store client: ",
                                                               t.getMessage() + "-->"+ invocation.getThis());
            }catch(Exception ex){
                ApplicationConfig.log.error("failed to send notification: " + ex.getMessage() + "->" + t.getMessage(), ex);
            }
            throw t;
        }
    }
}