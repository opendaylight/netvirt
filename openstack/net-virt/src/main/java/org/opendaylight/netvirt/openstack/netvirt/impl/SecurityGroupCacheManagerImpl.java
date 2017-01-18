/*
 * Copyright (c) 2014, 2015 HP, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.impl;

import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronPortCRUD;
import org.opendaylight.netvirt.openstack.netvirt.ConfigInterface;
import org.opendaylight.netvirt.openstack.netvirt.api.Constants;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityGroupCacheManger;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityServicesManager;
import org.opendaylight.netvirt.openstack.netvirt.api.Southbound;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityGroup;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityRule;
import org.opendaylight.netvirt.openstack.netvirt.translator.Neutron_IPs;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSecurityRuleCRUD;
import org.opendaylight.netvirt.utils.servicehelper.ServiceHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Aswin Suryanarayanan.
 */

public class SecurityGroupCacheManagerImpl implements ConfigInterface, SecurityGroupCacheManger {

    private final Map<String, Map<String, NodeId>> securityGroupCache = new ConcurrentHashMap<>();
    private static final Logger LOG = LoggerFactory.getLogger(SecurityGroupCacheManagerImpl.class);
    private volatile SecurityServicesManager securityServicesManager;
    private volatile Southbound southbound;
    private volatile INeutronPortCRUD neutronPortCache;
    private volatile NeutronL3Adapter neutronL3Adapter;
    private volatile INeutronSecurityRuleCRUD neutronSecurityRule;

    @Override
    public void portAdded(NeutronSecurityGroup securityGroup, String portUuid) {
        LOG.debug("In portAdded securityGroupUuid:" + securityGroup.getSecurityGroupUUID() + " portUuid:" + portUuid);
        NeutronPort port = neutronL3Adapter.getPortFromCleanupCache(portUuid);
        if (port == null) {
            port = neutronPortCache.getPort(portUuid);
            if (port == null) {
                LOG.error("In portAdded no neutron port found:" + " portUuid:" + portUuid);
                return;
            }
            neutronL3Adapter.storePortInCleanupCache(port);
        }
        processPortAdded(securityGroup,port);
    }

    @Override
    public void portRemoved(NeutronSecurityGroup securityGroup, String portUuid) {
        LOG.debug("In portRemoved securityGroupUuid:" + securityGroup.getSecurityGroupUUID() + " portUuid:" + portUuid);
        NeutronPort port = neutronL3Adapter.getPortFromCleanupCache(portUuid);

        if (port == null) {
            port = neutronPortCache.getPort(portUuid);
            if (port == null) {
                LOG.error("In portRemoved no neutron port found:" + " portUuid:" + portUuid);
                return;
            }
            neutronL3Adapter.storePortInCleanupCache(port);
        }
        processPortRemoved(securityGroup,port);
    }

    @Override
    public void addToCache(String remoteSgUuid, String portUuid, NodeId nodeId) {
        LOG.debug("In addToCache remoteSgUuid:" + remoteSgUuid + " portUuid:" + portUuid);
        Map<String, NodeId> remoteSgPorts = securityGroupCache.get(remoteSgUuid);
        if (null == remoteSgPorts) {
            remoteSgPorts = new HashMap<>();
            securityGroupCache.put(remoteSgUuid, remoteSgPorts);
        }
        remoteSgPorts.put(portUuid, nodeId);
    }

    @Override
    public void removeFromCache(String remoteSgUuid, String portUuid) {
        LOG.debug("In removeFromCache remoteSgUuid:" + remoteSgUuid + " portUuid:" + portUuid);
        Map<String, NodeId> remoteSgPorts = securityGroupCache.get(remoteSgUuid);
        if (null == remoteSgPorts) {
            LOG.debug("The port list is empty for security group:" + remoteSgUuid);
            return;
        }
        Set<String> portSet = remoteSgPorts.keySet();
        for (Iterator<String> iterator = portSet.iterator(); iterator.hasNext();) {
            String cachedPort = iterator.next();
            if (cachedPort.equals(portUuid)) {
                iterator.remove();
                break;
            }
        }
        if (portSet.isEmpty()) {
            securityGroupCache.remove(remoteSgUuid);
        }
    }

    private void processPortAdded(NeutronSecurityGroup securityGroup, NeutronPort port) {
        /*
         * Itreate through the cache maintained for the security group added. For each port in the cache
         * add the rule to allow traffic to/from the new port added.
         */
        LOG.debug("In processPortAdded securityGroupUuid:" + securityGroup.getSecurityGroupUUID() + " NeutronPort:" + port);
        Map<String, NodeId> portList = securityGroupCache.get( securityGroup.getSecurityGroupUUID());
        if (null == portList) {
            LOG.debug("The port list is empty for security group:" +  securityGroup.getSecurityGroupUUID());
            return;
        }
        processSyncRule(securityGroup, port, portList, true);
        
    }

