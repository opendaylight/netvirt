/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;
import static org.ops4j.pax.exam.MavenUtils.asInProject;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;
import static org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel.DEBUG;
import static org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel.ERROR;
import static org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel.INFO;
import static org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel.TRACE;
import static org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel.WARN;

import com.google.common.collect.Maps;
import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.mdsal.it.base.AbstractMdsalTestBase;
import org.opendaylight.netvirt.it.NetvirtITConstants.DefaultFlow;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.mdsal.utils.NotifyingDataChangeListener;
import org.opendaylight.ovsdb.utils.ovsdb.it.utils.DockerOvs;
import org.opendaylight.ovsdb.utils.ovsdb.it.utils.NodeInfo;
import org.opendaylight.ovsdb.utils.ovsdb.it.utils.OvsdbItUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ConnectionInfo;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for Netvirt.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class NetvirtIT extends AbstractMdsalTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(NetvirtIT.class);
    private static OvsdbItUtils itUtils;
    private static MdsalUtils mdsalUtils = null;
    private static SouthboundUtils southboundUtils;
    private static org.opendaylight.netvirt.it.SouthboundUtils nvSouthboundUtils;
    private static FlowITUtil flowITUtil;
    private static AtomicBoolean setup = new AtomicBoolean(false);
    private static final String NETVIRT_TOPOLOGY_ID = "netvirt:1";
    @Inject @Filter(timeout = 60000)
    private static DataBroker dataBroker = null;
    private static String userSpaceEnabled;

    @Override
    public MavenUrlReference getFeatureRepo() {
        return maven()
                .groupId("org.opendaylight.netvirt")
                .artifactId("vpnservice-features")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
    }

    @Override
    public String getFeatureName() {
        return "odl-netvirt-openstack-it";
    }

    @Configuration
    @Override
    public Option[] config() {
        Option[] ovsProps = super.config();
        Option[] propertiesOptions = DockerOvs.getSysPropOptions();
        Option[] otherOptions = getOtherOptions();
        Option[] options = new Option[ovsProps.length + propertiesOptions.length + otherOptions.length];
        System.arraycopy(ovsProps, 0, options, 0, ovsProps.length);
        System.arraycopy(propertiesOptions, 0, options, ovsProps.length, propertiesOptions.length);
        System.arraycopy(otherOptions, 0, options, ovsProps.length + propertiesOptions.length,
                otherOptions.length);
        return options;
    }

    private Option[] getOtherOptions() {
        return new Option[] {
                wrappedBundle(
                        mavenBundle("org.opendaylight.netvirt", "utils.mdsal-openflow")
                                .version(asInProject())
                                .type("jar")),
                configureConsole().startLocalConsole(),
                //when(System.getProperty("sgm").equals("transparent")).useOptions(
                        replaceConfigurationFile(
                                "etc/opendaylight/datastore/initial/config/netvirt-aclservice-config.xml",
                                new File("src/test/resources/initial/netvirt-aclservice-config.xml")),//),
                vmOption("-javaagent:../jars/org.jacoco.agent.jar=destfile=../../jacoco-it.exec"),
                keepRuntimeFolder()
        };
    }

    @Override
    public Option getLoggingOption() {
        return composite(
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        logConfiguration(NetvirtIT.class),
                        INFO.name()),
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.netvirt",
                        TRACE.name()),
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils",
                        TRACE.name()),
                /*editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.openflowplugin.impl",
                        DEBUG.name()),*/
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.openflowjava.protocol.impl.util.ListDeserializer",
                        ERROR.name()),
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.controller.configpusherfeature.internal.FeatureConfigPusher",
                        ERROR.name()),
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.apache.aries.blueprint.container.ServiceRecipe",
                        WARN.name()),
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.yangtools.yang.parser.repo.YangTextSchemaContextResolver",
                        WARN.name()),
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.netvirt.fibmanager.FibNodeCapableListener",
                        DEBUG.name()),
                super.getLoggingOption());
    }

    @Before
    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void setup() throws Exception {
        if (setup.get()) {
            LOG.info("Skipping setUp, already initialized");
            return;
        }

        try {
            super.setup();
        } catch (Exception e) {
            LOG.warn("Failed to setup test", e);
            fail("Failed to setup test: " + e);
        }

        Thread.sleep(10 * 1000);
        getProperties();

        assertNotNull("dataBroker should not be null", dataBroker);
        itUtils = new OvsdbItUtils(dataBroker);
        mdsalUtils = new MdsalUtils(dataBroker);
        assertNotNull("mdsalUtils should not be null", mdsalUtils);
        southboundUtils = new SouthboundUtils(mdsalUtils);
        nvSouthboundUtils = new org.opendaylight.netvirt.it.SouthboundUtils(mdsalUtils);
        assertTrue("Did not find " + NETVIRT_TOPOLOGY_ID, getNetvirtTopology());
        flowITUtil = new FlowITUtil(dataBroker);

        setup.set(true);
    }

    private void getProperties() {
        Properties props = System.getProperties();
        String addressStr = props.getProperty(NetvirtITConstants.SERVER_IPADDRESS);
        String portStr = props.getProperty(NetvirtITConstants.SERVER_PORT, NetvirtITConstants.DEFAULT_SERVER_PORT);
        String connectionType = props.getProperty(NetvirtITConstants.CONNECTION_TYPE, "active");
        String controllerStr = props.getProperty(NetvirtITConstants.CONTROLLER_IPADDRESS, "0.0.0.0");
        userSpaceEnabled = props.getProperty(NetvirtITConstants.USERSPACE_ENABLED, "no");
        LOG.info("setUp: Using the following properties: mode= {}, ip:port= {}:{}, controller ip: {}, "
                        + "userspace.enabled: {}",
                connectionType, addressStr, portStr, controllerStr, userSpaceEnabled);
    }

    private Boolean getNetvirtTopology() throws Exception {
        LOG.info("getNetvirtTopology: looking for {}...", NETVIRT_TOPOLOGY_ID);
        Boolean found = false;
        TopologyId topologyId = new TopologyId(NETVIRT_TOPOLOGY_ID);
        InstanceIdentifier<Topology> path =
                InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(topologyId));
        final NotifyingDataChangeListener netvirtTopologyListener =
                new NotifyingDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                        NotifyingDataChangeListener.BIT_CREATE, path, null);
        netvirtTopologyListener.registerDataChangeListener(dataBroker);
        netvirtTopologyListener.waitForCreation(60000);
        Topology topology = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, path);
        if (topology != null) {
            LOG.info("getNetvirtTopology: found {}...", NETVIRT_TOPOLOGY_ID);
            found = true;
        }
        netvirtTopologyListener.close();

        return found;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void validateDefaultFlows(long datapathId, int timeout) {
        LOG.info("Validating default flows");
        for (DefaultFlow defaultFlow : DefaultFlow.values()) {
            try {
                flowITUtil.verifyFlowByFields(datapathId, defaultFlow.getFlowId(), defaultFlow.getTableId(), timeout);
                //flowITUtil.verifyFlowById(datapathId, defaultFlow.getFlowId(), defaultFlow.getTableId());
            } catch (Exception e) {
                LOG.error("Failed to verify flow id : {}", defaultFlow.getFlowId());
                fail("Failed to verify flow id : " + defaultFlow.getFlowId());
            }
        }
    }

    private void addLocalIp(NodeInfo nodeInfo) {
        Map<String, String> otherConfigs = Maps.newHashMap();
        otherConfigs.put("local_ip", "10.1.1.1");
        assertTrue(nvSouthboundUtils.addOpenVSwitchOtherConfig(nodeInfo.ovsdbNode, otherConfigs));
    }

    /**
     * Test for basic southbound events to netvirt.
     * <pre>The test will:
     * - connect to an OVSDB node and verify it is added to operational
     * - then verify that br-int was created on the node and stored in operational
     * - a port is then added to the bridge to verify that it is ignored by netvirt
     * - remove the bridge
     * - remove the node and verify it is not in operational
     * </pre>
     */
    @Test
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void testNetVirt() throws InterruptedException {
        try (DockerOvs ovs = new DockerOvs()) {
            ovs.logState(0, "idle");
            ConnectionInfo connectionInfo =
                    SouthboundUtils.getConnectionInfo(ovs.getOvsdbAddress(0), ovs.getOvsdbPort(0));
            NodeInfo nodeInfo = itUtils.createNodeInfo(connectionInfo, null);
            nodeInfo.connect();
            LOG.info("testNetVirt: should be connected: {}", nodeInfo.ovsdbNode.getNodeId());
            addLocalIp(nodeInfo);

            validateDefaultFlows(nodeInfo.datapathId, 2 * 60 * 1000);
            ovs.logState(0, "default flows");

            nodeInfo.disconnect();
        } catch (Exception e) {
            LOG.error("testNetVirt: Exception thrown by OvsDocker.OvsDocker()", e);
            fail("testNetVirt: Exception thrown by OvsDocker.OvsDocker() : " + e.getMessage());
        }
    }


    /**
     * Test a basic neutron use case. This test constructs a Neutron network, subnet, and two "vm" ports
     * and validates that the correct flows are installed on OVS. Then it pings from one VM port to the other.
     * @throws InterruptedException if we're interrupted while waiting for some mdsal operation to complete
     */
    @Test
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void testNeutronNet() throws InterruptedException {
        LOG.warn("testNeutronNet: starting test");
        try (DockerOvs ovs = new DockerOvs()) {
            Neutron neutron = new Neutron(mdsalUtils);
            NetOvs netOvs;
            Boolean isUserSpace = userSpaceEnabled.equals("yes");
            LOG.info("isUserSpace: {}, usingExternalDocker: {}", isUserSpace, ovs.usingExternalDocker());
            if (ovs.usingExternalDocker()) {
                netOvs = new RealNetOvsImpl(ovs, isUserSpace, mdsalUtils, neutron, southboundUtils);
            } else {
                netOvs = new DockerNetOvsImpl(ovs, isUserSpace, mdsalUtils, neutron, southboundUtils);
            }

            netOvs.logState(0, "idle");
            ConnectionInfo connectionInfo =
                    SouthboundUtils.getConnectionInfo(ovs.getOvsdbAddress(0), ovs.getOvsdbPort(0));
            NodeInfo nodeInfo = itUtils.createNodeInfo(connectionInfo, null);
            nodeInfo.connect();
            LOG.info("testNeutronNet: should be connected: {}", nodeInfo.ovsdbNode.getNodeId());
            addLocalIp(nodeInfo);

            validateDefaultFlows(nodeInfo.datapathId, 2 * 60 * 1000);
            netOvs.logState(0, "default flows");

            neutron.createNetwork();
            neutron.createSubnet();

            String port1 = netOvs.createPort(nodeInfo.bridgeNode);
            String port2 = netOvs.createPort(nodeInfo.bridgeNode);

            InstanceIdentifier<TerminationPoint> tpIid =
                    southboundUtils.createTerminationPointInstanceIdentifier(nodeInfo.bridgeNode, port2);
            final NotifyingDataChangeListener portOperationalListener =
                    new NotifyingDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                            NotifyingDataChangeListener.BIT_CREATE, tpIid, null);
            portOperationalListener.registerDataChangeListener(dataBroker);

            netOvs.preparePortForPing(port1);
            netOvs.preparePortForPing(port2);

            portOperationalListener.waitForCreation(10000);
            Thread.sleep(30000);
            netOvs.logState(0, "after ports");

            int rc = netOvs.ping(port1, port2);
            netOvs.logState(0, "after ping");
            assertEquals("Ping failed rc: " + rc, 0, rc);

            netOvs.destroy();
            nodeInfo.disconnect();
        } catch (Exception e) {
            LOG.error("testNeutronNet: Exception thrown by OvsDocker.OvsDocker()", e);
            fail("testNeutronNet: Exception thrown by OvsDocker.OvsDocker() : " + e.getMessage());
        }
    }
}
