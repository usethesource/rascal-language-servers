package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.IValue;
import io.usethesource.vallang.io.StandardTextWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.interpreter.utils.LimitedResultWriter;

import java.io.IOException;
import java.io.Writer;

public class RascalVariableUtils {

    private static final int MAX_SIZE_STRING_NAME = 128;

    // took from Rascal Eclipse debug.core.model.RascalValue
    public static String getDisplayString(IValue value) {
        Writer w = new LimitedResultWriter(MAX_SIZE_STRING_NAME);
        try {
            new StandardTextWriter(true, 2).write(value, w);
            return w.toString();
        } catch (LimitedResultWriter.IOLimitReachedException e) {
            return w.toString();
        } catch (IOException e) {
            final Logger logger = LogManager.getLogger(RascalVariableUtils.class);
            logger.error(e.getMessage(), e);
            return "error during serialization...";
        }
    }
}
