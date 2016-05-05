/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsmanager.countermanager;

import java.lang.management.ManagementFactory;
import org.opendaylight.netvirt.fcapsmanager.PMServiceFacade;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServer;
import javax.management.ObjectName;

public class Poller {
    private final static org.slf4j.Logger LOG = LoggerFactory.getLogger(Poller.class);
    private static BundleContext context = null;
    public Poller(){
    }
    public Poller(BundleContext bundleContext) {
        context = bundleContext;
    }
    //This method do the Polling every 5 second and retrieves the the counter details
    //@Override
    public void polling() {
        LOG.debug("Poller Polling Mbean List and the content is " + PMRegistrationListener.beanNames);
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(new Pollerthread(), 0, 5, TimeUnit.SECONDS);
    }

    /**
     * Platform dependent bundle injects its handle and it is retrieved in the method
     */
    protected PMServiceFacade getPMServiceSPI (){
        PMServiceFacade service =null;
        if(context != null) {
            try {
                ServiceReference<?> serviceReference = context.
                        getServiceReference(PMServiceFacade.class.getName());
                service = (PMServiceFacade) context.
                        getService(serviceReference);
            }catch(NullPointerException ex){
                service = null;
            }catch (Exception e){
                LOG.error("Exception {} occurred in getting PMServiceSPI",e);
            }
        }
        return service;
    }
}

class Pollerthread implements Runnable {
    private final static org.slf4j.Logger LOG = LoggerFactory.getLogger(Pollerthread.class);
    MBeanServer mbs = null;
    Map<String,String> getCounter = new HashMap<String,String>();
    Poller poller = new Poller();

    /**
     * Retrieve countermap from each counter mbean and send to platform
     */
    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        try {
            mbs = ManagementFactory.getPlatformMBeanServer();
            for (ObjectName objectName : PMRegistrationListener.beanNames) {
                getCounter=(Map<String, String>) mbs.invoke(objectName, "retrieveCounterMap",null,null);
                if(poller.getPMServiceSPI() != null)
                    poller.getPMServiceSPI().connectToPMFactory(getCounter);
                else
                    LOG.debug("PM service not available");
            }
        } catch (Exception e) {
            LOG.error("Exception caught {} ", e);
        }
    }
}