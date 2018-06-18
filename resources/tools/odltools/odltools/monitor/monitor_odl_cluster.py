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

"""
Cluster Monitor Tool

This tool provides real-time visualization of the cluster member roles for all
shards in either the config or operational datastore.

A JSON file containing a list of the IP addresses and port numbers of the
controllers and other information is required.  The file should look like this:

    {
        "cluster": {
            "controllers": [
                {"ip": "172.17.10.93", "port": "8181"},
                {"ip": "172.17.10.93", "port": "8181"},
                {"ip": "172.17.10.93", "port": "8181"}
            ],
            "user": "username",
            "pass": "password",
            "shards_to_exclude": ["prefix-configuration-shard"]
        }
    }
"""

import curses
import json
import math
import os
import re
import time

from requests import exceptions

import odltools.common.constants as con
import odltools.common.odl_client as client


class OdlMonitoringException(Exception):
    pass


class OdlClusterCfgException(Exception):
    pass


class CursesWindow(object):
    def __enter__(self):
        stdscr = curses.initscr()
        curses.noecho()
        curses.cbreak()
        curses.curs_set(0)  # Invisible
        stdscr.keypad(True)
        stdscr.nodelay(True)
        curses.start_color()
        curses.init_pair(con.WHITE_ON_BLACK, curses.COLOR_WHITE,
                         curses.COLOR_BLACK)
        curses.init_pair(con.WHITE_ON_GREEN, curses.COLOR_WHITE,
                         curses.COLOR_GREEN)
        curses.init_pair(con.WHITE_ON_BLUE, curses.COLOR_WHITE,
                         curses.COLOR_BLUE)
        curses.init_pair(con.WHITE_ON_YELLOW, curses.COLOR_WHITE,
                         curses.COLOR_YELLOW)
        curses.init_pair(con.BLACK_ON_YELLOW, curses.COLOR_BLACK,
                         curses.COLOR_YELLOW)
        return stdscr

    def __exit__(self, *args):
        curses.nocbreak()
        curses.echo()
        curses.nl()
        curses.endwin()


def get_cluster_roles(shard_name, controllers, data_store):
    controller_state = {}
    for controller in controllers:
        controller_state[controller["ip"]] = None
        url_path = ('org.opendaylight.controller:Category'
                    '=Shards,name={}-shard-{}-{},type=Distributed{}'
                    'Datastore'.format(controller['name'], shard_name,
                                       data_store.lower(), data_store))
        odl_client = controller['client']
        try:
            resp = odl_client.request('get', url_path)
            if resp.status_code != 200:
                controller_state[controller["ip"]] = ('HTTP ' +
                                                      str(resp.status_code))
            else:
                resp_data = resp.json()
                try:
                    controller_state[controller["ip"]] = (resp_data['value'][
                        'RaftState'])
                except KeyError:
                    controller_state[controller["ip"]] = "HTTP Parsing Error"

        except exceptions.ConnectionError:
            controller_state[controller["ip"]] = 'Connection Error'
        except exceptions.ReadTimeout:
            controller_state[controller["ip"]] = 'HTTP Read Timeout'
        except ValueError:
            controller_state[controller["ip"]] = 'JSON Error'

    return controller_state


def size_and_color(cluster_roles, field_length, ip_addr):
    status_dict = {
        'txt': str(cluster_roles[ip_addr]).center(field_length)
    }
    # color map
    role_to_color = {
        con.LEADER: curses.color_pair(con.WHITE_ON_GREEN),
        con.FOLLOWER: curses.color_pair(con.WHITE_ON_BLUE),
        con.CANDIDATE: curses.color_pair(con.BLACK_ON_YELLOW)
    }

    status_dict['color'] = role_to_color.get(
        cluster_roles[ip_addr], curses.color_pair(con.DEFAULT_COLOR))
    return status_dict


def handle_window_resize(stdscr, y, x):
    if curses.is_term_resized(y, x):
        maxy, maxx = stdscr.getmaxyx()
        stdscr.clear()
        curses.resizeterm(y, x)
        stdscr.refresh()
    else:
        maxy = y
        maxx = x
    return maxy, maxx


