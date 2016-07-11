/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservicechain;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservicechain.listeners.VpnPseudoPortListener;
import org.opendaylight.vpnservicechain.listeners.VpnToDpnListener;
import org.opendaylight.vpnservicechain.listeners.VrfListener;
import org.opendaylight.vpnservicechain.utils.VpnPseudoPortCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.cloudservicechain.api.ICloudServiceChain;
import org.opendaylight.vpnservicechain.listeners.ElanDpnInterfacesListener;
import org.opendaylight.vpnservicechain.listeners.NodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudServiceChainProvider implements BindingAwareProvider, ICloudServiceChain, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CloudServiceChainProvider.class);
    private IMdsalApiManager mdsalManager;
    private FibRpcService fibRpcService;
    private NotificationService notificationService;
    VPNServiceChainHandler vpnServiceChainHandler;
    ElanServiceChainHandler elanServiceChainHandler;

    // Listeners
    VpnPseudoPortListener vpnPseudoPortListener;
    VrfListener labelListener;
    ElanDpnInterfacesListener elanDpnInterfacesListener;
    VpnToDpnListener vpnToDpnListener;
    NodeListener nodeListener;

    public CloudServiceChainProvider(NotificationService notificationServiceDependency,
                                     IMdsalApiManager mdsalManager, FibRpcService fibRpcService) {
        this.notificationService = notificationServiceDependency;
        this.mdsalManager = mdsalManager;
        this.fibRpcService = fibRpcService;
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("VpnServiceChainProvider Session Initiated");
        try {
            final DataBroker dataBroker = session.getSALService(DataBroker.class);
            VpnPseudoPortCache.createVpnPseudoPortCache();
            vpnServiceChainHandler = new VPNServiceChainHandler(dataBroker, fibRpcService);
            vpnServiceChainHandler.setMdsalManager(mdsalManager);

            vpnPseudoPortListener = new VpnPseudoPortListener(dataBroker);
            labelListener = new VrfListener(dataBroker, mdsalManager);
            elanDpnInterfacesListener = new ElanDpnInterfacesListener(dataBroker, mdsalManager);
            vpnToDpnListener = new VpnToDpnListener(dataBroker, mdsalManager);
            nodeListener = new NodeListener(dataBroker, mdsalManager);

            notificationService.registerNotificationListener(vpnToDpnListener);

            elanServiceChainHandler = new ElanServiceChainHandler(dataBroker, mdsalManager);

        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
    }


    @Override
    public void close() throws Exception {

    }


    @Override
    public void programVpnToScfPipeline(String vpnId, short tableId, int scfTag, int lportTag, int addOrRemove) {
        LOG.info("L3VPN Service chaining :programVpnToScfPipeline [Started] {} {} {} {} {}",
                 vpnId, tableId,scfTag, lportTag, addOrRemove);
        vpnServiceChainHandler.programVpnToScfPipeline(vpnId, tableId, scfTag, lportTag, addOrRemove);
    }

    @Override
    public void programScfToVpnPipeline(String vpnId, int scfTag, int scsTag, long dpnId, int lportTag,
                                        boolean isLastServiceChain, int addOrRemove) {
        LOG.info("L3VPN Service chaining :programScfToVpnPipeline [Started] {} {} {} {}", vpnId, scfTag,
                dpnId, lportTag);
        vpnServiceChainHandler.programScfToVpnPipeline(vpnId, scfTag, scsTag, dpnId, lportTag, isLastServiceChain,
                                                       addOrRemove);
    }

    /* (non-Javadoc)
     * @see org.opendaylight.vpnservicechain.api.IVpnServiceChain#removeVpnPseudoPortFlows(java.lang.String, int)
     */
    @Override
    public void removeVpnPseudoPortFlows(String vpnInstanceName, int vpnPseudoLportTag) {
        LOG.info("L3VPN Service chaining :removeVpnPseudoPortFlows [Started] vpnPseudoLportTag={}", vpnPseudoLportTag);
        vpnServiceChainHandler.removeVpnPseudoPortFlows(vpnInstanceName, vpnPseudoLportTag);
    }

    @Override
    public void programElanScfPipeline(String elanName, short tableId, int scfTag, int elanLportTag,
                                       boolean isLastServiceChain, int addOrRemove) {
        LOG.info("ELAN Service chaining :programElanScfPipeline [Started] {} {} {} {} {}",
                 elanName, tableId, scfTag, elanLportTag, addOrRemove);
        elanServiceChainHandler.programElanScfPipeline(elanName, tableId, scfTag, elanLportTag, addOrRemove);
    }

    @Override
    public void programElanScfPipeline(String elanName, short tableId, int scfTag, int elanLportTag, int addOrRemove) {
        LOG.info("ELAN Service chaining :programElanScfPipeline [Started] {} {} {} {} {}",
                 elanName, tableId, scfTag, elanLportTag, addOrRemove);
        elanServiceChainHandler.programElanScfPipeline(elanName, tableId, scfTag, elanLportTag, addOrRemove);
    }

    @Override
    public void removeElanPseudoPortFlows(String elanName, int elanPseudoLportTag) {
        LOG.info("ELAN Service chaining :removeElanPseudoPortFlows [Started] elanPseudoLportTag={}", elanPseudoLportTag);
        elanServiceChainHandler.removeElanPseudoPortFlows(elanName, elanPseudoLportTag);
    }

}
