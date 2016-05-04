/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.bgpmanager.api;

import java.util.Collection;

public interface IBgpManager {

    /**
     *
     * @param rd
     * @param importRts
     * @param exportRts
     */
    public void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts) throws Exception;

    /**
     *
     * @param rd
     */
    public void deleteVrf(String rd) throws Exception;

    /**
     *
     * @param rd
     * @param prefix
     * @param nextHop
     * @param vpnLabel
     */
    public void addPrefix(String rd, String prefix, String nextHop, int vpnLabel) throws Exception;

    /**
     *
     * @param rd
     * @param prefix
     */
    public void deletePrefix(String rd, String prefix) throws Exception;

    /**
     *
     * @param fileName
     * @param logLevel
     */
    public void setQbgpLog(String fileName, String logLevel) throws Exception;

    /**
     * @param rd
     * @param prefix
     * @param nextHop
     * @param vpnLabel
     */
    public void advertisePrefix(String rd, String prefix, String nextHop, int vpnLabel) throws Exception;

    /**
     *
     * @param rd
     * @param prefix
     */
    public void withdrawPrefix(String rd, String prefix) throws Exception;


    public String getDCGwIP();

}
