package org.opendaylight.vpnservice.test;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class MockFlowForwarder extends AbstractMockForwardingRulesManager<Flow> {

    private int nFlowCount = 0;

    private ListenerRegistration<MockFlowForwarder> listenerRegistration;

    public MockFlowForwarder( final DataBroker db) {
        super() ;
        registerListener(db) ;
    }

    private void registerListener(final DataBroker db) {
        final DataTreeIdentifier<Flow> treeId = new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, getWildCardPath());
        try {
            listenerRegistration = db.registerDataTreeChangeListener(treeId, MockFlowForwarder.this);
        } catch (final Exception e) {
            throw new IllegalStateException("FlowForwarder registration Listener fail! System needs restart.", e);
        }
    }

    private InstanceIdentifier<Flow> getWildCardPath() {
            return InstanceIdentifier.create(Nodes.class).child(Node.class)
                    .augmentation(FlowCapableNode.class).child(Table.class).child(Flow.class);
     }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<Flow>> changes) {
        for (DataTreeModification<Flow> change : changes) {
            final InstanceIdentifier<Flow> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Flow> mod = change.getRootNode();

                switch (mod.getModificationType()) {
                case DELETE:
                    nFlowCount -= 1;
                    break;
                case SUBTREE_MODIFIED:
                    // CHECK IF RQD
                    break;
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        nFlowCount += 1;
                    } else {
                        // UPDATE COUNT UNCHANGED
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());
                }
            }
     }

    public int getDataChgCount() {
        return nFlowCount;
    }
}
