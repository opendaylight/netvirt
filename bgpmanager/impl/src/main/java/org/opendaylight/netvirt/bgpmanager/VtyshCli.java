/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;

@Command(scope = "odl", name = "show-bgp", description = "")
@SuppressFBWarnings({"DM_DEFAULT_ENCODING", "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"})
public class VtyshCli extends OsgiCommandSupport {

    @Option(name = "--cmd", description = "command to run", required = true, multiValued = false)
    String cmd;

    private static final int BGPD = 1;
    private static final String PASSWORD_CHECK_STR = "Password:";
    private static final String VTY_PASSWORD = "sdncbgpc";
    private static final String NO_PAGINATION_CMD = "terminal length 0";
    private static final int SOCK_TIMEOUT = 5;
    private static final int SERVER_PORT = 2605;

    private static String serverName = "localhost";

    String[] validCommands = new String[] {
        "display routing ip bgp vpnv4 all",
        "display routing ip bgp vpnv4 rd <rd>",
        "display routing ip bgp vpnv4 all neighbors",
        "display routing ip bgp vpnv4 all neighbors  <ip> routes",
        "display routing ip bgp vpnv4 all  <ip/mask>",
        "display routing ip bgp vpnv4 all summary",
        "display routing ip bgp vpnv4 all tags",
        "display routing ip bgp vpnv4 rd <rd>  tags",
        "display routing ip bgp vpnv4 rd <rd>  <ip>",
        "display routing ip bgp vpnv4 rd <rd>  <ip/mask>",
        "display routing ip bgp vpnv6 all",
        "display routing ip bgp vpnv6 rd <rd>",
        "display routing ip bgp vpnv6 all summary",
        "display routing ip bgp vpnv6 all tags",
        "display routing ip bgp neighbors",
        "display routing ip bgp summary",
        "display routing ip bgp ipv4 unicast",
        "display routing ip bgp ipv4 unicast <ip/mask>",
        "display routing bgp neighbors",
        "display routing bgp neighbors <ip>",
        "display routing bgp ipv4 summary",
        "display routing bgp ipv4 vpn summary",
        "display routing bgp ipv4 unicast <ip>",
        "display routing bgp ipv4 unicast <ip/mask>",
        "display routing bgp bfd neighbors details",
        "display routing bgp bfd neighbors",
        "display routing bgp bfd neighbors <ip> details",
        "display routing bgp bfd global-config",
        "display routing running-config"
    };

    @Override
    protected Object doExecute() {
        int handlerModule = 0;
        cmd = cmd.trim();
        if (cmd.equals("") || cmd.equals("help") || cmd.equals("-help") || cmd.equals("--help")) {
            for (String help : validCommands) {
                session.getConsole().println(help);
            }
            return null;
        }
        String[] args = cmd.split(" ");
        if (args.length == 0) {
            return null;
        }
        String firstArg = args[0];
        if (firstArg == null || firstArg.trim().equals("")) {
            session.getConsole().println("Please provide a valid input.");
            return null;
        }
        switch (firstArg) {
            case "ip":
            case "bgp":
                handlerModule = BGPD;
                break;
            case "running-config":
                cmd = "running-config";
                handlerModule = BGPD;
                break;
            default:
                session.getConsole().println("Unknown command");
                return null;
        }

        switch (handlerModule) {
            case BGPD:
                try {
                    handleCommand(cmd);
                } catch (IOException ioe) {
                    session.getConsole().println("I/O error to" + serverName + ":"
                            + SERVER_PORT + "occured " + ioe.getMessage());
                }
                break;
            default:
                break;
        }
        return null;
    }

    public static void setHostAddr(String hostAddr) {
        serverName = hostAddr;
    }

