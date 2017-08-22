import netvirt_utils as utils


OPTIONALS = ['ifname', 'lport','elan-tag']
MAC_LEN = 17

# Flow table constants

PREFIX_211_GOTO = 'Egress_Fixed_Goto_Classifier_'
PREFIX_211_DHCPSv4 = 'Egress_DHCP_Server_v4'
PREFIX_211_DHCPSv6 = 'Egress_DHCP_Server_v6_'
PREFIX_211_DHCPCv4 = 'Egress_DHCP_Client_v4'
PREFIX_211_DHCPCv6 = 'Egress_DHCP_Client_v6_'
PREFIX_211_ARP = 'Egress_ARP_'
PREFIX_211_L2BCAST = 'Egress_L2Broadcast_'
PREFIX_211_ICMPv6 = 'Egress_ICMPv6_'

PREFIX_213 = 'Egress_Fixed_Conntrk_'
PREFIX_214 = 'Egress_Fixed_Conntrk_Drop'
PREFIX_215 = 'Egress_Fixed_NonConntrk_Drop'
PREFIX_241_DHCPv4 = 'Ingress_DHCP_Server_v4'
PREFIX_241_DHCPv6 = 'Ingress_DHCP_Server_v6_'
PREFIX_241_ICMPv6 = 'Ingress_ICMPv6_'
PREFIX_241_ARP = 'Ingress_ARP_'
PREFIX_241_BCASTv4 = 'Ingress_v4_Broadcast_'
PREFIX_241_GOTO = 'Ingress_Fixed_Goto_Classifier_'
PREFIX_243 = 'Ingress_Fixed_Conntrk_'
PREFIX_244 = 'Ingress_Fixed_Conntrk_Drop'
PREFIX_245 = 'Ingress_Fixed_NonConntrk_Drop'

PREFIX_FOR_LPORT = {211:[PREFIX_211_GOTO, PREFIX_211_DHCPSv4, PREFIX_211_DHCPSv6,
                      PREFIX_211_DHCPCv4, PREFIX_211_DHCPCv6, PREFIX_211_ARP,
                      PREFIX_211_L2BCAST, PREFIX_211_ICMPv6],
                    212:[],
                    213:[PREFIX_213],
                    214:[PREFIX_214],
                    215:[PREFIX_215],
                    241:[PREFIX_241_DHCPv4, PREFIX_241_DHCPv6, PREFIX_241_ICMPv6,
                         PREFIX_241_ARP, PREFIX_241_BCASTv4, PREFIX_241_GOTO],
                    242:[],
                    243:[PREFIX_243],
                    244:[PREFIX_244],
                    245:[PREFIX_245] }

PREFIX_SGR_ETHER = 'ETHERnull_'
PREFIX_SGR_ICMP = 'ICMP_'
PREFIX_SGR_TCP = 'TCP_'
PREFIX_SGR_UDP = 'UDP_'
PREFIX_SGR_OTHER = 'OTHER_PROTO'

PREFIX_LPORT_SGR = {211:[],212:[],213:[],
                    214:[PREFIX_SGR_ETHER, PREFIX_SGR_ICMP, PREFIX_SGR_TCP,
                         PREFIX_SGR_UDP, PREFIX_SGR_OTHER],
                    215:[PREFIX_SGR_ETHER, PREFIX_SGR_ICMP, PREFIX_SGR_TCP,
                         PREFIX_SGR_UDP, PREFIX_SGR_OTHER],
                    241:[], 242:[], 243:[],
                    244:[PREFIX_SGR_ETHER, PREFIX_SGR_ICMP, PREFIX_SGR_TCP,
                         PREFIX_SGR_UDP, PREFIX_SGR_OTHER],
                    245:[PREFIX_SGR_ETHER, PREFIX_SGR_ICMP, PREFIX_SGR_TCP,
                         PREFIX_SGR_UDP, PREFIX_SGR_OTHER]
                    }
