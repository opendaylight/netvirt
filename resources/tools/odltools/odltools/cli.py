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
from odltools import logg
import odltools.csit.cli
import odltools.karaf.cli
import odltools.mdsal.cli
import odltools.monitor.cli
import odltools.netvirt.cli


def create_parser():
    parser = argparse.ArgumentParser(prog="python -m odltools", description="OpenDaylight Troubleshooting Tools")
    parser.add_argument("-v", "--verbose", dest="verbose", action="count", default=0,
                        help="verbosity (-v, -vv)")
    parser.add_argument("-V", "--version", action="version",
                        version="%(prog)s (version {version})".format(version=odltools.__version__))
    subparsers = parser.add_subparsers(dest="command", description="Command Tool")
    odltools.csit.cli.add_parser(subparsers)
    odltools.karaf.cli.add_parser(subparsers)
    odltools.mdsal.cli.add_parser(subparsers)
    odltools.monitor.cli.add_parser(subparsers)
    odltools.netvirt.cli.add_parser(subparsers)

    return parser


def parse_args():
    parser = create_parser()
    args = parser.parse_args()

    return args


def main():
    args = parse_args()
    if args.verbose > 0:
        logg.debug()
    args.func(args)
