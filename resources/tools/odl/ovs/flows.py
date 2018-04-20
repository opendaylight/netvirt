import logging
from pprint import pformat
import re

import tables
import request


logging.basicConfig(format="%(levelname)-8s []%(name)s] [%(module)s:%(lineno)d] %(message)s",
                    level=logging.DEBUG)
logger = logging.getLogger(__name__)


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

    def __init__(self, data, level=logging.INFO):
        self.pdata = []
        self.fdata = []
        self.data = data
        print "level: {}".format(level)
        logger.setLevel(level)
        if level is not logging.INFO:
            logger.info("effective: %d", logger.getEffectiveLevel())
        self.process_data()
        self.format_data()
        logger.info("data has been processed and parsed")

    def set_log_level(self, level):
        logger.setLevel(level)
        logger.info("effective: %d", logger.getEffectiveLevel())

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
            start = 1
        else:
            start = 0
        if "jenkins" in self.data[-1]:
            end = len(self.data) - 2
        else:
            end = len(self.data) - 1

        # Parse each line of the data. Each line is a single flow.
        # Create a dictionary of all tokens in that flow.
        # Append this flow dictionary to a flow list.
        for line in self.data[start:end]:
            pline = {}
            tokens = line.split(" ")
            for token in tokens:
                # most lines are key=value so look for that pattern
                splits = token.split("=", 1)
                if len(splits) == 2:
                    pline[splits[0]] = splits[1].rstrip(",")
                elif token == Flows.SEND_FLOW_REMOVED:
                    # send_flow_rem is a single token without a value
                    pline[token] = token
            self.pdata.append(pline)
        logger.info("Processed %d lines, skipped %d", len(self.pdata), start)
        logger.debug("Processed data: %s", pformat(self.pdata))
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
            self.logger.warn("There is no data to process")
            return self.pdata
        header = "{:9} {:8} {:13}     {:6} {:12} {}... {}... {} {}\n" \
            .format(Flows.COOKIE, Flows.DURATION, Flows.TABLE, "n_pack", Flows.N_BYTES, Flows.MATCHES, Flows.ACTIONS,
                    Flows.IDLE_TIMEOUT, Flows.DURATION)
        header_under = "--------- -------- -------------     ------ ------------ ---------- ---------- --------" \
                       "---- --------\n"

        # Match goto_table: nnn or resubmit(,nnn) and return as goto or resubmit match group
        re_gt = re.compile(r"goto_table:(?P<goto>\d{1,3})|"
                           r"resubmit\(,(?P<resubmit>\d{1,3})\)")
        self.fdata = [header, header_under]
        for line in self.pdata:
            if Flows.SEND_FLOW_REMOVED in line:
                send_flow_rem = " {} ".format(line[Flows.SEND_FLOW_REMOVED])
            else:
                send_flow_rem = ""
            if Flows.IDLE_TIMEOUT in line:
                idle_timeo = " {}={}".format(Flows.IDLE_TIMEOUT, line[Flows.IDLE_TIMEOUT])
            else:
                idle_timeo = ""
            if Flows.ACTIONS in line:
                nactions = re_gt.sub(self.re_table, line[Flows.ACTIONS])
            else:
                logger.warn("Missing actions in %s", line)
                nactions = ""

            logger.debug("line: %s", line)

            fline = "{:9} {:8} {:3} {:13} {:6} {:12} priority={} actions={}{}{}" \
                .format(line[Flows.COOKIE], line[Flows.DURATION],
                        line[Flows.TABLE], tables.get_table_name(int(line[Flows.TABLE])),
                        line[Flows.N_PACKETS], line[Flows.N_BYTES],
                        line[Flows.PRIORITY], nactions,
                        idle_timeo, send_flow_rem, )
            self.fdata.append(fline)
        return self.fdata

    def write_fdata(self, filename):
        request.write_file(filename, self.fdata)
