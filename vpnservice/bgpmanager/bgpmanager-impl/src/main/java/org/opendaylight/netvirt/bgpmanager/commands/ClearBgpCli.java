/*
 * Copyright (c) 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "odl", name = "clear-bgp-neighbor", description = "")
public class ClearBgpCli extends OsgiCommandSupport {

    private static int serverPort = 2605;
    private static String serverName = "localhost";
    public static String passwordCheckStr = "Password:";
    public static String vtyPassword = "sdncbgpc";
    public static String enableCmd = "enable";
    public static int sockTimeout = 5;
    static char HASH_PROMPT = '#';
    static char GT = '>';

    private static final Logger LOG = LoggerFactory.getLogger(ClearBgpCli.class);

    @Argument(name = "neighbor-ip", description = "neighbor ip to be cleared", required = false, multiValued = false)
    String nbr = "";

    Socket socket = null;
    PrintWriter out = null;
    BufferedReader in = null;

    public static void setHostAddr(String hostAddr) {
        serverName = hostAddr;
    }

    @Override
    protected Object doExecute() throws Exception {
        if (nbr.isEmpty()) {
            session.getConsole().println("enter neighbor ip to be cleared");
            session.getConsole().println("Usage:");
            session.getConsole().println("odl:clear-bgp-neighbor <neighbor|all>");
            return null;
        } else if ("all".equals(nbr)) {
            nbr = "*";
        } else {
            try {
                InetAddress.getByName(nbr);
            } catch (UnknownHostException e) {
                session.getConsole().println("Invalid neighbor ip");
                return null;
            }
        }
        clearBgp("clear ip bgp " + nbr);
        return null;
    }

    public static void main(String[] args) throws IOException {
        ClearBgpCli test = new ClearBgpCli();
        test.clearBgp("clear ip bgp");
    }

    public void clearBgp(String clearCommand) throws IOException {
        try {
            LOG.trace("Connecting to BgpHost: {}, port {} for clearBgp()", serverName, serverPort);
            socket = new Socket(serverName, serverPort);
        } catch (IOException ioe) {
            session.getConsole().println("failed to connect to bgpd " + ioe.getMessage());
            return;
        }
        intializeSocketOptions();
        try {
            readPassword(in);

            out.println(vtyPassword);
            out.println("enable");
            LOG.trace("reading until HASH sign");
            readUntilPrompt(in, HASH_PROMPT);

            out.println(clearCommand);
            readUntilPrompt(in, HASH_PROMPT);
        } catch (IOException e) {
            session.getConsole().println(e.getMessage());
        } finally {
            socket.close();
        }
        return;

    }

    private void intializeSocketOptions() throws SocketException, IOException {
        socket.setSoTimeout(sockTimeout * 1000);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    private static boolean readPassword(BufferedReader in) throws IOException {
        return readUntilPrompt(in, GT, passwordCheckStr);
    }

    private static boolean readUntilPrompt(BufferedReader in, char promptChar) throws IOException {
        return readUntilPrompt(in, promptChar, null);
    }

    private static boolean readUntilPrompt(BufferedReader in, char promptChar, String passwordCheckStr)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        int ret = 0;
        while (true) {
            ret = in.read();
            if (ret == -1) {
                throw new IOException("connection closed by bgpd");
            } else if (ret == promptChar) {
                return true;
            }

            sb.append((char) ret);
            if (passwordCheckStr != null) {
                if (sb.toString().contains(passwordCheckStr)) {
                    break;
                }
            }
        }
        sb.setLength(0);
        return true;
    }
}
