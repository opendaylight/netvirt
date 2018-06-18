tables = {
    0: "INGRESS",
    10: "VXLAN_TRUNK",
    11: "TRUNK_L2",
    12: "GRE_TRUNK",
    17: "DISPATCHER",
    18: "DHCP_EXT_TUN",
    19: "L3_GW_MAC",
    20: "L3_LFIB",
    21: "L3_FIB",
    22: "L3_SUBNET_RT",
    23: "L3VNI_EXT_TUN",
    24: "L2VNI_EXT_TUN",
    25: "PDNAT",
    26: "PSNAT",
    27: "DNAT",
    28: "SNAT",
    36: "INT_TUN",
    38: "EXT_TUN",
    43: "ARP_CHK",
    44: "IN_NAPT",
    45: "IPV6",
    46: "OUT_NAPT",
    47: "NAPT_FIB",
    48: "ELAN_BASE",
    49: "ELAN_SMAC_LRN",
    50: "ELAN_SMAC",
    51: "ELAN_DMAC",
    52: "ELAN_UNK_DMAC",
    55: "ELAN_FILTER",
    60: "DHCP",
    70: "SCF_UP_SUB_TCP",
    72: "SCF_DOWN_SUB_TCP",
    75: "SCF_CHAIN_FWD",
    80: "L3_INTF",
    81: "ARP_RESPONDER",
    82: "SFC_TRANS_CLASS",
    83: "SFC_TRANS_IN",
    84: "SFC_TRANS_PMAP",
    86: "SFC_TRANS_PMAP_NHOP",
    87: "SFC_TRANS_EG",
    90: "QOS_DSCP",
    100: "SFC_IN_CLASS_FILTER",
    101: "SFC_IN_CLASS_ACL",
    180: "COE_KBPRXY",
    210: "IN_ACL_ASPF",
    211: "IN_ACL_CTRK_CLASS",
    212: "IN_ACL_CTRK_SNDR",
    213: "IN_ACL_EXISTING",
    214: "IN_ACL_FLTR_DISP",
    215: "IN_ACL_RULE_FLTR",
    216: "IN_ACL_REM",
    217: "IN_ACL_CMTR",
    219: "IN_CTRS",
    220: "EG_LPORT_DISP",
    221: "EG_SFC_CLASS_FLTR",
    222: "EG_SFC_CLASS_NHOP",
    223: "EG_SFC_CLASS_EG",
    230: "EG_POL_CLASS",
    231: "EG_POL_RT",
    239: "EG_ACL_DUMMY",
    240: "EG_ACL_ASPF",
    241: "EG_ACL_CTRK_CLASS",
    242: "EG_ACL_CTRK_SNDR",
    243: "EG_ACL_EXISTING",
    244: "EG_ACL_FLTR_DISP",
    245: "EG_ACL_RULE_FLTR",
    246: "EG_ACL_REM",
    247: "EG_ACL_CMTR",
    249: "EG_CTRS"
}


def get_table_name(table_id):
    if table_id in tables:
        return tables[table_id]
    else:
        return "unknown:{}".format(table_id)


table_model_map = {
    'ifm': [0, 17, 220],
    'l3vpn': [19, 20, 21, 22, 36, 81],
    'nat': [25, 26, 27, 28, 44, 46, 47],
    'elan': [50, 51, 52, 55],
    'acl': [211, 212, 213, 214, 215, 241, 242, 243, 244, 245]
}


def get_table_map(key):
    return table_model_map.get(key)


def get_all_modules():
    return list(table_model_map.keys())
