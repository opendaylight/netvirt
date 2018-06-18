# Copyright 2018 Red Hat, Inc. and others. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from odltools.mdsal.models.model import Model
from odltools.netvirt import utils


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
SERVICE_ID_MASK = 0xf000000000000000
SERVICE_ID_MASK_ZLEN = 15


def parse_flow(flow):
    # parse flow fields
    # hex(int(mask, 16) & int(data, 16))
    if flow['cookie']:
        utils.to_hex(flow, 'cookie')
    # parse instructions
    for instruction in flow['instructions'].get('instruction', []):
        if 'write-metadata' in instruction:
            utils.to_hex(instruction['write-metadata'], 'metadata')
            utils.to_hex(instruction['write-metadata'], 'metadata-mask')
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'openflowplugin-extension-nicira-action:nx-reg-load' in action:
                    utils.to_hex(action['openflowplugin-extension-nicira-action:nx-reg-load'], 'value')
    # parse matches
    if 'metadata' in flow['match']:
        metadata = flow['match']['metadata']
        utils.to_hex(metadata, 'metadata')
        utils.to_hex(metadata, 'metadata-mask')

    for ofex in flow['match'].get('openflowplugin-extension-general:extension-list', []):
        if ofex['extension-key'] == 'openflowplugin-extension-nicira-match:nxm-nx-reg6-key':
            utils.to_hex(ofex['extension']['openflowplugin-extension-nicira-match:nxm-nx-reg'], 'value')

    return flow


