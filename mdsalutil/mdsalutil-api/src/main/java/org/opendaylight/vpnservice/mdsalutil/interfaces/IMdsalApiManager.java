package org.opendaylight.vpnservice.mdsalutil.interfaces;

//import java.math.BigInteger;
import java.util.List;

import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
//import org.opendaylight.vpnservice.mdsalutil.BucketInfo;
//import org.opendaylight.vpnservice.mdsalutil.DpnState;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.GroupEntity;
//import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
//import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
//import org.opendaylight.vpnservice.mdsalutil.SyncStatus;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;

public interface IMdsalApiManager {

    public void installFlow(FlowEntity flowEntity);

    public void removeFlow(FlowEntity flowEntity);

    public void installGroup(GroupEntity groupEntity);

    public void modifyGroup(GroupEntity groupEntity);

    public void removeGroup(GroupEntity groupEntity);

    public void sendPacketOut(long lDpnId, int groupId, byte[] payload);

    public void sendPacketOutWithActions(long lDpnId, long groupId, byte[] payload, List<ActionInfo> actionInfos);

    public void sendARPPacketOutWithActions(long dpid, byte[] payload, List<ActionInfo> action_info);
    
    public void printTest() ;

 }
