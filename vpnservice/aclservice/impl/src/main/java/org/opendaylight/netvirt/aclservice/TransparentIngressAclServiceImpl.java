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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the transparent implementation for ingress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */
public class TransparentIngressAclServiceImpl extends IngressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(TransparentIngressAclServiceImpl.class);

    public TransparentIngressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager) {
        super(dataBroker, mdsalManager);
    }

    @Override
    protected void programFixedRules(BigInteger dpid, String dhcpMacAddress, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, String portId, Action action, int addOrRemove) {
    }

    @Override
    protected void programAceRule(BigInteger dpId, int lportTag, int addOrRemove, Ace ace, String portId,
            List<AllowedAddressPairs> syncAllowedAddresses) {
        LOG.debug("Transparent ingress acl service - do nothing");
    }
}
