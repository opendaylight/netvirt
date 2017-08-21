/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the transparent implementation for egress (w.r.t VM) ACL service.
 *
 */
public class TransparentEgressAclServiceImpl extends AbstractEgressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(TransparentEgressAclServiceImpl.class);

    public TransparentEgressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager,
            AclDataUtil aclDataUtil, AclServiceUtils aclServiceUtils) {
        super(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils);
    }

    @Override
    public void bindService(AclInterface aclInterface) {
        LOG.debug("transparent egress acl service - do nothing");
    }

    @Override
    protected void unbindService(AclInterface aclInterface) {
        LOG.debug("transparent egress acl service - do nothing");
    }

    @Override
    protected void programGeneralFixedRules(AclInterface port, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, Action action,
            int addOrRemove) {
        LOG.debug("transparent egress acl service - do nothing");
    }

    @Override
    protected void programSpecificFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int addOrRemove) {
    }

    @Override
    protected void programAceRule(AclInterface port, int addOrRemove, String aclName, Ace ace,
            List<AllowedAddressPairs> syncAllowedAddresses) {
        LOG.debug("transparent egress acl service - do nothing");
    }

    @Override
    protected String syncSpecificAclFlow(BigInteger dpId, int lportTag, int addOrRemove, Ace ace, String portId,
            Map<String, List<MatchInfoBase>> flowMap, String flowName) {
        // Not in use here. programAceRule function is overridden.
        return null;
    }

}
