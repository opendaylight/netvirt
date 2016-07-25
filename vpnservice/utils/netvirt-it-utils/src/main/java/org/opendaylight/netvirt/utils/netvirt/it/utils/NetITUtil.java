package org.opendaylight.netvirt.utils.netvirt.it.utils;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Assert;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.ovsdb.it.utils.DockerOvs;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionV4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.portsecurity.rev150712.PortSecurityExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.portsecurity.rev150712.PortSecurityExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Created by oshvartz on 7/13/16.
 */
public class NetITUtil {


    private MdsalUtils mdsalUtils;
    private DockerOvs dockerOvs;
    private SouthboundUtils southboundUtils;

    private String tenantId;
    private String networkId;
    private String subnetId;

    public String macPfx = "f4:00:00:0f:00:";
    public String ipPfx = "10.0.0.";
    public String segId = "101";

    private static final int DEFAULT_WAIT = 30*1000;

    /**
     * Maps port names (the ones you pass in to createPort() to their PortInfo objects
     */
    public Map<String, PortInfo> portInfoByName = new HashMap<>();


    public NetITUtil(DockerOvs dockerOvs, SouthboundUtils southboundUtils, MdsalUtils mdsalUtils) {
        this.dockerOvs = dockerOvs;
        this.southboundUtils = southboundUtils;
        this.mdsalUtils = mdsalUtils;

        // generate ids
        tenantId = UUID.randomUUID().toString();
        networkId = UUID.randomUUID().toString();
        subnetId = UUID.randomUUID().toString();
    }

    /**
     *
     */
    public void destroy(){
        //clean ports
        for (PortInfo portInfo : portInfoByName.values()) {
            this.deletePort(portInfo.id);
        }
        portInfoByName.clear();

        //clean subnet
        this.deleteSubnet();

        //clean network
        this.deleteNetwork();
    }

    /**
     *
     */
    public void createNetworkAndSubnet(){
        this.createNetwork();
        this.createSubnet();
    }

