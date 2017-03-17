/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.stats;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.flow.and.statistics.map.list.FlowAndStatisticsMapList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.Metadata;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.MetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.Direction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.AclPortStats;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.AclPortStatsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.acl.port.stats.AclDropStats;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.acl.port.stats.AclDropStatsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.acl.port.stats.ErrorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.acl.port.stats.acl.drop.stats.BytesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.acl.port.stats.acl.drop.stats.PacketsBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The helper class for ACL live statistics.
 */
public final class AclLiveStatisticsHelper {

    private static final Logger LOG = LoggerFactory.getLogger(AclLiveStatisticsHelper.class);

    /** The Constant COOKIE_ACL_DROP_FLOW_MASK. */
    protected static final BigInteger COOKIE_ACL_DROP_FLOW_MASK = new BigInteger("FFFFFFF", 16);

    /**
     * Gets the acl port stats.
     *
     * @param direction the direction
     * @param interfaceNames the interface names
     * @param odlDirectStatsService the odl direct stats service
     * @param dataBroker the data broker
     * @return the acl port stats
     */
    public static List<AclPortStats> getAclPortStats(Direction direction, List<String> interfaceNames,
            OpendaylightDirectStatisticsService odlDirectStatsService, DataBroker dataBroker) {
        LOG.trace("Get ACL port stats for direction {} and interfaces {}", direction, interfaceNames);
        List<AclPortStats> lstAclPortStats = new ArrayList<>();

        Short tableId = getTableId(direction);
        FlowCookie aclDropFlowCookie = new FlowCookie(AclConstants.COOKIE_ACL_DROP_FLOW);
        FlowCookie aclDropFlowCookieMask = new FlowCookie(COOKIE_ACL_DROP_FLOW_MASK);

        for (String interfaceName : interfaceNames) {
            AclPortStatsBuilder aclStatsBuilder = new AclPortStatsBuilder().setInterfaceName(interfaceName);

            Interface interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
            if (!Optional.fromNullable(interfaceState).isPresent()) {
                String errMsg = "Interface not found in datastore.";
                addError(lstAclPortStats, aclStatsBuilder, errMsg);
                continue;
            }
            BigInteger dpId = AclServiceUtils.getDpIdFromIterfaceState(interfaceState);
            if (!Optional.fromNullable(dpId).isPresent()) {
                String errMsg = "Failed to find device for the interface.";
                addError(lstAclPortStats, aclStatsBuilder, errMsg);
                continue;
            }

            NodeRef nodeRef = buildNodeRef(dpId);
            Integer lportTag = interfaceState.getIfIndex();
            Match metadataMatch = buildMetadataMatch(lportTag);

            GetFlowStatisticsInputBuilder input =
                    new GetFlowStatisticsInputBuilder().setNode(nodeRef).setCookie(aclDropFlowCookie)
                            .setCookieMask(aclDropFlowCookieMask).setMatch(metadataMatch).setStoreStats(false);
            if (direction != Direction.Both) {
                input.setTableId(tableId);
            }

            Future<RpcResult<GetFlowStatisticsOutput>> rpcResultFuture =
                    odlDirectStatsService.getFlowStatistics(input.build());
            RpcResult<GetFlowStatisticsOutput> rpcResult = null;
            try {
                rpcResult = rpcResultFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                String errMsg = "Unable to retrieve drop counts due to error: " + e.getMessage();
                addError(lstAclPortStats, aclStatsBuilder, errMsg);
                LOG.error("Exception occurred during get flow statistics for interface {}", interfaceName, e);
            }

            if (Optional.fromNullable(rpcResult).isPresent() && rpcResult.isSuccessful()
                    && Optional.fromNullable(rpcResult.getResult()).isPresent()) {
                GetFlowStatisticsOutput flowStatsOutput = rpcResult.getResult();
                getAclDropStats(direction, aclStatsBuilder, flowStatsOutput);
                lstAclPortStats.add(aclStatsBuilder.build());
            } else {
                handleRpcErrors(lstAclPortStats, aclStatsBuilder, rpcResult);
            }
        }
        return lstAclPortStats;
    }

    /**
     * Handle rpc errors.
     *
     * @param lstAclPortStats the lst acl port stats
     * @param aclStatsBuilder the acl stats builder
     * @param rpcResult the rpc result
     */
    private static void handleRpcErrors(List<AclPortStats> lstAclPortStats, AclPortStatsBuilder aclStatsBuilder,
            RpcResult<GetFlowStatisticsOutput> rpcResult) {
        LOG.error("Unable to retrieve drop counts due to error: {}", rpcResult);
        String errMsg = "Unable to retrieve drop counts due to error: ";
        if (Optional.fromNullable(rpcResult).isPresent() && Optional.fromNullable(rpcResult.getErrors()).isPresent()) {
            for (RpcError error : rpcResult.getErrors()) {
                errMsg += error.getMessage();
                break;
            }
        } else {
            errMsg += "Internal RPC call failed.";
        }
        addError(lstAclPortStats, aclStatsBuilder, errMsg);
    }

