/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw;

import java.io.PrintStream;
import org.opendaylight.genius.utils.hwvtep.DebugEvent;

public class MdsalEvent extends DebugEvent {

    private Object data1;
    private Object data2;
    private Object data3;
    private Object data4;

    public MdsalEvent(Object data1) {
        this.data1 = data1;
    }

    public MdsalEvent(Object data1, Object data2) {
        this.data1 = data1;
        this.data2 = data2;
    }

    public MdsalEvent(Object data1, Object data2, Object data3) {
        this.data1 = data1;
        this.data2 = data2;
        this.data3 = data3;
    }

    public MdsalEvent(Object data1, Object data4, Object data2, Object data3) {
        this.data1 = data1;
        this.data4 = data4;
        this.data2 = data2;
        this.data3 = data3;
    }

    public void print(PrintStream out) {
        print(data1, out);
        print(data2, out);
        print(data3, out);
        print(data4, out);
        out.println();
    }

    public void print(Object data, PrintStream out) {
        if (data != null) {
            out.print(" ");
            out.print(data.toString());
        }
    }
}
