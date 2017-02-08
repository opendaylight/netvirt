/*
 * Copyright (c) 2013, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.opendaylight.netvirt.openstack.netvirt.api.Action;
import org.opendaylight.netvirt.openstack.netvirt.api.Constants;
import org.opendaylight.netvirt.openstack.netvirt.api.EventDispatcher;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityGroupCacheManger;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityServicesManager;
import org.opendaylight.netvirt.openstack.netvirt.api.Southbound;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronSecurityRuleAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSecurityGroupCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityGroup;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityRule;
import org.opendaylight.netvirt.openstack.netvirt.translator.Neutron_IPs;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronPortCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronSecurityGroupAware;
import org.opendaylight.netvirt.utils.servicehelper.ServiceHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle requests for OpenStack Neutron v2.0 Port Security API calls.
 */
public class PortSecurityHandler extends AbstractHandler
        implements INeutronSecurityGroupAware, INeutronSecurityRuleAware, ConfigInterface {

    private static final Logger LOG = LoggerFactory.getLogger(PortSecurityHandler.class);
    private volatile Southbound southbound;
    private volatile INeutronPortCRUD neutronPortCache;
    private volatile SecurityServicesManager securityServicesManager;
    private volatile SecurityGroupCacheManger securityGroupCacheManger;

    @Override
    public int canCreateNeutronSecurityGroup(NeutronSecurityGroup neutronSecurityGroup) {
        return HttpURLConnection.HTTP_CREATED;
    }

    @Override
    public void neutronSecurityGroupCreated(NeutronSecurityGroup neutronSecurityGroup) {
        int result = canCreateNeutronSecurityGroup(neutronSecurityGroup);
        if (result != HttpURLConnection.HTTP_CREATED) {
            LOG.debug("Neutron Security Group creation failed {} ", result);
        }
    }

    @Override
    public int canUpdateNeutronSecurityGroup(NeutronSecurityGroup delta, NeutronSecurityGroup original) {
        return HttpURLConnection.HTTP_OK;
    }

    @Override
    public void neutronSecurityGroupUpdated(NeutronSecurityGroup neutronSecurityGroup) {
        // Nothing to do
    }

    @Override
    public int canDeleteNeutronSecurityGroup(NeutronSecurityGroup neutronSecurityGroup) {
        return HttpURLConnection.HTTP_OK;
    }

    @Override
    public void neutronSecurityGroupDeleted(NeutronSecurityGroup neutronSecurityGroup) {
        //TODO: Trigger flowmod removals
        int result = canDeleteNeutronSecurityGroup(neutronSecurityGroup);
        if  (result != HttpURLConnection.HTTP_OK) {
            LOG.error(" delete Neutron Security Rule validation failed for result - {} ", result);
        }
    }

    /**
     * Invoked when a Security Rules creation is requested
     * to indicate if the specified Rule can be created.
     *
     * @param neutronSecurityRule  An instance of proposed new Neutron Security Rule object.
     * @return A HTTP status code to the creation request.
     */

    @Override
    public int canCreateNeutronSecurityRule(NeutronSecurityRule neutronSecurityRule) {
        return HttpURLConnection.HTTP_CREATED;
    }

    @Override
    public void neutronSecurityRuleCreated(NeutronSecurityRule neutronSecurityRule) {
        enqueueEvent(new NorthboundEvent(neutronSecurityRule, Action.ADD));
    }

    @Override
    public int canUpdateNeutronSecurityRule(NeutronSecurityRule delta, NeutronSecurityRule original) {
        return HttpURLConnection.HTTP_OK;
    }

    @Override
    public void neutronSecurityRuleUpdated(NeutronSecurityRule neutronSecurityRule) {
        // Nothing to do
    }

    @Override
    public int canDeleteNeutronSecurityRule(NeutronSecurityRule neutronSecurityRule) {
        return HttpURLConnection.HTTP_OK;
    }

    @Override
    public void neutronSecurityRuleDeleted(NeutronSecurityRule neutronSecurityRule) {
        enqueueEvent(new NorthboundEvent(neutronSecurityRule, Action.DELETE));
    }

    /**
     * Process the event.
     *
     * @param abstractEvent the {@link AbstractEvent} event to be handled.
     * @see EventDispatcher
     */
    @Override
    public void processEvent(AbstractEvent abstractEvent) {
        if (!(abstractEvent instanceof NorthboundEvent)) {
            LOG.error("Unable to process abstract event {}", abstractEvent);
            return;
        }
        NorthboundEvent ev = (NorthboundEvent) abstractEvent;
        switch (ev.getAction()) {
            case ADD:
                processNeutronSecurityRuleAdded(ev.getNeutronSecurityRule());
                break;
            case DELETE:
                processNeutronSecurityRuleDeleted(ev.getNeutronSecurityRule());
                break;
            default:
                LOG.warn("Unable to process event action {}", ev.getAction());
                break;
        }
    }

    private void processNeutronSecurityRuleAdded(NeutronSecurityRule neutronSecurityRule) {
        List<NeutronPort> portList = getPortWithSecurityGroup(neutronSecurityRule.getSecurityRuleGroupID());
        Map<String, NodeId> portNodeCache = getPortNodeCache();
        INeutronSecurityGroupCRUD groupCRUD =
                (INeutronSecurityGroupCRUD) ServiceHelper.getGlobalInstance(INeutronSecurityGroupCRUD.class, this);
        NeutronSecurityGroup securityGroup = groupCRUD.getNeutronSecurityGroup(neutronSecurityRule.getSecurityRuleGroupID());
        if (null != portNodeCache) {
            for (NeutronPort port:portList) {
                syncSecurityGroup(neutronSecurityRule, port, portNodeCache.get(port.getID()), securityGroup, true);
            }
        }

    }

    private void processNeutronSecurityRuleDeleted(NeutronSecurityRule neutronSecurityRule) {
        List<NeutronPort> portList = getPortWithSecurityGroup(neutronSecurityRule.getSecurityRuleGroupID());
        Map<String, NodeId> portNodeCache = getPortNodeCache();
        INeutronSecurityGroupCRUD groupCRUD =
                (INeutronSecurityGroupCRUD) ServiceHelper.getGlobalInstance(INeutronSecurityGroupCRUD.class, this);
        NeutronSecurityGroup securityGroup = groupCRUD.getNeutronSecurityGroup(neutronSecurityRule.getSecurityRuleGroupID());
        if (null != portNodeCache) {
            for (NeutronPort port:portList) {
                syncSecurityGroup(neutronSecurityRule, port, portNodeCache.get(port.getID()), securityGroup, false);
            }
        }
    }

    private void syncSecurityGroup(NeutronSecurityRule securityRule, NeutronPort port, NodeId nodeId,
                                   NeutronSecurityGroup securityGroup, boolean write) {
        LOG.debug("syncSecurityGroup {} port {} ", securityRule, port);
        if (!port.getPortSecurityEnabled()) {
            LOG.info("Port security not enabled port {}", port);
            return;
        }
        if (null != securityRule.getSecurityRemoteGroupID()) {
            List<Neutron_IPs> vmIpList  = securityServicesManager
                    .getVmListForSecurityGroup(port.getID(), securityRule.getSecurityRemoteGroupID());

            // the returned vmIpList contains the list of VMs belong to the remote security group
            // excluding ones on this port.
            // If the list is empty, this port is the first member of the remote security group
            // we need to add/remove from the remote security group cache accordingly
            if (vmIpList.isEmpty()) {
                if (write) {
                    securityGroupCacheManger.addToCache(securityRule.getSecurityRemoteGroupID(), port.getPortUUID(), nodeId);
                } else {
                    securityGroupCacheManger.removeFromCache(securityRule.getSecurityRemoteGroupID(), port.getPortUUID());
                }
            } else {
                for (Neutron_IPs vmIp : vmIpList) {
                    securityServicesManager.syncSecurityRule(port, securityRule, vmIp, nodeId, securityGroup, write);
                }
            }
        } else {
            securityServicesManager.syncSecurityRule(port, securityRule, null, nodeId, securityGroup, write);
        }
    }

    private Map<String, NodeId> getPortNodeCache() {
        Map<String, NodeId> portNodeCache = new HashMap();
        List<Node> toplogyNodes = southbound.readOvsdbTopologyNodes();

        for (Node topologyNode : toplogyNodes) {
            try {
                Node node = southbound.getBridgeNode(topologyNode,Constants.INTEGRATION_BRIDGE);
                if (node == null) {
                    LOG.error("getNode: br-int interface is not found for node:{}", topologyNode.getNodeId().getValue());
                }
                List<OvsdbTerminationPointAugmentation> ovsdbPorts = southbound.getTerminationPointsOfBridge(node);
                for (OvsdbTerminationPointAugmentation ovsdbPort : ovsdbPorts) {
                    String uuid = southbound.getInterfaceExternalIdsValue(ovsdbPort,
                                                            Constants.EXTERNAL_ID_INTERFACE_ID);
                    NodeId nodeId = node.getNodeId();
                    if (null != uuid && null != nodeId) {
                        portNodeCache.put(uuid, nodeId);
                    }
                }
            } catch (Exception e) {
                LOG.error("Exception during handlingNeutron network delete", e);
            }
        }
        return portNodeCache;
    }

    private List<NeutronPort> getPortWithSecurityGroup(String securityGroupUuid) {

        List<NeutronPort> neutronPortList = neutronPortCache.getAllPorts();
        List<NeutronPort> neutronPortInSg = new ArrayList<>();
        for (NeutronPort neutronPort:neutronPortList) {
            List<NeutronSecurityGroup> securityGroupList = neutronPort.getSecurityGroups();
            for (NeutronSecurityGroup neutronSecurityGroup:securityGroupList) {
                if (neutronSecurityGroup.getID().equals(securityGroupUuid)) {
                    neutronPortInSg.add(neutronPort);
                    break;
                }
            }
        }
        return neutronPortInSg;
    }

    @Override
    public void setDependencies(ServiceReference serviceReference) {
        eventDispatcher =
                (EventDispatcher) ServiceHelper.getGlobalInstance(EventDispatcher.class, this);
        eventDispatcher.eventHandlerAdded(serviceReference, this);
        southbound =
                (Southbound) ServiceHelper.getGlobalInstance(Southbound.class, this);
        neutronPortCache = (INeutronPortCRUD) ServiceHelper.getGlobalInstance(INeutronPortCRUD.class, this);
        securityServicesManager =
                (SecurityServicesManager) ServiceHelper.getGlobalInstance(SecurityServicesManager.class, this);
        securityGroupCacheManger =
                (SecurityGroupCacheManger) ServiceHelper.getGlobalInstance(SecurityGroupCacheManger.class, this);
    }

    @Override
    public void setDependencies(Object impl) {}
}