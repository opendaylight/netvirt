/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.nexthopmgr;
/**********************************************************************************
** NextHop MD-SAL DS 
** ------------------------------------------------
** DP_ID  |  VPN   |   IP Address  |  GroupId     |
** ------------------------------------------------
** 
** Listen to DCNs from vpn-inetrfaces
** if a next-hop is added/removed in vpn-interfaces DS
** 	call add/removeNextHop(interface.dpn, interface.port, vpn_instance.vpnId, AdjacencyIpAddress);
** 
** if a tunnel-interface is added inn interfaces DS --
** 	call add/removeNextHop(interface.dpn, interface.port, 00, RemoteIpAddress);
*************************************************************************************/
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import java.util.List;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
/*import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.NextHopList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.next.hop.list.L3NextHops;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInterface1;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
*/
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150330.L3nexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150330.l3nexthop.VpnNexthops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NexthopManager extends AbstractDataChangeListener<L3nexthop> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(L3nexthop.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;

    public NexthopManager(final DataBroker db) {
        super(L3nexthop.class);
        broker = db;
        registerListener(db);
    }

	@Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("VPN Interface Manager Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), NexthopManager.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Nexthop Manager DataChange listener registration fail!", e);
            throw new IllegalStateException("Nexthop Manager registration Listener failed.", e);
        }
    }
    
	public void addNextHop(long dpnId, int port, String vpnRD, String IpAddress)
	{
		String nhKey = new String("nexthop"+vpnRD+IpAddress);
		
		int groupId = 1;//getIdManager().getUniqueId("nextHopGroupIdPool", nhKey);

/*		if (getNextHop(groupId) == Null){
			List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
			List<ActionInfo> listActionInfo = null;//nextHop.getActions({output to port}); 
			BucketInfo bucket = new BucketInfo(listActionInfo);
			listBucketInfo.add(bucket);
			//GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpId, groupId, IPAddress, GroupTypes.GroupIndirect, listBucketInfo);
			//getMdsalApiManager().installGroup(groupEntity, objTransaction???);
			
			//update MD-SAL DS
			addNextHopToDS(dpId, vpn, ipAddress, groupId);
		}else{
			//check update
		}*/
	}	

	public void removeNextHop(long dpnId, int port, String vpnRD, String IpAddress)
	{
		String nhKey = new String("nexthop"+vpnRD+IpAddress);
		int groupId = 1;//getIdManager().getUniqueId(L3Constants.L3NEXTHOP_GROUPID_POOL, nhKey);

/*		if (getNextHop(groupId) != Null){
			List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
			List<ActionInfo> listActionInfo = null;//nextHop.getActions({output to port}); 
			BucketInfo bucket = new BucketInfo(listActionInfo);
			listBucketInfo.add(bucket);
			//GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpId, groupId, IPAddress, GroupTypes.GroupIndirect, listBucketInfo);
			//getMdsalApiManager().removeGroup(groupEntity, objTransaction???);
			
			//update MD-SAL DS
			removeNextHopFromDS(dpId, vpn, ipAddress);
		}else{
			//check update
		}*/
	}	
		
	public long getNextHopPointer(long dpnId, int vpnId, String prefixIp, String nxetHopIp)
	{
/*		String endpointIp = interfaceManager.getLocalEndpointIp(dpnId);
		if (nextHopIp.equals(endpointIp)) {
			return getGidFromDS(dpnId, vpnId, prefixIp);
		} else {
			return getGidFromDS(dpnId, 00, nextHopIp);
		}*/
		return 0;
	}

    private InstanceIdentifier<L3nexthop> getWildCardPath() {
        return InstanceIdentifier.create(L3nexthop.class);//.child(l3nexthop.vpnNexthops.class);
    }

	private void addNextHopToDS(long dpId, int vpnId, String ipAddress, long groupId)
	{
		
	}

	private long getGidFromDS(String ipaddress)
	{
		return 0;
		
	}

	@Override
	protected void remove(InstanceIdentifier<L3nexthop> identifier,
			L3nexthop del) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void update(InstanceIdentifier<L3nexthop> identifier,
			L3nexthop original, L3nexthop update) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void add(InstanceIdentifier<L3nexthop> identifier, L3nexthop add) {
		// TODO Auto-generated method stub
		
	}

}