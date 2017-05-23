/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.IAclServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;

@Singleton
public class AclServiceUtilFacade implements IAclServiceUtil {
    private final AclServiceManager aclServiceManager;
    private final DataBroker broker;

    @Inject
    public AclServiceUtilFacade(DataBroker broker, AclServiceManager aclServiceManager) {
        this.broker = broker;
        this.aclServiceManager = aclServiceManager;
    }

    @Override
    public Map<String, List<MatchInfoBase>> programIpFlow(Matches matches) {
        return AclServiceOFFlowBuilder.programIpFlow(matches);
    }

    @Override
    public void updateBoundServicesFlow(String interfaceName, Long vpnId) {
        Optional<Interface> intfc = AclServiceUtils.getInterface(broker, interfaceName);
        InterfaceAcl aclInPort = intfc.get().getAugmentation(InterfaceAcl.class);
        if (aclInPort != null && aclInPort.isPortSecurityEnabled()) {
            aclServiceManager.bindAclTableForVpn(AclServiceUtils.buildAclInterfaceState(interfaceName, aclInPort),
                    vpnId);
        }
    }

    @Override
    public void updateRemoteAclFilterTable(String interfaceName, Long vpnId, BigInteger dpnId, int addOrDelete) {
        Optional<Interface> intfc = AclServiceUtils.getInterface(broker, interfaceName);
        InterfaceAcl aclInPort = intfc.get().getAugmentation(InterfaceAcl.class);
        if (aclInPort != null && aclInPort.isPortSecurityEnabled()) {
            AclInterface aclInterface = AclServiceUtils.buildAclInterfaceState(interfaceName, aclInPort);
            aclInterface.setDpId(dpnId);
            aclServiceManager.updateRemoteAclFilterTable(aclInterface, addOrDelete, vpnId);
        }
    }

}
