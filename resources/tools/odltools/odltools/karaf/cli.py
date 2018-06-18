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

import odltools.cli_utils
from odltools.karaf import dump


def add_parser(parsers):
    parser = parsers.add_parser("karaf", description="Karaf log tools")
    subparsers = parser.add_subparsers(dest="subcommand",
                                       description="Karaf tools")
    format_parser = subparsers.add_parser("format", help="Dump a karaf log "
                                          "with pretty printing of MDSAL "
                                          "objects")
    format_parser.add_argument("path", type=odltools.cli_utils.type_input_file,
                               help="Path to karaf log file")
    format_parser.set_defaults(func=dump.dump_karaf_log)
