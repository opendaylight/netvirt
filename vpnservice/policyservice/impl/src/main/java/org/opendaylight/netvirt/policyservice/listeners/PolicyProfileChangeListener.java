/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.PolicyProfiles;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfileKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class PolicyProfileChangeListener
        extends AsyncDataTreeChangeListenerBase<PolicyProfile, PolicyProfileChangeListener> {

    @Override
    protected InstanceIdentifier<PolicyProfile> getWildCardPath() {
        return InstanceIdentifier.create(PolicyProfiles.class).child(PolicyProfile.class);
    }

    @Override
    protected PolicyProfileChangeListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void remove(InstanceIdentifier<PolicyProfile> key, PolicyProfile policyProfile) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void update(InstanceIdentifier<PolicyProfile> key, PolicyProfile origPolicyProfile,
            PolicyProfile updatedPolicyProfile) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void add(InstanceIdentifier<PolicyProfile> key, PolicyProfile policyProfile) {
        // TODO Auto-generated method stub

    }

}
