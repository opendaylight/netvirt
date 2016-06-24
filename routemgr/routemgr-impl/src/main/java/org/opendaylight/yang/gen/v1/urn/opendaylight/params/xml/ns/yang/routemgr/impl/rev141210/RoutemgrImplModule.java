package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.routemgr.impl.rev141210;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.routemgr.net.OvsdbDataListener;
import org.opendaylight.netvirt.routemgr.net.PktHandler;
import org.opendaylight.netvirt.routemgr.net.NetDataListener;
import org.opendaylight.netvirt.routemgr.net.IfMgr;
import org.opendaylight.netvirt.routemgr.net.IPv6RtrFlow;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;

import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RoutemgrImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.routemgr.impl.rev141210.AbstractRoutemgrImplModule {

    private final static Logger LOG = LoggerFactory.getLogger(RoutemgrImplModule.class);
    private NetDataListener     netDataListener;
    private OvsdbDataListener ovsdbDataListener;
    private PktHandler          ipPktHandler;
    private Registration        packetListener = null;

    public RoutemgrImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public RoutemgrImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.routemgr.impl.rev141210.RoutemgrImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {

        LOG.info("createInstance invoked for the routemgr module.");
        NotificationProviderService notificationService = getNotificationServiceDependency();
        DataBroker dataService = getDataBrokerDependency();
        RpcProviderRegistry rpcRegistryDependency = getRpcRegistryDependency();

        IPv6RtrFlow ipv6RtrFlow = new IPv6RtrFlow();
        IPv6RtrFlow.setDataBroker (dataService);

        Preconditions.checkNotNull(dataService);
        Preconditions.checkNotNull(rpcRegistryDependency);

        netDataListener = new NetDataListener (dataService);
        netDataListener.registerDataChangeListener();

        ovsdbDataListener = new OvsdbDataListener(dataService, ipv6RtrFlow);
        ovsdbDataListener.registerDataChangeListener();

        IfMgr ifMgr = IfMgr.getIfMgrInstance();
        ifMgr.setIPv6RtrFlowService(ipv6RtrFlow);

        PacketProcessingService packetProcessingService =
                rpcRegistryDependency.getRpcService(PacketProcessingService.class);
        ipPktHandler = new PktHandler();
        ipPktHandler.setDataBrokerService(dataService);
        ipPktHandler.setPacketProcessingService(packetProcessingService);
        ipPktHandler.setIfMgrInstance(ifMgr);
        packetListener = notificationService.registerNotificationListener(ipPktHandler);
        LOG.debug ("started the packethandler to receive pdus");

        LOG.debug("starting to read from data store");
        netDataListener.readDataStore();

        final class CloseRtrMgrResources implements AutoCloseable {
            @Override
            public void close() throws Exception {
                if (packetListener != null)
                {
                    packetListener.close();
                }

                if (ovsdbDataListener != null) {
                    ovsdbDataListener.closeListeners();
                }

                if(netDataListener != null) {
                    netDataListener.closeListeners();
                }
                LOG.info("closed the listeners for routemgr. Clearing the cached info.");

                return;
            }
        }

        AutoCloseable ret = new CloseRtrMgrResources();
        LOG.info("Routemgr (instance {}) initialized.", ret);
        return ret;
    }

}
