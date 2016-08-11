/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.listeners.AclEventListener;
import org.opendaylight.netvirt.aclservice.listeners.AclInterfaceListener;
import org.opendaylight.netvirt.aclservice.listeners.AclInterfaceStateListener;
import org.opendaylight.netvirt.aclservice.listeners.AclNodeListener;

/**
 * Wiring in 2016 syntax (i.e. Java; strongly typed!) instead of late 1990s
 * XML syntax. This complements (not replaces!) blueprint/aclservice.xml; this
 * is for in-bundle wiring, whereas Blueprint XML is for tying into OSGi services.
 * Having this class avoids duplicating wiring for end2end JUnit tests.
 *
 * @author Michael Vorburger
 */
public class AclServiceModule implements AutoCloseable {

    // This class could be much simplified if we made it use any DI framework
    // such as e.g. Dagger2 or Guice instead of manual wiring.

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalApiManager;

    private AclServiceManagerImpl aclServiceManager;
    private IngressAclServiceImpl ingressAclService;
    private EgressAclServiceImpl egressAclService;
    private AclInterfaceStateListener aclInterfaceStateListener;
    private AclNodeListener aclNodeListener;
    private AclInterfaceListener aclInterfaceListener;
    private AclEventListener aclEventListener;

    public AclServiceModule(DataBroker dataBroker, IMdsalApiManager mdsalApiManager) {
        super();
        this.dataBroker = dataBroker;
        this.mdsalApiManager = mdsalApiManager;
    }

    public DataBroker dataBroker() {
        return dataBroker;
    }

    public IMdsalApiManager mdsalApiManager() {
        return mdsalApiManager;
    }

    public AclServiceManagerImpl aclServiceManager() {
        if (aclServiceManager == null) {
            aclServiceManager = new AclServiceManagerImpl(ingressAclService(), egressAclService());
        }
        return aclServiceManager;
    }

    public IngressAclServiceImpl ingressAclService() {
        if (ingressAclService == null) {
            ingressAclService = new IngressAclServiceImpl(dataBroker(), mdsalApiManager());
        }
        return ingressAclService;
    }

    public EgressAclServiceImpl egressAclService() {
        if (egressAclService == null) {
            egressAclService = new EgressAclServiceImpl(dataBroker(), mdsalApiManager());
        }
        return egressAclService;
    }

    public AclInterfaceStateListener aclInterfaceStateListener() {
        if (aclInterfaceStateListener == null) {
            aclInterfaceStateListener = new AclInterfaceStateListener(aclServiceManager(), dataBroker());
            aclInterfaceStateListener.start();
        }
        return aclInterfaceStateListener;
    }

    public AclNodeListener aclNodeListener() {
        if (aclNodeListener == null) {
            aclNodeListener = new AclNodeListener(mdsalApiManager(), dataBroker());
            aclNodeListener.start();
        }
        return aclNodeListener;
    }

    public AclInterfaceListener aclInterfaceListener() {
        if (aclInterfaceListener == null) {
            aclInterfaceListener = new AclInterfaceListener(aclServiceManager(), dataBroker());
            aclInterfaceListener.start();
        }
        return aclInterfaceListener;
    }

    public AclEventListener aclEventListener() {
        if (aclEventListener == null) {
            aclEventListener = new AclEventListener(aclServiceManager(), dataBroker());
            aclEventListener.start();
        }
        return aclEventListener;
    }

    public void start() {
        aclInterfaceStateListener();
        aclNodeListener();
        aclInterfaceListener();
        aclEventListener();
    }

    @Override
    public void close() throws Exception {
        aclInterfaceStateListener.close();
        aclNodeListener.close();
        aclInterfaceListener.close();
        aclEventListener.close();
    }
}
