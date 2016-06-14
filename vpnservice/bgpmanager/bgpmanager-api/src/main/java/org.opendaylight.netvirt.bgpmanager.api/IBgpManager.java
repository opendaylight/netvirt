/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.api;

import java.util.Collection;

public interface IBgpManager {

    /**
     *
     * @param rd
     * @param importRts
     * @param exportRts
     */
    void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts) throws Exception;

    /**
     *
     * @param rd
     */
    void deleteVrf(String rd) throws Exception;

    /**
     *
     * @param rd
     * @param prefix
     * @param nextHop
     * @param vpnLabel
     */
    void addPrefix(String rd, String prefix, String nextHop, int vpnLabel) throws Exception;

    /**
     *
     * @param rd
     * @param prefix
     */
    void deletePrefix(String rd, String prefix) throws Exception;

    /**
     *
     * @param fileName
     * @param logLevel
     */
    void setQbgpLog(String fileName, String logLevel) throws Exception;

    /**
     * @param rd
     * @param prefix
     * @param nextHop
     * @param vpnLabel
     */
    void advertisePrefix(String rd, String prefix, String nextHop, int vpnLabel) throws Exception;

    /**
     *
     * @param rd
     * @param prefix
     */
    void withdrawPrefix(String rd, String prefix) throws Exception;


    String getDCGwIP();

}
