/*
 * Copyright (c) 2014, 2015 HP, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.impl;

import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronPortCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSecurityGroupCRUD;
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
    private List<NeutronSecurityGroup> defaultSecurityGrpList;

    @Override
    public void portAdded(String securityGroupUuid, String portUuid) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("In portAdded securityGroupUuid: {} portUuid: {} " , securityGroupUuid, portUuid);
        }
        NeutronPort port = neutronL3Adapter.getPortDetailsByPortUUID(portUuid);
        if(port == null) {
            return;
        }
        processPortAdded(securityGroupUuid, port);
    }

    @Override
    public void portRemoved(String securityGroupUuid, String portUuid) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("In portRemoved securityGroupUuid: {} portUuid: {} " , securityGroupUuid, portUuid);
        }
        NeutronPort port = neutronL3Adapter.getPortDetailsByPortUUID(portUuid);
        if(port == null) {
            return;
        }
        processPortRemoved(securityGroupUuid, port);
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

    /**
     * Returns true if the specified SgId is default, else false
     * @param securityGroupUUID the security group id
     */
    public boolean isDefaultSecurityGroup(String securityGroupUUID) {
        if(this.defaultSecurityGrpList == null || this.defaultSecurityGrpList.isEmpty()) {
            initDefaultSG();
        }
        for (NeutronSecurityGroup defaultSG : defaultSecurityGrpList) {
            if (defaultSG.getSecurityGroupUUID().equals(securityGroupUUID)) {
                return true;
            }
        }
        return false;
    }
    private void initDefaultSG() {
        INeutronSecurityGroupCRUD groupCRUD =
                (INeutronSecurityGroupCRUD) ServiceHelper.getGlobalInstance(INeutronSecurityGroupCRUD.class, this);
        List<NeutronSecurityGroup> allSecurityGrps = groupCRUD.getAllNeutronSecurityGroups();
        this.defaultSecurityGrpList = new ArrayList<NeutronSecurityGroup>();
        for (NeutronSecurityGroup securtyGrp : allSecurityGrps) {
            if(securtyGrp.getSecurityGroupName().equals("default")) {
                this.defaultSecurityGrpList.add(securtyGrp);
            }
        }
    }

    private void processPortAdded(String securityGroupUuid, NeutronPort port) {
        processSyncRule(securityGroupUuid, port, true);
    }

    private void processSyncRule(String securityGroupUuid, NeutronPort port, boolean write) {
        /*
         * Itreate through the cache maintained for the security group added. For each port in the cache
         * add the rule to allow traffic to/from the new port added.
         */
        LOG.debug("In processPortAdded securityGroupUuid: {}, NeutronPort: {}", securityGroupUuid, port);
        Map<String, NodeId> portMap = securityGroupCache.get(securityGroupUuid);
        if (null == portMap) {
            LOG.debug("The port list is empty for security group: {}", securityGroupUuid);
            return;
        }
        Set portSet = portMap.entrySet();
        Iterator itr = portSet.iterator();
        Map<NodeId, Long> dpIdNodeMap = new HashMap<NodeId, Long>();
        Map<String, List<NeutronSecurityRule> > secGrpRulesMap = new HashMap<String, List<NeutronSecurityRule>>();
        while(itr.hasNext()) {
            Map.Entry<String, NodeId> portEntry = (Map.Entry)itr.next();
            String cachedportUuid = portEntry.getKey();
            NodeId nodeId = portEntry.getValue();
            if (cachedportUuid.equals(port.getID())) {
                continue;
            }
            NeutronPort cachedport = neutronL3Adapter.getPortDetailsByPortUUID(cachedportUuid);
            if(cachedport == null) {
                LOG.error("In processPortRemoved cachedport port not found in neuton cache:"
                        + " cachedportUuid:" + cachedportUuid);
                continue;
            }
            retrieveAndSyncSecurityRules(securityGroupUuid, cachedport, nodeId, dpIdNodeMap, secGrpRulesMap, port, write);
        }
    }

    private void processPortRemoved(String securityGroupUuid, NeutronPort port) {
        processSyncRule(securityGroupUuid, port, false);
    }

    private void retrieveAndSyncSecurityRules(String securityGroupUuid, NeutronPort cachedport, NodeId nodeId,
            Map<NodeId, Long> dpIdNodeMap, Map<String, List<NeutronSecurityRule> > secGrpRulesMap, NeutronPort currentPort, boolean write) {
        /*
         * Get the list of security rules in the port with portUuid that has securityGroupUuid as a remote
         * security group.
         */
        List<NeutronSecurityRule> securityRules =  new ArrayList<NeutronSecurityRule>();
        List<NeutronSecurityGroup> securityGroups = cachedport.getSecurityGroups();
        for (NeutronSecurityGroup securityGroup : securityGroups) {
            if (secGrpRulesMap.get(securityGroup.getSecurityGroupUUID()) == null) {
                securityRules = getSecurityRulesforGroup(securityGroup);
                secGrpRulesMap.put(securityGroup.getSecurityGroupUUID(), securityRules);
            } else {
                securityRules = secGrpRulesMap.get(securityGroup.getSecurityGroupUUID());
            }
            for (NeutronSecurityRule securityRule : securityRules) {
                if (securityGroupUuid.equals(securityRule.getSecurityRemoteGroupID())) {
                    if (currentPort.getFixedIPs() == null) {
                        continue;
                    }
                    for (Neutron_IPs vmIp : currentPort.getFixedIPs()) {
                        if (write) {
                            securityServicesManager.syncSecurityRule(cachedport, securityRule, vmIp, nodeId, dpIdNodeMap, true);
                        } else {
                            securityServicesManager.syncSecurityRule(cachedport, securityRule, vmIp, nodeId, dpIdNodeMap, false);
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
        /**
         * Setting up the default SG
         */
        initDefaultSG();
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
