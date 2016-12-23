/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.jobs;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeMplsOverGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.DcGatewayIpList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TunnelStateAddJob implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(TunnelStateAddJob.class);
    private DataBroker dataBroker;
    private StateTunnelList tunnelState;
    private IFibManager fibManager;

    public TunnelStateAddJob(DataBroker dataBroker,
            StateTunnelList tunnelState, IFibManager fibManager) {
        this.dataBroker = dataBroker;
        this.tunnelState = tunnelState;
        this.fibManager = fibManager;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        IpAddress dcGwIp = tunnelState.getDstInfo().getTepIp();
        if (dcGwIp == null) {
            return futures;
        }
        String dcGwIpAddress = String.valueOf(dcGwIp.getValue());
        List<String> availableDcGws = getDcGwIps();
        if (availableDcGws == null || availableDcGws.isEmpty()) {
            return futures;
        }
        // TODO: Remove this check once logic for getting all possible LB Group is ready
        Preconditions.checkArgument(!(availableDcGws.size() > 2));
        BigInteger dpId = new BigInteger(tunnelState.getSrcInfo().getTepDeviceId());
        if (availableDcGws.contains(dcGwIpAddress) && availableDcGws.size() > 1) {
            return fibManager.programDcGwLoadBalancingGroup(tx, dcGwIp, availableDcGws, dpId);
        }
        return futures;
    }

    private List<String> getDcGwIps() {
        InstanceIdentifier<DcGatewayIpList> dcGatewayIpListid =
                InstanceIdentifier.builder(DcGatewayIpList.class).build();
        Optional<DcGatewayIpList> dcGatewayIpListConfig =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, dcGatewayIpListid);
        if (dcGatewayIpListConfig.isPresent()) {
            DcGatewayIpList containerList = dcGatewayIpListConfig.get();
            if (containerList != null) {
                return containerList.getDcGatewayIp()
                        .stream()
                        .filter(dcGwIp -> dcGwIp.getTunnnelType().equals(TunnelTypeMplsOverGre.class))
                        .map(dcGwIp -> String.valueOf(dcGwIp.getIpAddress().getValue())).sorted()
                        .collect(Collectors.toList());
            }
        }
        return null;
    }
}