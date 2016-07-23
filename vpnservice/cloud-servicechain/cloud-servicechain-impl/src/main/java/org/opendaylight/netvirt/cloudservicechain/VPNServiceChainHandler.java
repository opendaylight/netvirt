/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.cloudservicechain;

import java.math.BigInteger;;
import java.util.Collection;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class VPNServiceChainHandler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VPNServiceChainHandler.class);
    private IMdsalApiManager mdsalManager;
    private final DataBroker broker;
    private FibRpcService fibRpcService;

    /**
     * @param db
     */
    public VPNServiceChainHandler(final DataBroker db, FibRpcService fibRpcSrv) {
        this.broker = db;
        this.fibRpcService = fibRpcSrv;
    }


    /**
     * Method for injecting MdsalManager dependency
     *
     * @param mdsalManager  MDSAL Util API access
     */
    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    /**
     * Method for injecting the FibRpcService dependency
     *
     * @param fibRpcSrv FIB's RPC Service accessor
     */
    public void setFibRpcService(FibRpcService fibRpcSrv) {
        this.fibRpcService = fibRpcSrv;
    }

    /**
     * Get RouterDistinguisher by VpnName
     *
     * @param vpnName Name of the VPN Instance
     * @return the Route-Distinguisher
     */

    private String getRouteDistinguisher(String vpnName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> vpnInstance = MDSALDataStoreUtils.read(broker, LogicalDatastoreType.CONFIGURATION, id);
        String rd = "";
        if (vpnInstance.isPresent()) {
            VpnInstance instance = vpnInstance.get();
            VpnAfConfig config = instance.getIpv4Family();
            rd = config.getRouteDistinguisher();
        }
        return rd;
    }

    /**
     * Getting the VpnInstance from RouteDistinguisher
     *
     * @param rd the Route-Distinguisher
     * @return an Object that holds the Operational info of the VPN
     */
    protected VpnInstanceOpDataEntry getVpnInstance(String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.create(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = MDSALDataStoreUtils.read(broker,
                LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    /**
     * Returns the list of VrfEntries that belong to a VPN
     *
     * @param rd route distinguisher of the VPN
     * @return the list of {@code VrfEntry}
     */
    private List<VrfEntry> getVrfEntries(String rd) {
        try {
            InstanceIdentifier<VrfTables> id = VpnServiceChainUtils.buildVrfId(rd);
            Optional<VrfTables> vrfTable = MDSALDataStoreUtils.read(broker, LogicalDatastoreType.CONFIGURATION, id);
            if (vrfTable.isPresent()) {
                return vrfTable.get().getVrfEntry();
            }
        } catch (Exception e) {
            LOG.error("Exception: getVrfEntries", e);
        }
        return null;
    }

    /**
     * Programs the necessary flows in LFIB and LPortDispatcher table so that the packets coming from a
     * given VPN are delivered to a given ServiceChain Pipeline
     *
     * @param vpnName Name of the VPN. Typically the UUID
     * @param tableId Table to which the LPortDispatcher table sends the packet to (Uplink or Downlink Subsc table)
     * @param scfTag Scf tag to the SCF to which the Vpn is linked to.
     * @param lportTag VpnPseudo Port lportTag
     * @param addOrRemove States if the VPN2SCF Pipeline must be installed or removed
     */
    public void programVpnToScfPipeline(String vpnName, short tableId, int scfTag, int lportTag, int addOrRemove) {
        // This entries must be created in the DPN where the CGNAT is installed. Since it is not possible
        // to know where CGNAT is located, this entries are installed in all the VPN footprint.

        //   LFIB:
        //     - Match: cgnatLabel   Instr: lportTag=vpnPseudoPortTag + SI=SCF  +  GOTO 17
        //   LportDisp:
        //     - Match: vpnPseudoPortTag + SI==SCF   Instr:  scfTag  +  GOTO 70
        LOG.info("L3VPN: programSCFinVPNPipeline ({}) : Parameters VpnName:{} tableId:{} scftag:{}  lportTag:{}",
                 (addOrRemove == NwConstants.ADD_FLOW) ? "Creation" : "Removal", vpnName, tableId, scfTag, lportTag);
        VpnInstanceOpDataEntry vpnInstance = null;
        try {
            String rd = getRouteDistinguisher(vpnName);
            LOG.debug("Router distinguisher (rd):{}", rd);
            if (!rd.isEmpty()) {
                vpnInstance = getVpnInstance(rd);
            }
            if ( vpnInstance == null ) {
                LOG.warn("Could not find a suitable VpnInstance for Route-Distinguisher={}", rd);
                return;
            }

            // Find out the set of DPNs for the given VPN ID
            Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
            List<VrfEntry> vrfEntries = getVrfEntries(rd);
            if (vrfEntries != null) {
                VpnServiceChainUtils.updateVpnToLportTagMap(broker, rd, lportTag, addOrRemove);

                for (VpnToDpnList dpnInVpn : vpnToDpnList) {
                    BigInteger dpnId = dpnInVpn.getDpnId();
                    VpnServiceChainUtils.programLFibEntriesForSCF(mdsalManager, dpnId, vrfEntries, lportTag,
                                                                  addOrRemove);

                    VpnServiceChainUtils.programLPortDispatcherFlowForVpnToScf(mdsalManager, dpnId, lportTag, scfTag,
                                                                               tableId, addOrRemove);
                }
            }
        } catch (Exception ex) {
            LOG.error("Exception: programSCFinVPNPipeline", ex);
        }
    }


    /**
     * Get fake VPNPseudoPort interface name
     * @param dpId Dpn Id
     * @param scfTag Service Function tag
     * @param scsTag Service Chain tag
     * @param lportTag Lport tag
     * @return fake VpnPseudoPort interface name
     */
    private String getVpnPseudoPortIfName(Long dpId, int scfTag, int scsTag, int lportTag) {
        return new StringBuilder("VpnPseudo.").append(dpId).append(NwConstants.FLOWID_SEPARATOR)
                                              .append(lportTag).append(NwConstants.FLOWID_SEPARATOR)
                                              .append(scfTag).append(NwConstants.FLOWID_SEPARATOR)
                                              .append(scsTag).toString();
    }


    @Override
    public void close() throws Exception {
        // add
    }



    /**
     * L3VPN Service chaining: It moves traffic from a ServiceChain to a L3VPN
     *
     * @param vpnName Vpn Instance Name. Typicall the UUID
     * @param scfTag ServiceChainForwarding Tag
     * @param servChainTag ServiceChain Tag
     * @param dpnId DpnId in which the egress pseudo logical port belongs
     * @param vpnPseudoLportTag VpnPseudo Logical port tag
     * @param isLastServiceChain Flag stating if there is no other ServiceChain using this VpnPseudoPort
     * @param addOrRemove States if pipeline must be installed or removed
     */
    public void programScfToVpnPipeline(String vpnName, int scfTag, int servChainTag, long dpnId, int vpnPseudoLportTag,
                                        boolean isLastServiceChain, int addOrRemove) {
        // These Flows must be installed in the DPN where the last SF in the ServiceChain is located
        //   + ScForwardingTable (75):  (This one is created and maintained by ScHopManager)
        //       - Match:  scfTag + servChainId + lportTagOfvVSF    Instr: VpnPseudoPortTag + SI=L3VPN + GOTO LPortDisp
        //   + LPortDisp (17):
        //       - Match:  VpnPseudoPortTag + SI==L3VPN    Instr: setVpnTag + GOTO FIB
        //   + FIB (21): (one entry per VrfEntry, and it is maintained by FibManager)
        //       - Match:  vrfTag==vpnTag + eth_type=IPv4  + dst_ip   Instr:  Output DC-GW
        //
        LOG.info("L3VPN: Service Chaining programScfToVpnPipeline [Started]: Parameters Vpn Name:{} ", vpnName);
        VpnInstanceOpDataEntry vpnInstance = null;
        try {
            String rd = getRouteDistinguisher(vpnName);

            if (rd == null || rd.isEmpty()) {
                LOG.debug("Router distinguisher (rd): {} associated to vpnName {} does not exists", rd, vpnName);
                return;
            }

            vpnInstance = getVpnInstance(rd);
            LOG.debug("Router distinguisher (rd): {}, lportTag: {} ", rd, vpnPseudoLportTag);
            // Find out the set of DPNs for the given VPN ID
            if (vpnInstance != null) {
                Long vpnId = vpnInstance.getVpnId();
                BigInteger dpId = BigInteger.valueOf(dpnId);
                Flow flow = VpnServiceChainUtils.buildLPortDispFromScfToL3VpnFlow(vpnId, dpId, vpnPseudoLportTag,
                                                                                  addOrRemove);
                if ( addOrRemove == NwConstants.ADD_FLOW ) {
                    mdsalManager.installFlow(BigInteger.valueOf(dpnId), flow);
                } else {
                    // Only remove if it is the LastServiceChain using it.
                    if ( isLastServiceChain ) {
                        mdsalManager.removeFlow(BigInteger.valueOf(dpnId), flow);
                    }
                }

                // Update the Vpn footprint by adding or removing the fake interfaces
                String intfName = getVpnPseudoPortIfName(dpnId, scfTag, servChainTag, vpnPseudoLportTag);
                if (addOrRemove == NwConstants.ADD_FLOW ) {
                    // Including this DPN in the VPN footprint even if there is no VpnInterface here.
                    // This is needed so that FibManager maintains the FIB table in this DPN
                    VpnServiceChainUtils.updateMappingDbs(broker, fibRpcService, vpnInstance.getVpnId(),
                                                          BigInteger.valueOf(dpnId), intfName, vpnName);
                } else {
                    VpnServiceChainUtils.removeFromMappingDbs(broker, fibRpcService, vpnInstance.getVpnId(),
                                                              BigInteger.valueOf(dpnId), intfName, vpnName);
                }
            }
        } catch (Exception ex) {
            LOG.error("Exception: programScfToVpnPipeline", ex);
        }
        LOG.info("L3VPN: Service Chaining programScfToVpnPipeline [End]");
    }

    /**
     * Removes all Flows in LFIB and LPortDispatcher that are related to this VpnPseudoLPort.
     *
     * @param vpnInstanceName vpn Instance name
     * @param vpnPseudoLportTag vpnPseudoLPort tag
     */
    public void removeVpnPseudoPortFlows(String vpnInstanceName, int vpnPseudoLportTag) {
        // At VpnPseudoPort removal time the current Vpn footprint could not be enough, so let's try to
        // remove all possible entries in all DPNs.
        // TODO: Study how this could be enhanced. It could be done at ServiceChain removal, but that
        // could imply check all ServiceChains ending in all DPNs in Vpn footprint to decide that if the entries
        // can be removed, and that sounds even costlier than this.

        String rd = getRouteDistinguisher(vpnInstanceName);
        List<VrfEntry> vrfEntries = null;
        if ( rd != null ) {
            vrfEntries = VpnServiceChainUtils.getAllVrfEntries(broker, rd);
        }
        boolean cleanLFib = vrfEntries != null && !vrfEntries.isEmpty();

        List<BigInteger> operativeDPNs = VpnServiceChainUtils.getOperativeDPNs(broker);
        for (BigInteger dpnId : operativeDPNs) {
            if ( cleanLFib ) {
                VpnServiceChainUtils.programLFibEntriesForSCF(mdsalManager, dpnId, vrfEntries, vpnPseudoLportTag,
                                                              NwConstants.DEL_FLOW);
            }

            String vpnToScfflowRef = VpnServiceChainUtils.getL3VpnToScfLportDispatcherFlowRef(vpnPseudoLportTag);
            Flow vpnToScfFlow = new FlowBuilder().setTableId(CloudServiceChainConstants.LPORT_DISPATCHER_TABLE)
                    .setId(new FlowId(vpnToScfflowRef)).build();
            mdsalManager.removeFlow(dpnId, vpnToScfFlow);
            String scfToVpnFlowRef = VpnServiceChainUtils.getScfToL3VpnLportDispatcherFlowRef(vpnPseudoLportTag);
            Flow scfToVpnFlow = new FlowBuilder().setTableId(CloudServiceChainConstants.LPORT_DISPATCHER_TABLE)
                    .setId(new FlowId(scfToVpnFlowRef)).build();
            mdsalManager.removeFlow(dpnId, scfToVpnFlow);
        }

        VpnServiceChainUtils.updateVpnToLportTagMap(broker, rd, vpnPseudoLportTag, NwConstants.DEL_FLOW);
    }

}
