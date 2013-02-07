package org.apache.james.pop3server.netty;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.slf4j.Logger;

public class POP3ServerFactory extends AbstractServerFactory{

    private ProtocolHandlerLoader loader;
    private FileSystem fileSystem;
    
    @Inject
    @Resource(name = "protocolhandlerloader")
    public void setProtocolHandlerLoader(@Named("protocolhandlerloader") ProtocolHandlerLoader loader) {
        this.loader = loader;
    }

    @Inject
    @Resource(name = "filesystem")
    public final void setFileSystem(@Named("filesystem") FileSystem filesystem) {
        this.fileSystem = filesystem;
    }

    protected POP3Server createServer() {
       return new POP3Server();
    }
    
    @SuppressWarnings("unchecked")
    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(Logger log, HierarchicalConfiguration config) throws Exception{
        List<AbstractConfigurableAsyncServer> servers = new ArrayList<AbstractConfigurableAsyncServer>();
        List<HierarchicalConfiguration> configs = config.configurationsAt("pop3server");
        
        for (HierarchicalConfiguration serverConfig: configs) {
            POP3Server server = createServer();
            server.setProtocolHandlerLoader(loader);
            server.setLog(log);
            server.setFileSystem(fileSystem);
            server.configure(serverConfig);
            servers.add(server);
        }

        return servers;
    }
    

}
