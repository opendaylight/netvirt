/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.sfc.translator.portchain;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.opendaylight.netvirt.sfc.translator.SfcMdsalHelper;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfDataPlaneLocatorName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SftTypeName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.TenantId;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sl.rev140701.Mac;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sl.rev140701.SlTransportType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.service.functions.service.function.sf.data.plane.locator.locator.type.LogicalInterface;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.service.functions.service.function.sf.data.plane.locator.locator.type.LogicalInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.port.pair.attributes.ServiceFunctionParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pair.groups.PortPairGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPair;

/**
 * Class will convert OpenStack Port Pair API yang models present in
 * neutron northbound project to OpenDaylight SFC Service Function yang models.
 */
public final class PortPairTranslator {
    private static final String SF_TYPE_PARAM = "type";
    private static final String DPL_TRANSPORT_PARAM = "dpl-transport";
    private static final String DPL_EGRESS_SUFFIX = "-egress";
    private static final String DPL_INGRESS_SUFFIX = "-ingress";

    public static final ImmutableBiMap<String, Class<? extends SlTransportType>> DPL_TRANSPORT_TYPE
            = new ImmutableBiMap.Builder<String, Class<? extends SlTransportType>>()
            .put("mac", Mac.class).build();

    private PortPairTranslator() {

    }

    @Nonnull
    public static ServiceFunction buildServiceFunction(
            PortPair portPair,
            PortPairGroup portPairGroup) {
        Preconditions.checkNotNull(portPair, "Port pair must not be null");
        Preconditions.checkNotNull(portPairGroup, "Port pair group must not be null");

        ServiceFunctionBuilder serviceFunctionBuilder = new ServiceFunctionBuilder();

        //Set SF name and tenant-id
        serviceFunctionBuilder.setName(new SfName(portPair.getName()));
        serviceFunctionBuilder.setTenantId(new TenantId(portPair.getTenantId().getValue()));

        //Set SF Type. Setting it to PortPairGroup Type, this will be overridden if user pass
        //it through service_function_params
        serviceFunctionBuilder.setType(SftTypeName.getDefaultInstance(portPairGroup.getName()));

        //If user pass specific param using service_function_parameters, set/override it accordingly
        Class transportTypeClass = null;
        List<ServiceFunctionParameters> sfParams = portPair.getServiceFunctionParameters();
        if (sfParams != null) {
            for (ServiceFunctionParameters sfParam : sfParams) {
                //There is by default type set to port pair group name, override it if user pass it specific type
                if (sfParam.getServiceFunctionParameter().equals(SF_TYPE_PARAM)) {
                    serviceFunctionBuilder.setType(new SftTypeName(sfParam.getServiceFunctionParameterValue()));
                }
                if (sfParam.getServiceFunctionParameter().equals(DPL_TRANSPORT_PARAM)) {
                    transportTypeClass = DPL_TRANSPORT_TYPE.get(sfParam.getServiceFunctionParameterValue());
                }
            }
        }

        //Build forward DPL
        SfDataPlaneLocatorBuilder sfForwardDplBuilder = new SfDataPlaneLocatorBuilder();
        sfForwardDplBuilder.setName(new SfDataPlaneLocatorName(portPair.getName() + DPL_INGRESS_SUFFIX));
        sfForwardDplBuilder.setTransport(transportTypeClass == null ? Mac.class : transportTypeClass);
        sfForwardDplBuilder.setServiceFunctionForwarder(new SffName(SfcMdsalHelper.NETVIRT_LOGICAL_SFF_NAME));
        String forwardPort = portPair.getIngress().getValue();
        LogicalInterface forwardInterface = new LogicalInterfaceBuilder().setInterfaceName(forwardPort).build();
        sfForwardDplBuilder.setLocatorType(forwardInterface);

        //Build reverse DPL
        SfDataPlaneLocatorBuilder sfReverseDplBuilder = new SfDataPlaneLocatorBuilder();
        sfReverseDplBuilder.setName(new SfDataPlaneLocatorName(portPair.getName() + DPL_EGRESS_SUFFIX));
        sfReverseDplBuilder.setTransport(transportTypeClass == null ? Mac.class : transportTypeClass);
        sfReverseDplBuilder.setServiceFunctionForwarder(new SffName(SfcMdsalHelper.NETVIRT_LOGICAL_SFF_NAME));
        String reversePort = portPair.getEgress().getValue();
        LogicalInterface reverseInterface = new LogicalInterfaceBuilder().setInterfaceName(reversePort).build();
        sfReverseDplBuilder.setLocatorType(reverseInterface);

        //Set all data plane locator
        List<SfDataPlaneLocator> sfDataPlaneLocatorList = new ArrayList<>();
        sfDataPlaneLocatorList.add(sfForwardDplBuilder.build());
        sfDataPlaneLocatorList.add(sfReverseDplBuilder.build());
        serviceFunctionBuilder.setSfDataPlaneLocator(sfDataPlaneLocatorList);

        return serviceFunctionBuilder.build();
    }

    public static ServiceFunctionKey getSFKey(PortPair portPair) {
        return new ServiceFunctionKey(new SfName(portPair.getName()));
    }


}
