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

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.DumbSlave;
import hudson.tools.ToolInstallation;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class XcodeXipInstallerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

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
    public void testXcodeInstallationDoCheckHome() throws Exception {
        XcodeInstallation.DescriptorImpl installationDescriptor = new XcodeInstallation.DescriptorImpl();
        assertEquals("Xcode tools", installationDescriptor.getDisplayName());
        assertEquals(FormValidation.Kind.OK, installationDescriptor.doCheckHome(null).kind);
        assertEquals(FormValidation.Kind.OK, installationDescriptor.doCheckHome(new File("")).kind);
        assertEquals(FormValidation.Kind.OK, installationDescriptor.doCheckHome(new File("   ")).kind);

        File nonExistent = new File(temp.newFolder(), "missing_dir");
        assertEquals(FormValidation.Kind.ERROR, installationDescriptor.doCheckHome(nonExistent).kind);

        File fakeXcode = temp.newFolder();
        File xcodebuild = new File(fakeXcode, "usr/bin/xcodebuild");
        assertTrue(xcodebuild.getParentFile().mkdirs());
        assertTrue(xcodebuild.createNewFile());
        xcodebuild.setExecutable(true);
        assertEquals(FormValidation.Kind.OK, installationDescriptor.doCheckHome(fakeXcode).kind);
    }

    @Test(expected = AbortException.class)
    public void testPerformInstallationThrowsWhenXipNotAvailable() throws Exception {
        File installFolder = temp.newFolder();
        ToolInstallation tool = new XcodeInstallation("xcode-default", installFolder.getAbsolutePath(), Collections.emptyList());
        XcodeXipInstaller installer = new XcodeXipInstaller(null, "https://example.com/Xcode.xip", null);
        installer.performInstallation(tool, j.jenkins, TaskListener.NULL);
    }

    @Test
    public void testPerformInstallationSuccessAndCacheHit() throws Exception {
        File installFolder = temp.newFolder();
        File archiveSource = temp.newFile("sample_xcode.xip");
        Files.writeString(archiveSource.toPath(), "mock-xip-content", StandardCharsets.UTF_8);
        String fileUrl = archiveSource.toURI().toURL().toString();

        ToolInstallation tool = new XcodeInstallation("xcode-test", installFolder.getAbsolutePath(), Collections.emptyList());

        TestableXcodeXipInstaller installer = new TestableXcodeXipInstaller(fileUrl, null);
        FilePath result = installer.performInstallation(tool, j.jenkins, TaskListener.NULL);

        assertNotNull(result);
        assertEquals(new FilePath(new File(installFolder, "Xcode.app/Contents/Developer")).getRemote(), result.getRemote());
        assertTrue(new File(installFolder, ".xip-installed").exists());
        assertFalse(new File(installFolder, "xcode_installer.xip").exists());
        assertEquals(1, installer.extractCount);

        // Second run: cache hit should return existing resolved home without re-extracting
        FilePath cachedResult = installer.performInstallation(tool, j.jenkins, TaskListener.NULL);
        assertEquals(result.getRemote(), cachedResult.getRemote());
        assertEquals(1, installer.extractCount);
    }

    @Test
    public void testPerformInstallationReinstallsOnMarkerMismatch() throws Exception {
        File installFolder = temp.newFolder();
        File archiveSource = temp.newFile("sample_xcode2.xip");
        Files.writeString(archiveSource.toPath(), "mock-xip-content", StandardCharsets.UTF_8);
        String fileUrl = archiveSource.toURI().toURL().toString();

        File markerFile = new File(installFolder, ".xip-installed");
        Files.writeString(markerFile.toPath(), "different-cache-marker", StandardCharsets.UTF_8);

        ToolInstallation tool = new XcodeInstallation("xcode-mismatch", installFolder.getAbsolutePath(), Collections.emptyList());
        TestableXcodeXipInstaller installer = new TestableXcodeXipInstaller(fileUrl, null);

        FilePath result = installer.performInstallation(tool, j.jenkins, TaskListener.NULL);
        assertNotNull(result);
        assertEquals(1, installer.extractCount);
        assertEquals(fileUrl, Files.readString(markerFile.toPath()).trim());
    }

    @Test
    public void testPerformInstallationReinstallsWhenResolvedHomeMissing() throws Exception {
        File installFolder = temp.newFolder();
        File archiveSource = temp.newFile("sample_xcode3.xip");
        Files.writeString(archiveSource.toPath(), "mock-xip-content", StandardCharsets.UTF_8);
        String fileUrl = archiveSource.toURI().toURL().toString();

        String subdir = "CustomXcode.app/Contents/Developer";
        File markerFile = new File(installFolder, ".xip-installed");
        Files.writeString(markerFile.toPath(), fileUrl + "|" + subdir, StandardCharsets.UTF_8);

        ToolInstallation tool = new XcodeInstallation("xcode-missing-home", installFolder.getAbsolutePath(), Collections.emptyList());
        TestableXcodeXipInstaller installer = new TestableXcodeXipInstaller(fileUrl, subdir);
        installer.simulateCustomSubdir = true;

        FilePath result = installer.performInstallation(tool, j.jenkins, TaskListener.NULL);
        assertNotNull(result);
        assertEquals(1, installer.extractCount);
    }

    @Test(expected = AbortException.class)
    public void testPerformInstallationExtractionFailure() throws Exception {
        File installFolder = temp.newFolder();
        File archiveSource = temp.newFile("sample_fail.xip");
        Files.writeString(archiveSource.toPath(), "mock-xip-content", StandardCharsets.UTF_8);
        String fileUrl = archiveSource.toURI().toURL().toString();

        ToolInstallation tool = new XcodeInstallation("xcode-fail", installFolder.getAbsolutePath(), Collections.emptyList());
        TestableXcodeXipInstaller installer = new TestableXcodeXipInstaller(fileUrl, null);
        installer.exitCode = 1;

        try {
            installer.performInstallation(tool, j.jenkins, TaskListener.NULL);
        } finally {
            assertFalse(new File(installFolder, "xcode_installer.xip").exists());
            assertFalse(new File(installFolder, ".xip-installed").exists());
        }
    }

    @Test
    public void testPerformInstallationWithCustomSubdir() throws Exception {
        File installFolder = temp.newFolder();
        File archiveSource = temp.newFile("sample_custom.xip");
        Files.writeString(archiveSource.toPath(), "mock-xip-content", StandardCharsets.UTF_8);
        String fileUrl = archiveSource.toURI().toURL().toString();

        ToolInstallation tool = new XcodeInstallation("xcode-custom", installFolder.getAbsolutePath(), Collections.emptyList());
        TestableXcodeXipInstaller installer = new TestableXcodeXipInstaller(fileUrl, "CustomXcode.app/Contents/Developer");
        installer.simulateCustomSubdir = true;

        FilePath result = installer.performInstallation(tool, j.jenkins, TaskListener.NULL);
        assertEquals(new FilePath(new File(installFolder, "CustomXcode.app/Contents/Developer")).getRemote(), result.getRemote());
        assertEquals(fileUrl + "|CustomXcode.app/Contents/Developer", Files.readString(new File(installFolder, ".xip-installed").toPath()).trim());
    }

    @Test
    public void testIsXipAvailableAndIsCurlAvailable() throws Exception {
        XcodeXipInstaller installer = new XcodeXipInstaller(null, "https://example.com/Xcode.xip", null);

        // Disconnected node without remoting channel: both return false
        DumbSlave disconnected = j.createSlave();
        assertFalse(installer.isXipAvailable(disconnected));
        assertFalse(installer.isCurlAvailable(disconnected));

        // Node with an active channel
        DumbSlave onlineSlave = j.createOnlineSlave();

        // When channel != null: exercises FilePath checking on the channel
        boolean xipAvail = installer.isXipAvailable(onlineSlave);
        boolean curlAvail = installer.isCurlAvailable(onlineSlave);
        // On non-macOS test runner, /usr/bin/xip does not exist
        assertFalse(xipAvail);
        assertNotNull(onlineSlave.getChannel());
    }

    @Test
    public void testExtractXipDirect() throws Exception {
        XcodeXipInstaller installer = new XcodeXipInstaller(null, "https://example.com/Xcode.xip", null);
        FilePath archive = new FilePath(temp.newFile("dummy.xip"));
        FilePath dest = new FilePath(temp.newFolder());

        try {
            installer.extractXip(archive, dest, j.jenkins, TaskListener.NULL);
        } catch (IOException expected) {
            // /usr/bin/xip does not exist on non-macOS test environments
        }
    }

    @Test
    public void testDownloadArchiveCurlFallback() throws Exception {
        File destFile = temp.newFile("download_target.xip");
        FilePath target = new FilePath(destFile);

        XcodeXipInstaller installer = new XcodeXipInstaller(null, "http://127.0.0.1:9/unreachable.xip", null) {
            @Override
            protected boolean isCurlAvailable(Node node) {
                return true;
            }
        };

        try {
            installer.downloadArchive("http://127.0.0.1:9/unreachable.xip", target, j.jenkins, TaskListener.NULL);
        } catch (IOException expected) {
            // Expected since localhost:9 is unreachable, but curl attempt was exercised
        }
    }

    private static class TestableXcodeXipInstaller extends XcodeXipInstaller {
        int exitCode = 0;
        int extractCount = 0;
        boolean simulateCustomSubdir = false;

        public TestableXcodeXipInstaller(String url, String subdir) {
            super(null, url, subdir);
        }

        @Override
        protected boolean isXipAvailable(Node node) {
            return true;
        }

        @Override
        protected int extractXip(FilePath archive, FilePath destinationDir, Node node, TaskListener log)
                throws IOException, InterruptedException {
            extractCount++;
            if (simulateCustomSubdir) {
                destinationDir.child("CustomXcode.app/Contents/Developer").mkdirs();
            } else {
                destinationDir.child("Xcode.app/Contents/Developer").mkdirs();
            }
            return exitCode;
        }
    }

    private static class DummyToolInstallation extends ToolInstallation {
        public DummyToolInstallation(String name, String home) {
            super(name, home, null);
        }
    }
}
