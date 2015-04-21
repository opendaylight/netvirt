/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fibmanager;

import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import com.google.common.util.concurrent.FutureCallback;
import org.opendaylight.vpnservice.AbstractDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.FibEntries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Optional;

public class FibManager extends AbstractDataChangeListener<FibEntries> implements AutoCloseable{
  private static final Logger LOG = LoggerFactory.getLogger(FibManager.class);
  private ListenerRegistration<DataChangeListener> listenerRegistration;
  private final DataBroker broker;

  private static final FutureCallback<Void> DEFAULT_CALLBACK =
      new FutureCallback<Void>() {
        public void onSuccess(Void result) {
          LOG.debug("Success in Datastore write operation");
        }

        public void onFailure(Throwable error) {
          LOG.error("Error in Datastore write operation", error);
        };
      };

  public FibManager(final DataBroker db) {
    super(FibEntries.class);
    broker = db;
    registerListener(db);
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
    LOG.info("Fib Manager Closed");
  }

  private void registerListener(final DataBroker db) {
    try {
      listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                                                           getWildCardPath(), FibManager.this, DataChangeScope.SUBTREE);
    } catch (final Exception e) {
      LOG.error("FibManager DataChange listener registration fail!", e);
      throw new IllegalStateException("FibManager registration Listener failed.", e);
    }
  }

  @Override
  protected void add(final InstanceIdentifier<FibEntries> identifier,
                     final FibEntries fibEntries) {
    LOG.trace("key: " + identifier + ", value=" + fibEntries );
  }

  private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
                                                  InstanceIdentifier<T> path) {

    ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

    Optional<T> result = Optional.absent();
    try {
      result = tx.read(datastoreType, path).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return result;
  }

  private InstanceIdentifier<FibEntries> getWildCardPath() {
    return InstanceIdentifier.create(FibEntries.class);
  }

  @Override
  protected void remove(InstanceIdentifier<FibEntries> identifier, FibEntries del) {
    LOG.trace("key: " + identifier + ", value=" + del );
  }

  @Override
  protected void update(InstanceIdentifier<FibEntries> identifier, FibEntries original, FibEntries update) {
    LOG.trace("key: " + identifier + ", original=" + original + ", update=" + update );
  }

  private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                                                 InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
    WriteTransaction tx = broker.newWriteOnlyTransaction();
    tx.put(datastoreType, path, data, true);
    Futures.addCallback(tx.submit(), callback);
  }
}