#Metadata consts
ELAN_LPORT_MASK = 0x1fffff0000000000
ELAN_LPORT_MASK_ZLEN = 10 # no. of trailing 0s in lport mask
ELAN_ELTAG_MASK = 0x000000ffff000000
ELAN_HEX_LEN = 4


def create_flow_dict(flow_info, flow):
    flow_dict = {}
    flow_dict['table'] = flow['table_id']
    flow_dict['id'] = flow['id']
    flow_dict['name'] = flow.get('flow-name')
    flow_dict['flow'] = flow
    flow_dict['dpnid'] = flow_info['dpnid']
    for opt in OPTIONALS:
        if flow_info.get(opt):
            flow_dict[opt] = flow_info.get(opt)
    return flow_dict


def stale_ifm_flow(ifaces, flow, flow_info):
    get_flow_info_from_ifm_table(flow_info, flow)
    flow_ifname = flow_info['ifname']
    if flow_ifname is not None and not ifaces.get(flow_ifname):
        return create_flow_dict(flow_info,flow)
    else:
        return None


def stale_elan_flow(ifindexes, flow, flow_info):
    # hex(int(mask, 16) & int(hexa, 16))
    get_flow_info_from_elan_table(flow_info, flow)
    lport = flow_info.get('lport')
    ifname = ifindexes.get(lport) if lport else None
    if lport and not ifname:
        return create_flow_dict(flow_info, flow)
    else:
        return None


def stale_acl_flow(ifaces, ifindexes, flow, flow_info):
    get_flow_info_from_acl_table(flow_info, flow)
    lport = flow_info.get('lport')
    ifname = ifindexes.get(lport) if lport else None
    if lport and not ifname:
        return create_flow_dict(flow_info, flow)
    else:
        return None


### Methods to extract flow fields
def get_instruction_writemeta(flow):
    for instruction in flow['instructions']['instruction']:
        if 'write-metadata' in instruction:
            return instruction['write-metadata']
    return None


def get_inst_reg6load(flow):
    for instruction in flow['instructions']['instruction']:
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions']['action']:
                if 'openflowplugin-extension-nicira-action:nx-reg-load' in action:
                    return action['openflowplugin-extension-nicira-action:nx-reg-load']
    return None


def get_match_metadata(flow):
    return flow['match'].get('metadata')


def get_match_reg6(flow):
    if 'openflowplugin-extension-general:extension-list' in flow['match']:
        for ofex in flow['match']['openflowplugin-extension-general:extension-list']:
            if ofex['extension-key'] == 'openflowplugin-extension-nicira-match:nxm-nx-reg6-key':
                return ofex['extension']['openflowplugin-extension-nicira-match:nxm-nx-reg']
    return None


### Table specific parsing

def get_ifname_from_flowid(flow_id, table):
    splitter = ':' if table == 0 else '.'
    i = 2 if table == 0 else 1
    ifname = None
    try:
        ifname = flow_id.split(splitter)[i]
    except IndexError:
        tun_index = flow_id.find('tun')
        if tun_index > -1:
            ifname = flow_id[tun_index:]
    return ifname


def get_flow_info_from_ifm_table(flow_info, flow):
    flow_info['ifname'] = get_ifname_from_flowid(flow['id'], flow['table_id'])
    return flow_info


def get_lport_elan_tags_from_flowid(flowid, dpnid):
    res = flowid[:-MAC_LEN].split(dpnid)
    lport = res[1]
    elan = res[0][2:]
    return lport, elan


def get_flow_info_from_elan_table(flow_info, flow):
    m_metadata = get_match_metadata(flow)
    if m_metadata:
        metadata = m_metadata['metadata']
        mask = m_metadata['metadata-mask']
        if (mask & ELAN_ELTAG_MASK):
            elan = ('%x' % (metadata & ELAN_ELTAG_MASK))[:ELAN_HEX_LEN]
            flow_info['elan-tag'] = int(elan,16)
        if (mask & ELAN_LPORT_MASK):
            lport = ('%x' % (metadata & ELAN_LPORT_MASK))[:-ELAN_LPORT_MASK_ZLEN]
            flow_info['lport'] = int(lport,16)

    if not flow_info.get('lport'):
        reg6_load = get_inst_reg6load(flow)
        if reg6_load and reg6_load.get('value'):
            # reg6load value is lport lft-shit by 8 bits.
            lport = ('%x' % reg6_load.get('value'))[:-2]
            flow_info['lport'] = int(lport,16)
    return flow_info


