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

import logging
import re
from pprint import pformat
from odltools.netvirt import request
from odltools.netvirt import tables

logger = logging.getLogger("ovs.flows")


# TODO:
# metadata decoder
# mac to port
# REG6 decoder
# group decoder
# curl -s -u admin:admin -X GET 127.0.0.1:8080/restconf/operational/odl-l3vpn:learnt-vpn-vip-to-port-data
# - check if external ip is resolved, devstack uses port 8087
class Flows:
    COOKIE = "cookie"
    DURATION = "duration"
    TABLE = "table"
    N_PACKETS = "n_packets"
    N_BYTES = "n_bytes"
    MATCHES = "matches"
    ACTIONS = "actions"
    IDLE_TIMEOUT = "idle_timeout"
    SEND_FLOW_REMOVED = "send_flow_rem"
    PRIORITY = "priority"
    GOTO = "goto"
    RESUBMIT = "resubmit"

    def __init__(self, data):
        self.pdata = []
        self.fdata = []
        if type(data) is str:
            self.data = data.splitlines()
        elif type(data) is list:
            self.data = data
        else:
            logger.error("init: data is not a supported type")
            return
        self.start = 0
        logger.debug("init: Copied %d lines", len(self.data))
        self.process_data()
        self.format_data()
        logger.debug("init: data has been processed and formatted")

    def pretty_print(self, data):
        return "{}".format(pformat(data))

    def process_data(self):
        """
        Process the dump-flows data into a map.

        The processing will tokenize the parts in each line of the flow dump.

        :return: A list of dictionaries of parsed tokens per line
        """
        # cookie=0x805138a, duration=193.107s, table=50, n_packets=119, n_bytes=11504, idle_timeout=300,
        #  send_flow_rem priority=20,metadata=0x2138a000000/0xfffffffff000000,dl_src=fa:16:3e:15:a8:66
        #  actions=goto_table:51

        self.pdata = []
        if len(self.data) == 0:
            logger.warn("There is no data to process")
            return self.pdata

        # skip the header if present
        if "OFPST_FLOW" in self.data[0]:
            self.start = 1
            logger.debug("process_data: will skip first line: OFPST_FLOW line")
        else:
            self.start = 0
        if "jenkins" in self.data[-1]:
            end = len(self.data) - 1
            logger.debug("process_data: will skip last line: jenkins line")
        else:
            end = len(self.data)

        # Parse each line of the data. Each line is a single flow.
        # Create a dictionary of all tokens in that flow.
        # Append this flow dictionary to a list of flows.
        for line in self.data[self.start:end]:
            pline = {Flows.IDLE_TIMEOUT: "---", Flows.SEND_FLOW_REMOVED: "-"}
            tokens = line.split(" ")
            for token in tokens:
                # most lines are key=value so look for that pattern
                splits = token.split("=", 1)
                if len(splits) == 2:
                    if Flows.PRIORITY in splits[0]:
                        splitp = splits[1].split(",", 1)
                        if len(splitp) == 2:
                            pline[Flows.PRIORITY] = splitp[0]
                            pline[Flows.MATCHES] = splitp[1]
                        else:
                            pline[Flows.PRIORITY] = splitp[0]
                            pline[Flows.MATCHES] = ""
                    else:
                        pline[splits[0]] = splits[1].rstrip(",")
                elif token == Flows.SEND_FLOW_REMOVED:
                    # send_flow_rem is a single token without a value
                    pline[token] = token
            self.pdata.append(pline)
            logger.debug("process_data: Processed line %d into: \n%s",
                         self.start + len(self.pdata), pformat(pline))
        logger.debug("process_data: Processed %d lines, skipped %d", len(self.pdata),
                     self.start + len(self.data) - end)

        return self.pdata

    def re_table(self, match):
        """
        regex function to add the table name to table lines

        :param match: The regex match
        :return: The new line with table name
        :rtype: str
        """
        if match.group(Flows.GOTO) is not None:
            table_id = int(match.group(Flows.GOTO))
        elif match.group(Flows.RESUBMIT) is not None:
            table_id = int(match.group(Flows.RESUBMIT))
        else:
            table_id = 256

        rep = "{}({})".format(match.group(), tables.get_table_name(table_id))
        return rep

    def format_data(self):
        if len(self.pdata) == 0:
            logger.warn("There is no data to process")
            return self.pdata
        header = "{:3} {:9} {:8} {:13}     {:6} {:12} {:1} {:3} {:5}\n" \
                 "    {}\n" \
                 "    {}\n" \
            .format("nnn", Flows.COOKIE, Flows.DURATION, Flows.TABLE, "n_pack", Flows.N_BYTES,
                    "S", "ito", "prio",
                    Flows.MATCHES,
                    Flows.ACTIONS)
        header_under = "--- --------- -------- -------------     ------ ------------ - --- -----\n"

        # Match goto_table: nnn or resubmit(,nnn) and return as goto or resubmit match group
        re_gt = re.compile(r"goto_table:(?P<goto>\d{1,3})|"
                           r"resubmit\(,(?P<resubmit>\d{1,3})\)")

        # Add the header as the first two lines of formatted data
        self.fdata = [header, header_under]

        # Format each line of parsed data
        for i, line in enumerate(self.pdata):
            logger.debug("format_data: processing line %d: %s", self.start + i + 1, line)

            if Flows.ACTIONS in line:
                nactions = re_gt.sub(self.re_table, line[Flows.ACTIONS])
            else:
                logger.warn("Missing actions in %s", line)
                nactions = ""

            fline = "{:3} {:9} {:8} {:3} {:13} {:6} {:12} {:1} {:3} {:5}\n" \
                    "    matches={}\n" \
                    "    actions={}\n" \
                .format(i + 1, line[Flows.COOKIE], line[Flows.DURATION],
                        line[Flows.TABLE], tables.get_table_name(int(line[Flows.TABLE])),
                        line[Flows.N_PACKETS], line[Flows.N_BYTES],
                        line[Flows.SEND_FLOW_REMOVED][0], line[Flows.IDLE_TIMEOUT],
                        line[Flows.PRIORITY],
                        line[Flows.MATCHES],
                        nactions)
            self.fdata.append(fline)
            logger.debug("format_data: formatted line %d: %s", self.start + i + 1, fline)
        return self.fdata

    def write_fdata(self, filename):
        request.write_file(filename, self.fdata)
