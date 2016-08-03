/*
 * Copyright (c) 2016 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.sfc.translator.portchain;

import com.google.common.collect.ImmutableBiMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.openstack.sfc.translator.INeutronSfcDataProcessor;
import org.opendaylight.netvirt.openstack.sfc.translator.NeutronMdsalHelper;
import org.opendaylight.netvirt.openstack.sfc.translator.OvsdbMdsalHelper;
import org.opendaylight.netvirt.openstack.sfc.translator.OvsdbPortMetadata;
import org.opendaylight.netvirt.openstack.sfc.translator.SfcMdsalHelper;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfDataPlaneLocatorName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SftTypeName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.TenantId;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocatorKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sl.rev140701.SlTransportType;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sl.rev140701.VxlanGpe;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sl.rev140701.data.plane.locator.locator.type.IpBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sf.ovs.rev160107.SfDplOvsAugmentation;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sf.ovs.rev160107.SfDplOvsAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sf.ovs.rev160107.connected.port.OvsPortBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.port.pair.attributes.ServiceFunctionParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPair;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class will convert OpenStack Port Pair API yang models present in
 * neutron northbound project to OpenDaylight SFC Service Function yang models.
 */
public class PortPairTranslator implements INeutronSfcDataProcessor<PortPair> {
    private static final Logger LOG = LoggerFactory.getLogger(PortPairTranslator.class);
    private static final String NSH_AWARE_PARAM = "nsh-aware";
    private static final String SF_TYPE_PARAM = "type";
    private static final String DPL_SUFFIX_PARAM = "-dpl";
    private static final String DPL_TRANSPORT_PARAM = "dpl-transport";
    private static final String DPL_IP_PARAM = "dpl-ip";
    private static final String DPL_PORT_PARAM = "dpl-port";
    private static final String SFF_STR = "sff";

    public static final ImmutableBiMap<String, Class<? extends SlTransportType>> DPL_TRANSPORT_TYPE
            = new ImmutableBiMap.Builder<String, Class<? extends SlTransportType>>()
            .put("vxlan-gpe", VxlanGpe.class).build();
    private static final PortNumber SF_LOCATOR_PORT = new PortNumber(6633);
    private static final AtomicInteger counter = new AtomicInteger(0);
    private static final String SFF_NAME_PARAM = "sff-name";

    private final DataBroker db;
    private NeutronPortPairListener neutronPortPairListener;
    private final SfcMdsalHelper sfcMdsalHelper;
    private final NeutronMdsalHelper neutronMdsalHelper;
    private final OvsdbMdsalHelper ovsdbMdsalHelper;

    public PortPairTranslator(DataBroker db) {
        this.db = db;
        sfcMdsalHelper = new SfcMdsalHelper(db);
        neutronMdsalHelper = new NeutronMdsalHelper(db);
        ovsdbMdsalHelper = new OvsdbMdsalHelper(db);
    }

    public void start() {
        LOG.info("Port Pair Translator Initialized.");
        if(neutronPortPairListener == null) {
            neutronPortPairListener = new NeutronPortPairListener(db, this);
        }
    }

    /**
     * Method removes PortPair which is identified by InstanceIdentifier.
     *
     * @param path - the whole path to PortPair
     * @param deletedPortPair        - PortPair for removing
     */
    @Override
    public void remove(InstanceIdentifier<PortPair> path, PortPair deletedPortPair) {
        sfcMdsalHelper.removeServiceFunction(getSFKey(deletedPortPair));
    }

    /**
     * Method updates the original PortPair to the update PortPair.
     * Both are identified by same InstanceIdentifier.
     *
     * @param path - the whole path to PortPair
     * @param originalPortPair   - original PortPair (for update)
     * @param updatePortPair     - changed PortPair (contain updates)
     */
    @Override
    public void update(InstanceIdentifier<PortPair> path, PortPair originalPortPair, PortPair updatePortPair) {
        ServiceFunction sf = buildServiceFunction(updatePortPair);
        sfcMdsalHelper.updateServiceFunction(sf);
    }

    /**
     * Method adds the PortPair which is identified by InstanceIdentifier
     * to device.
     *
     * @param path - the whole path to new PortPair
     * @param newPortPair        - new PortPair
     */
    @Override
    public void add(InstanceIdentifier<PortPair> path, PortPair newPortPair) {
        ServiceFunction sf = buildServiceFunction(newPortPair);
        sfcMdsalHelper.addServiceFunction(sf);
    }

