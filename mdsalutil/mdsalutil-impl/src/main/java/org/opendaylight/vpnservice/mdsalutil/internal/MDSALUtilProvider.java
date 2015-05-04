package org.opendaylight.vpnservice.mdsalutil.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareConsumer;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ConsumerContext;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MDSALUtilProvider implements BindingAwareConsumer, AutoCloseable {

    private static final Logger s_logger = LoggerFactory.getLogger(MDSALUtilProvider.class);
    private MDSALManager mdSalMgr;

    @Override
    public void onSessionInitialized(ConsumerContext session) {

        s_logger.info( " Session Initiated for MD SAL Util Provider") ;

        try {
            final DataBroker dataBroker;
            final PacketProcessingService packetProcessingService;
            dataBroker = session.getSALService(DataBroker.class);
             // TODO - Verify this.
             packetProcessingService = session.getRpcService(PacketProcessingService.class);
             mdSalMgr = new MDSALManager( dataBroker, packetProcessingService) ;
        }catch( Exception e) {
            s_logger.error( "Error initializing MD SAL Util Services " + e );
        }
    }


    @Override
    public void close() throws Exception {
        mdSalMgr.close();
        s_logger.info("MDSAL Manager Closed");
    }

}
