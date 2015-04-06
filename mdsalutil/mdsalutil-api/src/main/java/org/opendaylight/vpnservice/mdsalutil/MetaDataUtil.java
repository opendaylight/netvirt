package org.opendaylight.vpnservice.mdsalutil;

import java.math.BigInteger;

public class MetaDataUtil {

    public static final BigInteger METADATA_NO_MASK = new BigInteger("0000000000000000", 16);
    public static final BigInteger METADATA_MASK_SCF_MATCH = new BigInteger("FF00000000000000", 16);
    public static final BigInteger METADATA_MASK_SUBP_MATCH = new BigInteger("00000000FFFF0000", 16);
    public static final BigInteger METADATA_MASK_APPP_MATCH = new BigInteger("000000000000FFFF", 16);
    public static final BigInteger METADATA_MASK_LPORT_MATCH = new BigInteger("00FFFF0000000000", 16);
    public static final BigInteger METADATA_MASK_SCID_MATCH = new BigInteger("000000000000FFFF", 16);
    public static final BigInteger METADATA_MASK_SCID_WRITE = new BigInteger("000000000000FFFF", 16);
    public static final BigInteger METADATA_MASK_SUBP_WRITE = new BigInteger("00000000FFFF0000", 16);
    public static final BigInteger METADATA_MASK_APPP_WRITE = new BigInteger("000000000000FFFF", 16);
    public static final BigInteger MASK_DMAC_WRITE = new BigInteger("0000FFFFFFFFFFFF", 16);
    public static final BigInteger METADATA_MASK_SCF_WRITE = new BigInteger("FF00000000000000", 16);
    public static final BigInteger METADATA_MASK_LPORT_WRITE = new BigInteger("00FFFF0000000000", 16);
    public static final BigInteger METADATA_MASK_LPORT_TAG = new BigInteger("1FFFFF0000000000", 16);
    public static final BigInteger METADATA_MASK_SERVICE_INDEX = new BigInteger("E000000000000000", 16);
    public static final BigInteger METADATA_MASK_SERVICE = new BigInteger("000000FFFF000000", 16);
    public static final BigInteger METADA_MASK_TUNNEL_ID_VNI = new BigInteger("00000000FFFFFF00", 16);
    public static final BigInteger METADATA_MASK_LABEL_ITM = new BigInteger("40FFFFFF000000FF", 16);
    public static final BigInteger METADA_MASK_VALID_TUNNEL_ID_BIT_AND_TUNNEL_ID = new BigInteger("08000000FFFFFF00", 16);
    public static final BigInteger METADATA_MASK_LABEL_L3 = new BigInteger("000000FFFF000000", 16);
    public static final BigInteger METADATA_MASK_VRFID = new BigInteger("00000000FFFFFFFF", 16);

    public static BigInteger getMetadataSCF(int scfTag) {
        return (new BigInteger("FF", 16).and(BigInteger.valueOf(scfTag))).shiftLeft(56);
    }

    public static BigInteger getMetadataSCID(int scId) {
        return BigInteger.valueOf(scId).and(new BigInteger("FFFF", 16));
    }

    public static BigInteger getMetadataSubProfID(int subProfId) {
        return (BigInteger.valueOf(subProfId).and(new BigInteger("FFFF", 16))).shiftLeft(16);
    }

    public static BigInteger getMetadataAppProfID(int appProfId) {
        return BigInteger.valueOf(appProfId).and(new BigInteger("FFFF", 16));
    }

    public static BigInteger getMetadataAPPP(int appProfId) {
        return BigInteger.valueOf(appProfId).and(new BigInteger("FFFF", 16));
    }

    public static BigInteger getCookieSCFEthTypeFilter(int scfTag) {
        return MetaDataConstants.COOKIE_SCF_BASE.add(new BigInteger("0120000", 16)).add(BigInteger.valueOf(scfTag));
    }

    public static BigInteger getCookieSubFilter(int scfTag) {
        return MetaDataConstants.COOKIE_SCF_BASE.add(new BigInteger("0310000", 16)).add(BigInteger.valueOf(scfTag));
    }

    public static BigInteger getCookieProfMap(int scfTag) {
        return MetaDataConstants.COOKIE_SCF_BASE.add(new BigInteger("0510000", 16)).add(BigInteger.valueOf(scfTag));
    }

    public static BigInteger getCookieSCFAppFilter(int scfTag) {
        return MetaDataConstants.COOKIE_SCF_BASE.add(new BigInteger("0410000", 16)).add(BigInteger.valueOf(scfTag));
    }

