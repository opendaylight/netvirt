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

}