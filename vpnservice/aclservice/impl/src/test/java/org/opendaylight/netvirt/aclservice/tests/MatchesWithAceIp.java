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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRangeBuilder;

/**
 * Utility with "fluent" API to build {@link Matches} instances.
 *
 * @author Michael Vorburger
 */
@Immutable
@Value.Style(stagedBuilder = true)
abstract class MatchesWithAceIp {

    abstract Optional<Integer> protocol();

    abstract Optional<String> srcRemoteIpPrefix();

    abstract Optional<String> destRemoteIpPrefix();

    // LATER: abstract Class<? extends EthertypeBase> newEtherType();

    @Immutable
    @Value.Style(stagedBuilder = true)
    interface Range {
        int lower();

        int upper();
    }

    abstract Optional<Range> destinationPortRange();

    abstract Optional<Range> sourcePortRange();

    // TODO Remove this when https://github.com/immutables/immutables/issues/470 is resolved
    public static class Builder extends ImmutableMatchesWithAceIp.Builder {

        // TODO Remove these when https://github.com/immutables/immutables/issues/471 is resolved
        Builder destinationPortRange(int lower, int upper) {
            destinationPortRange(ImmutableRange.builder().lower(lower).upper(upper).build());
            return this;
        }

        Builder sourcePortRange(int lower, int upper) {
            sourcePortRange(ImmutableRange.builder().lower(lower).upper(upper).build());
            return this;
        }
    }

    Matches create() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        destinationPortRange().ifPresent(destinationPortsRange -> {
            DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
            destinationPortRangeBuilder.setLowerPort(new PortNumber(destinationPortsRange.lower()));
            destinationPortRangeBuilder.setUpperPort(new PortNumber(destinationPortsRange.upper()));
            aceIpBuilder.setDestinationPortRange(destinationPortRangeBuilder.build());
        });
        sourcePortRange().ifPresent(sourcePortsRange -> {
            SourcePortRangeBuilder sourcePortRangeBuilder = new SourcePortRangeBuilder();
            sourcePortRangeBuilder.setLowerPort(new PortNumber(sourcePortsRange.lower()));
            sourcePortRangeBuilder.setUpperPort(new PortNumber(sourcePortsRange.upper()));
            aceIpBuilder.setSourcePortRange(sourcePortRangeBuilder.build());
        });
        AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
        srcRemoteIpPrefix().ifPresent(
            srcRemoteIpPrefix -> aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(srcRemoteIpPrefix)));
        destRemoteIpPrefix().ifPresent(
            destRemoteIpPrefix -> aceIpv4Builder.setDestinationIpv4Network(new Ipv4Prefix(destRemoteIpPrefix)));
        protocol().ifPresent(protocol ->
            aceIpBuilder.setProtocol(protocol.shortValue())
        );
        aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        return matchesBuilder.build();
    }
}
