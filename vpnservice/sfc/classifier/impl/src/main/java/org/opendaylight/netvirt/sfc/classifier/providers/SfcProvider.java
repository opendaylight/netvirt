/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.RspName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.RenderedServicePaths;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePathKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.rendered.service.path.RenderedServicePathHop;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.ServiceFunctions;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.LogicalInterfaceLocator;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.service.functions.service.function.sf.data.plane.locator.locator.type.LogicalInterface;
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

    // This is for Logical SFFs
    public Optional<String> getFirstHopSfInterfaceFromRsp(RenderedServicePath rsp) {
        Optional<RenderedServicePathHop> firstHop = getRspFirstHop(rsp);
        if (!firstHop.isPresent()) {
            LOG.warn("getFirstHopSfInterfaceFromRsp RSP [{}] cant get first hop", rsp.getName().getValue());
            return Optional.empty();
        }

        SfName sfName = firstHop.get().getServiceFunctionName();
        if (sfName == null) {
            LOG.warn("getFirstHopSfInterfaceFromRsp RSP [{}] first hop has no SF", rsp.getName().getValue());
            return Optional.empty();
        }

        Optional<ServiceFunction> sf = getServiceFunction(sfName);
        if (!sf.isPresent()) {
            LOG.warn("getFirstHopSfInterfaceFromRsp RSP [{}] SF [{}] does not exist", rsp.getName().getValue(),
                    sfName.getValue());
            return Optional.empty();
        }

        List<SfDataPlaneLocator> sfDplList = sf.get().getSfDataPlaneLocator();
        if (sfDplList == null || sfDplList.isEmpty()) {
            LOG.warn("getFirstHopSfInterfaceFromRsp RSP [{}] SF [{}] has no SfDpl", rsp.getName().getValue(),
                    sfName.getValue());
            return Optional.empty();
        }

        // Get the first LogicalInterface locator, if there is one
        for (SfDataPlaneLocator sfDpl : sfDplList) {
            if (sfDpl.getLocatorType() instanceof LogicalInterface) {
                LogicalInterfaceLocator locator = (LogicalInterfaceLocator) sfDpl.getLocatorType();
                if (locator != null) {
                    return Optional.ofNullable(locator.getInterfaceName());
                }
            }
        }

        return Optional.empty();
    }

    private Optional<ServiceFunction> getServiceFunction(SfName name) {
        ServiceFunctionKey serviceFunctionKey = new ServiceFunctionKey(new SfName(name));
        InstanceIdentifier<ServiceFunction> sfIid = InstanceIdentifier.builder(ServiceFunctions.class)
                .child(ServiceFunction.class, serviceFunctionKey).build();

        return MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, sfIid).toJavaUtil();
    }

    private Optional<RenderedServicePathHop> getRspFirstHop(RenderedServicePath rsp) {
        List<RenderedServicePathHop> hops = rsp.getRenderedServicePathHop();
        if (hops == null || hops.isEmpty()) {
            LOG.warn("getRspFirstHop RSP [{}] has no hops list", rsp.getName().getValue());
            return Optional.empty();
        }

        return Optional.ofNullable(hops.get(0));
    }
}
