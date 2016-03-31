/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.itm.cli;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

/**
 * The Utility class for ITM CLI.
 */
public final class ItmCliUtils {

    /**
     * Construct dpn id list.
     *
     * @param dpnIds
     *            the dpn ids
     * @return the list
     */
    public static List<BigInteger> constructDpnIdList(final String dpnIds) {
        final List<BigInteger> lstDpnIds = new ArrayList<>();
        if (StringUtils.isNotBlank(dpnIds)) {
            final String[] arrDpnIds = StringUtils.split(dpnIds, ',');
            for (String dpn : arrDpnIds) {
                if (StringUtils.isNumeric(StringUtils.trim(dpn))) {
                    lstDpnIds.add(new BigInteger(StringUtils.trim(dpn)));
                } else {
                    Preconditions.checkArgument(false, String.format("DPN ID [%s] is not a numeric value.", dpn));
                }
            }
        }
        return lstDpnIds;
    }
}
