/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/


 
package org.apache.james.fetchmail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.logging.Log;
import org.apache.james.api.user.UsersRepository;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.lifecycle.Configurable;
import org.apache.james.lifecycle.LogEnabled;
import org.apache.james.services.MailServer;

/**
 *  A class to instantiate and schedule a set of mail fetching tasks
 *
 * $Id$
 *
 */
public class FetchScheduler implements FetchSchedulerMBean, LogEnabled, Configurable {

    /**
     * Configuration object for this service
     */
    private HierarchicalConfiguration conf;


    /**
     * The scheduler service that is used to trigger fetch tasks.
     */
    private ScheduledExecutorService scheduler;

    /**
     * Whether this service is enabled.
     */
    private volatile boolean enabled = false;

    private List<ScheduledFuture<?>> schedulers = new ArrayList<ScheduledFuture<?>>();


    private DNSService dns;


    private MailServer mailserver;


    private UsersRepository urepos;
    
    private Log logger;

    @Resource(name="scheduler")
    public void setScheduledExecutorService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    
    @Resource(name="dnsservice")
    public void setDNSService(DNSService dns) {
        this.dns = dns;
    }


    @Resource(name="James")
    public void setMailServer(MailServer mailserver) {
        this.mailserver = mailserver;
    }
   
    @Resource(name="localusersrepository")
    public void setUsersRepository(UsersRepository urepos) {
        this.urepos = urepos;
    }
    
    public final void setLog(Log logger) {
        this.logger = logger;
    }
    
    public final void configure(HierarchicalConfiguration config) throws ConfigurationException{
        this.conf = config;
    }
    
    
    @SuppressWarnings("unchecked")
    @PostConstruct
    public void init() throws Exception
    {
        enabled = conf.getBoolean("[@enabled]", false);
        if (enabled)
        {

            List<HierarchicalConfiguration> fetchConfs = conf.configurationsAt("fetch");
            for (int i = 0; i < fetchConfs.size(); i++)
            {
                // read configuration
                HierarchicalConfiguration fetchConf = fetchConfs.get(i);
                Long interval = fetchConf.getLong("interval");

                FetchMail fetcher = new FetchMail();
                    
                fetcher.setLog(logger);
                fetcher.setDNSService(dns);
                fetcher.setMailServer(mailserver);
                fetcher.setUsersRepository(urepos);
                
                fetcher.configure(fetchConf);
                
                // initialize scheduling
                schedulers.add(scheduler.scheduleWithFixedDelay(fetcher, 0, interval, TimeUnit.MILLISECONDS));
            }

            if (logger.isInfoEnabled()) logger.info("FetchMail Started");
        }
        else
        {
            if (logger.isInfoEnabled()) logger.info("FetchMail Disabled");
        }
    }

    @PreDestroy
    public void dispose()
    {
        if (enabled)
        {
            logger.info("FetchMail dispose...");
            Iterator<ScheduledFuture<?>> schedulersIt = schedulers.iterator();
            while (schedulersIt.hasNext())
            {
                schedulersIt.next().cancel(false);
            }
            logger.info("FetchMail ...dispose end");
        }
    }
    
    /**
     * Describes whether this service is enabled by configuration.
     *
     * @return is the service enabled.
     */
    public final boolean isEnabled() {
        return enabled;
    }
    
}