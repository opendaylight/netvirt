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

import os


class Args:
    def __init__(self, transport="http", ip="localhost", port=8181, user="admin", pw="admin", path="/tmp",
                 pretty_print=False, ifname=""):
        self.transport = transport
        self.ip = ip
        self.port = port
        self.user = user
        self.pw = pw
        self.path = path
        self.pretty_print = pretty_print
        self.ifname = ifname


def get_resources_path():
    return os.path.join(os.path.dirname(__file__), '../../tests/resources')
