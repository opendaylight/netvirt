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

import argparse
import logging
import os

logger = logging.getLogger("cli_utils")


def type_input_file(path):
    if path == '-':
        return path

    if not os.path.isfile(path):
        logger.error('File "%s" not found' % path)
        raise argparse.ArgumentError

    return path


def add_common_args(parser):
    parser.add_argument("--path",
                        help="the directory that the parsed data is written into")
    parser.add_argument("--transport", default="http",
                        choices=["http", "https"],
                        help="transport for connections")
    parser.add_argument("-i", "--ip", default="localhost",
                        help="OpenDaylight ip address")
    parser.add_argument("-t", "--port", default="8181",
                        help="OpenDaylight restconf port, default: 8181")
    parser.add_argument("-u", "--user", default="admin",
                        help="OpenDaylight restconf username, default: admin")
    parser.add_argument("-w", "--pw", default="admin",
                        help="OpenDaylight restconf password, default: admin")
    parser.add_argument("-p", "--pretty_print", action="store_true",
                        help="json dump with pretty_print")
