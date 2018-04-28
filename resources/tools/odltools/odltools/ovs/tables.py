tables = {
    0: "INGRESS",
    17: "DISPATCHER",
    18: "DHCP_EXT_TUN",
    19: "L3_GW_MAC",
    20: "L3_LFIB",
    22: "L3_SUBNET_RT",
    23: "L3VNI_EXT_TUN",
    36: "INT_TUN",
    38: "EXT_TUN",
    45: "IPV6",
    48: "ELAN_BASE",
    50: "ELAN_SMAC",
    51: "ELAN_DMAC",
    52: "ELAN_UNK_DMAC",
    55: "ELAN_FILTER",
    60: "DHCP",
    80: "L3_INTF",
    81: "ARP_RESPONDER",
    90: "QOS_DSCP",
    211: "IN_ACL",
    212: "IN_ACL_REM",
    213: "IN_ACL_FILTER",
    220: "EG_LPORT_DISP",
    230: "EG_POL_CLASS",
    231: "EG_POL_RT",
    241: "EG_ACL",
    242: "EG_ACL_REM",
    243: "EG_LEARN_REM"
}


def get_table_name(table_id):
    if table_id in tables:
        return tables[table_id]
    else:
        return "unknown:{}".format(table_id)


table_model_map = {
    'ifm': [0, 17, 220],
    'l3vpn': [19, 20, 21, 22, 36, 81],
    'elan': [50, 51, 52, 55],
    'acl': [211, 212, 213, 214, 215, 241, 242, 243, 244, 245]
}


def get_table_map(key):
    if key in table_model_map:
        return table_model_map[key]
    else:
        return None
