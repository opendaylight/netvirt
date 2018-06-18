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

import odltools.monitor.monitor_odl_cluster as monitor

JSON_FORMAT = """
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


def add_parser(parsers):
    parser = parsers.add_parser("monitor",
                                description="Graphical tool for monitoring "
                                            "an OpenDaylight cluster")
    parser.set_defaults(func=monitor.run_monitor)
    parser.add_argument('-d', '--datastore', default='Config', type=str,
                        choices=["Config", "Operational"],
                        help=('polling can be done on "Config" or '
                              '"Operational" data stores'))
    parser.add_argument('config_file',
                        metavar='cluster.json',
                        help='JSON Cluster configuration file in the '
                             'following format:\n{}'.format(JSON_FORMAT))
