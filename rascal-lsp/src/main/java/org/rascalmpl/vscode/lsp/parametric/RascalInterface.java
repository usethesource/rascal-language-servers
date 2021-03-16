 package org.rascalmpl.vscode.lsp.parametric;

import org.rascalmpl.ideservices.IDEServices;

import io.usethesource.vallang.IConstructor;

/**
 * This is to call the language registry from Rascal (for example in REPL code)
 * @param services
 */
public class RascalInterface {
    private final IDEServices services;

    public RascalInterface(IDEServices services) {
        this.services = services;
    }

    void registerLanguage(IConstructor lang) {
        
    }
}