def get_flow_info_from_acl_table(flow_info, flow):
    """
        Format for ACL flow ids is as follows:
        211:Egress_Fixed_Goto_Classifier_<dpId>_<lportTag>_<attachMac>_<attachIp>,
            Egress_DHCP_Server_v4<dpId>_<lportTag>__Drop_,
            Egress_DHCP_Server_v6_<dpId>_<lportTag>__Drop_,
            Egress_DHCP_Client_v4<dpId>_<lportTag>_<macAddress>_Permit_,
            Egress_DHCP_Client_v6_<dpId>_<lportTag>_<macAddress>_Permit_,
            Egress_ARP_<dpId>_<lportTag>_<allowedAddressMac><allowedAddressIp>,
            Egress_L2Broadcast_<dpId>_<lportTag>_<attachMac>,
            Egress_ICMPv6_<dpId>_<lportTag>_134_Drop_,
            Egress_ICMPv6_<dpId>_<lportTag>_<icmpv6Type>_<allowedAddressMac>_Permit_,
            Egress_Fixed_Goto_Classifier_<dpId>_<lportTag>_<attachMac>_<attachIp>

        212:Fixed_Conntrk_Classifier_<dpId>_212_<etherType>_<protocol>
        213:Egress_Fixed_Conntrk_<dpId>_<lportTag>_<etherType>_Recirc
        214:Fixed_Conntrk_Trk_<dpId>_Tracked_Established17,
            Fixed_Conntrk_Trk_<dpId>_Tracked_Related17,
            Egress_Fixed_Conntrk_Drop<dpId>_<lportTag>_Tracked_New,
            Egress_Fixed_Conntrk_Drop<dpId>_<lportTag>_Tracked_Invalid,
            ETHERnull_Egress_<lportTag>_<sgRuleId>,
            ETHERnull_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            ICMP__Egress_<lportTag>_<sgRuleId>,
            ICMP_V4_DESTINATION_<type><code>__Egress_<lportTag>_<sgRuleId>,
            ICMP_V6_DESTINATION_<type><code>__Egress_<lportTag>_<sgRuleId>,
            ICMP__ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            ICMP__ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            ICMP_V4_DESTINATION_<type><code>__ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            ICMP_V6_DESTINATION_<type><code>__ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_Egress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_<port>_<portMask>_Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_Egress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_Egress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,

        215:Egress_Fixed_NonConntrk_Drop<dpId>_<lportTag>_ACL_Rule_Miss,
            ETHERnull_Egress_<lportTag>_<sgRuleId>,
            ETHERnull_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            ETHERnull_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            ICMP__Egress_<lportTag>_<sgRuleId>,
            ICMP_V4_DESTINATION_<type><code>__Egress_<lportTag>_<sgRuleId>,
            ICMP_V6_DESTINATION_<type><code>__Egress_<lportTag>_<sgRuleId>,
            ICMP__ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            ICMP__ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            ICMP_V4_DESTINATION_<type><code>__ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            ICMP_V6_DESTINATION_<type><code>__ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_Egress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_<port>_<portMask>_Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_Egress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_Egress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_ipv4_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_ipv6_remoteACL_interface_aap_<remoteIp>_Egress_<lportTag>_<sgRuleId>

        241:Ingress_v4_Broadcast_<dpId>_Permit,
            Ingress_L2_Broadcast_<dpId>_Permit,
            Ingress_DHCP_Server_v4<dpId>_<lportTag>__Permit_,
            Ingress_DHCP_Server_v6_<dpId>_<lportTag>___Permit_,
            Ingress_ICMPv6_<dpId>_<lportTag>_130_Permit_,
            Ingress_ICMPv6_<dpId>_<lportTag>_134_LinkLocal_Permit_,
            Ingress_ICMPv6_<dpId>_<lportTag>_135_Permit_,
            Ingress_ICMPv6_<dpId>_<lportTag>_136_Permit_,
            Ingress_ARP_<dpId>_<lportTag>,
            Ingress_v4_Broadcast_<dpId>_<lportTag>_<broadcastAddress>_Permit,
            Ingress_Fixed_Goto_Classifier_<dpId>_<lportTag>_<attachMac>_<attachIp>

        242:Fixed_Conntrk_Classifier_<dpId>_242_<etherType>_<protocol>

        243:Ingress_Fixed_Conntrk_<dpId>_<lportTag>_<etherType>_Recirc

        244:Fixed_Conntrk_Trk_<dpId>_Tracked_Established220
            Fixed_Conntrk_Trk_<dpId>_Tracked_Related220,
            Ingress_Fixed_Conntrk_Drop<dpId>_<lportTag>_Tracked_New,
            Ingress_Fixed_Conntrk_Drop<dpId>_<lportTag>_Tracked_Invalid,
            ETHERnull_Ingress_<lportTag>_<sgRuleId>,
            ETHERnull_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ETHERnull_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ICMP__Ingress_<lportTag>_<sgRuleId>,
            ICMP_V4_DESTINATION_<type><code>__Ingress_<lportTag>_<sgRuleId>,
            ICMP_V6_DESTINATION_<type><code>__Ingress_<lportTag>_<sgRuleId>,
            ICMP__ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ICMP__ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ICMP_V4_DESTINATION_<type><code>__ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ICMP_V6_DESTINATION_<type><code>__ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_Ingress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_<port>_<portMask>_Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_Ingress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_Ingress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>

        245:Ingress_Fixed_NonConntrk_Drop<dpId>_<lportTag>_ACL_Rule_Miss,
            ETHERnull_Ingress_<lportTag>_<sgRuleId>,
            ETHERnull_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ETHERnull_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ICMP__Ingress_<lportTag>_<sgRuleId>,
            ICMP_V4_DESTINATION_<type><code>__Ingress_<lportTag>_<sgRuleId>,
            ICMP_V6_DESTINATION_<type><code>__Ingress_<lportTag>_<sgRuleId>,
            ICMP__ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ICMP__ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ICMP_V4_DESTINATION_<type><code>__ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            ICMP_V6_DESTINATION_<type><code>__ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_Ingress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_DESTINATION_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_<port>_<portMask>_Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            TCP_SOURCE_ALL__ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_Ingress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_DESTINATION_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_<port>_<portMask>_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            UDP_SOURCE_ALL__ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_Ingress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_ipv4_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>,
            OTHER_PROTO<protocolNumber>_ipv6_remoteACL_interface_aap_<remoteIp>_Ingress_<lportTag>_<sgRuleId>

    """
    flowid = flow['id']
    """
        This captures flows with following format:
            *_<dpnid>_<lport>_*
    """
    for prefix in PREFIX_FOR_LPORT[flow['table_id']]:
        if flowid.startswith(prefix):
            res = flowid[len(prefix):].split('_')
            try:
                flow_info['lport'] = int(res[1])
                return flow_info
            except ValueError:
                """ Possible cases, ignore:
                    241:Ingress_v4_Broadcast_<dpId>_Permit
                """
                pass
    """
        This captures flows with following format:
            *_<lport>_<sgRuleId>
    """
    for prefix in PREFIX_LPORT_SGR[flow['table_id']]:
        if flowid.startswith(prefix):
            res = flowid[len(prefix):].split('_')
            try:
                flow_info['lport'] = int(res[-2])
                return flow_info
            except ValueError:
                """ Possible cases, ignore:
                    Unexpected, log?
                """
                pass
            except IndexError:
                # Unknown flow type. Log???
                pass
    return flow_info

