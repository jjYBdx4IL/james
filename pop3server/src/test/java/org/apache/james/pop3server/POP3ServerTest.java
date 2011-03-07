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

package org.apache.james.pop3server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import junit.framework.TestCase;

import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.apache.commons.net.pop3.POP3Reply;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.mailbox.MailboxConstants;
import org.apache.james.mailbox.MailboxException;
import org.apache.james.mailbox.MailboxPath;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.mail.InMemoryCachingUidProvider;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.pop3server.netty.POP3Server;
import org.apache.james.protocols.lib.POP3BeforeSMTPHelper;
import org.apache.james.protocols.lib.PortUtil;
import org.apache.james.protocols.lib.mock.MockJSR250Loader;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerChain;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.mock.MockUsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class POP3ServerTest extends TestCase {

    private int m_pop3ListenerPort = PortUtil.getNonPrivilegedPort();
    private POP3TestConfiguration m_testConfiguration;
    private MockUsersRepository m_usersRepository = new MockUsersRepository();
    private POP3Client m_pop3Protocol = null;
    private MockJSR250Loader serviceManager;
    protected DNSService dnsservice;
    protected MockFileSystem fSystem;
    protected MockProtocolHandlerChain chain;
    private InMemoryMailboxManager manager;
    private byte[] content =        ("Return-path: return@test.com\r\n"+
            "Content-Transfer-Encoding: plain\r\n"+
            "Subject: test\r\n\r\n"+
            "Body Text POP3ServerTest.setupTestMails\r\n").getBytes();
    
    public POP3ServerTest() {
        super("AsyncPOP3ServerTest");
    }

    private POP3Server m_pop3Server;

    
    protected void initPOP3Server(POP3TestConfiguration testConfiguration) throws Exception {
        m_pop3Server.configure(testConfiguration);
        m_pop3Server.init();
    }

    
    protected void setUpPOP3Server() throws Exception {
        
        m_pop3Server = new POP3Server();
        m_pop3Server.setDNSService(dnsservice);
        m_pop3Server.setFileSystem(fSystem);
        m_pop3Server.setProtocolHandlerChain(chain);
       
        
        Logger log = LoggerFactory.getLogger("Mock");
        // slf4j can't set programmatically any log level. It's just a facade
        // log.setLevel(SimpleLog.LOG_LEVEL_DEBUG);
        m_pop3Server.setLog(log);
    }


    protected void setUp() throws Exception {
        setUpServiceManager();
        
        chain = new MockProtocolHandlerChain();
        chain.setLoader(serviceManager);
        chain.setLog(LoggerFactory.getLogger("ChainLog"));
   
        setUpPOP3Server();
        m_testConfiguration = new POP3TestConfiguration(m_pop3ListenerPort);
    }

    protected void finishSetUp(POP3TestConfiguration testConfiguration) throws Exception {
        testConfiguration.init();
        chain.configure(testConfiguration);        
        chain.init();
        initPOP3Server(testConfiguration);
    }

    
    protected void setUpServiceManager() throws Exception {
        serviceManager = new MockJSR250Loader();
        serviceManager.put("usersrepository",
                m_usersRepository);
        
        InMemoryMailboxSessionMapperFactory factory = new InMemoryMailboxSessionMapperFactory();

        manager = new InMemoryMailboxManager(factory, new Authenticator() {
            
            public boolean isAuthentic(String userid, CharSequence passwd) {
                try {
                    return m_usersRepository.test(userid, passwd.toString());
                } catch (UsersRepositoryException e) {

                    e.printStackTrace();
                    return false;
                }
            }
        }, new InMemoryCachingUidProvider());
        
        serviceManager.put("mailboxmanager", manager);
        
        dnsservice = setUpDNSServer();
        serviceManager.put("dnsservice", setUpDNSServer());
        fSystem = new MockFileSystem();
        serviceManager.put("filesystem",fSystem);
      
    }

    private DNSService setUpDNSServer() {
        DNSService dns = new MockDNSService() {
            public String getHostName(InetAddress addr) {
                return "localhost";
            }
            
            public InetAddress getLocalHost() throws UnknownHostException {
                return InetAddress.getLocalHost();
            }            
        
        };
        return dns;
    }
    protected void tearDown() throws Exception {
        
        if (m_pop3Protocol != null) {
           if ( m_pop3Protocol.isConnected()){
               m_pop3Protocol.sendCommand("quit");
               m_pop3Protocol.disconnect();
           }
        }
        
        manager.deleteEverything();
        //manager.deleteAll();

        m_pop3Server.destroy();

        super.tearDown();
    }

    public void testAuthenticationFail() throws Exception {
        finishSetUp(m_testConfiguration);
        
        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1", m_pop3ListenerPort);

        m_usersRepository.addUser("known", "test2");

        m_pop3Protocol.login("known", "test");
        assertEquals(0, m_pop3Protocol.getState());
        assertTrue(m_pop3Protocol.getReplyString().startsWith("-ERR"));
    }

    public void testUnknownUser() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1", m_pop3ListenerPort);

        m_pop3Protocol.login("unknown", "test");
        assertEquals(0, m_pop3Protocol.getState());
        assertTrue(m_pop3Protocol.getReplyString().startsWith("-ERR"));
    }

    public void testKnownUserEmptyInbox() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_usersRepository.addUser("foo", "bar");

        // not authenticated
        POP3MessageInfo[] entries = m_pop3Protocol.listMessages();
        assertNull(entries);

        m_pop3Protocol.login("foo", "bar");
        System.err.println(m_pop3Protocol.getState());
        assertEquals(1, m_pop3Protocol.getState());

        entries = m_pop3Protocol.listMessages();
        assertEquals(1, m_pop3Protocol.getState());

        assertNotNull(entries);
        assertEquals(entries.length, 0);
        
        POP3MessageInfo p3i = m_pop3Protocol.listMessage(1);
        assertEquals(1, m_pop3Protocol.getState());
        assertNull(p3i);

    }

    // TODO: This currently fails with Async implementation because
    //       it use Charset US-ASCII to decode / Encode the protocol
    //       from the RFC I'm currently not understand if NON-ASCII chars
    //       are allowed at all. So this needs to be checked
    /*
    public void testNotAsciiCharsInPassword() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        String pass = "bar" + (new String(new char[] { 200, 210 })) + "foo";
        m_usersRepository.addUser("foo", pass);
        InMemorySpoolRepository mockMailRepository = new InMemorySpoolRepository();
        m_mailServer.setUserInbox("foo", mockMailRepository);

        m_pop3Protocol.login("foo", pass);
        assertEquals(1, m_pop3Protocol.getState());
        ContainerUtil.dispose(mockMailRepository);
    }
    */


    public void testUnknownCommand() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);
        
        m_pop3Protocol.sendCommand("unkn");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("Expected -ERR as result for an unknown command", m_pop3Protocol.getReplyString().substring(0,4),"-ERR");
    }

    public void testUidlCommand() throws Exception {
        finishSetUp(m_testConfiguration);

        m_usersRepository.addUser("foo", "bar");

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_pop3Protocol.sendCommand("uidl");
        assertEquals(0, m_pop3Protocol.getState());

        m_pop3Protocol.login("foo", "bar");

        POP3MessageInfo[] list = m_pop3Protocol.listUniqueIdentifiers();
        assertEquals("Found unexpected messages", 0, list.length);

        m_pop3Protocol.disconnect();
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, "foo", "INBOX");
        MailboxSession session = manager.login("foo", "bar", LoggerFactory.getLogger("Test"));
        if (manager.mailboxExists(mailboxPath, session) == false) {
            manager.createMailbox(mailboxPath, session);
        }
        setupTestMails(session,manager.getMailbox(mailboxPath, session));
        
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);
        m_pop3Protocol.login("foo", "bar");

        list = m_pop3Protocol.listUniqueIdentifiers();
        assertEquals("Expected 2 messages, found: "+list.length, 2, list.length);
        
        POP3MessageInfo p3i = m_pop3Protocol.listUniqueIdentifier(1);
        assertNotNull(p3i);
        
        manager.deleteMailbox(mailboxPath, session);


    }

    public void testMiscCommandsWithWithoutAuth() throws Exception {
        finishSetUp(m_testConfiguration);

        m_usersRepository.addUser("foo", "bar");
        
        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_pop3Protocol.sendCommand("noop");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("stat");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("pass");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("auth");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("rset");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));
        
        m_pop3Protocol.login("foo", "bar");

        POP3MessageInfo[] list = m_pop3Protocol.listUniqueIdentifiers();
        assertEquals("Found unexpected messages", 0, list.length);

        m_pop3Protocol.sendCommand("noop");
        assertEquals(1, m_pop3Protocol.getState());

        m_pop3Protocol.sendCommand("pass");
        assertEquals(1, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("auth");
        assertEquals(1, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("user");
        assertEquals(1, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("rset");
        assertEquals(1, m_pop3Protocol.getState());
        
    }

    public void testKnownUserInboxWithMessages() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_usersRepository.addUser("foo2", "bar2");

        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, "foo2", "INBOX");
        MailboxSession session = manager.login("foo2", "bar2", LoggerFactory.getLogger("Test"));
        
        if (manager.mailboxExists(mailboxPath, session) == false) {
            manager.createMailbox(mailboxPath, session);
        }
        
        setupTestMails(session,manager.getMailbox(mailboxPath, session));
        
        m_pop3Protocol.sendCommand("retr","1");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.login("foo2", "bar2");
        assertEquals(1, m_pop3Protocol.getState());

        POP3MessageInfo[] entries = m_pop3Protocol.listMessages();

        assertNotNull(entries);
        assertEquals(2, entries.length);
        assertEquals(1, m_pop3Protocol.getState());

        Reader r = m_pop3Protocol.retrieveMessageTop(entries[0].number, 0);

        assertNotNull(r);

        r.close();

        Reader r2 = m_pop3Protocol.retrieveMessage(entries[0].number);
        assertNotNull(r2);
        r2.close();

        // existing message
        boolean deleted = m_pop3Protocol.deleteMessage(entries[0].number);
        assertTrue(deleted);

        // already deleted message
        deleted = m_pop3Protocol.deleteMessage(entries[0].number);
        
        // TODO: Understand why this fails...
        assertFalse(deleted);

        // unexisting message
        deleted = m_pop3Protocol.deleteMessage(10);
        assertFalse(deleted);

        m_pop3Protocol.sendCommand("quit");
        m_pop3Protocol.disconnect();

        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_pop3Protocol.login("foo2", "bar2");
        assertEquals(1, m_pop3Protocol.getState());

        entries = null;

        POP3MessageInfo stats = m_pop3Protocol.status();
        assertEquals(1, stats.number);
        assertEquals(5, stats.size);

        entries = m_pop3Protocol.listMessages();

        assertNotNull(entries);
        assertEquals(1, entries.length);
        assertEquals(1, m_pop3Protocol.getState());

        // top without arguments
        m_pop3Protocol.sendCommand("top");
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));
        
        Reader r3 = m_pop3Protocol.retrieveMessageTop(entries[0].number, 0);
        assertNotNull(r3);
        r3.close();
        manager.deleteMailbox(mailboxPath, session);
    }

    private void setupTestMails(MailboxSession session, MessageManager mailbox) throws MailboxException {
        mailbox.appendMessage(new ByteArrayInputStream(content), new Date(), session, true, new Flags());
        byte[] content2 = ("EMPTY").getBytes();
        mailbox.appendMessage(new ByteArrayInputStream(content2), new Date(), session, true, new Flags());
    }

    // Test for JAMES-1202
    // Which shows that UIDL,STAT and LIST all show the same message numbers
    public void testStatUidlList() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_usersRepository.addUser("foo2", "bar2");

        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, "foo2", "INBOX");
        MailboxSession session = manager.login("foo2", "bar2", LoggerFactory.getLogger("Test"));
        
        if (manager.mailboxExists(mailboxPath, session) == false) {
            manager.createMailbox(mailboxPath, session);
        }
        
        int msgCount = 100;
        for (int i = 0; i < msgCount;i++) {
            manager.getMailbox(mailboxPath, session).appendMessage(new ByteArrayInputStream(("Subject: test\r\n\r\n" +i).getBytes()), new Date(), session, true, new Flags());
        }
        
        m_pop3Protocol.login("foo2", "bar2");
        assertEquals(1, m_pop3Protocol.getState());

        POP3MessageInfo[] listEntries = m_pop3Protocol.listMessages();
        POP3MessageInfo[] uidlEntries = m_pop3Protocol.listUniqueIdentifiers();
        POP3MessageInfo statInfo = m_pop3Protocol.status();
        assertEquals(msgCount, listEntries.length);
        assertEquals(msgCount, uidlEntries.length);
        assertEquals(msgCount, statInfo.number);

        m_pop3Protocol.sendCommand("quit");
        m_pop3Protocol.disconnect();

        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_pop3Protocol.login("foo2", "bar2");
        assertEquals(1, m_pop3Protocol.getState());

        manager.deleteMailbox(mailboxPath, session);
    }
    /*
    public void testTwoSimultaneousMails() throws Exception {
        finishSetUp(m_testConfiguration);

        // make two user/repositories, open both
        m_usersRepository.addUser("foo1", "bar1");
        InMemorySpoolRepository mailRep1 = new InMemorySpoolRepository();
        setupTestMails(mailRep1);
        m_mailServer.setUserInbox("foo1", mailRep1);

        m_usersRepository.addUser("foo2", "bar2");
        InMemorySpoolRepository mailRep2 = new InMemorySpoolRepository();
        //do not setupTestMails, this is done later
        m_mailServer.setUserInbox("foo2", mailRep2);

        POP3Client pop3Protocol2 = null;
        try {
            // open two connections
            m_pop3Protocol = new POP3Client();
            m_pop3Protocol.connect("127.0.0.1", m_pop3ListenerPort);
            pop3Protocol2 = new POP3Client();
            pop3Protocol2.connect("127.0.0.1", m_pop3ListenerPort);

            assertEquals("first connection taken", 0, m_pop3Protocol.getState());
            assertEquals("second connection taken", 0, pop3Protocol2.getState());

            // open two accounts
            m_pop3Protocol.login("foo1", "bar1");

            pop3Protocol2.login("foo2", "bar2");

            POP3MessageInfo[] entries = m_pop3Protocol.listMessages();
            assertEquals("foo1 has mails", 2, entries.length);

            entries = pop3Protocol2.listMessages();
            assertEquals("foo2 has no mails", 0, entries.length);

        } finally {
            // put both to rest, field var is handled by tearDown()
            if (pop3Protocol2 != null) {
                pop3Protocol2.sendCommand("quit");
                pop3Protocol2.disconnect();
            }
        }
    }
    */
    
    public void testIpStored() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        String pass = "password";
        m_usersRepository.addUser("foo", pass);

        m_pop3Protocol.login("foo", pass);
        assertEquals(1, m_pop3Protocol.getState());
        assertTrue(POP3BeforeSMTPHelper.isAuthorized("127.0.0.1"));
    }
    
    public void testCapa() throws Exception {
         finishSetUp(m_testConfiguration);

         m_pop3Protocol = new POP3Client();
         m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

         String pass = "password";
         m_usersRepository.addUser("foo", pass);

         assertEquals(POP3Reply.OK, m_pop3Protocol.sendCommand("CAPA"));
         
         m_pop3Protocol.getAdditionalReply();
         m_pop3Protocol.getReplyString();
         List<String> replies = Arrays.asList(m_pop3Protocol.getReplyStrings());
         
         assertTrue("contains USER", replies.contains("USER"));
         
         m_pop3Protocol.login("foo", pass);
         assertEquals(POP3Reply.OK, m_pop3Protocol.sendCommand("CAPA"));
         
         m_pop3Protocol.getAdditionalReply();
         m_pop3Protocol.getReplyString();
         replies = Arrays.asList(m_pop3Protocol.getReplyStrings());
         assertTrue("contains USER", replies.contains("USER"));
         assertTrue("contains UIDL", replies.contains("UIDL"));
         assertTrue("contains TOP", replies.contains("TOP"));

    }
    

    /*
     * See JAMES-649
     * The same happens when using RETR
     *     
     * Comment to not broke the builds!
     *
    public void testOOMTop() throws Exception {
        finishSetUp(m_testConfiguration);

        int messageCount = 30000;
        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_usersRepository.addUser("foo", "bar");
        InMemorySpoolRepository mockMailRepository = new InMemorySpoolRepository();
        
        Mail m = new MailImpl();
        m.setMessage(Util.createMimeMessage("X-TEST", "test"));
        for (int i = 1; i < messageCount+1; i++ ) {
            m.setName("test" + i);
            mockMailRepository.store(m);
        }

        m_mailServer.setUserInbox("foo", mockMailRepository);

        // not authenticated
        POP3MessageInfo[] entries = m_pop3Protocol.listMessages();
        assertNull(entries);

        m_pop3Protocol.login("foo", "bar");
        System.err.println(m_pop3Protocol.getState());
        assertEquals(1, m_pop3Protocol.getState());

        entries = m_pop3Protocol.listMessages();
        assertEquals(1, m_pop3Protocol.getState());

        assertNotNull(entries);
        assertEquals(entries.length, messageCount);
        
        for (int i = 1; i < messageCount+1; i++ ) {
            Reader r = m_pop3Protocol.retrieveMessageTop(i, 100);
            assertNotNull(r);
            r.close();
        }
        
        ContainerUtil.dispose(mockMailRepository);
    }
    */
    
    
    // See JAMES-1136
    public void testDeadlockOnRetr() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_usersRepository.addUser("foo6", "bar6");

        MailboxPath mailboxPath = MailboxPath.inbox("foo6");
        MailboxSession session = manager.login("foo6", "bar6", LoggerFactory.getLogger("Test"));
        
        manager.startProcessingRequest(session);
        if (manager.mailboxExists(mailboxPath, session) == false) {
            manager.createMailbox(mailboxPath, session);
        }
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(content);
        
        byte[] bigMail = new byte[1024 * 1024 * 10];
        int c = 0;
        for (int i = 0; i < bigMail.length; i++) {
            
            bigMail[i] = 'X';
            c++;
            if (c == 1000 || i + 3 == bigMail.length) {
                c = 0;
                bigMail[++i] = '\r';
                bigMail[++i] = '\n';
            }
        }
        out.write(bigMail);
        bigMail = null;
        
        manager.getMailbox(mailboxPath, session).appendMessage(new ByteArrayInputStream(out.toByteArray()), new Date(), session, false, new Flags());
        manager.startProcessingRequest(session);
        
        m_pop3Protocol.login("foo6", "bar6");
        assertEquals(1, m_pop3Protocol.getState());

        POP3MessageInfo[] entries = m_pop3Protocol.listMessages();

        assertNotNull(entries);
        assertEquals(1, entries.length);
        assertEquals(1, m_pop3Protocol.getState());

        Reader r = m_pop3Protocol.retrieveMessage(entries[0].number);

        assertNotNull(r);
        r.close();
        manager.deleteMailbox(mailboxPath, session);
        
    }
    
}