def cluster_monitor(stdscr, controllers, username, password, data_store,
                    excluded_shards=None):
    shards = set()
    (maxy, maxx) = stdscr.getmaxyx()
    controller_len = con.MAX_CONTROLLER_NAME_LEN
    field_len = 0
    stdscr.addstr(len(controllers) + 3, 0,
                  'Polling controllers, please wait...',
                  curses.color_pair(con.WHITE_ON_BLACK))
    stdscr.addstr(len(controllers) + 4, 0,
                  'Press q or use ctrl + c to quit.',
                  curses.color_pair(con.WHITE_ON_BLACK))
    stdscr.refresh()
    # create rest clients for each controller
    for controller in controllers:
        url = ("http://{}:{}/jolokia/read".format(controller['ip'],
                                                  controller['port']))
        controller['client'] = client.OpenDaylightRestClient(
            username=username, password=password, url=url, timeout=2)

    key = -1
    while key != ord('q') and key != ord('Q'):
        (maxy, maxx) = handle_window_resize(stdscr, maxy, maxx)
        key = max(key, stdscr.getch())

        # Retrieve controller names and shard names.
        for controller in controllers:
            key = max(key, stdscr.getch())
            if key == ord('q') or key == ord('Q'):
                break

            url_path = ("org.opendaylight.controller:Category"
                        "=ShardManager,name=shard-manager-{},type=Distributed"
                        "{}Datastore".format(data_store.lower(), data_store))
            odl_client = controller['client']
            try:
                data = odl_client.request('get', url_path).json()
            except (exceptions.ConnectionError, exceptions.ReadTimeout,
                    ValueError):
                data = None

            # grab the controller name from the first shard
            try:
                controller['name'] = data['value']['MemberName']
            except (KeyError, TypeError):
                controller['name'] = "{}:{}".format(
                    controller['ip'], controller['port'])

            # collect shards found in any controller; does not require
            # all controllers to have the same shards
            if data and 'value' in data and 'LocalShards' in data['value']:
                for local_shard in data['value']['LocalShards']:
                    match = "^.*?-shard-(.+?)-{}$".format(data_store.lower())
                    m = re.search(match, local_shard)
                    if m and m.group(1) not in excluded_shards:
                        shards.add(m.group(1))

            controller_len = max(controller_len, len(controller['name']))

        if shards:
            field_len = max(map(len, shards)) + 2
        else:
            field_len = max(field_len, 0)

        # Ensure everything fits
        if controller_len + 1 + (field_len + 1) * len(shards) > maxx:
            extra = controller_len + 1 + (field_len + 1) * len(shards) - maxx
            delta = int(math.ceil(float(extra) / (1 + len(shards))))
            controller_len -= delta
            field_len -= delta

        # no shards found, use default
        if not shards:
            real_shards = ['default']
        else:
            real_shards = shards
        stdscr.move(0, 2)
        stdscr.clrtoeol()
        for data_column, shard in enumerate(real_shards):
            stdscr.addstr(1, controller_len + 1 + (field_len + 1) *
                          data_column, shard.center(field_len),
                          curses.color_pair(con.WHITE_ON_BLACK))
        # display controller and shard headers
        for row, controller in enumerate(controllers):
            addr = "{}:{}".format(controller['ip'], controller['port'])
            stdscr.addstr(row + 2, 0,
                          addr.center(controller_len),
                          curses.color_pair(con.WHITE_ON_BLACK))

        stdscr.addstr(0, 0, 'Controller'.center(controller_len),
                      curses.color_pair(con.WHITE_ON_BLACK))
        stdscr.addstr(0, max(controller_len + 1, 10),
                      'Shards/Status'.center(field_len),
                      curses.color_pair(con.WHITE_ON_BLACK))
        stdscr.refresh()

        # display shard status
        for data_column, shard_name in enumerate(real_shards):
            key = max(key, stdscr.getch())
            if key == ord('q') or key == ord('Q'):
                break

            if shard_name not in excluded_shards:
                cluster_stat = get_cluster_roles(shard_name,
                                                 controllers,
                                                 data_store)
                for row, controller in enumerate(controllers):
                    status = size_and_color(cluster_stat, field_len,
                                            controller["ip"])
                    stdscr.addstr(row + 2, controller_len + 1 +
                                  (field_len + 1) * data_column, status['txt'],
                                  status['color'])
            time.sleep(0.5)
            stdscr.refresh()


def run_monitor(args):

    data_store = args.datastore
    if data_store != 'Config' and data_store != 'Operational':
        raise OdlMonitoringException('"Config" or "Operational" data store '
                                     'not found')

    cluster_file = args.config_file
    if not os.path.isfile(cluster_file):
        raise OdlClusterCfgException('Cluster config file does not exist:'
                                     '{}'.format(cluster_file))

    data = None
    with open(cluster_file) as fh:
        try:
            data = json.load(fh)
        except ValueError:
            raise OdlMonitoringException(('Unable to decode cluster config '
                                          'json file: '
                                          '{}').format(cluster_file))

    if 'cluster' in data:
        for cluster_key in ('controllers', 'shards_to_exclude', 'user',
                            'pass'):
            if cluster_key not in data['cluster']:
                raise OdlClusterCfgException('Invalid Cluster Config. '
                                             'Missing required key: '
                                             '{}'.format(cluster_key))
    else:
        raise OdlClusterCfgException('Invalid Cluster config format. The '
                                     'key "cluster" is undefined')

    # validate values
    for setting in ('controllers', 'user', 'pass'):
        if data['cluster'][setting] is None:
            raise OdlClusterCfgException(
                'Invalid null value for cluster '
                'config setting: {}'.format(data['cluster'][setting]))

    controllers = data["cluster"]["controllers"]
    shards_to_exclude = data["cluster"]["shards_to_exclude"]
    username = data["cluster"]["user"]
    password = data["cluster"]["pass"]
    with CursesWindow() as stdscr:
        try:
            cluster_monitor(stdscr, controllers, username, password,
                            data_store, excluded_shards=shards_to_exclude)
        except KeyboardInterrupt:
            pass