    public String getHostAddr() {
        return serverName;
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public void handleCommand(String command) throws IOException {
        char[] cbuf = new char[10];
        Socket socket;
        PrintWriter outToSocket;
        BufferedReader inFromSocket;
        StringBuilder sb = new StringBuilder();
        int ip;
        int ret;
        StringBuilder temp;
        StringBuilder temp2;
        char ch;
        char gt = '>';
        char hashChar = '#';

        try {
            socket = new Socket(serverName, SERVER_PORT);

        } catch (UnknownHostException ioe) {
            System.out.println("No host to " + serverName + ":"
                                         + SERVER_PORT + "exists: " + ioe.getMessage());
            return;
        } catch (IOException ioe) {
            System.out.println("I/O error to" + serverName + ":"
                                         + SERVER_PORT + "occured " + ioe.getMessage());
            return;
        }
        try {
            socket.setSoTimeout(SOCK_TIMEOUT * 1000);
            outToSocket = new PrintWriter(socket.getOutputStream(), true);
            inFromSocket = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (IOException ioe) {
            System.out.println("I/O error to" + serverName + ":"
                    + SERVER_PORT + "occured " + ioe.getMessage());
            socket.close();
            return;
        }
        while (true) {
            try {
                ret = inFromSocket.read(cbuf);

            } catch (SocketTimeoutException ste) {
                System.out.println("Read from Socket timed Out while asking for password.");
                socket.close();
                return;
            }
            if (ret == -1) {
                System.out.println("Connection closed by BGPd.");
                socket.close();
                return;
            } else {
                sb.append(cbuf);

                if (sb.toString().contains(PASSWORD_CHECK_STR)) {
                    break;
                }
            }
        }

        sb.setLength(0);
        outToSocket.println(VTY_PASSWORD);

        while (true) {
            try {
                ip = inFromSocket.read();
            } catch (SocketTimeoutException ste) {
                System.out.println(sb.toString());
                System.out.println("Socket timeout while verifying the  password in" + serverName + ":"
                        + SERVER_PORT + "occured " + ste.getMessage());
                socket.close();
                return;
            }
            if (ip == gt || ip == hashChar) {
                if (ip == gt) {
                    sb.append(gt);
                } else {
                    sb.append(hashChar);
                }
                break;
            } else if (ip == -1) {
                System.out.println(sb.toString());
                System.out.println("Connection closed by BGPd.");
                socket.close();
                return;
            } else {
                ch = (char) ip;
                sb.append(ch);
            }
        }

        String promptStr = sb.toString();
        String prompt = promptStr.trim();
        sb.setLength(0);
        outToSocket.println(NO_PAGINATION_CMD);
        while (true) {
            try {
                ip = inFromSocket.read();
            } catch (SocketTimeoutException ste) {
                System.out.println(sb.toString());
                System.out.println("Read from socket timeout while sending term len command in" + serverName + ":"
                        + SERVER_PORT + "occured " + ste.getMessage());
                socket.close();
                return;
            }
            if (ip == gt || ip == hashChar) {
                break;
            } else if (ip == -1) {
                System.out.println(sb.toString());
                System.out.println("Connection closed by BGPd.");
                socket.close();
                return;
            } else {
                ch = (char) ip;
                sb.append(ch);
            }
        }
        sb.setLength(0);

        String inputCmd = "show " + command;
        outToSocket.println(inputCmd);
        StringBuilder output = new StringBuilder();
        String errorMsg = "";
        while (true) {
            char[] opBuf = new char[100];
            temp = new StringBuilder();
            temp2 = new StringBuilder();
            try {
                ret = inFromSocket.read(opBuf);

            } catch (SocketTimeoutException ste) {
                errorMsg = "Read from Socket timed Out while getting the data.";
                System.out.println("Socket timeout while getting the data in" + serverName + ":"
                        + SERVER_PORT + "occured " + ste.getMessage());
                break;
            }
            if (ret == -1) {
                errorMsg = "Connection closed by BGPd";
                break;
            }
            temp2.append(opBuf);

            if (temp2.toString().contains(inputCmd)) {

                String replacedStr = temp2.toString().replaceAll(inputCmd, "");
                temp.append(replacedStr);
                temp2.setLength(0);

            } else {
                temp.append(opBuf);
                temp2.setLength(0);

            }

            String outputStr = temp.toString();
            output.append(outputStr);
            if (output.toString().trim().endsWith(prompt)) {
                int index = output.toString().lastIndexOf(prompt);
                String newString = output.toString().substring(0, index);
                output.setLength(0);
                output.append(newString);
                break;
            }
            temp.setLength(0);
        }
        System.out.println(output.toString().trim());
        if (errorMsg.length() > 0) {
            System.out.println(errorMsg);
        }
        socket.close();
    }
}
