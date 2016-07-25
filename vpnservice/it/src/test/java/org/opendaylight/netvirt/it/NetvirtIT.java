package org.opendaylight.netvirt.it;

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

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.mdsal.it.base.AbstractMdsalTestBase;
import org.opendaylight.netvirt.utils.netvirt.it.utils.NetITUtil;
import org.opendaylight.netvirt.vpnmanager.VpnserviceProvider;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.ovsdb.it.utils.DockerOvs;
import org.opendaylight.ovsdb.utils.ovsdb.it.utils.NodeInfo;
import org.opendaylight.ovsdb.utils.ovsdb.it.utils.OvsdbItUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ConnectionInfo;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 *
 * Integration tests for vpnservice netvirt
 *
 * Created by oshvartz on 7/4/16.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class NetvirtIT extends AbstractMdsalTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(NetvirtIT.class);
    private static OvsdbItUtils itUtils;
    private static MdsalUtils mdsalUtils = null;
    private static SouthboundUtils southboundUtils;
    private static String addressStr;
    private static String portStr;
    private static String connectionType;
    private static String controllerStr;
    private static AtomicBoolean setup = new AtomicBoolean(false);
    private static DataBroker dataBroker = null;
    private static final String NETVIRT_TOPOLOGY_ID = "netvirt:1";

    @Override
    public String getModuleName() {
        return "vpnservice-impl";
    }

    @Override
    public String getInstanceName() { return "vpnservice-default"; }


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
//                wrappedBundle(
//                        mavenBundle("org.opendaylight.netvirt", "utils.config")
//                                .version(asInProject())
//                                .type("jar")),
                configureConsole().startLocalConsole(),
                vmOption("-javaagent:../jars/org.jacoco.agent.jar=destfile=../../jacoco-it.exec"),
                keepRuntimeFolder()
        };
    }

    @Override
    public Option getLoggingOption() {
        return composite(
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        logConfiguration(NetvirtIT.class),
                        LogLevel.INFO.name()),
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.netvirt",
                        LogLevel.DEBUG.name()),
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.openflowjava.protocol.impl.util.ListDeserializer",
                        LogLevel.ERROR.name()),
                editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                        "log4j.logger.org.opendaylight.controller.configpusherfeature.internal.FeatureConfigPusher",
                        LogLevel.ERROR.name()),
