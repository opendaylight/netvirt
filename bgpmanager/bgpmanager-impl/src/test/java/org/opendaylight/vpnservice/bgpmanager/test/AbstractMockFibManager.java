package org.opendaylight.vpnservice.test;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import com.google.common.base.Preconditions;

public abstract class AbstractMockFibManager<D extends DataObject> implements DataTreeChangeListener<D> {

    public AbstractMockFibManager() {
        // Do Nothing
    }


    public void onDataTreeChanged(Collection<DataTreeModification<D>> changes) {
        // TODO Auto-generated method stub
    }

}
