package org.opendaylight.vpnservice.interfacemgr;

import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;

import java.util.Iterator;
import java.util.List;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.vpnservice.AbstractDataChangeListener;

public class IfmNodeConnectorListener extends AbstractDataChangeListener<NodeConnector> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(IfmNodeConnectorListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private InterfaceManager ifManager;

    public IfmNodeConnectorListener(final DataBroker db) {
        super(NodeConnector.class);
        broker = db;
        registerListener(db);
    }

    public IfmNodeConnectorListener(final DataBroker dataBroker, InterfaceManager interfaceManager) {
        this(dataBroker);
        ifManager = interfaceManager;
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), IfmNodeConnectorListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("IfmNodeConnectorListener: DataChange listener registration fail!", e);
            throw new IllegalStateException("IfmNodeConnectorListener: registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<NodeConnector> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).child(NodeConnector.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("IfmNodeConnectorListener Closed");
    }

    @Override
    protected void add(InstanceIdentifier<NodeConnector> identifier, NodeConnector node) {
        LOG.trace("NodeConnectorAdded: key: " + identifier + ", value=" + node );
        ifManager.processPortAdd(node);
    }

    @Override
    protected void remove(InstanceIdentifier<NodeConnector> identifier, NodeConnector del) {
        LOG.trace("NodeConnectorRemoved: key: " + identifier + ", value=" + del );
        ifManager.processPortDelete(del);
    }

    @Override
    protected void update(InstanceIdentifier<NodeConnector> identifier, NodeConnector original, NodeConnector update) {
        ifManager.processPortUpdate(original, update);
    }

}
