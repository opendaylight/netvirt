/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.cloudservicechain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.globals.InterfaceServiceUtil;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.netvirt.cloudservicechain.jobs.AddVpnPseudoPortDataJob;
import org.opendaylight.netvirt.cloudservicechain.jobs.RemoveVpnPseudoPortDataJob;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.DpnOpElements;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.Vpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.VpnsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.Dpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg2;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VPNServiceChainHandler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VPNServiceChainHandler.class);
    private final IMdsalApiManager mdsalManager;
    private final DataBroker dataBroker;
    private final IVpnFootprintService vpnFootprintService;
    private final IInterfaceManager interfaceManager;
    private final JobCoordinator jobCoordinator;

    @Inject
    public VPNServiceChainHandler(final DataBroker db, final IMdsalApiManager mdsalManager,
                                  final IVpnFootprintService vpnFootprintService, final IInterfaceManager ifaceMgr,
                                  final JobCoordinator jobCoordinator) {
        this.dataBroker = db;
        this.mdsalManager = mdsalManager;
        this.vpnFootprintService = vpnFootprintService;
        this.interfaceManager = ifaceMgr;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
    public void init() {
    }

    @Override
    @PreDestroy
    public void close() {
    }

    /**
     * Getting the VpnInstance from RouteDistinguisher.
     *
     * @param rd the Route-Distinguisher
     * @return an Object that holds the Operational info of the VPN
     */
    protected VpnInstanceOpDataEntry getVpnInstance(String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.create(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
        return MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id).orElse(null);
    }

    protected Vpns getVpnInstanceFromDpnOpElements(String rd) {
        InstanceIdentifier<Vpns> id = InstanceIdentifier.create(DpnOpElements.class)
                .child(Vpns.class, new VpnsKey(rd));
        Optional<Vpns> vpnInstanceOpData = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    /**
     * Programs the necessary flows in LFIB and LPortDispatcher table so that
     * the packets coming from a given VPN are delivered to a given
     * ServiceChain Pipeline.
     *
     * @param vpnName Name of the VPN. Typically the UUID
     * @param tableId Table to which the LPortDispatcher table sends the packet
     *                 to (Uplink or Downlink Subsc table)
     * @param scfTag Scf tag to the SCF to which the Vpn is linked to.
     * @param lportTag VpnPseudo Port lportTag
     * @param addOrRemove States if the VPN2SCF Pipeline must be installed or
     *        removed
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
        String rd = VpnServiceChainUtils.getVpnRd(dataBroker, vpnName);
        LOG.debug("Router distinguisher (rd):{}", rd);
        if (rd == null || rd.isEmpty()) {
            LOG.warn("programVpnToScfPipeline: Could not find Router-distinguisher for VPN {}. No further actions",
                     vpnName);
            return;
        }
        VpnInstanceOpDataEntry vpnInstance = getVpnInstance(rd);
        Vpns vpnInstanceFromOpData = getVpnInstanceFromDpnOpElements(rd);
        if (vpnInstance == null) {
            LOG.warn("Could not find a suitable VpnInstance for Route-Distinguisher={}", rd);
            return;
        }

        // Find out the set of DPNs for the given VPN ID
        Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        Collection<Dpns> vpnToDpnFromDpnOpElements = vpnInstanceFromOpData.getDpns();
        List<VrfEntry> vrfEntries = VpnServiceChainUtils.getAllVrfEntries(dataBroker, rd);
        if (vrfEntries != null) {
            if (addOrRemove == NwConstants.ADD_FLOW) {
                AddVpnPseudoPortDataJob updateVpnToPseudoPortTask =
                        new AddVpnPseudoPortDataJob(dataBroker, rd, lportTag, tableId, (int) scfTag);
                jobCoordinator.enqueueJob(updateVpnToPseudoPortTask.getDsJobCoordinatorKey(),
                        updateVpnToPseudoPortTask);
            } else {
                RemoveVpnPseudoPortDataJob removeVpnPseudoPortDataTask = new RemoveVpnPseudoPortDataJob(dataBroker, rd);
                jobCoordinator.enqueueJob(removeVpnPseudoPortDataTask.getDsJobCoordinatorKey(),
                        removeVpnPseudoPortDataTask);
            }

            for (Dpns dpnInVpn : vpnToDpnFromDpnOpElements) {
                BigInteger dpnId = dpnInVpn.getDpnId();
                programVpnToScfPipelineOnDpn(dpnId, vrfEntries, tableId, (int) scfTag, lportTag, addOrRemove);

                if (dpnInVpn.getVpnInterfaces() != null) {
                    long vpnId = vpnInstance.getVpnId();
                    Flow flow = VpnServiceChainUtils.buildLPortDispFromScfToL3VpnFlow(vpnId, dpnId, lportTag,
                            NwConstants.ADD_FLOW);
                    if (addOrRemove == NwConstants.ADD_FLOW) {
                        mdsalManager.installFlow(dpnId, flow);
                    } else {
                        mdsalManager.removeFlow(dpnId, flow);
                    }
                    dpnInVpn.getVpnInterfaces().forEach(vpnIf -> {
                        if (addOrRemove == NwConstants.ADD_FLOW) {
                            bindScfOnVpnInterface(vpnIf.getInterfaceName(), (int) scfTag);
                        } else {
                            unbindScfOnVpnInterface(vpnIf.getInterfaceName());
                        }
                    });
                }
            }
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
     * L3VPN Service chaining: It moves traffic from a ServiceChain to a L3VPN.
     *
     * @param vpnName Vpn Instance Name. Typicall the UUID
     * @param scfTag ServiceChainForwarding Tag
     * @param servChainTag ServiceChain Tag
     * @param dpnId DpnId in which the egress pseudo logical port belongs
     * @param vpnPseudoLportTag VpnPseudo Logical port tag
     * @param isLastServiceChain Flag stating if there is no other ServiceChain
     *        using this VpnPseudoPort
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
        LOG.info("L3VPN: Service Chaining programScfToVpnPipeline [Started]: Parameters Vpn Name: {} ", vpnName);
        String rd = VpnServiceChainUtils.getVpnRd(dataBroker, vpnName);

        if (rd == null || rd.isEmpty()) {
            LOG.warn("programScfToVpnPipeline: Could not find Router-distinguisher for VPN {}. No further actions",
                     vpnName);
            return;
        }

        VpnInstanceOpDataEntry vpnInstance = getVpnInstance(rd);
        LOG.debug("programScfToVpnPipeline: rd={}, lportTag={} ", rd, vpnPseudoLportTag);
        // Find out the set of DPNs for the given VPN ID
        if (vpnInstance != null) {

            if (addOrRemove == NwConstants.ADD_FLOW
                   || addOrRemove == NwConstants.DEL_FLOW && isLastServiceChain) {

                Long vpnId = vpnInstance.getVpnId();
                List<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
                if (vpnToDpnList != null) {
                    List<BigInteger> dpns = new ArrayList<>();
                    for (VpnToDpnList dpnInVpn : vpnToDpnList) {
                        dpns.add(dpnInVpn.getDpnId());
                    }
                    if (!dpns.contains(BigInteger.valueOf(dpnId))) {
                        LOG.debug("Dpn {} is not included in the current VPN Footprint", dpnId);
                        dpns.add(BigInteger.valueOf(dpnId));
                    }
                    for (BigInteger dpn : dpns) {
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
            String intfName = VpnServiceChainUtils.buildVpnPseudoPortIfName(dpnId, scfTag, servChainTag,
                                                                            vpnPseudoLportTag);
            vpnFootprintService.updateVpnToDpnMapping(BigInteger.valueOf(dpnId), vpnName, rd, intfName,
                    null/*ipAddressSourceValuePair*/, addOrRemove == NwConstants.ADD_FLOW);
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

        String rd = VpnServiceChainUtils.getVpnRd(dataBroker, vpnInstanceName);
        List<VrfEntry> vrfEntries = null;
        if (rd != null) {
            vrfEntries = VpnServiceChainUtils.getAllVrfEntries(dataBroker, rd);
        }
        boolean cleanLFib = vrfEntries != null && !vrfEntries.isEmpty();

        List<BigInteger> operativeDPNs = NWUtil.getOperativeDPNs(dataBroker);
        for (BigInteger dpnId : operativeDPNs) {
            if (cleanLFib) {
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

        if (rd != null) {
            RemoveVpnPseudoPortDataJob removeVpnPseudoPortDataTask = new RemoveVpnPseudoPortDataJob(dataBroker, rd);
            jobCoordinator.enqueueJob(removeVpnPseudoPortDataTask.getDsJobCoordinatorKey(),
                    removeVpnPseudoPortDataTask);
        }
    }

    // TODO: Remove if [https://git.opendaylight.org/gerrit/#/c/51075/] lands on Genius
    private boolean isServiceBoundOnInterface(short servicePriority, String interfaceName) {
        InstanceIdentifier<BoundServices> boundServicesIId =
            VpnServiceChainUtils.buildBoundServicesIid(servicePriority, interfaceName);
        try {
            return SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                                                LogicalDatastoreType.CONFIGURATION, boundServicesIId)
                                              .isPresent();
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Error while reading [{}]", boundServicesIId, e);
            return false;
        }
    }


    public void bindScfOnVpnInterface(String ifName, int scfTag) {
        LOG.debug("bind SCF tag {} on iface {}", scfTag, ifName);
        if (isServiceBoundOnInterface(NwConstants.SCF_SERVICE_INDEX, ifName)) {
            LOG.info("SCF is already bound on Interface {} for Ingress. Binding aborted", ifName);
            return;
        }
        Action loadReg2Action = new ActionRegLoad(1, NxmNxReg2.class, 0, 31, scfTag).buildAction();
        List<Instruction> instructions =
            Arrays.asList(MDSALUtil.buildApplyActionsInstruction(Collections.singletonList(loadReg2Action)),
                          MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.SCF_DOWN_SUB_FILTER_TCP_BASED_TABLE,
                                                                    1 /*instructionKey, not sure why it is needed*/));
        BoundServices boundServices =
            InterfaceServiceUtil.getBoundServices(ifName, NwConstants.SCF_SERVICE_INDEX,
                                                  CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY,
                                                  CloudServiceChainConstants.COOKIE_SCF_BASE,
                                                  instructions);
        interfaceManager.bindService(ifName, ServiceModeIngress.class, boundServices);
    }

    public void unbindScfOnVpnInterface(String ifName) {
        BoundServices boundService =
            InterfaceServiceUtil.getBoundServices(ifName, NwConstants.SCF_SERVICE_INDEX,
                                                  CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY,
                                                  CloudServiceChainConstants.COOKIE_SCF_BASE,
                                                  /*instructions*/ Collections.emptyList());

        interfaceManager.unbindService(ifName, ServiceModeIngress.class, boundService);
    }

    public Optional<VpnToPseudoPortData> getScfInfoForVpn(String vpnName) throws ReadFailedException {
        String vpnRd = VpnServiceChainUtils.getVpnRd(dataBroker, vpnName);
        if (vpnRd == null) {
            LOG.trace("Checking if Vpn {} participates in SC. Could not find its RD", vpnName);
            return Optional.empty();
        }

        return VpnServiceChainUtils.getVpnPseudoPortData(dataBroker, vpnRd);
    }
}
