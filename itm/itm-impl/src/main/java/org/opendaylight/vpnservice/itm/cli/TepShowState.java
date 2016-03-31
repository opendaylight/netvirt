/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.cli;

import com.google.common.base.Optional;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.TunnelsState;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.ArrayList;
import java.util.List;

@Command(scope = "tep", name = "show-state", description="Monitors tunnel state")

    public class TepShowState extends OsgiCommandSupport {

    private IITMProvider itmProvider;

    public void setItmProvider(IITMProvider itmProvider) {
        this.itmProvider = itmProvider;
    }

    @Override
    protected Object doExecute() throws Exception {
        /*
        DataBroker broker = itmProvider.getDataBroker();
        List<String> result = new ArrayList<String>();
        InstanceIdentifier<TunnelsState> path = InstanceIdentifier.builder(TunnelsState.class).build();
        Optional<TunnelsState> tunnels = ItmUtils.read(LogicalDatastoreType.OPERATIONAL, path, broker);
        if (tunnels.isPresent()) {
            itmProvider.showState(tunnels.get());
        }
        else
            System.out.println("No Logical Tunnels Exist");
            */
        System.out.println( "Logical Tunnels state is not currently supported");
        return null;
    }
}
