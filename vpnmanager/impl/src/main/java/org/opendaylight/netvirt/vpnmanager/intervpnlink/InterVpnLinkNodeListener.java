/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import java.math.BigInteger;
import java.util.List;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.VpnFootprintService;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.tasks.InterVpnLinkCleanedCheckerTask;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.tasks.InterVpnLinkCreatorTask;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.tasks.InterVpnLinkRemoverTask;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for Nodes going down, in order to check if the InterVpnLink must be
 * moved to some other DPN.
 */
@Singleton
public class InterVpnLinkNodeListener extends AbstractAsyncDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkNodeListener.class);

    public static final TopologyId FLOW_TOPOLOGY_ID = new TopologyId(new Uri("flow:1"));

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final VpnFootprintService vpnFootprintService;
    private final JobCoordinator jobCoordinator;
    private final InterVpnLinkCache interVpnLinkCache;
    private final VpnUtil vpnUtil;
    private final InterVpnLinkUtil interVpnLinkUtil;

    @Inject
    public InterVpnLinkNodeListener(final DataBroker dataBroker, final IMdsalApiManager mdsalMgr,
                                    final VpnFootprintService vpnFootprintService,
                                    final JobCoordinator jobCoordinator, final InterVpnLinkCache interVpnLinkCache,
                                    VpnUtil vpnUtil, InterVpnLinkUtil interVpnLinkUtil) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(FLOW_TOPOLOGY_ID))
                .child(Node.class), Executors.newListeningSingleThreadExecutor("InterVpnLinkNodeListener",
                LOG));
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalMgr;
        this.vpnFootprintService = vpnFootprintService;
        this.jobCoordinator = jobCoordinator;
        this.interVpnLinkCache = interVpnLinkCache;
        this.vpnUtil = vpnUtil;
        this.interVpnLinkUtil = interVpnLinkUtil;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void add(InstanceIdentifier<Node> identifier, Node add) {
        NodeId nodeId = add.getNodeId();
        String[] node = nodeId.getValue().split(":");
        if (node.length < 2) {
            LOG.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpId = new BigInteger(node[1]);
        jobCoordinator.enqueueJob("IVpnLink" + dpId.toString(),
            new InterVpnLinkNodeAddTask(dataBroker, mdsalManager, vpnFootprintService, dpId, interVpnLinkCache,
                    vpnUtil, interVpnLinkUtil));
    }

    @Override
    public void remove(InstanceIdentifier<Node> identifier, Node del) {
        LOG.trace("Node {} has been deleted", identifier.firstKeyOf(Node.class).toString());
        NodeId nodeId = del.getNodeId();
        String[] node = nodeId.getValue().split(":");
        if (node.length < 2) {
            LOG.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpId = new BigInteger(node[1]);
        List<InterVpnLinkDataComposite> allInterVpnLinks = interVpnLinkCache.getAllInterVpnLinks();
        allInterVpnLinks.stream()
                        .filter(ivl -> ivl.stepsOnDpn(dpId))           // Only those affected by DPN going down
                        .forEach(this::reinstallInterVpnLink);         // Move them somewhere else
    }

    private void reinstallInterVpnLink(InterVpnLinkDataComposite ivl) {
        String ivlName = ivl.getInterVpnLinkName();
        LOG.debug("Reinstalling InterVpnLink {} affected by node going down", ivlName);
        // Lets move the InterVpnLink to some other place. Basically, remove it and create it again
        InstanceIdentifier<InterVpnLink> interVpnLinkIid = InterVpnLinkUtil.getInterVpnLinkPath(ivlName);
        String specificJobKey = "InterVpnLink.update." + ivlName;
        InterVpnLink interVpnLink = ivl.getInterVpnLinkConfig();
        jobCoordinator.enqueueJob(specificJobKey, new InterVpnLinkRemoverTask(dataBroker, interVpnLinkIid));
        jobCoordinator.enqueueJob(specificJobKey, new InterVpnLinkCleanedCheckerTask(dataBroker, interVpnLink,
                interVpnLinkUtil, vpnUtil));
        jobCoordinator.enqueueJob(specificJobKey, new InterVpnLinkCreatorTask(dataBroker, interVpnLink));
    }

    @Override
    public void update(InstanceIdentifier<Node> identifier, Node original, Node update) {
    }
}
