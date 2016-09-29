package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class SubnetMacHandler extends AsyncDataTreeChangeListenerBase<VpnPortipToPort, SubnetMacHandler> {

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;

    public SubnetMacHandler(DataBroker dataBroker, IMdsalApiManager mdsalManager) {
        super(VpnPortipToPort.class, SubnetMacHandler.class);
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
    }

    public void start() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnPortipToPort> getWildCardPath() {
        return InstanceIdentifier.create(NeutronVpnPortipPortData.class).child(VpnPortipToPort.class);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort value) {
        if (value.isSubnetIp()) {
            WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
            VpnUtil.setupSubnetMacIntoVpnInstance(dataBroker, mdsalManager, value.getVpnName(),
                    value.getMacAddress(), writeTx, NwConstants.DEL_FLOW);
            writeTx.submit();
        }}

    @Override
    protected void update(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort dataObjectModificationBefore,
            VpnPortipToPort dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort value) {
        if (value.isSubnetIp()) {
            WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
            VpnUtil.setupSubnetMacIntoVpnInstance(dataBroker, mdsalManager, value.getVpnName(),
                    value.getMacAddress(), writeTx, NwConstants.ADD_FLOW);
            writeTx.submit();
        }
    }

    @Override
    protected SubnetMacHandler getDataTreeChangeListener() {
        return this;
    }

}
