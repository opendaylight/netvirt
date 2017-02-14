/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.SettableFuture;

public class SettableFutureCallback<T> implements FutureCallback<T> {

    private final SettableFuture<T> settableFuture;

    public SettableFutureCallback(SettableFuture<T> settableFuture) {
        this.settableFuture = settableFuture;
    }

    @Override
    public void onSuccess(T t) {
        settableFuture.set(t);
    }

    @Override
    public void onFailure(Throwable throwable) {
        settableFuture.setException(throwable);
    }
}