# Methods to extract flow fields
def get_instruction_writemeta(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'write-metadata' in instruction:
            return instruction['write-metadata']


def get_act_reg6load(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'openflowplugin-extension-nicira-action:nx-reg-load' in action:
                    return action['openflowplugin-extension-nicira-action:nx-reg-load']


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


def get_act_set_ipv4_dest(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'set-field' in action and 'ipv4-destination' in action.get('set-field'):
                    return action.get('set-field').get('ipv4-destination')


def get_act_set_ipv4_src(flow):
    for instruction in flow['instructions'].get('instruction', []):
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'set-field' in action and 'ipv4-source' in action.get('set-field'):
                    return action.get('set-field').get('ipv4-source')


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


def get_match_mpls(flow):
    if flow['match'].get('protocol-match-fields'):
        return flow['match'].get('protocol-match-fields').get('mpls-label')


def get_match_tunnelid(flow):
    if flow['match'].get('tunnel'):
        return flow['match'].get('tunnel').get('tunnel-id')


def get_match_ether_dest(flow):
    if flow.get('match').get('ethernet-match') and flow['match'].get('ethernet-match').get('ethernet-destination'):
        return flow['match'].get('ethernet-match').get('ethernet-destination').get('address')


def get_match_ether_src(flow):
    if flow.get('match').get('ethernet-match') and flow['match'].get('ethernet-match').get('ethernet-source'):
        return flow['match'].get('ethernet-match').get('ethernet-source').get('address')


def get_match_ipv4_dest(flow):
    return utils.parse_ipv4(flow['match'].get('ipv4-destination'))


def get_match_ipv4_src(flow):
    return utils.parse_ipv4(flow['match'].get('ipv4-source'))


def get_match_vlanid(flow):
    if flow.get('match').get('vlan-match') and flow['match'].get('vlan-match').get('vlan-id'):
        return flow['match'].get('vlan-match').get('vlan-id')


def get_match_inport(flow):
    if flow.get('match').get('in-port'):
        return flow['match'].get('in-port')


def get_lport_from_mreg6(m_reg6):
    if m_reg6 and m_reg6.get('value'):
        return (('%x' % (m_reg6.get('value') & LPORT_REG6_MASK))[:-LPORT_REG6_MASK_ZLEN])


def get_lport_from_metadata(metadata, mask):
    if mask & LPORT_MASK:
        return ('%x' % (metadata & LPORT_MASK))[:-LPORT_MASK_ZLEN]


def get_elan_from_metadata(metadata, mask):
    if mask & ELAN_TAG_MASK:
        return ('%x' % (metadata & ELAN_TAG_MASK))[:ELAN_HEX_LEN]


def get_service_id_from_metadata(metadata, mask):
    if mask & SERVICE_ID_MASK:
        return ('%x' % (metadata & SERVICE_ID_MASK))[:-SERVICE_ID_MASK_ZLEN]


def get_vpnid_from_metadata(metadata, mask):
    if mask & VRFID_MASK:
        return (metadata & VRFID_MASK) / 2


def get_flow_info_from_any(flow_info, flow):
    w_mdata = get_instruction_writemeta(flow)
    lport = flow_info.get('lport') if flow_info else None
    serviceid = flow_info.get('serviceid') if flow_info else None
    if w_mdata:
        metadata = w_mdata['metadata']
        mask = w_mdata['metadata-mask']
        lport = get_lport_from_metadata(metadata, mask) if not lport else lport
        if lport:
            flow_info['lport'] = int(lport, 16)
        serviceid = get_service_id_from_metadata(metadata, mask) if not serviceid else serviceid
        if serviceid:
            flow_info['serviceid'] = int(serviceid, 16)
    m_metadata = get_match_metadata(flow)
    if m_metadata:
        elan = flow_info.get('elan-tag')
        vpnid = flow_info.get('vpnid')
        metadata = m_metadata['metadata']
        mask = m_metadata['metadata-mask']
        if not lport:
            lport = get_lport_from_metadata(metadata, mask)
            if lport:
                flow_info['lport'] = int(lport, 16)
        if not serviceid:
            serviceid = get_service_id_from_metadata(metadata, mask)
            flow_info['serviceid'] = int(serviceid, 16)
        if not elan:
            elan = get_elan_from_metadata(metadata, mask)
            if elan:
                flow_info['elan-tag'] = int(elan, 16)
        if not vpnid:
            vpnid = get_vpnid_from_metadata(metadata, mask)
            if vpnid:
                flow_info['vpnid'] = vpnid
    if not flow_info.get('dst-mac'):
        m_ether_dest = get_match_ether_dest(flow)
        if m_ether_dest:
            flow_info['dst-mac'] = m_ether_dest.lower()
    if not flow_info.get('src-mac'):
        m_ether_src = get_match_ether_src(flow)
        if m_ether_src:
            flow_info['src-mac'] = m_ether_src.lower()
    if not flow_info.get('dst-ip4'):
        m_ipv4_dest = get_match_ipv4_dest(flow)
        if m_ipv4_dest:
            flow_info['dst-ip4'] = m_ipv4_dest
    if not flow_info.get('src-ip4'):
        m_ipv4_src = get_match_ipv4_src(flow)
        if m_ipv4_src:
            flow_info['src-ip4'] = m_ipv4_src
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
        lport = get_lport_from_metadata(metadata, mask)
        if lport:
            flow_info['lport'] = int(lport, 16)
        service_id = get_service_id_from_metadata(metadata, mask)
        if service_id:
            flow_info['serviceid'] = int(service_id, 16)
    m_reg6 = get_match_reg6(flow)
    if not flow.get('lport'):
        lport = get_lport_from_mreg6(m_reg6)
        if lport:
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
        lport = get_lport_from_metadata(metadata, mask)
        if lport:
            flow_info['lport'] = int(lport, 16)
        vpnid = get_vpnid_from_metadata(metadata, mask)
        if vpnid:
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
        elan = get_elan_from_metadata(metadata, mask)
        if elan:
            flow_info['lport'] = int(elan, 16)
            flow_info['elan-tag'] = int(elan, 16)
        lport = get_lport_from_metadata(metadata, mask)
        if lport:
            flow_info['lport'] = int(lport, 16)
    m_ether_dest = get_match_ether_dest(flow)
    if m_ether_dest:
        flow_info['dst-mac'] = m_ether_dest.lower()
    m_ether_src = get_match_ether_src(flow)
    if m_ether_src:
        flow_info['src-mac'] = m_ether_src.lower()
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
        lport = get_lport_from_metadata(metadata, mask)
        if lport:
            flow_info['lport'] = int(lport, 16)
    a_conntrk = get_act_conntrack(flow)
    if a_conntrk and a_conntrk.get('conntrack-zone'):
        flow_info['elan-tag'] = a_conntrk.get('conntrack-zone')
    return flow_info


def get_flow_info_from_nat_table(flow_info, flow):
    m_metadata = get_match_metadata(flow)
    vpnid = None
    if m_metadata:
        metadata = m_metadata['metadata']
        mask = m_metadata['metadata-mask']
        lport = get_lport_from_metadata(metadata, mask)
        if lport:
            flow_info['lport'] = int(lport, 16)
        vpnid = get_vpnid_from_metadata(metadata, mask)
        if vpnid:
            flow_info['vpnid'] = vpnid
    if not vpnid:
        w_metadata = get_instruction_writemeta(flow)
        metadata = w_metadata['metadata']
        mask = w_metadata['metadata-mask']
        vpnid = get_vpnid_from_metadata(metadata, mask)
        if vpnid:
            flow_info['vpnid'] = vpnid
    m_ipv4_dest = get_match_ipv4_dest(flow)
    m_ipv4_src = get_match_ipv4_src(flow)
    a_set_ipv4_dest = get_act_set_ipv4_dest(flow)
    a_set_ipv4_src = get_act_set_ipv4_src(flow)
    m_ether_src = get_match_ether_src(flow)
    if flow['table_id'] in [25, 27]:
        if a_set_ipv4_dest:
            flow_info['int-ip4'] = a_set_ipv4_dest
        if m_ipv4_dest:
            flow_info['ext-ip4'] = m_ipv4_dest
        m_ether_dest = get_match_ether_dest(flow)
        if m_ether_dest:
            flow_info['ext-mac'] = m_ether_dest
    if flow['table_id'] in [26, 28]:
        if a_set_ipv4_src:
            flow_info['ext-ip4'] = a_set_ipv4_src
        if m_ipv4_src:
            flow_info['int-ip4'] = m_ipv4_src
        m_ether_src = get_match_ether_src(flow)
        if m_ether_src:
            flow_info['ext-mac'] = m_ether_src
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