    private ServiceFunction buildServiceFunction(PortPair portPair) {
        LOG.info("Port pair received : {}", portPair);

        ServiceFunctionBuilder serviceFunctionBuilder = new ServiceFunctionBuilder();
        List<SfDataPlaneLocator> sfDataPlaneLocatorList = new ArrayList<>();
        SfDataPlaneLocatorBuilder sfDataPlaneLocatorBuilder = new SfDataPlaneLocatorBuilder();
        IpBuilder sfLocator = new IpBuilder();

        List<ServiceFunctionParameters> sfParams = portPair.getServiceFunctionParameters();

        serviceFunctionBuilder.setName(new SfName(portPair.getName()));
        serviceFunctionBuilder.setKey(new ServiceFunctionKey(serviceFunctionBuilder.getName()));

        serviceFunctionBuilder.setTenantId(new TenantId(portPair.getTenantId().getValue()));
        //By default set it to true
        serviceFunctionBuilder.setNshAware(true);

        //Set data path locator
        sfDataPlaneLocatorBuilder.setName(new SfDataPlaneLocatorName(portPair.getName() + DPL_SUFFIX_PARAM));
        sfDataPlaneLocatorBuilder.setKey(new SfDataPlaneLocatorKey(sfDataPlaneLocatorBuilder.getName()));

        //Set vxlan-gpe as a default transport type, unless user pass specific transport in
        //service_function_params
        sfDataPlaneLocatorBuilder.setTransport(VxlanGpe.class);

        //Set locator type
        Port neutronPort = neutronMdsalHelper.getNeutronPort(portPair.getIngress());
        if (neutronPort == null) {
            //Try to get the neutron port for egress node. Currently assuming that
            //ingress port and egress port will be same.
            neutronPort = neutronMdsalHelper.getNeutronPort(portPair.getEgress());
        }
        if (neutronPort != null) {
            List<AllowedAddressPairs> attachedIpAddresses = neutronPort.getAllowedAddressPairs();
            //Pick up the first ip address
            IpAddress ipAddress;
            if (attachedIpAddresses != null && !attachedIpAddresses.isEmpty()) {
                ipAddress = attachedIpAddresses.get(0).getIpAddress().getIpAddress();
                sfLocator.setIp(ipAddress);
                sfLocator.setPort(new PortNumber(SF_LOCATOR_PORT));
            } else {
                LOG.warn("No ip address attached to Neutron Port {} related to Port Pair {}", neutronPort, portPair);
                //Ideally we should exit here, because without IP address OpenDaylight SFC won't be able to find the
                //respective overlay. But if user passes additional parameter through service_function_param
                //that can be leveraged here. Parameter passed through service_function_param will take precedence.
            }

        } else {
            LOG.warn("Neutron port mapped to Port pair ingress/egress port is not found : {}", portPair);
        }

        //Set OVS_Port
        //This call is very costly call, as it reads ovsdb:1 topology from the operational data store.
        // Need to think of caching to optimize it further.
        OvsdbPortMetadata ovsdbPortMetadata = ovsdbMdsalHelper.getOvsdbPortMetadata(portPair.getIngress());
        if (ovsdbPortMetadata.getOvsdbPort() != null ) {
            String ovsdbPortName = ovsdbMdsalHelper.getOvsdbPortName(ovsdbPortMetadata.getOvsdbPort());
            SfDplOvsAugmentationBuilder sfDplOvsAugmentationBuilder = new SfDplOvsAugmentationBuilder();
            OvsPortBuilder ovsPortBuilder = new OvsPortBuilder();
            ovsPortBuilder.setPortId(ovsdbPortName);
            sfDplOvsAugmentationBuilder.setOvsPort(ovsPortBuilder.build());
            sfDataPlaneLocatorBuilder.addAugmentation(SfDplOvsAugmentation.class, sfDplOvsAugmentationBuilder.build());
        }

        //But if user pass specific param using service_function_parameters, set it accordingly
        for(ServiceFunctionParameters sfParam : sfParams) {
            if (sfParam.getServiceFunctionParameter().equals(NSH_AWARE_PARAM)) {
                serviceFunctionBuilder.setNshAware(new Boolean(sfParam.getServiceFunctionParameterValue()));
            }
            //There is not default type set, user MUST pass it through service_function_parameters
            if (sfParam.getServiceFunctionParameter().equals(SF_TYPE_PARAM)) {
                serviceFunctionBuilder.setType(new SftTypeName(sfParam.getServiceFunctionParameterValue()));
            }
            if (sfParam.getServiceFunctionParameter().equals(DPL_TRANSPORT_PARAM)) {
                Class transportTypeClass
                        = DPL_TRANSPORT_TYPE.get(sfParam.getServiceFunctionParameterValue());
                sfDataPlaneLocatorBuilder.setTransport(transportTypeClass);
            }
            if (sfParam.getServiceFunctionParameter().equals(DPL_IP_PARAM)) {
                IpAddress ipAddress = new IpAddress(new Ipv4Address(sfParam.getServiceFunctionParameterValue()));
                sfLocator.setIp(ipAddress);
            }
            if (sfParam.getServiceFunctionParameter().equals(DPL_PORT_PARAM)) {
                sfLocator.setPort(new PortNumber(new Integer(sfParam.getServiceFunctionParameterValue())));
            }
            if (sfParam.getServiceFunctionParameter().equals(SFF_NAME_PARAM)) {
                sfDataPlaneLocatorBuilder.setServiceFunctionForwarder(
                        new SffName(sfParam.getServiceFunctionParameterValue()));
            }
        }

        //Set service_function_forwarder
        if (sfDataPlaneLocatorBuilder.getServiceFunctionForwarder() == null
                && ovsdbPortMetadata.getOvsdbNode() != null) {
            String ipAddress = ovsdbMdsalHelper.getNodeIpAddress(ovsdbPortMetadata.getOvsdbNode());
            SffName sffName = sfcMdsalHelper.getExistingSFF(ipAddress);
            if(sffName != null ) {
                sfDataPlaneLocatorBuilder.setServiceFunctionForwarder(sffName);
            } else {
                sfDataPlaneLocatorBuilder.setServiceFunctionForwarder(new SffName(SFF_STR + counter.incrementAndGet()));
            }
        }

        sfDataPlaneLocatorBuilder.setLocatorType(sfLocator.build());

        sfDataPlaneLocatorList.add(sfDataPlaneLocatorBuilder.build());
        //Set management IP to same as DPL IP.
        serviceFunctionBuilder.setIpMgmtAddress(sfLocator.getIp());

        //Set all data plane locator
        serviceFunctionBuilder.setSfDataPlaneLocator(sfDataPlaneLocatorList);
        LOG.info("Port Pair translated to Service Function: {}", serviceFunctionBuilder);
        return serviceFunctionBuilder.build();
    }

    private ServiceFunctionKey getSFKey(PortPair portPair) {
        return new ServiceFunctionKey(new SfName(portPair.getName()));
    }

}
