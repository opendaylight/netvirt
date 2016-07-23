/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.api.ICloudServiceChain;
import org.opendaylight.netvirt.cloudservicechain.listeners.ElanDpnInterfacesListener;
import org.opendaylight.netvirt.cloudservicechain.listeners.VpnPseudoPortListener;
import org.opendaylight.netvirt.cloudservicechain.listeners.VrfListener;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnPseudoPortCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudServiceChainProvider implements BindingAwareProvider, ICloudServiceChain, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CloudServiceChainProvider.class);
    private IMdsalApiManager mdsalManager;
    private FibRpcService fibRpcService;
    VPNServiceChainHandler vpnServiceChainHandler;
    ElanServiceChainHandler elanServiceChainHandler;

    // Listeners
    VpnPseudoPortListener vpnPseudoPortListener;
    VrfListener labelListener;
    ElanDpnInterfacesListener elanDpnInterfacesListener;

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

            elanServiceChainHandler = new ElanServiceChainHandler(dataBroker, mdsalManager);

        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setFibRpcService(FibRpcService fibManager) {
        this.fibRpcService = fibManager;
        if ( this.vpnServiceChainHandler != null ) {
            this.vpnServiceChainHandler.setFibRpcService(fibManager);
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
     * @see org.opendaylight.netvirtchain.api.IVpnServiceChain#removeVpnPseudoPortFlows(java.lang.String, int)
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
                 elanName, tableId,scfTag, elanLportTag, addOrRemove);
        elanServiceChainHandler.programElanScfPipeline(elanName, tableId, scfTag, elanLportTag, isLastServiceChain,
                                                       addOrRemove);

    }

    @Override
    public void removeElanPseudoPortFlows(String elanName, int elanPseudoLportTag) {
        LOG.info("ELAN Service chaining :removeElanPseudoPortFlows [Started] elanPseudoLportTag={}", elanPseudoLportTag);
        elanServiceChainHandler.removeElanPseudoPortFlows(elanName, elanPseudoLportTag);
    }

}
