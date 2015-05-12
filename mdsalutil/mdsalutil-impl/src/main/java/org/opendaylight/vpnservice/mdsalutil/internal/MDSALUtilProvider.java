package org.opendaylight.vpnservice.mdsalutil.internal;

import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareConsumer;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ConsumerContext;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.GroupEntity;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MDSALUtilProvider implements BindingAwareConsumer, IMdsalApiManager, AutoCloseable {

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


    @Override
    public void installFlow(FlowEntity flowEntity) {
          mdSalMgr.installFlow(flowEntity);
    }

    @Override
    public void removeFlow(FlowEntity flowEntity) {
        mdSalMgr.removeFlow(flowEntity);
    }

    @Override
    public void installGroup(GroupEntity groupEntity) {
        mdSalMgr.installGroup(groupEntity);
    }


    @Override
    public void modifyGroup(GroupEntity groupEntity) {
        mdSalMgr.modifyGroup(groupEntity);
    }


    @Override
    public void removeGroup(GroupEntity groupEntity) {
        mdSalMgr.removeGroup(groupEntity);
    }


    @Override
    public void sendPacketOut(long lDpnId, int groupId, byte[] payload) {
        mdSalMgr.sendPacketOut(lDpnId, groupId, payload);
    }


    @Override
    public void sendPacketOutWithActions(long lDpnId, long groupId,
            byte[] payload, List<ActionInfo> actionInfos) {
        mdSalMgr.sendPacketOutWithActions(lDpnId, groupId, payload, actionInfos);
    }


    @Override
    public void sendARPPacketOutWithActions(long dpid, byte[] payload,
            List<ActionInfo> action_info) {
        mdSalMgr.sendARPPacketOutWithActions(dpid, payload, action_info);
    }

}
