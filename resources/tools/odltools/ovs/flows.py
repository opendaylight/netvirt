import logging
import re
import tables
import request


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


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
        self.data = data
        self.process_data()
        self.format_data()
        self.pdata = None
        self.fdata = None
        logger.info("Data has been parsed and formatted")

    def set_log_level(self, level):
        logger.setLevel(level)

    def process_data(self):
        """
        Process the dump-flows data into a map.

        The processing will tokenize the parts in each line of the flow dump.

        :return: The processed data
        """
        # cookie=0x805138a, duration=193.107s, table=50, n_packets=119, n_bytes=11504, idle_timeout=300,
        #  send_flow_rem priority=20,metadata=0x2138a000000/0xfffffffff000000,dl_src=fa:16:3e:15:a8:66
        #  actions=goto_table:51
        self.pdata = []
        for line in self.data[1:]:
            pline = {}
            tokens = line.split(" ")
            for token in tokens:
                # most lines are key=value so look for that pattern
                splits = token.split("=", 1)
                # send_flow_remove is a single token without a value
                if len(splits) == 1 and token == Flows.SEND_FLOW_REMOVED:
                    pline[token] = token
                elif len(splits) == 2:
                    pline[splits[0]] = splits[1].rstrip(",")
            self.pdata.append(pline)
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
            nactions = re_gt.sub(self.re_table, line[Flows.ACTIONS])

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