    /**
     *
     */
    private void createNetwork(){
        NetworkProviderExtension networkProviderExtension = new NetworkProviderExtensionBuilder()
                .setNetworkType(NetworkTypeVxlan.class)
                .setSegmentationId(segId)
                .build();

        Network network = new NetworkBuilder()
                .setTenantId(new Uuid(tenantId))
                .setUuid(new Uuid(networkId))
                .setAdminStateUp(true)
                .setShared(false)
                .setStatus("ACTIVE")
                .setName("net1")
                .addAugmentation(NetworkProviderExtension.class, networkProviderExtension)
                .build();


        // write the object into mdsal
        mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class).
                child(Networks.class).child(Network.class, network.getKey()), network);
    }

    /**
     *
     */
    private void deleteNetwork(){
        if (networkId == null){
            return;
        }

        //create the network object
        Network network = new NetworkBuilder()
                .setUuid(new Uuid(networkId))
                .build();

        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class).
                child(Networks.class).child(Network.class, network.getKey()));

        networkId = null;
    }

    /**
     *
     */
    private void createSubnet(){
        String cidr = ipPfx + "0/24";
        Subnet subnet = new SubnetBuilder()
                .setName("subnet1")
                .setTenantId(new Uuid(tenantId))
                .setUuid(new Uuid(subnetId))
                .setNetworkId(new Uuid(networkId))
                .setCidr(new IpPrefix(cidr.toCharArray()))
                .setGatewayIp(new IpAddress(new Ipv4Address(ipPfx+"254")))
                .setIpVersion(IpVersionV4.class)
                .setEnableDhcp(true)
                .build();

        // write the object into mdsal
        mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class).
                child(Subnets.class).child(Subnet.class, subnet.getKey()), subnet);
    }

    /**
     *
     */
    private void deleteSubnet(){
        if (subnetId == null){
            return;
        }

        //create the subnet object
        Subnet subnet = new SubnetBuilder()
                .setUuid(new Uuid(subnetId))
                .build();

        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class).
                child(Subnets.class).child(Subnet.class, subnet.getKey()));

        subnetId = null;
    }

    /**
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public String createPort(Node node)
            throws IOException, InterruptedException {
        return this.createPort(node, "compute:None");
    }

    /**
     *
     * @param owner
     * @throws IOException
     * @throws InterruptedException
     */
    public String createPort(Node node, String owner)
            throws IOException, InterruptedException {
        PortInfo portInfo = buildPortInfo();

        // set docker interfaces
        if (!dockerOvs.usingExternalDocker()) {
            dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "tuntap", "add", portInfo.name, "mode", "tap");
            dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "link", "set", "dev", portInfo.name, "address", portInfo.mac);
        }

        //add neutron port to mdsal
        this.doCreatePort(portInfo, owner);

        //add port to ovs
        this.addTerminationPoint(portInfo, node, "internal");

        //add port to map
        portInfoByName.put(portInfo.name, portInfo);

        //DEBUG
        dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "a");

        return portInfo.name;
    }


    /**
     * This method must be run on a port before calling ping() or pingIp()
     * @param portName The name of the port used when it was created using createPort()
     * @throws IOException if an IO error occurs with one of the spawned procs
     * @throws InterruptedException because we sleep
     */
    public void preparePortForPing(String portName) throws IOException, InterruptedException {
        if (dockerOvs.usingExternalDocker()) {
            return;
        }

        String nsName = "ns-" + portName;

        PortInfo portInfo = portInfoByName.get(portName);
        Assert.assertNotNull(portInfo);
        dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "netns", "add", nsName);
        dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "link", "set", portName, "netns", nsName);
        dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "netns", "exec", nsName, "ip", "addr",
                "add", "dev", portName, portInfo.ip + "/24");
        dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "netns", "exec", nsName, "ip", "link",
                "set", "dev", portName, "up");
        dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "netns", "exec", nsName, "ip", "route",
                "add", "default", "via", portInfo.ip);

        //DEBUG
        dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "netns", "exec", nsName, "ip", "a");
    }

    /**
     * Ping from one port to the other
     * @param fromPort name of the port to ping from. This is the name you used for createPort.
     * @param toPort name of the port to ping to. This is the name you used for createPort.
     * @throws IOException if an IO error occurs with one of the spawned procs
     * @throws InterruptedException because we sleep
     */
    public void ping(String fromPort, String toPort) throws IOException, InterruptedException {
        if (dockerOvs.usingExternalDocker()) {
            return;
        }

        PortInfo portInfo = portInfoByName.get(toPort);
        Assert.assertNotNull(portInfo);
        pingIp(fromPort, portInfo.ip);
    }

    /**
     * Ping from one port to an IP address
     * @param fromPort name of the port to ping from. This is the name you used for createPort.
     * @param ip The IP address to ping
     * @throws IOException if an IO error occurs with one of the spawned procs
     * @throws InterruptedException because we sleep
     */
    public void pingIp(String fromPort, String ip) throws IOException, InterruptedException {
        if (dockerOvs.usingExternalDocker()) {
            return;
        }

        String fromNs = "ns-" + fromPort;
        dockerOvs.runInContainer(DEFAULT_WAIT, 0, "ip", "netns", "exec", fromNs, "ping", "-c", "4", ip);
    }

    /**
     *
     * @param portInfo
     * @param bridge
     * @param portType
     */
    private void addTerminationPoint(PortInfo portInfo, Node bridge, String portType){
        Map<String, String> externalIds = Maps.newHashMap();
        externalIds.put("attached-mac", portInfo.mac);
        externalIds.put("iface-id", portInfo.id);
        southboundUtils.addTerminationPoint(bridge, portInfo.name, portType, null, externalIds, portInfo.ofPort);
    }

    /**
     *
     * @param portInfo
     * @param owner
     */
    private void doCreatePort(PortInfo portInfo, String owner){
        // fixed ips
        IpAddress ipv4 = new IpAddress(new Ipv4Address(portInfo.ip));
        FixedIpsBuilder fib = new FixedIpsBuilder();
        fib.setIpAddress(ipv4);
        fib.setSubnetId(new Uuid(subnetId));
        List<FixedIps> fixedIps = new ArrayList<>();
        fixedIps.add(fib.build());

        PortBindingExtensionBuilder portBindingExtensionBuilder = new PortBindingExtensionBuilder();
        portBindingExtensionBuilder.setVifType(NeutronConstants.VIF_TYPE_OVS);
        portBindingExtensionBuilder.setVnicType(NeutronConstants.VNIC_TYPE_NORMAL);

        // port security
        PortSecurityExtensionBuilder portSecurityBuilder = new PortSecurityExtensionBuilder();
        portSecurityBuilder.setPortSecurityEnabled(true);

        Port port = new PortBuilder()
                .addAugmentation(PortSecurityExtension.class, portSecurityBuilder.build())
                .addAugmentation(PortBindingExtension.class, portBindingExtensionBuilder.build())
                .setStatus("ACTIVE")
                .setAdminStateUp(true)
                .setName(portInfo.id)
                .setDeviceOwner(owner)
                .setUuid(new Uuid(portInfo.id))
                .setMacAddress(new MacAddress(portInfo.mac))
                .setNetworkId(new Uuid(networkId))
                .setFixedIps(fixedIps)
                .build();

        mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class).
                child(Ports.class).child(Port.class, port.getKey()), port);
    }

    /**
     *
     * @param uuid
     */
    private void deletePort(String  uuid){
        if(uuid == null){
            return;
        }

        //create the port object
        Port port = new PortBuilder()
                .setUuid(new Uuid(uuid))
                .build();

        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class).
                child(Ports.class).child(Port.class, port.getKey()));
    }

    /**
     *
     * @return
     */
    private PortInfo buildPortInfo() {
        long idx = portInfoByName.size() + 1;
        Assert.assertTrue(idx < 256);
        return new PortInfo(idx);
    }

    /**
     * Information about a port created using createPort() - fields should be pretty self explanatory
     */
    public class PortInfo {
        public PortInfo(long ofPort) {
            this.ofPort = ofPort;
            this.ip = ipFor(ofPort);
            this.mac = macFor(ofPort);
            this.id = UUID.randomUUID().toString();
            this.name = "tap" + id.substring(0,11);

        }

        public String id;
        public String name;
        public String ip;
        public String mac;
        public long ofPort;
    }

    /**
     * Get the mac address for the n'th port created on this network (starts at 1).
     * @param portNum index of port created
     * @return the mac address
     */
    public String macFor(long portNum) {
        return macPfx + String.format("%02x", 5 - portNum);
    }

    /**
     * Get the ip address for the n'th port created on this network (starts at 1).
     * @param portNum index of port created
     * @return the mac address
     */
    public String ipFor(long portNum) {
        return ipPfx + portNum;
    }


}
