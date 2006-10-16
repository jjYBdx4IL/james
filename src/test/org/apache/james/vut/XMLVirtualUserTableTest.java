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


package org.apache.james.vut;

import junit.framework.TestCase;

import org.apache.avalon.cornerstone.services.datasources.DataSourceSelector;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.avalon.framework.container.ContainerUtil;
import org.apache.avalon.framework.service.DefaultServiceManager;
import org.apache.avalon.framework.service.ServiceException;

import org.apache.james.services.FileSystem;
import org.apache.james.services.VirtualUserTable;

import org.apache.james.test.mock.avalon.MockLogger;

import org.apache.james.test.mock.james.MockFileSystem;
import org.apache.james.test.mock.util.AttrValConfiguration;

import org.apache.james.test.util.Util;

public class XMLVirtualUserTableTest extends TestCase {
    private String user = "user1";
    private String domain = "localhost";
    
    VirtualUserTable table;
    
    protected VirtualUserTable getVirtalUserTable() throws ServiceException, ConfigurationException, Exception {
        DefaultServiceManager serviceManager = new DefaultServiceManager();
        serviceManager.put(FileSystem.ROLE, new MockFileSystem());
        serviceManager.put(DataSourceSelector.ROLE, Util.getDataSourceSelector());
        XMLVirtualUserTable mr = new XMLVirtualUserTable();
        

        mr.enableLogging(new MockLogger());
        DefaultConfiguration defaultConfiguration = new DefaultConfiguration("conf");
        defaultConfiguration.addChild(new AttrValConfiguration("mapping",user + "@" + domain +"=user2@localhost;user3@localhost"));
        mr.configure(defaultConfiguration);
        return mr;
    }
    
    public void setUp() throws Exception {
        table = getVirtalUserTable();
    }
    
    public void tearDown() {
        ContainerUtil.dispose(table);
    }
    
    public void testGetMappings() throws ErrorMappingException {
        assertTrue("Found 2 mappings", table.getMappings(user, domain).size() == 2);
    }
}
