package org.rascalmpl.vscode.lsp.rascal.model;

import java.net.URISyntaxException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValueFactory;

public class PathConfigsTest {

    private static final IValueFactory VF = IRascalValueFactory.getInstance();

    private ISourceLocation lspProjectRoot;

    @Before
    public void setUp() throws URISyntaxException {
        lspProjectRoot = Locations.toPhysicalIfPossible(VF.sourceLocation("cwd", "", ""));
    }

    private void assertEquals(ISourceLocation expected, ISourceLocation actual) {
        Assert.assertEquals(URIUtil.getChildLocation(expected, "/"), URIUtil.getChildLocation(actual, "/"));
    }

    @Test
    public void projectRootFromRoot() {
        var origin = lspProjectRoot;
        assertEquals(lspProjectRoot, PathConfigs.inferProjectRoot(origin));
    }

    @Test
    public void projectRootFromPom() {
        var origin = URIUtil.getChildLocation(lspProjectRoot, "pom.xml");
        assertEquals(lspProjectRoot, PathConfigs.inferProjectRoot(origin));
    }

    @Test
    public void projectRootFromRascalMf() {
        var origin = URIUtil.getChildLocation(lspProjectRoot, "META-INF/RASCAL.MF");
        assertEquals(lspProjectRoot, PathConfigs.inferProjectRoot(origin));
    }

    // @Test
    // public void projectRootFromNestedProject() {
    //     var origin = URIUtil.getChildLocation(lspProjectRoot, "src/test/resources/project-a/pom.xml");
    //     assertEquals(URIUtil.getChildLocation(lspProjectRoot, "src/test/resources/project-a"), PathConfigs.inferProjectRoot(origin));
    // }
}
