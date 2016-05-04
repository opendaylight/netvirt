/*
 * Copyright (c) 2016 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.shell;


import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.interfacemgr.shell.IfmCLIUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Command(scope = "vxlan", name = "show", description = "view the configured vxlan ports")
public class ShowVxlan extends OsgiCommandSupport {
    private static final Logger logger = LoggerFactory.getLogger(ShowVxlan.class);
    private IInterfaceManager interfaceManager;
    private DataBroker dataBroker;

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setDataBroker(DataBroker dataBroker){
        this.dataBroker = dataBroker;
    }

    @Override
    protected Object doExecute() throws Exception {
        logger.debug("Executing show Vxlan command");
        List<Interface> vxlanList = interfaceManager.getVxlanInterfaces();
        if (!vxlanList.isEmpty()) {
            IfmCLIUtil.showVxlanHeaderOutput();
        }
        for (Interface iface : vxlanList) {
            InterfaceInfo ifaceState = interfaceManager.getInterfaceInfoFromOperationalDataStore(iface.getName());
            IfmCLIUtil.showVxlanOutput(iface, ifaceState);
        }
        return null;
    }
}
