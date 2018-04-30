from odltools.mdsal.models.model import Model
import utils


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

PREFIX_FOR_LPORT = {211: [PREFIX_211_GOTO, PREFIX_211_DHCPSv4,
                          PREFIX_211_DHCPSv6, PREFIX_211_DHCPCv4,
                          PREFIX_211_DHCPCv6, PREFIX_211_ARP,
                          PREFIX_211_L2BCAST, PREFIX_211_ICMPv6],
                    212: [],
                    213: [PREFIX_213],
                    214: [PREFIX_214],
                    215: [PREFIX_215],
                    241: [PREFIX_241_DHCPv4, PREFIX_241_DHCPv6,
                          PREFIX_241_ICMPv6, PREFIX_241_ARP,
                          PREFIX_241_BCASTv4, PREFIX_241_GOTO],
                    242: [],
                    243: [PREFIX_243],
                    244: [PREFIX_244],
                    245: [PREFIX_245]}

PREFIX_SGR_ETHER = 'ETHERnull_'
PREFIX_SGR_ICMP = 'ICMP_'
PREFIX_SGR_TCP = 'TCP_'
PREFIX_SGR_UDP = 'UDP_'
PREFIX_SGR_OTHER = 'OTHER_PROTO'

PREFIX_LPORT_SGR = {211: [], 212: [], 213: [],
                    214: [PREFIX_SGR_ETHER, PREFIX_SGR_ICMP, PREFIX_SGR_TCP,
                          PREFIX_SGR_UDP, PREFIX_SGR_OTHER],
                    215: [PREFIX_SGR_ETHER, PREFIX_SGR_ICMP, PREFIX_SGR_TCP,
                          PREFIX_SGR_UDP, PREFIX_SGR_OTHER],
                    241: [], 242: [], 243: [],
                    244: [PREFIX_SGR_ETHER, PREFIX_SGR_ICMP, PREFIX_SGR_TCP,
                          PREFIX_SGR_UDP, PREFIX_SGR_OTHER],
                    245: [PREFIX_SGR_ETHER, PREFIX_SGR_ICMP, PREFIX_SGR_TCP,
                          PREFIX_SGR_UDP, PREFIX_SGR_OTHER]
                    }

# Metadata consts
LPORT_MASK = 0xfffff0000000000
LPORT_MASK_ZLEN = 10  # no. of trailing 0s in lport mask
ELAN_TAG_MASK = 0x000000ffff000000
ELAN_HEX_LEN = 4
LPORT_REG6_MASK = 0xfffff00
LPORT_REG6_MASK_ZLEN = 2
VRFID_MASK = 0x0000000000fffffe


def parse_flow(flow):
    #parse flow fields
    #hex(int(mask, 16) & int(data, 16))
    if flow['cookie']:
        utils.to_hex(flow, 'cookie')
    # parse instructions
    for instruction in flow['instructions'].get('instruction', []):
        if 'write-metadata' in instruction:
            utils.to_hex(instruction['write-metadata'],'metadata')
            utils.to_hex(instruction['write-metadata'],'metadata-mask')
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'openflowplugin-extension-nicira-action:nx-reg-load' in action:
                    utils.to_hex(action['openflowplugin-extension-nicira-action:nx-reg-load'], 'value')
    # parse matches
    if 'metadata' in flow['match']:
        metadata = flow['match']['metadata']
        utils.to_hex(metadata,'metadata')
        utils.to_hex(metadata,'metadata-mask')

    for ofex in flow['match'].get('openflowplugin-extension-general:extension-list', []):
        if ofex['extension-key'] == 'openflowplugin-extension-nicira-match:nxm-nx-reg6-key':
            utils.to_hex(ofex['extension']['openflowplugin-extension-nicira-match:nxm-nx-reg'], 'value')

    return flow


