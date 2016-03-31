/*
 * Copyright (c) 2015, 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.impl;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.vpnservice.itm.cli.TepCommandHelper;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.vpnservice.itm.listeners.TransportZoneListener;
import org.opendaylight.vpnservice.itm.listeners.VtepConfigSchemaListener;
import org.opendaylight.vpnservice.itm.rpc.ItmManagerRpcService;
import org.opendaylight.vpnservice.itm.snd.ITMStatusMonitor;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.VtepConfigSchemas;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.VtepConfigSchema;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.VtepConfigSchemaBuilder;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.VtepConfigSchemas;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.vtep.config.schemas.VtepConfigSchema;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.vtep.config.schemas.VtepConfigSchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class ItmProvider implements BindingAwareProvider, AutoCloseable, IITMProvider /*,ItmStateService */{

    private static final Logger LOG = LoggerFactory.getLogger(ItmProvider.class);
    private IInterfaceManager interfaceManager;
    private ITMManager itmManager;
    private IMdsalApiManager mdsalManager;
    private DataBroker dataBroker;
    private NotificationPublishService notificationPublishService;
    private ItmManagerRpcService itmRpcService ;
    private IdManagerService idManager;
    private NotificationService notificationService;
    private TepCommandHelper tepCommandHelper;
    private TransportZoneListener tzChangeListener;
    private VtepConfigSchemaListener vtepConfigSchemaListener;
    private RpcProviderRegistry rpcProviderRegistry;
    private static final ITMStatusMonitor itmStatusMonitor = ITMStatusMonitor.getInstance();
    static short flag = 0;

    public ItmProvider() {
        LOG.info("ItmProvider Before register MBean");
        itmStatusMonitor.registerMbean();
    }

    public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

    public RpcProviderRegistry getRpcProviderRegistry() {
        return this.rpcProviderRegistry;
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("ItmProvider Session Initiated");
        itmStatusMonitor.reportStatus("STARTING");
        try {
            dataBroker = session.getSALService(DataBroker.class);
            idManager = getRpcProviderRegistry().getRpcService(IdManagerService.class);

            itmManager = new ITMManager(dataBroker);
            tzChangeListener = new TransportZoneListener(dataBroker, idManager) ;
            itmRpcService = new ItmManagerRpcService(dataBroker, idManager);
            vtepConfigSchemaListener = new VtepConfigSchemaListener(dataBroker);
            tepCommandHelper = new TepCommandHelper(dataBroker);
            final BindingAwareBroker.RpcRegistration<ItmRpcService> rpcRegistration = getRpcProviderRegistry().addRpcImplementation(ItmRpcService.class, itmRpcService);
            itmRpcService.setMdsalManager(mdsalManager);
            itmManager.setMdsalManager(mdsalManager);
            itmManager.setNotificationPublishService(notificationPublishService);
            itmManager.setMdsalManager(mdsalManager);
            tzChangeListener.setMdsalManager(mdsalManager);
            tzChangeListener.setItmManager(itmManager);
            tzChangeListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
            tepCommandHelper = new TepCommandHelper(dataBroker);
            tepCommandHelper.setInterfaceManager(interfaceManager);
            createIdPool();
            itmStatusMonitor.reportStatus("OPERATIONAL");
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
            itmStatusMonitor.reportStatus("ERROR");
        }
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }
    
    public void setMdsalApiManager(IMdsalApiManager mdsalMgr) {
        this.mdsalManager = mdsalMgr;
    }
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void close() throws Exception {
        if (itmManager != null) {
            itmManager.close();
        }
        if (tzChangeListener != null) {
            tzChangeListener.close();
        }

        LOG.info("ItmProvider Closed");
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(ITMConstants.ITM_IDPOOL_NAME)
            .setLow(ITMConstants.ITM_IDPOOL_START)
            .setHigh(new BigInteger(ITMConstants.ITM_IDPOOL_SIZE).longValue())
            .build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if ((result != null) && (result.get().isSuccessful())) {
                LOG.debug("Created IdPool for ITM Service");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for ITM Service",e);
        }
    }

    @Override
    public DataBroker getDataBroker() {
        return dataBroker;
    }
    @Override
    public void createLocalCache(BigInteger dpnId, String portName, Integer vlanId, String ipAddress, String subnetMask,
                    String gatewayIp, String transportZone) {
        if (tepCommandHelper != null) {
            tepCommandHelper.createLocalCache(dpnId, portName, vlanId, ipAddress, subnetMask, gatewayIp, transportZone);
        } else {
            LOG.trace("tepCommandHelper doesnt exist");
        }
    }

    @Override
    public void commitTeps() {
        try {
                tepCommandHelper.deleteOnCommit();
                tepCommandHelper.buildTeps();
        } catch (Exception e) {
            LOG.debug("unable to configure teps" + e.toString());
        }
    }

    @Override
    public void showTeps() {
        tepCommandHelper.showTeps(itmManager.getTunnelMonitorEnabledFromConfigDS(),
                itmManager.getTunnelMonitorIntervalFromConfigDS());
    }

    public void deleteVtep(BigInteger dpnId, String portName, Integer vlanId, String ipAddress, String subnetMask,
            String gatewayIp, String transportZone) {
        try {
           tepCommandHelper.deleteVtep(dpnId,  portName, vlanId, ipAddress, subnetMask, gatewayIp, transportZone);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void configureTunnelType(String transportZone, String tunnelType) {
        LOG .debug("ItmProvider: configureTunnelType {} for transportZone {}", tunnelType, transportZone);
        tepCommandHelper.configureTunnelType(transportZone,tunnelType);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.opendaylight.vpnservice.itm.api.IITMProvider#addVtepConfigSchema(org.
     * opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.
     * rev151102.vtep.config.schemas.VtepConfigSchema)
     */
    @Override
    public void addVtepConfigSchema(VtepConfigSchema vtepConfigSchema) {
        VtepConfigSchema validatedSchema = ItmUtils.validateForAddVtepConfigSchema(vtepConfigSchema,
                getAllVtepConfigSchemas());

        String schemaName = validatedSchema.getSchemaName();
        VtepConfigSchema existingSchema = getVtepConfigSchema(schemaName);
        if (existingSchema != null) {
            Preconditions.checkArgument(false, String.format("VtepConfigSchema [%s] already exists!", schemaName));
        }
        MDSALUtil.syncWrite(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                ItmUtils.getVtepConfigSchemaIdentifier(schemaName), validatedSchema);
        LOG.debug("Vtep config schema {} added to config DS", schemaName);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.opendaylight.vpnservice.itm.api.IITMProvider#getVtepConfigSchema(java
     * .lang.String)
     */
    @Override
    public VtepConfigSchema getVtepConfigSchema(String schemaName) {
        Optional<VtepConfigSchema> schema = ItmUtils.read(LogicalDatastoreType.CONFIGURATION,
                ItmUtils.getVtepConfigSchemaIdentifier(schemaName), this.dataBroker);
        if (schema.isPresent()) {
            return schema.get();
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.opendaylight.vpnservice.itm.api.IITMProvider#getAllVtepConfigSchemas(
     * )
     */
    @Override
    public List<VtepConfigSchema> getAllVtepConfigSchemas() {
        Optional<VtepConfigSchemas> schemas = ItmUtils.read(LogicalDatastoreType.CONFIGURATION,
                ItmUtils.getVtepConfigSchemasIdentifier(), this.dataBroker);
        if (schemas.isPresent()) {
            return schemas.get().getVtepConfigSchema();
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.opendaylight.vpnservice.itm.api.IITMProvider#updateVtepSchema(java.
     * lang.String, java.util.List, java.util.List)
     */
    @Override
    public void updateVtepSchema(String schemaName, List<BigInteger> lstDpnsForAdd, List<BigInteger> lstDpnsForDelete) {
        LOG.trace("Updating VTEP schema {} by adding DPN's {} and deleting DPN's {}.", schemaName, lstDpnsForAdd,
                lstDpnsForDelete);

        VtepConfigSchema schema = ItmUtils.validateForUpdateVtepSchema(schemaName, lstDpnsForAdd, lstDpnsForDelete,
                this);
        if (ItmUtils.getDpnIdList(schema.getDpnIds()) == null) {
            VtepConfigSchemaBuilder builder = new VtepConfigSchemaBuilder(schema);
            builder.setDpnIds(schema.getDpnIds());
            schema = builder.build();
        } else {
            if (lstDpnsForAdd != null && !lstDpnsForAdd.isEmpty()) {
                ItmUtils.getDpnIdList(schema.getDpnIds()).addAll(lstDpnsForAdd);
            }
            if (lstDpnsForDelete != null && !lstDpnsForDelete.isEmpty()) {
                 ItmUtils.getDpnIdList(schema.getDpnIds()).removeAll(lstDpnsForDelete);
            }
        }
        MDSALUtil.syncWrite(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                ItmUtils.getVtepConfigSchemaIdentifier(schemaName), schema);
        LOG.debug("Vtep config schema {} updated to config DS with DPN's {}", schemaName, ItmUtils.getDpnIdList(schema.getDpnIds()));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.opendaylight.vpnservice.itm.api.IITMProvider#deleteAllVtepSchemas()
     */
    @Override
    public void deleteAllVtepSchemas() {
        List<VtepConfigSchema> lstSchemas = getAllVtepConfigSchemas();
        if (lstSchemas != null && !lstSchemas.isEmpty()) {
            for (VtepConfigSchema schema : lstSchemas) {
                MDSALUtil.syncDelete(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                        ItmUtils.getVtepConfigSchemaIdentifier(schema.getSchemaName()));
            }
        }
        LOG.debug("Deleted all Vtep schemas from config DS");
    }

    public void configureTunnelMonitorEnabled(boolean monitorEnabled) {
        tepCommandHelper.configureTunnelMonitorEnabled(monitorEnabled);
    }

    public void configureTunnelMonitorInterval(int interval) {
        tepCommandHelper.configureTunnelMonitorInterval(interval);
    }
}
