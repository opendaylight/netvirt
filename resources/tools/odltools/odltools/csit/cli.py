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

from odltools.csit import robotfiles


def add_parser(subparsers):
    parser = subparsers.add_parser("csit")
    parser.add_argument("infile",
                        help="XML output from a Robot test, e.g. output_01_l2.xml.gz")
    parser.add_argument("path",
                        help="the directory that the parsed data is written into")
    parser.add_argument("-g", "--gunzip", action="store_true",
                        help="unzip the infile")
    parser.add_argument("-d", "--dump", action="store_true",
                        help="dump extra debugging, e.g. ovs metadata")
    parser.set_defaults(func=robotfiles.run)