    /**
     * Gets the acl drop stats.
     *
     * @param direction the direction
     * @param aclStatsBuilder the acl stats builder
     * @param flowStatsOutput the flow stats output
     * @return the acl drop stats
     */
    private static void getAclDropStats(Direction direction, AclPortStatsBuilder aclStatsBuilder,
            GetFlowStatisticsOutput flowStatsOutput) {
        List<FlowAndStatisticsMapList> flowAndStatisticsMapList = flowStatsOutput.getFlowAndStatisticsMapList();
        if (!Optional.fromNullable(flowAndStatisticsMapList).isPresent() || flowAndStatisticsMapList.isEmpty()) {
            String errMsg = "Unable to retrieve drop counts as interface is not configured for statistics collection.";
            aclStatsBuilder.setError(new ErrorBuilder().setErrorMessage(errMsg).build());
            return;
        }

        BytesBuilder portEgressBytesBuilder = new BytesBuilder();
        BytesBuilder portIngressBytesBuilder = new BytesBuilder();

        PacketsBuilder portEgressPacketsBuilder = new PacketsBuilder();
        PacketsBuilder portIngressPacketsBuilder = new PacketsBuilder();

        for (FlowAndStatisticsMapList flowStats : flowAndStatisticsMapList) {
            switch (flowStats.getTableId()) {
                case NwConstants.INGRESS_ACL_FILTER_TABLE:
                    if (flowStats.getPriority().equals(AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY)) {
                        portEgressBytesBuilder.setInvalidDropCount(flowStats.getByteCount().getValue());
                        portEgressPacketsBuilder.setInvalidDropCount(flowStats.getPacketCount().getValue());
                    } else if (flowStats.getPriority().equals(AclConstants.CT_STATE_TRACKED_NEW_DROP_PRIORITY)) {
                        portEgressBytesBuilder.setDropCount(flowStats.getByteCount().getValue());
                        portEgressPacketsBuilder.setDropCount(flowStats.getPacketCount().getValue());
                    }
                    break;

                case NwConstants.EGRESS_ACL_FILTER_TABLE:
                    if (flowStats.getPriority().equals(AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY)) {
                        portIngressBytesBuilder.setInvalidDropCount(flowStats.getByteCount().getValue());
                        portIngressPacketsBuilder.setInvalidDropCount(flowStats.getPacketCount().getValue());
                    } else if (flowStats.getPriority().equals(AclConstants.CT_STATE_TRACKED_NEW_DROP_PRIORITY)) {
                        portIngressBytesBuilder.setDropCount(flowStats.getByteCount().getValue());
                        portIngressPacketsBuilder.setDropCount(flowStats.getPacketCount().getValue());
                    }
                    break;

                default:
                    LOG.warn("Invalid table ID filtered for Acl flow stats: {}", flowStats);
                    break;
            }
        }

        List<AclDropStats> lstAclDropStats = new ArrayList<>();
        if (direction == Direction.Egress || direction == Direction.Both) {
            AclDropStats aclEgressDropStats = new AclDropStatsBuilder().setDirection(Direction.Egress)
                    .setBytes(portEgressBytesBuilder.build()).setPackets(portEgressPacketsBuilder.build()).build();
            lstAclDropStats.add(aclEgressDropStats);
        }

        if (direction == Direction.Ingress || direction == Direction.Both) {
            AclDropStats aclIngressDropStats = new AclDropStatsBuilder().setDirection(Direction.Ingress)
                    .setBytes(portIngressBytesBuilder.build()).setPackets(portIngressPacketsBuilder.build()).build();
            lstAclDropStats.add(aclIngressDropStats);
        }
        aclStatsBuilder.setAclDropStats(lstAclDropStats);
    }

    /**
     * Adds the error.
     *
     * @param lstAclPortStats the lst acl port stats
     * @param aclStatsBuilder the acl stats builder
     * @param errMsg the error message
     */
    private static void addError(List<AclPortStats> lstAclPortStats, AclPortStatsBuilder aclStatsBuilder,
            String errMsg) {
        aclStatsBuilder.setError(new ErrorBuilder().setErrorMessage(errMsg).build());
        lstAclPortStats.add(aclStatsBuilder.build());
    }

    /**
     * Builds the metadata match.
     *
     * @param lportTag the lport tag
     * @return the match
     */
    protected static Match buildMetadataMatch(Integer lportTag) {
        Metadata metadata = new MetadataBuilder().setMetadata(MetaDataUtil.getLportTagMetaData(lportTag))
                .setMetadataMask(MetaDataUtil.METADATA_MASK_LPORT_TAG).build();
        return new MatchBuilder().setMetadata(metadata).build();
    }

    /**
     * Gets the table id.
     *
     * @param direction the direction
     * @return the table id
     */
    private static Short getTableId(Direction direction) {
        Short tableId;
        if (direction == Direction.Egress) {
            tableId = NwConstants.INGRESS_ACL_FILTER_TABLE;
        } else {
            // in case of ingress
            tableId = NwConstants.EGRESS_ACL_FILTER_TABLE;
        }
        return tableId;
    }

    /**
     * Builds the node ref.
     *
     * @param dpId the dp id
     * @return the node ref
     */
    @SuppressWarnings("deprecation")
    private static NodeRef buildNodeRef(BigInteger dpId) {
        return new NodeRef(InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:" + dpId))).build());
    }
}