//                editConfigurationFilePut(NetvirtITConstants.ORG_OPS4J_PAX_LOGGING_CFG,
//                        "log4j.logger.org.opendaylight.ovsdb",
//                        LogLevel.TRACE.name()),
                super.getLoggingOption());
    }

    @Before
    @Override
    public void setup() throws InterruptedException {
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

        Thread.sleep(10*1000);
        getProperties();

        // get the dataBroker
        dataBroker = VpnserviceProvider.getDataBroker();
        assertNotNull("dataBroker should not be null", dataBroker);
        itUtils = new OvsdbItUtils(dataBroker);
        mdsalUtils = new MdsalUtils(dataBroker);
        assertNotNull("mdsalUtils should not be null", mdsalUtils);
        southboundUtils = new SouthboundUtils(mdsalUtils);
        assertTrue("Did not find " + NETVIRT_TOPOLOGY_ID, this.getNetvirtTopology());

//      TODO: need to implement new pipelineOrchestrator for the vpnservice pipeline

        setup.set(true);
    }

    private void getProperties() {
        Properties props = System.getProperties();
        addressStr = props.getProperty(NetvirtITConstants.SERVER_IPADDRESS);
        portStr = props.getProperty(NetvirtITConstants.SERVER_PORT, NetvirtITConstants.DEFAULT_SERVER_PORT);
        connectionType = props.getProperty(NetvirtITConstants.CONNECTION_TYPE, "active");
        controllerStr = props.getProperty(NetvirtITConstants.CONTROLLER_IPADDRESS, "0.0.0.0");
        String userSpaceEnabled = props.getProperty(NetvirtITConstants.USERSPACE_ENABLED, "no");
        LOG.info("setUp: Using the following properties: mode= {}, ip:port= {}:{}, controller ip: {}, " +
                        "userspace.enabled: {}",
                connectionType, addressStr, portStr, controllerStr, userSpaceEnabled);
    }

    private Boolean getNetvirtTopology() {
        LOG.info("getNetvirtTopology: looking for {}...", NETVIRT_TOPOLOGY_ID);
        Boolean found = false;
        TopologyId topologyId = new TopologyId(NETVIRT_TOPOLOGY_ID);
        InstanceIdentifier<Topology> path =
                InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(topologyId));
        for(int i = 0; i < 60; ++i) {
            Topology topology = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, path);
            if(topology != null) {
                LOG.info("getNetvirtTopology: found {}...", NETVIRT_TOPOLOGY_ID);
                found = Boolean.valueOf(true);
                break;
            }

            LOG.info("getNetvirtTopology: still looking ({})...", i);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException var7) {
                LOG.warn("Interrupted while waiting for {}", "netvirt:1", var7);
            }
        }

        return found;
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
     * @throws InterruptedException
     */
    @Test
    public void testNetVirt() throws InterruptedException {
        try(DockerOvs ovs = new DockerOvs()) {
            ConnectionInfo connectionInfo = SouthboundUtils.
                    getConnectionInfo(ovs.getOvsdbAddress(0), ovs.getOvsdbPort(0));
            NodeInfo nodeInfo = itUtils.createNodeInfo(connectionInfo, null);
            nodeInfo.connect();

            LOG.info("testNetVirt: should be connected: {}", nodeInfo.ovsdbNode.getNodeId());

            southboundUtils.addTerminationPoint(nodeInfo.bridgeNode, NetvirtITConstants.PORT_NAME,
                    "internal", null, null, 0L);
            Thread.sleep(1000);

            OvsdbTerminationPointAugmentation terminationPointOfBridge = southboundUtils.
                    getTerminationPointOfBridge(nodeInfo.bridgeNode, NetvirtITConstants.PORT_NAME);
            assertNotNull("Did not find " + NetvirtITConstants.PORT_NAME, terminationPointOfBridge);

            nodeInfo.disconnect();
        } catch (Exception e) {
            LOG.error("testNetVirt: Exception thrown by OvsDocker.OvsDocker()", e);
            fail();
        }
    }


    /**
     * Test a basic neutron use case. This test constructs a Neutron network, subnet, and two "vm" ports
     * and validates that the correct flows are installed on OVS. Then it pings from one VM port to the other.
     * @throws InterruptedException if we're interrupted while waiting for some mdsal operation to complete
     */
    @Test
    public void testNeutronNet() throws InterruptedException {
        LOG.warn("testNeutronNet: starting test");
        try(DockerOvs ovs = new DockerOvs()) {
            ConnectionInfo connectionInfo = SouthboundUtils.
                    getConnectionInfo(ovs.getOvsdbAddress(0), ovs.getOvsdbPort(0));
            NodeInfo nodeInfo = itUtils.createNodeInfo(connectionInfo, null);
            nodeInfo.connect();

            // waiting for the default flows to be installed before adding the ports
            Thread.sleep(20*100);

            //create the neutron objects
            NetITUtil net = new NetITUtil(ovs, southboundUtils, mdsalUtils);
            net.createNetworkAndSubnet();
            String port1 = net.createPort(nodeInfo.bridgeNode);
            String port2 = net.createPort(nodeInfo.bridgeNode);

//          TODO - need to add pipeline validation

            Thread.sleep(1000);

            //ovs interface configuration for running the ping test
            net.preparePortForPing(port1);
            net.preparePortForPing(port2);

            //run the ping test
            net.ping(port1, port2);

            //clean the neutron object and disconnect from odl
            net.destroy();
            nodeInfo.disconnect();
        } catch (Exception e) {
            LOG.error("testNeutronNet: Exception thrown by OvsDocker.OvsDocker()", e);
            fail();
        }
    }
}
