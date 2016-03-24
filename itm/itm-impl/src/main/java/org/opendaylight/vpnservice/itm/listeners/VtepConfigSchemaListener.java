/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.itm.listeners;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.cli.TepCommandHelper;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.VtepConfigSchemas;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.VtepConfigSchema;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.VtepConfigSchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.ip.pools.VtepIpPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.ip.pools.VtepIpPoolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.vtep.config.schema.DpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.vtep.config.schema.DpnIdsKey;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.VtepConfigSchemas;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.vtep.config.schemas.VtepConfigSchema;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.vtep.config.schemas.VtepConfigSchemaBuilder;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.vtep.ip.pools.VtepIpPool;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.vtep.ip.pools.VtepIpPoolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.VtepsKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

/**
 * The listener class interested in processing data change on
 * {@code VtepConfigSchema} objects.
 *
 * @see VtepConfigSchema
 */
public class VtepConfigSchemaListener extends AbstractDataChangeListener<VtepConfigSchema> implements AutoCloseable {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(VtepConfigSchemaListener.class);

    /** The listener registration. */
    private ListenerRegistration<DataChangeListener> listenerRegistration;

    /** The data broker. */
    private final DataBroker dataBroker;

    /**
     * Instantiates a new vtep config schema listener.
     *
     * @param db
     *            the db
     */
    public VtepConfigSchemaListener(final DataBroker db) {
        super(VtepConfigSchema.class);
        this.dataBroker = db;
        registerListener(db);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() throws Exception {
        if (this.listenerRegistration != null) {
            try {
                this.listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            this.listenerRegistration = null;
        }
        LOG.info("VtepConfigSchemaListener Closed");
    }

    /**
     * Register listener.
     *
     * @param db
     *            the db
     */
    private void registerListener(final DataBroker db) {
        try {
            this.listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), VtepConfigSchemaListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("VtepConfigSchemaListener DataChange listener registration fail!", e);
            throw new IllegalStateException("VtepConfigSchemaListener registration Listener failed.", e);
        }
    }

    /**
     * Gets the wild card path.
     *
     * @return the wild card path
     */
    private InstanceIdentifier<VtepConfigSchema> getWildCardPath() {
        return ItmUtils.getVtepConfigSchemaIdentifier();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener#remove(
     * org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void remove(InstanceIdentifier<VtepConfigSchema> identifier, VtepConfigSchema schema) {
        LOG.trace("Received notification for VTEP config schema [{}] deleted.", schema.getSchemaName());
        try {
            List<BigInteger> lstDpnIds = ItmUtils.getDpnIdList(schema.getDpnIds());
            if (lstDpnIds != null && !lstDpnIds.isEmpty()) {
                deleteVteps(schema, lstDpnIds);
            }

            // Delete IP pool corresponding to schema
            // TODO: Ensure no schema exists with same subnet before deleting
            String subnetCidr = ItmUtils.getSubnetCidrAsString(schema.getSubnet());
            deleteVtepIpPool(subnetCidr);
        } catch (Exception e) {
            String error = new StringBuilder("Failed to handle DCN for delete VtepConfigSchema: ").append(schema)
                    .toString();
            LOG.error(error, e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener#update(
     * org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void update(InstanceIdentifier<VtepConfigSchema> identifier, VtepConfigSchema original,
            VtepConfigSchema updated) {
        LOG.error("Receivedn DCN for updating VTEP Original schema: {}. Updated schema: {}",
                original, updated);
        //LOG.trace("Received notification for VTEP config schema [{}] updated.", original.getSchemaName());

        try {
            VtepConfigSchema orignalSchema = ItmUtils.validateVtepConfigSchema(original);
            VtepConfigSchema updatedSchema = ItmUtils.validateVtepConfigSchema(updated);

            if (doesDeleteAndAddSchemaRequired(original, updated)) {
                LOG.error("Failed to handle DCN for updating VTEP schema {}. Original schema: {}. Updated schema: {}",
                        original, updated);
                // TODO: handle updates
                return;
            }

            handleUpdateOfDpnIds(orignalSchema, updatedSchema);

        } catch (Exception e) {
            String error = new StringBuilder("Failed to handle DCN for update VtepConfigSchema original:")
                    .append(original).append(", updated: ").append(updated).toString();
            LOG.error(error, e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener#add(org.
     * opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void add(InstanceIdentifier<VtepConfigSchema> identifier, VtepConfigSchema schema) {
        // Construct the transport zones from the provided schemas and push it
        // to config DS.
        LOG.trace("Add VtepConfigSchema: key: {} , value: {}", identifier, schema);

        try {
            VtepConfigSchema validatedSchema = ItmUtils.validateForAddVtepConfigSchema(schema,
                    getAllVtepConfigSchemas());

            VtepIpPool vtepIpPool = processAvailableIps(validatedSchema);
            addVteps(validatedSchema, vtepIpPool);
        } catch (Exception e) {
            LOG.error("Failed to handle DCN for add VtepConfigSchema: {}", e);
        }
    }

    /**
     * Handle update of dpn ids.
     *
     * @param original
     *            the original
     * @param updated
     *            the updated
     */
    private void handleUpdateOfDpnIds(VtepConfigSchema original, VtepConfigSchema updated) {
        // Handling add/delete DPNs from schema
        List<DpnIds> originalDpnIds = (original.getDpnIds() == null) ? new ArrayList<DpnIds>()
                : original.getDpnIds();
        List<DpnIds> updatedDpnIds = (updated.getDpnIds() == null) ? new ArrayList<DpnIds>()
                : updated.getDpnIds();

        handleDeletedDpnsFromSchema(original, originalDpnIds, updatedDpnIds);
        handleNewlyAddedDpnsToSchema(original, originalDpnIds, updatedDpnIds);
    }

    /**
     * Does delete and add schema required.
     *
     * @param original
     *            the original
     * @param updated
     *            the updated
     * @return true, if successful
     */
    private boolean doesDeleteAndAddSchemaRequired(VtepConfigSchema original, VtepConfigSchema updated) {
        boolean delnAddRequired = false;
        if (!StringUtils.equalsIgnoreCase(original.getPortName(), updated.getPortName())) {
            delnAddRequired = true;
        } else if (original.getVlanId() != updated.getVlanId()) {
            delnAddRequired = true;
        } else if (original.getSubnet() != null && !original.getSubnet().equals(updated.getSubnet())) {
            delnAddRequired = true;
        } else if (original.getGatewayIp() != null && !original.getGatewayIp().equals(updated.getGatewayIp())) {
            delnAddRequired = true;
        } else if (!StringUtils.equalsIgnoreCase(original.getTransportZoneName(), updated.getTransportZoneName())) {
            delnAddRequired = true;
        } else if (!(original.getTunnelType().equals(updated.getTunnelType()) )) {
            delnAddRequired = true;
        }
        return delnAddRequired;
    }

    /**
     * Handle newly added dpns to schema.
     *
     * @param original
     *            the original
     * @param originalDpnIds
     *            the original dpn ids
     * @param updatedDpnIds
     *            the updated dpn ids
     */
    private void handleNewlyAddedDpnsToSchema(VtepConfigSchema original, List<DpnIds> originalDpnIds,
            List<DpnIds> updatedDpnIds) {
        LOG.trace("Handle Addition of DPNs from VTEP Original Dpn: {}. Updated Dpn: {}", originalDpnIds, updatedDpnIds) ;
        ArrayList<DpnIds> newlyAddedDpns = new ArrayList<>(updatedDpnIds);
        newlyAddedDpns.removeAll(originalDpnIds);
        LOG.debug("Newly added DPNs {} to VTEP config schema [{}].", newlyAddedDpns, original.getSchemaName());
        if (!newlyAddedDpns.isEmpty()) {
            VtepConfigSchema diffSchema = new VtepConfigSchemaBuilder(original).setDpnIds(newlyAddedDpns).build();
            String subnetCidr = ItmUtils.getSubnetCidrAsString(original.getSubnet());
            VtepIpPool vtepIpPool = getVtepIpPool(subnetCidr);
            LOG.debug("Adding of DPNs in Diff Schema: {}", diffSchema) ;
            addVteps(diffSchema, vtepIpPool);
        }
    }

    /**
     * Handle deleted dpns from schema.
     *
     * @param original
     *            the original
     * @param originalDpnIds
     *            the original dpn ids
     * @param updatedDpnIds
     *            the updated dpn ids
     */
    private void handleDeletedDpnsFromSchema(VtepConfigSchema original, List<DpnIds> originalDpnIds,
            List<DpnIds> updatedDpnIds) {
        ArrayList<DpnIds> deletedDpns = new ArrayList<>(originalDpnIds);
        deletedDpns.removeAll(updatedDpnIds);
        LOG.debug("DPNs to be removed DPNs {} from VTEP config schema [{}].", deletedDpns, original.getSchemaName());
        if (!deletedDpns.isEmpty()) {
            LOG.debug("Deleting of DPNs from VTEP Schema: {}. To be deleted Dpns: {}", original, deletedDpns) ;
            deleteVteps(original, ItmUtils.getDpnIdList(deletedDpns));
        }
    }

    /**
     * Gets all vtep config schemas.
     *
     * @return the all vtep config schemas
     */
    private List<VtepConfigSchema> getAllVtepConfigSchemas() {
        Optional<VtepConfigSchemas> schemas = ItmUtils.read(LogicalDatastoreType.CONFIGURATION,
                ItmUtils.getVtepConfigSchemasIdentifier(), this.dataBroker);
        if (schemas.isPresent()) {
            return schemas.get().getVtepConfigSchema();
        }
        return null;
    }

    /**
     * Adds the vteps.
     *
     * @param schema
     *            the schema
     * @param vtepIpPool
     *            the vtep ip pool
     */
    private void addVteps(VtepConfigSchema schema, VtepIpPool vtepIpPool) {
        if (schema.getDpnIds() == null || schema.getDpnIds().isEmpty()) {
            LOG.debug("DPN list is empty, skipping addVteps for schema: {}", schema);
            return;
        }

        String subnetCidr = ItmUtils.getSubnetCidrAsString(schema.getSubnet());
        if (vtepIpPool == null) {
            LOG.error("VTEP config pool not found for subnetCidr {}. Failed to add VTEPs for schema {}", subnetCidr,
                    schema);
            return;
        }
        TepCommandHelper tepCommandHelper = new TepCommandHelper(this.dataBroker);
        // Check this later
        String tunType ;
        Class<? extends TunnelTypeBase> tunnelType = schema.getTunnelType() ;
        if( tunnelType.equals(TunnelTypeVxlan.class))
            tunType = ITMConstants.TUNNEL_TYPE_VXLAN ;
        else
            tunType =  ITMConstants.TUNNEL_TYPE_GRE;
        tepCommandHelper.configureTunnelType(schema.getTransportZoneName(),
                StringUtils.upperCase(tunType));

        List<IpAddress> availableIps = vtepIpPool.getAvailableIpaddress();
        List<IpAddress> newlyAllocatedIps = new ArrayList<>();
        List<BigInteger> skippedDpnIds = new ArrayList<>();

        String gatewayIp = handleGatewayIp(schema.getGatewayIp());
        for (BigInteger dpnId : ItmUtils.getDpnIdList(schema.getDpnIds())) {
            IpAddress ipAddress = getAnAvailableIP(availableIps);
            if (ipAddress == null) {
                skippedDpnIds.add(dpnId);
                continue;
            }
            tepCommandHelper.createLocalCache(dpnId, schema.getPortName(), schema.getVlanId(),
                    String.valueOf(ipAddress.getValue()), subnetCidr, gatewayIp, schema.getTransportZoneName());
            newlyAllocatedIps.add(ipAddress);
        }
        if (!skippedDpnIds.isEmpty()) {
            LOG.error("No available IP addresses in the VTEP config pool {}, skipping VTEP configurations for DPN's {}",
                    subnetCidr, skippedDpnIds);
        }

        if (!newlyAllocatedIps.isEmpty()) {
            LOG.debug( "Delete OnCommit and buildTeps in NewlyAddedDpns");
            tepCommandHelper.deleteOnCommit();
            tepCommandHelper.buildTeps();
            allocateIpAddresses(newlyAllocatedIps, vtepIpPool, subnetCidr);
        }
    }

    /**
     * Handle gateway ip.
     *
     * @param gatewayIp
     *            the gateway ip
     * @return the string
     */
    private String handleGatewayIp(IpAddress gatewayIp) {
        String strGatewayIp = (gatewayIp == null) ? null : String.valueOf(gatewayIp.getValue());
        if (StringUtils.isBlank(strGatewayIp) || StringUtils.equals(ITMConstants.DUMMY_IP_ADDRESS, strGatewayIp)) {
            // To avoid a validation exception in TepCommandHelper
            strGatewayIp = null;
        }
        return strGatewayIp;
    }

    /**
     * Delete vteps.
     *
     * @param schema
     *            the schema
     * @param lstDpnIdsToBeDeleted
     *            the dpn ids list to be deleted
     */
    private void deleteVteps(VtepConfigSchema schema, List<BigInteger> lstDpnIdsToBeDeleted) {
        TepCommandHelper tepCommandHelper = new TepCommandHelper(this.dataBroker);
        List<IpAddress> freeIps = new ArrayList<>();

        String subnetCidr = ItmUtils.getSubnetCidrAsString(schema.getSubnet());
        String gatewayIp = handleGatewayIp(schema.getGatewayIp());

        for (BigInteger dpnId : lstDpnIdsToBeDeleted) {
            VtepsKey vtepkey = new VtepsKey(dpnId, schema.getPortName());

            InstanceIdentifier<Vteps> vpath = InstanceIdentifier.builder(TransportZones.class)
                    .child(TransportZone.class, new TransportZoneKey(schema.getTransportZoneName()))
                    .child(Subnets.class, new SubnetsKey(schema.getSubnet())).child(Vteps.class, vtepkey).build();

            Vteps vtep = null;
            Optional<Vteps> vtepOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, vpath, dataBroker);
            if (vtepOptional.isPresent()) {
                vtep = vtepOptional.get();
            } else {
                LOG.warn("VTEP doesn't exist for DPN [{}] and port [{}].", dpnId, schema.getPortName());
                continue;
            }

            IpAddress ipAddress = vtep.getIpAddress();
            tepCommandHelper.deleteVtep(dpnId, vtep.getPortname(), schema.getVlanId(),
                    String.valueOf(ipAddress.getValue()), subnetCidr, gatewayIp, schema.getTransportZoneName());

            freeIps.add(ipAddress);
        }
        LOG.debug( "Delete OnCommit in NewlyAddedDpns");
        tepCommandHelper.deleteOnCommit();
        deAllocateIpAddresses(freeIps, subnetCidr);
    }

    /**
     * Calculate available IPs from the subnet mask specified in the schema.
     * Pushes the available and allocated IP address to config DS.
     *
     * @param schema
     *            the schema
     */
    private VtepIpPool processAvailableIps(final VtepConfigSchema schema) {
        String subnetCidr = ItmUtils.getSubnetCidrAsString(schema.getSubnet());
        SubnetUtils subnetUtils = new SubnetUtils(subnetCidr);

        List<IpAddress> availableIps = calculateAvailableIps(subnetUtils, schema.getExcludeIpFilter(),
                schema.getGatewayIp());
        VtepIpPool vtepIpPool = new VtepIpPoolBuilder().setSubnetCidr(subnetCidr).setAvailableIpaddress(availableIps)
                .setAllocatedIpaddress(new ArrayList<IpAddress>()).build();

        MDSALUtil.syncWrite(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                ItmUtils.getVtepIpPoolIdentifier(subnetCidr), vtepIpPool);
        LOG.info("Vtep IP Pool with key:{} added to config DS", subnetCidr);
        return vtepIpPool;
    }

    /**
     * Gets the vtep ip pool.
     *
     * @param subnetCidr
     *            the subnet cidr
     * @return the vtep ip pool
     */
    private VtepIpPool getVtepIpPool(final String subnetCidr) {
        Optional<VtepIpPool> vtepIpPool = ItmUtils.read(LogicalDatastoreType.CONFIGURATION,
                ItmUtils.getVtepIpPoolIdentifier(subnetCidr), this.dataBroker);
        if (vtepIpPool.isPresent()) {
            return vtepIpPool.get();
        }
        return null;
    }

    /**
     * Delete vtep ip pool.
     *
     * @param subnetCidr
     *            the subnet cidr
     */
    private void deleteVtepIpPool(final String subnetCidr) {
        if (StringUtils.isNotBlank(subnetCidr)) {
            MDSALUtil.syncDelete(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                    ItmUtils.getVtepIpPoolIdentifier(subnetCidr));
            LOG.debug("Deleted Vtep IP Pool with key:{}", subnetCidr);
        }
    }

    /**
     * Gets the an available ip.
     *
     * @param availableIps
     *
     * @return the an available ip
     */
    private IpAddress getAnAvailableIP(List<IpAddress> availableIps) {
        // TODO: Sort IP Addresses, get the least value
        IpAddress ipAddress = null;
        if (availableIps != null && !availableIps.isEmpty()) {
            ipAddress = availableIps.remove(0);
        }
        return ipAddress;
    }

    /**
     * Allocate ip addresses.
     *
     * @param allocatedIps
     *            the allocated ips
     * @param vtepIpPool
     *            the vtep ip pool
     * @param subnetCidr
     *            the subnet cidr
     */
    private void allocateIpAddresses(List<IpAddress> allocatedIps, VtepIpPool vtepIpPool, String subnetCidr) {
        if (allocatedIps != null && !allocatedIps.isEmpty() && vtepIpPool != null) {
            // Remove from the available IP address list and add to allocated IP
            // address list.
            VtepIpPoolBuilder builder = new VtepIpPoolBuilder(vtepIpPool);
            if (builder.getAvailableIpaddress() != null) {
                builder.getAvailableIpaddress().removeAll(allocatedIps);
            }
            if (builder.getAllocatedIpaddress() == null) {
                builder.setAllocatedIpaddress(allocatedIps);
            } else {
                builder.getAllocatedIpaddress().addAll(allocatedIps);
            }

            MDSALUtil.syncWrite(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                    ItmUtils.getVtepIpPoolIdentifier(subnetCidr), builder.build());
        }
    }

    /**
     * De-allocate ip addresses.
     *
     * @param freeIps
     *            the free ips
     * @param subnetCidr
     *            the subnet cidr
     */
    private void deAllocateIpAddresses(List<IpAddress> freeIps, String subnetCidr) {
        VtepIpPool vtepIpPool = getVtepIpPool(subnetCidr);
        if (freeIps != null && !freeIps.isEmpty() && vtepIpPool != null) {
            // Remove from the allocated IP address list and add to available IP
            // address list.
            VtepIpPoolBuilder builder = new VtepIpPoolBuilder(vtepIpPool);
            if (builder.getAllocatedIpaddress() != null) {
                builder.getAllocatedIpaddress().removeAll(freeIps);
            }
            if (builder.getAvailableIpaddress() == null) {
                builder.setAvailableIpaddress(freeIps);
            } else {
                builder.getAvailableIpaddress().addAll(freeIps);
            }

            MDSALUtil.syncWrite(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                    ItmUtils.getVtepIpPoolIdentifier(subnetCidr), builder.build());
            LOG.debug("Vtep IP Pool with key:{} updated to config DS", subnetCidr);
        }
    }

    /**
     * Calculate available ips.
     *
     * @param subnetCidr
     *            the subnet cidr
     * @param excludeIpFilter
     *            the exclude ip filter
     * @param gatewayIp
     *            the gateway IP
     * @return the list
     */
    private List<IpAddress> calculateAvailableIps(SubnetUtils subnetUtils, String excludeIpFilter,
            IpAddress gatewayIp) {
        List<IpAddress> lstAvailableIps = new ArrayList<>();
        SubnetInfo subnetInfo = subnetUtils.getInfo();
        String[] arrIpAddresses = subnetInfo.getAllAddresses();

        for (String ipAddress : arrIpAddresses) {
            lstAvailableIps.add(new IpAddress(ipAddress.toCharArray()));
        }
        lstAvailableIps.remove(gatewayIp);
        lstAvailableIps.removeAll(ItmUtils.getExcludeIpAddresses(excludeIpFilter, subnetInfo));

        return lstAvailableIps;
    }
}
