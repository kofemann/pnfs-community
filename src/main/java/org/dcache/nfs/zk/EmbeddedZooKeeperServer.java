/*
 * Copyright (c) 2015 - 2017 Deutsches Elektronen-Synchroton,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program (see the file COPYING.LIB for more
 * details); if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.dcache.nfs.zk;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

public class EmbeddedZooKeeperServer {

    private final ZooKeeperServer zkServer;
    private final ServerCnxnFactory cnxnFactory;

    private static Properties DEFAULT_PROPERTIES = new Properties();

    static {
        DEFAULT_PROPERTIES.setProperty("dataDir", "/tmp/zookeeper");
        DEFAULT_PROPERTIES.setProperty("clientPort", "2181");
    }

    public static ZooKeeperServer createStartedServer(Properties properties)
            throws IOException, QuorumPeerConfig.ConfigException, InterruptedException {

        EmbeddedZooKeeperServer ezk = new EmbeddedZooKeeperServer(properties);
        ezk.start();
        return ezk.getZooKeeperServer();
    }

    public EmbeddedZooKeeperServer() throws IOException, QuorumPeerConfig.ConfigException {
        this(DEFAULT_PROPERTIES);
    }

    public EmbeddedZooKeeperServer(Properties properties)
            throws IOException, QuorumPeerConfig.ConfigException {

        final ServerConfig serverConfig = new ServerConfig();
        final QuorumPeerConfig config = new QuorumPeerConfig();

        config.parseProperties(properties);
        serverConfig.readFrom(config);

        File dataDir = new File(serverConfig.getDataLogDir());
        dataDir.mkdirs();

        FileTxnSnapLog ftxn = new FileTxnSnapLog(dataDir, new File(serverConfig.getDataDir()));

        zkServer = new ZooKeeperServer();
        zkServer.setTxnLogFactory(ftxn);

        zkServer.setTickTime(serverConfig.getTickTime());
        zkServer.setMinSessionTimeout(serverConfig.getMinSessionTimeout());
        zkServer.setMaxSessionTimeout(serverConfig.getMaxSessionTimeout());

        cnxnFactory = NIOServerCnxnFactory.createFactory(serverConfig.getClientPortAddress(), serverConfig.getMaxClientCnxns());
    }

    public void start() throws IOException, InterruptedException {
        cnxnFactory.startup(zkServer);
    }

    public void shutdown() {
        zkServer.shutdown();
    }

    public ZooKeeperServer getZooKeeperServer() {
        return zkServer;
    }
}
