/*
 * Copyright © 2018 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Objects;

public class ForbiddenMethodCheck extends AbstractCheck {
    private String methodName;

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.METHOD_CALL};
    }

    public int[] getAcceptableTokens() {
        return new int[0];
    }

    public int[] getRequiredTokens() {
        return new int[0];
    }

    @Override
    public void visitToken(DetailAST ast) {
        // Method calls appear in the AST as DOT with two children IDENTs; the second one is the one we're after
        DetailAST dot = ast.findFirstToken(TokenTypes.DOT);
        if (dot != null) {
            DetailAST target = dot.findFirstToken(TokenTypes.IDENT);
            if (target != null) {
                DetailAST method = target.getNextSibling();
                if (method != null && Objects.equals(methodName, method.getText())) {
                    log(ast.getLineNo(), "method " + methodName + " must not be called");
                }
            }
        }
    }
}
