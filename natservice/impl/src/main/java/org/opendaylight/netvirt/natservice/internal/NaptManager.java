/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/*
 * Created eyugsar 2016/12/1
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalIpsCounter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.IntextIpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.IntextIpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProtocolTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.SnatintIpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.ExternalCounters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.ExternalCountersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.external.counters.ExternalIpCounter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.external.counters.ExternalIpCounterBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.external.counters.ExternalIpCounterKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.IpPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.IpPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolTypeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.ip.port.map.IpPortExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.ip.port.map.IpPortExternalBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.IntipPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.IntipPortMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.IpPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.IpPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.ip.port.IntIpProtoType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.ip.port.IntIpProtoTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.ip.port.IntIpProtoTypeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class NaptManager {
    private static final Logger LOG = LoggerFactory.getLogger(NaptManager.class);

    private static final long LOW_PORT = 49152L;
    private static final long HIGH_PORT = 65535L;

    private final DataBroker dataBroker;
    private final IdManagerService idManager;

    @Inject
    public NaptManager(final DataBroker dataBroker, final IdManagerService idManager) {
        this.dataBroker = dataBroker;
        this.idManager = idManager;
    }

    protected void createNaptPortPool(String poolName) {
        LOG.debug("createNaptPortPool : requested for : {}", poolName);
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(poolName)
            .setLow(LOW_PORT)
            .setHigh(HIGH_PORT)
            .build();
        try {
            Future<RpcResult<CreateIdPoolOutput>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("createNaptPortPool : Created PortPool :{}", poolName);
            } else {
                LOG.error("createNaptPortPool : Unable to create PortPool : {}", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("createNaptPortPool : Failed to create PortPool for NAPT Service", e);
        }
    }

    void removeNaptPortPool(String poolName) {
        DeleteIdPoolInput deleteIdPoolInput = new DeleteIdPoolInputBuilder().setPoolName(poolName).build();
        LOG.debug("removeNaptPortPool : Remove Napt port pool requested for : {}", poolName);
        try {
            Future<RpcResult<DeleteIdPoolOutput>> result = idManager.deleteIdPool(deleteIdPoolInput);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("removeNaptPortPool : Deleted PortPool {}", poolName);
            } else {
                LOG.error("removeNaptPortPool : Unable to delete PortPool {}", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("removeNaptPortPool : Failed to delete PortPool {} for NAPT Service", poolName, e);
        }
    }

    // 1. napt service functions

    /**
     * This method is used to inform this service of what external IP address to be used
     * as mapping when requested one for the internal IP address given in the input.
     *
     * @param segmentId – segmentation in which the mapping to be used. Eg; routerid
     * @param internal  subnet prefix or ip address
     * @param external  subnet prefix or ip address
     */

    public void registerMapping(long segmentId, IPAddress internal, IPAddress external) {
        LOG.debug("registerMapping : called with segmentid {}, internalIp {}, prefix {}, externalIp {} "
            + "and prefix {} ", segmentId, internal.getIpAddress(),
            internal.getPrefixLength(), external.getIpAddress(), external.getPrefixLength());
        // Create Pool per ExternalIp and not for all IPs in the subnet.
        // Create new Pools during getExternalAddressMapping if exhausted.
        String externalIpPool;
        // subnet case
        if (external.getPrefixLength() != 0 && external.getPrefixLength() != NatConstants.DEFAULT_PREFIX) {
            String externalSubnet = external.getIpAddress() + "/" + external.getPrefixLength();
            LOG.debug("registerMapping : externalSubnet is : {}", externalSubnet);
            SubnetUtils subnetUtils = new SubnetUtils(externalSubnet);
            SubnetInfo subnetInfo = subnetUtils.getInfo();
            externalIpPool = subnetInfo.getLowAddress();
        } else {  // ip case
            externalIpPool = external.getIpAddress();
        }
        createNaptPortPool(externalIpPool);

        // Store the ip to ip map in Operational DS
        String internalIp = internal.getIpAddress();
        if (internal.getPrefixLength() != 0) {
            internalIp = internal.getIpAddress() + "/" + internal.getPrefixLength();
        }
        String externalIp = external.getIpAddress();
        if (external.getPrefixLength() != 0) {
            externalIp = external.getIpAddress() + "/" + external.getPrefixLength();
        }
        updateCounter(segmentId, externalIp, true);
        //update the actual ip-map
        IpMap ipm = new IpMapBuilder().withKey(new IpMapKey(internalIp)).setInternalIp(internalIp)
            .setExternalIp(externalIp).build();
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
            getIpMapIdentifier(segmentId, internalIp), ipm);
        LOG.debug("registerMapping : registerMapping exit after updating DS with internalIP {}, externalIP {}",
            internalIp, externalIp);
    }

    public void updateCounter(long segmentId, String externalIp, boolean isAdd) {
        short counter = 0;
        InstanceIdentifier<ExternalIpCounter> id = InstanceIdentifier.builder(ExternalIpsCounter.class)
            .child(ExternalCounters.class, new ExternalCountersKey(segmentId))
            .child(ExternalIpCounter.class, new ExternalIpCounterKey(externalIp)).build();
        Optional<ExternalIpCounter> externalIpCounter;
        try {
            externalIpCounter = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, id);
        } catch (ReadFailedException e) {
            LOG.warn("Failed to read external IP counter for external IP {}", externalIp);
            externalIpCounter = Optional.absent();
        }
        if (externalIpCounter.isPresent()) {
            counter = externalIpCounter.get().getCounter();
            if (isAdd) {
                counter++;
                LOG.debug("updateCounter : externalIp and counter after increment are {} and {}", externalIp, counter);
            } else {
                if (counter > 0) {
                    counter--;
                }
                LOG.debug("updateCounter : externalIp and counter after decrement are {} and {}", externalIp, counter);
            }

        } else if (isAdd) {
            counter = 1;
        }

        //update the new counter value for this externalIp
        ExternalIpCounter externalIpCounterData = new ExternalIpCounterBuilder()
            .withKey(new ExternalIpCounterKey(externalIp)).setExternalIp(externalIp).setCounter(counter).build();
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
            getExternalIpsIdentifier(segmentId, externalIp), externalIpCounterData);
    }

    /**
     * method to get external ip/port mapping when provided with internal ip/port pair
     * If already a mapping exist for the given input, then the existing mapping is returned
     * instead of overwriting with new ip/port pair.
     *
     * @param segmentId     - Router ID
     * @param sourceAddress - internal ip address/port pair
     * @param protocol      - TCP/UDP
     * @return external ip address/port
     */
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public SessionAddress getExternalAddressMapping(long segmentId, SessionAddress sourceAddress,
                                                    NAPTEntryEvent.Protocol protocol) {
        LOG.debug("getExternalAddressMapping : called with segmentId {}, internalIp {} and port {}",
            segmentId, sourceAddress.getIpAddress(), sourceAddress.getPortNumber());
        /*
         1. Get Internal IP, Port in IP:Port format
         2. Inside DB with routerId get the list of entries and check if it matches with existing IP:Port
         3. If True return SessionAddress of ExternalIp and Port
         4. Else check ip Map and Form the ExternalIp and Port and update DB and then return ExternalIp and Port
         */

        //SessionAddress externalIpPort = new SessionAddress();
        String internalIpPort = sourceAddress.getIpAddress() + ":" + sourceAddress.getPortNumber();

        // First check existing Port Map.
        SessionAddress existingIpPort = checkIpPortMap(segmentId, internalIpPort, protocol);
        if (existingIpPort != null) {
            // populate externalIpPort from IpPortMap and return
            LOG.debug("getExternalAddressMapping : successfully returning existingIpPort as {} and {}",
                existingIpPort.getIpAddress(), existingIpPort.getPortNumber());
            return existingIpPort;
        } else {
            // Now check in ip-map
            String externalIp = checkIpMap(segmentId, sourceAddress.getIpAddress());
            if (externalIp == null) {
                LOG.error("getExternalAddressMapping : Unexpected error, internal to external "
                    + "ip map does not exist");
                return null;
            } else {
                 /* Logic assuming internalIp is always ip and not subnet
                  * case 1: externalIp is ip
                  *        a) goto externalIp pool and getPort and return
                  *        b) else return error
                  * case 2: externalIp is subnet
                  *        a) Take first externalIp and goto that Pool and getPort
                  *             if port -> return
                  *             else Take second externalIp and create that Pool and getPort
                  *             if port ->return
                  *             else
                  *             Continue same with third externalIp till we exhaust subnet
                  *        b) Nothing worked return error
                  */
                SubnetUtils externalIpSubnet;
                List<String> allIps = new ArrayList<>();
                String subnetPrefix = "/" + String.valueOf(NatConstants.DEFAULT_PREFIX);
                boolean extSubnetFlag = false;
                if (!externalIp.contains(subnetPrefix)) {
                    extSubnetFlag = true;
                    externalIpSubnet = new SubnetUtils(externalIp);
                    allIps = Arrays.asList(externalIpSubnet.getInfo().getAllAddresses());
                    LOG.debug("getExternalAddressMapping : total count of externalIps available {}",
                        externalIpSubnet.getInfo().getAddressCount());
                } else {
                    LOG.debug("getExternalAddressMapping : getExternalAddress single ip case");
                    if (externalIp.contains(subnetPrefix)) {
                        //remove /32 what we got from checkIpMap
                        externalIp = externalIp.substring(0, externalIp.indexOf(subnetPrefix));
                    }
                    allIps.add(externalIp);
                }

                boolean nextExtIpFlag = false;
                for (String extIp : allIps) {
                    LOG.info("getExternalAddressMapping : Looping externalIPs with externalIP now as {}", extIp);
                    if (nextExtIpFlag) {
                        createNaptPortPool(extIp);
                        LOG.debug("getExternalAddressMapping : Created Pool for next Ext IP {}", extIp);
                    }
                    AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                        .setPoolName(extIp).setIdKey(internalIpPort)
                        .build();
                    try {
                        Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
                        RpcResult<AllocateIdOutput> rpcResult;
                        if (result != null && result.get().isSuccessful()) {
                            LOG.debug("getExternalAddressMapping : Got id from idManager");
                            rpcResult = result.get();
                        } else {
                            LOG.error("getExternalAddressMapping : getExternalAddressMapping, idManager could not "
                                + "allocate id retry if subnet");
                            if (!extSubnetFlag) {
                                LOG.error("getExternalAddressMapping : getExternalAddressMapping returning null "
                                        + "for single IP case, may be ports exhausted");
                                return null;
                            }
                            LOG.debug("getExternalAddressMapping : Could be ports exhausted case, "
                                    + "try with another externalIP if possible");
                            nextExtIpFlag = true;
                            continue;
                        }
                        int extPort = rpcResult.getResult().getIdValue().intValue();
                        // Write to ip-port-map before returning
                        IpPortExternalBuilder ipExt = new IpPortExternalBuilder();
                        IpPortExternal ipPortExt = ipExt.setIpAddress(extIp).setPortNum(extPort).build();
                        IpPortMap ipm = new IpPortMapBuilder().withKey(new IpPortMapKey(internalIpPort))
                            .setIpPortInternal(internalIpPort).setIpPortExternal(ipPortExt).build();
                        LOG.debug("getExternalAddressMapping : writing into ip-port-map with "
                            + "externalIP {} and port {}",
                            ipPortExt.getIpAddress(), ipPortExt.getPortNum());
                        try {
                            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                getIpPortMapIdentifier(segmentId, internalIpPort, protocol), ipm);
                        } catch (UncheckedExecutionException uee) {
                            LOG.error("getExternalAddressMapping : Failed to write into ip-port-map with exception",
                                uee);
                        }

                        // Write to snat-internal-ip-port-info
                        String internalIpAddress = sourceAddress.getIpAddress();
                        int ipPort = sourceAddress.getPortNumber();
                        ProtocolTypes protocolType = NatUtil.getProtocolType(protocol);
                        List<Integer> portList = new ArrayList<>(
                                NatUtil.getInternalIpPortListInfo(dataBroker, segmentId, internalIpAddress,
                                        protocolType));
                        portList.add(ipPort);

                        IntIpProtoTypeBuilder builder = new IntIpProtoTypeBuilder();
                        IntIpProtoType intIpProtocolType =
                            builder.withKey(new IntIpProtoTypeKey(protocolType)).setPorts(portList).build();
                        try {
                            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                NatUtil.buildSnatIntIpPortIdentifier(segmentId, internalIpAddress, protocolType),
                                intIpProtocolType);
                        } catch (Exception ex) {
                            LOG.error("getExternalAddressMapping : Failed to write into snat-internal-ip-port-info "
                                    + "with exception", ex);
                        }

                        SessionAddress externalIpPort = new SessionAddress(extIp, extPort);
                        LOG.debug("getExternalAddressMapping : successfully returning externalIP {} "
                            + "and port {}", externalIpPort.getIpAddress(), externalIpPort.getPortNumber());
                        return externalIpPort;
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.error("getExternalAddressMapping : Exception caught", e);
                        return null;
                    }
                } // end of for loop
            } // end of else ipmap present
        } // end of else check ipmap
        LOG.error("getExternalAddressMapping : Unable to handle external IP address and port mapping with segmentId {},"
                + "internalIp {} and internalPort {}", segmentId, sourceAddress.getIpAddress(),
                sourceAddress.getPortNumber());
        return null;
    }

    /**
     * Release the existing mapping of internal ip/port to external ip/port pair
     * if no mapping exist for given internal ip/port, it returns false.
     *
     * @param segmentId - Router ID
     * @param address   - Session Address
     * @param protocol  - TCP/UDP
     * @return true if mapping exist and the mapping is removed successfully
     */
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public boolean releaseAddressMapping(long segmentId, SessionAddress address, NAPTEntryEvent.Protocol protocol) {
        LOG.debug("releaseAddressMapping : called with segmentId {}, internalIP {}, port {}",
            segmentId, address.getIpAddress(), address.getPortNumber());
        // delete entry from IpPort Map and IP Map if exists
        String internalIpPort = address.getIpAddress() + ":" + address.getPortNumber();
        SessionAddress existingIpPort = checkIpPortMap(segmentId, internalIpPort, protocol);
        if (existingIpPort != null) {
            // delete the entry from IpPortMap DS
            try {
                removeFromIpPortMapDS(segmentId, internalIpPort, protocol);
            } catch (Exception e) {
                LOG.error("releaseAddressMapping : failed, Removal of ipportmap {} for "
                    + "router {} failed", internalIpPort, segmentId, e);
                return false;
            }
        } else {
            LOG.error("releaseAddressMapping : failed, segmentId {} and internalIpPort {} "
                + "not found in IpPortMap DS", segmentId, internalIpPort);
            return false;
        }
        String existingIp = checkIpMap(segmentId, address.getIpAddress());
        if (existingIp != null) {
            // delete the entry from IpMap DS
            try {
                removeFromIpMapDS(segmentId, address.getIpAddress());
            } catch (Exception e) {
                LOG.error("releaseAddressMapping : Removal of  ipmap {} for router {} failed",
                    address.getIpAddress(), segmentId, e);
                return false;
            }
            //delete the entry from snatIntIpportinfo
            try {
                removeFromSnatIpPortDS(segmentId, address.getIpAddress());
            } catch (Exception e) {
                LOG.error("releaseAddressMapping : failed, Removal of snatipportmap {} for "
                    + "router {} failed", address.getIpAddress(), segmentId, e);
                return false;
            }
        } else {
            LOG.error("releaseAddressMapping : failed, segmentId {} and internalIpPort {} "
                + "not found in IpMap DS", segmentId, internalIpPort);
            return false;
        }
        // Finally release port from idmanager
        removePortFromPool(internalIpPort, existingIpPort.getIpAddress());

        LOG.debug("releaseAddressMapping : Exited successfully for segmentId {} and internalIpPort {}",
                segmentId, internalIpPort);
        return true;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void releaseIpExtPortMapping(long segmentId, SessionAddress address, NAPTEntryEvent.Protocol protocol) {
        String internalIpPort = address.getIpAddress() + ":" + address.getPortNumber();
        SessionAddress existingIpPort = checkIpPortMap(segmentId, internalIpPort, protocol);
        if (existingIpPort != null) {
            // delete the entry from IpPortMap DS
            try {
                removeFromIpPortMapDS(segmentId, internalIpPort, protocol);
                // Finally release port from idmanager
                removePortFromPool(internalIpPort, existingIpPort.getIpAddress());
            } catch (Exception e) {
                LOG.error("releaseIpExtPortMapping : failed, Removal of ipportmap {} for "
                    + "router {} failed", internalIpPort, segmentId, e);
            }
        } else {
            LOG.error("releaseIpExtPortMapping : failed, segmentId {} and "
                + "internalIpPort {} not found in IpPortMap DS", segmentId, internalIpPort);
        }

        //delete the entry of port for InternalIp from snatIntIpportMappingDS
        try {
            removeSnatIntIpPortDS(segmentId, address, protocol);
        } catch (Exception e) {
            LOG.error("releaseSnatIpPortMapping : failed, Removal of snatipportmap {} for "
                + "router {} failed",address.getIpAddress(), segmentId, e);
        }
    }

    /**
     * Removes the internal ip to external ip mapping if present.
     *
     * @param segmentId - Router ID
     * @return true if successfully removed
     */
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public boolean removeMapping(long segmentId) {
        try {
            removeIpMappingForRouterID(segmentId);
            removeIpPortMappingForRouterID(segmentId);
            removeIntIpPortMappingForRouterID(segmentId);
        } catch (Exception e) {
            LOG.error("removeMapping : Removal of  IPMapping for router {} failed", segmentId, e);
            return false;
        }

        //TODO :  This is when router is deleted then cleanup the entries in tables, ports etc - Delete scenarios
        return false;
    }

    protected InstanceIdentifier<IpMap> getIpMapIdentifier(long segid, String internal) {
        return InstanceIdentifier.builder(IntextIpMap.class)
            .child(IpMapping.class, new IpMappingKey(segid))
            .child(IpMap.class, new IpMapKey(internal)).build();
    }

    protected InstanceIdentifier<ExternalIpCounter> getExternalIpsIdentifier(long segmentId, String external) {
        return InstanceIdentifier.builder(ExternalIpsCounter.class)
            .child(ExternalCounters.class, new ExternalCountersKey(segmentId))
            .child(ExternalIpCounter.class, new ExternalIpCounterKey(external)).build();
    }

    @Nonnull
    public static List<IpMap> getIpMapList(DataBroker broker, Long routerId) {
        InstanceIdentifier<IpMapping> id = getIpMapList(routerId);
        try {
            return SingleTransactionDataBroker.syncReadOptional(broker,
                    LogicalDatastoreType.OPERATIONAL, id).toJavaUtil().map(IpMapping::getIpMap).orElse(
                    Collections.emptyList());
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get Ip Map list for router id {}", routerId);
            return Collections.emptyList();
        }
    }

    protected static InstanceIdentifier<IpMapping> getIpMapList(long routerId) {
        return InstanceIdentifier.builder(
            IntextIpMap.class).child(IpMapping.class, new IpMappingKey(routerId)).build();
    }

    protected InstanceIdentifier<IpPortMap> getIpPortMapIdentifier(long segid, String internal,
                                                                   NAPTEntryEvent.Protocol protocol) {
        ProtocolTypes protocolType = NatUtil.getProtocolType(protocol);
        return InstanceIdentifier.builder(IntextIpPortMap.class)
            .child(IpPortMapping.class, new IpPortMappingKey(segid))
            .child(IntextIpProtocolType.class, new IntextIpProtocolTypeKey(protocolType))
            .child(IpPortMap.class, new IpPortMapKey(internal)).build();
    }

    private SessionAddress checkIpPortMap(long segmentId, String internalIpPort,
            NAPTEntryEvent.Protocol protocol) {
        LOG.debug("checkIpPortMap : called with segmentId {} and internalIpPort {}",
                segmentId, internalIpPort);
        ProtocolTypes protocolType = NatUtil.getProtocolType(protocol);
        // check if ip-port-map node is there
        InstanceIdentifierBuilder<IpPortMap> idBuilder =
                InstanceIdentifier.builder(IntextIpPortMap.class)
                .child(IpPortMapping.class, new IpPortMappingKey(segmentId))
                .child(IntextIpProtocolType.class, new IntextIpProtocolTypeKey(protocolType))
                .child(IpPortMap.class, new IpPortMapKey(internalIpPort));
        InstanceIdentifier<IpPortMap> id = idBuilder.build();
        Optional<IpPortMap> ipPortMapType;
        try {
            ipPortMapType = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.CONFIGURATION, id);
        } catch (ReadFailedException e) {
            ipPortMapType = Optional.absent();
        }
        if (ipPortMapType.isPresent()) {
            LOG.debug("checkIpPortMap : {}", ipPortMapType.get());
            SessionAddress externalIpPort = new SessionAddress(ipPortMapType.get().getIpPortExternal().getIpAddress(),
                    ipPortMapType.get().getIpPortExternal().getPortNum());
            LOG.debug("checkIpPortMap : returning successfully externalIP {} and port {}",
                    externalIpPort.getIpAddress(), externalIpPort.getPortNumber());
            return externalIpPort;
        }
        // return null if not found
        LOG.warn("checkIpPortMap : no-entry in checkIpPortMap, returning NULL [should be OK] for "
                + "segmentId {} and internalIPPort {}", segmentId, internalIpPort);
        return null;
    }

    protected String checkIpMap(long segmentId, String internalIp) {
        LOG.debug("checkIpMap : called with segmentId {} and internalIp {}", segmentId, internalIp);
        String externalIp;
        // check if ip-map node is there
        InstanceIdentifierBuilder<IpMapping> idBuilder =
            InstanceIdentifier.builder(IntextIpMap.class).child(IpMapping.class, new IpMappingKey(segmentId));
        InstanceIdentifier<IpMapping> id = idBuilder.build();
        Optional<IpMapping> ipMapping;
        try {
            ipMapping = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, id);
        } catch (ReadFailedException e) {
            ipMapping = Optional.absent();
        }
        if (ipMapping.isPresent()) {
            List<IpMap> ipMaps = ipMapping.get().getIpMap();
            for (IpMap ipMap : ipMaps) {
                if (ipMap.getInternalIp().equals(internalIp)) {
                    LOG.debug("checkIpMap : IpMap : {}", ipMap);
                    externalIp = ipMap.getExternalIp();
                    LOG.debug("checkIpMap : successfully returning externalIp {}", externalIp);
                    return externalIp;
                } else if (ipMap.getInternalIp().contains("/")) { // subnet case
                    SubnetUtils subnetUtils = new SubnetUtils(ipMap.getInternalIp());
                    SubnetInfo subnetInfo = subnetUtils.getInfo();
                    if (subnetInfo.isInRange(internalIp)) {
                        LOG.debug("checkIpMap : internalIp {} found to be IpMap of internalIpSubnet {}",
                            internalIp, ipMap.getInternalIp());
                        externalIp = ipMap.getExternalIp();
                        LOG.debug("checkIpMap : checkIpMap successfully returning externalIp {}", externalIp);
                        return externalIp;
                    }
                }
            }
        }
        // return null if not found
        LOG.error("checkIpMap : failed, returning NULL for segmentId {} and internalIp {}",
            segmentId, internalIp);
        return null;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void removeSnatIntIpPortDS(long segmentId, SessionAddress address, NAPTEntryEvent.Protocol protocol) {
        LOG.trace("removeSnatIntIpPortDS : method called for IntIpport {} of router {} ",
            address, segmentId);
        ProtocolTypes protocolType = NatUtil.getProtocolType(protocol);
        List<Integer> portList =
            NatUtil.getInternalIpPortListInfo(dataBroker, segmentId, address.getIpAddress(), protocolType);
        if (portList.isEmpty() || !portList.contains(address.getPortNumber())) {
            LOG.error("removeSnatIntIpPortDS : Internal IP {} for port {} entry not found in SnatIntIpPort DS",
                address.getIpAddress(), address.getPortNumber());
            return;
        }
        LOG.trace("removeSnatIntIpPortDS : PortList {} retrieved for InternalIp {} of router {}",
            portList, address.getIpAddress(), segmentId);
        Integer port = address.getPortNumber();
        portList.remove(port);

        IntIpProtoTypeBuilder builder = new IntIpProtoTypeBuilder();
        IntIpProtoType intIpProtocolType =
            builder.withKey(new IntIpProtoTypeKey(protocolType)).setPorts(portList).build();
        try {
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                NatUtil.buildSnatIntIpPortIdentifier(segmentId, address.getIpAddress(), protocolType),
                intIpProtocolType);
        } catch (Exception ex) {
            LOG.error("removeSnatIntIpPortDS : Failed to write into snat-internal-ip-port-info with exception", ex);
        }
        LOG.debug("removeSnatIntIpPortDS : Removing SnatIp {} Port {} of router {} from SNATIntIpport datastore",
            address.getIpAddress(), address.getPortNumber(), segmentId);
    }

    protected void removeFromSnatIpPortDS(long segmentId, String internalIp) {
        InstanceIdentifier<IpPort> intIp = InstanceIdentifier.builder(SnatintIpPortMap.class)
            .child(IntipPortMap.class, new IntipPortMapKey(segmentId))
            .child(IpPort.class, new IpPortKey(internalIp)).build();
        // remove from SnatIpPortDS
        LOG.debug("removeFromSnatIpPortDS : Removing SnatIpPort from datastore : {}", intIp);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, intIp);
    }

    protected void removeFromIpPortMapDS(long segmentId, String internalIpPort, NAPTEntryEvent.Protocol protocol) {
        ProtocolTypes protocolType = NatUtil.getProtocolType(protocol);
        removeFromIpPortMapDS(segmentId, internalIpPort, protocolType);
    }

    protected void removeFromIpPortMapDS(long segmentId, String internalIpPort, ProtocolTypes protocolType) {
        InstanceIdentifierBuilder<IpPortMap> idBuilder = InstanceIdentifier.builder(IntextIpPortMap.class)
            .child(IpPortMapping.class, new IpPortMappingKey(segmentId))
            .child(IntextIpProtocolType.class, new IntextIpProtocolTypeKey(protocolType))
            .child(IpPortMap.class, new IpPortMapKey(internalIpPort));
        InstanceIdentifier<IpPortMap> id = idBuilder.build();
        // remove from ipportmap DS
        LOG.debug("removeFromIpPortMapDS : Removing ipportmap from datastore : {}", id);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
    }

    protected void removeFromIpMapDS(long segmentId, String internalIp) {
        InstanceIdentifierBuilder<IpMap> idBuilder = InstanceIdentifier.builder(IntextIpMap.class)
            .child(IpMapping.class, new IpMappingKey(segmentId))
            .child(IpMap.class, new IpMapKey(internalIp));
        InstanceIdentifier<IpMap> id = idBuilder.build();
        // Get externalIp and decrement the counter
        String externalIp = null;
        Optional<IpMap> ipMap;
        try {
            ipMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.OPERATIONAL, id);
        } catch (ReadFailedException e) {
            ipMap = Optional.absent();
        }
        if (ipMap.isPresent()) {
            externalIp = ipMap.get().getExternalIp();
            LOG.debug("removeFromIpMapDS : externalIP is {}", externalIp);
        } else {
            LOG.warn("removeFromIpMapDS : ipMap not present for the internal IP {}", internalIp);
        }

        if (externalIp != null) {
            updateCounter(segmentId, externalIp, false);
            // remove from ipmap DS
            LOG.debug("removeFromIpMapDS : Removing ipmap from datastore");
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        } else {
            LOG.warn("removeFromIpMapDS : externalIp not present for the internal IP {}", internalIp);
        }
    }

    protected void removeIntExtIpMapDS(long segmentId, String internalIp) {
        InstanceIdentifierBuilder<IpMap> idBuilder = InstanceIdentifier.builder(IntextIpMap.class)
            .child(IpMapping.class, new IpMappingKey(segmentId))
            .child(IpMap.class, new IpMapKey(internalIp));
        InstanceIdentifier<IpMap> id = idBuilder.build();

        LOG.debug("removeIntExtIpMapDS : Removing ipmap from datastore");
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
    }

    protected String getExternalIpAllocatedForSubnet(long segmentId, String internalIp) {
        InstanceIdentifierBuilder<IpMap> idBuilder = InstanceIdentifier.builder(IntextIpMap.class)
            .child(IpMapping.class, new IpMappingKey(segmentId))
            .child(IpMap.class, new IpMapKey(internalIp));
        InstanceIdentifier<IpMap> id = idBuilder.build();
        Optional<IpMap> ipMap;
        try {
            ipMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.OPERATIONAL, id);
        } catch (ReadFailedException e) {
            LOG.error("Failed to read External Ip Allocated For Subnet {}", internalIp);
            ipMap = Optional.absent();
        }
        if (ipMap.isPresent()) {
            return ipMap.get().getExternalIp();
        }
        return null;
    }

    private void removeIpMappingForRouterID(long segmentId) {
        InstanceIdentifierBuilder<IpMapping> idBuilder = InstanceIdentifier.builder(IntextIpMap.class)
            .child(IpMapping.class, new IpMappingKey(segmentId));
        InstanceIdentifier<IpMapping> id = idBuilder.build();
        // Get all externalIps and decrement their counters before deleting the ipmap
        String externalIp = null;
        Optional<IpMapping> ipMapping;
        try {
            ipMapping = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.OPERATIONAL, id);
        } catch (ReadFailedException e) {
            LOG.warn("Failed to read IP mapping for segment {}", segmentId);
            ipMapping = Optional.absent();
        }
        if (ipMapping.isPresent()) {
            List<IpMap> ipMaps = ipMapping.get().getIpMap();
            for (IpMap ipMap : ipMaps) {
                externalIp = ipMap.getExternalIp();
                LOG.debug("removeIpMappingForRouterID : externalIP is {}", externalIp);
                if (externalIp != null) {
                    updateCounter(segmentId, externalIp, false);
                }
            }
            // remove from ipmap DS
            LOG.debug("removeIpMappingForRouterID : Removing Ipmap for router {} from datastore", segmentId);
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        }
    }

    void removeIpPortMappingForRouterID(long segmentId) {
        InstanceIdentifier<IpPortMapping> idBuilder = InstanceIdentifier.builder(IntextIpPortMap.class)
            .child(IpPortMapping.class, new IpPortMappingKey(segmentId)).build();
        Optional<IpPortMapping> ipPortMapping;
        try {
            ipPortMapping = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, idBuilder);
        } catch (ReadFailedException e) {
            LOG.warn("Failed to read IP port mapping for router id {}", segmentId);
            ipPortMapping = Optional.absent();
        }
        if (ipPortMapping.isPresent()) {
            // remove from IntExtIpPortmap DS
            LOG.debug("removeIpPortMappingForRouterID : Removing IntExtIpPort map for router {} from datastore",
                    segmentId);
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, idBuilder);
        }
    }

    void removeIntIpPortMappingForRouterID(long segmentId) {
        InstanceIdentifier<IntipPortMap> intIp = InstanceIdentifier.builder(SnatintIpPortMap.class)
            .child(IntipPortMap.class, new IntipPortMapKey(segmentId)).build();
        Optional<IntipPortMap> intIpPortMap;
        try {
            intIpPortMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.CONFIGURATION, intIp);
        } catch (ReadFailedException e) {
            LOG.warn("Failed to read internal IP port mapping for router id {}", segmentId);
            intIpPortMap = Optional.absent();
        }
        if (intIpPortMap.isPresent()) {
            // remove from SnatIntIpPortmap DS
            LOG.debug("removeIntIpPortMappingForRouterID : Removing SnatIntIpPort from datastore : {}", intIp);
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, intIp);
        }
    }

    void removePortFromPool(String internalIpPort, String externalIp) {
        LOG.debug("removePortFromPool : method called");
        ReleaseIdInput idInput = new ReleaseIdInputBuilder()
            .setPoolName(externalIp)
            .setIdKey(internalIpPort).build();
        try {
            RpcResult<ReleaseIdOutput> rpcResult = idManager.releaseId(idInput).get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("removePortFromPool : idmanager failed to remove port from pool {}", rpcResult.getErrors());
            }
            LOG.debug("removePortFromPool : Removed port from pool for InternalIpPort {} with externalIp {}",
                internalIpPort, externalIp);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("removePortFromPool : idmanager failed when removing entry in pool with key {} with Exception",
                    internalIpPort, e);
        }
    }

    protected void initialiseExternalCounter(Routers routers, long routerId) {
        LOG.debug("initialiseExternalCounter : Initialise External IPs counter");
        List<ExternalIps> externalIps = routers.getExternalIps();

        //update the new counter value for this externalIp
        for (ExternalIps externalIp : externalIps) {
            String[] ipSplit = externalIp.getIpAddress().split("/");
            String extIp = ipSplit[0];
            String extPrefix = Short.toString(NatConstants.DEFAULT_PREFIX);
            if (ipSplit.length == 2) {
                extPrefix = ipSplit[1];
            }
            extIp = extIp + "/" + extPrefix;
            initialiseNewExternalIpCounter(routerId, extIp);
        }
    }

    protected void initialiseNewExternalIpCounter(long routerId, String externalIp) {
        ExternalIpCounter externalIpCounterData = new ExternalIpCounterBuilder()
            .withKey(new ExternalIpCounterKey(externalIp)).setExternalIp(externalIp).setCounter((short) 0).build();
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
            getExternalIpsIdentifier(routerId, externalIp), externalIpCounterData);
    }

    protected void removeExternalCounter(long routerId) {
        // Remove from external-counters model
        InstanceIdentifier<ExternalCounters> id = InstanceIdentifier.builder(ExternalIpsCounter.class)
            .child(ExternalCounters.class, new ExternalCountersKey(routerId)).build();
        LOG.debug("removeExternalCounter : Removing ExternalCounterd from datastore");
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
    }

    protected void removeExternalIpCounter(long routerId, String externalIp) {
        // Remove from external-counters model
        InstanceIdentifier<ExternalIpCounter> id = InstanceIdentifier.builder(ExternalIpsCounter.class)
            .child(ExternalCounters.class, new ExternalCountersKey(routerId))
            .child(ExternalIpCounter.class, new ExternalIpCounterKey(externalIp)).build();
        LOG.debug("removeExternalIpCounter : Removing ExternalIpsCounter from datastore");
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
    }
}
