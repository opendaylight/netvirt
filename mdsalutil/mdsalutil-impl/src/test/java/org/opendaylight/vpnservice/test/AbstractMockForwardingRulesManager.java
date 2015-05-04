package org.opendaylight.vpnservice.test;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import com.google.common.base.Preconditions;

public abstract class AbstractMockForwardingRulesManager<D extends DataObject> implements DataTreeChangeListener<D> {

    public AbstractMockForwardingRulesManager() {
        // Do Nothing
    }


    public void onDataTreeChanged(Collection<DataTreeModification<D>> changes) {
        // TODO Auto-generated method stub
    }

}
