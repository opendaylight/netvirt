/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.impl;

import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.nic.mapping.api.IntentMappingService;
import org.opendaylight.nic.utils.MdsalUtils;
import org.opendaylight.vpnservice.utils.IidFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.AddVpnEndpointInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.FailoverType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.MplsLabels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.MplsLabelsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.RemoveVpnEndpointInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.RemoveVpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.VpnintentService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.Vpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.VpnsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.vpn.intent.Endpoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.vpn.intent.EndpointBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.vpn.intent.EndpointKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.vpns.VpnIntents;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.vpns.VpnIntentsKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class VpnintentProvider implements VpnintentService, BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnintentProvider.class);
    public static final InstanceIdentifier<MplsLabels> LABELS_IID = IidFactory.getMplsLabelsIid();
    public static final InstanceIdentifier<Vpns> VPN_IID = IidFactory.getVpnsIid();
    public static final InstanceIdentifier<VpnIntents> VPN_INTENT_IID = IidFactory.getVpnIntentIid();
    public static final InstanceIdentifier<Endpoint> ENDPOINT_IID = IidFactory.getEndpointIid();

    private DataBroker dataBroker;
    private IntentMappingService intentMappingService;
    private BindingAwareBroker.RpcRegistration<VpnintentService> rpcRegistration = null;
    private MdsalUtils mdsal;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("VpnintentProvider Session Initiated");
        dataBroker = session.getSALService(DataBroker.class);
        rpcRegistration = session.addRpcImplementation(VpnintentService.class, this);
        this.mdsal = new MdsalUtils(this.dataBroker);

        // Load IntentMappingService Reference
        loadIntentMappingServiceReference();

        Vpns vpns = new VpnsBuilder().build();
        MplsLabels labels = new MplsLabelsBuilder().build();

        // Initialize MD-SAL data store for vpn-intents and mpls-labels
        initDatastore(LogicalDatastoreType.CONFIGURATION, VPN_IID, vpns);
        initDatastore(LogicalDatastoreType.OPERATIONAL, LABELS_IID, labels);
    }

    @Override
    public void close() throws Exception {
        LOG.info("VpnintentProvider Closed");
    }

    private <T extends DataObject> void initDatastore(LogicalDatastoreType store, InstanceIdentifier<T> iid, T object) {
        // Put data to MD-SAL data store
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        transaction.put(store, iid, object);

        // Perform the tx.submit asynchronously
        Futures.addCallback(transaction.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.info("initDatastore for VPN-Intents: transaction succeeded");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("initDatastore for VPN-Intents: transaction failed");
            }
        });
        LOG.info("initDatastore: data populated: {}, {}, {}", store, iid, object);
    }

    @Override
    public Future<RpcResult<Void>> removeVpn(RemoveVpnInput input) {
        InstanceIdentifier<VpnIntents> vpnIdentifier = InstanceIdentifier.builder(Vpns.class)
                .child(VpnIntents.class, new VpnIntentsKey(input.getVpnName())).build();
        MappingServiceManager msManager = new MappingServiceManager(intentMappingService);
        MplsLabelManagerService mplsManager = new MplsLabelManagerService(dataBroker);

        VpnIntents vpn = getVpn(input.getVpnName());

        if (vpn.getEndpoint() != null && vpn.getEndpoint().size() > 0) {
            for (Endpoint endpoint : vpn.getEndpoint()) {
                // Release MPLS label
                mplsManager.deleteLabel(endpoint);

                // Remove all intents related to this endpoint
                IntentServiceManager intentManager = new IntentServiceManager(dataBroker);
                intentManager.removeIntentsByEndpoint(endpoint.getSiteName());

                // Remove info from Mapping Service
                msManager.delete(endpoint.getSiteName());
            }
        }

        mdsal.delete(LogicalDatastoreType.CONFIGURATION, vpnIdentifier);
        LOG.info("Deleted VPN {}", input.getVpnName());
        return Futures.immediateFuture(RpcResultBuilder.<Void> success().build());
    }

    @Override
    public Future<RpcResult<Void>> addVpnEndpoint(AddVpnEndpointInput input) {
        Endpoint currentEndpoint = new EndpointBuilder().setIpPrefix(input.getIpPrefix())
                .setSiteName(input.getSiteName()).setSwitchPortId(input.getSwitchPortId())
                .setKey(new EndpointKey(input.getSiteName())).build();
        VpnIntents vpn = getVpn(input.getVpnName());
        String failOverType = null;
        if (vpn.isPathProtection() && vpn.getFailoverType()!= null) {
            if (vpn.getFailoverType().equals(FailoverType.FastReroute)) {
                failOverType = IntentServiceManager.FAST_REROUTE;
            } else if(vpn.getFailoverType().equals(FailoverType.SlowReroute)) {
                failOverType = IntentServiceManager.SLOW_REROUTE;
            }
        }

        MplsLabelManagerService mplsManager = new MplsLabelManagerService(dataBroker);

        // Get unique MPLS label
        Long mplsLabel = mplsManager.getUniqueLabel(currentEndpoint);

        // Add info into Mapping Service
        MappingServiceManager msManager = new MappingServiceManager(intentMappingService);
        msManager.add(currentEndpoint.getSiteName(), extractIP(currentEndpoint.getIpPrefix()),
                currentEndpoint.getSwitchPortId(), mplsLabel, null);

        if (vpn.getEndpoint() != null && vpn.getEndpoint().size() > 0) {
            IntentServiceManager intentManager = new IntentServiceManager(dataBroker);

            for (Endpoint member : vpn.getEndpoint()) {
                // Create mesh of Intents
                intentManager.addIntent(member.getSiteName(), currentEndpoint.getSiteName(),
                        IntentServiceManager.ACTION_ALLOW, failOverType);
                intentManager.addIntent(currentEndpoint.getSiteName(), member.getSiteName(),
                        IntentServiceManager.ACTION_ALLOW, failOverType);
            }
        }
        // Associate endpoint with VPN
        addEndpointToVpn(vpn, currentEndpoint);

        return Futures.immediateFuture(RpcResultBuilder.<Void> success().build());
    }

    /**
     * @param IpPrefix
     *            object
     * @return String representation of IP prefix
     */
    private String extractIP(IpPrefix ipPrefix) {
        String ip = null;
        if (ipPrefix.getIpv4Prefix() != null) {
            ip = ipPrefix.getIpv4Prefix().getValue();
        } else if (ipPrefix.getIpv6Prefix() != null) {
            ip = ipPrefix.getIpv6Prefix().getValue();
        }
        return ip;
    }

    /**
     * @param vpnName
     *            VPN name
     * @return VPN instance
     */
    private VpnIntents getVpn(String vpnName) {
        InstanceIdentifier<VpnIntents> identifier = InstanceIdentifier.builder(Vpns.class)
                .child(VpnIntents.class, new VpnIntentsKey(vpnName)).build();

        VpnIntents vpnIntents = mdsal.read(LogicalDatastoreType.CONFIGURATION, identifier);
        Preconditions.checkNotNull(vpnIntents);
        return vpnIntents;
    }

    @Override
    public Future<RpcResult<Void>> removeVpnEndpoint(RemoveVpnEndpointInput input) {
        Endpoint endpoint = getEndpoint(input.getVpnName(), input.getSiteName());

        // Release MPLS label
        MplsLabelManagerService mplsManager = new MplsLabelManagerService(dataBroker);
        mplsManager.deleteLabel(endpoint);

        // Remove all intents related to this endpoint
        IntentServiceManager intentManager = new IntentServiceManager(dataBroker);
        intentManager.removeIntentsByEndpoint(input.getSiteName());

        // Remove endpoint from VPN
        removeEndpointFromVpn(input.getVpnName(), input.getSiteName());

        return Futures.immediateFuture(RpcResultBuilder.<Void> success().build());
    }

    /**
     * @param siteName
     *            Site name of the VPN member
     * @return VPN member (Endpoint)
     */
    private Endpoint getEndpoint(String vpnName, String siteName) {
        InstanceIdentifier<Endpoint> endpointID = InstanceIdentifier.builder(Vpns.class)
                .child(VpnIntents.class, new VpnIntentsKey(vpnName)).child(Endpoint.class, new EndpointKey(siteName))
                .build();

        return mdsal.read(LogicalDatastoreType.CONFIGURATION, endpointID);
    }

    /**
     * @param vpnName
     *            VPN name
     * @param siteName
     *            Site name
     */
    private void removeEndpointFromVpn(String vpnName, String siteName) {
        InstanceIdentifier<Endpoint> identifier = InstanceIdentifier.builder(Vpns.class)
                .child(VpnIntents.class, new VpnIntentsKey(vpnName)).child(Endpoint.class, new EndpointKey(siteName))
                .build();

        mdsal.delete(LogicalDatastoreType.CONFIGURATION, identifier);
        LOG.info("Deleted VPN member : {} from VPN: {}", siteName, vpnName);
    }

    /**
     * @param vpn
     *            VPN
     * @param vpnMember
     *            VPN member (endpoint)
     */
    private void addEndpointToVpn(VpnIntents vpn, Endpoint vpnMember) {
        InstanceIdentifier<Endpoint> identifier = InstanceIdentifier.builder(Vpns.class)
                .child(VpnIntents.class, vpn.getKey())
                .child(Endpoint.class, vpnMember.getKey()).build();

        mdsal.put(LogicalDatastoreType.CONFIGURATION, identifier, vpnMember);
        LOG.info("Added VPN member : {} to VPN: {}", vpnMember.getSiteName(), vpn.getVpnName());
    }

    /**
     * Load IntentMappingService reference
     */
    private void loadIntentMappingServiceReference() {
        ServiceReference<?> serviceReference = getBundleCtx().getServiceReference(IntentMappingService.class);
        intentMappingService = (IntentMappingService) getBundleCtx().getService(serviceReference);
    }

    private BundleContext getBundleCtx() {
        return FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    }
}
