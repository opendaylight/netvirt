/*
 * Copyright (c) 2017 Inspur, Inc. and others.  All rights reserved.
 *
 */

package org.opendaylight.netvirt.sfc.classifier.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.sfc.classifier.service.ClassifierService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.classifier.rev150105.Classifiers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.classifier.rev150105.classifiers.Classifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data tree listener for interface.
 */
@Singleton
public class NetvirtSfcInterfaceListener  extends AsyncDataTreeChangeListenerBase<Interface, NetvirtSfcInterfaceListener>
    implements AutoCloseable {

    private final DataBroker dataBroker;
    private final ClassifierService classifierService;
    private static final Logger LOG = LoggerFactory.getLogger(NetvirtSfcInterfaceListener.class);
    

    @Inject
    public NetvirtSfcInterfaceListener(final DataBroker dataBroker, final ClassifierService classifierService) {
        super(Interface.class, NetvirtSfcInterfaceListener.class);
        
        this.dataBroker = dataBroker;
        this.classifierService = classifierService;
    }

    @Override
    @PostConstruct
    public void init() {
    	
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier
            .create(InterfacesState.class)
            .child(Interface.class);
    }

    @Override
    protected NetvirtSfcInterfaceListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface intface) {
    	
    	String portid=intface.getName();
    	
    	InstanceIdentifier<AccessLists> aclsIID = InstanceIdentifier.builder(AccessLists.class).build();
        com.google.common.base.Optional<AccessLists> acls =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, aclsIID);
        List<Acl> acllists=acls.get().getAcl();
        
        
        for(Acl acl:acllists) {
        	
        	AccessListEntries aclentries=acl.getAccessListEntries();
        	
        	List<Ace> entrieslist=aclentries.getAce();
        	for(Ace ace:entrieslist) {
        		
        		Matches matches=ace.getMatches();
        		
        		NeutronNetwork network = matches.getAugmentation(NeutronNetwork.class);
        		
        		List<String> interfaces=getLogicalInterfacesFromNeutronNetwork(network);
        		
        		for(String aclportid:interfaces) {
        			
        			if (aclportid.equals(portid)) {
        				classifierService.updateAll();
        				break;
        			}
        			
        		}
        		
        	}
        	
        }
       
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface intface) {
    	
    	String portid=intface.getName();
    	InstanceIdentifier<AccessLists> aclsIID = InstanceIdentifier.builder(AccessLists.class).build();
        com.google.common.base.Optional<AccessLists> acls =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, aclsIID);
        List<Acl> acllists=acls.get().getAcl();
        
        for(Acl acl:acllists) {
        	AccessListEntries aclentries=acl.getAccessListEntries();
        	List<Ace> entrieslist=aclentries.getAce();
        	for(Ace ace:entrieslist) {
        		Matches matches=ace.getMatches();
        		NeutronNetwork network = matches.getAugmentation(NeutronNetwork.class);
        		List<String> interfaces=getLogicalInterfacesFromNeutronNetwork(network);
        		for(String aclportid:interfaces) {
        			if (aclportid==portid) {
        				classifierService.updateAll();
        				break;
        			}
        			
        		}
        		
        	}
        	
        }
        
    }
    
    @Override
    protected void update(InstanceIdentifier<Interface> key,  Interface aclBefore, Interface aclAfter) {
    }
    
    
    private List<String> getLogicalInterfacesFromNeutronNetwork(NeutronNetwork nw) {
        InstanceIdentifier<NetworkMap> networkMapIdentifier =
                getNetworkMapIdentifier(new Uuid(nw.getNetworkUuid()));

        NetworkMap networkMap =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, networkMapIdentifier).orNull();
        if (networkMap == null) {
            LOG.warn("getLogicalInterfacesFromNeutronNetwork cant get NetworkMap for NW UUID [{}]",
                    nw.getNetworkUuid());
            return Collections.emptyList();
        }

        List<String> interfaces = new ArrayList<>();
        List<Uuid> subnetUuidList = networkMap.getSubnetIdList();
        if (subnetUuidList != null) {
            for (Uuid subnetUuid : subnetUuidList) {
            	LOG.info("rqz netvirt sfc subnetUuid {}",subnetUuid);
                InstanceIdentifier<Subnetmap> subnetId = getSubnetMapIdentifier(subnetUuid);
                LOG.info("rqz netvirt sfc subnetId {}",subnetId);
                Subnetmap subnet = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetId).orNull();
                if (subnet == null) {
                    LOG.warn(
                            " getLogicalInterfacesFromNeutronNetwork cant get Subnetmap for NW UUID [{}] Subnet UUID "
                                    + "[{}]",
                            nw.getNetworkUuid(), subnetUuid.getValue());
                    continue;
                }

                if (subnet.getPortList() == null || subnet.getPortList().isEmpty()) {
                    LOG.warn("getLogicalInterfacesFromNeutronNetwork No ports on Subnet: NW UUID [{}] Subnet UUID [{}]",
                            nw.getNetworkUuid(), subnetUuid.getValue());
                    continue;
                }
                LOG.info("rqz netvirt sfc subnet.getPortList() {}",subnet.getPortList());
                subnet.getPortList().forEach(portId -> interfaces.add(portId.getValue()));
            }
        }

        return interfaces;
    }

    //
    // Internal Util methods
    //

    private InstanceIdentifier<NetworkMap> getNetworkMapIdentifier(Uuid nwUuid) {
        return InstanceIdentifier.builder(NetworkMaps.class)
                .child(NetworkMap.class,new NetworkMapKey(nwUuid)).build();
    }

    private InstanceIdentifier<Subnetmap> getSubnetMapIdentifier(Uuid subnetUuid) {
        return InstanceIdentifier.builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetUuid)).build();
    }
    
    

    
}
