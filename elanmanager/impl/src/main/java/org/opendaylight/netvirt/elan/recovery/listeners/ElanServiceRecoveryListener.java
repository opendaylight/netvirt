package org.opendaylight.netvirt.elan.recovery.listeners;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.listeners.AbstractSyncDataTreeChangeListener;
import org.opendaylight.netvirt.elan.recovery.impl.ElanServiceRecoveryManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.ops.rev170711.ServiceOps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.ops.rev170711.service.ops.Services;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.ops.rev170711.service.ops.ServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.ops.rev170711.service.ops.services.Operations;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.types.rev170711.NetvirtElan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ElanServiceRecoveryListener extends AbstractSyncDataTreeChangeListener<Operations> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanServiceRecoveryListener.class);

    private final ElanServiceRecoveryManager serviceRecoveryManager;

    @Inject
    public ElanServiceRecoveryListener(DataBroker dataBroker, ElanServiceRecoveryManager serviceRecoveryManager) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ServiceOps.class)
                .child(Services.class, new ServicesKey(NetvirtElan.class)).child(Operations.class));
        this.serviceRecoveryManager = serviceRecoveryManager;
    }

    @Override
    public void add(@Nonnull Operations operations) {
        LOG.info("Service Recovery operation triggered for service: {}", operations);
        serviceRecoveryManager.recoverService(operations.getEntityType(), operations.getEntityName(),
                operations.getEntityId());
    }

    @Override
    public void remove(@Nonnull Operations removedDataObject) {

    }

    @Override
    public void update(@Nonnull Operations originalDataObject, @Nonnull Operations updatedDataObject) {
        add(updatedDataObject);
    }
}
