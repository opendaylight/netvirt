package org.opendaylight.netvirt.aclservice.utils;


/**
 * Created by nishchyag on 05-09-2017.
 */
public class Commands {
    static AclDataUtil aclutl;

    public static void setAclDataUtil(AclDataUtil aclDataUtil) {
        aclutl = aclDataUtil;
    }

    public static AclDataUtil getAclDataUtil(){
        return aclutl;
    }
}
