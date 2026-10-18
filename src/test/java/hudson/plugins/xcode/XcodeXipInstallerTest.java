/*
 * The MIT License
 *
 * Copyright (c) 2026 Jenkins Xcode Plugin Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.xcode;

import hudson.FilePath;
import hudson.tools.ToolInstallation;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class XcodeXipInstallerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private XcodeXipInstaller.DescriptorImpl descriptor;

    @Before
    public void setUp() {
        descriptor = new XcodeXipInstaller.DescriptorImpl();
    }

    @Test
    public void testConstructorAndGetters() {
        XcodeXipInstaller installer = new XcodeXipInstaller("osx", "https://artifactory.local/xcode/Xcode_15.4.xip", "Xcode.app/Contents/Developer");
        assertEquals("osx", installer.getLabel());
        assertEquals("https://artifactory.local/xcode/Xcode_15.4.xip", installer.getUrl());
        assertEquals("Xcode.app/Contents/Developer", installer.getSubdir());
    }

    @Test
    public void testConstructorTrimsValues() {
        XcodeXipInstaller installer = new XcodeXipInstaller("  osx  ", "  https://example.com/x.xip  ", "   ");
        assertEquals("osx", installer.getLabel());
        assertEquals("https://example.com/x.xip", installer.getUrl());
        assertNull(installer.getSubdir());
    }

    @Test
    public void testDescriptorDisplayNameAndApplicability() {
        assertEquals("Extract .xip archive", descriptor.getDisplayName());
        assertTrue(descriptor.isApplicable(XcodeInstallation.class));
        assertFalse(descriptor.isApplicable(DummyToolInstallation.class));
    }

    @Test
    public void testDoCheckUrl() {
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckUrl("https://artifactory.local/Xcode.xip").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckUrl("http://mirror.internal/Xcode.xip").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckUrl("file:///tmp/Xcode.xip").kind);

        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckUrl(null).kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckUrl("").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckUrl("   ").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckUrl("no-scheme-url").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckUrl("http:// invalid url with spaces").kind);
    }

    @Test
    public void testResolveHomeExplicitSubdir() throws Exception {
        File base = temp.newFolder();
        FilePath baseDir = new FilePath(base);
        XcodeXipInstaller installer = new XcodeXipInstaller(null, "https://example.com/xcode.xip", "CustomXcode.app/Contents/Developer");
        
        FilePath resolved = installer.resolveHome(baseDir);
        assertEquals(baseDir.child("CustomXcode.app/Contents/Developer").getRemote(), resolved.getRemote());
    }

    @Test
    public void testResolveHomeAutoDetectsXcodeApp() throws Exception {
        File base = temp.newFolder();
        File xcodeDev = new File(base, "Xcode.app/Contents/Developer");
        assertTrue(xcodeDev.mkdirs());

        FilePath baseDir = new FilePath(base);
        XcodeXipInstaller installer = new XcodeXipInstaller(null, "https://example.com/xcode.xip", null);

        FilePath resolved = installer.resolveHome(baseDir);
        assertEquals(new FilePath(xcodeDev).getRemote(), resolved.getRemote());
    }

    @Test
    public void testResolveHomeAutoDetectsContentsDeveloper() throws Exception {
        File base = temp.newFolder();
        File dev = new File(base, "Contents/Developer");
        assertTrue(dev.mkdirs());

        FilePath baseDir = new FilePath(base);
        XcodeXipInstaller installer = new XcodeXipInstaller(null, "https://example.com/xcode.xip", null);

        FilePath resolved = installer.resolveHome(baseDir);
        assertEquals(new FilePath(dev).getRemote(), resolved.getRemote());
    }

    @Test
    public void testResolveHomeFallbackToBase() throws Exception {
        File base = temp.newFolder();
        FilePath baseDir = new FilePath(base);
        XcodeXipInstaller installer = new XcodeXipInstaller(null, "https://example.com/xcode.xip", null);

        FilePath resolved = installer.resolveHome(baseDir);
        assertEquals(baseDir.getRemote(), resolved.getRemote());
    }

    @Test
    public void testXcodeInstallationDoCheckHome() {
        XcodeInstallation.DescriptorImpl installationDescriptor = new XcodeInstallation.DescriptorImpl();
        assertEquals(FormValidation.Kind.OK, installationDescriptor.doCheckHome(null).kind);
        assertEquals(FormValidation.Kind.OK, installationDescriptor.doCheckHome(new File("")).kind);
        assertEquals(FormValidation.Kind.OK, installationDescriptor.doCheckHome(new File("   ")).kind);
    }

    private static class DummyToolInstallation extends ToolInstallation {
        public DummyToolInstallation(String name, String home) {
            super(name, home, null);
        }
    }
}
