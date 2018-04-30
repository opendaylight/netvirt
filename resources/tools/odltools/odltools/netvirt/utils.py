import json


def format_json(args, data):
    if args.pretty_print:
        return json.dumps(data, indent=4, separators=(',', ': '))
    else:
        return json.dumps(data)


def show_optionals(flow):
    result = ''
    lport = flow.get('lport')
    elantag = flow.get('elan-tag')
    label = flow.get('mpls')
    vpnid = flow.get('vpnid')
    ip = flow.get('iface-ips')
    smac = flow.get('src-mac')
    dmac = flow.get('dst-mac')
    vlanid = flow.get('vlanid')
    ofport = flow.get('ofport')
    if lport:
        result = '{},LportTag:{}/{}'.format(result, lport, to_hex(lport))
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
    if ip:
        result = '{},LportIp:{}'.format(result, json.dumps(ip))
    result = '{},Reason:{}'.format(result, flow.get('reason'))
    return result


def sort(data, field):
    return sorted(data, key=lambda x: x[field])


def to_hex(data, ele=None):
    if not ele:
        data = ("0x%x" % data) if data else None
        return data
    elif data.get(ele):
        data[ele] = "0x%x" % data[ele]
        return data[ele]
    else:
        return data
