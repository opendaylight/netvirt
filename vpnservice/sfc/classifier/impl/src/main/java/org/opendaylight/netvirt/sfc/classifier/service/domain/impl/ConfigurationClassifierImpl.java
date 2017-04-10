/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.sfc.classifier.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.NetvirtProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.SfcProvider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderableEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierState;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NetvirtsfcAclActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.RedirectToSfc;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationClassifierImpl implements ClassifierState {

    private final SfcProvider sfcProvider;
    private final DataBroker dataBroker;
    private final GeniusProvider geniusProvider;
    private final NetvirtProvider netvirtProvider;
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationClassifierImpl.class);

    public ConfigurationClassifierImpl(GeniusProvider geniusProvider,
                                       NetvirtProvider netvirtProvider,
                                       SfcProvider sfcProvider,
                                       DataBroker dataBroker) {
        this.geniusProvider = geniusProvider;
        this.netvirtProvider = netvirtProvider;
        this.sfcProvider = sfcProvider;
        this.dataBroker = dataBroker;
    }

    @Override
    public Set<ClassifierRenderableEntry> getAllEntries() {
        return readAcls().stream()
                .map(Acl::getAccessListEntries)
                .filter(Objects::nonNull)
                .map(AccessListEntries::getAce)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(this::getEntries)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public List<Acl> readAcls() {
        InstanceIdentifier<AccessLists> aclsIID = InstanceIdentifier.builder(AccessLists.class).build();
        AccessLists acls = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, aclsIID).orNull();
        LOG.trace("Acls read from datastore: {}", acls);
        return Optional.ofNullable(acls).map(AccessLists::getAcl).orElse(Collections.emptyList());
    }

    public Set<ClassifierRenderableEntry> getEntries(Ace ace) {

        LOG.trace("Get entries for Ace {}", ace);

        Matches matches = ace.getMatches();

        if (Objects.isNull(matches)) {
            LOG.trace("Ace has no matches");
            return Collections.emptySet();
        }

        RenderedServicePath rsp =  Optional.ofNullable(ace.getActions())
                .map(actions -> actions.getAugmentation(RedirectToSfc.class))
                .map(NetvirtsfcAclActions::getRspName)
                .flatMap(sfcProvider::getRenderedServicePath)
                .orElse(null);

        if (Objects.isNull(rsp)) {
            LOG.trace("Ace has no valid SFC redirect action");
            return Collections.emptySet();
        }

        Long nsp = rsp.getPathId();
        Short nsi = rsp.getStartingIndex();

        if (Objects.isNull(nsp) || Objects.isNull(nsi)) {
            LOG.trace("RSP has no valid NSI or NSP");
            return Collections.emptySet();
        }

        String destinationIp = sfcProvider.getFirstHopSfInterfaceFromRsp(rsp)
                .flatMap(geniusProvider::getIpFromInterfaceName)
                .orElse(null);

        if (Objects.isNull(destinationIp)) {
            LOG.trace("Could not acquire a valid first RSP hop destination ip");
            return Collections.emptySet();
        }

        Map<NodeId, List<InterfaceKey>> nodeToInterfaces = Optional.ofNullable(matches.getAugmentation(NeutronNetwork
                .class))
                .map(netvirtProvider::getLogicalInterfacesFromNeutronNetwork)
                .orElse(Collections.emptyList())
                .stream()
                .map(iface -> new AbstractMap.SimpleEntry<>(
                        new InterfaceKey(iface),
                        geniusProvider.getNodeIdFromLogicalInterface(iface).orElse(null)))
                .filter(entry -> Objects.nonNull(entry.getValue()))
                .collect(Collectors.groupingBy(
                        AbstractMap.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

        LOG.trace("Got classifier nodes and interfaces: {}", nodeToInterfaces);

        Set<ClassifierRenderableEntry> entries = new HashSet<>();
        nodeToInterfaces.entrySet().forEach(nodeIdListEntry -> {
            NodeId nodeId = nodeIdListEntry.getKey();
            nodeIdListEntry.getValue().forEach(interfaceKey -> {
                entries.add(ClassifierEntry.buildIngressEntry(interfaceKey));
                entries.add(ClassifierEntry.buildMatchEntry(
                        nodeId,
                        geniusProvider.getNodeConnectorIdFromInterfaceName(interfaceKey.getName()).get(),
                        matches,
                        nsp,
                        nsi,
                        destinationIp));
            });
            entries.add(ClassifierEntry.buildNodeEntry(nodeId));
            entries.add(ClassifierEntry.buildPathEntry(nodeIdListEntry.getKey(), nsp, destinationIp));
            // Egress services must bind to egress ports. Since we dont know before-hand what
            // the egress ports will be, we will bind on all switch ports. If the packet
            // doesnt have NSH, it will be returned to the the egress dispatcher table.
            List<String> interfaceUuidStrList = geniusProvider.getInterfacesFromNode(nodeId);
            interfaceUuidStrList.forEach(interfaceUuidStr ->
                entries.add(ClassifierEntry.buildEgressEntry(new InterfaceKey(interfaceUuidStr))));
        });

        return entries;
    }
}
