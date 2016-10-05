/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.cloudservicechain;

import java.math.BigInteger;;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.jobs.AddVpnPseudoPortDataJob;
import org.opendaylight.netvirt.cloudservicechain.jobs.RemoveVpnPseudoPortDataJob;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.genius.mdsalutil.NWUtil;
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

    private final IMdsalApiManager mdsalManager;
    private final DataBroker broker;
    private final FibRpcService fibRpcService;

    /**
     * @param mdsalManager
     * @param db
     */
    public VPNServiceChainHandler(final DataBroker db, IMdsalApiManager mdsalManager, FibRpcService fibRpcSrv) {
        this.broker = db;
        this.mdsalManager = mdsalManager;
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
     * Programs the necessary flows in LFIB and LPortDispatcher table so that the packets coming from a
     * given VPN are delivered to a given ServiceChain Pipeline
     *
     * @param vpnName Name of the VPN. Typically the UUID
     * @param tableId Table to which the LPortDispatcher table sends the packet to (Uplink or Downlink Subsc table)
     * @param scfTag Scf tag to the SCF to which the Vpn is linked to.
     * @param lportTag VpnPseudo Port lportTag
     * @param addOrRemove States if the VPN2SCF Pipeline must be installed or removed
     */
    public void programVpnToScfPipeline(String vpnName, short tableId, long scfTag, int lportTag, int addOrRemove) {
        // This entries must be created in the DPN where the CGNAT is installed. Since it is not possible
        // to know where CGNAT is located, this entries are installed in all the VPN footprint.

        //   LFIB:
        //     - Match: cgnatLabel   Instr: lportTag=vpnPseudoPortTag + SI=SCF  +  GOTO 17
        //   LportDisp:
        //     - Match: vpnPseudoPortTag + SI==SCF   Instr:  scfTag  +  GOTO 70
        LOG.info("programVpnToScfPipeline ({}) : Parameters VpnName:{} tableId:{} scftag:{}  lportTag:{}",
                 addOrRemove == NwConstants.ADD_FLOW ? "Creation" : "Removal", vpnName, tableId, scfTag, lportTag);
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
            List<VrfEntry> vrfEntries = VpnServiceChainUtils.getAllVrfEntries(broker, rd);
            if (vrfEntries != null) {
                AddVpnPseudoPortDataJob updateVpnToPseudoPortTask =
                    new AddVpnPseudoPortDataJob(broker, rd, lportTag, tableId, (int) scfTag);
                DataStoreJobCoordinator.getInstance().enqueueJob(updateVpnToPseudoPortTask.getDsJobCoordinatorKey(),
                                                                 updateVpnToPseudoPortTask);

                for (VpnToDpnList dpnInVpn : vpnToDpnList) {
                    BigInteger dpnId = dpnInVpn.getDpnId();
                    programVpnToScfPipelineOnDpn(dpnId, vrfEntries, tableId, (int) scfTag, lportTag, addOrRemove);
                }
            }
        } catch (Exception ex) {
            LOG.error("Exception: programSCFinVPNPipeline", ex);
        }
    }

    public void programVpnToScfPipelineOnDpn(BigInteger dpnId, List<VrfEntry> vpnVrfEntries, short tableIdToGoTo,
                                             int scfTag, int lportTag, int addOrRemove) {
        VpnServiceChainUtils.programLFibEntriesForSCF(mdsalManager, dpnId, vpnVrfEntries, lportTag,
                                                      addOrRemove);

        VpnServiceChainUtils.programLPortDispatcherFlowForVpnToScf(mdsalManager, dpnId, lportTag, scfTag,
                                                                   tableIdToGoTo, addOrRemove);
    }

    /**
     * Get fake VPNPseudoPort interface name
     * @param dpId Dpn Id
     * @param scfTag Service Function tag
     * @param scsTag Service Chain tag
     * @param lportTag Lport tag
     * @return the fake VpnPseudoPort interface name
     */
    private String buildVpnPseudoPortIfName(Long dpId, long scfTag, int scsTag, int lportTag) {
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
    public void programScfToVpnPipeline(String vpnName, long scfTag, int servChainTag, long dpnId,
                                        int vpnPseudoLportTag, boolean isLastServiceChain, int addOrRemove) {
        // These Flows must be installed in the DPN where the last SF in the ServiceChain is located
        //   + ScForwardingTable (75):  (This one is created and maintained by ScHopManager)
        //       - Match:  scfTag + servChainId + lportTagOfvVSF    Instr: VpnPseudoPortTag + SI=L3VPN + GOTO LPortDisp
        // And these 2 flows must be installed in all Dpns where the Vpn is present:
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

                if ( addOrRemove == NwConstants.ADD_FLOW
                        || addOrRemove == NwConstants.DEL_FLOW && isLastServiceChain ) {

                    Long vpnId = vpnInstance.getVpnId();
                    List<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
                    if ( vpnToDpnList != null ) {
                        List<BigInteger> dpns = new ArrayList<>();
                        for (VpnToDpnList dpnInVpn : vpnToDpnList ) {
                            dpns.add(dpnInVpn.getDpnId());
                        }
                        if ( !dpns.contains(dpnId) ) {
                            LOG.debug("Dpn {} is not included in the current VPN Footprint", dpnId);
                            dpns.add(BigInteger.valueOf(dpnId));
                        }
                        for ( BigInteger dpn : dpns ) {
                            VpnServiceChainUtils.programLPortDispatcherFlowForScfToVpn(mdsalManager, vpnId, dpn,
                                    vpnPseudoLportTag, addOrRemove);
                        }
                    } else {
                        LOG.debug("Could not find VpnToDpn list for VPN {} with rd {}", vpnName, rd);
                    }
                }

                // We need to keep a fake VpnInterface in the DPN where the last vSF (before the VpnPseudoPort) is
                // located, because in case the last real VpnInterface is removed from that DPN, we still need
                // the Fib table programmed there
                String intfName = buildVpnPseudoPortIfName(dpnId, scfTag, servChainTag, vpnPseudoLportTag);
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

        List<BigInteger> operativeDPNs = NWUtil.getOperativeDPNs(broker);
        for (BigInteger dpnId : operativeDPNs) {
            if ( cleanLFib ) {
                VpnServiceChainUtils.programLFibEntriesForSCF(mdsalManager, dpnId, vrfEntries, vpnPseudoLportTag,
                                                              NwConstants.DEL_FLOW);
            }

            String vpnToScfflowRef = VpnServiceChainUtils.getL3VpnToScfLportDispatcherFlowRef(vpnPseudoLportTag);
            Flow vpnToScfFlow = new FlowBuilder().setTableId(NwConstants.LPORT_DISPATCHER_TABLE)
                                                 .setId(new FlowId(vpnToScfflowRef)).build();
            mdsalManager.removeFlow(dpnId, vpnToScfFlow);
            String scfToVpnFlowRef = VpnServiceChainUtils.getScfToL3VpnLportDispatcherFlowRef(vpnPseudoLportTag);
            Flow scfToVpnFlow = new FlowBuilder().setTableId(NwConstants.LPORT_DISPATCHER_TABLE)
                                                 .setId(new FlowId(scfToVpnFlowRef)).build();
            mdsalManager.removeFlow(dpnId, scfToVpnFlow);
        }

        RemoveVpnPseudoPortDataJob removeVpnPseudoPortDataTask = new RemoveVpnPseudoPortDataJob(broker, rd);
        DataStoreJobCoordinator.getInstance().enqueueJob(removeVpnPseudoPortDataTask.getDsJobCoordinatorKey(),
                                                         removeVpnPseudoPortDataTask);
    }

}
