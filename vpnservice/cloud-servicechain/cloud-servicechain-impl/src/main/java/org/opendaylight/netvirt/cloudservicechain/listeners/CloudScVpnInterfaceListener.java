package org.opendaylight.netvirt.cloudservicechain.listeners;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.globals.InterfaceServiceUtil;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.netvirt.cloudservicechain.CloudServiceChainConstants;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg2;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudScVpnInterfaceListener
    extends AsyncDataTreeChangeListenerBase<VpnInterface, CloudScVpnInterfaceListener>
    implements AutoCloseable {

    static final Logger LOG = LoggerFactory.getLogger(CloudScVpnInterfaceListener.class);

    private final DataBroker dataBroker;
    private final IInterfaceManager interfaceManager;

    public CloudScVpnInterfaceListener(final DataBroker dataBroker, final IInterfaceManager ifaceMgr) {
        super(VpnInterface.class, CloudScVpnInterfaceListener.class);

        this.dataBroker = dataBroker;
        this.interfaceManager = ifaceMgr;
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

        bindScfOnVpnInterface(vpnIface, optScfInfoForVpn.get());
    }

    private void bindScfOnVpnInterface(VpnInterface vpnIface, VpnToPseudoPortData scfInfoForVpn) {
        String ifName = vpnIface.getKey().getName();

        Action loadReg2Action = new ActionRegLoad(1, NxmNxReg2.class, 0, 31, scfInfoForVpn.getScfTag()).buildAction();
        List<Instruction> instructions =
            Arrays.asList(MDSALUtil.buildApplyActionsInstruction(Collections.singletonList(loadReg2Action)),
                          MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.SCF_DOWN_SUB_FILTER_TCP_BASED_TABLE,
                                                                    1 /*instructionKey, not sure why it is needed*/));
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
