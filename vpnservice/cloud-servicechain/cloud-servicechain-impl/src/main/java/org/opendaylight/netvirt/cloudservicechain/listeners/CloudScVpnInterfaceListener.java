package org.opendaylight.netvirt.cloudservicechain.listeners;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.globals.InterfaceServiceUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.cloudservicechain.CloudServiceChainConstants;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudScVpnInterfaceListener
    extends AsyncDataTreeChangeListenerBase<VpnInterface, CloudScVpnInterfaceListener>
    implements AutoCloseable {

    static final Logger LOG = LoggerFactory.getLogger(CloudScVpnInterfaceListener.class);

    private final DataBroker dataBroker;

    public CloudScVpnInterfaceListener(final DataBroker dataBroker) {
        super(VpnInterface.class, CloudScVpnInterfaceListener.class);

        this.dataBroker = dataBroker;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected CloudScVpnInterfaceListener getDataTreeChangeListener() {
        return CloudScVpnInterfaceListener.this;
    }

    @Override
    protected InstanceIdentifier<VpnInterface> getWildCardPath() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInterface> key, VpnInterface dataObjectModification) {
    }

    @Override
    protected void update(InstanceIdentifier<VpnInterface> key, VpnInterface dataObjectModificationBefore,
                          VpnInterface dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<VpnInterface> key, VpnInterface vpnIface) {
        String vpnName = vpnIface.getVpnInstanceName();
        Optional<VpnToPseudoPortData> optScfInfoForVpn = getScfInfoForVpn(vpnName);
        if (!optScfInfoForVpn.isPresent()) {
            LOG.trace("Vpn {} is not related to ServiceChaining. No further action", vpnName);
            return;
        }

        bindScfToVpnInterface(vpnName, vpnIface, optScfInfoForVpn.get());
    }

    private void bindScfToVpnInterface(String vpnName, VpnInterface vpnIface, VpnToPseudoPortData vpnToPseudoPortData) {
        String ifName = vpnIface.getKey().getName();

        BoundServices boundServices =
            InterfaceServiceUtil.getBoundServices(ifName, NwConstants.SCF_SERVICE_INDEX,
                                                  CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY,
                                                  CloudServiceChainConstants.COOKIE_SCF_BASE,
                                                  instructions);
        interfaceManager.bindService(ifName, ServiceModeIngress.class, boundServices);
    }

    private Optional<VpnToPseudoPortData> getScfInfoForVpn(String vpnName) {
        String vpnRd = VpnServiceChainUtils.getVpnRd(dataBroker, vpnName);
        if (vpnRd == null) {
            LOG.trace("Checking if Vpn {} participates in SC. Could not find its RD", vpnName);
            return Optional.absent();
        }

        return VpnServiceChainUtils.getVpnPseudoPortData(dataBroker, vpnRd);
    }



}
