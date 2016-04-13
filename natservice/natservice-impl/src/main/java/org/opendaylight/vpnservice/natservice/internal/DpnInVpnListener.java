/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.add.dpn.event.AddEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.RemoveDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.remove.dpn.event.RemoveEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.OdlL3vpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class DpnInVpnListener implements OdlL3vpnListener {
    private static final Logger LOG = LoggerFactory.getLogger(DpnInVpnListener.class);
    private DataBroker dataBroker;
    private SNATDefaultRouteProgrammer defaultRouteProgrammer;
    private NaptSwitchHA naptSwitchHA;
    private IMdsalApiManager mdsalManager;
    private IdManagerService idManager;

    public DpnInVpnListener(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    void setDefaultProgrammer(SNATDefaultRouteProgrammer defaultRouteProgrammer) {
        this.defaultRouteProgrammer = defaultRouteProgrammer;
    }

    void setNaptSwitchHA(NaptSwitchHA switchHA) {
        naptSwitchHA = switchHA;
    }

    void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    public void onAddDpnEvent(AddDpnEvent notification) {
        AddEventData eventData =  notification.getAddEventData();
        BigInteger dpnId = eventData.getDpnId();
        String vpnName = eventData.getVpnName();
        LOG.info("Received add dpn {} in vpn {} event", dpnId, vpnName);
        String  routerId = NatUtil.getRouterIdfromVpnId(dataBroker, vpnName);
        if (routerId != null) {
            //check router is associated to external network
            InstanceIdentifier<Routers> id = NatUtil.buildRouterIdentifier(routerId);
            Optional<Routers> routerData = NatUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (routerData.isPresent()) {
                Uuid networkId = routerData.get().getNetworkId();
                if(networkId != null) {
                    LOG.debug("Router {} is associated with ext nw {}", routerId, networkId);
                    long vpnId = NatUtil.readVpnId(dataBroker, vpnName);
                    if(vpnId != NatConstants.INVALID_ID) {
                        //Install default entry in FIB to SNAT table
                        LOG.debug("Installing default route in FIB on dpn {} for vpn {} ...", dpnId, vpnName);
                        defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, vpnId);
                    } else {
                        LOG.debug("Add DPN Event: Could not read vpnId for vpnName {}", vpnName);
                    }
                    if (routerData.get().isEnableSnat()) {
                        LOG.info("SNAT enabled for router {}", routerId);
                        handleSNATForDPN(dpnId, routerId);
                    } else {
                        LOG.info("SNAT is not enabled for router {} to handle addDPN event {}", routerId, dpnId);
                    }
                }
            }
        }
    }

    void handleSNATForDPN(BigInteger dpnId, String routerName) {
        //Check if primary and secondary switch are selected, If not select the role
        //Install select group to NAPT switch
        //Install default miss entry to NAPT switch
        BigInteger naptSwitch;
        try {
            Long routerId = NatUtil.getVpnId(dataBroker, routerName);
            if (routerId == NatConstants.INVALID_ID) {
                LOG.error("Invalid routerId returned for routerName {}",routerName);
                return;
            }
            BigInteger naptId = NatUtil.getPrimaryNaptfromRouterId(dataBroker, routerId);
            if (naptId == null) {
                LOG.debug("No Naptswitch is selected for router {}", routerName);

                naptSwitch = dpnId;
                boolean naptstatus = naptSwitchHA.updateNaptSwitch(routerName, naptSwitch);
                if(!naptstatus) {
                    LOG.error("Failed to update newNaptSwitch {} for routername {}",naptSwitch,routerName);
                    return;
                }
                LOG.debug("Switch {} is elected as NaptSwitch for router {}", dpnId, routerName);

                //installing group
                List<BucketInfo> bucketInfo = naptSwitchHA.handleGroupInPrimarySwitch();
                naptSwitchHA.installSnatGroupEntry(naptSwitch,bucketInfo,routerName);

            }  else {
                LOG.debug("Napt switch is already elected for router {}"
                        , naptId, routerName);
                naptSwitch = naptId;

                //installing group
                List<BucketInfo> bucketInfo = naptSwitchHA.handleGroupInNeighborSwitches(dpnId, routerName, naptSwitch);
                if (bucketInfo == null) {
                    return;
                }
                naptSwitchHA.installSnatGroupEntry(dpnId, bucketInfo, routerName);

            }
            // Install miss entry pointing to group
            long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(routerName), idManager);
            FlowEntity flowEntity = naptSwitchHA.buildSnatFlowEntity(dpnId, routerName, groupId,NatConstants.ADD_FLOW);
            if (flowEntity == null) {
                LOG.debug("Failed to populate flowentity for router {} with dpnId {} groupIs {}",routerName,dpnId,groupId);
                return;
            }
            LOG.debug("Sucessfully installed flow for dpnId {} router {} group {}",dpnId,routerName,groupId);
            mdsalManager.installFlow(flowEntity);
        } catch (Exception ex) {
            LOG.error("Exception in handleSNATForDPN method : {}",ex);
        }
    }

    public void onRemoveDpnEvent(RemoveDpnEvent notification) {
        RemoveEventData eventData = notification.getRemoveEventData();
        BigInteger dpnId = eventData.getDpnId();
        String vpnName = eventData.getVpnName();
        LOG.info("Received remove dpn {} in vpn {} event", dpnId, vpnName);
        String  routerId = NatUtil.getRouterIdfromVpnId(dataBroker, vpnName);
        if (routerId != null) {
            //check router is associated to external network
            InstanceIdentifier<Routers> id = NatUtil.buildRouterIdentifier(routerId);
            Optional<Routers> routerData = NatUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (routerData.isPresent()) {
                Uuid networkId = routerData.get().getNetworkId();
                if(networkId != null) {
                    LOG.debug("Router {} is associated with ext nw {}", routerId, networkId);
                    long vpnId = NatUtil.readVpnId(dataBroker, vpnName);
                    if(vpnId != NatConstants.INVALID_ID) {
                        //Remove default entry in FIB
                        LOG.debug("Removing default route in FIB on dpn {} for vpn {} ...", dpnId, vpnName);
                        defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, vpnId);
                    } else {
                        LOG.debug("Remove DPN Event: Could not read vpnId for vpnName {}", vpnName);
                    }
                    if (routerData.get().isEnableSnat()) {
                        LOG.info("SNAT enabled for router {}", routerId);
                        removeSNATFromDPN(dpnId,routerId);
                    } else {
                        LOG.info("SNAT is not enabled for router {} to handle removeDPN event {}", routerId, dpnId);
                    }
                }
            }
        }
    }

    void removeSNATFromDPN(BigInteger dpnId, String routerName) {
        //irrespective of naptswitch or non-naptswitch, SNAT default miss entry need to be removed
        //remove miss entry to NAPT switch
        long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(routerName), idManager);
        FlowEntity flowEntity = null;
        try {
            flowEntity = naptSwitchHA.buildSnatFlowEntity(dpnId, routerName, groupId, NatConstants.DEL_FLOW);
            if (flowEntity == null) {
                LOG.debug("Failed to populate flowentity for router {} with dpnId {} groupIs {}",routerName,dpnId,groupId);
                return;
            }
            LOG.debug("NAT Service : Removing default SNAT miss entry flow entity {}",flowEntity);
            mdsalManager.removeFlow(flowEntity);

        } catch (Exception ex) {
            LOG.debug("NAT Service : Failed to remove default SNAT miss entry flow entity {} : {}",flowEntity,ex);
            return;
        }
        LOG.debug("NAT Service : Removed default SNAT miss entry flow for dpnID {} with routername {}", dpnId, routerName);

        //remove group
        try {
            GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName,
                    GroupTypes.GroupAll, null);
            LOG.info("NAT Service : Removing NAPT GroupEntity:{}", groupEntity);
            mdsalManager.removeGroup(groupEntity);
        } catch (Exception ex) {
            LOG.debug("NAT Service : Failed to remove group entity {} : {}",flowEntity,ex);
            return;
        }
        LOG.debug("NAT Service : Removed default SNAT miss entry flow for dpnID {} with routername {}", dpnId, routerName);
    }
}