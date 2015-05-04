package org.opendaylight.vpnservice.test;

import java.util.Collection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;

public class MockGroupForwarder extends AbstractMockForwardingRulesManager<Group>{

    private int nGroupCount = 0;
    private ListenerRegistration<MockGroupForwarder> listenerRegistration ;

    public MockGroupForwarder( final DataBroker db) {
        super() ;
        registerListener(db) ;
    }

    private void registerListener(final DataBroker db) {
       final DataTreeIdentifier<Group> treeId = new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, getWildCardPath());
       try {
           listenerRegistration = db.registerDataTreeChangeListener(treeId, MockGroupForwarder.this);
       } catch (final Exception e) {
           throw new IllegalStateException("GroupForwarder registration Listener fail! System needs restart.", e);
       }
    }

    private InstanceIdentifier<Group> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).
                augmentation(FlowCapableNode.class).child(Group.class);
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<Group>> changes) {
        for (DataTreeModification<Group> change : changes) {
            final InstanceIdentifier<Group> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Group> mod = change.getRootNode();

                switch (mod.getModificationType()) {
                case DELETE:
                    nGroupCount -= 1;
                    break;
                case SUBTREE_MODIFIED:
                    // CHECK IF RQD
                    break;
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        nGroupCount += 1;
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
        return nGroupCount;
    }
}