# Methods to extract flow fields
def get_instruction_writemeta(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'write-metadata' in instruction:
            return instruction['write-metadata']
    return None


def get_act_reg6load(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'openflowplugin-extension-nicira-action:nx-reg-load' in action:
                    return action['openflowplugin-extension-nicira-action:nx-reg-load']
    return None


def get_act_conntrack(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'openflowplugin-extension-nicira-action:nx-conntrack' in action:
                    return action['openflowplugin-extension-nicira-action:nx-conntrack']


def get_act_group(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'group-action' in action:
                    return action['group-action']


def get_act_set_tunnel(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'set-field' in action and 'tunnel' in action.get('set-field'):
                    return action.get('set-field').get('tunnel')


def get_act_resubmit(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'openflowplugin-extension-nicira-action:nx-resubmit' in action:
                    return action[
                        'openflowplugin-extension-nicira-action:nx-resubmit']


def get_act_set_vlanid(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'set-field' in action and 'vlan-match' in action.get('set-field'):
                    return action.get('set-field').get('vlan-match').get('vlan-id')


def get_act_output(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'output-action' in action and 'output-node-connector' in action.get('output-action'):
                    return action.get('output-action')


def get_match_metadata(flow):
    return flow['match'].get('metadata')


def get_match_reg6(flow):
    for ofex in (
            flow['match'].get(
                'openflowplugin-extension-general:extension-list', [])):
        if (ofex['extension-key']
                == 'openflowplugin-extension-nicira-match:nxm-nx-reg6-key'):
            return (
                ofex['extension']
                ['openflowplugin-extension-nicira-match:nxm-nx-reg'])
    return None


def get_match_mpls(flow):
    if flow['match'].get('protocol-match-fields'):
        return flow['match'].get('protocol-match-fields').get('mpls-label')
    return None


def get_match_tunnelid(flow):
    if flow['match'].get('tunnel'):
        return flow['match'].get('tunnel').get('tunnel-id')
    return None


def get_match_ether_dest(flow):
    if flow.get('match').get('ethernet-match') and flow['match'].get('ethernet-match').get('ethernet-destination'):
        return flow['match'].get('ethernet-match').get('ethernet-destination')
    return None


def get_match_ether_src(flow):
    if flow.get('match').get('ethernet-match') and flow['match'].get('ethernet-match').get('ethernet-source'):
        return flow['match'].get('ethernet-match').get('ethernet-source')
    return None


def get_match_vlanid(flow):
    if flow.get('match').get('vlan-match') and flow['match'].get('vlan-match').get('vlan-id'):
        return flow['match'].get('vlan-match').get('vlan-id')
    return None


def get_match_inport(flow):
    if flow.get('match').get('in-port'):
        return flow['match'].get('in-port')
    return None


def get_flow_info_from_any(flow_info, flow):
    w_mdata = get_instruction_writemeta(flow)
    lport = None
    if w_mdata:
        metadata = w_mdata['metadata']
        mask = w_mdata['metadata-mask']
        if mask & LPORT_MASK:
            lport = ('%x' % (metadata & LPORT_MASK))[:-LPORT_MASK_ZLEN]
            if lport:
                flow_info['lport'] = int(lport, 16)
    m_metadata = get_match_metadata(flow)
    if m_metadata:
        metadata = m_metadata['metadata']
        mask = m_metadata['metadata-mask']
        if mask & ELAN_TAG_MASK:
            elan = ('%x' % (metadata & ELAN_TAG_MASK))[:ELAN_HEX_LEN]
            if elan:
                flow_info['elan-tag'] = int(elan, 16)
        if not lport and (mask & LPORT_MASK):
            lport = ('%x' % (metadata & LPORT_MASK))[:-LPORT_MASK_ZLEN]
            if lport:
                flow_info['lport'] = int(lport, 16)
    m_ether_dest = get_match_ether_dest(flow)
    if m_ether_dest and m_ether_dest.get('address'):
        flow_info['dst-mac'] = m_ether_dest.get('address').lower()
    m_ether_src = get_match_ether_src(flow)
    if m_ether_src and m_ether_src.get('address'):
        flow_info['src-mac'] = m_ether_src.get('address').lower()
    return flow_info

# Table specific parsing


def get_ifname_from_flowid(flow_id, table):
    splitter = ':' if table == 0 else '.'
    # i = 2 if table == 0 else 1
    i = 2
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
    w_mdata = get_instruction_writemeta(flow)
    if w_mdata:
        metadata = w_mdata['metadata']
        mask = w_mdata['metadata-mask']
        if mask & LPORT_MASK:
            lport = ('%x' % (metadata & LPORT_MASK))[:-LPORT_MASK_ZLEN]
            flow_info['lport'] = int(lport, 16)
    m_reg6 = get_match_reg6(flow)
    if not flow.get('lport') and m_reg6 and m_reg6.get('value'):
        lport = (('%x' % (m_reg6.get('value') & LPORT_REG6_MASK))
                 [:-LPORT_REG6_MASK_ZLEN])
        flow_info['lport'] = int(lport, 16)
    if flow['table_id'] == 0:
        m_inport = get_match_inport(flow)
        if m_inport:
            flow_info['ofport'] = Model.get_ofport_from_ncid(m_inport)
        m_vlan = get_match_vlanid(flow)
        if m_vlan and m_vlan.get('vlan-id'):
            flow_info['vlanid'] = m_vlan.get('vlan-id')
    elif flow['table_id'] == 220:
        a_output = get_act_output(flow)
        a_vlan = get_act_set_vlanid(flow)
        if a_output and a_output.get('output-node-connector'):
            flow_info['ofport'] = a_output.get('output-node-connector')
        if a_vlan and a_vlan.get('vlan-id'):
            flow_info['vlanid'] = a_vlan.get('vlan-id')
    return flow_info


def get_flow_info_from_l3vpn_table(flow_info, flow):
    label = get_match_mpls(flow)
    if not label and flow['table_id'] == 36:
        label = get_match_tunnelid(flow)
    if label:
        flow_info['mpls'] = label
    a_group = get_act_group(flow)
    if a_group and a_group.get('group-id'):
        flow_info['group-id'] = a_group.get('group-id')
    m_metadata = get_match_metadata(flow)
    if m_metadata:
        metadata = m_metadata['metadata']
        mask = m_metadata['metadata-mask']
        if mask & LPORT_MASK:
            lport = ('%x' % (metadata & LPORT_MASK))[:-LPORT_MASK_ZLEN]
            flow_info['lport'] = int(lport, 16)
        if mask & VRFID_MASK:
            flow_info['vpnid'] = (metadata & VRFID_MASK) / 2
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
        if mask & ELAN_TAG_MASK:
            elan = ('%x' % (metadata & ELAN_TAG_MASK))[:ELAN_HEX_LEN]
            flow_info['elan-tag'] = int(elan, 16)
        if mask & LPORT_MASK:
            lport = ('%x' % (metadata & LPORT_MASK))[:-LPORT_MASK_ZLEN]
            flow_info['lport'] = int(lport, 16)
    m_ether_dest = get_match_ether_dest(flow)
    if m_ether_dest and m_ether_dest.get('address'):
        flow_info['dst-mac'] = m_ether_dest.get('address').lower()
    m_ether_src = get_match_ether_src(flow)
    if m_ether_src and m_ether_src.get('address'):
        flow_info['src-mac'] = m_ether_src.get('address').lower()
    if not flow_info.get('lport'):
        reg6_load = get_act_reg6load(flow)
        if reg6_load and reg6_load.get('value'):
            # reg6load value is lport lft-shit by 8 bits.
            lport = ('%x' % reg6_load.get('value'))[:-2]
            flow_info['lport'] = int(lport, 16)
    return flow_info


def get_flow_info_from_acl_table(flow_info, flow):
    m_metadata = get_match_metadata(flow)
    if m_metadata:
        metadata = m_metadata['metadata']
        mask = m_metadata['metadata-mask']
        if mask & LPORT_MASK:
            lport = ('%x' % (metadata & LPORT_MASK))[:-LPORT_MASK_ZLEN]
            flow_info['lport'] = int(lport, 16)
    a_conntrk = get_act_conntrack(flow)
    if a_conntrk and a_conntrk.get('conntrack-zone'):
        flow_info['elan-tag'] = a_conntrk.get('conntrack-zone')
    return flow_info


def get_flow_info_from_acl_table_flowid(flow_info, flow):
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
