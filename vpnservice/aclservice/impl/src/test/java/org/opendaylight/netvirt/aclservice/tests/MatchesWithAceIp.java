/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import java.util.Optional;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;

/**
 * Utility with "fluent" API to build {@link Matches} instances.
 *
 * @author Michael Vorburger
 */
@Immutable
@Value.Style(stagedBuilder = true)
abstract class MatchesWithAceIp {

    abstract int destLowerPort();

    abstract int destUpperPort();

    abstract int protocol();

    abstract Optional<String> srcRemoteIpPrefix();

    abstract Optional<String> destRemoteIpPrefix();

    // TODO should the following 4 be mandatory here (and in which order)? But they aren't used..
    // abstract Class<? extends EthertypeBase> newEtherType();
    // abstract int srcLowerPort();
    // abstract int srcUpperPort();

    Matches create() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        if (destLowerPort() != -1) {
            DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
            destinationPortRangeBuilder.setLowerPort(new PortNumber(destLowerPort()));
            destinationPortRangeBuilder.setUpperPort(new PortNumber(destUpperPort()));
            aceIpBuilder.setDestinationPortRange(destinationPortRangeBuilder.build());
        }
        AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
        srcRemoteIpPrefix().ifPresent(
            srcRemoteIpPrefix -> aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(srcRemoteIpPrefix)));
        destRemoteIpPrefix().ifPresent(
            destRemoteIpPrefix -> aceIpv4Builder.setDestinationIpv4Network(new Ipv4Prefix(destRemoteIpPrefix)));
        if (protocol() != -1) {
            aceIpBuilder.setProtocol((short) protocol());
        }
        aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        return matchesBuilder.build();
    }
}
