/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.processors;

import java.util.List;
import java.util.Optional;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort;
import org.opendaylight.netvirt.sfc.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.providers.NetvirtProvider;
import org.opendaylight.netvirt.sfc.providers.OpenFlow13Provider;
import org.opendaylight.netvirt.sfc.providers.SfcProvider;
import org.opendaylight.netvirt.sfc.utils.AclMatches;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Actions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.sfc.acl.rev150105.NeutronNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.sfc.acl.rev150105.RedirectToSfc;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ebrjohn
 *
 */
public class NetvirtSfcAclDataProcessor extends NetvirtSfcDataProcessorBase<Acl> {

    private static final Logger LOG = LoggerFactory.getLogger(NetvirtSfcAclDataProcessor.class);

    // TODO upon init, read the ACLs from the data store and act on them
    //      as if they were just created. This is for ODL restarts

    public NetvirtSfcAclDataProcessor() {
    }

    @Override
    public void remove(InstanceIdentifier<Acl> identifier, Acl del) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }

    @Override
    public void update(InstanceIdentifier<Acl> identifier, Acl original, Acl update) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }

    @Override
    public void add(InstanceIdentifier<Acl> identifier, Acl acl) {
        for (Ace ace : acl.getAccessListEntries().getAce()) {

            Optional<List<NeutronPort>> neutronPorts = getNeutronPortsFromAceMatches(ace.getMatches());
            if (!neutronPorts.isPresent()) {
                LOG.info("Neutron Networks do not exist for entry: [{}]", ace.getKey().getRuleName());
                continue;
            }

            Optional<RenderedServicePath> rsp = getRspFromAceActions(ace.getActions());
            if(!rsp.isPresent()) {
                LOG.info("Rendered Service Path does not exist for entry: [{}]", ace.getKey().getRuleName());
                continue;
            }

            Optional<MatchBuilder> mb = getMatchesFromAceMatches(ace.getMatches());
            if(!mb.isPresent()) {
                continue;
            }

            neutronPorts.get().forEach((port) -> OpenFlow13Provider.writeClassifierFlow(
                    GeniusProvider.getNodeIdFromNeutronPort(port).get(), mb.get(), rsp.get()));
        }
    }

    // Internal util methods

    private Optional<List<NeutronPort>> getNeutronPortsFromAceMatches(Matches matches) {
        if (matches == null) {
            return Optional.empty();
        }

        NeutronNetwork neutronNetwork = matches.getAugmentation(NeutronNetwork.class);
        if (neutronNetwork == null) {
            // If the match doesnt have the NeutronNetwork augmentation, then its not for us
            return Optional.empty();
        }

        return NetvirtProvider.getNeutronPortsFromNeutronNetwork(neutronNetwork);
    }

    private Optional<MatchBuilder> getMatchesFromAceMatches(Matches matches) {
        if (matches == null) {
            return Optional.empty();
        }

        return Optional.of(new AclMatches(matches).buildMatch());
    }

    private Optional<RenderedServicePath> getRspFromAceActions(Actions actions) {
        RedirectToSfc redirectToSfc = actions.getAugmentation(RedirectToSfc.class);
        if (redirectToSfc == null) {
            // If the action doesnt have the RedirectToSfc augmentation, then its not for us
            return Optional.empty();
        }

        Optional<RenderedServicePath> rsp = Optional.empty();
        if (redirectToSfc.getRspName() != null) {
            rsp = SfcProvider.getRenderedServicePath(redirectToSfc.getRspName());
        } else if (redirectToSfc.getSfpName() != null) {
            LOG.warn("getRenderedServicePath: by sfp not handled yet");
            rsp = Optional.empty();
        } else {
            rsp = SfcProvider. getRenderedServicePathFromSfc(redirectToSfc.getSfcName());
        }

        if (!rsp.isPresent()) {
            // TODO need to buffer this ACL creation until the RSP is created
        }

        return rsp;
    }
}
