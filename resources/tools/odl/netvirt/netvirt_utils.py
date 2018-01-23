import constants as const
import json, pprint, urllib2, base64, sys
import optparse


options = None
args = None

def parse_args():
    global options, args
    parser = optparse.OptionParser(version="0.1")
    parser.add_option("-i", "--ip", action="store", type="string", dest="odlIp", default="localhost",
                      help="opendaylights ip address")
    parser.add_option("-t", "--port", action="store", type="string", dest="odlPort", default="8080",
                      help="opendaylights listening tcp port on restconf northbound")
    parser.add_option("-u", "--user", action="store", type="string", dest="odlUsername", default="admin",
                      help="opendaylight restconf username")
    parser.add_option("-p", "--password", action="store", type="string", dest="odlPassword", default="admin",
                      help="opendaylight restconf password")
    parser.add_option("-m", "--method", action="store", type="string", dest="callMethod", default=None,
                      help="method to call")
    (options, args) = parser.parse_args(sys.argv)
    return options, args


def get_options():
    return options


def get_args():
    return args


def create_url(dsType, path):
    return 'http://{}:{}/restconf/{}/{}/'.format(options.odlIp, options.odlPort, dsType, path)


def grabJson(url):
    data = None
    try:
        request = urllib2.Request(url)
        # You need the replace to handle encodestring adding a trailing newline
        # (https://docs.python.org/2/library/base64.html#base64.encodestring)
        base64string = base64.encodestring('{}:{}'.format(options.odlUsername, options.odlPassword)).replace('\n', '')
        request.add_header('Authorization', 'Basic {}'.format(base64string))
        result = urllib2.urlopen(request)
    except urllib2.URLError, e:
        printError('Unable to send request: {}\n'.format(e))
        return data

    if (result.code != 200):
        printError( '{}\n{}\n\nError: unexpected code: {}\n'.format(result.info(), result.read(), result.code) )
        return data

    data = json.load(result)
    return data


def nstr(s):
    if not s:
        return ''
    return str(s)


def pretty_print(arg):
    pp = pprint.PrettyPrinter(indent=2)
    pp.pprint(arg)
    print


def printError(msg):
    sys.stderr.write(msg)


def get_port_name(port):
    prefix = const.VIF_TYPE_TO_PREFIX.get(port[const.VIF_TYPE])
    if prefix is None:
        return None
    else:
        return prefix + port['uuid'][:11]


def get_dpn_from_ofnodeid(node_id):
    return node_id.split(':')[1]


def to_hex(data, ele=None):
    if not ele:
        data = ("0x%x" % data) if data else None
        return data
    elif data.get(ele):
        data[ele] = "0x%x" % data[ele]
        return data[ele]
    else:
        return data


def sort(data, field):
    return sorted(data, key=lambda x: x[field])


def show_optionals(flow):
    result = ''
    lport = flow.get('lport')
    elantag = flow.get('elan-tag')
    label = flow.get('mpls')
    vpnid = flow.get('vpnid')
    ip = flow.get('iface-ips')
    if lport:
        result = '{},LportTag:{}/{}'.format(result, lport, to_hex(lport))
    if vpnid:
        result = '{},VpnId:{}/{}'.format(result, vpnid, to_hex(vpnid*2))
    if label:
        result = '{},MplsLabel:{}'.format(result, label)
    if elantag:
        result = '{},ElanTag:{}/{}'.format(result, elantag, to_hex(elantag))
    if ip:
        result = '{},LportIp:{}'.format(result, json.dumps(ip))
    result = '{},Reason:{}'.format(result, flow.get('reason'))
    return result


def get_optionals(m_str):
    if str:
        return dict(s.split('=',1) for s in m_str.split(','))
    return None

