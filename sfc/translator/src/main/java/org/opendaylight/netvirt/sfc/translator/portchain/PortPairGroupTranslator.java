/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.sfc.translator.portchain;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opendaylight.netvirt.sfc.translator.SfcMdsalHelper;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfDataPlaneLocatorName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffDataPlaneLocatorName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarder.base.SffDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarder.base.SffDataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarder.base.sff.data.plane.locator.DataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.ServiceFunctionDictionary;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.ServiceFunctionDictionaryBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.service.function.dictionary.SffSfDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.service.function.dictionary.SffSfDataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sl.rev140701.Mac;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.service.function.forwarders.service.function.forwarder.sff.data.plane.locator.data.plane.locator.locator.type.LogicalInterface;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.service.function.forwarders.service.function.forwarder.sff.data.plane.locator.data.plane.locator.locator.type.LogicalInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pair.groups.PortPairGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class will convert OpenStack Port Pair API yang models present in
 * neutron northbound project to OpenDaylight SFC yang models.
 */
public final class PortPairGroupTranslator {
    private static final Logger LOG = LoggerFactory.getLogger(PortPairGroupTranslator.class);

    private static final String DPL_EGRESS_SUFFIX = "-egress";
    private static final String DPL_INGRESS_SUFFIX = "-ingress";

    private PortPairGroupTranslator() {

    }

    public static ServiceFunctionForwarder buildServiceFunctionForwarder(
            PortPairGroup portPairGroup,
            List<PortPair> portPairs) {
        Preconditions.checkNotNull(portPairGroup, "Port pair group must not be null");
        Preconditions.checkNotNull(portPairs, "Port pairs must not be null");
        Preconditions.checkElementIndex(0, portPairs.size(), "There must be at least one port pair");

        //Currently we only support one SF per type. Mean, one port-pair per port-pair-group.
        final PortPair portPair = portPairs.get(0);

        ServiceFunctionForwarderBuilder sffBuilder = new ServiceFunctionForwarderBuilder();
        sffBuilder.setName(new SffName(SfcMdsalHelper.NETVIRT_LOGICAL_SFF_NAME));

        DataPlaneLocatorBuilder forwardDplBuilder = new DataPlaneLocatorBuilder();
        forwardDplBuilder.setTransport(Mac.class);
        String forwardPort = portPair.getIngress().getValue();
        LogicalInterface forwardInterface = new LogicalInterfaceBuilder().setInterfaceName(forwardPort).build();
        forwardDplBuilder.setLocatorType(forwardInterface);
        SffDataPlaneLocatorBuilder sffForwardDplBuilder = new SffDataPlaneLocatorBuilder();
        sffForwardDplBuilder.setDataPlaneLocator(forwardDplBuilder.build());
        String forwardDplName = portPair.getName() + DPL_INGRESS_SUFFIX;
        sffForwardDplBuilder.setName(new SffDataPlaneLocatorName(forwardDplName));

        DataPlaneLocatorBuilder reverseDplBuilder = new DataPlaneLocatorBuilder();
        reverseDplBuilder.setTransport(Mac.class);
        String reversePort = portPair.getEgress().getValue();
        LogicalInterface reverseInterface = new LogicalInterfaceBuilder().setInterfaceName(reversePort).build();
        reverseDplBuilder.setLocatorType(reverseInterface);
        SffDataPlaneLocatorBuilder sffReverseDplBuilder = new SffDataPlaneLocatorBuilder();
        sffReverseDplBuilder.setDataPlaneLocator(reverseDplBuilder.build());
        String reverseDplName = portPair.getName() + DPL_EGRESS_SUFFIX;
        sffReverseDplBuilder.setName(new SffDataPlaneLocatorName(reverseDplName));

        List<SffDataPlaneLocator> sffDataPlaneLocator = new ArrayList<>();
        sffDataPlaneLocator.add(sffForwardDplBuilder.build());
        sffDataPlaneLocator.add(sffReverseDplBuilder.build());
        sffBuilder.setSffDataPlaneLocator(sffDataPlaneLocator);

        SffSfDataPlaneLocatorBuilder sffSfDataPlaneLocatorBuilder = new SffSfDataPlaneLocatorBuilder();
        sffSfDataPlaneLocatorBuilder.setSffForwardDplName(new SffDataPlaneLocatorName(forwardDplName));
        sffSfDataPlaneLocatorBuilder.setSfForwardDplName(new SfDataPlaneLocatorName(forwardDplName));
        sffSfDataPlaneLocatorBuilder.setSffReverseDplName(new SffDataPlaneLocatorName(reverseDplName));
        sffSfDataPlaneLocatorBuilder.setSfReverseDplName(new SfDataPlaneLocatorName(reverseDplName));
        ServiceFunctionDictionaryBuilder sfdBuilder = new ServiceFunctionDictionaryBuilder();
        sfdBuilder.setName(new SfName(portPair.getName()));
        sfdBuilder.setSffSfDataPlaneLocator(sffSfDataPlaneLocatorBuilder.build());

        List<ServiceFunctionDictionary> sfdList = new ArrayList<>();
        sfdList.add(sfdBuilder.build());
        sffBuilder.setServiceFunctionDictionary(sfdList);

        return sffBuilder.build();
    }

    public static ServiceFunctionForwarder removePortPairFromServiceFunctionForwarder(
            ServiceFunctionForwarder serviceFunctionForwarder,
            PortPair deletedPortPair) {
        Preconditions.checkNotNull(deletedPortPair, "Port pair must not be null");

        String portPairName = deletedPortPair.getName();
        ServiceFunctionForwarderBuilder sffBuilder = new ServiceFunctionForwarderBuilder(serviceFunctionForwarder);

        List<ServiceFunctionDictionary> serviceFunctionDictionaryList = sffBuilder.getServiceFunctionDictionary();
        if (serviceFunctionDictionaryList == null) {
            LOG.debug("SF dictionary is empty");
            return serviceFunctionForwarder;
        }

        ServiceFunctionDictionary serviceFunctionDictionary = serviceFunctionDictionaryList.stream()
                .filter(sfDictionary -> sfDictionary.getName().getValue().equals(portPairName))
                .findFirst()
                .orElse(null);

        if (serviceFunctionDictionary == null) {
            LOG.debug("SFF dictionary entry for port pair {} not found", portPairName);
            return serviceFunctionForwarder;
        }

        SffSfDataPlaneLocator sffSfDataPlaneLocator = serviceFunctionDictionary.getSffSfDataPlaneLocator();
        if (sffSfDataPlaneLocator != null) {
            List<SffDataPlaneLocatorName> locators = Arrays.asList(
                    sffSfDataPlaneLocator.getSffDplName(),
                    sffSfDataPlaneLocator.getSffForwardDplName(),
                    sffSfDataPlaneLocator.getSffReverseDplName());
            List<SffDataPlaneLocator> sffDplList = sffBuilder.getSffDataPlaneLocator();
            if (sffDplList != null) {
                sffDplList.stream()
                        .filter(sffDataPlaneLocator -> locators.contains(sffDataPlaneLocator.getName()))
                        .map(sffDplList::remove);
            }
        }
        serviceFunctionDictionaryList.remove(serviceFunctionDictionary);

        return sffBuilder.build();
    }
}
