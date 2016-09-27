/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.arp.responder;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arp Default Flow Installer installs default flows on the DPN.
 * <P>
 * Whenever an DPN is added to Data Store, Node added event is triggered, on the
 * node add event the flows are installed on the newly added DPN or node
 *
 */
@SuppressWarnings("deprecation")
public class ArpDefaultFlowInstaller
        extends AsyncDataTreeChangeListenerBase<Node, ArpDefaultFlowInstaller> {

    private final static Logger LOG = LoggerFactory
            .getLogger(ArpDefaultFlowInstaller.class);

    /**
     * The MDSAL API RPC reference used for installing flows
     */
    private final IMdsalApiManager iMdsalApiManager;

    /**
     * The constructor for the class with two arguments
     * 
     * @param dataBroker
     *            Reference of MDSAL data store
     * @param iMdsalApiManager
     *            Reference of MDSAL API FLow installer RPC
     */
    public ArpDefaultFlowInstaller(final DataBroker dataBroker,
            final IMdsalApiManager iMdsalApiManager) {
        super(Node.class, ArpDefaultFlowInstaller.class);
        this.iMdsalApiManager = iMdsalApiManager;
        super.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    /**
     * Path of DS Data to get notification on any changes in tree,
     * ArpDefaultFlowInstaller registers for {@link Nodes}
     * 
     * @see org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase
     */
    @Override
    protected InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    /**
     * Action to be performed on removal of node from DataStore,
     * {@link #ArpDefaultFlowInstaller(DataBroker, IMdsalApiManager) } does not
     * perform any task on node removal
     * 
     * @see org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#remove(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     *      org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void remove(InstanceIdentifier<Node> key, Node node) {

    }

    /**
     * Action to be performed on removal of node from DataStore,
     * {@link #ArpDefaultFlowInstaller(DataBroker, IMdsalApiManager) } does not
     * perform any task on node removal
     * 
     * @see org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#update(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     *      org.opendaylight.yangtools.yang.binding.DataObject,
     *      org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void update(InstanceIdentifier<Node> key, Node node,
            Node dataObjectModificationAfter) {
    }

    /**
     * Triggered when Node is added to DPN or when DPN connects to Controller,
     * Following tasks are performed
     * <ul><li>Install Default Groups, Group has 3 Buckets
     * <ul>
     * <li>Punt to controller</li>
     * <li>Resubmit to Table {@link NwConstants#LPORT_DISPATCHER_TABLE}, for
     * ELAN flooding
     * <li>Resubmit to Table {@link NwConstants#ARP_RESPONDER_TABLE}, for ARP
     * Auto response from DPN itself</li>
     * </ul>
     * </li>
     * <li>Install Default Drop Flow on table miss of table
     * {@link NwConstants#ARP_RESPONDER_TABLE}</li></ul>
     * 
     * @see org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#add(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     *      org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void add(InstanceIdentifier<Node> key, Node node) {

        final BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(node.getId());
        final List<BucketInfo> buckets = ArpResponderUtil.getDefaultBucketInfos(
                NwConstants.LPORT_DISPATCHER_TABLE,
                NwConstants.ARP_RESPONDER_TABLE);
        ArpResponderUtil.installGroupFlow(iMdsalApiManager, dpnId,
                ArpResponderConstant.Group.ID.value(),
                ArpResponderConstant.GROUP_FLOW_NAME.value(), buckets);
        ArpResponderUtil.installDropFlow(iMdsalApiManager, dpnId,
                NwConstants.ARP_RESPONDER_TABLE,
                String.valueOf(NwConstants.LPORT_DISPATCHER_TABLE), 0,
                ArpResponderConstant.DROP_FLOW_NAME.value(), 0, 0,
                ArpResponderConstant.Cookies.DROP_COOKIE.value());
        LOG.trace(
                "Installation of default ARP Responder groupflows completed on dpn {}",
                dpnId);

    }

    /**
     * Register the class to receive events on data changes
     * 
     * @see org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#getDataTreeChangeListener()
     */
    @Override
    protected ArpDefaultFlowInstaller getDataTreeChangeListener() {
        return ArpDefaultFlowInstaller.this;
    }
}
