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

from odltools.cli_utils import add_common_args
from odltools.mdsal.models import models


def add_get_parser(parsers):
    parser = parsers.add_parser("get", help="Get and write all mdsal models")
    add_common_args(parser)
    parser.add_argument("path",
                        help="the directory that the parsed data is written into")
    # Get a list of modules that was csv. The lambda parses the input into a list
    parser.add_argument("--modules", default="all",
                        type=lambda s: [item for item in s.split(',')],
                        help="all or a list of modules")
    parser.set_defaults(func=models.get_models)


def add_parser(parsers):
    parser = parsers.add_parser("model", description="Tools for MDSAL models")
    subparsers = parser.add_subparsers(dest="subcommand", description="Model tools")
    add_get_parser(subparsers)
