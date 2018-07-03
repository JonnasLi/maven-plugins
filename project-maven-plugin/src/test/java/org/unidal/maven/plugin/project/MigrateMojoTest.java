package org.unidal.maven.plugin.project;

import static org.mockito.Mockito.when;

import java.io.File;

import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unidal.helper.Reflects;
import org.unidal.lookup.ComponentTestCase;

@RunWith(JUnit4.class)
public class MigrateMojoTest extends ComponentTestCase {
	@Mock
	private MavenProject m_project;

	@Before
	public void before() {
		MockitoAnnotations.initMocks(this);
	}

	private void checkReverse(String packageName, String reversedPackageName) {
		String actual = new MigrateMojo().reversePackage(packageName);

		Assert.assertEquals("Reversed package name incorrecct!", reversedPackageName, actual);
	}

	private MavenProject createMavenProject(String finalName, String path) throws Exception {
		File baseDir = new File(path).getCanonicalFile();

		when(m_project.getBasedir()).thenReturn(baseDir);
		when(m_project.getPackaging()).thenReturn("war");
		// when(m_project.getFile()).thenReturn(new File(baseDir, finalName +
		// ".jar"));

		// MavenProject m_project = new MavenProject();

		// m_project.setFile(new File(baseDir, finalName + ".jar"));
		m_project.getCompileSourceRoots().add(new File(baseDir, "src/main/java").toString());
		m_project.getTestCompileSourceRoots().add(new File(baseDir, "src/test/java").toString());
		m_project.getResources().add(createResource(new File(baseDir, "src/main/resources")));
		m_project.getTestResources().add(createResource(new File(baseDir, "src/test/resources")));

		return m_project;
	}

	protected Resource createResource(File directory) {
		Resource resource = new Resource();

		resource.setDirectory(directory.toString());
		return resource;
	}

	private void setField(MigrateMojo mojo, String field, Object value) {
		Reflects.forField().setDeclaredFieldValue(mojo.getClass(), field, mojo, value);
	}

	@Test
	@Ignore
	public void testMigrate() throws Exception {
		MigrateMojo mojo = new MigrateMojo();

		setField(mojo, "project", createMavenProject("project-maven-plugin", "."));
		setField(mojo, "sourcePackage", "org.unidal.maven");
		setField(mojo, "targetPackage", "org.unidal.test");
		setField(mojo, "targetDir", "target/project-maven-plugin");

		mojo.execute();
	}

	@Test
	public void testReversePackage() {
		checkReverse("a.b", "b.a");
		checkReverse("a.b.c", "c.b.a");

		checkReverse("a.b.", ".b.a");
		checkReverse("a.b.c.", ".c.b.a");
	}
}
