/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;


import java.math.BigInteger;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.node.connector.statistics.and.port.number.map.NodeConnectorStatisticsAndPortNumberMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QosAlertPortData {
    private Port port;
    private static INeutronVpnManager neutronVpnManager;
    private BigInteger rxPackets;
    private BigInteger rxDroppedPackets;
    private static BigInteger   alertThreshold;
    private boolean statsDataInit;

    private static final BigInteger BIG_HUNDRED = new BigInteger("100");
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertPortData.class);


    public QosAlertPortData(final Port port, final INeutronVpnManager neutronVpnManager) {
        this.port = port;
        QosAlertPortData.neutronVpnManager = neutronVpnManager;
        statsDataInit = false;
    }

    public static void setAlertThreshold(int threshold) {
        alertThreshold = BigInteger.valueOf(threshold);
        LOG.info("setAlertThreshold:{}", alertThreshold);
    }

    public void updatePortStatistics(NodeConnectorStatisticsAndPortNumberMap statsData) {
        if (statsDataInit) {
            calculateAlertCondition(statsData);
        }
        rxPackets = statsData.getPackets().getReceived();
        rxDroppedPackets = statsData.getReceiveDrops();
        statsDataInit = true;
    }

    private void calculateAlertCondition(NodeConnectorStatisticsAndPortNumberMap statsData)  {
        BigInteger rxDiff = statsData.getPackets().getReceived().subtract(rxPackets);
        BigInteger rxDroppedDiff = statsData.getReceiveDrops().subtract(rxDroppedPackets);

        BigInteger rxTotalDiff = rxDiff.add(rxDroppedDiff);
        LOG.trace("Port {} rxDiff:{} rxDropped diff:{} total diff:{}", port.getUuid(), rxDiff,
                                                                            rxDroppedDiff, rxTotalDiff);
        QosPolicy qosPolicy = QosNeutronUtils.getQosPolicy(neutronVpnManager, port);

        if (qosPolicy == null) {
            return;
        }

        if (rxDroppedDiff.multiply(BIG_HUNDRED).compareTo(rxTotalDiff.multiply(alertThreshold)) > 0) {
            LOG.trace(QosConstants.alertMsgFormat, qosPolicy.getName(), qosPolicy.getUuid().getValue(),
                    port.getUuid().getValue(), port.getNetworkId().getValue(), statsData.getPackets().getReceived(),
                                                                                        statsData.getReceiveDrops());

            QosAlertGenerator.raiseAlert(qosPolicy.getName(), qosPolicy.getUuid().getValue(),
                    port.getUuid().getValue(), port.getNetworkId().getValue(), statsData.getPackets().getReceived(),
                                                                                          statsData.getReceiveDrops());
        }

    }
}
