/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.translator;

import java.util.Objects;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.ServiceFunctions;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.ServiceFunctionChains;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.ServiceFunctionChain;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.ServiceFunctionChainKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.ServiceFunctionForwarders;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.ServiceFunctionPaths;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPathKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods to read OpenDaylight SFC models.
 */
public class SfcMdsalHelper {
    private static final Logger LOG = LoggerFactory.getLogger(SfcMdsalHelper.class);
    private static InstanceIdentifier<AccessLists> accessListIid = InstanceIdentifier.create(AccessLists.class);
    private static InstanceIdentifier<ServiceFunctions> sfIid = InstanceIdentifier.create(ServiceFunctions.class);
    private static InstanceIdentifier<ServiceFunctionForwarders> sffIid =
            InstanceIdentifier.create(ServiceFunctionForwarders.class);
    private static InstanceIdentifier<ServiceFunctionChains> sfcIid =
            InstanceIdentifier.create(ServiceFunctionChains.class);
    private static InstanceIdentifier<ServiceFunctionPaths> sfpIid
            = InstanceIdentifier.create(ServiceFunctionPaths.class);
    public static final String NETVIRT_LOGICAL_SFF_NAME = "Netvirt-Logical-SFF";

    private final DataBroker dataBroker;

    public SfcMdsalHelper(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    //ACL Flow Classifier data store utility methods
    public void addAclFlowClassifier(Acl aclFlowClassifier) {
        InstanceIdentifier<Acl> aclIid = getAclPath(aclFlowClassifier.getKey());
        LOG.info("Write ACL FlowClassifier {} to config data store at {}",aclFlowClassifier, aclIid);
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, aclIid,
                    aclFlowClassifier);
        } catch (TransactionCommitFailedException e) {
            LOG.error("Error writing {} to {}", aclFlowClassifier, aclIid, e);
        }
    }

    public void updateAclFlowClassifier(Acl aclFlowClassifier) {
        InstanceIdentifier<Acl> aclIid = getAclPath(aclFlowClassifier.getKey());
        LOG.info("Update ACL FlowClassifier {} in config data store at {}",aclFlowClassifier, aclIid);
        try {
            SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, aclIid,
                    aclFlowClassifier);
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to merge {}", aclIid, e);
        }
    }

    public void removeAclFlowClassifier(Acl aclFlowClassifier) {
        InstanceIdentifier<Acl> aclIid = getAclPath(aclFlowClassifier.getKey());
        LOG.info("Remove ACL FlowClassifier {} from config data store at {}",aclFlowClassifier, aclIid);
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, aclIid);
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to delete {}", aclIid, e);
        }
    }

    //Service Function
    public ServiceFunction readServiceFunction(ServiceFunctionKey sfKey) {
        InstanceIdentifier<ServiceFunction> sfIid = getSFPath(sfKey);
        LOG.info("Read Service Function {} from config data store at {}",sfKey, sfIid);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, sfIid).orNull();
    }

    public void addServiceFunction(ServiceFunction sf) {
        InstanceIdentifier<ServiceFunction> sfIid = getSFPath(sf.getKey());
        LOG.info("Write Service Function {} to config data store at {}",sf, sfIid);
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, sfIid, sf);
        } catch (TransactionCommitFailedException e) {
            LOG.error("Error writing {} to {}", sf, sfIid, e);
        }
    }

    public void updateServiceFunction(ServiceFunction sf) {
        InstanceIdentifier<ServiceFunction> sfIid = getSFPath(sf.getKey());
        LOG.info("Update Service Function {} in config data store at {}",sf, sfIid);
        try {
            SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, sfIid, sf);
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to merge {}", sfIid, e);
        }
    }

    public void removeServiceFunction(ServiceFunctionKey sfKey) {
        InstanceIdentifier<ServiceFunction> sfIid = getSFPath(sfKey);
        LOG.info("Remove Service Function {} from config data store at {}",sfKey, sfIid);
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, sfIid);
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to delete {}", sfIid, e);
        }
    }

    //Service Function Forwarder
    public ServiceFunctionForwarder readServiceFunctionForwarder(ServiceFunctionForwarderKey sffKey) {
        InstanceIdentifier<ServiceFunctionForwarder> sffIid = getSFFPath(sffKey);
        LOG.info("Read Service Function Forwarder from config data store at {}", sffIid);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, sffIid).orNull();
    }

    public void addServiceFunctionForwarder(ServiceFunctionForwarder sff) {
        InstanceIdentifier<ServiceFunctionForwarder> sffIid = getSFFPath(sff.getKey());
        LOG.info("Write Service Function Forwarder {} to config data store at {}",sff, sffIid);
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, sffIid, sff);
        } catch (TransactionCommitFailedException e) {
            LOG.error("Error writing {} to {}", sff, sffIid, e);
        }
    }

    public void deleteServiceFunctionForwarder(ServiceFunctionForwarderKey sffKey) {
        InstanceIdentifier<ServiceFunctionForwarder> sffIid = getSFFPath(sffKey);
        LOG.info("Delete Service Function Forwarder from config data store at {}", sffIid);
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, sffIid);
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to delete {}", sffIid, e);
        }
    }

    public void addServiceFunctionChain(ServiceFunctionChain sfc) {
        InstanceIdentifier<ServiceFunctionChain> sfcIid = getSFCPath(sfc.getKey());
        LOG.info("Write Service Function Chain {} to config data store at {}",sfc, sfcIid);
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, sfcIid, sfc);
        } catch (TransactionCommitFailedException e) {
            LOG.error("Error writing {} to {}", sfc, sfcIid, e);
        }
    }

    public void deleteServiceFunctionChain(ServiceFunctionChainKey sfcKey) {
        InstanceIdentifier<ServiceFunctionChain> sfcIid = getSFCPath(sfcKey);
        LOG.info("Remove Service Function Chain {} from config data store at {}",sfcKey, sfcIid);
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, sfcIid);
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to delete {}", sfcIid, e);
        }
    }

    public void addServiceFunctionPath(ServiceFunctionPath sfp) {
        InstanceIdentifier<ServiceFunctionPath> sfpIid = getSFPPath(sfp.getKey());
        LOG.info("Write Service Function Path {} to config data store at {}",sfp, sfpIid);
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, sfpIid, sfp);
        } catch (TransactionCommitFailedException e) {
            LOG.error("Error writing {} to {}", sfp, sfpIid, e);
        }
    }

    public void deleteServiceFunctionPath(ServiceFunctionPathKey sfpKey) {
        InstanceIdentifier<ServiceFunctionPath> sfpIid = getSFPPath(sfpKey);
        LOG.info("Delete Service Function Path from config data store at {}", sfpIid);
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, sfpIid);
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to delete {}", sfpIid, e);
        }
    }

    private InstanceIdentifier<Acl> getAclPath(AclKey aclKey) {
        return accessListIid.builder().child(Acl.class, aclKey).build();
    }

    private InstanceIdentifier<ServiceFunction> getSFPath(ServiceFunctionKey key) {
        return sfIid.builder().child(ServiceFunction.class, key).build();
    }

    private InstanceIdentifier<ServiceFunctionForwarder> getSFFPath(ServiceFunctionForwarderKey key) {
        return sffIid.builder().child(ServiceFunctionForwarder.class, key).build();
    }

    private InstanceIdentifier<ServiceFunctionChain> getSFCPath(ServiceFunctionChainKey key) {
        return sfcIid.builder().child(ServiceFunctionChain.class, key).build();
    }

    private InstanceIdentifier<ServiceFunctionPath> getSFPPath(ServiceFunctionPathKey key) {
        return sfpIid.builder().child(ServiceFunctionPath.class, key).build();
    }

    public ServiceFunctionForwarder getExistingSFF(String ipAddress) {
        ServiceFunctionForwarders existingSffs =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, sffIid).orNull();

        return existingSffs == null || existingSffs.getServiceFunctionForwarder() == null
                ? null
                : existingSffs.getServiceFunctionForwarder().stream()
                    .filter(sff -> Objects.nonNull(sff.getIpMgmtAddress()))
                    .filter(sff -> new Ipv4Address(ipAddress).equals(sff.getIpMgmtAddress().getIpv4Address()))
                    .findFirst()
                    .orElse(null);
    }

    public void addNetvirLogicalSff() {
        ServiceFunctionForwarderBuilder sffBuilder = new ServiceFunctionForwarderBuilder();
        sffBuilder.setName(new SffName(NETVIRT_LOGICAL_SFF_NAME));
        ServiceFunctionForwarder sff = sffBuilder.build();
        this.addServiceFunctionForwarder(sff);
    }

    void removeNetvirtLogicalSff() {
        SffName netvirtLogicalSffName = new SffName(NETVIRT_LOGICAL_SFF_NAME);
        ServiceFunctionForwarderKey netvirtLogicalSffKey = new ServiceFunctionForwarderKey(netvirtLogicalSffName);
        this.deleteServiceFunctionForwarder(netvirtLogicalSffKey);
    }
}