    private void processSyncRule(NeutronSecurityGroup securityGroup, NeutronPort port, Map<String, NodeId> portList, boolean write) {
        Set portSet = portList.entrySet();
        Iterator itr = portSet.iterator();
        Map<String, List<NeutronSecurityRule> > secGrpRulesMap = new HashMap<String, List<NeutronSecurityRule>>();
        while(itr.hasNext()) {
            Map.Entry<String, NodeId> portEntry = (Map.Entry)itr.next();
            String cachedportUuid = portEntry.getKey();
            NodeId nodeId = portEntry.getValue();
            if (cachedportUuid.equals(port.getID())) {
                continue;
            }
            NeutronPort cachedport = neutronL3Adapter.getPortFromCleanupCache(cachedportUuid);
            if (cachedport == null) {
                cachedport = neutronPortCache.getPort(cachedportUuid);
                if (null == cachedport) {
                    LOG.error("In processPortRemoved cachedport port not found in neuton cache:"
                                + " cachedportUuid:" + cachedportUuid);
                    continue;
                }
                neutronL3Adapter.storePortInCleanupCache(cachedport);
            }
            retrieveAndSyncSecurityRules(securityGroup.getSecurityGroupUUID(), cachedport,nodeId, write,secGrpRulesMap,port);
        }
    }

    private void processPortRemoved(NeutronSecurityGroup securityGroup, NeutronPort port) {
            /*
             * Itreate through the cache maintained for the security group added. For each port in the cache remove
             * the rule to allow traffic to/from the  port that got deleted.
             */
            LOG.debug("In processPortRemoved securityGroupUuid:" + securityGroup.getSecurityGroupUUID() + " port:" + port);
            Map<String, NodeId> portList = securityGroupCache.get( securityGroup.getSecurityGroupUUID());
            if (null == portList) {
                LOG.debug("The port list is empty for security group:" +  securityGroup.getSecurityGroupUUID());
                return;
            }
            processSyncRule(securityGroup, port, portList, false);
        }

    private void retrieveAndSyncSecurityRules(String securityGroupUuid, NeutronPort cachedport, NodeId nodeId,  boolean write,
            Map<String, List<NeutronSecurityRule> > secGrpRulesMap, NeutronPort currentPort) {
        /*
         * Get the list of security rules in the port with portUuid that has securityGroupUuid as a remote
         * security group.
         */
        List<NeutronSecurityRule> securityRules =  new ArrayList<NeutronSecurityRule>();
        List<NeutronSecurityGroup> securityGroups = cachedport.getSecurityGroups();
        Map<NodeId, Long> dpIdNodeMap = new HashMap<NodeId, Long>();
        for (NeutronSecurityGroup securityGroup : securityGroups) {
            if(secGrpRulesMap.get(securityGroup.getSecurityGroupUUID()) == null) {
                securityRules = getSecurityRulesforGroup(securityGroup) ;
                secGrpRulesMap.put(securityGroup.getSecurityGroupUUID(), securityRules);
            } else {
                securityRules = secGrpRulesMap.get(securityGroup.getSecurityGroupUUID()) ;
            }
            for (NeutronSecurityRule securityRule : securityRules) {
                if (securityGroupUuid.equals(securityRule.getSecurityRemoteGroupID())) {
                            if (currentPort.getFixedIPs() == null) {
                                continue;
                            }
                            for (Neutron_IPs vmIp : currentPort.getFixedIPs()) {
                                if(write) {
                                    securityServicesManager.syncSecurityRule(cachedport, securityRule, vmIp, nodeId, true, dpIdNodeMap,securityGroup);
                                } else {
                                     securityServicesManager.syncSecurityRule(cachedport, securityRule, vmIp, nodeId, false, dpIdNodeMap,securityGroup);
                                }
                            }
                }
            }
        }
    }

    private void init() {
        /*
         * Rebuild the cache in case of a restart.
         */
        Map<String, NodeId> portNodeCache = getPortNodeCache();
        List<NeutronPort> portList = neutronPortCache.getAllPorts();
        for (NeutronPort port:portList) {
            List<NeutronSecurityGroup> securityGroupList = port.getSecurityGroups();
            if ( null != securityGroupList) {
                for (NeutronSecurityGroup securityGroup : securityGroupList) {
                    List<NeutronSecurityRule> securityRuleList = getSecurityRulesforGroup(securityGroup);
                    if ( null != securityRuleList) {
                        for (NeutronSecurityRule securityRule : securityRuleList) {
                            if (null != securityRule.getSecurityRemoteGroupID()) {
                                this.addToCache(securityRule.getSecurityRemoteGroupID(), port.getID(), portNodeCache.get(port.getID()));
                            }
                        }
                    }
                }
            }
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

    private List<NeutronSecurityRule> getSecurityRulesforGroup(NeutronSecurityGroup securityGroup) {
        List<NeutronSecurityRule> securityRules = new ArrayList<>();
        List<NeutronSecurityRule> rules = neutronSecurityRule.getAllNeutronSecurityRules();
        for (NeutronSecurityRule securityRule : rules) {
            if (securityGroup.getID().equals(securityRule.getSecurityRuleGroupID())) {
                securityRules.add(securityRule);
            }
        }
        return securityRules;
    }

    @Override
    public void setDependencies(ServiceReference serviceReference) {
        neutronL3Adapter =
                (NeutronL3Adapter) ServiceHelper.getGlobalInstance(NeutronL3Adapter.class, this);
        securityServicesManager =
                (SecurityServicesManager) ServiceHelper.getGlobalInstance(SecurityServicesManager.class, this);
        southbound =
                (Southbound) ServiceHelper.getGlobalInstance(Southbound.class, this);
        neutronPortCache = (INeutronPortCRUD) ServiceHelper.getGlobalInstance(INeutronPortCRUD.class, this);
        neutronSecurityRule = (INeutronSecurityRuleCRUD) ServiceHelper.getGlobalInstance(INeutronSecurityRuleCRUD.class, this);
        init();
    }

    @Override
    public void setDependencies(Object impl) {
    }
}
