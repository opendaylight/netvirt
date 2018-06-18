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

import json
import odltools.netvirt.services as svcs


def format_json(args, data):
    if args is None or args.pretty_print:
        return json.dumps(data, indent=4, separators=(',', ': '))
    else:
        return json.dumps(data)


def show_all(flow):
    dpnid = flow.get('dpnid')
    host = flow.get('host')
    result = 'Table:{}'.format(flow['table'])
    if host:
        result = '{}, Host:{}'.format(result, host)
    if dpnid:
        result = '{}, DpnId:{}/{}'.format(result, dpnid, to_hex(dpnid))
    result = '{}, FlowId:{}'.format(result, flow.get('id'))
    lport = flow.get('lport')
    elantag = flow.get('elan-tag')
    serviceid = flow.get('serviceid')
    label = flow.get('mpls')
    vpnid = flow.get('vpnid')
    ip = flow.get('iface-ips')
    smac = flow.get('src-mac')
    dmac = flow.get('dst-mac')
    intip4 = flow.get('int-ip4')
    extip4 = flow.get('ext-ip4')
    intmac = flow.get('int-mac')
    extmac = flow.get('ext-mac')
    vlanid = flow.get('vlanid')
    ofport = flow.get('ofport')
    if lport:
        result = '{},LportTag:{}/{}'.format(result, lport, to_hex(lport))
    if serviceid:
        result = '{},Service:{}'.format(result, svcs.get_service_name(serviceid))
    if ofport:
        result = '{},OfPort:{}'.format(result, ofport)
    if vlanid:
        result = '{},VlanId:{}'.format(result, vlanid)
    if vpnid:
        result = '{},VpnId:{}/{}'.format(result, vpnid, to_hex(vpnid*2))
    if label:
        result = '{},MplsLabel:{}'.format(result, label)
    if elantag:
        result = '{},ElanTag:{}/{}'.format(result, elantag, to_hex(elantag))
    if smac:
        result = '{},SrcMac:{}'.format(result, smac)
    if dmac:
        result = '{},DstMac:{}'.format(result, dmac)
    if intip4:
        result = '{},InternalIPv4:{}'.format(result, intip4)
    if extip4:
        result = '{},ExternalIPv4:{}'.format(result, extip4)
    if intmac:
        result = '{},InternalMAC:{}'.format(result, intmac)
    if extmac:
        result = '{},ExternalMAC:{}'.format(result, extmac)
    if ip:
        result = '{},LportIp:{}'.format(result, json.dumps(ip))
    result = '{},Reason:{}'.format(result, flow.get('reason'))
    return result


def parse_ipv4(ip):
    if ip and '/' in ip:
        ip_arr = ip.split('/')
        if ip_arr[1] == '32':
            return ip_arr[0]
    return ip


def sort(data, field):
    return sorted(data, key=lambda x: x[field])


def to_hex(data, ele=None):
    if not ele:
        data = ("0x%x" % int(data)) if data else None
        return data
    elif data.get(ele):
        data[ele] = "0x%x" % data[ele]
        return data[ele]
    else:
        return data


def nstr(s):
    if not s:
        return ''
    return str(s)
