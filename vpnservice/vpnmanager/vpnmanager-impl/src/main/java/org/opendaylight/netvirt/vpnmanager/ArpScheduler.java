/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortKey;

import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.*;

public class ArpScheduler extends AsyncDataTreeChangeListenerBase<VpnPortipToPort,ArpScheduler> {
    private static final Logger LOG = LoggerFactory.getLogger(ArpScheduler.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService intfRpc;
    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> scheduledResult;
    private  volatile static ArpScheduler arpScheduler = null;
    private static DelayQueue<MacEntry> macEntryQueue = new DelayQueue<MacEntry>();

    public ArpScheduler(final DataBroker dataBroker, final OdlInterfaceRpcService interfaceRpc) {
        super(VpnPortipToPort.class, ArpScheduler.class);
        this.dataBroker = dataBroker;
        this.intfRpc = interfaceRpc;
    }

    public static ArpScheduler getArpScheduler(DataBroker broker, OdlInterfaceRpcService interfaceRpc) {
        if (arpScheduler == null) {
            synchronized (ArpScheduler.class) {
                if (arpScheduler == null){
                    arpScheduler = new ArpScheduler(broker,interfaceRpc);
                    return arpScheduler;
                }
            }
        }
        return arpScheduler;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
        executorService = Executors.newScheduledThreadPool(ArpConstants.THREAD_POOL_SIZE, getThreadFactory("Arp Cache Timer Tasks"));
        scheduleExpiredEntryDrainerTask();
    }

    @Override
    protected InstanceIdentifier<VpnPortipToPort> getWildCardPath() {
        return InstanceIdentifier.create(NeutronVpnPortipPortData.class).child(VpnPortipToPort.class);
    }

    private void scheduleExpiredEntryDrainerTask() {
        LOG.info("Scheduling expired entry drainer task");
        ExpiredEntryDrainerTask expiredEntryDrainerTask = new ExpiredEntryDrainerTask();
        scheduledResult = executorService.scheduleAtFixedRate(expiredEntryDrainerTask, ArpConstants.NO_DELAY, ArpConstants.PERIOD, TimeUnit.MILLISECONDS);
    }

    private ThreadFactory getThreadFactory(String threadNameFormat) {
        ThreadFactoryBuilder builder = new ThreadFactoryBuilder();
        builder.setNameFormat(threadNameFormat);
        builder.setUncaughtExceptionHandler( new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                LOG.error("Received Uncaught Exception event in Thread: {}", t.getName(), e);
            }
        });
        return builder.build();
    }

    private class ExpiredEntryDrainerTask implements Runnable {
        @Override
        public void run() {
            Collection<MacEntry> expiredMacEntries = new ArrayList<>();
            macEntryQueue.drainTo(expiredMacEntries);
            for (MacEntry macEntry: expiredMacEntries) {
                LOG.info("Removing the ARP cache for"+macEntry);
                InstanceIdentifier<VpnPortipToPort> id = getVpnPortipToPortInstanceOpDataIdentifier(macEntry.getIpAddress().getHostAddress(),macEntry.getVpnName());
                Optional<VpnPortipToPort> vpnPortipToPort = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
                if (vpnPortipToPort.isPresent()) {
                    VpnPortipToPort vpnPortipToPortold = vpnPortipToPort.get();
                    String fixedip = vpnPortipToPortold.getPortFixedip();
                    String vpnName =  vpnPortipToPortold.getVpnName();
                    String interfaceName =  vpnPortipToPortold.getPortName();
                    DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                    coordinator.enqueueJob(buildJobKey(fixedip,vpnName),
                            new ArpUpdateCacheTask(dataBroker,fixedip, vpnName,interfaceName));
                }

            }
        }
    }

    private String getRouteDistinguisher(String vpnName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> vpnInstance = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        String rd = "";
        if(vpnInstance.isPresent()) {
            VpnInstance instance = vpnInstance.get();
            VpnAfConfig config = instance.getIpv4Family();
            rd = config.getRouteDistinguisher();
        }
        return rd;
    }

    public static InstanceIdentifier<VpnPortipToPort> getVpnPortipToPortInstanceOpDataIdentifier(String ip,String vpnName) {
        return InstanceIdentifier.builder(NeutronVpnPortipPortData.class)
                .child(VpnPortipToPort.class, new VpnPortipToPortKey(ip,vpnName)).build();
    }

    @Override
    protected ArpScheduler getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void update(InstanceIdentifier<VpnPortipToPort> id, VpnPortipToPort value,
            VpnPortipToPort dataObjectModificationAfter) {
        try {
            InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
            MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
            String vpnName =  value.getVpnName();
            String interfaceName =  value.getPortName();
            Boolean islearnt = value.isLearnt();
            if (islearnt) {
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(), vpnName),
                        new ArpAddCacheTask(srcInetAddr, srcMacAddress, vpnName, interfaceName, macEntryQueue));
            }
        } catch (Exception e) {
            LOG.error("Error in deserializing packet {} with exception {}", value, e);
            e.printStackTrace();
        }
    }

    @Override
    protected void add(InstanceIdentifier<VpnPortipToPort> identifier, VpnPortipToPort value) {
        try {
            InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
            MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
            String vpnName =  value.getVpnName();
            String interfaceName =  value.getPortName();
            Boolean islearnt = value.isLearnt();
            if (islearnt)
            {
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(),vpnName),
                        new ArpAddCacheTask(srcInetAddr, srcMacAddress, vpnName,interfaceName, macEntryQueue));
            }
        }
        catch (Exception e) {
            LOG.error("Error in deserializing packet {} with exception {}", value, e);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort value) {
        try {
            InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
            MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
            String vpnName =  value.getVpnName();
            String interfaceName =  value.getPortName();
            Boolean islearnt = value.isLearnt();
            if (islearnt) {
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(),vpnName),
                        new ArpRemoveCacheTask(srcInetAddr, srcMacAddress, vpnName,interfaceName, macEntryQueue));
            }
        } catch (Exception e) {
            LOG.error("Error in deserializing packet {} with exception {}", value, e);
        }
    }

    private String buildJobKey(String ip, String vpnName){
        return new StringBuilder(ArpConstants.ARPJOB).append(ip).append(vpnName).toString();
    }

    public void refreshArpEntry(VpnPortipToPort value) {
        try {
            InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
            MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
            String vpnName =  value.getVpnName();
            String interfaceName =  value.getPortName();
            Boolean islearnt = value.isLearnt();
            if (islearnt) {
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(), vpnName),
                        new ArpAddCacheTask(srcInetAddr, srcMacAddress, vpnName, interfaceName, macEntryQueue));
            }
        } catch (Exception e) {
            LOG.error("Error in deserializing packet {} with exception {}", value, e);
        }
    }
}