    public static BigInteger getEthDestForIpNextHop(int groupId) {
        return BigInteger.valueOf(groupId).and(MASK_DMAC_WRITE);
    }
    public static long getIpAddress(byte[] rawIpAddress) {
        return (((rawIpAddress[0] & 0xFF) << (3 * 8)) + ((rawIpAddress[1] & 0xFF) << (2 * 8))
                + ((rawIpAddress[2] & 0xFF) << (1 * 8)) + (rawIpAddress[3] & 0xFF)) & 0xffffffffL;
    }

    public static BigInteger getMetadataLPort(int lPortTag) {
        return (new BigInteger("FFFF", 16).and(BigInteger.valueOf(lPortTag))).shiftLeft(40);
    }

    public static BigInteger getMetadataScHop(int scfInstanceTag, int scfPortTag, int serviceChainId) {
        return getMetadataSCF(scfInstanceTag).or(getMetadataLPort(scfPortTag)).or(getMetadataSCID(serviceChainId));
    }

    public static BigInteger getMetadataMaskScHop() {
        return METADATA_MASK_SCF_WRITE.or(METADATA_MASK_LPORT_WRITE).or(METADATA_MASK_SCID_WRITE);
    }

    public static BigInteger getCookieSCHop(int scfInstanceTag) {
        return MetaDataConstants.COOKIE_SCF_BASE.add(new BigInteger("0610000", 16)).add(BigInteger.valueOf(scfInstanceTag));
    }

    public static BigInteger getMetadataScfPort(int scfInstanceTag, int scfPortTag) {
        return getMetadataSCF(scfInstanceTag).or(getMetadataLPort(scfPortTag));
    }

    public static BigInteger getMetadataMaskScfPort() {
        return METADATA_MASK_LPORT_WRITE.or(METADATA_MASK_SCF_WRITE);
    }

    public static BigInteger getCookieSCFPort() {
        return new BigInteger("5000000", 16);
    }

    public static BigInteger getCookieSCFIpv4EthTypeFilter(int scfInstanceTag) {
        return MetaDataConstants.COOKIE_SCF_BASE.add(new BigInteger("0100000", 16)).add(BigInteger.valueOf(scfInstanceTag));
    }

    public static BigInteger getCookieSCFArpEthTypeFilter(int scfInstanceTag) {
        return MetaDataConstants.COOKIE_SCF_BASE.add(new BigInteger("0110000", 16)).add(BigInteger.valueOf(scfInstanceTag));
    }

    public static BigInteger getLportTagMetaData(int lportTag) {
        return new BigInteger("1FFFFF", 16).and(BigInteger.valueOf(lportTag)).shiftLeft(40);
    }

    public static BigInteger getLportFromMetadata(BigInteger metadata) {
        return (metadata.and(METADATA_MASK_LPORT_TAG)).shiftRight(40);
    }

    public static BigInteger getServiceIndexMetaData(int serviceIndex) {
        return new BigInteger("7", 16).and(BigInteger.valueOf(serviceIndex)).shiftLeft(61);
    }

    public static BigInteger getMetaDataForLPortDispatcher(int lportTag, short serviceIndex) {
        return getServiceIndexMetaData(serviceIndex).or(getLportTagMetaData(lportTag));
    }

    public static BigInteger getMetaDataMaskForLPortDispatcher() {
        return METADATA_MASK_SERVICE_INDEX.or(METADATA_MASK_LPORT_TAG);
    }

    public static BigInteger getWriteMetaDataMaskForServicePorts() {
        return METADATA_MASK_SERVICE_INDEX.or(METADATA_MASK_LPORT_TAG).or(METADATA_MASK_SERVICE);
    }

    public static BigInteger getMetaDataMaskForLPortDispatcher(BigInteger metadataMaskForServiceIndex, 
            BigInteger metadataMaskForLPortTag, BigInteger metadataMaskForService) {
        return metadataMaskForServiceIndex.or(metadataMaskForLPortTag).or(metadataMaskForService);
    }

    public static BigInteger getMetaDataForLPortDispatcher(int lportTag, short serviceIndex,
            BigInteger serviceMetaData) {
        return getServiceIndexMetaData(serviceIndex).or(getLportTagMetaData(lportTag)).or(serviceMetaData);
    }


    public static BigInteger getVmLportTagMetaData(int vrfId) {
        return BigInteger.valueOf(vrfId);
    }

    public static BigInteger getTunnelIdWithVni(int vni) {
        return BigInteger.valueOf(vni).shiftLeft(8);
    }

    /**
     * For the tunnel id with VNI and valid-vni-flag set, the most significant byte
     * should have 08. So, shifting 08 to 7 bytes (56 bits) and the result is OR-ed with
     * VNI being shifted to 1 byte.
     */
    public static BigInteger getTunnelIdWithValidVniBitAndVniSet(int vni) {
        return BigInteger.valueOf(0X08).shiftLeft(56).or(BigInteger.valueOf(vni).shiftLeft(8));
    }
}
