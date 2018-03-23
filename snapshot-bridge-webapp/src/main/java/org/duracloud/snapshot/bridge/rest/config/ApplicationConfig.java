/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest.config;

import org.duracloud.client.ContentStore;
import org.duracloud.client.util.StoreClientUtil;
import org.duracloud.common.aop.RetryAdvice;
import org.duracloud.common.notification.NotificationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * All java-based config definitions live in this class.
 *
 * @author Daniel Bernstein
 * Date: Feb 4, 2014
 */
@Configuration
@EnableAspectJAutoProxy
public class ApplicationConfig {
    static Logger log = LoggerFactory.getLogger(ApplicationConfig.class);

    /**
     *
     */
    public ApplicationConfig() {
        log.info("creating ApplicationConfig instance...");
    }

    @Bean
    public StoreClientUtil storeClientUtil(RetryAdvice advice) {
        StoreClientUtil storeClientUtil = new ProxiedStoreClientUtil(advice);
        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.addAdvice(advice);
        proxyFactoryBean.setTarget(storeClientUtil);
        return (StoreClientUtil) proxyFactoryBean.getObject();
    }

    @Bean
    public RetryAdvice retryAdvice(NotificationManager notificationManager) {
        return new BridgeRetryAdvice(notificationManager);
    }

    class ProxiedStoreClientUtil extends StoreClientUtil {
        private RetryAdvice advice;

        public ProxiedStoreClientUtil(RetryAdvice advice) {
            this.advice = advice;
        }

        @Override
        public ContentStore createContentStore(String host,
                                               int port,
                                               String context,
                                               String username,
                                               String password,
                                               String storeId) {

            ContentStore contentStore = super.createContentStore(host, port, context, username, password, storeId);
            ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
            proxyFactoryBean.addAdvice(advice);
            proxyFactoryBean.setTarget(contentStore);
            return (ContentStore) proxyFactoryBean.getObject();
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return super.toString();
        }
    }
}


