#!/usr/bin/python

import argparse
import json
import re
import os
import sys
import time

IFC_PATTERN = ' (?P<ofport>[0-9]+)\((?P<name>.*)\): addr'
DURATION_PATTERN = 'duration=[^ ] '
NPACKETS_PATTERN = 'n_packets=[^ ] '
NBYTES_PATTERN = 'n_bytes=[^ ] '

PORT_CMDS = '''ovs-vsctl add-port br-int %(name)s -- set Interface %(name)s type=internal -- set Interface %(name)s ofport=%(num)s
ip netns add %(name)s
ip link set dev %(name)s netns %(name)s
ip netns exec %(name)s ip a add dev %(name)s %(ip)s/24
ip netns exec %(name)s ip link set %(name)s addr %(mac)s
ip netns exec %(name)s ip link set dev %(name)s up
ip netns exec %(name)s ip link set dev lo up
ip netns exec %(name)s ip route add default via %(ip)s'''


def get_port_commands(name, num, ip, mac):
    return (PORT_CMDS % {'name': name, 'num': num, 'ip': ip, 'mac': mac}).split('\n')


def parse_show(path):
    show_dump = open(path, 'r')
    ret = {}
    for line in show_dump:
        match = re.match(IFC_PATTERN, line)
        if match:
            ret[match.group('name')] = match.group('ofport')
    show_dump.close()
    return ret


def parse_ports(path):
    ports_dump = open(path, 'r')
    raw_json = ports_dump.read()
    ports_dump.close()

    ports = json.loads(raw_json)
    ret = {}

    if 'ports' not in ports:
        return ret

    ports = ports['ports']
    if 'port' not in ports:
        return ret

    for port in ports['port']:
        try:
            ret[port['uuid']] = (port['fixed-ips'][0]['ip-address'], port['mac-address'])
        except KeyError:
            continue
    return ret


def system(cmd):
    rc = os.system(cmd)
    if rc != 0:
        sys.stderr.write('Error executing "%s", return code %i\n' % (cmd, rc))
    return rc == 0


def docker_up():
    global container_name
    print 'Launching container...'
    system('docker run --name %s -e MODE=none -itd --cap-add ALL jhershbe/fedora-ovs-replay' % container_name)
    time.sleep(5)


def docker_down():
    global container_name
    system('docker stop %s' % container_name)
    system('docker rm -f %s' % container_name)


def docker_exec(cmd):
    global container_name
    return system('docker exec %s %s' % (container_name, cmd))


def config_ports(ports_path, show_path):
    print 'Configuring ports...'
    port2num = parse_show(show_path)
    uuid2ip = parse_ports(ports_path)

    unknown_ip_suffix = 1
    for name, num in port2num.iteritems():
        uuid_pfx = name[3:]
        the_ip = ''
        for uuid, (ip, mac) in uuid2ip.iteritems():
            if uuid.startswith(uuid_pfx):
                the_ip = ip
                break
        if the_ip == '':
            the_ip = '10.20.30.%s' % unknown_ip_suffix
            unknown_ip_suffix += 1
            sys.stderr.write('Could not find ip for %s, assuming non-neutron port and faking it with %s\n'
                             % (name, the_ip))

        for cmd in get_port_commands(name, num, the_ip, mac):
            docker_exec(cmd)


def config_groups(path):
    print 'Configuring groups...'
    groups_dump = ''
    with open(path, 'r') as f:
        groups_dump = f.read()
    groups = [line for line in groups_dump.split('\n')
              if line.startswith(' group_id=')]

    for i in range(0, len(groups)):
        failed_groups = []
        for group in groups:
            if not docker_exec('ovs-ofctl -OOpenFlow13 add-group br-int "%s"' % group):
                failed_groups.append(group)
        groups = failed_groups


def config_flows(path):
    print 'Configuring flows...'
    docker_exec('ovs-ofctl -OOpenFlow13 del-flows br-int')
    with open(path, 'r') as f:
        for line in f:
            if not line.startswith(' cookie='):
                continue
            line = re.sub(DURATION_PATTERN, '', line)
            line = re.sub(NPACKETS_PATTERN, '', line)
            line = re.sub(NBYTES_PATTERN, '', line)
            docker_exec('ovs-ofctl -OOpenFlow13 add-flow br-int "%s"' % line)


parser = argparse.ArgumentParser(prog='ovs-replay.py')
parser.add_argument('--name', default='ovs-replay', help='name the container, or "ovs-replay" if option omitted')
parser.add_argument('flows', help='path to the flow dump')
parser.add_argument('groups', help='path to the groups dump')
parser.add_argument('ports', help='path to the ports dump')
parser.add_argument('show', help='path to the show dump')
args = parser.parse_args(sys.argv[1:])
container_name = args.name

docker_up()
config_ports(args.ports, args.show)
config_groups(args.groups)
config_flows(args.flows)
print 'Done replaying OVS, connect to container with: docker exec -ti %s bash' % container_name
