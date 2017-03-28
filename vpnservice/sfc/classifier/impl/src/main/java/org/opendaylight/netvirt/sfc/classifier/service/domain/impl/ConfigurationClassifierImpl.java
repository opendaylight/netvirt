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
import org.opendaylight.netvirt.sfc.classifier.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.NetvirtProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.SfcProvider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderableEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierState;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AclBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NetvirtsfcAclActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.RedirectToSfc;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.classifier.rev150105.Classifiers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.classifier.rev150105.classifiers.Classifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class ConfigurationClassifierImpl implements ClassifierState {

    private final SfcProvider sfcProvider;
    private final MdsalUtils mdsalUtils;
    private final GeniusProvider geniusProvider;
    private final NetvirtProvider netvirtProvider;

    public ConfigurationClassifierImpl(GeniusProvider geniusProvider,
                                       NetvirtProvider netvirtProvider,
                                       SfcProvider sfcProvider,
                                       DataBroker dataBroker) {
        this.geniusProvider = geniusProvider;
        this.netvirtProvider = netvirtProvider;
        this.sfcProvider = sfcProvider;
        this.mdsalUtils = new MdsalUtils(dataBroker);
    }

    @Override
    public Set<ClassifierRenderableEntry> getAllEntries() {
        return readClassifiers().stream()
                .map(Classifier::getAcl)
                .filter(Objects::nonNull)
                .distinct()
                .map(this::readAcl)
                .filter(Objects::nonNull)
                .map(Acl::getAccessListEntries)
                .filter(Objects::nonNull)
                .map(AccessListEntries::getAce)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(this::getEntries)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public List<Classifier> readClassifiers() {
        InstanceIdentifier<Classifiers> classifiersIID = InstanceIdentifier.builder(Classifiers.class).build();
        Classifiers classifiers = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, classifiersIID);
        return Optional.ofNullable(classifiers).map(Classifiers::getClassifier).orElse(Collections.emptyList());
    }

    public Acl readAcl(String aclName) {
        InstanceIdentifier<Acl> aclIID = InstanceIdentifier.builder(AccessLists.class)
                .child(Acl.class, new AclKey(aclName, AclBase.class))
                .build();
        return  mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, aclIID);
    }

    public Set<ClassifierRenderableEntry> getEntries(Ace ace) {

        Matches matches = ace.getMatches();

        if (Objects.isNull(matches)) {
            return Collections.emptySet();
        }

        RenderedServicePath rsp =  Optional.ofNullable(ace.getActions())
                .map(actions -> actions.getAugmentation(RedirectToSfc.class))
                .map(NetvirtsfcAclActions::getRspName)
                .flatMap(sfcProvider::getRenderedServicePath)
                .orElse(null);
        Long nsp = rsp.getPathId();
        Short nsi = rsp.getStartingIndex();

        if (Objects.isNull(rsp) || Objects.isNull(nsp) || Objects.isNull(nsi)) {
            return Collections.emptySet();
        }

        String destinationIf = null;  //TODO SfcProvider should give me the interface of first SFof RSP

        NodeKey destinationNode = Optional.ofNullable(destinationIf)
                .flatMap(geniusProvider::getNodeIdFromLogicalInterface)
                .map(NodeKey::new)
                .orElse(null);

        if (Objects.isNull(destinationNode)) {
            return Collections.emptySet();
        }

        String destinationIp = null; // TODO GeniusProvider should give me the interface of first SF


        Map<NodeId, List<InterfaceKey>> nodeToInterfaces = Optional.ofNullable(matches.getAugmentation(NeutronNetwork
                .class))
                .flatMap(netvirtProvider::getLogicalInterfacesFromNeutronNetwork)
                .orElse(Collections.emptyList())
                .stream()
                .map(iface -> new AbstractMap.SimpleEntry<>(
                        new InterfaceKey(iface),
                        geniusProvider.getNodeIdFromLogicalInterface(iface).orElse(null)))
                .filter(entry -> Objects.isNull(entry.getValue()))
                .collect(Collectors.groupingBy(
                        AbstractMap.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

        Set<ClassifierRenderableEntry> entries = new HashSet<>();
        nodeToInterfaces.entrySet().forEach(nodeIdListEntry -> {
            NodeId nodeId = nodeIdListEntry.getKey();
            nodeIdListEntry.getValue().forEach(interfaceKey -> {
                entries.add(ClassifierEntry.buildIngressEntry(interfaceKey));
                entries.add(ClassifierEntry.buildMatchEntry(
                        nodeId,
                        null, // TODO get openflow port number
                        matches,
                        nsp,
                        nsi));
            });
            entries.add(ClassifierEntry.buildNodeEntry(nodeId));
            entries.add(ClassifierEntry.buildPathEntry(nodeIdListEntry.getKey(), nsp, destinationIp));
            // TODO get the interfaces of node and add the corresponding egress entries
        });

        return entries;
    }
}
