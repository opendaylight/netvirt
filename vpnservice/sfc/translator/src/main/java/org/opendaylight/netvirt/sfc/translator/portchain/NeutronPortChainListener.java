/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.translator.portchain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.netvirt.sfc.translator.DelegatingDataTreeListener;
import org.opendaylight.netvirt.sfc.translator.NeutronMdsalHelper;
import org.opendaylight.netvirt.sfc.translator.SfcMdsalHelper;
import org.opendaylight.netvirt.sfc.translator.flowclassifier.FlowClassifierTranslator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.CreateRenderedPathInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.CreateRenderedPathOutput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.DeleteRenderedPathInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.RenderedServicePathService;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.ServiceFunctionChain;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPath;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.flow.classifier.rev160511.sfc.flow.classifiers.attributes.sfc.flow.classifiers.SfcFlowClassifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.PortChains;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.chains.PortChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pair.groups.PortPairGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPair;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenDaylight Neutron Port Chain yang models data change listener.
 */
public class NeutronPortChainListener extends DelegatingDataTreeListener<PortChain> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChainListener.class);

    private static final InstanceIdentifier<PortChain> PORT_CHAIN_IID =
            InstanceIdentifier.create(Neutron.class).child(PortChains.class).child(PortChain.class);
    private final SfcMdsalHelper sfcMdsalHelper;
    private final NeutronMdsalHelper neutronMdsalHelper;
    private final RenderedServicePathService rspService;

    public NeutronPortChainListener(DataBroker db, RenderedServicePathService rspService) {
        super(db,new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, PORT_CHAIN_IID));
        this.sfcMdsalHelper = new SfcMdsalHelper(db);
        this.neutronMdsalHelper = new NeutronMdsalHelper(db);
        this.rspService = rspService;
    }

    /**
     * Method removes PortChain which is identified by InstanceIdentifier.
     *
     * @param path - the whole path to PortChain
     * @param deletedPortChain        - PortChain for removing
     */
    @Override
    public void remove(InstanceIdentifier<PortChain> path, PortChain deletedPortChain) {
        if (this.rspService != null) {
            DeleteRenderedPathInput deleteRenderedPathInput =
                    PortChainTranslator.buildDeleteRenderedServicePathInput(PortChainTranslator
                    .getSFPKey(deletedPortChain));
            if (deleteRenderedPathInput != null) {
                JdkFutures.addErrorLogging(rspService.deleteRenderedPath(deleteRenderedPathInput),
                        LOG, "deleteRenderedPath");
            }
        }
        sfcMdsalHelper.deleteServiceFunctionPath(PortChainTranslator.getSFPKey(deletedPortChain));
        sfcMdsalHelper.deleteServiceFunctionChain(PortChainTranslator.getSFCKey(deletedPortChain));
    }

    /**
     * Method updates the original PortChain to the update PortChain.
     * Both are identified by same InstanceIdentifier.
     *
     * @param path - the whole path to PortChain
     * @param originalPortChain   - original PortChain (for update)
     * @param updatePortChain     - changed PortChain (contain updates)
     */
    @Override
    public void update(InstanceIdentifier<PortChain> path, PortChain originalPortChain, PortChain updatePortChain) {
        //TODO: Add support for chain update
    }

    /**
     * Method adds the PortChain which is identified by InstanceIdentifier
     * to device.
     *
     * @param path - the whole path to new PortChain
     * @param newPortChain        - new PortChain
     */
    @Override
    public void add(final InstanceIdentifier<PortChain> path, final PortChain newPortChain) {
        processPortChain(newPortChain);
    }

    private void processPortChain(PortChain newPortChain) {

        //List of Port Pair Group attached to the Port Chain
        List<PortPairGroup> portPairGroupList = new ArrayList<>();
        //Port Pair Group and associated Port Pair
        Map<Uuid, List<PortPair>> groupPortPairsList = new HashMap<>();

        List<ServiceFunction> portChainServiceFunctionList = new ArrayList<>();

        //Read chain related port pair group from neutron data store
        for (Uuid ppgUuid : newPortChain.getPortPairGroups()) {
            PortPairGroup ppg = neutronMdsalHelper.getNeutronPortPairGroup(ppgUuid);
            if (ppg != null) {
                List<PortPair> portPairList = new ArrayList<>();
                portPairGroupList.add(ppg);
                for (Uuid ppUuid : ppg.getPortPairs()) {
                    PortPair pp = neutronMdsalHelper.getNeutronPortPair(ppUuid);
                    if (pp == null) {
                        LOG.error("Port pair {} does not exist in the neutron data store", ppUuid);
                        return;
                    }
                    portPairList.add(pp);
                }
                groupPortPairsList.put(ppgUuid, portPairList);
            }
        }

        //For each port pair group
        for (PortPairGroup ppg : portPairGroupList) {

            List<PortPair> portPairList =  groupPortPairsList.get(ppg.getUuid());

            //Generate all the SF and write it to SFC data store
            for (PortPair portPair : portPairList) {
                //Build the service function for the given port pair.
                ServiceFunction serviceFunction = PortPairTranslator.buildServiceFunction(portPair, ppg);
                portChainServiceFunctionList.add(serviceFunction);

                //Write the Service Function to SFC data store.
                LOG.info("Add Service Function {} for Port Pair {}", serviceFunction, portPair);
                sfcMdsalHelper.addServiceFunction(serviceFunction);
            }

            //Build the SFF Builder from port pair group
            ServiceFunctionForwarder serviceFunctionForwarder;
            serviceFunctionForwarder = PortPairGroupTranslator.buildServiceFunctionForwarder(ppg, portPairList);
            // Send SFF create request
            LOG.info("Update Service Function Forwarder with {} for Port Pair Group {}", serviceFunctionForwarder, ppg);
            sfcMdsalHelper.updateServiceFunctionForwarder(serviceFunctionForwarder);
        }

        //Build Service Function Chain Builder
        ServiceFunctionChain sfc =
                PortChainTranslator.buildServiceFunctionChain(newPortChain, portChainServiceFunctionList);

        //Write SFC to data store
        if (sfc == null) {
            LOG.warn("Service Function Chain building failed for Port Chain {}", newPortChain);
            return;
        }

        LOG.info("Add service function chain {}", sfc);
        sfcMdsalHelper.addServiceFunctionChain(sfc);

        // Build Service Function Path Builder
        ServiceFunctionPath sfp = PortChainTranslator.buildServiceFunctionPath(sfc);

        //Write SFP to data store
        LOG.info("Add service function path {}", sfp);
        sfcMdsalHelper.addServiceFunctionPath(sfp);

        //TODO:Generate Flow Classifiers and augment RSP on it.

        if (this.rspService != null) {
            // Build Create Rendered Service Path input
            CreateRenderedPathInput rpInput = PortChainTranslator.buildCreateRenderedServicePathInput(sfp);

            //Call Create Rendered Service Path RPC call
            if (rpInput != null) {
                LOG.info("Call RPC for creating RSP :{}", rpInput);
                Future<RpcResult<CreateRenderedPathOutput>> result =  this.rspService.createRenderedPath(rpInput);
                try {
                    if (result.get() != null) {
                        CreateRenderedPathOutput output = result.get().getResult();
                        LOG.debug("RSP name received from SFC : {}", output.getName());
                        processFlowClassifiers(newPortChain, newPortChain.getFlowClassifiers(), output.getName());
                    } else {
                        LOG.error("RSP creation failed : {}", rpInput);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    LOG.error("Error occurred during creating Rendered Service Path using RPC call", e);
                }
            }
        } else {
            LOG.error("Rendered Path Service is not available, can't create Rendered Path for Port Chain",
                    newPortChain);
        }
    }

    private void processFlowClassifiers(PortChain pc, List<Uuid> flowClassifiers, String rspName) {
        for (Uuid uuid : flowClassifiers) {
            SfcFlowClassifier fc = neutronMdsalHelper.getNeutronFlowClassifier(uuid);
            if (fc != null) {
                Acl acl = FlowClassifierTranslator.buildAcl(fc, rspName);
                if (acl != null) {
                    sfcMdsalHelper.addAclFlowClassifier(acl);
                } else {
                    LOG.warn("Acl building failed for flow classifier {}. Traffic might not be redirected to RSP", fc);
                }

            } else {
                LOG.error("Neutron Flow Classifier {} attached to Port Chain {} is not present in the neutron data "
                    + "store", uuid, pc);
            }
        }
    }
}
