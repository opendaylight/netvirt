/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.RspName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffDataPlaneLocatorName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfpName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.RenderedServicePaths;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePathKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.rendered.service.path.RenderedServicePathHop;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.ServiceFunctionForwarderBase;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.ServiceFunctionForwarders;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarder.base.SffDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.ServiceFunctionDictionary;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.service.function.dictionary.SffSfDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.ServiceFunctionPathsState;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.state.ServiceFunctionPathState;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.state.ServiceFunctionPathStateKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.LogicalInterfaceLocator;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.service.function.forwarders.service.function.forwarder.sff.data.plane.locator.data.plane.locator.locator.type.LogicalInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SfcProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SfcProvider.class);
    private final DataBroker dataBroker;

    @Inject
    public SfcProvider(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public Optional<RenderedServicePath> getRenderedServicePath(String rspName) {
        RenderedServicePathKey renderedServicePathKey = new RenderedServicePathKey(new RspName(rspName));
        InstanceIdentifier<RenderedServicePath> rspIid = InstanceIdentifier.builder(RenderedServicePaths.class)
                .child(RenderedServicePath.class, renderedServicePathKey).build();


        return MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, rspIid).toJavaUtil();
    }

    public Optional<RenderedServicePath> getRenderedServicePathFromSfc(String sfcName) {
        Optional<RenderedServicePath> rsp = Optional.empty();

        // TODO need to finish this

        return rsp;
    }

    public Optional<List<String>> readServicePathState(String sfpName) {
        ServiceFunctionPathStateKey serviceFunctionPathStateKey = new ServiceFunctionPathStateKey(new SfpName(sfpName));
        InstanceIdentifier<ServiceFunctionPathState> sfpIiD;
        sfpIiD = InstanceIdentifier.builder(ServiceFunctionPathsState.class)
                .child(ServiceFunctionPathState.class, serviceFunctionPathStateKey)
                .build();
        return MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, sfpIiD)
                .toJavaUtil()
                .map(ServiceFunctionPathState::getSfpRenderedServicePath)
                .map(sfpRenderedServicePaths -> sfpRenderedServicePaths.stream()
                        .map(sfpRenderedServicePath -> sfpRenderedServicePath.getName().getValue())
                        .collect(Collectors.toList()));
    }

    public Optional<String> getFirstHopIngressInterfaceFromRsp(RenderedServicePath rsp) {
        // The ingres interface on the first hop is specified in the forward DPL
        // in a forward path or the reverse DPL otherwise
        boolean useForwardDpl = !rsp.isReversePath();
        return getRspFirstHop(rsp).flatMap(rspHop -> getHopSfInterface(rspHop, useForwardDpl));
    }

    public Optional<String> getLastHopEgressInterfaceFromRsp(RenderedServicePath rsp) {
        // The egress interface on the first hop is specified in the forward DPL
        // in a forward path or the reverse DPL otherwise
        boolean useForwardDpl = rsp.isReversePath();
        return getRspLastHop(rsp).flatMap(rspHop -> getHopSfInterface(rspHop, useForwardDpl));
    }

    private Optional<String> getHopSfInterface(RenderedServicePathHop hop, boolean useForwardDpl) {

        LOG.trace("getHopSfInterface of hop {}", hop);

        SfName sfName = hop.getServiceFunctionName();
        if (sfName == null) {
            LOG.warn("getHopSfInterface hop has no SF");
            return Optional.empty();
        }

        SffName sffName = hop.getServiceFunctionForwarder();
        if (sffName == null) {
            LOG.warn("getHopSfInterface hop has no SFF");
            return Optional.empty();
        }

        Optional<ServiceFunctionForwarder> sff = getServiceFunctionForwarder(sffName);
        if (!sff.isPresent()) {
            LOG.warn("getHopSfInterface SFF [{}] does not exist", sffName.getValue());
            return Optional.empty();
        }

        // Find the SFF-SF data plane locator for the SF pair
        SffSfDataPlaneLocator sffSfDataPlaneLocator = sff.map(ServiceFunctionForwarder::getServiceFunctionDictionary)
                .orElse(Collections.emptyList())
                .stream()
                .filter(serviceFunctionDictionary -> serviceFunctionDictionary.getName().equals(sfName))
                .findAny()
                .map(ServiceFunctionDictionary::getSffSfDataPlaneLocator)
                .orElse(null);

        if (sffSfDataPlaneLocator == null) {
            LOG.warn("getHopSfInterface SFF [{}] has not dictionary for SF [{}]",
                    sffName.getValue(),
                    sffName.getValue());
            return Optional.empty();
        }

        // Get the forward or reverse locator name as appropriate if any,
        // otherwise default to non directional locator
        SffDataPlaneLocatorName sffDataPlaneLocatorName = null;
        if (useForwardDpl) {
            sffDataPlaneLocatorName = sffSfDataPlaneLocator.getSffForwardDplName();
        } else {
            sffDataPlaneLocatorName = sffSfDataPlaneLocator.getSffReverseDplName();
        }
        if (sffDataPlaneLocatorName == null) {
            sffDataPlaneLocatorName = sffSfDataPlaneLocator.getSffDplName();
        }

        // Get the interface name value of the locator with such name
        SffDataPlaneLocatorName locatorName = sffDataPlaneLocatorName;
        Optional<String> interfaceName = sff.map(ServiceFunctionForwarderBase::getSffDataPlaneLocator)
                .orElse(Collections.emptyList())
                .stream()
                .filter(sffDataPlaneLocator -> sffDataPlaneLocator.getName().equals(locatorName))
                .findAny()
                .map(SffDataPlaneLocator::getDataPlaneLocator)
                .filter(dataPlaneLocator -> dataPlaneLocator.getLocatorType() instanceof LogicalInterface)
                .map(dataPlaneLocator -> (LogicalInterfaceLocator) dataPlaneLocator.getLocatorType())
                .map(LogicalInterfaceLocator::getInterfaceName);

        return interfaceName;
    }

    private Optional<ServiceFunctionForwarder> getServiceFunctionForwarder(SffName name) {
        ServiceFunctionForwarderKey serviceFunctionForwarderKey = new ServiceFunctionForwarderKey(name);
        InstanceIdentifier<ServiceFunctionForwarder> sffIid;
        sffIid = InstanceIdentifier.builder(ServiceFunctionForwarders.class)
                .child(ServiceFunctionForwarder.class, serviceFunctionForwarderKey)
                .build();

        return MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, sffIid).toJavaUtil();
    }

    private Optional<RenderedServicePathHop> getRspFirstHop(RenderedServicePath rsp) {
        List<RenderedServicePathHop> hops = rsp.getRenderedServicePathHop();
        if (hops == null || hops.isEmpty()) {
            LOG.warn("getRspFirstHop RSP [{}] has no hops list", rsp.getName().getValue());
            return Optional.empty();
        }

        return Optional.ofNullable(hops.get(0));
    }

    private Optional<RenderedServicePathHop> getRspLastHop(RenderedServicePath rsp) {
        List<RenderedServicePathHop> hops = rsp.getRenderedServicePathHop();
        if (hops == null || hops.isEmpty()) {
            LOG.warn("getRspLastHop RSP [{}] has no hops list", rsp.getName().getValue());
            return Optional.empty();
        }

        return Optional.ofNullable(hops.get(hops.size() - 1));
    }
}
