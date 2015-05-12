package org.opendaylight.vpnservice.mdsalutil.interfaces;

import java.util.List;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.GroupEntity;

public interface IMdsalApiManager {

    public void installFlow(FlowEntity flowEntity);

    public void removeFlow(FlowEntity flowEntity);

    public void installGroup(GroupEntity groupEntity);

    public void modifyGroup(GroupEntity groupEntity);

    public void removeGroup(GroupEntity groupEntity);

    public void sendPacketOut(long lDpnId, int groupId, byte[] payload);

    public void sendPacketOutWithActions(long lDpnId, long groupId, byte[] payload, List<ActionInfo> actionInfos);

    public void sendARPPacketOutWithActions(long dpid, byte[] payload, List<ActionInfo> action_info);

}
